package com.rsps1008.stockify.ui.text

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * Per-app language switcher for the two Chinese scripts.
 *
 * The choice is persisted by AppCompat (`autoStoreLocales` in the manifest) and
 * by the framework on Android 13+, so no extra DataStore entry is needed.
 */
object AppLanguage {
    const val FOLLOW_SYSTEM = ""
    const val SIMPLIFIED = "zh-CN"
    const val TRADITIONAL = "zh-TW"

    fun currentTag(): String {
        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.isEmpty) return FOLLOW_SYSTEM
        val tag = locales.toLanguageTags()
        return when {
            tag.startsWith(TRADITIONAL, ignoreCase = true) -> TRADITIONAL
            tag.startsWith(SIMPLIFIED, ignoreCase = true) -> SIMPLIFIED
            else -> FOLLOW_SYSTEM
        }
    }

    fun apply(tag: String) {
        val locales = if (tag.isBlank()) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(tag)
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }
}
