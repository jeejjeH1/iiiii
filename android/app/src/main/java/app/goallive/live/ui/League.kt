package app.goallive.live.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.goallive.live.data.Api
import app.goallive.live.data.Store
import app.goallive.live.data.a
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

/* ---------- standings ---------- */

/** Accepts tltable's array, a league's "table" array, or a single table object. */
@Composable
fun TableView(data: Any?, highlight: List<Int> = emptyList()) {
    val root: JSONObject? = when (data) {
        is JSONArray -> data.optJSONObject(0)?.let { it.o("data") ?: it }
        is JSONObject -> data.o("data") ?: data
        else -> null
    }
    if (root == null) { EmptyView("جدولی وجود ندارد"); return }
    val tables = root.a("tables").objs().ifEmpty { listOf(root) }.filter { it.o("table") != null }
        .sortedBy { t -> if (highlight.isNotEmpty() && t.o("table").a("all").objs().any { (it.i("id") ?: 0) in highlight }) 0 else 1 }
    if (tables.isEmpty()) { EmptyView("جدولی وجود ندارد"); return }
    val kinds = listOf("all" to "همه", "home" to "خانه", "away" to "خارج", "xg" to "xG").filter { (k, _) -> tables.any { (it.o("table").a(k)?.length() ?: 0) > 0 } }
    var kind by rememberSaveable { mutableStateOf("all") }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (kinds.size > 1) Seg(kinds, kind) { kind = it }
        tables.forEach { t ->
            Card {
                if (tables.size > 1) CardTitle(t.s("leagueName") ?: "")
                TableRows(t.o("table").a(kind).objs(), kind, t.a("legend").objs(), highlight)
                val legend = t.a("legend").objs()
                if (legend.isNotEmpty() && kind == "all") Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    legend.forEach { lg ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(parseColor(lg.s("color")) ?: C.mute))
                            Spacer(Modifier.width(6.dp))
                            Ltr(lg.s("title") ?: "", color = C.mute, style = MaterialTheme.typography.labelSmall)
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
    val xg = kind == "xg"
    val cols = if (xg) listOf("xG", "xGA", "xPts", "امتیاز") else listOf("بازی", "+/-", "گل", "امتیاز")
    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("#", Modifier.width(26.dp), color = C.mute, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
        Text("تیم", Modifier.weight(1f), color = C.mute, style = MaterialTheme.typography.labelSmall)
        cols.forEach { Text(it, Modifier.width(if (it == "گل") 48.dp else 38.dp), color = C.mute, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center) }
    }
    rows.forEachIndexed { i, r ->
        val id = r.i("id") ?: 0
        val hl = id in highlight
        val color = legend.firstOrNull { lg -> lg.a("indices")?.let { arr -> (0 until arr.length()).any { arr.optInt(it) == i } } == true }?.s("color")
        Row(
            Modifier.fillMaxWidth().background(if (hl) LocalAccent.current.copy(alpha = 0.14f) else Color.Transparent)
                .clickable { nav.team(id) }.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(Modifier.width(26.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                if (color != null && kind == "all") Box(Modifier.size(3.dp, 16.dp).clip(RoundedCornerShape(2.dp)).background(parseColor(color) ?: C.mute))
                Spacer(Modifier.width(3.dp))
                Text("${if (xg) r.i("xPosition") ?: r.i("idx") ?: (i + 1) else r.i("idx") ?: (i + 1)}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Crest(id, 20.dp)
                Spacer(Modifier.width(8.dp))
                Ltr(r.s("shortName") ?: r.s("name") ?: "", Modifier.weight(1f, false), weight = if (hl) FontWeight.Bold else FontWeight.Medium)
                if (r.optBoolean("ongoing")) { Spacer(Modifier.width(4.dp)); LiveDot(size = 6.dp) }
            }
            val vals = if (xg) listOf(dec(r.d("xg"), 1), dec(r.d("xgConceded"), 1), dec(r.d("xPoints"), 1), "${r.i("pts") ?: 0}")
            else listOf("${r.i("played") ?: 0}", (r.i("goalConDiff") ?: 0).let { if (it > 0) "+$it" else "$it" }, r.s("scoresStr") ?: "", "${r.i("pts") ?: 0}")
            vals.forEachIndexed { j, v ->
                val w: Dp = if (!xg && j == 2) 48.dp else 38.dp
                Ltr(v, Modifier.width(w), align = TextAlign.Center, style = MaterialTheme.typography.labelLarge,
                    weight = if (j == 3) FontWeight.ExtraBold else FontWeight.Normal, color = if (j == 3) C.text else C.text.copy(alpha = 0.85f))
            }
        }
    }
}

/* ---------- league page ---------- */

@Composable
fun LeagueScreen(id: Int) {
    val nav = LocalNav.current
    val l = rememberLoader(id) { Api.fm("leagues", mapOf("id" to id, "ccode3" to "IRN"), 120_000) }
    Column(Modifier.fillMaxSize()) {
        TopBar("", { nav.back() }) {
            val name = l.value?.o("details")?.s("name") ?: ""
            StarButton(Store.isLeague(id), { Store.toggleLeague(id, name) })
        }
        LoadBox(l) { d -> LeagueBody(id, d) }
    }
}

@Composable
private fun LeagueBody(id: Int, d: JSONObject) {
    val det = d.o("details")
    val hasTable = (d.a("table")?.length() ?: 0) > 0
    val hasFx = (d.o("fixtures").a("allMatches")?.length() ?: 0) > 0
    val hasStats = (d.o("stats").a("players")?.length() ?: 0) > 0
    val tabs = buildList {
        if (hasTable) add("table" to "جدول")
        if (hasFx) add("fixtures" to "بازی‌ها")
        if (hasStats) add("stats" to "آمار")
    }
    var tab by rememberSaveable { mutableStateOf(tabs.firstOrNull()?.first ?: "fixtures") }
    val accent = parseColor(det.s("leagueColor")) ?: LocalAccent.current
    LazyColumn(contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                    .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.55f), C.card))).padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.92f)).padding(8.dp)) { LeagueLogo(id, 48.dp) }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Ltr(det.s("name") ?: "", style = MaterialTheme.typography.headlineSmall, weight = FontWeight.ExtraBold, maxLines = 2)
                    Text(listOfNotNull(det.s("country")?.takeIf { it != "INT" }, det.s("selectedSeason")?.let { "فصل $it" }).joinToString(" · "),
                        color = C.text.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        item { Seg(tabs, tab, Modifier.padding(horizontal = 0.dp)) { tab = it } }
        item {
            when (tab) {
                "table" -> TableView(d.a("table"))
                "fixtures" -> LeagueFixtures(d)
                "stats" -> Leaders(d.o("stats").a("players").objs())
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
    val first = firstIdx?.let { all.getOrNull(it) } ?: all.firstOrNull { !it.o("status").optBoolean("finished") } ?: all.lastOrNull()
    var cur by rememberSaveable { mutableStateOf(first?.let(::roundOf) ?: rounds.firstOrNull() ?: "") }
    val i = rounds.indexOf(cur)
    Card {
        Row(Modifier.fillMaxWidth().padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
            // RTL: right arrow = previous round
            IconButton(onClick = { if (i > 0) cur = rounds[i - 1] }, enabled = i > 0) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "قبلی") }
            Text(if (cur.all(Char::isDigit) && cur.isNotEmpty()) "هفته $cur" else cur.ifEmpty { "بازی‌ها" }, Modifier.weight(1f), textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleSmall)
            IconButton(onClick = { if (i < rounds.size - 1) cur = rounds[i + 1] }, enabled = i < rounds.size - 1) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "بعدی") }
        }
        all.filter { roundOf(it) == cur }.map { parseMatch(it) }.groupBy { dayKey(it.time) }.forEach { (_, ms) ->
            SubHead(persianDate(ms.first().time))
            ms.forEach { m -> Divider(); MatchRow(m, { nav.match(m.id) }) }
        }
    }
}

@Composable
private fun Leaders(blocks: List<JSONObject>) {
    val nav = LocalNav.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        blocks.forEach { b ->
            Card {
                CardTitle(tr(b.s("header")))
                b.a("topThree").objs().forEachIndexed { i, p ->
                    Row(Modifier.fillMaxWidth().clickable { nav.player(p.i("id") ?: 0) }.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${p.i("rank") ?: (i + 1)}", Modifier.width(22.dp), color = C.mute, fontWeight = FontWeight.Bold)
                        Face(p.i("id") ?: 0, 36.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Ltr(p.s("name") ?: "", weight = FontWeight.Bold)
                            Ltr(p.s("teamName") ?: "", color = C.mute, style = MaterialTheme.typography.labelSmall)
                        }
                        val v = p.d("value")
                        Text(if (v != null && v % 1 != 0.0) dec(v) else "${v?.toInt() ?: "–"}", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

/* ---------- leagues directory ---------- */

@Composable
fun LeaguesScreen() {
    val nav = LocalNav.current
    val l = rememberLoader("all", peek = { Api.peek(Api.fmUrl("allLeagues", mapOf("locale" to "en", "country" to "IRN"))) as? JSONObject }) {
        Api.fm("allLeagues", mapOf("locale" to "en", "country" to "IRN"), 3_600_000)
    }
    var q by rememberSaveable { mutableStateOf("") }
    val open = remember { mutableStateMapOf<String, Boolean>() }
    Column(Modifier.fillMaxSize()) {
        TopBar("لیگ‌ها", null)
        SearchField(q, "جستجوی لیگ یا کشور…") { q = it }
        LoadBox(l) { d ->
            val row: @Composable (JSONObject) -> Unit = { lg ->
                val id = lg.i("id") ?: 0
                val name = lg.s("localizedName") ?: lg.s("name") ?: ""
                Row(Modifier.fillMaxWidth().clickable { nav.league(id) }.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    LeagueLogo(id, 24.dp); Spacer(Modifier.width(12.dp))
                    Ltr(name, Modifier.weight(1f))
                    StarButton(Store.isLeague(id), { Store.toggleLeague(id, name) }, 18.dp)
                }
            }
            val popular = d.a("popular").objs()
            val intl = d.a("international").objs().flatMap { it.a("leagues").objs() }
            val countries = d.a("countries").objs()
            LazyColumn(contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val query = q.trim().lowercase()
                if (query.isNotEmpty()) {
                    val hits = (popular + intl + countries.flatMap { c -> c.a("leagues").objs().map { it.put("cname", c.s("name")) } })
                        .distinctBy { it.i("id") }
                        .filter { (it.s("localizedName") ?: it.s("name") ?: "").lowercase().contains(query) || (it.s("cname") ?: "").lowercase().contains(query) }
                        .take(80)
                    item { if (hits.isEmpty()) EmptyView("چیزی پیدا نشد", Icons.Rounded.Search) else Card { hits.forEach { row(it) } } }
                    return@LazyColumn
                }
                if (Store.leagues.isNotEmpty()) item {
                    SectionTitle("لیگ‌های دنبال‌شده")
                    Card { Store.leagues.forEach { (id, name) -> row(JSONObject().put("id", id).put("name", name)) } }
                }
                item { SectionTitle("محبوب"); Card { popular.forEach { row(it) } } }
                item { SectionTitle("بین‌المللی"); Card { intl.forEach { row(it) } } }
                item { SectionTitle("کشورها") }
                items(countries, key = { it.s("ccode") ?: it.s("name") ?: "" }) { c ->
                    val key = c.s("ccode") ?: ""
                    val isOpen = open[key] == true
                    Card {
                        Row(Modifier.fillMaxWidth().clickable { open[key] = !isOpen }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            coil.compose.AsyncImage(teamLogo(0).replace("0_small", key.lowercase()), null, Modifier.size(22.dp))
                            Spacer(Modifier.width(12.dp))
                            Ltr(c.s("localizedName") ?: c.s("name") ?: "", Modifier.weight(1f), weight = FontWeight.Medium)
                            Text("${c.a("leagues")?.length() ?: 0}", color = C.mute, style = MaterialTheme.typography.labelMedium)
                            Icon(if (isOpen) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = C.mute)
                        }
                        if (isOpen) c.a("leagues").objs().forEach { Divider(); row(it) }
                    }
                }
            }
        }
    }
}

@Composable
fun SearchField(value: String, hint: String, autoFocus: Boolean = false, onChange: (String) -> Unit) {
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).clip(RoundedCornerShape(16.dp)).background(C.card2).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, null, tint = C.mute)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f).padding(vertical = 14.dp)) {
            if (value.isEmpty()) Text(hint, color = C.mute)
            androidx.compose.foundation.text.BasicTextField(
                value, onChange, Modifier.fillMaxWidth().focusRequester(focus), singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = C.text),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(LocalAccent.current),
            )
        }
    }
    if (autoFocus) androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}
