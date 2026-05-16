"""Builds forest_zones.geojson: clip -> filter -> simplify -> enrich -> export."""
import json
import sys

from clip import clip_mfe50
from enrich import enrich_zone
from forest_filter import classify_forest

SPECIES_COLUMN = "NOM_FORARB"  # MFE50 formation name column (NOM_FORARB in Spanish MFE50).
SIMPLIFY_TOLERANCE = 0.005   # ~500 m, in degrees — balances detail vs. APK size.
MIN_AREA_DEG2 = 1e-6         # ~0.01 km² minimum polygon area; drops slivers.
COMPATIBLE_ONLY = True       # MVP: only export compatible forest zones.


def build(shapefile_path: str, dem_path: str, out_path: str) -> None:
    gdf = clip_mfe50(shapefile_path)
    gdf["geometry"] = gdf["geometry"].simplify(SIMPLIFY_TOLERANCE, preserve_topology=True)
    gdf = gdf[gdf["geometry"].area >= MIN_AREA_DEG2].copy()
    print(f"After simplify+filter: {len(gdf)} zones")

    features = []
    for i, row in gdf.iterrows():
        compatible, tipo = classify_forest(row.get(SPECIES_COLUMN))
        if COMPATIBLE_ONLY and not compatible:
            continue
        altitud, orientacion = enrich_zone(row.geometry, dem_path)
        centroid = row.geometry.centroid
        features.append({
            "type": "Feature",
            "geometry": row.geometry.__geo_interface__,
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
