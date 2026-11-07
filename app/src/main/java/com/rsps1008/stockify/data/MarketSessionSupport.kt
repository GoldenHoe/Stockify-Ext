package com.rsps1008.stockify.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * Decides whether a market has already traded today, so "當日損益" is not filled
 * with the previous session's price change.
 *
 * Quotes keep carrying the last session's `change` (price minus previous close) when
 * the market is closed: on a Saturday the app would otherwise report Friday's move as
 * today's profit/loss, and the number would look frozen for the whole weekend.
 */
object MarketSessionSupport {

    private val CN_ZONE: ZoneId = ZoneId.of("Asia/Shanghai")
    private val TW_ZONE: ZoneId = ZoneId.of("Asia/Taipei")
    private val US_ZONE: ZoneId = ZoneId.of("America/New_York")

    private val CN_OPEN: LocalTime = LocalTime.of(9, 30)
    private val TW_OPEN: LocalTime = LocalTime.of(9, 0)
    private val US_OPEN: LocalTime = LocalTime.of(9, 30)

    fun hasSessionStartedToday(market: String?, nowMillis: Long): Boolean {
        val zone = zoneFor(market)
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
        val date = now.toLocalDate()
        val time = now.toLocalTime()
        return when {
            StockMarket.isCn(market) -> CnMarketCalendar.isTradingDay(date) && !time.isBefore(CN_OPEN)
            StockMarket.isUs(market) -> isWeekday(date) && !time.isBefore(US_OPEN)
            else -> isWeekday(date) && !time.isBefore(TW_OPEN)
        }
    }

    private fun zoneFor(market: String?): ZoneId = when {
        StockMarket.isCn(market) -> CN_ZONE
        StockMarket.isUs(market) -> US_ZONE
        else -> TW_ZONE
    }

    private fun isWeekday(date: java.time.LocalDate): Boolean =
        date.dayOfWeek != DayOfWeek.SATURDAY && date.dayOfWeek != DayOfWeek.SUNDAY
}
