package com.cepalert.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OpenMeteoResponse(
    val daily: DailyDto
)

@Serializable
data class DailyDto(
    @SerialName("time") val time: List<String>,
    @SerialName("precipitation_sum") val precipitationSum: List<Double>,
    @SerialName("temperature_2m_mean") val temperatureMean: List<Double>,
    @SerialName("temperature_2m_max") val temperatureMax: List<Double>,
    @SerialName("temperature_2m_min") val temperatureMin: List<Double>,
    @SerialName("relative_humidity_2m_mean") val humidityMean: List<Double>
)
