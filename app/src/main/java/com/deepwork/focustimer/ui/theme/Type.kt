package com.deepwork.focustimer.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

/**
 * Monospace everywhere so the timer digits never reflow as numbers change.
 * Default text color is supplied by the color scheme (sepia).
 */
val AppTypography = Typography(
    headlineLarge = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 34.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 22.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 16.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp),
)
