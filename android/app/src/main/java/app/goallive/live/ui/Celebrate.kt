package app.goallive.live.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import app.goallive.live.data.Clubs
import app.goallive.live.data.i
import app.goallive.live.data.o
import app.goallive.live.data.s
import app.goallive.live.data.b
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** A goal that just happened while the match screen was open. */
class Goal(val teamId: Int, val team: String, val scorer: String?, val minute: String, val score: String, val color: Color)

/**
 * Watches the score of an open live match; when it goes up, returns the goal to celebrate.
 * Opening the page on a match that already has goals does not celebrate anything.
 */
@Composable
fun rememberGoal(m: MatchData): MutableState<Goal?> {
    val goal = remember(m.matchId) { mutableStateOf<Goal?>(null) }
    val last = remember(m.matchId) { intArrayOf(-1, -1) }
    val h = m.home.i("score") ?: 0
    val a = m.away.i("score") ?: 0
    LaunchedEffect(m.matchId, h, a) {
        val (ph, pa) = last[0] to last[1]
        last[0] = h; last[1] = a
        if (ph < 0 || !m.live) return@LaunchedEffect
        val home = h > ph
        if (!home && a <= pa) return@LaunchedEffect
        val ev = m.events.lastOrNull { it.s("type") == "Goal" && it.b("isHome") == home }
        val id = if (home) m.homeId else m.awayId
        val color = Clubs.get(id)?.let { Color(it.c1) } ?: if (home) m.homeColor else m.awayColor
        goal.value = Goal(
            id, teamName(id, if (home) m.homeName else m.awayName),
            ev?.let { it.o("player").s("name") ?: it.s("nameStr") },
            ev?.let { minuteText(it) } ?: m.minute.replace("'", ""),
            "$h - $a", color,
        )
    }
    return goal
}

/** Full-screen "گل!" in the scoring team's colors with confetti; goes away by itself (or on tap). */
@Composable
fun GoalCelebration(state: MutableState<Goal?>) {
    val g = state.value
    val haptic = LocalHapticFeedback.current
    val pop = remember { Animatable(0.3f) }
    val t = remember { Animatable(0f) }
    LaunchedEffect(g) {
        if (g == null) return@LaunchedEffect
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        pop.snapTo(0.3f); t.snapTo(0f)
        launch { pop.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)) }
        launch { t.animateTo(1f, tween(3200, easing = LinearEasing)) }
        delay(3600)
        state.value = null
    }
    // keep drawing the last goal while the overlay fades out
    var last by remember { mutableStateOf<Goal?>(null) }
    if (g != null) last = g
    AnimatedVisibility(g != null, enter = fadeIn(tween(150)), exit = fadeOut(tween(400))) {
        val goal = last ?: return@AnimatedVisibility
        val pieces = remember(goal) {
            val r = Random(goal.hashCode())
            List(70) { Confetto(r.nextFloat(), -0.1f - r.nextFloat() * 0.5f, 0.5f + r.nextFloat(), (r.nextFloat() - 0.5f) * 0.4f, r.nextFloat() * 360f, r.nextInt(4)) }
        }
        val ink = inkOn(goal.color)
        val palette = listOf(goal.color, Color.White, C.gold, lerpColor(goal.color, Color.White, 0.5f))
        Box(
            Modifier.fillMaxSize().background(Brush.radialGradient(listOf(goal.color.copy(alpha = 0.92f), darken(goal.color, 0.35f).copy(alpha = 0.96f))))
                .clickable(remember { MutableInteractionSource() }, null) { state.value = null }
                .semantics { liveRegion = LiveRegionMode.Assertive; contentDescription = L("گل برای {0}! {1} دقیقه {2}. نتیجه {3}", goal.team, goal.scorer ?: "", goal.minute, goal.score) },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                // light rays turning slowly behind the text
                rotate(t.value * 40f, Offset(size.width / 2, size.height * 0.42f)) {
                    for (k in 0 until 12) {
                        val ang = Math.toRadians(k * 30.0)
                        val c = Offset(size.width / 2, size.height * 0.42f)
                        val e = c + Offset(cos(ang).toFloat(), sin(ang).toFloat()) * size.height
                        drawLine(Color.White.copy(alpha = 0.07f), c, e, 46.dp.toPx())
                    }
                }
                for (p in pieces) {
                    val y = (p.y + t.value * p.speed * 1.4f) * size.height
                    if (y < -20f || y > size.height + 20f) continue
                    val x = (p.x + sin((t.value * 6 + p.x * 10).toDouble()).toFloat() * 0.03f + p.drift * t.value) * size.width
                    rotate(p.spin + t.value * 720f, Offset(x, y)) {
                        drawRect(palette[p.color], Offset(x - 5f, y - 9f), Size(10.dp.toPx() * 0.9f, 5.dp.toPx()))
                    }
                }
            }
            Column(Modifier.scale(pop.value).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Crest(goal.teamId, 96.dp, large = true)
                Spacer(Modifier.height(10.dp))
                Text(L("گل!"), color = ink, style = vz(84.sp, FontWeight.Black).copy(shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.35f), Offset(0f, 6f), 18f)))
                T(goal.team, 22, FontWeight.Black, ink, align = TextAlign.Center)
                goal.scorer?.let { Ltr(it, style = lat(22.sp, FontWeight.ExtraBold), color = ink.copy(alpha = 0.92f), align = TextAlign.Center) }
                Spacer(Modifier.height(14.dp))
                Box(Modifier.background(Color.Black.copy(alpha = 0.3f), androidx.compose.foundation.shape.RoundedCornerShape(50)).padding(horizontal = 18.dp, vertical = 6.dp)) {
                    T(L("{0}  ·  دقیقه {1}", goal.score, goal.minute), 16, FontWeight.Black, Color.White)
                }
            }
        }
    }
}

private class Confetto(val x: Float, val y: Float, val speed: Float, val drift: Float, val spin: Float, val color: Int)
