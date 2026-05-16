"""
One-time script: adds soil_ph to each zone in forest_zones.geojson.

Uses SoilGrids WCS to download a single GeoTIFF for the whole Pyrenees bbox,
then samples pH locally with rasterio. No rate limits, ~30 seconds total.

Usage:
    pip install requests rasterio
    python tools/build_geojson/enrich_soil_ph.py

SoilGrids is static (last updated 2020) — soil pH does not change on human
timescales, so this only needs to run once.
"""
import json
import sys
import tempfile
from pathlib import Path

import requests
import rasterio
from rasterio.transform import rowcol

GEOJSON_PATH = Path(__file__).parent.parent.parent / "app/src/main/assets/forest_zones.geojson"

# SoilGrids WCS — phh2o (pH in H2O), 0–5 cm depth, mean
# Returns pH × 10 (e.g. pixel value 65 = pH 6.5)
WCS_URL = "https://maps.isric.org/mapserv"
WCS_PARAMS = {
    "map":        "/map/phh2o.map",
    "SERVICE":    "WCS",
    "VERSION":    "2.0.1",
    "REQUEST":    "GetCoverage",
    "COVERAGEID": "phh2o_0-5cm_mean",
    "FORMAT":     "image/tiff",
    # Pyrenees bounding box with margin (lat/lon order for EPSG:4326)
    "SUBSET":     ["Y(41.5,43.5)", "X(-0.5,4.0)"],
    "SUBSETTINGCRS": "http://www.opengis.net/def/crs/EPSG/0/4326",
    "OUTPUTCRS":     "http://www.opengis.net/def/crs/EPSG/0/4326",
}

DEFAULT_PH = 5.5   # fallback if a centroid falls outside the raster


def download_tiff(out_path: Path) -> None:
    print("Downloading SoilGrids pH GeoTIFF (single request)...")
    # requests doesn't support repeated keys natively, so build URL manually
    base = f"{WCS_URL}?map={WCS_PARAMS['map']}"
    params = (
        f"&SERVICE=WCS&VERSION=2.0.1&REQUEST=GetCoverage"
        f"&COVERAGEID={WCS_PARAMS['COVERAGEID']}"
        f"&FORMAT=image/tiff"
        f"&SUBSET=Y(41.5,43.5)&SUBSET=X(-0.5,4.0)"
        f"&SUBSETTINGCRS=http://www.opengis.net/def/crs/EPSG/0/4326"
        f"&OUTPUTCRS=http://www.opengis.net/def/crs/EPSG/0/4326"
    )
    url = base + params
    r = requests.get(url, timeout=120, stream=True)
    r.raise_for_status()
    content_type = r.headers.get("Content-Type", "")
    if "tiff" not in content_type and "octet-stream" not in content_type:
        snippet = r.text[:500]
        print(f"Unexpected Content-Type: {content_type}", file=sys.stderr)
        print(f"Response: {snippet}", file=sys.stderr)
        raise RuntimeError("WCS did not return a TIFF — see error above")
    with open(out_path, "wb") as f:
        for chunk in r.iter_content(chunk_size=65536):
            f.write(chunk)
    size_kb = out_path.stat().st_size // 1024
    print(f"Downloaded {size_kb} KB -> {out_path}")


def sample_ph(tiff_path: Path, lat: float, lon: float) -> float:
    with rasterio.open(tiff_path) as src:
        # Convert lat/lon to pixel row/col
        row, col = rowcol(src.transform, lon, lat)
        if row < 0 or col < 0 or row >= src.height or col >= src.width:
            return DEFAULT_PH
        val = src.read(1)[row, col]
        nodata = src.nodata
        if nodata is not None and val == nodata:
            return DEFAULT_PH
        if val <= 0:
            return DEFAULT_PH
        return round(val / 10.0, 1)   # SoilGrids stores pH * 10


def main():
    print(f"Loading {GEOJSON_PATH}")
    with open(GEOJSON_PATH, encoding="utf-8") as f:
        geojson = json.load(f)
    features = geojson["features"]
    print(f"Loaded {len(features)} zones")

    with tempfile.TemporaryDirectory() as tmp:
        tiff_path = Path(tmp) / "phh2o.tiff"
        download_tiff(tiff_path)

        print("Sampling pH for each zone centroid...")
        for i, feat in enumerate(features, 1):
            p = feat["properties"]
            ph = sample_ph(tiff_path, p["centroid_lat"], p["centroid_lon"])
            p["soil_ph"] = ph
            if i % 500 == 0:
                print(f"  {i}/{len(features)} zones processed...")

    with open(GEOJSON_PATH, "w", encoding="utf-8") as f:
        json.dump(geojson, f, separators=(",", ":"))
    print(f"Done. Written {len(features)} zones with soil_ph.")

    phs = [feat["properties"]["soil_ph"] for feat in features]
    acidic   = sum(1 for p in phs if p < 6.0)
    neutral  = sum(1 for p in phs if 6.0 <= p < 7.0)
    alkaline = sum(1 for p in phs if p >= 7.0)
    defaults = sum(1 for p in phs if p == DEFAULT_PH)
    print(f"pH range: {min(phs):.1f} - {max(phs):.1f},  mean: {sum(phs)/len(phs):.1f}")
    print(f"Acidic (<6.0): {acidic}  |  Neutral (6-7): {neutral}  |  Alkaline (>=7): {alkaline}")
    if defaults:
        print(f"WARNING: {defaults} zones used default pH {DEFAULT_PH} (outside raster bounds)")


if __name__ == "__main__":
    main()
