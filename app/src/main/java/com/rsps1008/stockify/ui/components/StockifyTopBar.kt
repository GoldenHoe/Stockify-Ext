package com.rsps1008.stockify.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rsps1008.stockify.R
import com.rsps1008.stockify.ui.text.Text
import com.rsps1008.stockify.ui.text.localized

/**
 * Shared Material 3 top bar. Every screen states its function here so the
 * navigation bar no longer has to guess from a logo-only header.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockifyTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        modifier = modifier,
        title = {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        navigationIcon = {
            when {
                onBack != null -> IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "返回".localized()
                    )
                }

                navigationIcon != null -> navigationIcon()
            }
        },
        actions = actions
    )
}

/** Home variant: keeps the wordmark and states the page function under it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockifyBrandTopBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        modifier = modifier,
        title = {
            Column {
                Image(
                    painter = painterResource(R.drawable.stockify),
                    contentDescription = null,
                    modifier = Modifier.height(22.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        },
        navigationIcon = { navigationIcon?.invoke() },
        actions = actions
    )
}
