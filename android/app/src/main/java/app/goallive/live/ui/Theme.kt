package app.goallive.live.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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

/** Light or dark look ("بازی روز" / "بازی شبانه"); every color in [C] follows it, so switching recomposes the whole app. */
object Look {
    /** "dark", "light" or "auto" (follow the phone). */
    var mode by mutableStateOf("dark")
    var systemDark by mutableStateOf(true)
    val light get() = if (mode == "auto") !systemDark else mode == "light"
}

private fun pick(dark: Long, light: Long) = if (Look.light) Color(light) else Color(dark)

// The site's "floodlights" palette (assets/app.css :root) and its day-match variant (:root[data-theme="light"]).
object C {
    val bg get() = pick(0xFF05070A, 0xFFE9EEE9)
    val bg2 get() = pick(0xFF0A0E13, 0xFFF4F7F3)
    val card get() = pick(0xFF0E1318, 0xFFFFFFFF)          // --card-solid at 92% over --bg
    val cardSolid get() = pick(0xFF0F141A, 0xFFFFFFFF)
    val card2 get() = pick(0x12FFFFFF, 0x0D0A1E0F)         // rgba(255,255,255,.07)
    val ink get() = pick(0xFFF3F6F8, 0xFF0B130D)
    val text get() = ink
    // a little brighter than the site's greys so small text stays readable
    val mute get() = pick(0xFF9AA4AF, 0xFF56645A)
    val faint get() = pick(0xFF6E7883, 0xFF7E8A82)
    val line get() = pick(0x14FFFFFF, 0x140A1E0F)          // .08
    val line2 get() = pick(0x24FFFFFF, 0x240A1E0F)         // .14
    val live = Color(0xFFFF3358)
    val liveSoft = Color(0x24FF3358)
    val gold get() = pick(0xFFFFC53D, 0xFFD99A00)
    val win get() = pick(0xFF2FD673, 0xFF16A34A)
    val draw = Color(0xFF7D8792)
    val loss = Color(0xFFFF4D6A)
    val home = Color(0xFF4F8BFF)
    val away = Color(0xFFFF8A3D)
    val pitch get() = pick(0xFF1F7A3E, 0xFF2A9A52)
    val pitch2 get() = pick(0xFF23874A, 0xFF30A85B)
    val yellow get() = gold
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
val LocalAccent = compositionLocalOf { Lime }
val LocalAccentInk = compositionLocalOf { Color(0xFF0B1200) }

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
    val base = if (Look.light) lightColorScheme() else darkColorScheme()
    val scheme = base.copy(
        primary = accent, onPrimary = a.ink, secondary = accent, background = C.bg, onBackground = C.ink,
        surface = C.cardSolid, onSurface = C.ink, surfaceVariant = C.cardSolid, onSurfaceVariant = C.mute, outline = C.line2,
        surfaceContainer = C.cardSolid, surfaceContainerHigh = C.cardSolid, surfaceContainerLow = C.cardSolid, error = C.live,
    )
    MaterialTheme(colorScheme = scheme, typography = Type) {
        CompositionLocalProvider(
            LocalLayoutDirection provides if (app.goallive.live.Lang.en) LayoutDirection.Ltr else LayoutDirection.Rtl, LocalAccent provides accent, LocalAccentInk provides a.ink,
            content = content,
        )
    }
}

/** The site's light theme swaps the lime default for green, and very light club colors get darker so they read on white. */
fun accentForLook(c: Color): Color = when {
    !Look.light -> c
    c == Lime -> Color(0xFF16A34A)
    lum(c) > 0.55f -> darken(c, 0.62f)
    else -> c
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
