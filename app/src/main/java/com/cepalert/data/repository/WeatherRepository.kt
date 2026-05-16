package com.cepalert.data.repository

import android.util.Log
import com.cepalert.data.api.OpenMeteoApi
import com.cepalert.data.api.OpenMeteoResponse
import com.cepalert.data.model.WeatherData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

fun mapToWeatherData(response: OpenMeteoResponse, nowEpochMs: Long): WeatherData {
    val d = response.daily
    val n = d.time.size

    fun <T> lastN(list: List<T?>, k: Int): List<T> =
        list.subList((n - k).coerceAtLeast(0), n).filterNotNull()

    // Soil moisture (m3/m3)
    val moistValues = lastN(d.soilMoisture, 7)
    val soilMoisture7d = if (moistValues.isNotEmpty()) moistValues.average() else 0.25

    // Soil temperature (°C)
    val tempAll = d.soilTemperature.filterNotNull()
    val tempRecent = lastN(d.soilTemperature, 7)
    val tempOld    = d.soilTemperature.take(7).filterNotNull()
    val soilTemp7d  = if (tempRecent.isNotEmpty()) tempRecent.average() else 12.0
    val soilTempOld = if (tempOld.isNotEmpty()) tempOld.average() else soilTemp7d
    val soilTempDrop = soilTempOld - soilTemp7d  // positive = cooled

    // Rain trigger: best 3-consecutive-day block
    val precip = d.precipitationSum
    var bestMm = 0.0; var bestDaysAgo = 99
    for (i in 0 until n - 2) {
        val block = precip[i] + precip[i + 1] + precip[i + 2]
        if (block > bestMm) { bestMm = block; bestDaysAgo = n - i - 2 }
    }

    val rain14d = precip.sum()

    return WeatherData(
        soilMoisture7d   = soilMoisture7d,
        soilTemp7d       = soilTemp7d,
        soilTempDrop     = soilTempDrop,
        rain14dTotal     = rain14d,
        rainTriggerMm    = bestMm,
        triggerDaysAgo   = bestDaysAgo,
        source           = "Open-Meteo",
        updatedAtEpochMs = nowEpochMs
    )
}

@Singleton
class WeatherRepository @Inject constructor(
    private val api: OpenMeteoApi
) {
    suspend fun getWeather(lat: Double, lon: Double): WeatherData =
        withContext(Dispatchers.IO) {
            val response = api.getHistory(lat = lat, lon = lon)
            mapToWeatherData(response, System.currentTimeMillis())
        }
}
