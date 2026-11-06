package com.rsps1008.stockify.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.rsps1008.stockify.StockifyApplication
import com.rsps1008.stockify.data.dividend.YahooDividendRepository
import com.rsps1008.stockify.ui.navigation.Screen
import com.rsps1008.stockify.ui.viewmodel.DividendInfoViewModel
import com.rsps1008.stockify.ui.viewmodel.ViewModelFactory
import com.rsps1008.stockify.ui.viewmodel.DividendItemUiState
import com.rsps1008.stockify.ui.text.Text
import com.rsps1008.stockify.ui.components.StockifyTopBar
import com.rsps1008.stockify.ui.text.localized

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DividendInfoScreen(navController: NavController) {
    val application = LocalContext.current.applicationContext as StockifyApplication
    val viewModel: DividendInfoViewModel = viewModel(
        factory = ViewModelFactory(
            stockDao = application.database.stockDao(),
            settingsDataStore = application.settingsDataStore,
            realtimeStockDataService = application.realtimeStockDataService,
            dividendRepository = YahooDividendRepository(application.httpClient),
            cnDividendRepository = com.rsps1008.stockify.data.dividend.CnDividendRepository(application.httpClient),
            exchangeRateService = application.exchangeRateService,
            transactionListRepository = application.transactionListRepository
        )
    )

    val dividendList by viewModel.dividendList.collectAsState()

    Scaffold(
        topBar = {
            StockifyTopBar(
                title = "配息配股",
                onBack = { navController.popBackStack() },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "重新整理".localized())
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(
                items = dividendList,
                key = { item -> "${item.market}:${item.stockCode}" }
            ) { item ->
                DividendInfoCard(
                    item = item,
                    onClick = {
                        navController.navigate(
                            Screen.StockDetail.createRoute(item.stockCode, item.market)
                        )
                    }
                )
            }
        }
    }
}

/** A-share amounts come as per-share values with up to four decimals. */
private val DividendItemUiState.isCnMarket: Boolean
    get() = com.rsps1008.stockify.data.StockMarket.isCn(market)

private fun formatDividendAmount(value: Double): String =
    java.math.BigDecimal.valueOf(value)
        .setScale(4, java.math.RoundingMode.HALF_UP)
        .stripTrailingZeros()
        .toPlainString()

@Composable
fun DividendInfoCard(item: DividendItemUiState, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text(
                    text = "${item.stockName} (${item.stockCode})",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (item.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))

            DividendDetailRow(
                label = "現金股利",
                value = item.cashDividend?.let { dividendPerShareText(item, it, "元") } ?: "無",
                details = buildList {
                    item.cashDividendDate?.let { add("除息日 $it") }
                    if (item.isCnMarket) {
                        item.recordDate?.let { add("股權登記日 $it") }
                        item.planProgress?.takeIf { it.isNotBlank() }?.let { add("方案進度 $it") }
                    }
                    item.lastLocalCashDividend?.let { amount ->
                        val date = item.lastLocalCashDividendDate
                        add(if (date.isNullOrBlank()) "上次領取 $amount" else "上次領取 $amount（$date）")
                    }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 送股可能來自更早的方案（例如 2017 年），標示「最近一次」避免被誤讀成本次配股。
            val stockPlanIsStale = item.stockDividendDate != null &&
                item.cashDividendDate != null &&
                item.stockDividendDate.take(4) < item.cashDividendDate.take(4)
            DividendDetailRow(
                label = "股票股利",
                value = item.stockDividend
                    ?.let { dividendPerShareText(item, it, "股") }
                    ?.let { if (stockPlanIsStale) "最近一次 $it" else it }
                    ?: "無",
                details = buildList {
                    item.stockDividendDate?.let { add("除權日 $it") }
                    item.lastLocalStockDividend?.let { amount ->
                        val date = item.lastLocalStockDividendDate
                        add(if (date.isNullOrBlank()) "上次領取 $amount" else "上次領取 $amount（$date）")
                    }
                }
            )

            if (item.errorMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "錯誤: ${item.errorMessage}",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun DividendDetailRow(
    label: String,
    value: String,
    details: List<String>
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(72.dp)
        )
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
            details.forEach { detail ->
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** A-share per-share values carry at most four decimals; keep them free of float noise. */
private fun dividendPerShareText(item: DividendItemUiState, value: Double, unit: String): String =
    if (item.isCnMarket) {
        "每股 ${formatDividendAmount(value)} $unit"
    } else {
        formatDividendAmount(value)
    }
