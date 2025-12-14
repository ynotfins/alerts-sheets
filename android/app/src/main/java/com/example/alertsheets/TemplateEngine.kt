package com.example.alertsheets

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TemplateEngine {

    fun applyGeneric(template: String, pkg: String, title: String, text: String, bigText: String): String {
        return template
            .replace("{{package}}", escape(pkg))
            .replace("{{title}}", escape(title))
            .replace("{{text}}", escape(text))
            .replace("{{bigText}}", escape(bigText))
            .replace("{{time}}", escape(getTime()))
            .replace("{{sender}}", escape(title)) // SMS compat
            .replace("{{message}}", escape(text)) // SMS compat
    }

    fun applyBnn(template: String, data: ParsedData): String {
        val codesJson = if (data.fdCodes.isEmpty()) "[]" else {
            "[\"" + data.fdCodes.joinToString("\", \"") + "\"]"
        }
        
        return template
            .replace("{{id}}", escape(data.incidentId))
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
    }
    
    private fun escape(s: String): String {
        // Basic JSON escape
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
    }
    
    private fun getTime(): String {
        val sdf = SimpleDateFormat("MM/dd/yyyy HH:mm:ss", Locale.US)
        return sdf.format(Date())
    }
}
