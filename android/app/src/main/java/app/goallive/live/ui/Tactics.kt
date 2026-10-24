package app.goallive.live.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.goallive.live.L
import app.goallive.live.data.Api
import app.goallive.live.data.a
import app.goallive.live.data.b
import app.goallive.live.data.d
import app.goallive.live.data.i
import app.goallive.live.data.o
import app.goallive.live.data.objs
import app.goallive.live.data.path
import app.goallive.live.data.s
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.roundToInt

/* ---------- the pitch, drawn the same way everywhere here ---------- */

/** Grass stripes and white lines of a horizontal pitch (105×68), filling the canvas. */
fun DrawScope.grass(vertical: Boolean = false) {
    val n = 10
    for (k in 0 until n) {
        val c = if (k % 2 == 0) C.pitch else C.pitch2
        if (vertical) drawRect(c, Offset(0f, size.height * k / n), Size(size.width, size.height / n + 1))
        else drawRect(c, Offset(size.width * k / n, 0f), Size(size.width / n + 1, size.height))
    }
    val line = Color.White.copy(alpha = 0.55f); val st = Stroke(1.5.dp.toPx())
    val w = size.width; val h = size.height
    drawRect(line, Offset(2f, 2f), Size(w - 4f, h - 4f), style = st)
    if (vertical) {
        drawLine(line, Offset(0f, h / 2), Offset(w, h / 2), st.width)
        drawCircle(line, w * 0.13f, Offset(w / 2, h / 2), style = st)
        drawRect(line, Offset(w * 0.2f, 2f), Size(w * 0.6f, h * 0.15f), style = st)
        drawRect(line, Offset(w * 0.2f, h * 0.85f - 2f), Size(w * 0.6f, h * 0.15f), style = st)
    } else {
        drawLine(line, Offset(w / 2, 0f), Offset(w / 2, h), st.width)
        drawCircle(line, h * 0.13f, Offset(w / 2, h / 2), style = st)
        drawRect(line, Offset(2f, h * 0.2f), Size(w * 0.15f, h * 0.6f), style = st)
        drawRect(line, Offset(w * 0.85f - 2f, h * 0.2f), Size(w * 0.15f, h * 0.6f), style = st)
    }
}

/* ---------- 1. where each team attacked ---------- */

/** "نقشه حمله": both teams side by side, each seen from behind its attackers; the busiest lane glows, with one plain line under it. */
@Composable
fun AttackZones(m: MatchData) {
    val az = m.content.o("attackingZones") ?: return
    val parts = listOf("total" to L("کل بازی"), "firstHalf" to L("نیمه اول"), "secondHalf" to L("نیمه دوم")).filter { az.o("home")?.o(it.first) != null }
    if (parts.isEmpty()) return
    var part by rememberSaveable(m.matchId) { mutableStateOf(parts.first().first) }
    Card {
        CardTitle(L("🗺️ نقشه حمله"), sub = L("هر تیم از کدام طرف حمله کرد"))
        if (parts.size > 1) Box(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp)) { Seg(parts, part) { part = it } }
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for ((key, home) in listOf("home" to true, "away" to false)) {
                val z = az.o(key)?.o(part) ?: continue
                val l = z.i("left") ?: 0; val c = z.i("center") ?: 0; val r = z.i("right") ?: 0
                val id = if (home) m.homeId else m.awayId
                val name = teamName(id, if (home) m.homeName else m.awayName)
                val color = if (home) m.homeColor else m.awayColor
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Crest(id, 18.dp); T(name, 13, FontWeight.ExtraBold, maxLines = 1)
                    }
                    ZonePitch(l, c, r, color, Modifier.fillMaxWidth().aspectRatio(0.82f))
                    T(zoneLine(l, c, r), 12, color = C.mute, align = TextAlign.Center, maxLines = 2)
                }
            }
        }
    }
}

/** The busiest side in a few words. */
private fun zoneLine(l: Int, c: Int, r: Int): String {
    val top = maxOf(l, c, r)
    if (top - minOf(l, c, r) < 8) return L("حمله از هر سه طرف")
    return when (top) { l -> L("بیشتر از جناح چپ"); r -> L("بیشتر از جناح راست"); else -> L("بیشتر از وسط") }
}

/** The attacking half seen from behind the attackers: three lanes, each with an arrow as thick as its share; the busiest one glows. */
@Composable
private fun ZonePitch(l: Int, c: Int, r: Int, color: Color, modifier: Modifier) {
    // left and right are the attackers' own: drawn the same in both languages
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(modifier.clip(RoundedCornerShape(14.dp))) {
            val top = maxOf(l, c, r).coerceAtLeast(1)
            Canvas(Modifier.fillMaxSize()) {
                grass(vertical = true)
                // the goal they attack, at the top
                drawRect(Color.White.copy(alpha = 0.8f), Offset(size.width * 0.38f, 0f), Size(size.width * 0.24f, 4.dp.toPx()))
                listOf(l, c, r).forEachIndexed { k, v ->
                    val x = size.width * k / 3f
                    val share = v / top.toFloat()
                    drawRoundRect(color.copy(alpha = 0.12f + 0.55f * share), Offset(x + 3f, 6.dp.toPx()), Size(size.width / 3f - 6f, size.height - 12.dp.toPx()),
                        CornerRadius(8.dp.toPx()))
                    val cx = x + size.width / 6f
                    val head = size.height * 0.2f
                    val a = Color.White.copy(alpha = 0.35f + 0.65f * share)
                    val sw = (2f + 5f * share).dp.toPx()
                    drawLine(a, Offset(cx, size.height * 0.72f), Offset(cx, head + 4.dp.toPx()), sw, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    val p = androidx.compose.ui.graphics.Path().apply {
                        moveTo(cx, head - 6.dp.toPx()); lineTo(cx - (6f + 5f * share).dp.toPx(), head + 8.dp.toPx()); lineTo(cx + (6f + 5f * share).dp.toPx(), head + 8.dp.toPx()); close()
                    }
                    drawPath(p, a)
                }
            }
            Row(Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(bottom = 10.dp)) {
                for (v in listOf(l, c, r)) Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text2(fa(v) + "٪", if (v == top) Color.White else Color.White.copy(alpha = 0.8f))
                }
            }
        }
    }
}

@Composable
private fun Text2(s: String, color: Color) {
    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.45f)).padding(horizontal = 4.dp, vertical = 1.dp)) {
        androidx.compose.material3.Text(s, color = color, style = vz(12.sp, FontWeight.Black))
    }
}

/* ---------- 2. the match story in thirty seconds ---------- */

private class Moment(val minute: Int, val icon: String, val text: String, val home: Boolean?)

/** Momentum from FotMob, wherever this match has it. */
fun momentumOf(m: MatchData): List<JSONObject> =
    m.content.path("momentum.main").a("data").objs().ifEmpty { m.facts.path("momentum.main").a("data").objs() }

/**
 * "مومنتوم بازی": who was on top minute by minute (bars above the line for the home side, below for the away side), the goals and
 * red cards marked on it, one line saying who had the better of it, and the few moments that turned the match.
 */
@Composable
fun MomentumCard(m: MatchData) {
    val mom = momentumOf(m)
    if (!m.started || mom.size < 6) return
    val hn = teamName(m.homeId, m.homeName); val an = teamName(m.awayId, m.awayName)
    val moments = remember(m.d) { storyMoments(m, mom, hn, an) }
    val overall = mom.sumOf { it.d("value") ?: 0.0 } / mom.size
    var more by rememberSaveable(m.matchId) { mutableStateOf(false) }
    Card {
        CardTitle(L("مومنتوم بازی")) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                for ((c, n) in listOf(m.homeColor to hn, m.awayColor to an)) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.size(9.dp).clip(RoundedCornerShape(3.dp)).background(c)); T(n, 11, FontWeight.Bold, C.mute, maxLines = 1)
                }
            }
        }
        T(when {
            overall > 12 -> L("{0} در بیشتر زمان بازی برتر بود.", hn)
            overall < -12 -> L("{0} در بیشتر زمان بازی برتر بود.", an)
            else -> L("بازی متعادل بود و هیچ تیمی مدت زیادی برتری نداشت.")
        }, 13, FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp))
        MomentumBars(mom, moments, m.homeColor, m.awayColor)
        val shown = if (more) moments else moments.take(3)
        shown.forEach { mo ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.width(38.dp).clip(RoundedCornerShape(50)).background(C.card2).padding(vertical = 3.dp), contentAlignment = Alignment.Center) {
                    T(fa(mo.minute) + "'", 12, FontWeight.ExtraBold, C.mute)
                }
                T(mo.icon, 14)
                T(mo.text, 13, modifier = Modifier.weight(1f))
            }
        }
        if (moments.size > 3) T(if (more) L("کمتر") else L("لحظه‌های بیشتر ({0})", fa(moments.size - 3)), 12, FontWeight.Bold, LocalAccent.current,
            Modifier.fillMaxWidth().clickable { more = !more }.padding(vertical = 8.dp), align = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
    }
}

private fun storyMoments(m: MatchData, mom: List<JSONObject>, hn: String, an: String): List<Moment> {
    val out = ArrayList<Moment>()
    fun nm(e: JSONObject) = personName(e.o("player").s("lastName") ?: e.s("nameStr") ?: e.o("player").s("name") ?: "")
    var hs = 0; var aws = 0
    for (e in m.events) {
        val t = e.i("time") ?: continue
        val home = e.b("isHome")
        when (e.s("type")) {
            "Goal" -> {
                if (home) hs++ else aws++
                val who = nm(e)
                out += Moment(t, "⚽", if (e.b("ownGoal")) L("گل به خودی {0}؛ {1} {2}، {3} {4}", who, hn, fa(hs), an, fa(aws))
                    else L("گل {0} برای {1}؛ {2} {3}، {4} {5}", who, if (home) hn else an, hn, fa(hs), an, fa(aws)), home)
            }
            "Card" -> if (e.s("card") == "Red" || e.s("card") == "YellowRed")
                out += Moment(t, "🟥", L("اخراج {0}؛ {1} ده نفره شد.", nm(e), if (home) hn else an), home)
        }
    }
    // spells of pressure: six-minute windows where one side clearly had the ball in the other's half
    val byMin = mom.associate { (it.d("minute") ?: 0.0).toInt() to (it.d("value") ?: 0.0) }
    val last = byMin.keys.maxOrNull() ?: 0
    var k = 1
    while (k + 6 <= last) {
        val sum = (k until k + 6).sumOf { byMin[it] ?: 0.0 }
        if (abs(sum) > 330) {
            val home = sum > 0
            var end = k + 6
            while (end + 3 <= last && (end until end + 3).sumOf { byMin[it] ?: 0.0 }.let { if (home) it > 120 else it < -120 }) end += 3
            // a substitution by that side just before often explains it
            val sub = m.events.lastOrNull { it.s("type") == "Substitution" && it.b("isHome") == home && (it.i("time") ?: -99) in (k - 8)..k }
            val team = if (home) hn else an
            val text = if (sub != null) L("{0} بعد از ورود {1} بازی را در دست گرفت (تا دقیقه {2}).", team,
                personName(sub.a("swap").objs().getOrNull(0).s("name") ?: ""), fa(end))
            else L("{0} از اینجا تا دقیقه {1} پشت سر هم فشار آورد.", team, fa(end))
            if (out.none { it.icon == "🔥" && abs(it.minute - k) < 10 }) out += Moment(k, "🔥", text, home)
            k = end + 1
        } else k++
    }
    return out.sortedBy { it.minute }.let { all -> if (all.size <= 7) all else all.filter { it.icon != "🔥" }.take(7).let { keep -> (keep + all.filter { it.icon == "🔥" }.take(7 - keep.size)).sortedBy { it.minute } } }
}

@Composable
private fun MomentumBars(mom: List<JSONObject>, moments: List<Moment>, hc: Color, ac: Color) {
    val maxMin = maxOf(90.0, mom.maxOf { it.d("minute") ?: 0.0 }).toFloat()
    val gold = C.gold; val line = C.line; val red = C.live
    // time runs left to right in both languages
    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Canvas(Modifier.fillMaxWidth().height(110.dp)) {
            val mid = size.height / 2
            val bw = size.width / (maxMin + 1)
            drawLine(line, Offset(0f, mid), Offset(size.width, mid), 2f)
            val ht = 45f / (maxMin + 1) * size.width
            drawLine(line, Offset(ht, 4f), Offset(ht, size.height - 4f), 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 8f)))
            for (p in mom) {
                val v = (p.d("value") ?: 0.0).toFloat()
                val h = abs(v) / 100f * (mid - 10)
                val x = (p.d("minute") ?: 0.0).toFloat() / (maxMin + 1) * size.width
                drawRoundRect(if (v >= 0) hc else ac, Offset(x, if (v >= 0) mid - h else mid), Size(maxOf(bw - 1f, 1.5f), h), CornerRadius(1.5f))
            }
            // goals and red cards on the side of the team they belong to
            for (mo in moments) if (mo.icon != "🔥") {
                val x = mo.minute / (maxMin + 1) * size.width
                val y = if (mo.home == false) size.height - 5.dp.toPx() else 5.dp.toPx()
                drawCircle(Color.Black.copy(alpha = 0.5f), 5.5.dp.toPx(), Offset(x, y))
                drawCircle(if (mo.icon == "⚽") gold else red, 4.dp.toPx(), Offset(x, y))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Ltr("${maxMin.toInt()}'", style = vz(11.sp), color = C.mute); Spacer(Modifier.weight(1f))
            Ltr("45'", style = vz(11.sp), color = C.mute); Spacer(Modifier.weight(1f))
            Ltr("0'", style = vz(11.sp), color = C.mute)
        }
    }
}

/* ---------- 3. tactical preview before kick-off ---------- */

private class Duel(val atk: JSONObject, val def: JSONObject, val atkHome: Boolean)

/** "پیش‌نمایش تاکتیکی": both systems, the duels on each flank and what each side needs to do to win. */
@Composable
fun TacticalPreview(m: MatchData) {
    if (m.started || m.cancelled || m.homeId <= 0 || m.awayId <= 0) return
    val lu = m.content.o("lineup")
    val H = lu?.o("homeTeam"); val A = lu?.o("awayTeam")
    val hn = teamName(m.homeId, m.homeName); val an = teamName(m.awayId, m.awayName)
    val radar = rememberLoader(m.matchId, "preview") {
        coroutineScope {
            val h = async { runCatching { teamRadar(m.homeId, hn, true) }.getOrNull() }
            val a = async { runCatching { teamRadar(m.awayId, an, false) }.getOrNull() }
            h.await() to a.await()
        }
    }
    Card {
        CardTitle(L("♟️ پیش‌نمایش تاکتیکی"), sub = if ((lu?.s("lineupType") ?: "standard") != "standard") L("ترکیب احتمالی") else null)
        val hf = H?.s("formation"); val af = A?.s("formation")
        if (hf != null && af != null) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                FormationBox(m.homeId, hn, hf, m.homeColor, Modifier.weight(1f))
                T(L("در برابر"), 12, FontWeight.Bold, C.mute, Modifier.padding(horizontal = 8.dp))
                FormationBox(m.awayId, an, af, m.awayColor, Modifier.weight(1f))
            }
            val duels = remember(m.d) { duels(H, A) }
            if (duels.isNotEmpty()) {
                SubHead(L("دوئل‌های کلیدی"))
                duels.forEach { d -> DuelRow(d, if (d.atkHome) m.homeColor else m.awayColor, if (d.atkHome) m.awayColor else m.homeColor) }
            }
        } else T(L("ترکیب‌ها حدود یک ساعت قبل از بازی اعلام می‌شود؛ آن موقع سیستم دو تیم و دوئل‌های کلیدی اینجا می‌آید."), 12, color = C.mute,
            modifier = Modifier.padding(horizontal = 16.dp))
        radar.value?.let { (h, a) ->
            val keys = listOfNotNull(keyToWin(hn, h, a), keyToWin(an, a, h))
            if (keys.isNotEmpty()) {
                SubHead(L("کلید برد"))
                keys.forEach { T("• $it", 13, modifier = Modifier.padding(horizontal = 16.dp, vertical = 3.dp)) }
            }
        }
        Spacer(Modifier.height(14.dp))
    }
}

/** One line on what [team] should lean on: its own best strength, or the other side's clearest weakness. */
private fun keyToWin(team: String, mine: TeamRadar?, theirs: TeamRadar?): String? {
    val weak = theirs?.insights?.firstOrNull { !it.good }
    val strong = mine?.insights?.firstOrNull { it.good }
    return when {
        weak != null -> L("{0}: از ضعف حریف استفاده کند؛ {1}", team, weak.text)
        strong != null -> L("{0}: روی نقطه قوتش حساب کند؛ {1}", team, strong.text)
        else -> null
    }
}

@Composable
private fun FormationBox(id: Int, name: String, formation: String, color: Color, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(color.copy(alpha = 0.14f)).border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
        .padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Crest(id, 26.dp)
        T(name, 12, FontWeight.Bold, maxLines = 1, align = TextAlign.Center)
        Ltr(fa(formation), style = vz(22.sp, FontWeight.Black), raw = true)
    }
}

/** Each side's two most advanced players against the defender on their flank (same side of the pitch, mirrored). */
private fun duels(H: JSONObject?, A: JSONObject?): List<Duel> {
    fun xi(t: JSONObject?) = t?.a("starters").objs().filter { it.o("horizontalLayout") != null }
    val h = xi(H) ?: return emptyList(); val a = xi(A) ?: return emptyList()
    fun x(p: JSONObject) = p.path("horizontalLayout").d("x") ?: 0.5
    fun y(p: JSONObject) = p.path("horizontalLayout").d("y") ?: 0.5
    fun pick(atk: List<JSONObject>, def: List<JSONObject>, home: Boolean): List<Duel> {
        val forwards = atk.sortedByDescending { x(it) }.take(2)
        val backs = def.filter { x(it) in 0.15..0.45 }
        return forwards.mapNotNull { f -> backs.minByOrNull { abs(y(f) - (1 - y(it))) }?.let { Duel(f, it, home) } }
    }
    return pick(h, a, true) + pick(a, h, false)
}

@Composable
private fun DuelRow(d: Duel, ac: Color, dc: Color) {
    val nav = LocalNav.current
    @Composable fun side(p: JSONObject, color: Color, mod: Modifier) {
        Row(mod.clickable { nav.player(p.i("id") ?: 0) }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Face(p.i("id") ?: 0, 36.dp, color)
            Column(Modifier.weight(1f)) {
                Ltr(p.s("lastName") ?: p.s("name") ?: "", style = lat(14.sp))
                T(listOfNotNull(p.i("age")?.let { L("{0} سال", fa(it)) }, (p.opt("marketValue") as? Number)?.toLong()?.let { money(it) }).joinToString(" · "),
                    11, color = C.mute, maxLines = 1)
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        side(d.atk, ac, Modifier.weight(1f))
        T("⚔️", 16, modifier = Modifier.padding(horizontal = 6.dp))
        side(d.def, dc, Modifier.weight(1f))
    }
}

/* ---------- 4. a player's heatmap ---------- */

/** A player who can be put next to the one being looked at; [opta] is the id FotMob's heatmaps use. */
class HeatPlayer(val id: Int, val opta: Int, val name: String, val home: Boolean)

/**
 * "نقشه حرارتی": where a player was during the match, as a smooth heat layer over the whole pitch (attacking left to right),
 * the share of his time in each third and lane, a full-screen view, and a second player from the match next to him.
 */
@Composable
fun PlayerHeatmap(matchId: Int, playerId: Int, opta: Int, name: String, others: List<HeatPlayer>) {
    // loaded straight away (not tied to the screen's lifecycle): the player sheet is its own window
    val loaded by produceState<Map<Int, List<Pair<Float, Float>>>?>(heatCache[matchId], matchId) {
        if (value == null) value = runCatching { heatPoints(matchId) }.getOrNull()?.also { heatCache[matchId] = it }
    }
    val all = loaded ?: return
    val pts = all[opta] ?: return
    if (pts.size < 3) return
    var big by remember { mutableStateOf(false) }
    var vs by rememberSaveable(playerId) { mutableStateOf<Int?>(null) }
    val rivals = others.filter { it.id != playerId && (all[it.opta]?.size ?: 0) >= 3 }
    val other = rivals.firstOrNull { it.id == vs }
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        T(L("نقشه حرارتی"), 12, FontWeight.ExtraBold, C.mute, Modifier.weight(1f))
        T(if (big) L("نمای کوچک") else L("نمای بزرگ"), 12, FontWeight.Bold, LocalAccent.current,
            Modifier.clip(RoundedCornerShape(8.dp)).clickable { big = !big }.padding(horizontal = 6.dp, vertical = 2.dp))
    }
    // the large view stands the whole pitch upright, attack going up (drawn in place: the sheet is already its own window)
    if (big) HeatPitch(pts, Modifier.padding(horizontal = 16.dp).fillMaxWidth().aspectRatio(68f / 105f).clickable { big = false }, vertical = true)
    else HeatPitch(pts, Modifier.padding(horizontal = 16.dp).fillMaxWidth().aspectRatio(105f / 68f).clickable { big = true })
    HeatScale()
    if (other == null) ZoneShares(pts, null, name, null)
    if (rivals.isNotEmpty()) {
        T(L("مقایسه با بازیکن دیگر"), 12, FontWeight.ExtraBold, C.mute, Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp))
        androidx.compose.foundation.lazy.LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(rivals.size) { k ->
                val r = rivals[k]
                val on = r.id == vs
                Box(Modifier.clip(RoundedCornerShape(50)).background(if (on) LocalAccent.current else C.card2).clickable { vs = if (on) null else r.id }
                    .padding(horizontal = 12.dp, vertical = 6.dp)) {
                    T(personName(r.name), 12, FontWeight.Bold, if (on) LocalAccentInk.current else C.ink, maxLines = 1)
                }
            }
        }
    }
    if (other != null) {
        val op = all[other.opta]!!
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Face(other.id, 26.dp); Spacer(Modifier.width(8.dp)); T(personName(other.name), 13, FontWeight.ExtraBold)
        }
        HeatPitch(op, Modifier.padding(horizontal = 16.dp).fillMaxWidth().aspectRatio(105f / 68f))
        ZoneShares(pts, op, name, other.name)
    }
}

/** Heat from cold to hot, with its two ends named. */
@Composable
private fun HeatScale() {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        T(L("کم"), 11, color = C.mute)
        Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(6.dp)).background(Brush.horizontalGradient((1..5).map { heatColor(it / 5f, 1f) })))
        T(L("زیاد"), 11, color = C.mute)
    }
}

/** Share of the player's time in each third and each lane; with a second player, both side by side. */
@Composable
private fun ZoneShares(a: List<Pair<Float, Float>>, b: List<Pair<Float, Float>>?, an: String, bn: String?) {
    fun shares(p: List<Pair<Float, Float>>): List<Int> {
        val n = p.size.coerceAtLeast(1).toFloat()
        val thirds = listOf(p.count { it.first < 35 }, p.count { it.first in 35f..70f }, p.count { it.first > 70 })
        val lanes = listOf(p.count { it.second < 22.7f }, p.count { it.second in 22.7f..45.3f }, p.count { it.second > 45.3f })
        return (thirds + lanes).map { (it / n * 100).roundToInt() }
    }
    val sa = shares(a); val sb = b?.let(::shares)
    val labels = listOf(L("نیمه خودی"), L("میانه زمین"), L("یک‌سوم حمله"), L("جناح چپ"), L("مرکز زمین"), L("جناح راست"))
    val accent = LocalAccent.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(14.dp)).background(C.card2).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)) {
        if (bn != null) Row {
            T(personName(an), 12, FontWeight.ExtraBold, accent, Modifier.weight(1f), maxLines = 1)
            T(personName(bn), 12, FontWeight.ExtraBold, C.gold, Modifier.weight(1f), maxLines = 1, align = TextAlign.End)
        }
        labels.forEachIndexed { k, label ->
            if (k == 3) Box(Modifier.fillMaxWidth().height(1.dp).background(C.line))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (sb == null) {
                    T(label, 12, modifier = Modifier.width(84.dp))
                    Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(8.dp)).background(C.line)) {
                        Box(Modifier.fillMaxWidth(sa[k] / 100f).fillMaxSize().clip(RoundedCornerShape(8.dp)).background(accent))
                    }
                    T(fa(sa[k]) + "٪", 12, FontWeight.ExtraBold, modifier = Modifier.width(40.dp), align = TextAlign.End)
                } else {
                    T(fa(sa[k]) + "٪", 13, FontWeight.ExtraBold, if (sa[k] >= sb[k]) accent else C.mute, Modifier.width(44.dp))
                    T(label, 12, modifier = Modifier.weight(1f), align = TextAlign.Center)
                    T(fa(sb[k]) + "٪", 13, FontWeight.ExtraBold, if (sb[k] >= sa[k]) C.gold else C.mute, Modifier.width(44.dp), align = TextAlign.End)
                }
            }
        }
        if (sb == null) T(heatSentence(a), 12, color = C.mute)
    }
}

/** A smooth heat layer: every point spreads like a soft spot, added up on a fine grid and coloured cold to hot. */
@Composable
private fun HeatPitch(pts: List<Pair<Float, Float>>, modifier: Modifier, vertical: Boolean = false) {
    val grid = remember(pts) { density(pts) }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(modifier.clip(RoundedCornerShape(14.dp))) {
            Canvas(Modifier.fillMaxSize()) {
                grass(vertical)
                val cw = (if (vertical) size.height else size.width) / NX
                val ch = (if (vertical) size.width else size.height) / NY
                for (i in 0 until NX) for (j in 0 until NY) {
                    val v = grid[i * NY + j]
                    if (v < 0.1f) continue
                    // upright: attack goes up, the player's left stays on the left
                    val tl = if (vertical) Offset(j * ch, size.height - (i + 1) * cw) else Offset(i * cw, j * ch)
                    drawRect(heatColor(v, 0.1f + 0.75f * v * v), tl, if (vertical) Size(ch + 0.6f, cw + 0.6f) else Size(cw + 0.6f, ch + 0.6f))
                }
                // the attacking direction
                val arrow = Color.White.copy(alpha = 0.85f); val sw = 2.dp.toPx()
                if (vertical) {
                    val ax = size.width - 12.dp.toPx()
                    drawLine(arrow, Offset(ax, size.height * 0.58f), Offset(ax, size.height * 0.42f), sw)
                    drawLine(arrow, Offset(ax, size.height * 0.42f), Offset(ax - 5.dp.toPx(), size.height * 0.42f + 6.dp.toPx()), sw)
                    drawLine(arrow, Offset(ax, size.height * 0.42f), Offset(ax + 5.dp.toPx(), size.height * 0.42f + 6.dp.toPx()), sw)
                } else {
                    val ay = size.height - 10.dp.toPx()
                    drawLine(arrow, Offset(size.width * 0.42f, ay), Offset(size.width * 0.58f, ay), sw)
                    drawLine(arrow, Offset(size.width * 0.58f, ay), Offset(size.width * 0.58f - 6.dp.toPx(), ay - 5.dp.toPx()), sw)
                    drawLine(arrow, Offset(size.width * 0.58f, ay), Offset(size.width * 0.58f - 6.dp.toPx(), ay + 5.dp.toPx()), sw)
                }
            }
        }
    }
}

private const val NX = 70
private const val NY = 45

/** Points on the 105×68 pitch → a 0…1 grid, each point a Gaussian about five metres wide. */
private fun density(pts: List<Pair<Float, Float>>): FloatArray {
    val g = FloatArray(NX * NY)
    val sx = 105f / NX; val sy = 68f / NY; val sigma2 = 2 * 5f * 5f
    for ((x, y) in pts) for (i in 0 until NX) {
        val dx = (i + 0.5f) * sx - x
        if (dx * dx > 4 * sigma2) continue
        for (j in 0 until NY) {
            val dy = (j + 0.5f) * sy - y
            val d = dx * dx + dy * dy
            if (d < 4 * sigma2) g[i * NY + j] += kotlin.math.exp(-d / sigma2)
        }
    }
    val top = g.maxOrNull()?.takeIf { it > 0f } ?: 1f
    for (k in g.indices) g[k] /= top
    return g
}

/** Cold to hot: teal, green, yellow, orange, red. */
private fun heatColor(v: Float, alpha: Float): Color {
    val stops = listOf(Color(0xFF1FB5C9), Color(0xFF3DDC84), Color(0xFFFFE14D), Color(0xFFFF9A3C), Color(0xFFFF3B3B))
    val t = v.coerceIn(0f, 1f) * (stops.size - 1)
    val k = t.toInt().coerceAtMost(stops.size - 2)
    return androidx.compose.ui.graphics.lerp(stops[k], stops[k + 1], t - k).copy(alpha = alpha)
}

private fun heatSentence(pts: List<Pair<Float, Float>>): String {
    val third = listOf(pts.count { it.first < 35 }, pts.count { it.first in 35f..70f }, pts.count { it.first > 70 })
    val lane = listOf(pts.count { it.second < 22.7f }, pts.count { it.second in 22.7f..45.3f }, pts.count { it.second > 45.3f })
    val where = when (third.indexOf(third.max())) { 0 -> L("بیشتر در نیمه خودی"); 1 -> L("بیشتر در میانه زمین"); else -> L("بیشتر در یک‌سوم حمله") }
    val side = when (lane.indexOf(lane.max())) { 0 -> L("سمت چپ"); 1 -> L("وسط"); else -> L("سمت راست") }
    return L("{0} و {1} بازی کرد (جهت حمله از چپ به راست است).", where, side)
}

private val heatCache = java.util.concurrent.ConcurrentHashMap<Int, Map<Int, List<Pair<Float, Float>>>>()

/** Every player's heatmap points in a match, by Opta player id: FotMob draws them as circles on a 105×68 pitch. */
private suspend fun heatPoints(matchId: Int): Map<Int, List<Pair<Float, Float>>> {
    val url = java.net.URLEncoder.encode("https://pub.fotmob.com/prod/db/api/heatmap/match/$matchId", "UTF-8")
    val d = Api.fotmob("data/heatmap/match/$matchId/heatmaps?heatmapUrl=$url")
    val players = d.o("players") ?: return emptyMap()
    val circle = Regex("cx=\\\\?\"([0-9.]+)\\\\?\"\\s+cy=\\\\?\"([0-9.]+)")
    return players.keys().asSequence().mapNotNull { k ->
        val id = k.removePrefix("p").toIntOrNull() ?: return@mapNotNull null
        val raw = players.opt(k)?.toString() ?: return@mapNotNull null
        id to circle.findAll(raw).map { it.groupValues[1].toFloat() to it.groupValues[2].toFloat() }.toList()
    }.toMap()
}
