package com.minirili.app.ui.screens.weather

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.minirili.app.data.weather.City
import com.minirili.app.data.weather.WeatherCode
import com.minirili.app.ui.viewmodel.WeatherUiState
import com.minirili.app.ui.viewmodel.WeatherViewModel
import com.minirili.app.utils.TravelAdvicePrefs
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 天气详情页（单页聚合布局）。
 *
 * MVP 覆盖：定位权限请求 / 当前天气 / 24h 预报 / 15 天预报 / 日出日落。
 * v1.3 补充：多城市管理（城市切换 / 添加 / 删除）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherScreen(
    navController: NavHostController,
    viewModel: WeatherViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val city by viewModel.currentCity.collectAsState()
    val cities by viewModel.cities.collectAsState()
    val usingLocation by viewModel.usingCurrentLocation.collectAsState()
    val aqi by viewModel.aqi.collectAsState()

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    var showSearchDialog by remember { mutableStateOf(false) }
    var deleteConfirmCity by remember { mutableStateOf<City?>(null) }
    var showTravelAdviceSettings by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result.values.any { it }
        if (granted && !hasPermission) {
            hasPermission = true
            viewModel.refreshLocation()
        }
    }

    LaunchedEffect(Unit) {
        if (hasPermission) {
            viewModel.start()
        }
    }

    if (showSearchDialog) {
        CitySearchDialog(
            viewModel = viewModel,
            currentCityIds = cities.map { it.id }.toSet(),
            onDismiss = { showSearchDialog = false }
        )
    }

    deleteConfirmCity?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteConfirmCity = null },
            title = { Text("删除城市") },
            text = { Text("确定要删除「${target.name}」吗？") },
            confirmButton = {
                Button(onClick = {
                    viewModel.removeCity(target.id)
                    deleteConfirmCity = null
                }) { Text("删除") }
            },
            dismissButton = {
                OutlinedButton(onClick = { deleteConfirmCity = null }) { Text("取消") }
            }
        )
    }

    if (showTravelAdviceSettings) {
        TravelAdviceSettingsDialog(
            onDismiss = { showTravelAdviceSettings = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshLocation() }) {
                        Icon(Icons.Default.MyLocation, contentDescription = "重新定位")
                    }
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 城市切换行
            CityChipRow(
                cities = cities,
                currentCityId = city?.id,
                onSelectCity = { viewModel.selectCity(it) },
                onAddCity = { showSearchDialog = true },
                onDeleteCity = { if (cities.size > 1) deleteConfirmCity = it }
            )

            // Bug4: 首次进入 — 用户既未授权定位、也没有手动添加城市时，显示引导卡片
            LaunchedEffect(hasPermission, cities, usingLocation) { /* trigger recomp */ }
            if (!hasPermission && cities.isEmpty()) {
                FirstRunGuideCard(
                    onEnableLocation = {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    },
                    onAddCity = { showSearchDialog = true }
                )
            }

            when (val s = state) {
                is WeatherUiState.Loading -> {
                    Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is WeatherUiState.Ready -> {
                    LocationBanner(
                        cityName = s.data.city.name,
                        usingLocation = usingLocation,
                        hasPermission = hasPermission,
                        onRequestPermission = {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    )
                    CurrentWeatherCard(s.data)
                    TravelAdviceCard(s.data, onOpenSettings = { showTravelAdviceSettings = true })
                    TwentyFourHourCard(s.data)
                    SunMoonCard(s.data.daily.firstOrNull())
                    AQICard(aqi)
                    LifeIndexCard(s.data)
                    FifteenDayCard(s.data)
                }
                is WeatherUiState.Error -> {
                    if (!hasPermission) {
                        PermissionRequestCard(onRequest = {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        })
                    }
                    Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        Text("加载失败: ${s.error.message}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun CityChipRow(
    cities: List<City>,
    currentCityId: String?,
    onSelectCity: (City) -> Unit,
    onAddCity: () -> Unit,
    onDeleteCity: (City) -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(cities) { c ->
                FilterChip(
                    selected = c.id == currentCityId,
                    onClick = { onSelectCity(c) },
                    label = { Text(c.name, maxLines = 1) },
                    trailingIcon = if (c.id != currentCityId && cities.size > 1) {
                        { Icon(Icons.Default.Close, "删除", Modifier.size(14.dp).clickable { onDeleteCity(c) }) }
                    } else null
                )
            }
        }
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clickable { onAddCity() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Add, "添加城市", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
private fun LocationBanner(
    cityName: String,
    usingLocation: Boolean,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit
) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(4.dp))
                Column {
                    Text(cityName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    // 仅在「使用定位」且「有权限」时显示「当前位置」副标题；
                    // 用户手动添加的城市不显示任何后缀（Bug9 要求）
                    val subtitle = when {
                        usingLocation && hasPermission -> "当前位置"
                        usingLocation && !hasPermission -> "无定位权限"
                        else -> ""  // 手动添加的城市不标注"默认城市"
                    }
                    if (subtitle.isNotEmpty()) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (usingLocation && !hasPermission) {
                OutlinedButton(onClick = onRequestPermission) {
                    Text("允许定位")
                }
            }
        }
    }
}

@Composable
private fun PermissionRequestCard(onRequest: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("开启定位以获取您所在城市的天气", style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onRequest) { Text("开启定位权限") }
        }
    }
}

@Composable
private fun FirstRunGuideCard(
    onEnableLocation: () -> Unit,
    onAddCity: () -> Unit
) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("获取您所在地的天气", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "点击右上角「定位」按钮授权获取当前位置；或手动添加您关心的城市。",
                style = MaterialTheme.typography.bodyMedium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onEnableLocation) {
                    Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("允许定位")
                }
                OutlinedButton(onClick = onAddCity) { Text("添加城市") }
            }
        }
    }
}

@Composable
private fun CurrentWeatherCard(data: com.minirili.app.data.weather.WeatherResult.ForDate) {
    val cur = data.current
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(16.dp)) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Top) {
                Text(WeatherCode.icon(cur.weatherCode, cur.isDay), fontSize = 56.sp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("${cur.temperature.toInt()}°", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                    Text(WeatherCode.description(cur.weatherCode), style = MaterialTheme.typography.bodyMedium)
                    Text("体感 ${cur.apparentTemperature.toInt()}° · 湿度 ${cur.humidity}%")
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "风速 ${cur.windSpeed.toInt()} km/h · 气压 ${cur.pressure.toInt()} hPa",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun AQICard(aqi: com.minirili.app.data.weather.AQIData?) {
    if (aqi == null) return
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("空气质量", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                aqiItem("PM2.5", aqi.pm25, "μg/m³")
                aqiItem("PM10", aqi.pm10, "μg/m³")
                aqiItem("O₃", aqi.ozone, "μg/m³")
                aqiItem("NO₂", aqi.nitrogenDioxide, "μg/m³")
            }
            Text(
                aqi.level,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = when (aqi.level) {
                    "优" -> Color(0xFF4CAF50)
                    "良" -> Color(0xFF8BC34A)
                    "轻度污染" -> Color(0xFFFFC107)
                    "中度污染" -> Color(0xFFFF9800)
                    "重度污染" -> Color(0xFFF44336)
                    "严重污染" -> Color(0xFF880E4F)
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
        }
    }
}

@Composable
private fun aqiItem(label: String, value: Double?, unit: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value?.let { "${it.toInt()}" } ?: "—",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun LifeIndexCard(data: com.minirili.app.data.weather.WeatherResult.ForDate) {
    val cur = data.current
    val daily = data.daily.firstOrNull()

    // 本地规则引擎计算生活指数
    data class Index(val name: String, val level: String, val desc: String)

    val indices = remember(cur, daily) {
        mutableListOf<Index>().apply {
            // 紫外线指数（基于天气码+是否为白天）
            if (cur.isDay) {
                when {
                    cur.weatherCode == 0 || cur.weatherCode == 1 -> add(Index("紫外线", "强", "注意防晒"))
                    cur.weatherCode == 2 -> add(Index("紫外线", "中等", "适当防护"))
                    else -> add(Index("紫外线", "弱", "无需防护"))
                }
            } else {
                add(Index("紫外线", "无", "夜间无紫外线"))
            }

            // 穿衣指数（基于温度）
            val t = cur.temperature
            when {
                t <= 0 -> add(Index("穿衣", "极寒", "羽绒服+保暖内衣"))
                t <= 10 -> add(Index("穿衣", "冷", "厚外套/棉衣"))
                t <= 20 -> add(Index("穿衣", "舒适", "薄外套/长袖"))
                t <= 28 -> add(Index("穿衣", "温暖", "短袖/薄衫"))
                else -> add(Index("穿衣", "炎热", "短袖+防晒"))
            }

            // 洗车指数（基于降水概率）
            val prec = daily?.precipitationProbabilityMax ?: 0
            when {
                prec >= 60 -> add(Index("洗车", "不宜", "有降水，洗车白费"))
                prec >= 30 -> add(Index("洗车", "谨慎", "可能有降水"))
                else -> add(Index("洗车", "适宜", "近期无降水"))
            }

            // 运动指数（基于温度+天气）
            val badWeather = cur.weatherCode in 45..99
            when {
                badWeather -> add(Index("运动", "不宜", "天气不佳，建议室内运动"))
                t in 15.0..28.0 -> add(Index("运动", "适宜", "温度舒适，适合户外运动"))
                t in 5.0..35.0 -> add(Index("运动", "一般", "适度运动，注意补水"))
                else -> add(Index("运动", "不宜", "极端温度，避免剧烈运动"))
            }

            // 感冒指数（基于温差）
            val tempDiff = daily?.let { it.tempMax - it.tempMin } ?: 0.0
            when {
                tempDiff >= 15 -> add(Index("感冒", "易发", "昼夜温差大，注意保暖"))
                cur.temperature <= 5 -> add(Index("感冒", "易发", "气温低，注意防寒"))
                tempDiff >= 8 -> add(Index("感冒", "可能", "温差较大，体弱者注意"))
                else -> add(Index("感冒", "低", "感冒风险较低"))
            }

            // 洗晒指数（基于天气码+降水概率）
            when {
                cur.weatherCode in 45..99 || prec >= 50 -> add(Index("洗晒", "不宜", "有降水/阴天，不宜晾晒"))
                cur.weatherCode in 0..1 && prec < 20 -> add(Index("洗晒", "适宜", "晴好天气，适合晾晒"))
                else -> add(Index("洗晒", "一般", "可以晾晒，注意天气变化"))
            }
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text("生活指数", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            indices.chunked(3).forEach { row ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    row.forEach { idx ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                            Text(idx.name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(idx.level, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            Text(idx.desc, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TravelAdviceCard(
    data: com.minirili.app.data.weather.WeatherResult.ForDate,
    onOpenSettings: () -> Unit
) {
    val advice = com.minirili.app.utils.TravelAdviceEngine.getAdvice(data.current, data.daily.firstOrNull())
    if (advice.isEmpty()) return

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("出行建议", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                IconButton(onClick = onOpenSettings, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Settings, contentDescription = "出行建议提醒设置", modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            advice.forEach { item ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text("• ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    Text(item, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun TwentyFourHourCard(data: com.minirili.app.data.weather.WeatherResult.ForDate) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text("24 小时预报", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(data.hourly.take(24)) { h ->
                    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(sdf.format(Date(h.timeMillis)), style = MaterialTheme.typography.labelSmall)
                        Text(WeatherCode.icon(h.weatherCode), fontSize = 22.sp)
                        Text("${h.temperature.toInt()}°", style = MaterialTheme.typography.bodyMedium)
                        if (h.precipitationProbability > 0) {
                            Text("${h.precipitationProbability}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FifteenDayCard(data: com.minirili.app.data.weather.WeatherResult.ForDate) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text("15 天预报", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            data.daily.forEachIndexed { idx, d ->
                val label = when (idx) {
                    0 -> "今天"
                    1 -> "明天"
                    else -> d.date.substring(5)
                }
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(label, modifier = Modifier.width(56.dp))
                    Text(WeatherCode.icon(d.weatherCode), fontSize = 20.sp, modifier = Modifier.width(32.dp))
                    Text(
                        "${d.tempMin.toInt()}° ~ ${d.tempMax.toInt()}°",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (d.precipitationProbabilityMax > 0) {
                        Text("${d.precipitationProbabilityMax}%")
                    }
                }
            }
        }
    }
}

@Composable
private fun SunMoonCard(firstDaily: com.minirili.app.data.weather.DailyWeather?) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("日出日落", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            if (firstDaily?.sunriseMillis != null && firstDaily.sunsetMillis != null) {
                val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                Text("🌅 ${sdf.format(Date(firstDaily.sunriseMillis))}   🌇 ${sdf.format(Date(firstDaily.sunsetMillis))}")
            } else {
                Text("暂无数据", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun TravelAdviceSettingsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(TravelAdvicePrefs.isEnabled(context)) }
    var hour by remember { mutableStateOf(TravelAdvicePrefs.getHour(context)) }
    var minute by remember { mutableStateOf(TravelAdvicePrefs.getMinute(context)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("出行建议提醒") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("每日提醒", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = enabled, onCheckedChange = {
                        enabled = it
                        TravelAdvicePrefs.setEnabled(context, it)
                    })
                }
                Text(
                    "开启后，每日在指定时间推送当日出行建议通知。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (enabled) {
                    Spacer(Modifier.height(4.dp))
                    Text("提醒时间", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(onClick = { hour = (hour + 23) % 24 }, modifier = Modifier.size(32.dp)) {
                                Text("▲", fontSize = 14.sp)
                            }
                            Text(String.format("%02d", hour), style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.width(48.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            IconButton(onClick = { hour = (hour + 1) % 24 }, modifier = Modifier.size(32.dp)) {
                                Text("▼", fontSize = 14.sp)
                            }
                            Text("时", style = MaterialTheme.typography.labelSmall)
                        }
                        Text(" : ", style = MaterialTheme.typography.titleLarge)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(onClick = { minute = (minute + 59) % 60 }, modifier = Modifier.size(32.dp)) {
                                Text("▲", fontSize = 14.sp)
                            }
                            Text(String.format("%02d", minute), style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.width(48.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            IconButton(onClick = { minute = (minute + 1) % 60 }, modifier = Modifier.size(32.dp)) {
                                Text("▼", fontSize = 14.sp)
                            }
                            Text("分", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = {
                TravelAdvicePrefs.setTime(context, hour, minute)
                onDismiss()
            }) { Text("确定") }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
