package com.example.alertsheets

import java.util.UUID

data class Endpoint(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    var url: String,
    var isEnabled: Boolean = true
)
