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
import androidx.compose.ui.draw.alpha
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
 * فوتبال فانتزی: a squad of fifteen real players within a 140M budget (2 GK, 5 DEF, 5 MID, 3 FWD, at most 3 per club):
 * eleven on the pitch (1 GK, 3–5 DEF, 2–5 MID, 1–3 FWD) and four on the bench, in order, and a captain. Each gameweek the
 * team is locked at the first kick-off and scores from the real match data; a starter who doesn't play is replaced by the
 * first bench player who did and keeps the shape legal. Signed in, the squad and the points go to the server for the
 * fantasy leagues.
 */

private val F_LEAGUES = listOf(47 to L("لیگ برتر انگلیس"), 42 to L("لیگ قهرمانان اروپا"), 523 to L("لیگ برتر خلیج فارس"), 87 to L("لالیگا"), 55 to L("سری آ"), 54 to L("بوندس‌لیگا"))

/** Each league's own colour for its fantasy page: the accent, the buttons, the header's glow. */
internal fun fantasyColor(id: Int) = when (id) {
    47 -> Color(0xFFB04BFF)   // Premier League purple
    42 -> Color(0xFF4FB6FF)   // Champions League night blue
    523 -> Color(0xFF2BC46F)  // Persian Gulf green
    87 -> Color(0xFFFF7A1A)   // LaLiga orange
    55 -> Color(0xFF3B82F6)   // Serie A blue
    54 -> Color(0xFFE5243B)   // Bundesliga red
    else -> Color(0xFFC6FF3D)
}

/** The page in [id]'s colours. */
@Composable
internal fun FantasyTheme(id: Int, content: @Composable () -> Unit) {
    if (id == 0) { content(); return }
    val c = fantasyColor(id)
    androidx.compose.runtime.CompositionLocalProvider(LocalAccent provides c, LocalAccentInk provides inkOn(c)) {
        Box(Modifier.fillMaxSize()) {
            // the league's colour glowing down from the top
            Box(Modifier.fillMaxWidth().height(420.dp).drawBehind {
                drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(c.copy(alpha = 0.22f), Color.Transparent)))
                if (id == 42) for (k in 0 until 40) {
                    // the Champions League's stars
                    val x = ((k * 7919) % 1000) / 1000f * size.width; val y = ((k * 104729) % 1000) / 1000f * size.height * 0.8f
                    drawCircle(Color.White.copy(alpha = 0.10f + (k % 5) * 0.06f), (1 + k % 3).dp.toPx(), Offset(x, y))
                }
            })
            content()
        }
    }
}
private val POS = listOf(L("دروازه‌بان"), L("مدافع"), L("هافبک"), L("مهاجم"))
private val POS_SHORT = listOf("GK", "DEF", "MID", "FWD")
private val POS_RANGE = listOf(1..1, 3..5, 2..5, 1..3)
/** How many of each line the squad holds. */
private val SQUAD = listOf(2, 5, 5, 3)
private const val SQUAD_SIZE = 15
private const val BUDGET = 140.0

/** Whether eleven players make a legal shape. */
private fun legalXi(xi: List<FPlayer>) = xi.size == 11 && (0..3).all { pos -> xi.count { it.pos == pos } in POS_RANGE[pos] }

class FPlayer(val id: Int, val name: String, val teamId: Int, val pos: Int, val price: Double, val rating: Double?, val goals: Int, val assists: Int, val injured: Boolean)

/** Everything about one league needed by the game, loaded once and kept in memory. */
class FLeague(val id: Int, val players: Map<Int, FPlayer>, val teamNames: Map<Int, String>, val rounds: List<Pair<String, List<MatchItem>>>, val current: Int,
              /** Table position of each club, for how hard a fixture is. */
              val ranks: Map<Int, Int> = emptyMap())

private val leagueCache = HashMap<Int, FLeague>()

/** Players of a league, kept on the phone for half a day: twenty full team pages are slow to fetch and heavy to read. */
private fun playersFile(id: Int) = java.io.File(app.goallive.live.data.Store.cacheDir ?: java.io.File("/tmp"), "fantasy-$id.json")

private fun readPlayers(id: Int): Pair<Map<Int, FPlayer>, Map<Int, String>>? = runCatching {
    val f = playersFile(id)
    if (System.currentTimeMillis() - f.lastModified() > 12 * 3_600_000L) return null
    val o = JSONObject(f.readText())
    val names = o.getJSONObject("teams").let { t -> t.keys().asSequence().associate { it.toInt() to t.getString(it) } }
    val ps = o.getJSONArray("players").objs().associate { p ->
        p.getInt("id") to FPlayer(p.getInt("id"), p.getString("n"), p.getInt("t"), p.getInt("p"), p.getDouble("c"), p.optDouble("r").takeIf { !it.isNaN() },
            p.optInt("g"), p.optInt("a"), p.optBoolean("i"))
    }
    if (ps.size < 50) null else ps to names
}.getOrNull()

private fun writePlayers(id: Int, ps: Map<Int, FPlayer>, names: Map<Int, String>) = runCatching {
    val arr = JSONArray()
    ps.values.forEach { p -> arr.put(JSONObject().put("id", p.id).put("n", p.name).put("t", p.teamId).put("p", p.pos).put("c", p.price).put("r", p.rating ?: Double.NaN)
        .put("g", p.goals).put("a", p.assists).put("i", p.injured)) }
    playersFile(id).writeText(JSONObject().put("teams", JSONObject(names.mapKeys { it.key.toString() })).put("players", arr).toString())
}

private suspend fun loadLeague(id: Int): FLeague = leagueCache[id] ?: coroutineScope {
    val d = Api.fm("leagues", mapOf("id" to id, "ccode3" to "IRN"), 3_600_000)
    val rows = d.a("table")?.optJSONObject(0)?.o("data").let { t -> t.o("table").a("all").objs().ifEmpty { t.a("tables").objs().flatMap { it.o("table").a("all").objs() } } }
    val cached = readPlayers(id)
    val teamNames = cached?.second ?: rows.associate { (it.i("id") ?: 0) to (it.s("shortName") ?: it.s("name") ?: "") }
    // five teams at a time, so a phone on a slow line isn't flooded
    val squads = if (cached != null) emptyList() else teamNames.keys.chunked(5).flatMap { chunk ->
        chunk.map { tid -> async { tid to runCatching { Api.fm("teams", mapOf("id" to tid, "ccode3" to "IRN"), 6 * 3_600_000).o("squad").a("squad").objs() }.getOrDefault(emptyList()) } }.awaitAll()
    }
    val lines = mapOf("keepers" to 0, "defenders" to 1, "midfielders" to 2, "attackers" to 3)
    val raw = squads.flatMap { (tid, groups) ->
        groups.flatMap { g -> val pos = lines[g.s("title")] ?: return@flatMap emptyList(); g.a("members").objs().map { Triple(tid, pos, it) } }
    }
    // price from market value, scaled to this league: the most valuable player costs about 13.5M, the cheapest 4M
    val maxV = raw.maxOfOrNull { it.third.l("transferValue") ?: 0L }?.coerceAtLeast(1L) ?: 1L
    val players = cached?.first ?: raw.associate { (tid, pos, m) ->
        val v = (m.l("transferValue") ?: 0L).toDouble()
        val price = ((4.0 + 9.5 * sqrt(v / maxV) + if (pos == 3) 0.5 else 0.0) * 2).roundToInt() / 2.0
        // FotMob marks an injured player with an "injury" object
        (m.i("id") ?: 0) to FPlayer(m.i("id") ?: 0, m.s("name") ?: "", tid, pos, price.coerceIn(4.0, 14.0), m.d("rating"), m.i("goals") ?: 0, m.i("assists") ?: 0,
            m.opt("injury") is JSONObject)
    }.also { if (cached == null && it.size >= 50) writePlayers(id, it, teamNames) }
    val all = d.o("fixtures").a("allMatches").objs()
    val rounds = all.groupBy { it.s("round") ?: it.s("roundName") ?: "" }.map { (r, ms) -> r to ms.map { parseMatch(it) }.sortedBy { it.time } }
    val firstIdx = d.o("fixtures").o("firstUnplayedMatch").i("firstUnplayedMatchIndex")
    val firstRound = firstIdx?.let { all.getOrNull(it) }?.let { it.s("round") ?: it.s("roundName") }
    val current = rounds.indexOfFirst { it.first == firstRound }.let { if (it < 0) rounds.size - 1 else it }
    FLeague(id, players, teamNames, rounds, current, rows.mapIndexed { k, r -> (r.i("id") ?: 0) to (r.i("idx") ?: (k + 1)) }.toMap()).also { leagueCache[id] = it }
}

/** The saved game: current squad, captain, and the squad frozen at each gameweek's deadline. */
private class FState(val o: JSONObject) {
    val league get() = o.optInt("league")
    val team: MutableList<Int> get() = o.optJSONArray("team").let { a -> MutableList(a?.length() ?: 0) { a!!.getInt(it) } }
    val captain get() = o.optInt("captain")
    /** The four on the bench, in the order they come on. */
    val bench: MutableList<Int> get() = o.optJSONArray("bench").let { a -> MutableList(a?.length() ?: 0) { a!!.getInt(it) } }.filter { it in team }.toMutableList()
    fun frozen(round: String): Frozen? = o.optJSONObject("rounds")?.optJSONObject(round)?.let { r ->
        val b = r.optJSONArray("bench")
        Frozen(r.getJSONArray("team").let { a -> List(a.length()) { a.getInt(it) } }, r.optInt("captain"), List(b?.length() ?: 0) { b!!.getInt(it) })
    }
    companion object {
        fun load() = FState(runCatching { JSONObject(Store.prefs().getString("fantasy", "{}")!!) }.getOrDefault(JSONObject()))
    }
    fun save() { Store.prefs().edit().putString("fantasy", o.toString()).apply() }
    fun setTeam(t: List<Int>, c: Int, b: List<Int> = bench) { o.put("team", JSONArray(t)).put("captain", c).put("bench", JSONArray(b.filter { it in t })); save() }
    fun freeze(round: String) {
        val r = o.optJSONObject("rounds") ?: JSONObject().also { o.put("rounds", it) }
        if (!r.has(round)) { r.put(round, JSONObject().put("team", JSONArray(team)).put("captain", captain).put("bench", JSONArray(bench))); save() }
    }
    fun recordPoints(round: String, pts: Int) { val p = o.optJSONObject("points") ?: JSONObject().also { o.put("points", it) }; p.put(round, pts); save() }
    fun points(): Map<String, Int> = o.optJSONObject("points")?.let { p -> p.keys().asSequence().associateWith { p.optInt(it) } } ?: emptyMap()
}

class Frozen(val team: List<Int>, val captain: Int, val bench: List<Int>)

/** A new player: onto the pitch while the eleven can still take his line (and still reach every line's minimum), else to the bench. */
private fun addTo(st: FState, p: FPlayer, lg: FLeague) {
    val team = st.team.apply { add(p.id) }
    val bench = st.bench
    val xi = team.filter { it != p.id && it !in bench }.mapNotNull { lg.players[it] }
    val after = xi + p
    val count = (0..3).map { pos -> after.count { it.pos == pos } }
    val missing = (0..3).sumOf { pos -> (POS_RANGE[pos].first - count[pos]).coerceAtLeast(0) }
    // a full bench means the pitch takes him whatever the shape (the swaps put it right)
    val fits = xi.size < 11 && (bench.size >= 4 || count[p.pos] <= POS_RANGE[p.pos].last && 11 - after.size >= missing)
    if (!fits) { if (p.pos == 0) bench.add(0, p.id) else bench.add(p.id) }
    st.setTeam(team, if (st.captain == 0 && fits) p.id else st.captain, bench)
}

/** The gameweek's score with automatic substitutions: who counted, who came on, who went off. */
private class Week(val total: Int, val counted: Set<Int>, val cameOn: Set<Int>, val wentOff: Set<Int>)

private fun weekScore(squad: List<FPlayer>, bench: List<Int>, captain: Int, pts: Map<Int, Pair<Int, List<String>>>, done: (FPlayer) -> Boolean): Week {
    val played = { p: FPlayer -> pts[p.id]?.second?.isNotEmpty() == true }
    var xi = squad.filter { it.id !in bench }
    val benchP = bench.mapNotNull { id -> squad.firstOrNull { it.id == id } }.toMutableList()
    val on = HashSet<Int>(); val off = HashSet<Int>()
    for (s in xi.toList()) {
        if (played(s) || !done(s)) continue
        // the first on the bench who played and keeps the shape legal (a keeper only for the keeper)
        val sub = benchP.firstOrNull { b -> b.id !in on && played(b) && (b.pos == 0) == (s.pos == 0) && legalXi(xi - s + b) } ?: continue
        xi = xi - s + sub; on += sub.id; off += s.id
    }
    val total = xi.sumOf { p -> (pts[p.id]?.first ?: 0) * if (p.id == captain) 2 else 1 }
    return Week(total, xi.map { it.id }.toSet(), on, off)
}

/** A finished round's points kept on the phone, and sent for the fantasy leagues when they are new or changed. */
private fun record(st: FState, comp: Int, round: String, pts: Int) {
    if (st.points()[round] == pts && st.o.optJSONObject("sent")?.optInt(round, Int.MIN_VALUE) == pts) return
    st.recordPoints(round, pts)
    if (app.goallive.live.data.Account.signedIn) {
        app.goallive.live.data.Account.fantasyPoints(comp, round, pts)
        (st.o.optJSONObject("sent") ?: JSONObject().also { st.o.put("sent", it) }).put(round, pts); st.save()
    }
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
    val r = runCatching { rateMatch(MatchData(m))[p.id]?.value }.getOrNull()
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
    FantasyTheme(state.league) {
        LoadBox(l, 6) { lg -> FantasyBody(lg, state, { tick++ }) {
            state.o.remove("league"); state.o.remove("team"); state.o.remove("captain"); state.o.remove("bench"); state.save(); state = FState.load()
        } }
    }
}

@Composable
private fun LeaguePick(onPick: (Int) -> Unit) {
    val accent = LocalAccent.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = BottomSpace)) {
        item {
            Column(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(64.dp).glow(accent.copy(alpha = 0.4f), 10.dp, RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp)).background(accent),
                    contentAlignment = Alignment.Center) { androidx.compose.material3.Icon(Ic.shirt, null, Modifier.size(34.dp), tint = LocalAccentInk.current) }
                T(L("فوتبال فانتزی"), 22, FontWeight.Black)
                T(L("با ۱۴۰ میلیون بودجه ۱۵ بازیکن واقعی بخر: ۱۱ نفر در زمین و ۴ نفر روی نیمکت. کاپیتان انتخاب کن و از روی عملکرد واقعی‌شان در هر هفته امتیاز بگیر."), 13, color = C.mute, align = TextAlign.Center)
            }
        }
        item { SectionTitle(L("لیگ خودت را انتخاب کن")) }
        item {
            Card {
                F_LEAGUES.forEachIndexed { i, (id, name) ->
                    ListRow(first = i == 0, onClick = { onPick(id) }) {
                        Box(Modifier.size(6.dp, 30.dp).clip(RoundedCornerShape(3.dp)).background(fantasyColor(id)))
                        LeagueLogo(id, 30.dp); T(name, 15, FontWeight.Bold, modifier = Modifier.weight(1f)); androidx.compose.material3.Icon(Ic.chev, null, Modifier.size(18.dp), tint = fantasyColor(id))
                    }
                }
            }
        }
        item {
            Card(Modifier.padding(top = 12.dp)) {
                CardTitle(L("قانون امتیازها"))
                T(L("بازی کمتر از ۶۰ دقیقه ۱ و بیشتر ۲ امتیاز · گل: دروازه‌بان و مدافع ۶، هافبک ۵، مهاجم ۴ · پاس گل ۳ · کلین‌شیت: دروازه‌بان و مدافع ۴، هافبک ۱ · هر ۲ گل خورده (دروازه‌بان و مدافع) ۱- · کارت زرد ۱- · قرمز ۳- · گل به خودی ۲- · پاداش نمره: ۷+ یک، ۷٫۵+ دو، ۸٫۵+ سه · امتیاز کاپیتان دو برابر · اگر بازیکن زمین بازی نکند، اولین بازیکن نیمکت که بازی کرده جایش حساب می‌شود"),
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
    val team = if (locked && frozen != null) frozen.team else st.team
    val captain = if (locked && frozen != null) frozen.captain else st.captain
    val bench = if (locked && frozen != null) frozen.bench else st.bench
    val picks = team.mapNotNull { lg.players[it] }
    val xiPicks = picks.filter { it.id !in bench }
    val benchPicks = bench.mapNotNull { lg.players[it] }
    val spent = picks.sumOf { it.price }
    var pickPos by remember { mutableStateOf<Int?>(null) }
    var open by remember { mutableStateOf<FPlayer?>(null) }
    // a player picked to change places with one on the other side of the bench line
    var swapFrom by remember { mutableStateOf<FPlayer?>(null) }
    var swapError by remember { mutableStateOf<String?>(null) }
    // signed in: the squad goes to the server whenever it changes before the deadline (the fantasy leagues read it there)
    val first = roundMatches.minByOrNull { it.time }?.id ?: 0
    LaunchedEffect(lg.id, roundName, team.joinToString(","), bench.joinToString(","), captain, app.goallive.live.data.Account.signedIn) {
        if (!locked && team.isNotEmpty()) app.goallive.live.data.Account.fantasyTeam(lg.id, roundName, first, team, bench, captain)
    }

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
    // a player's matches this round are over (or he had none): only then can the bench take his place
    val doneFor = { p: FPlayer -> roundMatches.filter { it.home.id == p.teamId || it.away.id == p.teamId }.all { it.finished || it.cancelled } }
    val week = livePts?.let { weekScore(picks, bench, captain, it, doneFor) }
    val roundTotal = week?.total ?: 0
    if (locked && livePts != null && relevant.isNotEmpty() && relevant.all { it.finished }) record(st, lg.id, roundName, roundTotal)
    // rounds that ended while the app wasn't open get their points now
    LaunchedEffect(lg.id, roundName) {
        for ((r, ms) in lg.rounds.take(lg.current)) {
            if (r in st.points()) continue
            val fr = st.frozen(r) ?: continue
            val ps = fr.team.mapNotNull { lg.players[it] }
            val clubs2 = ps.map { it.teamId }.toSet()
            val played = ms.filter { (it.home.id in clubs2 || it.away.id in clubs2) && it.finished }
            if (played.isEmpty()) { record(st, lg.id, r, 0); continue }
            val det = coroutineScope { played.map { m -> async { runCatching { Api.fm("matchDetails", mapOf("matchId" to m.id), 6 * 3_600_000) }.getOrNull() } }.awaitAll().filterNotNull() }
            if (det.size < played.size) continue
            val each = ps.associate { p ->
                var total = 0; val why = ArrayList<String>()
                det.filter { d -> d.path("header").a("teams").objs().any { it.i("id") == p.teamId } }.forEach { d -> val (x, w) = score(p, d); total += x; why += w }
                p.id to (total to why)
            }
            record(st, lg.id, r, weekScore(ps, fr.bench, fr.captain, each) { true }.total); changed()
        }
    }
    val history = st.points()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = BottomSpace), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(
                Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(22.dp)).drawBehind { cssLinear(135f, 0f to accent.copy(alpha = 0.4f), 1f to C.card) }
                    .border(1.dp, C.line2, RoundedCornerShape(22.dp)).padding(16.dp),
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
        item { FantasyLeaguesCard(lg) }
        swapFrom?.let { f -> item {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.gold.copy(alpha = 0.16f)).border(1.dp, C.gold.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                T(swapError ?: L("جابه‌جایی {0}: بازیکنی از {1} را انتخاب کن", personName(f.name.substringAfterLast(' ')), if (f.id in bench) L("زمین") else L("نیمکت")),
                    13, FontWeight.Bold, if (swapError != null) C.loss else C.ink, Modifier.weight(1f))
                T(L("لغو"), 13, FontWeight.Bold, C.mute, Modifier.clip(RoundedCornerShape(8.dp)).clickable { swapFrom = null; swapError = null }.padding(6.dp))
            }
        } }
        // a tap while changing places finishes the swap; otherwise it opens the player
        val tapPlayer = { p: FPlayer ->
            val f = swapFrom
            if (f == null || f.id == p.id) { swapFrom = null; open = p }
            else if ((f.id in bench) == (p.id in bench)) swapError = L("یکی از زمین و یکی از نیمکت")
            else {
                val out = if (f.id in bench) p else f; val inn = if (f.id in bench) f else p
                val newXi = xiPicks - out + inn
                if (xiPicks.size == 11 && !legalXi(newXi)) swapError = L("این جابه‌جایی ترکیب مجاز نمی‌سازد")
                else {
                    val b = bench.toMutableList().apply { set(indexOf(inn.id), out.id) }
                    st.setTeam(st.team, if (captain == out.id) inn.id else st.captain, b); swapFrom = null; swapError = null; changed()
                }
            }
        }
        item { FPitch(xiPicks, captain, locked, livePts?.mapValues { it.value.first }, lg, big = xiPicks.size == 11, faded = week?.let { picks.map { it.id }.toSet() - it.counted } ?: emptySet(),
            marked = setOfNotNull(swapFrom?.id)) { tapPlayer(it) } }
        if (benchPicks.isNotEmpty() || picks.size >= 11) item { BenchRow(benchPicks, locked, livePts?.mapValues { it.value.first }, week?.cameOn ?: emptySet(), setOfNotNull(swapFrom?.id)) { tapPlayer(it) } }
        if (!locked && picks.isEmpty()) item {
            // one tap to a sensible team within budget, to edit from there
            Pill(L("✨ چیدن خودکار تیم"), PillKind.Accent, onClick = {
                val (auto, b) = autoTeam(lg)
                if (auto.isNotEmpty()) { st.setTeam(auto.map { it.id }, auto.filter { it.id !in b }.maxByOrNull { it.rating ?: 0.0 }?.id ?: auto.first().id, b); changed() }
            })
        }
        if (picks.size >= 11) item { TeamFacts(picks, lg) }
        if (picks.isNotEmpty()) item { Fixtures(picks, lg) }
        if (!locked) item {
            val counts = (0..3).map { pos -> picks.count { it.pos == pos } }
            val perClub = picks.groupingBy { it.teamId }.eachCount()
            var editOpen by remember { mutableStateOf(false) }
            Card {
                CardTitle(L("چیدن تیم"), sub = L("{0}/۱۵ بازیکن", fa(picks.size))) {
                    if (picks.size == SQUAD_SIZE) Pill(if (editOpen) L("بستن") else L("تغییر تیم"), onClick = { editOpen = !editOpen })
                }
                val problems = buildList {
                    if (spent > BUDGET) add(L("از بودجه بیشتر خرج کرده‌ای"))
                    perClub.filter { it.value > 3 }.keys.forEach { add(L("بیشتر از ۳ بازیکن از {0}", teamName(it, lg.teamNames[it] ?: ""))) }
                    if (picks.size == SQUAD_SIZE && !legalXi(xiPicks)) add(L("ترکیب ۱۱ نفر زمین مجاز نیست؛ با نیمکت جابه‌جا کن"))
                    if (picks.size == SQUAD_SIZE && (captain !in team || captain in bench)) add(L("کاپیتان باید در زمین باشد؛ روی یک بازیکن زمین بزن"))
                }
                if (picks.size < SQUAD_SIZE || editOpen) {
                Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (0..3).forEach { pos ->
                        val ok = counts[pos] == SQUAD[pos]
                        val full = counts[pos] >= SQUAD[pos] || picks.size >= SQUAD_SIZE
                        Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(if (ok) C.win.copy(alpha = 0.12f) else C.card2)
                            .clickable(enabled = !full) { pickPos = pos }.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            T(POS[pos], 11, FontWeight.Bold, C.mute)
                            T("${counts[pos]}", 18, FontWeight.Black, if (ok) C.win else C.ink)
                            T(L("از {0}", fa(SQUAD[pos])), 10, color = C.faint)
                            if (!full) T(L("+ افزودن"), 11, FontWeight.ExtraBold, accent)
                        }
                    }
                }
                if (picks.size == SQUAD_SIZE && problems.isEmpty()) T(L("✓ تیمت آماده است و تا مهلت هر تغییری بدهی ذخیره می‌شود."), 12, FontWeight.Bold, C.win, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp))
                problems.forEach { T("• $it", 12, FontWeight.Bold, C.loss, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 6.dp)) }
                } else if (problems.isEmpty()) T(L("✓ تیمت کامل است؛ روی هر بازیکن بزن تا کاپیتانش کنی یا بفروشی‌اش."), 12, FontWeight.Bold, C.win,
                    Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp))
                else problems.forEach { T("• $it", 12, FontWeight.Bold, C.loss, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 6.dp)) }
            }
        }
        if (locked && livePts != null) item {
            Card {
                CardTitle(L("امتیاز بازیکن‌ها در این هفته"))
                val counted = week?.counted ?: xiPicks.map { it.id }.toSet()
                picks.sortedWith(compareByDescending<FPlayer> { it.id in counted }.thenByDescending { livePts[it.id]?.first ?: 0 }).forEachIndexed { i, p ->
                    val on = p.id in counted
                    val v = (livePts[p.id]?.first ?: 0) * if (p.id == captain && on) 2 else 1
                    ListRow(first = i == 0, onClick = { open = p }) {
                        Face(p.id, 34.dp)
                        Column(Modifier.weight(1f)) {
                            Ltr(p.name + if (p.id == captain) " (C)" else "", color = if (on) C.ink else C.faint)
                            T(listOfNotNull(POS[p.pos], teamName(p.teamId, lg.teamNames[p.teamId] ?: ""),
                                when { p.id in (week?.cameOn ?: emptySet()) -> L("از نیمکت آمد"); p.id in (week?.wentOff ?: emptySet()) -> L("بازی نکرد؛ تعویض شد"); !on -> L("نیمکت"); else -> null }
                            ).joinToString(" · "), 11, color = C.mute)
                        }
                        T("$v", 17, FontWeight.Black, if (v > 0 && on) accent else C.mute)
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
        item { T(L("قیمت‌ها از ارزش بازار بازیکن‌ها ساخته شده و امتیازها از آمار واقعی بازی‌ها. با ورود به حساب، تیم و امتیازت برای لیگ‌های فانتزی ثبت می‌شود."), 11, color = C.faint, align = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
    }

    pickPos?.let { pos -> Picker(lg, pos, picks, spent, roundMatches, { pickPos = null }) { p -> addTo(st, p, lg); changed(); pickPos = null } }
    open?.let { p -> PlayerActions(p, lg, p.id == captain, !locked, livePts?.get(p.id), { open = null },
        onCaptain = { st.setTeam(st.team, p.id); changed(); open = null },
        onRemove = {
            val t = st.team.apply { remove(p.id) }; val b = st.bench.apply { remove(p.id) }
            st.setTeam(t, if (st.captain == p.id) t.firstOrNull { it !in b } ?: 0 else st.captain, b); changed(); open = null
        },
        onBench = if (!locked && picks.size >= 12) ({ open = null; swapFrom = p; swapError = null }) else null,
        onBenchUp = if (!locked && p.id in bench && bench.indexOf(p.id) > 1) ({
            val b = st.bench; val k = b.indexOf(p.id); b[k] = b[k - 1]; b[k - 1] = p.id; st.setTeam(st.team, st.captain, b); changed(); open = null
        }) else null,
        onBenchLabel = if (p.id in bench) L("به زمین") else L("به نیمکت"),
        canCaptain = p.id !in bench) }
}

@Composable
private fun Stat(big: String, small: String, modifier: Modifier, color: Color) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(alpha = 0.25f)).padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        T(big, 22, FontWeight.Black, color); T(small, 11, color = C.mute, maxLines = 1)
    }
}

/** The eleven by line: forwards at the top, goalkeeper at the bottom. Shows price before the deadline and points after. */
@Composable
private fun FPitch(picks: List<FPlayer>, captain: Int, locked: Boolean, pts: Map<Int, Int>?, lg: FLeague, big: Boolean = false,
                   faded: Set<Int> = emptySet(), marked: Set<Int> = emptySet(), onOpen: (FPlayer) -> Unit) {
    val disc = if (big) 54.dp else 42.dp
    BoxWithConstraints(
        Modifier.fillMaxWidth().aspectRatio(if (big) 68f / 104f else 68f / 84f).clip(RoundedCornerShape(18.dp)).drawBehind {
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
                Column(Modifier.align(AbsoluteAlignment.TopLeft).absoluteOffset(w * x - 40.dp, h * y - disc * 0.75f).width(80.dp)
                    .alpha(if (p.id in faded) 0.45f else 1f).clickable { onOpen(p) },
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Box {
                        val col = Clubs.get(p.teamId)?.let { Color(it.c1) } ?: hueColor(p.teamId)
                        Box(Modifier.size(disc).clip(CircleShape).background(col).border(if (p.id in marked) 3.dp else 2.dp, if (p.id in marked) C.gold else Color.White, CircleShape), contentAlignment = Alignment.Center) {
                            Crest(p.teamId, disc * 0.62f)
                            coil.compose.AsyncImage(playerFace(p.id), null, Modifier.fillMaxSize().clip(CircleShape), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                        }
                        if (p.id == captain) Box(Modifier.align(Alignment.TopStart).absoluteOffset((-6).dp, (-4).dp).size(17.dp).clip(CircleShape).background(C.gold),
                            contentAlignment = Alignment.Center) { Text("C", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color(0xFF2A1A00)) }
                        if (p.injured) Box(Modifier.align(Alignment.TopEnd).absoluteOffset(6.dp, (-4).dp).size(16.dp).clip(CircleShape).background(C.live), contentAlignment = Alignment.Center) {
                            Text("!", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color.White)
                        }
                    }
                    Ltr(p.name.substringAfterLast(' '), Modifier.padding(top = 3.dp).clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)).background(Color.White).padding(horizontal = 5.dp),
                        style = lat(if (big) 13.sp else 11.sp), color = Color(0xFF111111), align = TextAlign.Center)
                    val label = if (locked) "${(pts?.get(p.id) ?: 0) * if (p.id == captain) 2 else 1}" else dec(p.price, 1)
                    Box(Modifier.clip(RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp)).background(Color(0xFF14321F)).padding(horizontal = 8.dp)) {
                        T(label, 11, FontWeight.Black, Color.White)
                    }
                }
            }
        }
    }
}

/** The four on the bench, in the order they come on (the keeper first), with points once the week has begun. */
@Composable
private fun BenchRow(bench: List<FPlayer>, locked: Boolean, pts: Map<Int, Int>?, cameOn: Set<Int>, marked: Set<Int>, onOpen: (FPlayer) -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.card).border(1.dp, C.line, RoundedCornerShape(18.dp)).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            T(L("نیمکت"), 14, FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            T(L("اگر بازیکن زمین بازی نکند، به این ترتیب جایش می‌آیند"), 11, color = C.mute, maxLines = 1)
        }
        if (bench.isEmpty()) T(L("۴ بازیکن نیمکت را از «چیدن تیم» بخر"), 12, color = C.mute, modifier = Modifier.padding(top = 8.dp))
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            bench.forEachIndexed { k, p ->
                val on = p.id in cameOn
                Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(if (on) C.win.copy(alpha = 0.16f) else C.card2)
                    .border(if (p.id in marked) 2.dp else 0.dp, if (p.id in marked) C.gold else Color.Transparent, RoundedCornerShape(14.dp))
                    .clickable { onOpen(p) }.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    T(if (p.pos == 0) L("دروازه‌بان") else fa(k), 10, FontWeight.Bold, C.mute)
                    Face(p.id, 40.dp)
                    Ltr(p.name.substringAfterLast(' '), style = lat(11.sp), align = TextAlign.Center)
                    T(if (locked) fa((pts?.get(p.id) ?: 0)) else dec(p.price, 1), 12, FontWeight.Black, if (on) C.win else C.ink)
                    if (on) T(L("وارد شد"), 10, FontWeight.Bold, C.win)
                }
            }
            repeat((4 - bench.size).coerceAtLeast(0)) { Box(Modifier.weight(1f)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Picker(lg: FLeague, pos: Int, picks: List<FPlayer>, spent: Double, round: List<MatchItem>, onClose: () -> Unit, onPick: (FPlayer) -> Unit) {
    var q by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf("price") }
    val perClub = picks.groupingBy { it.teamId }.eachCount()
    val opponent = { tid: Int -> round.firstOrNull { it.home.id == tid || it.away.id == tid }?.let { m -> if (m.home.id == tid) teamName(m.away.id, lg.teamNames[m.away.id] ?: "") + L(" (خانه)") else teamName(m.home.id, lg.teamNames[m.home.id] ?: "") + L(" (بیرون)") } }
    val list = lg.players.values.filter { it.pos == pos && picks.none { x -> x.id == it.id } }
        .filter { q.isBlank() || it.name.contains(q.trim(), true) || (lg.teamNames[it.teamId] ?: "").contains(q.trim(), true) || teamName(it.teamId, lg.teamNames[it.teamId] ?: "").contains(q.trim())
            || personName(it.name).contains(q.trim()) }
        .sortedByDescending { when (sort) { "rating" -> it.rating ?: 0.0; "goals" -> (it.goals * 3 + it.assists).toDouble(); else -> it.price } }
        .take(80)
    GlSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = C.cardSolid,
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
                                T(listOfNotNull(teamName(p.teamId, lg.teamNames[p.teamId] ?: ""), L("{0} گل", p.goals), L("{0} پاس", p.assists), opponent(p.teamId)?.let { L("حریف: {0}", it) }).joinToString(" · "), 11, color = C.mute, maxLines = 1)
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

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun PlayerActions(p: FPlayer, lg: FLeague, isCaptain: Boolean, editable: Boolean, pts: Pair<Int, List<String>>?, onClose: () -> Unit, onCaptain: () -> Unit, onRemove: () -> Unit,
                          onBench: (() -> Unit)? = null, onBenchUp: (() -> Unit)? = null, onBenchLabel: String = "", canCaptain: Boolean = true) {
    val nav = LocalNav.current
    GlSheet(onDismissRequest = onClose, containerColor = C.cardSolid, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Face(p.id, 56.dp)
                Column(Modifier.weight(1f)) {
                    Ltr(p.name, style = lat(19.sp, FontWeight.ExtraBold))
                    T(L("{0} · {1} · قیمت {2}م", POS[p.pos], Clubs.get(p.teamId)?.fa ?: teamName(p.teamId, lg.teamNames[p.teamId] ?: ""), dec(p.price, 1)), 12, color = C.mute)
                }
                Rating(p.rating)
            }
            if (pts != null) {
                SubHead(L("امتیاز این هفته: {0}", pts.first * if (isCaptain) 2 else 1) + if (isCaptain) L(" (کاپیتان ×۲)") else "")
                if (pts.second.isEmpty()) T(L("هنوز بازی نکرده"), 13, color = C.mute, modifier = Modifier.padding(horizontal = 16.dp))
                pts.second.forEach { T("• $it", 13, modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) }
            }
            androidx.compose.foundation.layout.FlowRow(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (editable && !isCaptain && canCaptain) Pill(L("کاپیتان کن"), PillKind.Accent, onClick = onCaptain)
                onBench?.let { Pill(onBenchLabel, onClick = it) }
                onBenchUp?.let { Pill(L("اولویت بالاتر در نیمکت"), onClick = it) }
                if (editable) Pill(L("فروش"), PillKind.Live, onClick = onRemove)
                Pill(L("پروفایل"), onClick = { onClose(); nav.player(p.id) })
            }
        }
    }
}


/**
 * A squad within budget: a 1-4-4-2 eleven by season rating and goal involvement, then the cheapest sensible bench (a keeper,
 * a defender, a midfielder, a forward), at most three per club, no injured players. Returns the squad and the bench.
 */
private fun autoTeam(lg: FLeague): Pair<List<FPlayer>, List<Int>> {
    val out = ArrayList<FPlayer>()
    var left = BUDGET
    fun buy(pos: Int, cheap: Boolean): FPlayer? {
        val slotsAfter = SQUAD_SIZE - out.size - 1
        val cap = left - 4.5 * slotsAfter
        val perClub = out.groupingBy { it.teamId }.eachCount()
        val ok = lg.players.values.filter { it.pos == pos && !it.injured && it !in out && it.price <= cap && (perClub[it.teamId] ?: 0) < 3 }
        val best = (if (cheap) ok.filter { it.price <= 5.0 }.ifEmpty { ok }.maxByOrNull { (it.rating ?: 6.0) - it.price * 0.1 }
            else ok.maxByOrNull { (it.rating ?: 6.4) + it.goals * 0.12 + it.assists * 0.08 + it.price * 0.05 }) ?: return null
        out += best; left -= best.price; return best
    }
    // the eleven first, forwards and midfielders before the rest: they score the most
    for ((pos, n) in listOf(3 to 2, 2 to 4, 1 to 4, 0 to 1)) repeat(n) { buy(pos, false) ?: return emptyList<FPlayer>() to emptyList() }
    val bench = listOf(0, 1, 2, 3).map { pos -> buy(pos, true)?.id ?: return emptyList<FPlayer>() to emptyList() }
    return out to bench
}

/** Team value, average rating and the club spread. */
@Composable
private fun TeamFacts(picks: List<FPlayer>, lg: FLeague) {
    val rated = picks.mapNotNull { it.rating }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            L("{0}م", dec(picks.sumOf { it.price }, 1)) to L("ارزش تیم"),
            (if (rated.isEmpty()) "–" else dec(rated.average(), 2)) to L("میانگین نمره"),
            fa(picks.sumOf { it.goals }) to L("گل فصل"),
            fa(picks.map { it.teamId }.toSet().size) to L("باشگاه"),
        ).forEach { (v, k) ->
            Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(C.card).border(1.dp, C.line, RoundedCornerShape(14.dp)).padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                T(fa(v), 17, FontWeight.Black); T(k, 11, color = C.mute, maxLines = 1)
            }
        }
    }
}

/** The next three matches of every club in the team, coloured by how high the opponent is in the table. */
@Composable
private fun Fixtures(picks: List<FPlayer>, lg: FLeague) {
    val nav = LocalNav.current
    val upcoming = lg.rounds.drop(lg.current).flatMap { it.second }.filter { !it.finished && !it.cancelled }
    val clubs = picks.groupBy { it.teamId }
    val n = lg.ranks.size.coerceAtLeast(18)
    Card {
        CardTitle(L("بازی‌های پیش روی بازیکنانت"), sub = L("رنگ: سختی حریف"))
        clubs.entries.sortedByDescending { it.value.size }.forEachIndexed { i, (tid, ps) ->
            val next = upcoming.filter { it.home.id == tid || it.away.id == tid }.take(3)
            ListRow(first = i == 0) {
                Crest(tid, 22.dp)
                Column(Modifier.weight(1f)) {
                    T(teamName(tid, lg.teamNames[tid] ?: ""), 13, FontWeight.ExtraBold, maxLines = 1)
                    T(ps.joinToString(L("، ")) { personName(it.name.substringAfterLast(' ')) }, 11, color = C.mute, maxLines = 1)
                }
                next.forEach { m ->
                    val home = m.home.id == tid
                    val opp = if (home) m.away.id else m.home.id
                    val r = lg.ranks[opp] ?: (n / 2)
                    val c = when { r <= n / 4 -> C.loss; r <= n / 2 -> C.gold; else -> C.win }
                    Column(Modifier.width(48.dp).clip(RoundedCornerShape(10.dp)).background(c.copy(alpha = 0.16f)).border(1.dp, c.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        .clickable { nav.match(m.id) }.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Crest(opp, 18.dp)
                        T(if (home) L("خانه") else L("بیرون"), 10, FontWeight.Bold, c)
                    }
                }
                if (next.isEmpty()) T("–", 13, color = C.mute)
            }
        }
        T(L("قرمز: حریف بالای جدول · زرد: میانه · سبز: پایین جدول"), 11, color = C.faint, modifier = Modifier.padding(16.dp))
    }
}
