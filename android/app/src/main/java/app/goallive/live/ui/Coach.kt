package app.goallive.live.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.runtime.*
import androidx.compose.ui.draw.*
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.goallive.live.data.Api
import app.goallive.live.data.Clubs
import app.goallive.live.data.Store
import app.goallive.live.data.a
import app.goallive.live.data.d
import app.goallive.live.data.i
import app.goallive.live.data.o
import app.goallive.live.data.objs
import app.goallive.live.data.path
import app.goallive.live.data.s
import org.json.JSONArray
import org.json.JSONObject

/* "مربی تو هستی": pick your own eleven and subs, then see how they would have rated against the real coach's choices. */

private val FORMATIONS = linkedMapOf(
    "4-3-3" to listOf(4, 3, 3), "4-4-2" to listOf(4, 4, 2), "4-2-3-1" to listOf(4, 2, 3, 1), "3-5-2" to listOf(3, 5, 2),
    "3-4-3" to listOf(3, 4, 3), "4-1-4-1" to listOf(4, 1, 4, 1), "5-3-2" to listOf(5, 3, 2), "4-5-1" to listOf(4, 5, 1),
)
private val LINES = listOf("keepers" to "دروازه‌بان‌ها", "defenders" to "مدافعان", "midfielders" to "هافبک‌ها", "attackers" to "مهاجمان")

private class SquadPlayer(val id: Int, val name: String, val number: String, val line: Int, val seasonRating: Double?)
private class Sub(val out: Int, val inn: Int, val min: Int)

/** The user's plan for one match, saved on the phone. */
private class Plan(var team: Int, var formation: String, val xi: MutableList<Int>, val subs: MutableList<Sub>) {
    fun json() = JSONObject().put("team", team).put("formation", formation).put("xi", JSONArray(xi))
        .put("subs", JSONArray(subs.map { JSONObject().put("out", it.out).put("in", it.inn).put("min", it.min) }))
    companion object {
        fun load(matchId: Int): Plan? = runCatching {
            val o = JSONObject(Store.prefs().getString("coach_$matchId", null)!!)
            Plan(o.getInt("team"), o.getString("formation"), o.getJSONArray("xi").let { a -> MutableList(a.length()) { a.getInt(it) } },
                o.optJSONArray("subs").objs().map { Sub(it.getInt("out"), it.getInt("in"), it.getInt("min")) }.toMutableList())
        }.getOrNull()
    }
}

private fun save(matchId: Int, p: Plan) { Store.prefs().edit().putString("coach_$matchId", p.json().toString()).apply() }

/** Effective team rating: each of the eleven, blended with whoever replaced them for the rest of the 90 minutes. */
private fun teamScore(xi: List<Int>, subs: List<Sub>, rating: (Int) -> Pair<Double, Boolean>): Pair<Double, Boolean> {
    var estimated = false
    val v = xi.map { id ->
        val (r, est) = rating(id); estimated = estimated || est
        val s = subs.firstOrNull { it.out == id } ?: return@map r
        val (ri, esti) = rating(s.inn); estimated = estimated || esti
        val t = s.min.coerceIn(0, 90) / 90.0
        r * t + ri * (1 - t)
    }
    return (if (v.isEmpty()) 0.0 else v.average()) to estimated
}

@Composable
fun CoachTab(m: MatchData) {
    val ctx = LocalContext.current
    val mineIn = Store.myTeam?.id?.takeIf { it == m.homeId || it == m.awayId }
    var plan by remember(m.matchId) { mutableStateOf(Plan.load(m.matchId) ?: Plan(mineIn ?: m.homeId, "4-3-3", mutableListOf(), mutableListOf())) }
    var version by remember { mutableIntStateOf(0) } // bumps after every change so the screen redraws
    fun changed() { save(m.matchId, plan); version++ }
    @Suppress("UNUSED_VARIABLE") val redraw = version // reading it here redraws the tab after each change
    val locked = m.started
    val squadL = rememberLoader(plan.team) {
        Api.fm("teams", mapOf("id" to plan.team, "ccode3" to "IRN"), 3_600_000).o("squad").a("squad").objs().flatMap { g ->
            val line = LINES.indexOfFirst { it.first == g.s("title") }
            if (line < 0) emptyList() else g.a("members").objs().map { SquadPlayer(it.i("id") ?: 0, it.s("name") ?: "", it.s("shirtNumber") ?: "", line, it.d("rating")) }
        }
    }
    val teamName = if (plan.team == m.homeId) m.homeName else m.awayName
    val color = if (plan.team == m.homeId) m.homeColor else m.awayColor
    val accent = LocalAccent.current

    // header
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).drawBehind { cssLinear(140f, 0f to color.copy(alpha = 0.45f), 1f to C.card) }
            .border(1.dp, C.line2, RoundedCornerShape(22.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        var help by rememberTip("coach")
        Row(verticalAlignment = Alignment.CenterVertically) {
            T("🧠 مربی تو هستی", 18, FontWeight.Black, modifier = Modifier.weight(1f))
            HelpButton { help = true }
        }
        if (help) HowToSheet("🧠", "مربی تو هستی چطور کار می‌کند؟", COACH_STEPS, onClose = { Store.markTip("coach"); help = false })
        T(if (!locked) "ترکیب و سیستم $teamName را خودت بچین. بعد از اعلام ترکیب رسمی و در طول بازی، تصمیم‌هایت با سرمربی واقعی مقایسه می‌شود."
            else "بازی شروع شده و ترکیبت قفل شد. حالا می‌توانی تعویض‌هایت را ثبت کنی.", 13, color = C.ink.copy(alpha = 0.85f))
    }
    if (!locked && plan.xi.isEmpty()) Seg(listOf("h" to teamName(m.homeId, m.homeName), "a" to teamName(m.awayId, m.awayName)), if (plan.team == m.homeId) "h" else "a") {
        plan = Plan(if (it == "h") m.homeId else m.awayId, plan.formation, mutableListOf(), mutableListOf()); changed()
    }
    if (!locked) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FORMATIONS.keys.forEach { f ->
            val on = f == plan.formation
            Box(Modifier.clip(RoundedCornerShape(12.dp)).background(if (on) accent else C.card).border(1.dp, if (on) Color.Transparent else C.line, RoundedCornerShape(12.dp))
                .clickable { plan.formation = f; changed() }.padding(horizontal = 14.dp, vertical = 8.dp)) {
                Ltr(f, style = lat(15.sp, FontWeight.ExtraBold), color = if (on) LocalAccentInk.current else C.ink)
            }
        }
    }
    val squad = squadL.value
    if (squad == null) { if (squadL.state is Load.Err) ErrorView({ squadL.reload() }) else Skeleton(3); return }
    val byId = squad.associateBy { it.id }
    MyPitch(plan, byId, color)

    // ratings from this match (live or final), else the player's season rating (an estimate)
    val lu = m.content.o("lineup")
    val side = if (plan.team == m.homeId) lu.o("homeTeam") else lu.o("awayTeam")
    val official = side.a("starters").objs()
    val matchRating = HashMap<Int, Double>().apply {
        (official + side.a("subs").objs()).forEach { p -> p.path("performance").d("rating")?.let { put(p.i("id") ?: 0, it) } }
    }
    val rate = { id: Int -> matchRating[id]?.let { it to false } ?: ((byId[id]?.seasonRating ?: 6.0) to true) }

    if (official.isNotEmpty() && plan.xi.size == 11) {
        val same = plan.xi.count { id -> official.any { it.i("id") == id } }
        val realF = side.s("formation") ?: ""
        Card {
            CardTitle("مقایسه با ترکیب ${if ((lu.s("lineupType") ?: "standard") == "standard") "رسمی" else "احتمالی"}")
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Fact2("$same از ۱۱", "بازیکن مشترک", Modifier.weight(1f), if (same >= 9) C.win else if (same >= 6) C.gold else C.loss)
                Fact2(fa(realF), "سیستم مربی", Modifier.weight(1f), if (realF == plan.formation) C.win else C.ink)
                Fact2(fa(plan.formation), "سیستم تو", Modifier.weight(1f), accent)
            }
            val missing = official.filter { o -> plan.xi.none { it == o.i("id") } }.mapNotNull { it.s("lastName") ?: it.s("name") }
            if (missing.isNotEmpty()) T("مربی این‌ها را بازی داد و تو نه: " + missing.joinToString("، "), 12, color = C.mute, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp))
        }
    }

    // live / final verdict
    if (m.started && plan.xi.size == 11 && official.isNotEmpty()) {
        // the real coach's substitutions, paired by minute
        val outs = official.mapNotNull { p -> p.path("performance").a("substitutionEvents").objs().firstOrNull { it.s("type") == "subOut" }?.let { (p.i("id") ?: 0) to (it.i("time") ?: 0) } }.toMutableList()
        val realSubs = side.a("subs").objs().mapNotNull { p ->
            val t = p.path("performance").a("substitutionEvents").objs().firstOrNull { it.s("type") == "subIn" }?.i("time") ?: return@mapNotNull null
            val o = outs.firstOrNull { it.second == t } ?: outs.firstOrNull() ?: return@mapNotNull null
            outs.remove(o); Sub(o.first, p.i("id") ?: 0, t)
        }
        val (mine, est) = teamScore(plan.xi, plan.subs, rate)
        val (real, _) = teamScore(official.map { it.i("id") ?: 0 }, realSubs, rate)
        val win = mine > real + 0.05
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).drawBehind { cssLinear(160f, 0f to (if (win) accent else C.card2).copy(alpha = 0.35f), 1f to C.card) }
                .border(1.dp, C.line2, RoundedCornerShape(24.dp)).padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            T(if (m.finished) "نمره نهایی مربیگری" else "نمره تا این لحظه", 13, FontWeight.Bold, C.mute)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) { T(dec(mine, 1), 34, FontWeight.Black, accent); T("تو", 12, color = C.mute) }
                T("در برابر", 13, color = C.mute)
                Column(horizontalAlignment = Alignment.CenterHorizontally) { T(dec(real, 1), 34, FontWeight.Black); T("سرمربی واقعی", 12, color = C.mute) }
            }
            T(when { win -> "امشب از سرمربی $teamName بهتر بودی! 🧠"; mine < real - 0.05 -> "این بار سرمربی برنده شد؛ بازی بعد جبران کن"; else -> "تقریباً هم‌نمره با سرمربی!" },
                15, FontWeight.ExtraBold, align = TextAlign.Center)
            if (est) T("برای بازیکنی که در بازی نبود، میانگین نمره فصلش حساب شده (تخمینی).", 11, color = C.faint, align = TextAlign.Center)
            if (m.finished) {
                recordResult(m.matchId, mine, real)
                Pill("اشتراک‌گذاری", PillKind.Accent, icon = Ic.share, onClick = {
                    val t = "در بازی ${m.homeName} و ${m.awayName} مربی شدم: نمره من ${dec(mine, 1)} در برابر سرمربی ${dec(real, 1)} 🧠⚽\nگل‌لایو"
                    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, t), "اشتراک‌گذاری"))
                })
            }
        }
    }

    // substitutions once the match is on
    if (m.started && !m.finished && plan.xi.size == 11) Subs(plan, byId, m) { changed() }
    else if (plan.subs.isNotEmpty()) Card {
        CardTitle("تعویض‌های تو")
        plan.subs.forEachIndexed { i, s -> SubRow(s, byId, i == 0) }
    }

    // picking the eleven before kick-off
    if (!locked) {
        val gk = plan.xi.count { byId[it]?.line == 0 }
        Row(verticalAlignment = Alignment.CenterVertically) {
            T("انتخاب ۱۱ نفر: ${plan.xi.size}/۱۱", 14, FontWeight.Black, if (plan.xi.size == 11) C.win else C.ink, Modifier.weight(1f))
            if (plan.xi.isNotEmpty()) Pill("پاک کردن", onClick = { plan.xi.clear(); changed() })
        }
        if (plan.xi.size == 11 && gk != 1) T(if (gk == 0) "دروازه‌بان انتخاب نکرده‌ای!" else "بیشتر از یک دروازه‌بان در ترکیب است.", 12, FontWeight.Bold, C.loss)
        LINES.forEachIndexed { line, (_, label) ->
            val ps = squad.filter { it.line == line }
            if (ps.isEmpty()) return@forEachIndexed
            Card {
                CardTitle(label, sub = "${ps.count { it.id in plan.xi }} انتخاب")
                ps.forEachIndexed { i, p ->
                    val on = p.id in plan.xi
                    ListRow(first = i == 0, onClick = {
                        if (on) plan.xi.remove(p.id) else if (plan.xi.size < 11) plan.xi.add(p.id)
                        changed()
                    }) {
                        Box(Modifier.size(24.dp).clip(RoundedCornerShape(7.dp)).background(if (on) accent else Color.Transparent).border(2.dp, if (on) accent else C.line2, RoundedCornerShape(7.dp)),
                            contentAlignment = Alignment.Center) { if (on) T("✓", 14, FontWeight.Black, LocalAccentInk.current) }
                        Face(p.id, 34.dp)
                        Column(Modifier.weight(1f)) { Ltr(p.name); T(if (p.number.isNotEmpty()) "#${p.number}" else "", 11, color = C.mute) }
                        Rating(p.seasonRating)
                    }
                }
            }
        }
    }
    History()
}

@Composable
private fun Fact2(big: String, small: String, modifier: Modifier, color: Color) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(C.card2).padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        T(big, 17, FontWeight.Black, color, maxLines = 1); T(small, 11, color = C.mute)
    }
}

/** The chosen eleven on a pitch: goalkeeper at the bottom, then the formation's lines upwards. */
@Composable
private fun MyPitch(plan: Plan, byId: Map<Int, SquadPlayer>, color: Color) {
    val rows = listOf(1) + (FORMATIONS[plan.formation] ?: listOf(4, 3, 3))
    val ordered = plan.xi.mapNotNull { byId[it] }.sortedBy { it.line }
    BoxWithConstraints(
        Modifier.fillMaxWidth().aspectRatio(68f / 80f).clip(RoundedCornerShape(20.dp)).drawBehind {
            val n = 10
            for (i in 0 until n) drawRect(if (i % 2 == 0) C.pitch else C.pitch2, Offset(0f, size.height * i / n), Size(size.width, size.height / n + 1))
            val c = Color.White.copy(alpha = 0.5f); val st = Stroke(1.5f)
            drawRect(c, Offset(8f, 8f), Size(size.width - 16f, size.height - 16f), style = st)
            drawRect(c, Offset(size.width * 0.2f, size.height - 8f - size.height * 0.18f), Size(size.width * 0.6f, size.height * 0.18f), style = st)
            drawCircle(c, size.width * 0.13f, Offset(size.width / 2, 8f), style = st)
        },
    ) {
        val w = maxWidth; val h = maxHeight
        var k = 0
        rows.forEachIndexed { r, count ->
            val y = 0.88f - r * (0.78f / (rows.size - 1).coerceAtLeast(1))
            repeat(count) { j ->
                val x = (j + 1f) / (count + 1f)
                val p = ordered.getOrNull(k++)
                Column(Modifier.align(AbsoluteAlignment.TopLeft).absoluteOffset(w * x - 32.dp, h * y - 22.dp).width(64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (p == null) Box(Modifier.size(38.dp).clip(CircleShape).border(2.dp, Color.White.copy(alpha = 0.6f), CircleShape), contentAlignment = Alignment.Center) {
                        T("+", 18, FontWeight.Black, Color.White.copy(alpha = 0.7f))
                    } else {
                        Box(Modifier.size(38.dp).clip(CircleShape).background(color).border(2.dp, Color.White, CircleShape), contentAlignment = Alignment.Center) {
                            Text(p.number, color = inkOn(color), style = lat(14.sp, FontWeight.ExtraBold))
                            coil.compose.AsyncImage(playerFace(p.id), null, Modifier.fillMaxSize().clip(CircleShape), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                        }
                        Ltr(p.name.substringAfterLast(' '), Modifier.padding(top = 3.dp).clip(RoundedCornerShape(5.dp)).background(Color.Black.copy(alpha = 0.35f)).padding(horizontal = 4.dp),
                            style = lat(11.sp), color = Color.White, align = TextAlign.Center)
                    }
                }
            }
        }
    }
}

@Composable
private fun SubRow(s: Sub, byId: Map<Int, SquadPlayer>, first: Boolean) {
    ListRow(first = first) {
        T("${s.min}'", 12, FontWeight.Black, C.mute, Modifier.width(30.dp), align = TextAlign.Center)
        T("⇧", 14, FontWeight.Black, C.win); Ltr(byId[s.inn]?.name ?: "", Modifier.weight(1f))
        T("⇩", 14, FontWeight.Black, C.loss); Ltr(byId[s.out]?.name ?: "", Modifier.weight(1f), color = C.mute)
    }
}

@Composable
private fun Subs(plan: Plan, byId: Map<Int, SquadPlayer>, m: MatchData, onChange: () -> Unit) {
    var picking by remember { mutableStateOf(false) }
    var out by remember { mutableIntStateOf(0) }
    val minute = m.minute.substringBefore('+').filter(Char::isDigit).toIntOrNull() ?: 45
    Card {
        CardTitle("تعویض‌های تو", sub = "${plan.subs.size}/۵") { if (plan.subs.size < 5) Pill("+ تعویض", PillKind.Accent, onClick = { out = 0; picking = true }) }
        if (plan.subs.isEmpty()) T("مثل مربی واقعی تا ۵ تعویض داری. تعویض در دقیقه فعلی بازی ثبت می‌شود.", 12, color = C.mute, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp))
        plan.subs.forEachIndexed { i, s -> SubRow(s, byId, i == 0) }
    }
    if (picking) {
        val onPitch = plan.xi.map { id -> plan.subs.lastOrNull { it.out == id }?.inn ?: id }.filter { id -> plan.subs.none { it.out == id } }
        val bench = byId.values.filter { it.id !in plan.xi && plan.subs.none { s -> s.inn == it.id } }
        AlertDialog(
            onDismissRequest = { picking = false }, containerColor = C.cardSolid,
            title = { T(if (out == 0) "چه کسی بیرون برود؟" else "چه کسی وارد زمین شود؟", 16, FontWeight.Black) },
            text = {
                Column(Modifier.height(380.dp).verticalScroll(rememberScrollState())) {
                    (if (out == 0) onPitch.mapNotNull { byId[it] } else bench).forEachIndexed { i, p ->
                        ListRow(first = i == 0, onClick = {
                            if (out == 0) out = p.id else { plan.subs.add(Sub(out, p.id, minute)); picking = false; onChange() }
                        }, pad = PaddingValues(vertical = 8.dp)) {
                            Face(p.id, 32.dp); Ltr(p.name, Modifier.weight(1f)); Rating(p.seasonRating)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { picking = false }) { T("انصراف", 14, FontWeight.Bold, C.mute) } },
        )
    }
}

/** Finished matches are added to the user's coaching record once. */
private fun recordResult(matchId: Int, mine: Double, real: Double) {
    val p = Store.prefs()
    val arr = runCatching { JSONArray(p.getString("coachHistory", "[]")) }.getOrDefault(JSONArray())
    if (arr.objs().any { it.optInt("m") == matchId }) return
    arr.put(JSONObject().put("m", matchId).put("mine", mine).put("real", real))
    p.edit().putString("coachHistory", arr.toString()).apply()
}

@Composable
private fun History() {
    val arr = runCatching { JSONArray(Store.prefs().getString("coachHistory", "[]")) }.getOrDefault(JSONArray()).objs()
    if (arr.isEmpty()) return
    val wins = arr.count { it.optDouble("mine") > it.optDouble("real") + 0.05 }
    Card {
        CardTitle("کارنامه مربیگری تو")
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Fact2("${arr.size}", "بازی", Modifier.weight(1f), C.ink)
            Fact2("$wins", "بهتر از مربی", Modifier.weight(1f), C.win)
            Fact2(dec(arr.map { it.optDouble("mine") }.average(), 1), "میانگین نمره", Modifier.weight(1f), LocalAccent.current)
        }
    }
}
