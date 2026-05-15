package com.cepalert.data.repository

import android.content.Context
import com.cepalert.data.local.GeoJsonLoader
import com.cepalert.data.model.ForestZone
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ForestRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var cached: List<ForestZone>? = null

    suspend fun loadZones(): List<ForestZone> = withContext(Dispatchers.IO) {
        cached ?: run {
            val text = context.assets.open("forest_zones.geojson")
                .bufferedReader().use { it.readText() }
            GeoJsonLoader.parse(text).also { cached = it }
        }
    }
}
