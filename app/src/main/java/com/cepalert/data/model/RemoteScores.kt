package com.cepalert.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RemoteScores(
    val v: Int = 1,
    @SerialName("updated_at") val updatedAt: String,
    val month: Int,
    // Map<zoneId, [score, rain10d, temp7d, hum7d, rain7d, rain14d, daysSince]>
    val zones: Map<String, List<Double>>
)

fun RemoteScores.toWeatherData(zoneId: String): WeatherData? {
    val d = zones[zoneId] ?: return null
    return WeatherData(
        humidity7dAvg            = d[3],
        rain10dTotal             = d[1],
        rain7dTotal              = d[4],
        rain14dTotal             = d[5],
        temp7dAvg                = d[2],
        temp7dMax                = 0.0,
        temp7dMin                = 0.0,
        daysSinceSignificantRain = d[6].toInt(),
        source                   = "Open-Meteo",
        updatedAtEpochMs         = System.currentTimeMillis()
    )
}

fun RemoteScores.regionalWeather(): WeatherData {
    val values = zones.values
    fun avg(idx: Int) = values.map { it[idx] }.average()
    return WeatherData(
        humidity7dAvg            = avg(3),
        rain10dTotal             = avg(1),
        rain7dTotal              = avg(4),
        rain14dTotal             = avg(5),
        temp7dAvg                = avg(2),
        temp7dMax                = 0.0,
        temp7dMin                = 0.0,
        daysSinceSignificantRain = values.map { it[6].toInt() }.average().toInt(),
        source                   = "Open-Meteo",
        updatedAtEpochMs         = System.currentTimeMillis()
    )
}
