package app.goallive.live.ui

import app.goallive.live.L

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.goallive.live.data.Api
import app.goallive.live.data.Clubs
import app.goallive.live.data.a
import app.goallive.live.data.b
import app.goallive.live.data.d
import app.goallive.live.data.i
import app.goallive.live.data.l
import app.goallive.live.data.o
import app.goallive.live.data.objs
import app.goallive.live.data.parseTime
import app.goallive.live.data.path
import app.goallive.live.data.s
import app.goallive.live.data.strs
import app.goallive.live.data.tr
import org.json.JSONObject

@Composable
fun PlayerScreen(id: Int) {
    val l = rememberLoader(id) { Api.fm("playerData", mapOf("id" to id), 300_000) }
    LoadBox(l) { p -> PlayerBody(id, p) }
}

/** A grid of label/value cells separated by hairlines (".info" / ".kpis"). */
@Composable
private fun Grid(cells: List<Pair<String, String>>, cols: Int, big: Boolean) {
    Column(Modifier.fillMaxWidth().background(C.line)) {
        cells.chunked(cols).forEachIndexed { r, row ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(top = if (r == 0) 0.dp else 1.dp)) {
                row.forEachIndexed { c, (k, v) ->
                    Column(Modifier.weight(1f).fillMaxHeight().padding(start = if (c == 0) 0.dp else 1.dp).background(C.cardSolid)
                        .padding(horizontal = if (big) 8.dp else 16.dp, vertical = if (big) 14.dp else 12.dp),
                        horizontalAlignment = if (big) Alignment.CenterHorizontally else Alignment.Start) {
                        if (big) { T(v, 20, FontWeight.Black); T(k, 11, color = C.mute, align = TextAlign.Center) }
                        else { T(k, 11, color = C.mute); T(v, 13, FontWeight.Bold) }
                    }
                }
                repeat(cols - row.size) { Box(Modifier.weight(1f).fillMaxHeight().padding(start = 1.dp).background(C.cardSolid)) }
            }
        }
    }
}

@Composable
private fun PlayerBody(id: Int, p: JSONObject) {
    val nav = LocalNav.current
    val team = p.o("primaryTeam")
    val teamId = team.i("teamId") ?: 0
    val club = Clubs.get(teamId)
    val color = club?.let { Color(it.c1) } ?: parseColor(team.path("teamColors").s("color")) ?: Color(0xFF1A2A40)
    val ink = club?.let { if (lum(Color(it.ink)) < 0.3f) Color(it.ink) else Color.White } ?: if (lum(color) > 0.6f) Color(0xFF0B1B3A) else Color.White
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = BottomSpace), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            PageHero(color, club?.pattern ?: "dots") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Face(id, 84.dp, Color.White.copy(alpha = 0.85f), 3.dp)
                    Column(Modifier.weight(1f)) {
                        Ltr(p.s("name") ?: "", style = lat(26.sp, FontWeight.ExtraBold), color = ink, maxLines = 2)
                        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (team != null) Row(Modifier.clickable { nav.team(teamId) }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Crest(teamId, 18.dp)
                                T((club?.fa ?: teamName(teamId, team.s("teamName") ?: "")) + if (team.b("onLoan")) L(" (قرضی)") else "", 13, FontWeight.SemiBold, ink.copy(alpha = 0.9f), maxLines = 1)
                            }
                            val pos = tr(p.path("positionDescription.primaryPosition").s("label"))
                            T(listOf(pos, if (p.b("isCaptain")) L("کاپیتان") else "").filter { it.isNotEmpty() }.joinToString(" · ").let { if (it.isNotEmpty()) "· $it" else "" },
                                13, FontWeight.SemiBold, ink.copy(alpha = 0.85f), maxLines = 1)
                        }
                        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.height(28.dp).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.25f))
                                .clickable { nav.compare(id) }.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                Icon(Ic.swap, null, Modifier.size(14.dp), tint = ink)
                                T(L("مقایسه"), 12, FontWeight.ExtraBold, ink)
                            }
                            // the player as a story picture or video
                            Row(Modifier.height(28.dp).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.25f))
                                .clickable { StoryStudio.story = Story.Player(p) }.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                Icon(Ic.share, null, Modifier.size(14.dp), tint = ink)
                                T(L("استوری"), 12, FontWeight.ExtraBold, ink)
                            }
                        }
                    }
                }
            }
        }
        val info = p.a("playerInformation").objs().map { x ->
            val v = x.o("value")
            tr(x.s("title")) to when (x.s("title")) {
                "Market value" -> money(v.l("numberValue"))
                "Height" -> L("{0} سانتی‌متر", v.i("numberValue"))
                "Age" -> L("{0} سال", v.i("numberValue"))
                "Country" -> personName(v.s("fallback") ?: "")
                else -> v.s("dateValue")?.let { persianDate(parseTime(it), "d MMMM yyyy") } ?: tr(v.s("fallback") ?: v.s("numberValue"))
            }
        }
        val ml = p.o("mainLeague")
        if (info.isNotEmpty() || ml != null) item {
            Card(Modifier.padding(horizontal = 14.dp)) {
                if (info.isNotEmpty()) Grid(info, 2, false)
                if (ml != null) {
                    Divider()
                    Row(Modifier.fillMaxWidth().clickable { nav.league(ml.i("leagueId") ?: 0) }.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        LeagueLogo(ml.i("leagueId") ?: 0, 18.dp)
                        Ltr(ml.s("leagueName") ?: "", Modifier.weight(1f, false), style = vz(14.sp, FontWeight.ExtraBold))
                        T(fa(ml.s("season") ?: ""), 12, FontWeight.Medium, C.mute)
                    }
                    val kpis = ml.a("stats").objs().take(8).map { s ->
                        val v = s.d("value")
                        tr(s.s("title")) to fa(if (v == null) "–" else if (v % 1 != 0.0) dec(v) else "${v.toInt()}")
                    }
                    if (kpis.isNotEmpty()) { Divider(); Grid(kpis, 2, true) }
                }
            }
        }
        p.o("injuryInformation")?.let { inj ->
            item {
                Card(Modifier.padding(horizontal = 14.dp)) {
                    ListRow(first = true) {
                        Icon(Ic.bandage, null, Modifier.size(20.dp), tint = C.live)
                        Column {
                            T(L("مصدوم"), 14, FontWeight.Bold)
                            T(listOfNotNull(inj.s("name"), inj.path("expectedReturn").s("expectedReturnFallback")?.let { L("بازگشت: {0}", it) }).joinToString(" · "), 12, color = C.mute)
                        }
                    }
                }
            }
        }
        p.o("traits")?.takeIf { (it.a("items")?.length() ?: 0) > 0 }?.let { tr0 ->
            item {
                Card(Modifier.padding(horizontal = 14.dp)) {
                    CardTitle(tr0.s("title") ?: L("ویژگی‌ها"))
                    tr0.a("items").objs().forEach { t -> PctRow(tr(t.s("title")), L("{0}٪", ((t.d("value") ?: 0.0) * 100).toInt()), null, ((t.d("value") ?: 0.0) * 100).toFloat(), LocalAccent.current) }
                }
            }
        }
        p.path("firstSeasonStats.statsSection").a("items").objs().forEach { g ->
            item {
                Card(Modifier.padding(horizontal = 14.dp)) {
                    CardTitle(tr(g.s("title"))) { T(L("رتبه صدک بین هم‌پست‌ها"), 12, FontWeight.Medium, C.mute) }
                    g.a("items").objs().forEach { s ->
                        val pct = (s.d("percentileRank") ?: 0.0).toFloat()
                        val c = when { pct >= 80 -> C.win; pct >= 50 -> C.gold; pct >= 25 -> Color(0xFFF08A24); else -> C.loss }
                        val per90 = s.d("per90")?.takeIf { s.s("statFormat") != "percent" }?.let { L("{0} در ۹۰", dec(it)) }
                        PctRow(tr(s.s("title")), fa(s.s("statValue") ?: ""), per90, pct, c)
                    }
                }
            }
        }
        val recent = p.a("recentMatches").objs().filter { it.opt("playedInMatch") != false }.take(12)
        if (recent.isNotEmpty()) item {
            Column(Modifier.padding(horizontal = 14.dp)) {
                SectionTitle(L("بازی‌های اخیر"))
                Card {
                    recent.forEachIndexed { i, m ->
                        ListRow(first = i == 0, onClick = { nav.match(m.i("id") ?: 0) }) {
                            Crest(m.i("opponentTeamId") ?: 0, 34.dp)
                            Column(Modifier.weight(1f)) {
                                Ltr((if (m.b("isHomeTeam")) "" else "@ ") + (m.s("opponentTeamName") ?: ""))
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    T("${m.s("leagueName") ?: ""} · ${persianDate(parseTime(m.path("matchDate").s("utcTime")), "d MMM")} ·", 12, color = C.mute, maxLines = 1)
                                    ScoreText(m.i("homeScore"), m.i("awayScore"), 12, C.mute)
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if ((m.i("goals") ?: 0) > 0) Row(verticalAlignment = Alignment.CenterVertically) { T("${m.i("goals")}", 12, color = C.mute); Icon(Ic.ball, null, Modifier.size(13.dp), tint = C.mute) }
                                if ((m.i("assists") ?: 0) > 0) T("${m.i("assists")} A", 12, color = C.mute)
                                m.i("minutesPlayed")?.takeIf { it > 0 }?.let { T("$it′", 12, color = C.mute) }
                                if (m.b("playerOfTheMatch")) Icon(Ic.starOn, null, Modifier.size(13.dp), tint = C.gold)
                                Rating(m.path("ratingProps").s("rating")?.toDoubleOrNull())
                            }
                        }
                    }
                }
            }
        }
        val career = p.path("careerHistory.careerItems")
        val labels = mapOf("senior" to L("باشگاهی"), "national team" to L("تیم ملی"), "youth" to L("پایه"))
        career?.keys()?.asSequence()?.toList()?.forEach { k ->
            val entries = career.o(k).a("teamEntries").objs()
            if (entries.isNotEmpty()) item {
                Column(Modifier.padding(horizontal = 14.dp)) {
                    SectionTitle(L("سوابق {0}", labels[k] ?: k))
                    Card {
                        entries.forEachIndexed { i, t ->
                            ListRow(first = i == 0, onClick = { nav.team(t.i("teamId") ?: 0) }) {
                                Crest(t.i("teamId") ?: 0, 34.dp)
                                Column(Modifier.weight(1f)) {
                                    Ltr(t.s("team") ?: "", style = vz(14.sp, FontWeight.Bold))
                                    T("${t.s("startDate")?.take(4) ?: ""} – ${t.s("endDate")?.take(4) ?: "اکنون"}" + if (t.s("transferType") == "on loan") L(" · قرضی") else "", 12, color = C.mute)
                                }
                                T(listOfNotNull(t.i("appearances")?.let { L("{0} بازی", it) }, t.i("goals")?.takeIf { it > 0 }?.let { L("{0} گل", it) }).joinToString(" · "), 12, color = C.mute)
                            }
                        }
                    }
                }
            }
        }
        val trophies = p.path("trophies").a("playerTrophies").objs().flatMap { t ->
            t.a("tournaments").objs().filter { (it.a("seasonsWon")?.length() ?: 0) > 0 }.map { it to (t.s("teamName") ?: "") }
        }
        if (trophies.isNotEmpty()) item {
            Column(Modifier.padding(horizontal = 14.dp)) {
                SectionTitle(L("افتخارات"))
                Card {
                    trophies.forEachIndexed { i, (x, teamName) ->
                        ListRow(first = i == 0) {
                            Icon(Ic.trophy, null, Modifier.size(18.dp), tint = C.gold)
                            Column(Modifier.weight(1f)) {
                                Ltr(x.s("leagueName") ?: "", style = vz(14.sp, FontWeight.Bold))
                                Ltr("$teamName · " + fa(x.a("seasonsWon").strs().joinToString(L("، "))), style = vz(12.sp), color = C.mute, maxLines = 2)
                            }
                            T("${x.a("seasonsWon")?.length() ?: 0}×", 14, FontWeight.Black)
                        }
                    }
                }
            }
        }
    }
}

/** ".pct": label, value and a coloured percentile bar. */
@Composable
private fun PctRow(label: String, value: String, extra: String?, pct: Float, color: Color) {
    Divider()
    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            T(label, 13, modifier = Modifier.weight(1f))
            Ltr(value, style = vz(13.sp, FontWeight.ExtraBold))
            if (extra != null) { Spacer(Modifier.size(6.dp)); T(extra, 11, color = C.mute) }
        }
        Box(Modifier.padding(top = 4.dp).fillMaxWidth().height(6.dp).clip(RoundedCornerShape(6.dp)).background(C.card2)) {
            Box(Modifier.fillMaxWidth((pct / 100f).coerceIn(0.03f, 1f)).fillMaxHeight().clip(RoundedCornerShape(6.dp)).background(color))
        }
    }
}
