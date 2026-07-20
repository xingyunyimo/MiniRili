package com.minirili.app.utils

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.minirili.app.data.weather.City
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.coroutines.resume

/**
 * 定位辅助：用 Android 内置 LocationManager（无需 play-services-location），
 * 零新增依赖。无权限或定位失败时返回 null，由调用方降级到默认城市。
 *
 * 反地理编码（经纬度 → 城市名）双通道：
 *  1. Android 本地 Geocoder：零外发、最快；但部分设备（无 Play Services）返回空。
 *  2. Nominatim (OpenStreetMap) 网络反查：拿 address.state/city/county 拼成 "省 市 区县"。
 *
 * 直辖市自动去重；都失败时调用方 fallback "当前位置"。
 */
class LocationHelper(private val context: Context) {

    fun hasPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    @SuppressLint("MissingPermission")
    fun getCurrentCity(): City? {
        if (!hasPermission()) return null
        val loc = getLastKnownLocation() ?: return null
        val name = runBlocking { reverseGeocode(loc.latitude, loc.longitude) } ?: "当前位置"
        return City(
            id = "current",
            name = name,
            latitude = loc.latitude,
            longitude = loc.longitude,
            country = null,
            isCurrentLocation = true
        )
    }

    /**
     * 异步获取当前位置（协程安全）。
     * API 30+ 使用 [LocationManager.getCurrentLocation] 获取实时位置，
     * 低版本回退到 [getLastKnownLocation]。
     */
    @SuppressLint("MissingPermission")
    suspend fun getCurrentCityAsync(): City? {
        if (!hasPermission()) return null
        val loc = getFreshLocation() ?: return null
        val name = reverseGeocode(loc.latitude, loc.longitude) ?: "当前位置"
        return City(
            id = "current",
            name = name,
            latitude = loc.latitude,
            longitude = loc.longitude,
            country = null,
            isCurrentLocation = true
        )
    }

    /**
     * 获取新鲜位置：API 30+ 用 [LocationManager.getCurrentLocation] 做一次被动定位，
     * 低版本回退到 [getLastKnownLocation]。
     */
    @SuppressLint("MissingPermission")
    private suspend fun getFreshLocation(): Location? {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        if (Build.VERSION.SDK_INT >= 30) {
            return try {
                suspendCancellableCoroutine { cont ->
                    val cs = android.os.CancellationSignal()
                    cont.invokeOnCancellation { cs.cancel() }
                    lm.getCurrentLocation(
                        LocationManager.PASSIVE_PROVIDER,
                        cs,
                        ContextCompat.getMainExecutor(context)
                    ) { location ->
                        if (!cont.isCompleted) cont.resume(location)
                    }
                }
            } catch (_: Exception) { null }
        }
        return getLastKnownLocation()
    }

    @SuppressLint("MissingPermission")
    private fun getLastKnownLocation(): Location? {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        var best: Location? = null
        for (p in lm.allProviders) {
            @Suppress("DEPRECATION")
            val loc = runCatching { lm.getLastKnownLocation(p) }.getOrNull() ?: continue
            if (best == null || loc.time > best!!.time) best = loc
        }
        return best
    }

    /**
     * 反地理编码：Android Geocoder 优先，失败时 Nominatim (OpenStreetMap) 做网络反查。
     * 返回 "省 市 区县"；都失败返回 null，调用方 fallback "当前位置"。
     */
    private suspend fun reverseGeocode(lat: Double, lon: Double): String? {
        // 1. Android 本地 Geocoder（零外发）
        runCatching { reverseGeocodeLocal(lat, lon) }.getOrNull()?.let { return it }
        // 2. 网络反查 fallback
        return runCatching { reverseGeocodeNominatim(lat, lon) }.getOrNull()
    }

    /** Android 本地 Geocoder：API 33+ 走异步回调，旧版同步调用。 */
    private suspend fun reverseGeocodeLocal(lat: Double, lon: Double): String? {
        val geocoder = Geocoder(context, Locale.getDefault())
        val addresses: List<Address>? = if (Build.VERSION.SDK_INT >= 33) {
            suspendCancellableCoroutine { cont ->
                geocoder.getFromLocation(lat, lon, 1) { list ->
                    @Suppress("UNCHECKED_CAST")
                    cont.resume(list ?: emptyList<Address>())
                }
            }
        } else {
            @Suppress("DEPRECATION")
            geocoder.getFromLocation(lat, lon, 1)
        }
        val addr = addresses?.firstOrNull() ?: return null

        val province = (addr.adminArea ?: "").removeSuffix("市").trim()
        val city = (addr.locality ?: addr.subAdminArea ?: "").removeSuffix("市").trim()
        val district = (addr.subLocality ?: addr.subAdminArea ?: addr.adminArea ?: "").trim()

        val tokens = mutableListOf<String>()
        if (province.isNotBlank()) tokens.add(province)
        if (city.isNotBlank() && city != province) tokens.add(city)
        if (district.isNotBlank() && district != city && district != province) tokens.add(district)
        return tokens.joinToString(" ").ifBlank {
            addr.locality ?: addr.subAdminArea ?: addr.adminArea ?: addr.countryName
        }
    }

    /** Nominatim (OpenStreetMap) 网络反查，解析 address.state / city(municipality/county) / county(suburb/town)。 */
    private suspend fun reverseGeocodeNominatim(lat: Double, lon: Double): String? =
        withContext(Dispatchers.IO) {
            val urlStr = "https://nominatim.openstreetmap.org/reverse" +
                "?format=json&lat=$lat&lon=$lon&accept-language=zh&zoom=18"
            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "MiniRili/1.0 (personal Android calendar app)")
            }
            try {
                val code = conn.responseCode
                if (code !in 200..299) return@withContext null
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                val json = runCatching { org.json.JSONObject(text) }.getOrNull() ?: return@withContext null
                if (json.has("error")) return@withContext null
                val addr = json.optJSONObject("address") ?: return@withContext null

                val province = addr
                    .optString("state", addr.optString("province", ""))
                    .removeSuffix("市").removeSuffix("省")
                    .removeSuffix("自治区").removeSuffix("特别行政区")
                    .trim()
                val city = addr
                    .optString("city", addr.optString("municipality", addr.optString("county", "")))
                    .removeSuffix("市").removeSuffix("地区").removeSuffix("自治州").trim()
                val countyOrTown = addr
                    .optString("county", addr.optString("suburb", addr.optString("town", "")))
                    .removeSuffix("市").removeSuffix("区").removeSuffix("县")
                    .removeSuffix("镇").removeSuffix("街道").trim()

                val tokens = mutableListOf<String>()
                if (province.isNotBlank()) tokens.add(province)
                if (city.isNotBlank() && city != province) tokens.add(city)
                if (countyOrTown.isNotBlank() && countyOrTown != city && countyOrTown != province) tokens.add(countyOrTown)

                tokens.joinToString(" ").ifBlank { json.optString("display_name").ifBlank { null } }
            } catch (_: Exception) {
                null
            } finally {
                conn.disconnect()
            }
        }
}
