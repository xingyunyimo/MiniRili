package com.minirili.app.ui.screens.calendar

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.minirili.app.data.HolidayService
import com.minirili.app.ui.navigation.Screen
import com.minirili.app.ui.screens.weather.WeatherCard
import com.minirili.app.ui.viewmodel.EventViewModel
import com.minirili.app.utils.DateUtils
import com.minirili.app.utils.AppLaunchPrefs
import com.minirili.app.utils.AutoStartHelper
import com.minirili.app.utils.IcsUtils
import com.minirili.app.utils.LunarCalendar
import com.minirili.app.stopAlarmOnEventOpen
import java.util.Calendar

enum class CalendarViewType { MONTH, WEEK, DAY, YEAR }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CalendarScreen(
    viewModel: EventViewModel,
    navController: NavController,
    initialViewMode: CalendarViewType = CalendarViewType.MONTH,
    onImportICS: (List<com.minirili.app.database.entity.EventEntity>) -> Unit = {}
) {
    val context = LocalContext.current
    var viewMode by remember(initialViewMode) { mutableStateOf(initialViewMode) }
    var selectedDate by remember { mutableStateOf(DateUtils.today()) }
    var expandedMenu by remember { mutableStateOf(false) }
    var showMonthPicker by remember { mutableStateOf(false) }
    var showWeekPicker by remember { mutableStateOf(false) }
    var showDayPicker by remember { mutableStateOf(false) }
    var showYearPicker by remember { mutableStateOf(false) }

    val currentEvents = viewModel.currentEvents.collectAsState()
    val allEvents = viewModel.allEvents.collectAsState()

    var showImportDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showSearchDialog by remember { mutableStateOf(false) }
    var showAutoStartDialog by remember { mutableStateOf(false) }

    LaunchedEffect(selectedDate) { viewModel.selectDate(selectedDate) }

    // POST_NOTIFICATIONS 运行时权限
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
            } else true
        )
    }
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted
        // 用户已回应权限对话框，再检查自启动
        if (AppLaunchPrefs.shouldAskAutoStart(context)) {
            showAutoStartDialog = true
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            // Android 12- 或权限已授予，无需弹权限，直接检查自启动
            if (AppLaunchPrefs.shouldAskAutoStart(context)) {
                showAutoStartDialog = true
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val content = input.bufferedReader().readText()
                    val mime = context.contentResolver.getType(uri) ?: ""
                    if (mime.contains("json") || uri.toString().lowercase().endsWith(".json")) {
                        viewModel.importJSON(content)
                    } else {
                        viewModel.importICS(content)
                    }
                }
            }.onSuccess {
                Toast.makeText(context, "导入完成", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, "导入失败: ${it.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/calendar")) { uri ->
        if (uri != null) {
            runCatching {
                val events = allEvents.value  // 包含已完成事件，按 Bug1 要求保留
                val icsContent = IcsUtils.generateICS(events)
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(icsContent.toByteArray())
                }
            }.onSuccess {
                Toast.makeText(context, "导出完成", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, "导出失败: ${it.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val selCal = DateUtils.parseGregorian(selectedDate)
    val selYear = selCal.get(Calendar.YEAR)
    val selMonth = selCal.get(Calendar.MONTH) + 1
    val selWeekNumber = getWeekNumber(selectedDate)

    val headerTitleText = when (viewMode) {
        CalendarViewType.MONTH -> "${DateUtils.getMonthName(selMonth)} $selYear"
        CalendarViewType.WEEK -> "$selYear 第 $selWeekNumber 周"
        CalendarViewType.DAY -> selectedDate
        CalendarViewType.YEAR -> "$selYear 年"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        headerTitleText,
                        modifier = Modifier.clickable {
                            when (viewMode) {
                                CalendarViewType.MONTH -> showMonthPicker = true
                                CalendarViewType.WEEK -> showWeekPicker = true
                                CalendarViewType.DAY -> showDayPicker = true
                                CalendarViewType.YEAR -> showYearPicker = true
                            }
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { selectedDate = DateUtils.today() }) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "今",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showSearchDialog = true }) {
                        Icon(Icons.Default.Search, contentDescription = "搜索")
                    }
                    IconButton(onClick = { navController.navigate(Screen.AllEvents.route) }) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = "所有事件")
                    }
                    IconButton(onClick = { expandedMenu = true }) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = "更多选项")
                    }

                    DropdownMenu(expanded = expandedMenu, onDismissRequest = { expandedMenu = false }) {
                        listOf(
                            "月视图" to CalendarViewType.MONTH,
                            "日视图" to CalendarViewType.DAY,
                            "年视图" to CalendarViewType.YEAR
                        ).forEach { (label, type) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = { viewMode = type; expandedMenu = false }
                            )
                        }
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("导入 ICS") },
                            leadingIcon = { Icon(Icons.Default.CalendarMonth, contentDescription = null) },
                            onClick = { showImportDialog = true; expandedMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("导出 ICS") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                            onClick = { showExportDialog = true; expandedMenu = false }
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { navController.navigate(Screen.EventDetail.createRoute(0L, selectedDate)) }) {
                Icon(Icons.Default.Add, contentDescription = "新建事件")
            }
        }
    ) { paddingValues ->
        val pad = PaddingValues(
            start = paddingValues.calculateStartPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
            end = paddingValues.calculateEndPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
            bottom = paddingValues.calculateBottomPadding()
        )
        Column(modifier = Modifier.fillMaxSize().padding(top = paddingValues.calculateTopPadding())) {
            when (viewMode) {
                CalendarViewType.MONTH -> {
                    Column(
                        Modifier
                            .weight(1f, fill = false)
                            .swipeNav(
                                onPrev = { selectedDate = shiftMonth(selectedDate, -1) },
                                onNext = { selectedDate = shiftMonth(selectedDate, +1) }
                            )
                    ) {
                        renderMonthView(
                            selectedDate = selectedDate,
                            allEvents = allEvents.value,
                            onDateSelect = { selectedDate = it },
                            doubleTapToDayView = { d -> selectedDate = d; viewMode = CalendarViewType.DAY },
                            eventClick = { id -> stopAlarmOnEventOpen(); navController.navigate(Screen.EventDetail.createRoute(id)) },
                            onOpenWeatherPage = { navController.navigate(Screen.Weather.route) },
                            onMoveEvent = { id, up ->
                                if (up) viewModel.moveEventUp(id, selectedDate)
                                else viewModel.moveEventDown(id, selectedDate)
                            }
                        )
                    }
                }
                CalendarViewType.WEEK -> {
                    Column(
                        Modifier
                            .weight(1f, fill = false)
                            .swipeNav(
                                onPrev = { selectedDate = shiftDays(selectedDate, -7) },
                                onNext = { selectedDate = shiftDays(selectedDate, +7) }
                            )
                    ) {
                        renderWeekView(
                            selectedDate = selectedDate,
                            allEvents = allEvents.value,
                            onDateSelect = { selectedDate = it },
                            doubleTapToDayView = { d -> selectedDate = d; viewMode = CalendarViewType.DAY },
                            eventClick = { id -> stopAlarmOnEventOpen(); navController.navigate(Screen.EventDetail.createRoute(id)) },
                            onOpenWeatherPage = { navController.navigate(Screen.Weather.route) },
                            onMoveEvent = { id, up ->
                                if (up) viewModel.moveEventUp(id, selectedDate)
                                else viewModel.moveEventDown(id, selectedDate)
                            }
                        )
                    }
                }
                CalendarViewType.DAY -> {
                    Column(
                        Modifier
                            .weight(1f, fill = false)
                            .swipeNav(
                                onPrev = { selectedDate = shiftDays(selectedDate, -1) },
                                onNext = { selectedDate = shiftDays(selectedDate, +1) }
                            )
                    ) {
                        renderDayView(selectedDate, currentEvents.value, allEvents.value,
                            onEventClick = { id -> navController.navigate(Screen.EventDetail.createRoute(id)) },
                            onOpenWeatherPage = { navController.navigate(Screen.Weather.route) },
                            onMoveEvent = { id, up ->
                                if (up) viewModel.moveEventUp(id, selectedDate)
                                else viewModel.moveEventDown(id, selectedDate)
                            }
                        )
                    }
                }
                CalendarViewType.YEAR -> {
                    Column(
                        Modifier
                            .weight(1f, fill = false)
                            .swipeNav(
                                onPrev = { selectedDate = shiftYear(selectedDate, -1) },
                                onNext = { selectedDate = shiftYear(selectedDate, +1) }
                            )
                    ) {
                        renderYearView(
                            selectedDate = selectedDate,
                            onDayClick = { dateStr ->
                                selectedDate = dateStr
                                viewMode = CalendarViewType.DAY
                            },
                            onDayDoubleClick = { dateStr ->
                                selectedDate = dateStr
                                viewMode = CalendarViewType.DAY
                            },
                            onMonthClick = { month ->
                                selectedDate = String.format("%04d-%02d-01", selYear, month)
                                viewMode = CalendarViewType.MONTH
                            }
                        )
                    }
                }
            }

            if (showExportDialog) {
                AlertDialog(
                    onDismissRequest = { showExportDialog = false },
                    title = { Text("导出 ICS") },
                    text = { Text("将导出 ${allEvents.value.size} 个事件（含已完成）") },
                    confirmButton = { TextButton(onClick = { showExportDialog = false; exportLauncher.launch("calendar_export.ics") }) { Text("导出") } },
                    dismissButton = { TextButton(onClick = { showExportDialog = false }) { Text("取消") } }
                )
            }
            if (showImportDialog) {
                AlertDialog(
                    onDismissRequest = { showImportDialog = false },
                    title = { Text("导入 ICS / JSON") },
                    text = { Text("请在文件浏览器中选择 .ics 或 .json 文件") },
                    confirmButton = {
                        TextButton(onClick = {
                            showImportDialog = false
                            importLauncher.launch(arrayOf("text/calendar", "application/json", "*/*"))
                        }) { Text("选择文件") }
                    },
                    dismissButton = { TextButton(onClick = { showImportDialog = false }) { Text("取消") } }
                )
            }
            if (showSearchDialog) {
                SearchDialog(
                    query = viewModel.searchQuery.collectAsState().value,
                    searchResults = viewModel.searchResults.collectAsState().value,
                    onDateSelected = { d -> selectedDate = d; showSearchDialog = false },
                    onQueryChanged = { viewModel.updateSearchQuery(it) },
                    onDismiss = { showSearchDialog = false }
                )
            }
            if (showMonthPicker) {
                MonthPickerDialog(
                    initYear = selYear,
                    initMonth = selMonth,
                    onConfirm = { y, m ->
                        selectedDate = String.format("%04d-%02d-01", y, m)
                        showMonthPicker = false
                    },
                    onDismiss = { showMonthPicker = false }
                )
            }
            if (showWeekPicker) {
                WeekPickerDialog(
                    initYear = selYear,
                    initWeek = selWeekNumber,
                    onConfirm = { y, w ->
                        val c = Calendar.getInstance()
                        c.clear()
                        c.set(Calendar.YEAR, y)
                        c.set(Calendar.WEEK_OF_YEAR, w)
                        c.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
                        selectedDate = DateUtils.formatGregorian(c)
                        showWeekPicker = false
                    },
                    onDismiss = { showWeekPicker = false }
                )
            }
            if (showDayPicker) {
                DayPickerDialog(
                    initDate = selectedDate,
                    onConfirm = { d -> selectedDate = d; showDayPicker = false },
                    onDismiss = { showDayPicker = false }
                )
            }
            if (showYearPicker) {
                YearPickerDialog(
                    initYear = selYear,
                    onConfirm = { y ->
                        selectedDate = String.format("%04d-%02d-01", y, selMonth)
                        showYearPicker = false
                    },
                    onDismiss = { showYearPicker = false }
                )
            }
        }

        // 自启动引导：在通知权限请求之后弹出
        if (showAutoStartDialog) {
            AutoStartDialog(
                onDismiss = { showAutoStartDialog = false }
            )
        }
    }
}

@Composable
private fun AutoStartDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val guide = remember { AutoStartHelper.getManufacturerGuide() }
    var suppress30d by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = {},
        title = { Text("将迷历加入开机后台自启动！") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text("为确保事件闹钟提醒准时触发、桌面插件正常更新，请将 迷历 加入系统权限白名单：")
                Spacer(Modifier.height(12.dp))
                Text("⚠️ 开机启动：允许系统开机后自动运行 迷历")
                Spacer(Modifier.height(4.dp))
                Text("⚠️ 后台自启动：允许 迷历 在后台常驻运行")
                Spacer(Modifier.height(12.dp))
                if (guide != null) {
                    Text(guide.guideHint)
                } else {
                    Text("请进入系统「设置 → 应用管理 → 迷历」，找到「自启动」或「后台管理」并允许。")
                    Text("不同厂商设置路径不同，可在「手机管家」或「安全中心」中搜索「自启动」相关设置。")
                }
                Spacer(Modifier.height(16.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = suppress30d,
                        onCheckedChange = { suppress30d = it }
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("确认后 30 天内不再弹出")
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (suppress30d) {
                    AppLaunchPrefs.markAllAsked(context)
                }
                onDismiss()
            }) {
                Text("确认")
            }
        }
    )
}

// ===== 选择器对话框 =====

@Composable
private fun MonthPickerDialog(initYear: Int, initMonth: Int, onConfirm: (Int, Int) -> Unit, onDismiss: () -> Unit) {
    var year by remember { mutableStateOf(initYear.toString()) }
    var month by remember { mutableStateOf(initMonth.toString()) }
    val yearVal = year.toIntOrNull() ?: initYear
    val monthVal = month.toIntOrNull()?.coerceIn(1, 12) ?: initMonth
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择年月") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { year = (yearVal - 1).toString() }) { Text("−") }
                    OutlinedTextField(
                        value = year, onValueChange = { if (it.all { c -> c.isDigit() } || it.isEmpty()) year = it },
                        modifier = Modifier.width(90.dp), singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = MaterialTheme.typography.titleMedium.copy(textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                    )
                    Text("年", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { year = (yearVal + 1).toString() }) { Text("+") }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { month = ((monthVal - 1).coerceAtLeast(1)).toString() }) { Text("−") }
                    OutlinedTextField(
                        value = month, onValueChange = { if (it.all { c -> c.isDigit() } || it.isEmpty()) month = it },
                        modifier = Modifier.width(70.dp), singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = MaterialTheme.typography.titleMedium.copy(textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                    )
                    Text("月", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { month = ((monthVal + 1).coerceAtMost(12)).toString() }) { Text("+") }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(yearVal, monthVal) }) { Text("确定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun WeekPickerDialog(initYear: Int, initWeek: Int, onConfirm: (Int, Int) -> Unit, onDismiss: () -> Unit) {
    var year by remember { mutableStateOf(initYear.toString()) }
    var week by remember { mutableStateOf(initWeek.toString()) }
    val yearVal = year.toIntOrNull() ?: initYear
    val weekVal = week.toIntOrNull()?.coerceIn(1, 52) ?: initWeek
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择年周") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { year = (yearVal - 1).toString() }) { Text("−") }
                    OutlinedTextField(
                        value = year, onValueChange = { if (it.all { c -> c.isDigit() } || it.isEmpty()) year = it },
                        modifier = Modifier.width(90.dp), singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = MaterialTheme.typography.titleMedium.copy(textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                    )
                    Text("年", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { year = (yearVal + 1).toString() }) { Text("+") }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { week = ((weekVal - 1).coerceAtLeast(1)).toString() }) { Text("−") }
                    OutlinedTextField(
                        value = week, onValueChange = { if (it.all { c -> c.isDigit() } || it.isEmpty()) week = it },
                        modifier = Modifier.width(80.dp), singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = MaterialTheme.typography.titleMedium.copy(textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                    )
                    Text("周", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { week = ((weekVal + 1).coerceAtMost(52)).toString() }) { Text("+") }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(yearVal, weekVal) }) { Text("确定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun DayPickerDialog(initDate: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    val init = DateUtils.parseGregorian(initDate)
    var year by remember { mutableStateOf(init.get(Calendar.YEAR).toString()) }
    var month by remember { mutableStateOf((init.get(Calendar.MONTH) + 1).toString()) }
    var day by remember { mutableStateOf(init.get(Calendar.DAY_OF_MONTH).toString()) }
    val yearVal = year.toIntOrNull() ?: init.get(Calendar.YEAR)
    val monthVal = month.toIntOrNull()?.coerceIn(1, 12) ?: (init.get(Calendar.MONTH) + 1)
    val maxDay = remember(yearVal, monthVal) { DateUtils.getDaysInMonth(yearVal, monthVal) }
    val dayVal = day.toIntOrNull()?.coerceIn(1, maxDay) ?: init.get(Calendar.DAY_OF_MONTH).coerceAtMost(maxDay)
    LaunchedEffect(maxDay) {
        val d = day.toIntOrNull() ?: return@LaunchedEffect
        if (d > maxDay) day = maxDay.toString()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择日期") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { year = (yearVal - 1).toString() }) { Text("−") }
                    OutlinedTextField(
                        value = year, onValueChange = { if (it.all { c -> c.isDigit() } || it.isEmpty()) year = it },
                        modifier = Modifier.width(80.dp), singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = MaterialTheme.typography.titleMedium.copy(textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                    )
                    Text("年", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { year = (yearVal + 1).toString() }) { Text("+") }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { month = ((monthVal - 1).coerceAtLeast(1)).toString() }) { Text("−") }
                    OutlinedTextField(
                        value = month, onValueChange = { if (it.all { c -> c.isDigit() } || it.isEmpty()) month = it },
                        modifier = Modifier.width(60.dp), singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = MaterialTheme.typography.titleMedium.copy(textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                    )
                    Text("月", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { month = ((monthVal + 1).coerceAtMost(12)).toString() }) { Text("+") }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { day = ((dayVal - 1).coerceAtLeast(1)).toString() }) { Text("−") }
                    OutlinedTextField(
                        value = day, onValueChange = { if (it.all { c -> c.isDigit() } || it.isEmpty()) day = it },
                        modifier = Modifier.width(60.dp), singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = MaterialTheme.typography.titleMedium.copy(textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                    )
                    Text("日", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { day = ((dayVal + 1).coerceAtMost(maxDay)).toString() }) { Text("+") }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(String.format("%04d-%02d-%02d", yearVal, monthVal, dayVal)) }) { Text("确定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun YearPickerDialog(initYear: Int, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    var year by remember { mutableStateOf(initYear.toString()) }
    val yearVal = year.toIntOrNull() ?: initYear
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择年份") },
        text = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { year = (yearVal - 1).toString() }) { Text("−") }
                OutlinedTextField(
                    value = year, onValueChange = { if (it.all { c -> c.isDigit() } || it.isEmpty()) year = it },
                    modifier = Modifier.width(90.dp), singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = MaterialTheme.typography.titleMedium.copy(textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                )
                Text("年", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                TextButton(onClick = { year = (yearVal + 1).toString() }) { Text("+") }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(yearVal) }) { Text("确定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun SearchDialog(
    query: String,
    searchResults: List<com.minirili.app.database.entity.EventEntity>,
    onDateSelected: (String) -> Unit,
    onQueryChanged: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("搜索事件") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = query, onValueChange = onQueryChanged, label = { Text("输入关键词") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (query.isNotBlank()) {
                    if (searchResults.isEmpty()) {
                        Text("未找到匹配事件", style = MaterialTheme.typography.bodySmall)
                    } else {
                        searchResults.take(10).forEach { event ->
                            TextButton(onClick = { onDateSelected(event.gregorianDate) }, modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(event.title, style = MaterialTheme.typography.bodyMedium)
                                    Text("${event.gregorianDate} · ${event.type}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

// ===== 视图渲染 =====

@Composable
private fun renderMonthView(
    selectedDate: String,
    allEvents: List<com.minirili.app.database.entity.EventEntity>,
    onDateSelect: (String) -> Unit,
    eventClick: (Long) -> Unit,
    doubleTapToDayView: (String) -> Unit,
    onOpenWeatherPage: () -> Unit,
    onMoveEvent: ((Long, Boolean) -> Unit)? = null
) {
    val calendar = DateUtils.parseGregorian(selectedDate)
    val year = calendar.get(Calendar.YEAR)
    val month = calendar.get(Calendar.MONTH) + 1
    val daysInMonth = DateUtils.getDaysInMonth(year, month)
    val today = DateUtils.today()
    val firstDayOffset = Calendar.getInstance().apply { set(year, month - 1, 1) }.get(Calendar.DAY_OF_WEEK) - 1

    // 一次性预计算事件分组 + 每个日期的农历/节气，避免每个细胞重复遍历
    val (eventByDate, cellInfos) = remember(selectedDate, allEvents) {
        val mutableMap = mutableMapOf<String, List<com.minirili.app.database.entity.EventEntity>>()
        for (ev in allEvents) {
            mutableMap[ev.gregorianDate] = (mutableMap[ev.gregorianDate] ?: emptyList()) + ev
        }
        val infos = (1..daysInMonth).map { day ->
            val c = Calendar.getInstance().apply { set(year, month - 1, day) }
            val dateStr = DateUtils.formatGregorian(c)
            val cellLunar = LunarCalendar.getLunarDayLabel(c)
            val term = LunarCalendar.getSolarTerm(c)
            val holiday = HolidayService.getHolidayName(dateStr)
            CellLunarInfo(cellLunar, term, holiday)
        }
        Pair(mutableMap, infos)
    }

    val selectedDayEvents = eventByDate[selectedDate] ?: emptyList()

    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf("日", "一", "二", "三", "四", "五", "六").forEach { d ->
                Text(d, style = MaterialTheme.typography.labelMedium,
                    color = if (d == "日" || d == "六") Color.Red else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(horizontal = 4.dp)) {
            var dayCounter = 1
            val totalCells = firstDayOffset + daysInMonth
            val rows = (totalCells + 6) / 7
            for (row in 0 until rows) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    for (col in 0..6) {
                        val cellIndex = row * 7 + col
                        if (cellIndex < firstDayOffset || dayCounter > daysInMonth) {
                            Spacer(Modifier.weight(1f).height(72.dp))
                        } else {
                            val day = dayCounter
                            val currentDate = Calendar.getInstance().apply { set(year, month - 1, day) }
                            val dateStr = DateUtils.formatGregorian(currentDate)
                            val isToday = dateStr == today
                            val isSelected = dateStr == selectedDate
                            val dayEvents = eventByDate[dateStr] ?: emptyList()
                            val lunarInfo = cellInfos[day - 1]
                            DayCard(day = day, isToday = isToday, isSelected = isSelected,
                                lunarDay = lunarInfo.dayLabel, solarTerm = lunarInfo.termLabel, holidayName = lunarInfo.holidayName,
                                events = dayEvents, onClick = { onDateSelect(dateStr) },
                                onDoubleClick = { doubleTapToDayView(dateStr) })
                            dayCounter++
                        }
                    }
                }
            }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
        WeatherCard(
            selectedDate = selectedDate,
            onOpenWeatherPage = onOpenWeatherPage
        )
        SelectedDateEventsSection(
                            selectedDate = selectedDate,
                            events = selectedDayEvents,
                            onEventClick = eventClick,
                            onMoveEvent = onMoveEvent
                        )
    }
}

private data class CellLunarInfo(val dayLabel: String, val termLabel: String?, val holidayName: String?)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RowScope.DayCard(
    day: Int, isToday: Boolean, isSelected: Boolean, lunarDay: String, solarTerm: String?, holidayName: String?,
    events: List<com.minirili.app.database.entity.EventEntity>,
    onClick: () -> Unit, onDoubleClick: () -> Unit
) {
    val eventColor = events.firstOrNull { it.color != 0 }?.color
    val bg = when {
        isSelected -> MaterialTheme.colorScheme.primary
        isToday -> MaterialTheme.colorScheme.primaryContainer
        else -> Color.Transparent
    }
    val fg = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier.weight(1f).height(72.dp).padding(2.dp)
    ) {
        // 事件颜色条
        if (eventColor != null) {
            Box(
                Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(eventColor.toLong() or 0xFF000000L))
            )
        }
        Column(
            Modifier.weight(1f).fillMaxHeight()
                .clip(RoundedCornerShape(8.dp)).background(bg)
                .combinedClickable(onClick = onClick, onDoubleClick = onDoubleClick)
                .padding(start = if (eventColor != null) 4.dp else 4.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(day.toString(), style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal, color = fg)
                val labelColor = when {
                    isSelected -> fg.copy(alpha = 0.85f)
                    holidayName != null || solarTerm != null -> Color.Red
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Text(holidayName ?: solarTerm ?: lunarDay, style = MaterialTheme.typography.labelSmall, fontSize = 9.sp,
                    color = labelColor)
            }
            events.take(2).forEach { ev ->
                val titleColor = if (isSelected) fg.copy(alpha = 0.9f)
                    else if (ev.color != 0) Color(ev.color.toLong() or 0xFF000000L)
                    else MaterialTheme.colorScheme.primary
                val timePrefix = if (ev.reminderTime > 0) {
                    runCatching {
                        java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                            .format(java.util.Date(ev.reminderTime)) + " "
                    }.getOrDefault("")
                } else ""
                Text("$timePrefix${ev.title}", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    color = titleColor, modifier = Modifier.fillMaxWidth())
            }
            if (events.size > 2) Text("·${events.size}", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = fg)
        }
    }
}

@Composable
private fun SelectedDateEventsSection(
    selectedDate: String,
    events: List<com.minirili.app.database.entity.EventEntity>,
    onEventClick: (Long) -> Unit = {},
    onMoveEvent: ((Long, Boolean) -> Unit)? = null  // true=上移, false=下移
) {
    val lunarStr = runCatching {
        LunarCalendar.getLunarMonthDayName(DateUtils.parseGregorian(selectedDate))
    }.getOrDefault("")
    val holidayName = HolidayService.getHolidayName(selectedDate)

    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("$selectedDate 记事", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (holidayName != null) Text(holidayName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold,
                    color = Color.Red, modifier = Modifier.padding(end = 8.dp))
                if (lunarStr.isNotEmpty()) Text(lunarStr, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            }
        }
        Spacer(Modifier.height(6.dp))
        if (events.isEmpty()) {
            Text("当日暂无记事", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            events.forEachIndexed { index, ev ->
                Card(Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable { onEventClick(ev.id) },
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 0.dp, bottom = 0.dp, end = 10.dp),
                        verticalAlignment = Alignment.Top) {
                        if (ev.color != 0) {
                            Box(Modifier.width(4.dp).height(64.dp).padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color(ev.color.toLong() or 0xFF000000L)))
                        }
                        Column(Modifier.weight(1f).padding(top = 10.dp, bottom = 10.dp, end = 10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val timeColor = when {
                                    ev.completed -> MaterialTheme.colorScheme.onSurfaceVariant
                                    ev.color != 0 -> Color(ev.color.toLong() or 0xFF000000L)
                                    else -> MaterialTheme.colorScheme.primary
                                }
                                Text(formatEventTime(ev.reminderTime), style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold, color = timeColor)
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    if (ev.completed) "✓ ${ev.title}" else ev.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = if (ev.completed) MaterialTheme.colorScheme.onSurfaceVariant
                                            else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            if (ev.description.isNotBlank()) {
                                Text(ev.description, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2,
                                    modifier = Modifier.padding(top = 2.dp))
                            }
                            if (ev.tags.isNotBlank()) {
                                val tagList = ev.tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    tagList.take(4).forEach { tag ->
                                        Box(Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(MaterialTheme.colorScheme.primaryContainer)
                                            .padding(horizontal = 6.dp, vertical = 2.dp)) {
                                            Text(tag, style = MaterialTheme.typography.labelSmall, fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer)
                                        }
                                    }
                                }
                            }
                        }
                        // UI-04: 上移/下移按钮
                        if (onMoveEvent != null && events.size > 1) {
                            Column(verticalArrangement = Arrangement.Center) {
                                IconButton(
                                    onClick = { onMoveEvent(ev.id, true) },
                                    enabled = index > 0,
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.KeyboardArrowUp, "上移", modifier = Modifier.size(18.dp))
                                }
                                IconButton(
                                    onClick = { onMoveEvent(ev.id, false) },
                                    enabled = index < events.size - 1,
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.KeyboardArrowDown, "下移", modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatEventTime(reminderTime: Long): String {
    if (reminderTime <= 0) return "全天"
    return try {
        java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(reminderTime))
    } catch (e: Exception) { "全天" }
}

@Composable
private fun renderWeekView(
    selectedDate: String,
    allEvents: List<com.minirili.app.database.entity.EventEntity>,
    onDateSelect: (String) -> Unit,
    eventClick: (Long) -> Unit,
    doubleTapToDayView: (String) -> Unit,
    onOpenWeatherPage: () -> Unit,
    onMoveEvent: ((Long, Boolean) -> Unit)? = null
) {
    val calendar = DateUtils.parseGregorian(selectedDate)
    val startOfWeek = getStartOfWeek(calendar)
    val weekDates = (0..6).map {
        val c = startOfWeek.clone() as Calendar
        c.add(Calendar.DAY_OF_MONTH, it)
        DateUtils.formatGregorian(c)
    }
    val selectedDayEvents = allEvents.filter { it.gregorianDate == selectedDate }

    val eventByDate = remember(selectedDate, allEvents) {
        buildMap<String, List<com.minirili.app.database.entity.EventEntity>> {
            for (ev in allEvents) put(ev.gregorianDate, (get(ev.gregorianDate) ?: emptyList()) + ev)
        }
    }

    Column {
        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            weekDates.forEach { dateStr ->
                val dayEvents = eventByDate[dateStr] ?: emptyList()
                val holidayName = HolidayService.getHolidayName(dateStr)
                WeekDayCard(
                    dateStr = dateStr,
                    today = DateUtils.today(),
                    lunarText = runCatching { LunarCalendar.getLunarMonthDayName(DateUtils.parseGregorian(dateStr)) }.getOrDefault(""),
                    holidayName = holidayName,
                    eventCount = dayEvents.size,
                    isSelected = dateStr == selectedDate,
                    onSelect = { onDateSelect(dateStr) },
                    onDoubleClick = { doubleTapToDayView(dateStr) }
                )
            }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
        WeatherCard(
            selectedDate = selectedDate,
            onOpenWeatherPage = onOpenWeatherPage
        )
        SelectedDateEventsSection(
                            selectedDate = selectedDate,
                            events = selectedDayEvents,
                            onEventClick = eventClick,
                            onMoveEvent = onMoveEvent
                        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RowScope.WeekDayCard(
    dateStr: String, today: String, lunarText: String, holidayName: String?,
    eventCount: Int, isSelected: Boolean,
    onSelect: () -> Unit, onDoubleClick: () -> Unit
) {
    val cal = DateUtils.parseGregorian(dateStr)
    val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
    val isWeekend = dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY
    Card(
        modifier = Modifier.weight(1f).height(76.dp).padding(2.dp)
            .combinedClickable(onClick = onSelect, onDoubleClick = onDoubleClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
            else if (dateStr == today) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.fillMaxSize().padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(DateUtils.getWeekdayShort(dayOfWeek), style = MaterialTheme.typography.labelSmall,
                color = if (isWeekend) Color.Red else Color.Unspecified, fontWeight = FontWeight.Bold)
            Text(dateStr.substring(8, 10), style = MaterialTheme.typography.titleSmall,
                fontWeight = if (dateStr == today || isSelected) FontWeight.Bold else FontWeight.Normal)
            if (lunarText.isNotEmpty()) Text(lunarText, style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            if (holidayName != null) Text(holidayName, style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = Color.Red, maxLines = 1)
            if (eventCount > 0) Text("$eventCount 事件", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun renderDayView(
    selectedDate: String,
    currentEvents: List<com.minirili.app.database.entity.EventEntity>,
    allEvents: List<com.minirili.app.database.entity.EventEntity>,
    onEventClick: (Long) -> Unit,
    onOpenWeatherPage: () -> Unit,
    onMoveEvent: ((Long, Boolean) -> Unit)? = null
) {
    val events = currentEvents.filter { it.gregorianDate == selectedDate }
        .ifEmpty { allEvents.filter { it.gregorianDate == selectedDate } }
    val lunarText = runCatching {
        LunarCalendar.getLunarMonthDayName(DateUtils.parseGregorian(selectedDate))
    }.getOrDefault("")
    val holidayName = HolidayService.getHolidayName(selectedDate)

    Column {
        Card(Modifier.fillMaxWidth().padding(12.dp).height(80.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (selectedDate == DateUtils.today()) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant)) {
            Row(Modifier.fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(selectedDate, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    val dow = DateUtils.parseGregorian(selectedDate).get(Calendar.DAY_OF_WEEK)
                    val subtitle = buildString {
                        append(DateUtils.getWeekdayFull(dow))
                        if (lunarText.isNotEmpty()) append(" · $lunarText")
                        if (holidayName != null) append(" · $holidayName")
                    }
                    Text(subtitle,
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                Icon(Icons.Default.DateRange, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            }
        }
        WeatherCard(
            selectedDate = selectedDate,
            onOpenWeatherPage = onOpenWeatherPage
        )
        SelectedDateEventsSection(
                            selectedDate = selectedDate,
                            events = events,
                            onEventClick = onEventClick,
                            onMoveEvent = onMoveEvent
                        )
    }
}

@Composable
private fun renderYearView(
    selectedDate: String,
    onDayClick: (String) -> Unit,
    onDayDoubleClick: (String) -> Unit,
    onMonthClick: (Int) -> Unit
) {
    val year = DateUtils.parseGregorian(selectedDate).get(Calendar.YEAR)
    val today = DateUtils.today()
    val todayMonth = DateUtils.parseGregorian(today).get(Calendar.MONTH) + 1
    val listState = rememberLazyListState()
    LaunchedEffect(year) {
        if (todayMonth > 1) listState.scrollToItem(todayMonth - 1)
    }

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        for (month in 1..12) {
            item {
                val daysInMonth = DateUtils.getDaysInMonth(year, month)
                val firstDay = Calendar.getInstance().apply { set(year, month - 1, 1) }.get(Calendar.DAY_OF_WEEK) - 1
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(8.dp)) {
                        Text("${month}月", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { onMonthClick(month) })
                        Spacer(Modifier.height(4.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            listOf("日","一","二","三","四","五","六").forEach { d -> Text(d, style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center) }
                        }
                        var dayCounter = 1
                        val totalCells = firstDay + daysInMonth
                        val rows = (totalCells + 6) / 7
                        for (row in 0 until rows) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                for (col in 0..6) {
                                    val cellIndex = row * 7 + col
                                    if (cellIndex < firstDay || dayCounter > daysInMonth) {
                                        Spacer(Modifier.size(28.dp))
                                    } else {
                                        val day = dayCounter
                                        val dateStr = String.format("%04d-%02d-%02d", year, month, day)
                                        val isToday = dateStr == today
                                        val cal = Calendar.getInstance().apply { set(year, month - 1, day) }
                                        val solarTerm = LunarCalendar.getSolarTerm(cal)
                                        val lunarDay = LunarCalendar.getLunarDayLabel(cal)
                                        val holidayName = HolidayService.getHolidayName(dateStr)
                                        YearDayCell(
                                            day = day,
                                            isToday = isToday,
                                            solarTerm = solarTerm,
                                            lunarDay = lunarDay,
                                            holidayName = holidayName,
                                            onClick = { onDayClick(dateStr) },
                                            onDoubleClick = { onDayDoubleClick(dateStr) }
                                        )
                                        dayCounter++
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RowScope.YearDayCell(
    day: Int,
    isToday: Boolean,
    solarTerm: String?,
    lunarDay: String,
    holidayName: String?,
    onClick: () -> Unit,
    onDoubleClick: () -> Unit
) {
    val cellSize = 28.dp
    Column(
        Modifier.size(cellSize)
            .combinedClickable(onClick = onClick, onDoubleClick = onDoubleClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            day.toString(),
            style = MaterialTheme.typography.labelSmall, fontSize = 9.sp,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            color = if (isToday) Color.Red else Color.Unspecified,
            textAlign = TextAlign.Center
        )
        Text(
            holidayName ?: solarTerm ?: lunarDay,
            style = MaterialTheme.typography.labelSmall, fontSize = 6.sp,
            lineHeight = 8.sp,
            maxLines = 1,
            color = if (holidayName != null || solarTerm != null) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

private fun getStartOfWeek(calendar: Calendar): Calendar {
    val startOfWeek = calendar.clone() as Calendar
    startOfWeek.add(Calendar.DAY_OF_WEEK, -((startOfWeek.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY + 7) % 7))
    return startOfWeek
}

private fun getWeekNumber(dateStr: String): Int {
    val calendar = DateUtils.parseGregorian(dateStr)
    calendar.firstDayOfWeek = Calendar.SUNDAY
    return calendar.get(Calendar.WEEK_OF_YEAR)
}

/** 左右滑动手势修饰器：左滑=下一页/期，右滑=上一页/期 */
@Composable
private fun Modifier.swipeNav(
    threshold: Float = 80f,
    onPrev: () -> Unit,
    onNext: () -> Unit
): Modifier = this.then(
    Modifier.pointerInput(Unit) {
        var totalDrag = 0f
        detectHorizontalDragGestures(
            onDragStart = { totalDrag = 0f },
            onDragEnd = {
                if (totalDrag < -threshold) onNext()
                else if (totalDrag > threshold) onPrev()
                totalDrag = 0f
            },
            onDragCancel = { totalDrag = 0f },
            onHorizontalDrag = { _, delta -> totalDrag += delta }
        )
    }
)

private fun shiftMonth(dateStr: String, delta: Int): String {
    val c = DateUtils.parseGregorian(dateStr)
    c.add(Calendar.MONTH, delta)
    return DateUtils.formatGregorian(c)
}

private fun shiftDays(dateStr: String, delta: Int): String {
    val c = DateUtils.parseGregorian(dateStr)
    c.add(Calendar.DAY_OF_MONTH, delta)
    return DateUtils.formatGregorian(c)
}

private fun shiftYear(dateStr: String, delta: Int): String {
    val c = DateUtils.parseGregorian(dateStr)
    c.add(Calendar.YEAR, delta)
    return DateUtils.formatGregorian(c)
}

object JsonUtils {
    fun generateJson(events: List<com.minirili.app.database.entity.EventEntity>): String {
        val sb = StringBuilder()
        sb.appendLine("[")
        events.forEachIndexed { index, event ->
            sb.appendLine("  {")
            sb.appendLine("    \"id\": ${event.id},")
            sb.appendLine("    \"title\": \"${escapeJson(event.title)}\",")
            sb.appendLine("    \"description\": \"${escapeJson(event.description)}\",")
            sb.appendLine("    \"type\": \"${escapeJson(event.type)}\",")
            sb.appendLine("    \"tags\": \"${escapeJson(event.tags)}\",")
            sb.appendLine("    \"color\": ${event.color},")
            sb.appendLine("    \"priority\": ${event.priority},")
            sb.appendLine("    \"completed\": ${event.completed},")
            sb.appendLine("    \"gregorianDate\": \"${event.gregorianDate}\",")
            sb.appendLine("    \"lunarDate\": \"${event.lunarDate}\",")
            sb.appendLine("    \"useLunar\": ${event.useLunar},")
            sb.appendLine("    \"reminderTime\": ${event.reminderTime},")
            sb.appendLine("    \"reminderOffset\": ${event.reminderOffset},")
            sb.appendLine("    \"repeatType\": \"${event.repeatType}\",")
            sb.appendLine("    \"skipDates\": \"${event.skipDates}\",")
            sb.appendLine("    \"notifyNotification\": ${event.notifyNotification},")
            sb.appendLine("    \"notifyAlarm\": ${event.notifyAlarm},")
            sb.appendLine("    \"sortOrder\": ${event.sortOrder},")
            sb.appendLine("    \"attachments\": \"${escapeJson(event.attachments)}\",")
            sb.appendLine("    \"createdAt\": ${event.createdAt},")
            sb.appendLine("    \"updatedAt\": ${event.updatedAt}")
            sb.append("  }")
            if (index < events.size - 1) sb.append(",")
            sb.appendLine()
        }
        sb.appendLine("]")
        return sb.toString()
    }

    fun parseJson(jsonContent: String): List<com.minirili.app.database.entity.EventEntity> {
        val events = mutableListOf<com.minirili.app.database.entity.EventEntity>()
        try {
            val lines = jsonContent.lines()
            var currentMap = mutableMapOf<String, String>()
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed == "{") currentMap.clear()
                else if (trimmed == "}," || trimmed == "}") {
                    if (currentMap.isNotEmpty()) events.add(parseEventFromMap(currentMap))
                } else if (trimmed.contains("\":\"")) {
                    val parts = trimmed.split("\":\"", limit = 2)
                    val key = parts[0].removePrefix("\"").trim()
                    val value = parts[1].removeSuffix("\",").removeSuffix("\"").trim()
                    currentMap[key] = value
                } else if (trimmed.contains("\": ")) {
                    val colonIdx = trimmed.indexOf("\": ")
                    val key = trimmed.substring(0, colonIdx).removePrefix("\"").trim()
                    val value = trimmed.substring(colonIdx + 3).removeSuffix(",").trim()
                    currentMap[key] = value
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        return events
    }

    private fun parseEventFromMap(map: Map<String, String>) = com.minirili.app.database.entity.EventEntity(
        title = map["title"] ?: "",
        description = map["description"] ?: "",
        type = map["type"] ?: "普通",
        tags = map["tags"] ?: "",
        color = map["color"]?.toIntOrNull() ?: 0,
        priority = map["priority"]?.toIntOrNull() ?: 1,
        completed = map["completed"]?.toBoolean() ?: false,
        gregorianDate = map["gregorianDate"] ?: DateUtils.today(),
        lunarDate = map["lunarDate"] ?: "",
        useLunar = map["useLunar"]?.toBoolean() ?: false,
        reminderTime = map["reminderTime"]?.toLongOrNull() ?: 0L,
        reminderOffset = map["reminderOffset"]?.toIntOrNull() ?: 0,
        repeatType = map["repeatType"] ?: "none",
        skipDates = map["skipDates"] ?: "",
        notifyNotification = map["notifyNotification"]?.toBoolean() ?: true,
        notifyAlarm = map["notifyAlarm"]?.toBoolean() ?: true,
        sortOrder = map["sortOrder"]?.toLongOrNull() ?: 0L,
        attachments = map["attachments"] ?: "",
        createdAt = map["createdAt"]?.toLongOrNull() ?: System.currentTimeMillis(),
        updatedAt = map["updatedAt"]?.toLongOrNull() ?: System.currentTimeMillis()
    )

    private fun escapeJson(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"")
}
