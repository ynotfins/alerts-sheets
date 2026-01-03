package com.example.alertsheets.data.repositories

import android.content.Context
import com.example.alertsheets.utils.AppConstants

/**
 * Persists endpoint test results per source+endpoint so Lab status lights remain true across restarts.
 *
 * Stores compact strings in SharedPreferences (source_extras):
 * key: "<sourceId>:endpoint_test:<endpointId>"
 * val: "<timestampMs>|<httpCode>|<confirmed>|<configHash>"
 */
class SourceTestStatusRepository(private val context: Context) {

    data class EndpointTestStatus(
        val timestampMs: Long,
        val httpCode: Int,
        val confirmed: Boolean,
        val configHash: String
    )

    private fun prefs() = context.getSharedPreferences(AppConstants.PREFS_SOURCE_EXTRAS, Context.MODE_PRIVATE)

    fun record(
        sourceId: String,
        endpointId: String,
        configHash: String,
        timestampMs: Long,
        httpCode: Int,
        confirmed: Boolean
    ) {
        val key = key(sourceId, endpointId)
        val value = "${timestampMs}|${httpCode}|${if (confirmed) 1 else 0}|$configHash"
        prefs().edit().putString(key, value).apply()
    }

    fun get(sourceId: String, endpointId: String): EndpointTestStatus? {
        val raw = prefs().getString(key(sourceId, endpointId), null) ?: return null
        val parts = raw.split("|")
        if (parts.size < 4) return null
        val ts = parts[0].toLongOrNull() ?: return null
        val code = parts[1].toIntOrNull() ?: return null
        val confirmed = parts[2] == "1"
        val hash = parts[3]
        return EndpointTestStatus(ts, code, confirmed, hash)
    }

    fun hasAnyConfirmedForConfig(sourceId: String, endpointIds: List<String>, configHash: String): Boolean {
        return endpointIds.any { eid ->
            val s = get(sourceId, eid) ?: return@any false
            s.confirmed && s.configHash == configHash && s.httpCode in 200..299
        }
    }

    private fun key(sourceId: String, endpointId: String): String = "$sourceId:endpoint_test:$endpointId"
}


