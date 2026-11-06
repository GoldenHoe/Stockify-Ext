package com.rsps1008.stockify.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

/**
 * Material 3 Expressive shape scale. Components read these tokens instead of
 * hard-coding corner radii so every surface keeps the same visual language.
 */
val StockifyShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

private val AmoledColorScheme = darkColorScheme(
    primary = Blue80,
    secondary = BlueGrey80,
    tertiary = Amber80,
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0E0E10),
    surfaceContainer = Color(0xFF151517),
    surfaceContainerHigh = Color(0xFF1D1D20),
    surfaceContainerHighest = Color(0xFF242427),
    surfaceVariant = Color(0xFF242424)
)

private val LightColorScheme = lightColorScheme(
    primary = Blue40,
    onPrimary = Color.White,
    primaryContainer = BlueContainerLight,
    onPrimaryContainer = BlueOnContainerLight,
    secondary = BlueGrey40,
    tertiary = Amber40,
    background = Color(0xFFF8F9FF),
    surface = Color(0xFFF8F9FF),
    surfaceVariant = Color(0xFFDEE3EB),
    onSurfaceVariant = Color(0xFF42474E),
    outline = Color(0xFF72777F),
    outlineVariant = Color(0xFFC2C7CF),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF2F4FA),
    surfaceContainer = Color(0xFFECEEF4),
    surfaceContainerHigh = Color(0xFFE6E8EE),
    surfaceContainerHighest = Color(0xFFE0E2E8)
)

private val DarkColorScheme = darkColorScheme(
    primary = Blue80,
    onPrimary = BlueOnPrimaryDark,
    primaryContainer = BlueContainerDark,
    onPrimaryContainer = BlueOnContainerDark,
    secondary = BlueGrey80,
    tertiary = Amber80,
    background = Color(0xFF111318),
    surface = Color(0xFF111318),
    surfaceVariant = Color(0xFF42474E),
    onSurfaceVariant = Color(0xFFC2C7CF),
    outline = Color(0xFF8C9199),
    outlineVariant = Color(0xFF42474E),
    surfaceContainerLowest = Color(0xFF0C0E13),
    surfaceContainerLow = Color(0xFF191C20),
    surfaceContainer = Color(0xFF1D2024),
    surfaceContainerHigh = Color(0xFF272A2F),
    surfaceContainerHighest = Color(0xFF32353A)
)

// Custom Colors for Stock Gain/Loss
data class StockColors(
    val gain: Color,
    val loss: Color
)

private val LightStockColors = StockColors(
    gain = StockGain,
    loss = StockLoss
)

private val DarkStockColors = StockColors(
    gain = StockGainDark,
    loss = StockLossDark
)

private val LocalStockColors = staticCompositionLocalOf { LightStockColors }

// Custom Theme object to hold custom colors
object StockifyAppTheme {
    val stockColors: StockColors
        @Composable
        @ReadOnlyComposable
        get() = LocalStockColors.current
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockifyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    amoledTheme: Boolean = false,
    textScale: Float = 1f,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            when {
                amoledTheme -> dynamicDarkColorScheme(context).copy(
                    background = Color.Black,
                    surface = Color.Black,
                    surfaceContainerLowest = Color.Black,
                    surfaceContainerLow = Color(0xFF0E0E10),
                    surfaceContainer = Color(0xFF151517),
                    surfaceContainerHigh = Color(0xFF1D1D20),
                    surfaceContainerHighest = Color(0xFF242427),
                    surfaceVariant = Color(0xFF242424)
                )
                darkTheme -> dynamicDarkColorScheme(context)
                else -> dynamicLightColorScheme(context)
            }
        }

        amoledTheme -> AmoledColorScheme
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val stockColors = if (darkTheme) DarkStockColors else LightStockColors
    val currentDensity = LocalDensity.current
    val adjustedDensity = Density(
        density = currentDensity.density,
        fontScale = currentDensity.fontScale * textScale
    )

    CompositionLocalProvider(
        LocalStockColors provides stockColors,
        LocalDensity provides adjustedDensity
    ) {
        // Expressive shape scale: every component reads these tokens instead of
        // hard-coded corner radii. Motion specs come from MaterialTheme.motionScheme
        // at the call sites that animate.
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = StockifyShapes,
            typography = Typography,
            content = content
        )
    }
}
