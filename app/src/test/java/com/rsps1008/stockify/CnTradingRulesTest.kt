package com.rsps1008.stockify

import com.rsps1008.stockify.data.CnTradingRules
import com.rsps1008.stockify.data.StockMarket
import com.rsps1008.stockify.data.StockTransaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class CnTradingRulesTest {

    private val settings = CnTradingRules.FeeSettings(
        commissionRate = CnTradingRules.DEFAULT_COMMISSION_RATE,
        minimumCommission = CnTradingRules.DEFAULT_MINIMUM_COMMISSION
    )

    private fun dateMillis(year: Int, month: Int, day: Int): Long =
        LocalDate.of(year, month, day)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

    private var nextId = 1

    private fun buy(
        code: String = "600000",
        accountId: Int = 1,
        date: Long,
        shares: Double
    ) = StockTransaction(
        id = nextId++,
        stockCode = code,
        market = StockMarket.CN,
        accountId = accountId,
        date = date,
        recordTime = date,
        type = "買進",
        buyPrice = 10.0,
        buyShares = shares,
        expense = 10.0 * shares
    )

    private fun sell(
        code: String = "600000",
        accountId: Int = 1,
        date: Long,
        shares: Double,
        id: Int = 0
    ) = StockTransaction(
        id = id,
        stockCode = code,
        market = StockMarket.CN,
        accountId = accountId,
        date = date,
        recordTime = date,
        type = "賣出",
        sellPrice = 11.0,
        sellShares = shares,
        income = 11.0 * shares
    )

    @Test
    fun commissionUsesMinimumFiveYuan() {
        assertEquals(5.0, CnTradingRules.commission(10_000.0, settings), 1e-9)
        assertEquals(250.0, CnTradingRules.commission(1_000_000.0, settings), 1e-9)
        assertEquals(0.0, CnTradingRules.commission(0.0, settings), 1e-9)
    }

    @Test
    fun buyFeeAddsCommissionAndTransferFeeOnly() {
        // 10,000 CNY: minimum commission 5 + transfer fee 0.1
        assertEquals(5.1, CnTradingRules.buyFee(10_000.0, settings), 1e-9)
    }

    @Test
    fun sellFeeAndStampDutyApplyToSellsOnly() {
        assertEquals(5.0, CnTradingRules.stampDuty(10_000.0), 1e-9)
        assertEquals(5.1, CnTradingRules.sellFee(10_000.0, settings), 1e-9)
        // 10.1 = commission 5.1 + stamp duty 5.0
        assertEquals(10.1, CnTradingRules.exitCost(10_000.0, settings), 1e-9)
        // Buy side has no stamp duty.
        assertFalse(CnTradingRules.buyFee(10_000.0, settings) == CnTradingRules.exitCost(10_000.0, settings))
    }

    @Test
    fun priceLimitRateDependsOnBoardAndStStatus() {
        assertEquals(0.10, CnTradingRules.priceLimitRate("600000", "浦發銀行"), 1e-9)
        assertEquals(0.10, CnTradingRules.priceLimitRate("000001", "平安銀行"), 1e-9)
        assertEquals(0.20, CnTradingRules.priceLimitRate("300750", "寧德時代"), 1e-9)
        assertEquals(0.20, CnTradingRules.priceLimitRate("301001", "凱淳股份"), 1e-9)
        assertEquals(0.20, CnTradingRules.priceLimitRate("688981", "中芯國際"), 1e-9)
        assertEquals(0.30, CnTradingRules.priceLimitRate("830799", "艾融軟件"), 1e-9)
        assertEquals(0.30, CnTradingRules.priceLimitRate("430047", "諾思蘭德"), 1e-9)
        assertEquals(0.30, CnTradingRules.priceLimitRate("920085", "N森峰"), 1e-9)
        assertEquals(0.05, CnTradingRules.priceLimitRate("600001", "ST明誠"), 1e-9)
        assertEquals(0.05, CnTradingRules.priceLimitRate("600001", "*ST明誠"), 1e-9)
    }

    @Test
    fun priceLimitWarningOnlyOutsideTheBand() {
        assertNull(CnTradingRules.priceLimitWarning(10.5, 10.0, "600000", "浦發銀行"))
        assertNull(CnTradingRules.priceLimitWarning(11.0, 10.0, "600000", "浦發銀行"))
        assertNotNull(CnTradingRules.priceLimitWarning(11.5, 10.0, "600000", "浦發銀行"))
        assertNotNull(CnTradingRules.priceLimitWarning(8.9, 10.0, "600000", "浦發銀行"))
        // Growth board allows 20%, so 11.5 is still inside the band.
        assertNull(CnTradingRules.priceLimitWarning(11.5, 10.0, "300750", "寧德時代"))
        // No previous close means no hint.
        assertNull(CnTradingRules.priceLimitWarning(11.5, 0.0, "600000", "浦發銀行"))
    }

    @Test
    fun shareStepperLotSizeIsOneHundredShares() {
        assertTrue(CnTradingRules.isWholeLot(100.0))
        assertTrue(CnTradingRules.isWholeLot(300.0))
        assertFalse(CnTradingRules.isWholeLot(150.0))
        assertFalse(CnTradingRules.isWholeLot(0.0))
        assertEquals(100.0, CnTradingRules.LOT_SIZE, 0.0)
    }

    @Test
    fun t1OnlyAllowsSharesBoughtOnEarlierDays() {
        val yesterday = dateMillis(2026, 11, 5)
        val today = dateMillis(2026, 11, 6)
        val transactions = listOf(
            buy(date = yesterday, shares = 300.0),
            buy(date = today, shares = 500.0)
        )

        assertEquals(300.0, CnTradingRules.availableSellShares(transactions, today), 1e-9)
        assertNull(
            CnTradingRules.validateT1Sell(sell(date = today, shares = 300.0), transactions)
        )
        assertNotNull(
            CnTradingRules.validateT1Sell(sell(date = today, shares = 301.0), transactions)
        )
    }

    @Test
    fun t1CountsTheSameDayBeforeTheSell() {
        val yesterday = dateMillis(2026, 11, 5)
        val today = dateMillis(2026, 11, 6)
        val transactions = listOf(
            buy(date = yesterday, shares = 300.0),
            sell(date = today, shares = 200.0, id = 7)
        )
        // The 200 shares sold earlier today already left the settled position.
        assertEquals(100.0, CnTradingRules.availableSellShares(transactions, today), 1e-9)
        assertNull(CnTradingRules.validateT1Sell(sell(date = today, shares = 100.0, id = 8), transactions))
        assertNotNull(CnTradingRules.validateT1Sell(sell(date = today, shares = 101.0, id = 8), transactions))
    }

    @Test
    fun t1IsScopedToStockAndAccount() {
        val yesterday = dateMillis(2026, 11, 5)
        val today = dateMillis(2026, 11, 6)
        val transactions = listOf(
            buy(code = "600000", accountId = 1, date = yesterday, shares = 300.0),
            buy(code = "000001", accountId = 1, date = yesterday, shares = 300.0),
            buy(code = "600000", accountId = 2, date = yesterday, shares = 300.0)
        )
        // Account 3 has no settled shares even though other scopes do.
        assertNotNull(
            CnTradingRules.validateT1Sell(
                sell(accountId = 3, date = today, shares = 100.0),
                transactions
            )
        )
    }

    @Test
    fun t1DoesNotApplyToTaiwanOrUsSells() {
        val yesterday = dateMillis(2026, 11, 5)
        val today = dateMillis(2026, 11, 6)
        val twBuy = buy(date = yesterday, shares = 100.0).copy(market = StockMarket.TW)
        val twSell = sell(date = today, shares = 100.0).copy(market = StockMarket.TW)
        val usSell = sell(date = today, shares = 100.0).copy(market = StockMarket.US, stockCode = "AAPL")

        assertNull(CnTradingRules.validateT1Sell(twSell, listOf(twBuy)))
        assertNull(CnTradingRules.validateT1Sell(usSell, listOf(twBuy)))
    }

    @Test
    fun t1RejectsSellingSharesBoughtTheSameDay() {
        val today = dateMillis(2026, 11, 6)
        val transactions = listOf(buy(date = today, shares = 300.0))
        val error = CnTradingRules.validateT1Sell(sell(date = today, shares = 100.0), transactions)

        assertNotNull(error)
        assertTrue(error!!.contains("T+1"))
    }
}
