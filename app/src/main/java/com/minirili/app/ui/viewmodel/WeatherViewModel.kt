package com.minirili.app.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minirili.app.data.weather.AQIData
import com.minirili.app.data.weather.City
import com.minirili.app.data.weather.WeatherRepository
import com.minirili.app.data.weather.WeatherResult
import com.minirili.app.utils.LocationHelper
import com.minirili.app.widgets.CombinedWidgetProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 天气栏目 + 天气页共享的 ViewModel。 */
@HiltViewModel
class WeatherViewModel @Inject constructor(
    private val repository: WeatherRepository,
    private val locationHelper: LocationHelper,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _state = MutableStateFlow<WeatherUiState>(WeatherUiState.Loading)
    val state: StateFlow<WeatherUiState> = _state.asStateFlow()

    private val _currentCity = MutableStateFlow<City?>(null)
    val currentCity: StateFlow<City?> = _currentCity.asStateFlow()

    /** 所有已保存的城市列表 */
    private val _cities = MutableStateFlow<List<City>>(emptyList())
    val cities: StateFlow<List<City>> = _cities.asStateFlow()

    /** true=使用定位获取的当前位置；false=手动选城市或默认城市 */
    private val _usingCurrentLocation = MutableStateFlow(false)
    val usingCurrentLocation: StateFlow<Boolean> = _usingCurrentLocation.asStateFlow()

    private val _aqi = MutableStateFlow<AQIData?>(null)
    val aqi: StateFlow<AQIData?> = _aqi.asStateFlow()

    private var started = false

    /** 首次进入时拉取城市列表 + 天气。之后自动监听城市列表变化。 */
    fun start() {
        if (started) return
        started = true
        loadCityAndRefresh()
        viewModelScope.launch {
            // drop(1) 跳过初始发射（已由 loadCityAndRefresh 处理），只响应后续变化
            repository.observeCities().drop(1).collect { cityList ->
                _cities.value = cityList
                val current = _currentCity.value
                val first = cityList.firstOrNull()
                if (first != null && (current == null || current.id != first.id)) {
                    _currentCity.value = first.copy(isCurrentLocation = false)
                    _usingCurrentLocation.value = false
                    refresh(first)
                } else if (cityList.isEmpty() && current != null) {
                    loadDefaultCity()
                }
            }
        }
    }

    /** 用户主动触发"重新定位"。 */
    fun refreshLocation() {
        viewModelScope.launch {
            _state.value = WeatherUiState.Loading
            loadDefaultCity(fallback = _cities.value.firstOrNull())
        }
        CombinedWidgetProvider.refreshWidget(appContext)
    }

    /** 切换城市 */
    fun selectCity(city: City) {
        if (city.id == _currentCity.value?.id) return
        _currentCity.value = city
        _usingCurrentLocation.value = false
        refresh(city)
    }

    /** 添加城市并切换到该城市 */
    fun addCity(city: City) {
        viewModelScope.launch {
            repository.ensureCity(city)
            _cities.value = repository.getCities()
            _currentCity.value = city
            _usingCurrentLocation.value = false
            refresh(city)
        }
        CombinedWidgetProvider.refreshWidget(appContext)
    }

    /** 删除城市 */
    fun removeCity(cityId: String) {
        viewModelScope.launch {
            repository.removeCity(cityId)
            _cities.value = repository.getCities()
            // 如果删的是当前城市，切到第一个城市或重新定位
            if (cityId == _currentCity.value?.id) {
                loadDefaultCity()
            }
        }
    }

    /** 城市搜索结果 */
    private val _searchResults = MutableStateFlow<List<City>>(emptyList())
    val searchResults: StateFlow<List<City>> = _searchResults.asStateFlow()

    /** 是否正在搜索 */
    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching.asStateFlow()

    /** 城市搜索（异步，不阻塞主线程） */
    fun searchCities(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            _searching.value = true
            _searchResults.value = runCatching {
                repository.searchCity(query)
            }.getOrDefault(emptyList())
            _searching.value = false
        }
    }

    private fun loadCityAndRefresh() {
        viewModelScope.launch {
            _state.value = WeatherUiState.Loading
            _cities.value = repository.getCities()

            if (_cities.value.isNotEmpty()) {
                val city = _cities.value.first().copy(isCurrentLocation = false)
                _currentCity.value = city
                _usingCurrentLocation.value = false
                refresh(city)
                return@launch
            }
            loadDefaultCity()
        }
    }

    private suspend fun loadDefaultCity(fallback: City? = null) {
        val locatedCity = locationHelper.getCurrentCity()
        if (locatedCity != null) {
            repository.ensureCity(locatedCity)
            _cities.value = repository.getCities()
            _currentCity.value = locatedCity
            _usingCurrentLocation.value = true
            refresh(locatedCity)
            CombinedWidgetProvider.refreshWidget(appContext)
            return
        }
        val city = fallback ?: DEFAULT_BEIJING
        _currentCity.value = city
        _usingCurrentLocation.value = false
        refresh(city)
    }

    fun refresh(city: City? = null) {
        val effectiveCity = city ?: _currentCity.value ?: return
        viewModelScope.launch {
            _state.value = WeatherUiState.Loading
            _state.value = when (val r = repository.getCurrentWeather(effectiveCity)) {
                is WeatherResult.ForDate -> {
                    // 同时获取 AQI 数据
                    _aqi.value = repository.getAQI(effectiveCity)
                    WeatherUiState.Ready(r)
                }
                is WeatherResult.Error -> WeatherUiState.Error(r.error)
            }
        }
    }

    fun selectDate(date: String) {
        val city = _currentCity.value ?: return
        viewModelScope.launch {
            _state.value = WeatherUiState.Loading
            _state.value = when (val r = repository.getWeatherForDate(date, city)) {
                is WeatherResult.ForDate -> WeatherUiState.Ready(r)
                is WeatherResult.Error -> WeatherUiState.Error(r.error)
            }
        }
    }

    companion object {
        val DEFAULT_BEIJING = City(
            id = "39.9042,116.4074",
            name = "北京",
            latitude = 39.9042,
            longitude = 116.4074,
            country = "中国"
        )
    }
}

sealed class WeatherUiState {
    object Loading : WeatherUiState()
    data class Ready(val data: WeatherResult.ForDate) : WeatherUiState()
    data class Error(val error: Throwable) : WeatherUiState()
}
