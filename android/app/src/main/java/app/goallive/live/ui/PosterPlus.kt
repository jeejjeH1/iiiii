package app.goallive.live.ui

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import app.goallive.live.Lang
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/*
 * The two showpiece line-up designs:
 *  - «کارت»: every player as a game-style card (a shield in the club's colours with a gold edge: the rating large, the
 *    number and crest under it, the photo fading into the card, the name on a bar and what he did along the foot); the
 *    best player's card black and gold, glowing. In the video the cards flip into place, one by one.
 *  - «ورزشگاه»: a night stadium: floodlights flaring in the top corners with their beams, the stands full of lights, and
 *    the pitch in perspective with the players standing on it, smaller towards the far end where the forwards are.
 */

private const val W_ = 0xFFFFFFFF.toInt()
private const val GOLD_ = 0xFFF5C542.toInt()
private fun al(c: Int, a: Float) = (c and 0x00FFFFFF) or ((a * 255).toInt().coerceIn(0, 255) shl 24)

/** [c] made light enough to glow on a dark page. */
internal fun glowOf(c: Int): Int {
    var x = c or 0xFF000000.toInt(); var k = 0
    while (ColorUtils.calculateLuminance(x) < 0.22 && k++ < 10) x = ColorUtils.blendARGB(x, W_, 0.14f)
    return x
}

/** A few sparkles: four-pointed stars of light at fixed places (the same in every frame). */
internal fun sparkles(c: Canvas, W: Int, H: Int, n: Int, col: Int, seed: Long, top: Float = 0f, bottom: Float = H.toFloat()) {
    val rnd = java.util.Random(seed); val p = Paint(Paint.ANTI_ALIAS_FLAG)
    repeat(n) {
        val x = rnd.nextFloat() * W; val y = top + rnd.nextFloat() * (bottom - top); val s = 3f + rnd.nextFloat() * 9f
        p.color = al(if (rnd.nextInt(3) == 0) W_ else col, 0.25f + rnd.nextFloat() * 0.6f)
        val path = Path().apply { moveTo(x, y - s * 2.2f); quadTo(x, y, x + s * 2.2f, y); quadTo(x, y, x, y + s * 2.2f); quadTo(x, y, x - s * 2.2f, y); quadTo(x, y, x, y - s * 2.2f); close() }
        c.drawPath(path, p)
    }
}

/** Light rays fanning down from above the page, in [col]. */
internal fun rays(c: Canvas, W: Int, H: Int, col: Int, strong: Float) {
    val p = Paint(Paint.ANTI_ALIAS_FLAG); val w = W.toFloat()
    for (k in 0 until 23) {
        val an = Math.toRadians(14.0 + k * 6.9)
        val path = Path().apply {
            moveTo(w / 2, -60f)
            lineTo((w / 2 + 2300 * cos(an - 0.028)).toFloat(), (-60 + 2300 * sin(an - 0.028)).toFloat())
            lineTo((w / 2 + 2300 * cos(an + 0.028)).toFloat(), (-60 + 2300 * sin(an + 0.028)).toFloat()); close()
        }
        p.shader = LinearGradient(w / 2, -60f, w / 2, H * 0.75f, al(col, (if (k % 2 == 1) 0.16f else 0.07f) * strong), 0, Shader.TileMode.CLAMP)
        c.drawPath(path, p)
    }
    p.shader = null
}

/* ---------- «کارت» ---------- */

/** The page behind the cards: the club's dark colour, its glow, rays from above, its crest huge and faint, sparkles. */
internal fun cardsPage(c: Canvas, W: Int, H: Int, main: Int, deep: Int, mark: Bitmap?) {
    val p = Paint(Paint.ANTI_ALIAS_FLAG); val w = W.toFloat(); val h = H.toFloat()
    p.shader = LinearGradient(0f, 0f, 0f, h, intArrayOf(ColorUtils.blendARGB(deep, 0xFF000000.toInt(), 0.25f), 0xFF05060A.toInt(), ColorUtils.blendARGB(deep, 0xFF000000.toInt(), 0.5f)),
        floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
    c.drawRect(0f, 0f, w, h, p)
    p.shader = RadialGradient(w / 2, h * 0.46f, w * 0.95f, intArrayOf(al(glowOf(main), 0.42f), al(main, 0.12f), 0), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
    c.drawRect(0f, 0f, w, h, p); p.shader = null
    rays(c, W, H, glowOf(main), 1.2f)
    mark?.let { c.drawBitmap(it, null, RectF(w / 2 - 520, h * 0.46f - 520, w / 2 + 520, h * 0.46f + 520), Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = 16 }) }
    sparkles(c, W, H, 46, GOLD_, 7)
    grain(c, W, H, false)
}

/** One player's card: a shield, [cw] wide, its top at [top], centred on [cx]. */
internal fun futCard(c: Canvas, m: PitchMan, cx: Float, top: Float, cw: Float, main: Int, best: Boolean, face: Bitmap?, crest: Bitmap?, tf: Typeface, shine: Float = -1f, rateP: Float = 1f) {
    val ch = cw * 1.38f
    val l = cx - cw / 2; val r = cx + cw / 2; val b = top + ch
    val shield = Path().apply {
        moveTo(l + cw * 0.13f, top); lineTo(r - cw * 0.13f, top)
        quadTo(r - cw * 0.02f, top + ch * 0.005f, r, top + ch * 0.09f)
        lineTo(r, top + ch * 0.8f); quadTo(r, top + ch * 0.86f, r - cw * 0.09f, top + ch * 0.895f)
        lineTo(cx + cw * 0.09f, top + ch * 0.985f); quadTo(cx, b + ch * 0.008f, cx - cw * 0.09f, top + ch * 0.985f)
        lineTo(l + cw * 0.09f, top + ch * 0.895f); quadTo(l, top + ch * 0.86f, l, top + ch * 0.8f)
        lineTo(l, top + ch * 0.09f); quadTo(l + cw * 0.02f, top + ch * 0.005f, l + cw * 0.13f, top); close()
    }
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    val edge = if (best) GOLD_ else ColorUtils.blendARGB(GOLD_, W_, 0.15f)
    // its glow and shadow
    p.color = 0xFF000000.toInt(); p.setShadowLayer(cw * 0.12f, 0f, cw * 0.06f, 0x99000000.toInt()); c.drawPath(shield, p); p.clearShadowLayer()
    if (best) { p.maskFilter = BlurMaskFilter(cw * 0.16f, BlurMaskFilter.Blur.OUTER); p.color = al(GOLD_, 0.9f); c.drawPath(shield, p); p.maskFilter = null }
    // the face of the card: the club's colour (black and gold for the best), a sheen across it, fine lines
    c.save(); c.clipPath(shield)
    val a = if (best) 0xFF2A2208.toInt() else ColorUtils.blendARGB(main or 0xFF000000.toInt(), W_, 0.12f)
    val z = if (best) 0xFF050403.toInt() else ColorUtils.blendARGB(main or 0xFF000000.toInt(), 0xFF000000.toInt(), 0.55f)
    p.shader = LinearGradient(l, top, r, b, intArrayOf(a, z, a), floatArrayOf(0f, 0.65f, 1f), Shader.TileMode.CLAMP); c.drawRect(l, top, r, b, p)
    p.shader = LinearGradient(l, top, r, top + ch * 0.6f, intArrayOf(0x00FFFFFF, 0x33FFFFFF, 0x00FFFFFF), floatArrayOf(0.25f, 0.42f, 0.6f), Shader.TileMode.CLAMP)
    c.drawRect(l, top, r, b, p); p.shader = null
    p.color = al(if (best) GOLD_ else W_, 0.05f); p.strokeWidth = 2f
    var k = -ch; while (k < cw) { c.drawLine(l + k, top, l + k + ch, b, p); k += 14f }
    // the photo, fading into the card at its foot
    val ps = cw * 0.74f
    val px = if (Lang.en) r - ps - cw * 0.02f else l + cw * 0.02f
    val py = top + ch * 0.07f
    face?.let { f ->
        val n = c.saveLayer(px, py, px + ps, py + ps, null)
        c.drawBitmap(f, null, RectF(px, py, px + ps, py + ps), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        val mask = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN); shader = LinearGradient(0f, py + ps * 0.62f, 0f, py + ps, W_, 0, Shader.TileMode.CLAMP) }
        c.drawRect(px, py, px + ps, py + ps, mask)
        c.restoreToCount(n)
    }
    c.restore()
    // the rating column at the reading start: the rating on a badge in its colour (counting up as the card lands), the shirt number, the crest
    val colX = if (Lang.en) l + cw * 0.22f else r - cw * 0.22f
    val ink = if (best) GOLD_ else W_
    val rating = m.rating
    if (rating != null) {
        val bw = cw * 0.38f; val bh = cw * 0.26f; val by = top + ch * 0.155f
        val br = RectF(colX - bw / 2, by - bh / 2, colX + bw / 2, by + bh / 2)
        val rc = ratingColor(rating) or 0xFF000000.toInt()
        p.shader = LinearGradient(0f, br.top, 0f, br.bottom, ColorUtils.blendARGB(rc, W_, 0.25f), ColorUtils.blendARGB(rc, 0xFF000000.toInt(), 0.2f), Shader.TileMode.CLAMP)
        p.setShadowLayer(cw * 0.05f, 0f, cw * 0.02f, 0x99000000.toInt()); c.drawRoundRect(br, bh * 0.3f, bh * 0.3f, p); p.clearShadowLayer(); p.shader = null
        p.style = Paint.Style.STROKE; p.strokeWidth = cw * 0.014f; p.color = if (best) GOLD_ else 0xCCFFFFFF.toInt()
        c.drawRoundRect(br, bh * 0.3f, bh * 0.3f, p); p.style = Paint.Style.FILL
        val shown = rating * rateP.coerceIn(0f, 1f)
        rateText(c, shown, colX, by, cw * (if (rating >= 9.95) 0.165f else 0.2f), W_, tf)
        if (m.number.isNotEmpty()) textAt(c, "#" + fa(m.number), colX, top + ch * 0.275f, cw * 0.085f, al(ink, 0.85f), tf, false)
    } else textAt(c, fa(m.number), colX, top + ch * 0.16f, cw * 0.19f, ink, tf, true)
    p.color = al(ink, 0.6f); c.drawRect(colX - cw * 0.08f, top + ch * 0.31f, colX + cw * 0.08f, top + ch * 0.31f + 2.5f, p)
    crest?.let { cb -> val s = cw * 0.17f; c.drawBitmap(cb, null, RectF(colX - s / 2, top + ch * 0.34f, colX + s / 2, top + ch * 0.34f + s), Paint(Paint.FILTER_BITMAP_FLAG)) }
    if (m.captain) {
        val cy2 = top + ch * 0.34f + cw * 0.17f + cw * 0.1f
        p.color = GOLD_; c.drawCircle(colX, cy2, cw * 0.075f, p)
        textAt(c, if (Lang.en) "C" else "ک", colX, cy2, cw * 0.085f, 0xFF2A1A00.toInt(), tf, true)
    }
    if (best) {
        val sx = if (Lang.en) r - cw * 0.14f else l + cw * 0.14f
        val sp = Path(); val R = cw * 0.075f
        for (i in 0 until 10) { val an = Math.toRadians(-90.0 + i * 36); val rr = if (i % 2 == 0) R else R * 0.45f
            val x = sx + rr * cos(an).toFloat(); val y = top + ch * 0.1f + rr * sin(an).toFloat(); if (i == 0) sp.moveTo(x, y) else sp.lineTo(x, y) }
        sp.close(); p.color = GOLD_; c.drawPath(sp, p)
    }
    // the name on its bar, between two gold rules
    val ny = top + ch * 0.73f
    p.color = al(0xFF000000.toInt(), 0.35f); c.drawRect(l + 3, ny - ch * 0.06f, r - 3, ny + ch * 0.06f, p)
    p.color = al(edge, 0.85f); c.drawRect(l + cw * 0.12f, ny - ch * 0.065f, r - cw * 0.12f, ny - ch * 0.065f + 2f, p)
    c.drawRect(l + cw * 0.12f, ny + ch * 0.063f, r - cw * 0.12f, ny + ch * 0.063f + 2f, p)
    fitText(c, m.name, cx, ny, cw * 0.13f, if (best) GOLD_ else W_, tf, cw * 0.84f)
    // what he did, along the foot
    val marks = ArrayList<(Float) -> Float>()
    val ms = cw * 0.075f
    repeat(min(3, m.goals)) { marks += { x -> ball(c, x + ms, top + ch * 0.85f, ms); ms * 2 + 4 } }
    repeat(min(2, m.assists)) { marks += { x -> p.color = 0xFF14B866.toInt(); c.drawCircle(x + ms, top + ch * 0.85f, ms, p); textAt(c, if (Lang.en) "A" else "پ", x + ms, top + ch * 0.85f, ms * 1.1f, W_, tf, true); ms * 2 + 4 } }
    if (m.red || m.yellow) marks += { x -> p.color = if (m.red) 0xFFE5434B.toInt() else GOLD_; c.drawRoundRect(RectF(x, top + ch * 0.85f - ms, x + ms * 1.4f, top + ch * 0.85f + ms), 3f, 3f, p); ms * 1.4f + 4 }
    m.outAt?.takeIf { it.isNotBlank() }?.let { t -> marks += { x -> textAt(c, fa(t) + "′", x + ms * 1.3f, top + ch * 0.85f, ms * 1.3f, 0xFFFF8A8A.toInt(), tf, true); ms * 2.6f } }
    if (marks.isNotEmpty()) {
        val total = marks.size * (ms * 2 + 4)
        var x = cx - total / 2
        for (f in marks) x += f(x)
    }
    // the edge: gold, doubled
    p.style = Paint.Style.STROKE; p.strokeWidth = cw * 0.022f; p.color = edge; c.drawPath(shield, p)
    c.save(); c.scale(0.93f, 0.95f, cx, top + ch * 0.48f); p.strokeWidth = 1.5f; p.color = al(edge, 0.55f); c.drawPath(shield, p); c.restore()
    p.style = Paint.Style.FILL
    // a light passing over it, as when a card is revealed
    if (shine > 0f && shine < 1f) {
        c.save(); c.clipPath(shield)
        val x = l - cw * 0.6f + shine * cw * 2.2f
        p.shader = LinearGradient(x - cw * 0.22f, top, x + cw * 0.22f, top + ch * 0.25f, intArrayOf(0x00FFFFFF, 0x80FFFFFF.toInt(), 0x00FFFFFF), null, Shader.TileMode.CLAMP)
        c.drawRect(l, top, r, b, p); p.shader = null
        c.restore()
    }
}

/** A burst of light rays round ([cx], [cy]), turned by [deg]: behind the best player's card. */
internal fun burst(c: Canvas, cx: Float, cy: Float, R: Float, deg: Float, col: Int, a: Float) {
    if (a <= 0f) return
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.shader = RadialGradient(cx, cy, R, intArrayOf(al(col, 0.55f * a), al(col, 0.18f * a), 0), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
    val path = Path()
    for (k in 0 until 18) {
        val a0 = Math.toRadians((deg + k * 20).toDouble()); val a1 = Math.toRadians((deg + k * 20 + 7).toDouble())
        path.moveTo(cx, cy); path.lineTo(cx + R * cos(a0).toFloat(), cy + R * sin(a0).toFloat()); path.lineTo(cx + R * cos(a1).toFloat(), cy + R * sin(a1).toFloat()); path.close()
    }
    c.drawPath(path, p)
    p.shader = RadialGradient(cx, cy, R * 0.6f, intArrayOf(al(col, 0.45f * a), 0), null, Shader.TileMode.CLAMP); c.drawCircle(cx, cy, R * 0.6f, p)
}

/**
 * A rating as a scoreboard writes it: the whole number large and the tenth smaller beside it on one baseline (so «۷٫۶» never
 * reads as a fraction), centred on ([cx], [cy]). Returns its width; measures only when [c] is null.
 */
internal fun rateText(c: Canvas?, r: Double, cx: Float, cy: Float, size: Float, color: Int, tf: Typeface, shadow: Boolean = true): Float {
    val tenths = Math.round(r * 10).toInt().coerceAtLeast(0)
    val whole = fa(tenths / 10); val frac = (if (Lang.en) "." else "٫") + fa(tenths % 10)
    val big = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; typeface = tf; this.color = color; if (shadow) setShadowLayer(size * 0.12f, 0f, size * 0.05f, 0x80000000.toInt()) }
    val small = Paint(big).apply { textSize = size * 0.6f }
    val wb = big.measureText(whole); val ws = small.measureText(frac)
    if (c != null) {
        val fm = big.fontMetrics; val base = cy - (fm.ascent + fm.descent) / 2
        val left = cx - (wb + ws) / 2
        c.drawText(whole, left, base, big)
        c.drawText(frac, left + wb, base, small)
    }
    return wb + ws
}

private fun textAt(c: Canvas, s: String, cx: Float, cy: Float, size: Float, color: Int, tf: Typeface, shadow: Boolean) {
    val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; typeface = tf; this.color = color; textAlign = Paint.Align.CENTER; if (shadow) setShadowLayer(size * 0.15f, 0f, size * 0.06f, 0x99000000.toInt()) }
    val fm = tp.fontMetrics
    c.drawText(s, cx, cy - (fm.ascent + fm.descent) / 2, tp)
}

private fun fitText(c: Canvas, s: String, cx: Float, cy: Float, size: Float, color: Int, tf: Typeface, maxW: Float) {
    val tp = android.text.TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; typeface = tf; this.color = color; textAlign = Paint.Align.CENTER }
    while (tp.measureText(s) > maxW && tp.textSize > size * 0.6f) tp.textSize -= 1f
    val t = android.text.TextUtils.ellipsize(s, tp, maxW, android.text.TextUtils.TruncateAt.END).toString()
    val fm = tp.fontMetrics
    c.drawText(t, cx, cy - (fm.ascent + fm.descent) / 2, tp)
}

private fun ball(c: Canvas, cx: Float, cy: Float, r: Float) {
    val p = Paint(Paint.ANTI_ALIAS_FLAG); p.color = W_; c.drawCircle(cx, cy, r, p)
    p.color = 0xFF111111.toInt(); val path = Path(); val k = r * 0.38f
    for (i in 0 until 5) { val a = Math.toRadians(-90.0 + i * 72); val x = cx + k * cos(a).toFloat(); val y = cy + k * sin(a).toFloat(); if (i == 0) path.moveTo(x, y) else path.lineTo(x, y) }
    path.close(); c.drawPath(path, p)
    p.style = Paint.Style.STROKE; p.strokeWidth = r * 0.14f; c.drawCircle(cx, cy, r * 0.93f, p)
}

/** The cards in their rows (forwards on top, or at the bottom when [flip]), sized so every row fits; each flips in on its turn and a light passes over it. */
internal fun placeCards(c: Canvas, men: List<PitchMan>, box: RectF, main: Int, faces: Map<Int, Bitmap?>, crests: Map<Int, Bitmap?>, teamCrest: Bitmap?,
                        tf: Typeface, clk: Clock, start: Float, flip: Boolean = false, maxW: Float = 215f) {
    if (men.isEmpty()) return
    val rows = ArrayList<MutableList<PitchMan>>()
    for (m in men.sortedBy { it.y }) { val last = rows.lastOrNull(); if (last != null && m.y - last.last().y < 0.075f) last += m else rows += mutableListOf(m) }
    val n = rows.size; val widest = rows.maxOf { it.size }
    val gap = 12f
    var cw = min(maxW, (box.width() - 20) / widest - 14f)
    if (n * cw * 1.38f + (n - 1) * gap > box.height()) cw = (box.height() - (n - 1) * gap) / n / 1.38f
    val ch = cw * 1.38f
    val best = men.firstOrNull { it.star }
    val all = men.size
    // where each card goes, the keeper's row first so the team builds from the back
    class Spot(val m: PitchMan, val cx: Float, val top: Float, val k: Int)
    val spots = ArrayList<Spot>()
    var k = 0
    for ((ri, row) in rows.withIndex().reversed()) {
        val f = if (n > 1) ri / (n - 1f) else 0.5f
        val top = box.top + (if (flip) 1 - f else f) * (box.height() - ch)
        val across = (box.width() - 20) / row.size
        row.sortedBy { it.x }.forEachIndexed { i, m ->
            val u = (i + 0.5f) * across
            spots += Spot(m, box.left + 10 + if (flip) box.width() - 20 - u else u, top, k++)
        }
    }
    // the best player's rays first, behind every card
    val t = if (clk.still) LIVE_STILL_T else clk.t
    spots.firstOrNull { it.m === best }?.let { s -> burst(c, s.cx, s.top + ch * 0.48f, ch * 1.05f, t * 14f, GOLD_, if (clk.still) 1f else clk.at(start + all * 0.09f + 0.3f, 0.8f)) }
    for (s in spots) {
        val star = s.m === best
        val at = start + s.k * 0.09f
        // the light passes once as the card lands; over the best card, again every couple of seconds
        var shine = if (clk.still) -1f else clk.at(at + 0.42f, 0.55f).let { if (it in 0.001f..0.999f) it else -1f }
        if (star && !clk.still) { val tt = clk.t - (start + all * 0.09f + 0.9f); if (tt > 0f && tt % 1.9f < 0.7f) shine = (tt % 1.9f) / 0.7f }
        c.flipIn(clk.at(at, 0.5f), s.cx, s.top + ch / 2) {
            futCard(c, s.m, s.cx, s.top, cw, s.m.color?.toArgb() ?: main, star, faces[s.m.id], s.m.crest?.let { crests[it] } ?: teamCrest, tf, shine,
                if (clk.still) 1f else easeOut(clk.at(at + 0.3f, 0.8f)))
        }
    }
}

/** Turns [block] in like a card being flipped over: from edge-on to flat, growing a little past its size. */
internal inline fun Canvas.flipIn(p: Float, cx: Float, cy: Float, block: () -> Unit) {
    if (p <= 0f) return
    if (p >= 1f) { block(); return }
    val e = easeOut(p)
    val n = saveLayerAlpha(cx - 1400f, cy - 1400f, cx + 1400f, cy + 1400f, (255 * min(1f, p * 3f)).toInt())
    scale(kotlin.math.max(0.02f, e), 0.85f + 0.15f * easeBack(p), cx, cy)
    block()
    restoreToCount(n)
}

/* ---------- «ورزشگاه» ---------- */

/** The night: a dark sky, the stands full of lights, floodlights flaring with their beams, a haze over the far end. [pitchTop] is where the grass starts. */
internal fun stadiumPage(c: Canvas, W: Int, H: Int, main: Int, accent: Int, pitchTop: Float) {
    val p = Paint(Paint.ANTI_ALIAS_FLAG); val w = W.toFloat(); val h = H.toFloat()
    p.shader = LinearGradient(0f, 0f, 0f, h, intArrayOf(0xFF02040A.toInt(), ColorUtils.blendARGB(main or 0xFF000000.toInt(), 0xFF02040A.toInt(), 0.75f), 0xFF050B08.toInt()),
        floatArrayOf(0f, 0.3f, 1f), Shader.TileMode.CLAMP)
    c.drawRect(0f, 0f, w, h, p); p.shader = null
    // the stands: a band rising out of the night, in tiers, with thousands of small lights, the club's colours among them
    val sTop = pitchTop - STANDS; val sBot = pitchTop + 30f
    p.shader = LinearGradient(0f, sTop - 90f, 0f, sBot, intArrayOf(0x000B0E16, 0xFF0B0E16.toInt(), 0xFF161B26.toInt()), floatArrayOf(0f, 0.3f, 1f), Shader.TileMode.CLAMP)
    c.drawRect(0f, sTop - 90f, w, sBot, p); p.shader = null
    for (k in 1..4) {
        val y = sTop + (sBot - sTop) * k / 5f
        p.color = 0x14FFFFFF; c.drawRect(0f, y, w, y + 2f, p)
        p.color = 0x33000000; c.drawRect(0f, y + 2f, w, y + 10f, p)
    }
    val rnd = java.util.Random(3)
    val cols = intArrayOf(glowOf(main), W_, accent, glowOf(main), 0xFFFFE9A8.toInt())
    repeat(900) {
        val y = sTop + rnd.nextFloat() * (sBot - sTop); val x = rnd.nextFloat() * w
        p.color = al(cols[rnd.nextInt(cols.size)], 0.15f + rnd.nextFloat() * 0.55f)
        c.drawCircle(x, y, 1.2f + rnd.nextFloat() * 2.6f, p)
    }
    // the club's colour glowing over the stands
    p.shader = RadialGradient(w / 2, sTop + 80f, w * 0.7f, al(glowOf(main), 0.35f), 0, Shader.TileMode.CLAMP); c.drawRect(0f, sTop, w, sBot, p); p.shader = null
    // floodlights: towers in the corners, each flaring, a beam to the pitch
    for (side in listOf(-1f, 1f)) {
        val lx = w / 2 + side * w * 0.42f; val ly = sTop - 140f
        val beam = Path().apply { moveTo(lx - 30f, ly); lineTo(lx + 30f, ly); lineTo(w / 2 - side * w * 0.05f + 330f, h * 0.92f); lineTo(w / 2 - side * w * 0.05f - 330f, h * 0.92f); close() }
        p.shader = LinearGradient(lx, ly, w / 2, h * 0.9f, 0x22FFFFFF, 0, Shader.TileMode.CLAMP); c.drawPath(beam, p); p.shader = null
        p.color = 0xFF1A1F29.toInt(); c.drawRect(lx - 70f, ly - 34f, lx + 70f, ly + 30f, p)
        for (i in 0 until 4) for (j in 0 until 2) { p.color = 0xFFFFF6DA.toInt(); c.drawCircle(lx - 50f + i * 33f, ly - 18f + j * 30f, 9f, p) }
        p.shader = RadialGradient(lx, ly, 300f, intArrayOf(0xCCFFFFFF.toInt(), 0x44FFF1C6, 0), floatArrayOf(0f, 0.3f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(lx, ly, 300f, p); p.shader = null
        // the lens's streaks
        p.color = 0x55FFFFFF; c.drawRect(lx - 260f, ly - 2f, lx + 260f, ly + 2f, p); c.drawRect(lx - 2f, ly - 120f, lx + 2f, ly + 120f, p)
    }
    sparkles(c, W, H, 30, 0xFFFFF1C6.toInt(), 21, 0f, sTop)
}

/** The pitch in perspective inside [r]: the far end narrower. A point on it by (across 0..1, far 0 .. near 1). */
internal class Perspective(val r: RectF, val far: Float = 0.66f) {
    fun width(v: Float) = r.width() * (far + (1 - far) * v)
    fun y(v: Float) = r.top + r.height() * (v * (0.82f + 0.18f * v))
    fun x(u: Float, v: Float) = r.centerX() + (u - 0.5f) * width(v)
}

/** The grass: mown bands in perspective, the markings, a glow of the club's colour from the near end and haze at the far end. */
internal fun stadiumPitch(c: Canvas, ps: Perspective, main: Int) {
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    fun quad(v0: Float, v1: Float, u0: Float = 0f, u1: Float = 1f) = Path().apply {
        moveTo(ps.x(u0, v0), ps.y(v0)); lineTo(ps.x(u1, v0), ps.y(v0)); lineTo(ps.x(u1, v1), ps.y(v1)); lineTo(ps.x(u0, v1), ps.y(v1)); close()
    }
    // the grass runs past the lines to the boards, mown in bands
    val far = -0.07f
    val bands = 12
    for (k in -1 until bands) {
        val v0 = if (k < 0) far else k / bands.toFloat()
        p.color = if (k % 2 == 0) 0xFF1F8A4C.toInt() else 0xFF1A7A43.toInt()
        c.drawPath(quad(v0, (k + 1) / bands.toFloat() + 0.002f, -0.6f, 1.6f), p)
    }
    val ground = quad(far, 1f, -0.6f, 1.6f)
    c.save(); c.clipPath(ground)
    p.shader = LinearGradient(0f, ps.y(far), 0f, ps.y(1f), intArrayOf(0x88000000.toInt(), 0x00000000, al(glowOf(main), 0.28f)), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
    c.drawRect(ps.r.left - 400, ps.y(far), ps.r.right + 400, ps.r.bottom, p); p.shader = null
    // light pooled in the middle of the pitch, darker towards the sides
    p.shader = RadialGradient(ps.r.centerX(), ps.y(0.5f), ps.r.width() * 0.75f, intArrayOf(0x22FFFFFF, 0x00FFFFFF, 0x55000000), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
    c.drawRect(ps.r.left - 400, ps.y(far), ps.r.right + 400, ps.r.bottom, p); p.shader = null
    c.restore()
    // the boards along the far end, lit in the club's colour
    val by = ps.y(far); val bh = 30f
    p.color = 0xFF07090D.toInt(); c.drawRect(ps.r.left - 10, by - bh, ps.r.right + 10, by, p)
    p.shader = LinearGradient(ps.r.left, 0f, ps.r.right, 0f, intArrayOf(al(glowOf(main), 0.9f), al(glowOf(main), 0.35f), al(glowOf(main), 0.9f), al(glowOf(main), 0.35f), al(glowOf(main), 0.9f)), null, Shader.TileMode.CLAMP)
    c.drawRect(ps.r.left - 10, by - bh + 5, ps.r.right + 10, by - 5, p); p.shader = null
    p.shader = LinearGradient(0f, by - bh - 40f, 0f, by - bh, 0, al(glowOf(main), 0.35f), Shader.TileMode.CLAMP)
    c.drawRect(ps.r.left - 10, by - bh - 40f, ps.r.right + 10, by - bh, p); p.shader = null
    val all = quad(0f, 1f)
    // markings: 68 m across, 105 long (only what lies on this half-and-a-bit of pitch)
    p.style = Paint.Style.STROKE; p.color = 0xBFFFFFFF.toInt(); p.strokeWidth = 4f
    fun seg(u0: Float, v0: Float, u1: Float, v1: Float) = c.drawLine(ps.x(u0, v0), ps.y(v0), ps.x(u1, v1), ps.y(v1), p)
    c.drawPath(all, p)
    seg(0f, 0.5f, 1f, 0.5f)
    // the centre circle as an ellipse in perspective
    val path = Path(); for (i in 0..48) { val a = i / 48.0 * 2 * Math.PI; val u = 0.5f + 0.135f * cos(a).toFloat(); val v = 0.5f + 0.09f * sin(a).toFloat()
        if (i == 0) path.moveTo(ps.x(u, v), ps.y(v)) else path.lineTo(ps.x(u, v), ps.y(v)) }
    c.drawPath(path, p)
    for (near in listOf(false, true)) {
        val e = if (near) 1f else 0f; val d = if (near) -1f else 1f
        seg(0.2f, e, 0.2f, e + d * 0.16f); seg(0.8f, e, 0.8f, e + d * 0.16f); seg(0.2f, e + d * 0.16f, 0.8f, e + d * 0.16f)
        seg(0.37f, e, 0.37f, e + d * 0.055f); seg(0.63f, e, 0.63f, e + d * 0.055f); seg(0.37f, e + d * 0.055f, 0.63f, e + d * 0.055f)
    }
    p.style = Paint.Style.FILL
}

/** The eleven standing on the pitch in perspective: forwards far and small, the keeper near and large; drawn far to near, the keeper's row animated first. */
internal inline fun placeStanding(men: List<PitchMan>, ps: Perspective, dNear: Float, draw: (m: PitchMan, cx: Float, feet: Float, d: Float, order: Int) -> Unit) {
    if (men.isEmpty()) return
    val rows = ArrayList<MutableList<PitchMan>>()
    for (m in men.sortedBy { it.y }) { val last = rows.lastOrNull(); if (last != null && m.y - last.last().y < 0.075f) last += m else rows += mutableListOf(m) }
    val n = rows.size
    var k = men.size
    for ((ri, row) in rows.withIndex()) {
        val v = 0.1f + 0.8f * (if (n > 1) ri / (n - 1f) else 0.5f)
        val span = ps.width(v) * 0.78f
        val gap = if (row.size > 1) span / (row.size - 1) else span
        // nearer is larger, and never so large that two plates touch
        val d = min(dNear * (0.6f + 0.4f * v), gap - 48f)
        row.sortedBy { it.x }.forEachIndexed { i, m ->
            val u = if (row.size == 1) 0.5f else 0.11f + 0.78f * i / (row.size - 1f)
            draw(m, ps.x(u, v), ps.y(v), d, --k)
        }
    }
}

/** A soft shadow on the grass under a player standing at ([cx], [y]). */
internal fun feetShadow(c: Canvas, cx: Float, y: Float, d: Float) {
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.shader = RadialGradient(cx, y, d * 0.6f, 0x88000000.toInt(), 0, Shader.TileMode.CLAMP)
    c.save(); c.scale(1f, 0.28f, cx, y); c.drawCircle(cx, y, d * 0.6f, p); c.restore()
}

/** Where the stands begin above the pitch's far line. */
internal const val STANDS = 260f

/** The stadium alive: camera flashes popping in the stands, the floodlights breathing. */
internal fun stadiumLive(c: Canvas, W: Int, pitchTop: Float, t: Float) {
    val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    val sTop = pitchTop - STANDS; val w = W.toFloat()
    for (i in 0 until 22) {
        val period = 1.1f + 1.7f * rnd(i, 1)
        val ph = ((t + rnd(i, 2) * period) % period) / period
        if (ph > 0.14f) continue
        val a = 1f - ph / 0.14f; val cyc = (t / period).toInt()
        glint(c, p, rnd(i, 3 + cyc % 7) * w, sTop + 20f + (STANDS - 10f) * rnd(i, 11 + cyc % 5), 18f + 22f * rnd(i, 4), W_, (255 * a).toInt())
    }
    for ((k, side) in listOf(-1f, 1f).withIndex()) {
        val f = 0.5f + 0.5f * sin(t * 2.1f + k * 2f)
        spark(c, p, w / 2 + side * w * 0.42f, sTop - 140f, 170f + 50f * f, 0xFFFFF6DA.toInt(), (50 + 60 * f).toInt())
    }
}

/** A scene whose camera starts high on the floodlights and cranes down onto the pitch. */
internal class CraneScene(W: Int, H: Int, bg: Bitmap, hc: Int, ac: Int, fg: (Canvas, Clock) -> Unit) : Scene(W, H, bg, hc, ac, fg) {
    override fun frame(c: Canvas, t: Float) {
        val q = (t / 2.4f).coerceIn(0f, 1f); val e = q * q * (3 - 2 * q)
        val z = 1.3f - 0.3f * e
        c.save(); c.scale(z, z, W / 2f, H * (0.1f + 0.4f * e)); super.frame(c, t); c.restore()
    }
}
