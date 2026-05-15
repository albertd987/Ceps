"""Clips the MFE50 shapefile to the Pre-Pyrenees + Pyrenees bounding box."""
import geopandas as gpd

# Bounding box (WGS84): Pre-Pyrenees + Pyrenees catalanes.
BBOX = (0.5, 42.0, 2.2, 42.85)  # minx, miny, maxx, maxy


def clip_mfe50(shapefile_path: str) -> gpd.GeoDataFrame:
    gdf = gpd.read_file(shapefile_path)
    gdf = gdf.to_crs(epsg=4326)
    minx, miny, maxx, maxy = BBOX
    return gdf.cx[minx:maxx, miny:maxy].copy()
