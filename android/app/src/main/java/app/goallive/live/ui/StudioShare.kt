package app.goallive.live.ui

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import app.goallive.live.L
import app.goallive.live.R
import org.json.JSONObject
import java.io.File

/* ---------- a move as a video, pictures of its steps, and the tactic as a file ---------- */

/**
 * The move laid out in time, as it plays in the studio: a moment on the first step, each step at its own length (slower into a
 * shot), a pause on each event while it shows, and a moment on the last step.
 */
class StudioPlan(private val board: StudioBoard, speed: Float = 1f) {
    private class Piece(val start: Float, val len: Float, val k: Int, val hold: Boolean, val slow: Boolean)
    private val pieces = ArrayList<Piece>()
    val seconds: Float

    init {
        var t = 0.9f
        pieces += Piece(0f, t, 0, true, false)
        for (k in 0 until board.frames.size - 1) {
            val next = board.frames[k + 1]
            val slow = next.ev?.kind == "shot"
            val d = next.dur.coerceAtLeast(0.2f) / speed
            val len = if (slow) d * (0.55f + 0.45f / 0.38f) else d
            pieces += Piece(t, len, k, false, slow); t += len
            next.ev?.let { e ->
                val h = when { e.kind == "shot" && e.outcome == "goal" -> 2.1f; e.kind == "offside" -> 1.2f; e.kind == "shot" -> 1.1f; else -> 0.7f }
                pieces += Piece(t, h, k + 1, true, false); t += h
            }
        }
        val last = (board.frames.size - 1).coerceAtLeast(0)
        pieces += Piece(t, 1.4f, last, true, false); t += 1.4f
        seconds = t
    }

    /** At [s] seconds: the time in steps, and the event being shown (its step and how long since). */
    fun at(s: Float): Pair<Float, Pair<Int, Float>?> {
        val p = pieces.lastOrNull { s >= it.start } ?: pieces.first()
        val q = (s - p.start).coerceIn(0f, p.len)
        val t = if (p.hold) p.k.toFloat() else {
            val f = if (!p.slow) q / p.len else {
                val d = p.len / (0.55f + 0.45f / 0.38f); val a = 0.55f * d
                if (q < a) 0.55f * q / a else 0.55f + 0.45f * ((q - a) / (p.len - a)).coerceIn(0f, 1f)
            }
            p.k + f.coerceIn(0f, 1f)
        }
        // the latest event that has started and is still showing
        val ev = pieces.filter { it.hold && it.start <= s && board.frames.getOrNull(it.k)?.ev != null && it.start > 0f }.lastOrNull()
        val fx = ev?.let { e -> (s - e.start).takeIf { it < 2.6f }?.let { e.k to it } }
        return t to fx
    }

}

/** What a picture or a video frame needs: the fonts, the logo and the players' photos. */
class StudioArt(val black: Typeface, val bold: Typeface, val logo: Bitmap?, val faces: Map<Int, ImageBitmap>)

suspend fun studioArt(ctx: Context, board: StudioBoard): StudioArt {
    val en = app.goallive.live.Lang.en
    val black = if (en) Typeface.DEFAULT_BOLD else ResourcesCompat.getFont(ctx, R.font.vazir_black) ?: Typeface.DEFAULT_BOLD
    val bold = if (en) Typeface.DEFAULT else ResourcesCompat.getFont(ctx, R.font.vazir_bold) ?: Typeface.DEFAULT
    val faces = HashMap<Int, ImageBitmap>()
    if (StudioPrefs.style == "photo") for (pid in board.players.map { it.pid }.filter { it > 0 }.distinct()) image(ctx, playerFace(pid), 180)?.let { faces[pid] = it.asImageBitmap() }
    return StudioArt(black, bold, BitmapFactory.decodeResource(ctx.resources, R.drawable.logo), faces)
}

/**
 * One story-size picture (1080×1920) of the board at time [t] (in steps) with event [fx]: the title and the sides above,
 * the pitch, the step's caption, the app's name at the bottom.
 */
fun drawStudioStory(c: android.graphics.Canvas, board: StudioBoard, art: StudioArt, t: Float, fx: Pair<Int, Float>?) {
    val W = 1080f; val H = 1920f
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    // night, lit from both ends in the sides' colours
    p.shader = LinearGradient(0f, 0f, 0f, H, intArrayOf(0xFF0B1016.toInt(), 0xFF05070A.toInt()), null, Shader.TileMode.CLAMP); c.drawRect(0f, 0f, W, H, p)
    p.shader = RadialGradient(W / 2, H * 0.95f, W, (board.home.color and 0x00FFFFFF) or 0x55000000, board.home.color and 0x00FFFFFF, Shader.TileMode.CLAMP); c.drawRect(0f, 0f, W, H, p)
    p.shader = RadialGradient(W / 2, H * 0.05f, W, (board.away.color and 0x00FFFFFF) or 0x44000000, board.away.color and 0x00FFFFFF, Shader.TileMode.CLAMP); c.drawRect(0f, 0f, W, H, p)
    p.shader = null
    // the header
    fun centre(s: String, y: Float, size: Float, color: Int, tf: Typeface) {
        val tp = android.text.TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; typeface = tf; this.color = color; textAlign = Paint.Align.CENTER }
        c.drawText(android.text.TextUtils.ellipsize(s, tp, W - 120, android.text.TextUtils.TruncateAt.END).toString(), W / 2, y, tp)
    }
    centre(L("استودیو تاکتیک گل‌لایو"), 92f, 30f, 0xFFF5C542.toInt(), art.black)
    centre(board.title.ifBlank { L("تاکتیک من") }, 168f, 60f, 0xFFFFFFFF.toInt(), art.black)
    centre(board.home.name + "  " + L("مقابل") + "  " + board.away.name, 230f, 34f, 0xCCFFFFFF.toInt(), art.bold)
    // the pitch
    val (v0, v1) = rangeOf(StudioPrefs.range)
    val aspect = pitchAspect(StudioPrefs.range, StudioPrefs.tilt)
    var pw = 940f; var ph = pw * aspect
    if (ph > 1400f) { ph = 1400f; pw = ph / aspect }
    val left = (W - pw) / 2; val top = 280f + (1420f - ph) / 2
    val ed = StudioEditor().apply { playT = t; this.fx = fx }
    c.save(); c.translate(left, top)
    CanvasDrawScope().draw(Density(2.6f), LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(c), Size(pw, ph)) {
        drawBoardScene(board, ed, Proj(pw, ph, v0, v1, StudioPrefs.tilt), art.faces, art.black)
    }
    c.restore()
    // the caption of the step being reached
    val note = board.frames.getOrNull(kotlin.math.ceil(t).toInt().coerceIn(0, (board.frames.size - 1).coerceAtLeast(0)))?.note.orEmpty()
    if (note.isNotBlank()) {
        val tp = android.text.TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 36f; typeface = art.black; color = 0xFFFFFFFF.toInt(); textAlign = Paint.Align.CENTER }
        val s = android.text.TextUtils.ellipsize(note, tp, W - 160, android.text.TextUtils.TruncateAt.END).toString()
        val w = tp.measureText(s) + 60
        p.color = 0xD90B0F14.toInt(); c.drawRoundRect(RectF(W / 2 - w / 2, 1712f, W / 2 + w / 2, 1784f), 36f, 36f, p)
        c.drawText(s, W / 2, 1761f, tp)
    }
    // the app's name
    art.logo?.let { lg -> p.color = 0xFFFFFFFF.toInt(); c.drawCircle(W / 2 + 96, 1852f, 28f, p); c.drawBitmap(lg, null, RectF(W / 2 + 72, 1828f, W / 2 + 120, 1876f), null) }
    centre(L("گل‌لایو"), 1866f, 38f, 0xFFFFFFFF.toInt(), art.black)
}

private fun shareDir(ctx: Context) = File(ctx.cacheDir, "share").apply { mkdirs() }

/** Pictures of the steps: the current one, or every one. */
fun studioPictures(ctx: Context, board: StudioBoard, art: StudioArt, all: Boolean): List<File> = runCatching {
    board.syncOut()
    val steps = if (all) board.frames.indices.toList() else listOf(board.cur)
    steps.map { k ->
        val bmp = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)
        drawStudioStory(android.graphics.Canvas(bmp), board, art, k.toFloat(), null)
        File(shareDir(ctx), "goallive-tactic-${k + 1}.png").also { out -> out.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }; bmp.recycle() }
    }
}.getOrDefault(emptyList())

/** Hands [files] to the system's share sheet with a line about the app. */
fun shareStudio(ctx: Context, files: List<File>, mime: String) {
    if (files.isEmpty()) return
    val uris = ArrayList(files.map { FileProvider.getUriForFile(ctx, ctx.packageName + ".files", it) })
    val text = L("تاکتیک من در گل‌لایو") + "\n" + BAZAAR_URL
    val send = if (uris.size == 1) Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
        else Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
    send.setType(mime).putExtra(Intent.EXTRA_TEXT, text).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    ctx.startActivity(Intent.createChooser(send, L("اشتراک‌گذاری")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** Keeps [files] in the phone's gallery (Pictures or Movies / GoalLive). */
fun saveStudio(ctx: Context, files: List<File>, mime: String) {
    val video = mime.startsWith("video")
    val ok = files.isNotEmpty() && files.all { f ->
        runCatching {
            if (Build.VERSION.SDK_INT < 29) error("old")
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, f.nameWithoutExtension + "-" + System.currentTimeMillis() / 1000 + "." + f.extension)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, if (video) "Movies/GoalLive" else "Pictures/GoalLive")
            }
            val where = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val uri = ctx.contentResolver.insert(where, values) ?: error("no uri")
            ctx.contentResolver.openOutputStream(uri)?.use { out -> f.inputStream().use { it.copyTo(out) } } ?: error("no stream")
        }.isSuccess
    }
    Toast.makeText(ctx, if (ok) L("در گالری ذخیره شد") else L("ذخیره نشد؛ از «بقیه» استفاده کنید"), Toast.LENGTH_SHORT).show()
}
