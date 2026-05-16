package com.cepalert.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ForestZone(
    val id: String,
    val centroidLat: Double,
    val centroidLon: Double,
    val bosqueCompatible: Boolean,
    val bosqueTipo: String,           // "pino" | "haya" | "roble" | "otro"
    val altitud: Int,                 // mean altitude, meters
    val orientacion: String,          // "N", "NE", ...
    val soilPh: Double,               // topsoil pH (SoilGrids 0-5cm), default 5.5
    val geometryJson: String          // raw GeoJSON geometry string, for map rendering
)
