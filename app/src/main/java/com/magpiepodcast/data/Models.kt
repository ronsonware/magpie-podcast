package com.magpiepodcast.data

data class Episode(
    val id: Int,
    val title: String,
    val description: String,
    val date: String,
    val duration: String,
    val audioUrl: String,
    val image: String?,
    val tags: List<String> = emptyList()
)

data class EpisodesResponse(
    val episodes: List<Episode>,
    val hasActiveSubscription: Boolean
)

data class PlaybackProgress(
    val episodeId: Int,
    val title: String,
    val image: String?,
    val audioUrl: String,
    val durationLabel: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long
) {
    val progressFraction: Float
        get() = if (durationMs <= 0L) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)

    val isFinished: Boolean
        get() = durationMs > 0 && positionMs >= durationMs - 5_000
}
