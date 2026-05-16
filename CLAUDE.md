# CepAlert — Context per a Claude Code

## Què és el projecte

App Android (Kotlin + Jetpack Compose) que prediu les millors zones per recol·lectar *Boletus edulis* (cep) als Pirineus catalans. Combina dades cartogràfiques estàtiques (bosc, altitud, pH, orientació) amb meteorologia diària (Open-Meteo + AEMET) per generar una puntuació 0-100 per zona.

Repositori: `https://github.com/albertd987/Ceps` — branca activa: `feat/mvp-build`

---

## Arquitectura general

### Android (app/)
- **Kotlin + Jetpack Compose + Hilt**
- **MapLibre Android 11.5.0** per al mapa
- `forest_zones.geojson` — asset estàtic a l'APK (`app/src/main/assets/`). Conté les zones forestals amb metadades (bosque_tipo, altitud, orientacion, soil_ph, centroid_lat/lon).
- `scores.json` — descarregat en runtime des de GitHub Pages (`https://albertd987.github.io/Ceps/scores.json`). Conté puntuació i dades meteorològiques per zona.

### Pipeline de dades (tools/)
```
tools/build_geojson/   — scripts one-time per construir forest_zones.geojson
tools/compute_scores/  — script diari (GitHub Actions) per calcular scores.json
```

### GitHub Actions
- Workflow: `.github/workflows/daily_scores.yml`
- S'executa cada dia a les 06:00 UTC (o manualment)
- Llegeix `forest_zones.geojson` del repo → crida Open-Meteo + AEMET → escriu `scores.json` → desplega a GitHub Pages
- Secret requerit: `AEMET_API_KEY`

---

## Model de dades

### ForestZone (`data/model/ForestZone.kt`)
Carregat des de `forest_zones.geojson` via `GeoJsonLoader`.
Camps: `id`, `centroidLat`, `centroidLon`, `bosqueTipo`, `altitud`, `orientacion`, `soilPh`, `bosqueCompatible`, `geometryJson`

### WeatherData (`data/model/WeatherData.kt`)
Derivat del `scores.json` remot via `RemoteScores.toWeatherData(zoneId)`.
Camps: `soilMoisture7d`, `soilTemp7d`, `soilTempDrop`, `rain14dTotal`, `rainTriggerMm`, `triggerDaysAgo`, `source`, `updatedAtEpochMs`

### RemoteScores (`data/model/RemoteScores.kt`)
Format `scores.json`: `{ v, updated_at, month, zones: { zoneId: [score, soilMoist7d, soilTemp7d, soilTempDrop, rain14d, rainTriggerMm, triggerDaysAgo] } }`

### ScoreCache (`data/local/ScoreCache.kt`)
TTL de 6 hores. Guardat a `context.cacheDir/scores.json`. **Es preserva entre reinstal·lacions** (update de l'APK no l'esborra). Per saltar la caché: botó Refresh (↺) → `MapViewModel.forceRefresh()`.

---

## Motor de puntuació

### ScoringEngine (`domain/scoring/ScoringEngine.kt`)
Càlcul local a l'app. Factors i pesos:

| Factor | Pes | Notes |
|--------|-----|-------|
| Humitat sòl 7d | 28% | Volumètric 0-7cm (m³/m³), òptim 0.20-0.40 |
| Patró pluja | 20% | ≥15mm en 3 dies consecutius, finestra 8-16 dies |
| Tipus bosc | 11% | haya=1.0, pino=0.75, roble=0.6 |
| Temp sòl 7d | 11% | Òptim 8-15°C per al micel·li |
| pH del sòl | 10% | pH≤5 òptim, pH>7 gairebé zero |
| Refredament sòl | 10% | Drop setmanal (positiu = ha refredat) |
| Altitud | 7% | Bell curve: viable 500-2200m, pic 900-1500m |
| Orientació | 3% | N=1.0, NE=0.85 ... S=0.1 |
| Factor estacional | ×mult | Octubre=1.0, Setembre=0.8, Novembre=0.7 |

### Meteorologia (`tools/compute_scores/run.py`)
- Grid Open-Meteo 10×20 sobre els Pirineus (LAT 42.0-42.9, LON 0.4-3.3)
- Variables: `soil_moisture_0_to_7cm_mean`, `soil_temperature_0_to_7cm_mean`, `precipitation_sum`
- AEMET sobreescriu `rain14d` amb dades reals d'estació meteorològica

---

## Mapa (MapScreen.kt)

### MapLibre — lliçons apreses (IMPORTANT)
- **RasterDemSource amb URL TileJSON causa reset del renderer natiu.** Quan MapLibre processa el TileJSON asíncronament, el renderer GL descarta les capes afegides dinàmicament. El `style.getSource()/getLayer()` retorna non-null (Java side) però el renderer natiu ja no les té. **Solució: no afegir RasterDemSource dinàmicament.**
- **SymbolLayer amb textField causa el mateix problema** (fetch asíncron de glyphs). Solució: overlay Compose per als números dels clusters (vegeu més avall).
- **RasterSource amb WMS URL** (no TileJSON) és segur — no causa reset.
- Capes afegides en el callback `setStyle` (factory block) van per davant de les afegides al bloc `update` → ordre correcte: VEG < CLUSTER < COUNT < ID.

### Constants de capes
```kotlin
SOURCE_VEG    = "veg-source"    // WMS ICGC cobertes 2024
LAYER_VEG     = "veg-layer"
SOURCE_ID     = "zones-source"  // GeoJSON amb clustering
LAYER_CLUSTER = "zones-cluster" // Bubbles ambre (clusters)
LAYER_COUNT   = "zones-count"   // Placeholder invisible (CircleLayer r=0)
LAYER_ID      = "zones-layer"   // Punts individuals, color per score
PROP_SCORE    = "score"
```

### Clusters
- `GeoJsonOptions().withCluster(true).withClusterMaxZoom(12).withClusterRadius(60)`
- **withClusterProperty no funciona** (falla silenciosament en runtime). No intentar afegir propietats agregades.
- Color dels clusters: ambre fix `#FFB300` (color per score màxim descartada per la limitació anterior)

### Overlay de números de cluster (Compose)
`addOnCameraIdleListener` → `map.queryRenderedFeatures(RectF, LAYER_CLUSTER)` → `map.projection.toScreenLocation()` → estat Compose `clusterLabelsState` → `Text` amb `absoluteOffset { IntOffset(pixels) }` per sobre de l'AndroidView.

### Overlay WMS Cobertes ICGC
URL WMS (funcional, WMS 1.3.0 amb CRS=EPSG:3857):
```
https://geoserveis.icgc.cat/servei/catalunya/cobertes-sol/wms
  ?REQUEST=GetMap&SERVICE=WMS&VERSION=1.3.0&LAYERS=cobertes_2024
  &STYLES=&FORMAT=image/png&TRANSPARENT=TRUE
  &CRS=EPSG:3857&BBOX={bbox-epsg-3857}&WIDTH=256&HEIGHT=256
```
Afegit en el `setStyle` callback (no dinàmicament). Toggle via `LaunchedEffect(showVegLayer)` que canvia `PropertyFactory.visibility()` — canviar visibility és segur (no afegeix/elimina capes).

---

## Scripts one-time (tools/build_geojson/)

| Script | Funció | Font de dades |
|--------|--------|---------------|
| `build_geojson.py` | Construeix `forest_zones.geojson` des del MFE50 + DEM | MFE50 shapefile + MDT05 |
| `enrich.py` | Afegeix altitud i orientació per polígon | DEM raster via rasterio |
| `enrich_soil_ph.py` | Afegeix `soil_ph` per centroide | SoilGrids WCS (pH × 10 → pH real) |
| `enrich_hic.py` | Actualitza `bosque_tipo` amb HIC v2 2018 | CHIC_v2_abril2019.zip (~122MB) des de UB |
| `forest_filter.py` | Classifica espècies MFE50 → haya/pino/roble | — |
| `clip.py` | Retalla MFE50 als Pirineus catalans | — |

### Enriquiment HIC (`enrich_hic.py`)
- Descàrrega: `http://atzavara.bio.ub.edu/mapes_descarrega/CHIC_v2_abril2019.zip`
- 61.036 polígons, CRS EPSG:25831, columnes `HIC1`…`HIC10` per polígon
- Mapeig: 9120/9130/9150→haya, 9160/9190/9230/9240/9340→roble, 9430/9540→pino
- Prioritat quan hi ha múltiples HICs: haya > pino > roble
- Resultat: 5144 zones, 136 haya, 3273 pino, 1735 roble (557 canviades des de MFE50)
- Les zones haya es concentren a Berguedà/Moixeró/Cadí (lat≈42.2, lon≈1.85)

**Workflow correcte quan s'executa un script que modifica dades:**
```
python script.py → git add → git commit → git push → trigger GitHub Actions
```
No deixar cap pas per al final — fer-ho tot seguit.

---

## UI — Components principals

| Fitxer | Contingut |
|--------|-----------|
| `MapScreen.kt` | Pantalla principal: mapa + controls + overlays |
| `FilterSheet.kt` | Bottom sheet de filtres (score mínim, tipus bosc, altitud) |
| `ZoneDetailSheet.kt` | Bottom sheet al clicar una zona (score, breakdown, navegar) |
| `WeatherBottomSheet.kt` | Bottom sheet amb detall meteorològic de la zona |
| `MapViewModel.kt` | Lògica: carrega zones + scores + caché + fallbacks |

### Filtres (`MapFilter`)
- `minScore: Int` — slider 0-80
- `forestTypes: Set<String>` — chips Fageda/Pi/Roure
- `altBucket: String` — "all"/"low"(<900m)/"mid"(900-1500m)/"high"(>1500m)
- Aplicats client-side via `remember(state.scoredZones, filter)`

---

## Seguretat

- **Mai hardcodejar l'AEMET API key** — únicament com a secret de GitHub Actions (`AEMET_API_KEY`)
- Tokens de GitHub: revocar immediatament després d'usar

---

## Pendents coneguts

- **Invalidació de caché per versió de dades**: quan canvia `forest_zones.geojson`, la caché de 6h pot servir scores antics. Caldria comparar un hash/versió del fitxer amb el que té la caché.
- **Terrain/hillshade**: `RasterDemSource` descartada per incompatibilitat. Alternativa: usar un estil base que ja inclogui hillshade (MapTiler Outdoor, etc.).
- **Cluster color per score màxim**: `withClusterProperty` falla silenciosament. Alternativa: clustering client-side pre-agrupat.
- **App icon, Firebase Crashlytics, keystore, Play Store**: tasques de llançament pendents.
- **Números de cluster en zones amb molt zoom**: `queryRenderedFeatures` retorna els clusters correctament però en casos límit de zoom pot haver-hi desplaçament del label.
