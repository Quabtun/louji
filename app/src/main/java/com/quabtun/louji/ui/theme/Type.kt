package com.quabtun.louji.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable

private val Face = FontFamily.SansSerif

val LouJiTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = Face,
        fontWeight = FontWeight.Black,
        fontSize = 48.sp,
        lineHeight = 52.sp,
        letterSpacing = (-1.2).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = Face,
        fontWeight = FontWeight.Black,
        fontSize = 38.sp,
        lineHeight = 42.sp,
        letterSpacing = (-0.8).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = Face,
        fontWeight = FontWeight.Black,
        fontSize = 30.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.4).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = Face,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = Face,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = Face,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = Face,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Face,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = Face,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = Face,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Face,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Face,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = Face,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = Face,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.3.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = Face,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.6.sp,
    ),
)

/** Use for order numbers, phone tails, floors and dates so digits share a width. */
val TabularNumbers = TextStyle(fontFeatureSettings = "tnum")

@Immutable
data class LouJiStatusColors(
    val stair: Color,
    val stairContainer: Color,
    val onStairContainer: Color,
    val noStair: Color,
    val noStairContainer: Color,
    val onNoStairContainer: Color,
    val unknown: Color,
    val unknownContainer: Color,
    val onUnknownContainer: Color,
    val rule: Color,
)

val LightStatusColors = LouJiStatusColors(
    stair = Color(0xFF8A5A00),
    stairContainer = Color(0xFFFFE7A3),
    onStairContainer = Color(0xFF4A3200),
    noStair = SignalGreen,
    noStairContainer = Color(0xFFD5F5E2),
    onNoStairContainer = Color(0xFF06402A),
    unknown = Color(0xFF6E716B),
    unknownContainer = Color(0xFFECE8DE),
    onUnknownContainer = Color(0xFF4B4E48),
    rule = LineLight,
)

val DarkStatusColors = LouJiStatusColors(
    stair = SafetyAmber,
    stairContainer = Color(0xFF4A3600),
    onStairContainer = Color(0xFFFFE7A3),
    noStair = SignalGreenBright,
    noStairContainer = Color(0xFF0B5132),
    onNoStairContainer = Color(0xFFB7F0CE),
    unknown = Color(0xFF9DA09A),
    unknownContainer = Color(0xFF2A2D30),
    onUnknownContainer = Color(0xFFC5C6C1),
    rule = LineDark,
)
