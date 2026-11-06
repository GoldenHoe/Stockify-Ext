package com.rsps1008.stockify

import com.rsps1008.stockify.data.CnMarketCalendar
import com.rsps1008.stockify.data.isChinaMarketOpen
import com.rsps1008.stockify.data.parseCnMarketIndexes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class CnMarketIndexAndCalendarTest {

    private val shanghai: ZoneId = ZoneId.of("Asia/Shanghai")

    private val indexBody = """
        {"rc":0,"rt":11,"svr":183641481,"lt":1,"full":1,"dlmkts":"","dsc":"0","data":{"total":3,"diff":[
          {"f2":246523,"f3":-396,"f4":-10157,"f12":"399006","f14":"创业板指"},
          {"f2":364918,"f3":-110,"f4":-4073,"f12":"000001","f14":"上证指数"},
          {"f2":1117578,"f3":-257,"f4":-29531,"f12":"399001","f14":"深证成指"}
        ]}}
    """.trimIndent()

    @Test
    fun indexValuesAreScaledAndOrderedAsShanghaiShenzhenChiNext() {
        val indexes = parseCnMarketIndexes(indexBody)

        assertEquals(listOf("000001", "399001", "399006"), indexes.map { it.code })
        val shanghaiIndex = indexes.first()
        assertEquals("上证指数", shanghaiIndex.name)
        assertEquals(3649.18, shanghaiIndex.current, 1e-9)
        assertEquals(-40.73, shanghaiIndex.change, 1e-9)
        assertEquals(-1.10, shanghaiIndex.changePercent, 1e-9)
        assertEquals(11175.78, indexes[1].current, 1e-9)
        assertEquals(2465.23, indexes[2].current, 1e-9)
    }

    @Test
    fun indexParsingSkipsSuspendedRowsAndRejectsGarbage() {
        val body = """
            {"data":{"diff":[
              {"f2":"-","f3":"-","f4":"-","f12":"000001","f14":"上证指数"},
              {"f2":1117578,"f12":"399001","f14":"深证成指"}
            ]}}
        """.trimIndent()

        val indexes = parseCnMarketIndexes(body)

        assertEquals(listOf("399001"), indexes.map { it.code })
        assertEquals(0.0, indexes.first().changePercent, 1e-9)
        assertTrue(parseCnMarketIndexes("").isEmpty())
        assertTrue(parseCnMarketIndexes("not json").isEmpty())
        assertTrue(parseCnMarketIndexes("""{"data":null}""").isEmpty())
    }

    @Test
    fun verifiedHolidayWeekdaysAreNonTrading() {
        // 2026 春節 2/16-2/20 and 2/23 are weekdays the exchanges stay closed.
        assertTrue(CnMarketCalendar.isHoliday(LocalDate.of(2026, 2, 16)))
        assertTrue(CnMarketCalendar.isHoliday(LocalDate.of(2026, 10, 5)))
        assertFalse(CnMarketCalendar.isNonTradingDay(LocalDate.of(2026, 10, 8)))
        assertTrue(CnMarketCalendar.isTradingDay(LocalDate.of(2026, 11, 6)))
        assertFalse(CnMarketCalendar.isTradingDay(LocalDate.of(2026, 11, 7)))
        assertFalse(CnMarketCalendar.isTradingDay(LocalDate.of(2026, 1, 1)))
        // A year without a verified list falls back to weekends only.
        assertTrue(CnMarketCalendar.isTradingDay(LocalDate.of(2025, 10, 1)))
        assertFalse(CnMarketCalendar.isTradingDay(LocalDate.of(2025, 10, 4)))
    }

    @Test
    fun marketSessionSkipsVerifiedHolidays() {
        fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int) =
            LocalDateTime.of(year, month, day, hour, minute).atZone(shanghai)

        assertTrue(isChinaMarketOpen(at(2026, 11, 6, 10, 0)))
        assertFalse(isChinaMarketOpen(at(2026, 2, 17, 10, 0)))
        assertFalse(isChinaMarketOpen(at(2026, 10, 1, 14, 0)))
        assertTrue(isChinaMarketOpen(at(2026, 10, 8, 14, 0)))
    }
}
