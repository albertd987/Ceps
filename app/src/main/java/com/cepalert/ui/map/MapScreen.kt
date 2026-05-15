package com.cepalert.ui.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private fun scoreColorHex(score: Int?): String = when {
    score == null -> "#9E9E9E"
    score >= 67 -> "#1B5E20"
    score >= 34 -> "#F9A825"
    else -> "#9E9E9E"
}

@Composable
fun MapScreen(viewModel: MapViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showWeatherSheet by remember { mutableStateOf(false) }

    val dateLabel = remember {
        LocalDate.now().format(DateTimeFormatter.ofPattern("dd MMM", Locale("ca")))
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.forceRefresh() }) {
                Icon(Icons.Default.Refresh, contentDescription = "Refrescar")
            }
        }
    ) { innerPadding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
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

            // Date chip top-start
            AssistChip(
                onClick = {},
                label = { Text(dateLabel) },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
            )

            // Loading spinner
            if (state.isLoading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }

            // Bottom conditions banner
            state.weather?.let { weather ->
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .clickable { showWeatherSheet = true },
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    tonalElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Text("🌧 ${weather.rain10dTotal.toInt()} mm")
                        Text("🌡 ${weather.temp7dAvg.toInt()} °C")
                        Text("💧 ${weather.humidity7dAvg.toInt()} %")
                    }
                }
            }
        }
    }
}
