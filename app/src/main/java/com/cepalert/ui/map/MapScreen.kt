package com.cepalert.ui.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cepalert.ui.detail.ZoneDetailSheet
import com.cepalert.ui.weather.WeatherBottomSheet
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val SOURCE_ID = "zones-source"
private const val LAYER_ID  = "zones-layer"
private const val PROP_SCORE = "score"

private val BgPanel   = Color(0xCC0D1117)
private val BgStrip   = Color(0xF00D1117)
private val TextMuted = Color(0xFF78909C)
private val Divider   = Color(0xFF263238)
private val ColorLow  = Color(0xFF546E7A)
private val ColorMid  = Color(0xFFFFB300)
private val ColorHigh = Color(0xFF00E676)
private val ColorBlue = Color(0xFF00B0FF)

@SuppressLint("MissingPermission")
private fun enableLocation(map: MapLibreMap, style: Style, context: android.content.Context) {
    val lc = map.locationComponent
    lc.activateLocationComponent(
        LocationComponentActivationOptions.builder(context, style).build()
    )
    lc.isLocationComponentEnabled = true
    lc.cameraMode = CameraMode.NONE
    lc.renderMode = RenderMode.COMPASS
}

@SuppressLint("MissingPermission")
@Composable
fun MapScreen(viewModel: MapViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showWeatherSheet by remember { mutableStateOf(false) }
    val selectedZoneState = remember { mutableStateOf<ScoredZone?>(null) }
    var selectedZone by selectedZoneState

    var locationGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> locationGranted = granted }

    val mapRef   = remember { mutableStateOf<MapLibreMap?>(null) }
    val styleRef = remember { mutableStateOf<Style?>(null) }

    LaunchedEffect(locationGranted) {
        if (locationGranted) {
            val map   = mapRef.value   ?: return@LaunchedEffect
            val style = styleRef.value ?: return@LaunchedEffect
            enableLocation(map, style, context)
        }
    }

    val dateLabel = remember {
        LocalDate.now().format(DateTimeFormatter.ofPattern("dd MMM", Locale("ca")))
    }

    Box(Modifier.fillMaxSize()) {

        // ── Map ──────────────────────────────────────────────────────────────
        AndroidView(
            factory = { ctx ->
                MapLibre.getInstance(ctx)
                MapView(ctx).apply {
                    getMapAsync { map ->
                        mapRef.value = map
                        map.setStyle(Style.Builder().fromUri("https://tiles.openfreemap.org/styles/dark")) { style ->
                            styleRef.value = style
                            if (locationGranted) enableLocation(map, style, ctx)
                        }
                        map.cameraPosition = CameraPosition.Builder()
                            .target(LatLng(42.4, 1.5))
                            .zoom(8.0)
                            .build()
                        map.addOnMapClickListener { latLng ->
                            @Suppress("UNCHECKED_CAST")
                            val zones = (this.tag as? List<ScoredZone>) ?: return@addOnMapClickListener false
                            val nearest = zones.minByOrNull { sz ->
                                val dLat = sz.zone.centroidLat - latLng.latitude
                                val dLon  = sz.zone.centroidLon - latLng.longitude
                                dLat * dLat + dLon * dLon
                            }
                            nearest?.let { sz ->
                                val dLat = sz.zone.centroidLat - latLng.latitude
                                val dLon  = sz.zone.centroidLon - latLng.longitude
                                if (dLat * dLat + dLon * dLon < 0.01) selectedZoneState.value = sz
                            }
                            false
                        }
                    }
                }
            },
            update = { mapView ->
                mapView.tag = state.scoredZones
                if (state.scoredZones.isNotEmpty()) {
                    mapView.getMapAsync { map ->
                        map.getStyle { style ->
                            val features = state.scoredZones.map { sz ->
                                Feature.fromGeometry(
                                    Point.fromLngLat(sz.zone.centroidLon, sz.zone.centroidLat)
                                ).also { it.addNumberProperty(PROP_SCORE, sz.score?.score ?: -1) }
                            }
                            val collection = FeatureCollection.fromFeatures(features)
                            val existing = style.getSource(SOURCE_ID) as? GeoJsonSource
                            if (existing != null) {
                                existing.setGeoJson(collection)
                            } else {
                                style.addSource(GeoJsonSource(SOURCE_ID, collection))
                                style.addLayer(CircleLayer(LAYER_ID, SOURCE_ID).apply {
                                    setProperties(
                                        PropertyFactory.circleRadius(
                                            Expression.interpolate(
                                                Expression.linear(), Expression.zoom(),
                                                Expression.literal(6),  Expression.literal(2.5f),
                                                Expression.literal(9),  Expression.literal(5f),
                                                Expression.literal(11), Expression.literal(8f),
                                                Expression.literal(13), Expression.literal(12f)
                                            )
                                        ),
                                        PropertyFactory.circleOpacity(0.85f),
                                        PropertyFactory.circleStrokeWidth(1f),
                                        PropertyFactory.circleStrokeColor(AndroidColor.parseColor("#0D1117")),
                                        PropertyFactory.circleColor(
                                            Expression.step(
                                                Expression.get(PROP_SCORE),
                                                Expression.color(AndroidColor.parseColor("#546E7A")),
                                                Expression.literal(34), Expression.color(AndroidColor.parseColor("#FFB300")),
                                                Expression.literal(67), Expression.color(AndroidColor.parseColor("#00E676"))
                                            )
                                        )
                                    )
                                })
                            }
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // ── Top-left: date label ─────────────────────────────────────────────
        Text(
            text = dateLabel,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = 16.dp, top = 12.dp)
                .background(BgPanel, RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            color = Color.White,
            style = MaterialTheme.typography.labelLarge
        )

        // ── Top-right: circular action buttons ───────────────────────────────
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(end = 12.dp, top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            MapIconButton(onClick = { viewModel.forceRefresh() }) {
                androidx.compose.material3.Icon(Icons.Default.Refresh, contentDescription = "Refrescar", tint = Color.White)
            }
            MapIconButton(
                onClick = {
                    if (locationGranted) {
                        mapRef.value?.locationComponent?.lastKnownLocation?.let { loc ->
                            mapRef.value?.animateCamera(
                                CameraUpdateFactory.newLatLngZoom(LatLng(loc.latitude, loc.longitude), 12.0)
                            )
                        }
                    } else {
                        permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                }
            ) {
                androidx.compose.material3.Icon(
                    Icons.Default.MyLocation,
                    contentDescription = "La meva ubicació",
                    tint = if (locationGranted) ColorBlue else TextMuted
                )
            }
        }

        // ── Loading / error ──────────────────────────────────────────────────
        if (state.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = ColorMid
            )
        } else if (state.error != null) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(state.error!!, color = MaterialTheme.colorScheme.error)
                Button(onClick = { viewModel.refresh() }) { Text("Reintentar") }
            }
        }

        // ── Bottom: legend + weather strip ───────────────────────────────────
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .background(BgPanel, RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LegendItem(ColorLow, "Baix")
                LegendItem(ColorMid, "Mitjà")
                LegendItem(ColorHigh, "Alt")
            }

            state.weather?.let { weather ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showWeatherSheet = true },
                    color = BgStrip
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 24.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        WeatherStat("Pluja 14d", "${weather.rain14dTotal.toInt()} mm")
                        StripDivider()
                        WeatherStat("Temp sòl", "${"%.1f".format(weather.soilTemp7d)} °C")
                        StripDivider()
                        WeatherStat("Humitat sòl", "${"%.0f".format(weather.soilMoisture7d * 100)} %")
                    }
                }
            }
        }

        // ── Sheets ───────────────────────────────────────────────────────────
        if (showWeatherSheet) {
            state.weather?.let { WeatherBottomSheet(it, onDismiss = { showWeatherSheet = false }) }
        }
        selectedZone?.let {
            ZoneDetailSheet(scoredZone = it, onDismiss = { selectedZone = null })
        }
    }
}

@Composable
private fun MapIconButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    SmallFloatingActionButton(
        onClick = onClick,
        containerColor = BgPanel,
        contentColor = Color.White,
        elevation = FloatingActionButtonDefaults.elevation(4.dp)
    ) { content() }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Text(label, color = Color.White, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun WeatherStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Color.White, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Text(label, color = TextMuted, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun StripDivider() {
    Box(Modifier.width(1.dp).height(28.dp).background(Divider))
}
