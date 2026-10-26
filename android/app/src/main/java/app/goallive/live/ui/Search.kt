package app.goallive.live.ui

import app.goallive.live.L

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.runtime.*
import androidx.compose.ui.draw.*
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import org.json.JSONObject

/** Search results grouped by kind. */
class Found(val teams: List<JSONObject>, val players: List<JSONObject>, val leagues: List<JSONObject>, val matches: List<JSONObject>)

suspend fun search(term: String): Found {
    // FotMob only knows English names: Persian is turned into the English names it may mean first
    val persian = term.any { it in '\u0600'..'\u06FF' }
    val terms = if (persian) persianSearchTerms(term).ifEmpty { listOf(term) } else listOf(term)
    // every reading asked at once; one failing doesn't spoil the others (only all failing is an error)
    val answers = kotlinx.coroutines.coroutineScope {
        terms.map { t -> async { runCatching { Api.fmArray("suggest", mapOf("term" to t), 60_000) } } }.map { it.await() }
    }
    if (answers.all { it.isFailure }) throw answers.first().exceptionOrNull()!!
    val all = answers.flatMap { r -> r.getOrNull().objs().flatMap { it.a("suggestions").objs() } }
    fun of(t: String) = all.filter { it.s("type") == t }.distinctBy { it.s("id") }
    return Found(of("team"), of("player"), of("league"), of("match"))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen() {
    val nav = LocalNav.current
    var q by rememberSaveable { mutableStateOf("") }
    var res by remember { mutableStateOf<Found?>(null) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    LaunchedEffect(q, retry) {
        val term = q.trim()
        if (term.length < 2) { res = null; failed = false; return@LaunchedEffect }
        delay(280)
        busy = true
        val r = runCatching { search(term) }
        res = r.getOrNull(); failed = r.isFailure
        busy = false
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = BottomSpace)) {
        item { SearchField(q, L("تیم، بازیکن یا لیگ؛ فارسی یا انگلیسی…"), autoFocus = true) { q = it } }
        val r = res
        when {
            q.trim().length < 2 -> item {
                EmptyView(L("دنبال چه می‌گردید؟"), Ic.search, L("نام تیم، بازیکن یا لیگ را بنویسید؛ یا یکی از این‌ها:"))
                // popular searches, one tap away
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (w in listOf("Persepolis", "Esteghlal", "Real Madrid", "Barcelona", "Messi", "Ronaldo", "Taremi", "Premier League")) Pill(w, onClick = { q = w })
                }
            }
            busy && r == null -> item { Skeleton(5) }
            failed -> item { ErrorView({ retry++ }) }
            r == null -> {}
            r.teams.isEmpty() && r.players.isEmpty() && r.leagues.isEmpty() && r.matches.isEmpty() -> item {
                EmptyView(L("نتیجه‌ای برای «{0}» پیدا نشد", q.trim()), Ic.search, L("نام کامل‌تر یا املای انگلیسی را امتحان کنید؛ مثلاً «سپاهان» یا Mbappe."), L("پاک کردن جستجو")) { q = "" }
            }
            else -> {
                if (r.teams.isNotEmpty()) item {
                    SectionTitle(L("تیم‌ها"))
                    Card {
                        r.teams.forEachIndexed { i, t ->
                            val id = t.s("id")?.toIntOrNull() ?: 0
                            ListRow(first = i == 0, onClick = { nav.team(id) }) {
                                Crest(id, 34.dp)
                                Column(Modifier.weight(1f)) { Ltr(t.s("name") ?: "", style = vz(14.sp, androidx.compose.ui.text.font.FontWeight.Bold)); Ltr(t.s("leagueName") ?: "", style = vz(12.sp), color = C.mute) }
                                StarButton(Store.isTeam(id), { Store.toggleTeam(id, t.s("name") ?: "") })
                            }
                        }
                    }
                }
                if (r.players.isNotEmpty()) item {
                    SectionTitle(L("بازیکنان"))
                    Card {
                        r.players.forEachIndexed { i, p ->
                            val id = p.s("id")?.toIntOrNull() ?: 0
                            PersonRow(id, p.s("name") ?: "", (p.s("teamName") ?: "") + if (p.b("isCoach")) L(" · مربی") else "", first = i == 0) { nav.player(id) }
                        }
                    }
                }
                if (r.leagues.isNotEmpty()) item {
                    SectionTitle(L("لیگ‌ها"))
                    Card {
                        r.leagues.forEachIndexed { i, lg ->
                            val id = lg.s("id")?.toIntOrNull() ?: 0
                            ListRow(first = i == 0, onClick = { nav.league(id) }) {
                                LeagueLogo(id, 34.dp)
                                Column(Modifier.weight(1f)) { Ltr(lg.s("name") ?: "", style = vz(14.sp, androidx.compose.ui.text.font.FontWeight.Bold)); Ltr(lg.s("countryCode") ?: "", style = vz(12.sp), color = C.mute) }
                                StarButton(Store.isLeague(id), { Store.toggleLeague(id, lg.s("name") ?: "") })
                            }
                        }
                    }
                }
                if (r.matches.isNotEmpty()) item {
                    SectionTitle(L("بازی‌ها"))
                    Card {
                        r.matches.forEachIndexed { i, s ->
                            val st = s.o("status")
                            val m = MatchItem(
                                s.s("id")?.toIntOrNull() ?: 0,
                                Side(s.i("homeTeamId") ?: 0, s.s("homeTeamName") ?: "", s.i("homeScore")),
                                Side(s.i("awayTeamId") ?: 0, s.s("awayTeamName") ?: "", s.i("awayScore")),
                                parseTime(st.s("utcTime") ?: s.s("matchDate")), st.b("started"), st.b("finished"), st.b("cancelled"), "", st.o("reason").s("short") ?: "",
                            )
                            MatchRow(m, { nav.match(m.id) }, showDate = true, first = i == 0)
                        }
                    }
                }
            }
        }
    }
}
