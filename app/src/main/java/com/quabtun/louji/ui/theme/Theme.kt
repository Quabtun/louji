package com.quabtun.louji.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.quabtun.louji.model.ThemeMode

private val LightScheme = lightColorScheme(
    primary = Color(0xFF6A4B00),
    onPrimary = Color.White,
    primaryContainer = SafetyAmber,
    onPrimaryContainer = Color(0xFF241A00),
    inversePrimary = SafetyAmber,
    secondary = SignalGreen,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB7F0CE),
    onSecondaryContainer = Color(0xFF00210F),
    tertiary = Color(0xFF3E5560),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD6E7EE),
    onTertiaryContainer = Color(0xFF0E2C38),
    background = Paper,
    onBackground = Color(0xFF17191C),
    surface = PaperRaised,
    onSurface = Color(0xFF17191C),
    surfaceVariant = Color(0xFFE6E1D6),
    onSurfaceVariant = Color(0xFF4E514F),
    surfaceTint = Color(0xFF6A4B00),
    inverseSurface = Color(0xFF2E3133),
    inverseOnSurface = Color(0xFFF1F0EC),
    error = AlertRed,
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    outline = Color(0xFF7A7D77),
    outlineVariant = Color(0xFFCBC6B9),
    scrim = Color.Black,
)

private val DarkScheme = darkColorScheme(
    primary = SafetyAmber,
    onPrimary = Color(0xFF241A00),
    primaryContainer = SafetyAmber,
    onPrimaryContainer = Color(0xFF241A00),
    inversePrimary = Color(0xFF6A4B00),
    secondary = SignalGreenBright,
    onSecondary = Color(0xFF00391F),
    secondaryContainer = Color(0xFF0B5132),
    onSecondaryContainer = Color(0xFFB7F0CE),
    tertiary = Color(0xFFA9C7D6),
    onTertiary = Color(0xFF0E2C38),
    tertiaryContainer = Color(0xFF263B44),
    onTertiaryContainer = Color(0xFFD6E7EE),
    background = Color(0xFF101214),
    onBackground = Color(0xFFE8E6E1),
    surface = Color(0xFF15181A),
    onSurface = Color(0xFFE8E6E1),
    surfaceVariant = Color(0xFF2A2D30),
    onSurfaceVariant = Color(0xFFC5C6C1),
    surfaceTint = SafetyAmber,
    inverseSurface = Color(0xFFE8E6E1),
    inverseOnSurface = Color(0xFF2E3133),
    error = AlertRedDark,
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF8E918C),
    outlineVariant = Color(0xFF3F4143),
    scrim = Color.Black,
)

val LocalLouJiStatusColors = staticCompositionLocalOf { LightStatusColors }

@Composable
fun LouJiTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colorScheme = if (darkTheme) DarkScheme else LightScheme
    val statusColors = if (darkTheme) DarkStatusColors else LightStatusColors
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalLouJiStatusColors provides statusColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = LouJiTypography,
            shapes = LouJiShapes,
            content = content,
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
