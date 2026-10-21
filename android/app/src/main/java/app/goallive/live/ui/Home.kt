package app.goallive.live.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.goallive.live.data.Api
import app.goallive.live.data.Clubs
import app.goallive.live.data.LeagueBlock
import app.goallive.live.data.MatchItem
import app.goallive.live.data.Store
import app.goallive.live.data.i
import app.goallive.live.data.l
import app.goallive.live.data.o
import app.goallive.live.data.parseMatchList
import app.goallive.live.data.s
import kotlinx.coroutines.delay
import org.json.JSONObject

private val BIG = setOf(42, 47, 87, 55, 54, 53, 523, 73, 77, 50, 44, 289, 10216)

@Composable
fun HomeScreen() {
    val nav = LocalNav.current
    var day by rememberSaveable { mutableStateOf(0) }
    var filter by rememberSaveable { mutableStateOf("all") }
    val collapsed = remember { mutableStateMapOf<Int, Boolean>() }
    val dayStart = startOfDay(day)
    val url = Api.fmUrl("matches", Api.matchesParams(dayKey(dayStart)))

    val loader = rememberLoader(
        day,
        peek = { (Api.peek(url) as? JSONObject)?.let(::parseMatchList) },
        poll = { list -> if (day == 0) (if (list.any { b -> b.matches.any { it.live } }) 20_000L else 60_000L) else if (day > 0) 300_000L else null },
    ) { parseMatchList(Api.fm("matches", Api.matchesParams(dayKey(dayStart)), 15_000)) }

    // flash rows whose score just changed
    val lastScores = remember { HashMap<Int, Int>() }
    val flash = remember { mutableStateMapOf<Int, Long>() }
    LaunchedEffect(loader.value) {
        val list = loader.value ?: return@LaunchedEffect
        val now = System.currentTimeMillis()
        for (m in list.flatMap { it.matches }) {
            val total = (m.home.score ?: 0) + (m.away.score ?: 0)
            val before = lastScores[m.id]
            if (before != null && total > before && m.live) flash[m.id] = now + 10_000
            lastScores[m.id] = total
        }
        delay(10_500)
        flash.keys.filter { (flash[it] ?: 0) < System.currentTimeMillis() }.forEach { flash.remove(it) }
    }

    val listState = rememberLazyListState()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(58.dp).padding(start = 18.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.SportsSoccer, null, tint = LocalAccent.current, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(8.dp))
            Text("گل‌لایو", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = { nav.search() }) { Icon(Icons.Rounded.Search, "جستجو", tint = C.text) }
        }
        DateStrip(day) { day = it }
        val blocks = loader.value
        val all = blocks?.flatMap { it.matches }.orEmpty()
        val liveN = all.count { it.live }
        val favN = blocks?.sumOf { b -> b.matches.count { Store.followsWithLeague(it) } } ?: 0
        Seg(
            listOf("all" to "همه ${all.size}", "live" to (if (liveN > 0) "● زنده $liveN" else "زنده"), "fav" to "دنبال‌شده‌ها $favN"),
            filter, Modifier.padding(vertical = 8.dp),
        ) { filter = it }

        LoadBox(loader, 7) { list ->
            val keep: (MatchItem) -> Boolean = { m -> filter == "all" || (if (filter == "live") m.live else Store.followsWithLeague(m)) }
            val shown = list.map { b -> b to b.matches.filter(keep) }.filter { it.second.isNotEmpty() }
                .sortedByDescending { (b, ms) -> (if (Store.isLeague(b.primaryId)) 4 else 0) + (if (ms.any { Store.follows(it) }) 2 else 0) + (if (b.primaryId in BIG) 1 else 0) }
            LazyColumn(state = listState, contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (day == 0 && filter == "all") Store.myTeam?.let { t -> item(key = "my") { MyTeamCard(t.id, t.name) } }
                if (shown.isEmpty()) item { EmptyView(if (filter == "live") "الان بازی زنده‌ای در جریان نیست" else if (filter == "fav") "هنوز تیم یا لیگی را دنبال نکرده‌اید؛ روی ستاره بزنید" else "بازی‌ای در این روز نیست", Icons.Rounded.SportsSoccer) }
                items(shown, key = { it.first.id }) { (b, ms) ->
                    val isCollapsed = collapsed[b.id] == true && filter == "all"
                    LeagueCard(b, ms, isCollapsed, { collapsed[b.id] = !isCollapsed }, flash)
                }
            }
        }
    }
}

@Composable
private fun LeagueCard(b: LeagueBlock, ms: List<MatchItem>, isCollapsed: Boolean, toggle: () -> Unit, flash: Map<Int, Long>) {
    val nav = LocalNav.current
    val live = ms.count { it.live }
    Card(Modifier.animateContentSize()) {
        Row(Modifier.fillMaxWidth().clickable(onClick = toggle).padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            LeagueLogo(b.primaryId, 22.dp)
            Spacer(Modifier.width(10.dp))
            Row(Modifier.weight(1f).clickable { nav.league(b.primaryId) }, verticalAlignment = Alignment.CenterVertically) {
                Ltr(b.name, Modifier.weight(1f, fill = false), weight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                if (b.ccode.isNotEmpty() && b.ccode != "INT") Text("  ${b.ccode}", color = C.mute, style = MaterialTheme.typography.labelSmall)
            }
            if (live > 0) Row(Modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                LiveDot(size = 6.dp); Text("$live", color = C.live, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
            StarButton(Store.isLeague(b.primaryId), { Store.toggleLeague(b.primaryId, b.name) }, 18.dp)
            Icon(if (isCollapsed) Icons.Rounded.ExpandMore else Icons.Rounded.ExpandLess, null, tint = C.mute, modifier = Modifier.padding(end = 8.dp).size(20.dp))
        }
        AnimatedVisibility(!isCollapsed) {
            Column {
                for (m in ms) {
                    Divider()
                    MatchRow(m, { nav.match(m.id) }, flash = (flash[m.id] ?: 0) > System.currentTimeMillis())
                }
            }
        }
    }
}

@Composable
private fun DateStrip(selected: Int, onPick: (Int) -> Unit) {
    val days = (-7..7).toList()
    val state = rememberLazyListState(initialFirstVisibleItemIndex = (selected + 7 - 2).coerceAtLeast(0))
    LazyRow(state = state, contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(days) { d ->
            val ms = startOfDay(d)
            val on = d == selected
            Column(
                Modifier.clip(RoundedCornerShape(14.dp)).background(if (on) LocalAccent.current else C.card)
                    .clickable { onPick(d) }.padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val ink = if (on) MaterialTheme.colorScheme.onPrimary else C.text
                Text(dayName(ms), color = ink, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text(persianDate(ms, "d MMM"), color = if (on) ink.copy(alpha = 0.8f) else C.mute, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/** "My team" card: live score, next match or last result, painted in the club's colors. */
@Composable
fun MyTeamCard(teamId: Int, name: String) {
    val nav = LocalNav.current
    val l = rememberLoader(teamId, poll = { d -> if (d.s("state") == "live") 30_000L else 300_000L }) { Api.obj("/api/widget?team=$teamId", 20_000) }
    val club = Clubs.get(teamId)
    val c1 = club?.let { Color(it.c1) } ?: parseColor(Store.myTeam?.color) ?: C.card2
    val c2 = club?.let { Color(it.c2) } ?: C.card
    val ink = club?.let { Color(it.ink) } ?: if (lum(c1) > 0.6f) Color(0xFF0B1B3A) else Color.White
    val d = l.value
    val m = d?.o("match")
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(c1, lerpColor(c1, c2, 0.55f), C.card)))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(22.dp))
            .clickable { val id = m.i("id") ?: 0; if (id > 0) nav.match(id) else nav.team(teamId) }
            .padding(16.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Crest(teamId, 28.dp)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("تیم من", color = ink.copy(alpha = 0.75f), style = MaterialTheme.typography.labelSmall)
                    Text(club?.fa ?: name, color = ink, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                when (d?.s("state")) {
                    "live" -> Row(Modifier.clip(RoundedCornerShape(50)).background(C.live).padding(horizontal = 10.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("زنده  ", color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Ltr(cleanMin(m.s("minute")), color = Color.White, style = MaterialTheme.typography.labelMedium, weight = FontWeight.Bold)
                    }
                    "finished" -> Text("پایان بازی", color = ink.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium)
                    "upcoming" -> Text(countdown(m.l("kickoff") ?: 0), color = ink.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium)
                }
            }
            if (m != null) {
                Spacer(Modifier.height(14.dp))
                val h = m.o("home"); val a = m.o("away")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Crest(h.i("id") ?: 0, 40.dp); Ltr(h.s("name") ?: "", color = ink, style = MaterialTheme.typography.labelLarge)
                    }
                    if (d.s("state") == "upcoming") {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(timeOf(m.l("kickoff") ?: 0), color = ink, fontSize = 26.sp, fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineMedium)
                            Text(shortDate(m.l("kickoff") ?: 0), color = ink.copy(alpha = 0.75f), style = MaterialTheme.typography.labelSmall)
                        }
                    } else ScoreText(h.i("score"), a.i("score"), 30, ink, FontWeight.Black)
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Crest(a.i("id") ?: 0, 40.dp); Ltr(a.s("name") ?: "", color = ink, style = MaterialTheme.typography.labelLarge)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Ltr(m.s("league") ?: "", Modifier.fillMaxWidth(), color = ink.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall,
                    align = androidx.compose.ui.text.style.TextAlign.Center)
            } else if (d != null) {
                Spacer(Modifier.height(8.dp))
                Text("فعلاً بازی‌ای برنامه‌ریزی نشده", color = ink.copy(alpha = 0.8f))
            }
        }
    }
}

private fun cleanMin(s: String?) = app.goallive.live.data.cleanMinute(s).let { if (it == "HT") "بین دو نیمه" else it }
