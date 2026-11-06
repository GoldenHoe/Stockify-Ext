package com.rsps1008.stockify.data

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Shanghai composite, Shenzhen component and ChiNext board index shown on the CN home screen. */
@Serializable
data class CnMarketIndexInfo(
    val code: String,
    val name: String,
    val current: Double,
    val change: Double,
    val changePercent: Double
)

/** Eastmoney returns index values and percentages in hundredths. */
internal const val CN_INDEX_VALUE_SCALE = 100.0

/** The three mainland benchmarks, in display order. */
internal val CN_MARKET_INDEX_CODES = listOf("000001", "399001", "399006")

internal const val CN_MARKET_INDEX_URL =
    "https://push2.eastmoney.com/api/qt/ulist.np/get" +
        "?secids=1.000001,0.399001,0.399006&fields=f12,f14,f2,f3,f4"

/**
 * Parses Eastmoney's index list. A suspended or unknown value arrives as the
 * literal string "-", so each field is scaled only after a successful parse.
 */
internal fun parseCnMarketIndexes(jsonText: String): List<CnMarketIndexInfo> {
    if (jsonText.isBlank()) return emptyList()
    val root = runCatching { Json.parseToJsonElement(jsonText) }.getOrNull() as? JsonObject
        ?: return emptyList()
    val diff = (root["data"] as? JsonObject)?.get("diff") ?: return emptyList()
    val rows: List<JsonObject> = when (diff) {
        is JsonArray -> diff.mapNotNull { it as? JsonObject }
        is JsonObject -> diff.values.mapNotNull { it as? JsonObject }
        else -> return emptyList()
    }
    val parsed = rows.mapNotNull { row ->
        val code = row.stringOrNull("f12")?.trim()?.takeIf { it.isNotEmpty() }
            ?: return@mapNotNull null
        val current = row.scaledOrNull("f2") ?: return@mapNotNull null
        CnMarketIndexInfo(
            code = code,
            name = row.stringOrNull("f14")?.trim()?.takeIf { it.isNotEmpty() } ?: code,
            current = current,
            change = row.scaledOrNull("f4") ?: 0.0,
            changePercent = row.scaledOrNull("f3") ?: 0.0
        )
    }
    if (parsed.isEmpty()) return emptyList()
    val byCode = parsed.associateBy { it.code }
    // Keep a stable 上證／深證／創業板 order even when the API reorders its rows.
    return CN_MARKET_INDEX_CODES.mapNotNull { byCode[it] }
}

private fun JsonObject.stringOrNull(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.scaledOrNull(key: String): Double? {
    val raw = (this[key] as? JsonPrimitive)?.contentOrNull?.trim() ?: return null
    return raw.replace(",", "").toDoubleOrNull()
        ?.let { value -> (value / CN_INDEX_VALUE_SCALE).takeIf { it.isFinite() } }
}

/** Fetches the three mainland benchmarks and exposes them like [TaiwanWeightedIndexService]. */
class CnMarketIndexService(
    private val client: HttpClient,
    private val settingsDataStore: SettingsDataStore
) {
    private companion object {
        const val TAG = "CnMarketIndexService"
    }

    private val _indexes = MutableStateFlow<List<CnMarketIndexInfo>>(emptyList())
    val indexes: StateFlow<List<CnMarketIndexInfo>> = _indexes.asStateFlow()
    private val scope = CoroutineScope(Dispatchers.IO)

    init {
        scope.launch {
            _indexes.value = settingsDataStore.cnMarketIndexCacheFlow.first()
        }
    }

    suspend fun refreshOnce() {
        val fetched = fetchIndexes()
        if (fetched.isNullOrEmpty()) {
            Log.w(TAG, "Failed to fetch CN market indexes; keeping the cached values")
            return
        }
        _indexes.value = fetched
        settingsDataStore.setCnMarketIndexCache(fetched)
    }

    private suspend fun fetchIndexes(): List<CnMarketIndexInfo>? = try {
        val body = retryOnTransientNetworkFailure(TAG, "CN_INDEX") {
            client.get(CN_MARKET_INDEX_URL) {
                headers.append("User-Agent", "Mozilla/5.0")
            }.bodyAsText()
        }
        body?.let { parseCnMarketIndexes(it) }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to fetch CN market indexes: ${e.message}", e)
        null
    }
}
