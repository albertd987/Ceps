package com.cepalert.ui.map

import android.graphics.Color
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
import androidx.compose.material3.Button
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
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import com.cepalert.ui.detail.ZoneDetailSheet
import com.cepalert.ui.weather.WeatherBottomSheet
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val SOURCE_ID = "zones-source"
private const val LAYER_ID = "zones-layer"
private const val PROP_SCORE = "score"

@Composable
fun MapScreen(viewModel: MapViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showWeatherSheet by remember { mutableStateOf(false) }
    val selectedZoneState = remember { mutableStateOf<ScoredZone?>(null) }
    var selectedZone by selectedZoneState

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
                            // Single click listener registered once; reads zones from tag
                            map.addOnMapClickListener { latLng ->
                                @Suppress("UNCHECKED_CAST")
                                val zones = (this.tag as? List<ScoredZone>)
                                    ?: return@addOnMapClickListener false
                                val nearest = zones.minByOrNull { sz ->
                                    val dLat = sz.zone.centroidLat - latLng.latitude
                                    val dLon = sz.zone.centroidLon - latLng.longitude
                                    dLat * dLat + dLon * dLon
                                }
                                nearest?.let { sz ->
                                    val dLat = sz.zone.centroidLat - latLng.latitude
                                    val dLon = sz.zone.centroidLon - latLng.longitude
                                    if (dLat * dLat + dLon * dLon < 0.01) {
                                        selectedZoneState.value = sz
                                    }
                                }
                                false
                            }
                        }
                    }
                },
                update = { mapView ->
                    // Keep click listener up-to-date without re-registering it
                    mapView.tag = state.scoredZones

                    val zonesToRender = state.scoredZones
                    if (zonesToRender.isNotEmpty()) {
                        mapView.getMapAsync { map ->
                            map.getStyle { style ->
                                val features = zonesToRender.map { sz ->
                                    Feature.fromGeometry(
                                        Point.fromLngLat(sz.zone.centroidLon, sz.zone.centroidLat)
                                    ).also { f ->
                                        f.addNumberProperty(PROP_SCORE, sz.score?.score ?: -1)
                                    }
                                }
                                val collection = FeatureCollection.fromFeatures(features)
                                val existing = style.getSource(SOURCE_ID) as? GeoJsonSource
                                if (existing != null) {
                                    existing.setGeoJson(collection)
                                } else {
                                    style.addSource(GeoJsonSource(SOURCE_ID, collection))
                                    style.addLayer(
                                        CircleLayer(LAYER_ID, SOURCE_ID).apply {
                                            setProperties(
                                                PropertyFactory.circleRadius(10f),
                                                PropertyFactory.circleOpacity(0.8f),
                                                PropertyFactory.circleColor(
                                                    Expression.step(
                                                        Expression.get(PROP_SCORE),
                                                        Expression.color(Color.parseColor("#9E9E9E")),
                                                        Expression.literal(34), Expression.color(Color.parseColor("#F9A825")),
                                                        Expression.literal(67), Expression.color(Color.parseColor("#1B5E20"))
                                                    )
                                                )
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            AssistChip(
                onClick = {},
                label = { Text(dateLabel) },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
            )

            if (state.isLoading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else if (state.error != null) {
                val errorMessage = state.error
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = errorMessage!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Button(onClick = { viewModel.refresh() }) {
                        Text("Reintentar")
                    }
                }
            }

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

            if (showWeatherSheet) {
                state.weather?.let { weather ->
                    WeatherBottomSheet(
                        weather = weather,
                        onDismiss = { showWeatherSheet = false }
                    )
                }
            }

            selectedZone?.let { zone ->
                ZoneDetailSheet(
                    scoredZone = zone,
                    onDismiss = { selectedZone = null }
                )
            }
        }
    }
}
