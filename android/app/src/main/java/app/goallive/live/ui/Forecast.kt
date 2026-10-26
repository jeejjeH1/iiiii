package app.goallive.live.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.goallive.live.L
import app.goallive.live.data.Api
import app.goallive.live.data.a
import app.goallive.live.data.d
import app.goallive.live.data.i
import app.goallive.live.data.o
import app.goallive.live.data.objs
import app.goallive.live.data.s
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * Two things built on FotMob's expected-goals table of a league:
 *  - "جدول خوش‌شانسی": each team's real points against the points its chances were worth (xPoints), so the luckiest and the
 *    unluckiest sides stand out;
 *  - "پیش‌بینی گل‌لایو": a match forecast from both teams' season chances made and allowed per game, a little home advantage,
 *    and the same exact score arithmetic the analyst uses.
 */

/** One team's line in the expected-goals table. */
class XgRow(val id: Int, val name: String, val played: Int, val pts: Int, val xPts: Double, val xg: Double, val xga: Double) {
    val luck get() = pts - xPts
}

/** The expected-goals rows of whichever table (of a league with groups) holds [teams], with games played taken from the main table. */
internal fun xgRows(data: Any?, vararg teams: Int): List<XgRow> {
    val root: JSONObject? = when (data) {
        is JSONArray -> data.optJSONObject(0)?.let { it.o("data") ?: it }
        is JSONObject -> data.o("data") ?: data
        else -> null
    }
    val tables = root?.a("tables").objs().ifEmpty { listOfNotNull(root) }.filter { it.o("table") != null }
    val t = tables.firstOrNull { tb -> teams.all { id -> tb.o("table").a("all").objs().any { it.i("id") == id } } } ?: tables.firstOrNull() ?: return emptyList()
    val played = t.o("table").a("all").objs().associate { (it.i("id") ?: 0) to (it.i("played") ?: 0) }
    return t.o("table").a("xg").objs().mapNotNull { r ->
        val id = r.i("id") ?: return@mapNotNull null
        val xp = r.d("xPoints") ?: return@mapNotNull null
        XgRow(id, r.s("name") ?: "", played[id] ?: r.i("played") ?: 0, r.i("pts") ?: 0, xp, r.d("xg") ?: 0.0, r.d("xgConceded") ?: 0.0)
    }
}

/**
 * "جدول خوش‌شانسی": real points minus the points the chances were worth, luckiest first, as bars either side of zero, with a
 * line naming the luckiest and the unluckiest side.
 */
@Composable
fun LuckTable(data: Any?) {
    val nav = LocalNav.current
    val rows = remember(data) { xgRows(data).filter { it.played > 0 }.sortedByDescending { it.luck } }
    if (rows.size < 4) { EmptyView(L("برای این لیگ آمار موقعیت‌ها (xG) منتشر نشده"), Ic.list); return }
    val top = rows.maxOf { abs(it.luck) }.coerceAtLeast(1.0)
    val lucky = rows.first(); val unlucky = rows.last()
    Card(pad = PaddingValues(16.dp)) {
        T(L("🍀 جدول خوش‌شانسی"), 16, FontWeight.Black)
        T(L("امتیاز واقعی هر تیم منهای امتیازی که موقعیت‌هایش (xG) ارزش داشت. مثبت یعنی بیشتر از حقش گرفته."), 12, color = C.mute, modifier = Modifier.padding(top = 4.dp))
        T(L("خوش‌شانس‌ترین: {0} ({1} امتیاز بیشتر) · بدشانس‌ترین: {2} ({3} امتیاز کمتر)", teamName(lucky.id, lucky.name), fa(dec(lucky.luck, 1)),
            teamName(unlucky.id, unlucky.name), fa(dec(-unlucky.luck, 1))), 13, FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
    }
    Card {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            T(L("تیم"), 11, FontWeight.Bold, C.mute, Modifier.weight(1f))
            T(L("امتیاز"), 11, FontWeight.Bold, C.mute, Modifier.width(44.dp), align = TextAlign.Center)
            T(L("حق"), 11, FontWeight.Bold, C.mute, Modifier.width(44.dp), align = TextAlign.Center)
            T(L("فرق"), 11, FontWeight.Bold, C.mute, Modifier.width(120.dp), align = TextAlign.Center)
        }
        rows.forEachIndexed { k, r ->
            Divider()
            Row(Modifier.fillMaxWidth().clickable { nav.team(r.id) }.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    T(fa(k + 1), 12, FontWeight.Bold, C.mute, Modifier.width(20.dp), align = TextAlign.Center)
                    Crest(r.id, 22.dp); TeamText(r.id, r.name, Modifier.weight(1f, false), 14, maxLines = 1)
                }
                T(fa(r.pts), 13, FontWeight.Bold, modifier = Modifier.width(44.dp), align = TextAlign.Center)
                T(fa(dec(r.xPts, 1)), 13, color = C.mute, modifier = Modifier.width(44.dp), align = TextAlign.Center)
                // a bar out from the middle: lucky towards the reading start, unlucky the other way
                Row(Modifier.width(120.dp).height(22.dp), verticalAlignment = Alignment.CenterVertically) {
                    val w = (r.luck / top).toFloat().coerceIn(-1f, 1f)
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.CenterEnd) {
                        if (w > 0) Box(Modifier.fillMaxWidth(w).height(10.dp).clip(RoundedCornerShape(5.dp)).background(C.win))
                    }
                    Box(Modifier.width(2.dp).fillMaxHeight().background(C.line2))
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
                        if (w < 0) Box(Modifier.fillMaxWidth(-w).height(10.dp).clip(RoundedCornerShape(5.dp)).background(C.loss))
                    }
                }
            }
        }
    }
}

/* ---------- the GoalLive forecast ---------- */

/** A forecast: chances of a home win, a draw and an away win, the likeliest score, the goals expected for each side. */
class Forecast(val h: Double, val d: Double, val a: Double, val score: Pair<Int, Int>, val lh: Double, val la: Double)

/** Home advantage on expected goals (about what the big leagues show over many seasons). */
private const val HOME_EDGE = 1.12

/**
 * The forecast for [homeId] against [awayId] from the league's expected-goals table: each side's chances made per game against
 * the other's chances allowed per game, relative to the league's average, with a small home advantage; then the exact chances
 * of every score. Null when the table has no expected goals or a side has played fewer than three games.
 */
internal fun forecast(rows: List<XgRow>, homeId: Int, awayId: Int): Forecast? {
    val hr = rows.firstOrNull { it.id == homeId } ?: return null
    val ar = rows.firstOrNull { it.id == awayId } ?: return null
    if (hr.played < 3 || ar.played < 3) return null
    val avg = rows.filter { it.played > 0 }.map { it.xg / it.played }.average().takeIf { it > 0.2 } ?: return null
    val lh = (hr.xg / hr.played) * (ar.xga / ar.played) / avg * HOME_EDGE
    val la = (ar.xg / ar.played) * (hr.xga / hr.played) / avg / HOME_EDGE
    val (h, d, a) = outcome(0, 0, lh, la)
    // the likeliest exact score
    var best = 0 to 0; var bp = -1.0
    for (i in 0..6) for (j in 0..6) {
        val p = pois(i, lh) * pois(j, la)
        if (p > bp) { bp = p; best = i to j }
    }
    return Forecast(h, d, a, best, lh, la)
}

private fun pois(k: Int, l: Double): Double { var p = kotlin.math.exp(-l); for (n in 1..k) p *= l / n; return p }

/** The forecast for a match, from its league's table (loaded once and kept). */
@Composable
fun rememberForecast(m: MatchData): Forecast? {
    val leagueId = m.content.o("table").i("leagueId") ?: return null
    val l = rememberLoader(leagueId, "xg") { Api.fmArray("tltable", mapOf("leagueId" to leagueId), 3_600_000) }
    val data = l.value ?: return null
    return remember(data, m.homeId, m.awayId) { runCatching { forecast(xgRows(data, m.homeId, m.awayId), m.homeId, m.awayId) }.getOrNull() }
}

/** The forecast as one compact block: the three chances and the likeliest score. */
@Composable
fun ForecastLine(f: Forecast, m: MatchData) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.card2).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            T("🧠", 16)
            T(L("پیش‌بینی گل‌لایو"), 13, FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            ScoreText(f.score.first, f.score.second, 16)
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp).height(8.dp).clip(RoundedCornerShape(4.dp)), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Box(Modifier.weight(f.h.toFloat().coerceAtLeast(0.01f)).fillMaxHeight().background(m.homeColor))
            Box(Modifier.weight(f.d.toFloat().coerceAtLeast(0.01f)).fillMaxHeight().background(C.draw))
            Box(Modifier.weight(f.a.toFloat().coerceAtLeast(0.01f)).fillMaxHeight().background(m.awayColor))
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            T(fa((f.h * 100).roundToInt()) + "٪", 12, FontWeight.Bold, m.homeColor, Modifier.weight(1f))
            T(fa((f.d * 100).roundToInt()) + "٪", 12, FontWeight.Bold, C.mute, Modifier.weight(1f), align = TextAlign.Center)
            T(fa((f.a * 100).roundToInt()) + "٪", 12, FontWeight.Bold, m.awayColor, Modifier.weight(1f), align = TextAlign.End)
        }
    }
}
