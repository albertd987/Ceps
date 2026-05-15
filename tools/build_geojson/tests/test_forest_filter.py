from forest_filter import classify_forest

def test_pine_is_compatible():
    assert classify_forest("Pinus sylvestris") == (True, "pino")

def test_beech_is_compatible():
    assert classify_forest("Fagus sylvatica") == (True, "haya")

def test_oak_is_compatible():
    assert classify_forest("Quercus pubescens") == (True, "roble")

def test_unknown_species_is_not_compatible():
    assert classify_forest("Eucalyptus globulus") == (False, "otro")

def test_empty_or_none_is_not_compatible():
    assert classify_forest("") == (False, "otro")
    assert classify_forest(None) == (False, "otro")
