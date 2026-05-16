"""Python port of ScoringEngine.kt — must stay in sync with the Kotlin version."""


def normalize_soil_moisture(m3: float) -> float:
    """Volumetric soil water content 0-7cm (m3/m3). Optimal: 0.20-0.40."""
    if m3 < 0.05:
        return 0.0
    if m3 < 0.15:
        return (m3 - 0.05) / 0.10 * 0.5          # 0.0 -> 0.5
    if m3 <= 0.40:
        return 0.5 + (m3 - 0.15) / 0.25 * 0.5    # 0.5 -> 1.0
    if m3 <= 0.50:
        return 1.0 - (m3 - 0.40) / 0.10 * 0.4    # 1.0 -> 0.6 (waterlogged)
    return max(0.0, 0.6 - (m3 - 0.50) * 2)


def normalize_soil_temp(temp_c: float) -> float:
    """Soil temperature at 0-7cm. Optimal range 8-15°C for mycelium fruiting."""
    if temp_c < 4.0:
        return 0.0
    if temp_c < 8.0:
        return (temp_c - 4.0) / 4.0 * 0.7        # 0.0 -> 0.7
    if temp_c <= 15.0:
        return 1.0
    if temp_c <= 20.0:
        return 1.0 - (temp_c - 15.0) / 5.0 * 0.8 # 1.0 -> 0.2
    return max(0.0, 0.2 - (temp_c - 20.0) * 0.1)


def normalize_soil_temp_drop(drop_c: float) -> float:
    """Cooling of soil temp week-over-week (positive = cooled). Key autumn trigger."""
    if drop_c >= 4.0:
        return 1.0
    if drop_c >= 2.0:
        return 0.7 + (drop_c - 2.0) / 2.0 * 0.3  # 0.7 -> 1.0
    if drop_c >= 0.0:
        return 0.5 + drop_c / 2.0 * 0.2           # 0.5 -> 0.7
    if drop_c >= -2.0:
        return 0.5 + drop_c / 2.0 * 0.3           # 0.5 -> 0.2 (warming)
    return max(0.0, 0.2 + (drop_c + 2.0) * 0.1)


def normalize_rain_pattern(trigger_mm: float, trigger_days_ago: int) -> float:
    """
    Rain trigger: >=15mm in 3 consecutive days.
    Fruiting appears ~10-14 days after trigger (optimal window: 8-16 days ago).
    """
    if trigger_mm < 10.0:
        return 0.1   # no meaningful trigger, very unlikely
    if trigger_mm < 15.0:
        base = (trigger_mm - 10.0) / 5.0 * 0.4  # partial trigger: 0.0 -> 0.4
    else:
        base = 1.0   # full trigger (>=15mm)

    # Window score: how well-timed is the trigger?
    if trigger_days_ago < 5:
        window = 0.3   # too recent, mushrooms haven't formed yet
    elif trigger_days_ago <= 8:
        window = 0.3 + (trigger_days_ago - 5) / 3.0 * 0.7  # approaching window
    elif trigger_days_ago <= 16:
        window = 1.0   # optimal fruiting window
    elif trigger_days_ago <= 25:
        window = 1.0 - (trigger_days_ago - 16) / 9.0 * 0.7  # past peak
    else:
        window = 0.3   # too long ago, moisture lost

    return round(base * window, 3)


def normalize_forest(tipo: str) -> float:
    return {"haya": 1.0, "pino": 0.75, "roble": 0.6}.get(tipo.lower(), 0.3)


def normalize_soil_ph(ph: float) -> float:
    if ph <= 5.0:
        return 1.0
    if ph <= 6.0:
        return 1.0 - ((ph - 5.0) / 1.0) * 0.3
    if ph <= 7.0:
        return 0.7 - ((ph - 6.0) / 1.0) * 0.6
    return max(0.0, 0.1 - (ph - 7.0) * 0.1)


def normalize_altitude(m: int) -> float:
    if m < 500:
        return 0.0
    if m < 900:
        return min(1.0, (m - 500) / 400)
    if m <= 1500:
        return 1.0
    if m <= 2200:
        return max(0.0, 1.0 - (m - 1500) / 700)
    return 0.0


def normalize_orientation(o: str) -> float:
    mapping = {
        "N": 1.0, "NE": 0.85, "NW": 0.75,
        "E": 0.6, "W": 0.5,
        "SE": 0.35, "SW": 0.2, "S": 0.1,
    }
    return mapping.get(o.upper(), 0.5)


def seasonal_factor(month: int) -> float:
    factors = {10: 1.0, 9: 0.8, 11: 0.7, 8: 0.3, 12: 0.25, 5: 0.25, 4: 0.15, 6: 0.15, 7: 0.1}
    return factors.get(month, 0.1)


def compute_score(weather: dict, zone: dict, month: int) -> int:
    n_moist  = normalize_soil_moisture(weather["soil_moist_7d"])
    n_rain   = normalize_rain_pattern(weather["rain_trigger_mm"], weather["trigger_days_ago"])
    n_for    = normalize_forest(zone["bosque_tipo"])
    n_ph     = normalize_soil_ph(zone.get("soil_ph", 5.5))
    n_stemp  = normalize_soil_temp(weather["soil_temp_7d"])
    n_drop   = normalize_soil_temp_drop(weather["soil_temp_drop"])
    n_alt    = normalize_altitude(zone["altitud"])
    n_ori    = normalize_orientation(zone["orientacion"])
    n_sea    = seasonal_factor(month)

    # Weights sum to 1.0; season applied as multiplier
    base = (n_moist * 0.28 + n_rain  * 0.20 + n_for   * 0.11 +
            n_ph    * 0.10 + n_stemp * 0.11 + n_drop  * 0.10 +
            n_alt   * 0.07 + n_ori   * 0.03)
    total = base * n_sea
    return max(0, min(100, round(total * 100)))
