package com.cepalert.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RemoteScores(
    val v: Int = 2,
    @SerialName("updated_at") val updatedAt: String,
    val month: Int,
    // Map<zoneId, [score, soilMoist7d, soilTemp7d, soilTempDrop, rain14d, rainTriggerMm, triggerDaysAgo]>
    val zones: Map<String, List<Double>>
)

fun RemoteScores.toWeatherData(zoneId: String): WeatherData? {
    val d = zones[zoneId] ?: return null
    if (d.size < 7) return null
    return WeatherData(
        soilMoisture7d   = d[1],
        soilTemp7d       = d[2],
        soilTempDrop     = d[3],
        rain14dTotal     = d[4],
        rainTriggerMm    = d[5],
        triggerDaysAgo   = d[6].toInt(),
        source           = "Open-Meteo+AEMET",
        updatedAtEpochMs = System.currentTimeMillis()
    )
}

fun RemoteScores.regionalWeather(): WeatherData {
    val values = zones.values.filter { it.size >= 7 }
    fun avg(idx: Int) = values.map { it[idx] }.average()
    return WeatherData(
        soilMoisture7d   = avg(1),
        soilTemp7d       = avg(2),
        soilTempDrop     = avg(3),
        rain14dTotal     = avg(4),
        rainTriggerMm    = avg(5),
        triggerDaysAgo   = values.map { it[6].toInt() }.average().toInt(),
        source           = "Open-Meteo+AEMET",
        updatedAtEpochMs = System.currentTimeMillis()
    )
}
