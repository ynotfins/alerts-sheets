package com.example.alertsheets

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TemplateEngine {

    fun applyGeneric(
            template: String,
            pkg: String,
            title: String,
            text: String,
            bigText: String
    ): String {
        return template.replace("{{package}}", escape(pkg))
                .replace("{{title}}", escape(title))
                .replace("{{text}}", escape(text))
                .replace("{{bigText}}", escape(bigText))
                .replace("{{time}}", escape(getTime()))
                .replace("{{timestamp}}", escape(getTimestamp()))
                .replace("{{sender}}", escape(title)) // SMS compat
                .replace("{{message}}", escape(text)) // SMS compat
    }

    fun applyBnn(template: String, data: ParsedData): String {
        val codesJson =
                if (data.fdCodes.isEmpty()) "[]"
                else {
                    "[\"" + data.fdCodes.joinToString("\", \"") + "\"]"
                }

        return template.replace("{{id}}", escape(data.incidentId))
                .replace("{{status}}", escape(data.status))
                .replace("{{state}}", escape(data.state))
                .replace("{{county}}", escape(data.county))
                .replace("{{city}}", escape(data.city))
                .replace("{{address}}", escape(data.address))
                .replace("{{type}}", escape(data.incidentType))
                .replace("{{details}}", escape(data.incidentDetails))
                .replace("{{original}}", escape(data.originalBody))
                .replace("{{codes}}", codesJson) // Raw replacement for array, no quotes
                .replace("{{time}}", escape(getTime()))
                .replace("{{timestamp}}", escape(getTimestamp()))
    }

    private fun escape(s: String): String {
        // Basic JSON escape
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
    }

    /**
     * Removes emojis and non-standard characters. Keeps ASCII, common punctuation, and basic Latin
     * sets.
     */
    fun cleanText(input: String): String {
        val cleaned = input.replace(Regex("[^\\p{L}\\p{N}\\p{P}\\p{Z}]"), "")
        return cleaned.trim()
    }

    fun getTime(): String {
        val sdf = SimpleDateFormat("MM/dd/yyyy HH:mm:ss", Locale.US)
        return sdf.format(Date())
    }

    fun getTimestamp(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }
}
