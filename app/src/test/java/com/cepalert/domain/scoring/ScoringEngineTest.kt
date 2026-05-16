package com.cepalert.domain.scoring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test fun rain_in_optimal_band_is_one() {
        assertEquals(1.0, ScoringEngine.normalizeRain10d(30.0), 0.001)
        assertEquals(1.0, ScoringEngine.normalizeRain10d(80.0), 0.001)
    }

    @Test fun rain_below_optimal_ramps_from_zero() {
        assertEquals(0.0, ScoringEngine.normalizeRain10d(0.0), 0.001)
    }

    @Test fun temperature_in_optimal_band_is_one() {
        assertEquals(1.0, ScoringEngine.normalizeTemperature(10.0), 0.001)
        assertEquals(1.0, ScoringEngine.normalizeTemperature(20.0), 0.001)
    }

    @Test fun altitude_optimal_band_is_one() {
        assertEquals(1.0, ScoringEngine.normalizeAltitude(900), 0.001)
        assertEquals(1.0, ScoringEngine.normalizeAltitude(1400), 0.001)
        assertEquals(1.0, ScoringEngine.normalizeAltitude(1800), 0.001)
    }

    @Test fun altitude_below_400m_is_zero() {
        assertEquals(0.0, ScoringEngine.normalizeAltitude(400), 0.001)
        assertEquals(0.0, ScoringEngine.normalizeAltitude(0), 0.001)
    }

    @Test fun altitude_above_2100m_is_zero() {
        assertEquals(0.0, ScoringEngine.normalizeAltitude(2200), 0.001)
    }

    @Test fun orientation_north_is_best() {
        assertEquals(1.0, ScoringEngine.normalizeOrientation("N"), 0.001)
        assertTrue(ScoringEngine.normalizeOrientation("NE") > ScoringEngine.normalizeOrientation("S"))
    }

    @Test fun orientation_south_is_worst() {
        assertTrue(ScoringEngine.normalizeOrientation("S") < 0.2)
        assertTrue(ScoringEngine.normalizeOrientation("SW") < ScoringEngine.normalizeOrientation("N"))
    }

    @Test fun seasonal_factor_october_is_peak() {
        assertEquals(1.0, ScoringEngine.seasonalFactor(10), 0.001)
    }

    @Test fun seasonal_factor_may_is_low() {
        assertTrue(ScoringEngine.seasonalFactor(5) <= 0.25)
    }

    @Test fun seasonal_factor_summer_is_minimal() {
        assertTrue(ScoringEngine.seasonalFactor(7) <= 0.15)
    }

    private fun zone(compatible: Boolean, altitud: Int = 1200, orientacion: String = "N") =
        com.cepalert.data.model.ForestZone(
            id = "z1", centroidLat = 42.4, centroidLon = 1.5,
            bosqueCompatible = compatible,
            bosqueTipo = if (compatible) "pino" else "otro",
            altitud = altitud, orientacion = orientacion, geometryJson = "{}"
        )

    private fun weather(humidity: Double, rain10d: Double, temp: Double) =
        com.cepalert.data.model.WeatherData(
            humidity7dAvg = humidity, rain10dTotal = rain10d,
            rain7dTotal = 0.0, rain14dTotal = 0.0,
            temp7dAvg = temp, temp7dMax = 0.0, temp7dMin = 0.0,
            daysSinceSignificantRain = 0, source = "test", updatedAtEpochMs = 0L
        )

    @Test fun perfect_conditions_october_score_is_100() {
        // Optimal weather + compatible + optimal altitude + N orientation + October
        val result = ScoringEngine.score(weather(80.0, 55.0, 15.0), zone(true, 1200, "N"), month = 10)
        assertEquals(100, result.score)
        assertEquals(7, result.rows.size)
    }

    @Test fun worst_conditions_score_is_zero() {
        val result = ScoringEngine.score(weather(10.0, 0.0, -10.0), zone(false, 200, "S"), month = 1)
        assertEquals(0, result.score)
    }

    @Test fun north_zone_scores_higher_than_south_zone_same_weather() {
        val w = weather(80.0, 55.0, 15.0)
        val north = ScoringEngine.score(w, zone(true, 1200, "N"), month = 10)
        val south = ScoringEngine.score(w, zone(true, 1200, "S"), month = 10)
        assertTrue("N slope should score higher than S slope", north.score > south.score)
    }

    @Test fun optimal_altitude_scores_higher_than_low_altitude() {
        val w = weather(80.0, 55.0, 15.0)
        val high = ScoringEngine.score(w, zone(true, 1400, "N"), month = 10)
        val low  = ScoringEngine.score(w, zone(true, 300, "N"), month = 10)
        assertTrue("Optimal altitude should score higher than lowland", high.score > low.score)
    }

    @Test fun may_score_is_fraction_of_october() {
        val w = weather(80.0, 55.0, 15.0)
        val oct = ScoringEngine.score(w, zone(true), month = 10)
        val may = ScoringEngine.score(w, zone(true), month = 5)
        assertTrue("May score should be much lower than October", may.score < oct.score / 2)
    }

    @Test fun seasonal_row_has_weight_zero() {
        val result = ScoringEngine.score(weather(80.0, 55.0, 15.0), zone(true), month = 10)
        val seasonRow = result.rows.find { it.label == "Estació" }!!
        assertEquals(0.0, seasonRow.weight, 0.001)
    }
}
