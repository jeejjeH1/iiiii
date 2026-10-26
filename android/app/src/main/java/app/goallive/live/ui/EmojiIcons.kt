package app.goallive.live.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em

/*
 * The app's emoji, drawn as minimal line icons in the same hand as the rest of the app's icons (24-unit grid, round 1.8 strokes).
 * [T] shows them in place of the emoji, inline in the text and in the text's colour, so every title, tab, chip and line that
 * carries one gets the icon without changing its words; [EmojiBadge] shows one large on a soft tile.
 */

private fun line(name: String, vararg d: String) = vec(name, false, *d)
private fun solid(name: String, vararg d: String) = vec(name, true, *d)

private fun vec(name: String, filled: Boolean, vararg d: String): ImageVector {
    val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
    for (p in d) b.addPath(
        pathData = addPathNodes(p),
        fill = if (filled) SolidColor(Color.Black) else null,
        stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
    )
    return b.build()
}

private const val CIRCLE = "M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0"

object EmojiIcons {
    private val check by lazy { line("check", "M5 12.5l4.5 4.5L19 7") }
    private val checkCircle by lazy { line("checkCircle", CIRCLE, "M8 12.2l2.8 2.8L16 9.5") }
    private val brain by lazy {
        line("brain", "M12 5a3.2 3.2 0 0 0 -5.6 -1.4A3.2 3.2 0 0 0 4 7.5a3.2 3.2 0 0 0 -.5 5.2A3.2 3.2 0 0 0 6 17.5a3.2 3.2 0 0 0 6 .5z",
            "M12 5a3.2 3.2 0 0 1 5.6 -1.4A3.2 3.2 0 0 1 20 7.5a3.2 3.2 0 0 1 .5 5.2A3.2 3.2 0 0 1 18 17.5a3.2 3.2 0 0 1 -6 .5z",
            "M8 8.5c1 0 2 .7 2 2M16 8.5c-1 0-2 .7-2 2M7.5 14c1.2 0 2.2-.6 2.5-1.6M16.5 14c-1.2 0-2.2-.6-2.5-1.6")
    }
    private val flame by lazy {
        line("flame", "M8.5 14.5A2.5 2.5 0 0 0 11 12c0-1.4-.5-2-1-3-1.1-2.1-.2-4 2-6 .5 2.5 2 4.9 4 6.5 2 1.6 3 3.5 3 5.5a7 7 0 1 1 -14 0c0-1.2.4-2.3 1-3a2.5 2.5 0 0 0 2.5 2.5z")
    }
    private val zap by lazy { line("zap", "M13 2.5L4 14h8l-1 7.5L20 10h-8l1-7.5z") }
    private val target by lazy { line("target", CIRCLE, "M7 12a5 5 0 1 0 10 0a5 5 0 1 0 -10 0", "M11 12a1 1 0 1 0 2 0a1 1 0 1 0 -2 0") }
    private val card by lazy { solid("card", "M8.5 3.5h7a1.5 1.5 0 0 1 1.5 1.5v14a1.5 1.5 0 0 1 -1.5 1.5h-7a1.5 1.5 0 0 1 -1.5 -1.5v-14a1.5 1.5 0 0 1 1.5 -1.5z") }
    private val clipboard by lazy {
        line("clipboard", "M9 3.5h6a1 1 0 0 1 1 1v1.5a1 1 0 0 1 -1 1H9a1 1 0 0 1 -1 -1V4.5a1 1 0 0 1 1 -1z",
            "M8 5H7a2 2 0 0 0 -2 2v12a2 2 0 0 0 2 2h10a2 2 0 0 0 2 -2V7a2 2 0 0 0 -2 -2h-1", "M9 12h6M9 16h4")
    }
    private val flag by lazy { line("flag", "M5 21V4", "M5 4.5h12l-2.5 4 2.5 4H5") }
    private val film by lazy { line("film", "M4 5h16a1 1 0 0 1 1 1v12a1 1 0 0 1 -1 1H4a1 1 0 0 1 -1 -1V6a1 1 0 0 1 1 -1z", "M7.5 5v14M16.5 5v14M3 9.5h4.5M3 14.5h4.5M16.5 9.5H21M16.5 14.5H21") }
    private val clover by lazy {
        line("clover", "M7 8.5a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0 -5 0", "M12 8.5a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0 -5 0",
            "M7 13.5a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0 -5 0", "M12 13.5a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0 -5 0", "M14.5 15.5l4.5 5")
    }
    private val up by lazy { line("up", "M12 19V5M6 11l6-6 6 6") }
    private val downArrow by lazy { line("downArrow", "M12 5v14M6 13l6 6 6-6") }
    private val crystal by lazy { line("crystal", "M5 11a7 7 0 1 0 14 0a7 7 0 1 0 -14 0", "M7.5 21h9M9 18l-1 3M15 18l1 3", "M9 10a3 3 0 0 1 3 -3") }
    private val swords by lazy { line("swords", "M5 3l11 11M19 3L8 14", "M13.5 16.5l4-4M17 15l3 3M10.5 16.5l-4-4M7 15l-3 3") }
    private val sparkles by lazy { line("sparkles", "M11 3l1.9 5.1L18 10l-5.1 1.9L11 17l-1.9-5.1L4 10l5.1-1.9z", "M19 14.5v5M16.5 17h5") }
    private val scale by lazy { line("scale", "M12 4v16M8 20h8M5 7h14", "M5 7l-3 6a3 3 0 0 0 6 0z", "M19 7l-3 6a3 3 0 0 0 6 0z") }
    private val wall by lazy { line("wall", "M3 5h18v14H3z", "M3 9.7h18M3 14.3h18", "M9 5v4.7M15 9.7v4.6M9 14.3V19") }
    private val glove by lazy {
        line("glove", "M7 21v-3.5L4.3 13a1.6 1.6 0 0 1 2.7 -1.7L8.5 13V6a1.5 1.5 0 0 1 3 0v4V4.5a1.5 1.5 0 0 1 3 0V10V5.5a1.5 1.5 0 0 1 3 0V10.5V8a1.5 1.5 0 0 1 3 0v6c0 3.5-2 7-5 7z")
    }
    private val net by lazy { line("net", "M3 19.5V6h18v13.5", "M3 10.5h18M3 15h18", "M7.5 6v13.5M12 6v13.5M16.5 6v13.5") }
    private val trendUp by lazy { line("trendUp", "M3 17l6-6 4 4 8-8", "M15 7h6v6") }
    private val trendDown by lazy { line("trendDown", "M3 7l6 6 4-4 8 8", "M15 17h6v-6") }
    private val shield by lazy { line("shield", "M12 3l8 3v6c0 5-3.5 8-8 9-4.5-1-8-4-8-9V6z") }
    private val snow by lazy { line("snow", "M12 2.5v19M3.8 7.2l16.4 9.6M3.8 16.8l16.4-9.6", "M9.5 4L12 6.5 14.5 4M9.5 20L12 17.5l2.5 2.5") }
    private val watch by lazy { line("watch", "M5 13a7 7 0 1 0 14 0a7 7 0 1 0 -14 0", "M12 13V9.5M10 2.5h4M12 2.5V6M18.5 6.5L20 5") }
    private val megaphone by lazy { line("megaphone", "M3 10v4a1 1 0 0 0 1 1h3l7 4.5v-15L7 9H4a1 1 0 0 0 -1 1z", "M17.5 9a4 4 0 0 1 0 6M20 6.5a8 8 0 0 1 0 11") }
    private val chart by lazy { line("chart", "M4 20h16", "M7 16v-5M12 16V6M17 16V9") }
    private val alert by lazy { line("alert", CIRCLE, "M12 7.5V13M12 16.5h.01") }
    private val miss by lazy { line("miss", CIRCLE, "M5.6 5.6l12.8 12.8") }
    private val undo by lazy { line("undo", "M9 14L4 9l5-5", "M4 9h10.5a5.5 5.5 0 0 1 0 11H11") }
    private val rocket by lazy {
        line("rocket", "M12 15l-3-3a20 20 0 0 1 2-4A12 12 0 0 1 21 3c0 2.7-.8 7.3-6 10.8A20 20 0 0 1 12 15z",
            "M9 12H5s.6-3 2-4c1.6-1.1 4.5 0 4.5 0M12 15v4s3-.6 4-2c1.1-1.6 0-4.5 0-4.5", "M5 16.5c-1.5 1.3-2 4.5-2 4.5s3.2-.5 4.5-2c.7-.8.7-2-.1-2.8a2 2 0 0 0 -2.4 .3z")
    }
    private val assist by lazy { line("assist", "M5 4h14a1 1 0 0 1 1 1v14a1 1 0 0 1 -1 1H5a1 1 0 0 1 -1 -1V5a1 1 0 0 1 1 -1z", "M8.5 16.5l3.5-9 3.5 9M9.8 13.5h4.4") }
    private val plane by lazy { line("plane", "M21 3L10.5 13.5", "M21 3l-6.5 18-4-7.5L3 9.5z") }
    private val heart by lazy { line("heart", "M12 20.5s-8-4.6-8-10.5a4.5 4.5 0 0 1 8 -2.8A4.5 4.5 0 0 1 20 10c0 5.9-8 10.5-8 10.5z") }
    private val boot by lazy { line("boot", "M3 17.5h18v-2.2c0-.8-.6-1.5-1.4-1.6L15 12.5l-2.5-4.5-3 1.2.5 1.8H7.5L6 9.5 3 11z", "M3 20.5h18", "M10 13l1.5-.6M11.5 15l1.5-.6") }
    private val mapIcon by lazy { line("map", "M3 6.5l6-2.5 6 2.5 6-2.5v13l-6 2.5-6-2.5-6 2.5z", "M9 4v13M15 6.5v13") }
    private val pawn by lazy { line("pawn", "M10 6.5a2 2 0 1 0 4 0a2 2 0 1 0 -4 0", "M9.5 10.5h5M10.5 10.5l-1 6h5l-1-6", "M7 20.5h10l-1-4H8z") }
    private val robot by lazy {
        line("robot", "M6 8h12a2 2 0 0 1 2 2v7a2 2 0 0 1 -2 2H6a2 2 0 0 1 -2 -2v-7a2 2 0 0 1 2 -2z", "M12 4.5V8M12 3.5h.01", "M9 12.5v1.5M15 12.5v1.5M2 12.5v2.5M22 12.5v2.5")
    }
    private val pause by lazy { line("pause", "M9 5v14M15 5v14") }
    private val medal by lazy { line("medal", "M7 15a5 5 0 1 0 10 0a5 5 0 1 0 -10 0", "M8 3l3 7M16 3l-3 7M8 3h8") }
    private val radar by lazy { line("radar", CIRCLE, "M12 12l6-5M7.5 12A4.5 4.5 0 0 1 12 7.5M12 12h.01") }

    private val map0 by lazy {
        mapOf(
            "⚽" to Ic.ball, "🥅" to net, "🏆" to Ic.trophy, "🏅" to medal, "⭐" to Ic.star, "★" to Ic.starOn, "✨" to sparkles,
            "🧠" to brain, "🔥" to flame, "🥵" to flame, "⚡" to zap, "🎯" to target, "🟥" to card, "🟨" to card,
            "📋" to clipboard, "🚩" to flag, "🏁" to flag, "🎬" to film, "🎞" to film, "🍀" to clover, "🔄" to Ic.swap, "🔁" to Ic.swap,
            "⇧" to up, "⇩" to downArrow, "🎙" to Ic.mic, "🔮" to crystal, "⚔" to swords, "🔔" to Ic.bell, "⚖" to scale,
            "🧱" to wall, "🧤" to glove, "😬" to net, "🕳" to net, "💪" to trendUp, "📈" to trendUp, "📉" to trendDown, "🛡" to shield,
            "🥶" to snow, "⏱" to watch, "⏰" to watch, "🗣" to megaphone, "🏟" to Ic.stadium, "📊" to chart, "✅" to checkCircle,
            "✓" to check, "✗" to Ic.x, "❌" to Ic.x, "😤" to alert, "😩" to miss, "🙈" to miss, "📡" to Ic.live, "🛰" to radar,
            "↩" to undo, "🚀" to rocket, "🅰" to assist, "✈" to plane, "❤" to heart, "💚" to heart, "💙" to heart, "💛" to heart,
            "🤔" to Ic.help, "🔎" to Ic.search, "🔍" to Ic.search, "👟" to boot, "🗺" to mapIcon, "♟" to pawn, "📍" to Ic.pin,
            "🤖" to robot, "⏸" to pause, "🎤" to Ic.mic, "📅" to Ic.cal, "📺" to Ic.tv, "👥" to Ic.people,
        )
    }

    /** The icon for one emoji (with or without its variation selector), or null when there is none. */
    fun of(e: String): ImageVector? = map0[e.replace("", "")]

    /** The few icons that keep a colour of their own: the cards and the gold star. */
    fun tint(e: String): Color? = when (e.replace("", "")) {
        "🟥" -> Color(0xFFE5434B); "🟨" -> Color(0xFFF5C542); "★" -> Color(0xFFF5C542); else -> null
    }
}

/** True when [s] has an emoji the app draws as an icon. */
internal fun hasIcon(s: String): Boolean {
    var i = 0
    while (i < s.length) {
        val cp = s.codePointAt(i)
        if (cp >= 0x2190 && EmojiIcons.of(String(Character.toChars(cp))) != null) return true
        i += Character.charCount(cp)
    }
    return false
}

/** [s] with each drawn emoji swapped for its icon inline, in [color]. */
internal fun withIcons(s: String, color: Color): Pair<AnnotatedString, Map<String, InlineTextContent>> {
    val inline = HashMap<String, InlineTextContent>()
    val b = AnnotatedString.Builder()
    var i = 0
    while (i < s.length) {
        val cp = s.codePointAt(i); val n = Character.charCount(cp)
        val key = String(Character.toChars(cp))
        val v = if (cp >= 0x2190) EmojiIcons.of(key) else null
        if (v == null) { b.append(s.substring(i, i + n)); i += n; continue }
        val id = "ic:$key"
        b.appendInlineContent(id, key)
        if (id !in inline) {
            val tint = EmojiIcons.tint(key) ?: color
            inline[id] = InlineTextContent(Placeholder(1.15.em, 1.15.em, PlaceholderVerticalAlign.TextCenter)) {
                Icon(v, null, Modifier.fillMaxSize(), tint = tint)
            }
        }
        i += n
        // a variation selector or a joiner after it belongs to the emoji
        while (i < s.length && (s[i] == '' || s[i] == '‍')) i++
    }
    return b.toAnnotatedString() to inline
}

/** An emoji's icon, large, on a soft tile of [tint]: for headers, guides and the what's-new list. */
@Composable
fun EmojiBadge(emoji: String, size: Dp, tint: Color) {
    val v = EmojiIcons.of(emoji)
    val r = RoundedCornerShape(size * 0.32f)
    Box(Modifier.size(size).clip(r).background(Brush.linearGradient(listOf(tint.copy(alpha = 0.22f), tint.copy(alpha = 0.08f)))).border(1.dp, tint.copy(alpha = 0.3f), r),
        contentAlignment = Alignment.Center) {
        if (v != null) Icon(v, null, Modifier.size(size * 0.52f), tint = EmojiIcons.tint(emoji) ?: tint)
        else T(emoji, (size.value * 0.45f).toInt())
    }
}
