package app.goallive.live.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.runtime.*
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

    // season ranks among the league's teams (FotMob ranks 1 = best)
    val rows = d.a("table").objs().flatMap { t -> listOfNotNull(t.path("data.table")) + t.o("data").a("tables").objs().mapNotNull { it.o("table") } }
    val table = rows.firstOrNull { tb -> tb.a("all").objs().any { it.i("id") == id } }
    val n = table?.a("all")?.length() ?: 20
    val ranks = d.o("stats").a("teams").objs().associate { (it.s("header") ?: "") to it.o("participant") }
    fun rank(h: String) = ranks[h]?.i("rank")
    fun value(h: String) = ranks[h]?.d("value")
    rank("Goals per match")?.let { r ->
        if (r <= 3) out += Insight("⚽", "از بهترین خط‌های حمله لیگ (رتبه ${fa(r)})؛ ${dec(value("Goals per match"), 1)} گل در هر بازی", true)
        else if (r > n - 4) out += Insight("🥶", "خط حمله کم‌گل؛ فقط ${dec(value("Goals per match"), 1)} گل در هر بازی", false)
    }
    rank("Goals conceded per match")?.let { r ->
        if (r <= 3) out += Insight("🧱", "دفاع محکم (رتبه ${fa(r)} لیگ)؛ ${dec(value("Goals conceded per match"), 1)} گل خورده در هر بازی", true)
        else if (r > n - 4) out += Insight("🕳️", "دفاع شکننده؛ ${dec(value("Goals conceded per match"), 1)} گل خورده در هر بازی", false)
    }
    rank("Average possession")?.let { r ->
        if (r <= 3) out += Insight("🎯", "بازی مالکانه؛ میانگین ${fa(value("Average possession")?.toInt())}٪ مالکیت توپ", true)
        else if (r > n - 4) out += Insight("↩️", "کم توپ در اختیار می‌گیرد (${fa(value("Average possession")?.toInt())}٪)؛ بیشتر با ضدحمله بازی می‌کند", false)
    }
    rank("Set piece goals")?.takeIf { it <= 3 }?.let { out += Insight("🚩", "روی ضربات ایستگاهی خطرناک است (رتبه ${fa(it)} لیگ)", true) }
    rank("Set piece goals conceded")?.takeIf { it > n - 4 }?.let { out += Insight("🚩", "روی ضربات ایستگاهی زیاد گل می‌خورد", false) }

    // recent form
    val form = d.path("overview").a("teamForm").objs().takeLast(5).mapNotNull { it.s("resultString") }
    val w = form.count { it == "W" }; val l = form.count { it == "L" }
    if (form.size >= 4) when {
        w >= 4 -> out += Insight("🔥", "در فرم عالی: ${fa(w)} برد در ${fa(form.size)} بازی اخیر", true)
        l >= 3 -> out += Insight("📉", "فرم بد: ${fa(l)} باخت در ${fa(form.size)} بازی اخیر", false)
    }
    // home / away record in the league
    table?.a(if (isHome) "home" else "away")?.objs()?.firstOrNull { it.i("id") == id }?.let { r ->
        val pl = r.i("played") ?: 0; val wn = r.i("wins") ?: 0; val ls = r.i("losses") ?: 0
        val where = if (isHome) "در خانه" else "بیرون از خانه"
        if (pl >= 3 && wn * 100 / pl >= 70) out += Insight(if (isHome) "🏟️" else "✈️", "$where قوی: ${fa(wn)} برد در ${fa(pl)} بازی", true)
        else if (pl >= 3 && ls * 100 / pl >= 50) out += Insight(if (isHome) "🏟️" else "✈️", "$where ضعیف: ${fa(ls)} باخت در ${fa(pl)} بازی", false)
    }

    // goal by goal in the last league / cup matches (friendlies left out)
    val recent = d.path("fixtures.allFixtures").a("fixtures").objs()
        .filter { it.path("status").b("finished") && (it.o("tournament").i("leagueId") ?: 0) !in FRIENDLIES }.takeLast(6)
    val details = recent.map { f -> async { runCatching { Api.fm("matchDetails", mapOf("matchId" to f.s("id")), 6 * 3_600_000) }.getOrNull() } }.awaitAll()
    val scoredBy = IntArray(6); val concededBy = IntArray(6)
    var sHead = 0; var sSet = 0; var sFast = 0; var sAll = 0
    var cHead = 0; var cSet = 0; var cFast = 0; var cAll = 0
    var used = 0
    for (md in details.filterNotNull()) {
        used++
        for (g in md.path("content.shotmap").a("shots").objs().filter { it.s("eventType") == "Goal" }) {
            val ours = (g.i("teamId") == id) != g.b("isOwnGoal")
            val sl = slice(g.i("min") ?: 0)
            val head = g.s("shotType") == "Header"
            val set = g.s("situation") in SET_PIECES
            val fast = g.s("situation") == "FastBreak"
            if (ours) { scoredBy[sl]++; sAll++; if (head) sHead++; if (set) sSet++; if (fast) sFast++ }
            else { concededBy[sl]++; cAll++; if (head) cHead++; if (set) cSet++; if (fast) cFast++ }
        }
    }
    if (used >= 3) {
        val m = "در ${fa(used)} بازی اخیر"
        if (cAll >= 3 && pct(cHead, cAll) >= 35) out += Insight("🗣️", "${fa(pct(cHead, cAll))}٪ گل‌هایی که خورده با ضربه سر بوده ($m)", false)
        if (cAll >= 3 && pct(cSet, cAll) >= 35) out += Insight("🚩", "${fa(pct(cSet, cAll))}٪ گل‌های خورده از ضربات ایستگاهی ($m)", false)
        if (cAll >= 3 && pct(concededBy[5], cAll) >= 35) out += Insight("🥵", "در ۱۵ دقیقه آخر خسته می‌شود: ${fa(pct(concededBy[5], cAll))}٪ گل‌های خورده بعد از دقیقه ۷۵", false)
        if (cAll >= 3 && pct(concededBy[0], cAll) >= 30) out += Insight("⏰", "شروع بازی را بد آغاز می‌کند: ${fa(pct(concededBy[0], cAll))}٪ گل‌های خورده در ۱۵ دقیقه اول", false)
        if (cFast >= 2) out += Insight("⚡", "در برابر ضدحمله آسیب‌پذیر است (${fa(cFast)} گل خورده از ضدحمله)", false)
        if (sAll >= 3 && pct(sHead, sAll) >= 30) out += Insight("🗣️", "در ضربه سر خطرناک است: ${fa(pct(sHead, sAll))}٪ گل‌هایش با سر ($m)", true)
        if (sAll >= 3 && pct(sSet, sAll) >= 35) out += Insight("🚩", "${fa(pct(sSet, sAll))}٪ گل‌هایش از ضربات ایستگاهی ($m)", true)
        if (sAll >= 3 && pct(scoredBy[5], sAll) >= 35) out += Insight("⏱️", "تا آخرین دقیقه خطرناک است: ${fa(pct(scoredBy[5], sAll))}٪ گل‌هایش بعد از دقیقه ۷۵", true)
        if (sFast >= 2) out += Insight("⚡", "ضدحمله‌های سریع و کشنده (${fa(sFast)} گل از ضدحمله)", true)
        if (cAll == 0) out += Insight("🧤", "$m هیچ گلی نخورده", true)
        if (sAll == 0) out += Insight("🥶", "$m هیچ گلی نزده", false)
    }

    // the man to watch
    val top = d.o("stats").a("players").objs().firstOrNull { it.s("header") == "Top scorer" }?.o("participant")
    TeamRadar(
        id, name, out.sortedBy { !it.good }, scoredBy, concededBy, used,
        top?.i("id"), top?.s("name"), top?.i("value")?.let { "${fa(it)} گل در این فصل" },
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
        CardTitle(if (mine == m.homeId || mine == m.awayId) "🛰️ رادار حریف" else "🛰️ رادار دو تیم", sub = "نقاط قوت و ضعف")
        Box(Modifier.padding(horizontal = 12.dp)) {
            Seg(listOf("h" to teamName(m.homeId, m.homeName), "a" to teamName(m.awayId, m.awayName)), side) { side = it }
        }
        when (val s = l.state) {
            is Load.Loading -> Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                T("در حال بررسی بازی‌های اخیر…", 13, color = C.mute)
                Skeleton(2)
            }
            is Load.Err -> ErrorView({ l.state = Load.Loading; l.reload() }, "رادار آماده نشد")
            is Load.Ok -> {
                val r = if (side == "h") s.v.first else s.v.second
                val color = if (side == "h") m.homeColor else m.awayColor
                Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (r.insights.isEmpty()) T("نکته برجسته‌ای پیدا نشد؛ تیمی متعادل است.", 13, color = C.mute)
                    r.insights.forEach { ins ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background((if (ins.good) C.win else C.away).copy(alpha = 0.10f))
                            .padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            T(ins.icon, 18)
                            T(ins.text, 13, FontWeight.Bold, modifier = Modifier.weight(1f))
                            T(if (ins.good) "قوت" else "ضعف", 12, FontWeight.Black, if (ins.good) C.win else C.away)
                        }
                    }
                    if (r.matches >= 3) {
                        T("گل‌ها در طول بازی (${fa(r.matches)} بازی اخیر)", 12, FontWeight.ExtraBold, C.mute, Modifier.padding(top = 6.dp))
                        GoalClock(r.scoredBy, r.concededBy, color)
                    }
                    if (r.keyId != null && r.keyName != null) Row(
                        Modifier.fillMaxWidth().padding(top = 4.dp).clip(RoundedCornerShape(14.dp)).background(C.card2).clickable { nav.player(r.keyId) }.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Face(r.keyId, 40.dp, color)
                        Column(Modifier.weight(1f)) {
                            T("مراقب این بازیکن باشید", 12, color = C.mute)
                            Ltr(r.keyName, style = lat(16.sp))
                        }
                        r.keyText?.let { T(it, 12, FontWeight.Bold, color = C.ink) }
                    }
                    T("بر اساس آمار این فصل و گل‌به‌گل بازی‌های اخیر (FotMob).", 12, color = C.faint)
                }
            }
        }
    }
}

/** Six small columns (0-15 … 76-90+): goals scored above the line, goals let in below. */
@Composable
private fun GoalClock(scored: IntArray, conceded: IntArray, color: Color) {
    val top = maxOf(1, scored.maxOrNull() ?: 0, conceded.maxOrNull() ?: 0)
    val labels = listOf("۱۵", "۳۰", "۴۵", "۶۰", "۷۵", "۹۰")
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
        Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(10.dp).clip(CircleShape).background(color)); T(" گل زده", 12, color = C.mute) }
        Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(10.dp).clip(CircleShape).background(C.live)); T(" گل خورده", 12, color = C.mute) }
    }
}
