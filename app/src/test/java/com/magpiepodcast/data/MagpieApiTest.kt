package com.magpiepodcast.data

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class MagpieApiTest {

    private lateinit var server: MockWebServer

    private val sampleJson = """
        {
          "episodes": [
            {
              "id": 7,
              "title": "Episódio 7",
              "description": "Desc",
              "date": "2025-09-29T00:00:00.000Z",
              "duration": "122:34",
              "audioUrl": "/uploads/audio/7.mp3",
              "image": "/uploads/img/7.jpg",
              "tags": [{"id": 1, "name": "MAGPIE PODCAST"}, {"id": 2, "name": "Fulano"}]
            },
            {
              "id": 8,
              "title": "Episódio 8",
              "audioUrl": "/uploads/audio/8.mp3",
              "image": null
            }
          ],
          "hasActiveSubscription": true
        }
    """.trimIndent()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun api(token: String? = "tok") =
        MagpieApi(baseUrl = server.url("").toString().trimEnd('/'), tokenProvider = { token })

    @Test
    fun fetchEpisodes_sendsAuthHeadersAndParsesResponse() {
        server.enqueue(MockResponse().setBody(sampleJson))

        val result = api().fetchEpisodes()

        val request = server.takeRequest()
        assertEquals("/episodes", request.path)
        assertEquals("Bearer tok", request.getHeader("Authorization"))
        assertEquals(ApiConfig.SITE_BASE, request.getHeader("Origin"))

        assertTrue(result.hasActiveSubscription)
        assertEquals(2, result.episodes.size)
        val first = result.episodes[0]
        assertEquals(7, first.id)
        assertEquals("122:34", first.duration)
        assertEquals("/uploads/img/7.jpg", first.image)
        assertEquals(listOf("MAGPIE PODCAST", "Fulano"), first.tags)
        val second = result.episodes[1]
        assertNull(second.image)
        assertTrue(second.tags.isEmpty())
    }

    @Test
    fun fetchEpisodes_throwsOnHttpError() {
        server.enqueue(MockResponse().setResponseCode(401).setBody("unauthorized"))
        try {
            api().fetchEpisodes()
            fail("expected exception")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.startsWith("HTTP 401"))
        }
    }

    @Test(expected = IllegalStateException::class)
    fun fetchEpisodes_requiresToken() {
        api(token = null).fetchEpisodes()
    }

    @Test
    fun parseEpisodes_handlesMissingArrayAndFlag() {
        val result = api().parseEpisodes("{}")
        assertTrue(result.episodes.isEmpty())
        assertFalse(result.hasActiveSubscription)
    }
}
