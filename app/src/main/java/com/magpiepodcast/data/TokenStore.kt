package com.magpiepodcast.data

import android.content.Context
import android.content.SharedPreferences

class TokenStore(private val prefs: SharedPreferences) {

    constructor(context: Context) :
        this(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE))

    var token: String?
        get() = prefs.getString(KEY_TOKEN, null)
        set(value) {
            prefs.edit().apply {
                if (value.isNullOrBlank()) remove(KEY_TOKEN) else putString(KEY_TOKEN, value)
                apply()
            }
        }

    fun isLoggedIn(): Boolean = !token.isNullOrBlank()

    fun clear() {
        token = null
    }

    companion object {
        private const val PREFS = "magpie_auth"
        private const val KEY_TOKEN = "token"
    }
}
