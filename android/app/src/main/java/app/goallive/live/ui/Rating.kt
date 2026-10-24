package app.goallive.live.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.goallive.live.L
import app.goallive.live.data.a
import app.goallive.live.data.b
import app.goallive.live.data.d
import app.goallive.live.data.i
import app.goallive.live.data.o
import app.goallive.live.data.objs
import app.goallive.live.data.s
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/*
 * "نمره گل‌لایو": our own match rating, built only from this match's numbers so every point can be shown.
 *  - every player starts at 6 and each thing he did adds or takes away, in nine groups (attack, chances, passing, defending,
 *    duels, goalkeeping, the team while he was on, discipline and mistakes, work rate);
 *  - the role he actually played in this match decides how much each group counts (a centre-back is not marked down for not shooting);
 *  - goals, assists, saves and red cards are worth more when the game was close (an equaliser or a late winner counts most);
 *  - piled-up numbers (tackles, passes, dribbles…) are judged per 90 minutes, so a substitute is not punished for a short game,
 *    but a short game gives a wider margin, shown next to the rating;
 *  - the sum is put on FotMob's familiar scale (fitted on real matches: same average, rare nines).
 */

/** One line of the breakdown: what he did, how many times, and what it was worth. */
class RatingItem(val group: String, val label: String, val points: Double, val count: Int? = null, val note: String? = null)

class PlayerRating(
    val id: Int, val name: String, val home: Boolean, val role: String, val minutes: Int,
    val value: Double, val margin: Double, val items: List<RatingItem>, val fotmob: Double?,
) {
    /** Points per group, biggest effect first. */
    val groups: List<Pair<String, Double>> get() = items.groupBy { it.group }.map { (g, l) -> g to l.sumOf { it.points } }.sortedByDescending { abs(it.second) }
}

/** Group ids in the order they are shown, with their names. */
val RATING_GROUPS = listOf("att", "cre", "pas", "de", "du", "gk", "team", "disc", "phys")
fun ratingGroupName(g: String) = when (g) {
    "att" -> L("حمله و گلزنی"); "cre" -> L("ساختن موقعیت"); "pas" -> L("پاس و بازی‌سازی"); "de" -> L("دفاع")
    "du" -> L("نبردها"); "gk" -> L("دروازه‌بانی"); "team" -> L("نتیجه تیم در زمان حضور"); "disc" -> L("انضباط و اشتباه"); else -> L("تلاش بدنی")
}

/** How much each group counts for each role. */
private val WEIGHTS = mapOf(
    "GK" to mapOf("pas" to 0.6, "de" to 0.6, "du" to 0.6),
    "CB" to mapOf("pas" to 0.9, "de" to 1.2, "du" to 1.1),
    "FB" to mapOf("cre" to 1.1, "pas" to 0.9, "team" to 0.8),
    "DM" to mapOf("pas" to 1.1, "de" to 1.1, "team" to 0.5),
    "CM" to mapOf("cre" to 1.1, "de" to 0.9, "du" to 0.9, "team" to 0.3),
    "AM" to mapOf("cre" to 1.2, "pas" to 0.9, "de" to 0.7, "du" to 0.8, "team" to 0.2),
    "W" to mapOf("cre" to 1.1, "pas" to 0.8, "de" to 0.7, "du" to 0.8, "team" to 0.1),
    "ST" to mapOf("pas" to 0.7, "de" to 0.6, "du" to 0.9, "team" to 0.1),
)

/** A goal from further back is rarer, so it is worth more. */
private val GOAL = mapOf("GK" to 1.6, "CB" to 1.4, "FB" to 1.3, "DM" to 1.25, "CM" to 1.15, "AM" to 1.0, "W" to 1.0, "ST" to 0.9)

fun roleName(r: String) = when (r) {
    "GK" -> L("دروازه‌بان"); "CB" -> L("مدافع میانی"); "FB" -> L("مدافع کناری"); "DM" -> L("هافبک دفاعی"); "CM" -> L("هافبک میانی")
    "AM" -> L("هافبک هجومی"); "W" -> L("وینگر"); else -> L("مهاجم")
}

/** The role from where he stood in this match's line-up (FotMob's position ids), or his usual line for substitutes. */
private fun roleOf(positionId: Int?, usual: Int?): String {
    val p = positionId ?: return when (usual) { 0 -> "GK"; 1 -> "CB"; 3 -> "ST"; else -> "CM" }
    val last = p % 10
    return when {
        p < 20 -> "GK"
        p < 60 -> if (last in setOf(1, 2, 8, 9) || p / 10 == 5) "FB" else "CB"
        p < 70 -> "DM"
        p < 80 -> "CM"
        p < 100 -> if (last in setOf(2, 3, 7, 8)) "W" else "AM"
        else -> if (p < 110 && last in setOf(1, 2, 3, 7, 8, 9)) "W" else "ST"
    }
}

/** How much a goal (or save, or red card) matters given the score just before it, from the player's side. */
private fun stateWeight(diff: Int, minute: Int, kind: String): Double = when (kind) {
    "goal" -> {
        var w = when (diff) { -1 -> 1.3; 0 -> 1.35; 1 -> 1.05; else -> if (diff >= 2) 0.75 else 0.95 }
        if (minute >= 80 && diff in -1..0) w *= 1.25
        w
    }
    "red" -> if (diff in -1..1) 1.25 else 0.75
    "save" -> when (diff) { 0, 1 -> 1.3; -1 -> 1.0; else -> 0.7 }
    else -> 1.0
}

private fun goalNote(diff: Int, minute: Int): String {
    val what = when {
        diff == -1 -> L("گل تساوی")
        diff == 0 -> L("گل برتری")
        diff == 1 -> L("گل دوم با اختلاف")
        diff >= 2 -> L("وقتی بازی برده بود")
        else -> L("گل جبرانی")
    }
    return what + " · " + L("دقیقه {0}", fa(minute)) + if (minute >= 80 && diff in -1..0) L(" · دقیقه‌های حساس پایانی") else ""
}

// fitted on finished matches of the big leagues: raw sum → FotMob-like scale
private const val FIT_A = 6.0
private const val FIT_B = 0.78

/** Every player's GoalLive rating in this match, by FotMob player id (empty when FotMob has no player stats). */
fun rateMatch(m: MatchData): Map<Int, PlayerRating> {
    val stats = m.content.o("playerStats") ?: return emptyMap()
    val lu = m.content.o("lineup") ?: return emptyMap()
    // the score as it went, to judge each moment
    class G(val minute: Int, val homeScored: Boolean, val e: JSONObject)
    val goals = ArrayList<G>()
    var h = 0
    for (e in m.events) {
        if (e.s("type") != "Goal" || e.b("isPenaltyShootoutEvent")) continue
        val ns = e.a("newScore")
        val homeScored = if (ns != null && ns.length() == 2) ns.optInt(0) > h else e.b("isHome")
        if (ns != null && ns.length() == 2) h = ns.optInt(0)
        goals += G(e.i("time") ?: 0, homeScored, e)
    }
    fun diffAt(minute: Int, home: Boolean): Int {
        val hh = goals.count { it.homeScored && it.minute < minute }; val aa = goals.count { !it.homeScored && it.minute < minute }
        return if (home) hh - aa else aa - hh
    }
    val finalDiff = { home: Boolean -> diffAt(1000, home) }
    val out = HashMap<Int, PlayerRating>()
    for ((side, home) in listOf("homeTeam" to true, "awayTeam" to false)) {
        val team = lu.o(side) ?: continue
        val people = team.a("starters").objs().map { it to true } + team.a("subs").objs().map { it to false }
        for ((p, starter) in people) {
            val id = p.i("id") ?: continue
            val ps = stats.optJSONObject(id.toString()) ?: continue
            val st = HashMap<String, Pair<Double, Double>>()
            var fotmob: Double? = null
            for (g in ps.a("stats").objs()) {
                val all = g.o("stats") ?: continue
                for (t in all.keys()) {
                    val one = all.optJSONObject(t) ?: continue
                    val s = one.o("stat") ?: continue
                    one.s("key")?.let { st[it] = (s.d("value") ?: 0.0) to (s.d("total") ?: 0.0) }
                    if (t == "FotMob rating") fotmob = s.d("value")
                }
            }
            fun v(k: String) = st[k]?.first ?: 0.0
            fun tot(k: String) = st[k]?.second ?: 0.0
            val mins = v("minutes_played").toInt()
            if (mins <= 0) continue
            val subs = p.o("performance").a("substitutionEvents").objs()
            val tin = subs.firstOrNull { it.s("type") == "subIn" }?.i("time") ?: if (starter) 0 else continue
            val tout = subs.firstOrNull { it.s("type") == "subOut" }?.i("time") ?: 200
            var role = roleOf(if (starter) p.i("positionId") else null, p.i("usualPlayingPositionId"))
            if (ps.b("isGoalkeeper")) role = "GK"
            // piled-up numbers are judged per 90 minutes (partly: a short game is noisy)
            val scale = min(2.0, sqrt(90.0 / max(mins, 20)))
            val items = ArrayList<RatingItem>()
            fun add(g: String, label: String, pts: Double, count: Int? = null, note: String? = null) {
                if (abs(pts) >= 0.005) items += RatingItem(g, label, pts, count, note)
            }
            fun n(k: String) = v(k).toInt()

            // goals and assists, valued by the moment
            for (g in goals) {
                val e = g.e
                if (e.i("playerId") == id && !e.b("ownGoal")) {
                    val d = diffAt(g.minute, home)
                    val pen = isPen(e)
                    add("att", if (pen) L("گل پنالتی") else L("گل"), 0.95 * (GOAL[role] ?: 1.0) * stateWeight(d, g.minute, "goal") * (if (pen) 0.75 else 1.0), 1, goalNote(d, g.minute))
                }
                if (e.i("assistPlayerId") == id) {
                    val d = diffAt(g.minute, home)
                    add("cre", L("پاس گل"), 0.7 * stateWeight(d, g.minute, "goal"), 1, goalNote(d, g.minute))
                }
                if (e.b("ownGoal") && e.i("playerId") == id) add("disc", L("گل به خودی"), -1.0, 1, L("دقیقه {0}", fa(g.minute)))
            }
            // attack
            val xg = v("expected_goals_non_penalty").takeIf { it > 0 } ?: v("expected_goals")
            add("att", L("کیفیت موقعیت‌ها (xG)"), 0.45 * xg, note = "xG " + fa(dec(xg, 2)))
            add("att", L("کیفیت ضربه (xGOT منهای xG)"), 0.35 * (v("expected_goals_on_target_variant") - v("expected_goals")))
            add("att", L("شوت در چارچوب"), min(0.4, 0.08 * v("ShotsOnTarget") * scale), n("ShotsOnTarget"))
            add("att", L("شوت به بیرون"), -0.03 * v("ShotsOffTarget"), n("ShotsOffTarget"))
            val dr = v("dribbles_succeeded"); val drt = tot("dribbles_succeeded")
            add("att", L("دریبل موفق"), min(0.5, 0.1 * dr * scale), dr.toInt())
            add("att", L("دریبل ناموفق"), -0.04 * max(0.0, drt - dr), (drt - dr).toInt())
            add("att", L("لمس در محوطه حریف"), min(0.3, 0.025 * v("touches_opp_box") * scale), n("touches_opp_box"))
            add("att", L("خطا روی او"), min(0.25, 0.04 * v("was_fouled")), n("was_fouled"))
            add("att", L("آفساید"), -0.05 * v("Offsides"), n("Offsides"))
            add("att", L("از دست دادن توپ"), -0.05 * v("dispossessed"), n("dispossessed"))
            val missedPens = m.shots.count { it.i("playerId") == id && it.s("situation") == "Penalty" && it.s("eventType") != "Goal" }
            add("att", L("پنالتی از دست رفته"), -0.7 * missedPens, missedPens)
            // chances
            add("cre", L("کیفیت پاس‌ها (xA)"), 0.5 * v("expected_assists"), note = "xA " + fa(dec(v("expected_assists"), 2)))
            add("cre", L("پاس کلیدی"), min(0.6, 0.13 * v("chances_created") * scale), n("chances_created"))
            add("cre", L("موقعیت عالی ساخته"), 0.2 * v("big_chance_created_team_title"), n("big_chance_created_team_title"))
            // passing
            val ap = v("accurate_passes"); val at = tot("accurate_passes")
            if (at >= 8) add("pas", L("دقت پاس"), max(-0.4, min(0.35, (ap / at - 0.8) * 1.6)) * min(1.0, at / 35), note = L("{0} از {1}", fa(ap.toInt()), fa(at.toInt())))
            add("pas", L("پاس سالم"), min(0.35, 0.004 * ap * scale), ap.toInt())
            add("pas", L("پاس خط‌شکن"), min(0.4, 0.04 * v("line_breaking_passes") * scale), n("line_breaking_passes"))
            add("pas", L("پاس به یک‌سوم حمله"), min(0.3, 0.025 * v("passes_into_final_third") * scale), n("passes_into_final_third"))
            val lb = v("long_balls_accurate"); val lbt = tot("long_balls_accurate")
            add("pas", L("پاس بلند"), min(0.25, 0.035 * lb) - 0.015 * max(0.0, lbt - lb), lb.toInt(), L("{0} از {1}", fa(lb.toInt()), fa(lbt.toInt())))
            val cr = v("accurate_crosses"); val crt = tot("accurate_crosses")
            add("pas", L("سانتر"), min(0.3, 0.07 * cr) - 0.02 * max(0.0, crt - cr), cr.toInt(), L("{0} از {1}", fa(cr.toInt()), fa(crt.toInt())))
            // defending
            add("de", L("تکل"), min(0.5, 0.1 * v("matchstats.headers.tackles") * scale), n("matchstats.headers.tackles"))
            add("de", L("قطع توپ"), min(0.5, 0.1 * v("interceptions") * scale), n("interceptions"))
            add("de", L("بلاک شوت"), min(0.4, 0.12 * v("shot_blocks")), n("shot_blocks"))
            add("de", L("دفع توپ"), min(0.4, 0.04 * v("clearances") * scale), n("clearances"))
            add("de", L("بازپس‌گیری توپ"), min(0.3, 0.03 * v("recoveries") * scale), n("recoveries"))
            add("de", L("تکل نفر آخر"), 0.5 * v("last_man_tackle"), n("last_man_tackle"))
            add("de", L("دریبل خورد"), -0.09 * v("dribbled_past"), n("dribbled_past"))
            // duels
            val aw = v("aerials_won"); val awt = tot("aerials_won"); val gw = v("ground_duels_won"); val gwt = tot("ground_duels_won")
            add("du", L("نبرد هوایی برده"), min(0.35, 0.06 * aw), aw.toInt())
            add("du", L("نبرد هوایی باخته"), -0.03 * max(0.0, awt - aw), (awt - aw).toInt())
            add("du", L("نبرد زمینی برده"), min(0.35, 0.04 * gw), gw.toInt())
            add("du", L("نبرد زمینی باخته"), -0.03 * max(0.0, gwt - gw), (gwt - gw).toInt())
            add("du", L("خطا"), -0.07 * v("fouls"), n("fouls"))
            // goalkeeping: every save weighed by how dangerous the shot was and the score at that moment
            if (role == "GK") {
                val saves = m.shots.filter { it.i("keeperId") == id && it.b("isOnTarget") && it.s("eventType") != "Goal" && !it.b("isBlocked") }
                val sv = if (saves.isEmpty()) 0.2 * v("saves")
                else saves.sumOf { 0.18 * (1 + 2 * (it.d("expectedGoalsOnTarget") ?: 0.0)) * stateWeight(diffAt(it.i("min") ?: 0, home), it.i("min") ?: 0, "save") }
                add("gk", L("سیو"), sv, n("saves"))
                add("gk", L("گل جلوگیری‌شده"), 0.55 * v("goals_prevented"), note = fa(dec(v("goals_prevented"), 2)))
                add("gk", L("گرفتن توپ هوایی"), 0.08 * v("keeper_high_claim"), n("keeper_high_claim"))
                add("gk", L("مشت زدن"), 0.04 * v("punches"), n("punches"))
                add("gk", L("بیرون آمدن مثل لیبرو"), 0.08 * v("keeper_sweeper"), n("keeper_sweeper"))
            }
            // the team while he was on
            val conceded = goals.count { it.homeScored != home && it.minute in tin..tout }
            val onPitch = min(tout, 95) - tin
            val cs = mapOf("GK" to 0.6, "CB" to 0.5, "FB" to 0.4, "DM" to 0.25, "CM" to 0.12)[role]
            if (cs != null && onPitch >= 60 && conceded == 0) add("team", L("کلین‌شیت"), cs)
            val per = mapOf("GK" to 0.3, "CB" to 0.25, "FB" to 0.2, "DM" to 0.1)[role]
            if (per != null) add("team", L("گل خورده وقتی در زمین بود"), -per * conceded, conceded)
            val fd = finalDiff(home)
            add("team", if (fd > 0) L("برد تیم") else L("باخت تیم"), if (fd > 0) 0.15 else if (fd < 0) -0.1 else 0.0)
            // discipline and mistakes
            for (e in m.events) if (e.s("type") == "Card" && e.o("player").i("id") == id) {
                val t = e.i("time") ?: 0
                if (e.s("card") == "Yellow") add("disc", L("کارت زرد"), -0.25, 1, L("دقیقه {0}", fa(t)))
                else add("disc", L("اخراج"), -1.4 * stateWeight(diffAt(t, home), t, "red"), 1, L("دقیقه {0}", fa(t)))
            }
            add("disc", L("اشتباه منجر به گل"), -1.2 * v("errors_led_to_goal"), n("errors_led_to_goal"))
            // work rate (small)
            val dist = v("physical_metrics_distance_covered")
            if (dist > 0 && mins >= 20) add("phys", L("مسافت دویده"), max(-0.1, min(0.15, (dist / mins * 90 - 10500) / 10000)), note = L("{0} کیلومتر", fa(dec(dist / 1000, 1))))
            val spr = v("physical_metrics_number_of_sprints")
            if (spr > 0 && mins >= 20) add("phys", L("دوی سرعت"), max(0.0, min(0.1, (spr / mins * 90 - 20) / 100)), spr.toInt())

            val w = WEIGHTS[role].orEmpty()
            val weighted = items.map { RatingItem(it.group, it.label, it.points * (w[it.group] ?: 1.0) * FIT_B, it.count, it.note) }
            var r = FIT_A + weighted.sumOf { it.points }
            if (r > 8.3) r = 8.3 + (r - 8.3) * 0.7
            r = r.coerceIn(3.0, 10.0)
            // a short game says less: the margin grows as the minutes fall
            val margin = 0.25 + 0.9 * max(0.0, 1 - mins / 90.0)
            out[id] = PlayerRating(id, p.s("name") ?: "", home, role, mins, Math.round(r * 10) / 10.0, Math.round(margin * 10) / 10.0, weighted, fotmob)
        }
    }
    return out
}

/** Every player's rating in the match on screen, by player id. */
val LocalRatings = staticCompositionLocalOf<Map<Int, PlayerRating>> { emptyMap() }

private fun signed(x: Double) = (if (x >= 0) "+" else "−") + fa(dec(abs(x), 2))

/**
 * "چرا این نمره؟": his place among everyone in the match, his best and worst parts in one line, then each group as a bar with
 * its total; tapping a group lists every line in it.
 */
@Composable
fun RatingWhy(r: PlayerRating, all: List<PlayerRating>, open: Boolean, onToggle: () -> Unit) {
    val accent = LocalAccent.current
    val rank = all.sortedByDescending { it.value }.indexOfFirst { it.id == r.id } + 1
    val groups = r.groups.filter { abs(it.second) >= 0.01 }
    val best = groups.maxByOrNull { it.second }?.takeIf { it.second > 0.05 }
    val worst = groups.minByOrNull { it.second }?.takeIf { it.second < -0.05 }
    var shown by rememberSaveable(r.id) { mutableStateOf<String?>(null) }
    SheetCard {
        Row(Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                T(L("نمره گل‌لایو: چرا {0}؟", fa(dec(r.value, 1))), 15, FontWeight.ExtraBold)
                T(L("رتبه {0} از {1} بازیکن این بازی · {2}", fa(rank), fa(all.size), roleName(r.role)), 12, color = C.mute)
                if (best != null || worst != null) T(listOfNotNull(best?.let { L("بهترین بخش: {0}", ratingGroupName(it.first)) },
                    worst?.let { L("ضعیف‌ترین: {0}", ratingGroupName(it.first)) }).joinToString(" · "), 12, FontWeight.Bold, accent)
            }
            T(if (open) "▲" else "▼", 12, color = C.mute)
        }
        if (!open) return@SheetCard
        // start, then each group
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            T(L("نقطه شروع همه بازیکن‌ها"), 13, color = C.mute, modifier = Modifier.weight(1f)); T(fa(dec(6.0, 1)), 13, FontWeight.ExtraBold)
        }
        val top = groups.maxOfOrNull { abs(it.second) }?.coerceAtLeast(0.3) ?: 1.0
        for ((g, pts) in groups) {
            val pos = pts >= 0
            Column(Modifier.fillMaxWidth().clickable { shown = if (shown == g) null else g }.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    T(ratingGroupName(g), 13, FontWeight.Bold, modifier = Modifier.weight(1f))
                    Ltr(signed(pts), style = vz(13.sp, FontWeight.Black), color = if (pos) C.win else C.live, raw = true)
                }
                Box(Modifier.padding(top = 5.dp).fillMaxWidth().height(6.dp).clip(RoundedCornerShape(6.dp)).background(C.line)) {
                    Box(Modifier.fillMaxWidth((abs(pts) / top).toFloat().coerceIn(0.03f, 1f)).fillMaxHeight().clip(RoundedCornerShape(6.dp))
                        .background(if (pos) C.win else C.live))
                }
            }
            if (shown == g) Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.card2)
                .padding(horizontal = 12.dp, vertical = 6.dp)) {
                r.items.filter { it.group == g }.sortedByDescending { abs(it.points) }.forEach { i ->
                    Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            T(i.label + (i.count?.takeIf { it > 1 }?.let { " ×" + fa(it) } ?: ""), 12)
                            i.note?.let { T(it, 11, color = C.mute) }
                        }
                        Ltr(signed(i.points), style = vz(12.sp, FontWeight.ExtraBold), color = if (i.points >= 0) C.win else C.live, raw = true)
                    }
                }
            }
        }
        Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(1.dp).background(C.line))
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            T(L("نمره نهایی"), 14, FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            T(fa(dec(r.value, 1)) + if (r.minutes < 60) "  ± " + fa(dec(r.margin, 1)) else "", 15, FontWeight.Black, accent)
        }
    }
}

/** "برترین‌های گل‌لایو": the three best of the match by our rating, each with what lifted him most. */
@Composable
fun TopRated(m: MatchData) {
    val nav = LocalNav.current
    val top = LocalRatings.current.values.filter { it.minutes >= 20 }.sortedByDescending { it.value }.take(3)
    if (top.size < 3) return
    Card {
        CardTitle(L("⭐ برترین‌های گل‌لایو"))
        top.forEachIndexed { k, r ->
            val reason = r.items.filter { it.points > 0 }.maxByOrNull { it.points }
            ListRow(first = k == 0, onClick = { nav.player(r.id) }) {
                Box(Modifier.size(26.dp).clip(CircleShape).background(if (k == 0) C.gold else C.card2), contentAlignment = Alignment.Center) {
                    T(fa(k + 1), 12, FontWeight.Black, if (k == 0) Color(0xFF2A1A00) else C.ink)
                }
                Face(r.id, 40.dp)
                Column(Modifier.weight(1f)) {
                    T(personName(r.name), 14, FontWeight.ExtraBold, maxLines = 1)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Crest(if (r.home) m.homeId else m.awayId, 14.dp)
                        T(listOfNotNull(roleName(r.role), reason?.label).joinToString(" · "), 11, color = C.mute, maxLines = 1)
                    }
                }
                Rating(r.value)
            }
        }
    }
}
