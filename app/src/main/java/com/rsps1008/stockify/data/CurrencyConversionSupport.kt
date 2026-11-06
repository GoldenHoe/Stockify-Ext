package com.rsps1008.stockify.data

/**
 * Combined home mode reports everything in TWD. TW positions need no
 * conversion, US positions use USD/TWD and CN positions use CNY/TWD.
 */
object CurrencyConversionSupport {
    fun rateToTwd(market: String?, usdToTwdRate: Double, cnyToTwdRate: Double): Double {
        return when {
            StockMarket.isUs(market) -> usdToTwdRate.takeIf { it.isFinite() && it > 0.0 } ?: 1.0
            StockMarket.isCn(market) -> cnyToTwdRate.takeIf { it.isFinite() && it > 0.0 } ?: 1.0
            else -> 1.0
        }
    }

    fun combinedRateToTwd(
        market: String?,
        combined: Boolean,
        usdToTwdRate: Double,
        cnyToTwdRate: Double
    ): Double = if (combined) rateToTwd(market, usdToTwdRate, cnyToTwdRate) else 1.0
}
