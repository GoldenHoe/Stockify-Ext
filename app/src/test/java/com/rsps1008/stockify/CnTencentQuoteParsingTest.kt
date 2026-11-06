package com.rsps1008.stockify

import com.rsps1008.stockify.data.CnTradingRules
import com.rsps1008.stockify.data.parseTencentQuotes
import com.rsps1008.stockify.data.tencentSymbol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CnTencentQuoteParsingTest {

    /**
     * Builds one `v_sh600000="..."` line with the verified field positions:
     * 2 code, 3 price, 4 previous close, 31 change, 32 change percent,
     * 47 limit up, 48 limit down.
     */
    private fun tencentLine(
        symbol: String,
        flag: String,
        code: String,
        price: String,
        previousClose: String,
        change: String,
        changePercent: String,
        limitUp: String,
        limitDown: String
    ): String {
        val fields = MutableList(49) { "0" }
        fields[0] = flag
        fields[1] = "NAME"
        fields[2] = code
        fields[3] = price
        fields[4] = previousClose
        fields[30] = "20261106153542"
        fields[31] = change
        fields[32] = changePercent
        fields[47] = limitUp
        fields[48] = limitDown
        return "v_$symbol=\"" + fields.joinToString("~") + "\";"
    }

    private val body = listOf(
        tencentLine("sh600000", "1", "600000", "10.03", "9.87", "0.16", "1.62", "10.86", "8.88"),
        tencentLine("sz000001", "51", "000001", "11.81", "11.64", "0.17", "1.46", "12.80", "10.48")
    ).joinToString("\n")

    @Test
    fun tencentFieldsMapToQuotePricesAndLimits() {
        val quotes = parseTencentQuotes(body)

        assertEquals(setOf("600000", "000001"), quotes.keys)
        val shanghai = quotes.getValue("600000")
        assertEquals(10.03, shanghai.currentPrice, 1e-9)
        assertEquals(0.16, shanghai.change, 1e-9)
        assertEquals(1.62, shanghai.changePercent, 1e-9)
        assertEquals(10.86, shanghai.limitUpPrice!!, 1e-9)
        assertEquals(8.88, shanghai.limitDownPrice!!, 1e-9)

        val shenzhen = quotes.getValue("000001")
        assertEquals(11.81, shenzhen.currentPrice, 1e-9)
        assertEquals(12.80, shenzhen.limitUpPrice!!, 1e-9)
        assertEquals(10.48, shenzhen.limitDownPrice!!, 1e-9)
    }

    @Test
    fun suspendedAndMalformedLinesAreSkipped() {
        val zeroPayload = "v_sz000002=\"51~NAME~000002~0.00~0.00\";"
        val payload = listOf(
            "v_sh600000=\"\";",
            zeroPayload,
            "ignored line without quotes",
            tencentLine("sh600003", "1", "600003", "10.00", "9.50", "0.50", "5.26", "10.45", "8.55")
        ).joinToString("\n")

        val quotes = parseTencentQuotes(payload)

        assertEquals(setOf("600003"), quotes.keys)
        assertEquals(10.45, quotes.getValue("600003").limitUpPrice!!, 1e-9)
        assertTrue(parseTencentQuotes("").isEmpty())
        assertTrue(parseTencentQuotes("garbage").isEmpty())
    }

    @Test
    fun tencentSymbolUsesShanghaiShenzhenAndBeijingPrefixes() {
        assertEquals("sh600000", tencentSymbol("600000"))
        assertEquals("sh688981", tencentSymbol("688981"))
        assertEquals("sz000001", tencentSymbol("000001"))
        assertEquals("sz300750", tencentSymbol("300750"))
        assertEquals("bj920002", tencentSymbol("920002"))
        assertEquals("bj430047", tencentSymbol("430047"))
        assertNull(tencentSymbol(""))
    }

    @Test
    fun computedLimitPricesMatchTheExchangeRounding() {
        val mainBoard = CnTradingRules.computedLimitPrices(9.87, "600000")!!
        assertEquals(8.88, mainBoard.first, 1e-9)
        assertEquals(10.86, mainBoard.second, 1e-9)

        val shenzhen = CnTradingRules.computedLimitPrices(11.64, "000001")!!
        assertEquals(10.48, shenzhen.first, 1e-9)
        assertEquals(12.80, shenzhen.second, 1e-9)

        // 創業板 ±20%, 北交所 ±30%
        val chiNext = CnTradingRules.computedLimitPrices(20.0, "300750")!!
        assertEquals(16.0, chiNext.first, 1e-9)
        assertEquals(24.0, chiNext.second, 1e-9)

        val beijing = CnTradingRules.computedLimitPrices(49.30, "920002")!!
        assertEquals(34.51, beijing.first, 1e-9)
        assertEquals(64.09, beijing.second, 1e-9)

        assertNull(CnTradingRules.computedLimitPrices(0.0, "600000"))
    }

    @Test
    fun limitHintShowsBothQuotePricesAndFallsBackToTheComputedBand() {
        val fromQuote = CnTradingRules.priceLimitHint(
            previousClose = 9.87,
            stockCode = "600000",
            quoteLimitUp = 10.86,
            quoteLimitDown = 8.88
        )
        assertEquals("漲停 10.86／跌停 8.88（前收 9.87，±10%）", fromQuote)

        val computed = CnTradingRules.priceLimitHint(previousClose = 9.87, stockCode = "600000")
        assertEquals(fromQuote, computed)

        // A quote value wins over the computed one when the exchange publishes it.
        val quoted = CnTradingRules.priceLimitHint(
            previousClose = 9.87,
            stockCode = "600000",
            quoteLimitUp = 10.90
        )
        assertEquals("漲停 10.90／跌停 8.88（前收 9.87，±10%）", quoted)

        assertNull(CnTradingRules.priceLimitHint(previousClose = 0.0, stockCode = "600000"))
    }

    @Test
    fun limitWarningUsesQuotePricesWhenPresent() {
        assertNull(
            CnTradingRules.priceLimitWarning(
                price = 10.03,
                previousClose = 9.87,
                stockCode = "600000",
                quoteLimitUp = 10.86,
                quoteLimitDown = 8.88
            )
        )
        assertEquals(
            "價格超出漲跌幅限制（前收盤 9.87，±10% 區間 8.88～10.86）",
            CnTradingRules.priceLimitWarning(
                price = 11.5,
                previousClose = 9.87,
                stockCode = "600000"
            )
        )
    }
}
