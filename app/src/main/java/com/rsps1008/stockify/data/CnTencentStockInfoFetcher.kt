package com.rsps1008.stockify.data

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.charset.Charset
import java.time.ZonedDateTime

internal const val CN_TENCENT_BATCH_SIZE = 60

internal fun tencentSymbol(stockCode: String): String? {
    val code = canonicalStockCode(stockCode)
    if (code.isEmpty()) return null
    val exchangePrefix = when {
        code.startsWith("920") || code.startsWith("8") || code.startsWith("4") -> "bj"
        code.startsWith("6") || code.startsWith("9") -> "sh"
        else -> "sz"
    }
    return "$exchangePrefix$code"
}

/**
 * Tencent's single-line quote format, fields separated by "~":
 * 1 name, 2 code, 3 price, 4 previous close, 31 change, 32 change percent,
 * 47 limit up, 48 limit down.
 */
private const val TENCENT_FIELD_CODE = 2
private const val TENCENT_FIELD_PRICE = 3
private const val TENCENT_FIELD_PREVIOUS_CLOSE = 4
private const val TENCENT_FIELD_CHANGE = 31
private const val TENCENT_FIELD_CHANGE_PERCENT = 32
private const val TENCENT_FIELD_LIMIT_UP = 47
private const val TENCENT_FIELD_LIMIT_DOWN = 48

private fun String.field(index: Int): String? =
    split('~').getOrNull(index)?.trim()?.takeIf { it.isNotEmpty() }

private fun String.doubleField(index: Int): Double? = field(index)?.toDoubleOrNull()?.takeIf { it.isFinite() }

/**
 * Parses the `v_sh600000="..."` lines of `https://qt.gtimg.cn/q=sh600000,sz000001`.
 * A suspended symbol answers with `v_sh600000="";` and is simply skipped.
 */
internal fun parseTencentQuotes(body: String): Map<String, RealtimeStockInfo> {
    if (body.isBlank()) return emptyMap()
    val quotes = linkedMapOf<String, RealtimeStockInfo>()
    for (rawLine in body.lineSequence()) {
        val line = rawLine.trim()
        val payloadStart = line.indexOf('"')
        if (payloadStart < 0) continue
        val payloadEnd = line.lastIndexOf('"')
        if (payloadEnd <= payloadStart) continue
        val payload = line.substring(payloadStart + 1, payloadEnd)
        if (!payload.contains('~')) continue

        val code = payload.field(TENCENT_FIELD_CODE)?.takeIf { it.matches(Regex("\\d{6}")) }
            ?: continue
        val info = parseQuoteFields(
            price = payload.doubleField(TENCENT_FIELD_PRICE),
            change = payload.doubleField(TENCENT_FIELD_CHANGE),
            changePercent = payload.doubleField(TENCENT_FIELD_CHANGE_PERCENT),
            previousClose = payload.doubleField(TENCENT_FIELD_PREVIOUS_CLOSE),
            limitUpPrice = payload.doubleField(TENCENT_FIELD_LIMIT_UP),
            limitDownPrice = payload.doubleField(TENCENT_FIELD_LIMIT_DOWN)
        ) ?: continue
        quotes.putIfAbsent(code, info)
    }
    return quotes
}

/**
 * Second and last CN quote source. It is only used when both the Eastmoney batch
 * and Eastmoney's per-symbol endpoint returned nothing for a code.
 */
class CnTencentStockInfoFetcher : StockInfoFetcher {

    private val client = HttpClient(CIO) {
        engine {
            requestTimeout = 8000
        }
    }

    override fun isMarketOpen(): Boolean = isChinaMarketOpen(ZonedDateTime.now(CnTradingRules.SHANGHAI_ZONE))

    override suspend fun fetchStockInfoList(stockCodes: List<String>): Map<String, RealtimeStockInfo> =
        withContext(Dispatchers.IO) {
            val codes = stockCodes.map { canonicalStockCode(it) }.filter { it.isNotEmpty() }.distinct()
            if (codes.isEmpty()) return@withContext emptyMap()
            val result = mutableMapOf<String, RealtimeStockInfo>()
            for (chunk in codes.chunked(CN_TENCENT_BATCH_SIZE)) {
                fetchBatch(chunk).forEach { (code, info) -> result[code] = info }
            }
            result
        }

    override suspend fun fetchStockInfo(stockCode: String, stockType: String): RealtimeStockInfo? =
        withContext(Dispatchers.IO) {
            val code = canonicalStockCode(stockCode)
            if (code.isEmpty()) return@withContext null
            fetchBatch(listOf(code))[code]
        }

    private suspend fun fetchBatch(codes: List<String>): Map<String, RealtimeStockInfo> {
        val symbols = codes.mapNotNull { tencentSymbol(it) }
        if (symbols.isEmpty()) return emptyMap()
        val url = "https://qt.gtimg.cn/q=${symbols.joinToString(",")}"
        val body = retryOnTransientNetworkFailure(TAG, codes.first()) {
            // Tencent's payload is GBK; decoding it as UTF-8 would only garble the
            // names, but GB18030 keeps the numeric fields strictly separated.
            client.get(url).bodyAsBytes().toString(Charset.forName("GB18030"))
        } ?: return emptyMap()
        val quotes = parseTencentQuotes(body)
        if (quotes.isEmpty()) {
            Log.w(TAG, "Tencent quote returned no usable rows for ${codes.size} symbols")
        }
        return quotes
    }

    private companion object {
        const val TAG = "CnTencentQuoteFetcher"
    }
}
