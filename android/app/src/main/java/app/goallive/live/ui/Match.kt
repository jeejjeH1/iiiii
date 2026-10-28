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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.luminance
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
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.roundToInt
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
        poll = { m ->
            val toGo = m.time - System.currentTimeMillis()
            when {
                m.live -> 8_000L
                // around kick-off and while line-ups are due: every 15 seconds
                !m.finished && toGo < 90 * 60_000L -> 15_000L
                !m.finished && toGo < 2 * 3_600_000 -> 60_000L
                else -> null
            }
        },
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
    var lineupView by rememberSaveable { mutableStateOf("real") }
    // numbers only once the ball is rolling
    val statViews = if (!m.started) emptyList() else buildList {
        if (m.hasStats) add("team" to L("آمار تیمی"))
        if (m.shots.isNotEmpty()) add("shots" to L("شوت‌ها"))
        if (m.hasPlayers) add("players" to L("بازیکنان"))
    }
    val tabs = buildList {
        add("summary" to L("خلاصه"))
        // the written analysis, once there are chances to read
        if (m.started && m.shots.count { !it.b("isPenaltyShootoutEvent") } >= 4) add("analysis" to L("🧠 تحلیل"))
        // the live pitch only while the ball is rolling; afterwards just the written commentary, when there is one
        if (m.live) add("live" to L("زنده")) else if (m.finished && m.hasTicker) add("text" to L("گزارش متنی"))
        if (statViews.isNotEmpty()) add("stats" to L("آمار"))
        // the real line-up and «مربی تو» (pick your own eleven) share one tab
        if (m.hasLineup || !m.cancelled) add("lineup" to L("ترکیب"))
        if (m.hasTable) add("table" to L("جدول"))
        if (m.hasH2h) add("h2h" to L("رودررو"))
    }
    if (tabs.none { it.first == tab }) tab = "summary"
    if (statViews.none { it.first == statsView }) statsView = statViews.firstOrNull()?.first ?: "team"
    val ratings = remember(m.d) { runCatching { rateMatch(m) }.getOrDefault(emptyMap()) }
    CompositionLocalProvider(LocalRatings provides ratings) {
    // once the scoreboard at the top has scrolled away, the top bar carries a small one; the big one drifts up slower than the list and fades
    val list = androidx.compose.foundation.lazy.rememberLazyListState()
    val mini by remember { derivedStateOf { list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset > 420 } }
    LaunchedEffect(mini, m) { TopTitle.match = if (mini) m else null }
    DisposableEffect(Unit) { onDispose { TopTitle.match = null } }
    LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(bottom = BottomSpace)) {
        item {
            Box(Modifier.graphicsLayer {
                if (list.firstVisibleItemIndex == 0) {
                    val o = list.firstVisibleItemScrollOffset.toFloat()
                    translationY = o * 0.4f
                    alpha = (1f - o / size.height.coerceAtLeast(1f) * 1.1f).coerceIn(0f, 1f)
                }
            }) { Hero(m) }
        }
        stickyTabs(tabs, tab) { tab = it }
        item {
            Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (tab) {
                    "summary" -> Summary(m)
                    "analysis" -> AnalystTab(m)
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
                    "lineup" -> {
                        if (m.hasLineup && !m.cancelled) Seg(listOf("real" to L("ترکیب بازی"), "coach" to L("🧠 ترکیب تو")), lineupView) { lineupView = it }
                        if (m.hasLineup && (lineupView == "real" || m.cancelled)) LineupTab(m) else CoachTab(m)
                    }
                    "table" -> MatchTable(m)
                    "h2h" -> H2h(m, 50)
                }
            }
        }
    }
    }
}

/** Tabs that stay at the top while the page scrolls (".tabs" is sticky on the site). */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun androidx.compose.foundation.lazy.LazyListScope.stickyTabs(tabs: List<Pair<String, String>>, tab: String, onSelect: (String) -> Unit) {
    stickyHeader {
        Box(Modifier.fillMaxWidth().stickyBack().padding(vertical = 6.dp)) { Tabs(tabs, tab, onSelect = onSelect) }
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
            // each half in its own club's colour, the home side where its crest stands; a soft light where they meet
            val home = heroTone(c1); val away = heroTone(c2)
            val left = if (app.goallive.live.Lang.en) home else away; val right = if (app.goallive.live.Lang.en) away else home
            drawRect(Brush.horizontalGradient(0f to left, 0.38f to left, 0.62f to right, 1f to right))
            if (pattern != null) drawPattern(pattern, 0.1f)
            drawRect(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.2f), Color.White.copy(alpha = 0.06f), Color.Transparent),
                Offset(size.width / 2, size.height * 0.42f), size.width * 0.45f))
            drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.38f)), Offset(size.width / 2, size.height * 0.4f), size.width * 0.95f))
            drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.22f), Color.Black.copy(alpha = 0.12f), Color.Black.copy(alpha = 0.6f))))
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
                        Box(board.padding(horizontal = 18.dp, vertical = 0.dp)) { ScoreText(m.home.i("score"), m.away.i("score"), 52, Color.White, sepColor = Color.White.copy(alpha = 0.4f)) }
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
                        Box(board.padding(horizontal = 18.dp, vertical = 6.dp)) { Text(if (m.cancelled) "–" else timeOf(m.time), color = Color.White, style = num(36.sp)) }
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
                    Box(Modifier.padding(horizontal = 6.dp).width(1.dp).height(((goals.size + reds.size).coerceAtMost(6) * 12).dp).background(Color.White.copy(alpha = 0.18f)))
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
        Ltr("$minute'".replace("''", "'"), style = num(14.sp), color = Color.White)
    }
}

@Composable
private fun HeroTeam(id: Int, name: String, faName: String?, modifier: Modifier, shareKey: String) {
    val nav = LocalNav.current
    Column(modifier.clip(RoundedCornerShape(18.dp)).clickable { nav.team(id) }.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Crest(id, 68.dp, large = true, shareKey = shareKey)
        TeamText(id, name, size = 16, w = FontWeight.ExtraBold, color = Color.White, maxLines = 2, align = TextAlign.Center)
    }
}

@Composable
private fun Scorers(goals: List<JSONObject>, reds: List<JSONObject>, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        goals.groupBy { it.o("player").i("id") ?: it.s("nameStr") }.values.forEach { gs ->
            val p = gs.first()
            val name = p.o("player").s("lastName") ?: p.s("nameStr") ?: p.o("player").s("name") ?: ""
            val own = gs.all { it.b("ownGoal") }
            // a small ball, the name, then each goal's minute in its own chip
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Icon(Ic.ball, null, Modifier.size(13.dp), tint = if (own) Color(0xFFFF8A8A) else Color.White)
                Ltr(name, style = vz(12.sp, FontWeight.Bold), color = Color.White)
                gs.forEach { g -> MinuteChip(minuteText(g) + "'" + if (g.b("ownGoal")) L(" (گل به خودی)") else if (isPen(g)) L(" (پ)") else "") }
            }
        }
        reds.forEach { r ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(Modifier.size(9.dp, 12.dp).clip(RoundedCornerShape(2.dp)).background(C.live))
                Ltr(r.o("player").s("lastName") ?: r.s("nameStr") ?: "", style = vz(12.sp, FontWeight.Bold), color = Color.White)
                MinuteChip(minuteText(r) + "'")
            }
        }
    }
}

/** A minute in a small dark chip, as under the score. */
@Composable
private fun MinuteChip(s: String) {
    Box(Modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.32f)).padding(horizontal = 6.dp)) {
        Ltr(s, style = num(11.sp, FontWeight.Bold), color = Color.White.copy(alpha = 0.92f))
    }
}

/** A club colour that white writing still reads on: a white or very pale kit is toned down to slate. */
private fun heroTone(c: Color): Color = if (c.luminance() > 0.6f) androidx.compose.ui.graphics.lerp(c, Color(0xFF3A4252), 0.55f) else c

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

/**
 * "خلاصه": only what matters at this point of the match. Before kick-off: prediction, tactical preview, radar, form, head-to-head.
 * From kick-off: the video, what happened, momentum, where they attacked, the man of the match and three stat notes.
 */
@Composable
private fun Summary(m: MatchData) {
    val nav = LocalNav.current
    PredictCard(m)
    TacticalPreview(m)
    if (m.finished && m.events.isNotEmpty()) ReelBanner(m)
    if (m.finished) Card {
        CardTitle(L("خلاصه و گل‌ها"))
        Box(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) { Highlights(m, compact = true) }
    }
    if (m.started) {
        KeyStats(m)
        EventsCard(m)
        MomentumCard(m)
        if (m.live) WinProbability(m)
        AttackZones(m)
    }
    if (!m.finished && !m.cancelled && m.homeId > 0 && m.awayId > 0) OpponentRadar(m)
    if (m.started) TopRated(m)
    val ins = m.facts.a("insights").objs()
    if (ins.isNotEmpty()) {
        var all by rememberSaveable(m.matchId) { mutableStateOf(false) }
        Card {
            CardTitle(L("نکته‌های آماری"))
            (if (all) ins else ins.take(3)).forEach { i ->
                Divider()
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).height(androidx.compose.foundation.layout.IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.width(3.dp).fillMaxSize().clip(RoundedCornerShape(4.dp)).background(parseColor(i.s("color")) ?: LocalAccent.current))
                    (i.i("teamId") ?: 0).takeIf { it > 0 }?.let { Crest(it, 18.dp) }
                    if (app.goallive.live.Lang.en) Ltr(insightText(i, m), Modifier.weight(1f), style = vz(13.sp), maxLines = 6)
                    else T(insightText(i, m), 13, modifier = Modifier.weight(1f))
                }
            }
            if (ins.size > 3) T(if (all) L("کمتر") else L("نکته‌های بیشتر ({0})", fa(ins.size - 3)), 12, FontWeight.Bold, LocalAccent.current,
                Modifier.fillMaxWidth().clickable { all = !all }.padding(vertical = 10.dp), align = TextAlign.Center)
        }
    }
    if (!m.started) {
        val form = m.facts.a("teamForm")
        if (form != null && form.length() == 2) Card {
            CardTitle(L("فرم ۵ بازی اخیر"))
            listOf(m.homeId to m.homeName, m.awayId to m.awayName).forEachIndexed { i, (id, name) ->
                ListRow(first = i == 0) { Crest(id); T(teamName(id, name), 14, FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1); FormChips(form.optJSONArray(i)) }
            }
        }
    }
    InfoCard(m)
    if (!m.started && m.hasH2h) H2h(m, 3)
}

/** Four numbers that tell the match at a glance: possession, xG, shots and shots on target, with a link to all the stats. */
@Composable
private fun KeyStats(m: MatchData) {
    val top = m.content.path("stats.Periods.All").a("stats").objs().firstOrNull()?.a("stats").objs()
    if (top.isNullOrEmpty()) return
    val want = listOf("BallPossesion", "expected_goals", "total_shots", "ShotsOnTarget")
    val rows = want.mapNotNull { k -> top.firstOrNull { it.s("key") == k } }
    if (rows.size < 2) return
    Card(pad = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            rows.forEach { st ->
                val v = st.a("stats").strs()
                if (v.size < 2) return@forEach
                val raw = st.a("rawStats").objs()
                fun num(i: Int) = raw.getOrNull(i).d("value") ?: v[i].substringBefore(' ').replace("%", "").toDoubleOrNull() ?: 0.0
                val h = num(0); val a = num(1); val tot = (h + a).takeIf { it > 0 } ?: 1.0
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Ltr(fa(v[0]) + if (st.s("key") == "BallPossesion") "٪" else "", style = vz(14.sp, FontWeight.Black), color = if (h >= a) C.ink else C.mute)
                        T(tr(st.s("title")), 12, FontWeight.SemiBold, C.mute, Modifier.weight(1f), align = TextAlign.Center)
                        Ltr(fa(v[1]) + if (st.s("key") == "BallPossesion") "٪" else "", style = vz(14.sp, FontWeight.Black), color = if (a >= h) C.ink else C.mute)
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 5.dp).height(5.dp).clip(RoundedCornerShape(5.dp)), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Box(Modifier.weight(max(h / tot, 0.02).toFloat()).fillMaxSize().background(m.homeColor))
                        Box(Modifier.weight(max(a / tot, 0.02).toFloat()).fillMaxSize().background(m.awayColor))
                    }
                }
            }
        }
    }
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
            Box(Modifier.size(24.dp).clip(RoundedCornerShape(10.dp)).background(c), contentAlignment = Alignment.Center) {
                T(when (r) { "W" -> L("ب"); "L" -> L("ش"); else -> L("م") }, 11, FontWeight.Black, Color.White)
            }
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
            Canvas(Modifier.fillMaxWidth().height(110.dp).clip(RoundedCornerShape(14.dp))) {
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
            add(Triple(Ic.stadium, L("ورزشگاه"), listOfNotNull(s.s("name")?.let { personName(it) }, s.s("city")?.let { personName(it) }).joinToString(L("، ")) + (s.i("capacity")?.let { L(" · ظرفیت {0}", "%,d".format(it)) } ?: "") +
                (s.s("surface")?.let { if (it == "grass") L(" · چمن طبیعی") else L(" · چمن مصنوعی") } ?: "")))
        }
        ib.o("Referee")?.s("text")?.let { add(Triple(Ic.whistle, L("داور"), personName(it) + (ib.o("Referee").s("country")?.let { c -> " · " + tr(c).let { t -> if (t == c) personName(c) else t } } ?: ""))) }
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
    // the stats as a story picture or video
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) { Legend(m) }
        Pill(L("استوری آمار"), PillKind.Accent, icon = Ic.share, onClick = { StoryStudio.story = Story.Stats(m) })
    }
    val possRow = periods.o(period).a("stats").objs().flatMap { it.a("stats").objs() }.firstOrNull { it.s("key") == "BallPossesion" }
    val poss = possRow?.a("stats")?.let { (it.optString(0).filter(Char::isDigit).toIntOrNull() ?: 0) to (it.optString(1).filter(Char::isDigit).toIntOrNull() ?: 0) }
        ?.takeIf { it.first + it.second > 0 }
    poss?.let { PossessionRing(m, it.first, it.second) }
    // every group in one card, a small heading between them (possession has its ring above)
    Card {
        periods.o(period).a("stats").objs().forEachIndexed { k, g ->
            val rows = g.a("stats").objs().filter { !(poss != null && it.s("key") == "BallPossesion") }
            if (rows.none { it.s("type") != "title" }) return@forEachIndexed
            if (k > 0) Divider()
            T(tr(g.s("title")), 13, FontWeight.ExtraBold, LocalAccent.current, Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 2.dp))
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) { rows.forEach { st -> StatRow(st, m.homeColor, m.awayColor) } }
        }
    }
    if (period == "All") { TeamRadar(m, poss); RateRings(m) }
    // the rest of the picture, built from every player's own numbers (whole match only: players' stats are not split by half)
    if (period == "All") {
        val have = periods.o("All").a("stats").objs().flatMap { it.a("stats").objs() }.mapNotNull { it.s("key") }.toSet()
        MoreTeamStats(m, have)
        StatLeaders(m)
    }
    if (m.started) AveragePositions(m)
}

/**
 * Each team's sum of every player stat key: (values, totals) as [home, away]. The total is FotMob's attempts for a
 * fraction (passes tried for accurate passes), so a rate can be worked out for the whole team.
 */
private fun teamTotals(m: MatchData): Map<String, Pair<DoubleArray, DoubleArray>> {
    val stats = m.content.o("playerStats") ?: return emptyMap()
    val lu = m.content.o("lineup") ?: return emptyMap()
    val side = HashMap<Int, Boolean>()
    for ((k, home) in listOf("homeTeam" to true, "awayTeam" to false)) lu.o(k).let { t -> (t.a("starters").objs() + t.a("subs").objs()).forEach { p -> p.i("id")?.let { side[it] = home } } }
    val out = HashMap<String, Pair<DoubleArray, DoubleArray>>()
    for (k in stats.keys()) {
        val home = side[k.toIntOrNull() ?: continue] ?: continue
        for (g in stats.optJSONObject(k).a("stats").objs()) {
            val all = g.o("stats") ?: continue
            for (t in all.keys()) {
                val one = all.optJSONObject(t) ?: continue
                val key = one.s("key") ?: continue
                val st = one.o("stat") ?: continue
                val v = st.d("value") ?: continue
                val slot = out.getOrPut(key) { DoubleArray(2) to DoubleArray(2) }
                slot.first[if (home) 0 else 1] += v
                slot.second[if (home) 0 else 1] += st.d("total") ?: 0.0
            }
        }
    }
    return out
}

/** A ring filling up to [share] (0…1) in [color], drawn once the card shows; the text sits in the middle. */
@Composable
private fun Donut(share: Float, color: Color, size: androidx.compose.ui.unit.Dp, stroke: androidx.compose.ui.unit.Dp, center: @Composable () -> Unit) {
    val grow = remember { Animatable(0f) }
    LaunchedEffect(share) { grow.snapTo(0f); grow.animateTo(1f, androidx.compose.animation.core.tween(900, easing = androidx.compose.animation.core.FastOutSlowInEasing)) }
    val track = C.card2
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = stroke.toPx(); val inset = w / 2
            val rect = Size(this.size.width - w, this.size.height - w)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), rect, style = Stroke(w))
            drawArc(color, -90f, 360f * share.coerceIn(0f, 1f) * grow.value, false, Offset(inset, inset), rect, style = Stroke(w, cap = androidx.compose.ui.graphics.StrokeCap.Round))
        }
        center()
    }
}

/** "مالکیت توپ": one ring split in the two teams' colours, each share on its own side. */
@Composable
private fun PossessionRing(m: MatchData, h: Int, a: Int) {
    val grow = remember { Animatable(0f) }
    LaunchedEffect(h, a) { grow.snapTo(0f); grow.animateTo(1f, androidx.compose.animation.core.tween(1000, easing = androidx.compose.animation.core.FastOutSlowInEasing)) }
    val hs = h.toFloat() / (h + a).coerceAtLeast(1)
    val rtl = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
    Card(pad = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Crest(m.homeId, 30.dp); T(fa("${(h * grow.value).roundToInt()}٪"), 30, FontWeight.Black, m.homeColor)
            }
            Box(Modifier.size(130.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val w = 18.dp.toPx(); val o = Offset(w / 2, w / 2); val sz = Size(size.width - w, size.height - w)
                    val k = grow.value
                    // home grows from the top towards its own side (the start side), away the other way
                    val dir = if (rtl) 1f else -1f
                    drawArc(m.homeColor, -90f, dir * 360f * hs * k, false, o, sz, style = Stroke(w))
                    drawArc(m.awayColor, -90f, -dir * 360f * (1 - hs) * k, false, o, sz, style = Stroke(w))
                }
                T(L("مالکیت"), 13, FontWeight.Bold, C.mute)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Crest(m.awayId, 30.dp); T(fa("${(a * grow.value).roundToInt()}٪"), 30, FontWeight.Black, m.awayColor)
            }
        }
    }
}

/** "درصدها": the rates that tell a match best, as two small rings per line (pass accuracy, duels won, shots on target, dribbles). */
@Composable
private fun RateRings(m: MatchData) {
    val tt = remember(m.d) { teamTotals(m) }
    fun rate(key: String, side: Int) = tt[key]?.let { (v, t) -> if (t[side] > 0) (v[side] / t[side]).toFloat() else null }
    fun duel(side: Int): Float? {
        val v = (tt["ground_duels_won"]?.first?.get(side) ?: 0.0) + (tt["aerials_won"]?.first?.get(side) ?: 0.0)
        val t = (tt["ground_duels_won"]?.second?.get(side) ?: 0.0) + (tt["aerials_won"]?.second?.get(side) ?: 0.0)
        return if (t > 0) (v / t).toFloat() else null
    }
    val shots = m.shots.filter { !it.b("isPenaltyShootoutEvent") }
    fun onTarget(home: Boolean): Float? { val l = shots.filter { (it.i("teamId") == m.homeId) == home }; return if (l.isEmpty()) null else l.count { it.b("isOnTarget") }.toFloat() / l.size }
    val lines = listOf(
        L("دقت پاس") to (rate("accurate_passes", 0) to rate("accurate_passes", 1)),
        L("نبردهای برده") to (duel(0) to duel(1)),
        L("شوت در چارچوب") to (onTarget(true) to onTarget(false)),
        L("دریبل موفق") to (rate("dribbles_succeeded", 0) to rate("dribbles_succeeded", 1)),
        L("سانتر دقیق") to (rate("accurate_crosses", 0) to rate("accurate_crosses", 1)),
    ).filter { it.second.first != null && it.second.second != null }
    if (lines.size < 2) return
    Card(pad = PaddingValues(vertical = 12.dp)) {
        T(L("درصدها"), 15, FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 16.dp))
        lines.forEach { (label, pr) ->
            val (h, a) = pr
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Donut(h!!, m.homeColor, 58.dp, 7.dp) { T(fa("${(h * 100).roundToInt()}٪"), 13, FontWeight.Black) }
                T(label, 13, FontWeight.Bold, C.ink, Modifier.weight(1f), align = TextAlign.Center)
                Donut(a!!, m.awayColor, 58.dp, 7.dp) { T(fa("${(a * 100).roundToInt()}٪"), 13, FontWeight.Black) }
            }
        }
    }
}

/**
 * "رادار دو تیم": each axis is one side of the game (chances, shots, passing, creation, duels, defending, possession) and each
 * team's share of it, so the two shapes show at a glance where each side was stronger; half way is an even game.
 */
@Composable
private fun TeamRadar(m: MatchData, possession: Pair<Int, Int>?) {
    val tt = remember(m.d) { teamTotals(m) }
    fun v(key: String, side: Int) = tt[key]?.first?.get(side) ?: 0.0
    val shots = m.shots.filter { !it.b("isPenaltyShootoutEvent") }
    val axes = listOfNotNull(
        (L("xG") to (v("expected_goals", 0) to v("expected_goals", 1))),
        (L("شوت") to (shots.count { it.i("teamId") == m.homeId }.toDouble() to shots.count { it.i("teamId") != m.homeId }.toDouble())),
        (L("پاس دقیق") to (v("accurate_passes", 0) to v("accurate_passes", 1))),
        (L("موقعیت‌سازی") to (v("chances_created", 0) to v("chances_created", 1))),
        (L("نبرد") to (v("ground_duels_won", 0) + v("aerials_won", 0) to v("ground_duels_won", 1) + v("aerials_won", 1))),
        (L("دفاع") to (v("recoveries", 0) + v("interceptions", 0) + v("clearances", 0) to v("recoveries", 1) + v("interceptions", 1) + v("clearances", 1))),
        possession?.let { L("مالکیت") to (it.first.toDouble() to it.second.toDouble()) },
    ).filter { it.second.first + it.second.second > 0 }
    if (axes.size < 5) return
    val share = axes.map { (_, p) -> (p.first / (p.first + p.second)).toFloat() }
    val grow = remember { Animatable(0f) }
    LaunchedEffect(m.matchId) { grow.animateTo(1f, androidx.compose.animation.core.tween(900)) }
    val line = C.line2; val hc = m.homeColor; val ac = m.awayColor
    Card(pad = PaddingValues(12.dp)) {
        T(L("رادار دو تیم"), 15, FontWeight.ExtraBold)
        T(L("سهم هر تیم از هر بخش بازی؛ وسط یعنی برابر"), 12, color = C.mute)
        CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
            BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(1.15f)) {
                val w = maxWidth; val h = maxHeight
                val n = axes.size
                fun ang(i: Int) = Math.toRadians(-90.0 + 360.0 * i / n)
                Canvas(Modifier.fillMaxSize()) {
                    val c = Offset(size.width / 2, size.height / 2); val r = minOf(size.width, size.height) * 0.36f
                    fun pt(i: Int, k: Float) = c + Offset((cos(ang(i)) * r * k).toFloat(), (sin(ang(i)) * r * k).toFloat())
                    for (ring in listOf(0.25f, 0.5f, 0.75f, 1f)) {
                        val path = Path().apply { for (i in 0 until n) { val p = pt(i, ring); if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }; close() }
                        drawPath(path, line, style = Stroke(if (ring == 0.5f) 2.dp.toPx() else 1.dp.toPx()))
                    }
                    for (i in 0 until n) drawLine(line, c, pt(i, 1f), 1.dp.toPx())
                    for ((col, sh) in listOf(hc to share, ac to share.map { 1 - it })) {
                        val path = Path().apply { for (i in 0 until n) { val p = pt(i, sh[i] * grow.value); if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }; close() }
                        drawPath(path, col.copy(alpha = 0.28f)); drawPath(path, col, style = Stroke(2.5.dp.toPx(), join = androidx.compose.ui.graphics.StrokeJoin.Round))
                    }
                }
                axes.forEachIndexed { i, (label, _) ->
                    val lx = 0.5f + (cos(ang(i)) * 0.36f * 1.3f * minOf(w.value, h.value) / w.value).toFloat()
                    val ly = 0.5f + (sin(ang(i)) * 0.36f * 1.22f * minOf(w.value, h.value) / h.value).toFloat()
                    Box(Modifier.absoluteOffset(w * lx - 50.dp, h * ly - 11.dp).width(100.dp), contentAlignment = Alignment.Center) {
                        T(label, 12, FontWeight.Bold, C.mute, maxLines = 1)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)) {
            for ((id, name, col) in listOf(Triple(m.homeId, m.homeName, hc), Triple(m.awayId, m.awayName, ac))) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(col)); T(teamName(id, name), 12, FontWeight.Bold, maxLines = 1)
            }
        }
    }
}

/** A player's number for a FotMob stat key (the same key in whichever group it sits). */
private fun keyStat(p: JSONObject, key: String): Double? {
    for (g in p.a("stats").objs()) {
        val all = g.o("stats") ?: continue
        for (t in all.keys()) { val one = all.optJSONObject(t) ?: continue; if (one.s("key") == key) return one.o("stat").d("value") }
    }
    return null
}

/**
 * "برترین‌ها در هر آمار": who led the match in each department (most key passes, touches in the box, dribbles, recoveries,
 * tackles won, aerials, distance…), as SofaScore's top players list does. A tap opens the player.
 */
@Composable
private fun StatLeaders(m: MatchData) {
    val nav = LocalNav.current
    val stats = m.content.o("playerStats") ?: return
    val players = remember(m.d) { stats.keys().asSequence().mapNotNull { stats.optJSONObject(it) }.toList() }
    val boards = listOf(
        "expected_goals" to L("بیشترین xG"), "chances_created" to L("بیشترین پاس کلیدی"), "expected_assists" to L("بیشترین xA"),
        "touches_opp_box" to L("بیشترین لمس در محوطه حریف"), "dribbles_succeeded" to L("بیشترین دریبل موفق"), "accurate_passes" to L("بیشترین پاس دقیق"),
        "passes_into_final_third" to L("بیشترین پاس به یک‌سوم حمله"), "recoveries" to L("بیشترین بازپس‌گیری"), "interceptions" to L("بیشترین قطع توپ"),
        "ground_duels_won" to L("بیشترین نبرد زمینی برده"), "aerials_won" to L("بیشترین نبرد هوایی برده"), "clearances" to L("بیشترین دفع توپ"),
        "saves" to L("بیشترین سیو"), "physical_metrics_distance_covered" to L("بیشترین مسافت دویده"), "physical_metrics_number_of_sprints" to L("بیشترین دوی سرعت"),
    ).mapNotNull { (key, title) ->
        val best = players.mapNotNull { p -> keyStat(p, key)?.let { p to it } }.maxByOrNull { it.second } ?: return@mapNotNull null
        if (best.second <= 0.0) null else Triple(title, best.first, if (key.startsWith("physical_metrics_distance")) L("{0} کیلومتر", dec(best.second / 1000, 1))
            else if (key.startsWith("expected")) fa(dec(best.second, 2)) else fa(best.second.roundToInt()))
    }
    if (boards.size < 3) return
    Card {
        CardTitle(L("🏅 برترین‌ها در هر آمار"))
        boards.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { (title, p, value) ->
                    val id = p.i("id") ?: 0
                    val home = p.i("teamId") == m.homeId
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(C.card2).border(1.dp, C.line, RoundedCornerShape(18.dp))
                        .clickable { nav.player(id) }.padding(10.dp)) {
                        T(title, 11, FontWeight.Bold, C.mute, maxLines = 1)
                        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Face(id, 34.dp, ring = if (home) m.homeColor else m.awayColor)
                            Column(Modifier.weight(1f)) {
                                Ltr(personName((p.s("name") ?: "").substringAfterLast(' ')), style = vz(13.sp, FontWeight.ExtraBold))
                                T(value, 15, FontWeight.Black, LocalAccent.current, maxLines = 1)
                            }
                        }
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}

/** One team total that FotMob's team stats leave out; [better] is 1 when more is good for the team, -1 when less is. */
private class Extra(val key: String, val title: String, val better: Int = 1, val digits: Int = 0, val km: Boolean = false)

private val EXTRA_GROUPS: List<Pair<String, List<Extra>>> get() = listOf(
    L("ساختن موقعیت") to listOf(Extra("chances_created", L("پاس کلیدی")), Extra("expected_assists", L("پاس گل مورد انتظار (xA)"), digits = 2),
        Extra("touches_opp_box", L("لمس توپ در محوطه حریف")), Extra("was_fouled", L("خطای گرفته"))),
    L("پاس و بازی‌سازی") to listOf(Extra("passes_into_final_third", L("پاس به یک‌سوم حمله")), Extra("line_breaking_passes", L("پاس خط‌شکن")),
        Extra("long_balls_accurate", L("توپ بلند دقیق")), Extra("accurate_crosses", L("سانتر دقیق"))),
    L("دفاع") to listOf(Extra("recoveries", L("بازپس‌گیری توپ")), Extra("interceptions", L("قطع توپ")), Extra("clearances", L("دفع توپ")),
        Extra("shot_blocks", L("شوت بلاک‌شده")), Extra("last_man_tackle", L("تکل آخرین مدافع")), Extra("dribbled_past", L("دریبل خورده"), better = -1)),
    L("اشتباه‌ها") to listOf(Extra("dispossessed", L("توپ از دست داده"), better = -1), Extra("errors_led_to_goal", L("اشتباه منجر به گل"), better = -1),
        Extra("conceded_penalties", L("پنالتی داده"), better = -1)),
    L("دروازه‌بانی") to listOf(Extra("goals_prevented", L("گل جلوگیری‌شده"), digits = 2), Extra("keeper_high_claim", L("مهار توپ هوایی")),
        Extra("punches", L("مشت زدن توپ")), Extra("keeper_sweeper", L("خروج از محوطه"))),
    L("بدنی") to listOf(Extra("physical_metrics_distance_covered", L("مسافت دویده (کیلومتر)"), digits = 1, km = true),
        Extra("physical_metrics_number_of_sprints", L("دوی سرعت"))),
)

/**
 * "آمار تکمیلی": the team numbers a fan finds on SofaScore but not in FotMob's team stats (key passes, recoveries, balls lost,
 * goals prevented, distance run…), added up from every player's stats in this match. Rows FotMob already shows are left out.
 */
@Composable
private fun MoreTeamStats(m: MatchData, have: Set<String>) {
    val totals = remember(m.d) { teamTotals(m).mapValues { it.value.first } }
    val groups = EXTRA_GROUPS.map { (title, list) ->
        title to list.filter { it.key !in have && totals[it.key]?.let { v -> v[0] != 0.0 || v[1] != 0.0 } == true }
    }.filter { it.second.isNotEmpty() }
    if (groups.isEmpty()) return
    Card {
        CardTitle(L("📈 آمار تکمیلی"), sub = L("جمع آمار بازیکنان هر تیم"))
        groups.forEachIndexed { k, (title, list) ->
            if (k > 0) Divider()
            T(title, 13, FontWeight.ExtraBold, LocalAccent.current, Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 2.dp))
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) {
                list.forEach { x ->
                    val v = totals.getValue(x.key).let { if (x.km) doubleArrayOf(it[0] / 1000, it[1] / 1000) else it }
                    fun show(d: Double) = if (x.digits == 0) "${d.roundToInt()}" else dec(d, x.digits).let { if (x.key == "goals_prevented" && d > 0) "+$it" else it }
                    val hi = when { v[0] == v[1] -> null; (v[0] > v[1]) == (x.better > 0) -> "home"; else -> "away" }
                    val row = JSONObject().put("title", x.title).put("stats", org.json.JSONArray(listOf(show(v[0]), show(v[1]))))
                        .put("rawStats", org.json.JSONArray(listOf(JSONObject().put("value", v[0]), JSONObject().put("value", v[1]))))
                    if (hi != null) row.put("highlighted", hi)
                    StatRow(row, m.homeColor, m.awayColor)
                }
            }
        }
    }
}

@Composable
private fun StatRow(st: JSONObject, homeColor: Color, awayColor: Color) {
    if (st.s("type") == "title") { T(tr(st.s("title")), 12, FontWeight.ExtraBold, C.mute, Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp), align = TextAlign.Center); return }
    val v = st.a("stats").strs()
    if (v.size < 2) return
    val raw = st.a("rawStats").objs()
    fun value(idx: Int) = raw.getOrNull(idx).d("value") ?: v[idx].substringBefore(' ').replace("%", "").toDoubleOrNull() ?: 0.0
    val h = value(0); val a = value(1)
    val hi = st.s("highlighted")
    if (st.s("type") == "graph" || st.s("key") == "BallPossesion") {
        Column(Modifier.padding(vertical = 6.dp)) {
            T(tr(st.s("title")), 12, FontWeight.SemiBold, C.mute, modifier = Modifier.fillMaxWidth(), align = TextAlign.Center)
            Row(Modifier.fillMaxWidth().padding(top = 4.dp).height(26.dp).clip(RoundedCornerShape(14.dp)), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
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
    val grow = rememberGrow(st.s("key") ?: st.s("title") ?: "")
    Column(Modifier.padding(vertical = 5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatValue(v[0], hi == "home", homeColor, grow)
            T(tr(st.s("title")), 12, FontWeight.SemiBold, modifier = Modifier.weight(1f), align = TextAlign.Center)
            StatValue(v[1], hi == "away", awayColor, grow)
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            // each bar is anchored on its own outer edge, like the site
            Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(4.dp)).background(C.card2), contentAlignment = Alignment.CenterStart) {
                Box(Modifier.fillMaxWidth((h / tot).toFloat().coerceIn(0f, 1f) * grow).fillMaxSize().clip(RoundedCornerShape(6.dp)).background(homeColor))
            }
            Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(4.dp)).background(C.card2), contentAlignment = Alignment.CenterEnd) {
                Box(Modifier.fillMaxWidth((a / tot).toFloat().coerceIn(0f, 1f) * grow).fillMaxSize().clip(RoundedCornerShape(6.dp)).background(awayColor))
            }
        }
    }
}

@Composable
private fun StatValue(v: String, hi: Boolean, color: Color, grow: Float = 1f) {
    Box(Modifier.widthIn(min = 34.dp).clip(RoundedCornerShape(10.dp)).background(if (hi) color else Color.Transparent).padding(horizontal = 7.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center) {
        Ltr(fa(countingUp(v, grow)), style = num(13.sp), color = if (hi) inkOn(color) else C.ink)
    }
}

/** 0 → 1 over a moment, once per [key]: stat numbers count up and their bars grow when they come into view. */
@Composable
private fun rememberGrow(key: Any): Float {
    val a = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { a.animateTo(1f, androidx.compose.animation.core.tween(900, easing = androidx.compose.animation.core.FastOutSlowInEasing)) }
    return a.value
}

/** [v]'s leading number at [p] of its value, the rest ("(45%)", "%") kept as it is. */
private fun countingUp(v: String, p: Float): String {
    if (p >= 1f) return v
    val m = Regex("^(\\d+)(\\.(\\d+))?(.*)$").find(v) ?: return v
    val n = (m.groupValues[1] + m.groupValues[2]).toDoubleOrNull() ?: return v
    val dec = m.groupValues[3].length
    val shown = n * p
    return (if (dec == 0) Math.round(shown).toString() else String.format(java.util.Locale.US, "%.${dec}f", shown)) + m.groupValues[4]
}


/* ---------- xG race ---------- */

/**
 * "روند xG": each team's chance quality added up shot by shot over the minutes, as two stepped lines, goals as dots on them.
 * Time runs left to right whatever the language, like every match graph.
 */
@Composable
private fun XgRace(m: MatchData) {
    val shots = m.shots.filter { !it.b("isPenaltyShootoutEvent") }.sortedBy { (it.i("min") ?: 0) * 100 + (it.i("minAdded") ?: 0) }
    if (shots.size < 3) return
    val minuteOf = { s: JSONObject -> (s.i("min") ?: 0) + (s.i("minAdded") ?: 0) * 0.1f }
    val end = maxOf(90f, shots.maxOf(minuteOf) + 2f, if (m.live) (cleanMinute(m.minute).filter(Char::isDigit).toIntOrNull() ?: 0).toFloat() else 0f)
    fun line(home: Boolean): List<Triple<Float, Float, Boolean>> {
        var sum = 0f
        return shots.filter { (it.i("teamId") == m.homeId) == home }.map { s -> sum += (s.d("expectedGoals") ?: 0.0).toFloat(); Triple(minuteOf(s), sum, s.s("eventType") == "Goal") }
    }
    val h = line(true); val a = line(false)
    val top = maxOf(1f, h.lastOrNull()?.second ?: 0f, a.lastOrNull()?.second ?: 0f) * 1.1f
    Card(pad = PaddingValues(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            T(L("روند xG"), 15, FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            T(fa(dec(h.lastOrNull()?.second?.toDouble() ?: 0.0, 2)), 14, FontWeight.Black, m.homeColor)
            T("  –  ", 13, color = C.faint)
            T(fa(dec(a.lastOrNull()?.second?.toDouble() ?: 0.0, 2)), 14, FontWeight.Black, m.awayColor)
        }
        T(L("کیفیت موقعیت‌های هر تیم که در طول بازی جمع شده؛ دایره‌ها گل‌اند"), 12, color = C.mute, modifier = Modifier.padding(bottom = 8.dp))
        CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
            Column {
                val grid = C.line; val bg = C.cardSolid
                Canvas(Modifier.fillMaxWidth().height(150.dp)) {
                    fun x(t: Float) = size.width * (t / end).coerceIn(0f, 1f)
                    fun y(v: Float) = size.height * (1f - v / top)
                    for (t in listOf(15f, 30f, 45f, 60f, 75f, 90f)) drawLine(grid, Offset(x(t), 0f), Offset(x(t), size.height), if (t == 45f) 2f else 1f)
                    drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height), 2f)
                    for ((pts, col) in listOf(h to m.homeColor, a to m.awayColor)) {
                        val path = Path().apply {
                            moveTo(0f, y(0f)); var last = 0f
                            pts.forEach { (t, v, _) -> lineTo(x(t), y(last)); lineTo(x(t), y(v)); last = v }
                            lineTo(x(end), y(last))
                        }
                        drawPath(path, col, style = Stroke(3.dp.toPx(), join = androidx.compose.ui.graphics.StrokeJoin.Round))
                        pts.filter { it.third }.forEach { (t, v, _) ->
                            drawCircle(col, 7.dp.toPx(), Offset(x(t), y(v))); drawCircle(bg, 3.5.dp.toPx(), Offset(x(t), y(v)))
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    listOf(0, 45, 90).forEach { t -> T(fa("$t′"), 11, color = C.mute, modifier = Modifier.weight(1f), align = when (t) { 0 -> TextAlign.Start; 45 -> TextAlign.Center; else -> TextAlign.End }) }
                }
            }
        }
    }
}

/* ---------- shot breakdown ---------- */

/** Two numbers side by side with bars, home on the start side, the larger one in its team's colour. */
@Composable
private fun DuoRow(label: String, h: Double, a: Double, hText: String, aText: String, m: MatchData) {
    val tot = (h + a).takeIf { it > 0 } ?: 1.0
    Column(Modifier.padding(vertical = 5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            T(hText, 14, FontWeight.Black, if (h > a) m.homeColor else C.ink)
            T(label, 12, FontWeight.SemiBold, C.mute, Modifier.weight(1f), align = TextAlign.Center)
            T(aText, 14, FontWeight.Black, if (a > h) m.awayColor else C.ink)
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(4.dp)).background(C.card2), contentAlignment = Alignment.CenterStart) {
                Box(Modifier.fillMaxWidth((h / tot).toFloat().coerceIn(0f, 1f)).fillMaxSize().background(m.homeColor))
            }
            Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(4.dp)).background(C.card2), contentAlignment = Alignment.CenterEnd) {
                Box(Modifier.fillMaxWidth((a / tot).toFloat().coerceIn(0f, 1f)).fillMaxSize().background(m.awayColor))
            }
        }
    }
}

/** A shot from inside the penalty area (FotMob's x runs 0…105 towards the goal attacked, y 0…68 across). */
internal fun insideBox(s: JSONObject) = (s.d("x") ?: 0.0) >= 105 - 16.5 && (s.d("y") ?: 34.0) in 13.84..54.16

/**
 * "ریز شوت‌ها": where the shots came from (inside or outside the box), how (open play, counter-attack, set pieces, penalties),
 * with what (foot or head), how they ended, and the big chances: the split SofaScore shows under its shot map.
 */
@Composable
private fun ShotBreakdown(m: MatchData) {
    val shots = m.shots.filter { !it.b("isPenaltyShootoutEvent") }
    if (shots.size < 3) return
    val home = { s: JSONObject -> s.i("teamId") == m.homeId }
    val setPiece = setOf("FromCorner", "SetPiece", "FreeKick", "ThrowInSetPiece")
    val rows = listOf<Pair<String, (JSONObject) -> Boolean>>(
        L("داخل محوطه") to { s -> insideBox(s) },
        L("بیرون محوطه") to { s -> !insideBox(s) },
        L("موقعیت عالی (xG بالای ۰٫۳)") to { s -> (s.d("expectedGoals") ?: 0.0) >= 0.3 },
        L("از جریان بازی") to { s -> s.s("situation") in setOf("RegularPlay", "IndividualPlay") },
        L("از ضدحمله") to { s -> s.s("situation") == "FastBreak" },
        L("از ضربه ایستگاهی") to { s -> s.s("situation") in setPiece },
        L("پنالتی") to { s -> s.s("situation") == "Penalty" },
        L("با پا") to { s -> s.s("shotType") in setOf("LeftFoot", "RightFoot") },
        L("ضربه سر") to { s -> s.s("shotType") == "Header" },
        L("در چارچوب") to { s -> s.b("isOnTarget") },
        L("بلاک شد") to { s -> s.b("isBlocked") },
        L("به تیرک خورد") to { s -> s.s("eventType") == "Post" },
    )
    Card(pad = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
        T(L("ریز شوت‌ها"), 15, FontWeight.ExtraBold, modifier = Modifier.padding(bottom = 4.dp))
        rows.forEach { (label, f) ->
            val h = shots.count { home(it) && f(it) }; val a = shots.count { !home(it) && f(it) }
            if (h + a > 0) DuoRow(label, h.toDouble(), a.toDouble(), fa(h), fa(a), m)
        }
        // chance quality per shot: a few good chances or many hopeful ones
        val hs = shots.filter(home); val aw = shots.filterNot(home)
        fun per(l: List<JSONObject>) = if (l.isEmpty()) 0.0 else l.sumOf { it.d("expectedGoals") ?: 0.0 } / l.size
        DuoRow(L("xG در هر شوت"), per(hs), per(aw), fa(dec(per(hs), 2)), fa(dec(per(aw), 2)), m)
    }
}

/** "شوت‌ها در هر ۱۵ دقیقه": each team's shots and xG per quarter of an hour, so you see when each side was on top. */
@Composable
private fun ShotSlices(m: MatchData) {
    val shots = m.shots.filter { !it.b("isPenaltyShootoutEvent") }
    if (shots.size < 4) return
    val names = listOf("۰-۱۵", "۱۶-۳۰", "۳۱-۴۵+", "۴۶-۶۰", "۶۱-۷۵", "۷۶-۹۰+")
    // stoppage time counts with its half (45+2 is still the 31-45 slice); extra time stays out
    val slice = { s: JSONObject -> val mn = s.i("min") ?: 0; when { mn <= 15 -> 0; mn <= 30 -> 1; mn <= 45 -> 2; mn <= 60 -> 3; mn <= 75 -> 4; mn <= 90 -> 5; else -> -1 } }
    val extra = shots.any { (it.i("min") ?: 0) > 90 }
    val counts = Array(2) { IntArray(6) }; val xg = Array(2) { DoubleArray(6) }
    shots.forEach { s -> val k = slice(s); if (k >= 0) { val t = if (s.i("teamId") == m.homeId) 0 else 1; counts[t][k]++; xg[t][k] += s.d("expectedGoals") ?: 0.0 } }
    val top = maxOf(0.3, xg.maxOf { it.max() })
    Card(pad = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
        T(L("شوت‌ها در هر ۱۵ دقیقه"), 15, FontWeight.ExtraBold)
        T(L("ستون‌ها xG هر تیم‌اند، عددها تعداد شوت"), 12, color = C.mute, modifier = Modifier.padding(bottom = 8.dp))
        // time runs left to right, like the xG race above it
        CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
        Row(Modifier.fillMaxWidth().height(130.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
            for (k in 0 until 6) Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    for (t in 0..1) Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom, modifier = Modifier.fillMaxHeight()) {
                        if (counts[t][k] > 0) T(fa(counts[t][k]), 12, FontWeight.Bold, C.mute)
                        Box(Modifier.width(14.dp).fillMaxHeight((xg[t][k] / top).toFloat().coerceIn(0.02f, 0.82f)).clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(if (t == 0) m.homeColor else m.awayColor))
                    }
                }
                T(names[k], 11, color = C.mute, modifier = Modifier.padding(top = 4.dp), maxLines = 1)
            }
        }
        }
        if (extra) T(L("شوت‌های وقت‌های اضافه در این نمودار نیامده"), 11, color = C.faint, modifier = Modifier.padding(top = 6.dp))
    }
}

/* ---------- shotmap ---------- */

/**
 * Shots: one team at a time on its attacking half, the goal at the top, as on a broadcast graphic. Each dot is a shot, as big as
 * the chance was (xG); a goal is filled with a ring, a saved shot half-filled, a blocked one grey, a miss just an outline.
 */
@Composable
private fun ShotsTab(m: MatchData) {
    var team by rememberSaveable { mutableStateOf("h") }
    var sel by remember { mutableStateOf<Int?>(null) }
    var who by remember { mutableStateOf<Int?>(null) }
    val isH = { s: JSONObject -> s.i("teamId") == m.homeId }
    val home = team == "h"
    val teamShots = m.shots.filter { isH(it) == home }
    // one player's shots only, when picked under the map
    val vis = teamShots.filter { who == null || it.i("playerId") == who }
    val color = if (home) m.homeColor else m.awayColor
    Seg(listOf("h" to teamName(m.homeId, m.homeName), "a" to teamName(m.awayId, m.awayName)), team) { team = it; sel = null; who = null }
    // both teams side by side: chance quality and shots
    fun sum(arr: List<JSONObject>) = Triple(arr.sumOf { it.d("expectedGoals") ?: 0.0 }, arr.size, arr.count { it.b("isOnTarget") })
    val hs = sum(m.shots.filter(isH)); val aw = sum(m.shots.filter { !isH(it) })
    Card(pad = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
        for ((label, h, a, txt) in listOf(
            Quad(L("کیفیت موقعیت‌ها (xG)"), hs.first, aw.first, fa(dec(hs.first, 2)) to fa(dec(aw.first, 2))),
            Quad(L("شوت"), hs.second.toDouble(), aw.second.toDouble(), fa(hs.second) to fa(aw.second)),
            Quad(L("شوت در چارچوب"), hs.third.toDouble(), aw.third.toDouble(), fa(hs.third) to fa(aw.third)),
        )) {
            val tot = (h + a).takeIf { it > 0 } ?: 1.0
            Column(Modifier.padding(vertical = 5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    T(txt.first, 14, FontWeight.Black, m.homeColor); T(label, 12, FontWeight.SemiBold, C.mute, Modifier.weight(1f), align = TextAlign.Center); T(txt.second, 14, FontWeight.Black, m.awayColor)
                }
                Row(Modifier.fillMaxWidth().padding(top = 4.dp).height(5.dp).clip(RoundedCornerShape(5.dp)), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Box(Modifier.weight(max(h / tot, 0.02).toFloat()).fillMaxSize().background(m.homeColor))
                    Box(Modifier.weight(max(a / tot, 0.02).toFloat()).fillMaxSize().background(m.awayColor))
                }
            }
        }
    }
    XgRace(m)
    ShotBreakdown(m)
    ShotSlices(m)
    Card(pad = PaddingValues(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Crest(if (home) m.homeId else m.awayId, 22.dp)
            T(L("شوت‌های {0}", teamName(if (home) m.homeId else m.awayId, if (home) m.homeName else m.awayName)), 15, FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            T(L("{0} شوت", fa(vis.size)), 12, FontWeight.Bold, C.mute)
        }
        // who shot: all of them, or one player's shots with his chance total
        val shooters = teamShots.groupBy { it.i("playerId") ?: 0 }.filterKeys { it != 0 }.entries.sortedByDescending { e -> e.value.sumOf { it.d("expectedGoals") ?: 0.0 } }
        if (shooters.size > 1) androidx.compose.foundation.lazy.LazyRow(Modifier.padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            item {
                Box(Modifier.clip(RoundedCornerShape(50)).background(if (who == null) color else C.card2).clickable { who = null; sel = null }
                    .padding(horizontal = 12.dp, vertical = 7.dp)) { T(L("همه"), 12, FontWeight.Bold, if (who == null) inkOn(color) else C.ink) }
            }
            items(shooters.size) { k ->
                val (pid, list) = shooters[k]
                val on = pid == who
                Row(Modifier.clip(RoundedCornerShape(50)).background(if (on) color else C.card2).clickable { who = if (on) null else pid; sel = null }
                    .padding(start = 4.dp, end = 10.dp, top = 3.dp, bottom = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Face(pid, 24.dp); Spacer(Modifier.width(5.dp))
                    T(personName(list.first().s("lastName") ?: list.first().s("playerName") ?: ""), 12, FontWeight.Bold, if (on) inkOn(color) else C.ink, maxLines = 1)
                    Spacer(Modifier.width(5.dp))
                    T(fa(list.size), 11, FontWeight.Black, if (on) inkOn(color) else C.mute)
                }
            }
        }
        if (who != null && vis.isNotEmpty()) T(L("{0} شوت · {1} در چارچوب · کیفیت موقعیت‌ها {2}", fa(vis.size), fa(vis.count { it.b("isOnTarget") }),
            fa(dec(vis.sumOf { it.d("expectedGoals") ?: 0.0 }, 2))), 12, FontWeight.Bold, C.mute, Modifier.padding(bottom = 8.dp))
        // the attacking half stood up: FotMob's x runs 0…105 towards the goal attacked, y 0…68 across
        CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
            BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(68f / 56f).clip(RoundedCornerShape(18.dp))) {
                val w = maxWidth; val hgt = maxHeight
                fun px(s: JSONObject) = ((s.d("y") ?: 34.0) / 68.0).toFloat().coerceIn(0.02f, 0.98f)
                fun py(s: JSONObject) = ((105.0 - (s.d("x") ?: 90.0)) / 56.0).toFloat().coerceIn(0.02f, 0.98f)
                Canvas(Modifier.fillMaxSize()) {
                    val sx = size.width / 68f; val sy = size.height / 56f
                    for (i in 0 until 8) drawRect(if (i % 2 == 0) Color(0xFF123A26) else Color(0xFF103421), Offset(0f, i * size.height / 8), Size(size.width, size.height / 8 + 1))
                    val c = Color.White.copy(alpha = 0.5f); val st = Stroke(1.3.dp.toPx())
                    drawRect(c, Offset(1f, 1f), Size(size.width - 2f, size.height + 20f), style = st)
                    drawRect(c, Offset(13.84f * sx, 0f), Size(40.32f * sx, 16.5f * sy), style = st)
                    drawRect(c, Offset(24.84f * sx, 0f), Size(18.32f * sx, 5.5f * sy), style = st)
                    drawCircle(c, 1.8.dp.toPx(), Offset(34f * sx, 11f * sy))
                    drawArc(c, 36.87f, 106.26f, false, Offset(34f * sx - 9.15f * sx, 11f * sy - 9.15f * sx), Size(18.3f * sx, 18.3f * sx), style = st)
                    // halfway line and the centre circle at the bottom
                    drawLine(c, Offset(0f, 52.5f * sy), Offset(size.width, 52.5f * sy), st.width)
                    drawArc(c, 180f, 180f, false, Offset(34f * sx - 9.15f * sx, 52.5f * sy - 9.15f * sx), Size(18.3f * sx, 18.3f * sx), style = st)
                    // the goal
                    drawRect(Color.White.copy(alpha = 0.9f), Offset(30.34f * sx, 0f), Size(7.32f * sx, 3.dp.toPx()))
                    // biggest chances under the small ones, so every dot stays visible
                    for (s in vis.sortedByDescending { it.d("expectedGoals") ?: 0.0 }) {
                        val o = Offset(px(s) * size.width, py(s) * size.height)
                        val r = (5 + 22 * sqrt(s.d("expectedGoals") ?: 0.02)).dp.toPx() / 2.2f
                        val goal = s.s("eventType") == "Goal"
                        when {
                            goal -> { drawCircle(color, r, o); drawCircle(Color.White, r, o, style = Stroke(2.dp.toPx())); drawCircle(Color.White, r * 0.35f, o) }
                            s.b("isBlocked") -> drawCircle(Color(0xFF9AA4AE).copy(alpha = 0.75f), r, o)
                            s.b("isOnTarget") -> { drawCircle(color.copy(alpha = 0.55f), r, o); drawCircle(color, r, o, style = Stroke(1.6.dp.toPx())) }
                            else -> drawCircle(color, r, o, style = Stroke(1.8.dp.toPx()))
                        }
                        if (s.i("id") == sel) drawCircle(Color.White, r + 4.dp.toPx(), o, style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f))))
                    }
                }
                for (s in vis) Box(Modifier.align(AbsoluteAlignment.TopLeft).absoluteOffset(w * px(s) - 14.dp, hgt * py(s) - 14.dp).size(28.dp).clip(CircleShape).clickable { sel = s.i("id") })
            }
        }
        // what the dots mean
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            for ((kind, label) in listOf("goal" to L("گل"), "on" to L("مهار شد"), "block" to L("بلاک شد"), "off" to L("به بیرون"))) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Canvas(Modifier.size(12.dp)) {
                        val r = size.minDimension / 2.4f
                        when (kind) {
                            "goal" -> { drawCircle(color, r); drawCircle(Color.White, r, style = Stroke(1.5.dp.toPx())) }
                            "on" -> drawCircle(color.copy(alpha = 0.55f), r)
                            "block" -> drawCircle(Color(0xFF9AA4AE), r)
                            else -> drawCircle(color, r, style = Stroke(1.5.dp.toPx()))
                        }
                    }
                    T(label, 11, color = C.mute)
                }
            }
        }
        val s = vis.firstOrNull { it.i("id") == sel }
        if (s == null) T(L("روی هر شوت بزنید تا جزئیات آن را ببینید · بزرگی دایره = کیفیت موقعیت"), 11, color = C.faint, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), align = TextAlign.Center)
        else ShotInfo(s)
    }
    if (vis.isNotEmpty()) Card {
        CardTitle(L("همه شوت‌ها"))
        vis.sortedBy { it.i("min") ?: 0 }.forEachIndexed { k, s ->
            ListRow(first = k == 0, onClick = { sel = s.i("id") }) {
                Box(Modifier.width(34.dp).clip(RoundedCornerShape(50)).background(if (s.s("eventType") == "Goal") color else C.card2).padding(vertical = 3.dp),
                    contentAlignment = Alignment.Center) { T(fa(s.i("min") ?: 0) + "'", 11, FontWeight.ExtraBold, if (s.s("eventType") == "Goal") inkOn(color) else C.mute) }
                Column(Modifier.weight(1f)) {
                    T(personName(s.s("lastName") ?: s.s("playerName") ?: ""), 13, FontWeight.Bold, maxLines = 1)
                    T("${shotResult(s)} · ${tr(s.s("situation"))} · ${tr(s.s("shotType"))}", 11, color = C.mute, maxLines = 1)
                }
                T("xG " + fa(dec(s.d("expectedGoals"), 2)), 12, FontWeight.Bold, C.mute)
            }
        }
    }
}

private data class Quad(val a: String, val b: Double, val c: Double, val d: Pair<String, String>)

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
    Column(Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.card2).padding(horizontal = 14.dp, vertical = 12.dp)) {
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
/** Who came on for whom, from the match's substitution events: (player off id → player on), and the other way round. */
private class Swaps(val onFor: Map<Int, JSONObject>, val offFor: Map<Int, JSONObject>)

private fun swapsOf(m: MatchData): Swaps {
    val on = HashMap<Int, JSONObject>(); val off = HashMap<Int, JSONObject>()
    // the swap may carry only names: find their ids in the line-ups
    val byName = HashMap<String, Int>()
    for (k in listOf("homeTeam", "awayTeam")) m.content.path("lineup.$k").let { t -> (t.a("starters").objs() + t.a("subs").objs()).forEach { p -> p.i("id")?.let { id -> p.s("name")?.let { byName[it] = id } } } }
    fun idOf(x: JSONObject) = x.i("id") ?: x.s("name")?.let { byName[it] }
    for (e in m.events) {
        if (e.s("type") != "Substitution") continue
        val sw = e.a("swap").objs()
        val pin = sw.getOrNull(0) ?: continue; val pout = sw.getOrNull(1) ?: continue
        idOf(pout)?.let { on[it] = pin }; idOf(pin)?.let { off[it] = pout }
    }
    return Swaps(on, off)
}

private class LP(val p: JSONObject, val home: Boolean) {
    val id = p.i("id") ?: 0
    val name = p.s("name") ?: ""
    val short = personName(p.s("lastName") ?: name.substringAfterLast(' '))
    val number = p.s("shirtNumber") ?: ""
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
    val lu = m.content.o("lineup")!!
    val swaps = remember(m.d) { swapsOf(m) }
    CompositionLocalProvider(LocalSwaps provides swaps) { LineupBody(m, lu) }
}

private val LocalSwaps = staticCompositionLocalOf { Swaps(emptyMap(), emptyMap()) }

@Composable
private fun LineupBody(m: MatchData, lu: JSONObject) {
    val nav = LocalNav.current
    val H = lu.o("homeTeam")!!
    val A = lu.o("awayTeam")!!
    val hasLayout = H.a("starters").objs().any { it.o("verticalLayout") != null }
    // one team at a time: its eleven get the whole pitch, so nobody overlaps
    var side by rememberSaveable { mutableStateOf("h") }
    var sheet by remember { mutableStateOf<LP?>(null) }
    val home = side == "h"
    val team = if (home) H else A
    val color = if (home) m.homeColor else m.awayColor
    val id = if (home) m.homeId else m.awayId
    if ((lu.s("lineupType") ?: "standard") != "standard") Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pill(L("ترکیب احتمالی"), PillKind.Accent); T(L("ترکیب رسمی حدود یک ساعت قبل از بازی اعلام می‌شود"), 11, color = C.mute)
    }
    Seg(listOf("h" to teamName(m.homeId, m.homeName), "a" to teamName(m.awayId, m.awayName)), side) { side = it }
    val vs = teamName(m.homeId, m.homeName) + " " + (if (m.started) fa("${m.home.i("score") ?: 0} - ${m.away.i("score") ?: 0}") else "-") + " " + teamName(m.awayId, m.awayName) +
        " · " + leagueName(m.leagueId, m.general.s("leagueName") ?: "")
    if (hasLayout) Pitch(m, team, if (home) A else H, home, color, id, vs, { nav.board(m.matchId, side) }) { sheet = it }
    else TeamBar(team, home, color, id) { nav.board(m.matchId, side) }
    if (!hasLayout) Card { team.a("starters").objs().forEachIndexed { i, p -> LineupRow(LP(p, home), i == 0) { sheet = it } } }
    val subs = team.a("subs").objs().map { LP(it, home) }
    if (subs.isNotEmpty()) Card {
        CardTitle(L("ذخیره‌ها"), sub = subs.count { it.inAt != null }.takeIf { it > 0 }?.let { L("{0} بازیکن وارد زمین شد", it) })
        androidx.compose.foundation.lazy.LazyRow(contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(subs.sortedByDescending { if (it.inAt != null) 1000 - (it.inAt.toIntOrNull() ?: 0) else 0 }) { p -> BenchChip(p, color) { sheet = p } }
        }
    }
    Absentees(if (home) H else JSONObject(), if (home) JSONObject() else A)
    sheet?.let { p -> PlayerSheet(m, p) { sheet = null } }
}

private fun JSONObject.l2(k: String): Long? = (opt(k) as? Number)?.toLong()

/** Strip above/below the pitch: crest, team, coach, formation and team rating. */
@Composable
private fun TeamBar(t: JSONObject, home: Boolean, color: Color, id: Int, onBoard: () -> Unit) {
    val nav = LocalNav.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).drawBehind { cssLinear(270f, 0f to color.copy(alpha = 0.35f), 1f to Color.Transparent) }
            .background(C.card.copy(alpha = 0.7f)).border(1.dp, C.line, RoundedCornerShape(18.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Crest(id, 30.dp)
        Column(Modifier.weight(1f).clickable { nav.team(id) }) {
            TeamText(id, t.s("name") ?: "", size = 15, w = FontWeight.ExtraBold, maxLines = 1)
            t.o("coach")?.s("name")?.let { T(L("سرمربی: {0}", personName(it)), 11, color = C.mute, maxLines = 1) }
        }
        // open this team on the tactics board
        Box(Modifier.size(34.dp).clip(CircleShape).background(C.card2).clickable(onClick = onBoard), contentAlignment = Alignment.Center) { T("📋", 15) }
        Box(Modifier.clip(RoundedCornerShape(10.dp)).background(color).padding(horizontal = 10.dp, vertical = 4.dp)) {
            Ltr(fa(t.s("formation") ?: ""), style = vz(14.sp, FontWeight.ExtraBold), color = inkOn(color), raw = true)
        }
        Rating(teamRating(LocalRatings.current, home))
    }
}

@Composable
private fun Absentees(H: JSONObject, A: JSONObject) {
    val nav = LocalNav.current
    val all = H.a("unavailable").objs().map { it to teamName(H.i("id") ?: 0, H.s("name") ?: "") } + A.a("unavailable").objs().map { it to teamName(A.i("id") ?: 0, A.s("name") ?: "") }
    if (all.isEmpty()) return
    Card {
        CardTitle(L("غایبان"), sub = L("{0} بازیکن", all.size))
        all.forEachIndexed { i, (p, team) ->
            val type = p.o("unavailability").s("type")
            val ret = p.o("unavailability").s("expectedReturn")?.let { L(" · بازگشت: ") + if (it == "Doubtful") L("نامشخص") else returnFa(it) } ?: ""
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
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) { EventIcons(p); Rating(LocalRatings.current[p.id]?.value) }
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
    Column(Modifier.width(84.dp).clip(RoundedCornerShape(18.dp)).background(if (p.inAt != null) color.copy(alpha = 0.14f) else C.card2)
        .clickable(onClick = onClick).padding(vertical = 10.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            Kit(p, color, 44.dp)
            LocalRatings.current[p.id]?.value?.let { Rating(it, Modifier.align(Alignment.TopEnd).absoluteOffset(12.dp, (-6).dp), small = true) }
        }
        Ltr(p.short, Modifier.padding(top = 6.dp), style = lat(12.sp), align = TextAlign.Center)
        if (p.inAt != null) Row(verticalAlignment = Alignment.CenterVertically) {
            T("⇧", 11, FontWeight.Black, C.win); Ltr("${p.inAt}'", style = vz(11.sp, FontWeight.Bold), color = C.win)
        } else T("#${p.number}", 11, color = C.mute)
        // the player he came on for
        LocalSwaps.current.offFor[p.id]?.let { o ->
            Ltr(L("به‌جای ") + personName(o.s("lastName") ?: (o.s("name") ?: "").substringAfterLast(' ')), style = vz(10.sp, FontWeight.Bold), color = C.mute, maxLines = 1, raw = true)
        }
    }
}

/** One team's eleven on the formation pitch, keeper at the bottom; the header carries the team, coach, formation and rating. */
/** One team's eleven as [PitchMan]s, keeper at the bottom, the best rated starred. */
private fun pitchMen(team: JSONObject, home: Boolean, ratings: Map<Int, PlayerRating>, swaps: Swaps): Pair<List<LP>, List<PitchMan>> {
    val players = team.a("starters").objs().map { LP(it, home) }.filter { it.layout != null }
    val best = players.maxByOrNull { ratings[it.id]?.value ?: 0.0 }?.takeIf { ratings[it.id] != null }
    return players to players.map { p ->
        val v = p.layout!!
        val x = (v.d("x") ?: 0.5).toFloat(); val y = (v.d("y") ?: 0.5).toFloat()
        // FotMob: y 0.1 (keeper) … 0.87 (strikers); x 0 is the right back: seen from behind the team, that is the right edge
        PitchMan(p.id, p.short, 0.89f - x * 0.78f, 0.86f - (y - 0.1f) / 0.77f * 0.74f, p.number, ratings[p.id]?.value,
            p.goals, p.own, p.assists, p.yellow, p.red, p.outAt, p.captain, star = p === best,
            subIn = swaps.onFor[p.id]?.let { personName(it.s("lastName") ?: (it.s("name") ?: "").substringAfterLast(' ')) })
    }
}

/** The substitutes who came on, in order, then the coach: the strip under a line-up poster. */
private fun benchMen(team: JSONObject, home: Boolean, ratings: Map<Int, PlayerRating>): List<BenchMan> {
    val subs = team.a("subs").objs().map { LP(it, home) }.filter { it.inAt != null }
        .sortedBy { it.inAt!!.filter(Char::isDigit).take(3).toIntOrNull() ?: 99 }.take(5)
        .map { BenchMan(it.id, it.short, it.inAt, ratings[it.id]?.value, it.goals, it.assists, it.yellow) }
    val coach = team.o("coach")?.let { co -> BenchMan(co.i("id") ?: 0, personName(co.s("lastName") ?: (co.s("name") ?: "").substringAfterLast(' ')), coach = true) }
    return subs + listOfNotNull(coach)
}

/** One team's eleven on the formation pitch, keeper at the bottom; the header carries the team, coach, formation and rating. */
@Composable
private fun Pitch(m: MatchData, team: JSONObject, rival: JSONObject, home: Boolean, color: Color, id: Int, vs: String, onBoard: () -> Unit, onOpen: (LP) -> Unit) {
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    val ratings = LocalRatings.current
    val swaps = LocalSwaps.current
    val (players, men) = pitchMen(team, home, ratings, swaps)
    val best = players.firstOrNull { p -> men.any { it.id == p.id && it.star } }
    FormationPitch(men, color, header = {
        Crest(id, 32.dp)
        Column(Modifier.weight(1f).clickable { nav.team(id) }) {
            TeamText(id, team.s("name") ?: "", size = 15, w = FontWeight.ExtraBold, maxLines = 1, color = Color.White)
            team.o("coach")?.s("name")?.let { T(L("سرمربی: {0}", personName(it)), 11, color = Color.White.copy(alpha = 0.7f), maxLines = 1) }
        }
        PitchPill(fa(team.s("formation") ?: ""), color)
        Rating(teamRating(ratings, home))
        // the line-up as a story picture
        IconBtn(Ic.share, L("اشتراک‌گذاری"), Color.White, Color.White.copy(alpha = 0.12f), 18.dp) {
            if (busy) return@IconBtn
            busy = true
            scope.launch {
                runCatching {
                    val starName = best?.let { b -> personName(b.name) + " · " + fa(dec(ratings[b.id]?.value, 1)) }
                    val league = leagueName(m.leagueId, m.general.s("leagueName") ?: "")
                    val score = if (m.started) PosterScore(m.homeId, m.homeName, m.home.i("score") ?: 0, m.awayId, m.awayName, m.away.i("score") ?: 0) else null
                    val sub = listOf(league, persianDate(m.time, "EEEE d MMMM")).filter { it.isNotBlank() }.joinToString(" · ")
                    fun poster(t: JSONObject, h: Boolean, c: Color, tid: Int, xi: List<PitchMan>, other: Story.Lineup?) = Story.Lineup(
                        teamName(tid, t.s("name") ?: ""), sub, c.toArgb(), xi, tid, null, fa(t.s("formation") ?: ""),
                        starName?.let { L("بهترین بازیکن گل‌لایو: {0}", it) }, "goallive-lineup-$tid",
                        kicker = if ((m.content.path("lineup").s("lineupType") ?: "standard") != "standard") L("ترکیب احتمالی") else L("ترکیب اصلی"),
                        teamName = teamName(tid, t.s("name") ?: ""), bench = benchMen(t, h, ratings), score = score, home = h, rival = other)
                    val otherId = if (home) m.awayId else m.homeId
                    val otherMen = pitchMen(rival, !home, ratings, swaps).second
                    val them = if (otherMen.size >= 10) poster(rival, !home, if (home) m.awayColor else m.homeColor, otherId, otherMen, null) else null
                    StoryStudio.story = poster(team, home, color, id, men, them)
                }
                busy = false
            }
        }
    }) { man -> players.firstOrNull { it.id == man.id }?.let(onOpen) }
}

@Composable
private fun PitchPlayer(p: LP, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            Box(Modifier.padding(horizontal = 14.dp).glow(Color.Black.copy(alpha = 0.45f), 6.dp, CircleShape)) { Kit(p, color, 42.dp) }
            LocalRatings.current[p.id]?.value?.let { Rating(it, Modifier.align(Alignment.TopEnd).absoluteOffset(2.dp, (-4).dp), small = true) }
        }
        Row(Modifier.padding(top = 3.dp).clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.45f)).padding(horizontal = 5.dp, vertical = 1.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            if (p.captain) Box(Modifier.size(12.dp).clip(CircleShape).background(C.gold), contentAlignment = Alignment.Center) {
                Text(L("ک"), fontSize = 8.sp, fontWeight = FontWeight.Black, color = Color(0xFF2A1A00))
            }
            Ltr(p.short, Modifier.weight(1f, false), style = vz(11.sp, FontWeight.Bold), color = Color.White, align = TextAlign.Center)
        }
        PitchBadges(p)
    }
}

/** What a player did, in one small row under the name: goals, assists, cards and when he went off. */
@Composable
private fun PitchBadges(p: LP) {
    if (p.goals == 0 && p.own == 0 && !p.red && !p.yellow && p.assists == 0 && p.outAt == null) return
    Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        val dot = Modifier.size(15.dp).clip(CircleShape).background(Color.White)
        repeat(p.goals.coerceAtMost(3)) { Box(dot, contentAlignment = Alignment.Center) { Icon(Ic.ball, null, Modifier.size(11.dp), tint = Color(0xFF111111)) } }
        if (p.own > 0) Box(dot, contentAlignment = Alignment.Center) { Icon(Ic.ball, null, Modifier.size(11.dp), tint = C.live) }
        if (p.assists > 0) Box(dot, contentAlignment = Alignment.Center) { Text(if (app.goallive.live.Lang.en) "A" else "پ", fontSize = 8.sp, color = Color(0xFF111111), fontWeight = FontWeight.Black) }
        if (p.red) Box(Modifier.size(9.dp, 12.dp).clip(RoundedCornerShape(2.dp)).background(C.live))
        else if (p.yellow) Box(Modifier.size(9.dp, 12.dp).clip(RoundedCornerShape(2.dp)).background(C.gold))
        p.outAt?.let { t ->
            Row(Modifier.clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("⇩", fontSize = 9.sp, color = C.live, fontWeight = FontWeight.Black)
                Text(fa(t) + "'", fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * Tapping a player: his match in one sheet. A header in the team's colours (photo, name, team, position, rating), what he did
 * as chips, four key numbers, the two buttons, his heatmap, then every stat group as its own card.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun PlayerSheet(m: MatchData, p: LP, onClose: () -> Unit) {
    val nav = LocalNav.current
    val accent = LocalAccent.current
    val ps = m.content.o("playerStats")?.optJSONObject(p.id.toString())
    val color = if (p.home) m.homeColor else m.awayColor
    val teamId = if (p.home) m.homeId else m.awayId
    val all = LocalRatings.current
    val mine = all[p.id]
    val rating = mine?.value
    var why by rememberSaveable(p.id) { mutableStateOf(false) }
    // opens all the way up: there is a lot to read
    val state = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onClose, sheetState = state, containerColor = C.bg,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), dragHandle = {
            Box(Modifier.padding(top = 10.dp, bottom = 4.dp).width(44.dp).height(5.dp).clip(RoundedCornerShape(5.dp)).background(C.line2))
        }) {
        Column(Modifier.fillMaxWidth().testTag("player-sheet").verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 20.dp)) {
            // header in the team's colours
            Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp))
                .background(Brush.linearGradient(listOf(color.copy(alpha = 0.55f), color.copy(alpha = 0.12f), C.card)))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(22.dp)).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        Box(Modifier.size(84.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)).padding(4.dp)) { Kit(p, color, 76.dp) }
                        if (p.number.isNotEmpty()) Box(Modifier.align(Alignment.BottomEnd).size(28.dp).clip(CircleShape).background(color)
                            .border(2.dp, C.bg, CircleShape), contentAlignment = Alignment.Center) {
                            Text(fa(p.number), color = inkOn(color), style = vz(12.sp, FontWeight.Black))
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        T(personName(p.name), 20, FontWeight.Black, maxLines = 2)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Crest(teamId, 18.dp)
                            T(teamName(teamId, ps?.s("teamName") ?: if (p.home) m.homeName else m.awayName), 13, FontWeight.Bold, C.ink.copy(alpha = 0.85f), maxLines = 1)
                        }
                        positionName(ps?.i("usualPosition"))?.let { T(it, 12, color = C.mute) }
                    }
                    if (rating != null && rating > 0) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.clip(RoundedCornerShape(18.dp)).clickable(enabled = mine != null) { why = !why }) { BigRating(rating, L("نمره گل‌لایو")) }
                        mine?.let { r ->
                            if (r.minutes < 60) T("± " + fa(dec(r.margin, 1)), 11, FontWeight.Bold, C.mute)
                        }
                    }
                }
                // what he did, as chips
                val chips = buildList {
                    if (mine != null && all.values.filter { it.minutes >= 20 }.maxByOrNull { it.value }?.id == p.id) add("⭐ " + L("بهترین بازیکن زمین") to C.gold)
                    if (p.goals > 0) add("⚽ " + L("{0} گل", fa(p.goals)) to C.win)
                    if (p.assists > 0) add("🅰 " + L("{0} پاس گل", fa(p.assists)) to C.win)
                    if (p.red) add("🟥 " + L("اخراج") to C.live) else if (p.yellow) add("🟨 " + L("کارت زرد") to C.gold)
                    p.inAt?.let { add("⇧ " + L("ورود دقیقه {0}", fa(it)) to C.win) }
                    p.outAt?.let { add("⇩ " + L("خروج دقیقه {0}", fa(it)) to C.live) }
                }
                if (chips.isNotEmpty()) FlowRow(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for ((t, c) in chips) Box(Modifier.clip(RoundedCornerShape(50)).background(c.copy(alpha = 0.16f)).border(1.dp, c.copy(alpha = 0.4f), RoundedCornerShape(50))
                        .padding(horizontal = 10.dp, vertical = 4.dp)) { T(t, 12, FontWeight.Bold, C.ink) }
                }
            }
            // four key numbers
            val tiles = listOfNotNull(
                ps?.let { statOf(it, "Minutes played") }?.let { L("دقیقه") to fa(it.toInt()) },
                ps?.let { statOf(it, "Goals") }?.let { L("گل") to fa(it.toInt()) },
                ps?.let { statOf(it, "Assists") }?.let { L("پاس گل") to fa(it.toInt()) },
                (ps?.let { statOf(it, "Distance covered") }?.let { L("مسافت (کیلومتر)") to fa(dec(it / 1000.0, 1)) }
                    ?: ps?.let { statOf(it, "Touches") }?.let { L("لمس توپ") to fa(it.toInt()) }),
            )
            if (mine != null) RatingWhy(mine, all.values.toList(), why) { why = !why }
            if (tiles.isNotEmpty()) Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((label, v) in tiles) Column(Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(C.card).border(1.dp, C.line, RoundedCornerShape(18.dp))
                    .padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    T(v, 20, FontWeight.Black); T(label, 11, color = C.mute, maxLines = 1)
                }
            }
            Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(accent).clickable { onClose(); nav.player(p.id) }.padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center) { T(L("پروفایل کامل"), 14, FontWeight.ExtraBold, LocalAccentInk.current) }
                Row(Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).border(1.dp, C.line2, RoundedCornerShape(18.dp)).clickable { onClose(); nav.compare(p.id) }
                    .padding(vertical = 12.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Ic.swap, null, Modifier.size(16.dp), tint = C.ink); Spacer(Modifier.width(6.dp)); T(L("مقایسه"), 14, FontWeight.ExtraBold)
                }
            }
            if (m.started) {
                // everyone else in this match, own team first, with the minutes each was on (for the heatmap's halves)
                // heatmaps are keyed by Opta's player id, which the match's player stats carry
                val stats = m.content.o("playerStats")
                fun opta(id: Int) = stats?.optJSONObject(id.toString())?.s("optaId")?.toIntOrNull() ?: 0
                val lu = m.content.o("lineup")
                val end = matchEnd(m)
                val people = listOf(true, false).sortedByDescending { it == p.home }.flatMap { home ->
                    val t = lu?.o(if (home) "homeTeam" else "awayTeam")
                    t.a("starters").objs().map { it to home to true } + t.a("subs").objs().map { it to home to false }
                }.map { (ph, starter) ->
                    val (j, home) = ph
                    val id = j.i("id") ?: 0
                    val ev = j.o("performance").a("substitutionEvents").objs()
                    val from = ev.firstOrNull { it.s("type") == "subIn" }?.i("time") ?: if (starter) 0 else end
                    val to = ev.firstOrNull { it.s("type") == "subOut" }?.i("time") ?: end
                    HeatPlayer(id, opta(id), j.s("name") ?: "", home, from, to)
                }
                val self = people.firstOrNull { it.id == p.id }
                var vs by rememberSaveable(p.id) { mutableStateOf<Int?>(null) }
                MatchCompare(m, p.id, p.name, people.filter { it.id != p.id && it.from < it.to }, vs) { vs = it }
                PlayerHeatmap(m.matchId, p.id, opta(p.id), p.name, people, color, from = self?.from ?: 0, to = self?.to ?: end, end = end,
                    vsId = vs, picker = false, rating = rating, team = teamName(teamId, if (p.home) m.homeName else m.awayName))
            }
            val groups = ps?.a("stats").objs()
            if (groups.isNullOrEmpty()) T(L("آمار این بازیکن در این بازی ثبت نشده"), 13, color = C.mute, modifier = Modifier.fillMaxWidth().padding(24.dp), align = TextAlign.Center)
            groups?.forEach { g ->
                val st = g.o("stats") ?: return@forEach
                val rows = st.keys().asSequence().toList().filter { it !in setOf("Shotmap", "FotMob rating", "Fantasy points") && st.optJSONObject(it)?.s("key") != null }
                if (rows.isEmpty()) return@forEach
                SheetCard {
                    T(tr(g.s("title")), 14, FontWeight.ExtraBold, accent, Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp))
                    rows.forEachIndexed { k, t ->
                        val stat = st.optJSONObject(t)?.o("stat") ?: return@forEachIndexed
                        if (k > 0) Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(1.dp).background(C.line))
                        SheetStat(tr(t), stat, accent)
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

/** One stat line: the value on the end, and a thin bar for success rates. */
@Composable
private fun SheetStat(label: String, s: JSONObject, accent: Color) {
    val v = s.d("value")
    val total = s.d("total")
    val pct = if (v != null && total != null && total > 0 && s.s("type") in setOf("fractionWithPercentage", "distanceWithPercentage")) (v / total).toFloat() else null
    val shown = when {
        v == null -> "–"
        s.s("type") == "fractionWithPercentage" -> L("{0}/{1} ({2}٪)", fa(v.toInt()), fa(total?.toInt() ?: 0), fa(((pct ?: 0f) * 100).roundToInt()))
        s.s("type") == "distanceWithPercentage" -> L("{0} متر ({1}٪)", fa(v.toInt()), fa(((pct ?: 0f) * 100).roundToInt()))
        s.s("type") == "distance" -> if (v >= 1000) L("{0} کیلومتر", fa(dec(v / 1000.0, 1))) else L("{0} متر", fa(v.toInt()))
        s.s("type") == "speed" -> L("{0} کیلومتر بر ساعت", fa(dec(v, 1)))
        s.s("type") == "double" -> fa(dec(v))
        s.s("type") == "boolean" -> if (v != 0.0) L("بله") else L("خیر")
        else -> fa(v.toInt())
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            T(label, 13, color = C.ink.copy(alpha = 0.85f), modifier = Modifier.weight(1f))
            T(shown, 13, FontWeight.ExtraBold)
        }
        if (pct != null) Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(4.dp).clip(RoundedCornerShape(4.dp)).background(C.line)) {
            Box(Modifier.fillMaxWidth(pct.coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(accent))
        }
    }
}

/** The match rating as a big coloured badge. */
@Composable
private fun BigRating(r: Double, label: String) {
    val c = when { r >= 9 -> Color(0xFF1D9BF0); r >= 7.5 -> Color(0xFF14B866); r >= 6.5 -> Color(0xFF9FB21B); r >= 5.5 -> Color(0xFFF08A24); else -> Color(0xFFE5434B) }
    Column(Modifier.clip(RoundedCornerShape(18.dp)).background(c).padding(horizontal = 12.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(fa(dec(r, 1)), color = Color.White, style = vz(22.sp, FontWeight.Black))
        Text(label, color = Color.White.copy(alpha = 0.85f), style = vz(9.sp, FontWeight.Bold))
    }
}

/** FotMob's usual position (0 keeper … 3 forward) in words. */
private fun positionName(p: Int?) = when (p) { 0 -> L("دروازه‌بان"); 1 -> L("مدافع"); 2 -> L("هافبک"); 3 -> L("مهاجم"); else -> null }

private fun statOf(p: JSONObject, title: String): Double? {
    for (g in p.a("stats").objs()) g.o("stats")?.o(title)?.o("stat")?.d("value")?.let { return it }
    return null
}

/** The match's last minute so far: the clock while it is live, 90 once it is over. */
private fun matchEnd(m: MatchData): Int =
    if (m.live) (cleanMinute(m.minute).substringBefore('+').filter(Char::isDigit).toIntOrNull()?.coerceAtLeast(1) ?: 90) else 90

private fun statTotal(p: JSONObject, title: String): Double? {
    for (g in p.a("stats").objs()) g.o("stats")?.o(title)?.o("stat")?.d("total")?.let { return it }
    return null
}

/**
 * "مقایسه در همین بازی": the player next to anyone else from this match, line by line (rating, minutes, goals, chances,
 * passing, touches, duels, defending, running), mirrored bars meeting in the middle, the better side lit. The same choice
 * puts the other player's heatmap under his.
 */
@Composable
private fun MatchCompare(m: MatchData, id: Int, name: String, others: List<HeatPlayer>, vs: Int?, onPick: (Int?) -> Unit) {
    val stats = m.content.o("playerStats") ?: return
    val mine = stats.optJSONObject(id.toString()) ?: return
    val ratings = LocalRatings.current
    val rivals = others.filter { stats.optJSONObject(it.id.toString())?.a("stats")?.length()?.let { n -> n > 0 } == true }
    if (rivals.isEmpty()) return
    val accent = LocalAccent.current
    val other = rivals.firstOrNull { it.id == vs }
    val theirs = other?.let { stats.optJSONObject(it.id.toString()) }
    SheetCard {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Ic.swap, null, Modifier.size(16.dp), tint = accent); Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f)) {
                T(L("مقایسه در همین بازی"), 15, FontWeight.ExtraBold)
                T(L("یک بازیکن دیگر این بازی را انتخاب کن"), 11, color = C.mute, maxLines = 1)
            }
            if (other != null) T(L("بستن"), 12, FontWeight.Bold, C.mute, Modifier.clip(RoundedCornerShape(10.dp)).clickable { onPick(null) }.padding(6.dp))
        }
        androidx.compose.foundation.lazy.LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(rivals.size) { k ->
                val r = rivals[k]
                val on = r.id == vs
                Row(Modifier.clip(RoundedCornerShape(50)).background(if (on) C.gold.copy(alpha = 0.22f) else C.card2)
                    .border(1.dp, if (on) C.gold else Color.Transparent, RoundedCornerShape(50)).clickable { onPick(if (on) null else r.id) }
                    .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Face(r.id, 26.dp); Spacer(Modifier.width(6.dp))
                    T(personName(r.name.substringAfterLast(' ')), 12, FontWeight.Bold, if (on) C.gold else C.ink, maxLines = 1)
                    Spacer(Modifier.width(6.dp))
                    Box(Modifier.size(8.dp).clip(CircleShape).background(if (r.home) m.homeColor else m.awayColor))
                }
            }
        }
        if (other != null && theirs != null) {
            class Line(val label: String, val a: Double?, val b: Double?, val show: (Double) -> String, val lowBetter: Boolean = false)
            fun st(o: JSONObject, t: String) = statOf(o, t)
            val pct = { o: JSONObject -> st(o, "Accurate passes")?.let { v -> statTotal(o, "Accurate passes")?.takeIf { it > 0 }?.let { v / it * 100 } } }
            val lines = listOf(
                Line(L("نمره گل‌لایو"), ratings[id]?.value, ratings[other.id]?.value, { fa(dec(it, 1)) }),
                Line(L("دقیقه"), st(mine, "Minutes played"), st(theirs, "Minutes played"), { fa(it.toInt()) }),
                Line(L("گل"), st(mine, "Goals"), st(theirs, "Goals"), { fa(it.toInt()) }),
                Line(L("پاس گل"), st(mine, "Assists"), st(theirs, "Assists"), { fa(it.toInt()) }),
                Line(L("کیفیت موقعیت‌ها (xG)"), st(mine, "Expected goals (xG)"), st(theirs, "Expected goals (xG)"), { fa(dec(it, 2)) }),
                Line(L("شوت"), st(mine, "Total shots"), st(theirs, "Total shots"), { fa(it.toInt()) }),
                Line(L("موقعیت ساخته‌شده"), st(mine, "Chances created"), st(theirs, "Chances created"), { fa(it.toInt()) }),
                Line(L("دقت پاس"), pct(mine), pct(theirs), { fa(it.roundToInt()) + "٪" }),
                Line(L("لمس توپ"), st(mine, "Touches"), st(theirs, "Touches"), { fa(it.toInt()) }),
                Line(L("دوئل برده"), st(mine, "Duels won"), st(theirs, "Duels won"), { fa(it.toInt()) }),
                Line(L("تکل"), st(mine, "Tackles"), st(theirs, "Tackles"), { fa(it.toInt()) }),
                Line(L("توپ‌گیری"), st(mine, "Recoveries"), st(theirs, "Recoveries"), { fa(it.toInt()) }),
                Line(L("مسافت (کیلومتر)"), st(mine, "Distance covered")?.div(1000), st(theirs, "Distance covered")?.div(1000), { fa(dec(it, 1)) }),
                Line(L("توپ از دست داده"), st(mine, "Dispossessed"), st(theirs, "Dispossessed"), { fa(it.toInt()) }, lowBetter = true),
            ).filter { it.a != null || it.b != null }
            Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.card2).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Face(id, 30.dp); Spacer(Modifier.width(6.dp))
                    T(personName(name.substringAfterLast(' ')), 13, FontWeight.ExtraBold, accent, Modifier.weight(1f), maxLines = 1)
                    T(personName(other.name.substringAfterLast(' ')), 13, FontWeight.ExtraBold, C.gold, Modifier.weight(1f), maxLines = 1, align = TextAlign.End)
                    Spacer(Modifier.width(6.dp)); Face(other.id, 30.dp)
                }
                var wins = 0 to 0
                for (l in lines) {
                    val a = l.a ?: 0.0; val b = l.b ?: 0.0
                    val aw = if (l.lowBetter) a < b else a > b; val bw = if (l.lowBetter) b < a else b > a
                    if (aw) wins = wins.first + 1 to wins.second; if (bw) wins = wins.first to wins.second + 1
                    val top = max(max(a, b), 0.0001)
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            T(l.a?.let(l.show) ?: "–", 13, FontWeight.Black, if (aw) accent else C.mute, Modifier.width(52.dp))
                            T(l.label, 12, modifier = Modifier.weight(1f), align = TextAlign.Center, maxLines = 1)
                            T(l.b?.let(l.show) ?: "–", 13, FontWeight.Black, if (bw) C.gold else C.mute, Modifier.width(52.dp), align = TextAlign.End)
                        }
                        Row(Modifier.fillMaxWidth().padding(top = 3.dp).height(5.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.CenterEnd) {
                                Box(Modifier.fillMaxWidth((a / top).toFloat().coerceIn(0.02f, 1f)).fillMaxHeight().clip(RoundedCornerShape(5.dp)).background(if (aw) accent else accent.copy(alpha = 0.35f)))
                            }
                            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
                                Box(Modifier.fillMaxWidth((b / top).toFloat().coerceIn(0.02f, 1f)).fillMaxHeight().clip(RoundedCornerShape(5.dp)).background(if (bw) C.gold else C.gold.copy(alpha = 0.35f)))
                            }
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(C.line))
                T(when {
                    wins.first > wins.second -> L("{0} در {1} مورد از {2} بهتر بود", personName(name.substringAfterLast(' ')), fa(wins.first), fa(lines.size))
                    wins.second > wins.first -> L("{0} در {1} مورد از {2} بهتر بود", personName(other.name.substringAfterLast(' ')), fa(wins.second), fa(lines.size))
                    else -> L("دو بازیکن تقریباً هم‌سطح بودند")
                }, 13, FontWeight.Bold, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
        Spacer(Modifier.height(14.dp))
    }
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
    val ratings = LocalRatings.current
    val ps = m.content.o("playerStats")!!
    val all = ps.keys().asSequence().mapNotNull { ps.optJSONObject(it) }.filter { (it.a("stats")?.length() ?: 0) > 0 }.toList()
    val key: (JSONObject) -> Double = when (sort) {
        "ga" -> { p -> (statVal(p, "Goals") ?: 0.0) * 10 + (statVal(p, "Assists") ?: 0.0) }
        "xg" -> { p -> statVal(p, "Expected goals (xG)") ?: 0.0 }
        "min" -> { p -> statVal(p, "Minutes played") ?: 0.0 }
        else -> { p -> ratings[p.i("id") ?: 0]?.value ?: 0.0 }
    }
    val best = ratings.values.filter { it.minutes >= 20 }.maxByOrNull { it.value }?.id
    val list = all.filter { team == "all" || (team == "h") == (it.i("teamId") == m.homeId) }.sortedByDescending(key)
    Seg(listOf("all" to L("همه"), "h" to teamName(m.homeId, m.homeName), "a" to teamName(m.awayId, m.awayName)), team) { team = it }
    // FotMob's own groups (attack, defence, duels…), each shown as a table like SofaScore's player statistics
    val groups = remember(m.d) { all.flatMap { p -> p.a("stats").objs().mapNotNull { it.s("title") } }.distinct().filter { statColumns(all, it).isNotEmpty() } }
    var view by rememberSaveable { mutableStateOf("summary") }
    if (view != "summary" && view !in groups) view = "summary"
    Chips(listOf("summary" to L("خلاصه")) + groups.map { it to tr(it) }, view) { view = it }
    if (view != "summary") { StatTable(list, view, statColumns(all, view), ratings); return }
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
                if (p.i("id") == best) Icon(Ic.starOn, null, Modifier.size(14.dp), tint = C.gold)
                Rating(ratings[p.i("id") ?: 0]?.value)
            }
        }
    }
}

private val NOT_COLUMNS = setOf("Shotmap", "FotMob rating", "Fantasy points")

/** A player's stat in one of FotMob's groups, by its title. */
private fun statIn(p: JSONObject, group: String, title: String): JSONObject? =
    p.a("stats").objs().firstOrNull { it.s("title") == group }?.o("stats")?.optJSONObject(title)?.o("stat")

/** The stat titles of a group, in FotMob's order, across every player who has them. */
private fun statColumns(players: List<JSONObject>, group: String): List<String> =
    players.flatMap { p -> p.a("stats").objs().firstOrNull { it.s("title") == group }?.o("stats")?.keys()?.asSequence()?.toList().orEmpty() }
        .distinct().filter { it !in NOT_COLUMNS }

/** A table cell: short enough for a column (12/15 rather than 12/15 (80%)). */
private fun cellText(s: JSONObject?): String {
    val v = s.d("value") ?: return "–"
    val total = s.d("total")
    return when (s.s("type")) {
        "fractionWithPercentage" -> fa("${v.toInt()}/${total?.toInt() ?: 0}")
        // kilometres from a kilometre up, metres below
        "distance", "distanceWithPercentage" -> if (v >= 1000) dec(v / 1000.0, 1) else fa(v.toInt())
        "double", "speed" -> fa(dec(v, if (v >= 10) 1 else 2))
        "boolean" -> if (v != 0.0) "✓" else "–"
        else -> fa(v.toInt())
    }
}

/** A row of choices that scrolls sideways when they don't fit (FotMob has up to seven groups). */
@Composable
private fun Chips(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    val accent = LocalAccent.current
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (k, label) ->
            val on = k == selected
            Box(Modifier.height(36.dp).clip(RoundedCornerShape(50)).background(if (on) accent else C.card).border(1.dp, if (on) Color.Transparent else C.line, RoundedCornerShape(50))
                .clickable { onSelect(k) }.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                T(label, 13, FontWeight.ExtraBold, if (on) LocalAccentInk.current else C.ink, maxLines = 1)
            }
        }
    }
}

/**
 * One stat group as a table: the players down the side (they stay put), a column per stat that scrolls sideways,
 * the GoalLive rating first. A tap on a heading sorts by it; a tap on a player opens his page.
 */
@Composable
private fun StatTable(list: List<JSONObject>, group: String, cols: List<String>, ratings: Map<Int, PlayerRating>) {
    val nav = LocalNav.current
    val accent = LocalAccent.current
    var sortCol by rememberSaveable(group) { mutableStateOf(cols.firstOrNull() ?: "") }
    if (sortCol != "★" && sortCol !in cols) sortCol = cols.firstOrNull() ?: "★"
    val sortKey: (JSONObject) -> Double = if (sortCol == "★") { p -> ratings[p.i("id") ?: 0]?.value ?: -1.0 } else { p -> statIn(p, group, sortCol).d("value") ?: -1.0 }
    val rows = list.sortedByDescending(sortKey)
    val head = 46.dp; val rowH = 46.dp; val colW = 78.dp
    val stripe = { i: Int -> if (i % 2 == 1) C.ink.copy(alpha = 0.045f) else Color.Transparent }
    Card {
        Row {
            Column(Modifier.width(140.dp)) {
                Box(Modifier.height(head).fillMaxWidth().padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) { T(L("بازیکن"), 11, FontWeight.Bold, C.mute) }
                rows.forEachIndexed { i, p ->
                    Row(Modifier.height(rowH).fillMaxWidth().background(stripe(i)).clickable { nav.player(p.i("id") ?: 0) }.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Face(p.i("id") ?: 0, 28.dp)
                        Ltr(personName((p.s("name") ?: "").substringAfterLast(' ')), Modifier.weight(1f), style = vz(12.sp, FontWeight.Bold))
                    }
                }
            }
            Box(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
                Column {
                    Row(Modifier.height(head)) {
                        (listOf("★") + cols).forEach { c ->
                            val on = c == sortCol
                            Box(Modifier.width(colW).fillMaxHeight().clickable { sortCol = c }.padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
                                T(if (c == "★") L("نمره") else tr(c), 11, FontWeight.Bold, if (on) accent else C.mute, maxLines = 2, align = TextAlign.Center)
                            }
                        }
                    }
                    rows.forEachIndexed { i, p ->
                        Row(Modifier.height(rowH).background(stripe(i)), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.width(colW), contentAlignment = Alignment.Center) { Rating(ratings[p.i("id") ?: 0]?.value, small = true) }
                            cols.forEach { c ->
                                Box(Modifier.width(colW), contentAlignment = Alignment.Center) {
                                    T(cellText(statIn(p, group, c)), 13, if (c == sortCol) FontWeight.Black else FontWeight.Medium, if (c == sortCol) C.ink else C.ink.copy(alpha = 0.85f))
                                }
                            }
                        }
                    }
                }
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
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 14.dp).height(8.dp).clip(RoundedCornerShape(10.dp)), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
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
