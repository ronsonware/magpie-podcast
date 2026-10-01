package com.magpiepodcast.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressRepositoryTest {

    private val prefs = FakeSharedPreferences()
    private val repo = ProgressRepository(prefs)

    private fun progress(id: Int, positionMs: Long, updatedAt: Long = 0, image: String? = "/img.jpg") =
        PlaybackProgress(id, "Ep $id", image, "/a$id.mp3", "10:00", positionMs, 600_000, updatedAt)

    @Test
    fun emptyByDefault() {
        assertNull(repo.get(1))
        assertTrue(repo.allSorted().isEmpty())
    }

    @Test
    fun saveAndGetRoundTripsAllFields() {
        val p = progress(1, 120_000, updatedAt = 42)
        repo.save(p)
        assertEquals(p, repo.get(1))
    }

    @Test
    fun savePersistsSynchronouslyWithCommit() {
        repo.save(progress(1, 120_000))
        assertEquals(1, prefs.commitCount)
        assertEquals(120_000L, ProgressRepository(prefs).get(1)?.positionMs)
    }

    @Test
    fun ignoresTinyPositionForNewEpisode() {
        repo.save(progress(1, 2_000))
        assertNull(repo.get(1))
    }

    @Test
    fun allowsTinyPositionWhenHistoryExists() {
        repo.save(progress(1, 120_000))
        repo.save(progress(1, 1_000))
        assertEquals(1_000L, repo.get(1)?.positionMs)
    }

    @Test
    fun keepsFinishedEvenIfPositionLooksTiny() {
        val finished = PlaybackProgress(1, "Ep", null, "/a.mp3", "0:02", 2_000, 2_000, 0)
        repo.save(finished)
        assertTrue(repo.get(1)!!.isFinished)
    }

    @Test
    fun nullImageIsRestoredAsNull() {
        repo.save(progress(1, 120_000, image = null))
        assertNull(repo.get(1)?.image)
    }

    @Test
    fun allSortedOrdersByMostRecent() {
        repo.save(progress(1, 120_000, updatedAt = 1))
        repo.save(progress(2, 120_000, updatedAt = 3))
        repo.save(progress(3, 120_000, updatedAt = 2))
        assertEquals(listOf(2, 3, 1), repo.allSorted().map { it.episodeId })
    }

    @Test
    fun clearRemovesOnlyThatEpisode() {
        repo.save(progress(1, 120_000))
        repo.save(progress(2, 120_000))
        repo.clear(1)
        assertNull(repo.get(1))
        assertFalse(repo.allSorted().isEmpty())
    }
}
