package com.rsps1008.stockify.data

object StockMarket {
    const val TW = "TW"
    const val US = "US"
    const val CN = "CN"

    private val usTickerPattern = Regex("[A-Za-z]+(?:[.-][A-Za-z]+)?")
    private val cnStockCodePattern = Regex("\\d{6}")

    fun normalize(value: String?): String {
        return when (value?.trim()?.uppercase()) {
            US -> US
            CN -> CN
            TW -> TW
            else -> TW
        }
    }

    fun isUs(value: String?): Boolean = normalize(value) == US

    fun isTw(value: String?): Boolean = normalize(value) == TW

    fun isCn(value: String?): Boolean = normalize(value) == CN

    fun inferFromCode(stockCode: String): String {
        val trimmed = stockCode.trim()
        return when {
            trimmed.isBlank() -> TW
            // Taiwan ETFs and other securities may use mixed codes such as
            // 00981A. Only an unambiguously US-shaped ticker is inferred as US;
            // a code containing digits remains Taiwan unless its market is
            // explicitly supplied by the caller.
            trimmed.matches(usTickerPattern) -> US
            // CN codes are also six digits, so CN must never be inferred here.
            else -> TW
        }
    }

    /**
     * A market supplied by the caller must match the shape of its stock code.
     * The check is one-directional on purpose: six digit codes are valid for
     * both TW and CN, so only an explicitly supplied CN market makes a six
     * digit code Chinese.
     */
    fun isCodeConsistent(market: String?, stockCode: String): Boolean {
        val trimmed = stockCode.trim()
        if (trimmed.isBlank()) return false
        return when (normalize(market)) {
            US -> trimmed.matches(usTickerPattern)
            CN -> trimmed.matches(cnStockCodePattern)
            else -> !trimmed.matches(usTickerPattern)
        }
    }
}
