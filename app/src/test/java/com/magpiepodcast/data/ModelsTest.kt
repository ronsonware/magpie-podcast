package com.magpiepodcast.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelsTest {

    private fun progress(positionMs: Long, durationMs: Long) = PlaybackProgress(
        episodeId = 1,
        title = "Ep",
        image = null,
        audioUrl = "/a.mp3",
        durationLabel = "10:00",
        positionMs = positionMs,
        durationMs = durationMs,
        updatedAt = 0
    )

    @Test
    fun progressFraction_isZeroWithoutDuration() {
        assertEquals(0f, progress(10_000, 0).progressFraction)
    }

    @Test
    fun progressFraction_isRatioClampedToOne() {
        assertEquals(0.5f, progress(30_000, 60_000).progressFraction)
        assertEquals(1f, progress(90_000, 60_000).progressFraction)
    }

    @Test
    fun isFinished_withinLastFiveSeconds() {
        assertTrue(progress(55_000, 60_000).isFinished)
        assertFalse(progress(54_999, 60_000).isFinished)
    }

    @Test
    fun isFinished_falseWithoutDuration() {
        assertFalse(progress(10_000, 0).isFinished)
    }

    @Test
    fun mediaUrl_prefixesRelativePaths() {
        assertEquals(
            "https://api.magpiepodcast.com.br/uploads/a.mp3",
            ApiConfig.mediaUrl("/uploads/a.mp3")
        )
    }

    @Test
    fun mediaUrl_keepsAbsoluteUrlsAndRejectsBlank() {
        assertEquals("https://cdn/x.jpg", ApiConfig.mediaUrl("https://cdn/x.jpg"))
        assertNull(ApiConfig.mediaUrl(null))
        assertNull(ApiConfig.mediaUrl("  "))
    }
}
