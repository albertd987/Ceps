package com.cepalert.data.repository

import com.cepalert.data.model.RemoteScores
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RemoteScoresRepository @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    companion object {
        const val SCORES_URL = "https://albertd987.github.io/Ceps/scores.json"
    }

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getScores(): RemoteScores = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(SCORES_URL).build()
        val body = okHttpClient.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "HTTP ${response.code}" }
            response.body?.string() ?: error("Empty response")
        }
        json.decodeFromString<RemoteScores>(body)
    }
}
