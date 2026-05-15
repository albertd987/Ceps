package com.cepalert.data.model

import kotlinx.serialization.Serializable

@Serializable
data class WeatherData(
    val humidity7dAvg: Double,        // relative humidity %, mean of last 7 days
    val rain10dTotal: Double,         // mm, accumulated last 10 days
    val rain7dTotal: Double,          // mm, accumulated last 7 days
    val rain14dTotal: Double,         // mm, accumulated last 14 days
    val temp7dAvg: Double,            // °C, mean of last 7 days
    val temp7dMax: Double,            // °C
    val temp7dMin: Double,            // °C
    val daysSinceSignificantRain: Int,// days since last day with > 10 mm
    val source: String,
    val updatedAtEpochMs: Long
)
