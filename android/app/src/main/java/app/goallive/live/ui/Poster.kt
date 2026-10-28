package app.goallive.live.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.graphics.ColorUtils
import app.goallive.live.L
import app.goallive.live.Lang
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/* ---------- line-ups as posters: the match line-up (one team or both) and the team of the week ---------- */

/** The poster designs, in the studio's order. */
internal val POSTER_THEMES = listOf("team", "neon", "gold", "light", "pitch", "minimal")

internal fun posterThemeName(k: String) = when (k) {
    "team" -> L("رنگ تیم"); "neon" -> L("نئون"); "gold" -> L("طلایی"); "light" -> L("روشن"); "pitch" -> L("چمن"); else -> L("مینیمال")
}

/** Someone in the strip under the pitch: a substitute who came on (at [inAt]), the coach, or a league's best player ([note] names the league). */
class BenchMan(val id: Int, val name: String, val inAt: String? = null, val rating: Double? = null, val goals: Int = 0, val assists: Int = 0,
               val yellow: Boolean = false, val coach: Boolean = false, val crest: Int? = null, val note: String? = null)

/** The result in the header: both teams and their goals. */
class PosterScore(val homeId: Int, val home: String, val hg: Int, val awayId: Int, val away: String, val ag: Int)

/** A team's three colours: its dark, its own colour and a highlight; [pale] for a white kit (Real Madrid), whose design turns light. */
private class Palette(val deep: Int, val main: Int, val accent: Int, val pale: Boolean)

private fun palette(tone: Int, accent: Int?): Palette {
    val t = tone or 0xFF000000.toInt()
    val lum = ColorUtils.calculateLuminance(t)
    if (lum > 0.6) return Palette(0xFF0B1430.toInt(), 0xFF1D3A8A.toInt(), accent ?: 0xFFE2B84E.toInt(), true)
    val main = if (lum < 0.03) ColorUtils.blendARGB(t, 0xFFFFFFFF.toInt(), 0.18f) else t
    val deep = ColorUtils.blendARGB(main, 0xFF05070C.toInt(), 0.8f)
    // gold lifts most colours; a team already gold-ish gets white
    val hsl = FloatArray(3).also { ColorUtils.colorToHSL(main, it) }
    val yellowish = hsl[0] in 35f..70f && hsl[1] > 0.4f
    return Palette(deep, main, accent ?: if (yellowish) 0xFFFFFFFF.toInt() else 0xFFEDBB00.toInt(), false)
}

/** What a design paints with. */
private data class PosterLook(
    val ink: Int, val sub: Int,
    val plateTop: Int, val plateBottom: Int, val plateInk: Int, val plateEdge: Int,
    val badge: Int, val badgeInk: Int, val badgeEdge: Int,
    val ring: Int, val discA: Int, val discB: Int,
    val grid: Int, val gridAlpha: Int, val marks: Int,
    val kick: Int, val kickInk: Int, val chip: Int, val chipInk: Int,
    val bench: Int, val benchEdge: Int, val glow: Int, val minimal: Boolean = false, val formInk: Int = 0xE6FFFFFF.toInt(),
)

private fun ca(c: Int, a: Float) = (c and 0x00FFFFFF) or ((a * 255).toInt().coerceIn(0, 255) shl 24)
private const val WHITE = 0xFFFFFFFF.toInt()
private const val GOLD = 0xFFF5C542.toInt()

/** [c] lightened until it reads as a line on a dark page. */
private fun lift(c: Int, min: Double = 0.16): Int {
    var x = c or 0xFF000000.toInt(); var k = 0
    while (ColorUtils.calculateLuminance(x) < min && k++ < 10) x = ColorUtils.blendARGB(x, WHITE, 0.14f)
    return x
}

/** Text on a plate of colour [c]: white on a dark colour, near-black on a light one. */
private fun plateInk(c: Int) = if (ColorUtils.calculateLuminance(c or 0xFF000000.toInt()) > 0.45) 0xFF0B0F14.toInt() else WHITE

/**
 * The name plates in the team's own colour (a soft sheen from top to bottom, its highlight along the foot), the number
 * diamond white with the team's colour, and, when [rings], the rings round the photos and the panel in the team's colour too.
 */
private fun teamPlates(lk: PosterLook, pl: Palette, rings: Boolean): PosterLook {
    val m = pl.main or 0xFF000000.toInt()
    val top = ColorUtils.blendARGB(m, WHITE, 0.16f); val bottom = ColorUtils.blendARGB(m, 0xFF000000.toInt(), 0.22f)
    return lk.copy(plateTop = top, plateBottom = bottom, plateInk = plateInk(ColorUtils.blendARGB(top, bottom, 0.5f)), plateEdge = pl.accent,
        badge = WHITE, badgeInk = if (ColorUtils.calculateLuminance(m) > 0.6) pl.deep else m, badgeEdge = pl.accent,
        ring = if (rings) lift(m, 0.2) else lk.ring)
}

private fun posterLook(theme: String, pl: Palette): PosterLook {
    val d = pl.deep; val m = pl.main; val a = pl.accent
    val dark = PosterLook(WHITE, 0xB8FFFFFF.toInt(), WHITE, 0xFFEEF1F6.toInt(), d, a, d, WHITE, a, a, m, d, a, 46, 0x80FFFFFF.toInt(),
        a, 0xFF0B0F14.toInt(), 0x1AFFFFFF, WHITE, 0x12FFFFFF, 0x24FFFFFF, ca(a, 0.5f))
    return when (theme) {
        "neon" -> PosterLook(WHITE, 0xB8FFFFFF.toInt(), 0xD90A0616.toInt(), 0xD90A0616.toInt(), WHITE, a, 0xFF05030C.toInt(), a, a, a, m, 0xFF05030C.toInt(),
            a, 90, ca(a, 0.6f), a, 0xFF0B0F14.toInt(), 0x1AFFFFFF, WHITE, 0x12FFFFFF, 0x24FFFFFF, ca(a, 0.7f))
        "gold" -> PosterLook(WHITE, 0xB8FFFFFF.toInt(), 0xFFFFF3C4.toInt(), 0xFFB8862B.toInt(), 0xFF1E1405.toInt(), 0xFF7A5410.toInt(), 0xFF120D04.toInt(),
            0xFFE9C46A.toInt(), 0xFFE9C46A.toInt(), 0xFFE9C46A.toInt(), 0xFF4A3510.toInt(), 0xFF120D04.toInt(), 0xFFE9C46A.toInt(), 36, 0x80FFFFFF.toInt(),
            0xFFE9C46A.toInt(), 0xFF0B0F14.toInt(), 0x1AFFFFFF, WHITE, 0x12FFFFFF, 0x24FFFFFF, 0x80E9C46A.toInt())
        "light" -> PosterLook(d, ca(0xFF14181E.toInt(), 0.6f), m, d, WHITE, a, WHITE, d, d, m, WHITE, 0xFFDFE4EC.toInt(), d, 15, ca(0xFF0B1430.toInt(), 0.18f),
            d, WHITE, ca(0xFF0B1430.toInt(), 0.06f), d, WHITE, ca(0xFF0B1430.toInt(), 0.1f), 0x1F000000, formInk = ca(d, 0.85f))
        "pitch" -> teamPlates(PosterLook(WHITE, 0xB8FFFFFF.toInt(), WHITE, 0xFFEEF1F6.toInt(), d, a, d, WHITE, a, WHITE, m, d, WHITE, 0, 0xBFFFFFFF.toInt(),
            WHITE, 0xFF0B2614.toInt(), 0x1AFFFFFF, WHITE, 0x12FFFFFF, 0x24FFFFFF, 0x66000000), pl, rings = false)
        "minimal" -> PosterLook(WHITE, 0xA6FFFFFF.toInt(), 0xF51E1F23.toInt(), 0xF51E1F23.toInt(), WHITE, a, 0xFF0E0F11.toInt(), a, 0x40FFFFFF, 0xD9FFFFFF.toInt(),
            0xFF2A2C31.toInt(), 0xFF141518.toInt(), WHITE, 0, 0x1FFFFFFF, WHITE, 0xFF0E0F11.toInt(), 0x14FFFFFF, WHITE, 0x0FFFFFFF, 0x1FFFFFFF, 0, minimal = true)
        else -> teamPlates(if (pl.pale) PosterLook(d, ca(d, 0.65f), WHITE, 0xFFEEF1F6.toInt(), d, a, d, WHITE, a, a, m, d, a, 46, 0x80FFFFFF.toInt(),
            a, 0xFF0B0F14.toInt(), ca(d, 0.08f), d, ca(d, 0.06f), ca(d, 0.14f), ca(a, 0.5f)) else dark, pl, rings = true)
    }
}

/** The page behind the panel. */
private fun outer(c: Canvas, theme: String, pl: Palette, W: Int, H: Int, mark: Bitmap?) {
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    val w = W.toFloat(); val h = H.toFloat()
    fun lin(vararg cs: Int, x1: Float = w * 0.25f) { p.shader = LinearGradient(0f, 0f, x1, h, cs, null, Shader.TileMode.CLAMP); c.drawRect(0f, 0f, w, h, p); p.shader = null }
    fun blob(x: Float, y: Float, r: Float, col: Int) { p.shader = RadialGradient(x, y, r, col, col and 0x00FFFFFF, Shader.TileMode.CLAMP); c.drawCircle(x, y, r, p); p.shader = null }
    when (theme) {
        "neon" -> { p.color = 0xFF07040F.toInt(); c.drawRect(0f, 0f, w, h, p); blob(w / 2, 0f, h * 0.6f, 0xFF1A0B33.toInt()); blob(w * 0.3f, 200f, 850f, ca(pl.accent, 0.25f)) }
        "gold" -> {
            p.color = 0xFF070502.toInt(); c.drawRect(0f, 0f, w, h, p); blob(w / 2, 0f, h * 0.62f, 0xFF3A2A07.toInt())
            // rays from above the top
            for (k in 0 until 21) {
                val an = Math.toRadians(18.0 + k * 7.2)
                val path = Path().apply {
                    moveTo(w / 2, -40f)
                    lineTo((w / 2 + 1600 * cos(an - 0.03)).toFloat(), (-40 + 1600 * sin(an - 0.03)).toFloat())
                    lineTo((w / 2 + 1600 * cos(an + 0.03)).toFloat(), (-40 + 1600 * sin(an + 0.03)).toFloat()); close()
                }
                p.color = ca(GOLD, if (k % 2 == 1) 0.10f else 0.05f); c.drawPath(path, p)
            }
        }
        "light" -> { p.color = 0xFFF4F1EA.toInt(); c.drawRect(0f, 0f, w, h, p); blob(250f, 190f, 450f, ca(pl.accent, 0.35f)); blob(w + 190, h - 250, 450f, ca(pl.main, 0.25f)) }
        "pitch" -> lin(0xFF0B2614.toInt(), 0xFF06150B.toInt(), x1 = 0f)
        "minimal" -> { p.color = 0xFF0E0F11.toInt(); c.drawRect(0f, 0f, w, h, p) }
        else -> {
            if (pl.pale) lin(0xFFF7F8FB.toInt(), 0xFFDFE3EA.toInt(), 0xFFB9C0CC.toInt())
            else {
                lin(ColorUtils.blendARGB(pl.main, 0xFF05070C.toInt(), 0.55f), pl.deep, ColorUtils.blendARGB(pl.main, 0xFF05070C.toInt(), 0.35f))
                // the club's stripes, faint
                p.color = ca(pl.main, 0.16f)
                var x = 0f; while (x < w) { c.drawRect(x, 0f, x + 90f, h, p); x += 180f }
            }
            mark?.let {
                val wp = Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = if (pl.pale) 26 else 30 }
                c.drawBitmap(it, null, RectF(-280f, -180f, 540f, 640f), wp); c.drawBitmap(it, null, RectF(w - 540f, -180f, w + 280f, 640f), wp)
            }
        }
    }
    grain(c, W, H, theme == "light" || (theme == "team" && pl.pale))
}

/** The panel the players stand on, with its lines; [both] marks the halfway line for two teams. */
private fun panel(c: Canvas, theme: String, pl: Palette, lk: PosterLook, r: RectF, both: Boolean, lower: Palette? = null) {
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    val clip = Path().apply { addRoundRect(r, 44f, 44f, Path.Direction.CW) }
    // the glow and the ring
    if (!lk.minimal) {
        p.color = 0xFF000000.toInt(); p.setShadowLayer(70f, 0f, 30f, if (theme == "light") 0x33000000 else 0x80000000.toInt()); c.drawPath(clip, p); p.clearShadowLayer()
        if (lk.glow != 0) { p.style = Paint.Style.STROKE; p.strokeWidth = 30f; p.maskFilter = android.graphics.BlurMaskFilter(40f, android.graphics.BlurMaskFilter.Blur.NORMAL); p.color = lk.glow; c.drawPath(clip, p); p.maskFilter = null; p.style = Paint.Style.FILL }
    }
    // the fills below take the paint's alpha: back to opaque after the glow
    p.color = 0xFF000000.toInt()
    c.save(); c.clipPath(clip)
    fun fill(vararg cs: Int, top: Float = r.top, bottom: Float = r.bottom) { p.shader = LinearGradient(0f, top, 0f, bottom, cs, null, Shader.TileMode.CLAMP); c.drawRect(r.left, top, r.right, bottom, p); p.shader = null }
    when (theme) {
        "neon" -> fill(0xD9140A28.toInt(), 0xE605030C.toInt())
        "gold" -> fill(0xFF15100A.toInt(), 0xFF0A0703.toInt(), 0xFF050402.toInt())
        "light" -> fill(WHITE, 0xFFF3F5F8.toInt())
        "pitch" -> { var y = r.top; var k = 0; while (y < r.bottom) { p.color = if (k % 2 == 0) 0xFF1E7A42.toInt() else 0xFF1A6E3B.toInt(); c.drawRect(r.left, y, r.right, y + 90f, p); y += 90f; k++ } }
        "minimal" -> { p.color = 0xFF141518.toInt(); c.drawRect(r, p) }
        else -> {
            val lo = lower ?: pl
            if (lower != null) {
                fill(ColorUtils.blendARGB(pl.main, pl.deep, 0.6f), pl.deep, bottom = r.centerY())
                fill(lo.deep, ColorUtils.blendARGB(lo.main, lo.deep, 0.6f), top = r.centerY())
            } else fill(ColorUtils.blendARGB(pl.main, pl.deep, 0.55f), pl.deep, ColorUtils.blendARGB(pl.deep, 0xFF000000.toInt(), 0.5f))
        }
    }
    // a floor in perspective
    if (lk.gridAlpha > 0) {
        p.style = Paint.Style.STROKE; p.color = ca(lk.grid, lk.gridAlpha / 255f)
        val w = r.width(); val h = r.height()
        p.strokeWidth = 1.2f
        for (k in -14..14) c.drawLine(r.left + w / 2 + k * 18, r.top, r.left + w / 2 + k * 110, r.bottom, p)
        p.strokeWidth = 1f
        var y = 30f; var g = 24f
        while (y < h) { c.drawLine(r.left, r.top + y, r.right, r.top + y, p); g *= 1.09f; y += g }
        p.style = Paint.Style.FILL
    }
    // the markings
    p.style = Paint.Style.STROKE; p.strokeWidth = 4f; p.color = lk.marks
    val cx = r.centerX()
    if (both) {
        c.drawLine(r.left, r.centerY(), r.right, r.centerY(), p)
        c.drawCircle(cx, r.centerY(), 150f, p)
        c.drawRect(cx - 280, r.top - 4, cx + 280, r.top + 140, p)
        c.drawRect(cx - 280, r.bottom - 140, cx + 280, r.bottom + 4, p)
    } else {
        c.drawCircle(cx, r.top - 10f, 220f, p)
        c.drawRect(cx - 280, r.bottom - 140, cx + 280, r.bottom + 4, p)
    }
    p.style = Paint.Style.FILL
    c.restore()
    // the edge: one team's colour, or the two clubs' colours meeting at halfway
    p.style = Paint.Style.STROKE
    if (lk.minimal) { p.strokeWidth = 1.5f; p.color = 0x2EFFFFFF }
    else {
        p.strokeWidth = 5f; p.color = lk.ring
        if (theme == "team" && lower != null) {
            val a = lift(pl.main, 0.2); val b = lift(lower.main, 0.2)
            p.shader = LinearGradient(0f, r.top, 0f, r.bottom, intArrayOf(a, a, b, b), floatArrayOf(0f, 0.44f, 0.56f, 1f), Shader.TileMode.CLAMP)
        }
    }
    c.drawPath(clip, p); p.shader = null
}

/* ---------- small drawing helpers ---------- */

/** One line centred on ([cx], [cy]), shrunk (down to [minSize]) then cut to fit [maxW]. */
private fun line(c: Canvas, s: String, cx: Float, cy: Float, size: Float, color: Int, tf: Typeface, maxW: Float, minSize: Float = size * 0.55f): Float {
    if (s.isBlank()) return 0f
    val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; typeface = tf; this.color = color; textAlign = Paint.Align.CENTER }
    while (tp.measureText(s) > maxW && tp.textSize > minSize) tp.textSize -= 1f
    val t = TextUtils.ellipsize(s, tp, maxW, TextUtils.TruncateAt.END).toString()
    val fm = tp.fontMetrics
    c.drawText(t, cx, cy - (fm.ascent + fm.descent) / 2, tp)
    return min(maxW, tp.measureText(t))
}

private fun width(s: String, size: Float, tf: Typeface) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; typeface = tf }.measureText(s)

/** A football: a white disc with the pentagon and its seams. */
private fun posterBall(c: Canvas, cx: Float, cy: Float, r: Float) {
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.color = WHITE; p.setShadowLayer(r * 0.4f, 0f, r * 0.2f, 0x66000000); c.drawCircle(cx, cy, r, p); p.clearShadowLayer()
    p.color = 0xFF111111.toInt()
    val pent = Path(); val k = r * 0.36f
    for (i in 0 until 5) { val a = Math.toRadians(-90.0 + i * 72); val x = cx + k * cos(a).toFloat(); val y = cy + k * sin(a).toFloat(); if (i == 0) pent.moveTo(x, y) else pent.lineTo(x, y) }
    pent.close(); c.drawPath(pent, p)
    p.style = Paint.Style.STROKE; p.strokeWidth = r * 0.14f; p.strokeCap = Paint.Cap.ROUND
    for (i in 0 until 5) { val a = Math.toRadians(-90.0 + i * 72); c.drawLine(cx + k * cos(a).toFloat(), cy + k * sin(a).toFloat(), cx + r * 0.88f * cos(a).toFloat(), cy + r * 0.88f * sin(a).toFloat(), p) }
    p.color = 0xFF111111.toInt(); p.strokeWidth = r * 0.12f; c.drawCircle(cx, cy, r * 0.94f, p)
}

/** An arrow, up (came on) or down (went off), in a box of side [s] at ([x], [y]). */
private fun arrow(c: Canvas, x: Float, y: Float, s: Float, up: Boolean, col: Int) {
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col; style = Paint.Style.STROKE; strokeWidth = s * 0.14f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    val cx = x + s / 2; val t = y + s * 0.2f; val b = y + s * 0.8f
    c.drawLine(cx, t, cx, b, p)
    val path = Path()
    if (up) { path.moveTo(cx - s * 0.26f, t + s * 0.26f); path.lineTo(cx, t); path.lineTo(cx + s * 0.26f, t + s * 0.26f) }
    else { path.moveTo(cx - s * 0.26f, b - s * 0.26f); path.lineTo(cx, b); path.lineTo(cx + s * 0.26f, b - s * 0.26f) }
    c.drawPath(path, p)
}

/** A rating in its colour, its right edge at [right]. */
private fun rateChip(c: Canvas, r: Double, right: Float, top: Float, size: Float, tf: Typeface) {
    val s = fa(dec(r, 1)); val w = width(s, size, tf) + size * 0.8f; val h = size * 1.45f
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ratingColor(r); setShadowLayer(10f, 0f, 5f, 0x59000000) }
    c.drawRoundRect(RectF(right - w, top, right, top + h), size * 0.5f, size * 0.5f, p)
    line(c, s, right - w / 2, top + h / 2, size, WHITE, tf, w)
}

/** A round photo of [d] at ([cx], top [top]) on the design's disc, ringed; the best player's ring is gold and glows. */
private fun photo(c: Canvas, face: Bitmap?, cx: Float, top: Float, d: Float, lk: PosterLook, best: Boolean, ring: Float = 4f) {
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    val cy = top + d / 2; val r = d / 2
    if (best) { p.shader = RadialGradient(cx, cy, r * 1.6f, intArrayOf(0xAAF5C542.toInt(), 0x00F5C542), null, Shader.TileMode.CLAMP); c.drawCircle(cx, cy, r * 1.6f, p); p.shader = null }
    p.color = 0xFF000000.toInt(); p.setShadowLayer(26f, 0f, 14f, if (lk.minimal) 0 else 0x66000000); c.drawCircle(cx, cy, r, p); p.clearShadowLayer()
    p.shader = RadialGradient(cx, top + d * 0.3f, d * 0.75f, lk.discA, lk.discB, Shader.TileMode.CLAMP); c.drawCircle(cx, cy, r, p); p.shader = null
    face?.let { f ->
        c.save(); c.clipPath(Path().apply { addCircle(cx, cy, r, Path.Direction.CW) })
        c.drawBitmap(f, null, RectF(cx - r, top + d * 0.06f, cx + r, top + d * 1.06f), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        c.restore()
    }
    p.style = Paint.Style.STROKE; p.strokeWidth = if (best) ring + 1.5f else ring; p.color = if (best) GOLD else lk.ring
    c.drawCircle(cx, cy, r + p.strokeWidth / 2, p)
}

/** One player: the photo, a name plate with the shirt-number diamond, and his match in small marks round the photo. */
private fun man(c: Canvas, m: PitchMan, cx: Float, top: Float, d: Float, lk: PosterLook, best: Boolean, face: Bitmap?, crest: Bitmap?, tf: Typeface) {
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    val pw = d + 34; val ph = Math.round(d * 0.3f).toFloat(); val fs = Math.round(ph * 0.5f).toFloat(); val nb = Math.round(ph * 1.12f).toFloat()
    val left = cx - pw / 2; val dl = cx - d / 2
    photo(c, face, cx, top, d, lk, best)
    // the badge shows the number; the team of the week has none, so its rating goes there instead
    val badge = m.number.ifEmpty { m.rating?.let { dec(it, 1) } ?: "" }
    if (m.number.isNotEmpty()) m.rating?.let { rateChip(c, it, cx + d / 2 + d * 0.13f * 0.3f, top - d * 0.13f * 0.2f, Math.round(d * 0.13f).toFloat(), tf) }
    // goals and assists down the left of the photo
    val bs = Math.round(d * 0.17f).toFloat()
    var ey = top - bs * 0.1f
    val ex = dl - bs * 0.15f + bs / 2
    repeat(min(3, m.goals)) { posterBall(c, ex, ey + bs / 2, bs / 2); ey += bs + 3 }
    repeat(min(2, m.assists)) {
        p.color = 0xFF14B866.toInt(); p.setShadowLayer(8f, 0f, 3f, 0x66000000); c.drawCircle(ex, ey + bs / 2, bs / 2, p); p.clearShadowLayer()
        line(c, if (Lang.en) "A" else "پ", ex, ey + bs / 2, bs * 0.55f, WHITE, tf, bs); ey += bs + 3
    }
    // a club badge (the team of the week)
    if (crest != null) {
        val s = Math.round(d * 0.3f).toFloat(); val bx = dl - s * 0.2f + s / 2; val by = top + d - s * 1.55f + s / 2
        p.color = WHITE; p.setShadowLayer(12f, 0f, 5f, 0x59000000); c.drawCircle(bx, by, s / 2, p); p.clearShadowLayer()
        c.drawBitmap(crest, null, RectF(bx - s * 0.37f, by - s * 0.37f, bx + s * 0.37f, by + s * 0.37f), Paint(Paint.FILTER_BITMAP_FLAG))
    }
    // a card, and the minute he went off
    val cs = Math.round(d * 0.12f).toFloat()
    var fx = dl - cs * 0.3f; val fy = top + d * 0.6f
    if (m.red || m.yellow) {
        p.color = if (m.red) 0xFFE5434B.toInt() else GOLD; p.setShadowLayer(6f, 0f, 2f, 0x73000000)
        c.drawRoundRect(RectF(fx, fy, fx + cs * 0.72f, fy + cs), 3f, 3f, p); p.clearShadowLayer(); fx += cs * 0.72f + 4
    }
    m.outAt?.takeIf { it.isNotBlank() }?.let { t ->
        val sz = cs * 0.95f; val s = fa(t) + "′"; val h = sz * 1.5f; val w = width(s, sz, tf) + sz * 1.6f
        p.color = 0xD10A0A0E.toInt(); c.drawRoundRect(RectF(fx, fy, fx + w, fy + h), sz * 0.5f, sz * 0.5f, p)
        arrow(c, fx + sz * 0.15f, fy + (h - sz * 1.05f) / 2, sz * 1.05f, false, 0xFFFF6B6B.toInt())
        line(c, s, fx + sz * 1.2f + (w - sz * 1.2f) / 2 - sz * 0.15f, fy + h / 2, sz, WHITE, tf, w)
    }
    // the plate
    val pt = top + d - ph * 0.42f
    val pr = RectF(left, pt, left + pw, pt + ph)
    if (!lk.minimal) { p.color = 0xFF000000.toInt(); p.setShadowLayer(24f, 0f, 10f, 0x59000000); c.drawRoundRect(pr, 14f, 14f, p); p.clearShadowLayer() }
    c.save(); c.clipPath(Path().apply { addRoundRect(pr, 14f, 14f, Path.Direction.CW) })
    p.shader = LinearGradient(0f, pt, 0f, pt + ph, lk.plateTop, lk.plateBottom, Shader.TileMode.CLAMP); c.drawRect(pr, p); p.shader = null
    if (!lk.minimal) { p.color = lk.plateEdge; c.drawRect(pr.left, pr.bottom - 4, pr.right, pr.bottom, p) }
    c.restore()
    // the name between the plate's start and the diamond; the captain's mark beside it
    val pad = nb * 1.02f
    val capD = if (m.captain) fs * 1.25f else 0f
    val room = pw - pad - 12 - (if (m.captain) fs * 1.3f else 0f)
    val nameL = if (Lang.en) left + 10 + (if (m.captain) capD + 8 else 0f) else left + 10
    val nameR = if (Lang.en) left + pw - pad else left + pw - pad - (if (m.captain) capD + 8 else 0f)
    val nw = line(c, m.name, (nameL + nameR) / 2, pt + ph / 2, fs, lk.plateInk, tf, room)
    if (m.captain) {
        val ccx = if (Lang.en) (nameL + nameR) / 2 - nw / 2 - 8 - capD / 2 else (nameL + nameR) / 2 + nw / 2 + 8 + capD / 2
        p.shader = LinearGradient(0f, pt + ph / 2 - capD / 2, 0f, pt + ph / 2 + capD / 2, 0xFFFFE08A.toInt(), 0xFFE0A21B.toInt(), Shader.TileMode.CLAMP)
        c.drawCircle(ccx, pt + ph / 2, capD / 2, p); p.shader = null
        line(c, if (Lang.en) "C" else "ک", ccx, pt + ph / 2, fs * 0.85f, 0xFF2A1A00.toInt(), tf, capD)
    }
    // the diamond
    if (badge.isNotEmpty()) {
        val bx = left + pw + nb * 0.22f - nb / 2; val by = pt + ph / 2
        c.save(); c.rotate(45f, bx, by)
        val br = RectF(bx - nb / 2, by - nb / 2, bx + nb / 2, by + nb / 2)
        p.color = lk.badge; if (!lk.minimal) p.setShadowLayer(18f, 0f, 8f, 0x59000000); c.drawRoundRect(br, 10f, 10f, p); p.clearShadowLayer()
        p.style = Paint.Style.STROKE; p.strokeWidth = 3f; p.color = lk.badgeEdge; c.drawRoundRect(br, 10f, 10f, p); p.style = Paint.Style.FILL
        c.restore()
        line(c, fa(badge), bx, by, nb * 0.44f, lk.badgeInk, tf, nb * 0.95f)
    }
}

/** The eleven in a box: [PitchMan]'s x / y (as the in-app pitch has them) spread over it, forwards at the top, or at the bottom when [flip]. */
private fun placeAll(c: Canvas, men: List<PitchMan>, box: RectF, dMax: Float, lk: PosterLook, faces: Map<Int, Bitmap?>, crests: Map<Int, Bitmap?>,
                     tf: Typeface, clk: Clock, start: Float, flip: Boolean = false) {
    if (men.isEmpty()) return
    // the lines of the formation: players whose heights are close stand on one row, forwards first
    val rows = ArrayList<MutableList<PitchMan>>()
    for (m in men.sortedBy { it.y }) {
        val last = rows.lastOrNull()
        if (last != null && m.y - last.last().y < 0.075f) last += m else rows += mutableListOf(m)
    }
    val r = rows.size
    // a photo size that leaves every plate, diamond and mark clear of its neighbours, across and down
    val widest = rows.maxOf { it.size }
    val cell = (box.width() - 40) / widest
    val d = minOf(dMax, cell * 0.7f, if (r > 1) box.height() / (1.24f * (r - 1) + 1.18f) else dMax)
    val ph = Math.round(d * 0.3f); val block = d + ph * 0.6f
    val best = men.firstOrNull { it.star }
    var n = 0
    // the keeper's row first, so the team builds up from the back
    for ((ri, row) in rows.withIndex().reversed()) {
        val k = if (r > 1) ri / (r - 1f) else 0.5f
        val top = box.top + (if (flip) 1 - k else k) * (box.height() - block)
        val across = (box.width() - 40) / row.size
        row.sortedBy { it.x }.forEachIndexed { i, m ->
            val u = (i + 0.5f) * across
            // and never past the panel's edge: the marks on the left of the photo, the diamond on the right of the plate
            val lo = box.left + d * 0.72f + 12; val hi = box.right - (d + 34) / 2 - d * 0.16f - 14
            val cx = (box.left + 20 + if (flip) box.width() - 40 - u else u).coerceIn(lo, maxOf(lo, hi))
            c.pop(clk.at(start + n++ * 0.1f, 0.45f), cx, top + d / 2) {
                man(c, m, cx, top, d, lk, m === best, faces[m.id], m.crest?.let { crests[it] }, tf)
            }
        }
    }
}

/** A capsule: the logo on a white disc and [s]. */
private fun posterCapsule(c: Canvas, s: String, cx: Float, top: Float, size: Float, bg: Int, ink: Int, tf: Typeface, logo: Bitmap?, tail: String = "", tailTf: Typeface? = null): Float {
    val lg = size * 1.3f; val tw = width(s, size, tf); val tail2 = if (tail.isEmpty()) 0f else width(tail, size * 0.78f, tailTf ?: tf) + 12
    val w = lg + 12 + tw + tail2 + size * 1.6f; val h = lg + 16
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bg }
    val r = RectF(cx - w / 2, top, cx + w / 2, top + h)
    c.drawRoundRect(r, h / 2, h / 2, p)
    // the logo at the reading start
    val lx = if (Lang.en) r.left + size * 0.7f + lg / 2 else r.right - size * 0.7f - lg / 2
    p.color = WHITE; c.drawCircle(lx, top + h / 2, lg / 2, p)
    logo?.let { c.drawBitmap(it, null, RectF(lx - lg / 2 + 2, top + h / 2 - lg / 2 + 2, lx + lg / 2 - 2, top + h / 2 + lg / 2 - 2), Paint(Paint.FILTER_BITMAP_FLAG)) }
    val sx = if (Lang.en) lx + lg / 2 + 12 + tw / 2 else lx - lg / 2 - 12 - tw / 2
    line(c, s, sx, top + h / 2, size, ink, tf, tw + 4)
    if (tail.isNotEmpty()) {
        val tx = if (Lang.en) sx + tw / 2 + 12 + (tail2 - 12) / 2 else sx - tw / 2 - 12 - (tail2 - 12) / 2
        line(c, tail, tx, top + h / 2, size * 0.78f, ca(ink, 0.7f), tailTf ?: tf, tail2)
    }
    return h
}

/** The formation's numbers, left to right as on a team sheet. */
private fun formationText(c: Canvas, f: String, x: Float, top: Float, size: Float, color: Int, tf: Typeface) {
    if (f.isBlank()) return
    val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; typeface = tf; this.color = color; letterSpacing = 0.08f }
    var cx = x
    f.split("-").forEachIndexed { i, part ->
        if (i > 0) { c.drawText(" - ", cx, top + size, tp); cx += tp.measureText(" - ") }
        c.drawText(part, cx, top + size, tp); cx += tp.measureText(part)
    }
}

/** The result: both crests, names and the score between them. */
private fun scoreChip(c: Canvas, s: PosterScore, cx: Float, top: Float, size: Float, lk: PosterLook, tf: Typeface, crests: Map<Int, Bitmap?>) {
    val small = size * 0.75f
    val hn = teamName(s.homeId, s.home); val an = teamName(s.awayId, s.away)
    val score = fa("${s.hg} - ${s.ag}")
    val wh = width(hn, small, tf); val wa = width(an, small, tf); val ws = width(score, size, tf)
    val gap = 14f
    val w = size * 2 + wh + wa + ws + gap * 4 + 44
    val h = size + 16
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = lk.chip }
    c.drawRoundRect(RectF(cx - w / 2, top, cx + w / 2, top + h), h / 2, h / 2, p)
    // home at the reading start: on the right in Persian
    val dir = if (Lang.en) 1f else -1f
    var x = cx - dir * (w / 2 - 22)
    val mid = top + h / 2
    fun crest(id: Int) { crests[id]?.let { b -> val cx2 = x + dir * size / 2; c.drawBitmap(b, null, RectF(cx2 - size / 2, mid - size / 2, cx2 + size / 2, mid + size / 2), Paint(Paint.FILTER_BITMAP_FLAG)) }; x += dir * (size + gap) }
    fun word(t: String, sz: Float, ww: Float) { line(c, t, x + dir * ww / 2, mid, sz, lk.chipInk, tf, ww + 4); x += dir * (ww + gap) }
    crest(s.homeId); word(hn, small, wh)
    // the score reads home first: right to left in Persian, so draw it as "away - home" left to right
    line(c, if (Lang.en) score else fa("${s.ag} - ${s.hg}"), x + dir * ws / 2, mid, size, lk.chipInk, tf, ws + 4); x += dir * (ws + gap)
    word(an, small, wa); crest(s.awayId)
}

/** The strip under the pitch: the substitutes who came on and the coach, or each league's best. */
private fun benchStrip(c: Canvas, items: List<BenchMan>, label: String, r: RectF, lk: PosterLook, faces: Map<Int, Bitmap?>, crests: Map<Int, Bitmap?>,
                       tf: Typeface, bold: Typeface, clk: Clock, start: Float) {
    if (items.isEmpty()) return
    c.enter(clk.at(start - 0.1f), r.top - 50, r.top) {
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 26f; typeface = tf; color = lk.sub; textAlign = if (Lang.en) Paint.Align.LEFT else Paint.Align.RIGHT }
        c.drawText(label, if (Lang.en) r.left + 6 else r.right - 6, r.top - 16, tp)
    }
    val n = items.size; val gap = 14f; val cw = (r.width() - gap * (n - 1)) / n
    val d = min(110f, cw - 40)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    items.forEachIndexed { i, b ->
        val left = if (Lang.en) r.left + i * (cw + gap) else r.right - (i + 1) * cw - i * gap
        val cx = left + cw / 2
        c.enter(clk.at(start + i * 0.1f), r.top, r.bottom, 50f) {
            val card = RectF(left, r.top, left + cw, r.bottom)
            p.color = lk.bench; c.drawRoundRect(card, 24f, 24f, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = 1.5f; p.color = lk.benchEdge; c.drawRoundRect(card, 24f, 24f, p); p.style = Paint.Style.FILL
            photo(c, faces[b.id], cx, r.top + 14, d, lk, false, 3f)
            b.rating?.let { rateChip(c, it, cx + d / 2 + 12, r.top + 8, 18f, tf) }
            var ey = r.top + 8; val ex = cx - d / 2 - 6 + 12
            repeat(min(2, b.goals)) { posterBall(c, ex, ey + 12, 12f); ey += 27 }
            repeat(min(2, b.assists)) { p.color = 0xFF14B866.toInt(); c.drawCircle(ex, ey + 12, 12f, p); line(c, if (Lang.en) "A" else "پ", ex, ey + 12, 13f, WHITE, tf, 24f); ey += 27 }
            b.crest?.let { id -> crests[id]?.let { cb ->
                val bx = cx - d / 2 + 11; val by = r.top + 14 + d - 17
                p.color = WHITE; c.drawCircle(bx, by, 19f, p); c.drawBitmap(cb, null, RectF(bx - 14, by - 14, bx + 14, by + 14), Paint(Paint.FILTER_BITMAP_FLAG))
            } }
            line(c, b.name, cx, r.top + d + 38, 22f, lk.ink, tf, cw - 16, 14f)
            val ly = r.top + d + 70
            when {
                b.coach -> line(c, L("سرمربی"), cx, ly, 19f, lk.sub, bold, cw - 12)
                b.note != null -> line(c, b.note, cx, ly, 18f, lk.sub, bold, cw - 12)
                b.inAt != null -> {
                    val s = fa(b.inAt) + "′"; val sw = width(s, 19f, tf)
                    val total = 20 + sw + (if (b.yellow) 18 else 0)
                    var x = cx - total / 2
                    arrow(c, x, ly - 10, 20f, true, 0xFF39D98A.toInt()); x += 20
                    line(c, s, x + sw / 2, ly, 19f, 0xFF39D98A.toInt(), tf, sw + 4); x += sw + 6
                    if (b.yellow) { p.color = GOLD; c.drawRoundRect(RectF(x, ly - 8, x + 12, ly + 8), 2f, 2f, p) }
                }
            }
        }
    }
}

/** The app's mark at the bottom: the logo, the name and its line. */
private fun posterBrand(c: Canvas, clk: Clock, cx: Float, top: Float, lk: PosterLook, tf: Typeface, bold: Typeface, logo: Bitmap?, small: Boolean = false) {
    c.brand(clk, cx, top + 28) {
        posterCapsule(c, L("گل‌لایو"), cx, top, if (small) 24f else 28f, lk.chip, lk.chipInk, tf, logo, L("فوتبال به فارسی"), bold)
    }
}

/* ---------- the posters ---------- */

/** A line-up as a poster in [o]'s design and size; two teams on one pitch when [o] asks and the rival's line-up is known. */
internal fun posterScene(l: Story.Lineup, o: CardOptions, a: Art): Scene {
    val theme = o.theme.takeIf { it in POSTER_THEMES } ?: "team"
    val tf = a.display ?: a.black
    val rival = l.rival
    return if (o.both && rival != null) bothPoster(l, rival, theme, o, a, tf) else onePoster(l, theme, o, a, tf)
}

private fun onePoster(l: Story.Lineup, theme: String, o: CardOptions, a: Art, tf: Typeface): Scene {
    val post = o.format != CardFormat.STORY
    val W = 1080; val H = if (post) 1350 else 1920
    val pl = palette(l.tone, l.accent); val lk = posterLook(theme, pl)
    val badge = l.crest?.let { a.crests[it] } ?: l.league?.let { a.leagues[it] }
    val chip = l.score != null
    val bench = l.bench
    val cap = o.caption.isNotBlank()
    // where things go
    val px = 40f; val pw = 1000f
    val py = if (post) (if (chip) 300f else 250f) else (if (chip) 372f else 320f)
    val bottom = when {
        post -> 1250f - (if (cap) 60f else 0f)
        bench.isNotEmpty() -> 1508f - (if (cap) 80f else 0f)
        l.starLine != null -> 1690f - (if (cap) 80f else 0f)
        else -> 1780f - (if (cap) 80f else 0f)
    }
    val pr = RectF(px, py, px + pw, bottom)
    val bg = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
    val bc = Canvas(bg)
    outer(bc, theme, pl, W, H, if (theme == "team") badge else null)
    panel(bc, theme, pl, lk, pr, false)
    return Scene(W, H, bg, pl.main, pl.accent) { c, clk ->
        // the header
        c.enter(clk.at(0.1f), 0f, py) {
            var y = if (post) 26f else 40f
            y += posterCapsule(c, l.kicker.ifEmpty { L("ترکیب اصلی") }, W / 2f, y, if (post) 22f else 26f, lk.kick, lk.kickInk, tf, a.logo) + (if (post) 10f else 18f)
            val ts = if (post) 62f else 84f; val cs = if (post) 70f else 96f
            val tw = min(W - 220f - (if (badge != null) cs else 0f), width(l.title, ts, tf))
            val row = tw + (if (badge != null) cs + 20 else 0f)
            val tx = W / 2f + (if (badge != null) (if (Lang.en) 1 else -1) * (cs + 20) / 2 else 0f)
            badge?.let { b ->
                val bx = if (Lang.en) W / 2f - row / 2 else W / 2f + row / 2 - cs
                c.drawBitmap(b, null, RectF(bx, y + (ts * 1.1f - cs) / 2, bx + cs, y + (ts * 1.1f + cs) / 2), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
            }
            line(c, l.title, tx, y + ts * 0.55f, ts, lk.ink, tf, tw + 2)
            y += ts * 1.1f + (if (post) 4f else 10f)
            text(c, l.sub, W / 2f, y, if (post) 24f else 28f, lk.sub, a.bold, W - 120)
            y += if (post) 36f else 44f
            l.score?.let { scoreChip(c, it, W / 2f, y, if (post) 30f else 36f, lk, tf, a.crests) }
        }
        // the formation in the panel's corner, then the players
        c.enter(clk.at(0.4f), py, py + 80) { formationText(c, l.formation, pr.left + 28, pr.top + 18, if (post) 34f else 40f, lk.formInk, a.digits ?: tf) }
        placeAll(c, l.men, RectF(pr.left, pr.top + (if (post) 56f else 70f), pr.right, pr.bottom - (if (post) 14f else 20f)), if (post) 150f else 176f,
            lk, a.faces, a.crests, tf, clk, 0.6f)
        // under the pitch
        var y = pr.bottom
        if (post) {
            if (cap) c.enter(clk.at(2.6f), y, y + 60) { captionLine(c, o.caption, y + 4, lk.ink, tf, W) }
            bench.firstOrNull { it.coach }?.let { co ->
                c.enter(clk.at(2.2f), 1260f, 1340f) {
                    val d = 62f; val cx = if (Lang.en) 56 + d / 2 else W - 56 - d / 2
                    photo(c, a.faces[co.id], cx, 1270f, d, lk, false, 3f)
                    val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 26f; typeface = tf; color = lk.ink; textAlign = if (Lang.en) Paint.Align.LEFT else Paint.Align.RIGHT }
                    val nx = if (Lang.en) cx + d / 2 + 12 else cx - d / 2 - 12
                    c.drawText(TextUtils.ellipsize(co.name, tp, 320f, TextUtils.TruncateAt.END).toString(), nx, 1298f, tp)
                    tp.textSize = 17f; tp.typeface = a.bold; tp.color = lk.sub; c.drawText(L("سرمربی"), nx, 1324f, tp)
                }
            }
            val bx = if (Lang.en) W - 250f else 250f
            posterBrand(c, clk, bx, 1276f, lk, tf, a.bold, a.logo, small = true)
        } else {
            if (bench.isNotEmpty()) {
                y += 72
                benchStrip(c, bench, l.benchLabel.ifEmpty { L("تعویض‌ها و سرمربی") }, RectF(40f, y, 1040f, y + 210), lk, a.faces, a.crests, tf, a.bold, clk, 2.0f)
                y += 210
            } else l.starLine?.let { s ->
                c.enter(clk.at(2.2f), y, y + 100) { pill(c, "★ $s", W / 2f, y + 34, 32f, GOLD, 0x26F5C542, tf, 960, 0x66F5C542) }
                y += 120
            }
            if (cap) c.enter(clk.at(2.6f), y, y + 80) { captionLine(c, o.caption, y + 14, if (theme == "light" || (theme == "team" && pl.pale)) lk.ink else GOLD, tf, W) }
            posterBrand(c, clk, W / 2f, 1836f, lk, tf, a.bold, a.logo)
        }
    }
}

/** Both teams on one pitch: the home side at the top attacking down, the away side at the bottom attacking up. */
private fun bothPoster(l: Story.Lineup, r: Story.Lineup, theme: String, o: CardOptions, a: Art, tf: Typeface): Scene {
    val W = 1080; val H = 1920
    val home = if (l.home) l else r; val away = if (l.home) r else l
    val ph = palette(home.tone, null); val pa = palette(away.tone, null)
    val lk = posterLook(theme, if (theme == "team") pa.let { Palette(it.deep, it.main, 0xFFE2B84E.toInt(), false) } else pa)
    // each half's plates, diamonds and rings in its own club's colours
    val lkHome = posterLook(theme, if (theme == "team") ph.let { Palette(it.deep, it.main, 0xFFE2B84E.toInt(), false) } else ph)
    val cap = o.caption.isNotBlank()
    val pr = RectF(40f, 290f, 1040f, if (cap) 1752f else 1830f)
    val bg = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
    val bc = Canvas(bg)
    if (theme == "team") {
        // the page split in the two clubs' colours
        val p = Paint()
        p.shader = LinearGradient(0f, 0f, 0f, H.toFloat(), intArrayOf(ph.deep, ColorUtils.blendARGB(ph.main, ph.deep, 0.4f), ColorUtils.blendARGB(pa.main, pa.deep, 0.4f), pa.deep),
            floatArrayOf(0f, 0.48f, 0.52f, 1f), Shader.TileMode.CLAMP)
        bc.drawRect(0f, 0f, W.toFloat(), H.toFloat(), p)
        grain(bc, W, H, false)
        panel(bc, theme, ph, lk, pr, true, pa)
    } else {
        outer(bc, theme, pa, W, H, null)
        panel(bc, theme, pa, lk, pr, true)
    }
    val hdrInk = if (theme == "light") lk.ink else WHITE
    return Scene(W, H, bg, ph.main, pa.main) { c, clk ->
        c.enter(clk.at(0.1f), 0f, pr.top) {
            var y = 34f
            y += posterCapsule(c, l.kicker.ifEmpty { L("ترکیب دو تیم") }, W / 2f, y, 26f, lk.kick, lk.kickInk, tf, a.logo) + 16
            val s = l.score
            val mid = y + 50
            val dir = if (Lang.en) -1f else 1f // home on the reading start
            val hx = W / 2f + dir * 300; val ax = W / 2f - dir * 300
            for ((t, x) in listOf(home to hx, away to ax)) {
                t.crest?.let { a.crests[it] }?.let { b -> c.drawBitmap(b, null, RectF(x + dir * (if (t === home) 130 else -130) - 46, mid - 46, x + dir * (if (t === home) 130 else -130) + 46, mid + 46), Paint(Paint.FILTER_BITMAP_FLAG)) }
                line(c, t.teamName, x - dir * (if (t === home) 30 else -30), mid, 44f, hdrInk, tf, 230f, 26f)
            }
            if (s != null) line(c, if (Lang.en) fa("${s.hg} - ${s.ag}") else fa("${s.ag} - ${s.hg}"), W / 2f, mid, 84f, hdrInk, tf, 300f)
            else line(c, if (Lang.en) "vs" else "مقابل", W / 2f, mid, 50f, hdrInk, tf, 300f)
            text(c, l.sub, W / 2f, mid + 62, 26f, if (theme == "light") lk.sub else 0xC0FFFFFF.toInt(), a.bold, W - 120)
        }
        val half = pr.height() / 2
        c.enter(clk.at(0.4f), pr.top, pr.bottom) {
            formationText(c, home.formation, pr.left + 22, pr.top + 14, 30f, lk.formInk, a.digits ?: tf)
            formationText(c, away.formation, pr.left + 22, pr.bottom - 50, 30f, lk.formInk, a.digits ?: tf)
        }
        placeAll(c, home.men, RectF(pr.left, pr.top + 14, pr.right, pr.top + half - 38), 108f, lkHome, a.faces, a.crests, tf, clk, 0.6f, flip = true)
        placeAll(c, away.men, RectF(pr.left, pr.top + half + 38, pr.right, pr.bottom - 14), 108f, lk, a.faces, a.crests, tf, clk, 1.8f)
        if (cap) c.enter(clk.at(2.8f), pr.bottom, pr.bottom + 80) { captionLine(c, o.caption, pr.bottom + 16, if (theme == "light") lk.ink else GOLD, tf, W) }
        posterBrand(c, clk, W / 2f, 1848f, lk, tf, a.bold, a.logo)
    }
}
