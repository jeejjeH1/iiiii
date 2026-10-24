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

private fun seasonLabel(s: String) = fa(s.replace("/", "-").let { if (it.length == 9) it.take(5) + it.drop(7) else it })

@Composable
private fun BestPlayers(d: JSONObject) {
    val cur = d.s("current") ?: ""; val last = d.s("last") ?: ""
    var season by rememberSaveable { mutableStateOf(cur) }
    var group by rememberSaveable { mutableStateOf("all") }
    val sd = d.o("seasons")?.o(season)
    Seg(listOf(cur to L("این فصل"), last to L("فصل گذشته")), season) { season = it }
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

@Composable
private fun BallonDor(d: JSONObject) {
    val nav = LocalNav.current
    val last = d.s("last") ?: ""
    val pick = d.o("seasons")?.o(last)?.a("ballonDor").objs()
    val year = last.takeLast(4)
    if (pick.isNotEmpty()) {
        val w = pick.first()
        // the winner, big and golden
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp))
            .background(Brush.verticalGradient(listOf(GOLD.copy(alpha = 0.35f), GOLD.copy(alpha = 0.08f), C.card)))
            .border(1.dp, GOLD.copy(alpha = 0.5f), RoundedCornerShape(26.dp)).clickable { nav.player(w.i("id") ?: 0) }.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            T(L("توپ طلای {0} از نگاه گل‌لایو", fa(year)), 14, FontWeight.ExtraBold, GOLD)
            Box(Modifier.padding(top = 6.dp).size(112.dp).clip(CircleShape).background(GOLD.copy(alpha = 0.25f)).padding(5.dp)) { Face(w.i("id") ?: 0, 102.dp, GOLD, 3.dp) }
            T(personName(w.s("name") ?: ""), 22, FontWeight.Black)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (w.i("team") ?: 0).takeIf { it > 0 }?.let { Crest(it, 18.dp) }
                T(teamName(w.i("team") ?: 0, w.s("teamName") ?: ""), 13, FontWeight.Bold, C.mute)
            }
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((label, v) in listOf(L("نمره فصل") to fa(dec(w.d("score") ?: 0.0, 2)), L("بازی") to fa(w.i("apps") ?: 0), L("گل") to fa(w.i("goals") ?: 0), L("پاس گل") to fa(w.i("assists") ?: 0)))
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(alpha = 0.18f)).padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) { T(v, 16, FontWeight.Black); T(label, 10, color = C.mute, maxLines = 1) }
            }
        }
        Card {
            CardTitle(L("نامزدهای گل‌لایو"))
            pick.drop(1).forEachIndexed { k, p -> RankRow(k + 2, p, first = k == 0) }
        }
    }
    AwardHistory(L("برندگان توپ طلا"), d.o("awards")?.a("ballonDor").objs()) { x ->
        fa(x.i("year") ?: 0) to (if (x.s("name") == null) L("برگزار نشد") else null)
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
