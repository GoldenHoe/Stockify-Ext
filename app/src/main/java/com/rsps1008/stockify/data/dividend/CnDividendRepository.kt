package com.rsps1008.stockify.data.dividend

import com.rsps1008.stockify.data.canonicalStockCode
import com.rsps1008.stockify.data.retryOnTransientNetworkFailure
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * One A-share cash / stock dividend plan reported by Eastmoney's
 * `RPT_SHAREBONUS_DET` dataset. Eastmoney expresses the plan per ten shares
 * ("10派4.20元"), so the per-share values are divided by ten.
 */
data class CnDividendRecord(
    val reportDate: String?,
    val cashDividendPerShare: Double?,
    val stockDividendPerShare: Double?,
    val exDividendDate: String?,
    val recordDate: String?,
    val planProgress: String?,
    val planProfile: String?
) {
    val hasCash: Boolean get() = (cashDividendPerShare ?: 0.0) > 0.0
    val hasStock: Boolean get() = (stockDividendPerShare ?: 0.0) > 0.0
}

data class CnDividendSummary(
    val records: List<CnDividendRecord> = emptyList()
) {
    val latest: CnDividendRecord? get() = records.firstOrNull()
}

internal const val CN_DIVIDEND_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

internal fun cnDividendUrl(stockCode: String, pageSize: Int = 10): String? {
    val code = canonicalStockCode(stockCode)
    if (!code.matches(Regex("\\d{6}"))) return null
    return "https://datacenter-web.eastmoney.com/api/data/v1/get" +
        "?reportName=RPT_SHAREBONUS_DET&columns=ALL" +
        "&filter=(SECURITY_CODE%3D%22$code%22)" +
        "&pageNumber=1&pageSize=$pageSize&sortColumns=REPORT_DATE&sortTypes=-1"
}

/** Eastmoney timestamps look like `2026-07-16 00:00:00`; the UI expects `yyyy/MM/dd`. */
internal fun normalizeCnDividendDate(raw: String?): String? {
    val text = raw?.trim().orEmpty()
    if (text.isEmpty()) return null
    val datePart = text.substringBefore(' ').trim()
    return runCatching {
        LocalDate.parse(datePart, DateTimeFormatter.ISO_LOCAL_DATE)
            .format(DateTimeFormatter.ofPattern("yyyy/MM/dd"))
    }.getOrNull()
}

private fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() && it != "-" }

private fun JsonObject.doubleOrNull(key: String): Double? {
    val raw = string(key)?.replace(",", "") ?: return null
    return raw.toDoubleOrNull()?.takeIf { it.isFinite() }
}

/** Parses the share-bonus dataset defensively; a bad row is skipped, never guessed. */
/** 4.2 / 10.0 yields 0.42000000000000004; round the published per-ten-share value first. */
internal fun perShareFromTenShares(value: Double): Double =
    java.math.BigDecimal.valueOf(value)
        .divide(java.math.BigDecimal.TEN, 4, java.math.RoundingMode.HALF_UP)
        .toDouble()

internal fun parseCnDividendResponse(jsonText: String): List<CnDividendRecord> {
    if (jsonText.isBlank()) return emptyList()
    val root = runCatching { Json.parseToJsonElement(jsonText) }.getOrNull() as? JsonObject
        ?: return emptyList()
    val data = ((root["result"] as? JsonObject)?.get("data") as? kotlinx.serialization.json.JsonArray)
        ?: return emptyList()
    return data.mapNotNull { element ->
        val row = element as? JsonObject ?: return@mapNotNull null
        val cash = row.doubleOrNull("PRETAX_BONUS_RMB")?.takeIf { it > 0.0 }?.let(::perShareFromTenShares)
        val bonusRatio = row.doubleOrNull("BONUS_RATIO") ?: 0.0
        val bonusItRatio = row.doubleOrNull("BONUS_IT_RATIO") ?: 0.0
        val stock = (bonusRatio + bonusItRatio).takeIf { it > 0.0 }?.let(::perShareFromTenShares)
        val record = CnDividendRecord(
            reportDate = normalizeCnDividendDate(row.string("REPORT_DATE")),
            cashDividendPerShare = cash,
            stockDividendPerShare = stock,
            exDividendDate = normalizeCnDividendDate(row.string("EX_DIVIDEND_DATE")),
            recordDate = normalizeCnDividendDate(row.string("EQUITY_RECORD_DATE")),
            planProgress = row.string("ASSIGN_PROGRESS"),
            planProfile = row.string("IMPL_PLAN_PROFILE")
        )
        // A plan without any dividend amount or date carries no usable information.
        if (record.cashDividendPerShare == null &&
            record.stockDividendPerShare == null &&
            record.exDividendDate == null
        ) {
            null
        } else {
            record
        }
    }
}

/**
 * Picks the newest cash dividend plan: the most recent ex-dividend date that has
 * already happened, otherwise the newest announced plan.
 */
internal fun latestCnCashDividendRecord(
    records: List<CnDividendRecord>,
    today: LocalDate = LocalDate.now()
): CnDividendRecord? {
    val cashRecords = records.filter { it.hasCash }
    if (cashRecords.isEmpty()) return null
    fun dateOrMin(record: CnDividendRecord): String =
        record.exDividendDate ?: record.reportDate ?: ""
    val todayText = today.format(DateTimeFormatter.ofPattern("yyyy/MM/dd"))
    val past = cashRecords
        .filter { dateOrMin(it).isNotEmpty() && dateOrMin(it) <= todayText }
        .maxByOrNull { dateOrMin(it) }
    return past ?: cashRecords.maxByOrNull { dateOrMin(it) }
}

internal fun latestCnStockDividendRecord(records: List<CnDividendRecord>): CnDividendRecord? =
    records.filter { it.hasStock }.maxByOrNull { it.exDividendDate ?: it.reportDate ?: "" }

class CnDividendRepository(
    private val client: HttpClient
) {
    private companion object {
        const val TAG = "CnDividendRepository"
    }

    suspend fun fetchDividends(stockCode: String): CnDividendSummary {
        val url = cnDividendUrl(stockCode)
            ?: throw IOException("A 股代號不合法: $stockCode")
        val body = retryOnTransientNetworkFailure(TAG, stockCode) {
            client.get(url) {
                header("User-Agent", CN_DIVIDEND_USER_AGENT)
                header("Accept", "application/json, text/plain, */*")
            }.bodyAsText()
        } ?: throw IOException("東方財富股利資料請求失敗: $stockCode")
        return CnDividendSummary(parseCnDividendResponse(body))
    }

    /** Latest cash dividend per share and its ex-dividend date, or null when unknown. */
    suspend fun fetchLatestCashDividend(stockCode: String): DividendResult? =
        latestCnCashDividendRecord(fetchDividends(stockCode).records)?.let { record ->
            val amount = record.cashDividendPerShare ?: return@let null
            val date = record.exDividendDate ?: record.reportDate ?: return@let null
            DividendResult(amount, date)
        }

    suspend fun fetchLatestStockDividend(stockCode: String): DividendResult? =
        latestCnStockDividendRecord(fetchDividends(stockCode).records)?.let { record ->
            val amount = record.stockDividendPerShare ?: return@let null
            val date = record.exDividendDate ?: record.reportDate ?: return@let null
            DividendResult(amount, date)
        }
}
