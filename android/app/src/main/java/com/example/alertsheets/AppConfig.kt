package com.example.alertsheets

data class AppConfig(
    val packageName: String,
    val mappings: MutableList<FieldMapping> = mutableListOf(),
    val staticFields: MutableList<StaticField> = mutableListOf()
)

data class FieldMapping(
    val sourceField: NotificationField,
    val targetKey: String
)

data class StaticField(
    val key: String,
    val value: String
)

enum class NotificationField(val displayName: String) {
    TITLE("Title"),
    TEXT("Text"),
    BIG_TEXT("Big Text"),
    SUB_TEXT("Sub Text"),
    INFO_TEXT("Info Text"),
    TICKER("Ticker Text"),
    TIMESTAMP("Time"),
    PACKAGE_NAME("Package Name")
}
