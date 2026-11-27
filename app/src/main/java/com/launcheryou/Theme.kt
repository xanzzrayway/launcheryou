package com.launcheryou

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext

fun iconShape(kind: Int): Shape = when (kind) {
    1 -> RoundedCornerShape(percent = 34)   // squircle
    2 -> RoundedCornerShape(percent = 22)   // kotak bulat
    3 -> FlowerShape(8, 0.07f)              // bunga
    else -> CircleShape
}

/** Skema warna dari satu rona, dipakai saat warna dinamis (Android 12+) dimatikan atau tidak tersedia. */
private fun seedScheme(hue: Float, dark: Boolean): ColorScheme {
    fun c(s: Float, l: Float, off: Float = 0f) = Color.hsl((hue + off + 360f) % 360f, s, l)
    return if (dark) darkColorScheme(
        primary = c(.85f, .80f), onPrimary = c(.55f, .15f),
        primaryContainer = c(.38f, .28f), onPrimaryContainer = c(.90f, .90f),
        secondary = c(.35f, .78f, 15f), onSecondary = c(.30f, .15f, 15f),
        secondaryContainer = c(.22f, .28f, 15f), onSecondaryContainer = c(.60f, .90f, 15f),
        tertiary = c(.55f, .80f, 60f), onTertiary = c(.50f, .15f, 60f),
        tertiaryContainer = c(.30f, .26f, 60f), onTertiaryContainer = c(.70f, .90f, 60f),
        background = c(.15f, .09f), onBackground = c(.15f, .90f),
        surface = c(.15f, .09f), onSurface = c(.15f, .90f),
        surfaceVariant = c(.12f, .22f), onSurfaceVariant = c(.12f, .76f),
        outline = c(.08f, .55f),
        surfaceContainerLowest = c(.15f, .06f), surfaceContainerLow = c(.14f, .11f),
        surfaceContainer = c(.13f, .13f), surfaceContainerHigh = c(.12f, .17f),
        surfaceContainerHighest = c(.12f, .21f)
    ) else lightColorScheme(
        primary = c(.50f, .40f), onPrimary = Color.White,
        primaryContainer = c(.85f, .90f), onPrimaryContainer = c(.60f, .12f),
        secondary = c(.30f, .40f, 15f), onSecondary = Color.White,
        secondaryContainer = c(.45f, .89f, 15f), onSecondaryContainer = c(.50f, .12f, 15f),
        tertiary = c(.40f, .40f, 60f), onTertiary = Color.White,
        tertiaryContainer = c(.70f, .88f, 60f), onTertiaryContainer = c(.60f, .12f, 60f),
        background = c(.30f, .97f), onBackground = c(.15f, .10f),
        surface = c(.30f, .97f), onSurface = c(.15f, .10f),
        surfaceVariant = c(.25f, .90f), onSurfaceVariant = c(.10f, .35f),
        outline = c(.08f, .45f),
        surfaceContainerLowest = Color.White, surfaceContainerLow = c(.30f, .95f),
        surfaceContainer = c(.25f, .92f), surfaceContainerHigh = c(.25f, .89f),
        surfaceContainerHighest = c(.25f, .86f)
    )
}

@Composable
fun LauncherYouTheme(s: UiSettings, content: @Composable () -> Unit) {
    val dark = when (s.themeMode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    val ctx = LocalContext.current
    val scheme = if (s.dynamicColor && Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    } else seedScheme(s.seedHue.toFloat(), dark)
    val finalScheme = if (dark && s.amoled) scheme.copy(
        background = Color.Black, surface = Color.Black,
        surfaceContainerLowest = Color.Black, surfaceContainerLow = Color(0xFF0A0A0A),
        surfaceContainer = Color(0xFF111111), surfaceContainerHigh = Color(0xFF181818),
        surfaceContainerHighest = Color(0xFF202020)
    ) else scheme
    MaterialTheme(colorScheme = finalScheme, content = content)
}
