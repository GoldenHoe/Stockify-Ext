package com.rsps1008.stockify.data

import android.util.Log
import com.rsps1008.stockify.ui.text.ChineseTextConverter
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.net.URLEncoder

internal const val CN_SUGGEST_TOKEN = "D43BF722C8E33BDC906FB84D85E326E8"

internal fun cnSuggestUrl(query: String, count: Int = 10): String =
    "https://searchapi.eastmoney.com/api/suggest/get" +
        "?input=${URLEncoder.encode(query, "UTF-8")}" +
        "&type=14&token=$CN_SUGGEST_TOKEN&count=$count"

/**
 * Maps Eastmoney's suggest payload into [Stock] rows. Only `Classify == "AStock"`
 * rows are kept, so indices, funds, Hong Kong and US listings never leak into the
 * A-share list. Names arrive in Simplified Chinese and are stored as Traditional,
 * matching the bundled stock list.
 */
internal fun parseEastmoneySuggest(jsonText: String, limit: Int = 5): List<Stock> {
    if (jsonText.isBlank() || limit <= 0) return emptyList()
    val root = runCatching { Json.parseToJsonElement(jsonText) }.getOrNull() as? JsonObject
        ?: return emptyList()
    val table = root["QuotationCodeTable"] as? JsonObject ?: return emptyList()
    val rows = table["Data"] as? JsonArray ?: return emptyList()
    val result = linkedMapOf<String, Stock>()
    for (element in rows) {
        val row = element as? JsonObject ?: continue
        fun value(key: String): String? =
            (row[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
        if (value("Classify") != "AStock") continue
        val code = value("Code") ?: continue
        if (!code.matches(Regex("\\d{6}"))) continue
        val name = value("Name")?.let { ChineseTextConverter.toTraditional(it) } ?: code
        val marketFlag = value("MktNum")?.toIntOrNull()
        val exchange = when {
            marketFlag == 1 -> StockExchange.SH
            StockExchange.fromCnCode(code) != StockExchange.UNKNOWN ->
                StockExchange.fromCnCode(code)
            else -> StockExchange.SZ
        }
        result.putIfAbsent(
            code,
            Stock(
                name = name,
                code = code,
                market = StockMarket.CN,
                exchange = exchange,
                industry = "",
                stockType = "股票"
            )
        )
        if (result.size >= limit) break
    }
    return result.values.toList()
}

/**
 * Eastmoney search fallback for the CN market. It is only queried when the local
 * bundled list produced no A-share match, so local results always come first.
 */
class CnStockSuggestService(private val client: HttpClient) {

    private companion object {
        const val TAG = "CnStockSuggestService"
    }

    suspend fun suggest(query: String, limit: Int = 5): List<Stock> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        return try {
            val body = retryOnTransientNetworkFailure(TAG, trimmed) {
                client.get(cnSuggestUrl(trimmed)) {
                    headers.append("User-Agent", "Mozilla/5.0")
                    headers.append("Accept", "application/json, text/plain, */*")
                }.bodyAsText()
            } ?: return emptyList()
            parseEastmoneySuggest(body, limit)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "CN stock suggest failed for $trimmed: ${e.message}")
            emptyList()
        }
    }
}
