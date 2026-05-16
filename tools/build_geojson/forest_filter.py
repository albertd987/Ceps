"""Maps MFE50 dominant-species names to compatibility for Boletus edulis."""

_COMPATIBLE = {
    "pino": ("pin", "pinus", "coní", "coni"),   # Pinares + Mezclas de coníferas
    "haya": ("fag", "fagus", "haya", "hayedo"),
    "roble": ("quercus", "roure", "roble", "quejigar", "alcornoc", "encinar"),
}


def classify_forest(species: str | None) -> tuple[bool, str]:
    if not species:
        return (False, "otro")
    s = species.strip().lower()
    for label, keywords in _COMPATIBLE.items():
        if any(k in s for k in keywords):
            return (True, label)
    return (False, "otro")
