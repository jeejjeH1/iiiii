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
class Scene(val W: Int, val H: Int, val bg: Bitmap, val fg: (Canvas, Clock) -> Unit) {
    private val smooth = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    /** The finished picture at [scale]. */
    fun still(scale: Float): Bitmap {
        val out = Bitmap.createBitmap((W * scale).toInt(), (H * scale).toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.scale(scale, scale)
        c.drawBitmap(bg, 0f, 0f, smooth)
        fg(c, Clock.END)
        return out
    }

    /** Frame [t] of the video on [c] (already scaled to W×H): a slow push into the background, the parts, a light sweep at the end. */
    fun frame(c: Canvas, t: Float) {
        val intro = easeOut((t / 1.6f).coerceIn(0f, 1f))
        val z = 1.12f - 0.12f * intro + 0.035f * (t / STORY_SECONDS)
        c.save(); c.scale(z, z, W / 2f, H / 2f); c.drawBitmap(bg, 0f, 0f, smooth); c.restore()
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

private fun leagueLogo(id: Int) = "https://images.fotmob.com/image_resources/logo/leaguelogo/$id.png"

internal suspend fun Story.load(ctx: Context): Art {
    val en = Lang.en
    val black = if (en) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else ResourcesCompat.getFont(ctx, R.font.vazir_black) ?: Typeface.DEFAULT_BOLD
    val bold = if (en) Typeface.DEFAULT_BOLD else ResourcesCompat.getFont(ctx, R.font.vazir_bold) ?: Typeface.DEFAULT_BOLD
    val logo = BitmapFactory.decodeResource(ctx.resources, R.drawable.logo)
    suspend fun faces(ids: List<Int>, px: Int = 360) = ids.filter { it > 0 }.distinct().associateWith { image(ctx, playerFace(it), px) }
    suspend fun crests(ids: List<Int>) = ids.filter { it > 0 }.distinct().associateWith { image(ctx, teamLogo(it, true), 200) }
    return when (this) {
        is Story.Match -> { val best = bestOf(m); Art(logo, black, bold, card = cardAssets(ctx, m, best), best = best) }
        is Story.Player -> Art(logo, black, bold, faces(listOf(id), 480),
            crests(listOf(p.o("primaryTeam").i("teamId") ?: 0) + p.a("recentMatches").objs().take(6).map { it.i("opponentTeamId") ?: 0 }),
            (p.o("mainLeague").i("leagueId") ?: 0).takeIf { it > 0 }?.let { mapOf(it to image(ctx, leagueLogo(it), 200)) } ?: emptyMap())
        is Story.Table -> Art(logo, black, bold, crests = crests(rows.map { it.i("id") ?: 0 }), leagues = mapOf(league to image(ctx, leagueLogo(league), 220)))
        is Story.Ballon -> Art(logo, black, bold, faces(men.map { it.i("id") ?: 0 }, 420), crests(men.map { it.i("team") ?: 0 }))
        is Story.Duel -> Art(logo, black, bold, faces(listOf(a.i("id") ?: 0, b.i("id") ?: 0), 420),
            crests(listOf(a.o("primaryTeam").i("teamId") ?: 0, b.o("primaryTeam").i("teamId") ?: 0)))
        is Story.Lineup -> Art(logo, black, bold, faces(men.map { it.id }, 220), crests(men.mapNotNull { it.crest } + listOfNotNull(crest)),
            league?.let { mapOf(it to image(ctx, leagueLogo(it), 160)) } ?: emptyMap())
    }
}

/** The story drawn with the chosen options. */
internal fun Story.scene(o: CardOptions, a: Art): Scene = when (this) {
    is Story.Match -> matchScene(m, o, a.card!!, a.best)
    is Story.Lineup -> formationScene(title, sub, tone, men, a.faces, a.crests, crest?.let { a.crests[it] } ?: league?.let { a.leagues[it] }, a.logo,
        a.black, a.bold, formation, starLine, o.caption)
    is Story.Player -> playerScene(this, o, a)
    is Story.Table -> tableScene(this, o, a)
    is Story.Ballon -> ballonScene(this, o, a)
    is Story.Duel -> duelScene(this, o, a)
}

/* ---------- shared drawing for the new stories ---------- */

private const val SW = 1080
private const val SH = 1920
private val en get() = Lang.en
/** x measured from the reading start: from the left in English, from the right in Persian. */
private fun xs(x: Float) = if (en) x else SW - x

private fun teamTone(id: Int, data: JSONObject?, crest: Bitmap?, fallback: Long): Int {
    val club = Clubs.get(id)?.let { Color(it.c1) }
    val c = club ?: parseColor(data?.path("teamColors")?.s("color")) ?: Color(fallback)
    return cardColor(c, crest, fallback)
}

private fun darker(c: Int, k: Float = 0.55f) = androidx.core.graphics.ColorUtils.blendARGB(c, 0xFF000000.toInt(), k)

/** The still layer for a new story: the chosen design, a faint big crest or two, and grain. */
private fun storyBg(theme: String, hc: Int, ac: Int, marks: List<Pair<Bitmap?, Float>> = emptyList()): Pair<Bitmap, CardLook> {
    val bg = Bitmap.createBitmap(SW, SH, Bitmap.Config.ARGB_8888)
    val c = Canvas(bg)
    val look = background(c, theme, SW, SH, hc, ac)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    val wm = SW * 0.95f
    for ((bm, y0) in marks) bm?.let {
        p.alpha = if (theme == "light") 13 else 18
        c.drawBitmap(it, null, RectF(SW / 2f - wm / 2, y0, SW / 2f + wm / 2, y0 + wm), p)
    }
    grain(c, SW, SH, theme == "light")
    return bg to look
}

/** A face in a ring: a soft glow of [ring], the ring, the photo clipped round. */
private fun faceRing(c: Canvas, face: Bitmap?, cx: Float, cy: Float, r: Float, ring: Int, glow: Boolean = true) {
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
private fun crestDisc(c: Canvas, crest: Bitmap?, cx: Float, cy: Float, r: Float) {
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); setShadowLayer(r * 0.4f, 0f, r * 0.12f, 0x66000000) }
    c.drawCircle(cx, cy, r, p)
    crest?.let { c.drawBitmap(it, null, RectF(cx - r * 0.72f, cy - r * 0.72f, cx + r * 0.72f, cy + r * 0.72f), Paint(Paint.FILTER_BITMAP_FLAG)) }
}

/** A coloured rounded chip with a rating in it. */
private fun ratingChip(c: Canvas, v: Double, cx: Float, top: Float, size: Float, tf: Typeface) {
    val s = fa(dec(v, 1))
    val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; typeface = tf }
    val w = tp.measureText(s) + size * 0.9f
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ratingColor(v); setShadowLayer(size * 0.4f, 0f, size * 0.15f, (ratingColor(v) and 0x00FFFFFF) or 0x88000000.toInt()) }
    c.drawRoundRect(RectF(cx - w / 2, top, cx + w / 2, top + size * 1.5f), size * 0.45f, size * 0.45f, p)
    text(c, s, cx, top + size * 0.2f, size, 0xFFFFFFFF.toInt(), tf, (w + 20).toInt())
}

private fun num(v: Double, p: Float): String {
    val shown = v * easeOut(p)
    return fa(if (v % 1 != 0.0) dec(shown, if (v < 10) 2 else 1) else "${shown.roundToInt()}")
}

/** The app's mark and name, centred at [y]. */
private fun footer(c: Canvas, a: Art, look: CardLook, clk: Clock, y: Float = SH - 130f) {
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 44f; typeface = a.black }
    val name = L("گل‌لایو"); val nw = tp.measureText(name); val lr = 30f
    val bw = nw + 2 * lr + 20
    val lx = if (en) SW / 2f - bw / 2 + lr else SW / 2f + bw / 2 - lr
    c.brand(clk, SW / 2f, y) {
        a.logo?.let { p.color = 0xFFFFFFFF.toInt(); c.drawCircle(lx, y, lr + 4, p); c.drawBitmap(it, null, RectF(lx - lr, y - lr, lx + lr, y + lr), p) }
        text(c, name, if (en) SW / 2f - bw / 2 + 2 * lr + 20 + nw / 2 else SW / 2f - bw / 2 + nw / 2, y - 44f * 0.62f, 44f, look.ink, a.black, (nw + 20).toInt())
    }
}

/** The user's caption and sticker, common to every new story. */
private fun extras(c: Canvas, o: CardOptions, a: Art, look: CardLook, clk: Clock, captionY: Float) {
    if (o.caption.isNotBlank()) c.enter(clk.at(3.0f), captionY, captionY + 80f) { captionLine(c, o.caption, captionY, look.accent, a.black, SW) }
    if (o.sticker.isNotEmpty()) {
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 160f }
        val sx = if (en) SW - 120f else 120f; val sy = 70f
        c.pop(clk.at(3.2f, 0.5f), sx, sy + 80f) {
            c.save(); c.rotate(if (en) 14f else -14f, sx, sy + 80f); c.drawText(o.sticker, sx - tp.measureText(o.sticker) / 2, sy + tp.textSize * 0.85f, tp); c.restore()
        }
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
    val captionY = SH - 270f
    return Scene(SW, SH, bg) { c, clk ->
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
        val gy = 1030f; val tw = (SW - 112f - 2 * 20f) / 3; val th = 176f
        tiles.forEachIndexed { k, (label, v) ->
            val col = k % 3; val row = k / 3
            val left = if (en) 56f + col * (tw + 20f) else SW - 56f - (col + 1) * tw - col * 20f
            val top = gy + row * (th + 20f)
            c.enter(clk.at(1.3f + k * 0.12f), top, top + th) {
                glass(c, left, top, left + tw, top + th, look)
                text(c, num(v, clk.at(1.4f + k * 0.12f, 0.9f)), left + tw / 2, top + 26f, 64f, look.ink, a.black, tw.toInt())
                text(c, label, left + tw / 2, top + 116f, 26f, look.sub, a.bold, (tw - 20).toInt(), 1)
            }
        }
        // the last five games: opponent and rating
        if (recent.isNotEmpty()) {
            val ry = gy + 2 * (th + 20f) + 30f
            c.enter(clk.at(2.2f), ry, ry + 60f) { text(c, L("بازی‌های اخیر"), SW / 2f, ry, 30f, look.sub, a.bold, 600) }
            val cw = (SW - 112f) / 5
            recent.forEachIndexed { k, m ->
                val cx = if (en) 56f + cw * (k + 0.5f) else SW - 56f - cw * (k + 0.5f)
                c.pop(clk.at(2.4f + k * 0.1f, 0.45f), cx, ry + 120f) {
                    crestDisc(c, a.crests[m.i("opponentTeamId") ?: 0], cx, ry + 110f, 46f)
                    m.path("ratingProps").s("rating")?.toDoubleOrNull()?.let { ratingChip(c, it, cx, ry + 172f, 30f, a.black) }
                    val ga = listOfNotNull((m.i("goals") ?: 0).takeIf { it > 0 }?.let { "⚽".repeat(min(it, 3)) }, (m.i("assists") ?: 0).takeIf { it > 0 }?.let { "🅰".repeat(min(it, 2)) })
                    if (ga.isNotEmpty()) text(c, ga.joinToString(""), cx, ry + 230f, 24f, look.ink, a.bold, cw.toInt())
                }
            }
        }
        extras(c, o, a, look, clk, captionY)
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
    return Scene(SW, SH, bg) { c, clk ->
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
        extras(c, o, a, look, clk, captionY)
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
    return Scene(SW, SH, bg) { c, clk ->
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
        extras(c, o, a, look, clk, captionY)
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
    return Scene(SW, SH, bg) { c, clk ->
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
        extras(c, o, a, look, clk, captionY)
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
    var o by remember { mutableStateOf(if (match != null) loadOptions() else CardOptions(theme = story.theme)) }
    var video by remember { mutableStateOf(Store.prefs().getBoolean("story.video", false)) }
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
            val t = ((System.nanoTime() - start) / 1e9f) % (STORY_SECONDS + 0.8f)
            val b = bufs[n % 2]; n++
            withContext(Dispatchers.Default) {
                b.eraseColor(0xFF000000.toInt())
                val c = Canvas(b); c.scale(k, k); sc.frame(c, t.coerceAtMost(STORY_SECONDS))
            }
            preview = b
            withFrameNanos { }
        }
    }
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
                        val ok = StoryVideo.make(ctx, f, 720, 1280, STORY_SECONDS, musicRes(music), { making = it }, { alive.get() }) { c, t -> sc.frame(c, t) }
                        if (ok) f to "video/mp4" else null
                    }
                }.getOrNull()
            }
            making = null
            if (out != null) then(out.first, out.second)
            else if (alive.get()) Toast.makeText(ctx, L("ساختن ویدیو روی این گوشی ممکن نشد؛ عکس را امتحان کنید"), Toast.LENGTH_LONG).show()
        }
    }

    val state = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onClose, sheetState = state, containerColor = C.bg,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), dragHandle = {
            Box(Modifier.padding(top = 10.dp, bottom = 4.dp).width(44.dp).height(5.dp).clip(RoundedCornerShape(5.dp)).background(C.line2))
        }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 16.dp)) {
            T(L("🎬 استودیو استوری"), 18, FontWeight.Black, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
            // picture or video
            Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.card2).padding(4.dp)) {
                for ((v, label) in listOf(false to L("🖼️ عکس"), true to L("🎞️ ویدیو"))) {
                    val on = video == v
                    Box(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if (on) accent else Color.Transparent)
                        .clickable { video = v; Store.prefs().edit().putBoolean("story.video", v).apply() }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                        T(label, 14, FontWeight.ExtraBold, if (on) LocalAccentInk.current else C.ink)
                    }
                }
            }
            // as it will be sent
            Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                val ratio = shape.format.w.toFloat() / shape.format.h
                Box(Modifier.height(if (shape.format == CardFormat.STORY) 440.dp else 380.dp).aspectRatio(ratio).clip(RoundedCornerShape(18.dp))
                    .background(C.card2).border(1.dp, C.line2, RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
                    preview?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize()) } ?: T(L("در حال ساختن…"), 13, color = C.mute)
                    making?.let { pr ->
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
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
            if (video) {
                StudioLabel(L("آهنگ"))
                StudioChips(MUSIC.map { it.first to L(it.second) }, music) { music = it; Store.prefs().edit().putString("story.music", it).apply() }
            } else if (match != null) {
                StudioLabel(L("اندازه"))
                StudioChips(listOf(CardFormat.STORY to L("استوری"), CardFormat.PORTRAIT to L("پست عمودی"), CardFormat.SQUARE to L("پست مربع")), o.format) { o = o.copy(format = it) }
            }
            if (story !is Story.Lineup) {
                StudioLabel(L("طرح"))
                val hc = art?.card?.hc ?: accent.toArgb(); val ac = art?.card?.ac ?: deep(accent)
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(CARD_THEMES.size) { k ->
                        val t = CARD_THEMES[k]
                        val on = t == o.theme
                        Column(Modifier.clip(RoundedCornerShape(16.dp)).clickable { o = o.copy(theme = t) }.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.size(58.dp, 82.dp).clip(RoundedCornerShape(12.dp)).background(themeSwatch(t, hc, ac)).border(if (on) 3.dp else 1.dp, if (on) accent else C.line2, RoundedCornerShape(12.dp)))
                            T(themeName(t), 11, if (on) FontWeight.ExtraBold else FontWeight.Normal, if (on) accent else C.mute, modifier = Modifier.padding(top = 4.dp))
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
            StudioLabel(L("نوشته‌ی خودت"))
            Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.card2).border(1.dp, C.line2, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp)) {
                if (o.caption.isEmpty()) T(L("مثلاً: بهترین بازی فصل! 🔥"), 14, color = C.faint)
                BasicTextField(o.caption, { o = o.copy(caption = it.take(60)) }, Modifier.fillMaxWidth(), singleLine = true,
                    textStyle = vz(14.sp).copy(color = C.ink), cursorBrush = SolidColor(accent))
            }
            if (story !is Story.Lineup) {
                StudioLabel(L("استیکر"))
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val stickers = listOf("", "🔥", "⚽", "🏆", "😍", "💙", "❤️", "💛", "💚", "😭", "🤯", "👑", "🐐", "💪")
                    items(stickers.size) { k ->
                        val st = stickers[k]
                        val on = st == o.sticker
                        Box(Modifier.size(44.dp).clip(CircleShape).background(if (on) accent.copy(alpha = 0.25f) else C.card2).border(if (on) 2.dp else 0.dp, accent, CircleShape)
                            .clickable { o = o.copy(sticker = st) }, contentAlignment = Alignment.Center) {
                            if (st.isEmpty()) T("✕", 14, color = C.mute) else T(st, 20)
                        }
                    }
                }
            }
            // where it goes
            val ready = scene != null && making == null
            Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ShareTarget(L("اینستاگرام"), "📸", Modifier.weight(1f), ready, Color(0xFFDD2A7B)) { produce { f, mime -> send(ctx, f, mime, story, INSTAGRAM) } }
                ShareTarget(L("تلگرام"), "✈️", Modifier.weight(1f), ready, Color(0xFF229ED9)) { produce { f, mime -> send(ctx, f, mime, story, TELEGRAM) } }
            }
            Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ShareTarget(L("بقیه"), "↗️", Modifier.weight(1f), ready, null) { produce { f, mime -> send(ctx, f, mime, story, emptyList()) } }
                ShareTarget(L("ذخیره در گالری"), "⬇️", Modifier.weight(1f), ready, null) { produce { f, mime -> save(ctx, f, mime) } }
            }
        }
    }
}

@Composable
private fun ShareTarget(label: String, icon: String, modifier: Modifier, enabled: Boolean, tint: Color?, onClick: () -> Unit) {
    val bg = tint ?: C.card2
    Row(modifier.clip(RoundedCornerShape(16.dp)).background(if (enabled) bg else bg.copy(alpha = 0.4f)).let { if (tint == null) it.border(1.dp, C.line2, RoundedCornerShape(16.dp)) else it }
        .clickable(enabled = enabled, onClick = onClick).padding(vertical = 14.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        T(icon, 16); Spacer(Modifier.width(6.dp))
        T(label, 15, FontWeight.ExtraBold, if (tint != null) Color.White else C.ink)
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
