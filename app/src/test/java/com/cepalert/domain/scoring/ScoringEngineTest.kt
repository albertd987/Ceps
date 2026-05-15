package com.cepalert.domain.scoring

import org.junit.Assert.assertEquals
import org.junit.Test

class ScoringEngineTest {

    @Test fun humidity_in_optimal_band_is_one() {
        assertEquals(1.0, ScoringEngine.normalizeHumidity(70.0), 0.001)
        assertEquals(1.0, ScoringEngine.normalizeHumidity(80.0), 0.001)
        assertEquals(1.0, ScoringEngine.normalizeHumidity(90.0), 0.001)
    }

    @Test fun humidity_below_optimal_ramps_down_to_zero_at_40pct() {
        assertEquals(0.0, ScoringEngine.normalizeHumidity(40.0), 0.001)
        assertEquals(0.5, ScoringEngine.normalizeHumidity(55.0), 0.001)
    }

    @Test fun humidity_above_optimal_decays_to_zero_at_100pct() {
        assertEquals(0.0, ScoringEngine.normalizeHumidity(100.0), 0.001)
        assertEquals(0.5, ScoringEngine.normalizeHumidity(95.0), 0.001)
    }

    @Test fun humidity_is_clamped_outside_range() {
        assertEquals(0.0, ScoringEngine.normalizeHumidity(10.0), 0.001)
        assertEquals(0.0, ScoringEngine.normalizeHumidity(120.0), 0.001)
    }

    @Test fun rain_in_optimal_band_is_one() {
        assertEquals(1.0, ScoringEngine.normalizeRain10d(30.0), 0.001)
        assertEquals(1.0, ScoringEngine.normalizeRain10d(55.0), 0.001)
        assertEquals(1.0, ScoringEngine.normalizeRain10d(80.0), 0.001)
    }

    @Test fun rain_below_optimal_ramps_from_zero() {
        assertEquals(0.0, ScoringEngine.normalizeRain10d(0.0), 0.001)
        assertEquals(0.5, ScoringEngine.normalizeRain10d(15.0), 0.001)
    }

    @Test fun rain_between_80_and_120_decays() {
        assertEquals(1.0, ScoringEngine.normalizeRain10d(80.0), 0.001)
        assertEquals(0.5, ScoringEngine.normalizeRain10d(100.0), 0.001)
    }

    @Test fun rain_above_120_is_penalized() {
        assertEquals(0.2, ScoringEngine.normalizeRain10d(125.0), 0.001)
        assertEquals(0.2, ScoringEngine.normalizeRain10d(300.0), 0.001)
    }

    @Test fun temperature_in_optimal_band_is_one() {
        assertEquals(1.0, ScoringEngine.normalizeTemperature(10.0), 0.001)
        assertEquals(1.0, ScoringEngine.normalizeTemperature(15.0), 0.001)
        assertEquals(1.0, ScoringEngine.normalizeTemperature(20.0), 0.001)
    }

    @Test fun temperature_decays_linearly_outside_band() {
        assertEquals(0.5, ScoringEngine.normalizeTemperature(5.0), 0.001)
        assertEquals(0.0, ScoringEngine.normalizeTemperature(0.0), 0.001)
        assertEquals(0.5, ScoringEngine.normalizeTemperature(25.0), 0.001)
        assertEquals(0.0, ScoringEngine.normalizeTemperature(30.0), 0.001)
    }

    @Test fun temperature_is_clamped() {
        assertEquals(0.0, ScoringEngine.normalizeTemperature(-10.0), 0.001)
        assertEquals(0.0, ScoringEngine.normalizeTemperature(45.0), 0.001)
    }

    private fun zone(compatible: Boolean) = com.cepalert.data.model.ForestZone(
        id = "z1", centroidLat = 42.4, centroidLon = 1.5,
        bosqueCompatible = compatible,
        bosqueTipo = if (compatible) "pino" else "otro",
        altitud = 1200, orientacion = "N", geometryJson = "{}"
    )

    private fun weather(humidity: Double, rain10d: Double, temp: Double) =
        com.cepalert.data.model.WeatherData(
            humidity7dAvg = humidity, rain10dTotal = rain10d,
            rain7dTotal = 0.0, rain14dTotal = 0.0,
            temp7dAvg = temp, temp7dMax = 0.0, temp7dMin = 0.0,
            daysSinceSignificantRain = 0, source = "test", updatedAtEpochMs = 0L
        )

    @Test fun perfect_conditions_score_is_100() {
        val result = ScoringEngine.score(weather(80.0, 55.0, 15.0), zone(true))
        assertEquals(100, result.score)
        assertEquals(4, result.rows.size)
    }

    @Test fun worst_conditions_score_is_zero() {
        val result = ScoringEngine.score(weather(10.0, 0.0, -10.0), zone(false))
        assertEquals(0, result.score)
    }

    @Test fun incompatible_forest_caps_score_below_80() {
        val result = ScoringEngine.score(weather(80.0, 55.0, 15.0), zone(false))
        assertEquals(80, result.score)
    }
}
