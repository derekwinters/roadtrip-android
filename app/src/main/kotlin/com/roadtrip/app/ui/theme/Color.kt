package com.roadtrip.app.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * The app palette (AND-018): every raw colour value the UI uses lives here, as a named token.
 * Feature screens never write `Color(0x…)`; they read `MaterialTheme.colorScheme` roles or the
 * board palette (`LocalBoardColors`).
 *
 * The light scheme values equal the Material 3 baseline the app rendered with before it had its
 * own theme, so introducing the theme is not a visual change.
 */

// Light scheme — Material 3 baseline values.
internal val LightPrimary = Color(0xFF6750A4)
internal val LightOnPrimary = Color(0xFFFFFFFF)
internal val LightPrimaryContainer = Color(0xFFEADDFF)
internal val LightOnPrimaryContainer = Color(0xFF21005D)
internal val LightInversePrimary = Color(0xFFD0BCFF)
internal val LightSecondary = Color(0xFF625B71)
internal val LightOnSecondary = Color(0xFFFFFFFF)
internal val LightSecondaryContainer = Color(0xFFE8DEF8)
internal val LightOnSecondaryContainer = Color(0xFF1D192B)
internal val LightTertiary = Color(0xFF7D5260)
internal val LightOnTertiary = Color(0xFFFFFFFF)
internal val LightTertiaryContainer = Color(0xFFFFD8E4)
internal val LightOnTertiaryContainer = Color(0xFF31111D)
internal val LightBackground = Color(0xFFFEF7FF)
internal val LightOnBackground = Color(0xFF1D1B20)
internal val LightSurface = Color(0xFFFEF7FF)
internal val LightOnSurface = Color(0xFF1D1B20)
internal val LightSurfaceVariant = Color(0xFFE7E0EC)
internal val LightOnSurfaceVariant = Color(0xFF49454F)
internal val LightSurfaceTint = LightPrimary
internal val LightInverseSurface = Color(0xFF322F35)
internal val LightInverseOnSurface = Color(0xFFF5EFF7)
internal val LightError = Color(0xFFB3261E)
internal val LightOnError = Color(0xFFFFFFFF)
internal val LightErrorContainer = Color(0xFFF9DEDC)
internal val LightOnErrorContainer = Color(0xFF410E0B)
internal val LightOutline = Color(0xFF79747E)
internal val LightOutlineVariant = Color(0xFFCAC4D0)
internal val LightScrim = Color(0xFF000000)
internal val LightSurfaceBright = Color(0xFFFEF7FF)
internal val LightSurfaceContainer = Color(0xFFF3EDF7)
internal val LightSurfaceContainerHigh = Color(0xFFECE6F0)
internal val LightSurfaceContainerHighest = Color(0xFFE6E0E9)
internal val LightSurfaceContainerLow = Color(0xFFF7F2FA)
internal val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
internal val LightSurfaceDim = Color(0xFFDED8E1)

// Board palette (light) — board/domain colours, deliberately not colorScheme roles.
internal val BoardLightSquare = Color(0xFFF0D9B5)
internal val BoardDarkSquare = Color(0xFFB58863)
internal val PieceInkWhite = Color(0xFFFFFFFF)
internal val PieceInkBlack = Color(0xFF1A1A1A)
internal val PieceInkRed = Color(0xFFC62828)
internal val PieceOutlineShadow = Color(0xFF2B2B2B)
internal val LastMoveOriginShade = Color(0xFF000000).copy(alpha = 0.38f)
