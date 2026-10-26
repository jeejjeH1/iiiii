package app.goallive.live.ui

import android.content.ActivityNotFoundException
import android.content.ContentValues
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
import android.os.Build
import android.provider.MediaStore
import android.text.TextPaint
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import app.goallive.live.L
import app.goallive.live.Lang
import app.goallive.live.R
import app.goallive.live.data.Clubs
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
import app.goallive.live.data.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/* ---------- time: every story is a 7-second animation; the picture is its last frame ---------- */

const val STORY_SECONDS = 7f

/** [t] seconds into the story; a still picture is drawn with every part already in place. */
class Clock(val t: Float) {
    val still: Boolean get() = t >= 1e5f
    /** 0 before [start], then up to 1 over [dur] seconds; always 1 for a still picture. */
    fun at(start: Float, dur: Float = 0.55f): Float = if (still) 1f else ((t - start) / dur).coerceIn(0f, 1f)
    companion object { val END = Clock(1e6f) }
}

internal fun easeOut(p: Float) = 1 - (1 - p).pow(3)
internal fun easeBack(p: Float): Float { val q = p - 1; return 1 + q * q * (2.7f * q + 1.7f) }

/** Draws [block] faded in and slid up by [p] (0 hidden, 1 in place); [top]..[bottom] is roughly where it is. */
internal inline fun Canvas.enter(p: Float, top: Float, bottom: Float, dy: Float = 90f, block: () -> Unit) {
    if (p <= 0f) return
    if (p >= 1f) { block(); return }
    val e = easeOut(p)
    val n = saveLayerAlpha(-400f, top - dy - 160f, 1480f, bottom + dy + 160f, (255 * e).toInt())
    translate(0f, (1 - e) * dy)
    block()
    restoreToCount(n)
}

/** Pops [block] in around ([cx], [cy]): grows past its size a little and settles. */
internal inline fun Canvas.pop(p: Float, cx: Float, cy: Float, block: () -> Unit) {
    if (p <= 0f) return
    if (p >= 1f) { block(); return }
    val s = 0.35f + 0.65f * easeBack(p)
    val n = saveLayerAlpha(cx - 1400f, cy - 1400f, cx + 1400f, cy + 1400f, (255 * min(1f, p * 2.5f)).toInt())
    scale(s, s, cx, cy)
    block()
    restoreToCount(n)
}

/** The app's mark: fades in early, and swells once as the story ends. */
internal inline fun Canvas.brand(clk: Clock, cx: Float, cy: Float, block: () -> Unit) {
    val beat = sin(PI * clk.at(5.7f, 0.5f)).toFloat()
    val s = if (clk.still) 1f else 1f + 0.14f * beat
    save(); scale(s, s, cx, cy)
    enter(clk.at(0.3f), cy - 60f, cy + 60f, 30f, block)
    restore()
}

internal fun countUp(v: Int, p: Float) = (v * easeOut(p)).roundToInt()

/** A short line of the user's own words between two accent rules. */
internal fun captionLine(c: Canvas, s: String, top: Float, color: Int, tf: Typeface, W: Int) {
    val sz = 50f
    val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = sz; typeface = tf }
    val w = min(W - 260f, tp.measureText(s.trim()))
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    c.drawRoundRect(RectF(W / 2f - w / 2 - 90, top + sz * 0.62f, W / 2f - w / 2 - 30, top + sz * 0.62f + 6), 3f, 3f, p)
    c.drawRoundRect(RectF(W / 2f + w / 2 + 30, top + sz * 0.62f, W / 2f + w / 2 + 90, top + sz * 0.62f + 6), 3f, 3f, p)
    text(c, s.trim(), W / 2f, top, sz, color, tf, W - 240, 1)
}

/** A story: a still layer drawn once and the moving parts drawn per frame. */
/** [hc] / [ac]: the two colours the "team colours" design uses, for its swatch in the studio. */
open class Scene(val W: Int, val H: Int, val bg: Bitmap, val hc: Int = 0, val ac: Int = 0, val fg: (Canvas, Clock) -> Unit) {
    /** The design, for its moving layer (embers, snow, stars…); empty for none. */
    var theme: String = ""

    /** How long the video runs. */
    open val seconds: Float get() = STORY_SECONDS

    protected val smooth = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    /** The finished picture at [scale]. */
    fun still(scale: Float): Bitmap {
        val out = Bitmap.createBitmap((W * scale).toInt(), (H * scale).toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.scale(scale, scale)
        c.drawBitmap(bg, 0f, 0f, smooth)
        if (theme.isNotEmpty()) themeLive(c, theme, LIVE_STILL_T, W, H, hc, ac)
        fg(c, Clock.END)
        return out
    }

    /** Frame [t] of the video on [c] (already scaled to W×H): a slow push into the background, the parts, a light sweep at the end. */
    open fun frame(c: Canvas, t: Float) {
        val intro = easeOut((t / 1.6f).coerceIn(0f, 1f))
        val z = 1.12f - 0.12f * intro + 0.035f * (t / STORY_SECONDS)
        c.save(); c.scale(z, z, W / 2f, H / 2f); c.drawBitmap(bg, 0f, 0f, smooth); c.restore()
        if (theme.isNotEmpty()) themeLive(c, theme, t, W, H, hc, ac)
        fg(c, Clock(t))
        val sp = (t - 5.6f) / 0.9f
        if (sp in 0f..1f) {
            val x = -W * 0.7f + sp * W * 2.4f
            val p = Paint().apply { shader = LinearGradient(x - 260f, 0f, x + 260f, 0f, intArrayOf(0x00FFFFFF, 0x38FFFFFF, 0x00FFFFFF), null, Shader.TileMode.CLAMP) }
            c.save(); c.skew(-0.35f, 0f); c.drawRect(-W.toFloat(), 0f, W * 3f, H.toFloat(), p); c.restore()
        }
        if (t < 0.35f) c.drawColor(((255 * (1 - t / 0.35f)).toInt() shl 24))
    }
}

/* ---------- what can be made into a story ---------- */

sealed class Story {
    abstract val file: String
    open val theme: String = "teams"
    /** The words sent with it. */
    abstract fun message(): String

    class Match(val m: MatchData) : Story() {
        override val file = "goallive-${m.matchId}"
        override fun message() = "${teamName(m.homeId, m.homeName)} ${if (m.started) "${m.home.i("score") ?: 0}-${m.away.i("score") ?: 0}" else timeOf(m.time)} " +
            teamName(m.awayId, m.awayName)
    }

    /** [p] is FotMob's playerData. */
    class Player(val p: JSONObject) : Story() {
        val id = p.i("id") ?: 0
        override val file = "goallive-player-$id"
        override fun message() = personName(p.s("name") ?: "")
    }

    /** One league's table; [mark] is a team to light up. */
    class Table(val league: Int, val name: String, val rows: List<JSONObject>, val legend: List<JSONObject>, val mark: Int = 0) : Story() {
        override val file = "goallive-table-$league"
        override val theme = "night"
        override fun message() = L("جدول {0}", leagueName(league, name))
    }

    /** GoalLive's Ballon d'Or race, first place first; [official] when the year's winner is already known. */
    class Ballon(val year: Int, val men: List<JSONObject>, val official: Boolean) : Story() {
        override val file = "goallive-ballon-$year"
        override val theme = "gold"
        override fun message() = L("توپ طلای {0}", fa(year))
    }

    /** Two players' FotMob playerData, side by side. */
    class Duel(val a: JSONObject, val b: JSONObject) : Story() {
        override val file = "goallive-duel-${a.i("id")}-${b.i("id")}"
        override fun message() = personName(a.s("name") ?: "") + " × " + personName(b.s("name") ?: "")
    }

    /** A match's team stats: possession and the key numbers side by side. */
    class Stats(val m: MatchData) : Story() {
        override val file = "goallive-stats-${m.matchId}"
        override fun message() = L("آمار بازی") + ": " + teamName(m.homeId, m.homeName) + " ${m.home.i("score") ?: 0}-${m.away.i("score") ?: 0} " + teamName(m.awayId, m.awayName)
    }

    /** The analyst's reading of a match: the fair result and the findings that decided it. */
    class Analysis(val m: MatchData) : Story() {
        override val file = "goallive-analysis-${m.matchId}"
        override fun message() = L("تحلیل گل‌لایو") + ": " + teamName(m.homeId, m.homeName) + " ${m.home.i("score") ?: 0}-${m.away.i("score") ?: 0} " + teamName(m.awayId, m.awayName)
    }

    /** A finished match told in about a minute (video only). */
    class Reel(val m: MatchData) : Story() {
        override val file = "goallive-reel-${m.matchId}"
        override fun message() = L("بازی در یک دقیقه") + ": " + teamName(m.homeId, m.homeName) + " ${m.home.i("score") ?: 0}-${m.away.i("score") ?: 0} " + teamName(m.awayId, m.awayName)
    }

    /** A formation (a match line-up or the team of the week). */
    class Lineup(val title: String, val sub: String, val tone: Int, val men: List<PitchMan>, val crest: Int?, val league: Int?,
                 val formation: String, val starLine: String?, val name: String) : Story() {
        override val file = name
        override fun message() = "$title\n$sub"
    }
}

/** The story whose studio is open (shown by the app's root, over any screen). */
object StoryStudio {
    var story by mutableStateOf<Story?>(null)
}

/** Pictures and fonts a story needs, fetched once. */
class Art(val logo: Bitmap?, val black: Typeface, val bold: Typeface, val faces: Map<Int, Bitmap?> = emptyMap(), val crests: Map<Int, Bitmap?> = emptyMap(),
          val leagues: Map<Int, Bitmap?> = emptyMap(), val card: CardAssets? = null, val best: PlayerRating? = null)


/** The league badge for a white disc (the light version). */
private fun badgeUrl(id: Int) = "https://images.fotmob.com/image_resources/logo/leaguelogo/$id.png"

internal suspend fun Story.load(ctx: Context): Art {
    val en = Lang.en
    val black = if (en) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else ResourcesCompat.getFont(ctx, R.font.vazir_black) ?: Typeface.DEFAULT_BOLD
    val bold = if (en) Typeface.DEFAULT_BOLD else ResourcesCompat.getFont(ctx, R.font.vazir_bold) ?: Typeface.DEFAULT_BOLD
    val logo = BitmapFactory.decodeResource(ctx.resources, R.drawable.logo)
    suspend fun faces(ids: List<Int>, px: Int = 360) = ids.filter { it > 0 }.distinct().associateWith { image(ctx, playerFace(it), px) }
    suspend fun crests(ids: List<Int>) = ids.filter { it > 0 }.distinct().associateWith { image(ctx, teamLogo(it, true), 200) }
    return when (this) {
        is Story.Match -> { val best = bestOf(m); Art(logo, black, bold, card = cardAssets(ctx, m, best), best = best) }
        is Story.Reel -> { val best = bestOf(m); Art(logo, black, bold, faces(reelFaces(m), 260), card = cardAssets(ctx, m, best), best = best) }
        is Story.Stats -> Art(logo, black, bold, card = cardAssets(ctx, m, null))
        is Story.Analysis -> { val best = bestOf(m); Art(logo, black, bold, card = cardAssets(ctx, m, best), best = best) }
        is Story.Player -> Art(logo, black, bold, faces(listOf(id), 480),
            crests(listOf(p.o("primaryTeam").i("teamId") ?: 0) + p.a("recentMatches").objs().take(6).map { it.i("opponentTeamId") ?: 0 }),
            (p.o("mainLeague").i("leagueId") ?: 0).takeIf { it > 0 }?.let { mapOf(it to image(ctx, badgeUrl(it), 200)) } ?: emptyMap())
        is Story.Table -> Art(logo, black, bold, crests = crests(rows.map { it.i("id") ?: 0 }), leagues = mapOf(league to image(ctx, badgeUrl(league), 220)))
        is Story.Ballon -> Art(logo, black, bold, faces(men.map { it.i("id") ?: 0 }, 420), crests(men.map { it.i("team") ?: 0 }))
        is Story.Duel -> Art(logo, black, bold, faces(listOf(a.i("id") ?: 0, b.i("id") ?: 0), 420),
            crests(listOf(a.o("primaryTeam").i("teamId") ?: 0, b.o("primaryTeam").i("teamId") ?: 0)))
        is Story.Lineup -> Art(logo, black, bold, faces(men.map { it.id }, 220), crests(men.mapNotNull { it.crest } + listOfNotNull(crest)),
            league?.let { mapOf(it to image(ctx, badgeUrl(it), 160)) } ?: emptyMap())
    }
}

/** The story drawn with the chosen options. */
internal fun Story.scene(o: CardOptions, a: Art): Scene = when (this) {
    is Story.Match -> matchScene(m, o, a.card!!, a.best)
    is Story.Reel -> reelScene(m, o, a.card!!, a.faces, a.best)
    is Story.Lineup -> formationScene(title, sub, tone, men, a.faces, a.crests, crest?.let { a.crests[it] } ?: league?.let { a.leagues[it] }, a.logo,
        a.black, a.bold, formation, starLine, o.caption)
    is Story.Player -> playerScene(this, o, a)
    is Story.Table -> tableScene(this, o, a)
    is Story.Ballon -> ballonScene(this, o, a)
    is Story.Duel -> duelScene(this, o, a)
    is Story.Stats -> statsScene(this, o, a)
    is Story.Analysis -> analysisScene(this, o, a)
}.also { if (this !is Story.Lineup && this !is Story.Reel) it.theme = o.theme }

/* ---------- shared drawing for the new stories ---------- */

internal const val SW = 1080
internal const val SH = 1920
private val en get() = Lang.en
/** x measured from the reading start: from the left in English, from the right in Persian. */
private fun xs(x: Float) = if (en) x else SW - x

internal fun teamTone(id: Int, data: JSONObject?, crest: Bitmap?, fallback: Long): Int {
    val club = Clubs.get(id)?.let { Color(it.c1) }
    val c = club ?: parseColor(data?.path("teamColors")?.s("color")) ?: Color(fallback)
    return cardColor(c, crest, fallback)
}

internal fun darker(c: Int, k: Float = 0.55f) = androidx.core.graphics.ColorUtils.blendARGB(c, 0xFF000000.toInt(), k)

/** The still layer for a new story: the chosen design, a faint big crest or two, and grain. */
internal fun storyBg(theme: String, hc: Int, ac: Int, marks: List<Pair<Bitmap?, Float>> = emptyList()): Pair<Bitmap, CardLook> {
    val bg = Bitmap.createBitmap(SW, SH, Bitmap.Config.ARGB_8888)
    val c = Canvas(bg)
    val look = background(c, theme, SW, SH, hc, ac)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    val wm = SW * 0.95f
    for ((bm, y0) in marks) bm?.let {
        p.alpha = if (lightTheme(theme)) 13 else 18
        c.drawBitmap(it, null, RectF(SW / 2f - wm / 2, y0, SW / 2f + wm / 2, y0 + wm), p)
    }
    grain(c, SW, SH, lightTheme(theme))
    return bg to look
}

/** A face in a ring: a soft glow of [ring], the ring, the photo clipped round. */
internal fun faceRing(c: Canvas, face: Bitmap?, cx: Float, cy: Float, r: Float, ring: Int, glow: Boolean = true) {
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    if (glow) { p.shader = RadialGradient(cx, cy, r * 1.8f, intArrayOf((ring and 0x00FFFFFF) or 0x77000000, ring and 0x00FFFFFF), null, Shader.TileMode.CLAMP); c.drawCircle(cx, cy, r * 1.8f, p); p.shader = null }
    p.shader = LinearGradient(cx, cy - r, cx, cy + r, brighten(ring), darker(ring, 0.3f), Shader.TileMode.CLAMP)
    p.setShadowLayer(r * 0.2f, 0f, r * 0.08f, 0x88000000.toInt()); c.drawCircle(cx, cy, r + r * 0.07f, p); p.clearShadowLayer(); p.shader = null
    p.color = 0xFF1A2128.toInt(); c.drawCircle(cx, cy, r, p)
    p.shader = RadialGradient(cx, cy - r * 0.4f, r * 1.2f, 0x33FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP); c.drawCircle(cx, cy, r, p); p.shader = null
    face?.let { f ->
        c.save(); c.clipPath(Path().apply { addCircle(cx, cy, r, Path.Direction.CW) })
        c.drawBitmap(f, null, RectF(cx - r, cy - r + r * 0.08f, cx + r, cy + r + r * 0.08f), p); c.restore()
    }
}

/** A white disc with a crest on it. */
internal fun crestDisc(c: Canvas, crest: Bitmap?, cx: Float, cy: Float, r: Float) {
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); setShadowLayer(r * 0.4f, 0f, r * 0.12f, 0x66000000) }
    c.drawCircle(cx, cy, r, p)
    crest?.let { c.drawBitmap(it, null, RectF(cx - r * 0.72f, cy - r * 0.72f, cx + r * 0.72f, cy + r * 0.72f), Paint(Paint.FILTER_BITMAP_FLAG)) }
}

/** A coloured rounded chip with a rating in it. */
internal fun ratingChip(c: Canvas, v: Double, cx: Float, top: Float, size: Float, tf: Typeface) {
    val s = fa(dec(v, 1))
    val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; typeface = tf }
    val w = tp.measureText(s) + size * 0.9f
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ratingColor(v); setShadowLayer(size * 0.4f, 0f, size * 0.15f, (ratingColor(v) and 0x00FFFFFF) or 0x88000000.toInt()) }
    c.drawRoundRect(RectF(cx - w / 2, top, cx + w / 2, top + size * 1.5f), size * 0.45f, size * 0.45f, p)
    text(c, s, cx, top + size * 0.2f, size, 0xFFFFFFFF.toInt(), tf, (w + 20).toInt())
}

internal fun num(v: Double, p: Float): String {
    val shown = v * easeOut(p)
    return fa(if (v % 1 != 0.0) dec(shown, if (v < 10) 2 else 1) else "${shown.roundToInt()}")
}

/** The app's mark and name in a glass capsule, centred at [y]. */
internal fun footer(c: Canvas, a: Art, look: CardLook, clk: Clock, y: Float = SH - 130f) {
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 44f; typeface = a.black }
    val name = L("گل‌لایو"); val nw = tp.measureText(name); val lr = 30f
    val bw = nw + 2 * lr + 20
    val lx = if (en) SW / 2f - bw / 2 + lr else SW / 2f + bw / 2 - lr
    c.brand(clk, SW / 2f, y) {
        capsule(c, RectF(SW / 2f - bw / 2 - 26f, y - lr - 18f, SW / 2f + bw / 2 + 26f, y + lr + 18f), look)
        a.logo?.let { p.color = 0xFFFFFFFF.toInt(); c.drawCircle(lx, y, lr + 4, p); c.drawBitmap(it, null, RectF(lx - lr, y - lr, lx + lr, y + lr), p) }
        text(c, name, if (en) SW / 2f - bw / 2 + 2 * lr + 20 + nw / 2 else SW / 2f - bw / 2 + nw / 2, y - 44f * 0.62f, 44f, look.ink, a.black, (nw + 20).toInt())
    }
}

/** The brand's capsule: dark glass with an edge lit from above (a white box with a black edge in the comic design). */
internal fun capsule(c: Canvas, cap: RectF, look: CardLook) {
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    val rad = cap.height() / 2
    if (look.comic) {
        p.color = 0xFF111111.toInt(); c.drawRoundRect(RectF(cap.left + 8f, cap.top + 8f, cap.right + 8f, cap.bottom + 8f), rad, rad, p)
        p.color = 0xFFFFFFFF.toInt(); c.drawRoundRect(cap, rad, rad, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 5f; p.color = 0xFF111111.toInt(); c.drawRoundRect(cap, rad, rad, p)
        return
    }
    // a light design's capsule is darkened a touch, a dark one's a little more
    p.color = if (android.graphics.Color.red(look.ink) < 0x80) 0x14000000 else 0x33000000; c.drawRoundRect(cap, rad, rad, p)
    p.style = Paint.Style.STROKE; p.strokeWidth = 2f
    p.shader = LinearGradient(0f, cap.top, 0f, cap.bottom, edgeTop(look.stroke), edgeBottom(look.stroke), Shader.TileMode.CLAMP)
    c.drawRoundRect(cap, rad, rad, p)
}

/** A small label over a story's title: the accent colour in a tinted capsule with a dot. */
internal fun kicker(c: Canvas, s: String, cx: Float, top: Float, look: CardLook, tf: Typeface) {
    val size = 30f
    val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; typeface = tf }
    val tw = tp.measureText(s); val w = tw + 70f + 30f
    val r = RectF(cx - w / 2, top, cx + w / 2, top + size * 2.1f)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    if (look.comic) {
        p.color = 0xFF111111.toInt(); c.drawRoundRect(RectF(r.left + 6f, r.top + 6f, r.right + 6f, r.bottom + 6f), 40f, 40f, p)
        p.color = look.accent; c.drawRoundRect(r, 40f, 40f, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 4f; p.color = 0xFF111111.toInt(); c.drawRoundRect(r, 40f, 40f, p); p.style = Paint.Style.FILL
    } else {
        p.color = ca(look.accent, 0x2A); c.drawRoundRect(r, 40f, 40f, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 2f; p.color = ca(look.accent, 0x77); c.drawRoundRect(r, 40f, 40f, p); p.style = Paint.Style.FILL
    }
    val ink = if (look.comic) 0xFFFFFFFF.toInt() else look.accent
    // the dot at the reading start
    val dx = if (en) r.left + 34f else r.right - 34f
    p.color = ink; c.drawCircle(dx, r.centerY(), 7f, p)
    p.color = ca(ink, 0x44); c.drawCircle(dx, r.centerY(), 14f, p)
    text(c, s, if (en) r.left + 56f + tw / 2 else r.right - 56f - tw / 2, top + size * 0.5f, size, ink, tf, (tw + 20).toInt())
}

/** The user's caption and sticker, common to every new story. */
private fun extras(c: Canvas, o: CardOptions, a: Art, look: CardLook, clk: Clock, captionY: Float, tint: Int = 0) {
    if (o.caption.isNotBlank()) c.enter(clk.at(3.0f), captionY, captionY + 80f) { captionLine(c, o.caption, captionY, look.accent, a.black, SW) }
    if (o.sticker.isNotEmpty()) stickerAt(c, o.sticker, if (en) SW - 160f else 160f, 190f, 118f, tint, a.black, clk)
}

/** The user's sticker in a top corner: it slaps on tilted, then sways a little while the video runs. */
internal fun stickerAt(c: Canvas, key: String, cx: Float, cy: Float, s: Float, tint: Int, tf: Typeface, clk: Clock) {
    val sway = if (clk.still) 0f else 4f * sin(clk.t * 2.6f).toFloat()
    c.pop(clk.at(3.2f, 0.5f), cx, cy) {
        c.save(); c.rotate((if (en) 12f else -12f) + sway, cx, cy)
        drawSticker(c, key, cx, cy, s, tint, tf)
        c.restore()
    }
}

/* ---------- a player ---------- */

private fun playerScene(s: Story.Player, o: CardOptions, a: Art): Scene {
    val p = s.p
    val team = p.o("primaryTeam")
    val teamId = team.i("teamId") ?: 0
    val crest = a.crests[teamId]
    val tone = teamTone(teamId, team, crest, 0xFF1E5BD8)
    val (bg, look) = storyBg(o.theme, tone, darker(tone, 0.35f), listOf(crest to SH * 0.3f))
    val ml = p.o("mainLeague")
    val all = ml.a("stats").objs().mapNotNull { st -> st.s("title")?.let { t -> st.d("value")?.let { t to it } } }
    val rating = all.firstOrNull { it.first == "Rating" }?.second
    val tiles = all.filter { it.first != "Rating" }.take(6).map { tr(it.first) to it.second }
    val recent = p.a("recentMatches").objs().filter { it.opt("playedInMatch") != false && (it.i("minutesPlayed") ?: 1) > 0 }.take(5)
    val info = p.a("playerInformation").objs()
    fun infoOf(title: String) = info.firstOrNull { it.s("title") == title }?.o("value")
    val age = infoOf("Age")?.i("numberValue")
    val country = infoOf("Country")?.s("fallback")
    val pos = tr(p.path("positionDescription.primaryPosition").s("label"))
    val captionY = SH - 255f
    return Scene(SW, SH, bg, tone, darker(tone, 0.35f)) { c, clk ->
        // the competition and season over everything
        c.enter(clk.at(0.1f), 60f, 200f) {
            val lid = ml.i("leagueId") ?: 0
            a.leagues[lid]?.let { lg -> crestDisc(c, lg, SW / 2f, 120f, 44f) }
            text(c, listOfNotNull(ml.s("leagueName")?.let { leagueName(lid, it) }, ml.s("season")?.let { fa(it) }).joinToString(" · "),
                SW / 2f, 182f, 30f, look.sub, a.bold, SW - 200)
        }
        // the face, big
        val fy = 520f; val fr = 220f
        c.pop(clk.at(0.35f, 0.7f), SW / 2f, fy) {
            faceRing(c, a.faces[s.id], SW / 2f, fy, fr, tone)
            crestDisc(c, crest, SW / 2f + (if (en) 1 else -1) * fr * 0.74f, fy + fr * 0.72f, 62f)
            rating?.let { ratingChip(c, it, SW / 2f - (if (en) 1 else -1) * fr * 0.78f, fy - fr * 0.92f, 46f, a.black) }
        }
        // the name and a line about him
        c.enter(clk.at(0.8f), 790f, 960f) {
            text(c, personName(p.s("name") ?: ""), SW / 2f, 790f, 78f, look.ink, a.black, SW - 100, 2)
        }
        c.enter(clk.at(1.0f), 900f, 970f) {
            val line = listOfNotNull(teamName(teamId, team.s("teamName") ?: "").takeIf { it.isNotEmpty() }, pos.takeIf { it.isNotEmpty() },
                age?.let { L("{0} سال", fa(it)) }, country?.let { personName(it) }).joinToString("  ·  ")
            pill(c, line, SW / 2f, 905f, 30f, look.ink, look.panel, a.bold, SW - 120, look.stroke)
        }
        // the season in six numbers
        val gy = 1000f; val tw = (SW - 112f - 2 * 20f) / 3; val th = 160f
        tiles.forEachIndexed { k, (label, v) ->
            val col = k % 3; val row = k / 3
            val left = if (en) 56f + col * (tw + 20f) else SW - 56f - (col + 1) * tw - col * 20f
            val top = gy + row * (th + 20f)
            c.enter(clk.at(1.3f + k * 0.12f), top, top + th) {
                glass(c, left, top, left + tw, top + th, look)
                text(c, num(v, clk.at(1.4f + k * 0.12f, 0.9f)), left + tw / 2, top + 26f, 64f, look.ink, a.black, tw.toInt())
                text(c, label, left + tw / 2, top + 104f, 26f, look.sub, a.bold, (tw - 20).toInt(), 1)
            }
        }
        // the last five games: opponent and rating
        if (recent.isNotEmpty()) {
            val ry = gy + 2 * (th + 20f) + 14f
            c.enter(clk.at(2.2f), ry, ry + 60f) { text(c, L("بازی‌های اخیر"), SW / 2f, ry, 30f, look.sub, a.bold, 600) }
            val cw = (SW - 112f) / 5
            recent.forEachIndexed { k, m ->
                val cx = if (en) 56f + cw * (k + 0.5f) else SW - 56f - cw * (k + 0.5f)
                c.pop(clk.at(2.4f + k * 0.1f, 0.45f), cx, ry + 120f) {
                    crestDisc(c, a.crests[m.i("opponentTeamId") ?: 0], cx, ry + 110f, 46f)
                    m.path("ratingProps").s("rating")?.toDoubleOrNull()?.let { ratingChip(c, it, cx, ry + 172f, 30f, a.black) }
                    // goals as little balls, assists as green discs
                    val marks = List(min(m.i("goals") ?: 0, 3)) { true } + List(min(m.i("assists") ?: 0, 2)) { false }
                    var mx = cx - (marks.size - 1) * 15f
                    val mp = Paint(Paint.ANTI_ALIAS_FLAG)
                    for (g in marks) {
                        if (g) { mp.color = 0xFFFFFFFF.toInt(); c.drawCircle(mx, ry + 236f, 12f, mp); mp.color = 0xFF111111.toInt(); c.drawCircle(mx, ry + 236f, 4.5f, mp) }
                        else { mp.color = 0xFF39D98A.toInt(); c.drawCircle(mx, ry + 236f, 12f, mp); text(c, if (en) "A" else "پ", mx, ry + 225f, 16f, 0xFF052914.toInt(), a.black, 30) }
                        mx += 30f
                    }
                }
            }
        }
        extras(c, o, a, look, clk, captionY, tone)
        footer(c, a, look, clk)
    }
}

/* ---------- a league table ---------- */

private fun tableScene(s: Story.Table, o: CardOptions, a: Art): Scene {
    val lg = a.leagues[s.league]
    val tone = crestColor(lg) ?: 0xFF3A2D8F.toInt()
    val (bg, look) = storyBg(o.theme, darker(tone, 0.2f), darker(tone, 0.5f), listOf(lg to SH * 0.36f))
    // the top ten, or the top nine and the marked team further down
    val markAt = s.rows.indexOfFirst { (it.i("id") ?: 0) == s.mark && s.mark > 0 }
    val rows = if (markAt >= 10) s.rows.take(9) + s.rows[markAt] else s.rows.take(10)
    val captionY = SH - 270f
    return Scene(SW, SH, bg, darker(tone, 0.2f), darker(tone, 0.5f)) { c, clk ->
        c.pop(clk.at(0.1f, 0.6f), SW / 2f, 150f) { crestDisc(c, lg, SW / 2f, 150f, 74f) }
        c.enter(clk.at(0.4f), 250f, 380f) {
            text(c, leagueName(s.league, s.name), SW / 2f, 250f, 58f, look.ink, a.black, SW - 120)
            text(c, L("جدول لیگ") + " · " + fa(persianDate(System.currentTimeMillis(), "d MMMM yyyy")), SW / 2f, 330f, 30f, look.sub, a.bold, SW - 120)
        }
        // column titles
        val top = 420f; val rh = 112f; val gap = 10f
        val colPts = 90f; val colGd = 210f; val colPl = 320f
        c.enter(clk.at(0.6f), top, top + 50f) {
            text(c, "#", xs(84f), top, 26f, look.sub, a.bold, 80)
            text(c, L("بازی"), xs(SW - colPl), top, 26f, look.sub, a.bold, 110)
            text(c, L("تفاضل"), xs(SW - colGd), top, 26f, look.sub, a.bold, 110)
            text(c, L("امتیاز"), xs(SW - colPts), top, 26f, look.sub, a.bold, 120)
        }
        rows.forEachIndexed { k, r ->
            val y = top + 56f + k * (rh + gap)
            val idx = s.rows.indexOf(r)
            val marked = (r.i("id") ?: 0) == s.mark && s.mark > 0
            c.enter(clk.at(0.8f + k * 0.11f, 0.5f), y, y + rh, 60f) {
                glass(c, 56f, y, SW - 56f, y + rh, look)
                val p = Paint(Paint.ANTI_ALIAS_FLAG)
                if (marked) { p.style = Paint.Style.STROKE; p.strokeWidth = 4f; p.color = look.accent; c.drawRoundRect(RectF(56f, y, SW - 56f, y + rh), 40f, 40f, p); p.style = Paint.Style.FILL }
                // the zone colour as a bar at the reading start
                s.legend.firstOrNull { lgd -> lgd.a("indices")?.let { arr -> (0 until arr.length()).any { arr.optInt(it) == idx } } == true }?.s("color")?.let { parseColor(it) }?.let { zc ->
                    p.color = zc.toArgb(); c.drawRoundRect(RectF(xs(70f) - 4f, y + 26f, xs(70f) + 4f, y + rh - 26f), 4f, 4f, p)
                }
                text(c, fa(r.i("idx") ?: (idx + 1)), xs(110f), y + 30f, 40f, look.ink, a.black, 90)
                crestDisc(c, a.crests[r.i("id") ?: 0], xs(200f), y + rh / 2, 36f)
                val nameW = SW - 260f - colPl - 60f
                val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 36f; typeface = a.black }
                val nm = teamName(r.i("id") ?: 0, r.s("name") ?: "")
                val shown = android.text.TextUtils.ellipsize(nm, tp, nameW, android.text.TextUtils.TruncateAt.END).toString()
                val w = tp.measureText(shown)
                text(c, shown, xs(250f + w / 2), y + 32f, 36f, if (marked) look.accent else look.ink, a.black, (w + 30).toInt())
                text(c, fa(r.i("played") ?: 0), xs(SW - colPl), y + 34f, 34f, look.sub, a.bold, 110)
                val diff = r.i("goalConDiff") ?: 0
                text(c, fa(if (diff > 0) "+$diff" else "$diff"), xs(SW - colGd), y + 34f, 34f, look.sub, a.bold, 110)
                text(c, fa(countUp(r.i("pts") ?: 0, clk.at(0.9f + k * 0.11f, 0.9f))), xs(SW - colPts), y + 26f, 46f, look.ink, a.black, 120)
            }
        }
        extras(c, o, a, look, clk, captionY, tone)
        footer(c, a, look, clk)
    }
}

/* ---------- a match's stats ---------- */

/** The team stats rows worth a story: FotMob's own order, possession apart (it has its ring), two values each. */
internal fun storyStatRows(m: MatchData, n: Int): List<JSONObject> =
    m.content.path("stats.Periods.All").a("stats").objs().flatMap { it.a("stats").objs() }
        .filter { it.s("type") != "title" && it.s("key") != "BallPossesion" && (it.a("stats")?.length() ?: 0) == 2 }
        .distinctBy { it.s("key") ?: it.s("title") }.take(n)

private fun statsScene(s: Story.Stats, o: CardOptions, a: Art): Scene {
    val m = s.m; val card = a.card!!
    val hc = card.hc; val ac = card.ac
    val (bg, look) = storyBg(o.theme, darker(hc, 0.25f), darker(ac, 0.45f))
    val rows = storyStatRows(m, 9)
    val poss = m.content.path("stats.Periods.All").a("stats").objs().flatMap { it.a("stats").objs() }.firstOrNull { it.s("key") == "BallPossesion" }
        ?.a("stats")?.let { (it.optString(0).filter(Char::isDigit).toIntOrNull() ?: 50) to (it.optString(1).filter(Char::isDigit).toIntOrNull() ?: 50) }
    val captionY = SH - 270f
    return Scene(SW, SH, bg, hc, ac) { c, clk ->
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        // the match: crests, score, names (home at the reading start)
        c.enter(clk.at(0.05f), 30f, 150f) {
            kicker(c, L("آمار بازی"), SW / 2f, 34f, look, a.black)
            text(c, leagueName(m.leagueId, m.general.s("leagueName") ?: ""), SW / 2f, 112f, 26f, look.sub, a.bold, SW - 140)
        }
        c.pop(clk.at(0.2f, 0.6f), xs(230f), 250f) { crestDisc(c, card.home, xs(230f), 250f, 82f) }
        c.pop(clk.at(0.3f, 0.6f), xs(SW - 230f), 250f) { crestDisc(c, card.away, xs(SW - 230f), 250f, 82f) }
        // each side's goals on its own side, like the match card
        if (m.started) c.enter(clk.at(0.4f), 180f, 320f) {
            text(c, fa(m.home.i("score") ?: 0), xs(SW / 2f - 80f), 185f, 96f, look.ink, a.black, 160)
            text(c, "-", SW / 2f, 195f, 70f, look.sub, a.black, 80)
            text(c, fa(m.away.i("score") ?: 0), xs(SW / 2f + 80f), 185f, 96f, look.ink, a.black, 160)
        }
        c.enter(clk.at(0.5f), 350f, 420f) {
            text(c, teamName(m.homeId, m.homeName), xs(230f), 352f, 34f, look.ink, a.black, 360)
            text(c, teamName(m.awayId, m.awayName), xs(SW - 230f), 352f, 34f, look.ink, a.black, 360)
        }
        // possession as a ring
        poss?.let { (h, aw) ->
            val cx = SW / 2f; val cy = 600f; val r = 130f; val k = easeOut(clk.at(0.7f, 1.0f))
            c.enter(clk.at(0.6f), cy - r, cy + r) {
                p.style = Paint.Style.STROKE; p.strokeWidth = 50f; p.strokeCap = Paint.Cap.BUTT
                val rect = RectF(cx - r, cy - r, cx + r, cy + r)
                val hs = 360f * h / (h + aw).coerceAtLeast(1) * k
                // home sweeps from the top towards the reading start, away the other way; each glows in its colour, a hairline gap between
                p.color = track(look); c.drawArc(rect, 0f, 360f, false, p)
                p.color = brighten(hc); p.setShadowLayer(26f, 0f, 0f, ca(brighten(hc), 0xAA)); c.drawArc(rect, -90f, if (en) -(hs - 2f).coerceAtLeast(0f) else (hs - 2f).coerceAtLeast(0f), false, p)
                p.color = brighten(ac); p.setShadowLayer(26f, 0f, 0f, ca(brighten(ac), 0xAA)); c.drawArc(rect, -90f, if (en) (360f * k - hs - 2f).coerceAtLeast(0f) else -(360f * k - hs - 2f).coerceAtLeast(0f), false, p)
                p.clearShadowLayer()
                p.strokeWidth = 2f; p.color = if (darkInk(look)) 0x1A000000 else 0x33FFFFFF; c.drawCircle(cx, cy, r - 34f, p); c.drawCircle(cx, cy, r + 34f, p)
                p.style = Paint.Style.FILL
                text(c, L("مالکیت"), cx, cy - 26f, 30f, look.sub, a.bold, 220)
                text(c, fa("${(h * k).roundToInt()}٪"), xs(SW / 2f - 250f), cy - 40f, 64f, look.ink, a.black, 220)
                text(c, fa("${(aw * k).roundToInt()}٪"), xs(SW / 2f + 250f), cy - 40f, 64f, look.ink, a.black, 220)
            }
        }
        // the key numbers, a row each
        val top = 820f; val rh = 96f
        rows.forEachIndexed { i, st ->
            val y = top + i * rh
            val v = st.a("stats")
            val hv = v?.optString(0).orEmpty(); val av = v?.optString(1).orEmpty()
            val raw = st.a("rawStats").objs()
            val hn = raw.getOrNull(0).d("value") ?: hv.substringBefore(' ').replace("%", "").toDoubleOrNull() ?: 0.0
            val an = raw.getOrNull(1).d("value") ?: av.substringBefore(' ').replace("%", "").toDoubleOrNull() ?: 0.0
            c.enter(clk.at(1.0f + i * 0.12f, 0.5f), y, y + rh, 50f) {
                text(c, tr(st.s("title")), SW / 2f, y - 4f, 30f, look.sub, a.bold, 560)
                text(c, fa(hv.substringBefore(' ')), xs(110f), y - 6f, 46f, if (hn > an) look.ink else look.sub, a.black, 200)
                text(c, fa(av.substringBefore(' ')), xs(SW - 110f), y - 6f, 46f, if (an > hn) look.ink else look.sub, a.black, 200)
                // one bar split by share: home from the reading start in its colour, away the rest in its own, the leader brighter
                val tot = (hn + an).takeIf { it > 0 } ?: 1.0
                val g = easeOut(clk.at(1.1f + i * 0.12f, 0.7f)); val half = 330f; val by = y + 56f; val bh = 16f
                val l = SW / 2f - half; val r = SW / 2f + half
                p.color = track(look); c.drawRoundRect(RectF(l, by, r, by + bh), bh / 2, bh / 2, p)
                if (hn + an > 0) {
                    val split = (2 * half * (hn / tot)).toFloat()
                    val hw = split * g; val aw = (2 * half - split) * g
                    if (hn > an) p.setShadowLayer(14f, 0f, 0f, ca(brighten(hc), 0xCC)) else p.clearShadowLayer()
                    p.color = if (hn >= an) brighten(hc) else (brighten(hc) and 0x00FFFFFF) or 0x8C000000.toInt()
                    if (en) c.drawRoundRect(RectF(l, by, l + hw, by + bh), bh / 2, bh / 2, p) else c.drawRoundRect(RectF(r - hw, by, r, by + bh), bh / 2, bh / 2, p)
                    if (an > hn) p.setShadowLayer(14f, 0f, 0f, ca(brighten(ac), 0xCC)) else p.clearShadowLayer()
                    p.color = if (an >= hn) brighten(ac) else (brighten(ac) and 0x00FFFFFF) or 0x8C000000.toInt()
                    if (en) c.drawRoundRect(RectF(r - aw, by, r, by + bh), bh / 2, bh / 2, p) else c.drawRoundRect(RectF(l, by, l + aw, by + bh), bh / 2, bh / 2, p)
                    p.clearShadowLayer()
                }
            }
        }
        extras(c, o, a, look, clk, captionY, hc)
        footer(c, a, look, clk)
    }
}

/* ---------- the analyst ---------- */

private fun analysisScene(s: Story.Analysis, o: CardOptions, a: Art): Scene {
    val m = s.m; val card = a.card!!
    val hc = card.hc; val ac = card.ac
    val (bg, look) = storyBg(o.theme, darker(hc, 0.25f), darker(ac, 0.45f))
    val ratings = runCatching { rateMatch(m) }.getOrDefault(emptyMap())
    val an = analyse(m, ratings)
    val tl = runCatching { winTimeline(m) }.getOrDefault(emptyList())
    val goals = m.events.filter { it.s("type") == "Goal" && !it.b("isPenaltyShootoutEvent") }
    val hcol = brighten(hc); val acol = brighten(ac)
    val captionY = SH - 268f
    return Scene(SW, SH, bg, hc, ac) { c, clk ->
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        c.enter(clk.at(0.05f), 50f, 180f) {
            kicker(c, L("تحلیلگر گل‌لایو"), SW / 2f, 52f, look, a.black)
            text(c, leagueName(m.leagueId, m.general.s("leagueName") ?: ""), SW / 2f, 136f, 28f, look.sub, a.bold, SW - 160)
        }
        // the match: crests at the sides, the real score between them
        c.pop(clk.at(0.2f, 0.6f), xs(200f), 285f) { crestDisc(c, card.home, xs(200f), 285f, 70f) }
        c.pop(clk.at(0.3f, 0.6f), xs(SW - 200f), 285f) { crestDisc(c, card.away, xs(SW - 200f), 285f, 70f) }
        c.enter(clk.at(0.35f), 200f, 400f) {
            text(c, teamName(m.homeId, m.homeName), xs(200f), 368f, 30f, look.ink, a.black, 320)
            text(c, teamName(m.awayId, m.awayName), xs(SW - 200f), 368f, 30f, look.ink, a.black, 320)
            text(c, L("نتیجه"), SW / 2f, 214f, 26f, look.sub, a.bold, 200)
            text(c, fa(m.home.i("score") ?: 0), xs(SW / 2f - 64f), 244f, 84f, look.ink, a.black, 130)
            text(c, "-", SW / 2f, 254f, 60f, look.sub, a.black, 60)
            text(c, fa(m.away.i("score") ?: 0), xs(SW / 2f + 64f), 244f, 84f, look.ink, a.black, 130)
        }
        // the fair result: the score the chances deserved, and each outcome's chance
        val top = 440f; val ph = 330f
        c.enter(clk.at(0.6f), top, top + ph) {
            glass(c, 56f, top, SW - 56f, top + ph, look)
            text(c, L("نتیجه‌ی منصفانه"), SW / 2f, top + 26f, 34f, look.accent, a.black, 600)
            val k = easeOut(clk.at(0.8f, 0.8f))
            bigText(c, fa(an.fair.first), xs(SW / 2f - 96f), top + 66f, 124f, look.ink, a.black, 1f)
            text(c, "-", SW / 2f, top + 92f, 80f, look.sub, a.black, 80)
            bigText(c, fa(an.fair.second), xs(SW / 2f + 96f), top + 66f, 124f, look.ink, a.black, 1f)
            text(c, "xG " + fa(dec(an.hXg * k, 2)), xs(190f), top + 118f, 38f, readable(hc, look), a.black, 280)
            text(c, "xG " + fa(dec(an.aXg * k, 2)), xs(SW - 190f), top + 118f, 38f, readable(ac, look), a.black, 280)
            val l = 100f; val r = SW - 100f; val by = top + 228f; val bh = 28f; val w = r - l
            p.color = track(look); c.drawRoundRect(RectF(l, by, r, by + bh), bh / 2, bh / 2, p)
            var x0 = 0f
            for ((pp, col) in listOf(an.pHome to hcol, an.pDraw to 0xFF8A94A0.toInt(), an.pAway to acol)) {
                val pw = (w * pp * k).toFloat()
                p.color = col
                if (pw > 8f) if (en) c.drawRoundRect(RectF(l + x0, by, l + x0 + pw - 5f, by + bh), bh / 2, bh / 2, p) else c.drawRoundRect(RectF(r - x0 - pw + 5f, by, r - x0, by + bh), bh / 2, bh / 2, p)
                x0 += pw
            }
            text(c, L("برد {0}", "") + fa((an.pHome * 100 * k).roundToInt()) + "٪", xs(200f), by + 44f, 30f, look.ink, a.bold, 320)
            text(c, L("مساوی") + " " + fa((an.pDraw * 100 * k).roundToInt()) + "٪", SW / 2f, by + 44f, 30f, look.sub, a.bold, 300)
            text(c, L("برد {0}", "") + fa((an.pAway * 100 * k).roundToInt()) + "٪", xs(SW - 200f), by + 44f, 30f, look.ink, a.bold, 320)
        }
        // the win chances through the match, drawn left to right as the video runs
        var fy = top + ph + 30f
        if (tl.size > 5) {
            val ct = fy; val chH = 330f
            c.enter(clk.at(1.0f), ct, ct + chH) {
                glass(c, 56f, ct, SW - 56f, ct + chH, look)
                text(c, L("شانس برد در طول بازی"), SW / 2f, ct + 22f, 30f, look.ink, a.black, 700)
                val l = 100f; val r = SW - 100f; val t = ct + 82f; val b = ct + 270f
                val n = tl.size - 1
                val prog = easeOut(clk.at(1.2f, 1.8f))
                c.save()
                c.clipPath(Path().apply { addRoundRect(RectF(l, t, r, b), 18f, 18f, Path.Direction.CW) })
                c.clipRect(l, t, l + (r - l) * prog, b)
                fun band(lo: (WinPoint) -> Double, hi: (WinPoint) -> Double, col: Int) {
                    val path = Path()
                    for (i in 0..n) { val x = l + (r - l) * i / n; val y = t + (b - t) * hi(tl[i]).toFloat(); if (i == 0) path.moveTo(x, y) else path.lineTo(x, y) }
                    for (i in n downTo 0) path.lineTo(l + (r - l) * i / n, t + (b - t) * lo(tl[i]).toFloat())
                    path.close(); p.color = col; c.drawPath(path, p)
                }
                band({ 0.0 }, { it.h }, hcol)
                band({ it.h }, { it.h + it.d }, 0xFF5E6672.toInt())
                band({ it.h + it.d }, { 1.0 }, acol)
                // half time
                val hx = l + (r - l) * min(45, n) / n
                p.color = 0x66000000; var dy = t; while (dy < b) { c.drawRect(hx - 1.5f, dy, hx + 1.5f, dy + 10f, p); dy += 20f }
                c.restore()
                // the goals as balls on the edge of their side
                for (g in goals) {
                    val gm = (g.i("time") ?: 0).coerceAtMost(n)
                    val gp = gm.toFloat() / n
                    if (gp > prog) continue
                    val gx = l + (r - l) * gp; val home = g.b("isHome")
                    c.pop(((prog - gp) * 6f).coerceIn(0f, 1f), gx, if (home) t else b) { ball(c, gx, if (home) t else b, 17f, look) }
                }
                text(c, if (en) "0'" else "۰′", xs(if (en) 112f else SW - 112f), b + 14f, 22f, look.sub, a.bold, 60)
                text(c, if (en) "90'" else "۹۰′", xs(if (en) SW - 112f else 112f), b + 14f, 22f, look.sub, a.bold, 80)
            }
            fy += chH + 30f
        }
        // the strongest findings, each with its icon
        val ch = 210f
        val count = ((captionY - 20f - fy) / (ch + 20f)).toInt().coerceIn(1, 3)
        an.insights.take(count).forEachIndexed { i, x ->
            val y = fy + i * (ch + 20f)
            c.enter(clk.at(1.8f + i * 0.35f, 0.6f), y, y + ch, 60f) {
                glass(c, 56f, y, SW - 56f, y + ch, look)
                val col = when (x.home) { true -> hcol; false -> acol; null -> look.accent }
                c.pop(clk.at(2.0f + i * 0.35f, 0.5f), xs(150f), y + ch / 2) { glyphDisc(c, x.icon, xs(150f), y + ch / 2, 58f, col) }
                val bw = SW - 240f - 90f
                val left = if (en) 240f else 90f
                startText(c, x.title, left, y + 30f, 38f, look.ink, a.black, bw.toInt())
                startText(c, x.text, left, y + 92f, 28f, look.sub, a.bold, bw.toInt(), 2)
            }
        }
        extras(c, o, a, look, clk, captionY, hc)
        footer(c, a, look, clk)
    }
}

/* ---------- the Ballon d'Or race ---------- */

/** A golden ball: a lit sphere with darker panels. */
private fun goldBall(c: Canvas, cx: Float, cy: Float, r: Float) {
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.shader = RadialGradient(cx, cy, r * 2.2f, intArrayOf(0x66F5C542, 0x00F5C542), null, Shader.TileMode.CLAMP); c.drawCircle(cx, cy, r * 2.2f, p)
    p.shader = RadialGradient(cx - r * 0.35f, cy - r * 0.4f, r * 1.4f, intArrayOf(0xFFFFF4C2.toInt(), 0xFFF5C542.toInt(), 0xFF9A6A0A.toInt(), 0xFF4A3204.toInt()),
        floatArrayOf(0f, 0.35f, 0.8f, 1f), Shader.TileMode.CLAMP)
    p.setShadowLayer(r * 0.3f, 0f, r * 0.12f, 0x99000000.toInt()); c.drawCircle(cx, cy, r, p); p.clearShadowLayer(); p.shader = null
    c.save(); c.clipPath(Path().apply { addCircle(cx, cy, r, Path.Direction.CW) })
    p.color = 0x55613F02
    fun pent(x: Float, y: Float, rr: Float) {
        val path = Path()
        for (i in 0 until 5) { val an = Math.toRadians(-90.0 + i * 72); val px = x + rr * cos(an).toFloat(); val py = y + rr * sin(an).toFloat(); if (i == 0) path.moveTo(px, py) else path.lineTo(px, py) }
        path.close(); c.drawPath(path, p)
    }
    pent(cx, cy, r * 0.36f)
    for (i in 0 until 5) { val an = Math.toRadians(-90.0 + i * 72); pent(cx + r * 0.92f * cos(an).toFloat(), cy + r * 0.92f * sin(an).toFloat(), r * 0.3f) }
    c.restore()
    p.shader = RadialGradient(cx - r * 0.4f, cy - r * 0.5f, r * 0.5f, 0xCCFFFFFF.toInt(), 0x00FFFFFF, Shader.TileMode.CLAMP); c.drawCircle(cx - r * 0.4f, cy - r * 0.5f, r * 0.5f, p)
}

private fun ballonScene(s: Story.Ballon, o: CardOptions, a: Art): Scene {
    val w = s.men.firstOrNull()
    val (bg, look) = storyBg(o.theme, 0xFF6B4A06.toInt(), 0xFF2E2206.toInt())
    val gold = 0xFFF5C542.toInt()
    val captionY = SH - 270f
    return Scene(SW, SH, bg, 0xFF6B4A06.toInt(), 0xFF2E2206.toInt()) { c, clk ->
        c.pop(clk.at(0.1f, 0.7f), SW / 2f, 210f) { goldBall(c, SW / 2f, 210f, 105f) }
        c.enter(clk.at(0.5f), 350f, 480f) {
            text(c, L("توپ طلای {0}", fa(s.year)), SW / 2f, 350f, 66f, look.ink, a.black, SW - 120)
            pill(c, if (s.official) L("برنده رسمی") else L("پیش‌بینی گل‌لایو · تا این لحظه"), SW / 2f, 448f, 28f, look.accent, look.panel, a.bold, SW - 160, look.stroke)
        }
        if (w == null) return@Scene
        // the leader
        val fy = 740f; val fr = 170f
        c.pop(clk.at(0.9f, 0.7f), SW / 2f, fy) {
            faceRing(c, a.faces[w.i("id") ?: 0], SW / 2f, fy, fr, gold)
            crestDisc(c, a.crests[w.i("team") ?: 0], SW / 2f + (if (en) 1 else -1) * fr * 0.74f, fy + fr * 0.72f, 52f)
            p1Badge(c, SW / 2f - (if (en) 1 else -1) * fr * 0.8f, fy - fr * 0.8f, a.black)
        }
        c.enter(clk.at(1.3f), 950f, 1100f) {
            text(c, personName(w.s("name") ?: ""), SW / 2f, 950f, 70f, look.ink, a.black, SW - 120)
            text(c, teamName(w.i("team") ?: 0, w.s("teamName") ?: ""), SW / 2f, 1045f, 32f, look.sub, a.bold, SW - 160)
        }
        // his season in three numbers
        val tw = (SW - 112f - 40f) / 3; val ty = 1120f; val th = 150f
        listOf(L("نمره فصل") to (w.d("score") ?: 0.0), L("گل") to (w.i("goals") ?: 0).toDouble(), L("پاس گل") to (w.i("assists") ?: 0).toDouble()).forEachIndexed { k, (label, v) ->
            val left = if (en) 56f + k * (tw + 20f) else SW - 56f - (k + 1) * tw - k * 20f
            c.enter(clk.at(1.6f + k * 0.12f), ty, ty + th) {
                glass(c, left, ty, left + tw, ty + th, look)
                text(c, num(v, clk.at(1.7f + k * 0.12f, 0.9f)), left + tw / 2, ty + 18f, 58f, look.ink, a.black, tw.toInt())
                text(c, label, left + tw / 2, ty + 100f, 26f, look.sub, a.bold, (tw - 20).toInt())
            }
        }
        // the chasers
        val rh = 104f
        s.men.drop(1).take(4).forEachIndexed { k, m ->
            val y = ty + th + 34f + k * (rh + 12f)
            c.enter(clk.at(2.3f + k * 0.14f), y, y + rh, 60f) {
                glass(c, 56f, y, SW - 56f, y + rh, look)
                val p = Paint(Paint.ANTI_ALIAS_FLAG)
                val medal = when (k) { 0 -> 0xFFC9D1D9.toInt(); 1 -> 0xFFD08C4A.toInt(); else -> (gold and 0x00FFFFFF) or 0x33000000 }
                p.color = medal; c.drawCircle(xs(108f), y + rh / 2, 30f, p)
                text(c, fa(k + 2), xs(108f), y + rh / 2 - 21f, 30f, if (k < 2) 0xFF231A00.toInt() else gold, a.black, 60)
                faceRing(c, a.faces[m.i("id") ?: 0], xs(196f), y + rh / 2, 38f, gold, glow = false)
                val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 36f; typeface = a.black }
                val nm = personName(m.s("name") ?: ""); val nw = tp.measureText(nm).coerceAtMost(560f)
                text(c, nm, xs(256f + nw / 2), y + 16f, 36f, look.ink, a.black, (nw + 20).toInt())
                val tm = teamName(m.i("team") ?: 0, m.s("teamName") ?: "")
                val tw2 = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 24f; typeface = a.bold }.measureText(tm).coerceAtMost(560f)
                text(c, tm, xs(256f + tw2 / 2), y + 62f, 24f, look.sub, a.bold, (tw2 + 20).toInt())
                m.d("score")?.let { ratingChip(c, it, xs(SW - 130f), y + rh / 2 - 30f, 40f, a.black) }
            }
        }
        extras(c, o, a, look, clk, captionY, 0xFFF5C542.toInt())
        footer(c, a, look, clk)
    }
}

private fun p1Badge(c: Canvas, cx: Float, cy: Float, tf: Typeface) {
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF5C542.toInt(); setShadowLayer(16f, 0f, 6f, 0x88000000.toInt()) }
    c.drawCircle(cx, cy, 46f, p)
    text(c, fa(1), cx, cy - 32f, 46f, 0xFF231A00.toInt(), tf, 80)
}

/* ---------- two players ---------- */

private fun duelScene(s: Story.Duel, o: CardOptions, a: Art): Scene {
    val ta = s.a.o("primaryTeam"); val tb = s.b.o("primaryTeam")
    val ida = ta.i("teamId") ?: 0; val idb = tb.i("teamId") ?: 0
    val ca = teamTone(ida, ta, a.crests[ida], 0xFF1E5BD8); var cb = teamTone(idb, tb, a.crests[idb], 0xFFD62839)
    if (ida == idb || colorClose(ca, cb)) cb = 0xFFD62839.toInt().takeIf { !colorClose(ca, it) } ?: 0xFF1E5BD8.toInt()
    val (bg, look) = storyBg(o.theme, ca, cb)
    // numbers both have this season, in their league
    fun season(d: JSONObject) = d.o("mainLeague").a("stats").objs().mapNotNull { st -> st.s("title")?.let { t -> st.d("value")?.let { t to it } } }.toMap()
    val sa = season(s.a); val sb = season(s.b)
    val keys = sa.keys.filter { it in sb }.take(7)
    val left = if (en) SW * 0.26f else SW * 0.74f; val right = SW - left
    val captionY = SH - 270f
    return Scene(SW, SH, bg, ca, cb) { c, clk ->
        c.enter(clk.at(0.1f), 80f, 150f) {
            val la = s.a.o("mainLeague").s("leagueName"); val lb = s.b.o("mainLeague").s("leagueName")
            text(c, L("مقایسه بازیکنان") + if (la != null && la == lb) " · " + leagueName(0, la) else "", SW / 2f, 90f, 32f, look.sub, a.bold, SW - 160)
        }
        val fy = 360f; val fr = 150f
        for ((k, d) in listOf(s.a, s.b).withIndex()) {
            val x = if (k == 0) left else right
            val tone = if (k == 0) ca else cb
            val tid = if (k == 0) ida else idb
            c.pop(clk.at(0.3f + k * 0.2f, 0.7f), x, fy) {
                faceRing(c, a.faces[d.i("id") ?: 0], x, fy, fr, tone)
                crestDisc(c, a.crests[tid], x + (if (en) 1 else -1) * fr * 0.72f, fy + fr * 0.72f, 44f)
            }
            c.enter(clk.at(0.8f + k * 0.15f), 560f, 700f) {
                text(c, personName(d.s("name") ?: ""), x, 560f, 50f, look.ink, a.black, (SW * 0.46f).toInt(), 2)
                text(c, teamName(tid, d.o("primaryTeam").s("teamName") ?: ""), x, 690f, 28f, look.sub, a.bold, (SW * 0.44f).toInt())
            }
        }
        c.pop(clk.at(1.1f, 0.5f), SW / 2f, fy) {
            text(c, "VS", SW / 2f, fy - 40f, 72f, look.ink, Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC), 220)
        }
        val rh = 116f; val top0 = 780f
        keys.forEachIndexed { k, key ->
            val y = top0 + k * (rh + 14f)
            val va = sa[key] ?: 0.0; val vb = sb[key] ?: 0.0
            val pr = clk.at(1.5f + k * 0.15f, 0.9f)
            c.enter(clk.at(1.4f + k * 0.15f), y, y + rh) {
                glass(c, 56f, y, SW - 56f, y + rh, look)
                text(c, num(va, pr), left, y + 16f, 46f, if (va >= vb) look.ink else look.sub, a.black, 260)
                text(c, num(vb, pr), right, y + 16f, 46f, if (vb >= va) look.ink else look.sub, a.black, 260)
                text(c, tr(key), SW / 2f, y + 22f, 26f, look.sub, a.bold, 340)
                // each side's share as a bar from the middle outwards
                val tot = (va + vb).takeIf { it > 0 } ?: 1.0
                val half = (SW - 240f) / 2; val by = y + rh - 30f; val bh = 10f
                val p = Paint(Paint.ANTI_ALIAS_FLAG)
                p.color = look.stroke; c.drawRoundRect(RectF(120f, by, SW - 120f, by + bh), bh / 2, bh / 2, p)
                val wa = (half * va / tot * 2 * easeOut(pr)).toFloat().coerceAtMost(half); val wb = (half * vb / tot * 2 * easeOut(pr)).toFloat().coerceAtMost(half)
                val aRight = !en
                p.color = brighten(ca); if (aRight) c.drawRoundRect(RectF(SW / 2f + 4, by, SW / 2f + 4 + wa, by + bh), bh / 2, bh / 2, p) else c.drawRoundRect(RectF(SW / 2f - 4 - wa, by, SW / 2f - 4, by + bh), bh / 2, bh / 2, p)
                p.color = brighten(cb); if (aRight) c.drawRoundRect(RectF(SW / 2f - 4 - wb, by, SW / 2f - 4, by + bh), bh / 2, bh / 2, p) else c.drawRoundRect(RectF(SW / 2f + 4, by, SW / 2f + 4 + wb, by + bh), bh / 2, bh / 2, p)
            }
        }
        extras(c, o, a, look, clk, captionY, ca)
        footer(c, a, look, clk)
    }
}

private fun colorClose(x: Int, y: Int): Boolean {
    val dr = android.graphics.Color.red(x) - android.graphics.Color.red(y)
    val dg = android.graphics.Color.green(x) - android.graphics.Color.green(y)
    val db = android.graphics.Color.blue(x) - android.graphics.Color.blue(y)
    return dr * dr + dg * dg + db * db < 60 * 60
}

/* ---------- the studio ---------- */

private val MUSIC = listOf("" to "بدون صدا", "energy" to "پرانرژی", "epic" to "حماسی", "chill" to "آرام")
private fun musicRes(k: String) = when (k) { "energy" -> R.raw.story_energy; "epic" -> R.raw.story_epic; "chill" -> R.raw.story_chill; else -> null }

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun StorySheet(story: Story, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val accent = LocalAccent.current
    val match = story as? Story.Match
    val reel = story is Story.Reel
    var o by remember { mutableStateOf(if (match != null) loadOptions() else CardOptions(theme = story.theme)) }
    var video by remember { mutableStateOf(reel || Store.prefs().getBoolean("story.video", false)) }
    var reelSound by remember { mutableStateOf(Store.prefs().getString("reel.sound", "full") ?: "full") }
    var music by remember { mutableStateOf(Store.prefs().getString("story.music", "energy") ?: "energy") }
    var art by remember { mutableStateOf<Art?>(null) }
    LaunchedEffect(story) { art = withContext(Dispatchers.IO) { story.load(ctx) } }
    // a video is always story size
    val shape = if (video) o.copy(format = CardFormat.STORY) else o
    var scene by remember { mutableStateOf<Scene?>(null) }
    LaunchedEffect(shape, art) {
        val a = art ?: return@LaunchedEffect
        scene = withContext(Dispatchers.Default) { story.scene(shape, a) }
        if (match != null) saveOptions(o)
    }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(scene, video) {
        val sc = scene ?: return@LaunchedEffect
        if (!video) { preview = withContext(Dispatchers.Default) { sc.still(0.42f) }; return@LaunchedEffect }
        // the animation, played over and over in the preview (small, so it keeps up)
        val k = 0.3f
        val bufs = Array(2) { Bitmap.createBitmap((sc.W * k).toInt(), (sc.H * k).toInt(), Bitmap.Config.ARGB_8888) }
        var n = 0
        val start = System.nanoTime()
        while (isActive) {
            val t = ((System.nanoTime() - start) / 1e9f) % (sc.seconds + 0.8f)
            val b = bufs[n % 2]; n++
            withContext(Dispatchers.Default) {
                b.eraseColor(0xFF000000.toInt())
                val c = Canvas(b); c.scale(k, k); sc.frame(c, t.coerceAtMost(sc.seconds))
            }
            preview = b
            withFrameNanos { }
        }
    }
    LaunchedEffect(Unit) { app.goallive.live.data.Telemetry.event("story:open") }
    var making by remember { mutableStateOf<Float?>(null) }
    val alive = remember { AtomicBoolean(true) }
    DisposableEffect(Unit) { onDispose { alive.set(false) } }

    /** Makes the picture or the video, then hands the file over. */
    fun produce(then: (File, String) -> Unit) {
        val sc = scene ?: return
        if (making != null) return
        making = 0f
        scope.launch {
            val out = withContext(Dispatchers.Default) {
                runCatching {
                    val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
                    if (!video) {
                        val f = File(dir, story.file + ".png")
                        val bmp = sc.still(1f)
                        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                        f to "image/png"
                    } else {
                        val f = File(dir, story.file + ".mp4")
                        // the match reel gets its own sound: music, the crowd and the whistle at each moment
                        val rs = sc as? ReelScene
                        val audio = rs?.takeIf { reelSound.isNotEmpty() }?.let { StoryAudio.make(ctx, it.seconds, if (reelSound == "full") R.raw.reel_music else null, 0.5f, true, it.fx) }
                        val ok = StoryVideo.make(ctx, f, 720, 1280, sc.seconds, if (rs != null) null else musicRes(music), { making = it }, { alive.get() },
                            bitrate = if (rs != null) 4_000_000 else 7_000_000, audio = audio) { c, t -> sc.frame(c, t) }
                        if (ok) f to "video/mp4" else null
                    }
                }.getOrNull()
            }
            making = null
            if (out != null) app.goallive.live.data.Telemetry.event(if (!video) "story:image" else if (sc is ReelScene) "story:reel" else "story:video")
            if (out != null) then(out.first, out.second)
            else if (alive.get()) Toast.makeText(ctx, L("ساختن ویدیو روی این گوشی ممکن نشد؛ عکس را امتحان کنید"), Toast.LENGTH_LONG).show()
        }
    }

    // the story blown up and blurred, behind the top of the studio
    var ambient by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(scene) {
        val sc = scene ?: return@LaunchedEffect
        ambient = withContext(Dispatchers.Default) { runCatching { softBlur(sc.still(0.1f)) }.getOrNull() }
    }
    // every design drawn small with this very story, for the picker
    val themed = story !is Story.Lineup && !reel
    var thumbs by remember { mutableStateOf<Map<String, Bitmap>>(emptyMap()) }
    val thumbKey = shape.copy(theme = "", caption = "", sticker = "")
    LaunchedEffect(thumbKey, art) {
        val a = art ?: return@LaunchedEffect
        if (!themed) return@LaunchedEffect
        for (t in CARD_THEMES) {
            val b = withContext(Dispatchers.Default) { runCatching { val sc = story.scene(thumbKey.copy(theme = t), a); sc.still(0.16f).also { sc.bg.recycle() } }.getOrNull() } ?: continue
            thumbs = thumbs + (t to b)
        }
    }
    // the stickers as they will look
    val tint = scene?.hc?.takeIf { it != 0 } ?: accent.toArgb()
    var stickerArt by remember { mutableStateOf<Map<String, Bitmap>>(emptyMap()) }
    LaunchedEffect(art, tint) {
        val tf = art?.black ?: return@LaunchedEffect
        stickerArt = withContext(Dispatchers.Default) {
            STICKERS.filter { it.isNotEmpty() }.associateWith { k ->
                Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888).also { drawSticker(Canvas(it), k, 80f, 80f, 52f, tint, tf) }
            }
        }
    }

    val state = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onClose, sheetState = state, containerColor = C.bg,
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp), dragHandle = null) {
        Box(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            ambient?.let { amb ->
                Image(amb.asImageBitmap(), null, Modifier.fillMaxWidth().height(660.dp).let { if (Build.VERSION.SDK_INT >= 31) it.blur(30.dp) else it }.alpha(0.8f),
                    contentScale = ContentScale.Crop, filterQuality = FilterQuality.High)
            }
            Box(Modifier.fillMaxWidth().height(660.dp).background(Brush.verticalGradient(0f to C.bg.copy(alpha = 0.2f), 0.55f to C.bg.copy(alpha = 0.55f), 1f to C.bg)))
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 18.dp)) {
                Box(Modifier.align(Alignment.CenterHorizontally).padding(top = 10.dp, bottom = 4.dp).width(44.dp).height(5.dp).clip(RoundedCornerShape(5.dp)).background(C.ink.copy(alpha = 0.3f)))
                // title and close
                Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        T(if (reel) L("بازی در یک دقیقه") else L("استودیو استوری"), 22, FontWeight.Black)
                        T(if (reel) scene?.let { L("ویدیوی {0} ثانیه‌ای با گل‌ها، آمار و بهترین بازیکن", fa(it.seconds.roundToInt())) } ?: "" else L("طرح، استیکر و نوشته‌ات را انتخاب کن و بفرست"),
                            12, color = C.mute, modifier = Modifier.padding(top = 2.dp))
                    }
                    IconBtn(Ic.x, L("بستن"), C.ink, C.ink.copy(alpha = 0.08f), 18.dp) { onClose() }
                }
                // picture or video
                if (!reel) Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp).fillMaxWidth().clip(RoundedCornerShape(50)).background(C.card2.copy(alpha = 0.75f))
                    .border(1.dp, C.line2, RoundedCornerShape(50)).padding(4.dp)) {
                    for ((v, label) in listOf(false to L("عکس"), true to L("ویدیوی متحرک"))) {
                        val on = video == v
                        Box(Modifier.weight(1f).clip(RoundedCornerShape(50)).background(if (on) accent else Color.Transparent)
                            .clickable { video = v; Store.prefs().edit().putBoolean("story.video", v).apply() }.padding(vertical = 11.dp), contentAlignment = Alignment.Center) {
                            T(label, 14, FontWeight.ExtraBold, if (on) LocalAccentInk.current else C.ink)
                        }
                    }
                }
                // as it will be sent, glowing in the story's colour
                val glow = Color(brighten(tint))
                Box(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 14.dp), contentAlignment = Alignment.Center) {
                    val ratio = shape.format.w.toFloat() / shape.format.h
                    val r = RoundedCornerShape(26.dp)
                    Box(Modifier.height(if (shape.format == CardFormat.STORY) 500.dp else 400.dp).aspectRatio(ratio)
                        .shadow(30.dp, r, ambientColor = glow, spotColor = glow).clip(r).background(C.card2).border(1.dp, Color.White.copy(alpha = 0.14f), r),
                        contentAlignment = Alignment.Center) {
                        preview?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), filterQuality = FilterQuality.High) } ?: T(L("در حال ساختن…"), 13, color = C.mute)
                        making?.let { pr ->
                            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    T(if (video) L("ساختن ویدیو… {0}٪", fa((pr * 100).toInt())) else L("صبر کنید…"), 15, FontWeight.ExtraBold, Color.White)
                                    Box(Modifier.padding(top = 10.dp).width(160.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.25f))) {
                                        Box(Modifier.fillMaxHeight().fillMaxWidth(pr.coerceIn(0.02f, 1f)).background(accent))
                                    }
                                }
                            }
                        }
                    }
                }
                if (themed) {
                    StudioLabel(L("طرح"))
                    val hc = art?.card?.hc ?: scene?.hc?.takeIf { it != 0 } ?: accent.toArgb(); val ac = art?.card?.ac ?: scene?.ac?.takeIf { it != 0 } ?: deep(accent)
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(CARD_THEMES.size) { k ->
                            val t = CARD_THEMES[k]
                            val on = t == o.theme
                            val sc by androidx.compose.animation.core.animateFloatAsState(if (on) 1f else 0.93f, label = "theme")
                            Column(Modifier.graphicsLayer { scaleX = sc; scaleY = sc }.clip(RoundedCornerShape(18.dp)).clickable { o = o.copy(theme = t) }.padding(2.dp),
                                horizontalAlignment = Alignment.CenterHorizontally) {
                                val r = RoundedCornerShape(16.dp)
                                Box(Modifier.size(78.dp, 138.dp).clip(r).background(themeSwatch(t, hc, ac)).border(if (on) 3.dp else 1.dp, if (on) accent else C.line2, r)) {
                                    thumbs[t]?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, filterQuality = FilterQuality.High) }
                                    if (t in NEW_THEMES) Box(Modifier.align(Alignment.TopStart).padding(5.dp).clip(RoundedCornerShape(8.dp)).background(accent).padding(horizontal = 6.dp)) {
                                        T(L("تازه"), 12, FontWeight.Black, LocalAccentInk.current)
                                    }
                                    if (on) Box(Modifier.align(Alignment.BottomEnd).padding(6.dp).size(22.dp).clip(CircleShape).background(accent), contentAlignment = Alignment.Center) {
                                        T("✓", 12, FontWeight.Black, LocalAccentInk.current)
                                    }
                                }
                                T(themeName(t), 12, if (on) FontWeight.ExtraBold else FontWeight.Normal, if (on) accent else C.mute, modifier = Modifier.padding(top = 5.dp))
                            }
                        }
                    }
                }
                if (reel) {
                    StudioLabel(L("صدا"))
                    StudioChips(listOf("full" to L("آهنگ و ورزشگاه"), "crowd" to L("فقط صدای ورزشگاه"), "" to L("بدون صدا")), reelSound) {
                        reelSound = it; Store.prefs().edit().putString("reel.sound", it).apply()
                    }
                } else if (video) {
                    StudioLabel(L("آهنگ"))
                    StudioChips(MUSIC.map { it.first to L(it.second) }, music) { music = it; Store.prefs().edit().putString("story.music", it).apply() }
                } else if (match != null) {
                    StudioLabel(L("اندازه"))
                    StudioChips(listOf(CardFormat.STORY to L("استوری"), CardFormat.PORTRAIT to L("پست عمودی"), CardFormat.SQUARE to L("پست مربع")), o.format) { o = o.copy(format = it) }
                }
                if (story !is Story.Lineup && !reel) {
                    StudioLabel(L("استیکر"))
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(STICKERS.size) { k ->
                            val st = STICKERS[k]
                            val on = st == o.sticker
                            val r = RoundedCornerShape(20.dp)
                            val sc by androidx.compose.animation.core.animateFloatAsState(if (on) 1.08f else 1f, label = "sticker")
                            Box(Modifier.graphicsLayer { scaleX = sc; scaleY = sc }.size(66.dp).clip(r).background(if (on) accent.copy(alpha = 0.22f) else C.card2)
                                .border(if (on) 2.dp else 1.dp, if (on) accent else C.line2, r).clickable { o = o.copy(sticker = st) }, contentAlignment = Alignment.Center) {
                                if (st.isEmpty()) androidx.compose.material3.Icon(Ic.x, L("بدون استیکر"), Modifier.size(20.dp), tint = C.mute)
                                else stickerArt[st]?.let { Image(it.asImageBitmap(), null, Modifier.size(60.dp), filterQuality = FilterQuality.High) }
                            }
                        }
                    }
                }
                if (match != null) {
                    StudioLabel(L("روی عکس باشد"))
                    FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Toggle(L("گلزن‌ها"), o.scorers) { o = o.copy(scorers = it) }
                        Toggle(L("آمار کلیدی"), o.stats) { o = o.copy(stats = it) }
                        Toggle(L("بهترین بازیکن"), o.best) { o = o.copy(best = it) }
                        Toggle(L("مومنتوم"), o.momentum) { o = o.copy(momentum = it) }
                        if (Predictions.get(match.m.matchId) != null) Toggle(L("پیش‌بینی من"), o.prediction) { o = o.copy(prediction = it) }
                    }
                }
                if (!reel) StudioLabel(L("نوشته‌ی خودت"))
                if (!reel) Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.card2).border(1.dp, C.line2, RoundedCornerShape(18.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp)) {
                    if (o.caption.isEmpty()) T(L("مثلاً: بهترین بازی فصل!"), 14, color = C.faint)
                    BasicTextField(o.caption, { o = o.copy(caption = it.take(60)) }, Modifier.fillMaxWidth(), singleLine = true,
                        textStyle = vz(15.sp).copy(color = C.ink), cursorBrush = SolidColor(accent))
                }
                // where it goes
                val ready = scene != null && making == null
                Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 22.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ShareTarget(L("اینستاگرام"), "ig", Modifier.weight(1f), ready,
                        Brush.linearGradient(listOf(Color(0xFFFEDA75), Color(0xFFFA7E1E), Color(0xFFD62976), Color(0xFF962FBF), Color(0xFF4F5BD5)))) {
                        produce { f, mime -> app.goallive.live.data.Telemetry.event("share:instagram"); send(ctx, f, mime, story, INSTAGRAM) }
                    }
                    ShareTarget(L("تلگرام"), "tg", Modifier.weight(1f), ready, Brush.linearGradient(listOf(Color(0xFF37BBFE), Color(0xFF1E96D4)))) {
                        produce { f, mime -> app.goallive.live.data.Telemetry.event("share:telegram"); send(ctx, f, mime, story, TELEGRAM) }
                    }
                }
                Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ShareTarget(L("بقیه"), "share", Modifier.weight(1f), ready, null) { produce { f, mime -> app.goallive.live.data.Telemetry.event("share:other"); send(ctx, f, mime, story, emptyList()) } }
                    ShareTarget(L("ذخیره در گالری"), "save", Modifier.weight(1f), ready, null) { produce { f, mime -> app.goallive.live.data.Telemetry.event("share:save"); save(ctx, f, mime) } }
                }
            }
        }
    }
}

/** The designs added last, marked «تازه» in the picker. */
private val NEW_THEMES = setOf("aurora", "holo", "fire", "ice", "stadium", "comic")

/** A soft blur made by shrinking a picture to a few pixels and growing it back in steps. */
private fun softBlur(b: Bitmap): Bitmap {
    val tiny = Bitmap.createScaledBitmap(b, 10, 18, true)
    val mid = Bitmap.createScaledBitmap(tiny, 40, 72, true)
    return Bitmap.createScaledBitmap(mid, 120, 216, true)
}

@Composable
private fun ShareTarget(label: String, mark: String, modifier: Modifier, enabled: Boolean, brush: Brush?, onClick: () -> Unit) {
    val r = RoundedCornerShape(18.dp)
    val ink = if (brush != null) Color.White else C.ink
    Row(modifier.height(56.dp).alpha(if (enabled) 1f else 0.45f).clip(r).let { if (brush != null) it.background(brush) else it.background(C.card2).border(1.dp, C.line2, r) }
        .clickable(enabled = enabled, onClick = onClick), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        ShareMark(mark, ink); Spacer(Modifier.width(8.dp))
        T(label, 15, FontWeight.ExtraBold, ink)
    }
}

/** The mark on a share button: Instagram's camera, Telegram's plane, or the app's own icons. */
@Composable
private fun ShareMark(kind: String, tint: Color) {
    when (kind) {
        "share" -> androidx.compose.material3.Icon(Ic.share, null, Modifier.size(20.dp), tint = tint)
        "save" -> androidx.compose.material3.Icon(Ic.down, null, Modifier.size(20.dp), tint = tint)
        else -> androidx.compose.foundation.Canvas(Modifier.size(20.dp)) {
            val w = size.width
            if (kind == "ig") {
                drawRoundRect(tint, topLeft = Offset(w * 0.06f, w * 0.06f), size = Size(w * 0.88f, w * 0.88f), cornerRadius = CornerRadius(w * 0.28f), style = Stroke(w * 0.11f))
                drawCircle(tint, radius = w * 0.21f, center = center, style = Stroke(w * 0.11f))
                drawCircle(tint, radius = w * 0.065f, center = Offset(w * 0.74f, w * 0.26f))
            } else {
                val plane = androidx.compose.ui.graphics.Path().apply { moveTo(w * 0.04f, w * 0.47f); lineTo(w * 0.96f, w * 0.1f); lineTo(w * 0.78f, w * 0.9f); lineTo(w * 0.47f, w * 0.68f); close() }
                drawPath(plane, tint)
                drawLine(Color.Black.copy(alpha = 0.25f), Offset(w * 0.47f, w * 0.68f), Offset(w * 0.96f, w * 0.1f), strokeWidth = w * 0.06f)
            }
        }
    }
}

@Composable
internal fun StudioLabel(s: String) = T(s, 13, FontWeight.ExtraBold, C.mute, Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp))

@Composable
internal fun <K> StudioChips(options: List<Pair<K, String>>, selected: K, onPick: (K) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(options.size) { k ->
            val (key, label) = options[k]
            val on = key == selected
            Box(Modifier.clip(RoundedCornerShape(50)).background(if (on) LocalAccent.current else C.card2).clickable { onPick(key) }.padding(horizontal = 16.dp, vertical = 8.dp)) {
                T(label, 13, FontWeight.Bold, if (on) LocalAccentInk.current else C.ink)
            }
        }
    }
}

@Composable
internal fun Toggle(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    val accent = LocalAccent.current
    Row(Modifier.clip(RoundedCornerShape(50)).background(if (on) accent.copy(alpha = 0.18f) else C.card2).border(1.dp, if (on) accent else C.line2, RoundedCornerShape(50))
        .clickable { onChange(!on) }.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        T(if (on) "✓ " else "", 12, FontWeight.Black, accent); T(label, 13, FontWeight.Bold, if (on) C.ink else C.mute)
    }
}

private val INSTAGRAM = listOf("com.instagram.android")
private val TELEGRAM = listOf("org.telegram.messenger", "org.telegram.messenger.web", "org.thunderdog.challegram", "org.telegram.plus")

/** Sends [f] to the first of [apps] that is installed, or to the system's chooser. */
private fun send(ctx: Context, f: File, mime: String, story: Story, apps: List<String>) {
    val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
    val text = story.message() + "\n" + L("نتایج زنده فوتبال در گل‌لایو") + "\n" + BAZAAR_URL
    fun intent() = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri).putExtra(Intent.EXTRA_TEXT, text)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    for (pkg in apps) {
        try { ctx.startActivity(intent().setPackage(pkg)); return } catch (_: ActivityNotFoundException) { } catch (_: SecurityException) { }
    }
    if (apps.isNotEmpty()) Toast.makeText(ctx, L("این برنامه روی گوشی نصب نیست"), Toast.LENGTH_SHORT).show()
    ctx.startActivity(Intent.createChooser(intent(), L("اشتراک‌گذاری")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** Saves into Pictures/GoalLive or Movies/GoalLive (no permission needed from Android 10). */
private fun save(ctx: Context, f: File, mime: String) {
    val isVideo = mime.startsWith("video")
    val ok = runCatching {
        if (Build.VERSION.SDK_INT < 29) error("old")
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, f.nameWithoutExtension + "-" + System.currentTimeMillis() / 1000 + "." + f.extension)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, if (isVideo) "Movies/GoalLive" else "Pictures/GoalLive")
        }
        val where = if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val uri = ctx.contentResolver.insert(where, values) ?: error("no uri")
        ctx.contentResolver.openOutputStream(uri)?.use { out -> f.inputStream().use { it.copyTo(out) } } ?: error("no stream")
    }.isSuccess
    Toast.makeText(ctx, if (ok) L("در گالری ذخیره شد") else L("ذخیره نشد؛ از «بقیه» استفاده کنید"), Toast.LENGTH_SHORT).show()
}

/** On a finished match: opens the studio on the match told in about a minute. */
@Composable
fun ReelBanner(m: MatchData) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Brush.linearGradient(listOf(Color(deep(m.homeColor)), Color(deep(m.awayColor)))))
        .clickable { StoryStudio.story = Story.Reel(m) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(46.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) { T("🎬", 22) }
        Column(Modifier.weight(1f)) {
            T(L("بازی در یک دقیقه"), 16, FontWeight.Black, Color.White)
            T(L("ویدیوی بازی با گل‌ها، آمار و بهترین بازیکن؛ آماده‌ی استوری"), 12, color = Color.White.copy(alpha = 0.85f))
        }
        T("▶", 18, FontWeight.Black, Color.White)
    }
}
