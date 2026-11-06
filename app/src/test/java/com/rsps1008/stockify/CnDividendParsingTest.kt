package com.rsps1008.stockify

import com.rsps1008.stockify.data.dividend.cnDividendUrl
import com.rsps1008.stockify.data.dividend.latestCnCashDividendRecord
import com.rsps1008.stockify.data.dividend.latestCnStockDividendRecord
import com.rsps1008.stockify.data.dividend.normalizeCnDividendDate
import com.rsps1008.stockify.data.dividend.parseCnDividendResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CnDividendParsingTest {

    // Trimmed from the live RPT_SHAREBONUS_DET response for 600000.
    private val dividendBody = """
        {"version":"4d212f41b13cc2bae0a7f25369930a6d","result":{"pages":3,"data":[
          {"SECURITY_CODE":"600000","SECURITY_NAME_ABBR":"浦发银行","BONUS_IT_RATIO":null,
           "BONUS_RATIO":null,"PRETAX_BONUS_RMB":4.2,"EQUITY_RECORD_DATE":"2026-07-15 00:00:00",
           "EX_DIVIDEND_DATE":"2026-07-16 00:00:00","REPORT_DATE":"2025-12-31 00:00:00",
           "ASSIGN_PROGRESS":"实施分配","IMPL_PLAN_PROFILE":"10派4.20元(含税,扣税后3.78元)"},
          {"SECURITY_CODE":"600000","BONUS_IT_RATIO":3,"BONUS_RATIO":null,"PRETAX_BONUS_RMB":2,
           "EQUITY_RECORD_DATE":"2017-05-24 00:00:00","EX_DIVIDEND_DATE":"2017-05-25 00:00:00",
           "REPORT_DATE":"2016-12-31 00:00:00","ASSIGN_PROGRESS":"实施分配",
           "IMPL_PLAN_PROFILE":"10转3.00派2.00元(含税,扣税后1.80元)"},
          {"SECURITY_CODE":"600000","PRETAX_BONUS_RMB":null,"REPORT_DATE":"2015-12-31 00:00:00"}
        ],"count":3},"success":true,"message":"ok","code":0}
    """.trimIndent()

    @Test
    fun cashDividendIsConvertedFromPerTenSharesToPerShare() {
        val records = parseCnDividendResponse(dividendBody)

        assertEquals(2, records.size)
        val latest = records.first()
        assertEquals(0.42, latest.cashDividendPerShare!!, 1e-9)
        assertEquals("2026/07/16", latest.exDividendDate)
        assertEquals("2026/07/15", latest.recordDate)
        assertEquals("2025/12/31", latest.reportDate)
        assertEquals("实施分配", latest.planProgress)
        assertTrue(latest.hasCash)
    }

    @Test
    fun stockBonusRatioIsAlsoConvertedPerShare() {
        val stockRecord = parseCnDividendResponse(dividendBody)[1]

        assertEquals(0.3, stockRecord.stockDividendPerShare!!, 1e-9)
        assertEquals(0.2, stockRecord.cashDividendPerShare!!, 1e-9)
        assertTrue(stockRecord.hasStock)
    }

    @Test
    fun rowsWithoutAmountsOrDatesAreSkipped() {
        assertTrue(parseCnDividendResponse("""{"result":null}""").isEmpty())
        assertTrue(parseCnDividendResponse("""{"result":{"data":null}}""").isEmpty())
        assertTrue(parseCnDividendResponse("garbage").isEmpty())
        assertTrue(parseCnDividendResponse("").isEmpty())
    }

    @Test
    fun latestCashRecordPrefersTheMostRecentPastExDate() {
        val records = parseCnDividendResponse(dividendBody)

        val recent = latestCnCashDividendRecord(records, LocalDate.of(2026, 11, 6))
        assertEquals("2026/07/16", recent?.exDividendDate)

        val older = latestCnCashDividendRecord(records, LocalDate.of(2026, 7, 1))
        assertEquals("2017/05/25", older?.exDividendDate)

        // When every plan is still ahead of today, the newest announced one wins.
        val future = latestCnCashDividendRecord(records, LocalDate.of(2016, 1, 1))
        assertEquals("2026/07/16", future?.exDividendDate)
        assertNull(latestCnCashDividendRecord(emptyList()))
    }

    @Test
    fun latestStockRecordPicksTheOnlyStockPlan() {
        val records = parseCnDividendResponse(dividendBody)

        assertEquals("2017/05/25", latestCnStockDividendRecord(records)?.exDividendDate)
        assertNull(latestCnStockDividendRecord(emptyList()))
    }

    @Test
    fun dividendUrlOnlyAcceptsSixDigitCodes() {
        val url = cnDividendUrl("600000")
        assertTrue(url!!.contains("reportName=RPT_SHAREBONUS_DET"))
        assertTrue(url.contains("filter=(SECURITY_CODE%3D%22600000%22)"))
        assertTrue(url.contains("sortColumns=REPORT_DATE&sortTypes=-1"))
        assertNull(cnDividendUrl("AAPL"))
        assertNull(cnDividendUrl(""))
    }

    @Test
    fun timestampsNormaliseToSlashDatesAndJunkBecomesNull() {
        assertEquals("2026/07/16", normalizeCnDividendDate("2026-07-16 00:00:00"))
        assertNull(normalizeCnDividendDate(null))
        assertNull(normalizeCnDividendDate(""))
        assertNull(normalizeCnDividendDate("not-a-date"))
    }
}
