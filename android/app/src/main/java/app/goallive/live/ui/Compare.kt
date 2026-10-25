package app.goallive.live.ui

import app.goallive.live.L

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.goallive.live.data.Api
import app.goallive.live.data.Clubs
import app.goallive.live.data.d
import app.goallive.live.data.i
import app.goallive.live.data.o
import app.goallive.live.data.objs
import app.goallive.live.data.a
import app.goallive.live.data.path
import app.goallive.live.data.s
import app.goallive.live.data.tr
import kotlinx.coroutines.delay
import org.json.JSONObject
import kotlin.math.cos
import kotlin.math.sin

/** One player's numbers for the comparison: percentile ranks (radar) and season totals (table). */
private class Profile(val id: Int, val d: JSONObject) {
    val name = d.s("name") ?: ""
    val teamId = d.o("primaryTeam").i("teamId") ?: 0
    val teamName = d.o("primaryTeam").s("teamName") ?: ""
    val position = tr(d.path("positionDescription.primaryPosition").s("label"))
    /** English stat title -> (percentile 0..100, shown value) */
    val pct: LinkedHashMap<String, Pair<Float, String>> = LinkedHashMap<String, Pair<Float, String>>().apply {
        d.path("firstSeasonStats.statsSection").a("items").objs().forEach { g ->
            g.a("items").objs().forEach { s ->
                val t = s.s("title") ?: return@forEach
                val p = s.d("percentileRank") ?: return@forEach
                put(t, p.toFloat() to (s.s("statValue") ?: ""))
            }
        }
    }
    val season: List<Pair<String, Double>> = d.o("mainLeague").a("stats").objs().mapNotNull { s -> s.s("title")?.let { t -> s.d("value")?.let { t to it } } }
    val league = d.o("mainLeague").s("leagueName") ?: ""
}

/** Two players side by side: a radar of percentile ranks among same-position players, and season numbers. */
@Composable
fun CompareScreen(a: Int, b0: Int) {
    var b by rememberSaveable { mutableIntStateOf(b0) }
    val la = rememberLoader(a) { Profile(a, Api.fm("playerData", mapOf("id" to a), 300_000)) }
    val lb = rememberLoader(b) { if (b > 0) Profile(b, Api.fm("playerData", mapOf("id" to b), 300_000)) else null }
    val accent = LocalAccent.current
    // second player's colour: whichever of blue / orange is further from the accent
    val other = if (accent.blue > accent.red) C.away else C.home
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = BottomSpace)) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                T(L("مقایسه بازیکنان"), 16, FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                // the two as a story picture or video
                val pa = la.value; val pb = lb.value
                if (pa != null && pb != null && b > 0) Box(Modifier.clip(RoundedCornerShape(50)).background(C.card2).border(1.dp, C.line2, RoundedCornerShape(50))
                    .clickable { StoryStudio.story = Story.Duel(pa.d, pb.d) }.padding(horizontal = 14.dp, vertical = 6.dp)) {
                    T(L("🎬 استوری"), 12, FontWeight.ExtraBold, accent)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PlayerTile(la.value, accent, Modifier.weight(1f), null)
                Box(Modifier.align(Alignment.CenterVertically)) { T("VS", 14, FontWeight.Black, C.mute) }
                PlayerTile(lb.value, other, Modifier.weight(1f)) { b = 0 }
            }
        }
        val pa = la.value
        val pb = lb.value
        if (b == 0) {
            item { SectionTitle(L("بازیکن دوم را انتخاب کنید")) }
            item { PlayerPicker { b = it } }
            return@LazyColumn
        }
        if (pa == null || pb == null) { item { Skeleton(4) }; return@LazyColumn }
        // radar axes: stats both players have, one or two from each group, at most 8
        val keys = pa.pct.keys.filter { it in pb.pct }.take(8)
        item {
            Card(Modifier.padding(top = 12.dp)) {
                CardTitle(L("رتبه صدک بین هم‌پست‌ها")) { Legend(pa.name, accent, pb.name, other) }
                if (keys.size >= 3) Radar(keys.map { tr(it) }, keys.map { pa.pct[it]!!.first }, keys.map { pb.pct[it]!!.first }, accent, other)
                else T(L("این دو بازیکن آمار صدکی مشترک ندارند (معمولاً به‌خاطر پست متفاوت، مثل دروازه‌بان و مهاجم)."), 13, color = C.mute,
                    modifier = Modifier.padding(16.dp), align = TextAlign.Center)
            }
        }
        if (keys.isNotEmpty()) item {
            Card(Modifier.padding(top = 12.dp)) {
                CardTitle(L("جزئیات"))
                keys.forEachIndexed { i, k -> CompareRow(tr(k), pa.pct[k]!!.second, pb.pct[k]!!.second, pa.pct[k]!!.first, pb.pct[k]!!.first, accent, other, i == 0) }
            }
        }
        val season = pa.season.map { it.first }.filter { t -> pb.season.any { it.first == t } }
        if (season.isNotEmpty()) item {
            Card(Modifier.padding(top = 12.dp)) {
                CardTitle(L("آمار فصل در لیگ"), sub = if (pa.league == pb.league) pa.league else null)
                season.forEachIndexed { i, t ->
                    val va = pa.season.first { it.first == t }.second; val vb = pb.season.first { it.first == t }.second
                    val f = { v: Double -> if (v % 1 != 0.0) dec(v) else "${v.toInt()}" }
                    CompareRow(tr(t), fa(f(va)), fa(f(vb)), va.toFloat(), vb.toFloat(), accent, other, i == 0)
                }
            }
        }
    }
}

@Composable
private fun Legend(a: String, ca: Color, b: String, cb: Color) {
    Column(horizontalAlignment = Alignment.End) {
        for ((n, c) in listOf(a to ca, b to cb)) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(c)); Ltr(n.substringAfterLast(' '), style = vz(11.sp, FontWeight.Bold), color = C.mute)
        }
    }
}

@Composable
private fun PlayerTile(p: Profile?, color: Color, modifier: Modifier, onChange: (() -> Unit)?) {
    val nav = LocalNav.current
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier.clip(shape).background(C.card).border(2.dp, color.copy(alpha = if (p != null) 0.8f else 0.25f), shape)
            .clickable(enabled = p != null) { p?.let { nav.player(it.id) } }.padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (p == null) {
            Box(Modifier.size(64.dp).clip(RoundedCornerShape(50)).background(C.card2), contentAlignment = Alignment.Center) { Icon(Ic.people, null, Modifier.size(30.dp), tint = C.faint) }
            T(L("؟"), 15, FontWeight.Black, C.mute)
            return@Column
        }
        Face(p.id, 64.dp, color, 3.dp)
        Ltr(p.name, style = lat(16.sp, FontWeight.ExtraBold), align = TextAlign.Center, maxLines = 2)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Crest(p.teamId, 16.dp); T(Clubs.get(p.teamId)?.fa ?: p.teamName, 11, color = C.mute, maxLines = 1)
        }
        if (p.position.isNotEmpty()) T(p.position, 11, FontWeight.Bold, color)
        if (onChange != null) Pill(L("تغییر"), onClick = onChange)
    }
}

@Composable
private fun PlayerPicker(onPick: (Int) -> Unit) {
    var q by remember { mutableStateOf("") }
    var res by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    LaunchedEffect(q) {
        if (q.trim().length < 2) { res = emptyList(); return@LaunchedEffect }
        delay(300)
        res = runCatching { search(q.trim()).players.filter { !it.optBoolean("isCoach") } }.getOrDefault(emptyList()).take(8)
    }
    Column {
        SearchField(q, L("نام بازیکن (انگلیسی یا فارسی)…"), autoFocus = true) { q = it }
        if (res.isNotEmpty()) Card {
            res.forEachIndexed { i, p ->
                val id = p.s("id")?.toIntOrNull() ?: 0
                PersonRow(id, p.s("name") ?: "", p.s("teamName") ?: "", first = i == 0) { onPick(id) }
            }
        }
    }
}

@Composable
private fun Radar(labels: List<String>, a: List<Float>, b: List<Float>, ca: Color, cb: Color) {
    val grow = remember { Animatable(0f) }
    LaunchedEffect(a, b) { grow.snapTo(0f); grow.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
    val n = labels.size
    Box(Modifier.fillMaxWidth().aspectRatio(1f).padding(8.dp)) {
        Canvas(Modifier.fillMaxSize().padding(56.dp)) {
            val c = Offset(size.width / 2, size.height / 2)
            val r = size.minDimension / 2
            fun pt(i: Int, f: Float): Offset {
                val ang = -Math.PI / 2 + 2 * Math.PI * i / n
                return Offset(c.x + (cos(ang) * r * f).toFloat(), c.y + (sin(ang) * r * f).toFloat())
            }
            // grid rings and spokes
            for (level in 1..4) {
                val p = Path().apply { for (i in 0 until n) { val q = pt(i, level / 4f); if (i == 0) moveTo(q.x, q.y) else lineTo(q.x, q.y) }; close() }
                drawPath(p, C.line2, style = Stroke(1.dp.toPx()))
            }
            for (i in 0 until n) drawLine(C.line, c, pt(i, 1f), 1.dp.toPx())
            for ((vals, col) in listOf(b to cb, a to ca)) {
                val p = Path().apply { vals.forEachIndexed { i, v -> val q = pt(i, (v / 100f).coerceIn(0.02f, 1f) * grow.value); if (i == 0) moveTo(q.x, q.y) else lineTo(q.x, q.y) }; close() }
                drawPath(p, col.copy(alpha = 0.28f))
                drawPath(p, col, style = Stroke(2.dp.toPx()))
                vals.forEachIndexed { i, v -> drawCircle(col, 3.dp.toPx(), pt(i, (v / 100f).coerceIn(0.02f, 1f) * grow.value)) }
            }
        }
        // axis labels around the chart
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
            val w = maxWidth; val h = maxHeight
            labels.forEachIndexed { i, l ->
                val ang = -Math.PI / 2 + 2 * Math.PI * i / n
                val x = 0.5f + (cos(ang) * 0.43).toFloat(); val y = 0.5f + (sin(ang) * 0.43).toFloat()
                Box(Modifier.align(androidx.compose.ui.AbsoluteAlignment.TopLeft)
                    .absoluteOffset(w * x - 50.dp, h * y - 14.dp).width(100.dp), contentAlignment = Alignment.Center) {
                    T(l, 10, FontWeight.Bold, C.mute, maxLines = 2, align = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun CompareRow(title: String, va: String, vb: String, na: Float, nb: Float, ca: Color, cb: Color, first: Boolean) {
    if (!first) Divider()
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Value(va, na > nb, ca)
        T(title, 13, FontWeight.SemiBold, modifier = Modifier.weight(1f), align = TextAlign.Center, maxLines = 2)
        Value(vb, nb > na, cb)
    }
}

@Composable
private fun Value(v: String, win: Boolean, c: Color) {
    Box(Modifier.width(72.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.clip(RoundedCornerShape(9.dp)).background(if (win) c else Color.Transparent).padding(horizontal = 9.dp, vertical = 3.dp)) {
            Ltr(fa(v), style = vz(13.sp, FontWeight.Black), color = if (win) inkOn(c) else C.ink)
        }
    }
}
