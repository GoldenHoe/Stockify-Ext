package com.rsps1008.stockify

import com.rsps1008.stockify.data.CN_SUGGEST_TOKEN
import com.rsps1008.stockify.data.StockExchange
import com.rsps1008.stockify.data.StockMarket
import com.rsps1008.stockify.data.cnSuggestUrl
import com.rsps1008.stockify.data.parseEastmoneySuggest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CnStockSuggestParsingTest {

    // Trimmed live suggest responses; the Simplified names are what Eastmoney returns.
    private val body = """
        {"QuotationCodeTable":{"Data":[
          {"Code":"000001","Name":"平安银行","Classify":"AStock","MarketType":"2","SecurityTypeName":"深A","MktNum":"0","QuoteID":"0.000001"},
          {"Code":"000001","Name":"上证指数","Classify":"Index","MarketType":"1","SecurityTypeName":"指数","MktNum":"1","QuoteID":"1.000001"},
          {"Code":"000001","Name":"华夏成长混合","Classify":"OTCFUND","MarketType":"6","SecurityTypeName":"基金","MktNum":"150","QuoteID":"150.000001"},
          {"Code":"601318","Name":"中国平安","Classify":"AStock","MarketType":"1","SecurityTypeName":"沪A","MktNum":"1","QuoteID":"1.601318"},
          {"Code":"835688","Name":"平安环保","Classify":"NEEQ","MarketType":"_TB","SecurityTypeName":"三板","MktNum":"0","QuoteID":"0.835688"},
          {"Code":"920085","Name":"N森峰","Classify":"AStock","MarketType":"2","SecurityTypeName":"京A","MktNum":"0","QuoteID":"0.920085"}
        ],"Status":0,"Message":"成功","TotalCount":6}}
    """.trimIndent()

    @Test
    fun onlyAShareRowsBecomeCnStocks() {
        val stocks = parseEastmoneySuggest(body, limit = 10)

        assertEquals(listOf("000001", "601318", "920085"), stocks.map { it.code })
        assertTrue(stocks.all { StockMarket.isCn(it.market) })
    }

    @Test
    fun exchangesComeFromTheCodeAndNamesAreStoredAsTraditional() {
        val byCode = parseEastmoneySuggest(body, limit = 10).associateBy { it.code }

        assertEquals(StockExchange.SZ, byCode.getValue("000001").exchange)
        assertEquals(StockExchange.SH, byCode.getValue("601318").exchange)
        assertEquals(StockExchange.BJ, byCode.getValue("920085").exchange)
        assertEquals("中國平安", byCode.getValue("601318").name)
        assertEquals("股票", byCode.getValue("601318").stockType)
    }

    @Test
    fun limitAndMalformedPayloadsAreHandled() {
        assertEquals(1, parseEastmoneySuggest(body, limit = 1).size)
        assertTrue(parseEastmoneySuggest(body, limit = 0).isEmpty())
        assertTrue(parseEastmoneySuggest("", limit = 5).isEmpty())
        assertTrue(parseEastmoneySuggest("garbage", limit = 5).isEmpty())
        assertTrue(parseEastmoneySuggest("""{"QuotationCodeTable":{"Data":null}}""", limit = 5).isEmpty())
        assertTrue(
            parseEastmoneySuggest(
                """{"QuotationCodeTable":{"Data":[{"Code":"ABC","Name":"x","Classify":"AStock"}]}}""",
                limit = 5
            ).isEmpty()
        )
    }

    @Test
    fun suggestUrlEncodesTheQueryAndKeepsTheToken() {
        val url = cnSuggestUrl("茅台")
        assertTrue(url.startsWith("https://searchapi.eastmoney.com/api/suggest/get?input="))
        assertTrue(url.contains("type=14"))
        assertTrue(url.contains("token=$CN_SUGGEST_TOKEN"))
        assertTrue(url.contains("count=10"))
        // The Chinese query must be percent encoded, never sent raw.
        assertTrue(!url.contains("茅台"))
    }
}
