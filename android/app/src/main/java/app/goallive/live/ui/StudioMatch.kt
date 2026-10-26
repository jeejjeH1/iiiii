package app.goallive.live.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import app.goallive.live.L
import app.goallive.live.data.Api
import app.goallive.live.data.a
import app.goallive.live.data.b
import app.goallive.live.data.d
import app.goallive.live.data.i
import app.goallive.live.data.o
import app.goallive.live.data.objs
import app.goallive.live.data.path
import app.goallive.live.data.s
import org.json.JSONObject
import kotlin.math.abs

/*
 * Real matches in the studio: both line-ups as they started, and each goal rebuilt roughly from where it was shot from,
 * how it came (open play, a counter, a corner, a free kick, a penalty) and whether it was a header. Only the shot's
 * place is known, not where everyone stood, so the rest is laid out as such moves usually are.
 */

/** A match fetched for the studio: its data and both sides as the studio needs them. */
class StudioMatch(val m: MatchData, val home: LoadedTeam, val away: LoadedTeam) {
    /** Its goals (own goals left out), in order. */
    val goals: List<JSONObject> get() = m.events.filter { it.s("type") == "Goal" && !it.b("ownGoal") }
}

private fun lineupSide(t: JSONObject?, side: Int, color: Color): LoadedTeam? {
    t ?: return null
    val id = t.i("id") ?: 0
    val c = color.toArgb()
    val mirror = side == 1
    val xi = t.a("starters").objs().mapIndexedNotNull { k, p ->
        val vl = p.o("verticalLayout") ?: return@mapIndexedNotNull null
        val x = (vl.d("x") ?: 0.5).toFloat(); val y = (vl.d("y") ?: 0.5).toFloat()
        val u = 0.94f - x * 0.88f; val v = 0.94f - (y - 0.1f) / 0.77f * 0.42f
        SPlayer(side * 100 + k, side, if (mirror) 1 - u else u, if (mirror) 1 - v else v, p.s("shirtNumber") ?: "${k + 1}",
            personName(p.s("lastName") ?: (p.s("name") ?: "").substringAfterLast(' ')), p.i("id") ?: 0)
    }
    if (xi.size < 10) return null
    val squad = (t.a("starters").objs() + t.a("subs").objs()).map { Triple(it.i("id") ?: 0, personName(it.s("lastName") ?: (it.s("name") ?: "").substringAfterLast(' ')), it.s("shirtNumber") ?: "") }
    return LoadedTeam(STeam(teamName(id, t.s("name") ?: ""), c, inkFor(c), id, t.s("formation") ?: "4-3-3"), xi, squad)
}

/** Fetches match [id] with both line-ups; null when it has none yet. */
suspend fun loadStudioMatch(id: Int): StudioMatch? {
    val m = MatchData(Api.fm("matchDetails", mapOf("matchId" to id), 600_000))
    val lu = m.content.o("lineup")
    val h = lineupSide(lu.o("homeTeam"), 0, m.homeColor) ?: return null
    val a = lineupSide(lu.o("awayTeam"), 1, m.awayColor) ?: return null
    return StudioMatch(m, h, a)
}

/** Both sides as they started the match, on a fresh board. */
fun StudioBoard.loadLineups(sm: StudioMatch) {
    marks.clear(); squads.clear(); showAway = true
    setSide(0, sm.home); setSide(1, sm.away)
    ball = Offset(0.5f, 0.5f)
    frames.clear(); cur = 0; frames += frameNow(null)
    title = sm.home.team.name + " " + L("مقابل") + " " + sm.away.team.name
    commit()
}

/** Goal [e] of the match as a move: the build-up its kind of goal suggests, then the shot from where it was taken. */
fun StudioBoard.loadGoal(sm: StudioMatch, e: JSONObject) {
    loadLineups(sm)
    val side = if (e.b("isHome")) 0 else 1
    val fwd = if (side == 0) -1f else 1f            // up the pitch for the home side
    val shot = e.o("shotmapEvent")
    val sx = (shot.d("x") ?: 94.0).toFloat(); val sy = (shot.d("y") ?: 34.0).toFloat()
    // FotMob: x 0…105 towards the goal attacked, y 0…68 across
    val at = if (side == 0) Offset(sy / 68f, 1 - sx / 105f) else Offset(1 - sy / 68f, sx / 105f)
    val goalV = if (side == 0) 0f else 1f
    // the attackers pushed up, the defenders back towards their goal
    players.forEach { p ->
        if (p.side == side) p.v = (p.v + fwd * 0.3f).coerceIn(0.04f, 0.96f)
        else p.v = (goalV + (p.v - goalV) * 0.55f).coerceIn(0.01f, 0.99f)
    }
    val mine = players.filter { it.side == side }
    val keeperMine = keeperOf(side)
    val scorerId = e.o("player").i("id") ?: shot.i("playerId")
    val scorer = mine.firstOrNull { it.pid == scorerId && it.pid > 0 } ?: mine.filter { it != keeperMine }.minByOrNull { (Offset(it.u, it.v) - at).getDistance() } ?: return
    // the scorer brought on as a sub takes the place of the nearest team-mate
    if (scorer.pid != scorerId && scorerId != null) {
        val n = personName(e.o("player").s("lastName") ?: e.s("nameStr") ?: scorer.name)
        scorer.pid = scorerId; scorer.name = n
    }
    val assistName = (e.s("assistInput") ?: e.s("assistStr") ?: "").substringAfterLast(' ').lowercase()
    fun assister(near: Offset) = mine.filter { it != scorer && it != keeperMine }
        .firstOrNull { assistName.isNotEmpty() && it.name.lowercase().endsWith(assistName) }
        ?: mine.filter { it != scorer && it != keeperMine }.minByOrNull { (Offset(it.u, it.v) - near).getDistance() }
    val situation = shot.s("situation") ?: ""
    val header = shot.s("shotType") == "Header"
    val minute = minuteText(e)
    val name = scorer.name
    // two defenders close to the shooter, the keeper on his line
    val them = players.filter { it.side != side }
    val keeperThem = keeperOf(1 - side)
    fun closeDown() {
        them.filter { it != keeperThem }.sortedBy { (Offset(it.u, it.v) - at).getDistance() }.take(2).forEachIndexed { i, p ->
            val dx = if (i == 0) 0.035f else -0.04f
            p.u = (at.x + dx).coerceIn(0.02f, 0.98f); p.v = (at.y - fwd * 0.03f).coerceIn(0.01f, 0.99f)
        }
        keeperThem?.let { it.u = 0.5f + (at.x - 0.5f) * 0.15f; it.v = goalV - fwd * 0.02f }
    }
    fun step(ballAt: Offset, note: String, dur: Float = 1.1f, air: Boolean = false, moves: () -> Unit = {}) {
        frames.add(cur + 1, frames[cur].copy(paths = emptyMap(), note = "", air = false, ev = null)); cur += 1
        moves(); ball = ballAt
        frames[cur] = frameNow(null).copy(dur = dur, air = air, note = note)
    }
    fun setStart(ballAt: Offset, note: String) { ball = ballAt; frames[0] = frameNow(null).copy(note = note) }
    when {
        situation == "Penalty" -> {
            val spot = Offset(0.5f, goalV - fwd * 11f / 105f)
            players.filter { it != scorer && it != keeperThem && it != keeperMine }.forEach { p ->
                if (abs(p.v - goalV) < 0.18f) p.v = goalV - fwd * (0.18f + (p.key % 5) * 0.012f)
            }
            scorer.u = spot.x; scorer.v = spot.y - fwd * 0.02f
            keeperThem?.let { it.u = 0.5f; it.v = goalV - fwd * 0.005f }
            setStart(spot, L("دقیقه‌ی {0}: پنالتی برای {1}", fa(minute), name))
        }
        situation == "FromCorner" -> {
            val corner = Offset(if (at.x > 0.5f) 0.99f else 0.01f, goalV - fwd * 0.004f)
            assister(corner)?.let { it.u = corner.x; it.v = corner.y }
            scorer.u = at.x + (if (at.x > 0.5f) -0.06f else 0.06f); scorer.v = at.y - fwd * 0.07f
            setStart(corner, L("دقیقه‌ی {0}: کرنر", fa(minute)))
            step(at, if (header) L("سانتر روی ضربه‌ی سر {0}", name) else L("سانتر به محوطه"), 1.2f, air = true) { scorer.u = at.x; scorer.v = at.y; closeDown() }
        }
        situation == "SetPiece" || situation == "FreeKick" -> {
            val direct = !header && abs(at.y - goalV) * 105f > 16f
            if (direct) {
                scorer.u = at.x; scorer.v = at.y - fwd * 0.01f
                // the wall, 9 metres away
                them.filter { it != keeperThem }.sortedBy { (Offset(it.u, it.v) - at).getDistance() }.take(4).forEachIndexed { i, p ->
                    p.u = (0.5f + (at.x - 0.5f) * 0.82f) - 0.045f + i * 0.03f; p.v = at.y + fwd * 9f / 105f
                }
                setStart(at, L("دقیقه‌ی {0}: ضربه‌ی آزاد {1}", fa(minute), name))
            } else {
                val from = Offset(if (at.x > 0.5f) 0.88f else 0.12f, at.y - fwd * 0.16f)
                assister(from)?.let { it.u = from.x; it.v = from.y }
                scorer.u = at.x; scorer.v = at.y - fwd * 0.08f
                setStart(from, L("دقیقه‌ی {0}: ضربه‌ی آزاد", fa(minute)))
                step(at, L("سانتر به {0}", name), 1.2f, air = true) { scorer.u = at.x; scorer.v = at.y; closeDown() }
            }
        }
        situation == "FastBreak" -> {
            val deep = Offset(0.5f + (at.x - 0.5f) * 0.3f, (at.y - fwd * 0.42f).coerceIn(0.05f, 0.95f))
            val a = assister(deep)
            a?.let { it.u = deep.x; it.v = deep.y }
            scorer.u = (at.x + (0.5f - at.x) * 0.2f); scorer.v = (at.y - fwd * 0.28f).coerceIn(0.05f, 0.95f)
            // the defenders caught high
            them.filter { it != keeperThem }.forEach { p -> p.v = (p.v - fwd * 0.12f).coerceIn(0.02f, 0.98f) }
            setStart(deep, L("دقیقه‌ی {0}: ضدحمله", fa(minute)))
            val run = Offset(at.x + (0.5f - at.x) * 0.1f, at.y - fwd * 0.12f)
            step(run, L("پاس در عمق برای {0}", name), 1.0f) { scorer.u = run.x; scorer.v = run.y }
            step(at, L("{0} به سمت دروازه می‌برد", name), 0.9f) { scorer.u = at.x; scorer.v = at.y; closeDown() }
        }
        header -> {
            val wing = Offset(if (at.x > 0.5f) 0.1f else 0.9f, at.y - fwd * 0.12f)
            assister(wing)?.let { it.u = wing.x; it.v = wing.y }
            scorer.u = at.x; scorer.v = at.y - fwd * 0.08f
            setStart(wing, L("دقیقه‌ی {0}: حمله از جناح", fa(minute)))
            step(at, L("سانتر روی ضربه‌ی سر {0}", name), 1.2f, air = true) { scorer.u = at.x; scorer.v = at.y; closeDown() }
        }
        else -> {
            val from = Offset(at.x + (0.5f - at.x) * 0.35f, at.y - fwd * 0.13f)
            assister(from)?.let { it.u = from.x; it.v = from.y }
            scorer.u = at.x + (if (at.x > 0.5f) 0.04f else -0.04f); scorer.v = at.y - fwd * 0.07f
            setStart(from, L("دقیقه‌ی {0}: حمله‌ی {1}", fa(minute), (if (side == 0) home else away).name))
            step(at, L("پاس به {0}", name), 1.0f) { scorer.u = at.x; scorer.v = at.y; closeDown() }
        }
    }
    // the shot, with FotMob's own chance of a goal for it
    step(ball, L("گل {0}", name), 0.6f) { }
    setEvent(cur, "shot", "goal", "manual")
    frames[cur].ev?.let { ev -> frames[cur] = frames[cur].copy(ev = SEvent("shot", "goal", "manual", ev.by, (shot.d("expectedGoals") ?: ev.xg.toDouble()).toFloat(), ev.seed)) }
    title = L("گل {0} مقابل {1}", name, (if (side == 0) away else home).name)
    cur = 0; applyFrame(frames[0])
    commit()
}

/** The studio asked to open a match: from its line-up tab. */
object StudioOpen { var match by androidx.compose.runtime.mutableIntStateOf(0) }

@Suppress("unused") private fun keepPath(j: JSONObject) = j.path("x")
