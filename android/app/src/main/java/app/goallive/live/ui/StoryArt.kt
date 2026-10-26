package app.goallive.live.ui

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import app.goallive.live.Lang
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * Drawn art instead of emoji: the stickers a story can carry (glossy, die-cut with a white edge and a shadow, like real
 * stickers) and the small icons of the analyst's findings and turning points. Everything is drawn in unit coordinates
 * (−1..1) and scaled to the size asked for.
 */

/** The stickers, in the order the studio offers them ("" is none). */
val STICKERS = listOf("", "fire", "goal", "ball", "trophy", "crown", "star", "heart", "heartbreak", "goat", "wow", "bolt", "hundred", "siuu")

private fun unit(path: Path, cx: Float, cy: Float, s: Float): Path {
    val m = Matrix(); m.setScale(s, s); m.postTranslate(cx, cy)
    return Path(path).apply { transform(m) }
}

private fun star(points: Int, outer: Float, inner: Float, rot: Double = -PI / 2): Path = Path().apply {
    for (i in 0 until points * 2) {
        val r = if (i % 2 == 0) outer else inner
        val a = rot + PI * i / points
        val x = (r * cos(a)).toFloat(); val y = (r * sin(a)).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

private val FLAME = Path().apply {
    moveTo(0f, 1f)
    cubicTo(-0.62f, 1f, -0.86f, 0.42f, -0.58f, -0.12f)
    cubicTo(-0.5f, 0.12f, -0.36f, 0.22f, -0.24f, 0.18f)
    cubicTo(-0.42f, -0.36f, -0.12f, -0.74f, 0.08f, -1f)
    cubicTo(0.12f, -0.62f, 0.42f, -0.5f, 0.5f, -0.18f)
    cubicTo(0.56f, -0.3f, 0.6f, -0.4f, 0.58f, -0.52f)
    cubicTo(0.92f, -0.12f, 0.9f, 1f, 0f, 1f)
    close()
}

private val HEART = Path().apply {
    moveTo(0f, 0.9f)
    cubicTo(-0.2f, 0.7f, -1f, 0.2f, -1f, -0.3f)
    cubicTo(-1f, -0.75f, -0.55f, -0.95f, -0.25f, -0.85f)
    cubicTo(-0.1f, -0.8f, 0f, -0.65f, 0f, -0.55f)
    cubicTo(0f, -0.65f, 0.1f, -0.8f, 0.25f, -0.85f)
    cubicTo(0.55f, -0.95f, 1f, -0.75f, 1f, -0.3f)
    cubicTo(1f, 0.2f, 0.2f, 0.7f, 0f, 0.9f)
    close()
}

private val CROWN = Path().apply {
    moveTo(-0.8f, 0.5f); lineTo(-0.9f, -0.45f); lineTo(-0.45f, -0.02f); lineTo(0f, -0.72f); lineTo(0.45f, -0.02f); lineTo(0.9f, -0.45f); lineTo(0.8f, 0.5f); close()
}

private val BOLT = Path().apply {
    moveTo(0.22f, -1f); lineTo(-0.62f, 0.14f); lineTo(-0.06f, 0.14f); lineTo(-0.28f, 1f); lineTo(0.66f, -0.2f); lineTo(0.08f, -0.2f); lineTo(0.38f, -1f); close()
}

private val SHIELD = Path().apply {
    moveTo(0f, -0.85f); lineTo(0.7f, -0.6f); lineTo(0.62f, 0.15f)
    cubicTo(0.5f, 0.55f, 0.2f, 0.75f, 0f, 0.88f); cubicTo(-0.2f, 0.75f, -0.5f, 0.55f, -0.62f, 0.15f); lineTo(-0.7f, -0.6f); close()
}

/** The sticker's white die-cut edge and its shadow, round the shape [outline] (already in place). */
private fun dieCut(c: Canvas, outline: Path, s: Float) {
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL_AND_STROKE; strokeWidth = s * 0.2f; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
        color = 0xFFFFFFFF.toInt(); setShadowLayer(s * 0.14f, s * 0.03f, s * 0.08f, 0x70000000)
    }
    c.drawPath(outline, p)
}

/** Bold writing with a dark edge round it. */
private fun outlined(c: Canvas, s: String, cx: Float, base: Float, size: Float, fill: Int, edge: Int, tf: Typeface, fillShader: Shader? = null) {
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; typeface = tf; textAlign = Paint.Align.CENTER }
    p.style = Paint.Style.STROKE; p.strokeWidth = size * 0.16f; p.strokeJoin = Paint.Join.ROUND; p.color = edge
    c.drawText(s, cx, base, p)
    p.style = Paint.Style.FILL; p.color = fill; p.shader = fillShader
    c.drawText(s, cx, base, p)
}

private fun gold(cx: Float, top: Float, bottom: Float) =
    LinearGradient(cx, top, cx, bottom, intArrayOf(0xFFFFF4C2.toInt(), 0xFFFFCF3F.toInt(), 0xFFE09A12.toInt(), 0xFFFFE27A.toInt(), 0xFFB57400.toInt()),
        floatArrayOf(0f, 0.3f, 0.55f, 0.75f, 1f), Shader.TileMode.CLAMP)

/** A glossy highlight: a soft white oval near the top. */
private fun gloss(c: Canvas, cx: Float, cy: Float, rx: Float, ry: Float, rot: Float, a: Int = 0xB0) {
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.shader = RadialGradient(cx, cy, rx, intArrayOf(ca(0xFFFFFFFF.toInt(), a), ca(0xFFFFFFFF.toInt(), 0)), null, Shader.TileMode.CLAMP)
    c.save(); c.rotate(rot, cx, cy); c.scale(1f, ry / rx, cx, cy); c.drawCircle(cx, cy, rx, p); c.restore()
}

/** A three-dimensional star: each of its ten facets lit or in shade. */
private fun facetStar(c: Canvas, cx: Float, cy: Float, r: Float, light: Int, dark: Int) {
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    val pts = (0 until 10).map { i -> val rr = if (i % 2 == 0) r else r * 0.45f; val a = -PI / 2 + PI * i / 5; floatArrayOf(cx + (rr * cos(a)).toFloat(), cy + (rr * sin(a)).toFloat()) }
    for (i in 0 until 10) {
        val a = pts[i]; val b = pts[(i + 1) % 10]
        p.color = if (i % 2 == 0) light else dark
        c.drawPath(Path().apply { moveTo(cx, cy); lineTo(a[0], a[1]); lineTo(b[0], b[1]); close() }, p)
    }
}

/** A team colour fit for a heart: lifted, or red when the colour is too grey to love. */
private fun heartColour(tint: Int): Int {
    val hsv = FloatArray(3); android.graphics.Color.colorToHSV(tint or 0xFF000000.toInt(), hsv)
    if (hsv[1] < 0.3f || tint == 0) return 0xFFE5172F.toInt()
    hsv[2] = maxOf(hsv[2], 0.75f); hsv[1] = maxOf(hsv[1], 0.7f)
    return android.graphics.Color.HSVToColor(hsv)
}

/**
 * Draws sticker [key] centred on ([cx], [cy]), [s] from the centre to its edge. [tint] colours the heart (the team's colour);
 * [tf] is the font for the ones with words.
 */
internal fun drawSticker(c: Canvas, key: String, cx: Float, cy: Float, s: Float, tint: Int, tf: Typeface) {
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    val en = Lang.en
    when (key) {
        "fire" -> {
            val outer = unit(FLAME, cx, cy, s)
            dieCut(c, outer, s)
            p.shader = LinearGradient(cx, cy + s, cx, cy - s, intArrayOf(0xFFFFE066.toInt(), 0xFFFF8A00.toInt(), 0xFFFF2D2D.toInt()), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
            c.drawPath(outer, p)
            val inner = Path(FLAME).apply { transform(Matrix().apply { setScale(0.55f, 0.55f); postTranslate(0.02f, 0.42f) }) }
            p.shader = LinearGradient(cx, cy + s, cx, cy - s * 0.2f, intArrayOf(0xFFFFFFFF.toInt(), 0xFFFFF3A0.toInt(), 0xFFFFC300.toInt()), floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)
            c.drawPath(unit(inner, cx, cy, s), p); p.shader = null
            gloss(c, cx - s * 0.35f, cy - s * 0.05f, s * 0.2f, s * 0.08f, -60f, 0x90)
        }
        "goal" -> {
            val burst = unit(star(18, 1f, 0.8f), cx, cy, s)
            dieCut(c, burst, s)
            p.shader = RadialGradient(cx, cy, s, intArrayOf(0xFFFFE14D.toInt(), 0xFFFFB300.toInt()), null, Shader.TileMode.CLAMP); c.drawPath(burst, p)
            val core = unit(star(18, 0.84f, 0.68f, -PI / 2 + PI / 18), cx, cy, s)
            p.shader = RadialGradient(cx, cy - s * 0.3f, s, intArrayOf(0xFFFF5A4A.toInt(), 0xFFD0101C.toInt()), null, Shader.TileMode.CLAMP); c.drawPath(core, p); p.shader = null
            c.save(); c.rotate(-8f, cx, cy)
            val word = if (en) "GOAL!" else "گل!"
            val size = if (en) s * 0.5f else s * 0.78f
            outlined(c, word, cx, cy + size * 0.36f, size, 0xFFFFFFFF.toInt(), 0xFF2A0004.toInt(), tf)
            c.restore()
        }
        "ball" -> {
            val r = s * 0.62f; val bx = cx + s * 0.3f
            val outline = Path().apply { addCircle(bx, cy, r, Path.Direction.CW) }
            val lines = listOf(-0.32f to 0.75f, 0f to 1f, 0.32f to 0.7f)
            for ((dy, len) in lines) outline.addRoundRect(RectF(bx - r - s * 0.25f - s * len * 0.75f, cy + dy * s - s * 0.07f, bx - r * 0.3f, cy + dy * s + s * 0.07f), s, s, Path.Direction.CW)
            dieCut(c, outline, s)
            for ((dy, len) in lines) {
                p.shader = LinearGradient(bx - r - s * len * 0.95f, 0f, bx - r * 0.3f, 0f, 0x00FFB300, 0xFFFFB300.toInt(), Shader.TileMode.CLAMP)
                c.drawRoundRect(RectF(bx - r - s * 0.25f - s * len * 0.75f, cy + dy * s - s * 0.06f, bx - r * 0.3f, cy + dy * s + s * 0.06f), s, s, p)
            }
            p.shader = null
            ball(c, bx, cy, r, CardLook(0, 0, 0, 0, 0x33000000))
            gloss(c, bx - r * 0.35f, cy - r * 0.4f, r * 0.35f, r * 0.18f, -35f, 0x99)
        }
        "trophy" -> {
            val bowl = Path().apply { moveTo(-0.55f, -0.85f); lineTo(0.55f, -0.85f); cubicTo(0.55f, -0.1f, 0.3f, 0.15f, 0f, 0.15f); cubicTo(-0.3f, 0.15f, -0.55f, -0.1f, -0.55f, -0.85f); close() }
            val stem = Path().apply { moveTo(-0.08f, 0.1f); lineTo(0.08f, 0.1f); lineTo(0.14f, 0.5f); lineTo(-0.14f, 0.5f); close() }
            val base = Path().apply { addRoundRect(RectF(-0.3f, 0.46f, 0.3f, 0.6f), 0.04f, 0.04f, Path.Direction.CW); addRoundRect(RectF(-0.44f, 0.58f, 0.44f, 0.82f), 0.07f, 0.07f, Path.Direction.CW) }
            val handles = Path().apply { addOval(RectF(-0.88f, -0.78f, -0.38f, -0.2f), Path.Direction.CW); addOval(RectF(0.38f, -0.78f, 0.88f, -0.2f), Path.Direction.CW) }
            val all = Path(bowl).apply { op(stem, Path.Op.UNION); op(base, Path.Op.UNION); op(handles, Path.Op.UNION) }
            dieCut(c, unit(all, cx, cy, s), s)
            val g = gold(cx, cy - s, cy + s)
            // handles as thick rings
            p.style = Paint.Style.STROKE; p.strokeWidth = s * 0.1f; p.shader = g
            c.drawOval(RectF(cx - s * 0.84f, cy - s * 0.74f, cx - s * 0.42f, cy - s * 0.24f), p); c.drawOval(RectF(cx + s * 0.42f, cy - s * 0.74f, cx + s * 0.84f, cy - s * 0.24f), p)
            p.style = Paint.Style.FILL
            c.drawPath(unit(stem, cx, cy, s), p); c.drawPath(unit(base, cx, cy, s), p)
            p.shader = LinearGradient(cx - s * 0.55f, 0f, cx + s * 0.55f, 0f, intArrayOf(0xFFB57400.toInt(), 0xFFFFE27A.toInt(), 0xFFFFC22E.toInt(), 0xFFFFF4C2.toInt(), 0xFFC98500.toInt()),
                floatArrayOf(0f, 0.25f, 0.55f, 0.7f, 1f), Shader.TileMode.CLAMP)
            c.drawPath(unit(bowl, cx, cy, s), p); p.shader = null
            p.color = 0xFFFFF4C2.toInt(); c.drawOval(RectF(cx - s * 0.55f, cy - s * 0.92f, cx + s * 0.55f, cy - s * 0.78f), p)
            facetStar(c, cx, cy - s * 0.42f, s * 0.2f, 0xFFFFFFFF.toInt(), 0xFFFFE7A0.toInt())
            gloss(c, cx - s * 0.32f, cy - s * 0.45f, s * 0.26f, s * 0.08f, 75f, 0xA0)
        }
        "crown" -> {
            val tips = listOf(-0.9f to -0.52f, 0f to -0.8f, 0.9f to -0.52f)
            val all = Path(CROWN).apply { tips.forEach { (x, y) -> op(Path().apply { addCircle(x, y, 0.13f, Path.Direction.CW) }, Path.Op.UNION) } }
            dieCut(c, unit(all, cx, cy, s), s)
            p.shader = gold(cx, cy - s * 0.8f, cy + s * 0.5f); c.drawPath(unit(CROWN, cx, cy, s), p)
            tips.forEach { (x, y) -> c.drawCircle(cx + x * s, cy + y * s, s * 0.12f, p) }
            p.shader = null
            // the band and its jewels
            p.color = 0x33000000; c.drawRect(cx - s * 0.8f, cy + s * 0.22f, cx + s * 0.8f, cy + s * 0.27f, p)
            for ((x, col) in listOf(-0.45f to 0xFF2F6BFF.toInt(), 0f to 0xFFE5172F.toInt(), 0.45f to 0xFF14B866.toInt())) {
                p.shader = RadialGradient(cx + x * s - s * 0.03f, cy + s * 0.36f, s * 0.12f, intArrayOf(0xFFFFFFFF.toInt(), col, darker(col, 0.4f)), floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP)
                c.drawCircle(cx + x * s, cy + s * 0.38f, s * (if (x == 0f) 0.11f else 0.085f), p)
            }
            p.shader = null
            gloss(c, cx - s * 0.45f, cy - s * 0.12f, s * 0.22f, s * 0.07f, -60f, 0x90)
        }
        "star" -> {
            val outline = unit(star(5, 1f, 0.45f), cx, cy, s)
            dieCut(c, outline, s)
            facetStar(c, cx, cy, s, 0xFFFFE27A.toInt(), 0xFFE09A12.toInt())
            glintDot(c, cx + s * 0.9f, cy - s * 0.85f, s * 0.22f)
            glintDot(c, cx - s * 0.95f, cy + s * 0.55f, s * 0.16f)
        }
        "heart" -> {
            val h = unit(HEART, cx, cy, s)
            dieCut(c, h, s)
            val col = heartColour(tint)
            p.shader = RadialGradient(cx - s * 0.35f, cy - s * 0.4f, s * 1.5f, intArrayOf(brighten(col), col, darker(col, 0.45f)), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
            c.drawPath(h, p); p.shader = null
            gloss(c, cx - s * 0.48f, cy - s * 0.45f, s * 0.24f, s * 0.12f, -35f)
        }
        "heartbreak" -> {
            val left = Path().apply { moveTo(-1.2f, -1.2f); lineTo(0f, -1.2f); lineTo(0f, -0.55f); lineTo(-0.14f, -0.3f); lineTo(0.08f, -0.08f); lineTo(-0.12f, 0.16f); lineTo(0.06f, 0.42f); lineTo(0f, 1.2f); lineTo(-1.2f, 1.2f); close() }
            val right = Path().apply { moveTo(1.2f, -1.2f); lineTo(0f, -1.2f); lineTo(0f, -0.55f); lineTo(-0.14f, -0.3f); lineTo(0.08f, -0.08f); lineTo(-0.12f, 0.16f); lineTo(0.06f, 0.42f); lineTo(0f, 1.2f); lineTo(1.2f, 1.2f); close() }
            for ((half, side) in listOf(left to -1f, right to 1f)) {
                val piece = Path(HEART).apply { op(half, Path.Op.INTERSECT) }
                c.save(); c.translate(side * s * 0.1f, 0f); c.rotate(side * 9f, cx, cy + s * 0.9f)
                val pp = unit(piece, cx, cy, s)
                dieCut(c, pp, s)
                p.shader = RadialGradient(cx - s * 0.35f, cy - s * 0.4f, s * 1.5f, intArrayOf(0xFFFF6B7A.toInt(), 0xFFE5172F.toInt(), 0xFF7A0010.toInt()), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
                c.drawPath(pp, p); p.shader = null
                if (side < 0) gloss(c, cx - s * 0.48f, cy - s * 0.45f, s * 0.22f, s * 0.1f, -35f)
                c.restore()
            }
        }
        "goat" -> {
            val badge = Path().apply { addRoundRect(RectF(-1f, -0.4f, 1f, 0.45f), 0.22f, 0.22f, Path.Direction.CW) }
            val crown = Path(CROWN).apply { transform(Matrix().apply { setScale(0.34f, 0.34f); postTranslate(0f, -0.58f) }) }
            dieCut(c, unit(Path(badge).apply { op(crown, Path.Op.UNION) }, cx, cy, s), s)
            p.shader = LinearGradient(cx, cy - s * 0.4f, cx, cy + s * 0.45f, 0xFF2A2A30.toInt(), 0xFF050507.toInt(), Shader.TileMode.CLAMP)
            c.drawPath(unit(badge, cx, cy, s), p)
            p.style = Paint.Style.STROKE; p.strokeWidth = s * 0.06f; p.shader = gold(cx, cy - s * 0.4f, cy + s * 0.45f)
            c.drawRoundRect(RectF(cx - s * 0.92f, cy - s * 0.32f, cx + s * 0.92f, cy + s * 0.37f), s * 0.17f, s * 0.17f, p)
            p.style = Paint.Style.FILL; c.drawPath(unit(crown, cx, cy, s), p); p.shader = null
            val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = s * 0.5f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC); textAlign = Paint.Align.CENTER; letterSpacing = 0.08f }
            tp.shader = gold(cx, cy - s * 0.25f, cy + s * 0.2f)
            c.drawText("GOAT", cx, cy + s * 0.2f, tp)
        }
        "wow" -> {
            val bubble = Path().apply {
                addOval(RectF(-1f, -0.72f, 1f, 0.56f), Path.Direction.CW)
                op(Path().apply { moveTo(0.12f, 0.4f); lineTo(0.62f, 0.98f); lineTo(0.5f, 0.3f); close() }, Path.Op.UNION)
            }
            val b = unit(bubble, cx, cy, s)
            dieCut(c, b, s)
            p.color = 0xFFFFFFFF.toInt(); c.drawPath(b, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = s * 0.07f; p.strokeJoin = Paint.Join.ROUND; p.color = 0xFF111111.toInt(); c.drawPath(b, p)
            // action marks out of the top
            p.strokeCap = Paint.Cap.ROUND
            for ((x0, y0, x1, y1) in listOf(floatArrayOf(-0.75f, -0.82f, -0.95f, -1.05f), floatArrayOf(-0.25f, -0.92f, -0.3f, -1.18f), floatArrayOf(0.3f, -0.9f, 0.42f, -1.15f)))
                c.drawLine(cx + x0 * s, cy + y0 * s, cx + x1 * s, cy + y1 * s, p)
            p.style = Paint.Style.FILL
            c.save(); c.rotate(-6f, cx, cy)
            val word = if (en) "WOW!" else "وای!"
            val size = if (en) s * 0.52f else s * 0.66f
            outlined(c, word, cx, cy + size * 0.2f, size, 0xFFE5172F.toInt(), 0xFF111111.toInt(), tf)
            c.restore()
        }
        "bolt" -> {
            val b = unit(BOLT, cx, cy, s)
            p.shader = RadialGradient(cx, cy, s * 1.2f, intArrayOf(0x88FFE14D.toInt(), 0x00FFE14D), null, Shader.TileMode.CLAMP); c.drawCircle(cx, cy, s * 1.2f, p); p.shader = null
            dieCut(c, b, s)
            p.shader = LinearGradient(cx, cy - s, cx, cy + s, intArrayOf(0xFFFFF7A8.toInt(), 0xFFFFD000.toInt(), 0xFFFF8A00.toInt()), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
            c.drawPath(b, p); p.shader = null
            p.style = Paint.Style.STROKE; p.strokeWidth = s * 0.04f; p.strokeJoin = Paint.Join.ROUND; p.color = 0xFFB35C00.toInt(); c.drawPath(b, p); p.style = Paint.Style.FILL
            gloss(c, cx - s * 0.05f, cy - s * 0.45f, s * 0.2f, s * 0.06f, -55f, 0xC0)
        }
        "hundred" -> {
            val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = s * 0.95f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC); textAlign = Paint.Align.CENTER }
            val txt = Path(); tp.getTextPath("100", 0, 3, cx, cy + s * 0.18f, txt)
            val l1 = RectF(cx - s * 0.85f, cy + s * 0.34f, cx + s * 0.85f, cy + s * 0.46f); val l2 = RectF(cx - s * 0.75f, cy + s * 0.56f, cx + s * 0.75f, cy + s * 0.66f)
            val all = Path(txt).apply { addRoundRect(l1, s, s, Path.Direction.CW); addRoundRect(l2, s, s, Path.Direction.CW) }
            c.save(); c.rotate(-6f, cx, cy)
            dieCut(c, all, s * 0.8f)
            p.color = 0xFFE5172F.toInt(); c.drawPath(txt, p)
            c.drawRoundRect(l1, s, s, p); c.drawRoundRect(l2, s, s, p)
            c.restore()
        }
        "siuu" -> {
            val banner = Path().apply { addRoundRect(RectF(-1.05f, -0.36f, 1.05f, 0.36f), 0.36f, 0.36f, Path.Direction.CW) }
            c.save(); c.rotate(-10f, cx, cy)
            val bn = unit(banner, cx, cy, s)
            dieCut(c, bn, s)
            p.shader = LinearGradient(cx - s, cy, cx + s, cy, intArrayOf(0xFF0B3D91.toInt(), 0xFF1565FF.toInt(), 0xFF0B3D91.toInt()), null, Shader.TileMode.CLAMP); c.drawPath(bn, p); p.shader = null
            val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = s * 0.46f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC); textAlign = Paint.Align.CENTER }
            tp.shader = gold(cx, cy - s * 0.25f, cy + s * 0.2f)
            c.drawText("SIUUU!", cx, cy + s * 0.16f, tp)
            facetStar(c, cx - s * 0.98f, cy - s * 0.36f, s * 0.2f, 0xFFFFE27A.toInt(), 0xFFE09A12.toInt())
            facetStar(c, cx + s * 0.98f, cy + s * 0.34f, s * 0.15f, 0xFFFFE27A.toInt(), 0xFFE09A12.toInt())
            c.restore()
        }
    }
}

/** A small four-pointed sparkle. */
private fun glintDot(c: Canvas, x: Float, y: Float, r: Float) {
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
    c.drawPath(Path().apply {
        moveTo(x, y - r); quadTo(x, y, x + r, y); quadTo(x, y, x, y + r); quadTo(x, y, x - r, y); quadTo(x, y, x, y - r); close()
    }, p)
}

/* ---------- the analyst's icons ---------- */

/**
 * The icon that goes with a finding or a turning point, drawn in [ink] (white on a coloured disc) in a box [r] from its centre.
 * [icon] is the finding's own key (the emoji it is written with).
 */
internal fun drawGlyph(c: Canvas, icon: String, cx: Float, cy: Float, r: Float, ink: Int) {
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; strokeWidth = r * 0.16f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    fun x(v: Float) = cx + v * r
    fun y(v: Float) = cy + v * r
    fun line(vararg pts: Float) { val path = Path(); path.moveTo(x(pts[0]), y(pts[1])); var i = 2; while (i < pts.size) { path.lineTo(x(pts[i]), y(pts[i + 1])); i += 2 }; p.style = Paint.Style.STROKE; c.drawPath(path, p); p.style = Paint.Style.FILL }
    fun head(x0: Float, y0: Float, x1: Float, y1: Float) {
        // an arrow head at (x1, y1) pointing away from (x0, y0)
        val a = kotlin.math.atan2((y1 - y0).toDouble(), (x1 - x0).toDouble()); val l = 0.32f
        line(x1 + l * cos(a + 2.5).toFloat(), y1 + l * sin(a + 2.5).toFloat(), x1, y1, x1 + l * cos(a - 2.5).toFloat(), y1 + l * sin(a - 2.5).toFloat())
    }
    when (icon.replace("\uFE0F", "")) {
        "✅" -> line(-0.55f, 0.02f, -0.15f, 0.42f, 0.6f, -0.42f)
        "🍀" -> {
            for ((dx, dy) in listOf(-1f to -1f, 1f to -1f, -1f to 1f, 1f to 1f)) c.drawCircle(x(dx * 0.26f), y(dy * 0.26f - 0.08f), r * 0.3f, p)
            p.style = Paint.Style.STROKE; c.drawPath(Path().apply { moveTo(x(0f), y(0.1f)); quadTo(x(0.1f), y(0.6f), x(0.45f), y(0.85f)) }, p); p.style = Paint.Style.FILL
        }
        "🎯" -> {
            p.style = Paint.Style.STROKE; c.drawCircle(cx, cy, r * 0.82f, p); c.drawCircle(cx, cy, r * 0.46f, p); p.style = Paint.Style.FILL
            c.drawCircle(cx, cy, r * 0.16f, p)
        }
        "⚖" -> {
            line(0f, -0.8f, 0f, 0.72f); line(-0.42f, 0.74f, 0.42f, 0.74f); line(-0.78f, -0.55f, 0.78f, -0.55f)
            for (sx in listOf(-0.68f, 0.68f)) {
                line(sx, -0.55f, sx - 0.24f, 0.05f); line(sx, -0.55f, sx + 0.24f, 0.05f)
                c.drawArc(RectF(x(sx - 0.32f), y(-0.1f), x(sx + 0.32f), y(0.3f)), 0f, 180f, true, p)
            }
        }
        "😤" -> {
            p.style = Paint.Style.STROKE; p.strokeWidth = r * 0.24f; c.drawLine(cx, y(-0.68f), cx, y(0.2f), p); p.style = Paint.Style.FILL
            c.drawCircle(cx, y(0.6f), r * 0.15f, p)
        }
        "😩", "🙈" -> {
            // over the bar
            line(-0.78f, 0.75f, -0.78f, -0.3f, 0.78f, -0.3f, 0.78f, 0.75f)
            c.drawCircle(x(0.42f), y(-0.72f), r * 0.2f, p)
            p.strokeWidth = r * 0.1f; line(-0.3f, 0.35f, 0.18f, -0.5f)
        }
        "🧱" -> {
            val bw = 0.52f; val bh = 0.3f; val gap = 0.07f
            for (row in 0 until 3) {
                val top = -0.62f + row * (bh + gap); val off = if (row % 2 == 0) 0f else bw / 2
                var left = -0.86f - off
                while (left < 0.86f) {
                    val l = maxOf(left, -0.86f); val rr = minOf(left + bw, 0.86f)
                    if (rr - l > 0.1f) c.drawRoundRect(RectF(x(l), y(top), x(rr), y(top + bh)), r * 0.05f, r * 0.05f, p)
                    left += bw + gap
                }
            }
        }
        "🧤" -> {
            c.drawRoundRect(RectF(x(-0.42f), y(-0.08f), x(0.46f), y(0.66f)), r * 0.22f, r * 0.22f, p)
            for (fx in listOf(-0.33f, -0.11f, 0.11f, 0.33f)) c.drawRoundRect(RectF(x(fx - 0.1f), y(if (fx == -0.33f) -0.55f else -0.78f), x(fx + 0.1f), y(0.1f)), r * 0.1f, r * 0.1f, p)
            c.save(); c.rotate(-40f, x(-0.5f), y(0.25f)); c.drawRoundRect(RectF(x(-0.62f), y(-0.12f), x(-0.4f), y(0.42f)), r * 0.11f, r * 0.11f, p); c.restore()
            p.color = ca(ink, 0x99); c.drawRect(x(-0.42f), y(0.7f), x(0.46f), y(0.84f), p)
        }
        "😬" -> {
            line(-0.82f, 0.72f, -0.82f, -0.42f, 0.82f, -0.42f, 0.82f, 0.72f)
            p.strokeWidth = r * 0.05f; p.color = ca(ink, 0x88)
            var nx = -0.62f; while (nx < 0.7f) { line(nx, -0.42f, nx, 0.72f); nx += 0.24f }
            var ny = -0.2f; while (ny < 0.72f) { line(-0.82f, ny, 0.82f, ny); ny += 0.22f }
            p.color = ink; c.drawCircle(x(0.18f), y(0.42f), r * 0.24f, p)
        }
        "🟥" -> {
            c.save(); c.rotate(12f, cx, cy)
            p.color = 0xFFFFFFFF.toInt(); c.drawRoundRect(RectF(x(-0.46f), y(-0.7f), x(0.46f), y(0.7f)), r * 0.12f, r * 0.12f, p)
            p.color = 0xFFE5172F.toInt(); c.drawRoundRect(RectF(x(-0.36f), y(-0.6f), x(0.36f), y(0.6f)), r * 0.08f, r * 0.08f, p)
            c.restore()
        }
        "🔄" -> {
            p.color = 0xFF39D98A.toInt(); line(-0.65f, -0.3f, 0.6f, -0.3f); head(-0.65f, -0.3f, 0.6f, -0.3f)
            p.color = 0xFFFF5A5F.toInt(); line(0.65f, 0.32f, -0.6f, 0.32f); head(0.65f, 0.32f, -0.6f, 0.32f)
        }
        "⚽" -> ball(c, cx, cy, r * 0.78f, CardLook(0, 0, 0, 0, 0x33000000))
        "💪" -> { line(-0.78f, 0.6f, -0.28f, 0.08f, 0.06f, 0.34f, 0.66f, -0.44f); head(0.06f, 0.34f, 0.66f, -0.44f) }
        "🚩" -> {
            line(-0.42f, 0.82f, -0.42f, -0.8f)
            p.color = 0xFFFFD60A.toInt(); c.drawPath(Path().apply { moveTo(x(-0.38f), y(-0.8f)); lineTo(x(0.62f), y(-0.5f)); lineTo(x(-0.38f), y(-0.2f)); close() }, p)
            p.color = ink; p.style = Paint.Style.STROKE; p.strokeWidth = r * 0.1f; c.drawArc(RectF(x(-0.8f), y(0.44f), x(-0.04f), y(1.2f)), -90f, 90f, false, p); p.style = Paint.Style.FILL
        }
        "🔁" -> {
            p.style = Paint.Style.STROKE; c.drawCircle(cx, cy, r * 0.78f, p); p.style = Paint.Style.FILL
            c.drawArc(RectF(x(-0.56f), y(-0.56f), x(0.56f), y(0.56f)), -90f, 125f, true, p)
        }
        "🛡" -> {
            val sh = unit(SHIELD, cx, cy, r)
            p.color = ca(ink, 0x40); c.drawPath(sh, p)
            p.color = ink; p.style = Paint.Style.STROKE; c.drawPath(sh, p); p.style = Paint.Style.FILL
            line(-0.3f, 0.02f, -0.05f, 0.28f, 0.34f, -0.2f)
        }
        "⭐" -> facetStar(c, cx, cy, r * 0.9f, 0xFFFFE27A.toInt(), 0xFFE09A12.toInt())
        "🔥" -> {
            p.shader = LinearGradient(cx, cy + r, cx, cy - r, intArrayOf(0xFFFFF3A0.toInt(), 0xFFFF8A00.toInt(), 0xFFFF2D2D.toInt()), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
            c.drawPath(unit(FLAME, cx, cy, r * 0.88f), p); p.shader = null
        }
        "📡" -> {
            c.drawCircle(x(-0.55f), y(0.55f), r * 0.16f, p)
            p.style = Paint.Style.STROKE
            for (k in 1..3) { val rr = 0.42f * k; c.drawArc(RectF(x(-0.55f - rr), y(0.55f - rr), x(-0.55f + rr), y(0.55f + rr)), -90f, 90f, false, p) }
            p.style = Paint.Style.FILL
        }
        "🥶" -> {
            // a snowflake
            for (k in 0 until 3) {
                val a = PI / 3 * k
                val dx = cos(a).toFloat(); val dy = sin(a).toFloat()
                line(-dx * 0.85f, -dy * 0.85f, dx * 0.85f, dy * 0.85f)
                for (sgn in listOf(-1f, 1f)) {
                    val bx = sgn * dx * 0.5f; val by = sgn * dy * 0.5f
                    val a1 = a + (if (sgn > 0) 0.0 else PI)
                    line(bx + (0.28f * cos(a1 + 0.8)).toFloat(), by + (0.28f * sin(a1 + 0.8)).toFloat(), bx, by, bx + (0.28f * cos(a1 - 0.8)).toFloat(), by + (0.28f * sin(a1 - 0.8)).toFloat())
                }
            }
        }
        "🕳" -> drawGlyph(c, "😬", cx, cy, r, ink)
        "🥵" -> drawGlyph(c, "🔥", cx, cy, r, ink)
        "↩" -> {
            p.style = Paint.Style.STROKE
            c.drawArc(RectF(x(-0.55f), y(-0.55f), x(0.65f), y(0.65f)), -90f, 250f, false, p)
            p.style = Paint.Style.FILL
            head(0.6f, -0.62f, 0.05f, -0.55f)
        }
        "📉" -> {
            line(-0.8f, -0.8f, -0.8f, 0.8f, 0.8f, 0.8f)
            line(-0.55f, -0.45f, -0.15f, -0.05f, 0.1f, -0.25f, 0.62f, 0.42f); head(0.1f, -0.25f, 0.62f, 0.42f)
        }
        "⏰", "⏱" -> {
            p.style = Paint.Style.STROKE; c.drawCircle(cx, y(0.12f), r * 0.68f, p); p.style = Paint.Style.FILL
            line(0f, 0.12f, 0f, -0.3f); line(0f, 0.12f, 0.3f, 0.3f)
            c.drawRoundRect(RectF(x(-0.18f), y(-0.92f), x(0.18f), y(-0.72f)), r * 0.06f, r * 0.06f, p)
        }
        "🚀" -> {
            c.drawPath(Path().apply { moveTo(x(0f), y(-0.9f)); quadTo(x(0.42f), y(-0.4f), x(0.28f), y(0.38f)); lineTo(x(-0.28f), y(0.38f)); quadTo(x(-0.42f), y(-0.4f), x(0f), y(-0.9f)); close() }, p)
            c.drawPath(Path().apply { moveTo(x(-0.28f), y(0.05f)); lineTo(x(-0.55f), y(0.45f)); lineTo(x(-0.25f), y(0.38f)); close() }, p)
            c.drawPath(Path().apply { moveTo(x(0.28f), y(0.05f)); lineTo(x(0.55f), y(0.45f)); lineTo(x(0.25f), y(0.38f)); close() }, p)
            p.color = 0xFFFF8A00.toInt(); c.drawPath(Path().apply { moveTo(x(-0.18f), y(0.42f)); lineTo(x(0f), y(0.92f)); lineTo(x(0.18f), y(0.42f)); close() }, p)
            p.color = 0xFF1A2128.toInt(); c.drawCircle(cx, y(-0.28f), r * 0.13f, p)
        }
        "🗣" -> {
            c.drawPath(Path().apply { moveTo(x(-0.78f), y(-0.18f)); lineTo(x(-0.2f), y(-0.18f)); lineTo(x(0.3f), y(-0.6f)); lineTo(x(0.3f), y(0.6f)); lineTo(x(-0.2f), y(0.18f)); lineTo(x(-0.78f), y(0.18f)); close() }, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = r * 0.11f
            c.drawArc(RectF(x(0.1f), y(-0.45f), x(0.75f), y(0.45f)), -50f, 100f, false, p)
            c.drawArc(RectF(x(0.1f), y(-0.75f), x(1.0f), y(0.75f)), -45f, 90f, false, p)
            p.style = Paint.Style.FILL
        }
        "⚡" -> {
            p.shader = LinearGradient(cx, cy - r, cx, cy + r, intArrayOf(0xFFFFF7A8.toInt(), 0xFFFFD000.toInt(), 0xFFFF8A00.toInt()), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
            c.drawPath(unit(BOLT, cx, cy, r * 0.9f), p); p.shader = null
        }
        else -> c.drawCircle(cx, cy, r * 0.3f, p)
    }
}

/** A finding's or turning point's icon on a disc of [disc], with a soft glow; for the stories. */
internal fun glyphDisc(c: Canvas, icon: String, cx: Float, cy: Float, r: Float, disc: Int) {
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.shader = RadialGradient(cx, cy, r * 1.7f, intArrayOf(ca(disc, 0x77), ca(disc, 0)), null, Shader.TileMode.CLAMP); c.drawCircle(cx, cy, r * 1.7f, p)
    p.shader = LinearGradient(cx, cy - r, cx, cy + r, brighten(disc), darker(disc, 0.35f), Shader.TileMode.CLAMP); c.drawCircle(cx, cy, r, p); p.shader = null
    p.style = Paint.Style.STROKE; p.strokeWidth = r * 0.06f; p.color = 0x55FFFFFF; c.drawCircle(cx, cy, r * 0.97f, p)
    drawGlyph(c, icon, cx, cy, r * 0.56f, 0xFFFFFFFF.toInt())
}
