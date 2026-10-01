package com.magpiepodcast.data

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

object Formatters {
    private val apiDate = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    private val displayDate = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))

    fun formatDate(iso: String): String {
        return try {
            val parsed = apiDate.parse(iso) ?: return iso.take(10)
            displayDate.format(parsed)
        } catch (_: Exception) {
            iso.take(10)
        }
    }

    fun formatMs(ms: Long): String {
        val totalSec = (ms / 1000).coerceAtLeast(0)
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) {
            String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        } else {
            String.format(Locale.US, "%d:%02d", m, s)
        }
    }

    fun parseDurationLabelToMs(label: String): Long {
        val parts = label.split(":").mapNotNull { it.toLongOrNull() }
        return when (parts.size) {
            3 -> (parts[0] * 3600 + parts[1] * 60 + parts[2]) * 1000
            2 -> (parts[0] * 60 + parts[1]) * 1000
            1 -> parts[0] * 1000
            else -> 0L
        }
    }
}
