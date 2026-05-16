"""
One-time script: adds soil_ph to each zone in forest_zones.geojson via SoilGrids v2.0.
SoilGrids is static (last updated 2020) so this only needs to run once.

Usage: python tools/build_geojson/enrich_soil_ph.py

Runtime: ~15-20 min for 5144 zones (de-duplicated to ~1000 unique grid cells).
Results are cached in soil_ph_cache.json to allow resuming after interruptions.
"""
import json
import sys
import time
from pathlib import Path

import requests

GEOJSON_PATH = Path(__file__).parent.parent.parent / "app/src/main/assets/forest_zones.geojson"
CACHE_PATH   = Path(__file__).parent / "soil_ph_cache.json"
API_URL      = "https://rest.isric.org/soilgrids/v2.0/properties/query"

RATE_LIMIT   = 0.12   # seconds between requests (~8 req/s, below 10 req/s limit)
MAX_RETRIES  = 3
DEFAULT_PH   = 5.5    # slightly acidic fallback when API unavailable


def _cache_key(lat: float, lon: float) -> str:
    # Round to 2 decimal places (~1 km grid) — SoilGrids resolution is 250 m,
    # so 1 km de-duplication loses no meaningful precision.
    return f"{round(lat, 2)},{round(lon, 2)}"


def fetch_ph(lat: float, lon: float) -> float:
    params = {
        "lon":      lon,
        "lat":      lat,
        "property": "phh2o",
        "depth":    "0-5cm",
        "value":    "mean",
    }
    for attempt in range(MAX_RETRIES):
        try:
            r = requests.get(API_URL, params=params, timeout=20)
            r.raise_for_status()
            data = r.json()
            # SoilGrids returns pH * 10 (e.g. 65 = pH 6.5)
            val = data["properties"]["layers"][0]["depths"][0]["values"]["mean"]
            return round(val / 10, 1) if val is not None else DEFAULT_PH
        except Exception as e:
            wait = 5 * (2 ** attempt)
            if attempt < MAX_RETRIES - 1:
                print(f"  Retry {attempt+1} ({lat},{lon}): {e} — waiting {wait}s", file=sys.stderr)
                time.sleep(wait)
            else:
                print(f"  WARN: failed for ({lat},{lon}), using default {DEFAULT_PH}", file=sys.stderr)
                return DEFAULT_PH
    return DEFAULT_PH


def main():
    print(f"Loading {GEOJSON_PATH}")
    with open(GEOJSON_PATH, encoding="utf-8") as f:
        geojson = json.load(f)
    features = geojson["features"]
    print(f"Loaded {len(features)} zones")

    # Load cache
    cache: dict[str, float] = {}
    if CACHE_PATH.exists():
        with open(CACHE_PATH, encoding="utf-8") as f:
            cache = json.load(f)
        print(f"Cache loaded: {len(cache)} entries")

    # Collect unique grid cells
    unique_keys = {}
    for feat in features:
        p = feat["properties"]
        key = _cache_key(p["centroid_lat"], p["centroid_lon"])
        if key not in cache and key not in unique_keys:
            unique_keys[key] = (p["centroid_lat"], p["centroid_lon"])

    total_to_fetch = len(unique_keys)
    print(f"Unique cells to fetch: {total_to_fetch} (cached: {len(cache)})")

    # Fetch missing cells
    for i, (key, (lat, lon)) in enumerate(unique_keys.items(), 1):
        if i % 50 == 0 or i == 1:
            print(f"  [{i}/{total_to_fetch}] fetching ({lat}, {lon})...")
        ph = fetch_ph(lat, lon)
        cache[key] = ph
        time.sleep(RATE_LIMIT)
        # Save cache periodically
        if i % 100 == 0:
            with open(CACHE_PATH, "w", encoding="utf-8") as f:
                json.dump(cache, f)

    # Final cache save
    with open(CACHE_PATH, "w", encoding="utf-8") as f:
        json.dump(cache, f)
    print(f"Cache saved ({len(cache)} entries)")

    # Enrich features
    not_found = 0
    for feat in features:
        p = feat["properties"]
        key = _cache_key(p["centroid_lat"], p["centroid_lon"])
        ph = cache.get(key, DEFAULT_PH)
        if key not in cache:
            not_found += 1
        p["soil_ph"] = ph

    if not_found:
        print(f"WARNING: {not_found} zones used default pH (cache miss)", file=sys.stderr)

    # Write enriched GeoJSON
    with open(GEOJSON_PATH, "w", encoding="utf-8") as f:
        json.dump(geojson, f, separators=(",", ":"))
    print(f"Done. Written {len(features)} zones with soil_ph to {GEOJSON_PATH}")

    # Quick stats
    phs = [feat["properties"]["soil_ph"] for feat in features]
    print(f"pH range: {min(phs):.1f} – {max(phs):.1f}, mean: {sum(phs)/len(phs):.1f}")
    acidic  = sum(1 for p in phs if p < 6.0)
    neutral = sum(1 for p in phs if 6.0 <= p < 7.0)
    alkaline = sum(1 for p in phs if p >= 7.0)
    print(f"Acidic (<6): {acidic}, Neutral (6-7): {neutral}, Alkaline (>=7): {alkaline}")


if __name__ == "__main__":
    main()
