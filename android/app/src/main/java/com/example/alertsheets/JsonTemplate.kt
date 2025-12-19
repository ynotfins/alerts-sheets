package com.example.alertsheets

data class JsonTemplate(
    val name: String,
    val content: String,
    val isRockSolid: Boolean = false, // True for hardcoded templates
    val mode: TemplateMode = TemplateMode.APP
) {
    override fun toString(): String = name
}

enum class TemplateMode {
    APP,
    SMS
}

