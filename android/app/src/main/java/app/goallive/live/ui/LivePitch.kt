package app.goallive.live.ui

import app.goallive.live.L

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.goallive.live.data.a
import app.goallive.live.data.b
import app.goallive.live.data.d
import app.goallive.live.data.i
import app.goallive.live.data.o
import app.goallive.live.data.objs
import app.goallive.live.data.path
import app.goallive.live.data.s
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 * "زمین زنده": the match as a TV camera on the main stand would see it, drawn in perspective from FotMob's live data.
 * FotMob gives no ball tracking, so the picture is built from what it does give: the momentum of the last minutes moves both
 * line-ups (in their real formation) and the ball towards the side that is pressing, the attacked third glows, every recent shot
 * sits where it was taken with its flight to the goal, and the newest event pops up over the pitch.
 * Screen-world coordinates: X 0…105 from the left goal to the right one, Y 0…68 from the far touchline to the near one.
 * The home side defends the right goal and attacks to the left (home is on the right in the Persian layout).
 */

/** The TV camera: the far touchline narrower and higher than the near one. */
private class Cam(val w: Float, val h: Float) {
    val nearW = w * 1.04f; val farW = w * 0.56f
    val nearY = h * 0.985f; val farY = h * 0.25f
    private val zFar = nearW / farW
    private val sFar = 1f / zFar
    fun s(y: Float): Float = 1f / (zFar + (1 - zFar) * (y / 68f))
    /** A point on (or [up] metres above) the pitch. */
    fun at(x: Float, y: Float, up: Float = 0f): Offset {
        val s = s(y)
        val sy = farY + (nearY - farY) * (s - sFar) / (1 - sFar)
        return Offset(w / 2 + (x - 52.5f) / 105f * nearW * s, sy - up / 105f * nearW * s)
    }
    /** Pixels per metre at that depth. */
    fun ppm(y: Float) = nearW / 105f * s(y)
}

private fun DrawScope.quad(c: Cam, x0: Float, y0: Float, x1: Float, y1: Float, brush: Brush) {
    val p = Path().apply {
        val a = c.at(x0, y0); moveTo(a.x, a.y)
        c.at(x1, y0).let { lineTo(it.x, it.y) }; c.at(x1, y1).let { lineTo(it.x, it.y) }; c.at(x0, y1).let { lineTo(it.x, it.y) }; close()
    }
    drawPath(p, brush)
}

private fun DrawScope.line3(c: Cam, pts: List<Pair<Float, Float>>, color: Color, close: Boolean = false) {
    val p = Path()
    pts.forEachIndexed { i, (x, y) -> val o = c.at(x, y); if (i == 0) p.moveTo(o.x, o.y) else p.lineTo(o.x, o.y) }
    if (close) p.close()
    drawPath(p, color, style = Stroke(max(1.2f, c.ppm(34f) * 0.16f), cap = StrokeCap.Round))
}

private fun arc(cx: Float, cy: Float, r: Float, from: Double, to: Double, n: Int = 40) =
    (0..n).map { k -> val a = from + (to - from) * k / n; (cx + r * cos(a)).toFloat() to (cy + r * sin(a)).toFloat() }

/** Two kits too alike to tell apart on the pitch: the away side wears white (or black on a white home kit). */
private fun kits(home0: Color, away0: Color): Pair<Color, Color> {
    // a green kit disappears on the grass: such a side plays in white
    fun onGrass(c: Color) = if (c.green > c.red + 0.08f && c.green > c.blue + 0.05f) Color.White else c
    val home = onGrass(home0); val away = onGrass(away0)
    val d = abs(home.red - away.red) + abs(home.green - away.green) + abs(home.blue - away.blue)
    return if (d > 0.45f) home to away else home to (if (home.luminance() > 0.7f) Color(0xFF15171C) else Color.White)
}

private class PitchToken(val x: Float, val y: Float, val num: String, val home: Boolean, val keeper: Boolean)

@Composable
fun LivePitch(m: MatchData) {
    val mom = momentumOf(m)
    val minuteNow = if (m.finished) 95 else m.minute.substringBefore('+').filter(Char::isDigit).toIntOrNull() ?: if (m.minute == "HT") 45 else 0
    // the pressure of the last few minutes, so nothing jumps with every single point
    val recent = mom.filter { (it.d("minute") ?: 0.0) >= minuteNow - 4 }.ifEmpty { mom.takeLast(3) }
    val pressure = if (recent.isEmpty()) 0f else (recent.sumOf { it.d("value") ?: 0.0 } / recent.size).toFloat().coerceIn(-100f, 100f)
    val homePress = pressure >= 0
    val strength = abs(pressure) / 100f
    val (homeKit, awayKit) = kits(m.homeColor, m.awayColor)
    val attacker = if (homePress) homeKit else awayKit
    val shots = m.shots
    val shotMin = { s: JSONObject -> (s.i("min") ?: 0) + (s.i("minAdded") ?: 0) / 100f }
    val newest = shots.maxByOrNull(shotMin)
    val recentShots = if (m.finished) shots.sortedBy(shotMin).takeLast(10) else shots.filter { s -> (s.i("min") ?: 0) >= minuteNow - 15 }
    val freshShot = newest?.takeIf { m.live && minuteNow - (it.i("min") ?: -9) <= 1 }
    val lastEvent = m.events.lastOrNull { it.s("type") in setOf("Goal", "Card") }?.takeIf { m.live && minuteNow - (it.i("time") ?: -9) <= 3 }

    // both line-ups in their formation, pushed up the pitch by the side that is pressing
    val shift = -pressure / 100f * 16f
    val tokens = remember(m.d, shift) {
        val lu = m.content.path("lineup")
        fun side(team: String, home: Boolean) = lu.o(team).a("starters").objs().map { p ->
            val hl = p.o("horizontalLayout")
            val fx = (hl.d("x") ?: 0.5).toFloat(); val fy = (hl.d("y") ?: 0.5).toFloat()
            val keeper = (p.i("positionId") ?: 0) == 11 || fx < 0.15f
            val depth = 1.5f + fx * 52f + if (keeper) 0f else (if (home) -shift else shift)
            PitchToken(if (home) 105f - depth else depth, (if (home) 1 - fy else fy) * 60f + 4f, p.s("shirtNumber") ?: "", home, keeper)
        }
        side("homeTeam", true) + side("awayTeam", false)
    }
    // where the ball is: at a shot that has just been taken, otherwise in front of the pressing side
    val ballTargetX = freshShot?.let { s -> val x = (s.d("x") ?: 52.0).toFloat(); if (s.i("teamId") == m.homeId) 105f - x else x } ?: (52.5f - pressure / 100f * 30f)
    val ballTargetY = freshShot?.let { s -> val y = (s.d("y") ?: 34.0).toFloat(); if (s.i("teamId") == m.homeId) 68f - y else y } ?: 34f
    val bx by animateFloatAsState(ballTargetX, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessVeryLow), label = "bx")
    val by by animateFloatAsState(ballTargetY, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessVeryLow), label = "by")
    // a clock for the gentle movement of everything on the pitch
    val time by produceState(0f) { val start = withFrameMillis { it }; while (true) withFrameMillis { value = (it - start) / 1000f } }
    val tm = rememberTextMeasurer()
    val numStyle = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Black, color = Color.White)
    val crowd = remember { val r = java.util.Random(4); List(1600) { floatArrayOf(r.nextFloat(), r.nextFloat(), r.nextFloat(), r.nextFloat()) } }

    Card(pad = PaddingValues(0.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.32f).clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))) {
            Canvas(Modifier.fillMaxSize()) {
                val c = Cam(size.width, size.height)
                val pulse = 0.5f + 0.5f * sin(time * 3.2f)
                // the stand behind the far touchline: dark tiers and a crowd in both colours under the floodlights
                drawRect(Brush.verticalGradient(listOf(Color(0xFF070B10), Color(0xFF111A22), Color(0xFF0B1418)), endY = c.farY), size = Size(size.width, c.farY + 4))
                for (p in crowd) {  // tiers of fans, denser and larger towards the pitch
                    val yy = p[1] * (c.farY - 10f) + 2f
                    val col = when { p[2] < 0.35f -> homeKit; p[2] < 0.7f -> awayKit; else -> Color.White }
                    drawCircle(col.copy(alpha = 0.18f + 0.35f * p[3] * (yy / c.farY)), 1.1f + 1.4f * (yy / c.farY), Offset(p[0] * size.width, yy))
                }
                for (fx in listOf(0.12f, 0.88f)) drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.32f), Color.Transparent), Offset(size.width * fx, 0f), size.width * 0.32f),
                    size.width * 0.32f, Offset(size.width * fx, 0f))
                // the advertising boards along the far touchline
                quad(c, -6f, -3.4f, 111f, -2.6f, Brush.horizontalGradient(listOf(Color(0xFF0D2B1A), Color(0xFF1C6B3C), Color(0xFF0D2B1A))))
                // grass: the run-off, then the mowing stripes
                quad(c, -6f, -2.6f, 111f, 72f, Brush.verticalGradient(listOf(Color(0xFF1C6334), Color(0xFF1F7038))))
                for (k in 0 until 15) {
                    val col = if (k % 2 == 0) Color(0xFF2B8A49) else Color(0xFF268043)
                    quad(c, k * 7f, 0f, (k + 1) * 7f, 68f, Brush.verticalGradient(listOf(col.copy(alpha = 0.9f), col), startY = c.farY, endY = c.nearY))
                }
                // the third being attacked glows in the attacker's colour
                if (strength > 0.08f && m.started) {
                    val (x0, x1) = if (homePress) 0f to 35f else 70f to 105f
                    val a = 0.5f * strength * (0.55f + 0.45f * pulse)
                    val ends = if (homePress) listOf(attacker.copy(alpha = a), Color.Transparent) else listOf(Color.Transparent, attacker.copy(alpha = a))
                    quad(c, x0, 0f, x1, 68f, Brush.horizontalGradient(ends, startX = c.at(x0, 68f).x, endX = c.at(x1, 68f).x))
                }
                // the markings
                val lc = Color.White.copy(alpha = 0.82f)
                line3(c, listOf(0f to 0f, 105f to 0f, 105f to 68f, 0f to 68f), lc, close = true)
                line3(c, listOf(52.5f to 0f, 52.5f to 68f), lc)
                line3(c, arc(52.5f, 34f, 9.15f, 0.0, 2 * PI, 60), lc)
                for (left in listOf(true, false)) {
                    val g = if (left) 0f else 105f; val d = if (left) 1f else -1f
                    line3(c, listOf(g to 13.84f, g + d * 16.5f to 13.84f, g + d * 16.5f to 54.16f, g to 54.16f), lc)
                    line3(c, listOf(g to 24.84f, g + d * 5.5f to 24.84f, g + d * 5.5f to 43.16f, g to 43.16f), lc)
                    val pen = g + d * 11f
                    val off = kotlin.math.acos(5.5 / 9.15)
                    line3(c, if (left) arc(pen, 34f, 9.15f, -off, off) else arc(pen, 34f, 9.15f, PI - off, PI + off), lc)
                    drawCircle(lc, c.ppm(34f) * 0.25f, c.at(pen, 34f))
                }
                drawCircle(lc, c.ppm(34f) * 0.3f, c.at(52.5f, 34f))
                // a soft light from the stand side and darker edges
                drawRect(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.07f), Color.Transparent), Offset(size.width / 2, size.height * 0.45f), size.width * 0.7f))

                // the goals, back first so the posts stand in front of their nets
                for (left in listOf(true, false)) {
                    val g = if (left) 0f else 105f; val back = if (left) -2.2f else 107.2f
                    val net = Color.White.copy(alpha = 0.3f); val sw = max(1f, c.ppm(34f) * 0.05f)
                    for (k in 0..8) { val y = 30.34f + 7.32f * k / 8; drawLine(net, c.at(back, y, 0f), c.at(back, y, 2.0f), sw) ; drawLine(net, c.at(g, y, 2.44f), c.at(back, y, 2.0f), sw) }
                    for (k in 0..4) { val up = 2.0f * k / 4; drawLine(net, c.at(back, 30.34f, up), c.at(back, 37.66f, up), sw) }
                    for (y in listOf(30.34f, 37.66f)) for (k in 0..4) drawLine(net, c.at(g, y, 2.44f * k / 4), c.at(back, y, 2.0f * k / 4), sw)
                    val post = Color.White; val pw = max(2f, c.ppm(34f) * 0.14f)
                    drawLine(post, c.at(g, 30.34f), c.at(g, 30.34f, 2.44f), pw, StrokeCap.Round)
                    drawLine(post, c.at(g, 37.66f), c.at(g, 37.66f, 2.44f), pw, StrokeCap.Round)
                    drawLine(post, c.at(g, 30.34f, 2.44f), c.at(g, 37.66f, 2.44f), pw, StrokeCap.Round)
                }

                // recent shots where they were taken, each with its flight to the goal; the newest drawn as it flies
                for (s in recentShots) {
                    val home = s.i("teamId") == m.homeId
                    val sx = (s.d("x") ?: 0.0).toFloat(); val sy = (s.d("y") ?: 34.0).toFloat()
                    val x = if (home) 105f - sx else sx; val y = if (home) 68f - sy else sy
                    val goal = s.s("eventType") == "Goal"
                    val col = if (home) homeKit else awayKit
                    val gx = if (home) 0f else 105f
                    val ex: Float; val ey: Float; val ez: Float
                    if (s.b("isBlocked") && s.d("blockedX") != null) {
                        val bx0 = s.d("blockedX")!!.toFloat(); val by0 = (s.d("blockedY") ?: sy.toDouble()).toFloat()
                        ex = if (home) 105f - bx0 else bx0; ey = if (home) 68f - by0 else by0; ez = 0.4f
                    } else {
                        val cy0 = (s.d("goalCrossedY") ?: 34.0).toFloat(); ex = gx; ey = if (home) 68f - cy0 else cy0
                        ez = ((s.d("goalCrossedZ") ?: 0.8).toFloat()).coerceIn(0f, 4f)
                    }
                    val isNew = s === newest
                    val prog = if (isNew && m.live) ((time % 2.6f) / 1.6f).coerceAtMost(1f) else 1f
                    val dist = abs(ex - x) + abs(ey - y)
                    val peak = max(ez, 0.4f) + dist * 0.06f
                    val path = Path(); val n = 26
                    for (k in 0..(n * prog).toInt()) {
                        val t = k / n.toFloat()
                        val px = x + (ex - x) * t; val py = y + (ey - y) * t
                        val up = (1 - t) * (1 - t) * 0.2f + 2 * (1 - t) * t * peak + t * t * ez
                        val o = c.at(px, py, up); if (k == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
                    }
                    val alpha = if (isNew || goal) 0.95f else 0.38f
                    drawPath(path, (if (goal) C.gold else Color.White).copy(alpha = alpha),
                        style = Stroke(if (isNew || goal) 3.2f else 1.6f, cap = StrokeCap.Round, pathEffect = if (goal || isNew) null else PathEffect.dashPathEffect(floatArrayOf(6f, 6f))))
                    val o = c.at(x, y); val r = c.ppm(y) * (if (goal) 1.25f else 0.85f)
                    drawOval(Color.Black.copy(alpha = 0.3f), Offset(o.x - r, o.y - r * 0.35f), Size(r * 2, r * 0.9f))
                    drawOval(col.copy(alpha = if (goal) 1f else 0.85f), Offset(o.x - r, o.y - r * 0.55f), Size(r * 2, r * 1.1f))
                    drawOval(if (goal) C.gold else Color.White, Offset(o.x - r, o.y - r * 0.55f), Size(r * 2, r * 1.1f), style = Stroke(1.6f))
                    if (goal) drawCircle(C.gold.copy(alpha = 0.35f * pulse), c.ppm(34f) * 4f, c.at(gx, 34f, 1.2f))
                }

                // the players, far ones first, each bobbing a little around their spot
                for ((idx, p) in tokens.sortedBy { it.y }.withIndex()) {
                    val ph = idx * 1.7f
                    val x = p.x + sin(time * 0.9f + ph) * 1.3f; val y = p.y + cos(time * 0.7f + ph * 1.3f) * 1.1f
                    val o = c.at(x, y); val ppm = c.ppm(y); val r = ppm * 1.6f
                    val kit = if (p.keeper) (if (p.home) Color(0xFFFFB020) else Color(0xFF8B5CF6)) else if (p.home) homeKit else awayKit
                    drawOval(Color.Black.copy(alpha = 0.32f), Offset(o.x - r * 1.1f, o.y - r * 0.3f), Size(r * 2.2f, r * 0.75f))
                    val head = c.at(x, y, 2.2f)
                    drawLine(kit.copy(alpha = 0.85f), o, head, r * 0.75f, StrokeCap.Round)
                    drawCircle(kit, r, head)
                    drawCircle(Color.White.copy(alpha = 0.9f), r, head, style = Stroke(max(1.2f, r * 0.14f)))
                    if (r > 5f && p.num.isNotEmpty()) {
                        val tl = tm.measure(p.num, numStyle.copy(color = if (kit.luminance() > 0.6f) Color(0xFF111111) else Color.White))
                        val k = (r * 1.3f) / tl.size.height
                        scale(k, k, head) { drawText(tl, topLeft = Offset(head.x - tl.size.width / 2f, head.y - tl.size.height / 2f)) }
                    }
                }

                // the ball, bouncing gently, with its shadow on the grass
                val hop = abs(sin(time * 2.6f)) * (0.6f + strength * 1.4f)
                val bxx = bx + sin(time * 0.8f) * 1.5f; val byy = by + cos(time * 0.6f) * 1.2f
                val ground = c.at(bxx, byy); val ball = c.at(bxx, byy, 0.35f + hop); val br = max(6f, c.ppm(byy) * 0.95f)
                drawOval(Color.Black.copy(alpha = 0.4f - hop * 0.08f), Offset(ground.x - br, ground.y - br * 0.3f), Size(br * 2, br * 0.6f))
                drawCircle(Brush.radialGradient(listOf(Color.White, Color(0xFFDADDE2)), Offset(ball.x - br * 0.3f, ball.y - br * 0.3f), br * 1.4f), br, ball)
                drawCircle(Color(0xFF1B1E24), br * 0.36f, ball)
                drawCircle(attacker.copy(alpha = 0.5f + 0.4f * pulse), br * (1.6f + pulse * 0.6f), ball, style = Stroke(2f))

                // edges darkened like a camera lens
                drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f)), Offset(size.width / 2, size.height * 0.55f), size.width * 0.85f))
            }

            // the TV score bug
            Row(Modifier.align(Alignment.TopStart).padding(10.dp).clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(alpha = 0.62f))
                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(14.dp)).padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(4.dp, 18.dp).clip(RoundedCornerShape(2.dp)).background(homeKit))
                Crest(m.homeId, 18.dp)
                T(fa("${m.home.i("score") ?: 0}"), 15, FontWeight.Black, Color.White)
                T("-", 13, FontWeight.Black, Color.White.copy(alpha = 0.6f))
                T(fa("${m.away.i("score") ?: 0}"), 15, FontWeight.Black, Color.White)
                Crest(m.awayId, 18.dp)
                Box(Modifier.size(4.dp, 18.dp).clip(RoundedCornerShape(2.dp)).background(awayKit))
                Box(Modifier.clip(RoundedCornerShape(6.dp)).background(if (m.live) C.live else Color.White.copy(alpha = 0.15f)).padding(horizontal = 6.dp, vertical = 1.dp)) {
                    T(when { m.live && m.minute == "HT" -> L("بین دو نیمه"); m.live -> fa(m.minute); m.finished -> L("پایان"); else -> L("شروع نشده") }, 12, FontWeight.Black, Color.White)
                }
            }
            // the newest goal or card, over the pitch
            EventBanner(m, lastEvent, Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp))
        }
        // who has the upper hand right now
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Crest(if (homePress) m.homeId else m.awayId, 22.dp)
                T(when {
                    !m.started -> L("بازی هنوز شروع نشده")
                    strength < 0.15f -> L("بازی در میانه زمین جریان دارد")
                    strength < 0.5f -> L("{0} توپ را در اختیار دارد", teamName(if (homePress) m.homeId else m.awayId, if (homePress) m.homeName else m.awayName))
                    else -> L("فشار سنگین {0}!", teamName(if (homePress) m.homeId else m.awayId, if (homePress) m.homeName else m.awayName))
                }, 14, FontWeight.ExtraBold, modifier = Modifier.weight(1f), maxLines = 1)
                if (m.started) T(L("{0}٪", fa((strength * 100).toInt())), 13, FontWeight.Black, attacker)
            }
            // the last twenty minutes of momentum, the newest on the reading-start side
            val last = mom.filter { (it.d("minute") ?: 0.0) > minuteNow - 20 }
            if (last.size > 3) Canvas(Modifier.fillMaxWidth().height(44.dp)) {
                val n = last.size; val bw = size.width / n
                drawLine(Color.White.copy(alpha = 0.2f), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1f)
                last.forEachIndexed { k, p ->
                    val v = (p.d("value") ?: 0.0).toFloat() / 100f
                    val x = size.width - (k + 1) * bw
                    val hh = abs(v) * size.height / 2
                    drawRoundRect(if (v >= 0) homeKit else awayKit, Offset(x + bw * 0.15f, if (v >= 0) size.height / 2 - hh else size.height / 2), Size(bw * 0.7f, max(1f, hh)),
                        androidx.compose.ui.geometry.CornerRadius(2f))
                }
            }
            Row(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))) {
                val home = ((pressure + 100f) / 200f).coerceIn(0.02f, 0.98f)
                Box(Modifier.weight(home).fillMaxHeight().background(homeKit))
                Box(Modifier.weight(1f - home).fillMaxHeight().background(awayKit))
            }
        }
    }
    // the latest moments
    val feed = m.events.filter { it.s("type") in setOf("Goal", "Card", "Substitution") }.takeLast(6).reversed()
    if (feed.isNotEmpty()) Card {
        CardTitle(L("آخرین اتفاق‌ها"))
        feed.forEachIndexed { i, e ->
            ListRow(first = i == 0) {
                Box(Modifier.width(44.dp).clip(RoundedCornerShape(50)).background(if (e.s("type") == "Goal") LocalAccent.current else C.card2).padding(vertical = 3.dp),
                    contentAlignment = Alignment.Center) {
                    Ltr(minuteText(e) + "'", style = vz(11.sp, FontWeight.ExtraBold), color = if (e.s("type") == "Goal") LocalAccentInk.current else C.mute)
                }
                when (e.s("type")) {
                    "Goal" -> Icon(Ic.ball, null, Modifier.size(18.dp), tint = LocalAccent.current)
                    "Card" -> Box(Modifier.size(11.dp, 15.dp).clip(RoundedCornerShape(2.dp)).background(if (e.s("card") == "Yellow") C.gold else C.live))
                    else -> Icon(Ic.swap, null, Modifier.size(16.dp), tint = LocalAccent.current)
                }
                val who = if (e.s("type") == "Substitution") e.a("swap").objs().firstOrNull()?.s("name") else e.s("nameStr") ?: e.o("player").s("name")
                T(personName(who ?: ""), 14, modifier = Modifier.weight(1f), maxLines = 1)
                Crest(if (e.b("isHome")) m.homeId else m.awayId, 20.dp)
            }
        }
    }
}

/** The newest goal or card, popping up over the pitch. */
@Composable
private fun EventBanner(m: MatchData, e0: JSONObject?, modifier: Modifier) {
    AnimatedVisibility(e0 != null, modifier, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
        val e = e0 ?: return@AnimatedVisibility
        val goal = e.s("type") == "Goal"
        Row(Modifier.clip(RoundedCornerShape(50)).background(if (goal) C.gold else Color.Black.copy(alpha = 0.7f)).padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (goal) Icon(Ic.ball, null, Modifier.size(18.dp), tint = Color(0xFF15171C))
            else Box(Modifier.size(11.dp, 15.dp).clip(RoundedCornerShape(2.dp)).background(if (e.s("card") == "Yellow") C.gold else C.live))
            val who = personName(e.s("nameStr") ?: e.o("player").s("name") ?: "")
            T((if (goal) L("گل! {0}", who) else who) + "  " + fa(minuteText(e) + "'"), 14, FontWeight.Black, if (goal) Color(0xFF15171C) else Color.White, maxLines = 1)
            Crest(if (e.b("isHome")) m.homeId else m.awayId, 18.dp)
        }
    }
}
