package com.magpiepodcast.data

enum class ListenStatus {
    ALL,
    UNPLAYED,
    IN_PROGRESS,
    COMPLETED
}

enum class EpisodeSort {
    DATE_NEWEST,
    DATE_OLDEST,
    TITLE_AZ,
    TITLE_ZA,
    PROGRESS_HIGH,
    PROGRESS_LOW,
    DURATION_LONG,
    DURATION_SHORT,
    RECENTLY_PLAYED
}

object EpisodeQuery {
    private val PROGRAM_TAGS = setOf(
        "MAGPIE PODCAST",
        "CICLISTACAST",
        "CELIBACAST",
        "RÁDIO SOLITÁRIA/DISSIDÊNCIA",
        "DIRETO DO BANHADO",
        "BIZU DO RATÃO",
        "Brutalks",
        "NA CAMA COM EPI",
        "BAIXO Q.I",
        "ERNANE SHOW",
        "ZÉ TALKS"
    )

    fun programTags(episodes: List<Episode>): List<String> {
        val counts = mutableMapOf<String, Int>()
        episodes.forEach { ep ->
            ep.tags.forEach { tag ->
                if (isProgramTag(tag)) {
                    counts[tag] = (counts[tag] ?: 0) + 1
                }
            }
        }
        return counts.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { it.key }
    }

    fun guestTags(episodes: List<Episode>, selectedPrograms: Set<String>): List<String> {
        val scoped = if (selectedPrograms.isEmpty()) {
            episodes
        } else {
            episodes.filter { ep -> ep.tags.any { it in selectedPrograms } }
        }
        val counts = mutableMapOf<String, Int>()
        scoped.forEach { ep ->
            ep.tags.forEach { tag ->
                if (!isProgramTag(tag)) {
                    counts[tag] = (counts[tag] ?: 0) + 1
                }
            }
        }
        return counts.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { it.key }
    }

    private fun isProgramTag(tag: String): Boolean {
        if (tag in PROGRAM_TAGS) return true
        val upper = tag.uppercase()
        return upper.contains("CAST") ||
            upper.contains("PODCAST") ||
            upper.contains("SHOW") ||
            upper.contains("TALKS") ||
            upper.contains("RÁDIO") ||
            upper.contains("RADIO") ||
            upper.contains("BANHADO") ||
            upper.contains("BIZU")
    }

    fun progressPercent(progress: PlaybackProgress?, durationLabel: String): Int {
        if (progress == null || progress.positionMs <= 0L) return 0
        if (progress.isFinished) return 100
        val durationMs = when {
            progress.durationMs > 0 -> progress.durationMs
            else -> Formatters.parseDurationLabelToMs(durationLabel)
        }
        if (durationMs <= 0L) return 0
        return ((progress.positionMs * 100L) / durationMs).toInt().coerceIn(0, 99)
    }

    fun filterAndSort(
        episodes: List<Episode>,
        selectedPrograms: Set<String>,
        selectedGuests: Set<String>,
        status: ListenStatus,
        sort: EpisodeSort,
        progressOf: (Int) -> PlaybackProgress?
    ): List<Episode> {
        var list = episodes.asSequence()

        if (selectedPrograms.isNotEmpty()) {
            list = list.filter { ep -> ep.tags.any { it in selectedPrograms } }
        }
        if (selectedGuests.isNotEmpty()) {
            list = list.filter { ep -> selectedGuests.all { g -> g in ep.tags } }
        }

        list = when (status) {
            ListenStatus.ALL -> list
            ListenStatus.UNPLAYED -> list.filter {
                val p = progressOf(it.id)
                p == null || p.positionMs <= 0L
            }
            ListenStatus.IN_PROGRESS -> list.filter {
                val p = progressOf(it.id)
                p != null && p.positionMs > 0L && !p.isFinished
            }
            ListenStatus.COMPLETED -> list.filter {
                progressOf(it.id)?.isFinished == true
            }
        }

        val sorted = list.toList()
        return when (sort) {
            EpisodeSort.DATE_NEWEST -> sorted.sortedByDescending { it.date }
            EpisodeSort.DATE_OLDEST -> sorted.sortedBy { it.date }
            EpisodeSort.TITLE_AZ -> sorted.sortedBy { it.title.trim().lowercase() }
            EpisodeSort.TITLE_ZA -> sorted.sortedByDescending { it.title.trim().lowercase() }
            EpisodeSort.PROGRESS_HIGH -> sorted.sortedByDescending {
                progressPercent(progressOf(it.id), it.duration)
            }
            EpisodeSort.PROGRESS_LOW -> sorted.sortedBy {
                progressPercent(progressOf(it.id), it.duration)
            }
            EpisodeSort.DURATION_LONG -> sorted.sortedByDescending {
                Formatters.parseDurationLabelToMs(it.duration)
            }
            EpisodeSort.DURATION_SHORT -> sorted.sortedBy {
                Formatters.parseDurationLabelToMs(it.duration)
            }
            EpisodeSort.RECENTLY_PLAYED -> sorted.sortedByDescending {
                progressOf(it.id)?.updatedAt ?: 0L
            }
        }
    }
}
