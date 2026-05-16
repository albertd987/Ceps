package com.cepalert.ui.map

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cepalert.data.local.CachedScores
import com.cepalert.data.local.ScoreCache
import com.cepalert.data.model.ForestZone
import com.cepalert.data.model.RemoteScores
import com.cepalert.data.model.ScoreResult
import com.cepalert.data.model.WeatherData
import com.cepalert.data.model.regionalWeather
import com.cepalert.data.model.toWeatherData
import com.cepalert.data.repository.ForestRepository
import com.cepalert.data.repository.RemoteScoresRepository
import com.cepalert.data.repository.WeatherRepository
import com.cepalert.domain.scoring.ScoringEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
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
    private val remoteScoresRepository: RemoteScoresRepository,
    private val weatherRepository: WeatherRepository,
    private val scoreCache: ScoreCache
) : ViewModel() {

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val cached = scoreCache.read(System.currentTimeMillis())
            if (cached != null) {
                val zones   = forestRepository.loadZones()
                val zoneMap = zones.associateBy { it.id }
                val scored  = cached.scores.mapNotNull { result ->
                    zoneMap[result.zoneId]?.let { zone -> ScoredZone(zone, result) }
                }
                _uiState.update { it.copy(isLoading = false, scoredZones = scored, weather = cached.weather) }
            } else {
                fetchAndScore()
            }
        }
    }

    fun forceRefresh() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch { fetchAndScore() }
    }

    private suspend fun fetchAndScore() {
        runCatching {
            val zones = forestRepository.loadZones()
            val month = LocalDate.now().monthValue

            // Try remote pre-computed scores (per-zone weather, max precision)
            val remoteScores = runCatching {
                remoteScoresRepository.getScores()
            }.onFailure {
                Log.w("CepAlert", "Remote scores unavailable, falling back to centroid weather: ${it.message}")
            }.getOrNull()

            val (scored, displayWeather) = if (remoteScores != null) {
                scoreFromRemote(zones, remoteScores, month)
            } else {
                scoreFromCentroid(zones, month)
            }

            if (displayWeather != null) {
                scoreCache.write(
                    CachedScores(
                        savedAtEpochMs = System.currentTimeMillis(),
                        weather        = displayWeather,
                        scores         = scored.mapNotNull { it.score }
                    )
                )
            }
            _uiState.update { it.copy(isLoading = false, scoredZones = scored, weather = displayWeather) }

        }.onFailure { e ->
            Log.e("CepAlert", "fetchAndScore() FATAL: ${e.message}", e)
            _uiState.update { it.copy(isLoading = false, error = e.message ?: "Error desconocido") }
        }
    }

    private fun scoreFromRemote(
        zones: List<ForestZone>,
        remote: RemoteScores,
        month: Int
    ): Pair<List<ScoredZone>, WeatherData?> {
        val scored = zones.map { zone ->
            val weather = remote.toWeatherData(zone.id)
            ScoredZone(zone, weather?.let { ScoringEngine.score(it, zone, month) })
        }
        return scored to remote.regionalWeather()
    }

    private suspend fun scoreFromCentroid(
        zones: List<ForestZone>,
        month: Int
    ): Pair<List<ScoredZone>, WeatherData?> {
        val centerLat = zones.map { it.centroidLat }.average()
        val centerLon = zones.map { it.centroidLon }.average()
        val weather = runCatching {
            weatherRepository.getWeather(centerLat, centerLon)
        }.onFailure { Log.e("CepAlert", "Centroid weather failed: ${it.message}") }
         .getOrNull()
        val scored = zones.map { zone ->
            ScoredZone(zone, weather?.let { ScoringEngine.score(it, zone, month) })
        }
        return scored to weather
    }
}
