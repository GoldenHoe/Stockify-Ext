package com.rsps1008.stockify.data

import java.util.Locale

fun formatMarketAmount(value: Double, market: String?): String {
    return if (usesFractionalCurrency(market)) {
        String.format(Locale.US, "%,.2f", value)
    } else {
        String.format(Locale.US, "%,.0f", value)
    }
}

fun formatHomeAmount(value: Double, displayMode: String): String {
    return when (HomeDisplayMode.normalize(displayMode)) {
        HomeDisplayMode.US, HomeDisplayMode.CN -> String.format(Locale.US, "%,.2f", value)
        else -> String.format(Locale.US, "%,.0f", value)
    }
}

fun formatMarketPlainAmount(value: Double, market: String?): String {
    return if (usesFractionalCurrency(market)) {
        String.format(Locale.US, "%.2f", value)
    } else {
        String.format(Locale.US, "%.0f", value)
    }
}

/** CN amounts are kept in cents, US amounts in cents, TW amounts are whole dollars. */
internal fun usesFractionalCurrency(market: String?): Boolean =
    StockMarket.isUs(market) || StockMarket.isCn(market)
