package app.goallive.live.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.goallive.live.data.Api
import app.goallive.live.data.MatchItem
import app.goallive.live.data.Side
import app.goallive.live.data.Store
import app.goallive.live.data.a
import app.goallive.live.data.b
import app.goallive.live.data.i
import app.goallive.live.data.o
import app.goallive.live.data.objs
import app.goallive.live.data.parseTime
import app.goallive.live.data.s
import kotlinx.coroutines.delay
import org.json.JSONObject

/** Search results grouped by kind. */
class Found(val teams: List<JSONObject>, val players: List<JSONObject>, val leagues: List<JSONObject>, val matches: List<JSONObject>)

suspend fun search(term: String): Found {
    val arr = Api.fmArray("suggest", mapOf("term" to term), 60_000)
    val all = arr.objs().flatMap { it.a("suggestions").objs() }
    fun of(t: String) = all.filter { it.s("type") == t }.distinctBy { it.s("id") }
    return Found(of("team"), of("player"), of("league"), of("match"))
}

@Composable
fun SearchScreen() {
    val nav = LocalNav.current
    var q by rememberSaveable { mutableStateOf("") }
    var res by remember { mutableStateOf<Found?>(null) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(q) {
        val term = q.trim()
        if (term.length < 2) { res = null; return@LaunchedEffect }
        delay(300)
        busy = true
        res = runCatching { search(term) }.getOrNull()
        busy = false
    }
    Column(Modifier.fillMaxSize()) {
        TopBar("جستجو", { nav.back() })
        SearchField(q, "تیم، بازیکن یا لیگ…", autoFocus = true) { q = it }
        val r = res
        when {
            q.trim().length < 2 -> EmptyView("نام تیم، بازیکن یا لیگ را بنویسید (فارسی یا انگلیسی)", Icons.Rounded.Search)
            r == null && busy -> Skeleton(5)
            r == null -> ErrorView({ q = q.trim() + " " })
            r.teams.isEmpty() && r.players.isEmpty() && r.leagues.isEmpty() && r.matches.isEmpty() -> EmptyView("نتیجه‌ای پیدا نشد", Icons.Rounded.Search)
            else -> LazyColumn(contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (r.teams.isNotEmpty()) item {
                    SectionTitle("تیم‌ها")
                    Card {
                        r.teams.forEach { t ->
                            val id = t.s("id")?.toIntOrNull() ?: 0
                            Row(Modifier.fillMaxWidth().clickable { nav.team(id) }.padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Crest(id, 30.dp); Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Ltr(t.s("name") ?: "", weight = FontWeight.Bold)
                                    Ltr(t.s("leagueName") ?: "", color = C.mute, style = MaterialTheme.typography.labelSmall)
                                }
                                StarButton(Store.isTeam(id), { Store.toggleTeam(id, t.s("name") ?: "") })
                            }
                        }
                    }
                }
                if (r.players.isNotEmpty()) item {
                    SectionTitle("بازیکنان")
                    Card {
                        r.players.forEach { p ->
                            val id = p.s("id")?.toIntOrNull() ?: 0
                            PersonRow(id, p.s("name") ?: "", (p.s("teamName") ?: "") + if (p.b("isCoach")) " · مربی" else "") { nav.player(id) }
                        }
                    }
                }
                if (r.leagues.isNotEmpty()) item {
                    SectionTitle("لیگ‌ها")
                    Card {
                        r.leagues.forEach { lg ->
                            val id = lg.s("id")?.toIntOrNull() ?: 0
                            Row(Modifier.fillMaxWidth().clickable { nav.league(id) }.padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                LeagueLogo(id, 26.dp); Spacer(Modifier.width(12.dp))
                                Ltr(lg.s("name") ?: "", Modifier.weight(1f), weight = FontWeight.Bold)
                                StarButton(Store.isLeague(id), { Store.toggleLeague(id, lg.s("name") ?: "") })
                            }
                        }
                    }
                }
                if (r.matches.isNotEmpty()) item {
                    SectionTitle("بازی‌ها")
                    Card {
                        r.matches.forEach { s ->
                            val st = s.o("status")
                            val m = MatchItem(
                                s.s("id")?.toIntOrNull() ?: 0,
                                Side(s.i("homeTeamId") ?: 0, s.s("homeTeamName") ?: "", s.i("homeScore")),
                                Side(s.i("awayTeamId") ?: 0, s.s("awayTeamName") ?: "", s.i("awayScore")),
                                parseTime(st.s("utcTime") ?: s.s("matchDate")), st.b("started"), st.b("finished"), st.b("cancelled"), "", st.o("reason").s("short") ?: "",
                            )
                            MatchRow(m, { nav.match(m.id) }, showDate = true)
                        }
                    }
                }
            }
        }
    }
}
