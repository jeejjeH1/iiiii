package app.goallive.live.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.text.TextPaint
import app.goallive.live.L
import app.goallive.live.Lang
import app.goallive.live.data.a
import app.goallive.live.data.b
import app.goallive.live.data.d
import app.goallive.live.data.i
import app.goallive.live.data.o
import app.goallive.live.data.objs
import app.goallive.live.data.path
import app.goallive.live.data.s
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * "بازی در یک دقیقه": a finished match told in about a minute — the teams, both line-ups, kick-off, every goal flying into the net,
 * red cards, half time, the momentum, the numbers, GoalLive's best player and the final score, with a TV score bug on top.
 */

/** What the score bug shows during a part: the score and the clock at [t] seconds into it, or nothing. */
internal class Bug(val at: (Float) -> Triple<Int, Int, String>?)

internal class Part(val len: Float, val bug: Bug?, val fx: List<Pair<Float, StoryAudio.Fx>>, val draw: (Canvas, Float) -> Unit)

/** The reel: parts one after another over a still background that drifts, each fading out into the next. */
class ReelScene internal constructor(bg: Bitmap, hc: Int, ac: Int, private val parts: List<Part>, private val a: CardAssets, private val m: MatchData) :
    Scene(SW, SH, bg, hc, ac, { c, _ -> parts.last().draw(c, parts.last().len) }) {
    override val seconds: Float = parts.sumOf { it.len.toDouble() }.toFloat()

    /** Every sound and when it plays, for the sound track. */
    val fx: List<Pair<Float, StoryAudio.Fx>> get() {
        var t0 = 0f
        return parts.flatMapIndexed { k, p ->
            val list = p.fx.map { (t, f) -> t0 + t to f } + if (k > 0) listOf(t0 - 0.15f to StoryAudio.Fx.WHOOSH) else emptyList()
            t0 += p.len; list
        }
    }

    override fun frame(c: Canvas, t: Float) {
        val z = 1.08f + 0.04f * sin(t * 0.21f)
        c.save(); c.scale(z, z, W / 2f, H / 2f); c.drawBitmap(bg, 0f, 0f, smooth); c.restore()
        var t0 = 0f
        for (p in parts) {
            if (t < t0 + p.len || p === parts.last()) {
                val lt = (t - t0).coerceIn(0f, p.len)
                // the part fades out over its last moment (the last one stays)
                val out = if (p === parts.last()) 1f else ((p.len - lt) / 0.3f).coerceIn(0f, 1f)
                if (out < 1f) {
                    val n = c.saveLayerAlpha(0f, 0f, W.toFloat(), H.toFloat(), (255 * out).toInt())
                    c.scale(1f + (1 - out) * 0.04f, 1f + (1 - out) * 0.04f, W / 2f, H / 2f)
                    p.draw(c, lt); c.restoreToCount(n)
                } else p.draw(c, lt)
                p.bug?.at?.invoke(lt)?.let { (h, aw, clock) -> scoreBug(c, h, aw, clock, (lt / 0.35f).coerceIn(0f, 1f) * out) }
                break
            }
            t0 += p.len
        }
        if (t < 0.3f) c.drawColor(((255 * (1 - t / 0.3f)).toInt() shl 24))
    }

    /** The TV score bug: crests, names and the score in a dark pill, the clock under it. */
    private fun scoreBug(c: Canvas, hs: Int, aws: Int, clock: String, alpha: Float) {
        if (alpha <= 0f) return
        val n = c.saveLayerAlpha(0f, 40f, W.toFloat(), 260f, (255 * alpha).toInt())
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val en = Lang.en
        val r = RectF(70f, 70f, W - 70f, 170f)
        p.color = 0xE60B0F14.toInt(); p.setShadowLayer(30f, 0f, 10f, 0x88000000.toInt()); c.drawRoundRect(r, 50f, 50f, p); p.clearShadowLayer()
        p.style = Paint.Style.STROKE; p.strokeWidth = 2f; p.color = 0x33FFFFFF; c.drawRoundRect(r, 50f, 50f, p); p.style = Paint.Style.FILL
        // the home side on the reading start
        val dir = if (en) -1 else 1
        for ((side, crest, col, name, id) in listOf(Five(1, a.home, a.hc, m.homeName, m.homeId), Five(-1, a.away, a.ac, m.awayName, m.awayId))) {
            val sx = W / 2f + dir * side * 380f
            p.color = brighten(col); c.drawRoundRect(RectF(sx - 6f, 88f, sx + 6f, 152f), 6f, 6f, p)
            crestDisc(c, crest, W / 2f + dir * side * 320f, 120f, 34f)
            val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 32f; typeface = a.black }
            val nm = android.text.TextUtils.ellipsize(teamName(id, name), tp, 170f, android.text.TextUtils.TruncateAt.END).toString()
            text(c, nm, W / 2f + dir * side * 200f, 100f, 32f, 0xFFFFFFFF.toInt(), a.black, 200)
        }
        p.color = 0xFF1B222C.toInt(); c.drawRoundRect(RectF(W / 2f - 95f, 82f, W / 2f + 95f, 158f), 20f, 20f, p)
        text(c, fa(hs), W / 2f + dir * 48f, 88f, 50f, 0xFFFFFFFF.toInt(), a.black, 80)
        text(c, fa(aws), W / 2f - dir * 48f, 88f, 50f, 0xFFFFFFFF.toInt(), a.black, 80)
        p.color = 0x88FFFFFF.toInt(); c.drawRect(W / 2f - 10f, 118f, W / 2f + 10f, 123f, p)
        pill(c, clock, W / 2f, 182f, 26f, 0xFF0B0F14.toInt(), 0xFFF5C542.toInt(), a.black, 300)
        c.restoreToCount(n)
    }
}

private data class Five(val side: Int, val crest: Bitmap?, val col: Int, val name: String, val id: Int)

/** The parts of [m]'s reel. */
internal fun reelScene(m: MatchData, o: CardOptions, a: CardAssets, faces: Map<Int, Bitmap?>, best: PlayerRating?): ReelScene {
    val en = Lang.en
    val (bg, look) = storyBg(o.theme, a.hc, a.ac, listOf(a.home to SH * 0.04f, a.away to SH * 0.52f))
    val ratings = runCatching { rateMatch(m) }.getOrDefault(emptyMap())
    val goals = m.events.filter { it.s("type") == "Goal" && !it.b("isPenaltyShootoutEvent") }
    val reds = m.events.filter { it.s("type") == "Card" && (it.s("card") ?: "").contains("Red") }
    val homeX = if (en) SW * 0.25f else SW * 0.75f; val awayX = SW - homeX
    val hsF = m.home.i("score") ?: 0; val asF = m.away.i("score") ?: 0
    fun minuteOf(e: JSONObject) = (e.i("time") ?: 0) + (e.i("overloadTime") ?: 0) / 100f
    val stats = m.content.path("stats.Periods.All").a("stats").objs().firstOrNull()?.a("stats").objs()
    val statRows = listOf("BallPossesion" to L("مالکیت توپ"), "expected_goals" to L("گل مورد انتظار (xG)"), "total_shots" to L("شوت"),
        "ShotsOnTarget" to L("شوت در چارچوب"), "corners" to L("کرنر"), "big_chance" to L("موقعیت عالی"), "Offsides" to L("آفساید"))
        .mapNotNull { (k, label) -> stats.firstOrNull { it.s("key") == k }?.let { label to it } }.take(6)
    val mom = momentumOf(m)

    // the moments in order: goals and red cards, with half time after the first half's last moment
    class Moment(val e: JSONObject, val goal: Boolean)
    val moments = (goals.map { Moment(it, true) } + reds.map { Moment(it, false) }).sortedBy { minuteOf(it.e) }
    val fixedLen = 3.6f + 2 * 3.4f + 2.4f + 2.4f + (if (mom.size > 5) 4.6f else 0f) + (if (statRows.isNotEmpty()) 5f else 0f) + 4.6f + 5f + reds.size * 2.8f
    val goalLen = if (goals.isEmpty()) 5.8f else ((58f - fixedLen) / goals.size).coerceIn(3.8f, 5.8f)

    val parts = mutableListOf<Part>()
    // the score before each moment, for the bug
    var hs = 0; var aws = 0; var lastMin = "0"

    /* 1. the teams */
    parts += Part(3.6f, null, listOf(0.5f to StoryAudio.Fx.WHOOSH)) { c, t ->
        val clk = Clock(t)
        a.league?.let { lg -> c.pop(clk.at(0.1f, 0.6f), SW / 2f, 250f) { crestDisc(c, lg, SW / 2f, 250f, 64f) } }
        c.enter(clk.at(0.35f), 350f, 430f) { pill(c, L("🎬 بازی در یک دقیقه"), SW / 2f, 360f, 34f, 0xFF0B0F14.toInt(), look.accent, a.black, 700) }
        for ((k, bm) in listOf(a.home, a.away).withIndex()) {
            val x = if (k == 0) homeX else awayX
            val from = if ((k == 0) != en) SW + 300f else -300f
            val p = easeOut(clk.at(0.55f + k * 0.15f, 0.6f))
            if (p > 0f) {
                val cx = from + (x - from) * p
                val col = if (k == 0) a.hc else a.ac
                val pp = Paint(Paint.ANTI_ALIAS_FLAG)
                pp.shader = RadialGradient(cx, 760f, 300f, intArrayOf((brighten(col) and 0x00FFFFFF) or 0x99000000.toInt(), brighten(col) and 0x00FFFFFF), null, Shader.TileMode.CLAMP)
                c.drawCircle(cx, 760f, 300f, pp)
                crestDisc(c, bm, cx, 760f, 150f)
            }
        }
        c.pop(clk.at(1.15f, 0.5f), SW / 2f, 760f) { text(c, "VS", SW / 2f, 715f, 80f, look.ink, android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD_ITALIC), 240) }
        c.enter(clk.at(1.35f), 960f, 1100f) {
            text(c, teamName(m.homeId, m.homeName), homeX, 960f, 54f, look.ink, a.black, 470, 2)
            text(c, teamName(m.awayId, m.awayName), awayX, 960f, 54f, look.ink, a.black, 470, 2)
        }
        c.enter(clk.at(1.8f), 1180f, 1320f) {
            val round = m.general.s("leagueRoundName")?.let { if (it.all(Char::isDigit)) L(" | هفته {0}", fa(it)) else "" } ?: ""
            text(c, leagueName(m.leagueId, m.general.s("leagueName") ?: "") + round, SW / 2f, 1180f, 40f, look.ink, a.black, SW - 160)
            text(c, fa(persianDate(m.time, "EEEE d MMMM yyyy")), SW / 2f, 1250f, 32f, look.sub, a.bold, SW - 160)
            m.facts.o("infoBox")?.o("Stadium")?.s("name")?.let { text(c, "🏟 $it", SW / 2f, 1310f, 30f, look.sub, a.bold, SW - 160) }
        }
    }

    /* 2. both line-ups */
    for ((k, side) in listOf("homeTeam", "awayTeam").withIndex()) {
        val team = m.content.path("lineup.$side") ?: continue
        val men = lineupMen(team, ratings)
        if (men.size < 11) continue
        val id = if (k == 0) m.homeId else m.awayId
        val sc = formationScene(L("ترکیب {0}", teamName(id, team.s("name") ?: "")), team.o("coach")?.s("name")?.let { L("سرمربی: {0}", personName(it)) } ?: "",
            if (k == 0) a.hc else a.ac, men, faces, emptyMap(), if (k == 0) a.home else a.away, null, a.black, a.bold, fa(team.s("formation") ?: ""), null)
        parts += Part(3.4f, null, emptyList()) { c, t -> sc.frame(c, 0.4f + t * 1.15f) }
    }

    /* 3. kick-off */
    parts += Part(2.4f, Bug { Triple(0, 0, fa("0'")) }, listOf(0.25f to StoryAudio.Fx.WHISTLE)) { c, t ->
        val clk = Clock(t)
        c.pop(clk.at(0.15f, 0.5f), SW / 2f, 820f) { text(c, L("شروع بازی"), SW / 2f, 740f, 120f, look.ink, a.black, SW - 80) }
        c.enter(clk.at(0.6f), 960f, 1100f) {
            val ib = m.facts.o("infoBox")
            ib?.o("Referee")?.s("text")?.let { text(c, L("داور: {0}", it), SW / 2f, 960f, 36f, look.sub, a.bold, SW - 160) }
            ib?.i("Attendance")?.let { text(c, L("{0} تماشاگر", fa("%,d".format(it))), SW / 2f, 1025f, 36f, look.sub, a.bold, SW - 160) }
        }
    }

    /* 4. the goals and red cards, half time in between */
    var halfDone = false
    fun halfTime() {
        val h0 = hs; val a0 = aws
        parts += Part(2.4f, Bug { Triple(h0, a0, fa("45'")) }, listOf(0.2f to StoryAudio.Fx.WHISTLE_HALF)) { c, t ->
            val clk = Clock(t)
            c.pop(clk.at(0.15f, 0.5f), SW / 2f, 820f) { text(c, L("پایان نیمه اول"), SW / 2f, 750f, 100f, look.ink, a.black, SW - 80) }
            c.enter(clk.at(0.5f), 900f, 1100f) { bigText(c, fa(if (en) "$h0 - $a0" else "$a0 - $h0"), SW / 2f, 900f, 150f, look.ink, a.black, 1f) }
        }
        halfDone = true; lastMin = "45"
    }
    for (mo in moments) {
        val e = mo.e
        if (!halfDone && (e.i("time") ?: 0) > 45) halfTime()
        val from = lastMin.toIntOrNull() ?: 0
        val to = e.i("time") ?: from
        val label = fa(minuteText(e) + "'")
        val home = e.b("isHome")
        val h0 = hs; val a0 = aws
        if (mo.goal) {
            val ns = e.a("newScore")
            val h1 = ns?.optInt(0) ?: (h0 + if (home) 1 else 0); val a1 = ns?.optInt(1) ?: (a0 + if (home) 0 else 1)
            val L0 = goalLen
            val impact = min(2.1f, L0 * 0.4f)
            parts += Part(L0, Bug { t ->
                val clock = if (t < 1f) fa("${from + ((to - from) * (t / 1f)).roundToInt()}'") else label
                if (t < impact + 0.15f) Triple(h0, a0, clock) else Triple(h1, a1, clock)
            }, listOf(impact to StoryAudio.Fx.ROAR)) { c, t -> goalPart(c, t, L0, impact, e, home, a, look, faces, m) }
            hs = h1; aws = a1
        } else {
            parts += Part(2.8f, Bug { Triple(h0, a0, label) }, listOf(0.3f to StoryAudio.Fx.WHISTLE)) { c, t -> redPart(c, t, e, a, look, faces) }
        }
        lastMin = "$to"
    }
    if (!halfDone) halfTime()

    /* 5. the momentum */
    val endBug = Bug { Triple(hsF, asF, L("پایان")) }
    if (mom.size > 5) parts += Part(4.6f, endBug, emptyList()) { c, t -> momentumPart(c, t, mom, goals, a, look, m) }

    /* 6. the numbers */
    if (statRows.isNotEmpty()) parts += Part(5f, endBug, emptyList()) { c, t ->
        val clk = Clock(t)
        c.enter(clk.at(0.1f), 300f, 380f) { text(c, L("آمار بازی"), SW / 2f, 300f, 60f, look.ink, a.black, SW - 120) }
        val rh = 150f
        statRows.forEachIndexed { k, (label, st) ->
            val y = 440f + k * (rh + 18f)
            val v = st.a("stats")
            val hv = v?.optString(0) ?: ""; val av = v?.optString(1) ?: ""
            val hn = hv.substringBefore(' ').replace("%", "").toDoubleOrNull() ?: 0.0; val an = av.substringBefore(' ').replace("%", "").toDoubleOrNull() ?: 0.0
            val pr = clk.at(0.5f + k * 0.18f, 0.9f)
            c.enter(clk.at(0.4f + k * 0.18f), y, y + rh) {
                glass(c, 56f, y, SW - 56f, y + rh, look)
                val pct = hv.contains('%')
                text(c, num(hn, pr) + if (pct) "٪" else "", homeX.let { if (en) 170f else SW - 170f }, y + 22f, 56f, if (hn >= an) look.ink else look.sub, a.black, 260)
                text(c, num(an, pr) + if (pct) "٪" else "", if (en) SW - 170f else 170f, y + 22f, 56f, if (an >= hn) look.ink else look.sub, a.black, 260)
                text(c, label, SW / 2f, y + 34f, 30f, look.sub, a.bold, 420)
                val tot = (hn + an).takeIf { it > 0 } ?: 1.0
                val half = (SW - 240f) / 2; val by = y + rh - 38f; val bh = 14f
                val p = Paint(Paint.ANTI_ALIAS_FLAG)
                p.color = look.stroke; c.drawRoundRect(RectF(120f, by, SW - 120f, by + bh), bh / 2, bh / 2, p)
                val hw = (half * hn / tot * 2 * easeOut(pr)).toFloat().coerceAtMost(half); val aw = (half * an / tot * 2 * easeOut(pr)).toFloat().coerceAtMost(half)
                val hRight = !en
                p.color = brighten(a.hc); if (hRight) c.drawRoundRect(RectF(SW / 2f + 4, by, SW / 2f + 4 + hw, by + bh), bh / 2, bh / 2, p) else c.drawRoundRect(RectF(SW / 2f - 4 - hw, by, SW / 2f - 4, by + bh), bh / 2, bh / 2, p)
                p.color = brighten(a.ac); if (hRight) c.drawRoundRect(RectF(SW / 2f - 4 - aw, by, SW / 2f - 4, by + bh), bh / 2, bh / 2, p) else c.drawRoundRect(RectF(SW / 2f + 4, by, SW / 2f + 4 + aw, by + bh), bh / 2, bh / 2, p)
            }
        }
    }

    /* 7. GoalLive's best player */
    if (best != null) parts += Part(4.6f, endBug, listOf(0.5f to StoryAudio.Fx.CHEER)) { c, t ->
        val clk = Clock(t)
        val gold = 0xFFF5C542.toInt()
        c.enter(clk.at(0.1f), 300f, 380f) { text(c, "★ " + L("بهترین بازیکن گل‌لایو"), SW / 2f, 300f, 50f, gold, a.black, SW - 120) }
        c.pop(clk.at(0.35f, 0.7f), SW / 2f, 720f) {
            faceRing(c, faces[best.id] ?: a.best, SW / 2f, 720f, 230f, gold)
            ratingChip(c, best.value, SW / 2f + (if (en) 1 else -1) * 190f, 520f, 60f, a.black)
        }
        c.enter(clk.at(0.9f), 1010f, 1160f) {
            text(c, personName(best.name), SW / 2f, 1010f, 80f, look.ink, a.black, SW - 100)
            text(c, teamName(if (best.home) m.homeId else m.awayId, if (best.home) m.homeName else m.awayName), SW / 2f, 1115f, 36f, look.sub, a.bold, SW - 160)
        }
        // where his rating came from: the biggest parts
        best.groups.filter { it.second > 0.05 }.take(3).forEachIndexed { k, (g, pts) ->
            val y = 1220f + k * 110f
            c.enter(clk.at(1.4f + k * 0.2f), y, y + 90f) {
                glass(c, 180f, y, SW - 180f, y + 90f, look)
                text(c, ratingGroupName(g), SW / 2f + (if (en) -80f else 80f), y + 22f, 36f, look.ink, a.bold, 520)
                text(c, "+" + fa(dec(pts, 1)), if (en) SW - 260f else 260f, y + 18f, 42f, 0xFF39D98A.toInt(), a.black, 160)
            }
        }
    }

    /* 8. the final score and the brand */
    parts += Part(5f, null, listOf(0.1f to StoryAudio.Fx.WHISTLE_END, 0.75f to StoryAudio.Fx.BOOM, 0.8f to StoryAudio.Fx.CHEER)) { c, t ->
        val clk = Clock(t)
        c.enter(clk.at(0.1f), 300f, 400f) { pill(c, L("پایان بازی"), SW / 2f, 320f, 40f, 0xFF0B0F14.toInt(), look.accent, a.black, 500) }
        for ((k, bm) in listOf(a.home, a.away).withIndex()) {
            val x = if (k == 0) homeX else awayX
            c.pop(clk.at(0.3f + k * 0.1f, 0.5f), x, 640f) { crestDisc(c, bm, x, 640f, 120f) }
            c.enter(clk.at(0.5f), 790f, 900f) { text(c, teamName(if (k == 0) m.homeId else m.awayId, if (k == 0) m.homeName else m.awayName), x, 790f, 44f, look.ink, a.black, 440, 2) }
        }
        // the score slams in
        val sp = clk.at(0.75f, 0.35f)
        if (sp > 0f) {
            val s = 1f + 1.2f * (1 - easeOut(sp))
            c.save(); c.scale(s, s, SW / 2f, 1080f)
            val n = c.saveLayerAlpha(0f, 800f, SW.toFloat(), 1400f, (255 * min(1f, sp * 2)).toInt())
            bigText(c, fa(if (en) "$hsF - $asF" else "$asF - $hsF"), SW / 2f, 960f, 240f, look.ink, a.black, 1f)
            c.restoreToCount(n); c.restore()
        }
        c.enter(clk.at(1.3f), 1260f, 1340f) {
            val line = when { hsF > asF -> L("{0} برد", teamName(m.homeId, m.homeName)); asF > hsF -> L("{0} برد", teamName(m.awayId, m.awayName)); else -> L("مساوی") }
            text(c, line, SW / 2f, 1260f, 46f, look.accent, a.black, SW - 160)
        }
        // the brand, big
        val swell = 1f + 0.12f * sin(PI.toFloat() * clk.at(2.4f, 0.5f))
        c.save(); c.scale(swell, swell, SW / 2f, 1560f)
        c.enter(clk.at(1.6f), 1440f, 1760f) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            a.logo?.let { p.color = 0xFFFFFFFF.toInt(); c.drawCircle(SW / 2f, 1520f, 74f, p); c.drawBitmap(it, null, RectF(SW / 2f - 66f, 1454f, SW / 2f + 66f, 1586f), p) }
            text(c, L("گل‌لایو"), SW / 2f, 1620f, 64f, look.ink, a.black, 500)
            text(c, L("نتایج زنده فوتبال"), SW / 2f, 1712f, 32f, look.sub, a.bold, 600)
        }
        c.restore()
    }
    return ReelScene(bg, a.hc, a.ac, parts, a, m)
}

/** One goal: the shot on a tilted pitch, the ball flying in, «گل!», and the scorer. */
private fun goalPart(c: Canvas, t: Float, len: Float, impact: Float, e: JSONObject, home: Boolean, a: CardAssets, look: CardLook, faces: Map<Int, Bitmap?>, m: MatchData) {
    val en = Lang.en
    val clk = Clock(t)
    val col = brighten(if (home) a.hc else a.ac)
    val shot = e.o("shotmapEvent")?.takeIf { !it.b("isOwnGoal") && !e.b("ownGoal") }
    // the pitch: the attacked third, goal at the top, seen from behind the shooter
    val top = 300f; val bottom = 1180f
    c.enter(clk.at(0f, 0.4f), top, bottom, 60f) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val panel = RectF(40f, top, SW - 40f, bottom)
        c.save()
        c.clipPath(Path().apply { addRoundRect(panel, 46f, 46f, Path.Direction.CW) })
        // stands and lights behind the goal
        p.shader = LinearGradient(0f, top, 0f, top + 260f, intArrayOf(0xFF05070A.toInt(), 0xFF101820.toInt()), null, Shader.TileMode.CLAMP); c.drawRect(panel, p); p.shader = null
        val rnd = java.util.Random(3)
        repeat(260) { p.color = (listOf(0x55FFFFFF, (col and 0x00FFFFFF) or 0x66000000, 0x33FFFFFF)[rnd.nextInt(3)]); c.drawCircle(60f + rnd.nextFloat() * (SW - 120f), top + 20f + rnd.nextFloat() * 150f, 3f + rnd.nextFloat() * 3f, p) }
        for (lx in listOf(200f, SW - 200f)) { p.shader = RadialGradient(lx, top + 30f, 260f, 0x55FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP); c.drawCircle(lx, top + 30f, 260f, p); p.shader = null }
        // grass in bands toward the camera
        for (k in 0 until 8) {
            val y0 = proj(105f - k * 4.5f, 34f).second; val y1 = proj(105f - (k + 1) * 4.5f, 34f).second
            p.color = if (k % 2 == 0) 0xFF1E7A44.toInt() else 0xFF1A6B3C.toInt(); c.drawRect(40f, y0, SW - 40f, y1 + 1f, p)
        }
        p.color = 0xFF1A6B3C.toInt(); c.drawRect(40f, proj(105f - 8 * 4.5f, 34f).second, SW - 40f, bottom, p)
        // markings
        p.style = Paint.Style.STROKE; p.strokeWidth = 4f; p.color = 0xB3FFFFFF.toInt()
        fun line(x0: Float, y0: Float, x1: Float, y1: Float) { val a0 = proj(x0, y0); val a1 = proj(x1, y1); c.drawLine(a0.first, a0.second, a1.first, a1.second, p) }
        line(105f, -10f, 105f, 78f)
        for ((depth, half) in listOf(16.5f to 20.16f, 5.5f to 9.16f)) { line(105f, 34f - half, 105f - depth, 34f - half); line(105f - depth, 34f - half, 105f - depth, 34f + half); line(105f - depth, 34f + half, 105f, 34f + half) }
        val spot = proj(94f, 34f); p.style = Paint.Style.FILL; c.drawCircle(spot.first, spot.second, 5f, p); p.style = Paint.Style.STROKE
        val arc = Path(); for (k in 0..24) { val an = Math.toRadians(-53.0 + k * 106.0 / 24); val pt = proj(94f - 9.15f * kotlin.math.cos(an).toFloat(), 34f + 9.15f * kotlin.math.sin(an).toFloat()); if (k == 0) arc.moveTo(pt.first, pt.second) else arc.lineTo(pt.first, pt.second) }
        c.drawPath(arc, p)
        // the goal: posts, bar and the net behind
        val gl = proj(105f, 30.34f); val gr = proj(105f, 37.66f)
        val gh = GOAL_H * gl.third
        val flash = if (t >= impact) (1f - (t - impact) / 0.6f).coerceIn(0f, 1f) else 0f
        p.style = Paint.Style.FILL; p.color = 0x22FFFFFF
        c.drawRect(gl.first + 10f, gl.second - gh - 26f, gr.first - 10f, gl.second - 18f, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 1.5f; p.color = 0x55FFFFFF
        var nx = gl.first; while (nx < gr.first) { c.drawLine(nx, gl.second - gh, nx + 10f, gl.second - gh - 26f, p); nx += 12f }
        var ny = gl.second - gh; while (ny < gl.second) { c.drawLine(gl.first, ny, gr.first, ny - 10f, p); ny += 12f }
        p.strokeWidth = 7f; p.color = 0xFFFFFFFF.toInt(); p.strokeCap = Paint.Cap.ROUND
        c.drawLine(gl.first, gl.second, gl.first, gl.second - gh, p); c.drawLine(gr.first, gr.second, gr.first, gr.second - gh, p); c.drawLine(gl.first, gl.second - gh, gr.first, gr.second - gh, p)
        p.style = Paint.Style.FILL
        if (flash > 0f) { p.shader = RadialGradient((gl.first + gr.first) / 2, gl.second - gh / 2, 260f, (0xFFFFFF) or ((flash * 200).toInt() shl 24), 0x00FFFFFF, Shader.TileMode.CLAMP); c.drawCircle((gl.first + gr.first) / 2, gl.second - gh / 2, 260f, p); p.shader = null }
        // vignette
        p.shader = RadialGradient(SW / 2f, (top + bottom) / 2, 760f, intArrayOf(0x00000000, 0x88000000.toInt()), null, Shader.TileMode.CLAMP); c.drawRect(panel, p); p.shader = null
        // the shot: where it was struck, then the ball's flight
        shot?.let { s ->
            val sx = (s.d("x") ?: 94.0).toFloat().coerceIn(60f, 104f); val sy = (s.d("y") ?: 34.0).toFloat()
            val ey = (s.d("goalCrossedY") ?: 34.0).toFloat().coerceIn(30.6f, 37.4f); val ez = (s.d("goalCrossedZ") ?: 0.5).toFloat().coerceIn(0.15f, 2.3f)
            val xg = s.d("expectedGoals") ?: 0.1
            val start = proj(sx, sy)
            val mp = clk.at(0.45f, 0.4f)
            if (mp > 0f) {
                val r = (18f + 50f * kotlin.math.sqrt(xg).toFloat()) * start.third
                p.color = (col and 0x00FFFFFF) or 0x55000000; c.drawCircle(start.first, start.second, r * 1.8f * easeOut(mp), p)
                p.color = col; c.drawCircle(start.first, start.second, r * easeBack(mp), p)
                p.style = Paint.Style.STROKE; p.strokeWidth = 4f; p.color = 0xFFFFFFFF.toInt(); c.drawCircle(start.first, start.second, r * easeBack(mp), p); p.style = Paint.Style.FILL
                if (mp >= 1f) pill(c, "xG " + fa(dec(xg, 2)), start.first, start.second + r + 16f, 26f, 0xFFFFFFFF.toInt(), 0xCC0B0F14.toInt(), a.black, 240)
            }
            val fl = ((t - 1.0f) / (impact - 1.0f)).coerceIn(0f, 1f)
            if (t >= 1.0f) {
                val dist = abs(105f - sx) + abs(ey - sy)
                val peak = max(ez, 0.5f) + dist * 0.045f
                fun at(q: Float): Triple<Float, Float, Float> {
                    val x = sx + (105.4f - sx) * q; val y = sy + (ey - sy) * q
                    val h = ez * q + 4 * (peak - ez * 0.5f) * q * (1 - q) * 0.5f
                    val g = proj(x, y); return Triple(g.first, g.second - h * g.third * PX_PER_M, g.third)
                }
                // trail
                val n = 18
                for (k in 0 until n) {
                    val q = fl * k / n
                    val (x, y, sc) = at(q)
                    p.color = 0xFFFFFFFF.toInt(); p.alpha = (30 + 120 * k / n); c.drawCircle(x, y, 6f * sc, p)
                }
                p.alpha = 255
                val (bx, by, bs) = at(fl)
                val ground = proj(sx + (105.4f - sx) * fl, sy + (ey - sy) * fl)
                p.color = 0x66000000; c.drawOval(RectF(ground.first - 16f * bs, ground.second - 5f * bs, ground.first + 16f * bs, ground.second + 5f * bs), p)
                ball(c, bx, by, 15f * bs, look)
            }
        }
        c.restore()
        p.style = Paint.Style.STROKE; p.strokeWidth = 2.5f; p.color = look.stroke; c.drawRoundRect(panel, 46f, 46f, p)
    }
    // «گل!» bursts out
    val gp = clk.at(impact + 0.05f, 0.45f)
    if (gp > 0f) {
        val shake = if (t < impact + 0.6f) sin(t * 70f) * 6f * (1 - gp) else 0f
        c.pop(gp, SW / 2f, 760f) {
            val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 230f; typeface = a.black }
            val word = if (e.b("ownGoal")) L("گل به خودی!") else L("گل!")
            val size = if (tp.measureText(word) > SW - 120f) 230f * (SW - 120f) / tp.measureText(word) else 230f
            c.save(); c.translate(shake, 0f)
            // white letters with a thick edge in the team's colour and a shadow under them
            text(c, word, SW / 2f + 8f, 640f + 14f, size, 0x99000000.toInt(), a.black, SW)
            for (k in 0 until 12) {
                val an = k * PI.toFloat() / 6
                text(c, word, SW / 2f + kotlin.math.cos(an) * 9f, 640f + kotlin.math.sin(an) * 9f, size, col, a.black, SW)
            }
            text(c, word, SW / 2f, 640f, size, 0xFFFFFFFF.toInt(), a.black, SW)
            c.restore()
        }
        // confetti in the team's colour
        val cp = (t - impact) / 2.2f
        if (cp in 0f..1f) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            val rnd = java.util.Random(e.i("eventId")?.toLong() ?: 9L)
            repeat(70) {
                val an = rnd.nextFloat() * 2 * PI.toFloat(); val sp = 400f + rnd.nextFloat() * 700f
                val x = SW / 2f + kotlin.math.cos(an) * sp * cp; val y = 760f + kotlin.math.sin(an) * sp * cp * 0.8f + 900f * cp * cp
                p.color = if (rnd.nextBoolean()) col else 0xFFFFFFFF.toInt(); p.alpha = (255 * (1 - cp)).toInt()
                c.save(); c.rotate(rnd.nextFloat() * 360f + cp * 400f, x, y); c.drawRect(x - 9f, y - 5f, x + 9f, y + 5f, p); c.restore()
            }
        }
    }
    // the scorer
    val cardTop = 1230f
    c.enter(clk.at(impact + 0.6f), cardTop, cardTop + 420f, 120f) {
        glass(c, 56f, cardTop, SW - 56f, cardTop + 420f, look)
        val pid = e.i("playerId") ?: e.o("player")?.i("id") ?: 0
        val fx = if (en) 56f + 60f + 130f else SW - 56f - 60f - 130f
        faceRing(c, faces[pid], fx, cardTop + 210f, 130f, col, glow = false)
        val tx = if (en) fx + 160f else fx - 160f
        val w = (SW - 112f - 60f - 300f - 40f).toInt()
        fun line(s: String, y: Float, size: Float, color: Int, bold: Boolean) {
            val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; typeface = if (bold) a.black else a.bold }
            val sw = min(tp.measureText(s), w.toFloat())
            text(c, s, if (en) tx + sw / 2 else tx - sw / 2, y, size, color, if (bold) a.black else a.bold, (sw + 24).toInt())
        }
        val name = personName(e.s("nameStr") ?: e.s("fullName") ?: "")
        line(name, cardTop + 70f, 58f, look.ink, true)
        e.s("assistInput")?.takeIf { it.isNotBlank() }?.let { line(L("پاس گل: {0}", personName(it)), cardTop + 160f, 34f, look.sub, false) }
            ?: e.s("assistStr")?.takeIf { it.isNotBlank() }?.let { line(L("پاس گل: {0}", personName(it.removePrefix("assist by ").trim())), cardTop + 160f, 34f, look.sub, false) }
        val how = when {
            e.b("ownGoal") -> L("گل به خودی")
            isPen(e) -> L("پنالتی")
            shot?.s("shotType") == "Header" -> L("ضربه سر")
            shot?.s("situation") == "FreeKick" || (shot?.s("situation") == "SetPiece" && shot?.s("shotType") != "Header") -> L("ضربه ایستگاهی")
            shot?.s("shotType") == "LeftFoot" -> L("پای چپ")
            shot?.s("shotType") == "RightFoot" -> L("پای راست")
            else -> ""
        }
        line(listOf(fa(minuteText(e) + "'"), how).filter { it.isNotEmpty() }.joinToString("  ·  "), cardTop + 250f, 36f, look.accent, true)
        shot?.d("expectedGoals")?.let { xg -> line(L("احتمال گل این موقعیت: {0}٪", fa((xg * 100).roundToInt())), cardTop + 320f, 30f, look.sub, false) }
    }
}

/** A red card: the card itself, the player and the minute. */
private fun redPart(c: Canvas, t: Float, e: JSONObject, a: CardAssets, look: CardLook, faces: Map<Int, Bitmap?>) {
    val clk = Clock(t)
    c.pop(clk.at(0.2f, 0.5f), SW / 2f, 620f) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE5243B.toInt(); setShadowLayer(40f, 0f, 16f, 0x99000000.toInt()) }
        c.save(); c.rotate(-10f, SW / 2f, 620f); c.drawRoundRect(RectF(SW / 2f - 110f, 460f, SW / 2f + 110f, 780f), 24f, 24f, p); c.restore()
    }
    c.enter(clk.at(0.6f), 860f, 1300f) {
        val pid = e.i("playerId") ?: e.o("player")?.i("id") ?: 0
        faceRing(c, faces[pid], SW / 2f, 1000f, 120f, 0xFFE5243B.toInt())
        text(c, personName(e.s("nameStr") ?: e.s("fullName") ?: ""), SW / 2f, 1150f, 60f, look.ink, a.black, SW - 120)
        text(c, L("کارت قرمز · دقیقه {0}", fa(minuteText(e))), SW / 2f, 1240f, 36f, 0xFFFF6B7A.toInt(), a.black, SW - 120)
    }
}

/** The momentum line drawn from kick-off to the end, goals popping up as it passes them. */
private fun momentumPart(c: Canvas, t: Float, mom: List<JSONObject>, goals: List<JSONObject>, a: CardAssets, look: CardLook, m: MatchData) {
    val en = Lang.en
    val clk = Clock(t)
    c.enter(clk.at(0.1f), 300f, 380f) { text(c, L("مومنتوم بازی"), SW / 2f, 300f, 60f, look.ink, a.black, SW - 120) }
    val top = 440f; val h = 760f
    c.enter(clk.at(0.25f), top, top + h) {
        glass(c, 56f, top, SW - 56f, top + h, look)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val mid = top + h / 2; val left = 110f; val width = SW - 220f; val amp = h * 0.38f
        val maxMin = max(90.0, mom.maxOf { it.d("minute") ?: 0.0 }).toFloat()
        fun xOf(mn: Float) = if (en) left + mn / maxMin * width else left + width - mn / maxMin * width
        val drawn = maxMin * easeOut(clk.at(0.5f, 2.6f))
        val curve = Path().apply {
            moveTo(xOf(0f), mid)
            var px = xOf(0f); var py = mid
            for (pt in mom) {
                val mn = (pt.d("minute") ?: 0.0).toFloat(); if (mn > drawn) break
                val x = xOf(mn); val yv = mid - (pt.d("value") ?: 0.0).toFloat() / 100f * amp
                quadTo(px, py, (px + x) / 2, (py + yv) / 2); px = x; py = yv
            }
            lineTo(px, mid); close()
        }
        c.save(); c.clipRect(0f, top, SW.toFloat(), mid)
        p.shader = LinearGradient(0f, mid - amp, 0f, mid, brighten(a.hc), brighten(a.hc) and 0x00FFFFFF or 0x33000000, Shader.TileMode.CLAMP); c.drawPath(curve, p); c.restore()
        c.save(); c.clipRect(0f, mid, SW.toFloat(), top + h)
        p.shader = LinearGradient(0f, mid, 0f, mid + amp, brighten(a.ac) and 0x00FFFFFF or 0x33000000, brighten(a.ac), Shader.TileMode.CLAMP); c.drawPath(curve, p); c.restore()
        p.shader = null
        p.color = look.sub; p.alpha = 110; c.drawRect(left, mid - 1.5f, left + width, mid + 1.5f, p); p.alpha = 255
        p.color = look.stroke; c.drawRect(xOf(45f) - 1.5f, mid - amp, xOf(45f) + 1.5f, mid + amp, p)
        crestDisc(c, a.home, if (en) left - 10f else left + width + 10f, mid - amp + 30f, 34f)
        crestDisc(c, a.away, if (en) left - 10f else left + width + 10f, mid + amp - 30f, 34f)
        for (g in goals) {
            val mn = (minuteText(g).substringBefore('+').filter(Char::isDigit).toFloatOrNull() ?: continue).coerceAtMost(maxMin)
            if (mn > drawn) continue
            ball(c, xOf(mn), if (g.b("isHome")) mid - amp - 10 else mid + amp + 10, 20f, look)
        }
    }
    // who had the better of it
    val hSum = mom.sumOf { max(0.0, it.d("value") ?: 0.0) }; val aSum = mom.sumOf { max(0.0, -(it.d("value") ?: 0.0)) }
    c.enter(clk.at(3.0f), top + h + 40f, top + h + 120f) {
        val who = if (abs(hSum - aSum) < (hSum + aSum) * 0.08) L("بازی متعادل بود") else L("برتری بیشتر: {0}", if (hSum > aSum) teamName(m.homeId, m.homeName) else teamName(m.awayId, m.awayName))
        text(c, who, SW / 2f, top + h + 50f, 42f, look.accent, a.black, SW - 120)
    }
}

/* ---------- the pitch seen from behind the shooter ---------- */

private const val PX_PER_M = 26f
private const val GOAL_H = 2.44f * PX_PER_M * 1.35f

/** Pitch metres (x toward the attacked goal at 105, y across 0..68) to the screen, and the scale there. */
private fun proj(x: Float, y: Float): Triple<Float, Float, Float> {
    val d = ((105f - x) / 36f).coerceIn(-0.2f, 1.4f)     // 0 on the goal line, 1 at 36 m out
    val z = 1f + 0.55f * (1f - d)                         // farther away toward the goal
    val sc = 1.55f / z
    val ys = 380f + (1180f - 380f) * (1.55f / z - 1.55f / 1.55f) / (1.55f - 1f) + 40f
    val xs = SW / 2f + (y - 34f) * PX_PER_M * sc * 0.95f
    return Triple(xs, ys, sc)
}

/** One side's eleven as pitch tokens (keeper at the bottom), with GoalLive ratings when there are some. */
private fun lineupMen(team: JSONObject, ratings: Map<Int, PlayerRating>): List<PitchMan> {
    val st = team.a("starters").objs()
    val rated = st.mapNotNull { p -> (p.i("id") ?: 0).let { id -> ratings[id]?.value?.let { id to it } } }
    val bestId = rated.maxByOrNull { it.second }?.first
    return st.mapNotNull { p ->
        val v = p.o("verticalLayout") ?: return@mapNotNull null
        val id = p.i("id") ?: 0
        val ev = p.path("performance").a("events").objs().map { it.s("type") }
        PitchMan(id, personName(p.s("lastName") ?: (p.s("name") ?: "").substringAfterLast(' ')),
            0.89f - (v.d("x") ?: 0.5).toFloat() * 0.78f, 0.86f - ((v.d("y") ?: 0.5).toFloat() - 0.1f) / 0.77f * 0.74f,
            p.s("shirtNumber") ?: "", ratings[id]?.value,
            ev.count { it == "goal" }, ev.count { it == "ownGoal" }, ev.count { it == "assist" }, "yellowCard" in ev, "redCard" in ev, null,
            p.b("isCaptain"), star = id == bestId)
    }
}

/** Everyone whose face the reel shows: both line-ups, the scorers, the sent off. */
internal fun reelFaces(m: MatchData): List<Int> =
    (listOf("homeTeam", "awayTeam").flatMap { m.content.path("lineup.$it").a("starters").objs().mapNotNull { p -> p.i("id") } } +
        m.events.filter { it.s("type") == "Goal" || it.s("type") == "Card" }.mapNotNull { it.i("playerId") ?: it.o("player")?.i("id") }).distinct()
