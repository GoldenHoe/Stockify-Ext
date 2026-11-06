package com.rsps1008.stockify.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rsps1008.stockify.StockifyApplication
import com.rsps1008.stockify.data.CnMarketIndexInfo
import com.rsps1008.stockify.ui.text.Text
import com.rsps1008.stockify.ui.theme.StockifyAppTheme
import java.util.Locale
import kotlin.math.abs

/**
 * A-share index strip for the CN home mode: 上證指數 / 深證成指 / 創業板指.
 *
 * Kept in its own file so the home screen (owned by the navigation/header work)
 * only needs one call site. It mirrors the Taiwan weighted index strip and reads
 * the shared [com.rsps1008.stockify.data.CnMarketIndexService] StateFlow.
 */
@Composable
fun CnMarketIndexStrip() {
    val application = LocalContext.current.applicationContext as? StockifyApplication
        ?: return
    val indexes by application.cnMarketIndexService.indexes.collectAsState()
    // The shared service is also refreshed by the polling loop, but the strip can be
    // the first CN surface the user sees (no holdings yet), so pull once when empty.
    LaunchedEffect(indexes.isEmpty()) {
        if (indexes.isEmpty()) {
            application.cnMarketIndexService.refreshOnce()
        }
    }
    CnMarketIndexSection(indexes = indexes)
}

/** Stateless section so the strip can be previewed or fed from a ViewModel. */
@Composable
fun CnMarketIndexSection(
    indexes: List<CnMarketIndexInfo>,
    modifier: Modifier = Modifier
) {
    if (indexes.isEmpty()) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 6.dp, start = 4.dp, end = 4.dp, bottom = 0.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        indexes.forEach { index ->
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = shortIndexLabel(index),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatIndexValue(index),
                    style = MaterialTheme.typography.labelMedium,
                    color = indexValueColor(index),
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatIndexChange(index),
                    style = MaterialTheme.typography.labelSmall,
                    color = indexValueColor(index),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun indexValueColor(index: CnMarketIndexInfo): Color = when {
    index.change > 0.0 -> StockifyAppTheme.stockColors.gain
    index.change < 0.0 -> StockifyAppTheme.stockColors.loss
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** Eastmoney reports the full 上證指數 name; the strip uses the common short labels. */
internal fun shortIndexLabel(index: CnMarketIndexInfo): String = when (index.code) {
    "000001" -> "上證指數"
    "399001" -> "深證成指"
    "399006" -> "創業板指"
    else -> index.name
}

internal fun formatIndexValue(index: CnMarketIndexInfo): String =
    String.format(Locale.US, "%,.2f", index.current)

internal fun formatIndexChange(index: CnMarketIndexInfo): String {
    if (index.change == 0.0) return "0.00  (0.00%)"
    val arrow = if (index.change > 0.0) "▴" else "▾"
    return "$arrow${String.format(Locale.US, "%.2f", abs(index.change))}" +
        "  (${String.format(Locale.US, "%.2f%%", abs(index.changePercent))})"
}
