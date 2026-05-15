package com.cepalert.ui.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.Point

private fun scoreColorHex(score: Int?): String = when {
    score == null -> "#9E9E9E"
    score >= 67 -> "#1B5E20"
    score >= 34 -> "#F9A825"
    else -> "#9E9E9E"
}

@Composable
fun MapScreen(viewModel: MapViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { context ->
                MapLibre.getInstance(context)
                MapView(context).apply {
                    getMapAsync { map ->
                        map.setStyle(Style.Builder().fromUri("https://demotiles.maplibre.org/style.json"))
                        map.cameraPosition = CameraPosition.Builder()
                            .target(LatLng(42.4, 1.5))
                            .zoom(8.0)
                            .build()
                    }
                }
            },
            update = { mapView ->
                mapView.getMapAsync { map ->
                    val style = map.style ?: return@getMapAsync
                    state.scoredZones.forEachIndexed { index, scoredZone ->
                        val sourceId = "zone-source-$index"
                        val layerId = "zone-layer-$index"
                        if (style.getSource(sourceId) == null) {
                            style.addSource(
                                GeoJsonSource(
                                    sourceId,
                                    Feature.fromGeometry(
                                        Point.fromLngLat(
                                            scoredZone.zone.centroidLon,
                                            scoredZone.zone.centroidLat
                                        )
                                    )
                                )
                            )
                            style.addLayer(
                                CircleLayer(layerId, sourceId).apply {
                                    setProperties(
                                        PropertyFactory.circleRadius(10f),
                                        PropertyFactory.circleColor(
                                            scoreColorHex(scoredZone.score?.score)
                                        ),
                                        PropertyFactory.circleOpacity(0.8f)
                                    )
                                }
                            )
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (state.isLoading) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
    }
}
