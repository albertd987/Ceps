"""
Computes per-zone scores using a geographic grid of weather cells.
Instead of 5144 API calls, we use a 5×10 grid (~50 cells) over the Pyrenees.
Weather resolution: ~20km × 24km per cell — sufficient for precipitation patterns.
Runtime: ~30 seconds.
"""
import json
import sys
import time
from datetime import datetime, timezone
from pathlib import Path

import requests

from scoring import compute_score

GEOJSON_PATH = Path(__file__).parent.parent.parent / "app/src/main/assets/forest_zones.geojson"
OUT_DIR      = Path(__file__).parent / "output"
OUT_PATH     = OUT_DIR / "scores.json"

OPEN_METEO_URL = "https://api.open-meteo.com/v1/forecast"

# Grid covering Catalan Pyrenees + surroundings
LAT_MIN, LAT_MAX = 42.0, 42.9
LON_MIN, LON_MAX = 0.4, 3.3
GRID_ROWS = 10
GRID_COLS = 20

RATE_LIMIT_S = 0.5
MAX_RETRIES  = 3


def fetch_weather(lat: float, lon: float) -> dict | None:
    params = {
        "latitude":      lat,
        "longitude":     lon,
        "daily":         "precipitation_sum,temperature_2m_mean,temperature_2m_max,temperature_2m_min,relative_humidity_2m_mean",
        "past_days":     14,
        "forecast_days": 0,
        "timezone":      "Europe/Madrid",
    }
    for attempt in range(MAX_RETRIES):
        try:
            r = requests.get(OPEN_METEO_URL, params=params, timeout=20)
            r.raise_for_status()
            d = r.json()["daily"]
            n = len(d["time"])

            def last_k(lst, k):
                return lst[max(0, n - k):]

            rain10d = sum(last_k(d["precipitation_sum"], 10))
            rain7d  = sum(last_k(d["precipitation_sum"], 7))
            rain14d = sum(d["precipitation_sum"])
            temp7d  = sum(last_k(d["temperature_2m_mean"], 7)) / min(7, n)
            hum7d   = sum(last_k(d["relative_humidity_2m_mean"], 7)) / min(7, n)

            days_since = 0
            for v in reversed(d["precipitation_sum"]):
                if v > 10.0:
                    break
                days_since += 1

            return {
                "rain10d":    round(rain10d, 2),
                "rain7d":     round(rain7d, 2),
                "rain14d":    round(rain14d, 2),
                "temp7d":     round(temp7d, 2),
                "hum7d":      round(hum7d, 2),
                "days_since": days_since,
            }
        except Exception as e:
            wait = 5 * (2 ** attempt)
            if attempt < MAX_RETRIES - 1:
                print(f"  Retry {attempt+1} for ({lat:.2f},{lon:.2f}): {e} — waiting {wait}s", file=sys.stderr)
                time.sleep(wait)
            else:
                print(f"  WARN: cell ({lat:.2f},{lon:.2f}) failed after {MAX_RETRIES} retries", file=sys.stderr)
                return None


def build_grid() -> dict[tuple[int, int], dict | None]:
    """Fetch weather for each grid cell. Returns {(row, col): weather_dict}."""
    lat_step = (LAT_MAX - LAT_MIN) / GRID_ROWS
    lon_step = (LON_MAX - LON_MIN) / GRID_COLS

    grid = {}
    total = GRID_ROWS * GRID_COLS
    for row in range(GRID_ROWS):
        for col in range(GRID_COLS):
            lat = LAT_MIN + (row + 0.5) * lat_step
            lon = LON_MIN + (col + 0.5) * lon_step
            idx = row * GRID_COLS + col
            print(f"  Fetching cell {idx+1}/{total} lat={lat:.2f} lon={lon:.2f}")
            grid[(row, col)] = fetch_weather(lat, lon)
            time.sleep(RATE_LIMIT_S)
    return grid


def nearest_cell(lat: float, lon: float) -> tuple[int, int]:
    lat_step = (LAT_MAX - LAT_MIN) / GRID_ROWS
    lon_step = (LON_MAX - LON_MIN) / GRID_COLS
    row = int((lat - LAT_MIN) / lat_step)
    col = int((lon - LON_MIN) / lon_step)
    return (
        max(0, min(GRID_ROWS - 1, row)),
        max(0, min(GRID_COLS - 1, col)),
    )


def fallback_weather(grid: dict) -> dict:
    """Regional average from all successful cells — used if a zone's cell failed."""
    values = [w for w in grid.values() if w is not None]
    if not values:
        return {"rain10d": 0, "rain7d": 0, "rain14d": 0, "temp7d": 15, "hum7d": 60, "days_since": 30}
    return {k: round(sum(v[k] for v in values) / len(values), 2) for k in values[0]}


def main():
    OUT_DIR.mkdir(parents=True, exist_ok=True)

    print(f"Loading zones from {GEOJSON_PATH}")
    with open(GEOJSON_PATH, encoding="utf-8") as f:
        features = json.load(f)["features"]
    print(f"Loaded {len(features)} zones")

    month = datetime.now(timezone.utc).month

    print(f"\nFetching weather grid ({GRID_ROWS}×{GRID_COLS} = {GRID_ROWS*GRID_COLS} cells)...")
    grid = build_grid()
    ok_cells = sum(1 for w in grid.values() if w is not None)
    print(f"Grid done: {ok_cells}/{GRID_ROWS*GRID_COLS} cells successful\n")

    fallback = fallback_weather(grid)

    print("Computing scores...")
    zones_out = {}
    for feat in features:
        props   = feat["properties"]
        zone_id = props["id"]
        lat     = props["centroid_lat"]
        lon     = props["centroid_lon"]

        cell    = nearest_cell(lat, lon)
        weather = grid.get(cell) or fallback

        score = compute_score(weather, props, month)
        zones_out[zone_id] = [
            score,
            weather["rain10d"],
            weather["temp7d"],
            weather["hum7d"],
            weather["rain7d"],
            weather["rain14d"],
            weather["days_since"],
        ]

    output = {
        "v":          1,
        "updated_at": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "month":      month,
        "zones":      zones_out,
    }
    with open(OUT_PATH, "w", encoding="utf-8") as f:
        json.dump(output, f, separators=(",", ":"))

    print(f"Done. {len(zones_out)} zones written to {OUT_PATH}")
    failed_cells = GRID_ROWS * GRID_COLS - ok_cells
    if failed_cells:
        print(f"WARNING: {failed_cells} grid cells failed — those zones used regional average")


if __name__ == "__main__":
    main()
