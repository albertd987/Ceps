package com.cepalert.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoJsonLoaderTest {

    private val sample = """
    {"type":"FeatureCollection","features":[
      {"type":"Feature","geometry":{"type":"Point","coordinates":[1.5,42.4]},
       "properties":{"id":"zone-1","centroid_lat":42.4,"centroid_lon":1.5,
         "bosque_compatible":true,"bosque_tipo":"pino","altitud":1200,"orientacion":"N"}}
    ]}""".trimIndent()

    @Test fun parses_features_into_forest_zones() {
        val zones = GeoJsonLoader.parse(sample)
        assertEquals(1, zones.size)
        val z = zones.first()
        assertEquals("zone-1", z.id)
        assertEquals(42.4, z.centroidLat, 0.0001)
        assertTrue(z.bosqueCompatible)
        assertEquals("pino", z.bosqueTipo)
        assertEquals(1200, z.altitud)
    }
}
