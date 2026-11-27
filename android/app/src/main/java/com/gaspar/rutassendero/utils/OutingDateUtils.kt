package com.gaspar.rutassendero.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object OutingDateUtils {
    private val sdfDisplay = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    private val sdfDisplayShort = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
    private val sdfDateOnly = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val sdfIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)

    /**
     * Parsea una fecha en diversos formatos a milisegundos de época.
     */
    fun parseToMillis(dateStr: String?): Long? {
        if (dateStr.isNullOrBlank()) return null
        val trimmed = dateStr.trim()

        // 1. Timestamp en milisegundos como string
        trimmed.toLongOrNull()?.let { return it }

        // 2. Formato estándar: yyyy-MM-dd HH:mm:ss
        try {
            val d = sdfDisplay.parse(trimmed)
            if (d != null) return d.time
        } catch (_: Exception) {}

        // 3. Formato corto: yyyy-MM-dd HH:mm
        try {
            val d = sdfDisplayShort.parse(trimmed)
            if (d != null) return d.time
        } catch (_: Exception) {}

        // 4. Formato ISO 8601 con zona horaria o sufijo Z
        try {
            val cleaned = trimmed.replace("Z", "+00:00")
            val instant = java.time.OffsetDateTime.parse(cleaned)
            return Date.from(instant.toInstant()).time
        } catch (_: Exception) {}

        // 5. Formato ISO simple: yyyy-MM-dd'T'HH:mm:ss
        try {
            val d = sdfIso.parse(trimmed)
            if (d != null) return d.time
        } catch (_: Exception) {}

        // 6. Solo fecha: yyyy-MM-dd (se extiende a las 23:59:59 del mismo día)
        try {
            val d = sdfDateOnly.parse(trimmed)
            if (d != null) {
                return d.time + (24L * 60L * 60L * 1000L - 1000L)
            }
        } catch (_: Exception) {}

        return null
    }

    /**
     * Determina si la fecha de la salida es futura o está en curso.
     * @param graceHours Horas de gracia después de la hora de salida (por defecto 2 horas)
     */
    fun isFutureOrCurrent(dateStr: String?, graceHours: Long = 2L): Boolean {
        val millis = parseToMillis(dateStr) ?: return false
        val now = System.currentTimeMillis()
        val limit = now - (graceHours * 3600_000L)
        return millis >= limit
    }
}
