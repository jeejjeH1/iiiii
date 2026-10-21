package app.goallive.live.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.goallive.live.data.b
import app.goallive.live.data.d
import app.goallive.live.data.i
import app.goallive.live.data.o
import app.goallive.live.data.objs
import app.goallive.live.data.a
import app.goallive.live.data.path
import app.goallive.live.data.s
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.sin

/**
 * A broadcast-style live view rebuilt from the match data (FotMob has no ball tracking):
 * the ball sits where the pressure is (latest momentum), the attacked third glows in the attacking team's colour,
 * recent shots appear where they were taken, and the newest events scroll underneath.
 * Home attacks to the left, like the shot map.
 */
@Composable
fun LivePitch(m: MatchData) {
    val mom = m.content.path("momentum.main").a("data").objs()
    val minuteNow = if (m.finished) 95 else m.minute.substringBefore('+').filter(Char::isDigit).toIntOrNull() ?: 0
    // average of the last few minutes so the ball doesn't jump around
    val recent = mom.filter { (it.d("minute") ?: 0.0) >= minuteNow - 4 }.ifEmpty { mom.takeLast(3) }
    val pressure = if (recent.isEmpty()) 0f else (recent.sumOf { it.d("value") ?: 0.0 } / recent.size).toFloat().coerceIn(-100f, 100f)
    val homeAttacking = pressure >= 0
    val attacker = if (homeAttacking) m.homeColor else m.awayColor
    val strength = abs(pressure) / 100f
    val ballX by animateFloatAsState(0.5f - pressure / 100f * 0.38f, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessVeryLow), label = "ball")
    val t = rememberInfiniteTransition(label = "live")
    val wobble by t.animateFloat(0f, 6.283f, infiniteRepeatable(tween(2400)), label = "w")
    val pulse by t.animateFloat(0.3f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "p")
    val lastShots = m.shots.filter { (it.i("min") ?: 0) >= minuteNow - 10 }
    val newest = m.shots.maxByOrNull { (it.i("min") ?: 0) * 100 + (it.i("minAdded") ?: 0) }
    val lastGoal = m.events.lastOrNull { it.s("type") == "Goal" }
    val goalNow = m.live && lastGoal != null && minuteNow - (lastGoal.i("time") ?: -10) <= 2

    Card(pad = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (m.live) Pill(if (m.minute == "HT") "بین دو نیمه" else "زنده · ${m.minute}", PillKind.Live, dot = true) else Pill(if (m.finished) "پایان بازی" else "شروع نشده")
            Spacer(Modifier.weight(1f))
            Ltr("← ${m.homeName}", style = vz(12.sp, FontWeight.Bold), color = m.homeColor)
        }
        Box(Modifier.fillMaxWidth().aspectRatio(105f / 68f).clip(RoundedCornerShape(16.dp))) {
            Canvas(Modifier.fillMaxSize()) {
                val sx = size.width / 105f; val sy = size.height / 68f
                for (i in 0 until 12) drawRect(if (i % 2 == 0) C.pitch else C.pitch2, Offset(i * size.width / 12, 0f), Size(size.width / 12 + 1, size.height))
                // pressure glow on the third being attacked
                if (strength > 0.05f) {
                    val gx = if (homeAttacking) 0f else size.width * 2 / 3
                    drawRect(Brush.horizontalGradient(
                        if (homeAttacking) listOf(attacker.copy(alpha = 0.55f * strength * (0.6f + 0.4f * pulse)), Color.Transparent)
                        else listOf(Color.Transparent, attacker.copy(alpha = 0.55f * strength * (0.6f + 0.4f * pulse))),
                        startX = gx, endX = gx + size.width / 3), Offset(gx, 0f), Size(size.width / 3, size.height))
                }
                val c = Color.White.copy(alpha = 0.45f); val st = Stroke(1.5f)
                drawRect(c, Offset(0.5f * sx, 0.5f * sy), Size(104 * sx, 67 * sy), style = st)
                drawLine(c, Offset(52.5f * sx, 0f), Offset(52.5f * sx, size.height), 1.5f)
                drawCircle(c, 9.15f * sx, Offset(52.5f * sx, 34 * sy), style = st)
                drawRect(c, Offset(0.5f * sx, 13.85f * sy), Size(16.5f * sx, 40.3f * sy), style = st)
                drawRect(c, Offset(88 * sx, 13.85f * sy), Size(16.5f * sx, 40.3f * sy), style = st)
                drawRect(c, Offset(0.5f * sx, 24.85f * sy), Size(5.5f * sx, 18.3f * sy), style = st)
                drawRect(c, Offset(99 * sx, 24.85f * sy), Size(5.5f * sx, 18.3f * sy), style = st)
                // recent shots, the newest one pulsing
                for (s in lastShots) {
                    val h = s.i("teamId") == m.homeId
                    val x = ((if (h) 105 - (s.d("x") ?: 0.0) else (s.d("x") ?: 0.0)) * sx).toFloat()
                    val y = ((if (h) 68 - (s.d("y") ?: 0.0) else (s.d("y") ?: 0.0)) * sy).toFloat()
                    val col = if (h) m.homeColor else m.awayColor
                    val goal = s.s("eventType") == "Goal"
                    drawCircle(col.copy(alpha = if (goal) 1f else 0.6f), (if (goal) 2.4f else 1.6f) * sx, Offset(x, y))
                    drawCircle(Color.White, (if (goal) 2.4f else 1.6f) * sx, Offset(x, y), style = Stroke(1.5f))
                    if (s === newest) drawCircle(Color.White.copy(alpha = 1f - pulse * 0.7f), (2f + 4f * pulse) * sx, Offset(x, y), style = Stroke(2f))
                }
                // the ball: drifts with the pressure, bobbing slightly
                val bx = size.width * ballX + sin(wobble) * 6.dp.toPx() * (0.3f + strength)
                val by = size.height * 0.5f + sin(wobble * 2) * 10.dp.toPx() * (0.3f + strength)
                drawCircle(Color.Black.copy(alpha = 0.35f), 7.dp.toPx(), Offset(bx + 2.dp.toPx(), by + 3.dp.toPx()))
                drawCircle(Color.White, 6.dp.toPx(), Offset(bx, by))
                drawCircle(attacker, 6.dp.toPx(), Offset(bx, by), style = Stroke(2.dp.toPx()))
            }
            if (goalNow) Box(Modifier.align(Alignment.Center).clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 18.dp, vertical = 8.dp)) {
                T("گل! ${lastGoal?.s("nameStr") ?: lastGoal?.o("player")?.s("name") ?: ""}", 20, FontWeight.Black, Color.White)
            }
        }
        // who is pressing
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Crest(if (homeAttacking) m.homeId else m.awayId, 22.dp)
            T(when {
                !m.started -> "بازی هنوز شروع نشده"
                strength < 0.15f -> "بازی در میانه زمین جریان دارد"
                strength < 0.5f -> "${if (homeAttacking) m.homeName else m.awayName} توپ را در اختیار دارد"
                else -> "فشار سنگین ${if (homeAttacking) m.homeName else m.awayName}!"
            }, 14, FontWeight.ExtraBold, modifier = Modifier.weight(1f), maxLines = 1)
            T("${(strength * 100).toInt()}٪", 13, FontWeight.Black, attacker)
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp).height(8.dp).clip(RoundedCornerShape(4.dp))) {
            val home = ((pressure + 100f) / 200f).coerceIn(0.02f, 0.98f)
            Box(Modifier.weight(home).fillMaxSize().background(m.homeColor))
            Box(Modifier.weight(1f - home).fillMaxSize().background(m.awayColor))
        }
        T("نمای گرافیکی از روی داده‌های زنده (مومنتوم، شوت‌ها و رویدادها) ساخته می‌شود؛ هر ۲۰ ثانیه به‌روز می‌شود.", 11, color = C.faint,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp), align = TextAlign.Center)
    }
    // the latest moments
    val feed = m.events.filter { it.s("type") in setOf("Goal", "Card", "Substitution") }.takeLast(6).reversed()
    if (feed.isNotEmpty()) Card {
        CardTitle("آخرین اتفاق‌ها")
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
                Ltr(who ?: "", Modifier.weight(1f))
                Crest(if (e.b("isHome")) m.homeId else m.awayId, 20.dp)
            }
        }
    }
}
