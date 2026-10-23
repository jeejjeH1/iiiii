package app.goallive.live.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import app.goallive.live.L
import app.goallive.live.Lang
import app.goallive.live.R
import app.goallive.live.data.FaNames
import app.goallive.live.data.Predictions
import app.goallive.live.data.b
import app.goallive.live.data.i
import app.goallive.live.data.o
import app.goallive.live.data.s
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val W = 1080
private const val H = 1920
const val BAZAAR_URL = "https://cafebazaar.ir/app/app.goallive.live"

/**
 * Shares a match as a story-size picture (1080×1920): both crests, the score, the scorers, the user's prediction and the app's name,
 * so whoever sees it knows where it came from. Falls back to plain text when the picture can't be made.
 */
suspend fun shareMatchCard(ctx: Context, m: MatchData) {
    val text = "${teamName(m.homeId, m.homeName)} ${if (m.started) "${m.home.i("score") ?: 0}-${m.away.i("score") ?: 0}" else timeOf(m.time)} " +
        "${teamName(m.awayId, m.awayName)}\n" + L("نتایج زنده فوتبال در گل‌لایو") + "\n$BAZAAR_URL"
    val send = Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT, text)
    runCatching {
        val png = withContext(Dispatchers.IO) {
            val bmp = drawCard(ctx, m)
            val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
            File(dir, "goallive-${m.matchId}.png").also { f -> f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        }
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", png)
        send.setType("image/png").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }.onFailure { send.setType("text/plain") }
    ctx.startActivity(Intent.createChooser(send, L("اشتراک‌گذاری")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

// a crest that doesn't arrive in a few seconds is left out rather than holding up the picture
private suspend fun crest(ctx: Context, id: Int): Bitmap? = runCatching {
    kotlinx.coroutines.withTimeoutOrNull(6_000) { ctx.imageLoader.execute(ImageRequest.Builder(ctx).data(teamLogo(id, true)).allowHardware(false).build()) }?.drawable?.toBitmap(300, 300)
}.getOrNull()

internal suspend fun drawCard(ctx: Context, m: MatchData): Bitmap {
    val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val en = Lang.en
    val black = if (en) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else ResourcesCompat.getFont(ctx, R.font.vazir_black) ?: Typeface.DEFAULT_BOLD
    val bold = if (en) Typeface.DEFAULT_BOLD else ResourcesCompat.getFont(ctx, R.font.vazir_bold) ?: Typeface.DEFAULT_BOLD
    val hc = m.homeColor.toArgb(); val ac = m.awayColor.toArgb()
    // home on the reading start side: right in Persian, left in English
    val homeX = if (en) 270f else 810f
    val awayX = W - homeX

    // the two club colors split on a slant, darkened towards the bottom, with a soft light behind the score
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.color = 0xFF0B0F14.toInt(); c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), p)
    fun half(color: Int, homeSide: Boolean) {
        val right = homeSide != en
        val path = Path().apply {
            if (right) { moveTo(W * 0.62f, 0f); lineTo(W.toFloat(), 0f); lineTo(W.toFloat(), H.toFloat()); lineTo(W * 0.38f, H.toFloat()) }
            else { moveTo(0f, 0f); lineTo(W * 0.62f, 0f); lineTo(W * 0.38f, H.toFloat()); lineTo(0f, H.toFloat()) }
            close()
        }
        p.shader = LinearGradient(0f, 0f, 0f, H.toFloat(), intArrayOf(color, color, 0xFF0B0F14.toInt()), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        p.alpha = 235; c.drawPath(path, p); p.shader = null; p.alpha = 255
    }
    half(hc, true); half(ac, false)
    p.shader = RadialGradient(W / 2f, 720f, 520f, intArrayOf(0x55FFFFFF, 0x00FFFFFF), null, Shader.TileMode.CLAMP)
    c.drawCircle(W / 2f, 720f, 520f, p); p.shader = null
    p.shader = LinearGradient(0f, H * 0.55f, 0f, H.toFloat(), 0x00000000, 0xE6000000.toInt(), Shader.TileMode.CLAMP)
    c.drawRect(0f, H * 0.55f, W.toFloat(), H.toFloat(), p); p.shader = null

    // the app's logo and name
    BitmapFactory.decodeResource(ctx.resources, R.drawable.logo)?.let { logo ->
        p.color = 0xFFFFFFFF.toInt(); c.drawCircle(W / 2f, 170f, 74f, p)
        c.drawBitmap(logo, null, RectF(W / 2f - 64f, 106f, W / 2f + 64f, 234f), p)
    }
    text(c, L("گل‌لایو"), W / 2f, 262f, 64f, 0xFFFFFFFF.toInt(), black, 900)
    val league = (m.general.s("leagueName") ?: "").let { if (en) it else FaNames.of(it) }
    text(c, league + "  ·  " + persianDate(m.time, "EEEE d MMMM"), W / 2f, 352f, 36f, 0xDDFFFFFF.toInt(), bold, 960)

    // crests, names, score
    crest(ctx, m.homeId)?.let { c.drawBitmap(it, null, RectF(homeX - 150f, 470f, homeX + 150f, 770f), null) }
    crest(ctx, m.awayId)?.let { c.drawBitmap(it, null, RectF(awayX - 150f, 470f, awayX + 150f, 770f), null) }
    text(c, teamName(m.homeId, m.homeName), homeX, 800f, 56f, 0xFFFFFFFF.toInt(), black, 440, 2)
    text(c, teamName(m.awayId, m.awayName), awayX, 800f, 56f, 0xFFFFFFFF.toInt(), black, 440, 2)
    if (m.started) {
        text(c, fa(m.home.i("score") ?: 0), homeX, 1010f, 220f, 0xFFFFFFFF.toInt(), black, 400)
        text(c, fa(m.away.i("score") ?: 0), awayX, 1010f, 220f, 0xFFFFFFFF.toInt(), black, 400)
        text(c, "–", W / 2f, 1030f, 150f, 0x99FFFFFF.toInt(), black, 200)
    } else text(c, fa(timeOf(m.time)), W / 2f, 1030f, 170f, 0xFFFFFFFF.toInt(), black, 900)
    val pens = m.status.o("reason").let { it?.optJSONArray("penalties") }?.takeIf { it.length() == 2 }
    val state = when {
        pens != null -> L("پنالتی ") + fa("${pens.optInt(0)} - ${pens.optInt(1)}")
        m.finished -> L("پایان بازی")
        m.live -> L("زنده · {0}", fa(m.minute))
        else -> L("پیش از بازی")
    }
    p.color = if (m.live) C.live.toArgb() else 0x66000000
    val sw = 360f
    c.drawRoundRect(RectF(W / 2f - sw / 2, 1290f, W / 2f + sw / 2, 1370f), 40f, 40f, p)
    text(c, state, W / 2f, 1302f, 40f, 0xFFFFFFFF.toInt(), black, 340)

    // scorers under each side
    val goals = m.events.filter { it.s("type") == "Goal" }
    fun scorers(home: Boolean) = goals.filter { it.b("isHome") == home }.take(5).joinToString("\n") { g ->
        val n = g.o("player").s("lastName") ?: g.s("nameStr")?.substringAfterLast(' ') ?: g.o("player").s("name") ?: ""
        (if (en) n else FaNames.of(n)) + " " + fa(minuteText(g) + "'") + if (g.b("ownGoal")) L(" (گل به خودی)") else if (isPen(g)) L(" (پ)") else ""
    }
    text(c, scorers(true), homeX, 1420f, 34f, 0xEEFFFFFF.toInt(), bold, 460, 5)
    text(c, scorers(false), awayX, 1420f, 34f, 0xEEFFFFFF.toInt(), bold, 460, 5)

    // the user's prediction, when there is one
    Predictions.get(m.matchId)?.let { pk ->
        val pts = pk.points
        val line = L("پیش‌بینی من: {0}", fa("${pk.home} - ${pk.away}")) + when (pts) { 3 -> L("  🎯 +۳"); 1 -> L("  ✓ +۱"); 0 -> "  ✗"; else -> "" }
        p.color = 0x33FFFFFF
        c.drawRoundRect(RectF(160f, 1640f, W - 160f, 1720f), 40f, 40f, p)
        text(c, line, W / 2f, 1652f, 40f, C.gold.toArgb(), black, 760)
    }
    text(c, L("نتایج زنده فوتبال در گل‌لایو · دانلود از بازار"), W / 2f, 1790f, 38f, 0xCCFFFFFF.toInt(), bold, 980)
    return bmp
}

/** Centered text from [top], wrapped to [width] (Persian is shaped and laid out right to left by the layout itself). */
private fun text(c: Canvas, s: String, cx: Float, top: Float, size: Float, color: Int, tf: Typeface, width: Int, maxLines: Int = 1) {
    if (s.isBlank()) return
    val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; textSize = size; typeface = tf }
    val l = StaticLayout.Builder.obtain(s, 0, s.length, tp, width).setAlignment(Layout.Alignment.ALIGN_CENTER)
        .setMaxLines(maxLines).setEllipsize(TextUtils.TruncateAt.END).setIncludePad(false).build()
    c.save(); c.translate(cx - width / 2f, top); l.draw(c); c.restore()
}
