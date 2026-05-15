package com.cepalert.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ScoreBreakdownRow(
    val label: String,
    val rawValueText: String,         // human-readable, e.g. "78 %"
    val normalized: Double,           // 0..1
    val weight: Double                // 0..1
)

@Serializable
data class ScoreResult(
    val zoneId: String,
    val score: Int,                   // 0..100
    val rows: List<ScoreBreakdownRow>
)
