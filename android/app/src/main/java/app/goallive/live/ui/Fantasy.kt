package app.goallive.live.ui

import app.goallive.live.L

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.runtime.*
import androidx.compose.ui.draw.*
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.goallive.live.data.Api
import app.goallive.live.data.Clubs
import app.goallive.live.data.MatchItem
import app.goallive.live.data.Store
import app.goallive.live.data.a
import app.goallive.live.data.b
import app.goallive.live.data.d
import app.goallive.live.data.i
import app.goallive.live.data.l
import app.goallive.live.data.o
import app.goallive.live.data.objs
import app.goallive.live.data.parseMatch
import app.goallive.live.data.path
import app.goallive.live.data.s
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt
import kotlin.math.sqrt

/*
 * فوتبال فانتزی: eleven real players within a 100M budget (at most 3 per club, 1 GK, 3–5 DEF, 2–5 MID, 1–3 FWD)
 * and a captain. Each gameweek the team is locked at the first kick-off and scores from the real match data.
 */

private val F_LEAGUES = listOf(47 to L("لیگ برتر انگلیس"), 523 to L("لیگ برتر خلیج فارس"), 87 to L("لالیگا"), 55 to L("سری آ"), 54 to L("بوندس‌لیگا"))
private val POS = listOf(L("دروازه‌بان"), L("مدافع"), L("هافبک"), L("مهاجم"))
private val POS_SHORT = listOf("GK", "DEF", "MID", "FWD")
private val POS_RANGE = listOf(1..1, 3..5, 2..5, 1..3)
private const val BUDGET = 100.0

class FPlayer(val id: Int, val name: String, val teamId: Int, val pos: Int, val price: Double, val rating: Double?, val goals: Int, val assists: Int, val injured: Boolean)

/** Everything about one league needed by the game, loaded once and kept in memory. */
class FLeague(val id: Int, val players: Map<Int, FPlayer>, val teamNames: Map<Int, String>, val rounds: List<Pair<String, List<MatchItem>>>, val current: Int)

private val leagueCache = HashMap<Int, FLeague>()

private suspend fun loadLeague(id: Int): FLeague = leagueCache[id] ?: coroutineScope {
    val d = Api.fm("leagues", mapOf("id" to id, "ccode3" to "IRN"), 3_600_000)
    val rows = d.a("table")?.optJSONObject(0)?.o("data").let { t -> t.o("table").a("all").objs().ifEmpty { t.a("tables").objs().flatMap { it.o("table").a("all").objs() } } }
    val teamNames = rows.associate { (it.i("id") ?: 0) to (it.s("shortName") ?: it.s("name") ?: "") }
    val squads = teamNames.keys.map { tid ->
        async { tid to runCatching { Api.fm("teams", mapOf("id" to tid, "ccode3" to "IRN"), 6 * 3_600_000).o("squad").a("squad").objs() }.getOrDefault(emptyList()) }
    }.awaitAll()
    val lines = mapOf("keepers" to 0, "defenders" to 1, "midfielders" to 2, "attackers" to 3)
    val raw = squads.flatMap { (tid, groups) ->
        groups.flatMap { g -> val pos = lines[g.s("title")] ?: return@flatMap emptyList(); g.a("members").objs().map { Triple(tid, pos, it) } }
    }
    // price from market value, scaled to this league: the most valuable player costs about 13.5M, the cheapest 4M
    val maxV = raw.maxOfOrNull { it.third.l("transferValue") ?: 0L }?.coerceAtLeast(1L) ?: 1L
    val players = raw.associate { (tid, pos, m) ->
        val v = (m.l("transferValue") ?: 0L).toDouble()
        val price = ((4.0 + 9.5 * sqrt(v / maxV) + if (pos == 3) 0.5 else 0.0) * 2).roundToInt() / 2.0
        (m.i("id") ?: 0) to FPlayer(m.i("id") ?: 0, m.s("name") ?: "", tid, pos, price.coerceIn(4.0, 14.0), m.d("rating"), m.i("goals") ?: 0, m.i("assists") ?: 0, m.b("injured"))
    }
    val all = d.o("fixtures").a("allMatches").objs()
    val rounds = all.groupBy { it.s("round") ?: it.s("roundName") ?: "" }.map { (r, ms) -> r to ms.map { parseMatch(it) }.sortedBy { it.time } }
    val firstIdx = d.o("fixtures").o("firstUnplayedMatch").i("firstUnplayedMatchIndex")
    val firstRound = firstIdx?.let { all.getOrNull(it) }?.let { it.s("round") ?: it.s("roundName") }
    val current = rounds.indexOfFirst { it.first == firstRound }.let { if (it < 0) rounds.size - 1 else it }
    FLeague(id, players, teamNames, rounds, current).also { leagueCache[id] = it }
}

/** The saved game: current squad, captain, and the squad frozen at each gameweek's deadline. */
private class FState(val o: JSONObject) {
    val league get() = o.optInt("league")
    val team: MutableList<Int> get() = o.optJSONArray("team").let { a -> MutableList(a?.length() ?: 0) { a!!.getInt(it) } }
    val captain get() = o.optInt("captain")
    fun frozen(round: String): Pair<List<Int>, Int>? = o.optJSONObject("rounds")?.optJSONObject(round)?.let { r ->
        r.getJSONArray("team").let { a -> List(a.length()) { a.getInt(it) } } to r.optInt("captain")
    }
    companion object {
        fun load() = FState(runCatching { JSONObject(Store.prefs().getString("fantasy", "{}")!!) }.getOrDefault(JSONObject()))
    }
    fun save() { Store.prefs().edit().putString("fantasy", o.toString()).apply() }
    fun setTeam(t: List<Int>, c: Int) { o.put("team", JSONArray(t)).put("captain", c); save() }
    fun freeze(round: String) {
        val r = o.optJSONObject("rounds") ?: JSONObject().also { o.put("rounds", it) }
        if (!r.has(round)) { r.put(round, JSONObject().put("team", JSONArray(team)).put("captain", captain)); save() }
    }
    fun recordPoints(round: String, pts: Int) { val p = o.optJSONObject("points") ?: JSONObject().also { o.put("points", it) }; p.put(round, pts); save() }
    fun points(): Map<String, Int> = o.optJSONObject("points")?.let { p -> p.keys().asSequence().associateWith { p.optInt(it) } } ?: emptyMap()
}

/** Points of one player in one match, with the reasons (FPL-style rules). */
private fun score(p: FPlayer, m: JSONObject): Pair<Int, List<String>> {
    val lu = m.path("content.lineup")
    val home = lu.o("homeTeam"); val away = lu.o("awayTeam")
    val isHome = home.i("id") == p.teamId
    val side = if (isHome) home else away
    val lp = (side.a("starters").objs() + side.a("subs").objs()).firstOrNull { it.i("id") == p.id } ?: return 0 to emptyList()
    val starter = side.a("starters").objs().any { it.i("id") == p.id }
    val subEv = lp.path("performance").a("substitutionEvents").objs()
    val ps = m.path("content.playerStats")?.optJSONObject(p.id.toString())
    var mins = ps.a("stats").objs().firstNotNullOfOrNull { it.o("stats")?.o("Minutes played")?.o("stat")?.i("value") }
    if (mins == null) mins = if (starter) subEv.firstOrNull { it.s("type") == "subOut" }?.i("time") ?: 90
        else subEv.firstOrNull { it.s("type") == "subIn" }?.i("time")?.let { 90 - it } ?: 0
    if (mins <= 0) return 0 to emptyList()
    val ev = lp.path("performance").a("events").objs().map { it.s("type") }
    val why = ArrayList<String>()
    var pts = if (mins >= 60) 2 else 1; why += L("بازی {0}′: +{1}", mins, pts)
    val g = ev.count { it == "goal" }
    if (g > 0) { val per = when (p.pos) { 0, 1 -> 6; 2 -> 5; else -> 4 }; pts += g * per; why += L("{0} گل: +{1}", g, g * per) }
    val a = ev.count { it == "assist" }
    if (a > 0) { pts += a * 3; why += L("{0} پاس گل: +{1}", a, a * 3) }
    val conceded = m.path("header").a("teams").objs().getOrNull(if (isHome) 1 else 0).i("score") ?: 0
    if (conceded == 0 && mins >= 60 && p.pos <= 2) { val cs = if (p.pos <= 1) 4 else 1; pts += cs; why += L("کلین‌شیت: +{0}", cs) }
    if (p.pos <= 1 && conceded >= 2) { pts -= conceded / 2; why += L("{0} گل خورده: -{1}", conceded, conceded / 2) }
    if ("ownGoal" in ev) { pts -= 2; why += L("گل به خودی: -۲") }
    if ("redCard" in ev || "yellowRedCard" in ev) { pts -= 3; why += L("کارت قرمز: -۳") } else if ("yellowCard" in ev) { pts -= 1; why += L("کارت زرد: -۱") }
    val r = lp.path("performance").d("rating") ?: ps.a("stats").objs().firstNotNullOfOrNull { it.o("stats")?.o("FotMob rating")?.o("stat")?.d("value") }
    if (r != null) { val bonus = when { r >= 8.5 -> 3; r >= 7.5 -> 2; r >= 7.0 -> 1; else -> 0 }; if (bonus > 0) { pts += bonus; why += L("پاداش نمره {0}: +{1}", dec(r, 1), bonus) } }
    return pts to why
}

@Composable
fun FantasyScreen() {
    var state by remember { mutableStateOf(FState.load()) }
    var tick by remember { mutableIntStateOf(0) }
    @Suppress("UNUSED_VARIABLE") val redraw = tick
    if (state.league == 0) {
        var help by rememberTip("fantasy")
        if (help) HowToSheet("⚽", L("فوتبال فانتزی گل‌لایو"), FANTASY_STEPS, onClose = { Store.markTip("fantasy"); help = false })
        LeaguePick { id -> state.o.put("league", id); state.save(); state = FState.load() }
        return
    }
    var help by rememberTip("fantasy")
    if (help) HowToSheet("⚽", L("فوتبال فانتزی گل‌لایو"), FANTASY_STEPS, onClose = { Store.markTip("fantasy"); help = false })
    val l = rememberLoader(state.league) { loadLeague(state.league) }
    LoadBox(l, 6) { lg -> FantasyBody(lg, state, { tick++ }) { state.o.remove("league"); state.o.remove("team"); state.o.remove("captain"); state.save(); state = FState.load() } }
}

@Composable
private fun LeaguePick(onPick: (Int) -> Unit) {
    val accent = LocalAccent.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = BottomSpace)) {
        item {
            Column(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(64.dp).glow(accent.copy(alpha = 0.4f), 10.dp, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp)).background(accent),
                    contentAlignment = Alignment.Center) { androidx.compose.material3.Icon(Ic.shirt, null, Modifier.size(34.dp), tint = LocalAccentInk.current) }
                T(L("فوتبال فانتزی"), 22, FontWeight.Black)
                T(L("با ۱۰۰ میلیون بودجه ۱۱ بازیکن واقعی بخر، کاپیتان انتخاب کن و از روی عملکرد واقعی‌شان در هر هفته امتیاز بگیر."), 13, color = C.mute, align = TextAlign.Center)
            }
        }
        item { SectionTitle(L("لیگ خودت را انتخاب کن")) }
        item {
            Card {
                F_LEAGUES.forEachIndexed { i, (id, name) ->
                    ListRow(first = i == 0, onClick = { onPick(id) }) {
                        LeagueLogo(id, 30.dp); T(name, 15, FontWeight.Bold, modifier = Modifier.weight(1f)); androidx.compose.material3.Icon(Ic.chev, null, Modifier.size(18.dp), tint = C.mute)
                    }
                }
            }
        }
        item {
            Card(Modifier.padding(top = 12.dp)) {
                CardTitle(L("قانون امتیازها"))
                T(L("بازی کمتر از ۶۰ دقیقه ۱ و بیشتر ۲ امتیاز · گل: دروازه‌بان و مدافع ۶، هافبک ۵، مهاجم ۴ · پاس گل ۳ · کلین‌شیت: دروازه‌بان و مدافع ۴، هافبک ۱ · هر ۲ گل خورده (دروازه‌بان و مدافع) ۱- · کارت زرد ۱- · قرمز ۳- · گل به خودی ۲- · پاداش نمره: ۷+ یک، ۷.۵+ دو، ۸.۵+ سه · امتیاز کاپیتان دو برابر"),
                    13, color = C.ink.copy(alpha = 0.85f), modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp))
            }
        }
    }
}

@Composable
private fun FantasyBody(lg: FLeague, st: FState, changed: () -> Unit, reset: () -> Unit) {
    val ctx = LocalContext.current
    val accent = LocalAccent.current
    val (roundName, roundMatches) = lg.rounds.getOrNull(lg.current) ?: ("" to emptyList())
    val deadline = roundMatches.minOfOrNull { it.time } ?: Long.MAX_VALUE
    val locked = System.currentTimeMillis() >= deadline
    if (locked) st.freeze(roundName)
    val frozen = st.frozen(roundName)
    val team = if (locked && frozen != null) frozen.first else st.team
    val captain = if (locked && frozen != null) frozen.second else st.captain
    val picks = team.mapNotNull { lg.players[it] }
    val spent = picks.sumOf { it.price }
    var pickPos by remember { mutableStateOf<Int?>(null) }
    var open by remember { mutableStateOf<FPlayer?>(null) }

    // live points for this gameweek
    val clubs = picks.map { it.teamId }.toSet()
    val relevant = roundMatches.filter { (it.home.id in clubs || it.away.id in clubs) && (it.started || it.finished) }
    val pts = rememberLoader(lg.id, roundName, team.joinToString(","), relevant.map { it.id }.joinToString(","),
        poll = { _ -> if (relevant.any { it.live }) 60_000L else null }) {
        val details = coroutineScope { relevant.map { m -> async { runCatching { Api.fm("matchDetails", mapOf("matchId" to m.id), 30_000) }.getOrNull() } }.awaitAll().filterNotNull() }
        picks.associate { p ->
            val ms = details.filter { d -> d.path("header").a("teams").objs().any { it.i("id") == p.teamId } }
            var total = 0; val why = ArrayList<String>()
            ms.forEach { d -> val (x, w) = score(p, d); total += x; why += w }
            p.id to (total to why)
        }
    }
    val livePts = pts.value
    val roundTotal = livePts?.entries?.sumOf { (id, v) -> if (id == captain) v.first * 2 else v.first } ?: 0
    if (locked && livePts != null && relevant.isNotEmpty() && relevant.all { it.finished }) st.recordPoints(roundName, roundTotal)
    val history = st.points()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = BottomSpace), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(
                Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(24.dp)).drawBehind { cssLinear(135f, 0f to accent.copy(alpha = 0.4f), 1f to C.card) }
                    .border(1.dp, C.line2, RoundedCornerShape(24.dp)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LeagueLogo(lg.id, 26.dp)
                    Column(Modifier.weight(1f)) {
                        T(L("فانتزی · {0}", F_LEAGUES.firstOrNull { it.first == lg.id }?.second ?: ""), 15, FontWeight.Black)
                        T(if (roundName.all(Char::isDigit)) L("هفته {0}", roundName) else roundName, 12, color = C.mute)
                    }
                    Pill(L("تغییر لیگ"), onClick = reset)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Stat(if (locked) "$roundTotal" else "–", L("امتیاز این هفته"), Modifier.weight(1f), accent)
                    Stat("${history.values.sum() + if (locked && roundName !in history) roundTotal else 0}", L("امتیاز کل"), Modifier.weight(1f), C.ink)
                    Stat(dec(BUDGET - spent, 1), L("بودجه باقی (م)"), Modifier.weight(1f), if (spent > BUDGET) C.loss else C.win)
                }
                T(if (!locked) L("مهلت تغییر تیم: {0} ({1} ساعت {2})", countdown(deadline), persianDate(deadline, "EEEE d MMMM"), timeOf(deadline))
                    else L("تیم این هفته قفل شد") + if (relevant.any { it.live }) L(" · بازی‌ها در جریان است و امتیازها زنده حساب می‌شود") else "", 12, FontWeight.Bold, C.ink.copy(alpha = 0.9f))
            }
        }
        item { FPitch(picks, captain, locked, livePts?.mapValues { it.value.first }, lg) { open = it } }
        if (!locked) item {
            val counts = (0..3).map { pos -> picks.count { it.pos == pos } }
            val perClub = picks.groupingBy { it.teamId }.eachCount()
            Card {
                CardTitle(L("چیدن تیم"), sub = L("{0}/۱۱ بازیکن", picks.size))
                Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (0..3).forEach { pos ->
                        val ok = counts[pos] in POS_RANGE[pos]
                        val full = counts[pos] >= POS_RANGE[pos].last || picks.size >= 11
                        Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(if (ok) C.win.copy(alpha = 0.12f) else C.card2)
                            .clickable(enabled = !full) { pickPos = pos }.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            T(POS[pos], 11, FontWeight.Bold, C.mute)
                            T("${counts[pos]}", 18, FontWeight.Black, if (ok) C.win else C.ink)
                            T("${POS_RANGE[pos].first}–${POS_RANGE[pos].last}", 10, color = C.faint)
                            if (!full) T(L("+ افزودن"), 11, FontWeight.ExtraBold, accent)
                        }
                    }
                }
                val problems = buildList {
                    if (spent > BUDGET) add(L("از بودجه بیشتر خرج کرده‌ای"))
                    perClub.filter { it.value > 3 }.keys.forEach { add(L("بیشتر از ۳ بازیکن از {0}", lg.teamNames[it])) }
                    if (picks.size == 11 && (0..3).any { counts[it] !in POS_RANGE[it] }) add(L("ترکیب پست‌ها مجاز نیست"))
                    if (picks.size == 11 && captain !in team) add(L("کاپیتان انتخاب نشده؛ روی یک بازیکن بزن"))
                }
                if (picks.size == 11 && problems.isEmpty()) T(L("✓ تیمت آماده است و تا مهلت هر تغییری بدهی ذخیره می‌شود."), 12, FontWeight.Bold, C.win, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp))
                problems.forEach { T("• $it", 12, FontWeight.Bold, C.loss, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 6.dp)) }
            }
        }
        if (locked && livePts != null) item {
            Card {
                CardTitle(L("امتیاز بازیکن‌ها در این هفته"))
                picks.sortedByDescending { livePts[it.id]?.first ?: 0 }.forEachIndexed { i, p ->
                    val v = (livePts[p.id]?.first ?: 0) * if (p.id == captain) 2 else 1
                    ListRow(first = i == 0, onClick = { open = p }) {
                        Face(p.id, 34.dp)
                        Column(Modifier.weight(1f)) { Ltr(p.name + if (p.id == captain) " (C)" else ""); T("${POS[p.pos]} · ${lg.teamNames[p.teamId] ?: ""}", 11, color = C.mute) }
                        T("$v", 17, FontWeight.Black, if (v > 0) accent else C.mute)
                    }
                }
            }
        }
        if (history.isNotEmpty()) item {
            Card {
                CardTitle(L("هفته‌های قبل"))
                history.entries.sortedByDescending { it.key.toIntOrNull() ?: 0 }.forEachIndexed { i, (r, v) ->
                    ListRow(first = i == 0) { T(if (r.all(Char::isDigit)) L("هفته {0}", r) else r, 14, FontWeight.Bold, modifier = Modifier.weight(1f)); T(L("{0} امتیاز", v), 14, FontWeight.Black, accent) }
                }
                Row(Modifier.padding(12.dp)) {
                    Pill(L("اشتراک‌گذاری"), PillKind.Accent, icon = Ic.share, onClick = {
                        val t = L("امتیاز فانتزی من در گل‌لایو: {0} امتیاز در {1} هفته ⚽🏆", history.values.sum(), history.size)
                        ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, t), L("اشتراک‌گذاری")))
                    })
                }
            }
        }
        item { T(L("قیمت‌ها از ارزش بازار بازیکن‌ها ساخته شده و امتیازها از آمار واقعی FotMob. تیم و امتیازها روی همین گوشی ذخیره می‌شود."), 11, color = C.faint, align = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
    }

    pickPos?.let { pos -> Picker(lg, pos, picks, spent, roundMatches, { pickPos = null }) { p -> val t = st.team.apply { add(p.id) }; st.setTeam(t, if (st.captain == 0) p.id else st.captain); changed(); pickPos = null } }
    open?.let { p -> PlayerActions(p, lg, p.id == captain, !locked, livePts?.get(p.id), { open = null },
        onCaptain = { st.setTeam(st.team, p.id); changed(); open = null },
        onRemove = { val t = st.team.apply { remove(p.id) }; st.setTeam(t, if (st.captain == p.id) t.firstOrNull() ?: 0 else st.captain); changed(); open = null }) }
}

@Composable
private fun Stat(big: String, small: String, modifier: Modifier, color: Color) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(alpha = 0.25f)).padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        T(big, 22, FontWeight.Black, color); T(small, 11, color = C.mute, maxLines = 1)
    }
}

/** The eleven by line: forwards at the top, goalkeeper at the bottom. Shows price before the deadline and points after. */
@Composable
private fun FPitch(picks: List<FPlayer>, captain: Int, locked: Boolean, pts: Map<Int, Int>?, lg: FLeague, onOpen: (FPlayer) -> Unit) {
    BoxWithConstraints(
        Modifier.fillMaxWidth().aspectRatio(68f / 84f).clip(RoundedCornerShape(20.dp)).drawBehind {
            val n = 10
            for (i in 0 until n) drawRect(if (i % 2 == 0) C.pitch else C.pitch2, Offset(0f, size.height * i / n), Size(size.width, size.height / n + 1))
            val c = Color.White.copy(alpha = 0.45f); val st = Stroke(1.5f)
            drawRect(c, Offset(8f, 8f), Size(size.width - 16f, size.height - 16f), style = st)
            drawRect(c, Offset(size.width * 0.2f, size.height - 8f - size.height * 0.16f), Size(size.width * 0.6f, size.height * 0.16f), style = st)
            drawCircle(c, size.width * 0.13f, Offset(size.width / 2, 8f), style = st)
        },
    ) {
        val w = maxWidth; val h = maxHeight
        if (picks.isEmpty()) T(L("هنوز بازیکنی نخریده‌ای؛ از کادر «چیدن تیم» شروع کن"), 13, FontWeight.Bold, Color.White, Modifier.align(Alignment.Center).padding(24.dp), align = TextAlign.Center)
        for (pos in 0..3) {
            val row = picks.filter { it.pos == pos }
            val y = listOf(0.86f, 0.62f, 0.38f, 0.14f)[pos]
            row.forEachIndexed { j, p ->
                val x = (j + 1f) / (row.size + 1f)
                Column(Modifier.align(AbsoluteAlignment.TopLeft).absoluteOffset(w * x - 36.dp, h * y - 30.dp).width(72.dp).clickable { onOpen(p) },
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Box {
                        val col = Clubs.get(p.teamId)?.let { Color(it.c1) } ?: hueColor(p.teamId)
                        Box(Modifier.size(42.dp).clip(CircleShape).background(col).border(2.dp, Color.White, CircleShape), contentAlignment = Alignment.Center) {
                            Crest(p.teamId, 26.dp)
                            coil.compose.AsyncImage(playerFace(p.id), null, Modifier.fillMaxSize().clip(CircleShape), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                        }
                        if (p.id == captain) Box(Modifier.align(Alignment.TopStart).absoluteOffset((-6).dp, (-4).dp).size(17.dp).clip(CircleShape).background(C.gold),
                            contentAlignment = Alignment.Center) { Text("C", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color(0xFF2A1A00)) }
                        if (p.injured) Box(Modifier.align(Alignment.TopEnd).absoluteOffset(6.dp, (-4).dp).size(16.dp).clip(CircleShape).background(C.live), contentAlignment = Alignment.Center) {
                            Text("!", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color.White)
                        }
                    }
                    Ltr(p.name.substringAfterLast(' '), Modifier.padding(top = 3.dp).clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)).background(Color.White).padding(horizontal = 5.dp),
                        style = lat(11.sp), color = Color(0xFF111111), align = TextAlign.Center)
                    val label = if (locked) "${(pts?.get(p.id) ?: 0) * if (p.id == captain) 2 else 1}" else dec(p.price, 1)
                    Box(Modifier.clip(RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp)).background(Color(0xFF14321F)).padding(horizontal = 8.dp)) {
                        T(label, 11, FontWeight.Black, Color.White)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Picker(lg: FLeague, pos: Int, picks: List<FPlayer>, spent: Double, round: List<MatchItem>, onClose: () -> Unit, onPick: (FPlayer) -> Unit) {
    var q by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf("price") }
    val perClub = picks.groupingBy { it.teamId }.eachCount()
    val opponent = { tid: Int -> round.firstOrNull { it.home.id == tid || it.away.id == tid }?.let { m -> if (m.home.id == tid) lg.teamNames[m.away.id] + L(" (خانه)") else lg.teamNames[m.home.id] + L(" (بیرون)") } }
    val list = lg.players.values.filter { it.pos == pos && picks.none { x -> x.id == it.id } }
        .filter { q.isBlank() || it.name.contains(q.trim(), true) || (lg.teamNames[it.teamId] ?: "").contains(q.trim(), true) || (Clubs.get(it.teamId)?.fa ?: "").contains(q.trim()) }
        .sortedByDescending { when (sort) { "rating" -> it.rating ?: 0.0; "goals" -> (it.goals * 3 + it.assists).toDouble(); else -> it.price } }
        .take(80)
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = C.cardSolid,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            T(L("خرید {0} · بودجه باقی {1}م", POS[pos], dec(BUDGET - spent, 1)), 16, FontWeight.Black, modifier = Modifier.padding(horizontal = 16.dp))
            Box(Modifier.padding(horizontal = 16.dp)) { SearchField(q, L("نام بازیکن یا باشگاه…")) { q = it } }
            Box(Modifier.padding(horizontal = 16.dp)) { Seg(listOf("price" to L("گران‌ترین"), "rating" to L("بهترین نمره"), "goals" to L("گل و پاس گل")), sort) { sort = it } }
            LazyColumn(Modifier.fillMaxWidth().height(460.dp).padding(top = 8.dp)) {
                items(list) { p ->
                    val tooMuch = spent + p.price > BUDGET
                    val clubFull = (perClub[p.teamId] ?: 0) >= 3
                    val ok = !tooMuch && !clubFull
                    ListRow(onClick = if (ok) ({ onPick(p) }) else null) {
                        Face(p.id, 36.dp)
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Ltr(p.name, Modifier.weight(1f, false), color = if (ok) C.ink else C.faint)
                                if (p.injured) T(L("مصدوم"), 10, FontWeight.Bold, C.live)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Crest(p.teamId, 14.dp)
                                T(listOfNotNull(Clubs.get(p.teamId)?.fa ?: lg.teamNames[p.teamId], L("{0} گل", p.goals), L("{0} پاس", p.assists), opponent(p.teamId)?.let { L("حریف: {0}", it) }).joinToString(" · "), 11, color = C.mute, maxLines = 1)
                            }
                            if (clubFull) T(L("۳ بازیکن از این باشگاه داری"), 10, color = C.loss)
                        }
                        Rating(p.rating, small = true)
                        T(dec(p.price, 1), 15, FontWeight.Black, if (tooMuch) C.loss else LocalAccent.current)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerActions(p: FPlayer, lg: FLeague, isCaptain: Boolean, editable: Boolean, pts: Pair<Int, List<String>>?, onClose: () -> Unit, onCaptain: () -> Unit, onRemove: () -> Unit) {
    val nav = LocalNav.current
    ModalBottomSheet(onDismissRequest = onClose, containerColor = C.cardSolid, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Face(p.id, 56.dp)
                Column(Modifier.weight(1f)) {
                    Ltr(p.name, style = lat(19.sp, FontWeight.ExtraBold))
                    T(L("{0} · {1} · قیمت {2}م", POS[p.pos], Clubs.get(p.teamId)?.fa ?: lg.teamNames[p.teamId] ?: "", dec(p.price, 1)), 12, color = C.mute)
                }
                Rating(p.rating)
            }
            if (pts != null) {
                SubHead(L("امتیاز این هفته: {0}", pts.first * if (isCaptain) 2 else 1) + if (isCaptain) L(" (کاپیتان ×۲)") else "")
                if (pts.second.isEmpty()) T(L("هنوز بازی نکرده"), 13, color = C.mute, modifier = Modifier.padding(horizontal = 16.dp))
                pts.second.forEach { T("• $it", 13, modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) }
            }
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (editable && !isCaptain) Pill(L("کاپیتان کن"), PillKind.Accent, onClick = onCaptain)
                if (editable) Pill(L("فروش"), PillKind.Live, onClick = onRemove)
                Pill(L("پروفایل"), onClick = { onClose(); nav.player(p.id) })
            }
        }
    }
}
