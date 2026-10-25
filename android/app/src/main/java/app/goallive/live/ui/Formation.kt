package app.goallive.live.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.goallive.live.L

/** One player on a formation pitch; [x], [y] are screen fractions (left to right, top to bottom; attack is up). */
class PitchMan(
    val id: Int, val name: String, val x: Float, val y: Float,
    val number: String = "", val rating: Double? = null,
    val goals: Int = 0, val own: Int = 0, val assists: Int = 0, val yellow: Boolean = false, val red: Boolean = false,
    val outAt: String? = null, val captain: Boolean = false, val crest: Int? = null, val star: Boolean = false, val color: Color? = null,
)

private val GOLDEN = Color(0xFFF5C542)

/**
 * A formation on a full pitch, broadcast-graphics style: mown stripes under a soft light, crisp markings with goals and nets,
 * the team's colour glowing from its own end, a header strip and an optional footer. Each player is a photo in a ring of
 * the team colour with his number, rating and captaincy on it, a name plate and what he did in the match.
 */
@Composable
fun FormationPitch(
    players: List<PitchMan>, color: Color,
    header: @Composable RowScope.() -> Unit,
    footer: (@Composable () -> Unit)? = null,
    onTap: (PitchMan) -> Unit,
) {
    val ring = lerp(color, Color.White, 0.15f)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp))
            .background(Brush.verticalGradient(listOf(lerp(color, Color.Black, 0.55f), Color(0xFF0B1A12))))
            .border(1.5.dp, Brush.verticalGradient(listOf(ring.copy(alpha = 0.8f), Color.White.copy(alpha = 0.08f))), RoundedCornerShape(26.dp)),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp), content = header)
        Box(Modifier.padding(horizontal = 8.dp).fillMaxWidth().aspectRatio(0.7f).clip(RoundedCornerShape(20.dp))) {
            Canvas(Modifier.fillMaxSize()) { grass(color) }
            // pitch spots are screen positions: left stays left in both languages
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val w = maxWidth; val h = maxHeight
                    val tokenW = 84.dp
                    for (p in players.sortedBy { it.y }) {
                        Token(p, p.color ?: color, Modifier.align(Alignment.TopStart)
                            .absoluteOffset(w * p.x - tokenW / 2, h * p.y - 34.dp).width(tokenW)) { onTap(p) }
                    }
                }
            }
        }
        if (footer != null) Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) { footer() }
        else Spacer(Modifier.height(8.dp))
    }
}

/** The grass and its markings, attack upwards. */
private fun DrawScope.grass(team: Color) {
    val w = size.width; val h = size.height
    drawRect(Brush.verticalGradient(listOf(Color(0xFF237F47), Color(0xFF1B6B3B), Color(0xFF155A31))))
    // mown stripes, wider towards the camera
    val bands = 12
    for (k in 0 until bands) if (k % 2 == 0) drawRect(Color.White.copy(alpha = 0.045f), Offset(0f, h * k / bands), Size(w, h / bands))
    // floodlight sheen from the top corner and the team's colour from its own end
    drawRect(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.13f), Color.Transparent), Offset(0f, 0f), Offset(w * 0.7f, h * 0.45f)))
    drawRect(Brush.radialGradient(listOf(team.copy(alpha = 0.28f), Color.Transparent), Offset(w / 2, h * 1.05f), w * 0.9f))
    // markings: 68 m across, 105 m long
    val mx = w * 0.05f; val my = h * 0.035f
    val pw = w - 2 * mx; val ph = h - 2 * my
    val sx = pw / 68f; val sy = ph / 105f
    fun X(m: Float) = mx + m * sx
    fun Y(m: Float) = my + m * sy
    val glow = Color.White.copy(alpha = 0.07f); val line = Color.White.copy(alpha = 0.62f)
    val lw = 1.6.dp.toPx()
    for ((c, wd) in listOf(glow to lw * 4, line to lw)) {
        val st = Stroke(wd)
        drawRoundRect(c, Offset(mx, my), Size(pw, ph), CornerRadius(3.dp.toPx()), style = st)
        drawLine(c, Offset(mx, Y(52.5f)), Offset(mx + pw, Y(52.5f)), wd)
        drawCircle(c, 9.15f * sx, Offset(X(34f), Y(52.5f)), style = st)
        for (top in listOf(true, false)) {
            val g = if (top) 0f else 105f; val d = if (top) 1f else -1f
            fun box(depth: Float, half: Float) {
                val y0 = Y(g); val y1 = Y(g + d * depth)
                drawRect(c, Offset(X(34f - half), minOf(y0, y1)), Size(2 * half * sx, kotlin.math.abs(y1 - y0)), style = st)
            }
            box(16.5f, 20.16f); box(5.5f, 9.16f)
            // the D outside the box
            val pen = Y(g + d * 11f); val r = 9.15f * sx
            val sweep = 106f
            drawArc(c, if (top) 90f - sweep / 2 else 270f - sweep / 2, sweep, false, Offset(X(34f) - r, pen - r), Size(2 * r, 2 * r), style = st)
            // corner arcs
            for (cx in listOf(0f, 68f)) drawArc(c, 0f, 360f, false, Offset(X(cx) - 1f * sx, Y(g) - 1f * sx), Size(2f * sx, 2f * sx), style = st)
        }
    }
    for (top in listOf(true, false)) {
        val g = if (top) 0f else 105f; val d = if (top) 1f else -1f
        drawCircle(line, 2.2.dp.toPx(), Offset(X(34f), Y(g + d * 11f)))
        // the goal and its net just outside the line
        val gx0 = X(34f - 3.66f); val gx1 = X(34f + 3.66f); val gy = Y(g); val gy2 = gy - d * 2.2f * sy * 1.4f
        val net = Color.White.copy(alpha = 0.25f)
        for (k in 0..7) { val x = gx0 + (gx1 - gx0) * k / 7; drawLine(net, Offset(x, gy), Offset(x, gy2), 1f) }
        for (k in 0..2) { val y = gy + (gy2 - gy) * k / 2; drawLine(net, Offset(gx0, y), Offset(gx1, y), 1f) }
        drawLine(Color.White, Offset(gx0, gy), Offset(gx0, gy2), 2.5.dp.toPx()); drawLine(Color.White, Offset(gx1, gy), Offset(gx1, gy2), 2.5.dp.toPx())
        drawLine(Color.White, Offset(gx0, gy2), Offset(gx1, gy2), 2.5.dp.toPx())
    }
    drawCircle(line, 2.4.dp.toPx(), Offset(X(34f), Y(52.5f)))
    // a lens-like vignette
    drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.38f)), Offset(w / 2, h * 0.45f), h * 0.78f))
}

@Composable
private fun Token(p: PitchMan, color: Color, modifier: Modifier, onClick: () -> Unit) {
    val ink = inkOn(color)
    Column(modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(64.dp, 58.dp)) {
            // a soft shadow on the grass
            Box(Modifier.align(Alignment.BottomCenter).size(46.dp, 10.dp).drawBehind {
                drawOval(Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent)), size = size)
            })
            // the ring in the team colour (gold for the star), the photo inside it
            val ringBrush = if (p.star) Brush.sweepGradient(listOf(GOLDEN, Color(0xFFFFF2B0), Color(0xFFB8860B), GOLDEN))
                else Brush.sweepGradient(listOf(color, lerp(color, Color.White, 0.55f), color, lerp(color, Color.Black, 0.3f), color))
            Box(Modifier.align(Alignment.TopCenter).size(52.dp).then(if (p.star) Modifier.glow(GOLDEN.copy(alpha = 0.7f), 14.dp, CircleShape) else Modifier.glow(Color.Black.copy(alpha = 0.4f), 6.dp, CircleShape))
                .clip(CircleShape).background(ringBrush).padding(2.5.dp).clip(CircleShape).background(lerp(color, Color.Black, 0.25f)),
                contentAlignment = Alignment.Center) {
                Text(p.number, color = ink.copy(alpha = 0.9f), style = lat(16.sp, FontWeight.ExtraBold))
                coil.compose.AsyncImage(playerFace(p.id), null, Modifier.fillMaxSize().clip(CircleShape), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
            }
            p.rating?.let { Rating(it, Modifier.align(Alignment.TopEnd).absoluteOffset(0.dp, (-3).dp), small = true) }
            // shirt number, or the club crest when players come from different teams
            Box(Modifier.align(Alignment.BottomStart).absoluteOffset(2.dp, (-6).dp)) {
                if (p.crest != null) Box(Modifier.size(22.dp).clip(CircleShape).background(Color.White).padding(2.dp), contentAlignment = Alignment.Center) { Crest(p.crest, 17.dp) }
                else if (p.number.isNotEmpty()) Box(Modifier.clip(RoundedCornerShape(6.dp)).background(color).border(1.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 4.dp), contentAlignment = Alignment.Center) { Text(p.number, color = ink, style = lat(10.sp, FontWeight.ExtraBold)) }
            }
            if (p.captain) Box(Modifier.align(Alignment.BottomEnd).absoluteOffset((-4).dp, (-6).dp).size(17.dp).clip(CircleShape).background(GOLDEN)
                .border(1.dp, Color.White, CircleShape), contentAlignment = Alignment.Center) {
                Text(if (app.goallive.live.Lang.en) "C" else L("ک"), fontSize = 9.sp, fontWeight = FontWeight.Black, color = Color(0xFF2A1A00))
            }
            if (p.star) Text("★", Modifier.align(Alignment.TopStart).absoluteOffset(4.dp, (-4).dp), color = GOLDEN, fontSize = 17.sp, fontWeight = FontWeight.Black)
        }
        // name plate
        Box(Modifier.clip(RoundedCornerShape(8.dp)).background(Brush.verticalGradient(listOf(Color(0xE6121820), Color(0xE60A0E13))))
            .border(1.dp, (if (p.star) GOLDEN else Color.White).copy(alpha = if (p.star) 0.8f else 0.14f), RoundedCornerShape(8.dp)).padding(horizontal = 7.dp, vertical = 1.dp)) {
            Ltr(p.name, style = vz(11.5.sp, FontWeight.ExtraBold), color = if (p.star) GOLDEN else Color.White, align = TextAlign.Center, raw = true)
        }
        Events(p)
    }
}

/** Goals, assists, cards and the minute he went off, as small round badges under the name. */
@Composable
private fun Events(p: PitchMan) {
    if (p.goals == 0 && p.own == 0 && !p.red && !p.yellow && p.assists == 0 && p.outAt == null) return
    Row(Modifier.padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        val dot = Modifier.size(16.dp).clip(CircleShape).background(Color.White).border(1.dp, Color.Black.copy(alpha = 0.15f), CircleShape)
        fun count(n: Int) = if (n > 1) fa(n) else ""
        if (p.goals > 0) Row(verticalAlignment = Alignment.CenterVertically) {
            Box(dot, contentAlignment = Alignment.Center) { Icon(Ic.ball, null, Modifier.size(12.dp), tint = Color(0xFF111111)) }
            if (p.goals > 1) Text(count(p.goals), color = Color.White, style = vz(10.sp, FontWeight.Black), modifier = Modifier.padding(start = 1.dp))
        }
        if (p.own > 0) Box(dot, contentAlignment = Alignment.Center) { Icon(Ic.ball, null, Modifier.size(12.dp), tint = C.live) }
        if (p.assists > 0) Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(16.dp).clip(CircleShape).background(Color(0xFF39D98A)), contentAlignment = Alignment.Center) {
                Text(if (app.goallive.live.Lang.en) "A" else "پ", fontSize = 9.sp, color = Color(0xFF052914), fontWeight = FontWeight.Black)
            }
            if (p.assists > 1) Text(count(p.assists), color = Color.White, style = vz(10.sp, FontWeight.Black), modifier = Modifier.padding(start = 1.dp))
        }
        if (p.red) Box(Modifier.size(10.dp, 13.dp).clip(RoundedCornerShape(2.dp)).background(C.live))
        else if (p.yellow) Box(Modifier.size(10.dp, 13.dp).clip(RoundedCornerShape(2.dp)).background(C.gold))
        p.outAt?.let { t ->
            Row(Modifier.clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("⇩", fontSize = 9.sp, color = Color(0xFFFF6B6B), fontWeight = FontWeight.Black)
                Text(fa(t) + "'", fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** A small pill for the header: formation, rating, week. */
@Composable
fun PitchPill(text: String, bg: Color, ink: Color = inkOn(bg)) {
    Box(Modifier.clip(RoundedCornerShape(10.dp)).background(bg).padding(horizontal = 10.dp, vertical = 4.dp)) {
        // isolated left to right, so a formation reads 4-2-3-1 and not 4-1-2-3
        Ltr(ltrDigits(text), style = vz(13.sp, FontWeight.ExtraBold), color = ink, raw = true)
    }
}

@Suppress("unused") private val keepDp: Dp = 0.dp

/** Persian digits joined by dashes kept in reading order left to right (4-2-3-1), with left-to-right marks around each dash. */
fun ltrDigits(s: String) = "\u200E" + s.replace("-", "\u200E-\u200E") + "\u200E"
