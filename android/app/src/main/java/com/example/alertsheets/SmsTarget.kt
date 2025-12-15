package com.example.alertsheets

import java.util.UUID

data class SmsTarget(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "",
    var phoneNumber: String = "",
    var filterText: String = "",
    var isCaseSensitive: Boolean = false,
    var isEnabled: Boolean = true
)
