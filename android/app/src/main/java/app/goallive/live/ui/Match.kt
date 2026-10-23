package app.goallive.live.ui

import app.goallive.live.L

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.runtime.*
import androidx.compose.ui.draw.*
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.goallive.live.data.Api
import app.goallive.live.data.Clubs
import app.goallive.live.data.Comps
import app.goallive.live.data.Store
import app.goallive.live.data.a
import app.goallive.live.data.b
import app.goallive.live.data.cleanMinute
import app.goallive.live.data.d
import app.goallive.live.data.i
import app.goallive.live.data.o
import app.goallive.live.data.objs
import app.goallive.live.data.parseMatch
import app.goallive.live.data.parseTime
import app.goallive.live.data.path
import app.goallive.live.data.s
import app.goallive.live.data.strs
import app.goallive.live.data.tr
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Everything the match screen needs from matchDetails, read once per refresh. */
class MatchData(val d: JSONObject) {
    val header = d.o("header")
    val teams = header.a("teams").objs()
    val home = teams.getOrNull(0)
    val away = teams.getOrNull(1)
    val homeId = home.i("id") ?: 0
    val awayId = away.i("id") ?: 0
    val homeName = home.s("name") ?: ""
    val awayName = away.s("name") ?: ""
    val status = header.o("status")
    val general = d.o("general")
    val content = d.o("content")
    val facts = content.o("matchFacts")
    val started = status.b("started")
    val finished = status.b("finished")
    val cancelled = status.b("cancelled")
    val live = started && !finished && !cancelled
    val time = parseTime(status.s("utcTime"))
    val minute = cleanMinute(status.o("liveTime").s("short"))
    val leagueId = general.i("parentLeagueId") ?: general.i("leagueId") ?: 0
    val matchId = general.i("matchId") ?: 0
    val events = facts.path("events").a("events").objs()
    val shots = content.path("shotmap").a("shots").objs()
    val hasStats = (content.path("stats.Periods")?.length() ?: 0) > 0
    val hasLineup = (content.path("lineup.homeTeam").a("starters")?.length() ?: 0) > 0
    val hasTable = content.o("table").i("leagueId") != null
    val hasH2h = (content.o("h2h").a("matches")?.length() ?: 0) > 0
    /** FotMob writes minute-by-minute text only for some matches; without it the text view has nothing to show. */
    val hasTicker = content.o("liveticker")?.s("langs")?.split(',')?.any { it.trim() == "en" } == true
    val hasPlayers = content.o("playerStats")?.let { ps -> ps.keys().asSequence().any { (ps.optJSONObject(it)?.a("stats")?.length() ?: 0) > 0 } } == true

    /** Team colors for stats and charts (site: teamColors()), falling back to blue/orange when missing or too alike. */
    val homeColor: Color
    val awayColor: Color
    init {
        val dm = general.path("teamColors.darkMode")
        val h = parseColor(dm.s("home")); val a = parseColor(dm.s("away"))
        val close = h != null && a != null && abs(lum(h) - lum(a)) < 0.08f && abs(h.red - a.red) < 0.07f
        homeColor = if (h == null || a == null || close) C.home else h
        awayColor = if (h == null || a == null || close) C.away else a
    }
}

@Composable
fun MatchScreen(id: Int) {
    val url = Api.fmUrl("matchDetails", mapOf("matchId" to id))
    val l = rememberLoader(
        id,
        peek = { (Api.peek(url) as? JSONObject)?.let(::MatchData) },
        poll = { m -> if (m.live) 8_000L else if (!m.finished && m.time - System.currentTimeMillis() < 2 * 3_600_000) 60_000L else null },
    ) { MatchData(Api.fm("matchDetails", mapOf("matchId" to id), 5_000)) }
    Box(Modifier.fillMaxSize()) {
        Refreshable(l) { LoadBox(l, 6) { m -> MatchBody(m) } }
        l.value?.let { m -> GoalCelebration(rememberGoal(m)) }
    }
}

@Composable
private fun MatchBody(m: MatchData) {
    var tab by rememberSaveable { mutableStateOf("summary") }
    // fewer, bigger tabs: related views live together behind a small switch inside the tab
    var liveView by rememberSaveable { mutableStateOf("pitch") }
    var statsView by rememberSaveable { mutableStateOf("team") }
    val statViews = buildList {
        if (m.hasStats) add("team" to L("آمار تیمی"))
        if (m.shots.isNotEmpty()) add("shots" to L("شوت‌ها"))
        if (m.hasPlayers) add("players" to L("بازیکنان"))
    }
    val tabs = buildList {
        add("summary" to L("خلاصه"))
        // the live pitch only while the ball is rolling; afterwards just the written commentary, when there is one
        if (m.live) add("live" to L("زنده")) else if (m.finished && m.hasTicker) add("text" to L("گزارش متنی"))
        if (statViews.isNotEmpty()) add("stats" to L("آمار"))
        if (m.hasLineup) add("lineup" to L("ترکیب"))
        if (!m.cancelled) add("coach" to L("🧠 مربی تو"))
        if (m.hasTable) add("table" to L("جدول"))
        if (m.hasH2h) add("h2h" to L("رودررو"))
    }
    if (tabs.none { it.first == tab }) tab = "summary"
    if (statViews.none { it.first == statsView }) statsView = statViews.firstOrNull()?.first ?: "team"
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = BottomSpace)) {
        item { Hero(m) }
        stickyTabs(tabs, tab) { tab = it }
        item {
            Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (tab) {
                    "summary" -> Summary(m)
                    "live" -> {
                        if (m.hasTicker) Seg(listOf("pitch" to L("زمین زنده"), "text" to L("گزارش متنی")), liveView, icons = mapOf("pitch" to Ic.stadium, "text" to Ic.list)) { liveView = it }
                        if (liveView == "pitch" || !m.hasTicker) LivePitch(m) else Commentary(m)
                    }
                    "text" -> Commentary(m)
                    "stats" -> {
                        if (statViews.size > 1) Seg(statViews, statsView) { statsView = it }
                        when (statsView) {
                            "shots" -> ShotsTab(m)
                            "players" -> PlayersTab(m)
                            else -> StatsTab(m)
                        }
                    }
                    "lineup" -> LineupTab(m)
                    "coach" -> CoachTab(m)
                    "table" -> MatchTable(m)
                    "h2h" -> H2h(m, 50)
                }
            }
        }
    }
}

/** Tabs that stay at the top while the page scrolls (".tabs" is sticky on the site). */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun androidx.compose.foundation.lazy.LazyListScope.stickyTabs(tabs: List<Pair<String, String>>, tab: String, onSelect: (String) -> Unit) {
    stickyHeader {
        Box(Modifier.fillMaxWidth().background(C.bg.copy(alpha = 0.95f)).padding(vertical = 6.dp)) { Tabs(tabs, tab, onSelect = onSelect) }
    }
}

/* ---------- hero (".hero-m") ---------- */

@Composable
private fun Hero(m: MatchData) {
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val ch = Clubs.get(m.homeId); val ca = Clubs.get(m.awayId)
    val c1 = ch?.let { Color(it.c1) } ?: m.homeColor
    val c2 = ca?.let { Color(it.c1) } ?: m.awayColor
    val pattern = ch?.pattern ?: ca?.pattern
    var watch by remember { mutableStateOf(false) }
    val boardIn = remember { Animatable(0.6f) }
    LaunchedEffect(Unit) { boardIn.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)) }

    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(bottomStart = 30.dp, bottomEnd = 30.dp)).drawBehind {
            cssLinear(242f, 0f to c1, 0.48f to c1, 0.52f to c2, 1f to c2)
            if (pattern != null) drawPattern(pattern, 0.16f)
            drawRect(Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.15f), Color.Black.copy(alpha = 0.72f)), Offset(size.width / 2, size.height * 0.45f), size.width * 0.75f))
            drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.25f), Color.Black.copy(alpha = 0.6f))))
            pitchLines(this)
        }.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 20.dp),
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
                val round = m.general.s("leagueRoundName")
                Row(Modifier.weight(1f, false).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.28f))
                    .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(50)).clickable { nav.league(m.leagueId) }
                    .padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    LeagueLogo(m.leagueId, 16.dp)
                    T(leagueName(m.leagueId, m.general.s("leagueName") ?: "") + (round?.let { if (it.all(Char::isDigit)) L(" · هفته {0}", fa(it)) else " · " + tr(it) } ?: ""), 12, FontWeight.ExtraBold, Color.White, maxLines = 1)
                }
                Spacer(Modifier.weight(1f))
                StarButton(m.matchId in Store.matches, { Store.toggleMatch(m.matchId) }, 18.dp, Color.White, 40.dp)
                Spacer(Modifier.width(2.dp))
                // the match as a story-size picture with the app's name on it
                val shareScope = rememberCoroutineScope()
                IconBtn(Ic.share, L("اشتراک‌گذاری"), Color.White, Color.Black.copy(alpha = 0.2f), 18.dp) {
                    shareScope.launch { shareMatchCard(ctx, m) }
                }
            }
            Comps.get(m.leagueId)?.let { c ->
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Ic.starOn, null, Modifier.size(20.dp), tint = Color(c.accent))
                    T(c.fa, 12, FontWeight.Black, Color(c.accent))
                }
            }
            Clubs.derby(m.homeId, m.awayId)?.let { T("⚡ $it ⚡", 13, FontWeight.Black, C.gold, Modifier.padding(top = 10.dp)) }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                HeroTeam(m.homeId, m.homeName, ch?.fa, Modifier.weight(1f), "crest-${m.matchId}-h")
                Column(Modifier.scale(boardIn.value), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val board = Modifier.clip(RoundedCornerShape(18.dp)).background(Color.Black.copy(alpha = 0.42f)).border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(18.dp))
                    if (m.started && !m.cancelled) {
                        Box(board.padding(horizontal = 14.dp, vertical = 2.dp)) { ScoreText(m.home.i("score"), m.away.i("score"), 38, Color.White, sepColor = Color.White.copy(alpha = 0.4f)) }
                        when {
                            m.live && m.minute.substringBefore('+').toIntOrNull() != null -> MinuteRing(m.minute)
                            m.live -> Pill(if (m.minute == "HT") L("بین دو نیمه") else m.minute, PillKind.Live, dot = true)
                            else -> {
                                // after a shoot-out FotMob keeps its score in the reason: [home, away]
                                val pens = m.status.o("reason").a("penalties")?.takeIf { it.length() == 2 }
                                DarkPill(if (pens != null) L("پنالتی ") + fa("${pens.optInt(0)} - ${pens.optInt(1)}") else tr(m.status.o("reason").s("short") ?: "FT"))
                            }
                        }
                    } else {
                        Box(board.padding(horizontal = 16.dp, vertical = 8.dp)) { Text(if (m.cancelled) "–" else timeOf(m.time), color = Color.White, style = vz(30.sp, FontWeight.Black)) }
                        DarkPill(if (m.cancelled) tr(m.status.o("reason").s("short") ?: "Canc") else countdown(m.time))
                    }
                    T(persianDate(m.time, "EEEE d MMMM"), 12, FontWeight.Bold, Color.White.copy(alpha = 0.85f))
                }
                HeroTeam(m.awayId, m.awayName, ca?.fa, Modifier.weight(1f), "crest-${m.matchId}-a")
            }
            val goals = m.events.filter { it.s("type") == "Goal" }
            val reds = m.events.filter { it.s("type") == "Card" && (it.s("card") == "Red" || it.s("card") == "YellowRed") }
            if (goals.isNotEmpty() || reds.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().padding(top = 14.dp)) {
                    Scorers(goals.filter { it.b("isHome") }, reds.filter { it.b("isHome") }, Modifier.weight(1f))
                    Box(Modifier.width(22.dp), contentAlignment = Alignment.TopCenter) { if (goals.isNotEmpty()) Icon(Ic.ball, null, Modifier.size(14.dp), tint = Color.White.copy(alpha = 0.7f)) }
                    Scorers(goals.filter { !it.b("isHome") }, reds.filter { !it.b("isHome") }, Modifier.weight(1f))
                }
            }
            listOfNotNull(m.status.s("aggregatedStr")?.let { L("مجموع دو بازی: {0}", it) }).firstOrNull()?.let {
                T(it, 12, color = Color.White.copy(alpha = 0.9f), modifier = Modifier.padding(top = 10.dp))
            }
            if (m.live || (!m.started && !m.cancelled && m.time - System.currentTimeMillis() < 3 * 3_600_000)) VoiceButton(m)
            if (m.live || (!m.started && !m.cancelled && m.time - System.currentTimeMillis() < 3 * 3_600_000)) {
                Row(Modifier.padding(top = 12.dp).height(44.dp).glow(C.live.copy(alpha = 0.45f), 10.dp, RoundedCornerShape(50)).clip(RoundedCornerShape(50))
                    .background(C.live).clickable { watch = true }.padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Ic.live, null, Modifier.size(18.dp), tint = Color.White)
                    T(L("تماشای زنده"), 14, FontWeight.Black, Color.White)
                }
            }
        }
    }
    if (watch) WatchDialog(m) { watch = false }
}

private fun pitchLines(s: DrawScope) = with(s) {
    val w = size.width; val h = size.height
    val st = Stroke(1.2.dp.toPx())
    val c = Color.White.copy(alpha = 0.28f)
    val sx = w / 400f; val sy = h / 240f
    drawRoundRect(c, Offset(10 * sx, 10 * sy), Size(380 * sx, 220 * sy), CornerRadius(4 * sx), style = st)
    drawLine(c, Offset(200 * sx, 10 * sy), Offset(200 * sx, 230 * sy), st.width)
    drawCircle(c, 34 * sx, Offset(200 * sx, 120 * sy), style = st)
    drawRect(c, Offset(10 * sx, 62 * sy), Size(56 * sx, 116 * sy), style = st)
    drawRect(c, Offset(334 * sx, 62 * sy), Size(56 * sx, 116 * sy), style = st)
}

@Composable
private fun DarkPill(text: String) {
    Box(Modifier.height(26.dp).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.4f)).border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(50))
        .padding(horizontal = 10.dp), contentAlignment = Alignment.Center) { T(text, 11, FontWeight.ExtraBold, Color.White, maxLines = 1) }
}

/** ".ring": live minute as a red progress ring. */
@Composable
private fun MinuteRing(minute: String) {
    val mins = minute.substringBefore('+').toIntOrNull() ?: 0
    val full = if (mins > 90) 120f else 90f
    Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val st = Stroke(4.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
            val inset = 2.dp.toPx()
            drawArc(Color.White.copy(alpha = 0.15f), 0f, 360f, false, Offset(inset, inset), Size(size.width - 2 * inset, size.height - 2 * inset), style = st)
            drawArc(C.live, -90f, 360f * min(mins.toFloat(), full) / full, false, Offset(inset, inset), Size(size.width - 2 * inset, size.height - 2 * inset), style = st)
        }
        Ltr("$minute'".replace("''", "'"), style = vz(13.sp, FontWeight.ExtraBold), color = Color.White)
    }
}

@Composable
private fun HeroTeam(id: Int, name: String, faName: String?, modifier: Modifier, shareKey: String) {
    val nav = LocalNav.current
    Column(modifier.clip(RoundedCornerShape(16.dp)).clickable { nav.team(id) }.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Crest(id, 68.dp, large = true, shareKey = shareKey)
        TeamText(id, name, size = 16, w = FontWeight.ExtraBold, color = Color.White, maxLines = 2, align = TextAlign.Center)
    }
}

@Composable
private fun Scorers(goals: List<JSONObject>, reds: List<JSONObject>, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        goals.groupBy { it.o("player").i("id") ?: it.s("nameStr") }.values.forEach { gs ->
            val p = gs.first()
            val name = p.o("player").s("lastName") ?: p.s("nameStr") ?: p.o("player").s("name") ?: ""
            val mins = gs.joinToString(L("، ")) { minuteText(it) + "'" + if (it.b("ownGoal")) L(" (گل به خودی)") else if (isPen(it)) L(" (پ)") else "" }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Ltr(name, style = vz(12.sp, FontWeight.Medium), color = Color.White.copy(alpha = 0.95f))
                Ltr(mins, style = vz(12.sp), color = Color.White.copy(alpha = 0.95f))
            }
        }
        reds.forEach { r ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(8.dp, 11.dp).clip(RoundedCornerShape(2.dp)).background(C.live))
                Ltr((r.o("player").s("lastName") ?: r.s("nameStr") ?: "") + " " + minuteText(r) + "'", style = vz(12.sp), color = Color.White.copy(alpha = 0.95f))
            }
        }
    }
}


@Composable
private fun WatchDialog(m: MatchData, onClose: () -> Unit) {
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = onClose, containerColor = C.cardSolid,
        title = { T(L("تماشای زنده"), 18, FontWeight.Black) },
        text = {
            Column {
                T("${teamName(m.homeId, m.homeName)} - ${teamName(m.awayId, m.awayName)}", 12, FontWeight.ExtraBold, C.mute)
                STATIONS.forEachIndexed { i, st ->
                    val name = st.name; val sub = st.sub
                    ListRow(first = i == 0, onClick = { onClose(); openStation(ctx, st) },
                        pad = PaddingValues(vertical = 11.dp)) {
                        Icon(Ic.live, null, Modifier.size(22.dp), tint = C.live)
                        Column(Modifier.weight(1f)) { T(name, 14, FontWeight.Bold); T(sub, 12, color = C.mute) }
                        Icon(Ic.chev, null, Modifier.size(18.dp), tint = C.mute)
                    }
                }
                T(L("برنامه رسمی هر سرویس باز می‌شود (یا سایتش در مرورگر)؛ با حساب خودتان وارد شوید. هر بازی فقط روی سرویسی پخش می‌شود که حق پخش آن را دارد."), 12, color = C.mute, modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = { TextButton(onClick = onClose) { T(L("بستن"), 14, FontWeight.Bold, LocalAccent.current) } },
    )
}

fun minuteText(e: JSONObject): String {
    val t = (e.s("timeStr") ?: e.s("time") ?: "").replace(" ", "")
    val o = e.s("overloadTime")
    return if (o != null && o != "0" && !t.contains('+')) "$t+$o" else t
}

fun isPen(e: JSONObject) = e.s("goalDescriptionKey") == "penalty" || e.s("suffixKey") == "penalty_short" ||
    (e.s("suffix") ?: "").contains("pen", true) || e.path("shotmapEvent").s("situation") == "Penalty"

/* ---------- summary ---------- */

@Composable
private fun Summary(m: MatchData) {
    val nav = LocalNav.current
    PredictCard(m)
    TacticalPreview(m)
    if (m.started || m.finished) Card {
        CardTitle(L("خلاصه و گل‌ها"))
        Box(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) { Highlights(m, compact = true) }
    }
    // what happened comes first after the video
    if (m.started) EventsCard(m)
    MatchStory(m)
    if (m.started) AttackZones(m)
    // before kick-off (and during the match) the radar is the most useful thing on this tab
    if (!m.finished && !m.cancelled && m.homeId > 0 && m.awayId > 0) OpponentRadar(m)
    val momentum = m.content.path("momentum.main").a("data").objs()
    if (momentum.size > 5) Card {
        CardTitle(L("مومنتوم بازی")) { Legend(m) }
        Momentum(momentum, m.homeColor, m.awayColor)
        Spacer(Modifier.height(14.dp))
    }
    if (m.started && !m.cancelled) WinProbability(m)
    m.facts.o("playerOfTheMatch")?.takeIf { (it.i("id") ?: 0) > 0 }?.let { p ->
        Card {
            Row(Modifier.fillMaxWidth().drawBehind {
                drawRect(Brush.radialGradient(listOf(C.gold.copy(alpha = 0.18f), Color.Transparent), Offset(size.width, size.height / 2), size.width * 0.6f))
            }.clickable { nav.player(p.i("id")!!) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.glow(C.gold.copy(alpha = 0.4f), 8.dp, CircleShape)) { Face(p.i("id")!!, 60.dp, C.gold) }
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Ic.starOn, null, Modifier.size(13.dp), tint = C.gold)
                        T(L("بهترین بازیکن زمین"), 11, FontWeight.Black, C.gold)
                    }
                    Ltr(p.o("name").s("fullName") ?: "", style = lat(18.sp, FontWeight.ExtraBold))
                    T(p.s("teamName") ?: "", 12, color = C.mute)
                }
                Rating(p.o("rating").d("num") ?: p.d("rating"))
            }
        }
    }
    val tp = m.facts.o("topPlayers")
    if ((tp.a("homeTopPlayers")?.length() ?: 0) > 0) Card {
        CardTitle(L("بهترین‌های هر تیم"))
        listOf(m.homeId to m.homeName to tp.a("homeTopPlayers"), m.awayId to m.awayName to tp.a("awayTopPlayers")).forEach { (team, arr) ->
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Crest(team.first, 18.dp); T(team.second, 13, FontWeight.ExtraBold)
            }
            arr.objs().forEach { p ->
                val pid = p.i("playerId") ?: 0
                ListRow(onClick = { nav.player(pid) }) {
                    Face(pid)
                    Column(Modifier.weight(1f)) {
                        Ltr(p.o("name").s("lastName") ?: p.o("name").s("fullName") ?: "")
                        T(tr(p.o("positionLabel").s("label")), 12, color = C.mute)
                    }
                    Rating(p.d("playerRating"))
                }
            }
        }
    }
    val ins = m.facts.a("insights").objs()
    if (ins.isNotEmpty()) Card {
        CardTitle(L("نکته‌های آماری"))
        ins.forEach { i ->
            Divider()
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).height(androidx.compose.foundation.layout.IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.width(4.dp).fillMaxSize().clip(RoundedCornerShape(4.dp)).background(parseColor(i.s("color")) ?: LocalAccent.current))
                (i.i("teamId") ?: 0).takeIf { it > 0 }?.let { Crest(it, 18.dp) }
                if (app.goallive.live.Lang.en) Ltr(insightText(i, m), Modifier.weight(1f), style = vz(13.sp), maxLines = 6)
                else T(insightText(i, m), 13, modifier = Modifier.weight(1f))
            }
        }
    }
    val form = m.facts.a("teamForm")
    if (form != null && form.length() == 2) Card {
        CardTitle(L("فرم ۵ بازی اخیر"))
        listOf(m.homeId to m.homeName, m.awayId to m.awayName).forEachIndexed { i, (id, name) ->
            ListRow(first = i == 0) { Crest(id); T(teamName(id, name), 14, FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1); FormChips(form.optJSONArray(i)) }
        }
    }
    InfoCard(m)
    if (!m.started && m.hasH2h) H2h(m, 3)
}

@Composable
private fun Legend(m: MatchData) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        for ((c, n) in listOf(m.homeColor to m.homeName, m.awayColor to m.awayName)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(c))
                Ltr(n, style = vz(11.sp, FontWeight.Bold), color = C.mute)
            }
        }
    }
}

@Composable
fun FormChips(arr: JSONArray?) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        arr.objs().take(5).forEach { f ->
            val r = f.s("resultString") ?: when (f.i("result")) { 1 -> "W"; 0 -> "D"; -1 -> "L"; else -> "" }
            val c = when (r) { "W" -> C.win; "L" -> C.loss; else -> C.draw }
            Box(Modifier.size(24.dp).clip(RoundedCornerShape(8.dp)).background(c), contentAlignment = Alignment.Center) {
                T(when (r) { "W" -> L("ب"); "L" -> L("ش"); else -> L("م") }, 11, FontWeight.Black, Color.White)
            }
        }
    }
}

@Composable
private fun Momentum(data: List<JSONObject>, homeColor: Color, awayColor: Color) {
    val maxMin = max(90.0, data.maxOf { it.d("minute") ?: 0.0 })
    val grow = remember { Animatable(0f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, tween(600)) }
    Column(Modifier.padding(horizontal = 16.dp)) {
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val mid = size.height / 2
            val bw = size.width / (maxMin.toFloat() + 1)
            // timeline runs left to right like on the site (direction:ltr)
            drawLine(C.line, Offset(0f, mid), Offset(size.width, mid), 2f)
            val ht = 45f / (maxMin.toFloat() + 1) * size.width
            drawLine(C.line, Offset(ht, 4f), Offset(ht, size.height - 4f), 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 8f)))
            for (p in data) {
                val v = (p.d("value") ?: 0.0).toFloat()
                val h = abs(v) / 100f * (mid - 4) * grow.value
                val x = (p.d("minute") ?: 0.0).toFloat() / (maxMin.toFloat() + 1) * size.width
                drawRoundRect(if (v >= 0) homeColor else awayColor, Offset(x, if (v >= 0) mid - h else mid), Size(max(bw - 1f, 1.5f), h), CornerRadius(1.5f))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Ltr("${maxMin.toInt()}'", style = vz(11.sp), color = C.mute); Spacer(Modifier.weight(1f))
            Ltr("45'", style = vz(11.sp), color = C.mute); Spacer(Modifier.weight(1f))
            Ltr("0'", style = vz(11.sp), color = C.mute)
        }
    }
}

/** The site's win probability: Poisson model on xG so far and the score, as a stacked chart over the match. */
@Composable
private fun WinProbability(m: MatchData) {
    val goals = m.events.filter { it.s("type") == "Goal" }
    val now = if (m.finished) 95 else max(1, m.minute.substringBefore('+').filter(Char::isDigit).toIntOrNull() ?: 1)
    val end = max(93, now + 2)
    val base = 1.35 / 90
    fun poisson(l: Double): DoubleArray { val p = DoubleArray(11); p[0] = exp(-l); for (k in 1..10) p[k] = p[k - 1] * l / k; return p }
    fun probs(gh: Int, ga: Int, lh: Double, la: Double): Triple<Double, Double, Double> {
        val ph = poisson(lh); val pa = poisson(la)
        var w = 0.0; var dr = 0.0; var lo = 0.0
        for (i in 0..10) for (j in 0..10) { val p = ph[i] * pa[j]; if (gh + i > ga + j) w += p else if (gh + i == ga + j) dr += p else lo += p }
        val t = w + dr + lo
        return Triple(w / t, dr / t, lo / t)
    }
    val series = remember(m.d) {
        val list = ArrayList<Triple<Double, Double, Double>>()
        for (t in 0..min(now, end)) {
            val xh = m.shots.filter { (it.i("min") ?: 0) <= t && it.i("teamId") == m.homeId }.sumOf { it.d("expectedGoals") ?: 0.0 }
            val xa = m.shots.filter { (it.i("min") ?: 0) <= t && it.i("teamId") != m.homeId }.sumOf { it.d("expectedGoals") ?: 0.0 }
            val gh = goals.count { (it.i("time") ?: 0) <= t && it.b("isHome") }
            val ga = goals.count { (it.i("time") ?: 0) <= t && !it.b("isHome") }
            val w = t / (t + 35.0); val left = max(0, end - t)
            val rh = (1 - w) * base + w * (xh / max(t, 1)); val ra = (1 - w) * base + w * (xa / max(t, 1))
            list.add(probs(gh, ga, rh * left, ra * left))
        }
        if (m.finished) {
            val h = m.home.i("score") ?: 0; val a = m.away.i("score") ?: 0
            list.add(if (h > a) Triple(1.0, 0.0, 0.0) else if (h == a) Triple(0.0, 1.0, 0.0) else Triple(0.0, 0.0, 1.0))
        }
        list
    }
    // after the final whistle the outcome is known: show the chances at kick-off instead of 100٪ for the result
    val last = if (m.finished) series.first() else series.last()
    Card {
        CardTitle(if (m.finished) L("احتمال برد پیش از بازی") else L("احتمال برد")) {
            T(if (m.finished) L("و تغییرش در طول بازی") else L("برآورد گل‌لایو از روی نتیجه و xG"), 11, color = C.mute, maxLines = 1)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            for ((v, label, c) in listOf(Triple(last.first, m.homeName, m.homeColor), Triple(last.second, L("مساوی"), C.ink), Triple(last.third, m.awayName, m.awayColor))) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    T(L("{0}٪", (v * 100).toInt()), 24, FontWeight.Black, c)
                    Ltr(label, style = vz(11.sp), color = C.mute)
                }
            }
        }
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp)) {
            Canvas(Modifier.fillMaxWidth().height(110.dp).clip(RoundedCornerShape(12.dp))) {
                val n = max(series.size - 1, 1)
                val x = { i: Int -> i.toFloat() / n * size.width }
                fun area(top: (Triple<Double, Double, Double>) -> Double, bottom: (Triple<Double, Double, Double>) -> Double, c: Color, alpha: Float) {
                    val p = Path()
                    series.forEachIndexed { i, s -> val y = (top(s) * size.height).toFloat(); if (i == 0) p.moveTo(x(i), y) else p.lineTo(x(i), y) }
                    for (i in series.indices.reversed()) p.lineTo(x(i), (bottom(series[i]) * size.height).toFloat())
                    p.close(); drawPath(p, c.copy(alpha = alpha))
                }
                area({ 0.0 }, { it.third }, m.awayColor, 0.85f)
                area({ it.third }, { it.third + it.second }, C.draw, 0.55f)
                area({ it.third + it.second }, { 1.0 }, m.homeColor, 0.85f)
                val h = x(min(45, n))
                drawLine(Color.White.copy(alpha = 0.35f), Offset(h, 0f), Offset(h, size.height), 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
            }
            Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
                Ltr(if (m.finished) "FT" else "${m.minute}'", style = vz(11.sp), color = C.mute); Spacer(Modifier.weight(1f)); Ltr("0'", style = vz(11.sp), color = C.mute)
            }
        }
    }
}

@Composable
private fun InfoCard(m: MatchData) {
    val ib = m.facts.o("infoBox")
    val cells = buildList {
        add(Triple(Ic.cal, L("زمان"), "${persianDate(m.time, "EEEE d MMMM yyyy")} · ${timeOf(m.time)}"))
        ib.o("Tournament")?.let { t ->
            val r = t.s("roundName")
            add(Triple(Ic.trophy, L("رقابت"), leagueName(t.i("id") ?: m.leagueId, t.s("leagueName") ?: "") + (r?.let { if (it.all(Char::isDigit)) L(" · هفته {0}", fa(it)) else " · " + tr(it) } ?: "")))
        }
        ib.o("Stadium")?.let { s ->
            add(Triple(Ic.stadium, L("ورزشگاه"), listOfNotNull(s.s("name"), s.s("city")).joinToString(L("، ")) + (s.i("capacity")?.let { L(" · ظرفیت {0}", "%,d".format(it)) } ?: "") +
                (s.s("surface")?.let { if (it == "grass") L(" · چمن طبیعی") else L(" · چمن مصنوعی") } ?: "")))
        }
        ib.o("Referee")?.s("text")?.let { add(Triple(Ic.whistle, L("داور"), it + (ib.o("Referee").s("country")?.let { c -> " · $c" } ?: ""))) }
        ib.i("Attendance")?.let { add(Triple(Ic.people, L("تماشاگر"), "%,d".format(it))) }
        m.content.o("weather")?.let { w -> w.d("temperature")?.let { t -> add(Triple(Ic.cloud, L("آب‌وهوا"), "${t.toInt()}° · ${tr(w.s("defaultTitle") ?: w.s("description"))}")) } }
    }
    Card {
        CardTitle(L("اطلاعات بازی"))
        cells.forEach { (icon, k, v) ->
            Divider()
            Row(Modifier.fillMaxWidth().background(C.cardSolid).padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(icon, null, Modifier.padding(top = 2.dp).size(18.dp), tint = LocalAccent.current)
                Column { T(k, 11, color = C.mute); T(v, 13, FontWeight.Bold) }
            }
        }
    }
}

/* ---------- live commentary ---------- */

private val LTC = mapOf(
    "G" to Triple("⚽", L("گل!"), "goal"), "AS" to Triple("🅰️", L("پاس گل"), ""), "YC" to Triple("🟨", L("کارت زرد"), ""), "RC" to Triple("🟥", L("کارت قرمز"), "red"),
    "Y2C" to Triple("🟥", L("کارت زرد دوم"), "red"), "SI" to Triple("🔁", L("تعویض"), ""), "highlight" to Triple("✨", L("لحظه حساس"), "hl"),
    "post_match summary" to Triple("🏁", L("گزارش پایان بازی"), "sum"), "half_time summary" to Triple("⏸️", L("گزارش بین دو نیمه"), "sum"),
    "team news" to Triple("📋", L("اخبار ترکیب"), "sum"), "PM" to Triple("❌", L("پنالتی از دست رفت"), ""), "PG" to Triple("⚽", L("گل پنالتی"), "goal"),
    "OG" to Triple("⚽", L("گل به خودی"), "goal"),
)

@Composable
private fun Commentary(m: MatchData) {
    val l = rememberLoader(m.matchId, poll = { if (m.live) 30_000L else null }) {
        Api.fm("ltc", mapOf("matchId" to m.matchId, "teams" to JSONArray(listOf(m.homeName, m.awayName)).toString()), 10_000)
    }
    LoadBox(l, 6) { r ->
        val evs = r.a("events").objs()
        if (evs.isEmpty()) { EmptyView(L("گزارش لحظه‌به‌لحظه برای این بازی منتشر نشده"), Ic.list); return@LoadBox }
        val accent = LocalAccent.current
        // FotMob writes it in English only: in Persian each line is translated (once) and shown as soon as it is ready
        val persian = !app.goallive.live.Lang.en
        var fa by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
        if (persian) LaunchedEffect(evs.size) {
            val texts = evs.mapNotNull { it.s("text") }
            fa = texts.zip(app.goallive.live.data.Translate.toPersian(texts)).toMap()
        }
        Card {
            CardTitle(L("گزارش لحظه‌به‌لحظه")) {
                if (m.live) Pill(L("زنده"), PillKind.Live, dot = true)
            }
            evs.forEach { e ->
                val (ic, label, cls) = LTC[e.s("type")] ?: Triple("", "", "")
                val t = e.o("time")?.let { tm -> (tm.s("main") ?: "").filter(Char::isDigit) + "'" + (tm.s("added")?.takeIf { it != "0" }?.let { "+$it" } ?: "") } ?: ""
                val side = when (e.s("teamEvent")) { "home" -> teamName(m.homeId, m.homeName); "away" -> teamName(m.awayId, m.awayName); else -> null }
                Divider()
                Row(Modifier.fillMaxWidth().background(
                    when (cls) { "goal" -> Brush.horizontalGradient(listOf(accent.copy(alpha = 0.14f), Color.Transparent)); "sum" -> Brush.horizontalGradient(listOf(C.card2, C.card2)); else -> Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent)) })
                    .padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.width(46.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (t.length > 1) Ltr(t, style = vz(12.sp, FontWeight.ExtraBold), color = C.mute)
                        if (ic.isNotEmpty()) Text(ic, fontSize = 18.sp)
                    }
                    Column(Modifier.weight(1f)) {
                        if (label.isNotEmpty()) {
                            val players = e.a("players").objs().mapNotNull { it.s("name")?.let(::personName) }
                            T(label + (if (players.isNotEmpty() && cls != "sum") " · " + players.joinToString(" ← ") else "") + (if (side != null && cls != "sum") " ($side)" else ""),
                                13, FontWeight.Black, when (cls) { "goal" -> accent; "red" -> C.live; "hl" -> C.gold; else -> C.ink })
                        }
                        val text = e.s("text") ?: ""
                        val shown = if (persian) fa[text] ?: app.goallive.live.data.Translate.cached(text) else null
                        if (shown != null) T(shown, if (cls == "sum") 14 else 13, color = C.ink.copy(alpha = 0.88f))
                        else Ltr(text, style = vz(if (cls == "sum") 14.sp else 13.sp).copy(lineHeight = 22.sp), color = C.ink.copy(alpha = if (persian) 0.5f else 0.88f), maxLines = 40, align = TextAlign.Left)
                    }
                }
            }
        }
    }
}

/* ---------- stats ---------- */

@Composable
private fun StatsTab(m: MatchData) {
    val periods = m.content.path("stats.Periods")!!
    val keys = periods.keys().asSequence().toList()
    var period by rememberSaveable { mutableStateOf(keys.first()) }
    if (period !in keys) period = keys.first()
    val labels = mapOf("All" to L("کل بازی"), "FirstHalf" to L("نیمه اول"), "SecondHalf" to L("نیمه دوم"))
    if (keys.size > 1) Seg(keys.map { it to (labels[it] ?: it) }, period) { period = it }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Legend(m) }
    periods.o(period).a("stats").objs().forEach { g ->
        Card {
            CardTitle(tr(g.s("title")))
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) { g.a("stats").objs().forEach { st -> StatRow(st, m.homeColor, m.awayColor) } }
        }
    }
}

@Composable
private fun StatRow(st: JSONObject, homeColor: Color, awayColor: Color) {
    if (st.s("type") == "title") { T(tr(st.s("title")), 12, FontWeight.ExtraBold, C.mute, Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp), align = TextAlign.Center); return }
    val v = st.a("stats").strs()
    if (v.size < 2) return
    val raw = st.a("rawStats").objs()
    fun value(idx: Int) = raw.getOrNull(idx).d("value") ?: v[idx].substringBefore(' ').replace("%", "").toDoubleOrNull() ?: 0.0
    val h = value(0); val a = value(1)
    val hi = st.s("highlighted")
    if (st.s("type") == "graph" || st.s("key") == "BallPossesion") {
        Column(Modifier.padding(vertical = 9.dp)) {
            T(tr(st.s("title")), 13, FontWeight.SemiBold, modifier = Modifier.fillMaxWidth(), align = TextAlign.Center)
            Row(Modifier.fillMaxWidth().padding(top = 6.dp).height(34.dp).clip(RoundedCornerShape(12.dp)), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Box(Modifier.weight(max(h, 1.0).toFloat()).fillMaxSize().background(homeColor).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                    T(L("{0}٪", h.toInt()), 13, FontWeight.Black, inkOn(homeColor))
                }
                Box(Modifier.weight(max(a, 1.0).toFloat()).fillMaxSize().background(awayColor).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterEnd) {
                    T(L("{0}٪", a.toInt()), 13, FontWeight.Black, inkOn(awayColor))
                }
            }
        }
        return
    }
    val tot = (abs(h) + abs(a)).takeIf { it > 0 } ?: 1.0
    Column(Modifier.padding(vertical = 9.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatValue(v[0], hi == "home", homeColor)
            T(tr(st.s("title")), 13, FontWeight.SemiBold, modifier = Modifier.weight(1f), align = TextAlign.Center)
            StatValue(v[1], hi == "away", awayColor)
        }
        Row(Modifier.fillMaxWidth().padding(top = 7.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            // each bar is anchored on its own outer edge, like the site
            Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(6.dp)).background(C.card2), contentAlignment = Alignment.CenterStart) {
                Box(Modifier.fillMaxWidth((h / tot).toFloat().coerceIn(0f, 1f)).fillMaxSize().clip(RoundedCornerShape(6.dp)).background(homeColor))
            }
            Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(6.dp)).background(C.card2), contentAlignment = Alignment.CenterEnd) {
                Box(Modifier.fillMaxWidth((a / tot).toFloat().coerceIn(0f, 1f)).fillMaxSize().clip(RoundedCornerShape(6.dp)).background(awayColor))
            }
        }
    }
}

@Composable
private fun StatValue(v: String, hi: Boolean, color: Color) {
    Box(Modifier.widthIn(min = 38.dp).clip(RoundedCornerShape(9.dp)).background(if (hi) color else Color.Transparent).padding(horizontal = 9.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center) {
        Ltr(fa(v), style = vz(13.sp, FontWeight.Black), color = if (hi) inkOn(color) else C.ink)
    }
}


/* ---------- shotmap ---------- */

@Composable
private fun ShotsTab(m: MatchData) {
    var team by rememberSaveable { mutableStateOf("all") }
    var sel by remember { mutableStateOf<Int?>(null) }
    val isH = { s: JSONObject -> s.i("teamId") == m.homeId }
    val vis = m.shots.filter { team == "all" || (team == "h") == isH(it) }
    Seg(listOf("all" to L("هر دو تیم"), "h" to teamName(m.homeId, m.homeName), "a" to teamName(m.awayId, m.awayName)), team) { team = it }
    Card(pad = PaddingValues(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            Ltr("${teamName(m.awayId, m.awayName)} →", style = vz(12.sp, FontWeight.Bold), color = m.awayColor); Spacer(Modifier.weight(1f))
            Ltr("← ${teamName(m.homeId, m.homeName)}", style = vz(12.sp, FontWeight.Bold), color = m.homeColor)
        }
        BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(105f / 68f).clip(RoundedCornerShape(16.dp))) {
            val w = maxWidth; val hgt = maxHeight
            Canvas(Modifier.fillMaxSize()) {
                val sx = size.width / 105f; val sy = size.height / 68f
                for (i in 0 until 10) drawRect(if (i % 2 == 0) Color(0xFF0D3B24) else Color(0xFF0B3320), Offset(i * size.width / 10, 0f), Size(size.width / 10 + 1, size.height))
                val c = Color.White.copy(alpha = 0.35f); val st = Stroke(1.2f)
                drawRect(c, Offset(0.5f * sx, 0.5f * sy), Size(104 * sx, 67 * sy), style = st)
                drawLine(c, Offset(52.5f * sx, 0f), Offset(52.5f * sx, size.height), 1.2f)
                drawCircle(c, 9.15f * sx, Offset(52.5f * sx, 34 * sy), style = st)
                drawRect(c, Offset(0.5f * sx, 13.85f * sy), Size(16.5f * sx, 40.3f * sy), style = st)
                drawRect(c, Offset(88 * sx, 13.85f * sy), Size(16.5f * sx, 40.3f * sy), style = st)
                drawRect(c, Offset(0.5f * sx, 24.85f * sy), Size(5.5f * sx, 18.3f * sy), style = st)
                drawRect(c, Offset(99 * sx, 24.85f * sy), Size(5.5f * sx, 18.3f * sy), style = st)
                for (s in vis) {
                    val h = isH(s)
                    val x = ((if (h) 105 - (s.d("x") ?: 0.0) else (s.d("x") ?: 0.0)) * sx).toFloat()
                    val y = ((if (h) 68 - (s.d("y") ?: 0.0) else (s.d("y") ?: 0.0)) * sy).toFloat()
                    val r = ((0.9 + sqrt(s.d("expectedGoals") ?: 0.02) * 3.2) * sx).toFloat()
                    val col = if (h) m.homeColor else m.awayColor
                    val goal = s.s("eventType") == "Goal"
                    val on = s.b("isOnTarget")
                    if (goal || on) drawCircle(col.copy(alpha = if (goal) 1f else 0.45f), r, Offset(x, y))
                    drawCircle(if (goal) Color.White else col, r, Offset(x, y), style = Stroke(0.5f * sx))
                    if (goal) drawCircle(Color.White, r * 0.38f, Offset(x, y))
                    if (s.i("id") == sel) drawCircle(Color.White, r + 1.1f * sx, Offset(x, y), style = Stroke(0.5f * sx, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 3f))))
                }
            }
            // invisible tap targets on every shot
            for (s in vis) {
                val h = isH(s)
                val x = (if (h) 105 - (s.d("x") ?: 0.0) else (s.d("x") ?: 0.0)).toFloat() / 105f
                val y = (if (h) 68 - (s.d("y") ?: 0.0) else (s.d("y") ?: 0.0)).toFloat() / 68f
                Box(Modifier.align(AbsoluteAlignment.TopLeft).absoluteOffset(w * x - 12.dp, hgt * y - 12.dp).size(24.dp).clip(CircleShape).clickable { sel = s.i("id") })
            }
        }
        val s = m.shots.firstOrNull { it.i("id") == sel }
        if (s == null) T(L("روی هر شوت بزنید تا جزئیات آن را ببینید"), 12, color = C.mute, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), align = TextAlign.Center)
        else ShotInfo(s)
    }
    fun sum(arr: List<JSONObject>) = Triple(arr.sumOf { it.d("expectedGoals") ?: 0.0 }, arr.size, arr.count { it.b("isOnTarget") })
    val hs = sum(m.shots.filter(isH)); val aw = sum(m.shots.filter { !isH(it) })
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((big, small, col) in listOf(Triple(dec(hs.first), "xG ${teamName(m.homeId, m.homeName)}", m.homeColor), Triple("${hs.second} / ${aw.second}", L("شوت (در چارچوب {0} / {1})", hs.third, aw.third), C.ink), Triple(dec(aw.first), "xG ${teamName(m.awayId, m.awayName)}", m.awayColor))) {
            Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(C.card).border(1.dp, C.line, RoundedCornerShape(16.dp)).padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                T(big, 18, FontWeight.Black, col); T(small, 11, color = C.mute, maxLines = 2, align = TextAlign.Center)
            }
        }
    }
    Card {
        CardTitle(L("همه شوت‌ها"))
        m.shots.sortedBy { it.i("min") ?: 0 }.forEach { s ->
            ListRow(onClick = { sel = s.i("id") }) {
                T("${s.i("min") ?: 0}'", 12, FontWeight.ExtraBold, C.mute, Modifier.width(24.dp), align = TextAlign.Center)
                Crest(s.i("teamId") ?: 0, 18.dp)
                Column(Modifier.weight(1f)) {
                    Ltr(s.s("lastName") ?: s.s("playerName") ?: "")
                    T("${shotResult(s)} · ${tr(s.s("situation"))} · ${tr(s.s("shotType"))}", 12, color = C.mute, maxLines = 1)
                }
                Ltr("xG ${dec(s.d("expectedGoals"))}", style = vz(12.sp), color = C.mute)
            }
        }
    }
}

private fun shotResult(s: JSONObject) = when {
    s.s("eventType") == "Goal" -> L("گل")
    s.b("isBlocked") -> L("بلاک شد")
    s.b("isOnTarget") -> L("مهار شد")
    s.s("eventType") == "Post" -> L("تیرک")
    else -> L("به بیرون")
}

@Composable
private fun ShotInfo(s: JSONObject) {
    val nav = LocalNav.current
    Column(Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.card2).padding(horizontal = 14.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Face(s.i("playerId") ?: 0)
            Column(Modifier.weight(1f)) {
                Ltr(s.s("playerName") ?: "")
                T("${s.i("min") ?: 0}${s.i("minAdded")?.let { "+$it" } ?: ""}' · ${shotResult(s)}", 12, FontWeight.Medium, C.mute)
            }
            IconBtn(Ic.info, L("بازیکن"), size = 18.dp) { nav.player(s.i("playerId") ?: 0) }
        }
        Row(Modifier.padding(top = 8.dp)) {
            for ((k, v) in listOf("xG" to dec(s.d("expectedGoals")), "xGOT" to (if (s.b("isOnTarget")) dec(s.d("expectedGoalsOnTarget")) else "–"),
                L("موقعیت") to tr(s.s("situation")), L("نوع ضربه") to tr(s.s("shotType")))) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) { T(k, 11, color = C.mute); T(v, 13, FontWeight.ExtraBold, maxLines = 1) }
            }
        }
    }
}

/* ---------- lineup ---------- */

/** One player in the line-up, read once. */
private class LP(val p: JSONObject, val home: Boolean) {
    val id = p.i("id") ?: 0
    val name = p.s("name") ?: ""
    val short = p.s("lastName") ?: name.substringAfterLast(' ')
    val number = p.s("shirtNumber") ?: ""
    val rating = p.path("performance").d("rating")
    val events = p.path("performance").a("events").objs().map { it.s("type") }
    val subs = p.path("performance").a("substitutionEvents").objs()
    val outAt = subs.firstOrNull { it.s("type") == "subOut" }?.s("time")
    val inAt = subs.firstOrNull { it.s("type") == "subIn" }?.s("time")
    val captain = p.b("isCaptain") || p.b("captain")
    val goals = events.count { it == "goal" }
    val own = events.count { it == "ownGoal" }
    val assists = events.count { it == "assist" }
    val yellow = "yellowCard" in events
    val red = "redCard" in events || "yellowRedCard" in events
    val layout = p.o("verticalLayout")
    val country = p.s("countryName") ?: ""
}

@Composable
private fun LineupTab(m: MatchData) {
    val nav = LocalNav.current
    val lu = m.content.o("lineup")!!
    val H = lu.o("homeTeam")!!
    val A = lu.o("awayTeam")!!
    val hasLayout = H.a("starters").objs().any { it.o("verticalLayout") != null }
    var view by rememberSaveable { mutableStateOf(if (hasLayout) "pitch" else "list") }
    var side by rememberSaveable { mutableStateOf("both") }
    var sheet by remember { mutableStateOf<LP?>(null) }
    if ((lu.s("lineupType") ?: "standard") != "standard") Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pill(L("ترکیب احتمالی"), PillKind.Accent); T(L("ترکیب رسمی حدود یک ساعت قبل از بازی اعلام می‌شود"), 11, color = C.mute)
    }
    if (hasLayout) Seg(listOf("pitch" to L("روی زمین"), "list" to L("فهرست")), view) { view = it }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pill(L("📋 {0} روی تخته تاکتیک", teamName(m.homeId, m.homeName)), onClick = { nav.board(m.matchId, "h") })
        Pill(L("📋 {0} روی تخته تاکتیک", teamName(m.awayId, m.awayName)), onClick = { nav.board(m.matchId, "a") })
    }
    if (view == "pitch" && hasLayout) {
        Seg(listOf("both" to L("هر دو تیم"), "h" to teamName(m.homeId, m.homeName), "a" to teamName(m.awayId, m.awayName)), side) { side = it }
        TeamBar(H, m.homeColor, m.homeId)
        Pitch(H, A, m, side) { sheet = it }
        TeamBar(A, m.awayColor, m.awayId)
        // benches as swipeable chips under the pitch
        for ((team, home) in listOf(H to true, A to false)) {
            val subs = team.a("subs").objs().map { LP(it, home) }
            if (subs.isEmpty()) continue
            val color = if (home) m.homeColor else m.awayColor
            Card {
                CardTitle(L("ذخیره‌های {0}", team.s("name") ?: ""), sub = L("{0} بازیکن وارد زمین شد", subs.count { it.inAt != null }))
                androidx.compose.foundation.lazy.LazyRow(contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(subs.sortedByDescending { if (it.inAt != null) 1000 - (it.inAt.toIntOrNull() ?: 0) else 0 }) { p -> BenchChip(p, color) { sheet = p } }
                }
            }
        }
        Absentees(H, A)
    } else {
        listOf(H to true, A to false).forEach { (T0, home) ->
            Card {
                CardTitle(T0.s("name") ?: "", sub = T0.s("formation")) {
                    val parts = listOfNotNull(T0.d("averageStarterAge")?.let { L("میانگین سن {0}", dec(it, 1)) }, T0.l2("totalStarterMarketValue")?.let { L("ارزش {0}", money(it)) })
                    T(parts.joinToString(" · "), 12, FontWeight.Medium, C.mute, maxLines = 1)
                }
                T0.o("coach")?.let { c -> PersonRow(c.i("id") ?: 0, c.s("name") ?: "", L("سرمربی"), first = true) { nav.player(c.i("id") ?: 0) } }
                SubHead(L("ترکیب اصلی")); T0.a("starters").objs().forEachIndexed { i, p -> LineupRow(LP(p, home), i == 0) { sheet = it } }
                val subs = T0.a("subs").objs()
                if (subs.isNotEmpty()) { SubHead(L("ذخیره‌ها")); subs.forEachIndexed { i, p -> LineupRow(LP(p, home), i == 0) { sheet = it } } }
            }
        }
        Absentees(H, A)
    }
    sheet?.let { p -> PlayerSheet(m, p) { sheet = null } }
}

private fun JSONObject.l2(k: String): Long? = (opt(k) as? Number)?.toLong()

/** Strip above/below the pitch: crest, team, coach, formation and team rating. */
@Composable
private fun TeamBar(t: JSONObject, color: Color, id: Int) {
    val nav = LocalNav.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).drawBehind { cssLinear(270f, 0f to color.copy(alpha = 0.35f), 1f to Color.Transparent) }
            .background(C.card.copy(alpha = 0.7f)).border(1.dp, C.line, RoundedCornerShape(18.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Crest(id, 30.dp)
        Column(Modifier.weight(1f).clickable { nav.team(id) }) {
            Ltr(t.s("name") ?: "", style = lat(16.sp, FontWeight.ExtraBold))
            t.o("coach")?.s("name")?.let { T(L("سرمربی: {0}", it), 11, color = C.mute, maxLines = 1) }
        }
        Box(Modifier.clip(RoundedCornerShape(10.dp)).background(color).padding(horizontal = 10.dp, vertical = 4.dp)) {
            Ltr(t.s("formation") ?: "", style = lat(15.sp, FontWeight.ExtraBold), color = inkOn(color))
        }
        Rating(t.d("rating"))
    }
}

@Composable
private fun Absentees(H: JSONObject, A: JSONObject) {
    val nav = LocalNav.current
    val all = H.a("unavailable").objs().map { it to (H.s("name") ?: "") } + A.a("unavailable").objs().map { it to (A.s("name") ?: "") }
    if (all.isEmpty()) return
    Card {
        CardTitle(L("غایبان"), sub = L("{0} بازیکن", all.size))
        all.forEachIndexed { i, (p, team) ->
            val type = p.o("unavailability").s("type")
            val ret = p.o("unavailability").s("expectedReturn")?.let { L(" · بازگشت: ") + if (it == "Doubtful") L("نامشخص") else it } ?: ""
            PersonRow(p.i("id") ?: 0, p.s("name") ?: "", "$team · " + when (type) { "injury" -> L("مصدوم"); "suspension" -> L("محروم"); else -> L("غایب") } + ret, first = i == 0,
                trailing = { Icon(Ic.bandage, null, tint = C.live, modifier = Modifier.size(18.dp)) }) { nav.player(p.i("id") ?: 0) }
        }
    }
}

@Composable
fun SubHead(text: String) = T(text, 12, FontWeight.ExtraBold, C.mute, Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp))

@Composable
private fun LineupRow(p: LP, first: Boolean, onOpen: (LP) -> Unit) {
    val meta = listOfNotNull(p.number.takeIf { it.isNotEmpty() }?.let { "#$it" }, p.country.takeIf { it.isNotEmpty() },
        p.inAt?.let { L("⇧ ورود دقیقه {0}", it) }, p.outAt?.let { L("⇩ خروج دقیقه {0}", it) }).joinToString(" · ")
    PersonRow(p.id, p.name + if (p.captain) " (C)" else "", meta, first = first, trailing = {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) { EventIcons(p); Rating(p.rating) }
    }) { onOpen(p) }
}

@Composable
fun PersonRow(id: Int, name: String, meta: String, first: Boolean = false, trailing: @Composable () -> Unit = {}, onClick: () -> Unit) {
    ListRow(first = first, onClick = onClick) {
        Face(id, 38.dp)
        Column(Modifier.weight(1f)) {
            Ltr(name)
            if (meta.isNotEmpty()) T(meta, 12, color = C.mute, maxLines = 1)
        }
        trailing()
    }
}

/** Small goal / assist / card icons for a player. */
@Composable
private fun EventIcons(p: LP) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(p.goals.coerceAtMost(3)) { Icon(Ic.ball, null, Modifier.size(13.dp), tint = C.ink) }
        if (p.own > 0) Icon(Ic.ball, null, Modifier.size(13.dp), tint = C.live)
        if (p.assists > 0) T("A", 11, FontWeight.Black, LocalAccent.current)
        if (p.red) Box(Modifier.size(8.dp, 11.dp).clip(RoundedCornerShape(2.dp)).background(C.live))
        else if (p.yellow) Box(Modifier.size(8.dp, 11.dp).clip(RoundedCornerShape(2.dp)).background(C.gold))
    }
}

/** A face that falls back to a shirt-number disc in the team colour when there is no photo. */
@Composable
private fun Kit(p: LP, color: Color, size: androidx.compose.ui.unit.Dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color).border(2.dp, Color.White.copy(alpha = 0.95f), CircleShape), contentAlignment = Alignment.Center) {
        Text(p.number, color = inkOn(color), style = lat((size.value * 0.42f).sp, FontWeight.ExtraBold))
        coil.compose.AsyncImage(playerFace(p.id), null, Modifier.fillMaxSize().clip(CircleShape), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
    }
}

@Composable
private fun BenchChip(p: LP, color: Color, onClick: () -> Unit) {
    Column(Modifier.width(84.dp).clip(RoundedCornerShape(16.dp)).background(if (p.inAt != null) color.copy(alpha = 0.14f) else C.card2)
        .clickable(onClick = onClick).padding(vertical = 10.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            Kit(p, color, 44.dp)
            p.rating?.let { Rating(it, Modifier.align(Alignment.TopEnd).absoluteOffset(12.dp, (-6).dp), small = true) }
        }
        Ltr(p.short, Modifier.padding(top = 6.dp), style = lat(12.sp), align = TextAlign.Center)
        if (p.inAt != null) Row(verticalAlignment = Alignment.CenterVertically) {
            T("⇧", 11, FontWeight.Black, C.win); Ltr("${p.inAt}'", style = vz(11.sp, FontWeight.Bold), color = C.win)
        } else T("#${p.number}", 11, color = C.mute)
    }
}

@Composable
private fun Pitch(H: JSONObject, A: JSONObject, m: MatchData, side: String, onOpen: (LP) -> Unit) {
    // one team: its eleven spread over the whole pitch; both: each in its own half
    val single = side != "both"
    BoxWithConstraints(
        Modifier.fillMaxWidth().aspectRatio(if (single) 68f / 92f else 68f / 112f).clip(RoundedCornerShape(20.dp)).border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
            .drawBehind {
                val n = 14
                for (i in 0 until n) drawRect(if (i % 2 == 0) C.pitch else C.pitch2, Offset(0f, size.height * i / n), Size(size.width, size.height / n + 1))
                for ((cx, cy, a) in listOf(Triple(0f, 0f, 0.22f), Triple(1f, 0f, 0.22f), Triple(0f, 1f, 0.16f), Triple(1f, 1f, 0.16f)))
                    drawRect(Brush.radialGradient(listOf(Color(0xFFFFFFF0).copy(alpha = a), Color.Transparent), Offset(size.width * cx, size.height * cy), size.width * 0.6f))
                drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.45f)), Offset(size.width / 2, size.height / 2), size.height * 0.62f))
                val sx = size.width / 68f; val sy = size.height / 112f
                val c = Color.White.copy(alpha = 0.6f); val st = Stroke(0.35f * sx)
                drawRoundRect(c, Offset(1 * sx, 1 * sy), Size(66 * sx, 110 * sy), CornerRadius(sx), style = st)
                drawLine(c, Offset(1 * sx, 56 * sy), Offset(67 * sx, 56 * sy), st.width)
                drawCircle(c, 9.15f * sx, Offset(34 * sx, 56 * sy), style = st)
                drawRect(c, Offset(13.85f * sx, 1 * sy), Size(40.3f * sx, 16.5f * sy), style = st)
                drawRect(c, Offset(24.85f * sx, 1 * sy), Size(18.3f * sx, 5.5f * sy), style = st)
                drawRect(c, Offset(13.85f * sx, 94.5f * sy), Size(40.3f * sx, 16.5f * sy), style = st)
                drawRect(c, Offset(24.85f * sx, 105.5f * sy), Size(18.3f * sx, 5.5f * sy), style = st)
                val gw = size.width * 0.22f; val gh = size.height * 0.022f; val gx = (size.width - gw) / 2
                for (top in listOf(true, false)) {
                    val y = if (top) 0f else size.height - gh
                    drawRect(Color.White.copy(alpha = 0.85f), Offset(gx, y), Size(gw, gh), style = Stroke(2.dp.toPx()))
                    var x = gx; while (x < gx + gw) { drawLine(Color.White.copy(alpha = 0.35f), Offset(x, y), Offset(x, y + gh), 1f); x += 6.dp.toPx() }
                }
            },
    ) {
        val w = maxWidth; val h = maxHeight
        val chipW = 70.dp
        val teams = when (side) { "h" -> listOf(H to true); "a" -> listOf(A to false); else -> listOf(H to true, A to false) }
        for ((team, home) in teams) {
            val color = if (home) m.homeColor else m.awayColor
            team.a("starters").objs().map { LP(it, home) }.forEach { p ->
                val v = p.layout ?: return@forEach
                val x = (v.d("x") ?: 0.5).toFloat(); val y = (v.d("y") ?: 0.5).toFloat()
                // single team: goalkeeper at the bottom, attackers at the top
                val top = when { single -> 0.92f - y * 0.84f; home -> 0.04f + y * 0.44f; else -> 0.96f - y * 0.44f }
                val left = if (home || single) 0.06f + x * 0.88f else 0.94f - x * 0.88f
                Column(
                    Modifier.align(AbsoluteAlignment.TopLeft).absoluteOffset(w * left - chipW / 2, h * top - 24.dp).width(chipW).clickable { onOpen(p) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box {
                        Box(Modifier.glow(Color.Black.copy(alpha = 0.5f), 6.dp, CircleShape)) { Kit(p, color, if (single) 44.dp else 38.dp) }
                        p.rating?.let { Rating(it, Modifier.align(Alignment.TopEnd).absoluteOffset(16.dp, (-7).dp), small = true) }
                        if (p.captain) Box(Modifier.align(Alignment.TopStart).absoluteOffset((-6).dp, (-4).dp).size(15.dp).clip(CircleShape).background(C.gold),
                            contentAlignment = Alignment.Center) { Text("C", fontSize = 9.sp, fontWeight = FontWeight.Black, color = Color(0xFF2A1A00)) }
                        PitchBadges(p, Modifier.align(Alignment.BottomStart).absoluteOffset((-12).dp, 4.dp))
                        p.outAt?.let { t ->
                            Row(Modifier.align(Alignment.BottomEnd).absoluteOffset(14.dp, 4.dp).clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 3.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Text("⇩", fontSize = 9.sp, color = C.live, fontWeight = FontWeight.Black); Text("$t'", fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Ltr(
                        "${p.number} ${p.short}".trim(),
                        Modifier.padding(top = 4.dp).clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.35f)).padding(horizontal = 4.dp),
                        style = lat(if (single) 12.sp else 11.sp, FontWeight.Bold), color = Color.White, align = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun PitchBadges(p: LP, modifier: Modifier) {
    if (p.goals == 0 && p.own == 0 && !p.red && !p.yellow && p.assists == 0) return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(1.dp)) {
        val dot = Modifier.size(16.dp).clip(CircleShape).background(Color.White)
        if (p.goals > 0) Box(dot, contentAlignment = Alignment.Center) {
            if (p.goals > 1) Text("${p.goals}", fontSize = 9.sp, color = Color(0xFF111111), fontWeight = FontWeight.Black)
            else Icon(Ic.ball, null, Modifier.size(12.dp), tint = Color(0xFF111111))
        }
        if (p.own > 0) Box(dot, contentAlignment = Alignment.Center) { Icon(Ic.ball, null, Modifier.size(12.dp), tint = C.live) }
        if (p.assists > 0) Box(dot, contentAlignment = Alignment.Center) { Text("A", fontSize = 9.sp, color = Color(0xFF111111), fontWeight = FontWeight.Black) }
        if (p.red) Box(dot, contentAlignment = Alignment.Center) { Box(Modifier.size(8.dp, 11.dp).clip(RoundedCornerShape(2.dp)).background(C.live)) }
        else if (p.yellow) Box(dot, contentAlignment = Alignment.Center) { Box(Modifier.size(8.dp, 11.dp).clip(RoundedCornerShape(2.dp)).background(C.gold)) }
    }
}

/** Tapping a player: their numbers in this match, without leaving the match page. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun PlayerSheet(m: MatchData, p: LP, onClose: () -> Unit) {
    val nav = LocalNav.current
    val ps = m.content.o("playerStats")?.optJSONObject(p.id.toString())
    val color = if (p.home) m.homeColor else m.awayColor
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onClose, containerColor = C.cardSolid,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), dragHandle = {
            Box(Modifier.padding(top = 10.dp).width(44.dp).height(5.dp).clip(RoundedCornerShape(5.dp)).background(C.line2))
        }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Kit(p, color, 64.dp)
                Column(Modifier.weight(1f)) {
                    Ltr(p.name, style = lat(20.sp, FontWeight.ExtraBold), maxLines = 2)
                    T(listOfNotNull(ps?.s("teamName"), p.number.takeIf { it.isNotEmpty() }?.let { "#$it" }, if (ps?.b("isPotm") == true) L("بهترین بازیکن زمین") else null,
                        p.inAt?.let { L("ورود دقیقه {0}", it) }, p.outAt?.let { L("خروج دقیقه {0}", it) }).joinToString(" · "), 12, color = C.mute)
                }
                Rating(p.rating ?: ps?.let { statOf(it, "FotMob rating") })
            }
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(L("پروفایل کامل"), PillKind.Accent, onClick = { onClose(); nav.player(p.id) })
                Pill(L("مقایسه"), onClick = { onClose(); nav.compare(p.id) }, icon = Ic.swap)
            }
            if (m.started) PlayerHeatmap(m.matchId, p.id, color)
            val groups = ps?.a("stats").objs()
            if (groups.isNullOrEmpty()) T(L("آمار این بازیکن در این بازی ثبت نشده"), 13, color = C.mute, modifier = Modifier.fillMaxWidth().padding(24.dp), align = TextAlign.Center)
            groups?.forEach { g ->
                SubHead(tr(g.s("title")))
                val st = g.o("stats") ?: return@forEach
                st.keys().asSequence().toList().filter { it != "Shotmap" && st.optJSONObject(it)?.s("key") != null }.forEach { t ->
                    val s = st.optJSONObject(t)?.o("stat") ?: return@forEach
                    val v = s.d("value")
                    val shown = when {
                        v == null -> "–"
                        s.s("type") == "fractionWithPercentage" -> L("{0}/{1} ({2}٪)", v.toInt(), s.i("total") ?: 0, ((v / (s.d("total") ?: 1.0).coerceAtLeast(1.0)) * 100).toInt())
                        s.s("type") == "double" -> dec(v)
                        s.s("type") == "boolean" -> if (v != 0.0) L("بله") else L("خیر")
                        else -> "${v.toInt()}"
                    }
                    Divider()
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                        T(tr(t), 13, modifier = Modifier.weight(1f))
                        Ltr(fa(shown), style = vz(13.sp, FontWeight.ExtraBold))
                    }
                }
            }
        }
    }
}

private fun statOf(p: JSONObject, title: String): Double? {
    for (g in p.a("stats").objs()) g.o("stats")?.o(title)?.o("stat")?.d("value")?.let { return it }
    return null
}

/* ---------- players ---------- */

private fun statVal(p: JSONObject, title: String): Double? {
    for (g in p.a("stats").objs()) g.o("stats")?.o(title)?.o("stat")?.d("value")?.let { return it }
    return null
}

@Composable
private fun PlayersTab(m: MatchData) {
    val nav = LocalNav.current
    var team by rememberSaveable { mutableStateOf("all") }
    var sort by rememberSaveable { mutableStateOf("rating") }
    val ps = m.content.o("playerStats")!!
    val all = ps.keys().asSequence().mapNotNull { ps.optJSONObject(it) }.filter { (it.a("stats")?.length() ?: 0) > 0 }.toList()
    val key: (JSONObject) -> Double = when (sort) {
        "ga" -> { p -> (statVal(p, "Goals") ?: 0.0) * 10 + (statVal(p, "Assists") ?: 0.0) }
        "xg" -> { p -> statVal(p, "Expected goals (xG)") ?: 0.0 }
        "min" -> { p -> statVal(p, "Minutes played") ?: 0.0 }
        else -> { p -> statVal(p, "FotMob rating") ?: 0.0 }
    }
    val list = all.filter { team == "all" || (team == "h") == (it.i("teamId") == m.homeId) }.sortedByDescending(key)
    Seg(listOf("all" to L("همه"), "h" to teamName(m.homeId, m.homeName), "a" to teamName(m.awayId, m.awayName)), team) { team = it }
    Seg(listOf("rating" to L("نمره"), "ga" to L("گل و پاس گل"), "xg" to "xG", "min" to L("دقایق")), sort) { sort = it }
    Card {
        list.forEachIndexed { i, p ->
            val g = statVal(p, "Goals"); val asst = statVal(p, "Assists"); val mins = statVal(p, "Minutes played"); val xg = statVal(p, "Expected goals (xG)")
            val bits = listOfNotNull(mins?.let { "${it.toInt()}′" }, g?.takeIf { it > 0 }?.let { L("{0} گل", it.toInt()) }, asst?.takeIf { it > 0 }?.let { L("{0} پاس گل", it.toInt()) },
                xg?.takeIf { it > 0 }?.let { "xG ${dec(it)}" }).joinToString(" · ")
            ListRow(first = i == 0, onClick = { nav.player(p.i("id") ?: 0) }) {
                Face(p.i("id") ?: 0)
                Column(Modifier.weight(1f)) {
                    Ltr(p.s("name") ?: "")
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) { Crest(p.i("teamId") ?: 0, 14.dp); T(bits, 12, color = C.mute, maxLines = 1) }
                }
                if (p.b("isPotm")) Icon(Ic.starOn, null, Modifier.size(14.dp), tint = C.gold)
                Rating(statVal(p, "FotMob rating"))
            }
        }
    }
}

/* ---------- table & h2h ---------- */

@Composable
private fun MatchTable(m: MatchData) {
    val leagueId = m.content.o("table").i("leagueId") ?: return
    val l = rememberLoader(leagueId) { Api.fmArray("tltable", mapOf("leagueId" to leagueId), 60_000) }
    LoadBox(l, 8) { arr -> TableView(arr, listOf(m.homeId, m.awayId)) }
}

@Composable
private fun H2h(m: MatchData, limit: Int) {
    val nav = LocalNav.current
    val x = m.content.o("h2h") ?: return
    val sum = x.a("summary")
    val hw = sum?.optInt(0) ?: 0; val dr = sum?.optInt(1) ?: 0; val aw = sum?.optInt(2) ?: 0
    Card {
        CardTitle(L("رودررو"))
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 16.dp)) {
            listOf(Triple(hw, L("برد {0}", teamName(m.homeId, m.homeName)), m.homeColor), Triple(dr, L("تساوی"), C.ink), Triple(aw, L("برد {0}", teamName(m.awayId, m.awayName)), m.awayColor)).forEach { (n, label, c) ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    T("$n", 30, FontWeight.Black, c)
                    T(label, 12, color = C.mute, maxLines = 1, align = TextAlign.Center)
                }
            }
        }
        val tot = (hw + dr + aw).coerceAtLeast(1)
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 14.dp).height(8.dp).clip(RoundedCornerShape(8.dp)), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            if (hw > 0) Box(Modifier.weight(hw.toFloat() / tot).fillMaxSize().background(m.homeColor))
            if (dr > 0) Box(Modifier.weight(dr.toFloat() / tot).fillMaxSize().background(C.draw))
            if (aw > 0) Box(Modifier.weight(aw.toFloat() / tot).fillMaxSize().background(m.awayColor))
        }
        x.a("matches").objs().take(limit).forEach { j ->
            val mi = parseMatch(j)
            MatchRow(mi, { nav.match(mi.id) }, showDate = true, star = false,
                sub = listOfNotNull(j.o("league").s("name"), persianDate(mi.time, "d MMM yyyy")).joinToString(" · "))
        }
    }
}
