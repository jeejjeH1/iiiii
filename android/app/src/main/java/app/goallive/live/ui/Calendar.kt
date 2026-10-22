package app.goallive.live.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.runtime.*
import androidx.compose.ui.draw.*
import android.content.Intent
import android.provider.CalendarContract
import android.icu.util.Calendar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import app.goallive.live.data.Api
import app.goallive.live.data.Clubs
import app.goallive.live.data.MatchItem
import app.goallive.live.data.Store
import app.goallive.live.data.objs
import app.goallive.live.data.o
import app.goallive.live.data.path
import app.goallive.live.data.a
import app.goallive.live.data.s
import app.goallive.live.data.parseMatch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private fun persianCal(ms: Long): Calendar = Calendar.getInstance(FA).apply { timeInMillis = ms }

/** "yyyy/MM/dd" in the Persian calendar, to group fixtures by day. */
private fun pKey(ms: Long) = android.icu.text.SimpleDateFormat("yyyy/MM/dd", FA).format(Date(ms))

/** Fixtures of "my team" (or any followed team) on a Persian month calendar, with export to the phone's calendar. */
@Composable
fun CalendarScreen() {
    val ctx = LocalContext.current
    val nav = LocalNav.current
    val teams = buildList { Store.myTeam?.let { add(it.id to it.name) }; Store.teams.forEach { (id, n) -> if (none { it.first == id }) add(id to n) } }
    if (teams.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(14.dp)) {
            var picker by remember { mutableStateOf(false) }
            EmptyView("تقویم شما خالی است", Ic.cal, "برای دیدن تقویم، اول «تیم من» را انتخاب کنید یا تیمی را دنبال کنید.", "انتخاب تیم محبوب") { picker = true }
            if (picker) TeamPicker { picker = false }
        }
        return
    }
    var team by rememberSaveable { mutableIntStateOf(teams.first().first) }
    var monthOffset by rememberSaveable { mutableIntStateOf(0) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val l = rememberLoader(team) {
        Api.fm("teams", mapOf("id" to team, "ccode3" to "IRN"), 600_000).path("fixtures.allFixtures").a("fixtures").objs().map { j -> parseMatch(j, 0, j.o("tournament").s("name") ?: "") }
    }
    val accent = LocalAccent.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = BottomSpace)) {
        item { T("تقویم بازی‌ها", 16, FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp)) }
        if (teams.size > 1) item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                teams.forEach { (id, name) ->
                    val on = id == team
                    Row(Modifier.height(38.dp).clip(RoundedCornerShape(50)).background(if (on) accent else C.card).border(1.dp, if (on) Color.Transparent else C.line, RoundedCornerShape(50))
                        .clickable { team = id; selected = null }.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Crest(id, 20.dp); T(Clubs.get(id)?.fa ?: name, 13, FontWeight.ExtraBold, if (on) LocalAccentInk.current else C.ink, maxLines = 1)
                    }
                }
            }
        }
        val all = l.value
        if (all == null) { item { if (l.state is Load.Err) ErrorView({ l.state = Load.Loading; l.reload() }) else Skeleton(5) }; return@LazyColumn }
        val byDay = all.groupBy { pKey(it.time) }
        // the month shown: today's Persian month moved by monthOffset
        val first = persianCal(System.currentTimeMillis()).apply {
            set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 12); set(Calendar.MINUTE, 0); add(Calendar.MONTH, monthOffset)
        }
        val days = first.getActualMaximum(Calendar.DAY_OF_MONTH)
        val lead = (first.get(Calendar.DAY_OF_WEEK) - Calendar.SATURDAY + 7) % 7
        val monthMs = first.timeInMillis
        val inMonth = all.filter { val c = persianCal(it.time); c.get(Calendar.YEAR) == first.get(Calendar.YEAR) && c.get(Calendar.MONTH) == first.get(Calendar.MONTH) }
        item {
            Card {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBtn(Ic.back, "ماه قبل", size = 18.dp) { monthOffset--; selected = null }
                    T(android.icu.text.SimpleDateFormat("MMMM yyyy", FA).format(Date(monthMs)), 15, FontWeight.Black, modifier = Modifier.weight(1f), align = TextAlign.Center)
                    IconBtn(Ic.chev, "ماه بعد", size = 18.dp) { monthOffset++; selected = null }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                    listOf("ش", "ی", "د", "س", "چ", "پ", "ج").forEach { T(it, 12, FontWeight.Bold, C.mute, Modifier.weight(1f), align = TextAlign.Center) }
                }
                val today = pKey(System.currentTimeMillis())
                val cells = List(lead) { null } + (1..days).toList()
                cells.chunked(7).forEach { week ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp)) {
                        week.forEach { d ->
                            if (d == null) { Spacer(Modifier.weight(1f)); return@forEach }
                            val ms = monthMs + (d - 1) * 86_400_000L
                            val k = pKey(ms)
                            val ms0 = byDay[k].orEmpty()
                            val m = ms0.firstOrNull()
                            val on = selected == k
                            val isToday = k == today
                            Column(
                                Modifier.weight(1f).aspectRatio(0.82f).padding(2.dp).clip(RoundedCornerShape(12.dp))
                                    .background(when { on -> accent; m != null -> C.card2; else -> Color.Transparent })
                                    .border(1.dp, if (isToday && !on) accent else Color.Transparent, RoundedCornerShape(12.dp))
                                    .clickable(enabled = m != null) { selected = if (on) null else k },
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                            ) {
                                T("$d", 13, if (m != null) FontWeight.Black else FontWeight.Medium, if (on) LocalAccentInk.current else if (m != null) C.ink else C.mute)
                                if (m != null) {
                                    val opp = if (m.home.id == team) m.away.id else m.home.id
                                    Box { Crest(opp, 20.dp) }
                                    if (m.finished) {
                                        val my = if (m.home.id == team) m.home.score ?: 0 else m.away.score ?: 0
                                        val their = if (m.home.id == team) m.away.score ?: 0 else m.home.score ?: 0
                                        Box(Modifier.padding(top = 2.dp).size(6.dp).clip(CircleShape).background(if (my > their) C.win else if (my == their) C.draw else C.loss))
                                    }
                                }
                            }
                        }
                        repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally)) {
                    for ((c, t) in listOf(C.win to "برد", C.draw to "تساوی", C.loss to "باخت")) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(c)); T(t, 11, color = C.mute)
                    }
                }
            }
        }
        val list = (selected?.let { byDay[it] } ?: inMonth).sortedBy { it.time }
        item { SectionTitle(if (selected != null) persianDate(list.firstOrNull()?.time ?: 0, "EEEE d MMMM") else "بازی‌های این ماه") }
        item {
            if (list.isEmpty()) EmptyView("در این ماه بازی‌ای ثبت نشده", Ic.cal)
            else Card {
                list.forEachIndexed { i, m ->
                    MatchRow(m, { nav.match(m.id) }, showDate = true, sub = m.leagueName, star = false, first = i == 0)
                    if (!m.started && !m.cancelled && m.time > System.currentTimeMillis()) Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 10.dp)) {
                        Pill("افزودن به تقویم گوشی", PillKind.Accent, icon = Ic.cal, onClick = {
                            runCatching { ctx.startActivity(addEvent(m)) }
                        })
                    }
                }
            }
        }
        val upcoming = all.filter { !it.started && !it.cancelled && it.time > System.currentTimeMillis() }
        if (upcoming.size > 1) item {
            Box(Modifier.fillMaxWidth().padding(top = 14.dp), contentAlignment = Alignment.Center) {
                Pill("افزودن همه ${upcoming.size} بازی پیش رو به تقویم", PillKind.Accent, icon = Ic.cal, onClick = {
                    runCatching {
                        val dir = File(ctx.cacheDir, "ics").apply { mkdirs() }
                        val f = File(dir, "goallive-$team.ics").apply { writeText(ics(upcoming)) }
                        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
                        val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "text/calendar").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        ctx.startActivity(Intent.createChooser(view, "افزودن به تقویم"))
                    }
                })
            }
        }
    }
}

private fun title(m: MatchItem) = "⚽ ${m.home.name} - ${m.away.name}"

private fun addEvent(m: MatchItem) = Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
    .putExtra(CalendarContract.Events.TITLE, title(m))
    .putExtra(CalendarContract.Events.DESCRIPTION, "${m.leagueName}\n${Api.SITE}/#/match/${m.id}")
    .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, m.time)
    .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, m.time + 2 * 3_600_000L)

/** iCalendar file with every upcoming fixture (calendar apps import it in one go). */
private fun ics(list: List<MatchItem>): String {
    val f = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
    val esc = { s: String -> s.replace("\\", "\\\\").replace(",", "\\,").replace(";", "\\;") }
    return buildString {
        append("BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//GoalLive//FA\r\n")
        for (m in list) {
            append("BEGIN:VEVENT\r\nUID:goallive-${m.id}@goallive\r\nDTSTAMP:${f.format(Date())}\r\n")
            append("DTSTART:${f.format(Date(m.time))}\r\nDTEND:${f.format(Date(m.time + 2 * 3_600_000L))}\r\n")
            append("SUMMARY:${esc(title(m))}\r\nDESCRIPTION:${esc(m.leagueName)}\r\nEND:VEVENT\r\n")
        }
        append("END:VCALENDAR\r\n")
    }
}
