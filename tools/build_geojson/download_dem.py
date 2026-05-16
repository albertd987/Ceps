"""Downloads MDT25 from IGN WCS for the Pyrenean bbox and merges into one GeoTIFF."""
import os
import sys
import requests
import rasterio
from rasterio.merge import merge

WCS_URL = "https://servicios.idee.es/wcs-inspire/mdt"
COVERAGE_ID = "Elevacion4258_25"  # 25m resolution, geographic EPSG:4258
BBOX = (0.5, 42.0, 2.2, 42.85)   # minLon, minLat, maxLon, maxLat (same as clip.py)
TILE_DEG = 0.3                    # ~30km tiles to avoid server size limits
TILES_DIR = "data/mdt05/tiles"


def download_tile(lon1, lat1, lon2, lat2, out_path):
    params = {
        "service": "WCS",
        "version": "2.0.1",
        "request": "GetCoverage",
        "coverageid": COVERAGE_ID,
        "format": "image/tiff",
        "subset": [f"Long({lon1},{lon2})", f"Lat({lat1},{lat2})"],
    }
    r = requests.get(WCS_URL, params=params, timeout=120, stream=True)
    r.raise_for_status()
    if "xml" in r.headers.get("Content-Type", "") or "text" in r.headers.get("Content-Type", ""):
        print(f"  Error del servidor: {r.text[:300]}")
        return False
    with open(out_path, "wb") as f:
        for chunk in r.iter_content(chunk_size=65536):
            f.write(chunk)
    return True


def main(out_path):
    os.makedirs(TILES_DIR, exist_ok=True)
    os.makedirs(os.path.dirname(out_path) or ".", exist_ok=True)

    minlon, minlat, maxlon, maxlat = BBOX
    xs = []
    x = minlon
    while x < maxlon:
        xs.append((round(x, 4), round(min(x + TILE_DEG, maxlon), 4)))
        x += TILE_DEG

    ys = []
    y = minlat
    while y < maxlat:
        ys.append((round(y, 4), round(min(y + TILE_DEG, maxlat), 4)))
        y += TILE_DEG

    tiles = [(x1, y1, x2, y2) for (x1, x2) in xs for (y1, y2) in ys]
    print(f"Descargant {len(tiles)} tiles de MDT25...")

    tile_paths = []
    for i, (x1, y1, x2, y2) in enumerate(tiles):
        tile_path = f"{TILES_DIR}/tile_{i:03d}.tif"
        if os.path.exists(tile_path):
            print(f"  [{i+1}/{len(tiles)}] ja en cache: {tile_path}")
        else:
            print(f"  [{i+1}/{len(tiles)}] ({x1},{y1}) -> ({x2},{y2}) ...", end=" ", flush=True)
            if download_tile(x1, y1, x2, y2, tile_path):
                size_kb = os.path.getsize(tile_path) // 1024
                print(f"OK ({size_kb} KB)")
            else:
                print("FALLAT")
                continue
        tile_paths.append(tile_path)

    print(f"\nFusionant {len(tile_paths)} tiles...")
    datasets = [rasterio.open(p) for p in tile_paths]
    mosaic, transform = merge(datasets)

    meta = datasets[0].meta.copy()
    meta.update({
        "driver": "GTiff",
        "height": mosaic.shape[1],
        "width": mosaic.shape[2],
        "transform": transform,
        "compress": "lzw",
    })

    with rasterio.open(out_path, "w", **meta) as dst:
        dst.write(mosaic)

    for ds in datasets:
        ds.close()

    size_mb = os.path.getsize(out_path) / (1024 * 1024)
    print(f"DEM guardat: {out_path} ({size_mb:.1f} MB)")


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else "data/mdt05/dem_pirineos.tif"
    main(out)
