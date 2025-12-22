package com.example.alertsheets

import com.example.alertsheets.utils.PayloadSerializer
import java.util.UUID

/**
 * SMS source/target configuration
 * 
 * @property schemaVersion For migration support - increment when structure changes
 */
data class SmsTarget(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "",
    var phoneNumber: String = "",
    var filterText: String = "",
    var isCaseSensitive: Boolean = false,
    var isEnabled: Boolean = true,
    val schemaVersion: Int = PayloadSerializer.SchemaVersion.SMS_TARGETS
)
