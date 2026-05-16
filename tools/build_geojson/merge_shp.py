"""Merges MFE50 provincial shapefiles into a single Catalunya shapefile."""
import geopandas as gpd

provincias = [
    "data/mfe50/barcelona/mfe50_08.shp",
    "data/mfe50/girona/mfe50_17.shp",
    "data/mfe50/lleida/mfe50_25.shp",
    "data/mfe50/tarragona/mfe50_43.shp",
]

print("Llegint shapefiles...")
gdfs = [gpd.read_file(p) for p in provincias]
for p, gdf in zip(provincias, gdfs):
    print(f"  {p}: {len(gdf)} polígons, CRS={gdf.crs}")

combined = gpd.pd.concat(gdfs, ignore_index=True)
combined = gpd.GeoDataFrame(combined, crs=gdfs[0].crs)

print(f"\nTotal combinat: {len(combined)} polígons")
print(f"Columnes: {list(combined.columns)}")
print(f"\nValors ESPECIE1 (top 20):")
if "ESPECIE1" in combined.columns:
    print(combined["ESPECIE1"].value_counts().head(20).to_string())
else:
    print("AVÍS: columna ESPECIE1 no trobada. Columnes disponibles:", list(combined.columns))

combined.to_file("data/mfe50/catalunya_merged.shp")
print("\nEscrit: data/mfe50/catalunya_merged.shp")
