package com.rsps1008.stockify.ui.text

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Which Chinese script the UI renders in.
 *
 * Traditional Chinese is the canonical form everywhere in the app: Room column
 * names, CSV headers and stored enum values are Traditional, and the original
 * product language is Traditional as well. Simplified Chinese is produced at
 * render time by [ChineseTextConverter], so switching scripts never rewrites
 * persisted data.
 */
object DisplayLanguage {
    private val traditionalRegions = setOf("TW", "HK", "MO")

    /** True when text should be converted to Simplified Chinese before rendering. */
    @Volatile
    var simplified: Boolean = true
        private set

    fun updateFrom(context: Context) = updateFrom(context.resources.configuration)

    fun updateFrom(configuration: Configuration) {
        simplified = isSimplified(configuration)
    }

    fun isSimplified(configuration: Configuration): Boolean =
        isSimplified(configuration.locales[0])

    fun isSimplified(locale: Locale): Boolean {
        if (locale.language != Locale.CHINESE.language) return true
        if (locale.script.equals("Hant", ignoreCase = true)) return false
        return locale.country.uppercase(Locale.ROOT) !in traditionalRegions
    }
}
