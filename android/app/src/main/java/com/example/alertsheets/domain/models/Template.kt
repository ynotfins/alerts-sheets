package com.example.alertsheets.domain.models

/**
 * JSON template for formatting notification data
 * 
 * Templates can be:
 * - Rock Solid (immutable system templates)
 * - Custom (user-created, editable)
 * - Global (sourceId = null, available for all sources)
 * - Source-specific (sourceId = "com.example.bnn")
 */
data class Template(
    val id: String,                          // UUID
    val name: String,                        // Display name (e.g., "Rock Solid BNN Default")
    val sourceId: String? = null,            // null = available for all sources
    val content: String,                     // JSON with {{variables}}
    val isRockSolid: Boolean = false,        // If true, cannot be edited/deleted
    val variables: List<String> = emptyList(), // Extracted variables (e.g., ["{{package}}", "{{title}}"])
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    /**
     * Check if this template is available for a given source
     */
    fun isAvailableFor(source: Source): Boolean {
        return sourceId == null || sourceId == source.id
    }
    
    /**
     * Extract variables from content ({{var}} format)
     */
    fun extractVariables(): List<String> {
        val regex = Regex("""\{\{([^}]+)\}\}""")
        return regex.findAll(content).map { "{{${it.groupValues[1]}}}" }.distinct().toList()
    }
}

/**
 * Rock Solid template IDs (hardcoded, immutable)
 */
object RockSolidTemplates {
    const val APP_DEFAULT = "rock-solid-app-default"
    const val BNN_FORMAT = "rock-solid-bnn-format"
    const val SMS_DEFAULT = "rock-solid-sms-default"
    
    /**
     * Get all Rock Solid templates
     */
    fun getAll(): List<Template> {
        return listOf(
            Template(
                id = APP_DEFAULT,
                name = "🪨 Rock Solid App Default",
                sourceId = null,
                content = """
                    {
                      "type": "notification",
                      "package": "{{package}}",
                      "title": "{{title}}",
                      "text": "{{text}}",
                      "bigText": "{{bigText}}",
                      "time": "{{time}}",
                      "timestamp": "{{timestamp}}"
                    }
                """.trimIndent(),
                isRockSolid = true,
                variables = listOf("{{package}}", "{{title}}", "{{text}}", "{{bigText}}", "{{time}}", "{{timestamp}}")
            ),
            Template(
                id = BNN_FORMAT,
                name = "🪨 Rock Solid BNN Format",
                sourceId = "com.example.bnn",
                content = """
                    {
                      "incidentId": "{{incidentId}}",
                      "state": "{{state}}",
                      "county": "{{county}}",
                      "city": "{{city}}",
                      "address": "{{address}}",
                      "incidentType": "{{incidentType}}",
                      "incidentDetails": "{{incidentDetails}}",
                      "fdCodes": [{{fdCodes}}],
                      "timestamp": "{{timestamp}}",
                      "originalBody": "{{originalBody}}"
                    }
                """.trimIndent(),
                isRockSolid = true,
                variables = listOf("{{incidentId}}", "{{state}}", "{{county}}", "{{city}}", "{{address}}", 
                    "{{incidentType}}", "{{incidentDetails}}", "{{fdCodes}}", "{{timestamp}}", "{{originalBody}}")
            ),
            Template(
                id = SMS_DEFAULT,
                name = "🪨 Rock Solid SMS Default",
                sourceId = null,
                content = """
                    {
                      "source": "sms",
                      "sender": "{{sender}}",
                      "message": "{{message}}",
                      "time": "{{time}}",
                      "timestamp": "{{timestamp}}"
                    }
                """.trimIndent(),
                isRockSolid = true,
                variables = listOf("{{sender}}", "{{message}}", "{{time}}", "{{timestamp}}")
            )
        )
    }
}

