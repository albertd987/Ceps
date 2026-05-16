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

    // Bell curve: viable 500–2200m, peak 900–1500m. Below 500m too warm/wrong forest.
    fun normalizeAltitude(m: Int): Double = when {
        m < 500  -> 0.0
        m < 900  -> ((m - 500.0) / 400.0).coerceIn(0.0, 1.0)
        m <= 1500 -> 1.0
        m <= 2200 -> (1.0 - ((m - 1500.0) / 700.0)).coerceIn(0.0, 1.0)
        else -> 0.0
    }

    // Beech best (mycorrhiza + moisture), pine good, oak decent (lower altitude)
    fun normalizeForest(tipo: String): Double = when (tipo.lowercase()) {
        "haya"  -> 1.0
        "pino"  -> 0.75
        "roble" -> 0.6
        else    -> 0.3
    }

    // Boletus needs acidic soil (pH 3.5-6.0). Calcareous (pH>7) = near-zero chance.
    fun normalizeSoilPh(ph: Double): Double = when {
        ph <= 5.0 -> 1.0
        ph <= 6.0 -> 1.0 - ((ph - 5.0) / 1.0) * 0.3   // 1.0 → 0.7
        ph <= 7.0 -> 0.7 - ((ph - 6.0) / 1.0) * 0.6   // 0.7 → 0.1
        else      -> maxOf(0.0, 0.1 - ((ph - 7.0) * 0.1))
    }

    // N/NE retain moisture best; S/SW are driest
    fun normalizeOrientation(o: String): Double = when (o.uppercase()) {
        "N"  -> 1.0
        "NE" -> 0.85
        "NW" -> 0.75
        "E"  -> 0.6
        "W"  -> 0.5
        "SE" -> 0.35
        "SW" -> 0.2
        "S"  -> 0.1
        else -> 0.5  // unknown
    }

    // Boletus edulis Pyrenees: peak October, viable Sept–Nov, rare otherwise
    fun seasonalFactor(month: Int): Double = when (month) {
        10 -> 1.0
        9  -> 0.8
        11 -> 0.7
        8  -> 0.3
        12 -> 0.25
        5  -> 0.25
        4  -> 0.15
        6  -> 0.15
        7  -> 0.1
        else -> 0.1  // Jan, Feb, Mar
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
        val nHumidity    = normalizeHumidity(weather.humidity7dAvg)
        val nRain        = normalizeRain10d(weather.rain10dTotal)
        val nForest      = normalizeForest(zone.bosqueTipo)
        val nTemp        = normalizeTemperature(weather.temp7dAvg)
        val nAltitude    = normalizeAltitude(zone.altitud)
        val nOrientation = normalizeOrientation(zone.orientacion)
        val nSoilPh      = normalizeSoilPh(zone.soilPh)
        val nSeason      = seasonalFactor(month)

        // Weights sum to 1.0; season applied as multiplier
        val base = nHumidity * 0.30 + nRain * 0.22 + nForest * 0.13 +
                   nSoilPh * 0.12 + nTemp * 0.09 + nAltitude * 0.09 + nOrientation * 0.05
        val total = base * nSeason

        val rows = listOf(
            com.cepalert.data.model.ScoreBreakdownRow(
                "Humitat", "${weather.humidity7dAvg.toInt()} %", nHumidity, 0.30),
            com.cepalert.data.model.ScoreBreakdownRow(
                "Pluja 10d", "${weather.rain10dTotal.toInt()} mm", nRain, 0.22),
            com.cepalert.data.model.ScoreBreakdownRow(
                "Tipus de bosc", zone.bosqueTipo, nForest, 0.13),
            com.cepalert.data.model.ScoreBreakdownRow(
                "pH del sòl", "%.1f".format(zone.soilPh), nSoilPh, 0.12),
            com.cepalert.data.model.ScoreBreakdownRow(
                "Temperatura", "${weather.temp7dAvg.toInt()} °C", nTemp, 0.09),
            com.cepalert.data.model.ScoreBreakdownRow(
                "Altitud", "${zone.altitud} m", nAltitude, 0.09),
            com.cepalert.data.model.ScoreBreakdownRow(
                "Orientació", zone.orientacion, nOrientation, 0.05),
            com.cepalert.data.model.ScoreBreakdownRow(
                "Estació", MONTH_NAMES[month] ?: "$month", nSeason, 0.0),
        )
        return com.cepalert.data.model.ScoreResult(
            zoneId = zone.id,
            score = Math.round(total * 100).toInt().coerceIn(0, 100),
            rows = rows
        )
    }
}
