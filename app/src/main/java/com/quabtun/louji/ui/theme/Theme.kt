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
    primary = ActionAmberLightText,
    onPrimary = Color.White,
    primaryContainer = ActionAmber,
    onPrimaryContainer = OnActionAmber,
    inversePrimary = ActionAmber,
    secondary = PassGreen,
    onSecondary = Color.White,
    secondaryContainer = PassGreenContainer,
    onSecondaryContainer = PassGreenOnContainer,
    tertiary = InfoBlue,
    onTertiary = Color.White,
    tertiaryContainer = InfoBlueContainer,
    onTertiaryContainer = InfoBlueOnContainer,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceTint = ActionAmber,
    inverseSurface = Color(0xFF1E293B),
    inverseOnSurface = Color(0xFFF8FAFC),
    error = DangerRed,
    onError = Color.White,
    errorContainer = DangerRedContainer,
    onErrorContainer = DangerRedOnContainer,
    outline = LightOutline,
    outlineVariant = LightRule,
    scrim = Color.Black,
)

private val DarkScheme = darkColorScheme(
    primary = ActionAmberDarkText,
    onPrimary = OnActionAmber,
    primaryContainer = ActionAmber,
    onPrimaryContainer = OnActionAmber,
    inversePrimary = ActionAmberLightText,
    secondary = PassGreenNight,
    onSecondary = Color(0xFF022C22),
    secondaryContainer = PassGreenContainerNight,
    onSecondaryContainer = PassGreenOnContainerNight,
    tertiary = InfoBlueNight,
    onTertiary = Color(0xFF0C1B3A),
    tertiaryContainer = InfoBlueContainerNight,
    onTertiaryContainer = InfoBlueOnContainerNight,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceTint = ActionAmberDarkText,
    inverseSurface = Color(0xFFF1F5F9),
    inverseOnSurface = Color(0xFF0B0E14),
    error = DangerRedNight,
    onError = Color(0xFF450A0A),
    errorContainer = DangerRedContainerNight,
    onErrorContainer = DangerRedOnContainerNight,
    outline = DarkOutline,
    outlineVariant = DarkRule,
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
