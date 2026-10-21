package app.goallive.live.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import app.goallive.live.R

// "Floodlights" palette, same as the web app's dark theme.
object C {
    val bg = Color(0xFF0B0F14)
    val card = Color(0xFF131A23)
    val card2 = Color(0xFF1A2330)
    val line = Color(0x1AFFFFFF)
    val text = Color(0xFFE9EEF5)
    val mute = Color(0xFF8A96A8)
    val pitch = Color(0xFF1E7A46)
    val pitchDark = Color(0xFF16603A)
    val live = Color(0xFFFF4D6A)
    val gold = Color(0xFFF5C542)
    val win = Color(0xFF2BD576)
    val draw = Color(0xFF8A96A8)
    val loss = Color(0xFFFF4D6A)
    val home = Color(0xFF3D8BFF)
    val away = Color(0xFFFF8A3D)
    val yellow = Color(0xFFFFD43B)
    val red = Color(0xFFE5383B)
}

val DefaultAccent = Color(0xFF2BD576)
val LocalAccent = staticCompositionLocalOf { DefaultAccent }

val Vazir = FontFamily(
    Font(R.font.vazir_regular, FontWeight.Normal),
    Font(R.font.vazir_medium, FontWeight.Medium),
    Font(R.font.vazir_bold, FontWeight.Bold),
    Font(R.font.vazir_extrabold, FontWeight.ExtraBold),
    Font(R.font.vazir_black, FontWeight.Black),
)

private fun t(size: Int, w: FontWeight = FontWeight.Normal, lh: Int = (size * 1.45).toInt()) =
    TextStyle(fontFamily = Vazir, fontSize = size.sp, fontWeight = w, lineHeight = lh.sp)

private val Type = Typography(
    displayLarge = t(44, FontWeight.Black, 50),
    displayMedium = t(36, FontWeight.Black, 42),
    headlineLarge = t(26, FontWeight.ExtraBold),
    headlineMedium = t(22, FontWeight.ExtraBold),
    headlineSmall = t(19, FontWeight.Bold),
    titleLarge = t(18, FontWeight.Bold),
    titleMedium = t(15, FontWeight.Bold),
    titleSmall = t(14, FontWeight.Bold),
    bodyLarge = t(15),
    bodyMedium = t(14),
    bodySmall = t(12),
    labelLarge = t(14, FontWeight.Medium),
    labelMedium = t(12, FontWeight.Medium),
    labelSmall = t(11, FontWeight.Medium),
)

@Composable
fun GoalLiveTheme(accent: Color, content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = accent, onPrimary = if (lum(accent) > 0.55f) Color(0xFF05070A) else Color.White,
        secondary = accent, background = C.bg, onBackground = C.text, surface = C.card, onSurface = C.text,
        surfaceVariant = C.card2, onSurfaceVariant = C.mute, outline = C.line, surfaceContainer = C.card,
        surfaceContainerHigh = C.card2, surfaceContainerLow = C.card, error = C.live,
    )
    MaterialTheme(colorScheme = scheme, typography = Type) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl, LocalAccent provides accent, content = content)
    }
}

fun lum(c: Color) = 0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue

fun parseColor(hex: String?): Color? = runCatching {
    var h = hex!!.trim().removePrefix("#")
    if (h.length == 3) h = h.map { "$it$it" }.joinToString("")
    Color(("FF$h").toLong(16))
}.getOrNull()

/** A readable accent from a club color: lift very dark colors, keep bright ones. */
fun accentFrom(c: Color?): Color {
    if (c == null) return DefaultAccent
    val l = lum(c)
    return when {
        l < 0.12f -> lerpColor(c, Color.White, 0.55f)
        l < 0.25f -> lerpColor(c, Color.White, 0.3f)
        else -> c
    }
}

fun lerpColor(a: Color, b: Color, t: Float) = Color(a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t, a.blue + (b.blue - a.blue) * t, 1f)
