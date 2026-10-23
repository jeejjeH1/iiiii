package app.goallive.live.ui

import app.goallive.live.L

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.runtime.*
import androidx.compose.ui.draw.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.min
import app.goallive.live.data.Api
import app.goallive.live.data.Clubs
import app.goallive.live.data.Comps
import app.goallive.live.data.LeagueBlock
import app.goallive.live.data.MatchItem
import app.goallive.live.data.Store
import app.goallive.live.data.parseMatch
import app.goallive.live.data.parseMatchList
import app.goallive.live.data.o
import kotlinx.coroutines.delay
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private val BIG = setOf(42, 47, 87, 55, 54, 53, 523, 73, 77, 50, 44, 289, 10216)

/** Order of the league blocks: the user's leagues and teams, then Iran's competitions, then the big ones; FotMob's own order after that. */
private fun leagueWeight(b: LeagueBlock, ms: List<MatchItem>): Int =
    (if (Store.isLeague(b.primaryId)) 8 else 0) + (if (ms.any { Store.follows(it) }) 4 else 0) +
        (if (b.ccode == "IRN" || b.primaryId == 523) 2 else 0) + (if (b.primaryId in BIG) 1 else 0)

/** Club color for a team: its theme, else a stable hue (site: teamColor). */
fun teamColor(id: Int): Color = Clubs.get(id)?.let { Color(it.c1) } ?: hueColor(id)

/** CSS linear-gradient(deg, ...) with hard or soft stops, painted behind a box. */
fun DrawScope.cssLinear(deg: Float, vararg stops: Pair<Float, Color>) {
    val r = Math.toRadians(deg.toDouble())
    val dx = sin(r).toFloat(); val dy = -cos(r).toFloat()
    val len = abs(size.width * dx) + abs(size.height * dy)
    val c = Offset(size.width / 2, size.height / 2)
    drawRect(Brush.linearGradient(*stops, start = c - Offset(dx, dy) * (len / 2), end = c + Offset(dx, dy) * (len / 2)))
}

@Composable
fun HomeScreen() {
    val nav = LocalNav.current
    var day by rememberSaveable { mutableIntStateOf(0) }
    var filter by rememberSaveable { mutableStateOf("all") }
    var byTime by rememberSaveable { mutableStateOf(false) }
    val collapsed = remember { mutableStateMapOf<Int, Boolean>() }
    val dayStart = startOfDay(day)
    val url = Api.fmUrl("matches", Api.matchesParams(dayKey(dayStart)))

    val loader = rememberLoader(
        day,
        peek = { (Api.peek(url) as? JSONObject)?.let(::parseMatchList) },
        poll = { list -> if (day == 0) (if (list.any { b -> b.matches.any { it.live } }) 20_000L else 60_000L) else if (day > 0) 300_000L else null },
    ) { parseMatchList(Api.fm("matches", Api.matchesParams(dayKey(dayStart)), 15_000)) }

    // flash rows whose score just went up (".m.goal")
    val lastScores = remember { HashMap<Int, Int>() }
    val flash = remember { mutableStateMapOf<Int, Long>() }
    LaunchedEffect(loader.value) {
        val list = loader.value ?: return@LaunchedEffect
        val now = System.currentTimeMillis()
        for (m in list.flatMap { it.matches }) {
            val total = (m.home.score ?: 0) + (m.away.score ?: 0)
            val before = lastScores[m.id]
            if (before != null && total > before && m.live) flash[m.id] = now + 3_400
            lastScores[m.id] = total
        }
        delay(3_500)
        flash.keys.filter { (flash[it] ?: 0) < System.currentTimeMillis() }.forEach { flash.remove(it) }
    }

    val list = loader.value
    Refreshable(loader) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = BottomSpace)) {
        if (day == 0 && list != null) {
            val live = list.flatMap { b -> b.matches.filter { it.live }.map { it to b } }
            if (live.isNotEmpty()) item(key = "ticker") { Ticker(live.sortedByDescending { (_, b) -> if (b.primaryId in BIG) 1 else 0 }.take(24).map { it.first }) }
        }
        if (day == 0) item(key = "stories") { StoryRow() }
        if (day == 0) Store.myTeam?.let { t -> item(key = "my") { MyTeamCard(t.id, t.name, list) } }
        if (day == 0 && list != null) {
            val hit = list.mapNotNull { b -> Comps.get(b.primaryId)?.let { b to it } }.filter { it.first.matches.isNotEmpty() }.maxByOrNull { it.first.matches.size }
            if (hit != null) item(key = "comp") { CompNight(hit.first, hit.second) }
        }
        if (list != null) {
            val picks = featured(list)
            if (picks.isNotEmpty()) {
                item(key = "featT") { SectionTitle(L("بازی‌های بزرگ")) }
                item(key = "feat") { Featured(picks) }
            }
        }
        item(key = "dates") { DateStrip(day) { day = it } }
        item(key = "tools") {
            val all = list?.flatMap { it.matches }.orEmpty()
            val liveN = all.count { it.live }
            val favN = list?.sumOf { b -> b.matches.count { Store.followsWithLeague(it) } } ?: 0
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Seg(
                    listOf("all" to L("همه {0}", all.size), "live" to L("زنده"), "fav" to L("دنبال‌شده‌ها") + if (favN > 0) " $favN" else ""),
                    filter, Modifier.weight(1f), badge = mapOf("live" to liveN), icons = mapOf("fav" to Ic.star),
                ) { filter = it }
                Box(Modifier.size(46.dp).clip(RoundedCornerShape(13.dp)).background(C.card).border(1.dp, C.line, RoundedCornerShape(13.dp))
                    .clickable { byTime = !byTime }, contentAlignment = Alignment.Center) {
                    Icon(if (byTime) Ic.list else Ic.trophy, if (byTime) L("مرتب‌سازی بر اساس لیگ") else L("مرتب‌سازی بر اساس زمان"), Modifier.size(20.dp), tint = C.ink)
                }
            }
        }
        when (val s = loader.state) {
            is Load.Loading -> item { Skeleton(6) }
            is Load.Err -> item { ErrorView({ loader.state = Load.Loading; loader.reload() }) }
            is Load.Ok -> {
                val keep: (MatchItem) -> Boolean = { m -> filter == "all" || (if (filter == "live") m.live else Store.followsWithLeague(m)) }
                val empty: @Composable () -> Unit = {
                    when {
                        filter == "live" -> EmptyView(L("الان بازی زنده‌ای در جریان نیست"), Ic.live, L("بازی‌های امروز را ببینید؛ وقتی بازی شروع شود اینجا می‌آید."),
                            L("همه بازی‌های امروز")) { filter = "all"; day = 0 }
                        filter == "fav" && Store.teams.isEmpty() && Store.leagues.isEmpty() -> EmptyView(L("هنوز تیم یا لیگی را دنبال نکرده‌اید"), Ic.star,
                            L("روی ستاره کنار هر تیم یا لیگ بزنید تا بازی‌هایش اینجا جمع شود."), L("پیدا کردن تیم")) { nav.tab("search") }
                        filter == "fav" -> EmptyView(L("تیم‌های شما این روز بازی ندارند"), Ic.star, null, L("همه بازی‌ها")) { filter = "all" }
                        else -> EmptyView(L("بازی‌ای در این روز نیست"), Ic.ball, null, if (day != 0) L("بازی‌های امروز") else null) { day = 0 }
                    }
                }
                if (byTime) {
                    val rows = s.v.flatMap { b -> b.matches.filter(keep) }.sortedWith(compareByDescending<MatchItem> { it.live }.thenBy { it.time })
                    if (rows.isEmpty()) item { empty() }
                    else item {
                        Card(Modifier.padding(top = 12.dp)) {
                            rows.forEachIndexed { i, m -> MatchRow(m, { nav.match(m.id) }, sub = m.leagueName, flash = (flash[m.id] ?: 0) > 0, first = i == 0, crestFly = true) }
                        }
                    }
                } else {
                    val shown = s.v.map { b -> b to b.matches.filter(keep) }.filter { it.second.isNotEmpty() }
                        .sortedByDescending { (b, ms) -> leagueWeight(b, ms) }
                    if (shown.isEmpty()) item { empty() }
                    items(shown) { (b, ms) ->
                        val isCollapsed = collapsed[b.id] == true && filter == "all"
                        LeagueCard(b, ms, isCollapsed, { collapsed[b.id] = !isCollapsed }, flash)
                    }
                }
            }
        }
    }
    }
}

/* ---------- league block (".lg") ---------- */

@Composable
private fun LeagueCard(b: LeagueBlock, ms: List<MatchItem>, isCollapsed: Boolean, toggle: () -> Unit, flash: Map<Int, Long>) {
    val nav = LocalNav.current
    val accent = LocalAccent.current
    val live = ms.count { it.live }
    Card(Modifier.padding(top = 12.dp).animateContentSize()) {
        Box {
            // accent tab on the start edge
            Box(Modifier.align(Alignment.CenterStart).padding(top = 14.dp, bottom = 12.dp).width(3.dp).height(20.dp)
                .clip(RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp)).background(accent))
            Row(Modifier.fillMaxWidth().clickable(onClick = toggle).padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LeagueLogo(b.primaryId, 22.dp)
                Row(Modifier.weight(1f).clickable { nav.league(b.primaryId) }, verticalAlignment = Alignment.CenterVertically) {
                    Ltr(b.name, Modifier.weight(1f, false), style = vz(13.sp, FontWeight.Black))
                    if (b.ccode.isNotEmpty() && b.ccode != "INT") Text("  ${b.ccode}", color = C.mute, style = lat(11.sp, FontWeight.Bold).copy(letterSpacing = 1.sp))
                }
                if (live > 0) Pill("$live", PillKind.Live, dot = true)
                StarButton(Store.isLeague(b.primaryId), { Store.toggleLeague(b.primaryId, b.name) })
                Icon(Ic.down, null, Modifier.size(18.dp).rotate(if (isCollapsed) 90f else 0f), tint = C.faint)
            }
        }
        AnimatedVisibility(!isCollapsed) {
            Column { ms.forEach { m -> MatchRow(m, { nav.match(m.id) }, flash = (flash[m.id] ?: 0) > 0, crestFly = true) } }
        }
    }
}

/* ---------- live ticker (".ticker") ---------- */

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Ticker(live: List<MatchItem>) {
    val nav = LocalNav.current
    Box(Modifier.padding(top = 2.dp, bottom = 6.dp).fillMaxWidth().height(34.dp)
        .background(C.card).border(1.dp, C.line).clipToBounds()) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(Modifier.fillMaxSize().basicMarquee(iterations = Int.MAX_VALUE, velocity = 40.dp).padding(start = 70.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                live.forEach { m ->
                    Row(Modifier.clickable { nav.match(m.id) }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Crest(m.home.id, 16.dp)
                        TeamText(m.home.id, m.home.name, size = 13)
                        Text("${m.home.score ?: 0}-${m.away.score ?: 0}", color = LocalAccent.current, style = vz(13.sp, FontWeight.Black))
                        TeamText(m.away.id, m.away.name, size = 13)
                        Crest(m.away.id, 16.dp)
                        Text(statusText(m), color = C.live, style = vz(11.sp, FontWeight.Bold))
                    }
                }
            }
        }
        // red "LIVE" tag at the start edge (right in Persian, left in English), slanted on its inner side so the scores slide under it
        val ltr = app.goallive.live.Lang.en
        val slant = GenericShape { s, _ ->
            if (ltr) { moveTo(0f, 0f); lineTo(s.width, 0f); lineTo(s.width - s.height * 0.35f, s.height); lineTo(0f, s.height) }
            else { moveTo(s.height * 0.35f, 0f); lineTo(s.width, 0f); lineTo(s.width, s.height); lineTo(0f, s.height) }
            close()
        }
        Row(Modifier.align(Alignment.CenterStart).fillMaxHeight().clip(slant).background(C.live).padding(start = 12.dp, end = 20.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("LIVE", color = Color.White, style = lat(12.sp, FontWeight.ExtraBold).copy(letterSpacing = 2.sp))
            LiveDot(Color.White)
        }
    }
}

/* ---------- big games (".featured" / ".fcard") ---------- */

private fun featured(list: List<LeagueBlock>): List<MatchItem> {
    fun score(m: MatchItem, pid: Int) = (if (m.live) 100 else 0) + (if (Clubs.get(m.home.id) != null) 30 else 0) + (if (Clubs.get(m.away.id) != null) 30 else 0) +
        (if (Clubs.derby(m.home.id, m.away.id) != null) 40 else 0) + (if (pid in BIG) 15 else 0) + (if (Store.follows(m)) 50 else 0) - (if (m.finished) 20 else 0)
    return list.flatMap { b -> b.matches.filter { !it.cancelled }.map { it to score(it, b.primaryId) } }
        .filter { it.second >= 30 }.sortedByDescending { it.second }.take(8).map { it.first }
}

@Composable
private fun Featured(picks: List<MatchItem>) {
    val nav = LocalNav.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val w = min(maxWidth * 0.82f, 330.dp)
        LazyRow(contentPadding = PaddingValues(top = 6.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(picks) { m ->
                val shape = RoundedCornerShape(24.dp)
                val c1 = teamColor(m.home.id); val c2 = teamColor(m.away.id)
                Box(Modifier.width(w).height(178.dp).clip(shape).drawBehind {
                    cssLinear(245f, 0f to c1, 0.49f to c1, 0.51f to c2, 1f to c2)
                    drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.05f), Color.Black.copy(alpha = 0.55f))))
                    drawRect(Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent), Offset(size.width / 2, size.height * 1.2f), size.width * 0.6f))
                }.clickable { nav.match(m.id) }) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Ltr(m.leagueName, Modifier.weight(1f), style = vz(11.sp, FontWeight.ExtraBold), color = Color.White.copy(alpha = 0.92f))
                        if (m.live) Pill(L("زنده"), PillKind.Live, dot = true)
                    }
                    Row(Modifier.fillMaxWidth().align(Alignment.Center).padding(horizontal = 12.dp).offset(y = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        FTeam(m.home.id, m.home.name, Modifier.weight(1f))
                        Column(Modifier.width(80.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            if (m.started && !m.cancelled) ScoreText(m.home.score, m.away.score, 30, Color.White, sepColor = Color.White.copy(alpha = 0.5f))
                            else Text(timeOf(m.time), color = Color.White, style = vz(30.sp, FontWeight.Black))
                            Ltr(if (m.started) statusText(m) else persianDate(m.time, "EEE"), style = vz(11.sp, FontWeight.Bold), color = Color.White.copy(alpha = 0.85f))
                        }
                        FTeam(m.away.id, m.away.name, Modifier.weight(1f))
                    }
                    Clubs.derby(m.home.id, m.away.id)?.let {
                        T("⚡ $it", 12, FontWeight.Black, C.gold, Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun FTeam(id: Int, name: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Crest(id, 58.dp, large = true)
        Spacer(Modifier.height(6.dp))
        TeamText(id, name, size = 15, w = FontWeight.ExtraBold, color = Color.White, maxLines = 2, align = TextAlign.Center)
    }
}

/* ---------- competition night (".comp-night") ---------- */

@Composable
private fun CompNight(b: LeagueBlock, c: app.goallive.live.data.Comp) {
    val nav = LocalNav.current
    val accent = Color(c.accent); val bg = Color(c.bg); val glow = Color(c.glow)
    val live = b.matches.count { it.live }
    Row(
        Modifier.padding(top = 10.dp, bottom = 4.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp))
            .drawBehind {
                cssLinear(120f, 0f to bg, 1f to lerpColor(bg, glow, 0.55f))
                // a few stars
                for ((x, y) in listOf(0.15f to 0.3f, 0.6f to 0.2f, 0.85f to 0.7f, 0.35f to 0.8f, 0.72f to 0.45f)) drawCircle(Color.White.copy(alpha = 0.6f), 1.3f.dp.toPx(), Offset(size.width * x, size.height * y))
            }
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(22.dp))
            .clickable { nav.league(b.primaryId) }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Ic.starOn, null, Modifier.size(40.dp), tint = accent)
        Column(Modifier.weight(1f)) {
            T(c.tag, 17, FontWeight.Black, Color.White)
            T(L("{0} بازی امروز", b.matches.size) + if (live > 0) L(" · {0} بازی زنده", live) else "", 12, color = Color.White.copy(alpha = 0.85f))
        }
        Icon(Ic.chev, null, Modifier.size(20.dp), tint = Color.White)
    }
}

/* ---------- dates (".dates") ---------- */

@Composable
private fun DateStrip(selected: Int, onPick: (Int) -> Unit) {
    val accent = LocalAccent.current
    val days = (-7..7).toList()
    val state = rememberLazyListState(initialFirstVisibleItemIndex = (selected + 7 - 2).coerceAtLeast(0))
    LazyRow(Modifier.padding(top = 6.dp, bottom = 10.dp), state = state, contentPadding = PaddingValues(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(days) { d ->
            val ms = startOfDay(d)
            val on = d == selected
            val shape = RoundedCornerShape(16.dp)
            Column(
                Modifier.offset(y = if (on) (-2).dp else 0.dp).width(64.dp).height(56.dp)
                    .then(if (on) Modifier.glow(accent.copy(alpha = 0.35f), 8.dp, shape) else Modifier)
                    .clip(shape).background(if (on) accent else C.card).border(1.dp, if (on) Color.Transparent else C.line, shape)
                    .clickable { onPick(d) },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                val ink = LocalAccentInk.current
                T(dayName(ms).let { if (it.length > 6) persianDate(ms, "EEE") else it }, 11, FontWeight.Bold, if (on) ink.copy(alpha = 0.7f) else C.mute, maxLines = 1)
                T(persianDate(ms, "d MMM"), 14, FontWeight.Black, if (on) ink else C.ink, maxLines = 1)
            }
        }
    }
}

/* ---------- my team (".myteam") ---------- */

@Composable
fun MyTeamCard(teamId: Int, name: String, today: List<LeagueBlock>? = null) {
    val nav = LocalNav.current
    val club = Clubs.get(teamId)
    // today's match from the list we already have, otherwise the server's next / last match
    val fromList = today?.flatMap { it.matches }?.firstOrNull { it.home.id == teamId || it.away.id == teamId }
    val l = rememberLoader(teamId, fromList == null, poll = { _ -> 300_000L }) {
        if (fromList != null) null else {
            val d = runCatching { Api.obj("/api/widget?team=$teamId", 60_000) }.getOrElse {
                // our server is unreachable: work it out straight from FotMob
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { JSONObject(app.goallive.live.DirectData.widget(teamId.toString())) }
            }
            val state = d.optString("state")
            d.o("match")?.let { m ->
                val st = JSONObject().put("started", state == "live" || state == "finished").put("finished", state == "finished")
                    .put("liveTime", JSONObject().put("short", m.optString("minute")))
                parseMatch(JSONObject().put("id", m.optInt("id")).put("home", m.optJSONObject("home")).put("away", m.optJSONObject("away"))
                    .put("timeTS", m.optLong("kickoff")).put("status", st), 0, m.optString("league"))
            }
        }
    }
    val m = fromList ?: l.value ?: return
    val c1 = club?.let { Color(it.c1) } ?: parseColor(Store.myTeam?.color) ?: LocalAccent.current
    val c2 = club?.let { Color(it.accent ?: it.c2) } ?: Color.Black
    val ink = club?.let { if (lum(Color(it.ink)) < 0.3f) Color(it.ink) else Color.White } ?: Color.White
    Box(
        Modifier.padding(top = 10.dp, bottom = 4.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .drawBehind {
                cssLinear(120f, 0f to c1, 0.7f to darken(c1, 0.3f), 1f to c2)
                club?.let { drawPattern(it.pattern) }
            }
            .clickable { nav.match(m.id) }.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 12.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                T(L("⭐ تیم من · {0}", club?.fa ?: name), 12, FontWeight.Black, ink, Modifier.weight(1f), maxLines = 1)
                if (m.live) Pill(L("زنده"), PillKind.Live, dot = true) else Ltr(m.leagueName, style = vz(12.sp, FontWeight.Black), color = ink)
            }
            Row(Modifier.padding(top = 12.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                MtTeam(m.home.id, m.home.name, ink, Modifier.weight(1f))
                if (m.started && !m.cancelled) ScoreText(m.home.score, m.away.score, 30, ink, sepColor = ink.copy(alpha = 0.5f))
                else Text(timeOf(m.time), color = ink, style = vz(30.sp, FontWeight.Black))
                MtTeam(m.away.id, m.away.name, ink, Modifier.weight(1f))
            }
            T(if (m.started || m.finished) statusText(m) else persianDate(m.time, "EEEE d MMMM"), 12, FontWeight.Bold,
                ink.copy(alpha = 0.9f), Modifier.fillMaxWidth(), align = TextAlign.Center)
        }
    }
}

@Composable
private fun MtTeam(id: Int, name: String, ink: Color, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Crest(id, 46.dp, large = true)
        Spacer(Modifier.height(6.dp))
        TeamText(id, name, size = 15, w = FontWeight.ExtraBold, color = ink, align = TextAlign.Center)
    }
}

internal fun fantasySub(): String {
    val o = runCatching { org.json.JSONObject(Store.prefs().getString("fantasy", "{}")!!) }.getOrNull() ?: return L("تیم رویایی‌ات را بساز")
    val total = o.optJSONObject("points")?.let { p -> p.keys().asSequence().sumOf { p.optInt(it) } } ?: 0
    val n = o.optJSONArray("team")?.length() ?: 0
    return when { n == 0 -> L("تیم رویایی‌ات را بساز و امتیاز بگیر"); n < 11 -> L("تیمت {0}/۱۱ نفر است؛ کاملش کن", n); else -> L("مجموع امتیاز تو: {0}", total) }
}
