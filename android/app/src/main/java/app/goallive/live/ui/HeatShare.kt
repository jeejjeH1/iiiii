package app.goallive.live.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import app.goallive.live.L
import app.goallive.live.Lang
import app.goallive.live.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/**
 * A player's heatmap as a story picture (1080×1920): his face, name, club and rating over the team's colour, the part of the
 * match it covers, the heat on an upright pitch with the share of each of the nine zones, and the app's name at the foot.
 */
suspend fun shareHeatmap(ctx: Context, playerId: Int, name: String, team: String, rating: Double?, pts: List<Pair<Float, Float>>, span: String, teamColor: Color) {
    if (pts.size < 3) return
    val face = image(ctx, playerFace(playerId), 300)
    val uri = runCatching {
        withContext(Dispatchers.Default) {
            val W = 1080; val H = 1920
            val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            val black = if (Lang.en) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else ResourcesCompat.getFont(ctx, R.font.vazir_black) ?: Typeface.DEFAULT_BOLD
            val bold = if (Lang.en) Typeface.DEFAULT_BOLD else ResourcesCompat.getFont(ctx, R.font.vazir_bold) ?: Typeface.DEFAULT_BOLD
            val tc = teamColor.toArgb()
            // the team's colour fading into the night
            p.shader = LinearGradient(0f, 0f, 0f, H.toFloat(), intArrayOf(tc, 0xFF0B0F14.toInt(), 0xFF0B0F14.toInt()), floatArrayOf(0f, 0.42f, 1f), Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), p); p.shader = null
            p.color = 0x55000000; c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), p)
            val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; color = 0xFFFFFFFF.toInt() }
            // face
            val fy = 210f; val fr = 120f
            p.color = 0x33FFFFFF; c.drawCircle(W / 2f, fy, fr + 10f, p)
            p.color = 0xFF1B2330.toInt(); c.drawCircle(W / 2f, fy, fr, p)
            face?.let {
                c.save(); val clip = android.graphics.Path().apply { addCircle(W / 2f, fy, fr, android.graphics.Path.Direction.CW) }; c.clipPath(clip)
                c.drawBitmap(it, null, RectF(W / 2f - fr, fy - fr, W / 2f + fr, fy + fr), Paint(Paint.FILTER_BITMAP_FLAG)); c.restore()
            }
            rating?.takeIf { it > 0 }?.let { r ->
                p.color = ratingColor(r); c.drawRoundRect(RectF(W / 2f + 60f, fy - 150f, W / 2f + 190f, fy - 82f), 34f, 34f, p)
                tp.typeface = black; tp.textSize = 46f; c.drawText(fa(dec(r, 1)), W / 2f + 125f, fy - 100f, tp)
            }
            tp.typeface = black; tp.textSize = 76f; c.drawText(personName(name), W / 2f, 430f, tp)
            tp.typeface = bold; tp.textSize = 40f; tp.color = 0xCCFFFFFF.toInt()
            c.drawText(listOf(L("نقشه حرارتی"), team, span).filter { it.isNotEmpty() }.joinToString("  ·  "), W / 2f, 492f, tp)
            // the pitch, stood up, attacking upwards
            val pw = 740f; val ph = pw * 105f / 68f; val px = (W - pw) / 2f; val py = 560f
            val area = RectF(px, py, px + pw, py + ph)
            c.save(); c.clipPath(android.graphics.Path().apply { addRoundRect(area, 36f, 36f, android.graphics.Path.Direction.CW) })
            for (k in 0 until 12) { p.color = if (k % 2 == 0) 0xFF123A26.toInt() else 0xFF103421.toInt(); c.drawRect(px, py + ph * k / 12f, px + pw, py + ph * (k + 1) / 12f + 1, p) }
            c.drawBitmap(heatImage(pts, true).asAndroidBitmap(), null, area, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
            c.restore()
            val sx = pw / 68f; val sy = ph / 105f
            fun X(y: Float) = px + y * sx
            fun Y(x: Float) = py + (105f - x) * sy
            p.style = Paint.Style.STROKE; p.strokeWidth = 4f; p.color = 0x99FFFFFF.toInt()
            c.drawRoundRect(area, 36f, 36f, p)
            c.drawLine(px, Y(52.5f), px + pw, Y(52.5f), p)
            c.drawCircle(X(34f), Y(52.5f), 9.15f * sx, p)
            for (end in listOf(0f, 105f)) {
                val d = if (end == 0f) 1f else -1f
                c.drawRect(minOf(X(13.84f), X(54.16f)), minOf(Y(end), Y(end + d * 16.5f)), maxOf(X(13.84f), X(54.16f)), maxOf(Y(end), Y(end + d * 16.5f)), p)
                c.drawRect(minOf(X(24.84f), X(43.16f)), minOf(Y(end), Y(end + d * 5.5f)), maxOf(X(24.84f), X(43.16f)), maxOf(Y(end), Y(end + d * 5.5f)), p)
            }
            // the nine zones and their shares
            p.pathEffect = android.graphics.DashPathEffect(floatArrayOf(18f, 14f), 0f); p.strokeWidth = 3f; p.color = 0xAAFFFFFF.toInt()
            for (f in listOf(1f / 3, 2f / 3)) { c.drawLine(px + pw * f, py, px + pw * f, py + ph, p); c.drawLine(px, py + ph * f, px + pw, py + ph * f, p) }
            p.pathEffect = null; p.style = Paint.Style.FILL
            val n = pts.size.toFloat()
            val cells = IntArray(9)
            for ((x, y) in pts) cells[(if (x < 35f) 0 else if (x <= 70f) 1 else 2) * 3 + (if (y < 22.7f) 0 else if (y <= 45.3f) 1 else 2)]++
            val top = cells.maxOrNull() ?: 0
            tp.typeface = black; tp.textSize = 52f
            for (third in 0..2) for (lane in 0..2) {
                val v = cells[third * 3 + lane]
                val cx = px + (lane + 0.5f) / 3f * pw; val cy = py + (2 - third + 0.5f) / 3f * ph
                if (v == top && v > 0) { p.color = 0x77000000; c.drawRoundRect(RectF(cx - 90f, cy - 50f, cx + 90f, cy + 30f), 40f, 40f, p) }
                tp.color = if (v == top) 0xFFFFE066.toInt() else 0xFFFFFFFF.toInt()
                tp.setShadowLayer(10f, 0f, 2f, 0xCC000000.toInt())
                c.drawText(fa((v / n * 100).roundToInt()) + "٪", cx, cy + 8f, tp)
            }
            tp.clearShadowLayer()
            // foot
            BitmapFactory.decodeResource(ctx.resources, R.drawable.logo)?.let { logo ->
                val ly = py + ph + 50f
                p.color = 0xFFFFFFFF.toInt(); c.drawRoundRect(RectF(W / 2f - 250f, ly, W / 2f - 170f, ly + 80f), 22f, 22f, p)
                c.drawBitmap(logo, null, RectF(W / 2f - 244f, ly + 6f, W / 2f - 176f, ly + 74f), Paint(Paint.FILTER_BITMAP_FLAG))
                tp.typeface = black; tp.textSize = 46f; tp.color = 0xFFFFFFFF.toInt(); tp.textAlign = Paint.Align.LEFT
                c.drawText(L("گل‌لایو · از کافه‌بازار"), W / 2f - 150f, ly + 56f, tp)
            }
            val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
            val f = File(dir, "goallive-heatmap.png").also { out -> out.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } }
            FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
        }
    }.getOrNull() ?: return
    val send = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        .putExtra(Intent.EXTRA_TEXT, L("نقشه حرارتی {0} در گل‌لایو", personName(name)) + "\n" + BAZAAR_URL)
    ctx.startActivity(Intent.createChooser(send, L("اشتراک‌گذاری")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
