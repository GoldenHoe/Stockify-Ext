package com.rsps1008.stockify.data

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

internal const val CN_QUOTE_BATCH_SIZE = 50

/** Eastmoney gives every value in cents, so prices and percentages are divided by 100. */
internal const val CN_QUOTE_VALUE_SCALE = 100.0

internal fun eastmoneySecId(stockCode: String): String? {
    val code = canonicalStockCode(stockCode)
    if (code.isEmpty()) return null
    // Shanghai uses market 1. Shenzhen and Beijing (4xx, 8xx, 920xxx) use 0;
    // 920xxx must be checked before the general 9xxxxx Shanghai rule.
    val marketFlag = when {
        code.startsWith("920") || code.startsWith("8") || code.startsWith("4") -> "0"
        code.startsWith("6") || code.startsWith("9") -> "1"
        else -> "0"
    }
    return "$marketFlag.$code"
}

internal fun parseEastmoneyBatchQuotes(jsonText: String): Map<String, RealtimeStockInfo> {
    if (jsonText.isBlank()) return emptyMap()
    val root = runCatching { Json.parseToJsonElement(jsonText) }.getOrNull() as? JsonObject ?: return emptyMap()
    val diff = root["data"].asObjectOrNull()?.get("diff") ?: return emptyMap()
    val rows: List<JsonObject> = when (val element = diff) {
        is JsonArray -> element.mapNotNull { it as? JsonObject }
        is JsonObject -> element.values.mapNotNull { it as? JsonObject }
        else -> emptyList()
    }
    return rows.mapNotNull { row ->
        val code = row.stringOrNull("f12")?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        val info = parseQuoteFields(
            price = row.scaledDoubleOrNull("f2"),
            change = row.scaledDoubleOrNull("f4"),
            changePercent = row.scaledDoubleOrNull("f3"),
            previousClose = row.scaledDoubleOrNull("f18")
        ) ?: return@mapNotNull null
        code to info
    }.toMap()
}

internal fun parseEastmoneySingleQuote(jsonText: String, stockCode: String): RealtimeStockInfo? {
    if (jsonText.isBlank()) return null
    val root = runCatching { Json.parseToJsonElement(jsonText) }.getOrNull() as? JsonObject ?: return null
    val data = root["data"].asObjectOrNull() ?: return null
    return parseQuoteFields(
        price = data.scaledDoubleOrNull("f43"),
        change = data.scaledDoubleOrNull("f169"),
        changePercent = data.scaledDoubleOrNull("f170"),
        previousClose = data.scaledDoubleOrNull("f60"),
        // f51 / f52 are only meaningful on the per-symbol endpoint; the batch
        // (ulist.np) field dictionary maps them to unrelated financial values.
        limitUpPrice = data.scaledDoubleOrNull("f51"),
        limitDownPrice = data.scaledDoubleOrNull("f52")
    )
}

internal fun parseQuoteFields(
    price: Double?,
    change: Double?,
    changePercent: Double?,
    previousClose: Double?,
    limitUpPrice: Double? = null,
    limitDownPrice: Double? = null
): RealtimeStockInfo? {
    val validPrice = price?.takeIf { it.isFinite() && it > 0.0 } ?: return null
    val previous = previousClose?.takeIf { it.isFinite() && it > 0.0 }
    val resolvedChange = change?.takeIf { it.isFinite() }
        ?: previous?.let { validPrice - it }
        ?: 0.0
    val resolvedPercent = changePercent?.takeIf { it.isFinite() }
        ?: previous?.let { (validPrice - it) / it * 100.0 }
        ?: 0.0
    val validLimitUp = limitUpPrice?.takeIf { it.isFinite() && it > 0.0 }
    val validLimitDown = limitDownPrice?.takeIf { it.isFinite() && it > 0.0 }
    return RealtimeStockInfo(
        currentPrice = validPrice,
        change = resolvedChange,
        changePercent = resolvedPercent,
        limitState = LimitState.NONE,
        limitUpPrice = validLimitUp,
        limitDownPrice = validLimitDown
    )
}

private fun JsonElement?.asObjectOrNull(): JsonObject? = this as? JsonObject

private fun JsonObject.stringOrNull(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.scaledDoubleOrNull(key: String): Double? {
    val raw = (this[key] as? JsonPrimitive)?.contentOrNull?.trim() ?: return null
    return raw.replace(",", "").toDoubleOrNull()?.let { value ->
        (value / CN_QUOTE_VALUE_SCALE).takeIf { it.isFinite() }
    }
}

class CnStockInfoFetcher : StockInfoFetcher {

    private val client = HttpClient(CIO) {
        engine {
            requestTimeout = 8000
        }
    }

    override fun isMarketOpen(): Boolean = isChinaMarketOpen(ZonedDateTime.now(CN_ZONE))

    override suspend fun fetchStockInfoList(stockCodes: List<String>): Map<String, RealtimeStockInfo> =
        withContext(Dispatchers.IO) {
            val codes = stockCodes.map { canonicalStockCode(it) }.filter { it.isNotEmpty() }.distinct()
            if (codes.isEmpty()) return@withContext emptyMap()
            val result = mutableMapOf<String, RealtimeStockInfo>()
            for (chunk in codes.chunked(CN_QUOTE_BATCH_SIZE)) {
                fetchBatch(chunk).forEach { (code, info) -> result[code] = info }
            }
            result
        }

    override suspend fun fetchStockInfo(stockCode: String, stockType: String): RealtimeStockInfo? =
        withContext(Dispatchers.IO) {
            val code = canonicalStockCode(stockCode)
            val secId = eastmoneySecId(code) ?: return@withContext null
            val url = "https://push2.eastmoney.com/api/qt/stock/get" +
                "?secid=$secId&fields=f43,f51,f52,f57,f58,f60,f169,f170,f168"
            val body = retryOnTransientNetworkFailure(TAG, code) {
                client.get(url).bodyAsText()
            } ?: return@withContext null
            val info = parseEastmoneySingleQuote(body, code)
            if (info == null) {
                Log.w(TAG, "Missing CN quote fields for $code")
            }
            info
        }

    private suspend fun fetchBatch(codes: List<String>): Map<String, RealtimeStockInfo> {
        val secIds = codes.mapNotNull { eastmoneySecId(it) }
        if (secIds.isEmpty()) return emptyMap()
        val url = "https://push2.eastmoney.com/api/qt/ulist.np/get" +
            "?secids=${secIds.joinToString(",")}" +
            "&fields=f12,f13,f14,f2,f3,f4,f18,f15,f16,f17"
        val body = retryOnTransientNetworkFailure(TAG, codes.first()) {
            client.get(url).bodyAsText()
        } ?: return emptyMap()
        val quotes = parseEastmoneyBatchQuotes(body)
        if (quotes.isEmpty()) {
            Log.w(TAG, "CN batch quote returned no usable rows for ${codes.size} symbols")
        }
        return quotes
    }

    private companion object {
        const val TAG = "CnStockInfoFetcher"
        val CN_ZONE: ZoneId = ZoneId.of("Asia/Shanghai")
    }
}

/** China A-share session: 09:30-11:30 and 13:00-15:00 on trading days, Asia/Shanghai. */
internal fun isChinaMarketOpen(now: ZonedDateTime): Boolean {
    if (now.dayOfWeek !in DayOfWeek.MONDAY..DayOfWeek.FRIDAY) return false
    if (CnMarketCalendar.isHoliday(now.toLocalDate())) return false
    val time = now.toLocalTime()
    val morning = !time.isBefore(LocalTime.of(9, 30)) && time.isBefore(LocalTime.of(11, 30))
    val afternoon = !time.isBefore(LocalTime.of(13, 0)) && time.isBefore(LocalTime.of(15, 0))
    return morning || afternoon
}
