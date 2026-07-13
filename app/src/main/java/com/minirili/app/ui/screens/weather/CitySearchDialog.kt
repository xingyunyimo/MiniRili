package com.minirili.app.ui.screens.weather

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.minirili.app.data.weather.City
import com.minirili.app.ui.viewmodel.WeatherViewModel

/**
 * 城市搜索对话框：输入城市名 → 搜索 → 点击添加。
 *
 * 搜索是异步的（viewModel 内 viewModelScope.launch），不阻塞主线程。
 */
@Composable
fun CitySearchDialog(
    viewModel: WeatherViewModel,
    currentCityIds: Set<String>,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var searched by remember { mutableStateOf(false) }

    val results by viewModel.searchResults.collectAsState()
    val searching by viewModel.searching.collectAsState()

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("添加城市", style = MaterialTheme.typography.titleMedium)

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("输入城市名") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Button(
                        onClick = {
                            if (query.isBlank()) return@Button
                            searched = true
                            viewModel.searchCities(query.trim())
                        },
                        enabled = query.isNotBlank() && !searching
                    ) {
                        Text("搜索")
                    }
                }

                if (searching) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                }

                if (!searching && results.isNotEmpty()) {
                    Text(
                        "搜索结果",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LazyColumn(
                        modifier = Modifier.height(200.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(results) { city ->
                            val alreadyAdded = city.id in currentCityIds
                            Card(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !alreadyAdded) {
                                        viewModel.addCity(city)
                                        onDismiss()
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (alreadyAdded)
                                        MaterialTheme.colorScheme.surfaceVariant
                                    else
                                        MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Row(
                                    Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(city.name, style = MaterialTheme.typography.bodyLarge)
                                        city.country?.let { country ->
                                            Text(
                                                country,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Text(
                                        if (alreadyAdded) "已添加" else "添加",
                                        color = if (alreadyAdded)
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        else
                                            MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }
                    }
                }

                if (searched && !searching && results.isEmpty()) {
                    Text("未找到城市", style = MaterialTheme.typography.bodyMedium)
                }

                Spacer(Modifier.height(4.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("取消")
                }
            }
        }
    }
}