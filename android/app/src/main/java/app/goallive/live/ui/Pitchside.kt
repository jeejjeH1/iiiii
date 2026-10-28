package app.goallive.live.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.goallive.live.data.i
import kotlin.math.cos
import kotlin.math.sin

/*
 * The app's football touches: a real ball (for the tab bar and pulling to refresh), the back of the bars that stay on top
 * while a list scrolls under them, and faint pitch markings.
 */

/** A football of radius [r] at [c], turned by [deg]: a white leather ball with its black pentagons and a little light on it. */
internal fun DrawScope.football(c: Offset, r: Float, deg: Float) {
    val ball = Path().apply { addOval(Rect(c, r)) }
    drawCircle(Brush.radialGradient(listOf(Color.White, Color(0xFFE6E9EE), Color(0xFFB9C0CB)), c - Offset(r * 0.35f, r * 0.4f), r * 1.6f), r, c)
    clipPath(ball) {
        rotate(deg, c) {
            val ink = Color(0xFF14171C)
            fun pent(cx: Float, cy: Float, k: Float, turn: Double) = Path().apply {
                for (i in 0 until 5) {
                    val a = Math.toRadians(turn + i * 72.0)
                    val x = cx + k * cos(a).toFloat(); val y = cy + k * sin(a).toFloat()
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }
            val k = r * 0.36f
            drawPath(pent(c.x, c.y, k, -90.0), ink)
            // the five around it, half over the edge, and the seams joining them
            for (i in 0 until 5) {
                val a = Math.toRadians(-90.0 + 36 + i * 72.0)
                val px = c.x + r * 1.02f * cos(a).toFloat(); val py = c.y + r * 1.02f * sin(a).toFloat()
                drawPath(pent(px, py, k * 0.95f, Math.toDegrees(a) + 180 + 36), ink)
                val b = Math.toRadians(-90.0 + i * 72.0)
                drawLine(ink, Offset(c.x + k * cos(b).toFloat(), c.y + k * sin(b).toFloat()), Offset(c.x + r * 0.78f * cos(b).toFloat(), c.y + r * 0.78f * sin(b).toFloat()), r * 0.09f)
            }
        }
        // the light from above
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.55f), Color.Transparent), c - Offset(r * 0.38f, r * 0.45f), r * 0.6f), r, c)
    }
    drawCircle(Color.Black.copy(alpha = 0.35f), r, c, style = Stroke(r * 0.07f))
}

/** Faint pitch markings across a bar of [size]: the halfway line, the centre circle and spot, and a box at each end. */
internal fun DrawScope.pitchLines(color: Color) {
    val w = size.width; val h = size.height; val s = 1.2.dp.toPx()
    drawLine(color, Offset(w / 2, 0f), Offset(w / 2, h), s)
    drawCircle(color, h * 0.34f, Offset(w / 2, h / 2), style = Stroke(s))
    drawCircle(color, 2.dp.toPx(), Offset(w / 2, h / 2))
    val bw = w * 0.07f; val bh = h * 0.56f
    drawRect(color, Offset(-1f, (h - bh) / 2), Size(bw, bh), style = Stroke(s))
    drawRect(color, Offset(w - bw + 1f, (h - bh) / 2), Size(bw, bh), style = Stroke(s))
}

/**
 * The back of a bar that stays on top while a list scrolls under it: the page's colour edge to edge (past the list's side
 * padding, [bleed]) and a soft fade below, so the rows slide away under it instead of showing through or meeting a hard edge.
 */
fun Modifier.stickyBack(bleed: Dp = 0.dp): Modifier = drawBehind {
    val b = bleed.toPx(); val f = 16.dp.toPx()
    drawRect(C.bg, Offset(-b, 0f), Size(size.width + 2 * b, size.height))
    drawRect(Brush.verticalGradient(listOf(C.bg.copy(alpha = 0.92f), C.bg.copy(alpha = 0f)), size.height, size.height + f), Offset(-b, size.height), Size(size.width + 2 * b, f))
}

/** Pulling a list down: a ball comes down with the finger, turning as it goes, and spins while the page refreshes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BallRefresh(state: PullToRefreshState, busy: Boolean, modifier: Modifier = Modifier) {
    val f = state.distanceFraction
    if (!busy && f <= 0f) return
    val spin by rememberInfiniteTransition(label = "spin").animateFloat(0f, 360f, infiniteRepeatable(tween(650, easing = LinearEasing)), label = "s")
    Box(
        modifier.graphicsLayer {
            val pull = if (busy) 1f else f.coerceAtMost(1.5f)
            translationY = pull * 74.dp.toPx() - 46.dp.toPx()
            alpha = if (busy) 1f else (f * 2.2f).coerceAtMost(1f)
            val s = 0.6f + 0.4f * pull.coerceAtMost(1f); scaleX = s; scaleY = s
        }.size(42.dp).shadow(10.dp, CircleShape).clip(CircleShape).background(C.cardSolid).border(1.dp, C.line2, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(28.dp)) { football(center, size.minDimension / 2, if (busy) spin else f * 320f) }
    }
}

/** What the top bar shows in its middle once a match page's own header has scrolled away. */
object TopTitle {
    var match by mutableStateOf<MatchData?>(null)
}

/** The match in a small scoreboard for the top bar: both crests and the score (or the kick-off), and the minute while live. */
@Composable
internal fun MiniScore(m: MatchData) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier.clip(shape).background(C.card).border(1.dp, if (m.live) C.live.copy(alpha = 0.5f) else C.line, shape).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Crest(m.homeId, 22.dp)
        if (m.started && !m.cancelled) ScoreText(m.home.i("score"), m.away.i("score"), 17) else T(timeOf(m.time), 15, FontWeight.Black)
        Crest(m.awayId, 22.dp)
        if (m.live) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            LiveDot(size = 6.dp)
            Ltr(m.minute, style = vz(12.sp, FontWeight.ExtraBold), color = C.live)
        }
    }
}
