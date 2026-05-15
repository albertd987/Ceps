"""Adds mean altitude and aspect per polygon from the MDT05 DEM raster."""
import numpy as np
import rasterio
from rasterio.mask import mask

_ASPECT_LABELS = ["N", "NE", "E", "SE", "S", "SO", "O", "NO"]


def _aspect_label(degrees: float) -> str:
    idx = int(((degrees + 22.5) % 360) / 45)
    return _ASPECT_LABELS[idx]


def enrich_zone(geometry, dem_path: str) -> tuple[int, str]:
    """Returns (mean_altitude_m, aspect_label) for one polygon."""
    with rasterio.open(dem_path) as src:
        out, transform = mask(src, [geometry], crop=True, nodata=np.nan)
    elevation = out[0]
    valid = elevation[~np.isnan(elevation)]
    if valid.size == 0:
        return (0, "N")
    mean_alt = int(round(float(valid.mean())))
    gy, gx = np.gradient(elevation)
    aspect_rad = np.arctan2(-gx, gy)
    aspect_deg = (np.degrees(aspect_rad) + 360) % 360
    mean_aspect = float(np.nanmean(aspect_deg))
    return (mean_alt, _aspect_label(mean_aspect))
