package com.quabtun.louji.ui.theme

import androidx.compose.ui.graphics.Color

// "Modern tactical field ledger": clean cool-neutral surfaces, one amber action colour,
// and five strict semantic accents.

// --- Light ---
val LightBackground = Color(0xFFF4F6F9)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFEDF1F7)
val LightOnSurface = Color(0xFF0F172A)
val LightOnSurfaceVariant = Color(0xFF64748B)
val LightOutline = Color(0xFF94A3B8)
val LightRule = Color(0xFFE2E8F0)

// --- Dark ---
val DarkBackground = Color(0xFF0B0E14)
val DarkSurface = Color(0xFF141923)
val DarkSurfaceVariant = Color(0xFF1D2432)
val DarkOnSurface = Color(0xFFF1F5F9)
val DarkOnSurfaceVariant = Color(0xFF94A3B8)
val DarkOutline = Color(0xFF64748B)
val DarkRule = Color(0xFF242C3D)

// --- Brand action amber ---
val ActionAmber = Color(0xFFFFB800)
val ActionAmberLightText = Color(0xFFD97706)
val ActionAmberDarkText = Color(0xFFFFC83D)
val OnActionAmber = Color(0xFF111827)

// --- Semantic accents (light) ---
val PassGreen = Color(0xFF059669)
val PassGreenContainer = Color(0xFFECFDF5)
val PassGreenBorder = Color(0xFFA7F3D0)
val PassGreenOnContainer = Color(0xFF065F46)

val ClimbOrange = Color(0xFFEA580C)
val ClimbOrangeContainer = Color(0xFFFFF7ED)
val ClimbOrangeBorder = Color(0xFFFED7AA)
val ClimbOrangeOnContainer = Color(0xFF7C2D12)

val DangerRed = Color(0xFFDC2626)
val DangerRedContainer = Color(0xFFFEF2F2)
val DangerRedBorder = Color(0xFFFECACA)
val DangerRedOnContainer = Color(0xFF991B1B)

val InfoBlue = Color(0xFF2563EB)
val InfoBlueContainer = Color(0xFFEFF6FF)
val InfoBlueBorder = Color(0xFFBFDBFE)
val InfoBlueOnContainer = Color(0xFF1E40AF)

// --- Semantic accents (dark) ---
val PassGreenNight = Color(0xFF34D399)
val PassGreenContainerNight = Color(0xFF064E3B)
val PassGreenBorderNight = Color(0xFF047857)
val PassGreenOnContainerNight = Color(0xFFD1FAE5)

val ClimbOrangeNight = Color(0xFFFB923C)
val ClimbOrangeContainerNight = Color(0xFF431407)
val ClimbOrangeBorderNight = Color(0xFF9A3412)
val ClimbOrangeOnContainerNight = Color(0xFFFFEDD5)

val DangerRedNight = Color(0xFFF87171)
val DangerRedContainerNight = Color(0xFF450A0A)
val DangerRedBorderNight = Color(0xFF991B1B)
val DangerRedOnContainerNight = Color(0xFFFEE2E2)

val InfoBlueNight = Color(0xFF60A5FA)
val InfoBlueContainerNight = Color(0xFF1E3A8A)
val InfoBlueBorderNight = Color(0xFF1D4ED8)
val InfoBlueOnContainerNight = Color(0xFFDBEAFE)

// Legacy aliases kept so older call sites keep compiling.
val Ink = Color(0xFF0F172A)
val Paper = LightBackground
val PaperRaised = LightSurface
val SafetyAmber = ActionAmber
val SafetyAmberDark = ActionAmberLightText
val SignalGreen = PassGreen
val SignalGreenBright = PassGreenNight
val Concrete = LightOnSurfaceVariant
val LineLight = LightRule
val LineDark = DarkRule
val AlertRed = DangerRed
val AlertRedDark = DangerRedNight
