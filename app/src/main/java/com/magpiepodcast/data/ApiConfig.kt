package com.magpiepodcast.data

object ApiConfig {
    const val API_BASE = "https://api.magpiepodcast.com.br"
    const val SITE_BASE = "https://membros.magpiepodcast.com.br"
    const val LOGIN_URL = "$SITE_BASE/login"
    const val MEDIA_BASE = API_BASE

    fun mediaUrl(path: String?): String? {
        if (path.isNullOrBlank()) return null
        return if (path.startsWith("http")) path else MEDIA_BASE + path
    }
}
