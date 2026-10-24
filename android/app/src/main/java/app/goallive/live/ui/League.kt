package app.goallive.live.ui

import app.goallive.live.L

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.runtime.*
import androidx.compose.ui.draw.*
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
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
import app.goallive.live.data.parseMatch
import app.goallive.live.data.s
import app.goallive.live.data.strs
import app.goallive.live.data.tr
import org.json.JSONArray
import org.json.JSONObject

/* ---------- standings (".tbl") ---------- */

/** Accepts tltable's array, a league's "table" array, or a single table object. */
@Composable
fun TableView(data: Any?, highlight: List<Int> = emptyList()) {
    val root: JSONObject? = when (data) {
        is JSONArray -> data.optJSONObject(0)?.let { it.o("data") ?: it }
        is JSONObject -> data.o("data") ?: data
        else -> null
    }
    val tables = root?.a("tables").objs().ifEmpty { listOfNotNull(root) }.filter { it.o("table") != null }
        .sortedBy { t -> if (highlight.isNotEmpty() && t.o("table").a("all").objs().any { (it.i("id") ?: 0) in highlight }) 0 else 1 }
    if (tables.isEmpty()) { EmptyView(L("جدولی وجود ندارد"), Ic.list); return }
    val kinds = listOf("all" to L("همه"), "home" to L("خانه"), "away" to L("خارج"), "xg" to "xG").filter { (k, _) -> tables.any { (it.o("table").a(k)?.length() ?: 0) > 0 } }
    var kind by rememberSaveable { mutableStateOf("all") }
    if (kinds.none { it.first == kind }) kind = "all"
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (kinds.size > 1) Seg(kinds, kind) { kind = it }
        tables.forEach { t ->
            Card {
                if (tables.size > 1) CardTitle(t.s("leagueName") ?: "")
                TableRows(t.o("table").a(kind).objs(), kind, t.a("legend").objs(), highlight)
                val legend = t.a("legend").objs()
                if (legend.isNotEmpty() && kind == "all") Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    legend.forEach { lg ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(parseColor(lg.s("color")) ?: C.mute))
                            T(tr(lg.s("title")), 11, color = C.mute)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TableRows(rows: List<JSONObject>, kind: String, legend: List<JSONObject>, highlight: List<Int>) {
    val nav = LocalNav.current
    val accent = LocalAccent.current
    val xg = kind == "xg"
    val cols: List<Pair<String, Dp>> = if (xg) listOf("xG" to 40.dp, "xGA" to 40.dp, "xPts" to 40.dp, L("امتیاز") to 40.dp)
    else listOf(L("بازی") to 34.dp, L("گل") to 48.dp, L("تفاضل") to 40.dp, L("امتیاز") to 40.dp)
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        T("#", 11, FontWeight.Bold, C.mute, Modifier.width(30.dp), align = TextAlign.Center)
        T(L("تیم"), 11, FontWeight.Bold, C.mute, Modifier.weight(1f).padding(start = 4.dp))
        cols.forEach { (c, w) -> T(c, 11, FontWeight.Bold, C.mute, Modifier.width(w), align = TextAlign.Center) }
    }
    Divider()
    rows.forEachIndexed { i, r ->
        val id = r.i("id") ?: 0
        val hl = id in highlight
        val color = legend.firstOrNull { lg -> lg.a("indices")?.let { arr -> (0 until arr.length()).any { arr.optInt(it) == i } } == true }?.s("color")
        if (i > 0) Divider()
        Row(
            Modifier.fillMaxWidth().background(if (hl) accent.copy(alpha = 0.14f) else Color.Transparent)
                .clickable { nav.team(id) }.padding(horizontal = 4.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(30.dp).height(22.dp), contentAlignment = Alignment.Center) {
                if (color != null && kind == "all") Box(Modifier.align(Alignment.CenterStart).width(3.dp).fillMaxSize().clip(RoundedCornerShape(3.dp)).background(parseColor(color) ?: C.mute))
                T("${if (xg) r.i("xPosition") ?: r.i("idx") ?: (i + 1) else r.i("idx") ?: (i + 1)}", 13, FontWeight.ExtraBold)
            }
            Row(Modifier.weight(1f).padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Crest(id, 24.dp)
                TeamText(id, r.s("name") ?: "", Modifier.weight(1f, false), 15, FontWeight.Bold, if (hl) accent else C.ink, short = r.s("shortName"))
                if (r.optBoolean("ongoing")) LiveDot()
            }
            if (xg) {
                listOf(dec(r.d("xg"), 1), dec(r.d("xgConceded"), 1), dec(r.d("xPoints"), 1)).forEach { v -> T(fa(v), 13, modifier = Modifier.width(40.dp), align = TextAlign.Center) }
                T("${r.i("pts") ?: 0}", 13, color = C.mute, modifier = Modifier.width(40.dp), align = TextAlign.Center)
            } else {
                T("${r.i("played") ?: 0}", 13, modifier = Modifier.width(34.dp), align = TextAlign.Center)
                Ltr(fa(r.s("scoresStr") ?: ""), Modifier.width(48.dp), style = vz(13.sp), align = TextAlign.Center)
                val diff = r.i("goalConDiff") ?: 0
                Ltr(fa(if (diff > 0) "+$diff" else "$diff"), Modifier.width(40.dp), style = vz(13.sp), color = if (diff > 0) C.win else if (diff < 0) C.loss else C.ink, align = TextAlign.Center)
                T("${r.i("pts") ?: 0}", 13, FontWeight.Black, modifier = Modifier.width(40.dp), align = TextAlign.Center)
            }
        }
    }
}

/* ---------- league page ---------- */

@Composable
fun LeagueScreen(id: Int) {
    var season by rememberSaveable { mutableStateOf("") }
    val l = rememberLoader(id, season) { Api.fm("leagues", mapOf("id" to id, "ccode3" to "IRN", "season" to season.ifEmpty { null }), 120_000) }
    Refreshable(l) { LoadBox(l) { d -> LeagueBody(id, d) { season = it } } }
}

@Composable
private fun LeagueBody(id: Int, d: JSONObject, onSeason: (String) -> Unit) {
    val det = d.o("details")
    val name = det.s("name") ?: ""
    val tabs = buildList {
        if ((d.a("table")?.length() ?: 0) > 0) add("table" to L("جدول"))
        if ((d.o("fixtures").a("allMatches")?.length() ?: 0) > 0) add("fixtures" to L("بازی‌ها"))
        if ((d.o("stats").a("players")?.length() ?: 0) > 0 || (d.o("stats").a("teams")?.length() ?: 0) > 0) add("stats" to L("آمار"))
        if ((d.a("seasons")?.length() ?: 0) > 0) add("seasons" to L("قهرمانان"))
    }
    var tab by rememberSaveable { mutableStateOf(tabs.firstOrNull()?.first ?: "fixtures") }
    if (tabs.isNotEmpty() && tabs.none { it.first == tab }) tab = tabs.first().first
    val seasons = d.a("allAvailableSeasons").strs()
    var pick by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = BottomSpace)) {
        item {
            PageHero(parseColor(det.s("leagueColor"))?.let { if (lum(it) > 0.6f) darken(it, 0.55f) else it } ?: Color(0xFF1A2A40), "dots") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    LeagueLogo(id, 76.dp)
                    Column(Modifier.weight(1f)) {
                        Ltr(name, style = lat(28.sp, FontWeight.ExtraBold), color = Color.White, maxLines = 2)
                        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            det.s("country")?.takeIf { it != "INT" }?.let { T("$it ·", 13, FontWeight.SemiBold, Color.White.copy(alpha = 0.85f)) }
                            T(L("فصل"), 13, FontWeight.SemiBold, Color.White.copy(alpha = 0.85f))
                            Box {
                                Row(Modifier.height(30.dp).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.3f)).border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(50))
                                    .clickable(enabled = seasons.size > 1) { pick = true }.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    T(fa(det.s("selectedSeason") ?: ""), 13, FontWeight.Bold, Color.White)
                                    if (seasons.size > 1) Icon(Ic.down, null, Modifier.size(14.dp), tint = Color.White)
                                }
                                DropdownMenu(pick, { pick = false }, containerColor = C.cardSolid) {
                                    seasons.take(30).forEach { s -> DropdownMenuItem(text = { T(fa(s), 14) }, onClick = { pick = false; onSeason(s) }) }
                                }
                            }
                        }
                    }
                    Box(Modifier.align(Alignment.Top)) {
                        StarButton(Store.isLeague(id), { Store.toggleLeague(id, name) }, 20.dp, Color.White, 40.dp)
                    }
                }
            }
        }
        if (tabs.isNotEmpty()) stickyTabs(tabs, tab) { tab = it }
        item {
            Box(Modifier.padding(horizontal = 14.dp)) {
                when (tab) {
                    "table" -> TableView(d.a("table"))
                    "fixtures" -> LeagueFixtures(d)
                    "stats" -> Leaders(d.o("stats"))
                    "seasons" -> Winners(d)
                }
            }
        }
    }
}

@Composable
private fun LeagueFixtures(d: JSONObject) {
    val nav = LocalNav.current
    val all = d.o("fixtures").a("allMatches").objs()
    fun roundOf(m: JSONObject) = m.s("round") ?: m.s("roundName") ?: ""
    val rounds = all.map(::roundOf).distinct()
    val firstIdx = d.o("fixtures").o("firstUnplayedMatch").i("firstUnplayedMatchIndex")
    val first = firstIdx?.let { all.getOrNull(it) } ?: all.firstOrNull { !it.o("status").b("finished") } ?: all.lastOrNull()
    var cur by rememberSaveable { mutableStateOf(first?.let(::roundOf) ?: rounds.firstOrNull() ?: "") }
    val i = rounds.indexOf(cur)
    Card {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            // RTL: the right-hand arrow goes back a round
            IconBtn(Ic.back, L("هفته قبل"), if (i > 0) C.ink else C.faint, size = 18.dp) { if (i > 0) cur = rounds[i - 1] }
            T(if (cur.isNotEmpty() && cur.all(Char::isDigit)) L("هفته {0}", cur) else cur.ifEmpty { L("بازی‌ها") }, 14, FontWeight.ExtraBold, modifier = Modifier.weight(1f), align = TextAlign.Center)
            IconBtn(Ic.chev, L("هفته بعد"), if (i < rounds.size - 1) C.ink else C.faint, size = 18.dp) { if (i < rounds.size - 1) cur = rounds[i + 1] }
        }
        all.filter { roundOf(it) == cur }.map { parseMatch(it) }.groupBy { dayKey(it.time) }.forEach { (_, ms) ->
            SubHead(persianDate(ms.first().time, "EEEE d MMMM"))
            ms.forEach { m -> MatchRow(m, { nav.match(m.id) }) }
        }
    }
}

@Composable
private fun Leaders(stats: JSONObject?) {
    val nav = LocalNav.current
    Column {
        for ((title, arr, isTeam) in listOf(Triple(L("بازیکنان"), stats.a("players").objs(), false), Triple(L("تیم‌ها"), stats.a("teams").objs(), true))) {
            if (arr.isEmpty()) continue
            SectionTitle(title)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                arr.forEach { b ->
                    Card {
                        CardTitle(tr(b.s("header")))
                        b.a("topThree").objs().forEachIndexed { i, p ->
                            ListRow(first = i == 0, onClick = { if (isTeam) nav.team(p.i("teamId") ?: 0) else nav.player(p.i("id") ?: 0) }) {
                                T("${p.i("rank") ?: (i + 1)}", 12, FontWeight.ExtraBold, C.mute, Modifier.width(24.dp), align = TextAlign.Center)
                                if (isTeam) Crest(p.i("teamId") ?: 0, 34.dp) else Face(p.i("id") ?: 0)
                                Column(Modifier.weight(1f)) {
                                    Ltr(p.s("name") ?: "")
                                    if (!isTeam) T(p.s("teamName") ?: "", 12, color = C.mute, maxLines = 1)
                                }
                                val st = p.o("stat")
                                val v = st.d("value") ?: p.d("value")
                                Ltr(when {
                                    v == null -> "–"
                                    st.s("format") == "percent" -> L("{0}٪", v.toInt())
                                    st.s("name") == "squad_value_team" -> money(v.toLong())
                                    v % 1 != 0.0 -> dec(v)
                                    else -> "${v.toInt()}"
                                }.let { fa(it) }, style = vz(15.sp, FontWeight.Black))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Winners(d: JSONObject) {
    val nav = LocalNav.current
    Card {
        d.a("seasons").objs().forEachIndexed { i, s ->
            ListRow(first = i == 0) {
                T(fa(s.s("seasonName") ?: ""), 12, FontWeight.ExtraBold, C.mute)
                val w = s.o("winner")
                Row(Modifier.weight(1f).clickable(enabled = w != null) { nav.team(w.i("id") ?: 0) }, verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (w != null) { Icon(Ic.trophy, null, Modifier.size(16.dp), tint = C.gold); Crest(w.i("id") ?: 0); Ltr(w.s("name") ?: "", Modifier.weight(1f, false)) }
                }
                s.o("loser")?.let { lo ->
                    Row(Modifier.clickable { nav.team(lo.i("id") ?: 0) }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Crest(lo.i("id") ?: 0, 18.dp); Ltr(lo.s("name") ?: "", style = lat(13.sp, FontWeight.SemiBold), color = C.mute)
                    }
                }
            }
        }
    }
}

/* ---------- leagues directory ---------- */

@Composable
fun LeaguesScreen() {
    val nav = LocalNav.current
    val params = mapOf("locale" to "en", "country" to "IRN")
    val l = rememberLoader("all", peek = { Api.peek(Api.fmUrl("allLeagues", params)) as? JSONObject }) { Api.fm("allLeagues", params, 3_600_000) }
    var q by rememberSaveable { mutableStateOf("") }
    val open = remember { mutableStateMapOf<String, Boolean>() }
    val row: @Composable (JSONObject, Boolean) -> Unit = { lg, first ->
        val id = lg.i("id") ?: 0
        val name = lg.s("localizedName") ?: lg.s("name") ?: ""
        ListRow(first = first, onClick = { nav.league(id) }, pad = PaddingValues(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)) {
            LeagueLogo(id, 24.dp)
            Ltr(name, Modifier.weight(1f), style = vz(14.sp, FontWeight.Medium))
            StarButton(Store.isLeague(id), { Store.toggleLeague(id, name) })
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = BottomSpace)) {
        item { SearchField(q, L("جستجوی لیگ یا کشور…")) { q = it } }
        val d = l.value
        if (d == null) { item { if (l.state is Load.Err) ErrorView({ l.state = Load.Loading; l.reload() }) else Skeleton(8) }; return@LazyColumn }
        val popular = d.a("popular").objs()
        val intl = d.a("international").objs().flatMap { it.a("leagues").objs() }
        val countries = d.a("countries").objs()
        val query = q.trim().lowercase()
        if (query.isNotEmpty()) {
            val hits = (popular.map { it to "" } + intl.map { it to "" } + countries.flatMap { c -> c.a("leagues").objs().map { it to (c.s("name") ?: "") } })
                .distinctBy { it.first.i("id") }
                .filter { (lg, cn) -> (lg.s("localizedName") ?: lg.s("name") ?: "").lowercase().contains(query) || cn.lowercase().contains(query) }
                .take(80)
            item { if (hits.isEmpty()) EmptyView(L("چیزی پیدا نشد"), Ic.search) else Card { hits.forEachIndexed { i, (lg, _) -> row(lg, i == 0) } } }
            return@LazyColumn
        }
        if (Store.leagues.isNotEmpty()) item {
            SectionTitle(L("لیگ‌های دنبال‌شده"))
            Card { Store.leagues.entries.forEachIndexed { i, (id, name) -> row(JSONObject().put("id", id).put("name", name), i == 0) } }
        }
        val iran = countries.firstOrNull { it.s("ccode") == "IRN" }?.a("leagues")?.objs().orEmpty()
            // the qualifying round of the Persian Gulf league ("پرسیان گلف لیگ حرفه‌ای مقدماتی") has no data worth a row
            .filterNot { (it.s("name") ?: "").contains("Qualification", true) }
        if (iran.isNotEmpty()) item { SectionTitle(L("ایران")); Card { iran.forEachIndexed { i, it -> row(it, i == 0) } } }
        item { SectionTitle(L("محبوب")); Card { popular.forEachIndexed { i, it -> row(it, i == 0) } } }
        item { SectionTitle(L("بین‌المللی")); Card { intl.forEachIndexed { i, it -> row(it, i == 0) } } }
        item { SectionTitle(L("کشورها")) }
        item {
            Card {
                countries.forEachIndexed { i, c ->
                    val key = c.s("ccode") ?: "$i"
                    val isOpen = open[key] == true
                    ListRow(first = i == 0, onClick = { open[key] = !isOpen }) {
                        coil.compose.AsyncImage(flagLogo(key), null, Modifier.size(24.dp))
                        Ltr(c.s("localizedName") ?: c.s("name") ?: "", Modifier.weight(1f), style = vz(14.sp, FontWeight.Medium))
                        T("${c.a("leagues")?.length() ?: 0}", 12, color = C.mute)
                        Icon(Ic.down, null, Modifier.size(16.dp).rotate(if (isOpen) 180f else 0f), tint = C.mute)
                    }
                    if (isOpen) c.a("leagues").objs().forEach { row(it, false) }
                }
            }
        }
    }
}

/** ".search": a big rounded input with the magnifier on the right and an accent focus ring. */
@Composable
fun SearchField(value: String, hint: String, autoFocus: Boolean = false, onChange: (String) -> Unit) {
    val accent = LocalAccent.current
    val focus = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp).height(52.dp)
            .then(if (focused) Modifier.glow(accent.copy(alpha = 0.14f), 4.dp, shape) else Modifier)
            .clip(shape).background(C.card).border(1.dp, if (focused) accent else C.line2, shape).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Ic.search, null, Modifier.size(20.dp), tint = C.mute)
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) T(hint, 15, color = C.mute, maxLines = 1)
            BasicTextField(
                value, onChange, Modifier.fillMaxWidth().focusRequester(focus).onFocusChanged { focused = it.isFocused }, singleLine = true,
                textStyle = vz(15.sp).copy(color = C.ink), cursorBrush = SolidColor(accent),
            )
        }
        if (value.isNotEmpty()) Icon(Ic.x, L("پاک کردن"), Modifier.size(18.dp).clickable { onChange("") }, tint = C.mute)
    }
    if (autoFocus) LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}
