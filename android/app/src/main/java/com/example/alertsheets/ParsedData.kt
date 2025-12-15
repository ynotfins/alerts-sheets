package com.example.alertsheets

data class ParsedData(
    val status: String,
    val timestamp: String,
    val incidentId: String,
    val state: String,
    val county: String,
    val city: String,
    val address: String,
    val incidentType: String,
    val incidentDetails: String,
    var originalBody: String,
    val fdCodes: List<String>
)
