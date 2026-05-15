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
