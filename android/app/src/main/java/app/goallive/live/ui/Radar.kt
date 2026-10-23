package app.goallive.live.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
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
import app.goallive.live.data.Api
import app.goallive.live.data.Store
import app.goallive.live.data.a
import app.goallive.live.data.b
import app.goallive.live.data.d
import app.goallive.live.data.i
import app.goallive.live.data.o
import app.goallive.live.data.objs
import app.goallive.live.data.path
import app.goallive.live.data.s
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.json.JSONObject

/** One line of the radar: a strength (good) or a weakness, with the number behind it. */
class Insight(val icon: String, val text: String, val good: Boolean)

/** What the radar knows about one team. */
class TeamRadar(
    val id: Int, val name: String, val insights: List<Insight>,
    /** Goals scored / conceded per 15-minute slice (0-15 … 76-90+) in the recent matches. */
    val scoredBy: IntArray, val concededBy: IntArray, val matches: Int,
    val keyId: Int?, val keyName: String?, val keyText: String?,
)

private val SET_PIECES = setOf("SetPiece", "FromCorner", "FreeKick", "ThrowInSetPiece", "IndividualPlay_FreeKick")
private val FRIENDLIES = setOf(489, 10216)

private fun slice(min: Int) = ((min.coerceAtLeast(1) - 1) / 15).coerceIn(0, 5)
private fun pct(a: Int, b: Int) = if (b == 0) 0 else a * 100 / b

/**
 * Reads a team's season numbers and its last league matches (goal by goal) and turns them into plain sentences:
 * "most of the goals they let in are headers", "they tire in the last 15 minutes", "dangerous from set pieces"…
 */
suspend fun teamRadar(id: Int, name: String, isHome: Boolean): TeamRadar = coroutineScope {
    val d = Api.fm("teams", mapOf("id" to id, "ccode3" to "IRN"), 3_600_000)
    val out = ArrayList<Insight>()

    // season ranks among the league's teams (FotMob ranks 1 = best); leagues without them are ranked from the table
    val rows = d.a("table").objs().flatMap { t -> listOfNotNull(t.path("data.table")) + t.o("data").a("tables").objs().mapNotNull { it.o("table") } }
    val table = rows.firstOrNull { tb -> tb.a("all").objs().any { it.i("id") == id } }
    val all = table?.a("all")?.objs().orEmpty()
    val n = all.size.takeIf { it > 0 } ?: 20
    val ranks = d.o("stats").a("teams").objs().associate { (it.s("header") ?: "") to it.o("participant") }
    // goals for / against per game from the table: "13-25" over "played"
    fun perGame(r: JSONObject, k: Int): Double? {
        val pl = r.i("played") ?: 0
        val g = r.s("scoresStr")?.split("-")?.getOrNull(k)?.trim()?.toIntOrNull() ?: return null
        return if (pl >= 3) g.toDouble() / pl else null
    }
    fun tableRank(k: Int, highBest: Boolean): Pair<Int, Double>? {
        val mine = all.firstOrNull { it.i("id") == id }?.let { perGame(it, k) } ?: return null
        val vals = all.mapNotNull { perGame(it, k) }
        return (vals.count { if (highBest) it > mine else it < mine } + 1) to mine
    }
    fun rank(h: String, k: Int, highBest: Boolean): Pair<Int, Double>? =
        ranks[h]?.let { r -> r.i("rank")?.let { it to (r.d("value") ?: 0.0) } } ?: tableRank(k, highBest)
    rank("Goals per match", 0, true)?.let { (r, v) ->
        if (r <= 3) out += Insight("⚽", L("از بهترین خط‌های حمله لیگ (رتبه {0})؛ {1} گل زده در هر بازی", fa(r), dec(v, 1)), true)
        else if (r > n - 3) out += Insight("🥶", L("از کم‌گل‌ترین تیم‌های لیگ (رتبه {0} از {1})؛ {2} گل زده در هر بازی", fa(r), fa(n), dec(v, 1)), false)
    }
    rank("Goals conceded per match", 1, false)?.let { (r, v) ->
        if (r <= 3) out += Insight("🧱", L("از بهترین دفاع‌های لیگ (رتبه {0})؛ {1} گل خورده در هر بازی", fa(r), dec(v, 1)), true)
        else if (r > n - 3) out += Insight("🕳️", L("از پرگل‌خورده‌ترین تیم‌های لیگ (رتبه {0} از {1})؛ {2} گل خورده در هر بازی", fa(r), fa(n), dec(v, 1)), false)
    }
    ranks["Average possession"]?.let { p ->
        val r = p.i("rank") ?: return@let; val v = p.d("value")?.toInt()
        if (r <= 3) out += Insight("🎯", L("از تیم‌های پرمالکیت لیگ؛ میانگین {0}٪ مالکیت توپ", fa(v)), true)
        else if (r > n - 3) out += Insight("↩️", L("از کم‌مالکیت‌ترین تیم‌های لیگ ({0}٪)", fa(v)), false)
    }
    ranks["Set piece goals"]?.let { p -> val r = p.i("rank") ?: return@let
        if (r <= 3 && (p.i("value") ?: 0) >= 3) out += Insight("🚩", L("{0} گل از ضربات ایستگاهی در این فصل (رتبه {1} لیگ)", fa(p.i("value")), fa(r)), true) }
    ranks["Set piece goals conceded"]?.let { p -> val r = p.i("rank") ?: return@let
        if (r > n - 3 && (p.i("value") ?: 0) >= 3) out += Insight("🚩", L("{0} گل از ضربات ایستگاهی خورده در این فصل", fa(p.i("value"))), false) }

    // recent form
    val form = d.path("overview").a("teamForm").objs().takeLast(5).mapNotNull { it.s("resultString") }
    val w = form.count { it == "W" }; val l = form.count { it == "L" }
    if (form.size >= 4) when {
        w >= 4 -> out += Insight("🔥", L("در فرم خوب: {0} برد در {1} بازی اخیر", fa(w), fa(form.size)), true)
        l >= 3 -> out += Insight("📉", L("فرم بد: {0} باخت در {1} بازی اخیر", fa(l), fa(form.size)), false)
        l == 0 -> out += Insight("🛡️", L("در {0} بازی اخیر شکست نخورده", fa(form.size)), true)
    }
    // home / away record in the league
    table?.a(if (isHome) "home" else "away")?.objs()?.firstOrNull { it.i("id") == id }?.let { r ->
        val pl = r.i("played") ?: 0; val wn = r.i("wins") ?: 0; val ls = r.i("losses") ?: 0
        val where = if (isHome) L("در خانه") else L("بیرون از خانه")
        if (pl >= 3 && wn * 100 / pl >= 70) out += Insight(if (isHome) "🏟️" else "✈️", L("{0} قوی: {1} برد در {2} بازی", where, fa(wn), fa(pl)), true)
        else if (pl >= 3 && ls * 100 / pl >= 50) out += Insight(if (isHome) "🏟️" else "✈️", L("{0} ضعیف: {1} باخت در {2} بازی", where, fa(ls), fa(pl)), false)
    }

    // goal by goal in the last league / cup matches (friendlies left out). The shot map tells how each goal came;
    // leagues without one (Iran's among them) still list every goal with its minute in the match events.
    val recent = d.path("fixtures.allFixtures").a("fixtures").objs()
        .filter { it.path("status").b("finished") && (it.o("tournament").i("leagueId") ?: 0) !in FRIENDLIES }.takeLast(6)
    val details = recent.map { f -> async { runCatching { Api.fm("matchDetails", mapOf("matchId" to f.s("id")), 6 * 3_600_000) }.getOrNull() } }.awaitAll()
    val scoredBy = IntArray(6); val concededBy = IntArray(6)
    var sHead = 0; var sSet = 0; var sFast = 0; var sAll = 0
    var cHead = 0; var cSet = 0; var cFast = 0; var cAll = 0
    var used = 0; var rich = 0; var sRich = 0; var cRich = 0
    for (md in details.filterNotNull()) {
        val homeId = md.path("general.homeTeam").i("id")
        val shotGoals = md.path("content.shotmap").a("shots").objs().filter { it.s("eventType") == "Goal" }
        val evGoals = md.path("content.matchFacts.events").a("events").objs().filter { it.s("type") == "Goal" }
        val total = md.path("header").a("teams").objs().sumOf { it.i("score") ?: 0 }
        when {
            shotGoals.isNotEmpty() && shotGoals.size >= evGoals.size -> {
                used++; rich++
                for (g in shotGoals) {
                    val ours = (g.i("teamId") == id) != g.b("isOwnGoal")
                    val sl = slice(g.i("min") ?: 0)
                    val head = g.s("shotType") == "Header"
                    val set = g.s("situation") in SET_PIECES
                    val fast = g.s("situation") == "FastBreak"
                    if (ours) { scoredBy[sl]++; sAll++; sRich++; if (head) sHead++; if (set) sSet++; if (fast) sFast++ }
                    else { concededBy[sl]++; cAll++; cRich++; if (head) cHead++; if (set) cSet++; if (fast) cFast++ }
                }
            }
            evGoals.isNotEmpty() || total == 0 -> {
                used++
                for (g in evGoals) {
                    val forHome = g.b("isHome")
                    val ours = (forHome == (homeId == id))
                    val sl = slice(g.i("time") ?: 0)
                    if (ours) { scoredBy[sl]++; sAll++ } else { concededBy[sl]++; cAll++ }
                }
            }
            // a match with goals but no goal list: nothing to learn from it
        }
    }
    if (used >= 3) {
        val m = L("در {0} بازی اخیر", fa(used))
        // when the goals came: needs enough goals to mean something
        if (cAll >= 4 && pct(concededBy[5], cAll) >= 40) out += Insight("🥵", L("پایان بازی را بد تمام می‌کند: {0} از {1} گل خورده بعد از دقیقه ۷۵ ({2})", fa(concededBy[5]), fa(cAll), m), false)
        if (cAll >= 4 && pct(concededBy[0], cAll) >= 35) out += Insight("⏰", L("شروع بازی را بد آغاز می‌کند: {0} از {1} گل خورده در ۱۵ دقیقه اول ({2})", fa(concededBy[0]), fa(cAll), m), false)
        if (sAll >= 4 && pct(scoredBy[5], sAll) >= 40) out += Insight("⏱️", L("تا آخرین دقیقه خطرناک است: {0} از {1} گلش بعد از دقیقه ۷۵ ({2})", fa(scoredBy[5]), fa(sAll), m), true)
        if (sAll >= 4 && pct(scoredBy[0], sAll) >= 35) out += Insight("🚀", L("سریع شروع می‌کند: {0} از {1} گلش در ۱۵ دقیقه اول ({2})", fa(scoredBy[0]), fa(sAll), m), true)
        if (cAll == 0) out += Insight("🧤", L("{0} هیچ گلی نخورده", m), true)
        if (sAll == 0) out += Insight("🥶", L("{0} هیچ گلی نزده", m), false)
    }
    if (rich >= 3) {
        val m = L("در {0} بازی اخیر", fa(rich))
        // how the goals came (only from matches with a shot map)
        if (cRich >= 4 && pct(cHead, cRich) >= 40) out += Insight("🗣️", L("در دفاع از توپ‌های هوایی ضعیف است: {0} از {1} گل خورده با ضربه سر ({2})", fa(cHead), fa(cRich), m), false)
        if (cRich >= 4 && pct(cSet, cRich) >= 40) out += Insight("🚩", L("روی ضربات ایستگاهی گل می‌خورد: {0} از {1} گل خورده ({2})", fa(cSet), fa(cRich), m), false)
        if (cFast >= 2) out += Insight("⚡", L("در برابر ضدحمله آسیب‌پذیر است: {0} گل خورده از ضدحمله ({1})", fa(cFast), m), false)
        if (sRich >= 4 && pct(sHead, sRich) >= 35) out += Insight("🗣️", L("در ضربه سر خطرناک است: {0} از {1} گلش با سر ({2})", fa(sHead), fa(sRich), m), true)
        if (sRich >= 4 && pct(sSet, sRich) >= 40) out += Insight("🚩", L("روی ضربات ایستگاهی خطرناک است: {0} از {1} گلش ({2})", fa(sSet), fa(sRich), m), true)
        if (sFast >= 2) out += Insight("⚡", L("ضدحمله‌های سریع: {0} گل از ضدحمله ({1})", fa(sFast), m), true)
    }

    // the man to watch
    val top = d.o("stats").a("players").objs().firstOrNull { it.s("header") == "Top scorer" }?.o("participant")
    TeamRadar(
        id, name, out.sortedBy { !it.good }, scoredBy, concededBy, used,
        top?.i("id"), top?.s("name"), top?.i("value")?.let { L("{0} گل در این فصل", fa(it)) },
    )
}

/** "رادار حریف": strengths and weak spots of the two teams before (and during) a match. */
@Composable
fun OpponentRadar(m: MatchData) {
    val nav = LocalNav.current
    val mine = Store.myTeam?.id
    // facing my team: start on the opponent
    var side by rememberSaveable(m.matchId) { mutableStateOf(if (mine == m.homeId) "a" else "h") }
    val l = rememberLoader(m.matchId, "radar") {
        coroutineScope {
            val h = async { teamRadar(m.homeId, m.homeName, true) }
            val a = async { teamRadar(m.awayId, m.awayName, false) }
            h.await() to a.await()
        }
    }
    Card {
        CardTitle(if (mine == m.homeId || mine == m.awayId) L("🛰️ رادار حریف") else L("🛰️ رادار دو تیم"), sub = L("نقاط قوت و ضعف"))
        Box(Modifier.padding(horizontal = 12.dp)) {
            Seg(listOf("h" to teamName(m.homeId, m.homeName), "a" to teamName(m.awayId, m.awayName)), side) { side = it }
        }
        when (val s = l.state) {
            is Load.Loading -> Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                T(L("در حال بررسی بازی‌های اخیر…"), 13, color = C.mute)
                Skeleton(2)
            }
            is Load.Err -> ErrorView({ l.state = Load.Loading; l.reload() }, L("رادار آماده نشد"))
            is Load.Ok -> {
                val r = if (side == "h") s.v.first else s.v.second
                val color = if (side == "h") m.homeColor else m.awayColor
                Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (r.insights.isEmpty()) T(if (r.matches >= 3) L("در آمار این تیم نکته برجسته‌ای (خیلی خوب یا خیلی بد) دیده نمی‌شود.") else L("اطلاعات کافی از بازی‌های اخیر این تیم در دسترس نیست."), 13, color = C.mute)
                    r.insights.forEach { ins ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background((if (ins.good) C.win else C.away).copy(alpha = 0.10f))
                            .padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            T(ins.icon, 18)
                            T(ins.text, 13, FontWeight.Bold, modifier = Modifier.weight(1f))
                            T(if (ins.good) L("قوت") else L("ضعف"), 12, FontWeight.Black, if (ins.good) C.win else C.away)
                        }
                    }
                    if (r.matches >= 3) {
                        T(L("گل‌ها در طول بازی ({0} بازی اخیر)", fa(r.matches)), 12, FontWeight.ExtraBold, C.mute, Modifier.padding(top = 6.dp))
                        GoalClock(r.scoredBy, r.concededBy, color)
                    }
                    if (r.keyId != null && r.keyName != null) Row(
                        Modifier.fillMaxWidth().padding(top = 4.dp).clip(RoundedCornerShape(14.dp)).background(C.card2).clickable { nav.player(r.keyId) }.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Face(r.keyId, 40.dp, color)
                        Column(Modifier.weight(1f)) {
                            T(L("مراقب این بازیکن باشید"), 12, color = C.mute)
                            Ltr(r.keyName, style = lat(16.sp))
                        }
                        r.keyText?.let { T(it, 12, FontWeight.Bold, color = C.ink) }
                    }
                    T(L("فقط از روی آمار واقعی: جدول و آمار این فصل و گل‌های بازی‌های اخیر (FotMob)."), 12, color = C.faint)
                }
            }
        }
    }
}

/** Six small columns (0-15 … 76-90+): goals scored above the line, goals let in below. */
@Composable
private fun GoalClock(scored: IntArray, conceded: IntArray, color: Color) {
    val top = maxOf(1, scored.maxOrNull() ?: 0, conceded.maxOrNull() ?: 0)
    val labels = listOf(L("۱۵"), L("۳۰"), L("۴۵"), L("۶۰"), L("۷۵"), L("۹۰"))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (k in 0 until 6) Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.height(44.dp).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                Box(Modifier.fillMaxWidth(0.6f).fillMaxHeight(scored[k] / top.toFloat()).clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)).background(color))
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(C.line2))
            Box(Modifier.height(44.dp).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                Box(Modifier.fillMaxWidth(0.6f).fillMaxHeight(conceded[k] / top.toFloat()).clip(RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp)).background(C.live))
            }
            T(labels[k], 12, color = C.mute, align = TextAlign.Center)
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(10.dp).clip(CircleShape).background(color)); T(L(" گل زده"), 12, color = C.mute) }
        Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(10.dp).clip(CircleShape).background(C.live)); T(L(" گل خورده"), 12, color = C.mute) }
    }
}
