package com.offline.bydvoice.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Palette tuned for a car display at night: deep neutrals, one warm accent, clear status colours. */
object Palette {
    val Bg = Color(0xFF0B0D10)
    val Surface1 = Color(0xFF12151A)
    val Surface2 = Color(0xFF181C22)
    val Surface3 = Color(0xFF20252C)
    val Stroke = Color(0x14FFFFFF)
    val StrokeStrong = Color(0x26FFFFFF)
    val Text = Color(0xFFF3F5F7)
    val TextSecondary = Color(0xFFA3ABB7)
    val TextTertiary = Color(0xFF6B7380)
    val Accent = Color(0xFFE5484D)
    val AccentDeep = Color(0xFFB4122B)
    val AccentSoft = Color(0x26E5484D)
    val Success = Color(0xFF30A46C)
    val SuccessSoft = Color(0x2630A46C)
    val Warn = Color(0xFFF5A524)
    val WarnSoft = Color(0x26F5A524)
    val Think = Color(0xFF7C8CFF)
}

private val scheme = darkColorScheme(
    primary = Palette.Accent,
    onPrimary = Color.White,
    primaryContainer = Palette.AccentSoft,
    onPrimaryContainer = Palette.Text,
    secondary = Palette.TextSecondary,
    background = Palette.Bg,
    onBackground = Palette.Text,
    surface = Palette.Surface1,
    onSurface = Palette.Text,
    surfaceVariant = Palette.Surface2,
    onSurfaceVariant = Palette.TextSecondary,
    surfaceContainerHighest = Palette.Surface3,
    outline = Palette.StrokeStrong,
    outlineVariant = Palette.Stroke,
    error = Palette.Warn,
)

private val type = Typography(
    displaySmall = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
    headlineSmall = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp),
)

@Composable
fun BydTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = scheme, typography = type, content = content)
