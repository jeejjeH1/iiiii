package app.goallive.live.ui

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.goallive.live.data.Api
import app.goallive.live.data.Club
import app.goallive.live.data.Clubs
import app.goallive.live.data.MyTeam
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
import app.goallive.live.data.tr
import org.json.JSONObject

@Composable
fun TeamScreen(id: Int) {
    val nav = LocalNav.current
    val l = rememberLoader(id) { Api.fm("teams", mapOf("id" to id, "ccode3" to "IRN"), 120_000) }
    val club = Clubs.get(id)
    val color = club?.let { Color(it.c1) } ?: parseColor(l.value?.path("overview.teamColors")?.s("darkMode")) ?: parseColor(l.value?.path("overview.teamColors")?.s("color"))
    // the team page takes the club's color as its accent
    CompositionLocalProvider(LocalAccent provides (if (color != null) accentFrom(club?.accent?.let { Color(it) } ?: color) else LocalAccent.current)) {
        Column(Modifier.fillMaxSize()) {
            TopBar("", { nav.back() }) {
                val name = l.value?.o("details")?.s("name") ?: ""
                StarButton(Store.isTeam(id), { Store.toggleTeam(id, name) })
            }
            LoadBox(l) { d -> TeamBody(id, d, club, color ?: C.card2) }
        }
    }
}

@Composable
private fun TeamBody(id: Int, d: JSONObject, club: Club?, color: Color) {
    val det = d.o("details")
    val name = det.s("name") ?: ""
    val tabs = buildList {
        add("overview" to "نمای کلی")
        if (club != null) add("story" to "تاریخچه")
        if ((d.path("fixtures.allFixtures").a("fixtures")?.length() ?: 0) > 0) add("fixtures" to "بازی‌ها")
        if ((d.a("table")?.length() ?: 0) > 0) add("table" to "جدول")
        if ((d.o("squad").a("squad")?.length() ?: 0) > 0) add("squad" to "بازیکنان")
        if ((d.o("history").a("trophyList")?.length() ?: 0) > 0) add("trophies" to "افتخارات")
    }
    var tab by rememberSaveable { mutableStateOf("overview") }
    val ink = club?.let { Color(it.ink) } ?: if (lum(color) > 0.6f) Color(0xFF0B1B3A) else Color.White
    LazyColumn(contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp))
                    .background(Brush.linearGradient(listOf(color, lerpColor(color, club?.let { Color(it.c2) } ?: C.card, 0.5f), C.card)))
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(26.dp)).padding(18.dp),
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(78.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.9f)).padding(10.dp)) { Crest(id, 58.dp, large = true) }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(club?.fa ?: name, color = ink, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (club != null) Ltr(name, color = ink.copy(alpha = 0.75f), style = MaterialTheme.typography.labelLarge)
                            Text(club?.nick ?: (det.s("country") ?: ""), color = ink.copy(alpha = 0.75f), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    val mine = Store.myTeam?.id == id
                    Pill(if (mine) "تیم من" else "انتخاب به‌عنوان تیم من", if (mine) C.gold else ink, filled = mine,
                        icon = if (mine) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        onClick = {
                            if (mine) Store.setMyTeam(null)
                            else Store.setMyTeam(MyTeam(id, name, d.path("overview.teamColors").s("darkMode") ?: d.path("overview.teamColors").s("color")))
                        })
                }
            }
        }
        item { Seg(tabs, tab) { tab = it } }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (tab) {
                    "overview" -> Overview(id, d)
                    "story" -> club?.let { Story(it, d) }
                    "fixtures" -> TeamFixtures(d)
                    "table" -> TableView(d.a("table"), listOf(id))
                    "squad" -> Squad(d)
                    "trophies" -> Trophies(d)
                }
            }
        }
    }
}

@Composable
private fun Overview(id: Int, d: JSONObject) {
    val nav = LocalNav.current
    val ov = d.o("overview")
    for ((key, title) in listOf("nextMatch" to "بازی بعدی", "lastMatch" to "آخرین بازی")) {
        val j = ov.o(key) ?: continue
        val m = parseMatch(j)
        Card {
            CardTitle(title) { Ltr(j.o("tournament").s("name") ?: "", color = C.mute, style = MaterialTheme.typography.labelSmall) }
            Row(Modifier.fillMaxWidth().clickable { nav.match(m.id) }.padding(start = 10.dp, end = 10.dp, bottom = 16.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) { Crest(m.home.id, 44.dp); Ltr(m.home.name, style = MaterialTheme.typography.labelLarge) }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (m.started || m.finished) ScoreText(m.home.score, m.away.score, 26)
                    else Text(timeOf(m.time), fontSize = 24.sp, fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineSmall)
                    Text(shortDate(m.time), color = C.mute, style = MaterialTheme.typography.labelSmall)
                    if (!m.started && !m.finished) Text(countdown(m.time), color = LocalAccent.current, style = MaterialTheme.typography.labelSmall)
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) { Crest(m.away.id, 44.dp); Ltr(m.away.name, style = MaterialTheme.typography.labelLarge) }
            }
        }
    }
    ov.a("teamForm")?.takeIf { it.length() > 0 }?.let { f ->
        Card { Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Text("فرم اخیر", Modifier.weight(1f), fontWeight = FontWeight.Bold); FormChips(f) } }
    }
    val tp = ov.o("topPlayers")
    for ((key, title) in listOf("byRating" to "بهترین نمره", "byGoals" to "گلزنان", "byAssists" to "پاس گل")) {
        val ps = tp.o(key).a("players").objs().take(3)
        if (ps.isEmpty()) continue
        Card {
            CardTitle(title)
            ps.forEach { p ->
                PersonRow(p.i("id") ?: 0, p.s("name") ?: "", "", trailing = {
                    if (key == "byRating") Rating(p.d("value")) else Text("${p.d("value")?.toInt() ?: 0}", fontWeight = FontWeight.ExtraBold)
                }) { nav.player(p.i("id") ?: 0) }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
    // the team's position with its neighbours
    val t = d.a("table")?.optJSONObject(0)?.o("data")
    val rows = (t.o("table").a("all").objs().ifEmpty {
        t.a("tables").objs().firstOrNull { tb -> tb.o("table").a("all").objs().any { it.i("id") == id } }?.o("table").a("all").objs()
    })
    val i = rows.indexOfFirst { it.i("id") == id }
    if (i >= 0) Card {
        CardTitle(t.s("leagueName") ?: "") { Pill("جدول کامل", onClick = { nav.league(t.i("leagueId") ?: 0) }) }
        TableRows(rows.subList((i - 2).coerceAtLeast(0), (i + 3).coerceAtMost(rows.size)), "all", emptyList(), listOf(id))
        Spacer(Modifier.height(6.dp))
    }
    ov.path("venue.widget")?.let { v ->
        Card {
            CardTitle("ورزشگاه")
            InfoLine(Icons.Rounded.Place, v.s("city") ?: "", v.s("name") ?: "")
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun Story(c: Club, d: JSONObject) {
    val trophies = d.o("history").a("trophyList").objs().filter { (it.i("won") ?: 0) > 0 }.sortedByDescending { it.i("won") ?: 0 }.take(6)
    Card(pad = PaddingValues(16.dp)) {
        Text(c.motto, color = LocalAccent.current, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(10.dp))
        Text(c.story, style = MaterialTheme.typography.bodyLarge, lineHeight = 28.sp)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Fact("تأسیس", "${c.founded}", Modifier.weight(1f))
            Fact("شهر", c.city, Modifier.weight(1f))
            Fact("ورزشگاه", c.stadium, Modifier.weight(1.4f))
        }
    }
    if (trophies.isNotEmpty()) Card {
        CardTitle("ویترین افتخارات")
        trophies.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
                row.forEach { t ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.EmojiEvents, null, tint = C.gold, modifier = Modifier.size(30.dp))
                        Text("${t.i("won")}", fontSize = 24.sp, fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineSmall)
                        Ltr(t.s("name") ?: "", color = C.mute, style = MaterialTheme.typography.labelSmall, maxLines = 2, align = TextAlign.Center)
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Spacer(Modifier.height(10.dp))
    }
    Card {
        CardTitle("لحظه‌های تاریخی")
        c.years.forEach { (y, t) ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.clip(RoundedCornerShape(10.dp)).background(LocalAccent.current.copy(alpha = 0.18f)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                    Text("$y", color = LocalAccent.current, fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.width(12.dp))
                Text(t, Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(8.dp))
    }
    Card {
        CardTitle("اسطوره‌ها")
        Text(c.legends.joinToString(" · "), Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), lineHeight = 26.sp)
    }
}

@Composable
private fun Fact(k: String, v: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(C.card2).padding(10.dp)) {
        Text(k, color = C.mute, style = MaterialTheme.typography.labelSmall)
        Text(v, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun TeamFixtures(d: JSONObject) {
    val nav = LocalNav.current
    val all = d.path("fixtures.allFixtures").a("fixtures").objs()
    val next = all.indexOfFirst { !it.o("status").b("finished") && !it.o("status").b("cancelled") }
    val past = if (next < 0) all else all.subList(0, next)
    val upcoming = if (next < 0) emptyList() else all.subList(next, all.size)
    if (upcoming.isNotEmpty()) {
        SectionTitle("بازی‌های پیش رو")
        Card { upcoming.forEachIndexed { i, j -> if (i > 0) Divider(); val m = parseMatch(j); MatchRow(m, { nav.match(m.id) }, showDate = true, sub = j.o("tournament").s("name")) } }
    }
    if (past.isNotEmpty()) {
        SectionTitle("نتایج")
        Card { past.reversed().forEachIndexed { i, j -> if (i > 0) Divider(); val m = parseMatch(j); MatchRow(m, { nav.match(m.id) }, showDate = true, sub = j.o("tournament").s("name")) } }
    }
}

@Composable
private fun Squad(d: JSONObject) {
    val nav = LocalNav.current
    d.o("squad").a("squad").objs().forEach { g ->
        SectionTitle(tr(g.s("title")))
        Card {
            g.a("members").objs().forEach { p ->
                val meta = listOfNotNull(p.s("shirtNumber")?.let { "#$it" }, p.s("cname"), p.i("age")?.let { "$it سال" }).joinToString(" · ")
                PersonRow(p.i("id") ?: 0, p.s("name") ?: "", meta, trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (p.o("injury") != null) Icon(Icons.Rounded.LocalHospital, null, tint = C.live, modifier = Modifier.size(16.dp))
                        if ((p.i("goals") ?: 0) > 0) Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${p.i("goals")}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                            Icon(Icons.Rounded.SportsSoccer, null, Modifier.size(13.dp), tint = C.mute)
                        }
                        Rating(p.d("rating"))
                    }
                }) { nav.player(p.i("id") ?: 0) }
            }
        }
    }
}

@Composable
private fun Trophies(d: JSONObject) {
    val nav = LocalNav.current
    Card {
        d.o("history").a("trophyList").objs().forEachIndexed { i, t ->
            if (i > 0) Divider()
            Row(Modifier.fillMaxWidth().clickable { nav.league(t.i("leagueId") ?: 0) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                LeagueLogo(t.i("leagueId") ?: 0, 30.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Ltr(t.s("name") ?: "", weight = FontWeight.Bold)
                    val seasons = (t.s("seasonsWon") ?: "").split(',').filter { it.isNotBlank() }
                    if (seasons.isNotEmpty()) Ltr(seasons.take(5).joinToString(", ") + if (seasons.size > 5) " …" else "", color = C.mute, style = MaterialTheme.typography.labelSmall)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.EmojiEvents, null, tint = C.gold, modifier = Modifier.size(18.dp))
                        Text(" ${t.i("won") ?: 0}", color = C.gold, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                    }
                    if ((t.i("runnerup") ?: 0) > 0) Text("نایب‌قهرمان ${t.i("runnerup")}", color = C.mute, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
