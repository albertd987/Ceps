package com.cepalert.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OpenMeteoResponse(
    val daily: DailyDto
)

@Serializable
data class DailyDto(
    @SerialName("time")                            val time: List<String>,
    @SerialName("precipitation_sum")               val precipitationSum: List<Double>,
    @SerialName("soil_temperature_0_to_7cm_mean")  val soilTemperature: List<Double?>,
    @SerialName("soil_moisture_0_to_7cm_mean")     val soilMoisture: List<Double?>
)
