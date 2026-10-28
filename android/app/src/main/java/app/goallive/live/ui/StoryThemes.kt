package app.goallive.live.ui

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import app.goallive.live.L
import app.goallive.live.Lang
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * The designs of the share pictures and stories. Each one paints a still background once ([background]) and, for the moving
 * ones, a live layer drawn on every video frame ([themeLive]): embers rising, snow falling, stars twinkling, camera flashes in
 * the crowd, a neon grid running towards the viewer, a shimmer across a hologram. A still picture shows the live layer frozen.
 */

val CARD_THEMES = listOf("teams", "aurora", "holo", "fire", "ice", "stadium", "comic", "neon", "night", "gold", "pitch", "light")

internal fun themeName(k: String) = when (k) {
    "teams" -> L("رنگ تیم‌ها"); "aurora" -> L("شفق"); "holo" -> L("هولوگرام"); "fire" -> L("آتش"); "ice" -> L("یخ")
    "stadium" -> L("ورزشگاه"); "comic" -> L("کمیک"); "neon" -> L("نئون"); "night" -> L("شب"); "gold" -> L("طلایی"); "pitch" -> L("چمن")
    else -> L("روشن")
}

/** Light designs take dark writing (and lighter grain and watermarks). */
internal fun lightTheme(t: String) = t == "light" || t == "comic"

/** The colours a design gives the writing and the panels on it; [comic] draws panels as white boxes with thick black edges. */
internal class CardLook(val ink: Int, val sub: Int, val panel: Int, val accent: Int, val stroke: Int, val comic: Boolean = false)

/** A small look at each design, for the picker until the real thumbnails are drawn. */
internal fun themeSwatch(t: String, hc: Int, ac: Int): Brush = when (t) {
    "teams" -> Brush.linearGradient(listOf(Color(hc), Color(0xFF06080C), Color(ac)))
    "aurora" -> Brush.verticalGradient(listOf(Color(0xFF2CF5B5), Color(0xFF6D3CF0), Color(0xFF02040A)))
    "holo" -> Brush.linearGradient(listOf(Color(0xFF6CF3FF), Color(0xFFFF6CE0), Color(0xFFFFE66C), Color(0xFF8B6CFF)))
    "fire" -> Brush.verticalGradient(listOf(Color(0xFF0B0302), Color(0xFF7A1A04), Color(0xFFFFA020)))
    "ice" -> Brush.verticalGradient(listOf(Color(0xFFBFF3FF), Color(0xFF1B5E86), Color(0xFF041626)))
    "stadium" -> Brush.verticalGradient(listOf(Color(0xFF0B1630), Color(0xFF050A16), Color(0xFF0E3A1E)))
    "comic" -> Brush.linearGradient(listOf(Color(0xFFFFD60A), Color(0xFFFFC300), Color(0xFFE5172F)))
    "night" -> Brush.verticalGradient(listOf(Color(0xFF1A2350), Color(0xFF05060D)))
    "gold" -> Brush.verticalGradient(listOf(Color(0xFF6B4E0C), Color(0xFF0D0A04)))
    "light" -> Brush.verticalGradient(listOf(Color(0xFFFFFFFF), Color(0xFFEDE7DA)))
    "pitch" -> Brush.verticalGradient(listOf(Color(0xFF1F7A45), Color(0xFF0E3D22)))
    else -> Brush.linearGradient(listOf(Color(0xFF05050A), Color(hc).copy(alpha = 0.7f), Color(0xFF05050A), Color(ac).copy(alpha = 0.7f)))
}

/* ---------- small tools ---------- */

/** [c] with alpha [a]. */
internal fun ca(c: Int, a: Int) = (c and 0x00FFFFFF) or (a.coerceIn(0, 255) shl 24)

/** A steady pseudo-random number in 0..1 for particle [i], property [k]: the same every frame, so things move smoothly. */
internal fun rnd(i: Int, k: Int): Float { val v = sin(i * 12.9898 + k * 78.233) * 43758.5453; return (v - floor(v)).toFloat() }

private fun mix(a: Int, b: Int, k: Float) = androidx.core.graphics.ColorUtils.blendARGB(a, b, k.coerceIn(0f, 1f))

/** A soft round light. */
private fun glow(c: Canvas, p: Paint, x: Float, y: Float, r: Float, color: Int, a: Int) {
    if (r <= 1f) return
    p.shader = RadialGradient(x, y, r, intArrayOf(ca(color, a), ca(color, (a * 0.5f).toInt()), ca(color, (a * 0.15f).toInt()), ca(color, 0)),
        floatArrayOf(0f, 0.3f, 0.65f, 1f), Shader.TileMode.CLAMP)
    c.drawCircle(x, y, r, p); p.shader = null
}

/** Darker towards the edges, so the eye goes to the middle. */
private fun vignette(c: Canvas, p: Paint, w: Float, h: Float, a: Int) {
    p.shader = RadialGradient(w / 2, h * 0.45f, h * 0.78f, intArrayOf(0x00000000, 0x00000000, ca(0, a)), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
    c.drawRect(0f, 0f, w, h, p); p.shader = null
}

/** Darker at the bottom, so the brand reads. */
private fun floorFade(c: Canvas, p: Paint, w: Float, h: Float, a: Int, from: Float = 0.6f) {
    p.shader = LinearGradient(0f, h * from, 0f, h, 0x00000000, ca(0, a), Shader.TileMode.CLAMP)
    c.drawRect(0f, h * from, w, h, p); p.shader = null
}

private fun vertical(c: Canvas, p: Paint, w: Float, h: Float, colors: IntArray, stops: FloatArray? = null) {
    p.shader = LinearGradient(0f, 0f, 0f, h, colors, stops, Shader.TileMode.CLAMP); c.drawRect(0f, 0f, w, h, p); p.shader = null
}

/** A white glow, drawn once and tinted per use (much faster than a blur for hundreds of particles a frame). */
private val sprite: Bitmap by lazy {
    val b = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.shader = RadialGradient(32f, 32f, 32f, intArrayOf(0xFFFFFFFF.toInt(), 0x88FFFFFF.toInt(), 0x22FFFFFF, 0x00FFFFFF), floatArrayOf(0f, 0.25f, 0.6f, 1f), Shader.TileMode.CLAMP)
    Canvas(b).drawCircle(32f, 32f, 32f, p); b
}

internal fun spark(c: Canvas, p: Paint, x: Float, y: Float, r: Float, color: Int, a: Int) {
    if (a <= 0 || r <= 0.5f) return
    p.colorFilter = PorterDuffColorFilter(color or 0xFF000000.toInt(), PorterDuff.Mode.SRC_IN); p.alpha = a.coerceIn(0, 255)
    c.drawBitmap(sprite, null, RectF(x - r, y - r, x + r, y + r), p)
    p.colorFilter = null; p.alpha = 255
}

/** A four-pointed glint: a soft light with a thin cross through it. */
internal fun glint(c: Canvas, p: Paint, x: Float, y: Float, r: Float, color: Int, a: Int) {
    if (a <= 0) return
    spark(c, p, x, y, r * 0.55f, color, a)
    p.shader = LinearGradient(x - r, y, x + r, y, intArrayOf(ca(color, 0), ca(color, a), ca(color, 0)), null, Shader.TileMode.CLAMP)
    c.drawRect(x - r, y - 1.6f, x + r, y + 1.6f, p)
    p.shader = LinearGradient(x, y - r, x, y + r, intArrayOf(ca(color, 0), ca(color, a), ca(color, 0)), null, Shader.TileMode.CLAMP)
    c.drawRect(x - 1.6f, y - r, x + 1.6f, y + r, p); p.shader = null
}

/* ---------- the still designs ---------- */

/** Paints the chosen design and returns its colours. */
internal fun background(c: Canvas, theme: String, W: Int, H: Int, hc: Int, ac: Int): CardLook {
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    val w = W.toFloat(); val h = H.toFloat()
    // the reading start: home's corner
    val hx = if (Lang.en) w * 0.08f else w * 0.92f; val ax = w - hx
    return when (theme) {
        "aurora" -> aurora(c, p, w, h, hc, ac)
        "holo" -> holo(c, p, w, h)
        "fire" -> fire(c, p, w, h, hc, hx)
        "ice" -> ice(c, p, w, h, hc, hx)
        "stadium" -> stadium(c, p, w, h, hc, ac)
        "comic" -> comic(c, p, w, h, hc, ac, hx, ax)
        "night" -> {
            vertical(c, p, w, h, intArrayOf(0xFF151C42.toInt(), 0xFF070912.toInt(), 0xFF040509.toInt()), floatArrayOf(0f, 0.6f, 1f))
            glow(c, p, hx, h * 0.16f, w * 0.85f, brighten(hc), 0x80); glow(c, p, ax, h * 0.6f, w * 0.85f, brighten(ac), 0x66)
            // the milky way across the sky: a hazy band of tiny stars
            c.save(); c.rotate(-28f, w / 2, h * 0.35f)
            p.shader = LinearGradient(0f, h * 0.27f, 0f, h * 0.43f, intArrayOf(0x00FFFFFF, 0x1AC8D4FF, 0x00FFFFFF), null, Shader.TileMode.CLAMP)
            c.drawRect(-w, h * 0.27f, w * 2, h * 0.43f, p); p.shader = null
            p.color = 0xFFFFFFFF.toInt()
            for (i in 0 until 900) { val x = -w + rnd(i, 1) * w * 3; val y = h * 0.27f + h * 0.16f * (0.5f + 0.5f * (rnd(i, 2) - rnd(i, 3))); p.alpha = (20 + 70 * rnd(i, 4)).toInt(); c.drawCircle(x, y, 0.8f + rnd(i, 5) * 1.4f, p) }
            p.alpha = 255; c.restore()
            vignette(c, p, w, h, 0x99)
            CardLook(0xFFFFFFFF.toInt(), 0xCCB9C3E6.toInt(), 0x22FFFFFF, 0xFF7CF5FF.toInt(), 0x33FFFFFF)
        }
        "gold" -> {
            vertical(c, p, w, h, intArrayOf(0xFF3A2A07.toInt(), 0xFF140E04.toInt(), 0xFF070502.toInt()), floatArrayOf(0f, 0.55f, 1f))
            // light rays fanning out from the top
            p.color = 0xFFF5C542.toInt()
            for (k in 0 until 22) {
                val a0 = Math.toRadians(14.0 + k * 152.0 / 21); val spread = Math.toRadians(1.6 + (k % 3) * 0.7); val len = h * 1.5f
                val path = Path().apply {
                    moveTo(w / 2, -60f)
                    lineTo(w / 2 + len * cos(a0 - spread).toFloat(), -60f + len * sin(a0 - spread).toFloat())
                    lineTo(w / 2 + len * cos(a0 + spread).toFloat(), -60f + len * sin(a0 + spread).toFloat())
                    close()
                }
                p.alpha = if (k % 2 == 0) 24 else 12; c.drawPath(path, p)
            }
            p.alpha = 255
            glow(c, p, w / 2, h * 0.22f, w * 0.85f, 0xFFF5C542.toInt(), 0x70)
            // golden bokeh
            for (i in 0 until 26) {
                val r = 14f + rnd(i, 3) * 70f
                p.maskFilter = BlurMaskFilter(r * 0.45f, BlurMaskFilter.Blur.NORMAL)
                p.color = ca(if (i % 3 == 0) 0xFFFFE9A8.toInt() else 0xFFF5C542.toInt(), (18 + 40 * rnd(i, 4)).toInt())
                c.drawCircle(rnd(i, 1) * w, rnd(i, 2) * h, r, p)
            }
            p.maskFilter = null
            // a polished floor
            p.shader = LinearGradient(0f, h * 0.82f, 0f, h, 0x00F5C542, 0x33F5C542, Shader.TileMode.CLAMP); c.drawRect(0f, h * 0.82f, w, h, p); p.shader = null
            vignette(c, p, w, h, 0xAA)
            CardLook(0xFFFFF4D6.toInt(), 0xCCE8D9A8.toInt(), 0x26F5C542, 0xFFF5C542.toInt(), 0x66F5C542)
        }
        "light" -> {
            p.color = 0xFFF6F3ED.toInt(); c.drawRect(0f, 0f, w, h, p)
            // the two colours as soft pastel lights, a fine grid like a notebook, a thin bar of both colours on top
            glow(c, p, hx, 0f, w * 1.0f, brighten(hc), 0x50); glow(c, p, ax, h, w * 1.0f, brighten(ac), 0x44)
            glow(c, p, w / 2, h * 0.4f, w * 0.7f, 0xFFFFFFFF.toInt(), 0xE0)
            p.color = 0x0A000000; p.strokeWidth = 1.5f
            var gx = 0f; while (gx < w) { c.drawLine(gx, 0f, gx, h, p); gx += 54f }
            var gy = 0f; while (gy < h) { c.drawLine(0f, gy, w, gy, p); gy += 54f }
            p.shader = LinearGradient(0f, 0f, w, 0f, if (Lang.en) intArrayOf(hc, hc, ac, ac) else intArrayOf(ac, ac, hc, hc), floatArrayOf(0f, 0.5f, 0.5f, 1f), Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w, 16f, p); p.shader = null
            CardLook(0xFF15171C.toInt(), 0xFF5D6470.toInt(), 0xE6FFFFFF.toInt(), hc or 0xFF000000.toInt(), 0x1F000000)
        }
        "pitch" -> {
            val n = 12
            for (k in 0 until n) { p.color = if (k % 2 == 0) 0xFF1D6E3F.toInt() else 0xFF186236.toInt(); c.drawRect(0f, h * k / n, w, h * (k + 1) / n + 1, p) }
            p.style = Paint.Style.STROKE; p.strokeWidth = 5f; p.color = 0x55FFFFFF
            c.drawRect(40f, 40f, w - 40f, h - 40f, p); c.drawLine(40f, h / 2, w - 40f, h / 2, p); c.drawCircle(w / 2, h / 2, w * 0.17f, p)
            c.drawRect(w * 0.2f, 40f, w * 0.8f, h * 0.14f, p); c.drawRect(w * 0.2f, h * 0.86f, w * 0.8f, h - 40f, p)
            p.style = Paint.Style.FILL
            // floodlights from the top corners
            glow(c, p, 0f, 0f, w * 0.9f, 0xFFFFFFFF.toInt(), 0x50); glow(c, p, w, 0f, w * 0.9f, 0xFFFFFFFF.toInt(), 0x50)
            p.shader = RadialGradient(w / 2, h / 2, h * 0.75f, intArrayOf(0x00000000, 0xAA000000.toInt()), null, Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w, h, p); p.shader = null
            CardLook(0xFFFFFFFF.toInt(), 0xDDDFF5E6.toInt(), 0x44000000, 0xFFFFE14D.toInt(), 0x33FFFFFF)
        }
        "neon" -> {
            vertical(c, p, w, h, intArrayOf(0xFF07031A.toInt(), 0xFF05030C.toInt(), 0xFF0A0418.toInt()), floatArrayOf(0f, 0.6f, 1f))
            glow(c, p, hx, h * 0.12f, w * 0.9f, brighten(hc), 0x70); glow(c, p, ax, h * 0.5f, w * 0.8f, brighten(ac), 0x55)
            // glowing rings in both colours
            p.style = Paint.Style.STROKE
            for ((col, x, yy) in listOf(Triple(hc, w * 0.15f, h * 0.2f), Triple(ac, w * 0.85f, h * 0.55f))) {
                for (k in 0 until 3) {
                    p.color = brighten(col); p.strokeWidth = 5f; p.alpha = 170 - k * 45
                    p.maskFilter = BlurMaskFilter(16f, BlurMaskFilter.Blur.NORMAL)
                    c.drawCircle(x, yy, w * (0.22f + k * 0.12f), p)
                }
            }
            p.maskFilter = null; p.style = Paint.Style.FILL; p.alpha = 255
            // the horizon of the grid floor, glowing
            val hy = h * NEON_HORIZON
            p.shader = LinearGradient(0f, hy, w, hy, brighten(hc), brighten(ac), Shader.TileMode.CLAMP)
            p.maskFilter = BlurMaskFilter(24f, BlurMaskFilter.Blur.NORMAL); c.drawRect(0f, hy - 6f, w, hy + 6f, p); p.maskFilter = null
            c.drawRect(0f, hy - 1.5f, w, hy + 1.5f, p); p.shader = null
            p.shader = LinearGradient(0f, hy, 0f, h, 0x22FF3DD4, 0x00000000, Shader.TileMode.CLAMP); c.drawRect(0f, hy, w, h, p); p.shader = null
            // scan lines
            p.color = 0x0CFFFFFF; var sy = 0f; while (sy < h) { c.drawRect(0f, sy, w, sy + 1.5f, p); sy += 6f }
            CardLook(0xFFFFFFFF.toInt(), 0xCCC8C8E0.toInt(), 0x26000000, 0xFF39FFB4.toInt(), 0x66FFFFFF)
        }
        else -> {
            // each club's colour as a big soft light (home from its top corner, away from the opposite bottom), a blade of light
            // where they meet, halftone dots fading down, a light behind the score
            p.color = 0xFF06080C.toInt(); c.drawRect(0f, 0f, w, h, p)
            glow(c, p, hx, h * 0.06f, w * 1.25f, hc, 0xF5); glow(c, p, hx, h * 0.06f, w * 0.6f, brighten(hc), 0x60)
            glow(c, p, ax, h * 0.92f, w * 1.25f, ac, 0xEE); glow(c, p, ax, h * 0.92f, w * 0.6f, brighten(ac), 0x55)
            p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND
            p.color = 0x12FFFFFF; p.strokeWidth = 220f; p.maskFilter = BlurMaskFilter(100f, BlurMaskFilter.Blur.NORMAL)
            c.drawLine(ax, -h * 0.05f, hx, h * 1.05f, p)
            p.color = 0x38FFFFFF; p.strokeWidth = 3f; p.maskFilter = BlurMaskFilter(3f, BlurMaskFilter.Blur.NORMAL)
            c.drawLine(ax, -h * 0.05f, hx, h * 1.05f, p)
            p.maskFilter = null; p.style = Paint.Style.FILL
            p.color = 0xFFFFFFFF.toInt()
            var y = 13f
            while (y < h * 0.7f) {
                val r = 2.8f * (1 - y / (h * 0.7f)).pow(1.3f)
                var x = if (((y / 26f).toInt()) % 2 == 0) 13f else 26f
                p.alpha = 34
                while (x < w) { c.drawCircle(x, y, r, p); x += 26f }
                y += 26f
            }
            p.alpha = 255
            glow(c, p, w / 2, h * 0.3f, w * 0.62f, 0xFFFFFFFF.toInt(), 0x34)
            floorFade(c, p, w, h, 0xD0, 0.55f)
            vignette(c, p, w, h, 0x88)
            CardLook(0xFFFFFFFF.toInt(), 0xCCFFFFFF.toInt(), 0x40000000, 0xFFF5C542.toInt(), 0x40FFFFFF)
        }
    }
}

/** Where the neon grid floor begins, as a share of the height. */
private const val NEON_HORIZON = 0.7f

/** "شفق": curtains of northern light in the clubs' colours and green over dark mountains. */
private fun aurora(c: Canvas, p: Paint, w: Float, h: Float, hc: Int, ac: Int): CardLook {
    vertical(c, p, w, h, intArrayOf(0xFF03081A.toInt(), 0xFF050B16.toInt(), 0xFF02040A.toInt()), floatArrayOf(0f, 0.55f, 1f))
    val teal = 0xFF2CF5B5.toInt(); val violet = 0xFF8B5CF6.toInt()
    val bands = listOf(Triple(brighten(hc), h * 0.14f, 0.0f), Triple(teal, h * 0.3f, 1.7f), Triple(violet, h * 0.44f, 3.1f), Triple(brighten(ac), h * 0.68f, 4.4f))
    for ((k, b) in bands.withIndex()) {
        val (col, y0, ph) = b
        val amp = h * (0.05f + 0.02f * k)
        fun yAt(x: Float) = y0 + amp * sin(x / w * 2 * PI * 0.8 + ph).toFloat() + amp * 0.4f * sin(x / w * 2 * PI * 2.1 + ph * 1.7).toFloat()
        // the curtain: thin vertical rays rising from the band
        var x = -20f
        while (x < w + 20f) {
            val y = yAt(x); val len = 240f + 200f * rnd(x.toInt(), k)
            p.shader = LinearGradient(0f, y - len, 0f, y + 20f, intArrayOf(ca(col, 0), ca(col, if (k == 3) 0x22 else 0x40), ca(col, 0)), floatArrayOf(0f, 0.85f, 1f), Shader.TileMode.CLAMP)
            c.drawRect(x, y - len, x + 9f, y + 20f, p)
            x += 12f
        }
        p.shader = null
        // the glowing band itself
        val path = Path(); var px = -100f; path.moveTo(px, yAt(px))
        while (px < w + 100f) { px += 40f; path.lineTo(px, yAt(px)) }
        p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND
        p.color = ca(col, if (k == 3) 0x30 else 0x55); p.strokeWidth = 220f; p.maskFilter = BlurMaskFilter(110f, BlurMaskFilter.Blur.NORMAL); c.drawPath(path, p)
        p.color = ca(col, if (k == 3) 0x50 else 0x90); p.strokeWidth = 26f; p.maskFilter = BlurMaskFilter(22f, BlurMaskFilter.Blur.NORMAL); c.drawPath(path, p)
        p.maskFilter = null; p.style = Paint.Style.FILL
    }
    // dark mountains along the bottom, a rim of light on their ridge
    val ridge = Path(); ridge.moveTo(0f, h)
    var x = 0f
    while (x <= w) { ridge.lineTo(x, h * 0.86f - h * 0.05f * abs(sin(x / w * 5.3 + 0.6)).toFloat() - h * 0.025f * rnd(x.toInt() / 60, 9)); x += 60f }
    ridge.lineTo(w, h); ridge.close()
    p.color = 0xFF01030A.toInt(); c.drawPath(ridge, p)
    p.style = Paint.Style.STROKE; p.strokeWidth = 3f; p.color = ca(teal, 0x44); p.maskFilter = BlurMaskFilter(4f, BlurMaskFilter.Blur.NORMAL); c.drawPath(ridge, p)
    p.maskFilter = null; p.style = Paint.Style.FILL
    vignette(c, p, w, h, 0x99)
    return CardLook(0xFFFFFFFF.toInt(), 0xCCBFEFE5.toInt(), 0x33000000, 0xFF7CFFCB.toInt(), 0x4DA0FFE0)
}

/** "هولوگرام": a dark card with rainbow foil around it, like a rare trading card. */
private fun holo(c: Canvas, p: Paint, w: Float, h: Float): CardLook {
    p.color = 0xFF0A0A12.toInt(); c.drawRect(0f, 0f, w, h, p)
    val foil = intArrayOf(0xFF6CF3FF.toInt(), 0xFFFF6CE0.toInt(), 0xFFFFE66C.toInt(), 0xFF8B6CFF.toInt(), 0xFF6CF3FF.toInt())
    p.shader = LinearGradient(0f, 0f, w * 0.7f, h * 0.45f, foil, null, Shader.TileMode.MIRROR); p.alpha = 0xD0
    c.drawRect(0f, 0f, w, h, p); p.shader = null; p.alpha = 255
    // creases in the foil: bands of light and shade on a slant
    for (k in 0 until 9) {
        val x0 = -w * 0.5f + k * w * 0.26f
        p.shader = LinearGradient(x0, 0f, x0 + 160f, 90f, intArrayOf(0x00FFFFFF, if (k % 2 == 0) 0x40FFFFFF else 0x30000000, 0x00FFFFFF), null, Shader.TileMode.CLAMP)
        c.save(); c.skew(-0.55f, 0f); c.drawRect(x0, 0f, x0 + 160f, h, p); c.restore()
    }
    p.shader = null
    // the dark card in the middle, so the writing reads; the foil stays round the edges
    val inset = 30f
    val card = RectF(inset, inset, w - inset, h - inset)
    p.shader = RadialGradient(w / 2, h * 0.48f, h * 0.62f, intArrayOf(0xF00B0A14.toInt(), 0xE60B0A14.toInt(), 0xA60B0A14.toInt()), floatArrayOf(0f, 0.7f, 1f), Shader.TileMode.CLAMP)
    c.drawRoundRect(card, 54f, 54f, p); p.shader = null
    // a faint foil inside the card too
    p.shader = LinearGradient(0f, 0f, w, h, foil, null, Shader.TileMode.MIRROR); p.alpha = 0x16; c.drawRoundRect(card, 54f, 54f, p); p.shader = null; p.alpha = 255
    // the card's edge: a rainbow line all round
    p.style = Paint.Style.STROKE; p.strokeWidth = 6f
    p.shader = SweepGradient(w / 2, h / 2, foil, null); c.drawRoundRect(card, 54f, 54f, p)
    p.strokeWidth = 2f; p.shader = null; p.color = 0x55FFFFFF; c.drawRoundRect(RectF(inset + 12f, inset + 12f, w - inset - 12f, h - inset - 12f), 44f, 44f, p)
    p.style = Paint.Style.FILL
    return CardLook(0xFFFFFFFF.toInt(), 0xDDE9E4FF.toInt(), 0x30FFFFFF, 0xFF6CF3FF.toInt(), 0x66FFFFFF)
}

/** "آتش": flames rising from the bottom on a smoky dark red, the home colour glowing through the smoke. */
private fun fire(c: Canvas, p: Paint, w: Float, h: Float, hc: Int, hx: Float): CardLook {
    vertical(c, p, w, h, intArrayOf(0xFF0B0302.toInt(), 0xFF1E0603.toInt(), 0xFF4A0E03.toInt(), 0xFF9A2A06.toInt()), floatArrayOf(0f, 0.5f, 0.82f, 1f))
    glow(c, p, hx, h * 0.1f, w * 0.9f, brighten(hc), 0x55)
    glow(c, p, w / 2, h * 1.02f, w * 1.1f, 0xFFFF6A00.toInt(), 0xC0)
    // the flames: soft tongues added on top of each other, so where they overlap they burn brighter
    p.xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
    for (i in 0 until 34) {
        val x = rnd(i, 1) * (w + 200f) - 100f
        val fh = h * (0.12f + 0.24f * rnd(i, 2)) * (if (i % 5 == 0) 1.3f else 1f)
        val fw = 70f + 150f * rnd(i, 3)
        val lean = (rnd(i, 4) - 0.5f) * fw * 1.2f
        val base = h + 20f; val tip = base - fh
        val path = Path().apply {
            moveTo(x - fw / 2, base)
            cubicTo(x - fw * 0.55f, base - fh * 0.45f, x + lean - fw * 0.2f, tip + fh * 0.3f, x + lean, tip)
            cubicTo(x + lean + fw * 0.2f, tip + fh * 0.3f, x + fw * 0.55f, base - fh * 0.45f, x + fw / 2, base)
            close()
        }
        val core = if (i % 3 == 0) 0xFFFFD27A.toInt() else 0xFFFF9A1F.toInt()
        p.shader = LinearGradient(0f, base, 0f, tip, intArrayOf(ca(core, 0x70), ca(0xFFFF4A00.toInt(), 0x40), ca(0xFFB01800.toInt(), 0)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        p.maskFilter = BlurMaskFilter(18f, BlurMaskFilter.Blur.NORMAL)
        c.drawPath(path, p)
    }
    p.maskFilter = null; p.shader = null; p.xfermode = null
    // smoke over the top
    p.shader = LinearGradient(0f, 0f, 0f, h * 0.5f, 0xB0000000.toInt(), 0x00000000, Shader.TileMode.CLAMP); c.drawRect(0f, 0f, w, h * 0.5f, p); p.shader = null
    vignette(c, p, w, h, 0x77)
    return CardLook(0xFFFFFFFF.toInt(), 0xDDFFD9B8.toInt(), 0x4D000000, 0xFFFFC23D.toInt(), 0x55FFA060)
}

/** "یخ": a wall of ice: faceted crystal, frost in the corners, a few cracks. */
private fun ice(c: Canvas, p: Paint, w: Float, h: Float, hc: Int, hx: Float): CardLook {
    vertical(c, p, w, h, intArrayOf(0xFF0A2C48.toInt(), 0xFF061B30.toInt(), 0xFF041222.toInt()), floatArrayOf(0f, 0.5f, 1f))
    // facets: a jittered grid cut into triangles, each a slightly different shade of ice
    val cols = 7; val rows = 12
    val pts = Array(rows + 1) { r -> Array(cols + 1) { k ->
        val edgeX = k == 0 || k == cols; val edgeY = r == 0 || r == rows
        val jx = if (edgeX) 0f else (rnd(r * 31 + k, 1) - 0.5f) * w / cols * 0.8f
        val jy = if (edgeY) 0f else (rnd(r * 31 + k, 2) - 0.5f) * h / rows * 0.8f
        floatArrayOf(k * w / cols + jx, r * h / rows + jy)
    } }
    val dark = 0xFF0B3556.toInt(); val lit = 0xFF2C7FB0.toInt()
    for (r in 0 until rows) for (k in 0 until cols) {
        val a = pts[r][k]; val b = pts[r][k + 1]; val d = pts[r + 1][k]; val e = pts[r + 1][k + 1]
        for ((t, tri) in listOf(listOf(a, b, e), listOf(a, e, d)).withIndex()) {
            val shade = rnd(r * 17 + k * 3 + t, 6) * (1f - r.toFloat() / rows * 0.6f)
            p.color = ca(mix(dark, lit, shade), 0x70)
            val path = Path().apply { moveTo(tri[0][0], tri[0][1]); lineTo(tri[1][0], tri[1][1]); lineTo(tri[2][0], tri[2][1]); close() }
            c.drawPath(path, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = 1.5f; p.color = 0x26BFEFFF; c.drawPath(path, p); p.style = Paint.Style.FILL
        }
    }
    glow(c, p, hx, h * 0.08f, w * 0.8f, brighten(hc), 0x4A)
    // frost creeping in from the corners
    for ((x, y) in listOf(0f to 0f, w to 0f, 0f to h, w to h)) glow(c, p, x, y, w * 0.55f, 0xFFE6FBFF.toInt(), 0x66)
    // cracks
    p.style = Paint.Style.STROKE; p.strokeWidth = 2.5f; p.color = 0x55E6FBFF; p.strokeCap = Paint.Cap.ROUND
    for (i in 0 until 4) {
        val path = Path(); var x = rnd(i, 7) * w; var y = if (i % 2 == 0) 0f else h
        path.moveTo(x, y)
        repeat(9) { j -> x += (rnd(i * 13 + j, 8) - 0.5f) * 140f; y += (if (i % 2 == 0) 1 else -1) * (40f + 50f * rnd(i * 13 + j, 9)); path.lineTo(x, y) }
        c.drawPath(path, p)
    }
    p.style = Paint.Style.FILL
    vignette(c, p, w, h, 0x77)
    return CardLook(0xFFFFFFFF.toInt(), 0xCCCFEFFF.toInt(), 0x26CFF4FF, 0xFF8FE9FF.toInt(), 0x66CFF4FF)
}

/** "ورزشگاه": a night match: floodlight towers and their beams, the crowd's lights in both clubs' colours, the lit pitch below. */
private fun stadium(c: Canvas, p: Paint, w: Float, h: Float, hc: Int, ac: Int): CardLook {
    vertical(c, p, w, h, intArrayOf(0xFF0A1530.toInt(), 0xFF050A16.toInt(), 0xFF07130F.toInt(), 0xFF0F3D20.toInt()), floatArrayOf(0f, 0.45f, 0.8f, 1f))
    // the stands: a dark bowl across the lower middle, full of tiny lights
    val top = h * 0.6f; val bottom = h * 0.84f
    val bowl = Path().apply { moveTo(0f, top - h * 0.06f); quadTo(w / 2, top + h * 0.03f, w, top - h * 0.06f); lineTo(w, bottom); quadTo(w / 2, bottom + h * 0.03f, 0f, bottom); close() }
    p.color = 0xFF04070D.toInt(); c.drawPath(bowl, p)
    c.save(); c.clipPath(bowl)
    for (i in 0 until 1600) {
        val x = rnd(i, 1) * w; val y = top - h * 0.06f + rnd(i, 2) * (bottom - top + h * 0.09f)
        // the home end on one side, the away end on the other, white phones everywhere
        val side = if ((x < w / 2) != Lang.en) hc else ac
        val col = if (rnd(i, 3) < 0.35f) 0xFFFFFFFF.toInt() else brighten(side)
        p.color = ca(col, (40 + 140 * rnd(i, 4)).toInt()); c.drawCircle(x, y, 1.6f + 2.2f * rnd(i, 5), p)
    }
    c.restore()
    // the pitch: mown stripes in perspective, lit from above
    val pTop = bottom + h * 0.01f
    for (k in 0 until 10) {
        val x0 = w * (-0.4f + k * 0.18f); val x1 = x0 + w * 0.18f
        val path = Path().apply { moveTo(w / 2 + (x0 - w / 2) * 0.55f, pTop); lineTo(w / 2 + (x1 - w / 2) * 0.55f, pTop); lineTo(x1 + (x1 - w / 2) * 0.3f, h); lineTo(x0 + (x0 - w / 2) * 0.3f, h); close() }
        p.color = if (k % 2 == 0) 0xFF1E7A3F.toInt() else 0xFF186A36.toInt(); c.drawPath(path, p)
    }
    p.shader = LinearGradient(0f, pTop, 0f, h, 0x66000000, 0x00000000, Shader.TileMode.CLAMP); c.drawRect(0f, pTop, w, h, p); p.shader = null
    // floodlight towers in the top corners: a bank of lamps, a halo and a beam down to the pitch
    for (x in listOf(w * 0.1f, w * 0.9f)) {
        val y = h * 0.07f
        p.xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
        val beam = Path().apply { moveTo(x - 30f, y); lineTo(x + 30f, y); lineTo(w / 2 + (x - w / 2) * 0.1f + 260f, h * 0.95f); lineTo(w / 2 + (x - w / 2) * 0.1f - 260f, h * 0.95f); close() }
        p.shader = LinearGradient(0f, y, 0f, h * 0.95f, 0x30FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
        p.maskFilter = BlurMaskFilter(40f, BlurMaskFilter.Blur.NORMAL); c.drawPath(beam, p); p.maskFilter = null; p.shader = null
        glow(c, p, x, y, w * 0.42f, 0xFFDCE8FF.toInt(), 0x80)
        p.xfermode = null
        // the lamps
        p.color = 0xFF1A2230.toInt(); c.drawRoundRect(RectF(x - 74f, y - 40f, x + 74f, y + 40f), 10f, 10f, p)
        for (r in 0 until 3) for (k in 0 until 5) { p.color = 0xFFFFFFFF.toInt(); c.drawCircle(x - 56f + k * 28f, y - 22f + r * 22f, 8f, p) }
        // a lens streak across
        p.shader = LinearGradient(x - w * 0.45f, y, x + w * 0.45f, y, intArrayOf(0x00BFD8FF, 0x66BFD8FF, 0x00BFD8FF), null, Shader.TileMode.CLAMP)
        c.drawRect(x - w * 0.45f, y - 2f, x + w * 0.45f, y + 2f, p); p.shader = null
    }
    // haze over everything
    glow(c, p, w / 2, h * 0.45f, w * 0.9f, 0xFFB8C8E8.toInt(), 0x1C)
    vignette(c, p, w, h, 0x88)
    return CardLook(0xFFFFFFFF.toInt(), 0xCCDDE6F5.toInt(), 0x3A000000, 0xFFFFE14D.toInt(), 0x40FFFFFF)
}

/** "کمیک": pop art: a burst of yellow rays, halftone dots in the clubs' colours, speed lines and a thick black frame. */
private fun comic(c: Canvas, p: Paint, w: Float, h: Float, hc: Int, ac: Int, hx: Float, ax: Float): CardLook {
    p.color = 0xFFFFD60A.toInt(); c.drawRect(0f, 0f, w, h, p)
    // the burst behind the middle
    val cx = w / 2; val cy = h * 0.3f
    p.color = 0xFFFFC300.toInt()
    for (k in 0 until 28 step 2) {
        val a0 = 2 * PI * k / 28; val a1 = 2 * PI * (k + 1) / 28; val r = h * 1.5f
        val path = Path().apply { moveTo(cx, cy); lineTo(cx + r * cos(a0).toFloat(), cy + r * sin(a0).toFloat()); lineTo(cx + r * cos(a1).toFloat(), cy + r * sin(a1).toFloat()); close() }
        c.drawPath(path, p)
    }
    // halftone: big dots near each club's corner, shrinking away from it
    for ((col, ox, oy) in listOf(Triple(brighten(hc), hx, 0f), Triple(brighten(ac), ax, h))) {
        p.color = col
        var y = 0f
        while (y <= h) {
            var x = if (((y / 34f).toInt()) % 2 == 0) 0f else 17f
            while (x <= w) {
                val d = sqrt((x - ox) * (x - ox) + (y - oy) * (y - oy)) / (w * 1.05f)
                val r = 15f * (1 - d)
                if (r > 1f) c.drawCircle(x, y, r, p)
                x += 34f
            }
            y += 34f
        }
    }
    // speed lines from the edges towards the middle
    p.color = 0x5A111111; p.strokeWidth = 3f
    for (k in 0 until 90) {
        val a = 2 * PI * k / 90 + rnd(k, 1) * 0.05
        val r0 = h * (0.62f + 0.12f * rnd(k, 2)); val r1 = h * 1.2f
        c.drawLine(cx + r0 * cos(a).toFloat(), cy + r0 * sin(a).toFloat(), cx + r1 * cos(a).toFloat(), cy + r1 * sin(a).toFloat(), p)
    }
    // the frame
    p.style = Paint.Style.STROKE; p.strokeWidth = 16f; p.color = 0xFF111111.toInt(); c.drawRect(22f, 22f, w - 22f, h - 22f, p); p.style = Paint.Style.FILL
    return CardLook(0xFF111111.toInt(), 0xFF3A3A3A.toInt(), 0xF2FFFFFF.toInt(), 0xFFE5172F.toInt(), 0xFF111111.toInt(), comic = true)
}

/* ---------- the moving layer ---------- */

/** Draws the design's moving parts at [t] seconds (a still picture uses one moment of it). Nothing for the still designs. */
internal fun themeLive(c: Canvas, theme: String, t: Float, W: Int, H: Int, hc: Int, ac: Int) {
    val w = W.toFloat(); val h = H.toFloat()
    val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    when (theme) {
        "fire" -> {
            // embers rising and swaying, flickering
            for (i in 0 until 70) {
                val sp = 90f + 160f * rnd(i, 1)
                val y = h + 40f - ((t * sp + rnd(i, 2) * h * 1.2f) % (h * 1.2f))
                val x = rnd(i, 3) * w + 40f * sin(t * (0.8f + rnd(i, 4)) + i).toFloat()
                val life = 1f - ((h - y) / (h * 1.1f)).coerceIn(0f, 1f)
                val fl = 0.6f + 0.4f * sin(t * 9f + i * 1.7f).toFloat()
                val r = 3f + 6f * rnd(i, 5)
                val col = if (rnd(i, 6) > 0.5f) 0xFFFFC23D.toInt() else 0xFFFF6A1A.toInt()
                spark(c, p, x, y, r * 5f, col, (110 * life * fl).toInt())
                p.color = ca(0xFFFFF1C4.toInt(), (230 * life * fl).toInt()); c.drawCircle(x, y, r * 0.55f, p)
            }
            // the fire breathing at the bottom
            val b = 0.5f + 0.5f * sin(t * 3.1f).toFloat() * sin(t * 1.7f + 1).toFloat()
            glow(c, p, w / 2, h * 1.05f, w * 0.8f, 0xFFFF7A00.toInt(), (40 + 50 * b).toInt())
        }
        "ice" -> {
            // snow drifting down
            for (i in 0 until 90) {
                val near = rnd(i, 1)
                val sp = 40f + 120f * near
                val y = (t * sp + rnd(i, 2) * h * 1.1f) % (h * 1.1f) - 40f
                val x = (rnd(i, 3) * w + 50f * sin(t * (0.5f + rnd(i, 4)) + i).toFloat() + w) % w
                val r = 2f + 6f * near
                spark(c, p, x, y, r * 2.6f, 0xFFFFFFFF.toInt(), (70 + 120 * near).toInt())
            }
            // glints that come and go
            for (i in 0 until 9) {
                val ph = ((t * 0.6f + rnd(i, 7)) % 1f); val a = sin(PI * ph).toFloat().pow(3)
                glint(c, p, rnd(i, 8) * w, rnd(i, 9) * h, 34f + 30f * rnd(i, 10), 0xFFE6FBFF.toInt(), (230 * a).toInt())
            }
        }
        "stadium" -> {
            // camera flashes popping in the crowd
            for (i in 0 until 26) {
                val period = 1.3f + 1.8f * rnd(i, 1)
                val ph = ((t + rnd(i, 2) * period) % period) / period
                if (ph > 0.12f) continue
                val a = 1f - ph / 0.12f
                val x = rnd(i, 3 + (t / period).toInt() % 7) * w; val y = h * (0.56f + 0.26f * rnd(i, 11 + (t / period).toInt() % 5))
                glint(c, p, x, y, 22f + 26f * rnd(i, 4), 0xFFFFFFFF.toInt(), (255 * a).toInt())
            }
            // the floodlights shimmer a little
            for ((k, x) in listOf(w * 0.1f, w * 0.9f).withIndex()) {
                val f = 0.5f + 0.5f * sin(t * 2.3f + k * 2f).toFloat()
                spark(c, p, x, h * 0.07f, 150f + 40f * f, 0xFFDCE8FF.toInt(), (60 + 60 * f).toInt())
            }
        }
        "neon" -> {
            // a grid floor running towards the viewer
            val hy = h * NEON_HORIZON
            p.strokeWidth = 2.5f
            val shift = (t * 0.55f) % 1f
            for (k in 0 until 14) {
                val d = ((k + shift) / 14f); val y = hy + (h - hy) * d * d
                p.shader = LinearGradient(0f, y, w, y, ca(brighten(hc), (60 + 150 * d).toInt()), ca(brighten(ac), (60 + 150 * d).toInt()), Shader.TileMode.CLAMP)
                c.drawLine(0f, y, w, y, p)
            }
            for (k in -10..10) {
                val x1 = w / 2 + k * w * 0.16f
                p.shader = LinearGradient(w / 2, hy, x1, h, ca(brighten(if (k < 0) hc else ac), 0x20), ca(brighten(if (k < 0) hc else ac), 0xB0), Shader.TileMode.CLAMP)
                c.drawLine(w / 2 + k * w * 0.012f, hy, x1, h, p)
            }
            p.shader = null
        }
        "holo" -> {
            // a shimmer running across the foil
            val x = -w + ((t * 0.45f) % 1.6f) * w * 1.8f
            c.save(); c.skew(-0.5f, 0f)
            p.shader = LinearGradient(x, 0f, x + 260f, 0f, intArrayOf(0x00FFFFFF, 0x2EFFFFFF, 0x00FFFFFF), null, Shader.TileMode.CLAMP)
            c.drawRect(x, 0f, x + 260f, h, p); p.shader = null; c.restore()
            for (i in 0 until 14) {
                val ph = ((t * 0.5f + rnd(i, 1)) % 1f); val a = sin(PI * ph).toFloat().pow(2)
                val col = listOf(0xFF6CF3FF.toInt(), 0xFFFF6CE0.toInt(), 0xFFFFE66C.toInt())[i % 3]
                glint(c, p, rnd(i, 2) * w, rnd(i, 3) * h, 20f + 24f * rnd(i, 4), col, (220 * a).toInt())
            }
        }
        "aurora", "night" -> {
            // stars twinkling, now and then a shooting star
            for (i in 0 until 110) {
                val x = rnd(i, 1) * w; val y = rnd(i, 2) * h * (if (theme == "aurora") 0.8f else 0.9f)
                val tw = 0.55f + 0.45f * sin(t * (1.5f + 2.5f * rnd(i, 3)) + i).toFloat()
                val r = 1.2f + 2.4f * rnd(i, 4)
                p.color = ca(0xFFFFFFFF.toInt(), (60 + 180 * tw * rnd(i, 5)).toInt()); c.drawCircle(x, y, r, p)
                if (i % 12 == 0) glint(c, p, x, y, 18f + 14f * rnd(i, 6), 0xFFFFFFFF.toInt(), (160 * tw).toInt())
            }
            val st = (t % 4.5f) / 0.9f
            if (st in 0f..1f) {
                val x0 = w * 0.85f - st * w * 0.7f; val y0 = h * 0.08f + st * h * 0.16f
                p.shader = LinearGradient(x0, y0, x0 + 220f, y0 - 50f, 0xFFFFFFFF.toInt(), 0x00FFFFFF, Shader.TileMode.CLAMP); p.strokeWidth = 3f
                p.alpha = (255 * sin(PI * st)).toInt(); c.drawLine(x0, y0, x0 + 220f, y0 - 50f, p); p.shader = null; p.alpha = 255
            }
        }
        "gold" -> {
            // gold dust floating up
            for (i in 0 until 60) {
                val sp = 30f + 70f * rnd(i, 1)
                val y = h + 20f - ((t * sp + rnd(i, 2) * h * 1.1f) % (h * 1.1f))
                val x = rnd(i, 3) * w + 30f * sin(t * 0.9f + i).toFloat()
                val tw = 0.5f + 0.5f * sin(t * 4f + i * 2.1f).toFloat()
                spark(c, p, x, y, 6f + 12f * rnd(i, 4), 0xFFFFE08A.toInt(), (60 + 150 * tw).toInt())
            }
        }
        "comic" -> {
            // the burst turns slowly, like a cartoon
            val cx = w / 2; val cy = h * 0.3f
            p.color = 0x14000000
            for (k in 0 until 12) {
                val a0 = 2 * PI * k / 12 + t * 0.15; val r = h * 0.9f
                val path = Path().apply { moveTo(cx, cy); lineTo(cx + r * cos(a0).toFloat(), cy + r * sin(a0).toFloat()); lineTo(cx + r * cos(a0 + 0.08).toFloat(), cy + r * sin(a0 + 0.08).toFloat()); close() }
                c.drawPath(path, p)
            }
        }
    }
}

/** True for a design with dark writing on a light ground. */
internal fun darkInk(look: CardLook) = android.graphics.Color.red(look.ink) < 0x80

/** A team colour made legible as writing on the design: lifted on dark grounds, deepened on light ones. */
internal fun readable(c: Int, look: CardLook): Int {
    val hsv = FloatArray(3); android.graphics.Color.colorToHSV(c or 0xFF000000.toInt(), hsv)
    if (darkInk(look)) hsv[2] = minOf(hsv[2], 0.5f) else { hsv[2] = maxOf(hsv[2], 0.95f); hsv[1] = minOf(hsv[1], 0.6f) }
    return android.graphics.Color.HSVToColor(hsv)
}

/** The empty part of a bar or a ring. */
internal fun track(look: CardLook) = if (darkInk(look)) 0x16000000 else 0x22FFFFFF

/** Every design's moving layer frozen at one moment, for a still picture. */
internal const val LIVE_STILL_T = 3.3f

/** How much brighter the edge of a panel is at its top than at its bottom. */
internal fun edgeTop(stroke: Int) = ca(stroke, min(255, (android.graphics.Color.alpha(stroke) * 2.2f).toInt()))
internal fun edgeBottom(stroke: Int) = ca(stroke, max(10, (android.graphics.Color.alpha(stroke) * 0.45f).toInt()))
