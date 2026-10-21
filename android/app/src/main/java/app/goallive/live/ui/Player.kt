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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import app.goallive.live.data.tr
import org.json.JSONObject

@Composable
fun PlayerScreen(id: Int) {
    val nav = LocalNav.current
    val l = rememberLoader(id) { Api.fm("playerData", mapOf("id" to id), 300_000) }
    Column(Modifier.fillMaxSize()) {
        TopBar("", { nav.back() })
        LoadBox(l) { p -> PlayerBody(id, p) }
    }
}

@Composable
private fun PlayerBody(id: Int, p: JSONObject) {
    val nav = LocalNav.current
    val team = p.o("primaryTeam")
    val teamId = team.i("teamId") ?: 0
    val club = Clubs.get(teamId)
    val color = club?.let { Color(it.c1) } ?: parseColor(team.path("teamColors").s("color")) ?: C.card2
    LazyColumn(contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Brush.linearGradient(listOf(color.copy(alpha = 0.8f), C.card))).padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Face(id, 84.dp, Color.White.copy(alpha = 0.7f))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Ltr(p.s("name") ?: "", style = MaterialTheme.typography.headlineSmall, weight = FontWeight.ExtraBold, maxLines = 2)
                    if (team != null) Row(Modifier.clickable { nav.team(teamId) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Crest(teamId, 18.dp); Spacer(Modifier.width(6.dp))
                        Text(club?.fa ?: team.s("teamName") ?: "", style = MaterialTheme.typography.labelLarge)
                        if (team.b("onLoan")) Text(" (قرضی)", color = C.mute, style = MaterialTheme.typography.labelSmall)
                    }
                    Text(tr(p.path("positionDescription.primaryPosition").s("label")), color = C.text.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        p.o("injuryInformation")?.let { inj ->
            item {
                Card { Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.LocalHospital, null, tint = C.live); Spacer(Modifier.width(10.dp))
                    Column { Text("مصدوم", fontWeight = FontWeight.Bold); Ltr(inj.s("name") ?: "", color = C.mute, style = MaterialTheme.typography.labelSmall) }
                } }
            }
        }
        val info = p.a("playerInformation").objs()
        if (info.isNotEmpty()) item {
            Card {
                info.chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
                        row.forEach { x ->
                            val v = x.o("value")
                            val text = when (x.s("title")) {
                                "Market value" -> money(v.l("numberValue"))
                                "Height" -> "${v.i("numberValue")} سانتی‌متر"
                                "Age" -> "${v.i("numberValue")} سال"
                                else -> v.s("dateValue")?.let { persianDate(parseTime(it), "d MMMM yyyy") } ?: tr(v.s("fallback") ?: v.s("numberValue"))
                            }
                            Column(Modifier.weight(1f).padding(6.dp)) {
                                Text(tr(x.s("title")), color = C.mute, style = MaterialTheme.typography.labelSmall)
                                Text(text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge, maxLines = 2)
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        p.o("mainLeague")?.let { ml ->
            val stats = ml.a("stats").objs().take(8)
            if (stats.isNotEmpty()) item {
                Card {
                    Row(Modifier.fillMaxWidth().clickable { nav.league(ml.i("leagueId") ?: 0) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        LeagueLogo(ml.i("leagueId") ?: 0, 22.dp); Spacer(Modifier.width(8.dp))
                        Ltr(ml.s("leagueName") ?: "", Modifier.weight(1f), weight = FontWeight.Bold)
                        Text(ml.s("season") ?: "", color = C.mute, style = MaterialTheme.typography.labelMedium)
                    }
                    stats.chunked(4).forEach { row ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp)) {
                            row.forEach { s ->
                                val v = s.d("value")
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(if (v != null && v % 1 != 0.0) dec(v) else "${v?.toInt() ?: "–"}", fontSize = 22.sp, fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineSmall)
                                    Text(tr(s.s("title")), color = C.mute, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 2)
                                }
                            }
                            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
        p.path("firstSeasonStats.statsSection").a("items").objs().forEach { g ->
            item {
                Card {
                    CardTitle(tr(g.s("title"))) { Text("رتبه بین هم‌پست‌ها", color = C.mute, style = MaterialTheme.typography.labelSmall) }
                    g.a("items").objs().forEach { s ->
                        val pct = (s.d("percentileRank") ?: 0.0).toFloat()
                        val c = when { pct >= 80 -> C.win; pct >= 50 -> C.gold; pct >= 25 -> Color(0xFFF08A24); else -> C.loss }
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                            Row {
                                Text(tr(s.s("title")), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                                Ltr(s.s("statValue") ?: "", weight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                            }
                            Spacer(Modifier.height(4.dp))
                            Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(C.card2)) {
                                Box(Modifier.fillMaxWidth((pct / 100f).coerceIn(0.03f, 1f)).height(5.dp).clip(RoundedCornerShape(3.dp)).background(c))
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
        val recent = p.a("recentMatches").objs().filter { it.opt("playedInMatch") != false }.take(12)
        if (recent.isNotEmpty()) item {
            SectionTitle("بازی‌های اخیر")
            Card {
                recent.forEachIndexed { i, m ->
                    if (i > 0) Divider()
                    Row(Modifier.fillMaxWidth().clickable { nav.match(m.i("id") ?: 0) }.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Crest(m.i("opponentTeamId") ?: 0, 26.dp); Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Ltr((if (m.b("isHomeTeam")) "" else "@ ") + (m.s("opponentTeamName") ?: ""), weight = FontWeight.Bold)
                            Text("${shortDate(parseTime(m.path("matchDate").s("utcTime")))} · ${m.i("homeScore") ?: 0}-${m.i("awayScore") ?: 0}", color = C.mute, style = MaterialTheme.typography.labelSmall)
                        }
                        if ((m.i("goals") ?: 0) > 0) Text("⚽${m.i("goals")} ", style = MaterialTheme.typography.labelMedium)
                        if ((m.i("assists") ?: 0) > 0) Text("A${m.i("assists")} ", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Rating(m.path("ratingProps").s("rating")?.toDoubleOrNull())
                    }
                }
            }
        }
        val career = p.path("careerHistory.careerItems")
        val labels = mapOf("senior" to "باشگاهی", "national team" to "تیم ملی", "youth" to "پایه")
        career?.keys()?.asSequence()?.toList()?.forEach { k ->
            val entries = career.o(k).a("teamEntries").objs()
            if (entries.isNotEmpty()) item {
                SectionTitle("سوابق ${labels[k] ?: k}")
                Card {
                    entries.forEachIndexed { i, t ->
                        if (i > 0) Divider()
                        Row(Modifier.fillMaxWidth().clickable { nav.team(t.i("teamId") ?: 0) }.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Crest(t.i("teamId") ?: 0, 26.dp); Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Ltr(t.s("team") ?: "", weight = FontWeight.Bold)
                                Text("${t.s("startDate")?.take(4) ?: ""} – ${t.s("endDate")?.take(4) ?: "اکنون"}", color = C.mute, style = MaterialTheme.typography.labelSmall)
                            }
                            Text(listOfNotNull(t.i("appearances")?.let { "$it بازی" }, t.i("goals")?.takeIf { it > 0 }?.let { "$it گل" }).joinToString(" · "),
                                color = C.mute, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
        val trophies = p.path("trophies").a("playerTrophies").objs().flatMap { t ->
            t.a("tournaments").objs().filter { (it.a("seasonsWon")?.length() ?: 0) > 0 }.map { it to (t.s("teamName") ?: "") }
        }
        if (trophies.isNotEmpty()) item {
            SectionTitle("افتخارات")
            Card {
                trophies.forEachIndexed { i, (x, teamName) ->
                    if (i > 0) Divider()
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.EmojiEvents, null, tint = C.gold); Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Ltr(x.s("leagueName") ?: "", weight = FontWeight.Bold)
                            Ltr(teamName, color = C.mute, style = MaterialTheme.typography.labelSmall)
                        }
                        Text("${x.a("seasonsWon")?.length() ?: 0}×", fontWeight = FontWeight.Black, color = C.gold)
                    }
                }
            }
        }
    }
}
