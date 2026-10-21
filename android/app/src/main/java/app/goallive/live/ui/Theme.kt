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
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import app.goallive.live.R
import app.goallive.live.data.Club

// The site's "floodlights" palette (assets/app.css :root).
object C {
    val bg = Color(0xFF05070A)
    val bg2 = Color(0xFF0A0E13)
    val card = Color(0xFF0E1318)          // --card-solid at 92% over --bg
    val cardSolid = Color(0xFF0F141A)
    val card2 = Color(0x12FFFFFF)         // rgba(255,255,255,.07)
    val ink = Color(0xFFF3F6F8)
    val text = ink
    val mute = Color(0xFF8D97A3)
    val faint = Color(0xFF56606B)
    val line = Color(0x14FFFFFF)          // .08
    val line2 = Color(0x24FFFFFF)         // .14
    val live = Color(0xFFFF3358)
    val liveSoft = Color(0x24FF3358)
    val gold = Color(0xFFFFC53D)
    val win = Color(0xFF2FD673)
    val draw = Color(0xFF7D8792)
    val loss = Color(0xFFFF4D6A)
    val home = Color(0xFF4F8BFF)
    val away = Color(0xFFFF8A3D)
    val pitch = Color(0xFF1F7A3E)
    val pitch2 = Color(0xFF23874A)
    val yellow = gold
    val red = live
}

/** The accent and its companions (--accent, --accent-ink, --accent-soft, --accent-glow). */
data class Accent(val c: Color) {
    val ink = if (lum(c) > 0.55f) Color(0xFF0B0F14) else Color.White
    val soft = c.copy(alpha = 0.14f)
    val glow = c.copy(alpha = 0.40f)
}

val Lime = Color(0xFFC6FF3D)
val DefaultAccent = Lime
val LocalAccent = staticCompositionLocalOf { Lime }
val LocalAccentInk = staticCompositionLocalOf { Color(0xFF0B1200) }

val Vazir = FontFamily(
    Font(R.font.vazir_regular, FontWeight.Normal),
    Font(R.font.vazir_medium, FontWeight.Medium),
    Font(R.font.vazir_bold, FontWeight.Bold),
    Font(R.font.vazir_extrabold, FontWeight.ExtraBold),
    Font(R.font.vazir_black, FontWeight.Black),
)

/** "Barlow Condensed": the broadcast type the site uses for Latin team and player names. */
val Barlow = FontFamily(
    Font(R.font.barlow_semibold, FontWeight.SemiBold),
    Font(R.font.barlow_bold, FontWeight.Bold),
    Font(R.font.barlow_extrabold, FontWeight.ExtraBold),
)

fun vz(size: TextUnit, w: FontWeight = FontWeight.Normal) = TextStyle(fontFamily = Vazir, fontSize = size, fontWeight = w, lineHeight = size * 1.5f)
fun lat(size: TextUnit, w: FontWeight = FontWeight.Bold) = TextStyle(fontFamily = Barlow, fontSize = size, fontWeight = w, lineHeight = size * 1.2f, letterSpacing = 0.2.sp)

private val Type = Typography(
    displayLarge = vz(44.sp, FontWeight.Black),
    displayMedium = vz(36.sp, FontWeight.Black),
    headlineLarge = vz(26.sp, FontWeight.Black),
    headlineMedium = vz(22.sp, FontWeight.Black),
    headlineSmall = vz(19.sp, FontWeight.Black),
    titleLarge = vz(18.sp, FontWeight.Black),
    titleMedium = vz(15.sp, FontWeight.ExtraBold),
    titleSmall = vz(14.sp, FontWeight.ExtraBold),
    bodyLarge = vz(15.sp),
    bodyMedium = vz(14.sp),
    bodySmall = vz(12.sp),
    labelLarge = vz(13.sp, FontWeight.Bold),
    labelMedium = vz(12.sp, FontWeight.Bold),
    labelSmall = vz(11.sp, FontWeight.Bold),
)

@Composable
fun GoalLiveTheme(accent: Color, content: @Composable () -> Unit) {
    val a = Accent(accent)
    val scheme = darkColorScheme(
        primary = accent, onPrimary = a.ink, secondary = accent, background = C.bg, onBackground = C.ink,
        surface = C.cardSolid, onSurface = C.ink, surfaceVariant = C.cardSolid, onSurfaceVariant = C.mute, outline = C.line2,
        surfaceContainer = C.cardSolid, surfaceContainerHigh = C.cardSolid, surfaceContainerLow = C.cardSolid, error = C.live,
    )
    MaterialTheme(colorScheme = scheme, typography = Type) {
        CompositionLocalProvider(
            LocalLayoutDirection provides LayoutDirection.Rtl, LocalAccent provides accent, LocalAccentInk provides a.ink,
            content = content,
        )
    }
}

fun lum(c: Color) = 0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue

fun parseColor(hex: String?): Color? = runCatching {
    var h = hex!!.trim().removePrefix("#")
    if (h.length == 3) h = h.map { "$it$it" }.joinToString("")
    Color(("FF" + h.take(6)).toLong(16))
}.getOrNull()

/** Same rule as the site's applyTeamTheme: the first club color that is not too dark. */
fun accentFor(club: Club?, color: String?): Color {
    val colors = if (club != null) listOfNotNull(club.accent, club.c1, club.c2).map { Color(it) } else listOfNotNull(parseColor(color))
    return colors.firstOrNull { lum(it) > 0.12f } ?: colors.firstOrNull() ?: Lime
}

fun accentFrom(c: Color?): Color = c?.let { if (lum(it) > 0.12f) it else lerpColor(it, Color.White, 0.45f) } ?: Lime

fun lerpColor(a: Color, b: Color, t: Float) = Color(a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t, a.blue + (b.blue - a.blue) * t, 1f)

/** color-mix(in srgb, c p%, black) */
fun darken(c: Color, keep: Float) = lerpColor(Color.Black, c, keep)

/** Readable text on a color, like the site's --home-ink. */
fun inkOn(c: Color) = if (lum(c) > 0.6f) Color(0xFF111111) else Color.White

/** Stable fallback color for clubs without a theme (site: hsl(id*47 55% 34%)). */
fun hueColor(id: Int): Color = Color.hsl(((id.toLong() * 47) % 360).toFloat(), 0.55f, 0.34f)
