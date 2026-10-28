package app.goallive.live.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.core.graphics.ColorUtils
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

/*
 * The camera work every story's video shares, over its own animation:
 *  - it opens out of the dark on the first drum: a flare streaking across, the club's light leaking in from the corners;
 *  - specks of light drift up through it all the way, and the edges breathe with the beat;
 *  - on the music's big hit the picture kicks, a flash and a ring of light go out from the middle.
 */

/** When the music's big hit lands (and the boom in the sound-effects-only track). */
internal const val STORY_HIT = 5.74f

/** The tracks' first beat and the time between beats (130 a minute). */
private const val BEAT0 = 0.2f
private const val BEAT = 60f / 130f

/** How much the picture swells on the hit, [t] seconds in: 0 before it, a quick kick that settles. */
internal fun kick(t: Float) = if (t < STORY_HIT) 0f else exp(-(t - STORY_HIT) * 6f)

/** True when [bg] is mostly light (the light designs), so the dark edges and white specks stay away. */
internal fun lightPage(bg: Bitmap): Boolean {
    val pts = listOf(0.05f to 0.05f, 0.95f to 0.05f, 0.05f to 0.5f, 0.95f to 0.5f, 0.05f to 0.95f, 0.95f to 0.95f)
    return pts.map { (x, y) -> ColorUtils.calculateLuminance(bg.getPixel((x * (bg.width - 1)).toInt(), (y * (bg.height - 1)).toInt()) or 0xFF000000.toInt()) }.average() > 0.5
}

/** The shared camera work for frame [t] of a W×H story in the colours [hc] and [ac]. */
internal fun cinema(c: Canvas, t: Float, W: Int, H: Int, hc: Int, ac: Int, light: Boolean) {
    val w = W.toFloat(); val h = H.toFloat()
    val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    val a1 = glowOf(if (hc != 0) hc else 0xFF3DDC84.toInt()); val a2 = glowOf(if (ac != 0) ac else a1)

    // the edges: dark, breathing a little of the club's light with every beat once the music is going
    if (!light) {
        p.shader = RadialGradient(w / 2, h * 0.45f, h * 0.78f, intArrayOf(0, 0, 0x5C000000), floatArrayOf(0f, 0.62f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p); p.shader = null
        if (t > BEAT0 && t < STORY_HIT) {
            val ph = ((t - BEAT0) % BEAT) / BEAT
            val pulse = (1 - ph).pow(3) * 0.16f * ((t - BEAT0) / 1.5f).coerceIn(0f, 1f)
            p.shader = RadialGradient(w / 2, h * 0.5f, h * 0.75f, intArrayOf(0, 0, ca(a1, (255 * pulse).toInt())), floatArrayOf(0f, 0.7f, 1f), Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w, h, p); p.shader = null
        }
    }

    // specks of light drifting up
    for (i in 0 until 28) {
        val sp = 22f + 55f * rnd(i, 21)
        val y = h + 60f - ((t * sp + rnd(i, 22) * h * 1.15f) % (h * 1.15f))
        val x = rnd(i, 23) * w + 36f * sin(t * (0.4f + rnd(i, 24)) + i * 2f)
        val tw = 0.55f + 0.45f * sin(t * (1.3f + 2f * rnd(i, 25)) + i)
        val r = 5f + 22f * rnd(i, 26).pow(2)
        val col = when (i % 3) { 0 -> a1; 1 -> a2; else -> 0xFFFFFFFF.toInt() }
        if (light && col == 0xFFFFFFFF.toInt()) continue
        spark(c, p, x, y, r * 2.4f, col, ((if (light) 55 else 85) * tw.coerceIn(0f, 1f) * (t / 0.8f).coerceIn(0f, 1f)).toInt())
    }

    // the opening: a flare streaking out from the middle on the first drum, the club's light leaking in from two corners
    if (t < 1.6f) {
        val q = ((t - 0.12f) / 0.9f).coerceIn(0f, 1f)
        if (q > 0f && q < 1f) {
            val a = sin(PI * q).toFloat()
            val half = w * (0.25f + 1.1f * q); val y = h * 0.38f
            p.shader = LinearGradient(w / 2 - half, 0f, w / 2 + half, 0f, intArrayOf(0, ca(a1, (200 * a).toInt()), ca(0xFFFFFFFF.toInt(), (255 * a).toInt()), ca(a1, (200 * a).toInt()), 0),
                floatArrayOf(0f, 0.3f, 0.5f, 0.7f, 1f), Shader.TileMode.CLAMP)
            c.drawRect(w / 2 - half, y - 3f, w / 2 + half, y + 3f, p)
            p.shader = LinearGradient(0f, y - 70f, 0f, y + 70f, intArrayOf(0, ca(a1, (70 * a).toInt()), 0), null, Shader.TileMode.CLAMP)
            c.drawRect(w / 2 - half * 0.8f, y - 70f, w / 2 + half * 0.8f, y + 70f, p); p.shader = null
            spark(c, p, w / 2, y, 160f * a + 40f, 0xFFFFFFFF.toInt(), (230 * a).toInt())
        }
        val lq = (t / 1.6f).coerceIn(0f, 1f); val la = sin(PI * lq).toFloat().pow(1.5f) * (if (light) 0.35f else 0.6f)
        p.shader = RadialGradient(-w * 0.1f + w * 0.3f * lq, -h * 0.05f, w * 1.1f, intArrayOf(ca(a1, (255 * la).toInt()), 0), null, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p)
        p.shader = RadialGradient(w * 1.1f - w * 0.3f * lq, h * 1.05f, w * 1.1f, intArrayOf(ca(a2, (200 * la).toInt()), 0), null, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p); p.shader = null
    }

    // the hit: a flash and a ring of light going out from the middle
    if (t >= STORY_HIT && t < STORY_HIT + 1.2f) {
        val d = t - STORY_HIT
        val f = exp(-d * 9f) * 0.32f
        c.drawColor(ca(0xFFFFFFFF.toInt(), (255 * f).toInt()))
        val R = 80f + d * h * 1.3f; val a = (1 - d / 1.2f).pow(2)
        val ring = 90f / R
        p.shader = RadialGradient(w / 2, h / 2, R, intArrayOf(0, 0, ca(a1, (150 * a).toInt()), ca(0xFFFFFFFF.toInt(), (120 * a).toInt()), 0),
            floatArrayOf(0f, (1 - ring * 2).coerceAtLeast(0f), 1 - ring, (1 - ring * 0.4f), 1f), Shader.TileMode.CLAMP)
        c.drawCircle(w / 2, h / 2, R, p); p.shader = null
    }
}
