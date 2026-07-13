package com.minirili.app.ui.screens.weather

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.minirili.app.data.weather.WeatherCode
import com.minirili.app.ui.viewmodel.WeatherUiState
import com.minirili.app.ui.viewmodel.WeatherViewModel

/**
 * 天气栏目：插入月/周/日视图的日历区与事件列表之间。
 *
 * 逻辑：
 *  - 若 selectedDate 在 15 天预报范围内 → 显示 selectedDate 的预报（高/低、天气）
 *  - 若 selectedDate 超出范围 → 显示今日预报，并注明"超出预报范围"
 */
@Composable
fun WeatherCard(
    selectedDate: String,
    onOpenWeatherPage: () -> Unit,
    viewModel: WeatherViewModel = hiltViewModel()
) {
    LaunchedEffect(Unit) { viewModel.start() }
    LaunchedEffect(selectedDate) { viewModel.selectDate(selectedDate) }

    val state by viewModel.state.collectAsState()
    val city by viewModel.currentCity.collectAsState()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clickable { onOpenWeatherPage() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        when (val s = state) {
            is WeatherUiState.Loading -> {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("正在获取天气…", style = MaterialTheme.typography.bodySmall)
                }
            }
            is WeatherUiState.Ready -> {
                val effectiveDate = s.data.effectiveDate
                val daily = s.data.daily.firstOrNull { it.date == effectiveDate }
                val isOutOfRange = effectiveDate != selectedDate
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            WeatherCode.icon(daily?.weatherCode ?: 0),
                            fontSize = 28.sp
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            if (daily != null) {
                                Text(
                                    "${daily.tempMin.toInt()}° ~ ${daily.tempMax.toInt()}°",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    WeatherCode.description(daily.weatherCode),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Text("--°", style = MaterialTheme.typography.titleMedium)
                            }
                            if (isOutOfRange) {
                                Text(
                                    "仅显示今日（超出 15 天范围）",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(14.dp))
                            Text(
                                city?.name ?: "—",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Text(
                            labelForDate(effectiveDate),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            is WeatherUiState.Error -> {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("？", fontSize = 28.sp)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("天气暂不可用", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "点击重试或进入天气页",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "重试")
                    }
                }
            }
        }
    }
}

private fun labelForDate(effectiveDate: String): String {
    if (effectiveDate.isBlank()) return "—"
    return effectiveDate.substringAfter("-").substringBeforeLast("-") + "-" + effectiveDate.substringAfterLast("-")
}
