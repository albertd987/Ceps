"""
Hybrid weather computation:
  - Precipitation: AEMET real station data (nearest station, 14d accumulated)
  - Temperature + Humidity: Open-Meteo 10x20 grid (model, altitude-corrected)

Requires env var: AEMET_API_KEY (set as GitHub Actions secret)
Falls back to Open-Meteo precipitation if AEMET unavailable.
Runtime: ~3 min (200 Open-Meteo calls + AEMET station fetch).
"""
import json
import os
import sys
import time
from datetime import datetime, timezone
from pathlib import Path

import requests

from aemet import get_stations, get_precip_14d, nearest_station_precip
from scoring import compute_score

GEOJSON_PATH = Path(__file__).parent.parent.parent / "app/src/main/assets/forest_zones.geojson"
OUT_DIR      = Path(__file__).parent / "output"
OUT_PATH     = OUT_DIR / "scores.json"

OPEN_METEO_URL = "https://api.open-meteo.com/v1/forecast"

LAT_MIN, LAT_MAX = 42.0, 42.9
LON_MIN, LON_MAX = 0.4,  3.3
GRID_ROWS = 10
GRID_COLS = 20

RATE_LIMIT_S = 0.5
MAX_RETRIES  = 3


# ── Open-Meteo helpers ────────────────────────────────────────────────────────

def fetch_om_weather(lat: float, lon: float) -> dict | None:
    params = {
        "latitude":      lat,
        "longitude":     lon,
        "daily":         "precipitation_sum,temperature_2m_mean,temperature_2m_max,"
                         "temperature_2m_min,relative_humidity_2m_mean",
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
            if attempt < MAX_RETRIES - 1:
                time.sleep(5 * (2 ** attempt))
            else:
                print(f"  WARN: OM cell ({lat:.2f},{lon:.2f}) failed: {e}", file=sys.stderr)
                return None


def build_om_grid() -> dict[tuple[int, int], dict | None]:
    lat_step = (LAT_MAX - LAT_MIN) / GRID_ROWS
    lon_step = (LON_MAX - LON_MIN) / GRID_COLS
    grid = {}
    total = GRID_ROWS * GRID_COLS
    for row in range(GRID_ROWS):
        for col in range(GRID_COLS):
            lat = LAT_MIN + (row + 0.5) * lat_step
            lon = LON_MIN + (col + 0.5) * lon_step
            idx = row * GRID_COLS + col
            print(f"  OM grid {idx+1}/{total} ({lat:.2f},{lon:.2f})")
            grid[(row, col)] = fetch_om_weather(lat, lon)
            time.sleep(RATE_LIMIT_S)
    return grid


def nearest_om_cell(lat: float, lon: float) -> tuple[int, int]:
    lat_step = (LAT_MAX - LAT_MIN) / GRID_ROWS
    lon_step = (LON_MAX - LON_MIN) / GRID_COLS
    row = int((lat - LAT_MIN) / lat_step)
    col = int((lon - LON_MIN) / lon_step)
    return (max(0, min(GRID_ROWS - 1, row)), max(0, min(GRID_COLS - 1, col)))


def om_fallback(grid: dict) -> dict:
    values = [w for w in grid.values() if w is not None]
    if not values:
        return {"rain10d": 0, "rain7d": 0, "rain14d": 0,
                "temp7d": 15, "hum7d": 60, "days_since": 30}
    return {k: round(sum(v[k] for v in values) / len(values), 2) for k in values[0]}


# ── AEMET helpers ─────────────────────────────────────────────────────────────

def load_aemet_precip(api_key: str) -> tuple[list[dict], dict[str, float]]:
    print("  Fetching AEMET station inventory...")
    stations = get_stations(api_key)
    print(f"  {len(stations)} stations in bounding box")
    if not stations:
        return [], {}
    station_ids = [s["id"] for s in stations]
    print("  Fetching AEMET 14-day precipitation...")
    precip = get_precip_14d(station_ids, api_key)
    covered = sum(1 for sid in station_ids if sid in precip)
    print(f"  {covered}/{len(stations)} stations with precipitation data")
    return stations, precip


# ── Main ─────────────────────────────────────────────────────────────────────

def main():
    OUT_DIR.mkdir(parents=True, exist_ok=True)

    print(f"Loading zones from {GEOJSON_PATH}")
    with open(GEOJSON_PATH, encoding="utf-8") as f:
        features = json.load(f)["features"]
    print(f"Loaded {len(features)} zones\n")

    month = datetime.now(timezone.utc).month

    # ── AEMET precipitation ───────────────────────────────────────────────────
    aemet_key = os.environ.get("AEMET_API_KEY", "")
    aemet_stations, aemet_precip = [], {}
    if aemet_key:
        print("=== AEMET (precipitation) ===")
        try:
            aemet_stations, aemet_precip = load_aemet_precip(aemet_key)
        except Exception as e:
            print(f"  AEMET failed, using Open-Meteo precip: {e}", file=sys.stderr)
    else:
        print("AEMET_API_KEY not set — using Open-Meteo for precipitation")

    # ── Open-Meteo grid (temperature + humidity + fallback precip) ────────────
    print("\n=== Open-Meteo grid (temperature + humidity) ===")
    om_grid = build_om_grid()
    ok_cells = sum(1 for w in om_grid.values() if w is not None)
    print(f"Grid done: {ok_cells}/{GRID_ROWS*GRID_COLS} cells OK\n")
    fallback = om_fallback(om_grid)

    # ── Score each zone ───────────────────────────────────────────────────────
    print("Computing scores...")
    zones_out = {}
    aemet_hits = 0

    for feat in features:
        props   = feat["properties"]
        zone_id = props["id"]
        lat     = props["centroid_lat"]
        lon     = props["centroid_lon"]

        # Base weather from nearest Open-Meteo cell
        cell    = nearest_om_cell(lat, lon)
        om_w    = om_grid.get(cell) or fallback

        # Override precipitation with AEMET if available
        aemet_rain = None
        if aemet_stations:
            aemet_rain = nearest_station_precip(lat, lon, aemet_stations, aemet_precip)

        if aemet_rain is not None:
            aemet_hits += 1
            rain10d    = aemet_rain                         # real measurement
            rain7d     = aemet_rain * (7 / 14)             # proportional estimate
            rain14d    = aemet_rain
            days_since = om_w["days_since"]                 # from Open-Meteo
        else:
            rain10d    = om_w["rain10d"]
            rain7d     = om_w["rain7d"]
            rain14d    = om_w["rain14d"]
            days_since = om_w["days_since"]

        weather = {
            "rain10d":    round(rain10d, 2),
            "rain7d":     round(rain7d, 2),
            "rain14d":    round(rain14d, 2),
            "temp7d":     om_w["temp7d"],
            "hum7d":      om_w["hum7d"],
            "days_since": days_since,
        }

        score = compute_score(weather, props, month)
        zones_out[zone_id] = [
            score, weather["rain10d"], weather["temp7d"],
            weather["hum7d"], weather["rain7d"], weather["rain14d"],
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

    aemet_pct = aemet_hits / len(features) * 100 if features else 0
    print(f"\nDone. {len(zones_out)} zones written.")
    print(f"Precipitation source: AEMET={aemet_hits} ({aemet_pct:.0f}%), "
          f"Open-Meteo={len(features)-aemet_hits} ({100-aemet_pct:.0f}%)")


if __name__ == "__main__":
    main()
