package com.magpiepodcast.data

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class MagpieApi(
    private val baseUrl: String = ApiConfig.API_BASE,
    private val tokenProvider: () -> String?
) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    fun fetchEpisodes(): EpisodesResponse {
        val token = tokenProvider()
            ?: throw IllegalStateException("Sem token de autenticação")

        val request = Request.Builder()
            .url("$baseUrl/episodes")
            .get()
            .header("Accept", "application/json")
            .header("Authorization", "Bearer $token")
            .header("Origin", ApiConfig.SITE_BASE)
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code}: ${body.take(200)}")
            }
            return parseEpisodes(body)
        }
    }

    internal fun parseEpisodes(json: String): EpisodesResponse {
        val root = JSONObject(json)
        val array = root.optJSONArray("episodes") ?: org.json.JSONArray()
        val list = mutableListOf<Episode>()
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            val tagsJson = item.optJSONArray("tags")
            val tags = mutableListOf<String>()
            if (tagsJson != null) {
                for (t in 0 until tagsJson.length()) {
                    tags += tagsJson.getJSONObject(t).optString("name")
                }
            }
            list += Episode(
                id = item.getInt("id"),
                title = item.optString("title"),
                description = item.optString("description"),
                date = item.optString("date"),
                duration = item.optString("duration"),
                audioUrl = item.optString("audioUrl"),
                image = item.optString("image").takeIf { it.isNotBlank() && it != "null" },
                tags = tags
            )
        }
        return EpisodesResponse(
            episodes = list,
            hasActiveSubscription = root.optBoolean("hasActiveSubscription", false)
        )
    }
}
