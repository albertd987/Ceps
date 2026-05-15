package com.cepalert.data.repository

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
    fun lastN(list: List<Double>, k: Int) = list.subList((n - k).coerceAtLeast(0), n)

    val rain10d = lastN(d.precipitationSum, 10).sum()
    val rain7d = lastN(d.precipitationSum, 7).sum()
    val rain14d = lastN(d.precipitationSum, 14).sum()
    val temp7d = lastN(d.temperatureMean, 7)
    val hum7d = lastN(d.humidityMean, 7)

    var daysSince = 0
    for (i in d.precipitationSum.indices.reversed()) {
        if (d.precipitationSum[i] > 10.0) break
        daysSince++
    }

    return WeatherData(
        humidity7dAvg = hum7d.average(),
        rain10dTotal = rain10d,
        rain7dTotal = rain7d,
        rain14dTotal = rain14d,
        temp7dAvg = temp7d.average(),
        temp7dMax = lastN(d.temperatureMax, 7).max(),
        temp7dMin = lastN(d.temperatureMin, 7).min(),
        daysSinceSignificantRain = daysSince,
        source = "Open-Meteo",
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
