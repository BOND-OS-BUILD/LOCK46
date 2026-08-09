package com.lock46.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * LOCK46 uses a single dark scheme in both light and dark system modes.
 *
 * This is a deliberate product decision rather than an oversight: the app is read at a
 * glance, often outdoors, and a status screen that changes colour with the system theme
 * makes "am I on duty?" harder to answer instantly.
 */
object Lock46Colors {
    val Ink = Color(0xFF06070A)
    val Panel = Color(0xFF10141A)
    val PanelRaised = Color(0xFF161C24)
    val Line = Color(0xFF232C37)
    val TextPrimary = Color(0xFFE9EDF2)
    val TextSecondary = Color(0xFF8B98A8)
    val TextDim = Color(0xFF5C6775)

    /** Free Mode / permission satisfied. */
    val Ready = Color(0xFF3FA06A)

    /** Duty Mode active. */
    val Duty = Color(0xFFE0912F)

    /** Blocked, destructive, or unmet requirement. */
    val Alert = Color(0xFFCF4A3C)
}

private val Lock46ColorScheme = darkColorScheme(
    primary = Lock46Colors.Duty,
    onPrimary = Lock46Colors.Ink,
    secondary = Lock46Colors.Ready,
    onSecondary = Lock46Colors.Ink,
    error = Lock46Colors.Alert,
    onError = Lock46Colors.TextPrimary,
    background = Lock46Colors.Ink,
    onBackground = Lock46Colors.TextPrimary,
    surface = Lock46Colors.Panel,
    onSurface = Lock46Colors.TextPrimary,
    surfaceVariant = Lock46Colors.PanelRaised,
    onSurfaceVariant = Lock46Colors.TextSecondary,
    outline = Lock46Colors.Line
)

private val Lock46Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 46.sp,
        letterSpacing = 1.sp
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        letterSpacing = 3.sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        letterSpacing = 1.sp
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        letterSpacing = 1.5.sp
    ),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 13.sp, lineHeight = 19.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp)
)

@Composable
fun Lock46Theme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = Lock46ColorScheme,
        typography = Lock46Typography,
        content = content
    )
}
