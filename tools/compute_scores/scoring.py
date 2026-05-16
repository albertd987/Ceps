"""Python port of ScoringEngine.kt — must stay in sync with the Kotlin version."""
import math


def normalize_humidity(rh: float) -> float:
    if 70 <= rh <= 90:
        return 1.0
    if rh < 70:
        return max(0.0, (rh - 40) / 30)
    return max(0.0, (100 - rh) / 10)


def normalize_rain10d(mm: float) -> float:
    if 30 <= mm <= 80:
        return 1.0
    if mm < 30:
        return min(1.0, mm / 30)
    if mm <= 120:
        return 1.0 - (mm - 80) / 40
    return 0.2


def normalize_temperature(temp: float) -> float:
    if 10 <= temp <= 20:
        return 1.0
    if temp < 10:
        return max(0.0, 1.0 - (10 - temp) / 10)
    return max(0.0, 1.0 - (temp - 20) / 10)


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
    n_hum  = normalize_humidity(weather["hum7d"])
    n_rain = normalize_rain10d(weather["rain10d"])
    n_for  = normalize_forest(zone["bosque_tipo"])
    n_ph   = normalize_soil_ph(zone.get("soil_ph", 5.5))
    n_temp = normalize_temperature(weather["temp7d"])
    n_alt  = normalize_altitude(zone["altitud"])
    n_ori  = normalize_orientation(zone["orientacion"])
    n_sea  = seasonal_factor(month)

    base  = (n_hum * 0.30 + n_rain * 0.22 + n_for * 0.13 +
             n_ph  * 0.12 + n_temp * 0.09 + n_alt * 0.09 + n_ori * 0.05)
    total = base * n_sea
    return max(0, min(100, round(total * 100)))
