"""
Fetches per-zone weather from Open-Meteo and computes scores for all forest zones.
Outputs output/scores.json — deployed to GitHub Pages by the CI workflow.
Runtime: ~10 min for 5144 zones (0.1s rate limit between calls).
"""
import json
import os
import sys
import time
from datetime import datetime, timezone
from pathlib import Path

import requests

from scoring import compute_score

GEOJSON_PATH = Path(__file__).parent.parent.parent / "app/src/main/assets/forest_zones.geojson"
OUT_DIR      = Path(__file__).parent / "output"
OUT_PATH     = OUT_DIR / "scores.json"
CHECKPOINT   = Path(__file__).parent / "output" / "checkpoint.json"

OPEN_METEO_URL = "https://api.open-meteo.com/v1/forecast"
RATE_LIMIT_S   = 0.1   # seconds between requests
MAX_RETRIES    = 3


def fetch_weather(lat: float, lon: float) -> dict | None:
    params = {
        "latitude":       lat,
        "longitude":      lon,
        "daily":          "precipitation_sum,temperature_2m_mean,temperature_2m_max,temperature_2m_min,relative_humidity_2m_mean",
        "past_days":      14,
        "forecast_days":  0,
        "timezone":       "Europe/Madrid",
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
                time.sleep(2 ** attempt)
            else:
                print(f"  WARN: failed after {MAX_RETRIES} retries — {e}", file=sys.stderr)
                return None


def main():
    OUT_DIR.mkdir(parents=True, exist_ok=True)

    print(f"Loading zones from {GEOJSON_PATH}")
    with open(GEOJSON_PATH, encoding="utf-8") as f:
        features = json.load(f)["features"]
    print(f"Loaded {len(features)} zones")

    month = datetime.now(timezone.utc).month

    # Load checkpoint to resume interrupted runs
    done: dict[str, list] = {}
    if CHECKPOINT.exists():
        with open(CHECKPOINT, encoding="utf-8") as f:
            done = json.load(f)
        print(f"Resuming from checkpoint: {len(done)} zones already done")

    total   = len(features)
    skipped = 0
    failed  = 0

    for i, feat in enumerate(features):
        props   = feat["properties"]
        zone_id = props["id"]

        if zone_id in done:
            skipped += 1
            continue

        lat = props["centroid_lat"]
        lon = props["centroid_lon"]

        weather = fetch_weather(lat, lon)
        time.sleep(RATE_LIMIT_S)

        if weather is None:
            failed += 1
            # Store score=0 so the zone still renders (gray)
            done[zone_id] = [0, 0.0, 15.0, 60.0, 0.0, 0.0, 30]
            continue

        score = compute_score(weather, props, month)
        # Array: [score, rain10d, temp7d, hum7d, rain7d, rain14d, days_since]
        done[zone_id] = [
            score,
            weather["rain10d"],
            weather["temp7d"],
            weather["hum7d"],
            weather["rain7d"],
            weather["rain14d"],
            weather["days_since"],
        ]

        # Save checkpoint every 100 zones
        if (i + 1) % 100 == 0:
            with open(CHECKPOINT, "w", encoding="utf-8") as f:
                json.dump(done, f, separators=(",", ":"))
            pct = (i + 1) / total * 100
            print(f"  {i+1}/{total} ({pct:.0f}%) — {failed} failed so far")

    output = {
        "v":          1,
        "updated_at": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "month":      month,
        "zones":      done,
    }
    with open(OUT_PATH, "w", encoding="utf-8") as f:
        json.dump(output, f, separators=(",", ":"))

    # Clean up checkpoint on success
    if CHECKPOINT.exists():
        CHECKPOINT.unlink()

    print(f"Done. {len(done)} zones written to {OUT_PATH} ({failed} failed)")


if __name__ == "__main__":
    main()
