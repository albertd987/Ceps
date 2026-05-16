package com.cepalert.data.model

import kotlinx.serialization.Serializable

@Serializable
data class WeatherData(
    val soilMoisture7d: Double,     // m3/m3, volumetric soil water content 0-7cm, mean 7d
    val soilTemp7d: Double,         // °C, soil temperature 0-7cm, mean 7d
    val soilTempDrop: Double,       // °C, cooling week-over-week (positive = cooled)
    val rain14dTotal: Double,       // mm, accumulated 14 days (AEMET if available)
    val rainTriggerMm: Double,      // mm, best 3-consecutive-day block last 14d
    val triggerDaysAgo: Int,        // days ago the trigger block ended
    val source: String,
    val updatedAtEpochMs: Long
)
