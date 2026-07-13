package com.minirili.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.minirili.app.receivers.AlarmReceiver
import com.minirili.app.ui.navigation.CalendarNavHost
import com.minirili.app.ui.navigation.Screen
import com.minirili.app.ui.theme.CalendarTheme
import com.minirili.app.ui.viewmodel.EventViewModel
import com.minirili.app.utils.AppLaunchPrefs
import com.minirili.app.utils.BatteryOptimizationHelper
import com.minirili.app.utils.NotificationHelper
import com.minirili.app.utils.BatteryOptimizationHelper.ManufacturerGuide
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.atomic.AtomicBoolean

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    /** 电池引导"去设置"后 pending 复查标记：onResume 检测到真改了才 markBatteryAsked */
    private var batteryPendingCheck = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        AlarmReceiver.stopAlarm()
        AlarmScreenOffReceiver.register(this)
        createNewEventShortcut()
        val initialNotificationId = notificationEventIdFromIntent(intent)
        setContent {
            val viewModel: EventViewModel = hiltViewModel()
            CalendarTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController: NavHostController = rememberNavController()
                    var showBatteryPrompt by remember { mutableStateOf(false) }
                    var showAutoStartPrompt by remember { mutableStateOf(false) }

                    // 启动时检测：电池优化 + 常驻后台。各自独立控制，30 天内不再重复提示
                    val batteryChecked = remember { AtomicBoolean() }
                    LaunchedEffect(Unit) {
                        if (batteryChecked.compareAndSet(false, true)) {
                            val batteryIgnored = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(this@MainActivity)
                            val batteryShouldAsk = AppLaunchPrefs.shouldAskBattery(this@MainActivity)
                            if (!batteryIgnored && batteryShouldAsk) {
                                showBatteryPrompt = true
                            }
                        }
                    }
                    val autoStartChecked = remember { AtomicBoolean() }
                    LaunchedEffect(Unit) {
                        if (autoStartChecked.compareAndSet(false, true)) {
                            val autoStartAvailable = BatteryOptimizationHelper.hasAutoStartSettings(this@MainActivity)
                            val autoStartShouldAsk = AppLaunchPrefs.shouldAskAutoStart(this@MainActivity)
                            if (autoStartAvailable && autoStartShouldAsk) {
                                showAutoStartPrompt = true
                            }
                        }
                    }

                    // 创建带提醒事件后的引导：ViewModel 通知时检查是否需要弹电池引导
                    LaunchedEffect(Unit) {
                        viewModel.batteryGuideTrigger.collect {
                            if (showBatteryPrompt) return@collect  // 已有引导对话框，不重复
                            val batteryIgnored = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(this@MainActivity)
                            val batteryShouldAsk = AppLaunchPrefs.shouldAskBattery(this@MainActivity)
                            if (!batteryIgnored && batteryShouldAsk) {
                                showBatteryPrompt = true
                            }
                        }
                    }

                    NotificationRouter(
                        navController,
                        notificationEventId = initialNotificationId,
                        navigateToNewEvent = intent?.getStringExtra("navigate_to") == "new_event",
                        navigateToWeather = intent?.getStringExtra("navigate_to") == "weather",
                        navigateToAllEvents = intent?.getStringExtra("navigate_to") == "all_events",
                        navigateToDay = intent?.getStringExtra("navigate_to") == "day"
                    )

                    if (showBatteryPrompt) {
                        BatteryOptDialog(
                            onConfirm = {
                                showBatteryPrompt = false
                                batteryPendingCheck = true  // 跳转后待复查
                                BatteryOptimizationHelper.startBatterySettings(this@MainActivity)
                            },
                            onDismiss = {
                                showBatteryPrompt = false
                                AppLaunchPrefs.markBatteryAsked(this@MainActivity)
                            }
                        )
                    }

                    if (showAutoStartPrompt) {
                        val guide = remember { BatteryOptimizationHelper.getManufacturerGuide() }
                        AutoStartDialog(
                            guide = guide,
                            onConfirm = {
                                showAutoStartPrompt = false
                                AppLaunchPrefs.markAutoStartAsked(this@MainActivity)
                                BatteryOptimizationHelper.buildAutoStartIntent(this@MainActivity)?.let {
                                    runCatching { startActivity(it) }
                                }
                            },
                            onDismiss = {
                                showAutoStartPrompt = false
                                AppLaunchPrefs.markAutoStartAsked(this@MainActivity)
                            }
                        )
                    }

                    CalendarNavHost(
                        navController = navController,
                        viewModel = viewModel,
                        onStartPrivacyPolicy = {
                            // TODO: 请求通知权限
                        }
                    )
                }
            }
        }
        intent?.removeExtra(NotificationHelper.EXTRA_EVENT_ID)
    }

    override fun onDestroy() {
        AlarmScreenOffReceiver.unregister(this)
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        AlarmReceiver.stopAlarm()
        // 电池引导"去设置"后复查：用户是否真的改了白名单
        if (batteryPendingCheck && BatteryOptimizationHelper.isIgnoringBatteryOptimizations(this)) {
            batteryPendingCheck = false
            AppLaunchPrefs.markBatteryAsked(this)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        AlarmReceiver.stopAlarm()
        setIntent(intent)
    }

    private fun notificationEventIdFromIntent(intent: Intent?): Long {
        val eventId = intent?.getLongExtra(NotificationHelper.EXTRA_EVENT_ID, -1L) ?: -1L
        return if (eventId > 0) eventId else -1L
    }

    /** UI-02: 创建长按图标快捷入口「新建事件」。 */
    private fun createNewEventShortcut() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return
        val shortcutManager = getSystemService(ShortcutManager::class.java)
        if (shortcutManager == null) return

        val shortcut = ShortcutInfo.Builder(this, "new_event")
            .setShortLabel("新建事件")
            .setLongLabel("快速新建事件")
            .setIcon(Icon.createWithResource(this, android.R.drawable.ic_menu_add))
            .setIntent(Intent(this, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                putExtra("navigate_to", "new_event")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            .build()

        shortcutManager.dynamicShortcuts = listOf(shortcut)
    }
}

/** 监听屏幕关闭 → 停闹钟 */
class AlarmScreenOffReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_SCREEN_OFF) {
            com.minirili.app.receivers.AlarmReceiver.stopAlarm()
        }
    }

    companion object {
        private var instance: AlarmScreenOffReceiver? = null
        fun register(context: Context) {
            if (instance == null) {
                instance = AlarmScreenOffReceiver()
                val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.registerReceiver(instance, filter, Context.RECEIVER_NOT_EXPORTED)
                } else {
                    context.registerReceiver(instance, filter)
                }
            }
        }
        fun unregister(context: Context) {
            instance?.let { runCatching { context.unregisterReceiver(it) } }
            instance = null
        }
    }
}

/** 用户主动打开事件页面时调用（如列表里点击事件），也同步停止闹钟 */
fun stopAlarmOnEventOpen() {
    com.minirili.app.receivers.AlarmReceiver.stopAlarm()
}

@Composable
private fun NotificationRouter(navController: NavHostController, notificationEventId: Long, navigateToNewEvent: Boolean = false, navigateToWeather: Boolean = false, navigateToAllEvents: Boolean = false, navigateToDay: Boolean = false) {
    LaunchedEffect(notificationEventId) {
        if (notificationEventId > 0) {
            navController.navigate(Screen.EventDetail.createRoute(notificationEventId)) {
                launchSingleTop = true
            }
        }
    }
    LaunchedEffect(navigateToNewEvent) {
        if (navigateToNewEvent) {
            navController.navigate(Screen.EventDetail.createRoute(0)) {
                launchSingleTop = true
            }
        }
    }
    LaunchedEffect(navigateToWeather) {
        if (navigateToWeather) {
            navController.navigate(Screen.Weather.route) {
                launchSingleTop = true
            }
        }
    }
    LaunchedEffect(navigateToAllEvents) {
        if (navigateToAllEvents) {
            navController.navigate(Screen.AllEvents.route) {
                launchSingleTop = true
            }
        }
    }
    LaunchedEffect(navigateToDay) {
        if (navigateToDay) {
            navController.navigate(Screen.Calendar.createRoute("day")) {
                popUpTo(Screen.Calendar.route) { inclusive = true }
                launchSingleTop = true
            }
        }
    }
}

@Composable
private fun BatteryOptDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("保证提醒不会被漏掉") },
        text = {
            Text(
                "为了保证闹钟在后台稳定触发，建议把「MiniRili」加入电池优化白名单。未加入的设备可能在锁屏几小时后收不到提醒。"
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("去设置") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("稍后再说") } }
    )
}

@Composable
private fun AutoStartDialog(guide: ManufacturerGuide?, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val text = if (guide != null) {
        "为了闹钟不被系统自动清理，建议允许 MiniRili 后台常驻。" +
            guide.guideHint + "。点「去设置」后将跳转至设置页。"
    } else {
        "为了闹钟不被系统自动清理，建议您在手机管家中把「MiniRili」加入「自启动 / 后台常驻」白名单。" +
            "不同厂商设置路径不同，点「去设置」后请手动找到「自启动管理」或「后台管理」并允许。"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("允许后台常驻") },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("去设置") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("稍后再说") } }
    )
}
