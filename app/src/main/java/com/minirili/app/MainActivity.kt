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
import com.minirili.app.utils.AutoStartHelper
import com.minirili.app.utils.AppLaunchPrefs
import com.minirili.app.utils.AutoStartHelper.ManufacturerGuide
import com.minirili.app.utils.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.atomic.AtomicBoolean

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

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
                    var showAutoStartPrompt by remember { mutableStateOf(false) }

                    // 每次启动检测厂商自启动：设备支持 + 30天未提示 → 弹窗引导
                    val autoStartChecked = remember { AtomicBoolean() }
                    LaunchedEffect(Unit) {
                        if (autoStartChecked.compareAndSet(false, true)) {
                            if (AutoStartHelper.hasAutoStartSettings(this@MainActivity) &&
                                AppLaunchPrefs.shouldAskAutoStart(this@MainActivity)) {
                                showAutoStartPrompt = true
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

                    if (showAutoStartPrompt) {
                        AutoStartDialog(
                            onLater = {
                                showAutoStartPrompt = false
                            },
                            onDismiss30d = {
                                showAutoStartPrompt = false
                                AppLaunchPrefs.markAllAsked(this@MainActivity)
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
private fun AutoStartDialog(
    onLater: () -> Unit,
    onDismiss30d: () -> Unit
) {
    val guide = remember { AutoStartHelper.getManufacturerGuide() }
    val body = buildString {
        append("为确保事件闹钟提醒准时触发、桌面插件正常更新，建议将 迷历 加入系统权限白名单：\n\n")
        append("• 开机启动：允许系统开机后自动运行 迷历\n")
        append("• 后台自启动：允许 迷历 在后台常驻运行\n\n")
        if (guide != null) {
            append(guide.guideHint)
        } else {
            append("请进入系统「设置 → 应用管理 → 迷历」，找到「自启动」或「后台管理」并允许。\n")
            append("不同厂商设置路径不同，可在「手机管家」或「安全中心」中搜索「自启动」相关设置。")
        }
    }
    AlertDialog(
        onDismissRequest = onLater,
        title = { Text("将 迷历 加入开机启动") },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = onLater) { Text("稍后设置") } },
        dismissButton = { TextButton(onClick = onDismiss30d) { Text("30天内不再提示") } }
    )
}
