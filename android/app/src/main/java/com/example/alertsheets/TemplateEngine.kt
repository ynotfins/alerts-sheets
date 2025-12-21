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
        // Comprehensive JSON escape including emojis and special characters
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
                .replace("\b", "\\b")
                .replace("\u000C", "\\f")
    }

    /**
     * Removes emojis and non-standard characters. Keeps ASCII, common punctuation, and basic Latin
     * sets.
     */
    fun cleanText(input: String): String {
        // Remove emojis, control chars, but keep standard punctuation and newlines
        val cleaned = input.replace(Regex("[\uD800-\uDFFF]"), "") // Remove surrogates (emojis)
                .replace(Regex("[\\p{So}\\p{Sk}\\p{Sm}\\p{Sc}&&[^\\p{Ascii}]]"), "") // Remove non-ASCII symbols
                .replace(Regex("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]"), "") // Remove control chars except \n, \r, \t
        return cleaned.trim()
    }

    fun getTime(): String {
        val sdf = SimpleDateFormat("MM/dd/yyyy HH:mm:ss", Locale.US)
        return sdf.format(Date())
    }

    fun getTimestamp(): String {
        val sdf = SimpleDateFormat("MM/dd/yyyy hh:mm:ss a", Locale.US)
        return sdf.format(Date())
    }
}
