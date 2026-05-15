package com.cepalert.data.repository

import com.cepalert.data.api.DailyDto
import com.cepalert.data.api.OpenMeteoResponse
import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherMapperTest {

    // 15 days of data; index 14 is "today".
    private fun response(): OpenMeteoResponse {
        val days = (1..15).map { "2026-05-%02d".format(it) }
        return OpenMeteoResponse(
            DailyDto(
                time = days,
                precipitationSum = List(15) { 4.0 },
                temperatureMean = List(15) { 15.0 },
                temperatureMax = List(15) { 22.0 },
                temperatureMin = List(15) { 8.0 },
                humidityMean = List(15) { 78.0 }
            )
        )
    }

    @Test fun rain10d_sums_last_10_days() {
        val w = mapToWeatherData(response(), nowEpochMs = 0L)
        assertEquals(40.0, w.rain10dTotal, 0.001)   // 10 * 4
    }

    @Test fun rain7d_and_14d_sum_correct_windows() {
        val w = mapToWeatherData(response(), nowEpochMs = 0L)
        assertEquals(28.0, w.rain7dTotal, 0.001)    // 7 * 4
        assertEquals(56.0, w.rain14dTotal, 0.001)   // 14 * 4
    }

    @Test fun temperature_and_humidity_averaged_over_7d() {
        val w = mapToWeatherData(response(), nowEpochMs = 0L)
        assertEquals(15.0, w.temp7dAvg, 0.001)
        assertEquals(78.0, w.humidity7dAvg, 0.001)
    }

    @Test fun days_since_significant_rain_counts_back_from_today() {
        // 4 mm/day everywhere -> never > 10 mm -> equals window length (15 days)
        val w = mapToWeatherData(response(), nowEpochMs = 0L)
        assertEquals(15, w.daysSinceSignificantRain)
    }
}
