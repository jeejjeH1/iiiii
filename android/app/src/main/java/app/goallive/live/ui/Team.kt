package app.goallive.live.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.runtime.*
import androidx.compose.ui.draw.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
    val l = rememberLoader(id) { Api.fm("teams", mapOf("id" to id, "ccode3" to "IRN"), 120_000) }
    Refreshable(l) { LoadBox(l) { d -> TeamBody(id, d) } }
}

@Composable
private fun TeamBody(id: Int, d: JSONObject) {
    val det = d.o("details")
    val name = det.s("name") ?: ""
    val club = Clubs.get(id)
    val color = club?.let { Color(it.c1) } ?: parseColor(d.path("overview.teamColors").s("darkMode")) ?: parseColor(d.path("overview.teamColors").s("color")) ?: Color(0xFF1A2A40)
    val ink = club?.let { if (lum(Color(it.ink)) < 0.3f) Color(it.ink) else Color.White } ?: if (lum(color) > 0.6f) Color(0xFF0B1B3A) else Color.White
    val league = d.a("table")?.optJSONObject(0)?.o("data")
    val tabs = buildList {
        add("overview" to L("نمای کلی"))
        if (club != null) add("story" to L("تاریخچه"))
        if ((d.path("fixtures.allFixtures").a("fixtures")?.length() ?: 0) > 0) add("fixtures" to L("بازی‌ها"))
        if ((d.a("table")?.length() ?: 0) > 0) add("table" to L("جدول"))
        if ((d.o("squad").a("squad")?.length() ?: 0) > 0) add("squad" to L("بازیکنان"))
        if ((d.o("history").a("trophyList")?.length() ?: 0) > 0) add("trophies" to L("افتخارات"))
    }
    var tab by rememberSaveable { mutableStateOf("overview") }
    if (tabs.none { it.first == tab }) tab = "overview"
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = BottomSpace)) {
        item {
            PageHero(color, club?.pattern) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Crest(id, 76.dp, large = true)
                    Column(Modifier.weight(1f)) {
                        if (club != null) T(club.fa, 22, FontWeight.Black, ink) else Ltr(name, style = lat(26.sp, FontWeight.ExtraBold), color = ink, maxLines = 2)
                        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (club != null) { Ltr(name, style = lat(15.sp), color = ink.copy(alpha = 0.85f)); T("· ${club.nick}", 13, FontWeight.SemiBold, ink.copy(alpha = 0.85f), maxLines = 1) }
                            else det.s("country")?.let { T(it, 13, FontWeight.SemiBold, ink.copy(alpha = 0.85f)) }
                        }
                        league?.s("leagueName")?.let { ln ->
                            val nav = LocalNav.current
                            Ltr(ln, Modifier.padding(top = 2.dp).clickable { nav.league(league.i("leagueId") ?: 0) }, style = vz(13.sp, FontWeight.SemiBold), color = ink.copy(alpha = 0.85f))
                        }
                        val mine = Store.myTeam?.id == id
                        Row(Modifier.padding(top = 10.dp).height(28.dp).clip(RoundedCornerShape(50)).background(if (mine) C.gold else Color.Black.copy(alpha = 0.25f))
                            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(50))
                            .clickable { if (mine) Store.pickMyTeam(null) else Store.pickMyTeam(MyTeam(id, name, d.path("overview.teamColors").s("darkMode") ?: d.path("overview.teamColors").s("color"))) }
                            .padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Icon(if (mine) Ic.starOn else Ic.star, null, Modifier.size(14.dp), tint = if (mine) Color(0xFF2A1A00) else Color.White)
                            T(if (mine) L("تیم من") else L("انتخاب به‌عنوان تیم من"), 12, FontWeight.ExtraBold, if (mine) Color(0xFF2A1A00) else Color.White)
                        }
                    }
                    Box(Modifier.align(Alignment.Top).clip(RoundedCornerShape(13.dp)).background(Color.Black.copy(alpha = 0.22f))) {
                        StarButton(Store.isTeam(id), { Store.toggleTeam(id, name) }, 20.dp, ink, 40.dp)
                    }
                }
            }
        }
        stickyTabs(tabs, tab) { tab = it }
        item {
            Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (tab) {
                    "overview" -> Overview(id, d)
                    "story" -> club?.let { Story(id, it, d) }
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
    for ((key, title) in listOf("nextMatch" to L("بازی بعدی"), "lastMatch" to L("آخرین بازی"))) {
        val j = ov.o(key) ?: continue
        val m = parseMatch(j)
        Card {
            CardTitle(title) { Ltr(j.o("tournament").s("name") ?: "", style = vz(12.sp), color = C.mute) }
            Row(Modifier.fillMaxWidth().clickable { nav.match(m.id) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Crest(m.home.id, 34.dp); TeamText(m.home.id, m.home.name, size = 15, align = TextAlign.Center)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (m.started || m.finished) ScoreText(m.home.score, m.away.score, 24) else T(timeOf(m.time), 24, FontWeight.Black)
                    T(persianDate(m.time, "EEEE d MMMM"), 12, color = C.mute)
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Crest(m.away.id, 34.dp); TeamText(m.away.id, m.away.name, size = 15, align = TextAlign.Center)
                }
            }
        }
    }
    ov.a("teamForm")?.takeIf { it.length() > 0 }?.let { f ->
        Card { ListRow(first = true) { T(L("فرم اخیر"), 14, FontWeight.Bold, modifier = Modifier.weight(1f)); FormChips(f) } }
    }
    val tp = ov.o("topPlayers")
    for ((key, title) in listOf("byRating" to L("بهترین نمره"), "byGoals" to L("گلزنان"), "byAssists" to L("پاس گل"))) {
        val ps = tp.o(key).a("players").objs().take(3)
        if (ps.isEmpty()) continue
        Card {
            CardTitle(title)
            ps.forEach { p ->
                PersonRow(p.i("id") ?: 0, p.s("name") ?: "", "", trailing = {
                    if (key == "byRating") Rating(p.d("value")) else T("${p.d("value")?.toInt() ?: 0}", 14, FontWeight.Black)
                }) { nav.player(p.i("id") ?: 0) }
            }
        }
    }
    // the team's position with its neighbours
    val t = d.a("table")?.optJSONObject(0)?.o("data")
    val rows = t.o("table").a("all").objs().ifEmpty {
        t.a("tables").objs().firstOrNull { tb -> tb.o("table").a("all").objs().any { it.i("id") == id } }?.o("table").a("all").objs()
    }
    val i = rows.indexOfFirst { it.i("id") == id }
    if (i >= 0) Card {
        CardTitle(t.s("leagueName") ?: "") { Pill(L("جدول کامل"), onClick = { nav.league(t.i("leagueId") ?: 0) }) }
        TableRows(rows.subList((i - 2).coerceAtLeast(0), (i + 3).coerceAtMost(rows.size)), "all", emptyList(), listOf(id))
    }
    ov.path("venue.widget")?.let { v ->
        Card {
            CardTitle(L("ورزشگاه"))
            Divider()
            Row(Modifier.fillMaxWidth().background(C.cardSolid).padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Ic.stadium, null, Modifier.size(18.dp), tint = LocalAccent.current)
                Column { T(L("نام"), 11, color = C.mute); Ltr(v.s("name") ?: "", style = vz(13.sp, FontWeight.Bold)) }
                Spacer(Modifier.weight(1f))
                Icon(Ic.pin, null, Modifier.size(18.dp), tint = LocalAccent.current)
                Column { T(L("شهر"), 11, color = C.mute); T(v.s("city") ?: "", 13, FontWeight.Bold) }
            }
        }
    }
}

/** ".story": the club history card in the club's colors. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Story(id: Int, c: Club, d: JSONObject) {
    val c1 = Color(c.c1)
    val c2 = Color(c.accent ?: c.c2)
    val dark = lum(Color(c.ink)) < 0.3f
    val ink = if (dark) Color(c.ink) else Color.White
    val chip = if (dark) Color.White.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.28f)
    val hi = if (dark) ink else c2
    val trophies = d.o("history").a("trophyList").objs().filter { (it.i("won") ?: 0) > 0 }.sortedByDescending { it.i("won") ?: 0 }.take(8)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).drawBehind {
            cssLinear(140f, 0f to c1, 0.7f to darken(c1, 0.4f), 1f to darken(c1, 0.4f))
            drawPattern(c.pattern, 0.13f)
        }.border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(22.dp)),
    ) {
        Row(Modifier.padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Crest(id, 64.dp, large = true)
            Column { T(c.fa, 22, FontWeight.Black, ink); T(c.nick, 12, FontWeight.Bold, ink.copy(alpha = 0.85f)) }
        }
        Row(Modifier.padding(horizontal = 18.dp, vertical = 8.dp).fillMaxWidth().height(IntrinsicSize.Min).clip(RoundedCornerShape(10.dp)).background(chip)) {
            Box(Modifier.width(3.dp).fillMaxHeight().background(c2))
            Ltr(c.motto, Modifier.padding(horizontal = 14.dp, vertical = 10.dp), style = vz(13.sp, FontWeight.ExtraBold), color = ink, maxLines = 3, align = TextAlign.Right)
        }
        T(c.story, 13, color = ink.copy(alpha = 0.95f), modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp))
        Row(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((v, k) in listOf("${c.founded}" to L("تأسیس"), c.city to L("شهر"), c.stadium to L("ورزشگاه"))) {
                Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(chip).border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(14.dp)).padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    T(v, if (v.length > 8) 13 else 20, FontWeight.Black, ink, maxLines = 2, align = TextAlign.Center)
                    T(k, 11, color = ink.copy(alpha = 0.8f))
                }
            }
        }
        if (trophies.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            trophies.forEach { t ->
                Column(Modifier.widthIn(min = 108.dp).clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(alpha = if (dark) 0.08f else 0.3f))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(14.dp)).padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    T("${t.i("won")}", 24, FontWeight.Black, if (dark) ink else C.gold)
                    Ltr(t.s("name") ?: "", style = vz(11.sp), color = ink.copy(alpha = 0.85f), maxLines = 2, align = TextAlign.Center)
                }
            }
        }
        FlowRow(Modifier.padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            c.legends.forEach { n ->
                Box(Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.14f)).padding(horizontal = 11.dp, vertical = 5.dp)) { T(n, 12, FontWeight.Bold, ink) }
            }
        }
        Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 18.dp).drawBehind {
            drawRect(Brush.verticalGradient(listOf(hi, Color.Transparent)), Offset(size.width - 5.dp.toPx(), 4.dp.toPx()), Size(2.dp.toPx(), size.height - 8.dp.toPx()))
        }) {
            c.years.forEach { (y, t) ->
                Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.padding(start = 1.dp).size(10.dp).clip(CircleShape).background(hi))
                    Spacer(Modifier.width(10.dp))
                    T("$y", 15, FontWeight.ExtraBold, hi)
                    Spacer(Modifier.width(8.dp))
                    T(t, 13, color = ink)
                }
            }
        }
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
        SectionTitle(L("بازی‌های پیش رو"))
        Card { upcoming.forEachIndexed { i, j -> val m = parseMatch(j); MatchRow(m, { nav.match(m.id) }, showDate = true, sub = j.o("tournament").s("name"), first = i == 0) } }
    }
    if (past.isNotEmpty()) {
        SectionTitle(L("نتایج"))
        Card { past.reversed().forEachIndexed { i, j -> val m = parseMatch(j); MatchRow(m, { nav.match(m.id) }, showDate = true, sub = j.o("tournament").s("name"), first = i == 0) } }
    }
}

@Composable
private fun Squad(d: JSONObject) {
    val nav = LocalNav.current
    d.o("squad").a("squad").objs().forEach { g ->
        SectionTitle(tr(g.s("title")))
        Card {
            g.a("members").objs().forEachIndexed { i, p ->
                val meta = listOfNotNull(p.s("shirtNumber")?.let { "#$it" }, p.s("cname"), p.i("age")?.let { L("{0} سال", it) }, p.o("injury")?.let { L("مصدوم") }).joinToString(" · ")
                PersonRow(p.i("id") ?: 0, p.s("name") ?: "", meta, first = i == 0, trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if ((p.i("goals") ?: 0) > 0) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            T("${p.i("goals")}", 12, FontWeight.Bold, C.mute); Icon(Ic.ball, null, Modifier.size(13.dp), tint = C.mute)
                        }
                        p.l("transferValue")?.takeIf { it > 0 }?.let { T(money(it), 12, color = C.mute) }
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
            ListRow(first = i == 0, onClick = { nav.league(t.i("leagueId") ?: 0) }) {
                LeagueLogo(t.i("leagueId") ?: 0, 34.dp)
                Column(Modifier.weight(1f)) {
                    Ltr(t.s("name") ?: "", style = vz(14.sp, FontWeight.Bold))
                    val seasons = (t.s("seasonsWon") ?: "").split(',').filter { it.isNotBlank() }
                    if (seasons.isNotEmpty()) Ltr(fa(seasons.take(6).joinToString(L("، "))) + if (seasons.size > 6) " …" else "", style = vz(12.sp), color = C.mute, maxLines = 2)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Ic.trophy, null, Modifier.size(16.dp), tint = C.gold); T("${t.i("won") ?: 0}", 16, FontWeight.Black, C.gold)
                    }
                    if ((t.i("runnerup") ?: 0) > 0) T(L("نایب‌قهرمان {0}", t.i("runnerup")), 12, color = C.mute)
                }
            }
        }
    }
}
