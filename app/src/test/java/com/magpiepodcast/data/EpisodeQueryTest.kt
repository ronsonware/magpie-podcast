package com.magpiepodcast.data

import org.junit.Assert.assertEquals
import org.junit.Test

class EpisodeQueryTest {

    private fun episode(
        id: Int,
        title: String = "Ep $id",
        date: String = "2025-01-0${id}T00:00:00.000Z",
        duration: String = "10:00",
        tags: List<String> = emptyList()
    ) = Episode(id, title, "", date, duration, "/a$id.mp3", null, tags)

    private fun progress(id: Int, positionMs: Long, durationMs: Long = 600_000, updatedAt: Long = 0) =
        PlaybackProgress(id, "Ep $id", null, "/a$id.mp3", "10:00", positionMs, durationMs, updatedAt)

    private val episodes = listOf(
        episode(1, title = "Bravo", duration = "30:00", tags = listOf("MAGPIE PODCAST", "Fulano")),
        episode(2, title = "alfa", duration = "1:10:00", tags = listOf("CELIBACAST", "Fulano", "Beltrano")),
        episode(3, title = "Charlie", duration = "5:00", tags = listOf("MAGPIE PODCAST", "Beltrano")),
        episode(4, title = "Delta", duration = "20:00", tags = listOf("Novo Show", "Ciclano"))
    )

    private val progressMap = mapOf(
        1 to progress(1, 300_000, updatedAt = 10),
        2 to progress(2, 598_000, updatedAt = 30),
        3 to progress(3, 0, updatedAt = 20)
    )

    private fun query(
        programs: Set<String> = emptySet(),
        guests: Set<String> = emptySet(),
        status: ListenStatus = ListenStatus.ALL,
        sort: EpisodeSort = EpisodeSort.DATE_NEWEST
    ) = EpisodeQuery.filterAndSort(episodes, programs, guests, status, sort) { progressMap[it] }
        .map { it.id }

    @Test
    fun programTags_detectsKnownAndHeuristicProgramsSortedByCount() {
        assertEquals(
            listOf("MAGPIE PODCAST", "CELIBACAST", "Novo Show"),
            EpisodeQuery.programTags(episodes)
        )
    }

    @Test
    fun guestTags_excludesProgramsAndRespectsSelectedPrograms() {
        assertEquals(
            listOf("Beltrano", "Fulano", "Ciclano"),
            EpisodeQuery.guestTags(episodes, emptySet())
        )
        assertEquals(
            listOf("Beltrano", "Fulano"),
            EpisodeQuery.guestTags(episodes, setOf("MAGPIE PODCAST"))
        )
    }

    @Test
    fun guestTags_heuristicCoversRadioBanhadoBizuTalks() {
        val eps = listOf(
            episode(1, tags = listOf("Rádio X", "Radio Y", "O Banhado", "Bizu Z", "Zé Talks Extra", "Pessoa"))
        )
        assertEquals(listOf("Pessoa"), EpisodeQuery.guestTags(eps, emptySet()))
    }

    @Test
    fun progressPercent_coversAllBranches() {
        assertEquals(0, EpisodeQuery.progressPercent(null, "10:00"))
        assertEquals(0, EpisodeQuery.progressPercent(progress(1, 0), "10:00"))
        assertEquals(100, EpisodeQuery.progressPercent(progress(1, 598_000), "10:00"))
        assertEquals(50, EpisodeQuery.progressPercent(progress(1, 300_000), "10:00"))
        assertEquals(25, EpisodeQuery.progressPercent(progress(1, 150_000, durationMs = 0), "10:00"))
        assertEquals(0, EpisodeQuery.progressPercent(progress(1, 150_000, durationMs = 0), ""))
    }

    @Test
    fun filter_programsAreOr() {
        assertEquals(listOf(3, 2, 1), query(programs = setOf("MAGPIE PODCAST", "CELIBACAST")))
    }

    @Test
    fun filter_guestsAreAnd() {
        assertEquals(listOf(2), query(guests = setOf("Fulano", "Beltrano")))
    }

    @Test
    fun filter_programsAndGuestsCombine() {
        assertEquals(listOf(1), query(programs = setOf("MAGPIE PODCAST"), guests = setOf("Fulano")))
    }

    @Test
    fun filter_byListenStatus() {
        assertEquals(listOf(4, 3), query(status = ListenStatus.UNPLAYED))
        assertEquals(listOf(1), query(status = ListenStatus.IN_PROGRESS))
        assertEquals(listOf(2), query(status = ListenStatus.COMPLETED))
    }

    @Test
    fun sort_allOptions() {
        assertEquals(listOf(4, 3, 2, 1), query(sort = EpisodeSort.DATE_NEWEST))
        assertEquals(listOf(1, 2, 3, 4), query(sort = EpisodeSort.DATE_OLDEST))
        assertEquals(listOf(2, 1, 3, 4), query(sort = EpisodeSort.TITLE_AZ))
        assertEquals(listOf(4, 3, 1, 2), query(sort = EpisodeSort.TITLE_ZA))
        assertEquals(listOf(2, 1, 3, 4), query(sort = EpisodeSort.PROGRESS_HIGH))
        assertEquals(listOf(3, 4, 1, 2), query(sort = EpisodeSort.PROGRESS_LOW))
        assertEquals(listOf(2, 1, 4, 3), query(sort = EpisodeSort.DURATION_LONG))
        assertEquals(listOf(3, 4, 1, 2), query(sort = EpisodeSort.DURATION_SHORT))
        assertEquals(listOf(2, 3, 1, 4), query(sort = EpisodeSort.RECENTLY_PLAYED))
    }
}
