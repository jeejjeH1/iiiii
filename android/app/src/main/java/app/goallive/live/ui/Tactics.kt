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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
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
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(color.copy(alpha = 0.14f)).border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
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
 * "نقشه حرارتی": where a player was during the match. A smooth heat layer (FotMob's points spread out and coloured from faint
 * green to red) under crisp pitch markings, attacking left to right; a large upright view; how his time split over the thirds
 * and the flanks; and any other player of the match next to him.
 */
@Composable
fun PlayerHeatmap(matchId: Int, playerId: Int, opta: Int, name: String, others: List<HeatPlayer>, color: Color) {
    // loaded straight away (not tied to the screen's lifecycle): the player sheet is its own window
    val loaded by produceState<Map<Int, List<Pair<Float, Float>>>?>(heatCache[matchId], matchId) {
        if (value == null) value = runCatching { heatPoints(matchId) }.getOrNull()?.also { heatCache[matchId] = it }
    }
    val all = loaded ?: return
    val pts = all[opta] ?: return
    if (pts.size < 3) return
    var big by rememberSaveable(playerId) { mutableStateOf(false) }
    var vs by rememberSaveable(playerId) { mutableStateOf<Int?>(null) }
    val rivals = others.filter { it.id != playerId && (all[it.opta]?.size ?: 0) >= 3 }
    val other = rivals.firstOrNull { it.id == vs }
    val accent = LocalAccent.current
    SheetCard {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp, top = 14.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            T("🔥", 16); Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f)) {
                T(L("نقشه حرارتی"), 15, FontWeight.ExtraBold)
                T(L("هر جا گرم‌تر است، بازیکن بیشتر آنجا بوده"), 11, color = C.mute, maxLines = 1)
            }
            MiniSeg(listOf(false to L("افقی"), true to L("بزرگ")), big) { big = it }
        }
        Box(Modifier.padding(horizontal = 12.dp)) {
            if (big) HeatPitch(pts, Modifier.fillMaxWidth().aspectRatio(68f / 105f), vertical = true)
            else HeatPitch(pts, Modifier.fillMaxWidth().aspectRatio(105f / 68f))
        }
        HeatLegend()
        if (other == null) ZoneBars(pts, accent)
        if (rivals.isNotEmpty()) {
            T(L("مقایسه با بازیکن دیگر"), 12, FontWeight.ExtraBold, C.mute, Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 8.dp))
            androidx.compose.foundation.lazy.LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(rivals.size) { k ->
                    val r = rivals[k]
                    val on = r.id == vs
                    Row(Modifier.clip(RoundedCornerShape(50)).background(if (on) C.gold.copy(alpha = 0.22f) else C.card2)
                        .border(1.dp, if (on) C.gold else Color.Transparent, RoundedCornerShape(50)).clickable { vs = if (on) null else r.id }
                        .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Face(r.id, 26.dp); Spacer(Modifier.width(6.dp))
                        T(personName(r.name.substringAfterLast(' ')), 12, FontWeight.Bold, if (on) C.gold else C.ink, maxLines = 1)
                    }
                }
            }
        }
        if (other != null) {
            val op = all[other.opta]!!
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(C.gold)); Spacer(Modifier.width(8.dp))
                Face(other.id, 26.dp); Spacer(Modifier.width(8.dp)); T(personName(other.name), 14, FontWeight.ExtraBold, modifier = Modifier.weight(1f), maxLines = 1)
                T(L("بستن"), 12, FontWeight.Bold, C.mute, Modifier.clip(RoundedCornerShape(10.dp)).clickable { vs = null }.padding(6.dp))
            }
            Box(Modifier.padding(horizontal = 12.dp)) {
                if (big) HeatPitch(op, Modifier.fillMaxWidth().aspectRatio(68f / 105f), vertical = true)
                else HeatPitch(op, Modifier.fillMaxWidth().aspectRatio(105f / 68f))
            }
            ZoneCompare(pts, op, name, other.name, accent)
        }
        Spacer(Modifier.height(14.dp))
    }
}

/** A rounded card used inside the player sheet. */
@Composable
fun SheetCard(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(C.card)
        .border(1.dp, C.line, RoundedCornerShape(22.dp)), content = content)
}

/** A two-option pill switch. */
@Composable
fun <K> MiniSeg(options: List<Pair<K, String>>, selected: K, onPick: (K) -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(50)).background(C.card2).padding(3.dp)) {
        for ((k, label) in options) {
            val on = k == selected
            Box(Modifier.clip(RoundedCornerShape(50)).background(if (on) LocalAccent.current else Color.Transparent).clickable { onPick(k) }
                .padding(horizontal = 12.dp, vertical = 5.dp)) {
                T(label, 12, FontWeight.Bold, if (on) LocalAccentInk.current else C.mute)
            }
        }
    }
}

/** The colour ramp with its two ends named, and which way the team attacks. */
@Composable
private fun HeatLegend() {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        T(L("کم"), 11, color = C.mute)
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(10.dp)).background(Brush.horizontalGradient(RAMP.map { it.second.copy(alpha = 1f) })))
        }
        T(L("زیاد"), 11, color = C.mute)
    }
}

/** Two stacked bars: time in own half / midfield / final third, and on the left / centre / right. */
@Composable
private fun ZoneBars(p: List<Pair<Float, Float>>, accent: Color) {
    val z = zoneShares(p)
    Column(Modifier.padding(horizontal = 12.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.card2).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        T(L("در کدام بخش زمین"), 12, FontWeight.Bold, C.mute)
        StackBar(z.take(3), listOf(L("نیمه خودی"), L("میانه زمین"), L("یک‌سوم حمله")), accent)
        Spacer(Modifier.height(4.dp))
        T(L("در کدام سمت"), 12, FontWeight.Bold, C.mute)
        StackBar(z.drop(3), listOf(L("جناح چپ"), L("مرکز زمین"), L("جناح راست")), accent)
        Spacer(Modifier.height(2.dp))
        T(heatSentence(p), 12, color = C.ink)
    }
}

/** Three shares as one bar, strongest darkest; laid out like the pitch above it (own goal on the left, the player's left first). */
@Composable
private fun StackBar(v: List<Int>, labels: List<String>, accent: Color) {
    val top = v.maxOrNull()?.coerceAtLeast(1) ?: 1
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column {
            Row(Modifier.fillMaxWidth().height(30.dp).clip(RoundedCornerShape(10.dp)), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                v.forEachIndexed { k, x ->
                    Box(Modifier.weight(x.coerceAtLeast(9).toFloat()).fillMaxHeight().background(accent.copy(alpha = 0.22f + 0.7f * x / top)), contentAlignment = Alignment.Center) {
                        T(fa(x) + "٪", 12, FontWeight.Black, if (x == top) LocalAccentInk.current else C.ink, maxLines = 1)
                    }
                }
            }
            // names in three equal columns, so a thin slice still gets its whole label
            Row(Modifier.fillMaxWidth().padding(top = 3.dp)) {
                labels.forEachIndexed { k, label ->
                    Box(Modifier.weight(1f), contentAlignment = when (k) { 0 -> Alignment.CenterStart; 2 -> Alignment.CenterEnd; else -> Alignment.Center }) {
                        T(label, 11, color = C.mute, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** Both players' shares, mirrored bars meeting in the middle. */
@Composable
private fun ZoneCompare(a: List<Pair<Float, Float>>, b: List<Pair<Float, Float>>, an: String, bn: String, accent: Color) {
    val sa = zoneShares(a); val sb = zoneShares(b)
    val labels = listOf(L("نیمه خودی"), L("میانه زمین"), L("یک‌سوم حمله"), L("جناح چپ"), L("مرکز زمین"), L("جناح راست"))
    Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.card2).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(accent)); Spacer(Modifier.width(5.dp))
            T(personName(an.substringAfterLast(' ')), 12, FontWeight.ExtraBold, accent, Modifier.weight(1f), maxLines = 1)
            T(personName(bn.substringAfterLast(' ')), 12, FontWeight.ExtraBold, C.gold, Modifier.weight(1f), maxLines = 1, align = TextAlign.End)
            Spacer(Modifier.width(5.dp)); Box(Modifier.size(8.dp).clip(CircleShape).background(C.gold))
        }
        labels.forEachIndexed { k, label ->
            if (k == 3) Box(Modifier.fillMaxWidth().height(1.dp).background(C.line))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    T(fa(sa[k]) + "٪", 13, FontWeight.Black, if (sa[k] >= sb[k]) accent else C.mute, Modifier.width(44.dp))
                    T(label, 12, modifier = Modifier.weight(1f), align = TextAlign.Center)
                    T(fa(sb[k]) + "٪", 13, FontWeight.Black, if (sb[k] >= sa[k]) C.gold else C.mute, Modifier.width(44.dp), align = TextAlign.End)
                }
                Row(Modifier.fillMaxWidth().padding(top = 3.dp).height(5.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.CenterEnd) {
                        Box(Modifier.fillMaxWidth(sa[k] / 100f).fillMaxHeight().clip(RoundedCornerShape(5.dp)).background(accent))
                    }
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
                        Box(Modifier.fillMaxWidth(sb[k] / 100f).fillMaxHeight().clip(RoundedCornerShape(5.dp)).background(C.gold))
                    }
                }
            }
        }
    }
}

/** Shares of the points: own half, midfield, final third, then left, centre, right (the attacker's own sides). */
private fun zoneShares(p: List<Pair<Float, Float>>): List<Int> {
    val n = p.size.coerceAtLeast(1).toFloat()
    val thirds = listOf(p.count { it.first < 35 }, p.count { it.first in 35f..70f }, p.count { it.first > 70 })
    val lanes = listOf(p.count { it.second < 22.7f }, p.count { it.second in 22.7f..45.3f }, p.count { it.second > 45.3f })
    return (thirds + lanes).map { (it / n * 100).roundToInt() }
}

/**
 * The heat itself: a 1-metre grid where every point spreads like a soft spot, coloured on [RAMP] into a small bitmap that is
 * stretched smoothly over the pitch; then the markings on top so they stay sharp.
 */
@Composable
private fun HeatPitch(pts: List<Pair<Float, Float>>, modifier: Modifier, vertical: Boolean = false) {
    val image = remember(pts, vertical) { heatImage(pts, vertical) }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Canvas(modifier.clip(RoundedCornerShape(18.dp))) {
            // a deep, quiet pitch so the colours stand out
            val n = 12
            for (k in 0 until n) {
                val c = if (k % 2 == 0) Color(0xFF123A26) else Color(0xFF103421)
                if (vertical) drawRect(c, Offset(0f, size.height * k / n), Size(size.width, size.height / n + 1))
                else drawRect(c, Offset(size.width * k / n, 0f), Size(size.width / n + 1, size.height))
            }
            drawImage(image, dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt()),
                filterQuality = androidx.compose.ui.graphics.FilterQuality.High)
            pitchMarkings(vertical)
            // which way he attacks
            val arrow = Color.White.copy(alpha = 0.9f); val sw = 2.dp.toPx(); val h = 6.dp.toPx()
            if (vertical) {
                val x = size.width - 14.dp.toPx(); val y0 = size.height * 0.56f; val y1 = size.height * 0.44f
                drawLine(arrow, Offset(x, y0), Offset(x, y1), sw)
                drawLine(arrow, Offset(x, y1), Offset(x - h, y1 + h), sw); drawLine(arrow, Offset(x, y1), Offset(x + h, y1 + h), sw)
            } else {
                val y = size.height - 12.dp.toPx(); val x0 = size.width * 0.44f; val x1 = size.width * 0.56f
                drawLine(arrow, Offset(x0, y), Offset(x1, y), sw)
                drawLine(arrow, Offset(x1, y), Offset(x1 - h, y - h), sw); drawLine(arrow, Offset(x1, y), Offset(x1 - h, y + h), sw)
            }
        }
    }
}

/** Every line of a 105×68 pitch, horizontal or stood up (own goal at the bottom). */
private fun DrawScope.pitchMarkings(vertical: Boolean) {
    val line = Color.White.copy(alpha = 0.55f); val st = Stroke(1.3.dp.toPx())
    val sx = size.width / (if (vertical) 68f else 105f); val sy = size.height / (if (vertical) 105f else 68f)
    fun pt(x: Float, y: Float) = if (vertical) Offset(y * sx, (105f - x) * sy) else Offset(x * sx, y * sy)
    fun rect(x0: Float, y0: Float, x1: Float, y1: Float) {
        val a = pt(x0, y0); val b = pt(x1, y1)
        drawRect(line, Offset(minOf(a.x, b.x), minOf(a.y, b.y)), Size(abs(a.x - b.x), abs(a.y - b.y)), style = st)
    }
    val r = 9.15f * sx
    rect(0.4f, 0.4f, 104.6f, 67.6f)
    drawLine(line, pt(52.5f, 0f), pt(52.5f, 68f), st.width)
    drawCircle(line, r, pt(52.5f, 34f), style = st)
    drawCircle(line, 2.dp.toPx(), pt(52.5f, 34f))
    for (left in listOf(true, false)) {
        val e = if (left) 0f else 105f; val d = if (left) 1f else -1f
        rect(e, 13.84f, e + d * 16.5f, 54.16f)
        rect(e, 24.84f, e + d * 5.5f, 43.16f)
        val spot = pt(e + d * 11f, 34f)
        drawCircle(line, 1.8.dp.toPx(), spot)
        // the arc outside the box; angles turn a quarter when the pitch stands up
        val base = (if (left) -53.13f else 126.87f) + if (vertical) -90f else 0f
        drawArc(line, base, 106.26f, false, Offset(spot.x - r, spot.y - r), Size(2 * r, 2 * r), style = st)
    }
}

/** Faint green → yellow → orange → red, with the faintest heat left see-through. */
private val RAMP = listOf(
    0.10f to Color(0x0034D399), 0.25f to Color(0x6634D399), 0.45f to Color(0x99D9F24A),
    0.65f to Color(0xC0FFC93C), 0.82f to Color(0xD9FF7A2F), 1.0f to Color(0xEEFF2E3C),
)

private fun ramp(v: Float): Color {
    if (v <= RAMP.first().first) return Color.Transparent
    for (k in 1 until RAMP.size) {
        val (t1, c1) = RAMP[k]
        if (v <= t1) {
            val (t0, c0) = RAMP[k - 1]
            return androidx.compose.ui.graphics.lerp(c0, c1, (v - t0) / (t1 - t0))
        }
    }
    return RAMP.last().second
}

/** The heat as a small bitmap: one pixel per metre (105×68, or 68×105 stood up). */
private fun heatImage(pts: List<Pair<Float, Float>>, vertical: Boolean): androidx.compose.ui.graphics.ImageBitmap {
    val w = 105; val h = 68
    val g = FloatArray(w * h)
    val s2 = 2 * 4.5f * 4.5f; val reach = 11
    for ((px, py) in pts) {
        val cx = px.roundToInt(); val cy = py.roundToInt()
        for (x in maxOf(0, cx - reach)..minOf(w - 1, cx + reach)) for (y in maxOf(0, cy - reach)..minOf(h - 1, cy + reach)) {
            val dx = x + 0.5f - px; val dy = y + 0.5f - py
            g[y * w + x] += kotlin.math.exp(-(dx * dx + dy * dy) / s2)
        }
    }
    val top = g.maxOrNull()?.takeIf { it > 0f } ?: 1f
    val bw = if (vertical) h else w; val bh = if (vertical) w else h
    val px = IntArray(bw * bh)
    for (x in 0 until w) for (y in 0 until h) {
        val c = ramp(g[y * w + x] / top).toArgb()
        if (vertical) px[(w - 1 - x) * bw + y] = c else px[y * bw + x] = c
    }
    return android.graphics.Bitmap.createBitmap(px, bw, bh, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
}

private fun heatSentence(pts: List<Pair<Float, Float>>): String {
    val third = listOf(pts.count { it.first < 35 }, pts.count { it.first in 35f..70f }, pts.count { it.first > 70 })
    val lane = listOf(pts.count { it.second < 22.7f }, pts.count { it.second in 22.7f..45.3f }, pts.count { it.second > 45.3f })
    val where = when (third.indexOf(third.max())) { 0 -> L("بیشتر در نیمه خودی"); 1 -> L("بیشتر در میانه زمین"); else -> L("بیشتر در یک‌سوم حمله") }
    val side = when (lane.indexOf(lane.max())) { 0 -> L("سمت چپ"); 1 -> L("وسط"); else -> L("سمت راست") }
    return L("{0} و {1} بازی کرد (جهت حمله از چپ به راست است).", where, side)
}

internal val heatCache = java.util.concurrent.ConcurrentHashMap<Int, Map<Int, List<Pair<Float, Float>>>>()

/** Every player's heatmap points in a match, by Opta player id: FotMob draws them as circles on a 105×68 pitch. */
internal suspend fun heatPoints(matchId: Int): Map<Int, List<Pair<Float, Float>>> {
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

/* ---------- 5. average positions ---------- */

/**
 * "میانگین جایگاه": where each starter stood on average, worked out from his heatmap points (as SofaScore shows it).
 * Both teams on one pitch, the home side attacking left to right and the away side turned round to face it; a toggle
 * shows one team on its own, with names. A tap on a player opens his page.
 */
@Composable
fun AveragePositions(m: MatchData) {
    val loaded by produceState<Map<Int, List<Pair<Float, Float>>>?>(heatCache[m.matchId], m.matchId) {
        if (value == null) value = runCatching { heatPoints(m.matchId) }.getOrNull()?.also { heatCache[m.matchId] = it }
    }
    val all = loaded ?: return
    val dots = remember(all, m.d) { averageDots(m, all) }
    if (dots.size < 6) return
    var show by rememberSaveable(m.matchId) { mutableStateOf("both") }
    val nav = LocalNav.current
    Card {
        CardTitle(L("📍 میانگین جایگاه بازیکنان"), sub = L("جای هر بازیکن به‌طور میانگین در طول بازی"))
        Box(Modifier.padding(horizontal = 12.dp)) {
            Seg(listOf("both" to L("هر دو"), "home" to teamName(m.homeId, m.homeName), "away" to teamName(m.awayId, m.awayName)), show) { show = it }
        }
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            BoxWithConstraints(Modifier.padding(12.dp).fillMaxWidth().aspectRatio(105f / 68f).clip(RoundedCornerShape(18.dp))) {
                Canvas(Modifier.fillMaxSize()) {
                    for (k in 0 until 12) drawRect(if (k % 2 == 0) Color(0xFF1C5A36) else Color(0xFF19512F), Offset(size.width * k / 12, 0f), Size(size.width / 12 + 1, size.height))
                    pitchMarkings(false)
                }
                val w = maxWidth; val h = maxHeight
                dots.filter { show == "both" || it.home == (show == "home") }.forEach { d ->
                    val col = if (d.home) m.homeColor else m.awayColor
                    val r = 13.dp
                    Column(Modifier.absoluteOffset(w * (d.x / 105f) - 30.dp, h * (d.y / 68f) - r).width(60.dp).clickable { nav.player(d.id) },
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(r * 2).clip(CircleShape).background(col).border(1.5.dp, if (d.home) Color.White else Color(0xFF0B0F14), CircleShape),
                            contentAlignment = Alignment.Center) { T(fa(d.number), 12, FontWeight.Black, inkOn(col)) }
                        if (show != "both") T(d.name, 12, FontWeight.Bold, Color.White, maxLines = 1, align = TextAlign.Center)
                    }
                }
            }
        }
        T(L("{0} از چپ به راست حمله می‌کند", teamName(m.homeId, m.homeName)), 12, color = C.mute,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp), align = TextAlign.Center)
    }
}

internal class AvgDot(val id: Int, val number: String, val name: String, val x: Float, val y: Float, val home: Boolean)

/** Each starter's mean heatmap point; heatmaps all attack left to right, so the away side is turned round. */
internal fun averageDots(m: MatchData, all: Map<Int, List<Pair<Float, Float>>>): List<AvgDot> {
    val stats = m.content.o("playerStats")
    val lu = m.content.o("lineup")
    return listOf(true, false).flatMap { home ->
        lu.o(if (home) "homeTeam" else "awayTeam").a("starters").objs().mapNotNull { p ->
            val id = p.i("id") ?: return@mapNotNull null
            val opta = stats?.optJSONObject(id.toString())?.s("optaId")?.toIntOrNull() ?: return@mapNotNull null
            val pts = all[opta]?.takeIf { it.size >= 3 } ?: return@mapNotNull null
            val ax = pts.map { it.first }.average().toFloat(); val ay = pts.map { it.second }.average().toFloat()
            AvgDot(id, p.s("shirtNumber") ?: "", personName(p.s("lastName") ?: (p.s("name") ?: "").substringAfterLast(' ')),
                if (home) ax else 105f - ax, if (home) ay else 68f - ay, home)
        }
    }
}
