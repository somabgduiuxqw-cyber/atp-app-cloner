package com.atp.cloner.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val LightColorScheme = lightColorScheme(
    primary = AtpPrimaryBlue,
    onPrimary = AtpWhite,
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = AtpStrongBlue,
    secondary = Color(0xFF455A64),
    onSecondary = AtpWhite,
    secondaryContainer = AtpSilver,
    onSecondaryContainer = AtpCharcoalText,
    tertiary = AtpAccentRed,
    onTertiary = AtpWhite,
    background = AtpLightBackground,
    onBackground = AtpCharcoalText,
    surface = AtpWhite,
    onSurface = AtpCharcoalText,
    surfaceVariant = Color(0xFFECEFF1),
    onSurfaceVariant = Color(0xFF455A64),
    outline = AtpBorderGray,
    outlineVariant = Color(0xFFCFD8DC),
    error = AtpErrorRed,
    onError = AtpWhite
)

val DarkBlueColorScheme = darkColorScheme(
    primary = DarkBluePrimary,
    onPrimary = Color(0xFF031020),
    primaryContainer = DarkBlueElevated,
    onPrimaryContainer = DarkBlueAccent,
    secondary = DarkBlueAccent,
    onSecondary = Color(0xFF031020),
    secondaryContainer = Color(0xFF1E3A5F),
    onSecondaryContainer = DarkBlueText,
    tertiary = AtpAccentRed,
    onTertiary = AtpWhite,
    background = DarkBlueBg,
    onBackground = DarkBlueText,
    surface = DarkBlueSurface,
    onSurface = DarkBlueText,
    surfaceVariant = DarkBlueElevated,
    onSurfaceVariant = Color(0xFF90CAF9),
    outline = Color(0xFF1E3A5F),
    outlineVariant = Color(0xFF152A45),
    error = AtpErrorRed,
    onError = AtpWhite
)

val DarkCharcoalColorScheme = darkColorScheme(
    primary = DarkCharcoalPrimary,
    onPrimary = Color(0xFF0A0C0E),
    primaryContainer = DarkCharcoalElevated,
    onPrimaryContainer = DarkCharcoalText,
    secondary = DarkCharcoalSilver,
    onSecondary = Color(0xFF0A0C0E),
    secondaryContainer = Color(0xFF37474F),
    onSecondaryContainer = DarkCharcoalText,
    tertiary = AtpAccentRed,
    onTertiary = AtpWhite,
    background = DarkCharcoalBg,
    onBackground = DarkCharcoalText,
    surface = DarkCharcoalSurface,
    onSurface = DarkCharcoalText,
    surfaceVariant = DarkCharcoalElevated,
    onSurfaceVariant = DarkCharcoalSilver,
    outline = Color(0xFF374151),
    outlineVariant = Color(0xFF263238),
    error = AtpErrorRed,
    onError = AtpWhite
)

val DarkGreenColorScheme = darkColorScheme(
    primary = DarkGreenPrimary,
    onPrimary = Color(0xFF03140A),
    primaryContainer = DarkGreenElevated,
    onPrimaryContainer = DarkGreenText,
    secondary = DarkGreenSecondary,
    onSecondary = Color(0xFF03140A),
    secondaryContainer = Color(0xFF1B5E38),
    onSecondaryContainer = DarkGreenText,
    tertiary = AtpAccentRed,
    onTertiary = AtpWhite,
    background = DarkGreenBg,
    onBackground = DarkGreenText,
    surface = DarkGreenSurface,
    onSurface = DarkGreenText,
    surfaceVariant = DarkGreenElevated,
    onSurfaceVariant = Color(0xFFA5D6A7),
    outline = Color(0xFF1E462E),
    outlineVariant = Color(0xFF123520),
    error = AtpErrorRed,
    onError = AtpWhite
)

val DarkTealColorScheme = darkColorScheme(
    primary = DarkTealPrimary,
    onPrimary = Color(0xFF021518),
    primaryContainer = DarkTealElevated,
    onPrimaryContainer = DarkTealText,
    secondary = DarkTealSecondary,
    onSecondary = Color(0xFF021518),
    secondaryContainer = Color(0xFF155E68),
    onSecondaryContainer = DarkTealText,
    tertiary = AtpAccentRed,
    onTertiary = AtpWhite,
    background = DarkTealBg,
    onBackground = DarkTealText,
    surface = DarkTealSurface,
    onSurface = DarkTealText,
    surfaceVariant = DarkTealElevated,
    onSurfaceVariant = Color(0xFF80DEEA),
    outline = Color(0xFF164B54),
    outlineVariant = Color(0xFF0E383F),
    error = AtpErrorRed,
    onError = AtpWhite
)

@Composable
fun AtpClonerTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    darkVariant: DarkThemeVariant = DarkThemeVariant.DARK_BLUE,
    content: @Composable () -> Unit
) {
    val systemInDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        AppThemeMode.SYSTEM -> systemInDark
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }

    val colorScheme = if (!isDark) {
        LightColorScheme
    } else {
        when (darkVariant) {
            DarkThemeVariant.DARK_BLUE -> DarkBlueColorScheme
            DarkThemeVariant.DARK_CHARCOAL -> DarkCharcoalColorScheme
            DarkThemeVariant.DARK_GREEN -> DarkGreenColorScheme
            DarkThemeVariant.DARK_TEAL -> DarkTealColorScheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
