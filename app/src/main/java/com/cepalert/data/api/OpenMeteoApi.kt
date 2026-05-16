package com.cepalert.data.api

import retrofit2.http.GET
import retrofit2.http.Query

interface OpenMeteoApi {

    @GET("v1/forecast")
    suspend fun getHistory(
        @Query("latitude") lat: Double,
        @Query("longitude") lon: Double,
        @Query("past_days") pastDays: Int = 14,
        @Query("forecast_days") forecastDays: Int = 0,
        @Query("daily") daily: String =
            "precipitation_sum," +
            "soil_temperature_0_to_7cm_mean," +
            "soil_moisture_0_to_7cm_mean",
        @Query("timezone") timezone: String = "Europe/Madrid"
    ): OpenMeteoResponse
}
