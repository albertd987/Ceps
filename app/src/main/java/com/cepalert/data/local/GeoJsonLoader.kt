package com.cepalert.data.local

import com.cepalert.data.model.ForestZone
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object GeoJsonLoader {

    fun parse(geoJson: String): List<ForestZone> {
        val root = Json.parseToJsonElement(geoJson).jsonObject
        val features = root["features"]?.jsonArray ?: return emptyList()
        return features.map { feature ->
            val obj = feature.jsonObject
            val props = obj["properties"]!!.jsonObject
            val geometry = obj["geometry"]!!.jsonObject
            ForestZone(
                id = props["id"]!!.jsonPrimitive.content,
                centroidLat = props["centroid_lat"]!!.jsonPrimitive.double,
                centroidLon = props["centroid_lon"]!!.jsonPrimitive.double,
                bosqueCompatible = props["bosque_compatible"]!!.jsonPrimitive.boolean,
                bosqueTipo = props["bosque_tipo"]!!.jsonPrimitive.content,
                altitud = props["altitud"]!!.jsonPrimitive.int,
                orientacion = props["orientacion"]!!.jsonPrimitive.content,
                soilPh = props["soil_ph"]?.jsonPrimitive?.doubleOrNull ?: 5.5,
                geometryJson = (geometry as JsonObject).toString()
            )
        }
    }
}
