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
    val info: Color,
    val infoContainer: Color,
    val onInfoContainer: Color,
    val danger: Color,
    val dangerContainer: Color,
    val onDangerContainer: Color,
    val rule: Color,
)

val LightStatusColors = LouJiStatusColors(
    stair = ClimbOrange,
    stairContainer = ClimbOrangeContainer,
    onStairContainer = ClimbOrangeOnContainer,
    noStair = PassGreen,
    noStairContainer = PassGreenContainer,
    onNoStairContainer = PassGreenOnContainer,
    unknown = Color(0xFF64748B),
    unknownContainer = Color(0xFFEDF1F7),
    onUnknownContainer = Color(0xFF334155),
    info = InfoBlue,
    infoContainer = InfoBlueContainer,
    onInfoContainer = InfoBlueOnContainer,
    danger = DangerRed,
    dangerContainer = DangerRedContainer,
    onDangerContainer = DangerRedOnContainer,
    rule = LightRule,
)

val DarkStatusColors = LouJiStatusColors(
    stair = ClimbOrangeNight,
    stairContainer = ClimbOrangeContainerNight,
    onStairContainer = ClimbOrangeOnContainerNight,
    noStair = PassGreenNight,
    noStairContainer = PassGreenContainerNight,
    onNoStairContainer = PassGreenOnContainerNight,
    unknown = Color(0xFF94A3B8),
    unknownContainer = Color(0xFF1D2432),
    onUnknownContainer = Color(0xFFE2E8F0),
    info = InfoBlueNight,
    infoContainer = InfoBlueContainerNight,
    onInfoContainer = InfoBlueOnContainerNight,
    danger = DangerRedNight,
    dangerContainer = DangerRedContainerNight,
    onDangerContainer = DangerRedOnContainerNight,
    rule = DarkRule,
)
