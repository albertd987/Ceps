# CepAlert MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build and ship to Google Play Store an Android app that predicts *Boletus edulis* zones in the Catalan Pyrenees by combining Open-Meteo weather with precomputed forest-cover data and a manual scoring model.

**Architecture:** No backend. A one-off Python pipeline turns ICGC MFE50 + IGN MDT05 data into a `forest_zones.geojson` asset bundled in the APK. At runtime the app loads that asset, calls Open-Meteo per zone centroid, computes a 0–100 score per zone with a pure `ScoringEngine`, and renders a heatmap on a MapLibre map. Results cache 6 h on disk. MVVM + Repository + Hilt.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, MapLibre GL Android SDK, Retrofit + OkHttp + kotlinx-serialization, Coroutines + Flow, Hilt, JUnit + Turbine + MockK; Python 3.11 + geopandas + rasterio for the data pipeline.

---

## File Structure

**Python pipeline** (`tools/build_geojson/`)
- `build_geojson.py` — orchestrator: clip → filter → simplify → enrich → export.
- `clip.py` — clip MFE50 shapefile to the Pyrenees bounding box.
- `forest_filter.py` — pure function: dominant-species string → `bosque_compatible` bool + `bosque_tipo`.
- `enrich.py` — sample MDT05 raster for mean altitude + aspect per polygon.
- `requirements.txt`, `README.md` — how to run it.
- `tests/test_forest_filter.py` — unit tests for the pure filter.

**Android app** (`app/src/main/java/com/cepalert/`)
- `data/model/WeatherData.kt`, `ForestZone.kt`, `ScoreResult.kt` — data classes.
- `data/api/OpenMeteoApi.kt`, `OpenMeteoDto.kt` — Retrofit interface + JSON DTOs.
- `data/local/GeoJsonLoader.kt` — parse bundled `forest_zones.geojson` into `List<ForestZone>`.
- `data/local/ScoreCache.kt` — 6 h disk cache of `List<ScoreResult>`.
- `data/repository/ForestRepository.kt` — exposes forest zones.
- `data/repository/WeatherRepository.kt` — fetches weather per coordinate, maps DTO → `WeatherData`.
- `domain/scoring/ScoringEngine.kt` — pure scoring logic.
- `ui/map/MapScreen.kt`, `MapViewModel.kt` — main map + heatmap.
- `ui/detail/ZoneDetailSheet.kt`, `DetailViewModel.kt` — zone detail.
- `ui/weather/WeatherBottomSheet.kt` — weather conditions sheet.
- `ui/theme/` — Material 3 theme.
- `di/AppModule.kt` — Hilt bindings.
- `CepAlertApplication.kt`, `MainActivity.kt`.

**Assets:** `app/src/main/assets/forest_zones.geojson`.

---

## Phase 0 — Project setup

### Task 0.1: Create the Android project skeleton

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `app/build.gradle.kts`, `gradle/libs.versions.toml`
- Create: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Generate the project**

Create the project in Android Studio (Empty Compose Activity, package `com.cepalert`, min SDK 26, target SDK 34, Kotlin DSL), OR scaffold manually. Confirm `./gradlew tasks` runs.

- [ ] **Step 2: Add dependencies to `gradle/libs.versions.toml`**

```toml
[versions]
hilt = "2.51.1"
retrofit = "2.11.0"
okhttp = "4.12.0"
serialization = "1.6.3"
maplibre = "11.5.0"
coroutines = "1.8.1"
turbine = "1.1.0"
mockk = "1.13.12"

[libraries]
hilt-android = { module = "com.google.dagger:hilt-android", version.ref = "hilt" }
hilt-compiler = { module = "com.google.dagger:hilt-android-compiler", version.ref = "hilt" }
retrofit = { module = "com.squareup.retrofit2:retrofit", version.ref = "retrofit" }
retrofit-serialization = { module = "com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter", version = "1.0.0" }
okhttp = { module = "com.squareup.okhttp3:okhttp", version.ref = "okhttp" }
okhttp-logging = { module = "com.squareup.okhttp3:logging-interceptor", version.ref = "okhttp" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "serialization" }
maplibre = { module = "org.maplibre.gl:android-sdk", version.ref = "maplibre" }
coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }
coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }
turbine = { module = "app.cash.turbine:turbine", version.ref = "turbine" }
mockk = { module = "io.mockk:mockk", version.ref = "mockk" }
```

Add Hilt, kotlin-serialization, and ksp plugins in `app/build.gradle.kts`. Enable Compose.

- [ ] **Step 3: Internet permission**

In `AndroidManifest.xml`, inside `<manifest>`: `<uses-permission android:name="android.permission.INTERNET" />`.

- [ ] **Step 4: Verify build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add settings.gradle.kts build.gradle.kts app/ gradle/
git commit -m "chore: scaffold Android project with Compose, Hilt, MapLibre deps"
```

### Task 0.2: Hilt application class

**Files:**
- Create: `app/src/main/java/com/cepalert/CepAlertApplication.kt`
- Modify: `AndroidManifest.xml`

- [ ] **Step 1: Application class**

```kotlin
package com.cepalert

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class CepAlertApplication : Application()
```

- [ ] **Step 2: Register in manifest**

Add `android:name=".CepAlertApplication"` to the `<application>` tag.

- [ ] **Step 3: Verify build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/cepalert/CepAlertApplication.kt app/src/main/AndroidManifest.xml
git commit -m "chore: add Hilt application class"
```

---

## Phase 1 — Forest data pipeline (Python)

### Task 1.1: Pure forest-species filter

**Files:**
- Create: `tools/build_geojson/forest_filter.py`
- Test: `tools/build_geojson/tests/test_forest_filter.py`
- Create: `tools/build_geojson/requirements.txt`

- [ ] **Step 1: Write the failing test**

```python
# tools/build_geojson/tests/test_forest_filter.py
from forest_filter import classify_forest

def test_pine_is_compatible():
    assert classify_forest("Pinus sylvestris") == (True, "pino")

def test_beech_is_compatible():
    assert classify_forest("Fagus sylvatica") == (True, "haya")

def test_oak_is_compatible():
    assert classify_forest("Quercus pubescens") == (True, "roble")

def test_unknown_species_is_not_compatible():
    assert classify_forest("Eucalyptus globulus") == (False, "otro")

def test_empty_or_none_is_not_compatible():
    assert classify_forest("") == (False, "otro")
    assert classify_forest(None) == (False, "otro")
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd tools/build_geojson && python -m pytest tests/test_forest_filter.py -v`
Expected: FAIL — `ModuleNotFoundError: No module named 'forest_filter'`.

- [ ] **Step 3: Implement `forest_filter.py`**

```python
# tools/build_geojson/forest_filter.py
"""Maps MFE50 dominant-species names to compatibility for Boletus edulis."""

_COMPATIBLE = {
    "pino": ("pin", "pinus"),
    "haya": ("fag", "fagus", "haya"),
    "roble": ("quercus", "roure", "roble"),
}


def classify_forest(species: str | None) -> tuple[bool, str]:
    if not species:
        return (False, "otro")
    s = species.strip().lower()
    for label, keywords in _COMPATIBLE.items():
        if any(k in s for k in keywords):
            return (True, label)
    return (False, "otro")
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd tools/build_geojson && python -m pytest tests/test_forest_filter.py -v`
Expected: PASS — 5 passed.

- [ ] **Step 5: Write `requirements.txt`**

```
geopandas==1.0.1
rasterio==1.3.11
shapely==2.0.6
pytest==8.3.3
```

- [ ] **Step 6: Commit**

```bash
git add tools/build_geojson/
git commit -m "feat: add forest-species classifier for data pipeline"
```

### Task 1.2: Clip MFE50 to the Pyrenees bounding box

**Files:**
- Create: `tools/build_geojson/clip.py`

- [ ] **Step 1: Implement `clip.py`**

```python
# tools/build_geojson/clip.py
"""Clips the MFE50 shapefile to the Pre-Pyrenees + Pyrenees bounding box."""
import geopandas as gpd

# Bounding box (WGS84): Pre-Pyrenees + Pyrenees catalanes.
BBOX = (0.5, 42.0, 2.2, 42.85)  # minx, miny, maxx, maxy


def clip_mfe50(shapefile_path: str) -> gpd.GeoDataFrame:
    gdf = gpd.read_file(shapefile_path)
    gdf = gdf.to_crs(epsg=4326)
    minx, miny, maxx, maxy = BBOX
    return gdf.cx[minx:maxx, miny:maxy].copy()
```

- [ ] **Step 2: Manual verification**

Download MFE50 for Catalunya from the ICGC portal into `tools/build_geojson/data/mfe50.shp` (+ sidecar files). In a Python REPL: `from clip import clip_mfe50; print(len(clip_mfe50("data/mfe50.shp")))`.
Expected: a non-zero polygon count, far smaller than the full dataset.

- [ ] **Step 3: Commit**

```bash
git add tools/build_geojson/clip.py
git commit -m "feat: add MFE50 bounding-box clip step"
```

### Task 1.3: Enrich polygons with altitude and aspect

**Files:**
- Create: `tools/build_geojson/enrich.py`

- [ ] **Step 1: Implement `enrich.py`**

```python
# tools/build_geojson/enrich.py
"""Adds mean altitude and aspect per polygon from the MDT05 DEM raster."""
import numpy as np
import rasterio
from rasterio.mask import mask

_ASPECT_LABELS = ["N", "NE", "E", "SE", "S", "SO", "O", "NO"]


def _aspect_label(degrees: float) -> str:
    idx = int(((degrees + 22.5) % 360) / 45)
    return _ASPECT_LABELS[idx]


def enrich_zone(geometry, dem_path: str) -> tuple[int, str]:
    """Returns (mean_altitude_m, aspect_label) for one polygon."""
    with rasterio.open(dem_path) as src:
        out, transform = mask(src, [geometry], crop=True, nodata=np.nan)
    elevation = out[0]
    valid = elevation[~np.isnan(elevation)]
    if valid.size == 0:
        return (0, "N")
    mean_alt = int(round(float(valid.mean())))
    gy, gx = np.gradient(elevation)
    aspect_rad = np.arctan2(-gx, gy)
    aspect_deg = (np.degrees(aspect_rad) + 360) % 360
    mean_aspect = float(np.nanmean(aspect_deg))
    return (mean_alt, _aspect_label(mean_aspect))
```

- [ ] **Step 2: Manual verification**

Download MDT05 tiles covering the bbox from the IGN Centro de Descargas into `data/mdt05.tif`. Test `enrich_zone` against one clipped polygon; confirm altitude is plausible (Pyrenees: 600–2500 m).

- [ ] **Step 3: Commit**

```bash
git add tools/build_geojson/enrich.py
git commit -m "feat: add altitude and aspect enrichment step"
```

### Task 1.4: Orchestrator — build `forest_zones.geojson`

**Files:**
- Create: `tools/build_geojson/build_geojson.py`
- Create: `tools/build_geojson/README.md`
- Create (output): `app/src/main/assets/forest_zones.geojson`

- [ ] **Step 1: Implement `build_geojson.py`**

```python
# tools/build_geojson/build_geojson.py
"""Builds forest_zones.geojson: clip -> filter -> simplify -> enrich -> export."""
import json
import sys

from clip import clip_mfe50
from enrich import enrich_zone
from forest_filter import classify_forest

SPECIES_COLUMN = "ESPECIE1"  # MFE50 dominant-species column; adjust if needed.
SIMPLIFY_TOLERANCE = 0.0005  # ~50 m, in degrees.


def build(shapefile_path: str, dem_path: str, out_path: str) -> None:
    gdf = clip_mfe50(shapefile_path)
    gdf["geometry"] = gdf["geometry"].simplify(SIMPLIFY_TOLERANCE, preserve_topology=True)

    features = []
    for i, row in gdf.iterrows():
        compatible, tipo = classify_forest(row.get(SPECIES_COLUMN))
        altitud, orientacion = enrich_zone(row.geometry, dem_path)
        centroid = row.geometry.centroid
        features.append({
            "type": "Feature",
            "geometry": json.loads(gdf.geometry.iloc[gdf.index.get_loc(i)].__geo_interface__
                                   and json.dumps(row.geometry.__geo_interface__)),
            "properties": {
                "id": f"zone-{i}",
                "centroid_lat": round(centroid.y, 5),
                "centroid_lon": round(centroid.x, 5),
                "bosque_compatible": compatible,
                "bosque_tipo": tipo,
                "altitud": altitud,
                "orientacion": orientacion,
            },
        })

    out = {"type": "FeatureCollection", "features": features}
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(out, f, separators=(",", ":"))
    print(f"Wrote {len(features)} zones to {out_path}")


if __name__ == "__main__":
    build(sys.argv[1], sys.argv[2], sys.argv[3])
```

> Note: the `geometry` line above is intentionally simple — replace with
> `"geometry": row.geometry.__geo_interface__,` which `json.dump` serializes directly.
> Use that form:
>
> ```python
> "geometry": row.geometry.__geo_interface__,
> ```

- [ ] **Step 2: Run the pipeline**

Run: `cd tools/build_geojson && python build_geojson.py data/mfe50.shp data/mdt05.tif ../../app/src/main/assets/forest_zones.geojson`
Expected: prints `Wrote N zones`, file created.

- [ ] **Step 3: Verify output size and shape**

Confirm `forest_zones.geojson` is < 2 MB. Open it and confirm each feature has all 7 properties. If > 2 MB, raise `SIMPLIFY_TOLERANCE` and re-run.

- [ ] **Step 4: Write `README.md`**

Document: where to download MFE50 and MDT05, the `pip install -r requirements.txt` step, and the exact `build_geojson.py` command.

- [ ] **Step 5: Commit**

```bash
git add tools/build_geojson/build_geojson.py tools/build_geojson/README.md app/src/main/assets/forest_zones.geojson
git commit -m "feat: build forest_zones.geojson pipeline and bundled asset"
```

---

## Phase 2 — Vertical slice (map + zones + Open-Meteo + score + heatmap)

### Task 2.1: Domain data classes

**Files:**
- Create: `app/src/main/java/com/cepalert/data/model/WeatherData.kt`
- Create: `app/src/main/java/com/cepalert/data/model/ForestZone.kt`
- Create: `app/src/main/java/com/cepalert/data/model/ScoreResult.kt`

- [ ] **Step 1: Write `WeatherData.kt`**

```kotlin
package com.cepalert.data.model

data class WeatherData(
    val humidity7dAvg: Double,        // relative humidity %, mean of last 7 days
    val rain10dTotal: Double,         // mm, accumulated last 10 days
    val rain7dTotal: Double,          // mm, accumulated last 7 days
    val rain14dTotal: Double,         // mm, accumulated last 14 days
    val temp7dAvg: Double,            // °C, mean of last 7 days
    val temp7dMax: Double,            // °C
    val temp7dMin: Double,            // °C
    val daysSinceSignificantRain: Int,// days since last day with > 10 mm
    val source: String,               // e.g. "Open-Meteo"
    val updatedAtEpochMs: Long
)
```

- [ ] **Step 2: Write `ForestZone.kt`**

```kotlin
package com.cepalert.data.model

data class ForestZone(
    val id: String,
    val centroidLat: Double,
    val centroidLon: Double,
    val bosqueCompatible: Boolean,
    val bosqueTipo: String,           // "pino" | "haya" | "roble" | "otro"
    val altitud: Int,                 // mean altitude, meters
    val orientacion: String,          // "N", "NE", ...
    val geometryJson: String          // raw GeoJSON geometry, for map rendering
)
```

- [ ] **Step 3: Write `ScoreResult.kt`**

```kotlin
package com.cepalert.data.model

data class ScoreBreakdownRow(
    val label: String,                // "Humedad", "Lluvia 10d", ...
    val rawValueText: String,         // human-readable, e.g. "78 %"
    val normalized: Double,           // 0..1
    val weight: Double                // 0..1
)

data class ScoreResult(
    val zoneId: String,
    val score: Int,                   // 0..100
    val rows: List<ScoreBreakdownRow>
)
```

- [ ] **Step 4: Verify build**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/cepalert/data/model/
git commit -m "feat: add domain data models"
```

### Task 2.2: ScoringEngine — humidity normalization (TDD)

**Files:**
- Create: `app/src/main/java/com/cepalert/domain/scoring/ScoringEngine.kt`
- Test: `app/src/test/java/com/cepalert/domain/scoring/ScoringEngineTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.cepalert.domain.scoring

import org.junit.Assert.assertEquals
import org.junit.Test

class ScoringEngineTest {

    @Test fun humidity_in_optimal_band_is_one() {
        assertEquals(1.0, ScoringEngine.normalizeHumidity(70.0), 0.001)
        assertEquals(1.0, ScoringEngine.normalizeHumidity(80.0), 0.001)
        assertEquals(1.0, ScoringEngine.normalizeHumidity(90.0), 0.001)
    }

    @Test fun humidity_below_optimal_ramps_down_to_zero_at_40pct() {
        assertEquals(0.0, ScoringEngine.normalizeHumidity(40.0), 0.001)
        assertEquals(0.5, ScoringEngine.normalizeHumidity(55.0), 0.001)
    }

    @Test fun humidity_above_optimal_decays_to_zero_at_100pct() {
        assertEquals(0.0, ScoringEngine.normalizeHumidity(100.0), 0.001)
        assertEquals(0.5, ScoringEngine.normalizeHumidity(95.0), 0.001)
    }

    @Test fun humidity_is_clamped_outside_range() {
        assertEquals(0.0, ScoringEngine.normalizeHumidity(10.0), 0.001)
        assertEquals(0.0, ScoringEngine.normalizeHumidity(120.0), 0.001)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.cepalert.domain.scoring.ScoringEngineTest"`
Expected: FAIL — `ScoringEngine` unresolved.

- [ ] **Step 3: Implement `normalizeHumidity`**

```kotlin
package com.cepalert.domain.scoring

object ScoringEngine {

    fun normalizeHumidity(rh: Double): Double = when {
        rh in 70.0..90.0 -> 1.0
        rh < 70.0 -> ((rh - 40.0) / 30.0).coerceIn(0.0, 1.0)
        else -> ((100.0 - rh) / 10.0).coerceIn(0.0, 1.0)
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.cepalert.domain.scoring.ScoringEngineTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/cepalert/domain/scoring/ScoringEngine.kt app/src/test/java/com/cepalert/domain/scoring/ScoringEngineTest.kt
git commit -m "feat: add humidity normalization to ScoringEngine"
```

### Task 2.3: ScoringEngine — rain normalization (TDD)

**Files:**
- Modify: `app/src/main/java/com/cepalert/domain/scoring/ScoringEngine.kt`
- Modify: `app/src/test/java/com/cepalert/domain/scoring/ScoringEngineTest.kt`

- [ ] **Step 1: Add failing tests**

```kotlin
    @Test fun rain_in_optimal_band_is_one() {
        assertEquals(1.0, ScoringEngine.normalizeRain10d(30.0), 0.001)
        assertEquals(1.0, ScoringEngine.normalizeRain10d(55.0), 0.001)
        assertEquals(1.0, ScoringEngine.normalizeRain10d(80.0), 0.001)
    }

    @Test fun rain_below_optimal_ramps_from_zero() {
        assertEquals(0.0, ScoringEngine.normalizeRain10d(0.0), 0.001)
        assertEquals(0.5, ScoringEngine.normalizeRain10d(15.0), 0.001)
    }

    @Test fun rain_between_80_and_120_decays() {
        assertEquals(1.0, ScoringEngine.normalizeRain10d(80.0), 0.001)
        assertEquals(0.5, ScoringEngine.normalizeRain10d(100.0), 0.001)
    }

    @Test fun rain_above_120_is_penalized() {
        assertEquals(0.2, ScoringEngine.normalizeRain10d(125.0), 0.001)
        assertEquals(0.2, ScoringEngine.normalizeRain10d(300.0), 0.001)
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.cepalert.domain.scoring.ScoringEngineTest"`
Expected: FAIL — `normalizeRain10d` unresolved.

- [ ] **Step 3: Implement `normalizeRain10d`**

```kotlin
    fun normalizeRain10d(mm: Double): Double = when {
        mm in 30.0..80.0 -> 1.0
        mm < 30.0 -> (mm / 30.0).coerceIn(0.0, 1.0)
        mm <= 120.0 -> 1.0 - ((mm - 80.0) / 40.0) * 0.5
        else -> 0.2
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.cepalert.domain.scoring.ScoringEngineTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/cepalert/domain/scoring/ScoringEngine.kt app/src/test/java/com/cepalert/domain/scoring/ScoringEngineTest.kt
git commit -m "feat: add rain normalization to ScoringEngine"
```

### Task 2.4: ScoringEngine — temperature normalization (TDD)

**Files:**
- Modify: `ScoringEngine.kt`, `ScoringEngineTest.kt`

- [ ] **Step 1: Add failing tests**

```kotlin
    @Test fun temperature_in_optimal_band_is_one() {
        assertEquals(1.0, ScoringEngine.normalizeTemperature(10.0), 0.001)
        assertEquals(1.0, ScoringEngine.normalizeTemperature(15.0), 0.001)
        assertEquals(1.0, ScoringEngine.normalizeTemperature(20.0), 0.001)
    }

    @Test fun temperature_decays_linearly_outside_band() {
        assertEquals(0.5, ScoringEngine.normalizeTemperature(5.0), 0.001)
        assertEquals(0.0, ScoringEngine.normalizeTemperature(0.0), 0.001)
        assertEquals(0.5, ScoringEngine.normalizeTemperature(25.0), 0.001)
        assertEquals(0.0, ScoringEngine.normalizeTemperature(30.0), 0.001)
    }

    @Test fun temperature_is_clamped() {
        assertEquals(0.0, ScoringEngine.normalizeTemperature(-10.0), 0.001)
        assertEquals(0.0, ScoringEngine.normalizeTemperature(45.0), 0.001)
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.cepalert.domain.scoring.ScoringEngineTest"`
Expected: FAIL — `normalizeTemperature` unresolved.

- [ ] **Step 3: Implement `normalizeTemperature`**

```kotlin
    fun normalizeTemperature(tempC: Double): Double = when {
        tempC in 10.0..20.0 -> 1.0
        tempC < 10.0 -> (1.0 - (10.0 - tempC) / 10.0).coerceIn(0.0, 1.0)
        else -> (1.0 - (tempC - 20.0) / 10.0).coerceIn(0.0, 1.0)
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.cepalert.domain.scoring.ScoringEngineTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/cepalert/domain/scoring/ScoringEngine.kt app/src/test/java/com/cepalert/domain/scoring/ScoringEngineTest.kt
git commit -m "feat: add temperature normalization to ScoringEngine"
```

### Task 2.5: ScoringEngine — full score with breakdown (TDD)

**Files:**
- Modify: `ScoringEngine.kt`, `ScoringEngineTest.kt`

- [ ] **Step 1: Add failing tests**

```kotlin
    private fun zone(compatible: Boolean) = com.cepalert.data.model.ForestZone(
        id = "z1", centroidLat = 42.4, centroidLon = 1.5,
        bosqueCompatible = compatible, bosqueTipo = if (compatible) "pino" else "otro",
        altitud = 1200, orientacion = "N", geometryJson = "{}"
    )

    private fun weather(humidity: Double, rain10d: Double, temp: Double) =
        com.cepalert.data.model.WeatherData(
            humidity7dAvg = humidity, rain10dTotal = rain10d,
            rain7dTotal = 0.0, rain14dTotal = 0.0,
            temp7dAvg = temp, temp7dMax = 0.0, temp7dMin = 0.0,
            daysSinceSignificantRain = 0, source = "test", updatedAtEpochMs = 0L
        )

    @Test fun perfect_conditions_score_is_100() {
        val result = ScoringEngine.score(weather(80.0, 55.0, 15.0), zone(true))
        assertEquals(100, result.score)
        assertEquals(4, result.rows.size)
    }

    @Test fun worst_conditions_score_is_zero() {
        val result = ScoringEngine.score(weather(10.0, 0.0, -10.0), zone(false))
        assertEquals(0, result.score)
    }

    @Test fun incompatible_forest_caps_score_below_80() {
        // humidity+rain+temp all perfect = 0.4+0.3+0.1 = 0.8 -> 80; forest 0 -> stays 80
        val result = ScoringEngine.score(weather(80.0, 55.0, 15.0), zone(false))
        assertEquals(80, result.score)
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.cepalert.domain.scoring.ScoringEngineTest"`
Expected: FAIL — `score` unresolved.

- [ ] **Step 3: Implement `score`**

```kotlin
    fun score(
        weather: com.cepalert.data.model.WeatherData,
        zone: com.cepalert.data.model.ForestZone
    ): com.cepalert.data.model.ScoreResult {
        val nHumidity = normalizeHumidity(weather.humidity7dAvg)
        val nRain = normalizeRain10d(weather.rain10dTotal)
        val nForest = if (zone.bosqueCompatible) 1.0 else 0.0
        val nTemp = normalizeTemperature(weather.temp7dAvg)

        val total = nHumidity * 0.4 + nRain * 0.3 + nForest * 0.2 + nTemp * 0.1
        val rows = listOf(
            com.cepalert.data.model.ScoreBreakdownRow(
                "Humedad", "${weather.humidity7dAvg.toInt()} %", nHumidity, 0.4),
            com.cepalert.data.model.ScoreBreakdownRow(
                "Lluvia 10d", "${weather.rain10dTotal.toInt()} mm", nRain, 0.3),
            com.cepalert.data.model.ScoreBreakdownRow(
                "Tipo de bosque", zone.bosqueTipo, nForest, 0.2),
            com.cepalert.data.model.ScoreBreakdownRow(
                "Temperatura", "${weather.temp7dAvg.toInt()} °C", nTemp, 0.1),
        )
        return com.cepalert.data.model.ScoreResult(
            zoneId = zone.id,
            score = (total * 100).toInt().coerceIn(0, 100),
            rows = rows
        )
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.cepalert.domain.scoring.ScoringEngineTest"`
Expected: PASS — all tests green.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/cepalert/domain/scoring/ScoringEngine.kt app/src/test/java/com/cepalert/domain/scoring/ScoringEngineTest.kt
git commit -m "feat: add full score computation with breakdown"
```

### Task 2.6: GeoJsonLoader — parse the bundled asset (TDD)

**Files:**
- Create: `app/src/main/java/com/cepalert/data/local/GeoJsonLoader.kt`
- Test: `app/src/test/java/com/cepalert/data/local/GeoJsonLoaderTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.cepalert.data.local.GeoJsonLoaderTest"`
Expected: FAIL — `GeoJsonLoader` unresolved.

- [ ] **Step 3: Implement `GeoJsonLoader`**

```kotlin
package com.cepalert.data.local

import com.cepalert.data.model.ForestZone
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
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
                geometryJson = (geometry as JsonObject).toString()
            )
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.cepalert.data.local.GeoJsonLoaderTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/cepalert/data/local/GeoJsonLoader.kt app/src/test/java/com/cepalert/data/local/GeoJsonLoaderTest.kt
git commit -m "feat: add GeoJSON parser for forest zones"
```

### Task 2.7: ForestRepository — load zones from assets

**Files:**
- Create: `app/src/main/java/com/cepalert/data/repository/ForestRepository.kt`

- [ ] **Step 1: Implement `ForestRepository`**

```kotlin
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
```

- [ ] **Step 2: Verify build**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/cepalert/data/repository/ForestRepository.kt
git commit -m "feat: add ForestRepository loading zones from assets"
```

### Task 2.8: Open-Meteo API interface + DTOs

**Files:**
- Create: `app/src/main/java/com/cepalert/data/api/OpenMeteoDto.kt`
- Create: `app/src/main/java/com/cepalert/data/api/OpenMeteoApi.kt`

- [ ] **Step 1: Write `OpenMeteoDto.kt`**

```kotlin
package com.cepalert.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OpenMeteoResponse(
    val daily: DailyDto
)

@Serializable
data class DailyDto(
    @SerialName("time") val time: List<String>,
    @SerialName("precipitation_sum") val precipitationSum: List<Double>,
    @SerialName("temperature_2m_mean") val temperatureMean: List<Double>,
    @SerialName("temperature_2m_max") val temperatureMax: List<Double>,
    @SerialName("temperature_2m_min") val temperatureMin: List<Double>,
    @SerialName("relative_humidity_2m_mean") val humidityMean: List<Double>
)
```

- [ ] **Step 2: Write `OpenMeteoApi.kt`**

```kotlin
package com.cepalert.data.api

import retrofit2.http.GET
import retrofit2.http.Query

interface OpenMeteoApi {

    @GET("v1/forecast")
    suspend fun getHistory(
        @Query("latitude") lat: Double,
        @Query("longitude") lon: Double,
        @Query("past_days") pastDays: Int = 14,
        @Query("forecast_days") forecastDays: Int = 1,
        @Query("daily") daily: String =
            "precipitation_sum,temperature_2m_mean,temperature_2m_max," +
            "temperature_2m_min,relative_humidity_2m_mean",
        @Query("timezone") timezone: String = "Europe/Madrid"
    ): OpenMeteoResponse
}
```

- [ ] **Step 3: Verify build**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/cepalert/data/api/
git commit -m "feat: add Open-Meteo API interface and DTOs"
```

### Task 2.9: WeatherRepository — DTO to WeatherData mapping (TDD)

**Files:**
- Create: `app/src/main/java/com/cepalert/data/repository/WeatherRepository.kt`
- Test: `app/src/test/java/com/cepalert/data/repository/WeatherMapperTest.kt`

The DTO→domain mapping is a pure function `mapToWeatherData`, tested in isolation; the network call wraps it.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.cepalert.data.repository

import com.cepalert.data.api.DailyDto
import com.cepalert.data.api.OpenMeteoResponse
import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherMapperTest {

    // 15 days of data; index 14 is "today".
    private fun response(): OpenMeteoResponse {
        val days = (1..15).map { "2026-05-%02d".format(it) }
        return OpenMeteoResponse(
            DailyDto(
                time = days,
                precipitationSum = List(15) { 4.0 },          // 4 mm/day
                temperatureMean = List(15) { 15.0 },
                temperatureMax = List(15) { 22.0 },
                temperatureMin = List(15) { 8.0 },
                humidityMean = List(15) { 78.0 }
            )
        )
    }

    @Test fun rain10d_sums_last_10_days() {
        val w = mapToWeatherData(response(), nowEpochMs = 0L)
        assertEquals(40.0, w.rain10dTotal, 0.001)   // 10 * 4
    }

    @Test fun rain7d_and_14d_sum_correct_windows() {
        val w = mapToWeatherData(response(), nowEpochMs = 0L)
        assertEquals(28.0, w.rain7dTotal, 0.001)    // 7 * 4
        assertEquals(56.0, w.rain14dTotal, 0.001)   // 14 * 4
    }

    @Test fun temperature_and_humidity_averaged_over_7d() {
        val w = mapToWeatherData(response(), nowEpochMs = 0L)
        assertEquals(15.0, w.temp7dAvg, 0.001)
        assertEquals(78.0, w.humidity7dAvg, 0.001)
    }

    @Test fun days_since_significant_rain_counts_back_from_today() {
        // 4 mm/day everywhere -> never > 10 mm -> equals window length
        val w = mapToWeatherData(response(), nowEpochMs = 0L)
        assertEquals(15, w.daysSinceSignificantRain)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.cepalert.data.repository.WeatherMapperTest"`
Expected: FAIL — `mapToWeatherData` unresolved.

- [ ] **Step 3: Implement `WeatherRepository.kt` (mapper + network)**

```kotlin
package com.cepalert.data.repository

import com.cepalert.data.api.OpenMeteoApi
import com.cepalert.data.api.OpenMeteoResponse
import com.cepalert.data.model.WeatherData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Pure mapper: aggregates daily series into the domain model. */
fun mapToWeatherData(response: OpenMeteoResponse, nowEpochMs: Long): WeatherData {
    val d = response.daily
    val n = d.time.size
    fun lastN(list: List<Double>, k: Int) = list.subList((n - k).coerceAtLeast(0), n)

    val rain10d = lastN(d.precipitationSum, 10).sum()
    val rain7d = lastN(d.precipitationSum, 7).sum()
    val rain14d = lastN(d.precipitationSum, 14).sum()
    val temp7d = lastN(d.temperatureMean, 7)
    val hum7d = lastN(d.humidityMean, 7)

    var daysSince = 0
    for (i in d.precipitationSum.indices.reversed()) {
        if (d.precipitationSum[i] > 10.0) break
        daysSince++
    }

    return WeatherData(
        humidity7dAvg = hum7d.average(),
        rain10dTotal = rain10d,
        rain7dTotal = rain7d,
        rain14dTotal = rain14d,
        temp7dAvg = temp7d.average(),
        temp7dMax = lastN(d.temperatureMax, 7).max(),
        temp7dMin = lastN(d.temperatureMin, 7).min(),
        daysSinceSignificantRain = daysSince,
        source = "Open-Meteo",
        updatedAtEpochMs = nowEpochMs
    )
}

@Singleton
class WeatherRepository @Inject constructor(
    private val api: OpenMeteoApi
) {
    suspend fun getWeather(lat: Double, lon: Double): WeatherData =
        withContext(Dispatchers.IO) {
            val response = api.getHistory(lat = lat, lon = lon)
            mapToWeatherData(response, System.currentTimeMillis())
        }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.cepalert.data.repository.WeatherMapperTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/cepalert/data/repository/WeatherRepository.kt app/src/test/java/com/cepalert/data/repository/WeatherMapperTest.kt
git commit -m "feat: add WeatherRepository with Open-Meteo mapping"
```

### Task 2.10: Hilt module — network + Retrofit

**Files:**
- Create: `app/src/main/java/com/cepalert/di/AppModule.kt`

- [ ] **Step 1: Implement `AppModule`**

```kotlin
package com.cepalert.di

import com.cepalert.data.api.OpenMeteoApi
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    private val json = Json { ignoreUnknownKeys = true }

    @Provides @Singleton
    fun provideOkHttp(): OkHttpClient = OkHttpClient.Builder().build()

    @Provides @Singleton
    fun provideRetrofit(client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl("https://api.open-meteo.com/")
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides @Singleton
    fun provideOpenMeteoApi(retrofit: Retrofit): OpenMeteoApi =
        retrofit.create(OpenMeteoApi::class.java)
}
```

- [ ] **Step 2: Verify build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/cepalert/di/AppModule.kt
git commit -m "feat: add Hilt module for Retrofit and Open-Meteo API"
```

### Task 2.11: MapViewModel — load zones, score them (TDD)

**Files:**
- Create: `app/src/main/java/com/cepalert/ui/map/MapViewModel.kt`
- Test: `app/src/test/java/com/cepalert/ui/map/MapViewModelTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.cepalert.ui.map

import app.cash.turbine.test
import com.cepalert.data.model.ForestZone
import com.cepalert.data.model.WeatherData
import com.cepalert.data.repository.ForestRepository
import com.cepalert.data.repository.WeatherRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MapViewModelTest {

    private val forestRepo = mockk<ForestRepository>()
    private val weatherRepo = mockk<WeatherRepository>()

    @Before fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun zone(id: String) = ForestZone(
        id = id, centroidLat = 42.4, centroidLon = 1.5,
        bosqueCompatible = true, bosqueTipo = "pino",
        altitud = 1200, orientacion = "N", geometryJson = "{}"
    )

    private val goodWeather = WeatherData(
        humidity7dAvg = 80.0, rain10dTotal = 55.0, rain7dTotal = 30.0,
        rain14dTotal = 70.0, temp7dAvg = 15.0, temp7dMax = 22.0, temp7dMin = 8.0,
        daysSinceSignificantRain = 3, source = "test", updatedAtEpochMs = 0L
    )

    @Test fun loads_zones_and_emits_scored_state() = runTest {
        coEvery { forestRepo.loadZones() } returns listOf(zone("z1"))
        coEvery { weatherRepo.getWeather(any(), any()) } returns goodWeather
        val vm = MapViewModel(forestRepo, weatherRepo)

        vm.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()
            assertEquals(1, state.scoredZones.size)
            assertEquals(100, state.scoredZones.first().score.score)
            assertEquals(goodWeather, state.weather)
        }
    }

    @Test fun zone_with_failed_weather_is_marked_no_data() = runTest {
        coEvery { forestRepo.loadZones() } returns listOf(zone("z1"))
        coEvery { weatherRepo.getWeather(any(), any()) } throws RuntimeException("net")
        val vm = MapViewModel(forestRepo, weatherRepo)

        vm.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()
            assertTrue(state.scoredZones.first().score == null)
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.cepalert.ui.map.MapViewModelTest"`
Expected: FAIL — `MapViewModel` unresolved.

- [ ] **Step 3: Implement `MapViewModel`**

```kotlin
package com.cepalert.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cepalert.data.model.ForestZone
import com.cepalert.data.model.ScoreResult
import com.cepalert.data.model.WeatherData
import com.cepalert.data.repository.ForestRepository
import com.cepalert.data.repository.WeatherRepository
import com.cepalert.domain.scoring.ScoringEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A forest zone paired with its score (null = weather unavailable). */
data class ScoredZone(val zone: ForestZone, val score: ScoreResult?)

data class MapUiState(
    val isLoading: Boolean = true,
    val scoredZones: List<ScoredZone> = emptyList(),
    val weather: WeatherData? = null,
    val error: String? = null
)

@HiltViewModel
class MapViewModel @Inject constructor(
    private val forestRepository: ForestRepository,
    private val weatherRepository: WeatherRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            runCatching {
                val zones = forestRepository.loadZones()
                var representativeWeather: WeatherData? = null
                val scored = zones.map { zone ->
                    val weather = runCatching {
                        weatherRepository.getWeather(zone.centroidLat, zone.centroidLon)
                    }.getOrNull()
                    if (weather != null && representativeWeather == null) {
                        representativeWeather = weather
                    }
                    ScoredZone(
                        zone = zone,
                        score = weather?.let { ScoringEngine.score(it, zone) }
                    )
                }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        scoredZones = scored,
                        weather = representativeWeather
                    )
                }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "Error desconocido")
                }
            }
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.cepalert.ui.map.MapViewModelTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/cepalert/ui/map/MapViewModel.kt app/src/test/java/com/cepalert/ui/map/MapViewModelTest.kt
git commit -m "feat: add MapViewModel with zone scoring"
```

### Task 2.12: Material 3 theme

**Files:**
- Create: `app/src/main/java/com/cepalert/ui/theme/Color.kt`, `Theme.kt`

- [ ] **Step 1: Write `Color.kt`**

```kotlin
package com.cepalert.ui.theme

import androidx.compose.ui.graphics.Color

val ForestGreen = Color(0xFF2E7D32)
val ScoreHigh = Color(0xFF1B5E20)
val ScoreMid = Color(0xFFF9A825)
val ScoreLow = Color(0xFF9E9E9E)
```

- [ ] **Step 2: Write `Theme.kt`**

```kotlin
package com.cepalert.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(primary = ForestGreen)
private val DarkColors = darkColorScheme(primary = ForestGreen)

@Composable
fun CepAlertTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
```

- [ ] **Step 3: Verify build**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/cepalert/ui/theme/
git commit -m "feat: add Material 3 theme"
```

### Task 2.13: MapScreen — MapLibre map with score markers

**Files:**
- Create: `app/src/main/java/com/cepalert/ui/map/MapScreen.kt`
- Modify: `app/src/main/java/com/cepalert/MainActivity.kt`

- [ ] **Step 1: Implement `MapScreen.kt`**

Render the MapLibre map via `AndroidView`, centered on Catalunya (lat 42.4, lon 1.5, zoom 8), OSM raster style. For each scored zone add a circle marker at the centroid colored by score band (high ≥ 67 → `ScoreHigh`, 34–66 → `ScoreMid`, else `ScoreLow`; null score → `ScoreLow`). Show a centered `CircularProgressIndicator` while `uiState.isLoading`.

```kotlin
package com.cepalert.ui.map

import android.graphics.Color as AColor
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.cepalert.ui.theme.ScoreHigh
import com.cepalert.ui.theme.ScoreLow
import com.cepalert.ui.theme.ScoreMid
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView

private fun scoreColor(score: Int?): Int = when {
    score == null -> ScoreLow.value.toInt()
    score >= 67 -> ScoreHigh.value.toInt()
    score >= 34 -> ScoreMid.value.toInt()
    else -> ScoreLow.value.toInt()
}

@Composable
fun MapScreen(viewModel: MapViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { context ->
            MapLibre.getInstance(context)
            MapView(context).apply {
                getMapAsync { map ->
                    map.setStyle(
                        "https://demotiles.maplibre.org/style.json"
                    )
                    map.cameraPosition = CameraPosition.Builder()
                        .target(LatLng(42.4, 1.5)).zoom(8.0).build()
                }
            }
        }, update = { mapView ->
            mapView.getMapAsync { map ->
                // Re-draw markers from state.scoredZones using
                // org.maplibre.android.annotations or a CircleManager.
                // Each marker positioned at zone.centroidLat/Lon,
                // filled with scoreColor(scoredZone.score?.score).
            }
        }, modifier = Modifier.fillMaxSize())

        if (state.isLoading) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
    }
}
```

> Marker rendering detail: add the `org.maplibre.gl:android-plugin-annotation-v9`
> dependency and use `CircleManager` to draw one `CircleOptions` per zone with
> `withCircleColor` set from `scoreColor(...)`. Clear and re-add circles in `update`.

- [ ] **Step 2: Wire `MainActivity`**

```kotlin
package com.cepalert

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.cepalert.ui.map.MapScreen
import com.cepalert.ui.theme.CepAlertTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CepAlertTheme { MapScreen() }
        }
    }
}
```

- [ ] **Step 3: Run on device/emulator**

Run: `./gradlew installDebug`, then launch the app.
Expected: map of Catalunya appears; after the loading spinner, colored circles appear at zone centroids within < 5 s on a normal connection.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/cepalert/ui/map/MapScreen.kt app/src/main/java/com/cepalert/MainActivity.kt app/build.gradle.kts
git commit -m "feat: add MapScreen with MapLibre and score markers"
```

**END OF VERTICAL SLICE — the app loads real zones, fetches real weather, scores them, and renders the result. Verify on a device before continuing.**

---

## Phase 3 — All zones, cache, FAB, conditions banner

### Task 3.1: ScoreCache — 6 h disk cache (TDD)

**Files:**
- Create: `app/src/main/java/com/cepalert/data/local/ScoreCache.kt`
- Test: `app/src/test/java/com/cepalert/data/local/ScoreCacheTest.kt`

The freshness check is a pure function tested in isolation; file I/O wraps it.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.cepalert.data.local

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoreCacheTest {

    private val sixHoursMs = 6 * 60 * 60 * 1000L

    @Test fun cache_is_fresh_within_6_hours() {
        assertTrue(ScoreCache.isFresh(savedAt = 1_000_000L, now = 1_000_000L + sixHoursMs - 1))
    }

    @Test fun cache_is_stale_after_6_hours() {
        assertFalse(ScoreCache.isFresh(savedAt = 1_000_000L, now = 1_000_000L + sixHoursMs + 1))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.cepalert.data.local.ScoreCacheTest"`
Expected: FAIL — `ScoreCache` unresolved.

- [ ] **Step 3: Implement `ScoreCache`**

```kotlin
package com.cepalert.data.local

import android.content.Context
import com.cepalert.data.model.ScoreResult
import com.cepalert.data.model.WeatherData
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class CachedScores(
    val savedAtEpochMs: Long,
    val weather: WeatherData,
    val scores: List<ScoreResult>
)

@Singleton
class ScoreCache @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TTL_MS = 6 * 60 * 60 * 1000L
        fun isFresh(savedAt: Long, now: Long): Boolean = now - savedAt < TTL_MS
    }

    private val file: File get() = File(context.cacheDir, "scores.json")
    private val json = Json { ignoreUnknownKeys = true }

    fun read(now: Long): CachedScores? {
        if (!file.exists()) return null
        return runCatching {
            json.decodeFromString<CachedScores>(file.readText())
        }.getOrNull()?.takeIf { isFresh(it.savedAtEpochMs, now) }
    }

    fun write(cached: CachedScores) {
        runCatching { file.writeText(json.encodeToString(cached)) }
    }
}
```

> Add `@Serializable` to `WeatherData`, `ScoreResult`, and `ScoreBreakdownRow`
> (import `kotlinx.serialization.Serializable`) so they can be cached.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.cepalert.data.local.ScoreCacheTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/cepalert/data/local/ScoreCache.kt app/src/main/java/com/cepalert/data/model/
git commit -m "feat: add 6h disk cache for scores"
```

### Task 3.2: Wire ScoreCache into MapViewModel

**Files:**
- Modify: `app/src/main/java/com/cepalert/ui/map/MapViewModel.kt`
- Modify: `app/src/test/java/com/cepalert/ui/map/MapViewModelTest.kt`

- [ ] **Step 1: Add a failing test**

```kotlin
    @Test fun fresh_cache_is_used_without_calling_weather() = runTest {
        val cache = mockk<com.cepalert.data.local.ScoreCache>()
        val cached = com.cepalert.data.local.CachedScores(
            savedAtEpochMs = 0L, weather = goodWeather,
            scores = listOf(com.cepalert.domain.scoring.ScoringEngine.score(goodWeather, zone("z1")))
        )
        coEvery { forestRepo.loadZones() } returns listOf(zone("z1"))
        io.mockk.every { cache.read(any()) } returns cached
        val vm = MapViewModel(forestRepo, weatherRepo, cache)

        vm.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()
            assertEquals(1, state.scoredZones.size)
        }
        io.mockk.coVerify(exactly = 0) { weatherRepo.getWeather(any(), any()) }
    }
```

Update the other tests' `MapViewModel(...)` constructor calls to pass a `mockk<ScoreCache>()` whose `read` returns `null` and `write` is `every { ... } returns Unit` / `just Runs`.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.cepalert.ui.map.MapViewModelTest"`
Expected: FAIL — constructor arity mismatch.

- [ ] **Step 3: Modify `MapViewModel`**

Add `private val scoreCache: ScoreCache` as a third constructor parameter. In `refresh()`, before loading: if `scoreCache.read(System.currentTimeMillis())` returns non-null, build `scoredZones` by pairing loaded zones with cached scores by `zoneId` and emit immediately. Otherwise run the existing fetch path, then call `scoreCache.write(CachedScores(now, representativeWeather, allScores))` when `representativeWeather != null`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.cepalert.ui.map.MapViewModelTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/cepalert/ui/map/MapViewModel.kt app/src/test/java/com/cepalert/ui/map/MapViewModelTest.kt
git commit -m "feat: use 6h cache in MapViewModel"
```

### Task 3.3: FAB refresh + conditions banner + date chip

**Files:**
- Modify: `app/src/main/java/com/cepalert/ui/map/MapScreen.kt`

- [ ] **Step 1: Add UI elements**

Wrap the map in a `Scaffold`. Add a `FloatingActionButton` (refresh icon) that calls `viewModel.refresh()` and bypasses the cache — add a `forceRefresh()` to `MapViewModel` that ignores `scoreCache.read`. Add a top-start date chip (`AssistChip`) showing today's date formatted `dd MMM`. Add a bottom banner (`Surface` strip) showing `state.weather`: `"Lluvia 10d: X mm · Temp: Y °C · Humedad: Z %"`; tapping it sets a `showWeatherSheet` state to true.

- [ ] **Step 2: Run on device**

Run: `./gradlew installDebug`
Expected: FAB refreshes data; banner shows current conditions; date chip shows today.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/cepalert/ui/map/MapScreen.kt app/src/main/java/com/cepalert/ui/map/MapViewModel.kt
git commit -m "feat: add FAB refresh, conditions banner, date chip"
```

---

## Phase 4 — Remaining screens

### Task 4.1: ZoneDetailSheet

**Files:**
- Create: `app/src/main/java/com/cepalert/ui/detail/ZoneDetailSheet.kt`
- Modify: `app/src/main/java/com/cepalert/ui/map/MapScreen.kt`

- [ ] **Step 1: Implement `ZoneDetailSheet`**

A `ModalBottomSheet` taking a `ScoredZone`. Layout top-to-bottom:
1. Large score number (0–100) colored by band (`scoreColor`), or "Sin datos" if `score == null`.
2. Four breakdown rows from `score.rows` — each shows `label`, `rawValueText`, and `"peso ${(weight*100).toInt()}%"`.
3. Zone metadata: `"Bosque: ${zone.bosqueTipo} · Altitud: ${zone.altitud} m · Orientación: ${zone.orientacion}"`.
4. Small-print legal notice: `"Predicción orientativa. Las condiciones reales pueden variar."`

- [ ] **Step 2: Wire into `MapScreen`**

On marker tap, set a `selectedZone` state; render `ZoneDetailSheet` when non-null.

- [ ] **Step 3: Run on device**

Run: `./gradlew installDebug`
Expected: tapping a zone opens the detail sheet with score, breakdown, metadata, legal notice.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/cepalert/ui/detail/ZoneDetailSheet.kt app/src/main/java/com/cepalert/ui/map/MapScreen.kt
git commit -m "feat: add zone detail bottom sheet"
```

### Task 4.2: WeatherBottomSheet

**Files:**
- Create: `app/src/main/java/com/cepalert/ui/weather/WeatherBottomSheet.kt`
- Modify: `app/src/main/java/com/cepalert/ui/map/MapScreen.kt`

- [ ] **Step 1: Implement `WeatherBottomSheet`**

A `ModalBottomSheet` taking a `WeatherData`. Rows:
- `"Lluvia acumulada 7 días: ${rain7dTotal.toInt()} mm"`
- `"Lluvia acumulada 14 días: ${rain14dTotal.toInt()} mm"`
- `"Temperatura 7d — máx ${temp7dMax.toInt()} / mín ${temp7dMin.toInt()} / media ${temp7dAvg.toInt()} °C"`
- `"Humedad relativa media: ${humidity7dAvg.toInt()} %"`
- `"Días desde última lluvia significativa (>10 mm): ${daysSinceSignificantRain}"`
- `"Fuente: ${source} · Actualizado: ${formatted updatedAtEpochMs}"`

- [ ] **Step 2: Wire into `MapScreen`**

Render it when `showWeatherSheet` is true (set by the conditions banner tap from Task 3.3).

- [ ] **Step 3: Run on device**

Run: `./gradlew installDebug`
Expected: tapping the banner opens the weather sheet with all six rows.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/cepalert/ui/weather/WeatherBottomSheet.kt app/src/main/java/com/cepalert/ui/map/MapScreen.kt
git commit -m "feat: add weather conditions bottom sheet"
```

---

## Phase 5 — Polish

### Task 5.1: Loading and error states

**Files:**
- Modify: `app/src/main/java/com/cepalert/ui/map/MapScreen.kt`

- [ ] **Step 1: Add states**

When `state.error != null`, show a centered column with the message and a "Reintentar" button calling `viewModel.refresh()`. Keep the loading spinner. Confirm that zones with `score == null` render as grey markers (already handled by `scoreColor`).

- [ ] **Step 2: Run on device with airplane mode**

Enable airplane mode, launch the app.
Expected: error state with retry button; no crash.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/cepalert/ui/map/MapScreen.kt
git commit -m "feat: add loading and error UI states"
```

### Task 5.2: Firebase Crashlytics

**Files:**
- Modify: `build.gradle.kts`, `app/build.gradle.kts`
- Create: `app/google-services.json`
- Create: `app/src/main/res/values/strings.xml` (if not present)

- [ ] **Step 1: Set up Firebase**

Create a Firebase project, register the Android app with package `com.cepalert`, download `google-services.json` into `app/`. Add the `google-services` and `firebase-crashlytics` Gradle plugins and the `com.google.firebase:firebase-crashlytics` dependency (via the Firebase BoM).

- [ ] **Step 2: Verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL. Force a test crash once, confirm it appears in the Crashlytics console.

- [ ] **Step 3: Commit**

```bash
git add build.gradle.kts app/build.gradle.kts app/src/main/res/
git commit -m "chore: integrate Firebase Crashlytics"
```

> Do NOT commit `google-services.json` if it contains restricted keys — add it to
> `.gitignore` and document it in the pipeline README instead.

### Task 5.3: App icon, name, manifest polish

**Files:**
- Modify: `app/src/main/res/` (mipmap icons), `AndroidManifest.xml`, `strings.xml`

- [ ] **Step 1: Add branding**

Set `app_name` to "CepAlert" in `strings.xml`. Generate adaptive launcher icons (mushroom mark, forest green) via Android Studio's Image Asset wizard. Set `android:label` and `android:icon` in the manifest.

- [ ] **Step 2: Run on device**

Run: `./gradlew installDebug`
Expected: app shows the CepAlert name and icon in the launcher.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/res/ app/src/main/AndroidManifest.xml
git commit -m "feat: add app icon and name branding"
```

---

## Phase 6 — Release to Google Play Store

### Task 6.1: Release build configuration

**Files:**
- Modify: `app/build.gradle.kts`
- Create: `keystore.properties` (git-ignored)

- [ ] **Step 1: Create an upload keystore**

Run: `keytool -genkey -v -keystore cepalert-upload.keystore -alias cepalert -keyalg RSA -keysize 2048 -validity 9125`
Store the keystore file OUTSIDE the repo. Record the passwords in a password manager.

- [ ] **Step 2: Configure signing**

Create `keystore.properties` (add to `.gitignore`) with `storeFile`, `storePassword`, `keyAlias`, `keyPassword`. In `app/build.gradle.kts`, read it into a `signingConfigs.release` block and reference it from `buildTypes.release`. Enable `isMinifyEnabled = true` and `isShrinkResources = true` for release.

- [ ] **Step 3: Build the release bundle**

Run: `./gradlew bundleRelease`
Expected: signed `app/build/outputs/bundle/release/app-release.aab` produced.

- [ ] **Step 4: Commit**

```bash
git add app/build.gradle.kts .gitignore
git commit -m "chore: configure signed release build"
```

### Task 6.2: Privacy policy

**Files:**
- Create: `docs/privacy-policy.md`

- [ ] **Step 1: Write the policy**

State plainly: CepAlert collects no personal data and creates no user accounts; it sends only zone coordinates to Open-Meteo to fetch weather; it uses Firebase Crashlytics, which collects anonymous crash diagnostics; no data is sold or shared otherwise.

- [ ] **Step 2: Publish it**

Host the policy at a public URL (GitHub Pages works). The URL is required by the Play Console.

- [ ] **Step 3: Commit**

```bash
git add docs/privacy-policy.md
git commit -m "docs: add privacy policy"
```

### Task 6.3: Play Console setup and submission

**Files:** none (Play Console web work).

- [ ] **Step 1: Developer account**

Create a Google Play Developer account ($25 one-time fee). Enroll in Play App Signing.

- [ ] **Step 2: Create the app + store listing**

In the Play Console create the "CepAlert" app. Fill the store listing: short and full description (from the spec's product vision), at least 2 phone screenshots, a 512×512 icon, a 1024×500 feature graphic.

- [ ] **Step 3: Content rating and Data safety**

Complete the content rating questionnaire. Complete the Data safety form: declare location coordinates sent to a third party (Open-Meteo) and crash diagnostics (Crashlytics); declare no account, no data sale.

- [ ] **Step 4: Upload and roll out**

Upload `app-release.aab` to a Closed testing track first; add the 10–15 beta boletaires as testers. After beta validation, promote to Production.
Expected: app passes review and is live on the Play Store.

- [ ] **Step 5: Final commit / tag**

```bash
git tag v1.0.0-mvp
git commit --allow-empty -m "release: CepAlert MVP v1.0.0 submitted to Play Store"
```

---

## Self-Review

**Spec coverage:**
- Map + heatmap → Tasks 2.13, 3.3 (markers colored by score band). ✓
- Conditions banner (rain 10d / temp / humidity) → Task 3.3. ✓
- Zone detail with score breakdown + metadata + legal notice → Task 4.1. ✓
- Weather bottom sheet (rain 7/14d, temp max/min/avg, humidity, days since rain, source) → Task 4.2. ✓
- Pyrenees geographic scope → Task 1.2 (bounding box). ✓
- Open-Meteo data source → Tasks 2.8–2.10. ✓
- ICGC forest data → Tasks 1.1–1.4. ✓
- Scoring formula and 4 variables with weights → Tasks 2.2–2.5. ✓
- No backend / client-side scoring → architecture, Phase 2. ✓
- 6 h cache → Tasks 3.1–3.2. ✓
- Kotlin/Compose/MVVM/Hilt/Retrofit/Coroutines stack → Phase 0, throughout. ✓
- Crashlytics → Task 5.2. ✓
- < 5 s load → verified in Task 2.13 Step 3. ✓
- Play Store release → Phase 6. ✓
- Out of scope (accounts, history, push, ML, other species) → not planned. ✓

**Type consistency:** `ForestZone`, `WeatherData`, `ScoreResult`/`ScoreBreakdownRow`, `ScoredZone`, `MapUiState`, `CachedScores` are defined once and used consistently. `ScoringEngine.score()`, `normalizeHumidity/Rain10d/Temperature`, `mapToWeatherData`, `GeoJsonLoader.parse`, `ScoreCache.isFresh/read/write` keep stable signatures across tasks.

**Placeholder note:** UI tasks (2.13, 3.3, 4.1, 4.2, 5.x) describe Compose layouts in prose with exact field references and full code for the non-trivial parts (theme, screen scaffolding, view models). Pixel-level Compose styling is left to the implementer's judgement, which is appropriate — the data contracts and behavior are fully specified.
