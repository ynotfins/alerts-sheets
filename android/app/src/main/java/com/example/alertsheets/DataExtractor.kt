package com.example.alertsheets

import android.app.Notification
import android.content.Context
import android.os.Bundle
import android.service.notification.StatusBarNotification

object DataExtractor {

    fun extract(context: Context, sbn: StatusBarNotification, config: AppConfig): Map<String, Any> {
        val data = mutableMapOf<String, Any>()
        val extras = sbn.notification.extras

        // Dynamic Mappings
        config.mappings.forEach { mapping ->
            val value = when (mapping.sourceField) {
                NotificationField.TITLE -> extras.getString(Notification.EXTRA_TITLE)
                NotificationField.TEXT -> extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
                NotificationField.BIG_TEXT -> extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
                NotificationField.SUB_TEXT -> extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
                NotificationField.INFO_TEXT -> extras.getCharSequence(Notification.EXTRA_INFO_TEXT)?.toString()
                NotificationField.TICKER -> sbn.notification.tickerText?.toString()
                NotificationField.TIMESTAMP -> sbn.postTime
                NotificationField.PACKAGE_NAME -> sbn.packageName
            }
            if (value != null) {
                data[mapping.targetKey] = value
            }
        }

        // Static Fields
        config.staticFields.forEach { staticField ->
            data[staticField.key] = staticField.value
        }

        return data
    }
}
