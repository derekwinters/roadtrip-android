package com.roadtrip.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.roadtrip.core.settings.EffectiveTheme
import com.roadtrip.core.settings.ThemeMode
import com.roadtrip.core.settings.ThemeResolver

/** The app's light colour scheme (AND-018), built from the named palette in `Color.kt`. */
val AppLightColorScheme: ColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    inversePrimary = LightInversePrimary,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = LightTertiary,
    onTertiary = LightOnTertiary,
    tertiaryContainer = LightTertiaryContainer,
    onTertiaryContainer = LightOnTertiaryContainer,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceTint = LightSurfaceTint,
    inverseSurface = LightInverseSurface,
    inverseOnSurface = LightInverseOnSurface,
    error = LightError,
    onError = LightOnError,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    scrim = LightScrim,
    surfaceBright = LightSurfaceBright,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    surfaceContainerHighest = LightSurfaceContainerHighest,
    surfaceContainerLow = LightSurfaceContainerLow,
    surfaceContainerLowest = LightSurfaceContainerLowest,
    surfaceDim = LightSurfaceDim,
)

/** The app's dark colour scheme (AND-019): the Material 3 baseline dark values from `Color.kt`. */
val AppDarkColorScheme: ColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    inversePrimary = DarkInversePrimary,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnTertiaryContainer,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceTint = DarkSurfaceTint,
    inverseSurface = DarkInverseSurface,
    inverseOnSurface = DarkInverseOnSurface,
    error = DarkError,
    onError = DarkOnError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    scrim = DarkScrim,
    surfaceBright = DarkSurfaceBright,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = DarkSurfaceContainerHighest,
    surfaceContainerLow = DarkSurfaceContainerLow,
    surfaceContainerLowest = DarkSurfaceContainerLowest,
    surfaceDim = DarkSurfaceDim,
)

/** The colour scheme for a resolved theme (AND-019). */
fun colorSchemeFor(theme: EffectiveTheme): ColorScheme = when (theme) {
    EffectiveTheme.LIGHT -> AppLightColorScheme
    EffectiveTheme.DARK -> AppDarkColorScheme
}

/** The board palette for a resolved theme (AND-019). */
fun boardColorsFor(theme: EffectiveTheme): BoardColors = when (theme) {
    EffectiveTheme.LIGHT -> LightBoardColors
    EffectiveTheme.DARK -> DarkBoardColors
}

/**
 * The app theme (AND-018/AND-019): resolves [themeMode] (the device preference, ANDSET-007)
 * against the system dark-mode flag through the one [ThemeResolver] (ANDSET-008), then wraps the
 * UI in the matching [ColorScheme] and provides the matching board palette through
 * [LocalBoardColors]. This is the only place the system flag is read — feature screens never pick
 * light or dark themselves. A new [themeMode] recomposes the root, so the app re-themes at once.
 */
@Composable
fun RoadTripTheme(themeMode: ThemeMode, content: @Composable () -> Unit) {
    val theme = ThemeResolver.resolve(themeMode, systemDark = isSystemInDarkTheme())
    CompositionLocalProvider(LocalBoardColors provides boardColorsFor(theme)) {
        MaterialTheme(colorScheme = colorSchemeFor(theme), content = content)
    }
}
