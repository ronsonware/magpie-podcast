package com.magpiepodcast.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenStoreTest {

    private val store = TokenStore(FakeSharedPreferences())

    @Test
    fun loggedOutByDefault() {
        assertNull(store.token)
        assertFalse(store.isLoggedIn())
    }

    @Test
    fun storesToken() {
        store.token = "abc"
        assertEquals("abc", store.token)
        assertTrue(store.isLoggedIn())
    }

    @Test
    fun blankTokenRemovesIt() {
        store.token = "abc"
        store.token = "   "
        assertNull(store.token)
    }

    @Test
    fun clearLogsOut() {
        store.token = "abc"
        store.clear()
        assertFalse(store.isLoggedIn())
    }
}
