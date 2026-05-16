package com.cepalert.domain.scoring

object ScoringEngine {

    // Volumetric soil water content 0-7cm (m3/m3). Optimal: 0.20-0.40.
    fun normalizeSoilMoisture(m3: Double): Double = when {
        m3 < 0.05  -> 0.0
        m3 < 0.15  -> (m3 - 0.05) / 0.10 * 0.5
        m3 <= 0.40 -> 0.5 + (m3 - 0.15) / 0.25 * 0.5
        m3 <= 0.50 -> 1.0 - (m3 - 0.40) / 0.10 * 0.4
        else       -> maxOf(0.0, 0.6 - (m3 - 0.50) * 2.0)
    }

    // Soil temperature at 0-7cm. Optimal 8-15°C for mycelium fruiting.
    fun normalizeSoilTemp(tempC: Double): Double = when {
        tempC < 4.0  -> 0.0
        tempC < 8.0  -> (tempC - 4.0) / 4.0 * 0.7
        tempC <= 15.0 -> 1.0
        tempC <= 20.0 -> 1.0 - (tempC - 15.0) / 5.0 * 0.8
        else         -> maxOf(0.0, 0.2 - (tempC - 20.0) * 0.1)
    }

    // Soil cooling week-over-week (positive = cooled). Key autumn fruiting trigger.
    fun normalizeSoilTempDrop(dropC: Double): Double = when {
        dropC >= 4.0  -> 1.0
        dropC >= 2.0  -> 0.7 + (dropC - 2.0) / 2.0 * 0.3
        dropC >= 0.0  -> 0.5 + dropC / 2.0 * 0.2
        dropC >= -2.0 -> 0.5 + dropC / 2.0 * 0.3
        else          -> maxOf(0.0, 0.2 + (dropC + 2.0) * 0.1)
    }

    // Rain trigger: >=15mm in 3 consecutive days. Fruiting window: 8-16 days after trigger.
    fun normalizeRainPattern(triggerMm: Double, triggerDaysAgo: Int): Double {
        if (triggerMm < 10.0) return 0.1
        val base = if (triggerMm < 15.0) (triggerMm - 10.0) / 5.0 * 0.4 else 1.0
        val window = when {
            triggerDaysAgo < 5   -> 0.3
            triggerDaysAgo <= 8  -> 0.3 + (triggerDaysAgo - 5).toDouble() / 3.0 * 0.7
            triggerDaysAgo <= 16 -> 1.0
            triggerDaysAgo <= 25 -> 1.0 - (triggerDaysAgo - 16).toDouble() / 9.0 * 0.7
            else                 -> 0.3
        }
        return base * window
    }

    // Bell curve: viable 500-2200m, peak 900-1500m.
    fun normalizeAltitude(m: Int): Double = when {
        m < 500   -> 0.0
        m < 900   -> ((m - 500.0) / 400.0).coerceIn(0.0, 1.0)
        m <= 1500 -> 1.0
        m <= 2200 -> (1.0 - ((m - 1500.0) / 700.0)).coerceIn(0.0, 1.0)
        else      -> 0.0
    }

    // Beech best, pine good, oak decent.
    fun normalizeForest(tipo: String): Double = when (tipo.lowercase()) {
        "haya"  -> 1.0
        "pino"  -> 0.75
        "roble" -> 0.6
        else    -> 0.3
    }

    // pH<=5 optimal, pH>7 near-zero (calcareous).
    fun normalizeSoilPh(ph: Double): Double = when {
        ph <= 5.0 -> 1.0
        ph <= 6.0 -> 1.0 - ((ph - 5.0) / 1.0) * 0.3
        ph <= 7.0 -> 0.7 - ((ph - 6.0) / 1.0) * 0.6
        else      -> maxOf(0.0, 0.1 - ((ph - 7.0) * 0.1))
    }

    // N/NE retain moisture best in Mediterranean mountains.
    fun normalizeOrientation(o: String): Double = when (o.uppercase()) {
        "N"  -> 1.0; "NE" -> 0.85; "NW" -> 0.75
        "E"  -> 0.6; "W"  -> 0.5
        "SE" -> 0.35; "SW" -> 0.2; "S"  -> 0.1
        else -> 0.5
    }

    // Boletus edulis Pyrenees: peak October, viable Sept-Nov.
    fun seasonalFactor(month: Int): Double = when (month) {
        10 -> 1.0; 9  -> 0.8; 11 -> 0.7
        8  -> 0.3; 12 -> 0.25; 5  -> 0.25
        4  -> 0.15; 6  -> 0.15; 7  -> 0.1
        else -> 0.1
    }

    private val MONTH_NAMES = mapOf(
        1 to "Gener", 2 to "Febrer", 3 to "Març", 4 to "Abril",
        5 to "Maig", 6 to "Juny", 7 to "Juliol", 8 to "Agost",
        9 to "Setembre", 10 to "Octubre", 11 to "Novembre", 12 to "Desembre"
    )

    fun score(
        weather: com.cepalert.data.model.WeatherData,
        zone: com.cepalert.data.model.ForestZone,
        month: Int
    ): com.cepalert.data.model.ScoreResult {
        val nMoist  = normalizeSoilMoisture(weather.soilMoisture7d)
        val nRain   = normalizeRainPattern(weather.rainTriggerMm, weather.triggerDaysAgo)
        val nForest = normalizeForest(zone.bosqueTipo)
        val nPh     = normalizeSoilPh(zone.soilPh)
        val nStemp  = normalizeSoilTemp(weather.soilTemp7d)
        val nDrop   = normalizeSoilTempDrop(weather.soilTempDrop)
        val nAlt    = normalizeAltitude(zone.altitud)
        val nOri    = normalizeOrientation(zone.orientacion)
        val nSeason = seasonalFactor(month)

        val base = (nMoist  * 0.28 + nRain   * 0.20 + nForest * 0.11 +
                    nPh     * 0.10 + nStemp  * 0.11 + nDrop   * 0.10 +
                    nAlt    * 0.07 + nOri    * 0.03)
        val total = base * nSeason

        val triggerText = if (weather.rainTriggerMm >= 10.0)
            "${"%.0f".format(weather.rainTriggerMm)}mm fa ${weather.triggerDaysAgo}d"
        else "Sense detonant"

        val dropSign = if (weather.soilTempDrop >= 0) "+" else ""

        val rows = listOf(
            com.cepalert.data.model.ScoreBreakdownRow(
                "Humitat sòl", "${"%.0f".format(weather.soilMoisture7d * 100)} %", nMoist, 0.28),
            com.cepalert.data.model.ScoreBreakdownRow(
                "Patró pluja", triggerText, nRain, 0.20),
            com.cepalert.data.model.ScoreBreakdownRow(
                "Tipus de bosc", zone.bosqueTipo, nForest, 0.11),
            com.cepalert.data.model.ScoreBreakdownRow(
                "Temp sòl", "${"%.1f".format(weather.soilTemp7d)} °C", nStemp, 0.11),
            com.cepalert.data.model.ScoreBreakdownRow(
                "pH del sòl", "%.1f".format(zone.soilPh), nPh, 0.10),
            com.cepalert.data.model.ScoreBreakdownRow(
                "Refredament", "${dropSign}${"%.1f".format(weather.soilTempDrop)} °C", nDrop, 0.10),
            com.cepalert.data.model.ScoreBreakdownRow(
                "Altitud", "${zone.altitud} m", nAlt, 0.07),
            com.cepalert.data.model.ScoreBreakdownRow(
                "Orientació", zone.orientacion, nOri, 0.03),
            com.cepalert.data.model.ScoreBreakdownRow(
                "Estació", MONTH_NAMES[month] ?: "$month", nSeason, 0.0),
        )
        return com.cepalert.data.model.ScoreResult(
            zoneId = zone.id,
            score  = Math.round(total * 100).toInt().coerceIn(0, 100),
            rows   = rows
        )
    }
}
