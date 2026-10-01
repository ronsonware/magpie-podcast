package com.magpiepodcast.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

class ProgressRepository(private val prefs: SharedPreferences) {

    constructor(context: Context) :
        this(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE))

    fun save(progress: PlaybackProgress) {
        if (progress.positionMs < 3_000L && !progress.isFinished) {
            val existing = get(progress.episodeId)
            if (existing == null) return
        }

        val map = loadAllMutable()
        map[progress.episodeId] = progress
        persist(map)
    }

    fun get(episodeId: Int): PlaybackProgress? = loadAllMutable()[episodeId]

    fun allSorted(): List<PlaybackProgress> =
        loadAllMutable().values.sortedByDescending { it.updatedAt }

    fun clear(episodeId: Int) {
        val map = loadAllMutable()
        map.remove(episodeId)
        persist(map)
    }

    private fun loadAllMutable(): MutableMap<Int, PlaybackProgress> {
        val raw = prefs.getString(KEY_HISTORY, "[]") ?: "[]"
        val array = JSONArray(raw)
        val map = mutableMapOf<Int, PlaybackProgress>()
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            val p = PlaybackProgress(
                episodeId = o.getInt("episodeId"),
                title = o.optString("title"),
                image = o.optString("image").takeIf { it.isNotBlank() && it != "null" },
                audioUrl = o.optString("audioUrl"),
                durationLabel = o.optString("durationLabel"),
                positionMs = o.optLong("positionMs"),
                durationMs = o.optLong("durationMs"),
                updatedAt = o.optLong("updatedAt")
            )
            map[p.episodeId] = p
        }
        return map
    }

    private fun persist(map: Map<Int, PlaybackProgress>) {
        val array = JSONArray()
        map.values.forEach { p ->
            array.put(
                JSONObject()
                    .put("episodeId", p.episodeId)
                    .put("title", p.title)
                    .put("image", p.image)
                    .put("audioUrl", p.audioUrl)
                    .put("durationLabel", p.durationLabel)
                    .put("positionMs", p.positionMs)
                    .put("durationMs", p.durationMs)
                    .put("updatedAt", p.updatedAt)
            )
        }
        prefs.edit().putString(KEY_HISTORY, array.toString()).commit()
    }

    companion object {
        private const val PREFS = "magpie_progress"
        private const val KEY_HISTORY = "history"
    }
}
