package com.wavetone.app.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val NeonCyan = Color(0xFF38E1FF)
val NeonGreen = Color(0xFF2EE59D)
val AlertRed = Color(0xFFFF4D6D)
val WarningAmber = Color(0xFFFFC24D)
val CrimsonShield = Color(0xFFB00020)
val DeepSpace = Color(0xFF060B16)
val PanelDark = Color(0xFF0C1524)
val PanelRaised = Color(0xFF12203A)
val PanelBorder = Color(0xFF1D3557)
val TextPrimary = Color(0xFFE8F1FF)
val TextSecondary = Color(0xFF93A9C9)

private val WavetoneColors = darkColorScheme(
    primary = NeonCyan,
    onPrimary = Color(0xFF00222B),
    secondary = NeonGreen,
    onSecondary = Color(0xFF00301F),
    error = AlertRed,
    onError = Color.White,
    background = DeepSpace,
    onBackground = TextPrimary,
    surface = PanelDark,
    onSurface = TextPrimary,
    surfaceVariant = PanelRaised,
    onSurfaceVariant = TextSecondary,
    outline = PanelBorder
)

private val WavetoneTypography = Typography(
    headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp),
    titleMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp, color = TextSecondary),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
)

private val WavetoneShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp)
)

val MonoFamily = FontFamily.Monospace

@Composable
fun WavetoneTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = WavetoneColors,
        typography = WavetoneTypography,
        shapes = WavetoneShapes,
        content = content
    )
}
