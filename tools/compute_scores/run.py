"""
Hybrid weather computation:
  - Precipitation pattern + soil variables: Open-Meteo 10x20 grid (model, altitude-corrected)
  - Precipitation total (rain14d): AEMET real station data (nearest station, 14d accumulated)

Soil variables used (more accurate than air proxies):
  - soil_moisture_0_to_7cm_mean: actual soil water content (m3/m3)
  - soil_temperature_0_to_7cm_mean: actual soil temp at mycelium depth

Requires env var: AEMET_API_KEY (set as GitHub Actions secret)
Falls back to Open-Meteo precipitation if AEMET unavailable.
Runtime: ~3 min (4 Open-Meteo batch calls + AEMET station fetch).
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

BATCH_SIZE   = 50   # locations per Open-Meteo request (API supports up to 50)
MAX_RETRIES  = 3


# ── Open-Meteo helpers ────────────────────────────────────────────────────────

def _parse_om_response(d: dict) -> dict:
    n = len(d["time"])

    def last_k(lst, k):
        return lst[max(0, n - k):]

    def safe_mean(lst, k):
        vals = [v for v in last_k(lst, k) if v is not None]
        return sum(vals) / len(vals) if vals else None

    precip = d["precipitation_sum"]
    rain14d = sum(v for v in precip if v is not None)

    # Soil moisture (m3/m3) — actual water content at 0-7cm
    soil_moist_raw = d.get("soil_moisture_0_to_7cm_mean") or []
    soil_moist_7d  = safe_mean(soil_moist_raw, 7) or 0.25   # fallback: field capacity

    # Soil temperature (°C) at mycelium depth 0-7cm
    soil_temp_raw    = d.get("soil_temperature_0_to_7cm_mean") or []
    soil_temp_recent = safe_mean(soil_temp_raw, 7) or 12.0
    soil_temp_old    = safe_mean(soil_temp_raw[:7], 7) or 12.0
    # positive = soil has cooled (old week warmer than recent week)
    soil_temp_drop   = round(soil_temp_old - soil_temp_recent, 1)

    # Rain trigger: best 3-consecutive-day block in last 14 days
    # Index n-1 = yesterday (1 day ago), index 0 = 14 days ago
    best_trigger_mm   = 0.0
    best_trigger_days = 99
    for i in range(n - 2):
        block = sum(precip[i:i+3])
        if block > best_trigger_mm:
            best_trigger_mm   = block
            # days ago the block ended: yesterday=1, so index i+2 -> (n-1-i-2)+1 = n-i-2
            best_trigger_days = n - i - 2

    return {
        "soil_moist_7d":    round(soil_moist_7d, 4),
        "soil_temp_7d":     round(soil_temp_recent, 1),
        "soil_temp_drop":   soil_temp_drop,
        "rain14d":          round(rain14d, 1),
        "rain_trigger_mm":  round(best_trigger_mm, 1),
        "trigger_days_ago": best_trigger_days,
    }


def fetch_om_batch(cells: list[tuple[int, int, float, float]]) -> dict[tuple[int, int], dict | None]:
    """Fetch weather for multiple grid cells in one API call."""
    lats = ",".join(f"{lat:.4f}" for _, _, lat, _ in cells)
    lons = ",".join(f"{lon:.4f}" for _, _, _, lon in cells)
    params = {
        "latitude":      lats,
        "longitude":     lons,
        "daily":         "precipitation_sum,"
                         "soil_temperature_0_to_7cm_mean,"
                         "soil_moisture_0_to_7cm_mean",
        "past_days":     14,
        "forecast_days": 0,
        "timezone":      "Europe/Madrid",
    }
    for attempt in range(MAX_RETRIES):
        try:
            r = requests.get(OPEN_METEO_URL, params=params, timeout=30)
            r.raise_for_status()
            data = r.json()
            # Single location returns a dict; multiple returns a list
            if isinstance(data, dict):
                data = [data]
            result = {}
            for i, (row, col, _, _) in enumerate(cells):
                try:
                    result[(row, col)] = _parse_om_response(data[i]["daily"])
                except Exception:
                    result[(row, col)] = None
            return result
        except Exception as e:
            wait = 10 * (2 ** attempt)
            if attempt < MAX_RETRIES - 1:
                print(f"  Retry {attempt+1} batch of {len(cells)}, waiting {wait}s: {e}", file=sys.stderr)
                time.sleep(wait)
            else:
                print(f"  WARN: batch of {len(cells)} failed after {MAX_RETRIES} retries", file=sys.stderr)
                return {(row, col): None for row, col, _, _ in cells}
    return {}


def build_om_grid() -> dict[tuple[int, int], dict | None]:
    lat_step = (LAT_MAX - LAT_MIN) / GRID_ROWS
    lon_step = (LON_MAX - LON_MIN) / GRID_COLS

    all_cells = [
        (row, col,
         LAT_MIN + (row + 0.5) * lat_step,
         LON_MIN + (col + 0.5) * lon_step)
        for row in range(GRID_ROWS)
        for col in range(GRID_COLS)
    ]

    total  = len(all_cells)
    grid   = {}
    done   = 0

    for i in range(0, total, BATCH_SIZE):
        batch = all_cells[i:i + BATCH_SIZE]
        print(f"  OM batch {i//BATCH_SIZE + 1}/{-(-total//BATCH_SIZE)}: cells {i+1}-{min(i+BATCH_SIZE, total)}/{total}")
        grid.update(fetch_om_batch(batch))
        done += len(batch)
        if done < total:
            time.sleep(1)  # brief pause between batches

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
        return {"soil_moist_7d": 0.25, "soil_temp_7d": 12.0, "soil_temp_drop": 0.0,
                "rain14d": 0, "rain_trigger_mm": 0, "trigger_days_ago": 99}
    return {k: round(sum(v[k] for v in values) / len(values), 4) for k in values[0]}


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

        # AEMET overrides rain14d with real station measurement
        aemet_rain = None
        if aemet_stations:
            aemet_rain = nearest_station_precip(lat, lon, aemet_stations, aemet_precip)

        if aemet_rain is not None:
            aemet_hits += 1

        weather = {
            "soil_moist_7d":    om_w["soil_moist_7d"],
            "soil_temp_7d":     om_w["soil_temp_7d"],
            "soil_temp_drop":   om_w["soil_temp_drop"],
            "rain14d":          round(aemet_rain, 1) if aemet_rain is not None else om_w["rain14d"],
            "rain_trigger_mm":  om_w["rain_trigger_mm"],
            "trigger_days_ago": om_w["trigger_days_ago"],
        }

        score = compute_score(weather, props, month)
        zones_out[zone_id] = [
            score,
            weather["soil_moist_7d"],
            weather["soil_temp_7d"],
            weather["soil_temp_drop"],
            weather["rain14d"],
            weather["rain_trigger_mm"],
            weather["trigger_days_ago"],
        ]

    output = {
        "v":          2,
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
