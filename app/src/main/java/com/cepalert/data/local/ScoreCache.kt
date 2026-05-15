package com.cepalert.data.local

import android.content.Context
import com.cepalert.data.model.ScoreResult
import com.cepalert.data.model.WeatherData
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class CachedScores(
    val savedAtEpochMs: Long,
    val weather: WeatherData,
    val scores: List<ScoreResult>
)

@Singleton
class ScoreCache @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TTL_MS = 6 * 60 * 60 * 1000L
        fun isFresh(savedAt: Long, now: Long): Boolean = now - savedAt < TTL_MS
    }

    private val file: File get() = File(context.cacheDir, "scores.json")
    private val json = Json { ignoreUnknownKeys = true }

    fun read(now: Long): CachedScores? {
        if (!file.exists()) return null
        return runCatching {
            json.decodeFromString<CachedScores>(file.readText())
        }.getOrNull()?.takeIf { isFresh(it.savedAtEpochMs, now) }
    }

    fun write(cached: CachedScores) {
        runCatching { file.writeText(json.encodeToString(cached)) }
    }
}
