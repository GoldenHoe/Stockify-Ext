package com.rsps1008.stockify.data

import java.util.Locale

object StockExchange {
    const val UNKNOWN = ""
    const val LISTED = "上市"
    const val OTC = "上櫃"
    const val EMERGING = "興櫃"

    // CN exchanges. "滬市" is used instead of "上市" so a Chinese exchange can
    // never be confused with the existing Taiwan listed value.
    const val SH = "滬市"
    const val SZ = "深市"
    const val BJ = "北交所"

    fun normalize(value: String?): String {
        val text = value?.trim().orEmpty()
        return when {
            text.contains("北交所") || text.contains("北京") -> BJ
            text.contains("深市") || text.contains("深圳") -> SZ
            text.contains("滬市") || text.contains("上海") || text.contains("滬") -> SH
            text.contains("興櫃") -> EMERGING
            text.contains("上櫃") -> OTC
            text.contains("上市") -> LISTED
            else -> UNKNOWN
        }
    }

    fun isEmerging(value: String?): Boolean = normalize(value) == EMERGING

    fun isCn(value: String?): Boolean = normalize(value) in cnExchanges

    private val cnExchanges = setOf(SH, SZ, BJ)

    /** Resolves a Chinese exchange from an A-share code prefix. */
    fun fromCnCode(stockCode: String): String {
        val code = stockCode.trim().uppercase(Locale.ROOT)
        return when {
            code.startsWith("92") || code.startsWith("4") || code.startsWith("8") -> BJ
            code.startsWith("6") || code.startsWith("9") -> SH
            code.startsWith("0") || code.startsWith("2") || code.startsWith("3") -> SZ
            else -> UNKNOWN
        }
    }
}
