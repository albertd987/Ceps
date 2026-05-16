"""AEMET OpenData client — fetches real precipitation from Pyrenees stations."""
import re
import sys
import time
from datetime import datetime, timedelta, timezone

import requests

BASE_URL    = "https://opendata.aemet.es/opendata/api"
RATE_LIMIT  = 0.5   # seconds between calls (limit: 50 req/min)

# Bounding box: Catalan Pyrenees + margin
LAT_MIN, LAT_MAX = 41.8, 43.2
LON_MIN, LON_MAX = 0.0,  3.5


def _dms_to_decimal(dms_str: str) -> float:
    """Convert AEMET DMS string like '424512N' or '013045E' to decimal degrees."""
    match = re.match(r"(\d{2})(\d{2})(\d{2})([NSEW])", dms_str.strip())
    if not match:
        raise ValueError(f"Cannot parse DMS: {dms_str!r}")
    deg, mn, sec, direction = int(match[1]), int(match[2]), int(match[3]), match[4]
    decimal = deg + mn / 60 + sec / 3600
    if direction in ("S", "W"):
        decimal = -decimal
    return decimal


def _parse_precip(value: str) -> float:
    """Parse AEMET precipitation field. Returns 0.0 for trace/missing."""
    if not value or value.strip() in ("", "Ip", "ip", "Acum"):
        return 0.0
    return float(value.replace(",", "."))


def _aemet_get(endpoint: str, api_key: str) -> list | dict | None:
    """Two-step AEMET call: get redirect URL then fetch data."""
    headers = {"api_key": api_key}
    try:
        r1 = requests.get(f"{BASE_URL}{endpoint}", headers=headers, timeout=15)
        r1.raise_for_status()
        meta = r1.json()
        if meta.get("estado") != 200:
            print(f"  AEMET error: {meta.get('descripcion')}", file=sys.stderr)
            return None
        time.sleep(RATE_LIMIT)
        r2 = requests.get(meta["datos"], timeout=30)
        r2.raise_for_status()
        return r2.json()
    except Exception as e:
        print(f"  AEMET request failed: {e}", file=sys.stderr)
        return None


def get_stations(api_key: str) -> list[dict]:
    """Return stations within the Pyrenees bounding box with decimal coordinates."""
    data = _aemet_get("/valores/climatologicos/inventarioestaciones/todasestaciones", api_key)
    if not data:
        return []
    stations = []
    for s in data:
        try:
            lat = _dms_to_decimal(s["latitud"])
            lon = _dms_to_decimal(s["longitud"])
        except (ValueError, KeyError):
            continue
        if LAT_MIN <= lat <= LAT_MAX and LON_MIN <= lon <= LON_MAX:
            stations.append({
                "id":      s["indicativo"],
                "name":    s.get("nombre", ""),
                "lat":     lat,
                "lon":     lon,
                "alt":     int(s.get("altitud", 0)),
            })
    return stations


def get_precip_14d(station_ids: list[str], api_key: str) -> dict[str, float]:
    """
    Return {station_id: total_precipitation_last_14_days} for given stations.
    AEMET max date range per call: 15 days — one call covers our 14-day window.
    """
    today     = datetime.now(timezone.utc).date()
    date_from = today - timedelta(days=14)
    date_to   = today - timedelta(days=1)  # yesterday (today not yet closed)

    fmt       = "%Y-%m-%dT00:00:00UTC"
    endpoint  = (
        f"/valores/climatologicos/diarios/datos"
        f"/fechaini/{date_from.strftime('%Y-%m-%d')}T00:00:00UTC"
        f"/fechafin/{date_to.strftime('%Y-%m-%d')}T00:00:00UTC"
        f"/todasestaciones"
    )

    data = _aemet_get(endpoint, api_key)
    if not data:
        return {}

    totals: dict[str, float] = {}
    for record in data:
        sid = record.get("indicativo", "")
        if sid not in station_ids:
            continue
        try:
            p = _parse_precip(record.get("prec", ""))
        except ValueError:
            p = 0.0
        totals[sid] = totals.get(sid, 0.0) + p

    return totals


def nearest_station_precip(
    lat: float,
    lon: float,
    stations: list[dict],
    precip_by_station: dict[str, float],
    max_distance_deg: float = 0.3   # ~30km
) -> float | None:
    """
    Return the 14-day precipitation total from the nearest AEMET station.
    Returns None if no station is within max_distance_deg.
    """
    best_dist  = float("inf")
    best_precip = None

    for s in stations:
        if s["id"] not in precip_by_station:
            continue
        dlat = s["lat"] - lat
        dlon = s["lon"] - lon
        dist = (dlat ** 2 + dlon ** 2) ** 0.5
        if dist < best_dist and dist <= max_distance_deg:
            best_dist   = dist
            best_precip = precip_by_station[s["id"]]

    return best_precip
