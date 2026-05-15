package com.cepalert.data.local

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoreCacheTest {

    private val sixHoursMs = 6 * 60 * 60 * 1000L

    @Test fun cache_is_fresh_within_6_hours() {
        assertTrue(ScoreCache.isFresh(savedAt = 1_000_000L, now = 1_000_000L + sixHoursMs - 1))
    }

    @Test fun cache_is_stale_after_6_hours() {
        assertFalse(ScoreCache.isFresh(savedAt = 1_000_000L, now = 1_000_000L + sixHoursMs + 1))
    }
}
