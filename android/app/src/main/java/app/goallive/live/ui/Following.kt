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
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.goallive.live.data.Clubs
import app.goallive.live.data.Store

@Composable
fun FollowingScreen() {
    val nav = LocalNav.current
    Column(Modifier.fillMaxSize()) {
        TopBar("دنبال‌شده‌ها", null) { IconButton(onClick = { nav.search() }) { Icon(Icons.Rounded.Search, "جستجو", tint = C.text) } }
        if (Store.teams.isEmpty() && Store.leagues.isEmpty() && Store.myTeam == null) {
            EmptyView("هنوز چیزی دنبال نکرده‌اید.\nروی ستاره کنار تیم‌ها، لیگ‌ها و بازی‌ها بزنید تا اینجا جمع شوند و اعلان گل و ترکیبشان را بگیرید.", Icons.Rounded.StarBorder)
            return@Column
        }
        LazyColumn(contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Store.myTeam?.let { t -> item { SectionTitle("تیم من"); MyTeamCard(t.id, t.name) } }
            if (Store.teams.isNotEmpty()) item {
                SectionTitle("تیم‌ها")
                Card {
                    Store.teams.forEach { (id, name) ->
                        Row(Modifier.fillMaxWidth().clickable { nav.team(id) }.padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Crest(id, 30.dp); Spacer(Modifier.width(12.dp))
                            Ltr(Clubs.get(id)?.fa ?: name, Modifier.weight(1f), weight = FontWeight.Bold)
                            StarButton(true, { Store.toggleTeam(id, name) })
                        }
                    }
                }
            }
            if (Store.leagues.isNotEmpty()) item {
                SectionTitle("لیگ‌ها")
                Card {
                    Store.leagues.forEach { (id, name) ->
                        Row(Modifier.fillMaxWidth().clickable { nav.league(id) }.padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            LeagueLogo(id, 26.dp); Spacer(Modifier.width(12.dp))
                            Ltr(name, Modifier.weight(1f), weight = FontWeight.Bold)
                            StarButton(true, { Store.toggleLeague(id, name) })
                        }
                    }
                }
            }
        }
    }
}
