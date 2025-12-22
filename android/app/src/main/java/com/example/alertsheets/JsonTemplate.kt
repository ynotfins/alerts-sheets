package com.example.alertsheets

import com.example.alertsheets.utils.PayloadSerializer

/**
 * JSON template for payload generation
 * 
 * @property schemaVersion For migration support - increment when structure changes
 */
data class JsonTemplate(
    val name: String,
    val content: String,
    val isRockSolid: Boolean = false, // True for hardcoded templates
    val mode: TemplateMode = TemplateMode.APP,
    val schemaVersion: Int = PayloadSerializer.SchemaVersion.TEMPLATES
) {
    override fun toString(): String = name
}

enum class TemplateMode {
    APP,
    SMS
}

