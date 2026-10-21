package app.goallive.live.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Sports
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.goallive.live.data.Api
import app.goallive.live.data.Clubs
import app.goallive.live.data.Store
import app.goallive.live.data.a
import app.goallive.live.data.arrs
import app.goallive.live.data.b
import app.goallive.live.data.cleanMinute
import app.goallive.live.data.d
import app.goallive.live.data.i
import app.goallive.live.data.o
import app.goallive.live.data.objs
import app.goallive.live.data.parseMatch
import app.goallive.live.data.parseTime
import app.goallive.live.data.path
import app.goallive.live.data.s
import app.goallive.live.data.strs
import app.goallive.live.data.tr
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.max

/** Everything the match screen needs from matchDetails, read once per refresh. */
class MatchData(val d: JSONObject) {
    val header = d.o("header")
    val teams = header.a("teams").objs()
    val home = teams.getOrNull(0)
    val away = teams.getOrNull(1)
    val status = header.o("status")
    val general = d.o("general")
    val content = d.o("content")
    val facts = content.o("matchFacts")
    val started = status.b("started")
    val finished = status.b("finished")
    val cancelled = status.b("cancelled")
    val live = started && !finished && !cancelled
    val time = parseTime(status.s("utcTime"))
    val minute = cleanMinute(status.o("liveTime").s("short"))
    val leagueId = general.i("parentLeagueId") ?: general.i("leagueId") ?: 0
    val events = facts.path("events").a("events").objs()
    val hasStats = content.path("stats.Periods")?.length()?.let { it > 0 } == true
    val hasLineup = (content.path("lineup.homeTeam").a("starters")?.length() ?: 0) > 0
    val hasTable = content.o("table").i("leagueId") != null
    val hasH2h = (content.o("h2h").a("matches")?.length() ?: 0) > 0
    val sig = (header?.toString() ?: "") + events.size + (content.o("stats")?.toString()?.length ?: 0)
}

@Composable
fun MatchScreen(id: Int) {
    val nav = LocalNav.current
    val url = Api.fmUrl("matchDetails", mapOf("matchId" to id))
    val l = rememberLoader(
        id,
        peek = { (Api.peek(url) as? JSONObject)?.let(::MatchData) },
        poll = { m -> if (m.live) 20_000L else if (!m.finished && m.time - System.currentTimeMillis() < 2 * 3_600_000) 60_000L else null },
    ) { MatchData(Api.fm("matchDetails", mapOf("matchId" to id), 15_000)) }

    Column(Modifier.fillMaxSize()) {
        TopBar("", { nav.back() }) {
            StarButton(id in Store.matches, { Store.toggleMatch(id) })
        }
        LoadBox(l, 6) { m -> MatchBody(m) }
    }
}

@Composable
private fun MatchBody(m: MatchData) {
    var tab by rememberSaveable { mutableStateOf("summary") }
    val tabs = buildList {
        add("summary" to "خلاصه")
        if (m.started || m.finished) add("video" to "ویدیو")
        if (m.hasStats) add("stats" to "آمار")
        if (m.hasLineup) add("lineup" to "ترکیب")
        if (m.hasTable) add("table" to "جدول")
        if (m.hasH2h) add("h2h" to "رودررو")
    }
    if (tabs.none { it.first == tab }) tab = "summary"
    val homeColor = parseColor(m.general.path("teamColors.darkMode").s("home")) ?: Clubs.get(m.home.i("id"))?.let { Color(it.c1) } ?: C.home
    val awayColor = parseColor(m.general.path("teamColors.darkMode").s("away")) ?: Clubs.get(m.away.i("id"))?.let { Color(it.c1) } ?: C.away

    LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
        item { Hero(m, homeColor, awayColor) }
        item {
            val idx = tabs.indexOfFirst { it.first == tab }.coerceAtLeast(0)
            ScrollableTabRow(
                selectedTabIndex = idx, containerColor = C.bg, contentColor = C.text, edgePadding = 12.dp, divider = {},
                indicator = { pos -> TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(pos[idx]), color = LocalAccent.current, height = 3.dp) },
            ) {
                tabs.forEach { (k, label) ->
                    Tab(selected = k == tab, onClick = { tab = k }, text = {
                        Text(label, fontWeight = if (k == tab) FontWeight.Bold else FontWeight.Medium, color = if (k == tab) C.text else C.mute)
                    })
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (tab) {
                    "summary" -> Summary(m, homeColor, awayColor)
                    "video" -> Highlights(m)
                    "stats" -> StatsTab(m, homeColor, awayColor)
                    "lineup" -> LineupTab(m)
                    "table" -> MatchTable(m)
                    "h2h" -> H2h(m, homeColor, awayColor, 50)
                }
            }
        }
    }
}

/* ---------- hero ---------- */

@Composable
private fun Hero(m: MatchData, homeColor: Color, awayColor: Color) {
    val nav = LocalNav.current
    val hId = m.home.i("id") ?: 0
    val aId = m.away.i("id") ?: 0
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp).clip(RoundedCornerShape(26.dp))
            // home color on the right (RTL), away on the left
            .background(Brush.horizontalGradient(listOf(awayColor.copy(alpha = 0.55f), C.card, homeColor.copy(alpha = 0.55f))))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(26.dp)),
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 16.dp, horizontal = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.clip(RoundedCornerShape(50)).clickable { nav.league(m.leagueId) }.padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                LeagueLogo(m.leagueId, 18.dp)
                Spacer(Modifier.width(6.dp))
                val round = m.general.s("leagueRoundName")
                Ltr(listOfNotNull(m.general.s("leagueName"), round?.let { if (it.all(Char::isDigit)) "Round $it" else it }).joinToString(" · "),
                    color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelMedium)
            }
            Clubs.derby(hId, aId)?.let {
                Text(it, color = C.gold, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 4.dp))
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                HeroTeam(hId, m.home.s("name") ?: "", Modifier.weight(1f))
                Column(Modifier.width(130.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (m.started || m.finished) {
                        ScoreText(m.home.i("score"), m.away.i("score"), 40, Color.White, FontWeight.Black)
                        if (m.status.o("reason").s("short") == "Pen") Text("پس از پنالتی", color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelSmall)
                    } else {
                        Text(timeOf(m.time), color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineLarge)
                        Text(shortDate(m.time), color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelMedium)
                    }
                    Spacer(Modifier.height(6.dp))
                    when {
                        m.live -> Row(Modifier.clip(RoundedCornerShape(50)).background(C.live.copy(alpha = 0.18f)).padding(horizontal = 10.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            LiveDot(size = 6.dp)
                            Ltr(if (m.minute == "HT") "بین دو نیمه" else m.minute, color = C.live, weight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                        }
                        m.finished -> Text(tr(m.status.o("reason").s("short") ?: "FT").let { if (it == "پایان") "پایان بازی" else it }, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium)
                        m.cancelled -> Text("لغو شد", color = C.live, style = MaterialTheme.typography.labelMedium)
                        else -> Text(countdown(m.time), color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
                    }
                }
                HeroTeam(aId, m.away.s("name") ?: "", Modifier.weight(1f))
            }
            // scorers under each team
            val goals = m.events.filter { it.s("type") == "Goal" }
            if (goals.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth()) {
                    Scorers(goals.filter { it.b("isHome") }, Modifier.weight(1f))
                    Icon(Icons.Rounded.SportsSoccer, null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(14.dp))
                    Scorers(goals.filter { !it.b("isHome") }, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun HeroTeam(id: Int, name: String, modifier: Modifier) {
    val nav = LocalNav.current
    Column(modifier.clip(RoundedCornerShape(16.dp)).clickable { nav.team(id) }.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Crest(id, 60.dp)
        Spacer(Modifier.height(6.dp))
        Text(Clubs.get(id)?.fa ?: name, color = Color.White, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Scorers(goals: List<JSONObject>, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        goals.groupBy { it.o("player").s("name") ?: it.s("nameStr") ?: "" }.forEach { (name, gs) ->
            val mins = gs.joinToString(", ") { minuteText(it) + "'" + if (it.b("ownGoal")) " OG" else if (isPen(it)) " P" else "" }
            Ltr("${name.substringAfterLast(' ')} $mins", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun minuteText(e: JSONObject): String {
    val t = (e.s("timeStr") ?: e.s("time") ?: "").replace(" ", "")
    val o = e.s("overloadTime")
    return if (o != null && o != "0" && !t.contains('+')) "$t+$o" else t
}

private fun isPen(e: JSONObject) = e.s("goalDescriptionKey") == "penalty" || (e.s("suffix") ?: "").contains("pen", true) || e.path("shotmapEvent").s("situation") == "Penalty"

/* ---------- summary ---------- */

@Composable
private fun Summary(m: MatchData, homeColor: Color, awayColor: Color) {
    val nav = LocalNav.current
    val momentum = m.content.path("momentum.main").a("data").objs()
    if (momentum.size > 5) Card {
        CardTitle("مومنتوم بازی")
        Momentum(momentum, homeColor, awayColor)
        Spacer(Modifier.height(12.dp))
    }
    WinProbability(m, homeColor, awayColor)
    val evs = if (m.live) m.events.reversed() else m.events
    if (evs.isNotEmpty()) Card {
        CardTitle("رویدادها")
        evs.forEach { EventRow(it) }
        Spacer(Modifier.height(8.dp))
    }
    m.facts.o("playerOfTheMatch")?.takeIf { (it.i("id") ?: 0) > 0 }?.let { p ->
        Card {
            Row(Modifier.fillMaxWidth().clickable { nav.player(p.i("id")!!) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Face(p.i("id")!!, 52.dp, C.gold)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Star, null, tint = C.gold, modifier = Modifier.size(14.dp))
                        Text(" بهترین بازیکن زمین", color = C.gold, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                    Ltr(p.o("name").s("fullName") ?: "", weight = FontWeight.Bold)
                    Text(p.s("teamName") ?: "", color = C.mute, style = MaterialTheme.typography.labelSmall)
                }
                Rating(p.o("rating").d("num") ?: p.d("rating"))
            }
        }
    }
    val form = m.facts.a("teamForm")
    if (form != null && form.length() == 2) Card {
        CardTitle("فرم ۵ بازی اخیر")
        listOf(m.home, m.away).forEachIndexed { i, t ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Crest(t.i("id") ?: 0, 22.dp); Spacer(Modifier.width(8.dp))
                Ltr(t.s("name") ?: "", Modifier.weight(1f))
                FormChips(form.optJSONArray(i))
            }
        }
        Spacer(Modifier.height(6.dp))
    }
    InfoCard(m)
    if (!m.started && m.hasH2h) H2h(m, homeColor, awayColor, 3)
}

@Composable
fun FormChips(arr: JSONArray?) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        arr.objs().take(5).forEach { f ->
            val r = f.s("resultString") ?: ""
            val c = when (r) { "W" -> C.win; "L" -> C.loss; else -> C.draw }
            Box(Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)).background(c), contentAlignment = Alignment.Center) {
                Text(when (r) { "W" -> "ب"; "L" -> "ش"; else -> "م" }, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun EventRow(e: JSONObject) {
    val nav = LocalNav.current
    when (e.s("type")) {
        "Half" -> {
            val label = when (e.s("halfStrShort")) { "HT" -> "پایان نیمه اول"; "FT" -> "پایان بازی"; else -> tr(e.s("halfStrShort")) }
            DividerLabel("$label   ${e.i("homeScore") ?: 0} - ${e.i("awayScore") ?: 0}")
            return
        }
        "AddedTime" -> { DividerLabel("+${e.s("minutesAddedInput") ?: ""} دقیقه وقت اضافه"); return }
    }
    val type = e.s("type")
    val pid = e.o("player").i("id") ?: 0
    val name = e.s("nameStr") ?: e.o("player").s("name") ?: ""
    val content: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when (type) {
                "Goal" -> Icon(Icons.Rounded.SportsSoccer, null, tint = if (e.b("ownGoal")) C.live else C.text, modifier = Modifier.size(18.dp))
                "Card" -> Box(Modifier.size(11.dp, 15.dp).clip(RoundedCornerShape(2.dp)).background(
                    when (e.s("card")) { "Red" -> C.red; "YellowRed" -> C.red; else -> C.yellow }))
                "Substitution" -> Icon(Icons.Rounded.SwapVert, null, tint = LocalAccent.current, modifier = Modifier.size(18.dp))
                else -> Icon(Icons.Rounded.Info, null, tint = C.mute, modifier = Modifier.size(16.dp))
            }
            Column(Modifier.clickable(enabled = pid > 0) { nav.player(pid) }) {
                when (type) {
                    "Goal" -> {
                        val sc = e.a("newScore")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (sc != null && sc.length() == 2) Text("${sc.optInt(0)}-${sc.optInt(1)}  ", fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelLarge)
                            Ltr(name, weight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                        }
                        val sub = when {
                            e.b("ownGoal") -> "گل به خودی"
                            isPen(e) -> "پنالتی"
                            e.s("assistInput") != null -> "پاس گل: ${e.s("assistInput")}"
                            else -> "گل"
                        }
                        Ltr(sub, color = C.mute, style = MaterialTheme.typography.labelSmall)
                    }
                    "Card" -> {
                        Ltr(name, weight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                        Text(when (e.s("card")) { "Red" -> "کارت قرمز"; "YellowRed" -> "کارت زرد دوم"; else -> "کارت زرد" }, color = C.mute, style = MaterialTheme.typography.labelSmall)
                    }
                    "Substitution" -> {
                        val sw = e.a("swap").objs()
                        Ltr(sw.getOrNull(0).s("name") ?: "", color = LocalAccent.current, weight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                        Ltr((sw.getOrNull(1).s("name") ?: "") + if (e.b("injuredPlayerOut")) " · injured" else "", color = C.mute, style = MaterialTheme.typography.labelSmall)
                    }
                    "VAR" -> { Text("VAR", fontWeight = FontWeight.Bold); Ltr(e.path("VAR.decision").s("value") ?: "", color = C.mute, style = MaterialTheme.typography.labelSmall) }
                    else -> { Ltr(name, weight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge); Ltr(type ?: "", color = C.mute, style = MaterialTheme.typography.labelSmall) }
                }
            }
        }
    }
    val home = e.b("isHome")
    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) { if (home) content() }
        Box(Modifier.padding(horizontal = 8.dp).clip(RoundedCornerShape(50)).background(if (type == "Goal") LocalAccent.current.copy(alpha = 0.2f) else C.card2)
            .padding(horizontal = 8.dp, vertical = 2.dp)) {
            Ltr(minuteText(e) + "'", style = MaterialTheme.typography.labelMedium, weight = FontWeight.Bold)
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { if (!home) content() }
    }
}

@Composable
private fun DividerLabel(text: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Divider(Modifier.weight(1f))
        Text(text, color = C.mute, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp))
        Divider(Modifier.weight(1f))
    }
}

@Composable
private fun Momentum(data: List<JSONObject>, homeColor: Color, awayColor: Color) {
    val maxMin = max(90.0, data.maxOf { it.d("minute") ?: 0.0 })
    val grow by animateFloatAsState(1f, tween(900), label = "m")
    Canvas(Modifier.fillMaxWidth().height(90.dp).padding(horizontal = 14.dp)) {
        val mid = size.height / 2
        val bw = size.width / (maxMin.toFloat() + 1)
        drawLine(C.line, Offset(0f, mid), Offset(size.width, mid), 2f)
        val ht = (45f / (maxMin.toFloat() + 1)) * size.width
        drawLine(C.line, Offset(ht, 4f), Offset(ht, size.height - 4f), 2f)
        for (p in data) {
            val v = (p.d("value") ?: 0.0).toFloat()
            val x = ((p.d("minute") ?: 0.0).toFloat() / (maxMin.toFloat() + 1)) * size.width
            val h = abs(v) / 100f * (mid - 4) * grow
            // left-to-right timeline (minute 0 on the left) like FotMob
            drawRoundRect(if (v >= 0) homeColor else awayColor, Offset(x, if (v >= 0) mid - h else mid), Size(max(bw - 1f, 1.5f), h), CornerRadius(1.5f))
        }
    }
}

/** Simple live win probability from expected goals and the current score (Poisson). */
@Composable
private fun WinProbability(m: MatchData, homeColor: Color, awayColor: Color) {
    if (!m.live) return
    var xgH = 0.0; var xgA = 0.0
    m.content.path("stats.Periods.All").a("stats").objs().forEach { g ->
        g.a("stats").objs().forEach { s -> if (s.s("key") == "expected_goals") { val v = s.a("stats"); xgH = v?.optString(0)?.toDoubleOrNull() ?: 0.0; xgA = v?.optString(1)?.toDoubleOrNull() ?: 0.0 } }
    }
    val played = m.minute.substringBefore('+').filter(Char::isDigit).toIntOrNull()?.coerceIn(1, 90) ?: return
    val left = (90 - played) / 90.0
    val rateH = max(0.15, xgH / played * 90) * left
    val rateA = max(0.15, xgA / played * 90) * left
    val gh = m.home.i("score") ?: 0; val ga = m.away.i("score") ?: 0
    fun pois(l: Double, k: Int): Double { var r = Math.exp(-l); for (i in 1..k) r *= l / i; return r }
    var pH = 0.0; var pD = 0.0; var pA = 0.0
    for (i in 0..8) for (j in 0..8) {
        val p = pois(rateH, i) * pois(rateA, j)
        when { gh + i > ga + j -> pH += p; gh + i == ga + j -> pD += p; else -> pA += p }
    }
    val t = pH + pD + pA
    Card {
        CardTitle("احتمال برد (زنده)")
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp).height(12.dp).clip(RoundedCornerShape(6.dp))) {
            Box(Modifier.weight(max(0.01, pH / t).toFloat()).fillMaxSize().background(homeColor))
            Box(Modifier.weight(max(0.01, pD / t).toFloat()).fillMaxSize().background(C.draw))
            Box(Modifier.weight(max(0.01, pA / t).toFloat()).fillMaxSize().background(awayColor))
        }
        Row(Modifier.fillMaxWidth().padding(14.dp)) {
            Text("${(pH / t * 100).toInt()}٪ ${m.home.s("name")}", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
            Text("مساوی ${(pD / t * 100).toInt()}٪", style = MaterialTheme.typography.labelMedium, color = C.mute)
            Text("${(pA / t * 100).toInt()}٪ ${m.away.s("name")}", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.End)
        }
    }
}

@Composable
private fun InfoCard(m: MatchData) {
    val ib = m.facts.o("infoBox")
    Card {
        CardTitle("اطلاعات بازی")
        InfoLine(Icons.Rounded.Schedule, "زمان", "${persianDate(m.time, "EEEE d MMMM yyyy")} · ${timeOf(m.time)}")
        ib.o("Tournament")?.let { t ->
            val r = t.s("roundName")
            InfoLine(Icons.Rounded.EmojiEvents, "رقابت", (t.s("leagueName") ?: "") + (r?.let { if (it.all(Char::isDigit)) " · هفته $it" else " · $it" } ?: ""))
        }
        ib.o("Stadium")?.let { s ->
            InfoLine(Icons.Rounded.Place, "ورزشگاه", listOfNotNull(s.s("name"), s.s("city")).joinToString("، ") + (s.i("capacity")?.let { " · ظرفیت $it" } ?: ""))
        }
        ib.o("Referee")?.s("text")?.let { InfoLine(Icons.Rounded.Sports, "داور", it) }
        ib.i("Attendance")?.let { InfoLine(Icons.Rounded.Groups, "تماشاگر", "%,d".format(it)) }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun InfoLine(icon: ImageVector, k: String, v: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = C.mute, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(k, color = C.mute, style = MaterialTheme.typography.labelSmall)
            Text(v, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/* ---------- stats ---------- */

@Composable
private fun StatsTab(m: MatchData, homeColor: Color, awayColor: Color) {
    val periods = m.content.path("stats.Periods")!!
    val keys = periods.keys().asSequence().toList()
    var period by rememberSaveable { mutableStateOf(keys.first()) }
    if (period !in keys) period = keys.first()
    val labels = mapOf("All" to "کل بازی", "FirstHalf" to "نیمه اول", "SecondHalf" to "نیمه دوم")
    if (keys.size > 1) Seg(keys.map { it to (labels[it] ?: it) }, period, Modifier.padding(horizontal = 0.dp)) { period = it }
    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Crest(m.home.i("id") ?: 0, 22.dp); Spacer(Modifier.width(6.dp)); Ltr(m.home.s("name") ?: "", Modifier.weight(1f), weight = FontWeight.Bold)
        Ltr(m.away.s("name") ?: "", Modifier.weight(1f), weight = FontWeight.Bold, align = TextAlign.End); Spacer(Modifier.width(6.dp)); Crest(m.away.i("id") ?: 0, 22.dp)
    }
    periods.o(period).a("stats").objs().forEach { g ->
        Card {
            CardTitle(tr(g.s("title")))
            g.a("stats").objs().forEach { st -> StatRow(st, homeColor, awayColor) }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun StatRow(st: JSONObject, homeColor: Color, awayColor: Color) {
    if (st.s("type") == "title") { Text(tr(st.s("title")), color = C.mute, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)); return }
    val v = st.a("stats").strs()
    if (v.size < 2) return
    val raw = st.a("rawStats").objs()
    fun num(i: Int) = raw.getOrNull(i).d("value") ?: v[i].substringBefore(' ').replace("%", "").toDoubleOrNull() ?: 0.0
    val h = num(0); val a = num(1)
    val hi = st.s("highlighted")
    if (st.s("type") == "graph" || st.s("key") == "BallPossesion") {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            Text(tr(st.s("title")), Modifier.fillMaxWidth(), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth().height(28.dp).clip(RoundedCornerShape(14.dp))) {
                Box(Modifier.weight(max(h, 1.0).toFloat()).fillMaxSize().background(homeColor), contentAlignment = Alignment.Center) {
                    Text("${h.toInt()}٪", color = if (lum(homeColor) > 0.6f) Color.Black else Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                }
                Box(Modifier.weight(max(a, 1.0).toFloat()).fillMaxSize().background(awayColor), contentAlignment = Alignment.Center) {
                    Text("${a.toInt()}٪", color = if (lum(awayColor) > 0.6f) Color.Black else Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        return
    }
    val tot = (h + a).takeIf { it > 0 } ?: 1.0
    Column(Modifier.padding(horizontal = 14.dp, vertical = 7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatValue(v[0], hi == "home", homeColor)
            Text(tr(st.s("title")), Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelLarge, color = C.text)
            StatValue(v[1], hi == "away", awayColor)
        }
        Spacer(Modifier.height(5.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            // home bar grows from the middle to the right, away from the middle to the left
            Box(Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(3.dp)).background(C.card2), contentAlignment = Alignment.CenterEnd) {
                Box(Modifier.fillMaxWidth((h / tot).toFloat()).fillMaxSize().clip(RoundedCornerShape(3.dp)).background(if (hi == "home") homeColor else homeColor.copy(alpha = 0.45f)))
            }
            Box(Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(3.dp)).background(C.card2), contentAlignment = Alignment.CenterStart) {
                Box(Modifier.fillMaxWidth((a / tot).toFloat()).fillMaxSize().clip(RoundedCornerShape(3.dp)).background(if (hi == "away") awayColor else awayColor.copy(alpha = 0.45f)))
            }
        }
    }
}

@Composable
private fun StatValue(v: String, hi: Boolean, color: Color) {
    Box(Modifier.clip(RoundedCornerShape(50)).background(if (hi) color else Color.Transparent).padding(horizontal = 8.dp, vertical = 1.dp)) {
        Ltr(v, color = if (hi) (if (lum(color) > 0.6f) Color.Black else Color.White) else C.text, weight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
    }
}

/* ---------- lineup ---------- */

@Composable
private fun LineupTab(m: MatchData) {
    val nav = LocalNav.current
    val lu = m.content.o("lineup")!!
    val H = lu.o("homeTeam")!!
    val A = lu.o("awayTeam")!!
    if ((lu.s("lineupType") ?: "standard") != "standard") Pill("ترکیب احتمالی")
    val hasLayout = H.a("starters").objs().any { it.o("verticalLayout") != null }
    if (hasLayout) Pitch(H, A)
    listOf(H, A).forEach { T ->
        Card {
            CardTitle(T.s("name") ?: "") { Text(T.s("formation") ?: "", color = C.mute, style = MaterialTheme.typography.labelMedium) }
            T.o("coach")?.let { c -> PersonRow(c.i("id") ?: 0, c.s("name") ?: "", "سرمربی") { nav.player(c.i("id") ?: 0) } }
            if (!hasLayout) { SubHead("ترکیب اصلی"); T.a("starters").objs().forEach { p -> LineupRow(p) } }
            val subs = T.a("subs").objs()
            if (subs.isNotEmpty()) { SubHead("ذخیره‌ها"); subs.forEach { p -> LineupRow(p) } }
            val out = T.a("unavailable").objs()
            if (out.isNotEmpty()) {
                SubHead("غایبان")
                out.forEach { p ->
                    val type = p.o("unavailability").s("type")
                    PersonRow(p.i("id") ?: 0, p.s("name") ?: "", when (type) { "injury" -> "مصدوم"; "suspension" -> "محروم"; else -> "غایب" },
                        trailing = { Icon(Icons.Rounded.LocalHospital, null, tint = C.live, modifier = Modifier.size(18.dp)) }) { nav.player(p.i("id") ?: 0) }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun SubHead(text: String) = Text(text, color = C.mute, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
    modifier = Modifier.padding(start = 14.dp, top = 10.dp, bottom = 4.dp))

@Composable
private fun LineupRow(p: JSONObject) {
    val nav = LocalNav.current
    val subIn = p.path("performance").a("substitutionEvents").objs().firstOrNull { it.s("type") == "subIn" }
    val meta = listOfNotNull(p.s("shirtNumber")?.let { "#$it" }, p.s("countryName"), subIn?.let { "ورود دقیقه ${it.s("time")}" }).joinToString(" · ")
    PersonRow(p.i("id") ?: 0, p.s("name") ?: "", meta, trailing = { Rating(p.path("performance").d("rating")) }) { nav.player(p.i("id") ?: 0) }
}

@Composable
fun PersonRow(id: Int, name: String, meta: String, trailing: @Composable () -> Unit = {}, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Face(id, 38.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Ltr(name, weight = FontWeight.Bold)
            if (meta.isNotEmpty()) Text(meta, color = C.mute, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing()
    }
}

@Composable
private fun Pitch(H: JSONObject, A: JSONObject) {
    val nav = LocalNav.current
    BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(0.66f).clip(RoundedCornerShape(22.dp))
        .background(Brush.verticalGradient(listOf(C.pitch, C.pitchDark, C.pitch)))) {
        val w = maxWidth; val h = maxHeight
        Canvas(Modifier.fillMaxSize()) {
            // mowing stripes
            val n = 12
            for (i in 0 until n) if (i % 2 == 0) drawRect(Color.White.copy(alpha = 0.035f), Offset(0f, size.height * i / n), Size(size.width, size.height / n))
            val st = Stroke(width = 2.dp.toPx())
            val line = Color.White.copy(alpha = 0.35f)
            val pad = 8.dp.toPx()
            drawRect(line, Offset(pad, pad), Size(size.width - 2 * pad, size.height - 2 * pad), style = st)
            drawLine(line, Offset(pad, size.height / 2), Offset(size.width - pad, size.height / 2), st.width)
            drawCircle(line, size.width * 0.14f, Offset(size.width / 2, size.height / 2), style = st)
            val bw = size.width * 0.6f; val bh = size.height * 0.15f
            drawRect(line, Offset((size.width - bw) / 2, pad), Size(bw, bh), style = st)
            drawRect(line, Offset((size.width - bw) / 2, size.height - pad - bh), Size(bw, bh), style = st)
            val sw = size.width * 0.28f; val sh = size.height * 0.055f
            drawRect(line, Offset((size.width - sw) / 2, pad), Size(sw, sh), style = st)
            drawRect(line, Offset((size.width - sw) / 2, size.height - pad - sh), Size(sw, sh), style = st)
        }
        Text("${H.s("formation") ?: ""}", Modifier.absoluteOffset(12.dp, 12.dp), color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Text("${A.s("formation") ?: ""}", Modifier.absoluteOffset(12.dp, h - 30.dp), color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        val chipW = 64.dp
        for ((team, home) in listOf(H to true, A to false)) {
            team.a("starters").objs().forEach { p ->
                val v = p.o("verticalLayout") ?: return@forEach
                val x = (v.d("x") ?: 0.5).toFloat(); val y = (v.d("y") ?: 0.5).toFloat()
                val top = if (home) 0.03f + y * 0.44f else 0.97f - y * 0.44f
                val left = if (home) 0.06f + x * 0.88f else 0.94f - x * 0.88f
                Column(
                    Modifier.absoluteOffset(w * left - chipW / 2, h * top - 22.dp).width(chipW).clickable { nav.player(p.i("id") ?: 0) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box {
                        Face(p.i("id") ?: 0, 38.dp, if (home) Color.White else C.gold)
                        val r = p.path("performance").d("rating")
                        if (r != null) Rating(r, Modifier.align(Alignment.TopEnd).absoluteOffset(10.dp, (-6).dp))
                        PlayerBadges(p, Modifier.align(Alignment.BottomStart).absoluteOffset((-8).dp, 4.dp))
                    }
                    Text(
                        "${p.s("shirtNumber") ?: ""} ${p.s("lastName") ?: p.s("name")?.substringAfterLast(' ') ?: ""}",
                        color = Color.White, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 2.dp).clip(RoundedCornerShape(4.dp)).background(Color.Black.copy(alpha = 0.35f)).padding(horizontal = 3.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerBadges(p: JSONObject, modifier: Modifier) {
    val ev = p.path("performance").a("events").objs().map { it.s("type") }
    val goals = ev.count { it == "goal" }
    val subOut = p.path("performance").a("substitutionEvents").objs().any { it.s("type") == "subOut" }
    if (goals == 0 && "redCard" !in ev && "yellowCard" !in ev && !subOut && "assist" !in ev) return
    Row(modifier.clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 3.dp, vertical = 1.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        if (goals > 0) Text(if (goals > 1) "⚽$goals" else "⚽", fontSize = 9.sp)
        if ("assist" in ev) Text("A", fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold)
        if ("redCard" in ev) Box(Modifier.size(6.dp, 9.dp).background(C.red))
        else if ("yellowCard" in ev) Box(Modifier.size(6.dp, 9.dp).background(C.yellow))
        if (subOut) Icon(Icons.Rounded.SwapVert, null, tint = C.live, modifier = Modifier.size(10.dp))
    }
}

/* ---------- table & h2h ---------- */

@Composable
private fun MatchTable(m: MatchData) {
    val leagueId = m.content.o("table").i("leagueId") ?: return
    val l = rememberLoader(leagueId) { Api.fmArray("tltable", mapOf("leagueId" to leagueId), 60_000) }
    val hl = listOfNotNull(m.home.i("id"), m.away.i("id"))
    LoadBox(l, 8) { arr -> TableView(arr, hl) }
}

@Composable
private fun H2h(m: MatchData, homeColor: Color, awayColor: Color, limit: Int) {
    val nav = LocalNav.current
    val x = m.content.o("h2h") ?: return
    val sum = x.a("summary")
    val hw = sum?.optInt(0) ?: 0; val dr = sum?.optInt(1) ?: 0; val aw = sum?.optInt(2) ?: 0
    Card {
        CardTitle("رودررو")
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
            listOf(Triple(hw, "برد ${m.home.s("name")}", homeColor), Triple(dr, "تساوی", C.draw), Triple(aw, "برد ${m.away.s("name")}", awayColor)).forEach { (n, label, c) ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$n", fontSize = 26.sp, fontWeight = FontWeight.Black, color = c, style = MaterialTheme.typography.headlineMedium)
                    Text(label, color = C.mute, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        val tot = (hw + dr + aw).coerceAtLeast(1)
        Row(Modifier.fillMaxWidth().padding(14.dp).height(8.dp).clip(CircleShape)) {
            if (hw > 0) Box(Modifier.weight(hw.toFloat() / tot).fillMaxSize().background(homeColor))
            if (dr > 0) Box(Modifier.weight(dr.toFloat() / tot).fillMaxSize().background(C.draw))
            if (aw > 0) Box(Modifier.weight(aw.toFloat() / tot).fillMaxSize().background(awayColor))
        }
        x.a("matches").objs().take(limit).forEach { j ->
            val mi = parseMatch(j)
            Divider()
            MatchRow(mi, { nav.match(mi.id) }, showDate = true, sub = j.o("league").s("name"), star = false)
        }
    }
}
