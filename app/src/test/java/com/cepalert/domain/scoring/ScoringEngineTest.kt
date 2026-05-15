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
}
