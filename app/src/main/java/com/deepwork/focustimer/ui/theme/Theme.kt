package com.deepwork.focustimer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * A locked-down dark scheme. Every slot that could ever surface as a light or
 * white element is mapped to black/sepia so the "no white" rule holds even for
 * Material defaults (ripples, dialog scrims, disabled states, etc.).
 */
private val FocusColorScheme = darkColorScheme(
    primary = Sepia,
    onPrimary = PitchBlack,
    secondary = Sepia,
    onSecondary = PitchBlack,
    tertiary = Sepia,
    onTertiary = PitchBlack,
    background = PitchBlack,
    onBackground = Sepia,
    surface = PitchBlack,
    onSurface = Sepia,
    surfaceVariant = PitchBlack,
    onSurfaceVariant = SepiaDim,
    surfaceContainer = PitchBlack,
    surfaceContainerHigh = PitchBlack,
    surfaceContainerHighest = PitchBlack,
    outline = SepiaDim,
    outlineVariant = SepiaFaint,
    error = Sepia,
    onError = PitchBlack,
    inverseSurface = PitchBlack,
    inverseOnSurface = Sepia,
    scrim = PitchBlack,
)

@Composable
fun FocusTimerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = FocusColorScheme,
        typography = AppTypography,
        content = content,
    )
}
