package com.rsps1008.stockify.data

import android.annotation.SuppressLint
import kotlinx.serialization.Serializable

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class RealtimeStockInfo(
    val currentPrice: Double,
    val change: Double,
    val changePercent: Double,
    val lastUpdated: Long = System.currentTimeMillis(),
    val limitState: LimitState = LimitState.NONE,
    // A-share exchanges publish the exact daily limit prices; a source that does not
    // report them leaves these null and the UI falls back to the computed band.
    val limitUpPrice: Double? = null,
    val limitDownPrice: Double? = null
)

enum class LimitState {
    LIMIT_UP,
    LIMIT_DOWN,
    NONE
}
