# CepAlert — Forest Zones GeoJSON Builder

Generates `forest_zones.geojson` (bundled in the Android app) from public data.

## Data sources (manual download required)

1. **MFE50 shapefile** — Mapa Forestal de España a escala 1:50.000 for Catalunya  
   Download from: https://www.miteco.gob.es/es/biodiversidad/servicios/banco-datos-naturaleza/informacion-disponible/mfe50.html  
   Extract the Catalunya `.shp` files into `data/mfe50/`

2. **MDT05 DEM** — Modelo Digital del Terreno (5m resolution)  
   Download tiles covering the Pyrenees from: https://centrodedescargas.cnig.es/  
   Merge tiles and save as `data/mdt05.tif`

## Setup

```bash
pip install -r requirements.txt
```

## Run

```bash
python build_geojson.py data/mfe50/your_file.shp data/mdt05.tif ../../app/src/main/assets/forest_zones.geojson
```

Output: `app/src/main/assets/forest_zones.geojson` (target < 2 MB)

If the output is > 2 MB, increase `SIMPLIFY_TOLERANCE` in `build_geojson.py` and re-run.

## Tests

```bash
python -m pytest tests/ -v
```
