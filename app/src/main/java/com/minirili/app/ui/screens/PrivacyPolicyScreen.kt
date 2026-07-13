package com.minirili.app.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private const val PREFS_NAME = "calendar_privacy"
private const val KEY_ACCEPTED_AT = "privacy_accepted_at"
private const val THIRTY_DAYS_MILLIS = 30L * 24 * 60 * 60 * 1000

fun shouldShowPrivacyPolicy(context: Context): Boolean {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val acceptedAt = prefs.getLong(KEY_ACCEPTED_AT, 0L)
    if (acceptedAt <= 0L) return true
    return System.currentTimeMillis() - acceptedAt > THIRTY_DAYS_MILLIS
}

private fun markPrivacyAccepted(context: Context) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putLong(KEY_ACCEPTED_AT, System.currentTimeMillis()).apply()
}

@Composable
fun PrivacyPolicyScreen(
    onAccept: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var suppressFor30Days by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = {},
        title = { Text(text = "隐私政策") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Text(
                    text = "日历 APP 隐私政策",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "本应用不收集任何个人信息，所有数据均存储在本地设备中。\n\n" +
                           "我们仅使用以下系统权限：\n" +
                           "• 通知权限 - 用于显示事件提醒\n" +
                           "• 开机自启动权限 - 用于恢复错过的事件提醒\n" +
                           "• 网络权限 - 仅当您主动使用天气功能时访问 open-meteo.com 获取天气数据\n" +
                           "• 定位权限 - 仅当您主动使用天气功能且未指定城市时，用于查询当地天气\n\n" +
                           "天气查询时，您的地理位置（经纬度）仅发送至 open-meteo.com，不用于其他目的或与第三方分享。\n\n" +
                           "您的日历事件数据完全属于您自己，不会上传到任何服务器。",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = suppressFor30Days,
                        onCheckedChange = { suppressFor30Days = it }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "确认后 30 天内不再弹出",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (suppressFor30Days) {
                    markPrivacyAccepted(context)
                }
                onAccept()
            }) {
                Text("接受")
            }
        },
        dismissButton = {
            TextButton(onClick = { /* 退出应用 */ }) {
                Text("退出")
            }
        }
    )
}
