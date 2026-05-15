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

    fun normalizeTemperature(tempC: Double): Double = when {
        tempC in 10.0..20.0 -> 1.0
        tempC < 10.0 -> (1.0 - (10.0 - tempC) / 10.0).coerceIn(0.0, 1.0)
        else -> (1.0 - (tempC - 20.0) / 10.0).coerceIn(0.0, 1.0)
    }

    fun score(
        weather: com.cepalert.data.model.WeatherData,
        zone: com.cepalert.data.model.ForestZone
    ): com.cepalert.data.model.ScoreResult {
        val nHumidity = normalizeHumidity(weather.humidity7dAvg)
        val nRain = normalizeRain10d(weather.rain10dTotal)
        val nForest = if (zone.bosqueCompatible) 1.0 else 0.0
        val nTemp = normalizeTemperature(weather.temp7dAvg)

        val total = nHumidity * 0.4 + nRain * 0.3 + nForest * 0.2 + nTemp * 0.1
        val rows = listOf(
            com.cepalert.data.model.ScoreBreakdownRow(
                "Humedad", "${weather.humidity7dAvg.toInt()} %", nHumidity, 0.4),
            com.cepalert.data.model.ScoreBreakdownRow(
                "Lluvia 10d", "${weather.rain10dTotal.toInt()} mm", nRain, 0.3),
            com.cepalert.data.model.ScoreBreakdownRow(
                "Tipo de bosque", zone.bosqueTipo, nForest, 0.2),
            com.cepalert.data.model.ScoreBreakdownRow(
                "Temperatura", "${weather.temp7dAvg.toInt()} °C", nTemp, 0.1),
        )
        return com.cepalert.data.model.ScoreResult(
            zoneId = zone.id,
            score = Math.round(total * 100).toInt().coerceIn(0, 100),
            rows = rows
        )
    }
}
