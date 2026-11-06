package com.rsps1008.stockify.data

import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.max

/**
 * China A-share trading rules (lot size, fees, price limits and T+1).
 *
 * The app is a bookkeeping tool, so these helpers only produce the defaults and
 * warnings shown while a transaction is entered; stored transactions always win.
 */
object CnTradingRules {
    const val LOT_SIZE = 100.0
    const val DEFAULT_COMMISSION_RATE = 0.00025
    const val DEFAULT_MINIMUM_COMMISSION = 5.0
    const val STAMP_DUTY_RATE = 0.0005
    const val TRANSFER_FEE_RATE = 0.00001

    const val MAIN_BOARD_LIMIT_RATE = 0.10
    const val GROWTH_BOARD_LIMIT_RATE = 0.20
    const val BEIJING_LIMIT_RATE = 0.30
    const val ST_LIMIT_RATE = 0.05

    val SHANGHAI_ZONE: ZoneId = ZoneId.of("Asia/Shanghai")

    private const val SHARE_EPSILON = 1e-6
    private const val PRICE_EPSILON = 1e-6

    data class FeeSettings(
        val commissionRate: Double = DEFAULT_COMMISSION_RATE,
        val minimumCommission: Double = DEFAULT_MINIMUM_COMMISSION
    ) {
        fun normalized(): FeeSettings = FeeSettings(
            commissionRate = commissionRate.takeIf { it.isFinite() && it >= 0.0 } ?: DEFAULT_COMMISSION_RATE,
            minimumCommission = minimumCommission.takeIf { it.isFinite() && it >= 0.0 } ?: DEFAULT_MINIMUM_COMMISSION
        )
    }

    /** Commission with the CNY 5 minimum, rounded to cents. */
    fun commission(amount: Double, settings: FeeSettings = FeeSettings()): Double {
        if (!amount.isFinite() || amount <= 0.0) return 0.0
        val normalized = settings.normalized()
        val calculated = amount * normalized.commissionRate
        return roundToCents(max(calculated, normalized.minimumCommission))
    }

    /** Transfer fee (過戶費) applies to both buys and sells. */
    fun transferFee(amount: Double): Double {
        if (!amount.isFinite() || amount <= 0.0) return 0.0
        return roundToCents(amount * TRANSFER_FEE_RATE)
    }

    /** Stamp duty (印花稅) applies to sells only. */
    fun stampDuty(amount: Double): Double {
        if (!amount.isFinite() || amount <= 0.0) return 0.0
        return roundToCents(amount * STAMP_DUTY_RATE)
    }

    fun buyFee(amount: Double, settings: FeeSettings = FeeSettings()): Double =
        roundToCents(commission(amount, settings) + transferFee(amount))

    fun sellFee(amount: Double, settings: FeeSettings = FeeSettings()): Double =
        roundToCents(commission(amount, settings) + transferFee(amount))

    /** Estimated cost of leaving a CN position: sell fee plus stamp duty. */
    fun exitCost(marketValue: Double, settings: FeeSettings = FeeSettings()): Double =
        roundToCents(sellFee(marketValue, settings) + stampDuty(marketValue))

    fun roundToCents(value: Double): Double {
        if (!value.isFinite()) return 0.0
        return Math.round(value * 100.0) / 100.0
    }

    /** Daily price limit ratio for an A-share code, honouring ST names. */
    fun priceLimitRate(stockCode: String, stockName: String = ""): Double {
        val code = stockCode.trim()
        val name = stockName.trim().uppercase()
        if (name.startsWith("ST") || name.startsWith("*ST") || name.contains(" ST")) {
            return ST_LIMIT_RATE
        }
        return when {
            code.startsWith("300") || code.startsWith("301") || code.startsWith("688") ->
                GROWTH_BOARD_LIMIT_RATE
            code.startsWith("8") || code.startsWith("4") || code.startsWith("920") ->
                BEIJING_LIMIT_RATE
            else -> MAIN_BOARD_LIMIT_RATE
        }
    }

    fun priceLimitRange(
        previousClose: Double,
        stockCode: String,
        stockName: String = ""
    ): ClosedFloatingPointRange<Double>? {
        if (!previousClose.isFinite() || previousClose <= 0.0) return null
        val rate = priceLimitRate(stockCode, stockName)
        return (previousClose * (1.0 - rate))..(previousClose * (1.0 + rate))
    }

    /**
     * Exchange-style limit prices: the band is rounded to cents the same way the
     * Shanghai / Shenzhen exchanges round it (四捨五入 to 0.01).
     */
    fun computedLimitPrices(
        previousClose: Double,
        stockCode: String,
        stockName: String = ""
    ): Pair<Double, Double>? {
        if (!previousClose.isFinite() || previousClose <= 0.0) return null
        val rate = priceLimitRate(stockCode, stockName)
        val up = roundToCentsHalfUp(previousClose * (1.0 + rate))
        val down = roundToCentsHalfUp(previousClose * (1.0 - rate))
        if (up <= 0.0 || down <= 0.0) return null
        return down to up
    }

    private fun roundToCentsHalfUp(value: Double): Double {
        if (!value.isFinite()) return 0.0
        return java.math.BigDecimal.valueOf(value)
            .setScale(2, java.math.RoundingMode.HALF_UP)
            .toDouble()
    }

    /**
     * The one-line A-share limit hint. Quote-provided [quoteLimitUp] /
     * [quoteLimitDown] win because the exchange publishes the exact prices; the
     * computed band is only the fallback. Both limit prices are always shown.
     */
    fun priceLimitHint(
        previousClose: Double,
        stockCode: String,
        stockName: String = "",
        quoteLimitUp: Double? = null,
        quoteLimitDown: Double? = null
    ): String? {
        if (!previousClose.isFinite() || previousClose <= 0.0) return null
        val computed = computedLimitPrices(previousClose, stockCode, stockName)
        val up = quoteLimitUp?.takeIf { it.isFinite() && it > 0.0 } ?: computed?.second
        val down = quoteLimitDown?.takeIf { it.isFinite() && it > 0.0 } ?: computed?.first
        if (up == null || down == null) return null
        val percent = (priceLimitRate(stockCode, stockName) * 100).toInt()
        return "漲停 ${formatLimitPrice(up)}／跌停 ${formatLimitPrice(down)}" +
            "（前收 ${formatLimitPrice(previousClose)}，±$percent%）"
    }

    /** Returns a user-facing warning when [price] leaves the daily limit band. */
    fun priceLimitWarning(
        price: Double,
        previousClose: Double,
        stockCode: String,
        stockName: String = "",
        quoteLimitUp: Double? = null,
        quoteLimitDown: Double? = null
    ): String? {
        if (!price.isFinite() || price <= 0.0) return null
        val range = priceLimitRange(previousClose, stockCode, stockName) ?: return null
        val limitUp = quoteLimitUp?.takeIf { it.isFinite() && it > 0.0 } ?: range.endInclusive
        val limitDown = quoteLimitDown?.takeIf { it.isFinite() && it > 0.0 } ?: range.start
        if (price >= limitDown - PRICE_EPSILON && price <= limitUp + PRICE_EPSILON) {
            return null
        }
        val percent = (priceLimitRate(stockCode, stockName) * 100).toInt()
        return "價格超出漲跌幅限制（前收盤 ${formatLimitPrice(previousClose)}，±$percent% 區間 " +
            "${formatLimitPrice(limitDown)}～${formatLimitPrice(limitUp)}）"
    }

    private fun formatLimitPrice(value: Double): String = String.format(java.util.Locale.US, "%.2f", value)

    /**
     * Shares bought on an earlier calendar day, in the market time zone, minus
     * the shares already sold on the trade date. Shares bought today (T) cannot
     * be sold until the next trading day (T+1).
     */
    fun availableSellShares(
        transactions: List<StockTransaction>,
        tradeDateMillis: Long,
        zoneId: ZoneId = SHANGHAI_ZONE
    ): Double {
        val todayStart = TransactionDateSupport.moveToZoneDateStartMillis(tradeDateMillis, zoneId)
        val settled = transactions.filter { it.date < todayStart }
        val settledShares = if (settled.isEmpty()) {
            0.0
        } else {
            HoldingCalculationSupport.replayLongPosition(settled, todayStart - 1L).shares
        }
        val soldToday = transactions
            .filter { it.date in todayStart until nextDayStartMillis(todayStart, zoneId) }
            .filter { it.type == "賣出" }
            .sumOf { it.sellShares }
        return (settledShares - soldToday).coerceAtLeast(0.0)
    }

    private fun nextDayStartMillis(dayStartMillis: Long, zoneId: ZoneId): Long =
        java.time.Instant.ofEpochMilli(dayStartMillis)
            .atZone(zoneId)
            .toLocalDate()
            .plusDays(1)
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()

    /** Validates the T+1 rule for a CN sell against the same stock and account. */
    fun validateT1Sell(
        candidate: StockTransaction,
        existingTransactions: List<StockTransaction>,
        zoneId: ZoneId = SHANGHAI_ZONE
    ): String? {
        if (candidate.type != "賣出" || !StockMarket.isCn(candidate.market)) return null
        if (!candidate.sellShares.isFinite() || candidate.sellShares <= 0.0) return null
        val scoped = existingTransactions.filter {
            // A brand new candidate has no id yet, so only an existing row can
            // be excluded from the replay.
            (candidate.id == 0 || it.id != candidate.id) &&
                it.accountId == candidate.accountId &&
                canonicalStockCode(it.stockCode) == canonicalStockCode(candidate.stockCode) &&
                StockMarket.normalize(it.market) == StockMarket.normalize(candidate.market)
        }
        val available = availableSellShares(scoped, candidate.date, zoneId)
        if (candidate.sellShares <= available + SHARE_EPSILON) return null
        val roundedAvailable = if (abs(available - Math.round(available)) < SHARE_EPSILON) {
            Math.round(available).toDouble()
        } else {
            available
        }
        return "A 股 T+1 限制：當日買進的股票下一個交易日才能賣出，" +
            "可賣出股數為 ${formatSellableShares(roundedAvailable)} 股"
    }

    private fun formatSellableShares(shares: Double): String =
        if (abs(shares - Math.round(shares)) < SHARE_EPSILON) {
            Math.round(shares).toString()
        } else {
            String.format(java.util.Locale.US, "%.2f", shares)
        }

    /**
     * Replays a whole transaction list in order and validates every CN sell.
     * Used by CSV import, where all rows must be checked before any write.
     */
    fun validateT1Sells(
        transactions: List<StockTransaction>,
        zoneId: ZoneId = SHANGHAI_ZONE
    ): String? {
        val ordered = transactions.sortedWith(
            compareBy<StockTransaction> { it.date }
                .thenBy { it.recordTime }
                .thenBy { it.id }
        )
        val prior = mutableListOf<StockTransaction>()
        for (transaction in ordered) {
            validateT1Sell(transaction, prior, zoneId)?.let { return it }
            prior += transaction
        }
        return null
    }

    /** True when [shares] is a whole number of 100-share lots. */
    fun isWholeLot(shares: Double): Boolean {
        if (!shares.isFinite() || shares <= 0.0) return false
        val lots = shares / LOT_SIZE
        return abs(lots - Math.round(lots)) < SHARE_EPSILON
    }
}
