package com.rsps1008.stockify

import com.rsps1008.stockify.data.HomeDisplayMode
import com.rsps1008.stockify.data.StockExchange
import com.rsps1008.stockify.data.StockHistoryPoint
import com.rsps1008.stockify.data.StockMarket
import com.rsps1008.stockify.data.CN_STOCKS_ASSET_NAME
import com.rsps1008.stockify.data.CnTradingRules
import com.rsps1008.stockify.data.eastmoneySecId
import com.rsps1008.stockify.data.isChinaMarketOpen
import com.rsps1008.stockify.data.parseEastmoneyBatchQuotes
import com.rsps1008.stockify.data.parseEastmoneyKlineResponse
import com.rsps1008.stockify.data.parseEastmoneySingleQuote
import com.rsps1008.stockify.data.parseEastmoneyStockListPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId

class CnStockDataParsingTest {

    private val shanghai: ZoneId = ZoneId.of("Asia/Shanghai")

    @Test
    fun marketModelAcceptsExplicitCnButNeverInfersIt() {
        assertEquals(StockMarket.CN, StockMarket.normalize("cn"))
        assertEquals(StockMarket.CN, StockMarket.normalize(" CN "))
        assertTrue(StockMarket.isCn("CN"))
        assertFalse(StockMarket.isCn("TW"))
        // Six digit codes are shared with TW ETFs, so CN can never be inferred.
        assertEquals(StockMarket.TW, StockMarket.inferFromCode("600000"))
        assertEquals(StockMarket.TW, StockMarket.inferFromCode("00981A"))
        assertTrue(StockMarket.isCodeConsistent(StockMarket.CN, "600000"))
        assertFalse(StockMarket.isCodeConsistent(StockMarket.CN, "AAPL"))
        assertTrue(StockMarket.isCodeConsistent(StockMarket.TW, "2330"))
        assertFalse(StockMarket.isCodeConsistent(StockMarket.TW, "AAPL"))
        assertTrue(StockMarket.isCodeConsistent(StockMarket.US, "BRK.B"))
        assertFalse(StockMarket.isCodeConsistent(StockMarket.US, "600000"))
    }

    @Test
    fun exchangeCodesCoverShanghaiShenzhenAndBeijing() {
        assertEquals(StockExchange.SH, StockExchange.normalize("滬市"))
        assertEquals(StockExchange.SZ, StockExchange.normalize("深市"))
        assertEquals(StockExchange.BJ, StockExchange.normalize("北交所"))
        assertEquals(StockExchange.LISTED, StockExchange.normalize("上市"))
        assertEquals(StockExchange.OTC, StockExchange.normalize("上櫃"))
        assertEquals(StockExchange.EMERGING, StockExchange.normalize("興櫃"))
        assertTrue(StockExchange.isCn(StockExchange.BJ))
        assertFalse(StockExchange.isCn(StockExchange.LISTED))
        assertEquals(StockExchange.SH, StockExchange.fromCnCode("600000"))
        assertEquals(StockExchange.SH, StockExchange.fromCnCode("688981"))
        assertEquals(StockExchange.SZ, StockExchange.fromCnCode("000001"))
        assertEquals(StockExchange.SZ, StockExchange.fromCnCode("300750"))
        assertEquals(StockExchange.BJ, StockExchange.fromCnCode("920085"))
        assertEquals(StockExchange.BJ, StockExchange.fromCnCode("430047"))
    }

    @Test
    fun homeDisplayModeIncludesChina() {
        assertEquals(HomeDisplayMode.CN, HomeDisplayMode.normalize("cn"))
        assertEquals("純A股", HomeDisplayMode.label(HomeDisplayMode.CN))
        assertEquals(HomeDisplayMode.COMBINED, HomeDisplayMode.normalize("unknown"))
    }

    @Test
    fun batchQuotesAreScaledByOneHundred() {
        val body = """
            {"rc":0,"data":{"total":2,"diff":[
              {"f2":1005,"f3":182,"f4":18,"f12":"600000","f13":1,"f14":"浦發銀行","f15":1007,"f16":984,"f17":991,"f18":987},
              {"f2":1183,"f3":163,"f4":19,"f12":"000001","f13":0,"f14":"平安銀行","f15":1184,"f16":1157,"f17":1162,"f18":1164}
            ]}}
        """.trimIndent()

        val quotes = parseEastmoneyBatchQuotes(body)

        assertEquals(2, quotes.size)
        val shanghai = quotes.getValue("600000")
        assertEquals(10.05, shanghai.currentPrice, 1e-9)
        assertEquals(1.82, shanghai.changePercent, 1e-9)
        assertEquals(0.18, shanghai.change, 1e-9)
        assertEquals(11.83, quotes.getValue("000001").currentPrice, 1e-9)
    }

    @Test
    fun batchQuotesSkipSuspendedAndMalformedRows() {
        val body = """
            {"data":{"diff":[
              {"f2":"-","f12":"600001","f13":1},
              {"f2":null,"f12":"600002","f13":1},
              {"f12":"600003","f13":1},
              {"f2":0,"f12":"600004","f13":1},
              {"f2":1005,"f12":"600005","f13":1}
            ]}}
        """.trimIndent()

        val quotes = parseEastmoneyBatchQuotes(body)

        assertEquals(setOf("600005"), quotes.keys)
        assertEquals(10.05, quotes.getValue("600005").currentPrice, 1e-9)
    }

    @Test
    fun batchQuotesAcceptObjectDiffAndRejectMissingData() {
        val objectDiff = """{"data":{"diff":{"0":{"f2":1005,"f12":"600000"}}}}"""
        assertEquals(1, parseEastmoneyBatchQuotes(objectDiff).size)

        assertTrue(parseEastmoneyBatchQuotes("""{"data":null}""").isEmpty())
        assertTrue(parseEastmoneyBatchQuotes("not json").isEmpty())
        assertTrue(parseEastmoneyBatchQuotes("").isEmpty())
    }

    @Test
    fun singleQuoteUsesScaledFields() {
        val body = """
            {"rc":0,"data":{"f43":1005,"f57":"600000","f58":"浦發銀行","f60":987,"f168":18,"f169":18,"f170":182}}
        """.trimIndent()

        val quote = parseEastmoneySingleQuote(body, "600000")

        assertEquals(10.05, quote!!.currentPrice, 1e-9)
        assertEquals(0.18, quote.change, 1e-9)
        assertEquals(1.82, quote.changePercent, 1e-9)
        assertNull(parseEastmoneySingleQuote("""{"data":{"f43":"-"}}""", "600000"))
        assertNull(parseEastmoneySingleQuote("""{"data":null}""", "600000"))
    }

    @Test
    fun secIdUsesShanghaiMarketOneAndShenzhenBeijingMarketZero() {
        assertEquals("1.600000", eastmoneySecId("600000"))
        assertEquals("1.688981", eastmoneySecId("688981"))
        assertEquals("0.000001", eastmoneySecId("000001"))
        assertEquals("0.300750", eastmoneySecId("300750"))
        assertEquals("0.920085", eastmoneySecId("920085"))
        assertNull(eastmoneySecId(""))
    }

    @Test
    fun klineParsingUsesClosePriceAtIndexTwo() {
        val body = """
            {"data":{"code":"600000","market":1,"klines":[
              "2026-11-04,10.05,10.09,10.23,10.05,824086,835024728.00,1.78",
              "2026-11-05,10.09,9.87,10.10,9.82,629078,623425673.00,2.78",
              "2026-11-06,9.91,10.05,10.07,9.84,584009,581529080.00,2.33"
            ]}}
        """.trimIndent()

        val points = parseEastmoneyKlineResponse(body)

        assertEquals(
            listOf(
                StockHistoryPoint("2026-11-04", 10.09),
                StockHistoryPoint("2026-11-05", 9.87),
                StockHistoryPoint("2026-11-06", 10.05)
            ),
            points
        )
    }

    @Test
    fun klineParsingSkipsInvalidRows() {
        val body = """
            {"data":{"klines":[
              "not-a-date,1,2,3,4,5,6,7",
              "2026-11-04,1,-,3,4,5,6,7",
              "2026-11-05,1,9.87",
              "2026-11-06,1,10.05,10.07,9.84"
            ]}}
        """.trimIndent()

        assertEquals(
            listOf(
                StockHistoryPoint("2026-11-05", 9.87),
                StockHistoryPoint("2026-11-06", 10.05)
            ),
            parseEastmoneyKlineResponse(body)
        )
        assertTrue(parseEastmoneyKlineResponse("""{"data":null}""").isEmpty())
    }

    @Test
    fun stockListPageParsesCodesNamesAndExchanges() {
        val body = """
            {"rc":0,"data":{"total":5927,"diff":[
              {"f12":"920085","f13":0,"f14":"N森峰"},
              {"f12":"301697","f13":0,"f14":"貝特利"},
              {"f12":"600000","f13":1,"f14":"浦發銀行"}
            ]}}
        """.trimIndent()

        val page = parseEastmoneyStockListPage(body)

        assertEquals(5927, page.total)
        assertEquals(3, page.stocks.size)
        val byCode = page.stocks.associateBy { it.code }
        assertEquals(StockMarket.CN, byCode.getValue("600000").market)
        assertEquals(StockExchange.SH, byCode.getValue("600000").exchange)
        assertEquals(StockExchange.SZ, byCode.getValue("301697").exchange)
        assertEquals(StockExchange.BJ, byCode.getValue("920085").exchange)
    }

    @Test
    fun stockListPageRejectsBlankRows() {
        val body = """{"data":{"total":2,"diff":[{"f12":"","f14":"x"},{"f12":"600000","f14":""}]}}"""
        assertTrue(parseEastmoneyStockListPage(body).stocks.isEmpty())
        assertTrue(parseEastmoneyStockListPage("garbage").stocks.isEmpty())
    }

    @Test
    fun chinaSessionIsSplitByTheLunchBreak() {
        fun at(day: DayOfWeek, hour: Int, minute: Int) =
            LocalDateTime.of(2026, 11, 2, hour, minute).with(day).atZone(shanghai)

        assertTrue(isChinaMarketOpen(at(DayOfWeek.MONDAY, 9, 30)))
        assertTrue(isChinaMarketOpen(at(DayOfWeek.MONDAY, 10, 0)))
        assertTrue(isChinaMarketOpen(at(DayOfWeek.MONDAY, 11, 29)))
        assertFalse(isChinaMarketOpen(at(DayOfWeek.MONDAY, 11, 30)))
        assertFalse(isChinaMarketOpen(at(DayOfWeek.MONDAY, 12, 0)))
        assertTrue(isChinaMarketOpen(at(DayOfWeek.MONDAY, 13, 0)))
        assertTrue(isChinaMarketOpen(at(DayOfWeek.MONDAY, 14, 59)))
        assertFalse(isChinaMarketOpen(at(DayOfWeek.MONDAY, 15, 0)))
        assertFalse(isChinaMarketOpen(at(DayOfWeek.SATURDAY, 10, 0)))
        assertFalse(isChinaMarketOpen(at(DayOfWeek.SUNDAY, 14, 0)))
    }

    @Test
    fun cnFeeDefaultsMatchTheDocumentedRules() {
        assertEquals(0.00025, CnTradingRules.DEFAULT_COMMISSION_RATE, 1e-12)
        assertEquals(5.0, CnTradingRules.DEFAULT_MINIMUM_COMMISSION, 1e-12)
        assertEquals(0.0005, CnTradingRules.STAMP_DUTY_RATE, 1e-12)
        assertEquals(0.00001, CnTradingRules.TRANSFER_FEE_RATE, 1e-12)
    }

    @Test
    fun cnAssetNameIsBundledSeparately() {
        assertEquals("cn_stocks.json", CN_STOCKS_ASSET_NAME)
    }

    @Test
    fun bundledCnAssetUsesTheExistingStockSchema() {
        val file = java.io.File("src/main/assets/$CN_STOCKS_ASSET_NAME")
        assertTrue("bundled asset missing: ${file.absolutePath}", file.exists())

        val rows = kotlinx.serialization.json.Json.parseToJsonElement(
            file.readText(Charsets.UTF_8)
        ).let { it as kotlinx.serialization.json.JsonArray }

        assertTrue(rows.size > 5_000)
        val codes = mutableSetOf<String>()
        for (element in rows) {
            val row = element as kotlinx.serialization.json.JsonObject
            assertEquals(
                setOf("name", "code", "market", "industry", "stockType"),
                row.keys
            )
            val code = (row["code"] as kotlinx.serialization.json.JsonPrimitive).content
            val market = (row["market"] as kotlinx.serialization.json.JsonPrimitive).content
            assertTrue("unexpected code $code", Regex("\\d{6}").matches(code))
            assertTrue("unexpected market $market", StockExchange.isCn(StockExchange.normalize(market)))
            assertTrue("duplicate code $code", codes.add(code))
        }
    }
}
