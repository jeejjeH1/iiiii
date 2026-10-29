package app.goallive.live.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.goallive.live.L
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * The VIP look: an obsidian night lit in electric cyan and violet. The page is alive (aurora light drifting, fine silk lines,
 * sparks rising, a spotlight from above); cards are dark glass with a glowing edge a light keeps travelling round; every card rises into place
 * as it appears; the bars and the home page have their own VIP pieces.
 */

/** One clock for every moving part of the look, read only while drawing, so nothing recomposes for it. */
object VipClock {
    var time: State<Float>? = null
    val t: Float get() = time?.value ?: 0f
}

/** Starts the clock (Root calls it while the look is on). */
@Composable
fun VipClockDriver() {
    VipClock.time = rememberInfiniteTransition(label = "vip").animateFloat(0f, 600f, infiniteRepeatable(tween(600_000, easing = LinearEasing)), label = "t")
}

private val G1 = Color(0xFF45E3FF)
private val G2 = Color(0xFFCFF8FF)
private val G3 = Color(0xFF8B5CF6)

private fun r(i: Int, k: Int): Float { val v = sin(i * 12.9898 + k * 78.233) * 43758.5453; return (v - kotlin.math.floor(v)).toFloat() }

/** The VIP page. */
internal fun DrawScope.vipBackdrop() {
    val w = size.width; val h = size.height; val t = VipClock.t
    drawRect(Brush.verticalGradient(listOf(Color(0xFF0B0D1E), Color(0xFF05060D), Color(0xFF030308))))
    // aurora light drifting slowly: cyan, violet, magenta
    val orbs = listOf(Triple(0.2f, 0.18f, Color(0x3345E3FF)), Triple(0.85f, 0.45f, Color(0x338B5CF6)), Triple(0.35f, 0.82f, Color(0x26FF3DCB)))
    orbs.forEachIndexed { i, (x, y, c) ->
        val cx = w * (x + 0.12f * sin(t * 0.13f + i * 2f)); val cy = h * (y + 0.06f * cos(t * 0.11f + i))
        drawCircle(Brush.radialGradient(listOf(c, Color.Transparent), Offset(cx, cy), w * 0.75f), w * 0.75f, Offset(cx, cy))
    }
    // fine silk lines across
    val step = 34.dp.toPx(); var x = -h
    while (x < w) { drawLine(Color(0x0A45E3FF), Offset(x, h), Offset(x + h * 0.6f, 0f), 1f); x += step }
    // sparks rising
    for (i in 0 until 46) {
        val sp = 14f + 30f * r(i, 1)
        val y = h + 20f - ((t * sp + r(i, 2) * h * 1.2f) % (h * 1.2f))
        val px = r(i, 3) * w + 18f * sin(t * (0.5f + r(i, 4)) + i)
        val tw = 0.5f + 0.5f * sin(t * (1.2f + 2f * r(i, 5)) + i)
        drawCircle(G2.copy(alpha = 0.1f + 0.35f * tw * r(i, 6)), 1.2.dp.toPx() + 1.6.dp.toPx() * r(i, 7), Offset(px, y))
    }
    // a spotlight from above and darker edges
    drawRect(Brush.radialGradient(listOf(Color(0x24CFF8FF), Color.Transparent), Offset(w / 2, -h * 0.08f), h * 0.55f))
    drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)), Offset(w / 2, h * 0.42f), h * 0.85f))
}

/** A VIP card's edge: cyan into violet, with a bright light travelling round it, and a soft sheen along the top inside. */
fun Modifier.vipEdge(shape: Shape): Modifier = drawWithContent {
    drawContent()
    val t = VipClock.t
    val outline = shape.createOutline(size, layoutDirection, this)
    // the bright spot runs along the diagonal and back
    val p = ((t * 0.18f) % 2f).let { if (it > 1f) 2f - it else it }
    val cx = -size.width * 0.3f + p * size.width * 1.6f
    drawOutline(outline, Brush.linearGradient(listOf(G3.copy(alpha = 0.55f), G2, G1, G3.copy(alpha = 0.55f)),
        start = Offset(cx - size.width * 0.5f, 0f), end = Offset(cx + size.width * 0.5f, size.height)), style = Stroke(1.3.dp.toPx()))
    drawRect(Brush.verticalGradient(listOf(Color(0x1ACFF8FF), Color.Transparent), 0f, 26.dp.toPx()), size = Size(size.width, 26.dp.toPx()))
}

/** Rises into place the first time it is shown: fades in, slides up a little and grows to its size. */
fun Modifier.vipEnter(): Modifier = composed {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { a.animateTo(1f, tween(560, easing = FastOutSlowInEasing)) }
    graphicsLayer {
        val v = a.value
        alpha = v; translationY = (1 - v) * 46.dp.toPx(); scaleX = 0.95f + 0.05f * v; scaleY = scaleX
    }
}

/** The VIP mark for section titles: a small glowing diamond with a glow. */
internal fun DrawScope.vipDiamond() {
    val c = center; val s = size.minDimension / 2
    drawCircle(Brush.radialGradient(listOf(Color(0x6645E3FF), Color.Transparent), c, s * 1.6f), s * 1.6f, c)
    val p = Path().apply { moveTo(c.x, c.y - s); lineTo(c.x + s * 0.72f, c.y); lineTo(c.x, c.y + s); lineTo(c.x - s * 0.72f, c.y); close() }
    drawPath(p, Brush.linearGradient(listOf(G2, G1, G3), Offset(c.x - s, c.y - s), Offset(c.x + s, c.y + s)))
}

/** A sheen sweeping across a shape every few seconds (buttons, the selected pill). */
fun Modifier.vipSheen(): Modifier = drawWithContent {
    drawContent()
    val t = VipClock.t
    val p = (t % 3.2f) / 1.1f
    if (p < 1f) {
        val x = -size.width * 0.4f + p * size.width * 1.8f
        drawRect(Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.45f), Color.Transparent),
            Offset(x - 40.dp.toPx(), 0f), Offset(x + 40.dp.toPx(), size.height)))
    }
}

/** A crown, drawn: for the VIP chip in the top bar and the home banner. */
internal fun DrawScope.crown(color: Brush) {
    val w = size.width; val h = size.height
    val p = Path().apply {
        moveTo(w * 0.08f, h * 0.82f); lineTo(w * 0.02f, h * 0.28f); lineTo(w * 0.3f, h * 0.52f); lineTo(w * 0.5f, h * 0.12f)
        lineTo(w * 0.7f, h * 0.52f); lineTo(w * 0.98f, h * 0.28f); lineTo(w * 0.92f, h * 0.82f); close()
    }
    drawPath(p, color)
    drawRect(color, Offset(w * 0.08f, h * 0.86f), Size(w * 0.84f, h * 0.1f))
}

/** The small «VIP» chip beside the app's name: a crown and the word, with a sheen passing over. */
@Composable
internal fun VipChip() {
    val shape = RoundedCornerShape(8.dp)
    Row(Modifier.clip(shape).background(Brush.linearGradient(listOf(G2, G1, G3))).vipSheen().padding(horizontal = 6.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Canvas(Modifier.size(11.dp)) { crown(Brush.linearGradient(listOf(Color(0xFF06101E), Color(0xFF0A1428)))) }
        T("VIP", 12, FontWeight.Black, Color(0xFF06101E))
    }
}

/**
 * The VIP home banner: a greeting for the time of day, today's date, and the day in numbers (matches, live now, finished),
 * on dark glass with a glowing edge, rotating rings of light behind the crown and rising sparks.
 */
@Composable
internal fun VipHero(total: Int, live: Int, done: Int) {
    val shape = RoundedCornerShape(28.dp)
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    val hello = when (hour) { in 5..11 -> L("صبح بخیر"); in 12..16 -> L("ظهر بخیر"); in 17..20 -> L("عصر بخیر"); else -> L("شب بخیر") }
    Box(Modifier.padding(top = 10.dp).fillMaxWidth().vipEnter().clip(shape)
        .background(Brush.linearGradient(listOf(Color(0xFF14183A), Color(0xFF07081A), Color(0xFF1C0F33))))
        .drawBehind {
            val t = VipClock.t
            // rings of light turning behind the crown, at the reading start
            val cx = if (app.goallive.live.Lang.en) size.width * 0.18f else size.width * 0.82f; val cy = size.height * 0.42f
            for (k in 0 until 3) {
                val a = t * (0.6f + k * 0.25f) + k * 2f
                drawArc(Brush.sweepGradient(listOf(Color.Transparent, G1.copy(alpha = 0.7f), Color.Transparent), Offset(cx, cy)),
                    a * 57.3f, 120f, false, Offset(cx - 44.dp.toPx() - k * 8.dp.toPx(), cy - 44.dp.toPx() - k * 8.dp.toPx()),
                    Size((88 + k * 16).dp.toPx(), (88 + k * 16).dp.toPx()), style = Stroke(1.5.dp.toPx()))
            }
            for (i in 0 until 18) {
                val y = size.height - ((t * (10f + 20f * r(i, 1)) + r(i, 2) * size.height) % size.height)
                drawCircle(G2.copy(alpha = 0.15f + 0.3f * r(i, 3)), 1.5.dp.toPx(), Offset(r(i, 4) * size.width, y))
            }
        }
        .vipEdge(shape).padding(20.dp)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(34.dp, 28.dp)) { crown(Brush.linearGradient(listOf(G2, G1, G3))) }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    T(hello, 22, FontWeight.Black, C.ink)
                    T(persianDate(System.currentTimeMillis(), "EEEE d MMMM"), 13, FontWeight.Bold, C.mute)
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for ((n, label, live0) in listOf(Triple(total, L("بازی امروز"), false), Triple(live, L("زنده"), true), Triple(done, L("تمام‌شده"), false))) {
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(Color(0x1445E3FF)).border(1.dp, Color(0x338B5CF6), RoundedCornerShape(16.dp))
                        .padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (live0 && n > 0) { LiveDot(size = 7.dp); Spacer(Modifier.width(5.dp)) }
                            T(fa(n), 22, FontWeight.Black, if (live0 && n > 0) C.live else C.ink)
                        }
                        T(label, 12, FontWeight.Bold, C.mute, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** A light cone falling from the bar's top edge onto the selected tab (the VIP tab bar's marker). */
internal fun DrawScope.vipSpot(cx: Float, w: Float) {
    val p = Path().apply { moveTo(cx - w * 0.12f, 0f); lineTo(cx + w * 0.12f, 0f); lineTo(cx + w * 0.5f, size.height); lineTo(cx - w * 0.5f, size.height); close() }
    drawPath(p, Brush.verticalGradient(listOf(Color(0x5545E3FF), Color.Transparent)))
    drawLine(Brush.horizontalGradient(listOf(Color.Transparent, G2, Color.Transparent), cx - w * 0.4f, cx + w * 0.4f), Offset(cx - w * 0.4f, 1f), Offset(cx + w * 0.4f, 1f), 2.dp.toPx())
}

@Suppress("unused") private val keepPi = PI

/* ---------- the VIP home: a carousel of live matches and the day as tiles ---------- */

/** Live (or, without live ones, the day's biggest) matches as large cards on a turning carousel: the side cards lean back and dim. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun VipCarousel(ms: List<app.goallive.live.data.MatchItem>) {
    if (ms.isEmpty()) return
    val nav = LocalNav.current
    val pager = androidx.compose.foundation.pager.rememberPagerState { ms.size }
    androidx.compose.foundation.pager.HorizontalPager(pager, Modifier.padding(top = 14.dp).fillMaxWidth().height(210.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 38.dp), pageSpacing = 12.dp) { i ->
        val m = ms[i]
        val shape = RoundedCornerShape(26.dp)
        val hc = app.goallive.live.data.Clubs.get(m.home.id)?.let { Color(it.c1) } ?: Color(0xFF45E3FF)
        val ac = app.goallive.live.data.Clubs.get(m.away.id)?.let { Color(it.c1) } ?: Color(0xFF8B5CF6)
        Box(Modifier.fillMaxWidth().height(200.dp).graphicsLayer {
                val off = ((pager.currentPage - i) + pager.currentPageOffsetFraction)
                val a = kotlin.math.abs(off).coerceIn(0f, 1f)
                rotationY = off * 22f; scaleX = 1f - 0.12f * a; scaleY = scaleX; alpha = 1f - 0.45f * a
                cameraDistance = 14f * density
            }.clip(shape)
            .background(Brush.linearGradient(listOf(darken(hc, 0.45f), Color(0xFF080A18), darken(ac, 0.45f))))
            .drawBehind {
                val t = VipClock.t
                drawCircle(Brush.radialGradient(listOf(hc.copy(alpha = 0.45f), Color.Transparent), Offset(size.width * 0.85f, 0f), size.width * 0.6f), size.width * 0.6f, Offset(size.width * 0.85f, 0f))
                drawCircle(Brush.radialGradient(listOf(ac.copy(alpha = 0.45f), Color.Transparent), Offset(size.width * 0.15f, size.height), size.width * 0.6f), size.width * 0.6f, Offset(size.width * 0.15f, size.height))
                // a light line scanning down the card, like a broadcast graphic
                val y = (t * 60f) % (size.height * 2f)
                if (y < size.height) drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color(0x22FFFFFF), Color.Transparent), y - 30f, y + 30f), Offset(0f, y - 30f), Size(size.width, 60f))
            }
            .vipEdge(shape)
            .clickable { nav.match(m.id) }.padding(16.dp)) {
            Column(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (m.live) { LiveDot(size = 7.dp); Spacer(Modifier.width(6.dp)) }
                    Ltr(if (m.live) statusText(m) else m.leagueName, Modifier.weight(1f), style = vz(12.sp, FontWeight.Black), color = if (m.live) C.live else C.mute)
                }
                Spacer(Modifier.weight(1f))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Crest(m.home.id, 58.dp, large = true); Spacer(Modifier.height(6.dp))
                        TeamText(m.home.id, m.home.name, size = 13, w = FontWeight.Black, color = C.ink, maxLines = 1)
                    }
                    Box(Modifier.width(110.dp), contentAlignment = Alignment.Center) {
                        if (m.hasScore) ScoreText(m.home.score, m.away.score, 34, Color.White) else T(timeOf(m.time), 28, FontWeight.Black, Color.White)
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Crest(m.away.id, 58.dp, large = true); Spacer(Modifier.height(6.dp))
                        TeamText(m.away.id, m.away.name, size = 13, w = FontWeight.Black, color = C.ink, maxLines = 1)
                    }
                }
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** One league's matches as a header chip and two tiles a row. */
@Composable
internal fun VipLeague(b: app.goallive.live.data.LeagueBlock, ms: List<app.goallive.live.data.MatchItem>, flash: Map<Int, Long>) {
    val nav = LocalNav.current
    Column(Modifier.padding(top = 18.dp).fillMaxWidth().vipEnter()) {
        Row(Modifier.clip(RoundedCornerShape(50)).background(Color(0x1445E3FF)).border(1.dp, Color(0x338B5CF6), RoundedCornerShape(50))
            .clickable { nav.league(b.primaryId) }.padding(start = 8.dp, end = 14.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(26.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color.White), contentAlignment = Alignment.Center) { LeagueLogo(b.primaryId, 18.dp) }
            Spacer(Modifier.width(8.dp))
            Ltr(b.name, style = vz(13.sp, FontWeight.Black), color = C.ink)
            val live = ms.count { it.live }
            if (live > 0) { Spacer(Modifier.width(8.dp)); LiveDot(size = 6.dp); Spacer(Modifier.width(3.dp)); T(fa(live), 12, FontWeight.Black, C.live) }
        }
        Spacer(Modifier.height(10.dp))
        ms.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { m -> VipTile(m, (flash[m.id] ?: 0) > 0, Modifier.weight(1f)) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** A match as a tile: the status on top, both crests large with the score or the time between, the names under. */
@Composable
private fun VipTile(m: app.goallive.live.data.MatchItem, flash: Boolean, modifier: Modifier) {
    val nav = LocalNav.current
    val shape = RoundedCornerShape(22.dp)
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    Column(modifier.vipEnter().clip(shape).background(if (flash) Color(0x3345E3FF) else C.card)
        .then(if (m.live) Modifier.border(1.5.dp, Brush.linearGradient(listOf(C.live, Color(0xFF8B5CF6))), shape) else Modifier.vipEdge(shape))
        .clickable { haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove); nav.match(m.id) }
        .padding(horizontal = 10.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (m.live) { LiveDot(size = 6.dp); Spacer(Modifier.width(4.dp)) }
            Ltr(if (m.started || m.cancelled) statusText(m) else persianDate(m.time, "EEEE"), style = vz(11.sp, FontWeight.Black), color = if (m.live) C.live else C.faint)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Crest(m.home.id, 34.dp)
            if (m.hasScore) ScoreText(m.home.score, m.away.score, 20) else T(timeOf(m.time), 17, FontWeight.Black)
            Crest(m.away.id, 34.dp)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TeamText(m.home.id, m.home.name, Modifier.weight(1f), 11, FontWeight.Bold, C.mute, maxLines = 1)
            TeamText(m.away.id, m.away.name, Modifier.weight(1f), 11, FontWeight.Bold, C.mute, maxLines = 1, align = androidx.compose.ui.text.style.TextAlign.End)
        }
    }
}
