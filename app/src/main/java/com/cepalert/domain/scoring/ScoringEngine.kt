package com.cepalert.domain.scoring

object ScoringEngine {

    fun normalizeHumidity(rh: Double): Double = when {
        rh in 70.0..90.0 -> 1.0
        rh < 70.0 -> ((rh - 40.0) / 30.0).coerceIn(0.0, 1.0)
        else -> ((100.0 - rh) / 10.0).coerceIn(0.0, 1.0)
    }

    fun normalizeRain10d(mm: Double): Double = when {
        mm in 30.0..80.0 -> 1.0
        mm < 30.0 -> (mm / 30.0).coerceIn(0.0, 1.0)
        mm <= 120.0 -> 1.0 - ((mm - 80.0) / 40.0)
        else -> 0.2
    }
}
