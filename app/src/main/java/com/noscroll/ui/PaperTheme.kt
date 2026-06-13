package com.noscroll.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

object PaperColors {
    val Paper = Color(0xFFF7F3EA)
    val Raised = Color(0xFFFBF8F0)
    val Ink = Color(0xFF171615)
    val Graphite = Color(0xFF66615A)
    val Muted = Color(0xFF8A8378)
    val Hairline = Color(0xFFDDD5C8)
    val SoftInk = Color(0xFFE8E0D2)
    val Danger = Color(0xFF9E4A3E)
    val Amber = Color(0xFFD9A441)
    val Sage = Color(0xFF77846F)
    val OverlayInk = Color(0xE8171615)
}

private val PaperScheme: ColorScheme = lightColorScheme(
    primary = PaperColors.Ink,
    onPrimary = PaperColors.Raised,
    secondary = PaperColors.Sage,
    onSecondary = PaperColors.Raised,
    background = PaperColors.Paper,
    onBackground = PaperColors.Ink,
    surface = PaperColors.Raised,
    onSurface = PaperColors.Ink,
    surfaceVariant = PaperColors.Paper,
    onSurfaceVariant = PaperColors.Graphite,
    outline = PaperColors.Hairline,
    tertiary = PaperColors.Amber
)

private val PaperTypography = Typography(
    headlineMedium = TextStyle(fontFamily = FontFamily.Serif, fontSize = 28.sp, lineHeight = 34.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.Serif, fontSize = 22.sp, lineHeight = 27.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.Serif, fontSize = 16.sp, lineHeight = 21.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 15.sp, lineHeight = 21.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 13.sp, lineHeight = 18.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 13.sp, lineHeight = 17.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 11.sp, lineHeight = 14.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 10.sp, lineHeight = 13.sp)
)

@Composable
fun NoScrollTheme(content: @Composable () -> Unit) {
    @Suppress("UNUSED_VARIABLE")
    val ignored = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = PaperScheme,
        typography = PaperTypography,
        content = content
    )
}
