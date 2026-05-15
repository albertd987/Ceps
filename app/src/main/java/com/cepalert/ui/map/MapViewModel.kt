package com.cepalert.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cepalert.data.model.ForestZone
import com.cepalert.data.model.ScoreResult
import com.cepalert.data.model.WeatherData
import com.cepalert.data.repository.ForestRepository
import com.cepalert.data.repository.WeatherRepository
import com.cepalert.domain.scoring.ScoringEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ScoredZone(val zone: ForestZone, val score: ScoreResult?)

data class MapUiState(
    val isLoading: Boolean = true,
    val scoredZones: List<ScoredZone> = emptyList(),
    val weather: WeatherData? = null,
    val error: String? = null
)

@HiltViewModel
class MapViewModel @Inject constructor(
    private val forestRepository: ForestRepository,
    private val weatherRepository: WeatherRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            runCatching {
                val zones = forestRepository.loadZones()
                var representativeWeather: WeatherData? = null
                val scored = zones.map { zone ->
                    val weather = runCatching {
                        weatherRepository.getWeather(zone.centroidLat, zone.centroidLon)
                    }.getOrNull()
                    if (weather != null && representativeWeather == null) {
                        representativeWeather = weather
                    }
                    ScoredZone(
                        zone = zone,
                        score = weather?.let { ScoringEngine.score(it, zone) }
                    )
                }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        scoredZones = scored,
                        weather = representativeWeather
                    )
                }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "Error desconocido")
                }
            }
        }
    }
}
