package com.zerium.gecko.ui

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zerium.gecko.Prefs

/**
 * Tinted icon helper. Compose loads tint-free `_c` vector variants and
 * applies the palette color explicitly (theme-attribute tints inside vector
 * XMLs are a View-layer concept).
 */
@Composable
fun ZIcon(res: Int, size: Dp = 22.dp, tint: Color = MaterialTheme.colorScheme.onSurface) {
    Image(
        painter = painterResource(res),
        contentDescription = null,
        colorFilter = ColorFilter.tint(tint),
        modifier = Modifier.size(size)
    )
}

private val Indigo = Color(0xFF4355B9)
private val IndigoOn = Color(0xFFFFFFFF)
private val IndigoContainer = Color(0xFFDEE1FF)
private val IndigoOnContainer = Color(0xFF001159)
private val IndigoNight = Color(0xFFBAC3FF)
private val IndigoNightOn = Color(0xFF253380)
private val IndigoNightContainer = Color(0xFF3C4A9E)
private val IndigoNightOnContainer = Color(0xFFDEE1FF)

private val LightScheme = lightColorScheme(
    primary = Indigo,
    onPrimary = IndigoOn,
    primaryContainer = IndigoContainer,
    onPrimaryContainer = IndigoOnContainer,
    secondary = Color(0xFF595D72),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDEE1F9),
    onSecondaryContainer = Color(0xFF161B2C),
    tertiary = Color(0xFF77546F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD7F1),
    onTertiaryContainer = Color(0xFF2D1228),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFBF8FF),
    onBackground = Color(0xFF1B1B21),
    surface = Color(0xFFFBF8FF),
    onSurface = Color(0xFF1B1B21),
    surfaceVariant = Color(0xFFE3E1EC),
    onSurfaceVariant = Color(0xFF46464F),
    outline = Color(0xFF767680),
    outlineVariant = Color(0xFFC7C5D0),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F3FA),
    surfaceContainer = Color(0xFFEFEDF5),
    surfaceContainerHigh = Color(0xFFE9E7EF),
    surfaceContainerHighest = Color(0xFFE4E2E9)
)

private val DarkScheme = darkColorScheme(
    primary = IndigoNight,
    onPrimary = IndigoNightOn,
    primaryContainer = IndigoNightContainer,
    onPrimaryContainer = IndigoNightOnContainer,
    secondary = Color(0xFFC2C6DD),
    onSecondary = Color(0xFF2B3042),
    secondaryContainer = Color(0xFF414659),
    onSecondaryContainer = Color(0xFFDEE1F9),
    tertiary = Color(0xFFE5B9D8),
    onTertiary = Color(0xFF44283E),
    tertiaryContainer = Color(0xFF5C3F55),
    onTertiaryContainer = Color(0xFFFFD7F1),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF131318),
    onBackground = Color(0xFFE4E1E9),
    surface = Color(0xFF131318),
    onSurface = Color(0xFFE4E1E9),
    surfaceVariant = Color(0xFF46464F),
    onSurfaceVariant = Color(0xFFC7C5D0),
    outline = Color(0xFF90909A),
    outlineVariant = Color(0xFF46464F),
    surfaceContainerLowest = Color(0xFF0E0E13),
    surfaceContainerLow = Color(0xFF1B1B21),
    surfaceContainer = Color(0xFF1F1F25),
    surfaceContainerHigh = Color(0xFF2A292F),
    surfaceContainerHighest = Color(0xFF34343A)
)

/** Accent options offered in Appearance (ARGB ints for Prefs). */
val AccentChoices: List<Pair<String, Int>> = listOf(
    "Indigo" to 0xFF4355B9.toInt(),
    "Ocean" to 0xFF006A60.toInt(),
    "Forest" to 0xFF1F6D3A.toInt(),
    "Plum" to 0xFF7D5260.toInt(),
    "Sunset" to 0xFFB4551E.toInt(),
    "Rose" to 0xFFA63158.toInt()
)

/**
 * Compose counterpart of Theme.Zerium: mirrors the M3 tonal palette, honours
 * the app theme setting (system/light/dark), Material You dynamic color when
 * enabled (Android 12+), and the user accent choice for static palettes.
 */
@Composable
fun ZeriumTheme(content: @Composable () -> Unit) {
    val prefs = Prefs(LocalContext.current)
    val dark = when (prefs.appTheme()) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    val dynamic = prefs.dynamicColor() && Build.VERSION.SDK_INT >= 31
    val context = LocalContext.current
    val base = when {
        dynamic -> if (dark) dynamicDarkColorScheme(context)
                  else dynamicLightColorScheme(context)
        dark -> DarkScheme
        else -> LightScheme
    }
    val accentInt = prefs.accentColor()
    val scheme = if (!dynamic && accentInt != 0) {
        val accent = Color(accentInt)
        base.copy(
            primary = accent,
            onPrimary = Color.White
        )
    } else base
    MaterialTheme(colorScheme = scheme, content = content)
}
