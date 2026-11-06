package com.rsps1008.stockify.data

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class UsdTwdExchangeRateService(
    private val settingsDataStore: SettingsDataStore
) {
    private companion object {
        private const val REFRESH_INTERVAL_MS = 24 * 60 * 60 * 1000L
    }

    private data class ExchangeRates(val usdToTwd: Double?, val cnyToTwd: Double?)

    private val client = HttpClient(CIO) {
        engine {
            requestTimeout = 5000
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private var refreshJob: Job? = null

    private val _usdToTwdRate = MutableStateFlow(32.0)
    val usdToTwdRate: StateFlow<Double> = _usdToTwdRate.asStateFlow()

    private val _cnyToTwdRate = MutableStateFlow(DEFAULT_CNY_TO_TWD_RATE)
    val cnyToTwdRate: StateFlow<Double> = _cnyToTwdRate.asStateFlow()

    init {
        scope.launch {
            _usdToTwdRate.value = settingsDataStore.usdToTwdRateFlow.first()
        }
        scope.launch {
            _cnyToTwdRate.value = settingsDataStore.cnyToTwdRateFlow.first()
        }
        startRefreshing()
    }

    fun startRefreshing() {
        refreshJob?.cancel()
        refreshJob = scope.launch {
            refreshOnce()
            while (true) {
                delay(REFRESH_INTERVAL_MS)
                refreshOnce()
            }
        }
    }

    suspend fun refreshOnce() {
        val rates = fetchRates()
        val validUsd = rates.usdToTwd?.takeIf { it.isFinitePositive() }
        if (validUsd != null) {
            _usdToTwdRate.value = validUsd
            settingsDataStore.setUsdToTwdRate(validUsd)
        }
        val validCny = rates.cnyToTwd?.takeIf { it.isFinitePositive() }
        if (validCny != null) {
            _cnyToTwdRate.value = validCny
            settingsDataStore.setCnyToTwdRate(validCny)
        }
    }

    private suspend fun fetchRates(): ExchangeRates {
        return try {
            val jsonText = client.get("https://open.er-api.com/v6/latest/USD").bodyAsText()
            val root = Json.parseToJsonElement(jsonText).jsonObject
            if (root["result"]?.jsonPrimitive?.content != "success") {
                return ExchangeRates(null, null)
            }
            val rates = root["rates"]?.jsonObject
            val twd = rates?.get("TWD")?.jsonPrimitive?.doubleOrNull
            val cny = rates?.get("CNY")?.jsonPrimitive?.doubleOrNull
            // The API is USD based, so CNY/TWD is TWD per CNY.
            val cnyToTwd = if (twd != null && cny != null && cny > 0.0) twd / cny else null
            ExchangeRates(twd, cnyToTwd)
        } catch (e: Exception) {
            Log.e("UsdTwdExchangeRateService", "Failed to fetch exchange rates: ${e.message}", e)
            ExchangeRates(null, null)
        }
    }
}
