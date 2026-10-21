package app.goallive.live.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.goallive.live.data.Clubs
import app.goallive.live.data.Store

@Composable
fun FollowingScreen() {
    val nav = LocalNav.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = BottomSpace)) {
        if (Store.teams.isEmpty() && Store.leagues.isEmpty() && Store.myTeam == null) {
            item { EmptyView("هنوز چیزی دنبال نکرده‌اید.\nروی ستاره کنار تیم‌ها، لیگ‌ها و بازی‌ها بزنید تا اینجا جمع شوند و اعلان گل و ترکیبشان را بگیرید.", Ic.star) }
            return@LazyColumn
        }
        Store.myTeam?.let { t -> item { MyTeamCard(t.id, t.name) } }
        if (Store.teams.isNotEmpty()) item {
            SectionTitle("تیم‌ها")
            Card {
                Store.teams.entries.forEachIndexed { i, (id, name) ->
                    ListRow(first = i == 0, onClick = { nav.team(id) }) {
                        Crest(id, 34.dp)
                        if (Clubs.get(id) != null) T(Clubs.get(id)!!.fa, 14, FontWeight.Bold, modifier = Modifier.weight(1f)) else Ltr(name, Modifier.weight(1f))
                        StarButton(true, { Store.toggleTeam(id, name) })
                    }
                }
            }
        }
        if (Store.leagues.isNotEmpty()) item {
            SectionTitle("لیگ‌ها")
            Card {
                Store.leagues.entries.forEachIndexed { i, (id, name) ->
                    ListRow(first = i == 0, onClick = { nav.league(id) }) {
                        LeagueLogo(id, 30.dp)
                        Ltr(name, Modifier.weight(1f), style = vz(14.sp, FontWeight.Bold))
                        StarButton(true, { Store.toggleLeague(id, name) })
                    }
                }
            }
        }
    }
}
