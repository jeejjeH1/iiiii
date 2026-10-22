package app.goallive.live.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.runtime.*
import androidx.compose.ui.draw.*
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
    var picker by remember { mutableStateOf(false) }
    if (picker) TeamPicker { picker = false }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = BottomSpace)) {
        if (Store.teams.isEmpty() && Store.leagues.isEmpty() && Store.myTeam == null) {
            item {
                EmptyView("هنوز چیزی دنبال نکرده‌اید", Ic.star,
                    hint = "روی ستاره کنار تیم‌ها، لیگ‌ها و بازی‌ها بزنید تا اینجا جمع شوند و اعلان گل و ترکیبشان را بگیرید.",
                    action = "انتخاب تیم محبوب") { picker = true }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    T("یا لیگ‌ها را ببینید", 13, FontWeight.Bold, LocalAccent.current, Modifier.clip(RoundedCornerShape(50)).clickable { nav.tab("leagues") }.padding(10.dp))
                }
            }
            return@LazyColumn
        }
        Store.myTeam?.let { t -> item { MyTeamCard(t.id, t.name) } }
        if (Store.teams.isNotEmpty()) item {
            SectionTitle("تیم‌ها")
            Card {
                Store.teams.entries.forEachIndexed { i, (id, name) ->
                    ListRow(first = i == 0, onClick = { nav.team(id) }) {
                        Crest(id, 34.dp)
                        TeamText(id, name, Modifier.weight(1f), 15)
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
        item { MadeBy() }
    }
}
