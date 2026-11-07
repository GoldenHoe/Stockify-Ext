package com.rsps1008.stockify

import com.rsps1008.stockify.data.MarketSessionSupport
import com.rsps1008.stockify.data.StockMarket
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class MarketSessionSupportTest {

    private fun millis(date: LocalDate, time: LocalTime, zoneId: ZoneId): Long =
        date.atTime(time).atZone(zoneId).toInstant().toEpochMilli()

    private val shanghai: ZoneId = ZoneId.of("Asia/Shanghai")
    private val newYork: ZoneId = ZoneId.of("America/New_York")

    @Test
    fun `weekend keeps the daily profit at zero`() {
        // 2026-11-07 is a Saturday: quotes still carry Friday's change.
        val saturday = millis(LocalDate.of(2026, 11, 7), LocalTime.of(13, 57), shanghai)
        assertFalse(MarketSessionSupport.hasSessionStartedToday(StockMarket.CN, saturday))
        assertFalse(MarketSessionSupport.hasSessionStartedToday(StockMarket.TW, saturday))
        assertFalse(MarketSessionSupport.hasSessionStartedToday(StockMarket.US, saturday))
    }

    @Test
    fun `trading day counts only after the opening time`() {
        val friday = LocalDate.of(2026, 11, 6)
        assertFalse(
            MarketSessionSupport.hasSessionStartedToday(
                StockMarket.CN,
                millis(friday, LocalTime.of(8, 30), shanghai)
            )
        )
        assertTrue(
            MarketSessionSupport.hasSessionStartedToday(
                StockMarket.CN,
                millis(friday, LocalTime.of(9, 30), shanghai)
            )
        )
        assertTrue(
            MarketSessionSupport.hasSessionStartedToday(
                StockMarket.CN,
                millis(friday, LocalTime.of(20, 0), shanghai)
            )
        )
    }

    @Test
    fun `china public holidays never start a session`() {
        // 2026-10-01 is a Thursday but a State Council holiday.
        val holiday = millis(LocalDate.of(2026, 10, 1), LocalTime.of(10, 0), shanghai)
        assertFalse(MarketSessionSupport.hasSessionStartedToday(StockMarket.CN, holiday))
    }

    @Test
    fun `us session follows new york time`() {
        val friday = LocalDate.of(2026, 11, 6)
        assertFalse(
            MarketSessionSupport.hasSessionStartedToday(
                StockMarket.US,
                millis(friday, LocalTime.of(9, 0), newYork)
            )
        )
        assertTrue(
            MarketSessionSupport.hasSessionStartedToday(
                StockMarket.US,
                millis(friday, LocalTime.of(10, 0), newYork)
            )
        )
    }

    @Test
    fun `unknown market falls back to taipei hours`() {
        val friday = LocalDate.of(2026, 11, 6)
        val taipei = ZoneId.of("Asia/Taipei")
        assertFalse(
            MarketSessionSupport.hasSessionStartedToday(
                "",
                millis(friday, LocalTime.of(8, 59), taipei)
            )
        )
        assertTrue(
            MarketSessionSupport.hasSessionStartedToday(
                "",
                millis(friday, LocalTime.of(9, 0), taipei)
            )
        )
    }
}
