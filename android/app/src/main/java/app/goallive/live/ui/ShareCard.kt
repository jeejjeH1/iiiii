package app.goallive.live.ui

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import app.goallive.live.L
import app.goallive.live.Lang
import app.goallive.live.R
import app.goallive.live.data.FaNames
import app.goallive.live.data.Predictions
import app.goallive.live.data.Store
import app.goallive.live.data.a
import app.goallive.live.data.b
import app.goallive.live.data.d
import app.goallive.live.data.i
import app.goallive.live.data.o
import app.goallive.live.data.objs
import app.goallive.live.data.path
import app.goallive.live.data.s
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

const val BAZAAR_URL = "https://cafebazaar.ir/app/app.goallive.live"

/* ---------- what the user can choose ---------- */

enum class CardFormat(val w: Int, val h: Int) { STORY(1080, 1920), PORTRAIT(1080, 1350), SQUARE(1080, 1080) }

/** Everything that shapes the picture; kept between shares. */
data class CardOptions(
    val format: CardFormat = CardFormat.STORY,
    val theme: String = "teams",
    val scorers: Boolean = true,
    val stats: Boolean = true,
    val best: Boolean = true,
    val momentum: Boolean = true,
    val prediction: Boolean = true,
    val caption: String = "",
    val sticker: String = "",
)

internal fun loadOptions(): CardOptions = runCatching {
    val p = Store.prefs()
    CardOptions(
        format = CardFormat.valueOf(p.getString("card.format", "STORY") ?: "STORY"),
        theme = p.getString("card.theme", "teams") ?: "teams",
        scorers = p.getBoolean("card.scorers", true), stats = p.getBoolean("card.stats", true), best = p.getBoolean("card.best", true),
        momentum = p.getBoolean("card.momentum", true), prediction = p.getBoolean("card.prediction", true),
    )
}.getOrDefault(CardOptions())

internal fun saveOptions(o: CardOptions) = runCatching {
    Store.prefs().edit().putString("card.format", o.format.name).putString("card.theme", o.theme).putBoolean("card.scorers", o.scorers)
        .putBoolean("card.stats", o.stats).putBoolean("card.best", o.best).putBoolean("card.momentum", o.momentum)
        .putBoolean("card.prediction", o.prediction).apply()
}

/** Opens the story studio for [m]: the user styles the picture or the video, then shares or saves it. */
fun shareMatchCard(@Suppress("UNUSED_PARAMETER") ctx: Context, m: MatchData) { StoryStudio.story = Story.Match(m) }

/** Pictures the card needs, fetched once per match. */
class CardAssets(val home: Bitmap?, val away: Bitmap?, val logo: Bitmap?, val best: Bitmap?, val black: Typeface, val bold: Typeface, val hc: Int, val ac: Int, val league: Bitmap?)

internal suspend fun image(ctx: Context, url: String, px: Int): Bitmap? = runCatching {
    kotlinx.coroutines.withTimeoutOrNull(6_000) { ctx.imageLoader.execute(ImageRequest.Builder(ctx).data(url).allowHardware(false).build()) }?.drawable?.toBitmap(px, px)
}.getOrNull()

internal suspend fun cardAssets(ctx: Context, m: MatchData, best: PlayerRating?): CardAssets {
    val en = Lang.en
    val home = image(ctx, teamLogo(m.homeId, true), 320); val away = image(ctx, teamLogo(m.awayId, true), 320)
    return CardAssets(
        home, away,
        BitmapFactory.decodeResource(ctx.resources, R.drawable.logo),
        best?.let { image(ctx, playerFace(it.id), 260) },
        if (en) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else ResourcesCompat.getFont(ctx, R.font.vazir_black) ?: Typeface.DEFAULT_BOLD,
        if (en) Typeface.DEFAULT_BOLD else ResourcesCompat.getFont(ctx, R.font.vazir_bold) ?: Typeface.DEFAULT_BOLD,
        cardColor(m.homeColor, home, 0xFF1E5BD8), cardColor(m.awayColor, away, 0xFFD62839),
        image(ctx, "https://images.fotmob.com/image_resources/logo/leaguelogo/${m.leagueId}.png", 200),
    )
}

/** GoalLive's best player of the match, if it was rated. */
internal fun bestOf(m: MatchData): PlayerRating? =
    runCatching { rateMatch(m) }.getOrDefault(emptyMap()).values.filter { it.minutes >= 20 }.maxByOrNull { it.value }

/** White writing sits on the team colours, so a light kit colour is taken down to a mid tone first. */
internal fun deep(c: Color) = (if (c.luminance() > 0.45f) androidx.compose.ui.graphics.lerp(c, Color.Black, 0.55f) else c).toArgb()


/* ---------- drawing ---------- */

/** The share picture: competition at the top, the crests and the score, then the chosen parts spread over the space, and the brand. Drawn at [scale]. */
internal fun drawCard(@Suppress("UNUSED_PARAMETER") ctx: Context, m: MatchData, o: CardOptions, a: CardAssets, best: PlayerRating?, scale: Float): Bitmap =
    matchScene(m, o, a, best).still(scale)

/** The match card as a scene: the design and the faint crests stay still behind; everything else comes in on [Clock]. */
internal fun matchScene(m: MatchData, o: CardOptions, a: CardAssets, best: PlayerRating?): Scene {
    val W = o.format.w; val H = o.format.h
    val bg = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
    val bc = Canvas(bg)
    val look = background(bc, o.theme, W, H, a.hc, a.ac)
    val homeX = if (Lang.en) W * 0.25f else W * 0.75f
    val awayX = W - homeX
    // both crests, huge and faint, behind everything
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    val wm = W * 0.95f
    for ((bm, x, y0) in listOf(Triple(a.home, homeX, H * 0.05f), Triple(a.away, awayX, H * 0.55f))) bm?.let {
        p.alpha = if (lightTheme(o.theme)) 13 else 18
        bc.drawBitmap(it, null, RectF(x - wm / 2, y0, x + wm / 2, y0 + wm), p); p.alpha = 255
    }
    grain(bc, W, H, lightTheme(o.theme))
    return Scene(W, H, bg, a.hc, a.ac) { c, clk -> matchFront(c, m, o, a, best, look, clk) }.also { it.theme = o.theme }
}

private fun matchFront(c: Canvas, m: MatchData, o: CardOptions, a: CardAssets, best: PlayerRating?, look: CardLook, clk: Clock) {
    val W = o.format.w; val H = o.format.h
    val en = Lang.en
    val hc = a.hc; val ac = a.ac
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    val story = o.format == CardFormat.STORY
    val sq = o.format == CardFormat.SQUARE
    val k = if (story) 1f else if (sq) 0.78f else 0.86f   // overall size of the writing
    val homeX = if (en) W * 0.25f else W * 0.75f
    val awayX = W - homeX

    // header: the competition's badge, its name and round, the day under it
    val y0 = if (story) 80f else if (sq) 46f else 54f
    val lr = 36f * k
    val nameY = y0 + if (a.league != null) 2 * lr + 26 * k else 0f
    val dateY = nameY + 36f * k * 1.5f
    val top = dateY + 27f * k * 1.4f + (if (story) 50f else 24f)
    c.enter(clk.at(0.1f), 0f, top) {
        a.league?.let { lg ->
            p.color = 0xFFFFFFFF.toInt(); p.setShadowLayer(18f, 0f, 6f, 0x55000000); c.drawCircle(W / 2f, y0 + lr, lr + 10, p); p.clearShadowLayer()
            c.drawBitmap(lg, null, RectF(W / 2f - lr, y0, W / 2f + lr, y0 + 2 * lr), p)
        }
        val round = m.general.s("leagueRoundName")?.let { if (it.all(Char::isDigit)) L(" | هفته {0}", fa(it)) else "" } ?: ""
        text(c, leagueName(m.leagueId, m.general.s("leagueName") ?: "") + round, W / 2f, nameY, 36f * k, look.ink, a.black, W - 160)
        text(c, fa(persianDate(m.time, "EEEE d MMMM yyyy")), W / 2f, dateY, 27f * k, look.sub, a.bold, W - 160)
    }
    val footer = if (story) 160f else if (sq) 110f else 120f
    var y: Float

    // each part: its height, and how to draw it from a given top
    class Block(val h: Float, val draw: (Float) -> Unit)
    val blocks = mutableListOf<Block>()
    val pens = m.status.o("reason")?.optJSONArray("penalties")?.takeIf { it.length() == 2 }
    val state = when {
        m.finished -> L("پایان بازی")
        m.live -> L("زنده | {0}", fa(m.minute))
        else -> L("پیش از بازی")
    }
    val hs0 = m.home.i("score") ?: 0; val aws0 = m.away.i("score") ?: 0
    val run = easeOut(clk.at(0.95f, 0.8f))
    val hs = Math.round(hs0 * run); val aws = Math.round(aws0 * run)
    fun crest(bm: Bitmap?, x: Float, cy: Float, r: Float, col: Int) {
        // the crest on a glass disc ringed in the team's colour, with a glow of that colour behind
        p.shader = RadialGradient(x, cy, r * 1.9f, intArrayOf(brighten(col) and 0x00FFFFFF or 0x99000000.toInt(), brighten(col) and 0x00FFFFFF), null, Shader.TileMode.CLAMP)
        c.drawCircle(x, cy, r * 1.9f, p); p.shader = null
        p.color = look.panel; p.setShadowLayer(30f, 0f, 14f, 0x66000000); c.drawCircle(x, cy, r * 1.22f, p); p.clearShadowLayer()
        p.style = Paint.Style.STROKE; p.strokeWidth = 6f * k
        p.shader = LinearGradient(x, cy - r, x, cy + r, brighten(col), brighten(col) and 0x00FFFFFF or 0x55000000, Shader.TileMode.CLAMP)
        c.drawCircle(x, cy, r * 1.22f, p); p.shader = null; p.style = Paint.Style.FILL
        bm?.let { c.drawBitmap(it, null, RectF(x - r * 0.86f, cy - r * 0.86f, x + r * 0.86f, cy + r * 0.86f), null) }
    }
    // the score: each side's number on its own side, the loser's a little dimmer
    fun numbers(cy: Float, size: Float, gapX: Float) {
        if (!m.started) { bigText(c, fa(timeOf(m.time)), W / 2f, cy - size * 0.36f, size * 0.62f, look.ink, a.black, 1f); return }
        bigText(c, fa(hs), homeX.let { if (en) W / 2f - gapX else W / 2f + gapX }, cy - size * 0.6f, size, look.ink, a.black, if (hs0 < aws0 && m.finished && run >= 1f) 0.5f else 1f)
        bigText(c, fa(aws), if (en) W / 2f + gapX else W / 2f - gapX, cy - size * 0.6f, size, look.ink, a.black, if (aws0 < hs0 && m.finished && run >= 1f) 0.5f else 1f)
        p.color = look.sub; c.drawRoundRect(RectF(W / 2f - size * 0.12f, cy - size * 0.04f, W / 2f + size * 0.12f, cy + size * 0.04f), 8f, 8f, p)
    }
    val stateInk = if (m.live) 0xFFFFFFFF.toInt() else look.ink
    val stateBg = if (m.live) C.live.toArgb() else look.panel
    if (sq) {
        val cr = 80f; val nm = 34f; val size = 150f
        blocks += Block(2 * cr * 1.22f + 20 + nm * 2.5f) { t ->
            val cy = t + cr * 1.22f
            crest(a.home, if (en) 175f else W - 175f, cy, cr, hc); crest(a.away, if (en) W - 175f else 175f, cy, cr, ac)
            numbers(cy, size, 110f)
            pill(c, state + (pens?.let { "  " + L("پنالتی ") + fa("${it.optInt(if (en) 0 else 1)} - ${it.optInt(if (en) 1 else 0)}") } ?: ""), W / 2f, cy + size * 0.5f, 22f, stateInk, stateBg, a.black, 420, look.stroke)
            text(c, teamName(m.homeId, m.homeName), if (en) 175f else W - 175f, t + 2 * cr * 1.22f + 22, nm, look.ink, a.black, 330, 2)
            text(c, teamName(m.awayId, m.awayName), if (en) W - 175f else 175f, t + 2 * cr * 1.22f + 22, nm, look.ink, a.black, 330, 2)
        }
    } else {
        val cr = if (story) 100f else 84f; val nm = if (story) 46f else 38f; val size = if (story) 220f else 180f
        blocks += Block(2 * cr * 1.22f + 24 + nm * 2.5f + size * 1.05f + 60f * k) { t ->
            val cy = t + cr * 1.22f
            crest(a.home, homeX, cy, cr, hc); crest(a.away, awayX, cy, cr, ac)
            text(c, "VS", W / 2f, cy - 30f * k, 44f * k, look.sub, Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC), 200)
            val nt = t + 2 * cr * 1.22f + 24
            text(c, teamName(m.homeId, m.homeName), homeX, nt, nm, look.ink, a.black, (W * 0.44f).toInt(), 2)
            text(c, teamName(m.awayId, m.awayName), awayX, nt, nm, look.ink, a.black, (W * 0.44f).toInt(), 2)
            val sy = nt + nm * 2.5f + size * 0.55f
            numbers(sy, size, size * 0.62f)
            pill(c, state, W / 2f, sy + size * 0.52f, 28f * k, stateInk, stateBg, a.black, 420, look.stroke)
            pens?.let { pk -> text(c, L("پنالتی ") + fa("${pk.optInt(if (en) 0 else 1)} - ${pk.optInt(if (en) 1 else 0)}"), W / 2f, sy + size * 0.52f + 70f * k, 30f * k, look.accent, a.black, 600) }
        }
    }
    // before kick-off: each side's last five results, as coloured tiles under its crest
    val form = m.facts.a("teamForm")?.takeIf { !m.started && it.length() == 2 }
    if (form != null) {
        val h = 150f * k
        blocks += Block(h) { t ->
            glass(c, 56f, t, W - 56f, t + h, look)
            text(c, L("فرم ۵ بازی اخیر"), W / 2f, t + 18f * k, 27f * k, look.sub, a.bold, 600)
            for ((side, x0) in listOf(0 to homeX, 1 to awayX)) {
                val res = form.optJSONArray(side).objs().takeLast(5).map { f -> f.s("resultString") ?: when (f.i("result")) { 1 -> "W"; 0 -> "D"; -1 -> "L"; else -> "" } }
                val sz = 58f * k; val gapX = 12f * k
                val rowW = res.size * sz + (res.size - 1).coerceAtLeast(0) * gapX
                // oldest first in reading order
                res.forEachIndexed { j, r ->
                    val left = if (en) x0 - rowW / 2 + j * (sz + gapX) else x0 + rowW / 2 - (j + 1) * sz - j * gapX
                    p.color = when (r) { "W" -> 0xFF14B866.toInt(); "L" -> 0xFFE5434B.toInt(); else -> 0xFF8A93A3.toInt() }
                    c.drawRoundRect(RectF(left, t + 68f * k, left + sz, t + 68f * k + sz), 16f * k, 16f * k, p)
                    text(c, when (r) { "W" -> L("ب"); "L" -> L("ش"); else -> L("م") }, left + sz / 2, t + 68f * k + sz * 0.16f, sz * 0.5f, 0xFFFFFFFF.toInt(), a.black, sz.toInt())
                }
            }
        }
    }
    if (o.caption.isNotBlank()) {
        val sz = 56f * k
        blocks += Block(sz * 1.6f) { t ->
            // the user's words between two short rules in the accent colour
            val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = sz; typeface = a.black }
            val w = min(W - 260f, tp.measureText(o.caption.trim()))
            p.color = look.accent
            c.drawRoundRect(RectF(W / 2f - w / 2 - 90, t + sz * 0.62f, W / 2f - w / 2 - 30, t + sz * 0.62f + 6), 3f, 3f, p)
            c.drawRoundRect(RectF(W / 2f + w / 2 + 30, t + sz * 0.62f, W / 2f + w / 2 + 90, t + sz * 0.62f + 6), 3f, 3f, p)
            text(c, o.caption.trim(), W / 2f, t, sz, look.accent, a.black, W - 240, 1)
        }
    }
    val goals = m.events.filter { it.s("type") == "Goal" && !it.b("isPenaltyShootoutEvent") }
    if (o.scorers && goals.isNotEmpty()) {
        fun lines(home: Boolean): CharSequence {
            val sb = android.text.SpannableStringBuilder()
            for (g in goals.filter { it.b("isHome") == home }.take(if (story) 5 else 4)) {
                val n = g.o("player").s("lastName") ?: g.s("nameStr")?.substringAfterLast(' ') ?: g.o("player").s("name") ?: ""
                if (sb.isNotEmpty()) sb.append("\n")
                sb.append(if (en) n else FaNames.of(n))
                val tail = " " + fa(minuteText(g) + "'") + if (g.b("ownGoal")) L(" (به خودی)") else if (isPen(g)) L(" (پ)") else ""
                val s0 = sb.length; sb.append(tail)
                sb.setSpan(android.text.style.ForegroundColorSpan(look.accent), s0, sb.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            return sb
        }
        val rows = maxOf(goals.count { it.b("isHome") }.coerceAtMost(if (story) 5 else 4), goals.count { !it.b("isHome") }.coerceAtMost(if (story) 5 else 4))
        val sz = 33f * k; val lh = sz * 1.5f; val h = rows * lh + 56f * k
        blocks += Block(h) { t ->
            glass(c, 56f, t, W - 56f, t + h, look)
            ball(c, W / 2f, t + h / 2, 22f * k, look)
            p.color = look.stroke; c.drawRect(W / 2f - 1, t + 24, W / 2f + 1, t + h / 2 - 34 * k, p); c.drawRect(W / 2f - 1, t + h / 2 + 34 * k, W / 2f + 1, t + h - 24, p)
            spanText(c, lines(true), homeX + (if (en) -10f else 10f), t + 28f * k, sz, look.ink, a.bold, (W * 0.4f).toInt())
            spanText(c, lines(false), awayX + (if (en) 10f else -10f), t + 28f * k, sz, look.ink, a.bold, (W * 0.4f).toInt())
        }
    }
    if (o.best && best != null) {
        val h = 200f * k
        blocks += Block(h) { t ->
            glass(c, 56f, t, W - 56f, t + h, look)
            // the face in a ring of the accent colour on the reading-start side, the rating on the other
            val fr = 70f * k; val fx = if (en) 56f + 40 + fr else W - 56f - 40 - fr; val fy = t + h / 2
            p.color = look.accent; c.drawCircle(fx, fy, fr + 6, p)
            p.color = 0xFF1B2028.toInt(); c.drawCircle(fx, fy, fr, p)
            a.best?.let { f ->
                c.save(); c.clipPath(Path().apply { addCircle(fx, fy, fr, Path.Direction.CW) })
                c.drawBitmap(f, null, RectF(fx - fr, fy - fr + 6, fx + fr, fy + fr + 6), null); c.restore()
            }
            text(c, "★ " + L("بهترین بازیکن گل‌لایو"), W / 2f, t + h / 2 - 50f * k, 27f * k, look.accent, a.bold, 520)
            text(c, personName(best.name), W / 2f, t + h / 2 - 8f * k, 46f * k, look.ink, a.black, 520)
            val rx = if (en) W - 56f - 40 - 70f * k else 56f + 40 + 70f * k
            p.color = ratingColor(best.value); p.setShadowLayer(20f, 0f, 8f, ratingColor(best.value) and 0x00FFFFFF or 0x88000000.toInt())
            c.drawRoundRect(RectF(rx - 72 * k, fy - 42 * k, rx + 72 * k, fy + 42 * k), 28f * k, 28f * k, p); p.clearShadowLayer()
            text(c, fa(dec(best.value, 1)), rx, fy - 30f * k, 50f * k, 0xFFFFFFFF.toInt(), a.black, 160)
        }
    }
    if (o.stats && m.started) {
        val all = m.content.path("stats.Periods.All").a("stats").objs().firstOrNull()?.a("stats").objs()
        val rows = listOf("BallPossesion" to L("مالکیت توپ"), "expected_goals" to L("گل مورد انتظار (xG)"), "total_shots" to L("شوت"), "ShotsOnTarget" to L("شوت در چارچوب"))
            .mapNotNull { (key, label) -> all.firstOrNull { it.s("key") == key }?.let { label to it } }.take(3)
        val rh = 84f * k
        if (rows.isNotEmpty()) blocks += Block(rows.size * rh + 40f * k) { t ->
            glass(c, 56f, t, W - 56f, t + rows.size * rh + 40f * k, look)
            var ry = t + 24f * k
            for ((label, st) in rows) {
                val v = st.a("stats")
                val hv = v?.optString(0) ?: ""; val av = v?.optString(1) ?: ""
                val hn = hv.substringBefore(' ').replace("%", "").toDoubleOrNull() ?: 0.0; val an = av.substringBefore(' ').replace("%", "").toDoubleOrNull() ?: 0.0
                text(c, fa(hv), homeX.let { if (en) 150f else W - 150f }, ry, 38f * k, if (hn >= an) look.ink else look.sub, a.black, 220)
                text(c, label, W / 2f, ry + 6 * k, 28f * k, look.sub, a.bold, 520)
                text(c, fa(av), if (en) W - 150f else 150f, ry, 38f * k, if (an >= hn) look.ink else look.sub, a.black, 220)
                // two bars from the middle outwards, each as long as that side's share
                val tot = (hn + an).takeIf { it > 0 } ?: 1.0
                val half = (W - 240f) / 2; val by = ry + rh - 34f * k; val bh = 12f * k
                p.color = look.stroke
                c.drawRoundRect(RectF(120f, by, W - 120f, by + bh), bh / 2, bh / 2, p)
                val hw = (half * hn / tot * 2).toFloat().coerceAtMost(half); val aw = (half * an / tot * 2).toFloat().coerceAtMost(half)
                val hSide = if (en) -1 else 1
                p.color = brighten(hc); if (hSide > 0) c.drawRoundRect(RectF(W / 2f + 4, by, W / 2f + 4 + hw, by + bh), bh / 2, bh / 2, p) else c.drawRoundRect(RectF(W / 2f - 4 - hw, by, W / 2f - 4, by + bh), bh / 2, bh / 2, p)
                p.color = brighten(ac); if (hSide > 0) c.drawRoundRect(RectF(W / 2f - 4 - aw, by, W / 2f - 4, by + bh), bh / 2, bh / 2, p) else c.drawRoundRect(RectF(W / 2f + 4, by, W / 2f + 4 + aw, by + bh), bh / 2, bh / 2, p)
                ry += rh
            }
        }
    }
    val mom = if (o.momentum && m.started) momentumOf(m) else emptyList()
    if (mom.size > 5) {
        val h = 220f * k
        blocks += Block(h) { t ->
            glass(c, 56f, t, W - 56f, t + h, look)
            text(c, L("مومنتوم بازی"), W / 2f, t + 18f * k, 27f * k, look.sub, a.bold, 600)
            val mid = t + h * 0.6f; val left = 100f; val width = W - 200f; val amp = h * 0.3f
            val maxMin = maxOf(90.0, mom.maxOf { it.d("minute") ?: 0.0 }).toFloat()
            fun xOf(mn: Float) = if (en) left + mn / maxMin * width else left + width - mn / maxMin * width
            // a smooth curve, filled above the line for the home side and below it for the away side
            val curve = Path().apply {
                moveTo(xOf(0f), mid)
                var px = xOf(0f); var py = mid
                for (pt in mom) {
                    val x = xOf((pt.d("minute") ?: 0.0).toFloat()); val yv = mid - (pt.d("value") ?: 0.0).toFloat() / 100f * amp
                    quadTo(px, py, (px + x) / 2, (py + yv) / 2); px = x; py = yv
                }
                lineTo(px, mid); close()
            }
            c.save(); c.clipRect(0f, t, W.toFloat(), mid)
            p.shader = LinearGradient(0f, mid - amp, 0f, mid, brighten(hc), brighten(hc) and 0x00FFFFFF or 0x33000000, Shader.TileMode.CLAMP); c.drawPath(curve, p); c.restore()
            c.save(); c.clipRect(0f, mid, W.toFloat(), t + h)
            p.shader = LinearGradient(0f, mid, 0f, mid + amp, brighten(ac) and 0x00FFFFFF or 0x33000000, brighten(ac), Shader.TileMode.CLAMP); c.drawPath(curve, p); c.restore()
            p.shader = null
            p.color = look.sub; p.alpha = 110; c.drawRect(left, mid - 1.5f, left + width, mid + 1.5f, p); p.alpha = 255
            // half time and the goals on the line
            p.color = look.stroke; c.drawRect(xOf(45f) - 1.5f, mid - amp, xOf(45f) + 1.5f, mid + amp, p)
            for (g in goals) {
                val mn = (minuteText(g).substringBefore('+').filter(Char::isDigit).toFloatOrNull() ?: continue).coerceAtMost(maxMin)
                ball(c, xOf(mn), if (g.b("isHome")) mid - amp - 6 else mid + amp + 6, 13f * k, look)
            }
        }
    }
    if (o.prediction) Predictions.get(m.matchId)?.let { pk ->
        val line = L("پیش‌بینی من: {0}", fa(if (en) "${pk.home} - ${pk.away}" else "${pk.away} - ${pk.home}")) + when (pk.points) { null -> ""; else -> "  ·  +" + fa(pk.points ?: 0) }
        blocks += Block(36f * k * 2.1f) { t -> pill(c, line, W / 2f, t, 34f * k, look.accent, look.panel, a.black, 860, look.stroke) }
    }

    // keep the parts that fit, in order, and spread them evenly between the header and the footer
    val avail = H - footer - top
    val minGap = if (story) 40f else 22f
    val shown = mutableListOf<Block>(); var used = 0f
    for (b in blocks) { val need = b.h + if (shown.isEmpty()) 0f else minGap; if (used + need <= avail) { used += need; shown += b } }
    val gap = if (shown.size > 1) min(minGap * 2.8f, minGap + (avail - used) / (shown.size + 1)) else 0f
    val total = shown.sumOf { it.h.toDouble() }.toFloat() + gap * (shown.size - 1).coerceAtLeast(0)
    y = top + (avail - total) / 2
    shown.forEachIndexed { i, b ->
        val by0 = y
        // the crests and the score pop in first; the other parts slide up one after another
        if (i == 0) c.pop(clk.at(0.4f, 0.6f), W / 2f, by0 + b.h / 2) { b.draw(by0) }
        else c.enter(clk.at(1.2f + (i - 1) * 0.35f), by0, by0 + b.h) { b.draw(by0) }
        y += b.h + gap
    }

    // the sticker, big in a top corner, a little tilted
    if (o.sticker.isNotEmpty()) stickerAt(c, o.sticker, if (en) W - 150f * k else 150f * k, if (story) 190f else 140f * k, 110f * k, hc, a.black, clk)
    // the brand: the app's mark and name side by side
    val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 44f * k; typeface = a.black }
    val name = L("گل‌لایو"); val nw = tp.measureText(name); val lr2 = 30f * k
    val bw = nw + 2 * lr2 + 20
    val by = H - footer / 2 - lr2 - (if (story) 10f else 0f)
    val lx = if (en) W / 2f - bw / 2 + lr2 else W / 2f + bw / 2 - lr2
    c.brand(clk, W / 2f, by + lr2) {
        capsule(c, RectF(W / 2f - bw / 2 - 26f * k, by - 18f * k, W / 2f + bw / 2 + 26f * k, by + 2 * lr2 + 18f * k), look)
        a.logo?.let { logo ->
            p.color = 0xFFFFFFFF.toInt(); c.drawCircle(lx, by + lr2, lr2 + 4, p)
            c.drawBitmap(logo, null, RectF(lx - lr2, by, lx + lr2, by + 2 * lr2), p)
        }
        text(c, name, if (en) W / 2f - bw / 2 + 2 * lr2 + 20 + nw / 2 else W / 2f - bw / 2 + nw / 2, by + lr2 - 44f * k * 0.62f, 44f * k, look.ink, a.black, (nw + 20).toInt())
    }
}

/** A little ball: a white disc with a dark pentagon pattern. */
internal fun ball(c: Canvas, x: Float, y: Float, r: Float, look: CardLook) {
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.color = 0xFFFFFFFF.toInt(); p.setShadowLayer(r * 0.6f, 0f, r * 0.2f, 0x66000000); c.drawCircle(x, y, r, p); p.clearShadowLayer()
    p.color = 0xFF15171C.toInt()
    fun pent(cx: Float, cy: Float, rr: Float) {
        val path = Path()
        for (i in 0 until 5) { val an = Math.toRadians(-90.0 + i * 72); val px = cx + rr * cos(an).toFloat(); val py = cy + rr * sin(an).toFloat(); if (i == 0) path.moveTo(px, py) else path.lineTo(px, py) }
        path.close(); c.drawPath(path, p)
    }
    pent(x, y, r * 0.38f)
    c.save(); c.clipPath(Path().apply { addCircle(x, y, r, Path.Direction.CW) })
    for (i in 0 until 5) { val an = Math.toRadians(-90.0 + i * 72); pent(x + r * 0.95f * cos(an).toFloat(), y + r * 0.95f * sin(an).toFloat(), r * 0.32f) }
    c.restore()
    p.style = Paint.Style.STROKE; p.strokeWidth = r * 0.08f; p.color = look.stroke or 0x22000000; c.drawCircle(x, y, r, p)
}

/** Fine film grain, so flat colours don't look flat. */
internal fun grain(c: Canvas, W: Int, H: Int, light: Boolean) {
    val p = Paint()
    val rnd = java.util.Random(11)
    repeat(W * H / 120) {
        p.color = if (rnd.nextBoolean()) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
        p.alpha = (if (light) 4 else 6) + rnd.nextInt(8)
        val x = rnd.nextFloat() * W; val y = rnd.nextFloat() * H
        c.drawRect(x, y, x + 2.5f, y + 2.5f, p)
    }
}

/** A frosted panel: the theme's glass colour, a light sheen at the top, an edge brighter at the top than the bottom. Comic: a white box, thick black edge, hard shadow. */
internal fun glass(c: Canvas, l: Float, t: Float, r: Float, b: Float, look: CardLook) {
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    val rect = RectF(l, t, r, b)
    if (look.comic) {
        val rad = 22f
        p.color = 0xFF111111.toInt(); c.drawRoundRect(RectF(l + 12f, t + 12f, r + 12f, b + 12f), rad, rad, p)
        p.color = look.panel; c.drawRoundRect(rect, rad, rad, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 6f; p.color = look.stroke; c.drawRoundRect(rect, rad, rad, p)
        return
    }
    val rad = 44f
    p.color = look.panel; p.setShadowLayer(48f, 0f, 20f, if (darkInk(look)) 0x1A000000 else 0x38000000); c.drawRoundRect(rect, rad, rad, p); p.clearShadowLayer()
    p.shader = LinearGradient(0f, t, 0f, b, intArrayOf(0x24FFFFFF, 0x08FFFFFF, 0x00FFFFFF), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP); c.drawRoundRect(rect, rad, rad, p)
    p.style = Paint.Style.STROKE; p.strokeWidth = 2.5f
    p.shader = LinearGradient(0f, t, 0f, b, edgeTop(look.stroke), edgeBottom(look.stroke), Shader.TileMode.CLAMP); c.drawRoundRect(rect, rad, rad, p); p.shader = null
}

/** A big number: a soft shadow under it and a top-to-bottom sheen in it; [fade] dims it. */
internal fun bigText(c: Canvas, s: String, cx: Float, top: Float, size: Float, ink: Int, tf: Typeface, fade: Float) {
    val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; typeface = tf }
    val w = tp.measureText(s); val x = cx - w / 2
    val base = top + size * 0.95f
    tp.color = 0x66000000; tp.maskFilter = BlurMaskFilter(size * 0.08f, BlurMaskFilter.Blur.NORMAL)
    c.drawText(s, x, base + size * 0.05f, tp); tp.maskFilter = null
    tp.shader = LinearGradient(0f, top + size * 0.2f, 0f, base, ink, androidx.core.graphics.ColorUtils.blendARGB(ink, 0xFF888888.toInt(), 0.35f), Shader.TileMode.CLAMP)
    tp.alpha = (255 * fade).toInt()
    c.drawText(s, x, base, tp)
}

/** Centred styled text (spans keep their colours), wrapped to [width]. */
internal fun spanText(c: Canvas, s: CharSequence, cx: Float, top: Float, size: Float, color: Int, tf: Typeface, width: Int) {
    if (s.isEmpty()) return
    val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; textSize = size; typeface = tf }
    val l = StaticLayout.Builder.obtain(s, 0, s.length, tp, width).setAlignment(Layout.Alignment.ALIGN_CENTER).setLineSpacing(size * 1.5f - tp.fontSpacing, 1f)
        .setIncludePad(false).build()
    c.save(); c.translate(cx - width / 2f, top); l.draw(c); c.restore()
}

/** A team colour lifted a little so thin bars stay visible on dark backgrounds. */
internal fun brighten(c: Int): Int {
    val hsv = FloatArray(3); android.graphics.Color.colorToHSV(c or 0xFF000000.toInt(), hsv)
    hsv[2] = maxOf(hsv[2], 0.55f)
    return android.graphics.Color.HSVToColor(hsv)
}

/** The crest's main colour: the most common strong hue among its pixels, or null for a plain black-and-white crest. */
internal fun crestColor(b: Bitmap?): Int? {
    b ?: return null
    val s = Bitmap.createScaledBitmap(b, 40, 40, true)
    val weight = FloatArray(24); val sum = Array(24) { FloatArray(3) }
    val hsv = FloatArray(3)
    for (x in 0 until 40) for (y in 0 until 40) {
        val px = s.getPixel(x, y)
        if (android.graphics.Color.alpha(px) < 200) continue
        android.graphics.Color.colorToHSV(px, hsv)
        if (hsv[1] < 0.35f || hsv[2] < 0.25f) continue
        val k = (hsv[0] / 15f).toInt().coerceIn(0, 23)
        weight[k] += hsv[1]; sum[k][0] += android.graphics.Color.red(px).toFloat(); sum[k][1] += android.graphics.Color.green(px).toFloat(); sum[k][2] += android.graphics.Color.blue(px).toFloat()
    }
    val k = weight.indices.maxByOrNull { weight[it] } ?: return null
    if (weight[k] < 40f) return null
    var n = 0
    for (x in 0 until 40) for (y in 0 until 40) { android.graphics.Color.colorToHSV(s.getPixel(x, y), hsv); if (android.graphics.Color.alpha(s.getPixel(x, y)) >= 200 && hsv[1] >= 0.35f && hsv[2] >= 0.25f && (hsv[0] / 15f).toInt().coerceIn(0, 23) == k) n++ }
    return android.graphics.Color.rgb((sum[k][0] / n).toInt(), (sum[k][1] / n).toInt(), (sum[k][2] / n).toInt())
}

/** The team's colour for the card: FotMob's when it has one, else the crest's, darkened so white writing reads on it. */
internal fun cardColor(c: Color, crest: Bitmap?, fallback: Long): Int {
    val hsv = FloatArray(3); android.graphics.Color.colorToHSV(c.toArgb(), hsv)
    val picked = if (hsv[1] >= 0.2f) c else crestColor(crest)?.let { Color(it) } ?: Color(fallback)
    return deep(picked)
}

internal fun ratingColor(r: Double) = when { r >= 9 -> 0xFF1D9BF0; r >= 7.5 -> 0xFF14B866; r >= 6.5 -> 0xFF9FB21B; r >= 5.5 -> 0xFFF08A24; else -> 0xFFE5434B }.toInt()

/** Text in a rounded pill with a hairline edge, centred on [cx]. */
internal fun pill(c: Canvas, s: String, cx: Float, top: Float, size: Float, ink: Int, bg: Int, tf: Typeface, maxW: Int, stroke: Int = 0) {
    val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; typeface = tf }
    val w = min(maxW.toFloat(), tp.measureText(s) + size * 1.8f)
    val r = RectF(cx - w / 2, top, cx + w / 2, top + size * 2f)
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bg }
    c.drawRoundRect(r, size, size, p)
    if (stroke != 0) { p.style = Paint.Style.STROKE; p.strokeWidth = 2f; p.color = stroke; c.drawRoundRect(r, size, size, p) }
    text(c, s, cx, top + size * 0.42f, size, ink, tf, (w - size).toInt())
}

/** Centered text from [top], wrapped to [width] (Persian is shaped and laid out right to left by the layout itself). */
internal fun text(c: Canvas, s: String, cx: Float, top: Float, size: Float, color: Int, tf: Typeface, width: Int, maxLines: Int = 1) {
    if (s.isBlank()) return
    val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; textSize = size; typeface = tf }
    val l = StaticLayout.Builder.obtain(s, 0, s.length, tp, width).setAlignment(Layout.Alignment.ALIGN_CENTER)
        .setMaxLines(maxLines).setEllipsize(TextUtils.TruncateAt.END).setIncludePad(false).build()
    c.save(); c.translate(cx - width / 2f, top); l.draw(c); c.restore()
}

/** Text from the reading start of the box [left]..[left] + [width] (the right edge in Persian), wrapped to [maxLines]. */
internal fun startText(c: Canvas, s: String, left: Float, top: Float, size: Float, color: Int, tf: Typeface, width: Int, maxLines: Int = 1, spacing: Float = 1.25f) {
    if (s.isBlank()) return
    val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; textSize = size; typeface = tf }
    val l = StaticLayout.Builder.obtain(s, 0, s.length, tp, width).setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(0f, spacing)
        .setMaxLines(maxLines).setEllipsize(TextUtils.TruncateAt.END).setIncludePad(false).build()
    c.save(); c.translate(left, top); l.draw(c); c.restore()
}

/* ---------- a formation as a story picture: team of the week and match line-ups ---------- */

/** Opens the story studio for [men] on a pitch under [title] / [sub]; [crest] (a club) or [league] marks the header. */
@Suppress("UNUSED_PARAMETER", "RedundantSuspendModifier")
suspend fun shareFormation(ctx: Context, title: String, sub: String, color: Color, men: List<PitchMan>, crest: Int? = null, league: Int? = null,
                           formation: String = "", starLine: String? = null, fileName: String = "goallive-xi") {
    StoryStudio.story = Story.Lineup(title, sub, color.toArgb(), men, crest, league, formation, starLine, fileName)
}

internal fun drawFormation(title: String, sub: String, tone: Int, men: List<PitchMan>, faces: Map<Int, Bitmap?>, crests: Map<Int, Bitmap?>,
                           badge: Bitmap?, logo: Bitmap?, black: Typeface, bold: Typeface, formation: String, starLine: String?, caption: String = ""): Bitmap =
    formationScene(title, sub, tone, men, faces, crests, badge, logo, black, bold, formation, starLine, caption).still(1f)

/** A formation as a scene: the night sky and the pitch stay still; the header, then each player from the keeper forwards, come in on [Clock]. */
internal fun formationScene(title: String, sub: String, tone: Int, men: List<PitchMan>, faces: Map<Int, Bitmap?>, crests: Map<Int, Bitmap?>,
                            badge: Bitmap?, logo: Bitmap?, black: Typeface, bold: Typeface, formation: String, starLine: String?, caption: String = ""): Scene {
    val W = 1080; val H = 1920
    val gold = 0xFFF5C542.toInt()
    val toneB = brighten(tone)
    // where things go: badge, title, subtitle, formation, then the pitch down to the star line / caption / brand
    val titleY = 90f + if (badge != null) 150f else 0f
    val subY = titleY + 92f
    val pillY = subY + 70f
    val pt = pillY + (if (formation.isNotEmpty()) 80f else 0f) + 10f
    val pl = 50f; val pr = W - 50f
    val pb = H - (if (starLine != null) 250f else 170f) - (if (caption.isNotBlank()) 96f else 0f)
    val pw = pr - pl; val ph = pb - pt
    val bg = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
    val bc = Canvas(bg)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    // night background lit in the team / league colour
    p.shader = LinearGradient(0f, 0f, 0f, H.toFloat(), intArrayOf(0xFF0B1016.toInt(), 0xFF06080B.toInt()), null, Shader.TileMode.CLAMP); bc.drawRect(0f, 0f, W.toFloat(), H.toFloat(), p)
    for ((x, y, r, al) in listOf(floatArrayOf(160f, 160f, 820f, 0.55f), floatArrayOf(980f, 1500f, 900f, 0.35f))) {
        p.shader = RadialGradient(x, y, r, intArrayOf((toneB and 0x00FFFFFF) or ((al * 255).toInt() shl 24), toneB and 0x00FFFFFF), null, Shader.TileMode.CLAMP)
        bc.drawCircle(x, y, r, p)
    }
    p.shader = null
    grain(bc, W, H, false)
    // the pitch
    bc.save()
    val clip = Path().apply { addRoundRect(RectF(pl, pt, pr, pb), 46f, 46f, Path.Direction.CW) }
    p.setShadowLayer(60f, 0f, 24f, 0x99000000.toInt()); p.color = 0xFF1B6B3B.toInt(); bc.drawPath(clip, p); p.clearShadowLayer()
    bc.clipPath(clip)
    p.shader = LinearGradient(0f, pt, 0f, pb, intArrayOf(0xFF237F47.toInt(), 0xFF1B6B3B.toInt(), 0xFF155A31.toInt()), null, Shader.TileMode.CLAMP); bc.drawRect(pl, pt, pr, pb, p); p.shader = null
    for (k in 0 until 12) if (k % 2 == 0) { p.color = 0x0BFFFFFF; bc.drawRect(pl, pt + ph * k / 12, pr, pt + ph * (k + 1) / 12, p) }
    p.shader = LinearGradient(pl, pt, pl + pw * 0.7f, pt + ph * 0.45f, 0x22FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP); bc.drawRect(pl, pt, pr, pb, p)
    p.shader = RadialGradient(W / 2f, pb + 60, pw * 0.9f, intArrayOf((toneB and 0x00FFFFFF) or 0x55000000, toneB and 0x00FFFFFF), null, Shader.TileMode.CLAMP); bc.drawRect(pl, pt, pr, pb, p); p.shader = null
    // markings
    val mx = pl + pw * 0.05f; val my = pt + ph * 0.035f; val mw = pw * 0.9f; val mh = ph * 0.93f
    val sx = mw / 68f; val sy = mh / 105f
    fun X(m: Float) = mx + m * sx
    fun Y(m: Float) = my + m * sy
    p.style = Paint.Style.STROKE; p.strokeWidth = 4f; p.color = 0x9EFFFFFF.toInt()
    bc.drawRoundRect(RectF(mx, my, mx + mw, my + mh), 6f, 6f, p)
    bc.drawLine(mx, Y(52.5f), mx + mw, Y(52.5f), p); bc.drawCircle(X(34f), Y(52.5f), 9.15f * sx, p)
    for (top in listOf(true, false)) {
        val g = if (top) 0f else 105f; val d = if (top) 1f else -1f
        for ((depth, half) in listOf(16.5f to 20.16f, 5.5f to 9.16f)) { val y0 = Y(g); val y1 = Y(g + d * depth); bc.drawRect(X(34f - half), minOf(y0, y1), X(34f + half), maxOf(y0, y1), p) }
        val pen = Y(g + d * 11f); val r = 9.15f * sx
        bc.drawArc(RectF(X(34f) - r, pen - r, X(34f) + r, pen + r), if (top) 37f else 217f, 106f, false, p)
        val gx0 = X(30.34f); val gx1 = X(37.66f); val gy = Y(g); val gy2 = gy - d * 3f * sy * 1.3f
        p.strokeWidth = 5f; p.color = 0xFFFFFFFF.toInt(); bc.drawLine(gx0, gy, gx0, gy2, p); bc.drawLine(gx1, gy, gx1, gy2, p); bc.drawLine(gx0, gy2, gx1, gy2, p)
        p.strokeWidth = 4f; p.color = 0x9EFFFFFF.toInt()
    }
    p.style = Paint.Style.FILL
    p.shader = RadialGradient(W / 2f, pt + ph * 0.45f, ph * 0.8f, intArrayOf(0x00000000, 0x66000000), null, Shader.TileMode.CLAMP); bc.drawRect(pl, pt, pr, pb, p); p.shader = null
    bc.restore()
    return Scene(W, H, bg, tone, tone) { c, clk ->
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        c.enter(clk.at(0.1f), 0f, pt) {
            badge?.let {
                p.color = 0xFFFFFFFF.toInt(); p.setShadowLayer(30f, 0f, 10f, 0x66000000); c.drawCircle(W / 2f, 90f + 62, 66f, p); p.clearShadowLayer()
                c.drawBitmap(it, null, RectF(W / 2f - 48, 90f + 14, W / 2f + 48, 90f + 110), null)
            }
            text(c, title, W / 2f, titleY, 64f, 0xFFFFFFFF.toInt(), black, W - 120)
            text(c, sub, W / 2f, subY, 34f, 0xCCFFFFFF.toInt(), bold, W - 120, 2)
            if (formation.isNotEmpty()) formationPill(c, formation, W / 2f, pillY, black)
        }
        // the players pop in from the keeper forwards; drawn back to front
        val order = men.sortedByDescending { it.y }.withIndex().associate { (i, m) -> m.id to i }
        for (m in men.sortedBy { it.y }) {
            val cx = pl + pw * m.x; val cy = pt + ph * m.y
            c.pop(clk.at(0.6f + (order[m.id] ?: 0) * 0.13f, 0.45f), cx, cy) {
            val r = 62f
            val ring = if (m.star) gold else toneB
            p.color = 0x66000000; c.drawOval(RectF(cx - 52, cy + r - 10, cx + 52, cy + r + 14), p)
            if (m.star) { p.shader = RadialGradient(cx, cy, r * 1.7f, intArrayOf(0x88F5C542.toInt(), 0x00F5C542), null, Shader.TileMode.CLAMP); c.drawCircle(cx, cy, r * 1.7f, p); p.shader = null }
            p.color = ring; c.drawCircle(cx, cy, r + 6, p)
            p.color = 0xFF1A2128.toInt(); c.drawCircle(cx, cy, r, p)
            faces[m.id]?.let { f -> c.save(); c.clipPath(Path().apply { addCircle(cx, cy, r, Path.Direction.CW) }); c.drawBitmap(f, null, RectF(cx - r, cy - r + 6, cx + r, cy + r + 6), null); c.restore() }
                ?: text(c, m.number, cx, cy - 26, 44f, 0xFFFFFFFF.toInt(), black, 120)
            // rating
            m.rating?.let { rt ->
                val s = fa(dec(rt, 1)); val tp = android.text.TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 30f; typeface = black }
                val bw = tp.measureText(s) + 24; val bx = cx + r * 0.55f; val by = cy - r - 4
                p.color = ratingColor(rt); c.drawRoundRect(RectF(bx - bw / 2, by, bx + bw / 2, by + 42), 12f, 12f, p)
                text(c, s, bx, by + 4, 30f, 0xFFFFFFFF.toInt(), black, 140)
            }
            // crest or number
            val bx = cx - r * 0.7f; val by = cy + r * 0.62f
            val cb = m.crest?.let { crests[it] }
            if (cb != null) { p.color = 0xFFFFFFFF.toInt(); c.drawCircle(bx, by, 24f, p); c.drawBitmap(cb, null, RectF(bx - 18, by - 18, bx + 18, by + 18), null) }
            else if (m.number.isNotEmpty()) {
                // the shirt number on the team colour, its ink dark on a light kit (Real Madrid's white hid it)
                val bg = tone or 0xFF000000.toInt()
                val light = androidx.core.graphics.ColorUtils.calculateLuminance(bg) > 0.55
                p.color = bg; c.drawRoundRect(RectF(bx - 22, by - 18, bx + 22, by + 18), 8f, 8f, p)
                p.style = Paint.Style.STROKE; p.strokeWidth = 2f; p.color = if (light) 0x55000000 else 0xAAFFFFFF.toInt(); c.drawRoundRect(RectF(bx - 22, by - 18, bx + 22, by + 18), 8f, 8f, p); p.style = Paint.Style.FILL
                text(c, m.number, bx, by - 15, 24f, if (light) 0xFF0B0F14.toInt() else 0xFFFFFFFF.toInt(), black, 60)
            }
            if (m.captain) { p.color = gold; c.drawCircle(cx + r * 0.7f, cy + r * 0.62f, 18f, p); text(c, if (Lang.en) "C" else "ک", cx + r * 0.7f, cy + r * 0.62f - 14, 22f, 0xFF2A1A00.toInt(), black, 40) }
            // name plate
            val tp = android.text.TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 30f; typeface = black }
            val nw = minOf(230f, tp.measureText(m.name) + 32); val ny = cy + r + 14
            p.color = 0xE60B0F14.toInt(); c.drawRoundRect(RectF(cx - nw / 2, ny, cx + nw / 2, ny + 46), 14f, 14f, p)
            if (m.star) { p.style = Paint.Style.STROKE; p.strokeWidth = 2.5f; p.color = gold; c.drawRoundRect(RectF(cx - nw / 2, ny, cx + nw / 2, ny + 46), 14f, 14f, p); p.style = Paint.Style.FILL }
            text(c, m.name, cx, ny + 5, 30f, if (m.star) gold else 0xFFFFFFFF.toInt(), black, (nw - 16).toInt())
            // goals and assists
            val marks = buildList { repeat(m.goals.coerceAtMost(4)) { add("g") }; repeat(m.assists.coerceAtMost(3)) { add("a") }; if (m.red) add("r") else if (m.yellow) add("y") }
            var ex = cx - (marks.size - 1) * 17f
            for (k in marks) {
                when (k) {
                    "g" -> { p.color = 0xFFFFFFFF.toInt(); c.drawCircle(ex, ny + 70, 14f, p); p.color = 0xFF111111.toInt(); c.drawCircle(ex, ny + 70, 5f, p) }
                    "a" -> { p.color = 0xFF39D98A.toInt(); c.drawCircle(ex, ny + 70, 14f, p); text(c, if (Lang.en) "A" else "پ", ex, ny + 58, 18f, 0xFF052914.toInt(), black, 30) }
                    "r", "y" -> { p.color = if (k == "r") 0xFFFF3358.toInt() else 0xFFFFC53D.toInt(); c.drawRoundRect(RectF(ex - 9, ny + 58, ex + 9, ny + 82), 3f, 3f, p) }
                }
                ex += 34f
            }
            }
        }
        // the star of the week, the caption, then the brand
        c.enter(clk.at(2.4f), pb, pb + 120f) { starLine?.let { pill(c, "★ $it", W / 2f, pb + 34, 32f, gold, 0x26F5C542, black, 960, 0x66F5C542) } }
        val capY = pb + if (starLine != null) 120f else 30f
        if (caption.isNotBlank()) c.enter(clk.at(2.8f), capY, capY + 80f) { captionLine(c, caption, capY, 0xFFF5C542.toInt(), black, W) }
        val fy = H - 110f
        c.brand(clk, W / 2f, fy + 26) {
            logo?.let { p.color = 0xFFFFFFFF.toInt(); c.drawCircle(W / 2f + 92, fy + 26, 30f, p); c.drawBitmap(it, null, RectF(W / 2f + 66, fy, W / 2f + 118, fy + 52), null) }
            text(c, L("گل‌لایو"), W / 2f - 20, fy + 2, 40f, 0xFFFFFFFF.toInt(), black, 220)
        }
    }
}

/** The formation in a pill, its pieces drawn one after another left to right (Persian digits would otherwise reverse). */
internal fun formationPill(c: Canvas, f: String, cx: Float, top: Float, tf: Typeface) {
    val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 30f; typeface = tf; color = 0xFFFFFFFF.toInt() }
    val parts = f.split("-"); val dash = tp.measureText(" - ")
    val w = parts.sumOf { tp.measureText(it).toDouble() }.toFloat() + dash * (parts.size - 1)
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33FFFFFF }
    val r = RectF(cx - w / 2 - 30, top, cx + w / 2 + 30, top + 60)
    c.drawRoundRect(r, 30f, 30f, p); p.style = Paint.Style.STROKE; p.strokeWidth = 2f; p.color = 0x55FFFFFF; c.drawRoundRect(r, 30f, 30f, p)
    var x = cx - w / 2
    parts.forEachIndexed { i, part ->
        if (i > 0) { c.drawText(" - ", x, top + 41, tp); x += dash }
        c.drawText(part, x, top + 41, tp); x += tp.measureText(part)
    }
}
