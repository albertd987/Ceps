package com.cepalert.data.api

import retrofit2.http.GET
import retrofit2.http.Query

interface OpenMeteoApi {

    @GET("v1/forecast")
    suspend fun getHistory(
        @Query("latitude") lat: Double,
        @Query("longitude") lon: Double,
        @Query("past_days") pastDays: Int = 14,
        @Query("forecast_days") forecastDays: Int = 1,
        @Query("daily") daily: String =
            "precipitation_sum,temperature_2m_mean,temperature_2m_max," +
            "temperature_2m_min,relative_humidity_2m_mean",
        @Query("timezone") timezone: String = "Europe/Madrid"
    ): OpenMeteoResponse
}
