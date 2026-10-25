package app.goallive.live.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.goallive.live.L
import app.goallive.live.data.Api
import app.goallive.live.data.Pick
import app.goallive.live.data.Predictions
import app.goallive.live.data.a
import app.goallive.live.data.b
import app.goallive.live.data.i
import app.goallive.live.data.o
import app.goallive.live.data.objs
import app.goallive.live.data.s
import kotlinx.coroutines.launch

/** The title that goes with a points total, and the total needed for the next one. */
fun predictLevel(points: Int): Pair<String, Int?> = when {
    points >= 150 -> L("پیشگوی افسانه‌ای") to null
    points >= 80 -> L("پیشگو") to 150
    points >= 40 -> L("کارشناس فوتبال") to 80
    points >= 15 -> L("هوادار باهوش") to 40
    else -> L("تازه‌کار") to 15
}

private fun pointsText(p: Int) = when (p) {
    3 -> L("+۳ · نتیجه دقیق! 🎯")
    1 -> L("+۱ · برنده را درست گفتی ✓")
    else -> L("۰ · این بار نشد")
}

/** "پیش‌بینی تو": guess the score before kick-off, see the points after the final whistle. */
@Composable
fun PredictCard(m: MatchData) {
    if (m.cancelled || m.homeId <= 0 || m.awayId <= 0) return
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val pick = Predictions.all[m.matchId]
    val open = !m.started && m.time > System.currentTimeMillis()
    if (!open && pick == null) return
    // the final score settles the pick (and its points) as soon as the match is over
    if (m.finished && pick != null) LaunchedEffect(m.matchId) { Predictions.settle(m.matchId, m.home.i("score") ?: 0, m.away.i("score") ?: 0) }
    val accent = LocalAccent.current
    Card {
        Box(Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(C.gold.copy(alpha = 0.10f), Color.Transparent)))) {
            Column {
                CardTitle(L("🔮 پیش‌بینی تو"), sub = if (open) L("نتیجه را حدس بزن") else null) {
                    T(L("{0} امتیاز", fa(Predictions.total)), 12, FontWeight.ExtraBold, C.gold, Modifier.clickable { nav.predictions() })
                }
                if (open) {
                    val h = pick?.home ?: 0; val a = pick?.away ?: 0
                    fun save(nh: Int, na: Int) = Predictions.put(
                        Pick(m.matchId, nh, na, m.homeId, m.awayId, m.homeName, m.awayName, m.time, m.general.s("leagueName") ?: ""),
                    )
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Stepper(m.homeId, m.homeName, h, Modifier.weight(1f)) { save(it, a) }
                        T("-", 26, FontWeight.Black, C.faint)
                        Stepper(m.awayId, m.awayName, a, Modifier.weight(1f)) { save(h, it) }
                    }
                    // a goalless draw needs no taps on + and −: it can be saved as it stands
                    if (pick == null) Box(Modifier.fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.Center) {
                        Pill(L("ثبت {0}", fa("0-0")), PillKind.Accent, onClick = { save(0, 0) })
                    }
                    T(if (pick == null) L("با زدن + و − پیش‌بینی‌ات ثبت می‌شود. نتیجه دقیق ۳ امتیاز، برنده درست ۱ امتیاز.")
                        else L("✓ ثبت شد؛ تا سوت شروع می‌توانی تغییرش بدهی."), 12, color = if (pick == null) C.mute else C.win,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 14.dp))
                } else if (pick != null) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        T(L("پیش‌بینی تو"), 13, FontWeight.Bold, C.mute)
                        ScoreText(pick.home, pick.away, 20)
                        Spacer(Modifier.weight(1f))
                        val pts = if (m.finished) Predictions.get(m.matchId)?.points else null
                        when {
                            pts != null -> Pill(pointsText(pts), if (pts > 0) PillKind.Accent else PillKind.Plain)
                            m.live -> {
                                val h = m.home.i("score") ?: 0; val a = m.away.i("score") ?: 0
                                val now = Pick(0, pick.home, pick.away, 0, 0, "", "", 0, "", h to a).points ?: 0
                                Pill(if (now == 3) L("فعلاً دقیق!") else if (now == 1) L("فعلاً درست") else L("فعلاً نه"), if (now > 0) PillKind.Accent else PillKind.Plain)
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill(L("همه پیش‌بینی‌هایم"), PillKind.Plain, onClick = { nav.predictions() })
                        if (m.finished) Pill(L("اشتراک کارت"), PillKind.Accent, icon = Ic.share, onClick = { scope.launch { shareMatchCard(ctx, m) } })
                    }
                }
            }
        }
    }
}

@Composable
private fun Stepper(teamId: Int, name: String, value: Int, modifier: Modifier, onChange: (Int) -> Unit) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Crest(teamId, 34.dp)
        TeamText(teamId, name, size = 13, maxLines = 1, align = TextAlign.Center)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StepBtn("−", value > 0) { onChange(value - 1) }
            T(fa(value), 30, FontWeight.Black)
            StepBtn("+", value < 9) { onChange(value + 1) }
        }
    }
}

@Composable
private fun StepBtn(sign: String, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(36.dp).clip(CircleShape).background(if (enabled) C.card2 else Color.Transparent).border(1.dp, C.line, CircleShape)
        .clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        T(sign, 20, FontWeight.Black, if (enabled) C.ink else C.faint)
    }
}

/** "پیش‌بینی‌های من": points, accuracy, title, and every pick with its result. */
@Composable
fun PredictionsScreen() {
    val nav = LocalNav.current
    val all = Predictions.all.values.sortedByDescending { it.time }
    // picks of matches that should be over by now get their final score
    LaunchedEffect(Unit) {
        for (pk in all.filter { it.result == null && it.time < System.currentTimeMillis() - 2 * 3_600_000L }.take(15)) runCatching {
            val d = Api.fm("matchDetails", mapOf("matchId" to pk.matchId), 3_600_000)
            val h = d.o("header"); val teams = h.a("teams").objs()
            if (h.o("status").b("finished") && teams.size == 2) Predictions.settle(pk.matchId, teams[0].i("score") ?: 0, teams[1].i("score") ?: 0)
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = BottomSpace)) {
        item {
            T(L("🔮 پیش‌بینی‌های من"), 18, FontWeight.Black, modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp))
            val total = Predictions.total
            val (title, next) = predictLevel(total)
            Card {
                Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(C.gold.copy(alpha = 0.16f), Color.Transparent))).padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    T(fa(total), 44, FontWeight.Black, C.gold)
                    T(L("امتیاز · {0}", title), 14, FontWeight.ExtraBold)
                    if (next != null) {
                        Box(Modifier.padding(top = 10.dp).fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(C.card2)) {
                            Box(Modifier.fillMaxWidth((total.toFloat() / next).coerceIn(0f, 1f)).fillMaxHeight().background(C.gold))
                        }
                        T(L("{0} امتیاز تا «{1}»", fa(next - total), predictLevel(next).first), 12, color = C.mute, modifier = Modifier.padding(top = 6.dp))
                    }
                }
                Row(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
                    val n = Predictions.settled.size
                    Tile(fa(all.size), L("پیش‌بینی"), Modifier.weight(1f))
                    Tile(fa(Predictions.exact), L("نتیجه دقیق"), Modifier.weight(1f))
                    Tile(if (n == 0) "–" else fa(Predictions.right * 100 / n) + "٪", L("درست"), Modifier.weight(1f))
                    Tile(fa(Predictions.streak), L("پیاپی"), Modifier.weight(1f))
                }
            }
            T(L("نتیجه دقیق ۳ امتیاز و برنده درست (یا مساوی) ۱ امتیاز. پیش‌بینی‌ها روی همین گوشی ذخیره می‌شود."), 12, color = C.mute,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp))
        }
        if (all.isEmpty()) item {
            EmptyView(L("هنوز پیش‌بینی نکرده‌ای"), null, L("یک بازی را باز کن و در «پیش‌بینی تو» نتیجه را حدس بزن."), L("بازی‌های امروز")) { nav.tab("home") }
        }
        val upcoming = all.filter { it.result == null }
        val done = all.filter { it.result != null }
        if (upcoming.isNotEmpty()) item { SectionTitle(L("در انتظار نتیجه")) }
        if (upcoming.isNotEmpty()) item { Card { upcoming.forEachIndexed { i, pk -> PickRow(pk, i == 0) } } }
        if (done.isNotEmpty()) item { SectionTitle(L("تمام‌شده")) }
        if (done.isNotEmpty()) item { Card { done.forEachIndexed { i, pk -> PickRow(pk, i == 0) } } }
    }
}

@Composable
private fun Tile(v: String, label: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        T(v, 20, FontWeight.Black)
        T(label, 12, color = C.mute)
    }
}

@Composable
private fun PickRow(pk: Pick, first: Boolean) {
    val nav = LocalNav.current
    ListRow(first = first, onClick = { nav.match(pk.matchId) }) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Crest(pk.homeId, 18.dp); TeamText(pk.homeId, pk.homeName, Modifier.weight(1f, false), 13)
                T("–", 13, color = C.faint)
                TeamText(pk.awayId, pk.awayName, Modifier.weight(1f, false), 13); Crest(pk.awayId, 18.dp)
            }
            T(persianDate(pk.time, "EEEE d MMMM") + (pk.result?.let { L(" · نتیجه {0}", fa("${it.first}-${it.second}")) } ?: ""), 12, color = C.mute, maxLines = 1)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ScoreText(pk.home, pk.away, 16)
            val p = pk.points
            T(if (p == null) L("منتظر") else "+" + fa(p), 12, FontWeight.ExtraBold,
                when (p) { 3 -> C.gold; 1 -> C.win; null -> C.mute; else -> C.loss })
        }
    }
}
