package app.goallive.live.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import app.goallive.live.L
import app.goallive.live.data.Api
import app.goallive.live.data.Clubs
import app.goallive.live.data.Store
import app.goallive.live.data.a
import app.goallive.live.data.b
import app.goallive.live.data.d
import app.goallive.live.data.i
import app.goallive.live.data.o
import app.goallive.live.data.objs
import app.goallive.live.data.path
import app.goallive.live.data.s
import org.json.JSONArray
import org.json.JSONObject

/*
 * The tactics studio's model. Everything on the pitch is in pitch shares: u across (0 the left touchline, 1 the right one,
 * as the home side sees it attacking up) and v down the pitch (0 the top goal line, 1 the bottom one). The home side
 * defends the bottom goal, the away side the top one.
 */

/** A side: its name, shirt colour and number ink; [teamId] when it is a real club (for crests, photos and its squad). */
class STeam(val name: String, val color: Int, val ink: Int, val teamId: Int = 0, val formation: String = "4-3-3") {
    fun json(): JSONObject = JSONObject().put("name", name).put("color", color).put("ink", ink).put("teamId", teamId).put("formation", formation)
    companion object {
        fun of(j: JSONObject?) = j?.let { STeam(it.optString("name"), it.optInt("color"), it.optInt("ink", -1), it.optInt("teamId"), it.optString("formation", "4-3-3")) }
    }
}

/** A player on the board; [side] 0 home, 1 away; [pid] the real player's id (0 for a made-up one). */
class SPlayer(val key: Int, val side: Int, u: Float, v: Float, num: String, name: String, pid: Int = 0, spot: Boolean = false) {
    var u by mutableFloatStateOf(u)
    var v by mutableFloatStateOf(v)
    var num by mutableStateOf(num)
    var name by mutableStateOf(name)
    var pid by mutableIntStateOf(pid)
    var spot by mutableStateOf(spot)
    fun json(): JSONObject = JSONObject().put("k", key).put("s", side).put("u", u.toDouble()).put("v", v.toDouble()).put("n", num).put("name", name)
        .put("pid", pid).put("spot", spot)
    companion object {
        fun of(j: JSONObject) = SPlayer(j.optInt("k"), j.optInt("s"), j.optDouble("u").toFloat(), j.optDouble("v").toFloat(), j.optString("n"),
            j.optString("name"), j.optInt("pid"), j.optBoolean("spot"))
    }
}

/**
 * Something drawn: [kind] is one of [MARK_KINDS]; [pts] its points in pitch shares (two ends for a straight one, the finger's path
 * for a run or a dribble, ends and a bend for a curve, a corner pair for a box, centre and edge for a circle).
 */
class SMark(val kind: String, val pts: List<Offset>, val color: Int, val width: Float, val alpha: Float, val text: String = "", val layer: String = "attack") {
    fun copy(pts: List<Offset> = this.pts, color: Int = this.color, width: Float = this.width, alpha: Float = this.alpha, text: String = this.text, layer: String = this.layer) =
        SMark(kind, pts, color, width, alpha, text, layer)
    fun json(): JSONObject = JSONObject().put("kind", kind).put("pts", JSONArray().apply { pts.forEach { put(it.x.toDouble()); put(it.y.toDouble()) } })
        .put("c", color).put("w", width.toDouble()).put("a", alpha.toDouble()).put("t", text).put("l", layer)
    companion object {
        fun of(j: JSONObject): SMark {
            val a = j.optJSONArray("pts") ?: JSONArray()
            val pts = (0 until a.length() / 2).map { Offset(a.optDouble(2 * it).toFloat(), a.optDouble(2 * it + 1).toFloat()) }
            return SMark(j.optString("kind"), pts, j.optInt("c"), j.optDouble("w", 3.0).toFloat(), j.optDouble("a", 1.0).toFloat(), j.optString("t"), j.optString("l", "attack"))
        }
    }
}

/**
 * One step of a move: where everyone stands and where the ball is when it ends; [dur] seconds to get there from the step before,
 * the ball in the air on the way when [air]; [note] shown under the pitch; [paths] the routes drawn by finger (player key → points).
 */
class SFrame(val pos: Map<Int, Offset>, val ball: Offset, val air: Boolean = false, val dur: Float = 1.2f, val note: String = "",
             val paths: Map<Int, List<Offset>> = emptyMap()) {
    fun copy(pos: Map<Int, Offset> = this.pos, ball: Offset = this.ball, air: Boolean = this.air, dur: Float = this.dur, note: String = this.note,
             paths: Map<Int, List<Offset>> = this.paths) = SFrame(pos, ball, air, dur, note, paths)
    fun json(): JSONObject = JSONObject()
        .put("pos", JSONObject().apply { pos.forEach { (k, o) -> put("$k", JSONArray().put(o.x.toDouble()).put(o.y.toDouble())) } })
        .put("ball", JSONArray().put(ball.x.toDouble()).put(ball.y.toDouble())).put("air", air).put("dur", dur.toDouble()).put("note", note)
        .put("paths", JSONObject().apply { paths.forEach { (k, ps) -> put("$k", JSONArray().apply { ps.forEach { put(it.x.toDouble()); put(it.y.toDouble()) } }) } })
    companion object {
        private fun pt(a: JSONArray?) = Offset(a?.optDouble(0, 0.5)?.toFloat() ?: 0.5f, a?.optDouble(1, 0.5)?.toFloat() ?: 0.5f)
        fun of(j: JSONObject): SFrame {
            val pos = HashMap<Int, Offset>()
            j.optJSONObject("pos")?.let { o -> o.keys().forEach { k -> pos[k.toIntOrNull() ?: 0] = pt(o.optJSONArray(k)) } }
            val paths = HashMap<Int, List<Offset>>()
            j.optJSONObject("paths")?.let { o -> o.keys().forEach { k ->
                val a = o.optJSONArray(k) ?: return@forEach
                paths[k.toIntOrNull() ?: 0] = (0 until a.length() / 2).map { Offset(a.optDouble(2 * it).toFloat(), a.optDouble(2 * it + 1).toFloat()) }
            } }
            return SFrame(pos, pt(j.optJSONArray("ball")), j.optBoolean("air"), j.optDouble("dur", 1.2).toFloat(), j.optString("note"), paths)
        }
    }
}

/** What can be drawn, in the toolbar's order. */
val MARK_KINDS = listOf("pass", "run", "dribble", "curve", "lob", "both", "zone", "rect", "circle", "dist", "hline", "text")

/** Kinds drawn by two ends (dragged straight from the first touch to the last). */
val TWO_POINT = setOf("pass", "both", "dist", "rect", "circle", "curve", "lob")

/** The layers drawings go on; each can be hidden. */
val LAYERS = listOf("attack", "defense", "notes")
fun layerName(l: String) = when (l) { "attack" -> L("حمله"); "defense" -> L("دفاع"); else -> L("یادداشت") }

/** The pens' colours. */
val PENS = listOf(0xFFFFFFFF, 0xFFF5C542, 0xFF39D98A, 0xFF35B6FF, 0xFFFF5A6E, 0xFFB57BFF, 0xFFFF9F43, 0xFF111111).map { it.toInt() }

val STUDIO_FORMATIONS = listOf("4-3-3", "4-2-3-1", "4-4-2", "4-1-4-1", "3-5-2", "3-4-3", "5-3-2", "4-4-1-1", "4-3-1-2", "3-4-2-1", "4-5-1", "5-4-1")

/** Shirt colours for a made-up side. */
val KIT_COLORS = listOf(0xFFD7263D, 0xFF1D4ED8, 0xFFFFFFFF, 0xFF111827, 0xFFF5C542, 0xFF16A34A, 0xFF7C3AED, 0xFFF97316, 0xFF0EA5E9, 0xFFEC4899, 0xFF8B1E3F, 0xFF64748B).map { it.toInt() }

fun inkFor(c: Int): Int = if (androidx.core.graphics.ColorUtils.calculateLuminance(c or 0xFF000000.toInt()) > 0.55) 0xFF0B0F14.toInt() else 0xFFFFFFFF.toInt()

/** Eleven places for [formation], keeper first, for the home side (bottom); [mirror] puts them at the top for the away side. */
fun formationSpots(formation: String, mirror: Boolean): List<Offset> {
    val lines = formation.split('-').mapNotNull { it.trim().toIntOrNull() }.ifEmpty { listOf(4, 3, 3) }
    val out = arrayListOf(Offset(0.5f, 0.94f))
    lines.forEachIndexed { li, n ->
        val v = 0.80f - li * (0.27f / (lines.size - 1).coerceAtLeast(1))
        for (k in 0 until n) {
            // full backs and wide men further out, a lone man in the middle
            val u = if (n == 1) 0.5f else 0.12f + 0.76f * k / (n - 1)
            out += Offset(u, v)
        }
    }
    return out.take(11).map { if (mirror) Offset(1 - it.x, 1 - it.y) else it }
}

/** A team the studio fetched: its colours, the line-up it last played and its squad, to pick from. */
class LoadedTeam(val team: STeam, val xi: List<SPlayer>, val squad: List<Triple<Int, String, String>>)

/** Fetches a club: its colours, the eleven of its last match (or the best guess from its squad), and its squad. */
suspend fun loadTeam(id: Int, side: Int): LoadedTeam {
    val d = Api.fm("teams", mapOf("id" to id), 3_600_000)
    val name = teamName(id, d.o("details").s("name") ?: "")
    val club = Clubs.get(id)
    val color = club?.let { Color(it.c1) } ?: parseColor(d.path("overview.teamColors").s("darkMode")) ?: parseColor(d.path("overview.teamColors").s("color")) ?: Color(0xFF1D4ED8)
    val c = color.toArgb()
    // the squad, keepers first
    val groups = d.o("squad").a("squad").objs()
    val squad = groups.filter { it.s("title") != "coach" }.flatMap { g -> g.a("members").objs().map { Triple(it.i("id") ?: 0, personName(shortName(it.s("name") ?: "")), it.s("shirtNumber") ?: "") } }
    val mirror = side == 1
    // the eleven of the last match, where they stood
    val last = d.path("overview.lastMatch").i("id")
    var formation = "4-3-3"
    val xi = runCatching {
        val md = Api.fm("matchDetails", mapOf("matchId" to (last ?: 0)), 3_600_000)
        val lu = md.path("content.lineup")
        val t = listOfNotNull(lu.o("homeTeam"), lu.o("awayTeam")).firstOrNull { it.i("id") == id } ?: error("no line-up")
        t.s("formation")?.let { formation = it }
        t.a("starters").objs().mapIndexedNotNull { k, p ->
            val vl = p.o("verticalLayout") ?: return@mapIndexedNotNull null
            val x = (vl.d("x") ?: 0.5).toFloat(); val y = (vl.d("y") ?: 0.5).toFloat()
            // FotMob: x 0 is the right back seen from behind the team; y 0.1 the keeper, 0.87 the strikers
            val u = 0.94f - x * 0.88f; val v = 0.94f - (y - 0.1f) / 0.77f * 0.42f
            SPlayer(side * 100 + k, side, if (mirror) 1 - u else u, if (mirror) 1 - v else v, p.s("shirtNumber") ?: "${k + 1}",
                personName(p.s("lastName") ?: shortName(p.s("name") ?: "")), p.i("id") ?: 0)
        }.takeIf { it.size >= 10 } ?: error("short")
    }.getOrElse {
        // no line-up: the likeliest eleven from the squad, by position
        fun grp(t: String) = groups.firstOrNull { it.s("title") == t }?.a("members").objs()
        val picks = grp("keepers").take(1) + grp("defenders").take(4) + grp("midfielders").take(3) + grp("attackers").take(3)
        formationSpots(formation, mirror).mapIndexed { k, at ->
            val p = picks.getOrNull(k)
            SPlayer(side * 100 + k, side, at.x, at.y, p?.s("shirtNumber") ?: "${k + 1}", p?.let { personName(shortName(it.s("name") ?: "")) } ?: "", p?.i("id") ?: 0)
        }
    }
    return LoadedTeam(STeam(name, c, inkFor(c), id, formation), xi, squad)
}

private fun shortName(full: String) = full.trim().substringAfterLast(' ')

/** A made-up eleven numbered 1 to 11 in [formation]. */
fun plainEleven(side: Int, formation: String): List<SPlayer> =
    formationSpots(formation, side == 1).mapIndexed { k, at -> SPlayer(side * 100 + k, side, at.x, at.y, "${k + 1}", "") }

/* ---------- the board, its history and the saved ones ---------- */

/** Everything on the board, with undo / redo by snapshots and an autosave. */
class StudioBoard {
    val players = mutableStateListOf<SPlayer>()
    val marks = mutableStateListOf<SMark>()
    var ball by mutableStateOf(Offset(0.5f, 0.5f))
    var home by mutableStateOf(STeam(L("تیم من"), 0xFFD7263D.toInt(), 0xFFFFFFFF.toInt()))
    var away by mutableStateOf(STeam(L("حریف"), 0xFF1D4ED8.toInt(), 0xFFFFFFFF.toInt()))
    var showAway by mutableStateOf(true)
    var title by mutableStateOf("")
    /** The steps of the move, and the one on the board now. */
    val frames = mutableStateListOf<SFrame>()
    var cur by mutableIntStateOf(0)
    /** Squads of the real teams on the board, for swapping a player in. */
    val squads = HashMap<Int, List<Triple<Int, String, String>>>()

    private val undoStack = ArrayDeque<String>()
    private val redoStack = ArrayDeque<String>()
    private var last = ""
    var version by mutableIntStateOf(0)
        private set
    val canUndo get() = version >= 0 && undoStack.isNotEmpty()
    val canRedo get() = version >= 0 && redoStack.isNotEmpty()

    fun json(): JSONObject = JSONObject()
        .put("frames", JSONArray().apply { frames.forEach { put(it.json()) } }).put("cur", cur)
        .put("players", JSONArray().apply { players.forEach { put(it.json()) } })
        .put("marks", JSONArray().apply { marks.forEach { put(it.json()) } })
        .put("ball", JSONArray().put(ball.x.toDouble()).put(ball.y.toDouble()))
        .put("home", home.json()).put("away", away.json()).put("showAway", showAway).put("title", title)
        .put("squads", JSONObject().apply { squads.forEach { (k, v) -> put("$k", JSONArray().apply { v.forEach { put(JSONArray().put(it.first).put(it.second).put(it.third)) } }) } })

    fun load(j: JSONObject) {
        players.clear(); j.optJSONArray("players").objs().forEach { players += SPlayer.of(it) }
        marks.clear(); j.optJSONArray("marks").objs().forEach { marks += SMark.of(it) }
        j.optJSONArray("ball")?.let { ball = Offset(it.optDouble(0, 0.5).toFloat(), it.optDouble(1, 0.5).toFloat()) }
        STeam.of(j.optJSONObject("home"))?.let { home = it }
        STeam.of(j.optJSONObject("away"))?.let { away = it }
        showAway = j.optBoolean("showAway", true); title = j.optString("title")
        frames.clear(); j.optJSONArray("frames").objs().forEach { frames += SFrame.of(it) }
        cur = j.optInt("cur").coerceIn(0, (frames.size - 1).coerceAtLeast(0))
        if (frames.isEmpty()) frames += frameNow(null) else applyFrame(frames[cur])
        squads.clear()
        j.optJSONObject("squads")?.let { sq -> sq.keys().forEach { k ->
            val arr = sq.optJSONArray(k) ?: return@forEach
            squads[k.toIntOrNull() ?: 0] = (0 until arr.length()).map { arr.getJSONArray(it).let { t -> Triple(t.optInt(0), t.optString(1), t.optString(2)) } }
        } }
    }

    /** Starts the history from what is on the board now. */
    fun reset() { syncOut(); undoStack.clear(); redoStack.clear(); last = json().toString(); version++ }

    /** After each finished change: a step to undo, and the board kept for next time. */
    fun commit() {
        syncOut()
        val now = json().toString()
        if (now == last) return
        undoStack.addLast(last); if (undoStack.size > 80) undoStack.removeFirst()
        redoStack.clear(); last = now; version++
        Store.prefs().edit().putString("studio.current", now).apply()
    }

    fun undo() { val prev = undoStack.removeLastOrNull() ?: return; redoStack.addLast(last); last = prev; load(JSONObject(prev)); version++; autosave() }
    fun redo() { val next = redoStack.removeLastOrNull() ?: return; undoStack.addLast(last); last = next; load(JSONObject(next)); version++; autosave() }
    private fun autosave() = Store.prefs().edit().putString("studio.current", last).apply()

    /** A fresh board: [home] against [away] in their formations. */
    fun fresh() {
        players.clear(); marks.clear(); squads.clear(); title = ""
        home = STeam(L("تیم من"), 0xFFD7263D.toInt(), 0xFFFFFFFF.toInt()); away = STeam(L("حریف"), 0xFF1D4ED8.toInt(), 0xFFFFFFFF.toInt())
        players += plainEleven(0, home.formation); players += plainEleven(1, away.formation)
        ball = Offset(0.5f, 0.5f); showAway = true
        frames.clear(); cur = 0; frames += frameNow(null)
    }

    /* ---------- the steps ---------- */

    /** The board as it stands, as a step (keeping [old]'s timing, note and routes). */
    fun frameNow(old: SFrame?) = SFrame(players.associate { it.key to Offset(it.u, it.v) }, ball, old?.air ?: false, old?.dur ?: 1.2f, old?.note ?: "",
        old?.paths?.filterKeys { k -> players.any { it.key == k } } ?: emptyMap())

    /** Keeps what is on the board in the current step. */
    fun syncOut() { if (frames.isEmpty()) { frames += frameNow(null); cur = 0 } else frames[cur.coerceIn(0, frames.size - 1)] = frameNow(frames.getOrNull(cur)) }

    fun applyFrame(f: SFrame) {
        players.forEach { p -> f.pos[p.key]?.let { p.u = it.x; p.v = it.y } }
        ball = f.ball
    }

    /** Shows step [k] on the board. */
    fun show(k: Int) {
        if (frames.isEmpty()) return
        syncOut(); cur = k.coerceIn(0, frames.size - 1); applyFrame(frames[cur])
    }

    /** A new step after this one, starting where this one ends. */
    fun addFrame() { syncOut(); frames.add(cur + 1, frames[cur].copy(paths = emptyMap(), note = "", air = false)); cur += 1; commit() }

    fun duplicateFrame(k: Int) { syncOut(); frames.add(k + 1, frames[k]); cur = k + 1; applyFrame(frames[cur]); commit() }

    fun deleteFrame(k: Int) {
        if (frames.size <= 1) return
        syncOut(); frames.removeAt(k); cur = cur.coerceAtMost(frames.size - 1).let { if (it > k) it - 1 else it }.coerceIn(0, frames.size - 1)
        applyFrame(frames[cur]); commit()
    }

    fun moveFrame(k: Int, by: Int) {
        val to = k + by
        if (to !in frames.indices) return
        syncOut(); val f = frames.removeAt(k); frames.add(to, f); cur = to; applyFrame(frames[cur]); commit()
    }

    fun setFrame(k: Int, f: SFrame) { frames[k] = f; if (k == cur) applyFrame(f); commit() }

    /** Puts [t]'s eleven on [side], replacing what was there. */
    fun setSide(side: Int, t: LoadedTeam) {
        players.removeAll { it.side == side }
        players += t.xi
        if (side == 0) home = t.team else away = t.team
        if (t.team.teamId > 0) squads[t.team.teamId] = t.squad
    }

    /** Re-forms [side] into [formation], keeping who plays (keeper stays first). */
    fun reform(side: Int, formation: String) {
        val mine = players.filter { it.side == side }
        val spots = formationSpots(formation, side == 1)
        // the keeper (deepest) first, then from the back line forwards, left to right
        val ordered = mine.sortedWith(compareBy<SPlayer> { if (side == 0) -it.v else it.v }.thenBy { if (side == 0) it.u else -it.u })
        ordered.forEachIndexed { k, p -> spots.getOrNull(k)?.let { p.u = it.x; p.v = it.y } }
        if (side == 0) home = STeam(home.name, home.color, home.ink, home.teamId, formation) else away = STeam(away.name, away.color, away.ink, away.teamId, formation)
    }

    /* ---------- the move between steps ---------- */

    /** Where everyone and the ball are at time [t] (in steps: 1.5 is half way from the second step to the third), and the ball's height. */
    fun at(t: Float): BoardMoment {
        val n = frames.size
        if (n < 2) return BoardMoment(frames.firstOrNull()?.pos ?: emptyMap(), frames.firstOrNull()?.ball ?: ball, 0f, 0f)
        val tc = t.coerceIn(0f, (n - 1).toFloat())
        val k = kotlin.math.floor(tc).toInt().coerceIn(0, n - 2)
        val f = tc - k
        val e = easeInOut(f)
        val f0 = frames[(k - 1).coerceAtLeast(0)]; val f1 = frames[k]; val f2 = frames[k + 1]; val f3 = frames[(k + 2).coerceAtMost(n - 1)]
        val pos = HashMap<Int, Offset>()
        for (p in players) {
            val p1 = f1.pos[p.key] ?: continue
            val p2 = f2.pos[p.key] ?: p1
            val route = f2.paths[p.key]
            pos[p.key] = if (route != null && route.size >= 2) along(route, e)
                else catmull(f0.pos[p.key] ?: p1, p1, p2, f3.pos[p.key] ?: p2, e)
        }
        // the ball: rolled along the ground (quick off the foot, slowing), or flighted with an arc above its shadow
        val b1 = f1.ball; val b2 = f2.ball
        val eb = 1 - (1 - f) * (1 - f)
        val travel = (b2 - b1).getDistance()
        return if (f2.air) BoardMoment(pos, b1 + (b2 - b1) * f, kotlin.math.sin(Math.PI * f).toFloat() * (0.04f + travel * 0.35f), f * travel)
        else BoardMoment(pos, if (travel < 0.002f) b2 else b1 + (b2 - b1) * eb, 0f, eb * travel)
    }
}

/** Everyone's place at one moment of a move; the ball's [height] above the grass (pitch shares) and how far it has rolled. */
class BoardMoment(val pos: Map<Int, Offset>, val ball: Offset, val height: Float, val roll: Float)

fun easeInOut(t: Float) = if (t < 0.5f) 4 * t * t * t else 1 - (-2 * t + 2).let { it * it * it } / 2

/** A smooth curve through four steps, at [t] between the middle two (so runs bend naturally instead of zig-zagging). */
fun catmull(p0: Offset, p1: Offset, p2: Offset, p3: Offset, t: Float): Offset {
    val t2 = t * t; val t3 = t2 * t
    return (p1 * 2f + (p2 - p0) * t + (p0 * 2f - p1 * 5f + p2 * 4f - p3) * t2 + (p1 * 3f - p0 - p2 * 3f + p3) * t3) * 0.5f
}

/** The point [t] of the way along a drawn route, by length. */
fun along(route: List<Offset>, t: Float): Offset {
    val lens = (0 until route.size - 1).map { (route[it + 1] - route[it]).getDistance() }
    val total = lens.sum()
    if (total <= 0f) return route.last()
    var d = t.coerceIn(0f, 1f) * total
    for (i in lens.indices) { if (d <= lens[i] || i == lens.size - 1) return route[i] + (route[i + 1] - route[i]) * (if (lens[i] > 0) (d / lens[i]).coerceIn(0f, 1f) else 1f); d -= lens[i] }
    return route.last()

}

/** A saved tactic in the phone's list. */
class SavedTactic(val id: Long, val name: String, val time: Long, val data: String)

object StudioLibrary {
    fun all(): List<SavedTactic> = runCatching {
        val a = JSONArray(Store.prefs().getString("studio.saved", "[]") ?: "[]")
        (0 until a.length()).map { a.getJSONObject(it) }.map { SavedTactic(it.optLong("id"), it.optString("name"), it.optLong("time"), it.optString("data")) }
    }.getOrDefault(emptyList()).sortedByDescending { it.time }

    private fun write(list: List<SavedTactic>) = Store.prefs().edit().putString("studio.saved",
        JSONArray().apply { list.forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("time", it.time).put("data", it.data)) } }.toString()).apply()

    fun save(name: String, data: String, id: Long = System.currentTimeMillis()) {
        write(all().filter { it.id != id } + SavedTactic(id, name, System.currentTimeMillis(), data))
    }

    fun delete(id: Long) = write(all().filter { it.id != id })
}

/** The look of the board, kept between visits. */
object StudioPrefs {
    var tilt by mutableStateOf(Store.prefs().getBoolean("studio.tilt", false))
    var range by mutableStateOf(Store.prefs().getString("studio.range", "full") ?: "full")
    var style by mutableStateOf(Store.prefs().getString("studio.style", "photo") ?: "photo")
    var names by mutableStateOf(Store.prefs().getBoolean("studio.names", true))
    var snap by mutableStateOf(Store.prefs().getBoolean("studio.snap", false))
    var grass by mutableStateOf(Store.prefs().getString("studio.grass", "green") ?: "green")
    /** Playing a move: speed, over and over, trails behind, the camera after the ball, the step before shown faintly. */
    var speed by mutableFloatStateOf(Store.prefs().getFloat("studio.speed", 1f))
    var loop by mutableStateOf(Store.prefs().getBoolean("studio.loop", false))
    var trails by mutableStateOf(Store.prefs().getBoolean("studio.trails", true))
    var follow by mutableStateOf(Store.prefs().getBoolean("studio.follow", false))
    var onion by mutableStateOf(Store.prefs().getBoolean("studio.onion", true))
    val hidden = mutableStateListOf<String>()
    fun keep() = Store.prefs().edit().putBoolean("studio.tilt", tilt).putString("studio.range", range).putString("studio.style", style)
        .putBoolean("studio.names", names).putBoolean("studio.snap", snap).putString("studio.grass", grass)
        .putFloat("studio.speed", speed).putBoolean("studio.loop", loop).putBoolean("studio.trails", trails).putBoolean("studio.follow", follow)
        .putBoolean("studio.onion", onion).apply()
}

@Suppress("unused") private fun keepImports(j: JSONObject) = j.b("x") || j.d("x") != null
