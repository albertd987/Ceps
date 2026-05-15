# CepAlert MVP — Documento de diseño

**Fecha:** 2026-05-15
**Estado:** Aprobado
**Base:** CepAlert — Especificación MVP v0.1 (CepAlert_MVP_Spec.pdf)

## Objetivo

App Android que predice zonas con probabilidad de salida de *Boletus edulis*
en los Pre-Pirineos y Pirineos catalanes, combinando meteorología reciente
(Open-Meteo) con cobertura forestal pública (ICGC MFE50) y un modelo de
scoring manual. El MVP se publica en Google Play Store.

Hipótesis a validar: ¿el usuario entiende y confía en la predicción de
probabilidad?

## Decisiones de diseño

| Decisión | Elección | Motivo |
|----------|----------|--------|
| SDK de mapa | MapLibre GL | Open source, gratis, sin API key ni billing |
| Backend | Ninguno (Opción A) | Scoring en cliente con APIs públicas |
| Fuente meteo | Open-Meteo | Gratis, sin API key, histórico 90d |
| Datos forestales | GeoJSON precomputado desde ICGC MFE50 | Pipeline Python offline |
| Estrategia de build | Vertical slice primero | De-risk de la integración temprano |

## Arquitectura general

Dos artefactos:

1. **App Android** (Kotlin / Jetpack Compose) — calcula el score en cliente.
2. **Pipeline de datos** (Python, ejecución única offline en el PC del
   desarrollador) — genera `forest_zones.geojson`, que se empaqueta como
   asset dentro del APK.

Flujo en runtime:

1. La app arranca y lee `forest_zones.geojson` del bundle.
2. Por cada zona, llama a Open-Meteo con las coordenadas del centroide.
3. `ScoringEngine` calcula el score por zona.
4. Se pinta el heatmap sobre el mapa según el score.
5. El resultado se cachea 6 h en disco para no sobrecargar la API.

## Pipeline de datos forestales (Python)

Script en `tools/build_geojson/`, basado en geopandas. Se ejecuta una vez
(o cuando se actualicen los datos de origen). No forma parte del runtime
de la app.

Pasos:

1. Descargar MFE50 (ICGC) y recortar a Pre-Pirineos + Pirineos catalanes.
2. Filtrar polígonos con especie dominante pino / haya / roble →
   `bosque_compatible = 1`.
3. Simplificar geometrías (reducir vértices). Objetivo: GeoJSON final
   < 1–2 MB dentro del APK.
4. Cruzar con MDT05 (IGN) para añadir altitud media y orientación por zona.
5. Exportar `forest_zones.geojson` con, por zona: `id`, geometría,
   `centroid_lat`, `centroid_lon`, `bosque_compatible`, `bosque_tipo`,
   `altitud`, `orientacion`.

## Estructura de la app

MVVM + Repository pattern + Hilt, según la spec.

```
com.cepalert
 ├── data/
 │   ├── api/         OpenMeteoApi (Retrofit)
 │   ├── model/       WeatherData, ForestZone, ScoreResult
 │   ├── local/       GeoJsonLoader (lee asset), ScoreCache (6h)
 │   └── repository/  WeatherRepository, ForestRepository
 ├── domain/
 │   └── scoring/     ScoringEngine  (función pura, sin Android ni red)
 └── ui/
     ├── map/         MapScreen, MapViewModel
     ├── detail/      ZoneDetailSheet, DetailViewModel
     └── weather/     WeatherBottomSheet
```

`ScoringEngine` es una unidad pura, testeable de forma aislada. Los
repositorios se testean con fakes; los ViewModels con datos mock.

## Modelo de scoring

```
score = humedad × 0.4 + lluvia_10d × 0.3 + bosque_compatible × 0.2
        + temperatura_optima × 0.1
```

| Variable | Peso | Lógica |
|----------|------|--------|
| humedad | 0.4 | Humedad relativa media 7d. Óptimo 70–90 %. Normalizado 0–1 |
| lluvia_10d | 0.3 | Lluvia acumulada 10d. Óptimo 30–80 mm. >120 mm penaliza |
| bosque_compatible | 0.2 | Binario: 1 si el polígono contiene pino/haya/roble |
| temperatura_optima | 0.1 | Media 7d en 10–20 °C = 1. Fuera del rango, decay lineal a 0 |

Score final 0–100. Los pesos son un punto de partida, ajustables con
observaciones reales.

## Pantallas

1. **Mapa principal** — mapa base MapLibre + capa OSM, heatmap
   (verde oscuro / amarillo / gris), chip de fecha (siempre hoy en MVP),
   FAB de refresco, banner inferior de condiciones.
2. **Detalle de zona** — score global (0–100, grande, con color),
   desglose en 4 filas (valor + peso), metadatos de zona (bosque, altitud,
   orientación), aviso legal.
3. **Condiciones meteorológicas** — bottom sheet con lluvia 7d/14d,
   temperatura máx/mín/media 7d, humedad media, días desde última lluvia
   significativa, fuente y fecha de actualización.

## Testing y manejo de errores

- **TDD** en `ScoringEngine`: normalización de variables, decay de
  temperatura, penalización de lluvia > 120 mm, casos límite.
- Repositorios testeados con fakes de las fuentes de datos.
- ViewModels testeados con datos mock.
- **Errores**: si Open-Meteo falla o no hay red, la zona se muestra en
  gris ("sin datos") — nunca un crash. La caché actúa como fallback.
- Aviso legal ("Predicción orientativa") visible siempre en el detalle.

## Fases del plan (vertical slice primero)

| Fase | Entregable |
|------|-----------|
| 0. Setup | Proyecto Gradle, MapLibre, Hilt, Compose, CI básico |
| 1. Pipeline de datos | `forest_zones.geojson` generado y empaquetado |
| 2. Vertical slice | Mapa + ~5 zonas reales + Open-Meteo + ScoringEngine + heatmap. App funcionando end-to-end |
| 3. Ampliar zonas + caché | Todas las zonas, caché 6h, FAB refresco, banner de condiciones |
| 4. Pantallas restantes | Detalle de zona, bottom sheet meteo |
| 5. Pulido | Material 3, estados de carga/error, Crashlytics, aviso legal |
| 6. Release Play Store | Cuenta Google Play Developer, keystore / Play App Signing, ficha de tienda, política de privacidad, content rating, data safety form, build firmado (AAB) |

La Fase 2 valida toda la cadena de integración pronto; las fases
siguientes amplían sobre una base que ya funciona.

## Criterios de éxito del MVP

| Métrica | Objetivo mínimo |
|---------|-----------------|
| Usuarios beta | 10–15 boletaires durante una temporada (oct–nov) |
| Retención semana 2 | ≥ 50 % vuelve a abrir la app |
| Precisión percibida | > 60 % considera la predicción útil (encuesta) |
| Crashes | < 1 crash por sesión (Firebase Crashlytics) |
| Tiempo de carga | Heatmap visible en < 5 s en 4G |

## Fuera del MVP

Cuentas de usuario, historial de observaciones, notificaciones push,
otras especies, cobertura fuera de Catalunya, modelo de IA/ML.

## Riesgos

| Riesgo | Mitigación |
|--------|------------|
| Scoring impreciso → pérdida de confianza | Comunicar que es orientativo; recoger feedback para ajustar pesos |
| API meteo cambia o limita acceso | Open-Meteo primaria (sin key); AEMET como fallback futuro |
| Mapa forestal sin detalle suficiente | MFE50 tiene buena resolución; complementar con SIGPAC si hace falta |
| GeoJSON demasiado pesado para el APK | Simplificación de geometrías en el pipeline; objetivo < 1–2 MB |
