package app.goallive.live.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.goallive.live.L
import app.goallive.live.data.Api
import app.goallive.live.data.a
import app.goallive.live.data.d
import app.goallive.live.data.i
import app.goallive.live.data.o
import app.goallive.live.data.objs
import app.goallive.live.data.s
import org.json.JSONObject

/** Rebuilt every day on GitHub from every match of the big competitions (tools/rankings). */
private const val RANKINGS_URL = "https://github.com/jeejjeH1/iiiii/releases/download/rankings/rankings.json"

private val GOLD = Color(0xFFF5C542)

/**
 * "رتبه‌بندی گل‌لایو": the world's best players this season (or last) by GoalLive rating, with a position filter;
 * GoalLive's own Ballon d'Or pick and the real winners through the years; the Golden Shoe race and its history.
 */
@Composable
fun RankingsScreen() {
    val l = rememberLoader("rankings") { Api.get(RANKINGS_URL, 3 * 3_600_000L) as JSONObject }
    var tab by rememberSaveable { mutableStateOf("best") }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = BottomSpace),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            T(L("🏆 رتبه‌بندی گل‌لایو"), 18, FontWeight.Black, modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp))
            Seg(listOf("best" to L("برترین‌ها"), "ballon" to L("توپ طلا"), "shoe" to L("کفش طلا")), tab) { tab = it }
        }
        item {
            LoadBox(l, 4) { d ->
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    when (tab) {
                        "best" -> BestPlayers(d)
                        "ballon" -> BallonDor(d)
                        else -> GoldenShoe(d)
                    }
                }
            }
        }
    }
}

// kept left to right inside Persian text, so "2026-27" does not come out as "27-2026"
private fun seasonLabel(s: String) = "\u2066" + fa(s.replace("/", "-").let { if (it.length == 9) it.take(5) + it.drop(7) else it }) + "\u2069"

@Composable
private fun BestPlayers(d: JSONObject) {
    val cur = d.s("current") ?: ""
    var season by rememberSaveable { mutableStateOf(cur) }
    var group by rememberSaveable { mutableStateOf("all") }
    val sd = d.o("seasons")?.o(season)
    SeasonChips(d, season, LocalAccent.current) { season = it }
    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val groups = listOf("all" to L("همه"), "ATT" to L("مهاجم"), "MID" to L("هافبک"), "DEF" to L("مدافع"), "GK" to L("دروازه‌بان"))
        items(groups.size) { k ->
            val (g, label) = groups[k]
            val on = g == group
            Box(Modifier.clip(RoundedCornerShape(50)).background(if (on) LocalAccent.current else C.card2).clickable { group = g }.padding(horizontal = 14.dp, vertical = 7.dp)) {
                T(label, 13, FontWeight.Bold, if (on) LocalAccentInk.current else C.ink)
            }
        }
    }
    val list = (if (group == "all") sd?.a("world").objs() else sd?.o("roles")?.a(group).objs()).orEmpty()
    if (list.isEmpty()) {
        Card { T(L("هنوز داده کافی برای این فصل جمع نشده است."), 13, color = C.mute, modifier = Modifier.padding(20.dp)) }
        return
    }
    T(L("{0} بازی · حداقل {1} دقیقه بازی", fa(sd?.i("matches") ?: 0), fa(sd?.i("minMinutes") ?: 0)), 11, color = C.mute, modifier = Modifier.padding(horizontal = 4.dp))
    Card { list.take(50).forEachIndexed { k, p -> RankRow(k + 1, p, first = k == 0) } }
}

/** One player in a ranking: place, photo, name, team and role, games, goals and assists, and the score. */
@Composable
private fun RankRow(rank: Int, p: JSONObject, first: Boolean, score: Double? = p.d("score")) {
    val nav = LocalNav.current
    val medal = when (rank) { 1 -> GOLD; 2 -> Color(0xFFC9D1D9); 3 -> Color(0xFFD08C4A); else -> null }
    ListRow(first = first, onClick = { nav.player(p.i("id") ?: 0) }) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(medal ?: C.card2), contentAlignment = Alignment.Center) {
            T(fa(rank), 12, FontWeight.Black, if (medal != null) Color(0xFF231A00) else C.ink)
        }
        Face(p.i("id") ?: 0, 42.dp, medal)
        Column(Modifier.weight(1f)) {
            T(personName(p.s("name") ?: ""), 14, FontWeight.ExtraBold, maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                (p.i("team") ?: 0).takeIf { it > 0 }?.let { Crest(it, 14.dp) }
                T(listOfNotNull(teamName(p.i("team") ?: 0, p.s("teamName") ?: ""), p.s("role")?.let { roleName(it) }).joinToString(" · "), 11, color = C.mute, maxLines = 1)
            }
            T(L("{0} بازی · {1} گل · {2} پاس گل", fa(p.i("apps") ?: 0), fa(p.i("goals") ?: 0), fa(p.i("assists") ?: 0)), 11, color = C.mute, maxLines = 1)
        }
        score?.let { Rating(it) }
    }
}

/** Season chips, newest first: "2027 (so far)", "2026", … */
@Composable
private fun SeasonChips(d: JSONObject, selected: String, color: Color, ballon: Boolean = false, onPick: (String) -> Unit) {
    val order = d.a("order")?.let { arr -> (0 until arr.length()).map { arr.optString(it) } }.orEmpty()
    val cur = d.s("current")
    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(order.size) { k ->
            val key = order[k]
            val on = key == selected
            val label = if (ballon) fa(key.takeLast(4)) + if (key == cur) " " + L("(تا الان)") else ""
                else if (key == cur) L("این فصل") else seasonLabel(key)
            Box(Modifier.clip(RoundedCornerShape(50)).background(if (on) color else C.card2).border(1.dp, if (on) color else C.line, RoundedCornerShape(50))
                .clickable { onPick(key) }.padding(horizontal = 14.dp, vertical = 7.dp)) {
                T(label, 13, FontWeight.Bold, if (on) Color(0xFF231A00) else C.ink, maxLines = 1)
            }
        }
    }
}

/** A golden ball drawn by hand: a gold sphere with darker panels, a soft shine and a glow around it. */
@Composable
fun GoldenBall(size: androidx.compose.ui.unit.Dp) {
    androidx.compose.foundation.Canvas(Modifier.size(size)) {
        val r = this.size.minDimension / 2 * 0.86f
        val c = center
        drawCircle(Brush.radialGradient(listOf(GOLD.copy(alpha = 0.45f), Color.Transparent), c, r * 1.25f), r * 1.25f, c)
        drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF4C2), Color(0xFFF5C542), Color(0xFFB8860B), Color(0xFF6B4A06)),
            androidx.compose.ui.geometry.Offset(c.x - r * 0.35f, c.y - r * 0.4f), r * 1.5f), r, c)
        fun pentagon(cx: Float, cy: Float, rad: Float, turn: Float) = androidx.compose.ui.graphics.Path().apply {
            for (k in 0 until 5) {
                val a = Math.toRadians((turn + k * 72 - 90).toDouble())
                val x = cx + rad * kotlin.math.cos(a).toFloat(); val y = cy + rad * kotlin.math.sin(a).toFloat()
                if (k == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
        val panel = Color(0xFF8A6208).copy(alpha = 0.75f)
        val clip = androidx.compose.ui.graphics.Path().apply { addOval(androidx.compose.ui.geometry.Rect(c, r)) }
        clipPath(clip) {
            drawPath(pentagon(c.x, c.y, r * 0.3f, 0f), panel)
            for (k in 0 until 5) {
                val a = Math.toRadians((k * 72 - 90).toDouble())
                drawPath(pentagon(c.x + r * 0.78f * kotlin.math.cos(a).toFloat(), c.y + r * 0.78f * kotlin.math.sin(a).toFloat(), r * 0.27f, 36f), panel)
                drawLine(panel, androidx.compose.ui.geometry.Offset(c.x + r * 0.3f * kotlin.math.cos(a).toFloat(), c.y + r * 0.3f * kotlin.math.sin(a).toFloat()),
                    androidx.compose.ui.geometry.Offset(c.x + r * 0.55f * kotlin.math.cos(a).toFloat(), c.y + r * 0.55f * kotlin.math.sin(a).toFloat()), r * 0.05f)
            }
        }
        // shine
        drawOval(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.75f), Color.Transparent), androidx.compose.ui.geometry.Offset(c.x - r * 0.38f, c.y - r * 0.45f), r * 0.45f),
            androidx.compose.ui.geometry.Offset(c.x - r * 0.75f, c.y - r * 0.8f), androidx.compose.ui.geometry.Size(r * 0.75f, r * 0.55f))
        drawCircle(Color(0xFFFFE9A3).copy(alpha = 0.6f), r, c, style = androidx.compose.ui.graphics.drawscope.Stroke(r * 0.04f))
    }
}

private fun compName(id: Int) = when (id) {
    47 -> L("لیگ برتر انگلیس"); 87 -> L("لالیگا"); 55 -> L("سری آ"); 54 -> L("بوندسلیگا"); 53 -> L("لوشامپیونه")
    42 -> L("لیگ قهرمانان اروپا"); 73 -> L("لیگ اروپا"); 77 -> L("جام جهانی"); 50 -> L("یورو"); 44 -> L("کوپا آمریکا"); else -> ""
}

/** "L:87:1" → "قهرمان لالیگا", "C:42:5" → "فینالیست لیگ قهرمانان اروپا". */
private fun honourText(code: String): String {
    val (kind, id, lvl) = code.split(':').let { Triple(it[0], it.getOrNull(1)?.toIntOrNull() ?: 0, it.getOrNull(2)?.toIntOrNull() ?: 0) }
    val name = compName(id)
    return if (kind == "L") { if (lvl == 1) L("قهرمان {0}", name) else L("رتبه {0} {1}", fa(lvl), name) }
    else when (lvl) { 6 -> L("قهرمان {0}", name); 5 -> L("فینالیست {0}", name); 4 -> L("نیمه‌نهایی {0}", name); 3 -> L("یک‌چهارم نهایی {0}", name); else -> L("یک‌هشتم نهایی {0}", name) }
}

/** Gold-trimmed card for the Ballon d'Or tab. */
@Composable
private fun GoldCard(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Brush.verticalGradient(listOf(Color(0xFF1E1606), Color(0xFF110D05))))
        .border(1.dp, GOLD.copy(alpha = 0.35f), RoundedCornerShape(22.dp)), content = content)
}

@Composable
private fun BallonDor(d: JSONObject) {
    val nav = LocalNav.current
    val cur = d.s("current") ?: ""
    var season by rememberSaveable { mutableStateOf(d.s("last") ?: cur) }
    SeasonChips(d, season, GOLD, ballon = true) { season = it }
    val sd = d.o("seasons")?.o(season)
    val pick = sd?.a("ballonDor").objs().orEmpty()
    val year = sd?.i("ballonYear") ?: season.takeLast(4).toIntOrNull() ?: 0
    val ongoing = season == cur
    if (pick.isEmpty()) {
        GoldCard { T(L("هنوز بازی کافی در این فصل انجام نشده است."), 13, color = C.mute, modifier = Modifier.padding(20.dp)) }
    } else {
        val w = pick.first()
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF3A2A06), Color(0xFF1A1305), Color(0xFF0D0A04))))
            .border(1.5.dp, Brush.linearGradient(listOf(Color(0xFFFFE9A3), GOLD, Color(0xFF8A6208))), RoundedCornerShape(28.dp))
            .clickable { nav.player(w.i("id") ?: 0) }.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            GoldenBall(84.dp)
            T(if (ongoing) L("توپ طلای {0} گل‌لایو · تا این لحظه", fa(year)) else L("توپ طلای {0} گل‌لایو", fa(year)), 15, FontWeight.Black, GOLD)
            Box(Modifier.padding(top = 4.dp).size(116.dp).clip(CircleShape).background(Brush.radialGradient(listOf(GOLD.copy(alpha = 0.5f), Color.Transparent))).padding(6.dp)) {
                Face(w.i("id") ?: 0, 104.dp, GOLD, 3.dp)
            }
            T(personName(w.s("name") ?: ""), 24, FontWeight.Black, Color(0xFFFFF4D6))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (w.i("team") ?: 0).takeIf { it > 0 }?.let { Crest(it, 18.dp) }
                T(teamName(w.i("team") ?: 0, w.s("teamName") ?: ""), 13, FontWeight.Bold, Color(0xFFE8D9A8))
            }
            val hon = w.a("honours")?.let { arr -> (0 until arr.length()).map { arr.optString(it) } }.orEmpty()
            if (hon.isNotEmpty()) FlowRowGold(hon.take(4).map { "🏆 " + honourText(it) })
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((label, v) in listOf(L("نمره فصل") to fa(dec(w.d("score") ?: 0.0, 2)), L("بازی") to fa(w.i("apps") ?: 0), L("گل") to fa(w.i("goals") ?: 0), L("پاس گل") to fa(w.i("assists") ?: 0)))
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(GOLD.copy(alpha = 0.1f)).border(1.dp, GOLD.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                        .padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        T(v, 16, FontWeight.Black, Color(0xFFFFF4D6)); T(label, 10, color = Color(0xFFBFAE7A), maxLines = 1)
                    }
            }
        }
        if (pick.size > 1) GoldCard {
            T(L("نامزدهای گل‌لایو"), 15, FontWeight.ExtraBold, GOLD, Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp))
            pick.drop(1).forEachIndexed { k, p ->
                val hon = p.a("honours")?.let { arr -> (0 until arr.length()).map { arr.optString(it) } }.orEmpty()
                ListRow(first = k == 0, onClick = { nav.player(p.i("id") ?: 0) }) {
                    Box(Modifier.size(28.dp).clip(CircleShape).background(if (k == 0) Color(0xFFC9D1D9) else if (k == 1) Color(0xFFD08C4A) else GOLD.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center) { T(fa(k + 2), 12, FontWeight.Black, if (k < 2) Color(0xFF231A00) else GOLD) }
                    Face(p.i("id") ?: 0, 42.dp, GOLD.copy(alpha = 0.6f))
                    Column(Modifier.weight(1f)) {
                        T(personName(p.s("name") ?: ""), 14, FontWeight.ExtraBold, Color(0xFFFFF4D6), maxLines = 1)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            (p.i("team") ?: 0).takeIf { it > 0 }?.let { Crest(it, 14.dp) }
                            T(teamName(p.i("team") ?: 0, p.s("teamName") ?: ""), 11, color = Color(0xFFBFAE7A), maxLines = 1)
                        }
                        T(listOfNotNull(L("{0} گل · {1} پاس گل", fa(p.i("goals") ?: 0), fa(p.i("assists") ?: 0)), hon.firstOrNull()?.let { honourText(it) }).joinToString(" · "),
                            11, color = Color(0xFFBFAE7A), maxLines = 1)
                    }
                    Rating(p.d("score"))
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
    GoldHistory(d.o("awards")?.a("ballonDor").objs().orEmpty())
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun FlowRowGold(items: List<String>) {
    FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (t in items) Box(Modifier.clip(RoundedCornerShape(50)).background(GOLD.copy(alpha = 0.14f)).border(1.dp, GOLD.copy(alpha = 0.45f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp)) { T(t, 12, FontWeight.Bold, Color(0xFFFFE9A3)) }
    }
}

/** Every Ballon d'Or since 1956, gold-styled, ten at a time. */
@Composable
private fun GoldHistory(list: List<JSONObject>) {
    val nav = LocalNav.current
    if (list.isEmpty()) return
    var all by rememberSaveable { mutableStateOf(false) }
    GoldCard {
        Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            GoldenBall(26.dp); Spacer(Modifier.width(8.dp)); T(L("همه برندگان توپ طلا"), 15, FontWeight.ExtraBold, GOLD)
        }
        (if (all) list else list.take(10)).forEachIndexed { k, x ->
            val id = x.i("id") ?: 0
            ListRow(first = k == 0, onClick = if (id > 0) ({ nav.player(id) }) else null) {
                Box(Modifier.width(48.dp)) { T(fa(x.i("year") ?: 0), 14, FontWeight.Black, GOLD, maxLines = 1) }
                if (id > 0) Face(id, 36.dp, GOLD.copy(alpha = 0.5f)) else GoldenBall(36.dp)
                Column(Modifier.weight(1f)) {
                    if (x.s("name") == null) T(L("برگزار نشد"), 14, FontWeight.ExtraBold, Color(0xFFBFAE7A))
                    else T(personName(x.s("name") ?: ""), 14, FontWeight.ExtraBold, Color(0xFFFFF4D6), maxLines = 1)
                    x.s("team")?.let { T(it.split(" / ").joinToString(" / ") { t -> teamName(0, t) }, 11, color = Color(0xFFBFAE7A), maxLines = 1) }
                }
            }
        }
        T(if (all) L("کمتر") else L("همه ({0})", fa(list.size)), 13, FontWeight.Bold, GOLD,
            Modifier.fillMaxWidth().clickable { all = !all }.padding(vertical = 12.dp), align = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun GoldenShoe(d: JSONObject) {
    val cur = d.s("current") ?: ""; val last = d.s("last") ?: ""
    val race = d.o("seasons")?.o(cur)?.a("goldenShoe").objs()
    val prev = d.o("seasons")?.o(last)?.a("goldenShoe").objs()
    if (race.isNotEmpty()) Card {
        CardTitle(L("👟 رقابت کفش طلا · {0}", seasonLabel(cur)))
        race.take(10).forEachIndexed { k, g -> ShoeRow(k + 1, g, k == 0) }
    }
    if (prev.isNotEmpty()) Card {
        CardTitle(L("کفش طلای {0}", seasonLabel(last)))
        prev.take(5).forEachIndexed { k, g -> ShoeRow(k + 1, g, k == 0) }
    }
    AwardHistory(L("برندگان کفش طلا"), d.o("awards")?.a("goldenShoe").objs()) { x ->
        seasonLabel(x.s("season") ?: "") to L("{0} گل", fa(x.i("goals") ?: 0))
    }
}

@Composable
private fun ShoeRow(rank: Int, g: JSONObject, first: Boolean) {
    val nav = LocalNav.current
    ListRow(first = first, onClick = { nav.player(g.i("id") ?: 0) }) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(if (rank == 1) GOLD else C.card2), contentAlignment = Alignment.Center) {
            T(fa(rank), 12, FontWeight.Black, if (rank == 1) Color(0xFF231A00) else C.ink)
        }
        Face(g.i("id") ?: 0, 40.dp, if (rank == 1) GOLD else null)
        Column(Modifier.weight(1f)) {
            T(personName(g.s("name") ?: ""), 14, FontWeight.ExtraBold, maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                (g.i("team") ?: 0).takeIf { it > 0 }?.let { Crest(it, 14.dp) }
                T(teamName(g.i("team") ?: 0, g.s("teamName") ?: ""), 11, color = C.mute, maxLines = 1)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            T(L("{0} گل", fa(g.i("goals") ?: 0)), 14, FontWeight.Black)
            T(L("{0} امتیاز", fa(g.i("points") ?: 0)), 11, color = C.mute)
        }
    }
}

/** Past winners: year, photo, name, club, and a short extra (goals, or that it was not held). */
@Composable
private fun AwardHistory(title: String, list: List<JSONObject>, line: (JSONObject) -> Pair<String, String?>) {
    val nav = LocalNav.current
    if (list.isEmpty()) return
    var all by rememberSaveable(title) { mutableStateOf(false) }
    Card {
        CardTitle(title)
        (if (all) list else list.take(10)).forEachIndexed { k, x ->
            val (when_, extra) = line(x)
            val id = x.i("id") ?: 0
            ListRow(first = k == 0, onClick = if (id > 0) ({ nav.player(id) }) else null) {
                Box(Modifier.width(54.dp)) { T(when_, 13, FontWeight.Black, GOLD, maxLines = 1) }
                if (id > 0) Face(id, 36.dp) else Box(Modifier.size(36.dp).clip(CircleShape).background(C.card2), contentAlignment = Alignment.Center) { T("🏆", 15) }
                Column(Modifier.weight(1f)) {
                    T(x.s("name")?.split(" · ")?.joinToString(" · ") { personName(it) } ?: "—", 14, FontWeight.ExtraBold, maxLines = 1)
                    x.s("team")?.let { T(it.split(" / ", " · ").joinToString(" / ") { t -> teamName(0, t) }, 11, color = C.mute, maxLines = 1) }
                }
                extra?.let { T(it, 12, FontWeight.Bold, C.mute) }
            }
        }
        if (list.size > 10) T(if (all) L("کمتر") else L("همه ({0})", fa(list.size)), 12, FontWeight.Bold, LocalAccent.current,
            Modifier.fillMaxWidth().clickable { all = !all }.padding(vertical = 10.dp), align = androidx.compose.ui.text.style.TextAlign.Center)
    }
}
