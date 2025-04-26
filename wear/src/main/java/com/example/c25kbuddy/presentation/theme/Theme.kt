package com.example.c25kbuddy.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.Typography

private val C25KColorPalette = Colors(
    primary = Primary,
    primaryVariant = PrimaryDark,
    secondary = Accent,
    secondaryVariant = AccentDark,
    error = Color(0xFFB00020),
    onPrimary = TextPrimary,
    onSecondary = TextPrimary,
    onError = TextPrimary,
    background = BackgroundDark,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary
)

private val C25KTypography = Typography(
    // Use default typography but could customize if needed
)

@Composable
fun C25KBuddyTheme(
    content: @Composable () -> Unit
) {
    /**
     * Empty theme to customize for your app.
     * See: https://developer.android.com/jetpack/compose/designsystems/custom
     */
    MaterialTheme(
        colors = C25KColorPalette,
        typography = C25KTypography,
        content = content
    )
}