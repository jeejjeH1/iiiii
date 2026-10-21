package app.goallive.live.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.goallive.live.data.MatchItem
import app.goallive.live.data.Store
import coil.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

private const val IMG = "https://images.fotmob.com/image_resources"
fun teamLogo(id: Int, large: Boolean = false) = "$IMG/logo/teamlogo/${id}${if (large) "" else "_small"}.png"
fun leagueLogo(id: Int) = "$IMG/logo/leaguelogo/dark/$id.png"
fun playerFace(id: Int) = "$IMG/playerimages/$id.png"

@Composable
fun Crest(id: Int, size: Dp = 24.dp, large: Boolean = size > 40.dp) {
    AsyncImage(model = teamLogo(id, large), contentDescription = null, modifier = Modifier.size(size), contentScale = ContentScale.Fit)
}

@Composable
fun LeagueLogo(id: Int, size: Dp = 22.dp) {
    AsyncImage(model = leagueLogo(id), contentDescription = null, modifier = Modifier.size(size), contentScale = ContentScale.Fit)
}

@Composable
fun Face(id: Int, size: Dp = 38.dp, ring: Color? = null) {
    Box(
        Modifier.size(size).clip(CircleShape).background(C.card2)
            .then(if (ring != null) Modifier.border(2.dp, ring, CircleShape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(model = playerFace(id), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

/** Text that is always laid out left-to-right (Latin names, minutes like 45+2'). */
@Composable
fun Ltr(text: String, modifier: Modifier = Modifier, style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium,
        color: Color = Color.Unspecified, weight: FontWeight? = null, maxLines: Int = 1, align: TextAlign? = null) {
    Text(text, modifier, color = color, style = style.copy(textDirection = TextDirection.ContentOrLtr), fontWeight = weight,
        maxLines = maxLines, overflow = TextOverflow.Ellipsis, textAlign = align)
}

@Composable
fun Card(modifier: Modifier = Modifier, pad: PaddingValues = PaddingValues(0.dp), content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.card)
            .border(1.dp, C.line, RoundedCornerShape(18.dp)).padding(pad),
        content = content,
    )
}

@Composable
fun CardTitle(text: String, modifier: Modifier = Modifier, end: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        end()
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = C.mute, modifier = modifier.padding(start = 6.dp, top = 18.dp, bottom = 8.dp))
}

@Composable
fun Divider(modifier: Modifier = Modifier) = Box(modifier.fillMaxWidth().height(1.dp).background(C.line))

@Composable
fun LiveDot(color: Color = C.live, size: Dp = 7.dp) {
    val t = rememberInfiniteTransition(label = "dot")
    val a by t.animateFloat(1f, 0.25f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "a")
    Box(Modifier.size(size).alpha(a).clip(CircleShape).background(color))
}

@Composable
fun Pill(text: String, color: Color = LocalAccent.current, filled: Boolean = false, onClick: (() -> Unit)? = null, icon: ImageVector? = null) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(if (filled) color else color.copy(alpha = 0.14f))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) Icon(icon, null, Modifier.size(15.dp), tint = if (filled) MaterialTheme.colorScheme.onPrimary else color)
        Text(text, style = MaterialTheme.typography.labelMedium, color = if (filled) MaterialTheme.colorScheme.onPrimary else color, fontWeight = FontWeight.Bold)
    }
}

/** Segmented chips; the selected one is filled with the accent. */
@Composable
fun Seg(options: List<Pair<String, String>>, selected: String, modifier: Modifier = Modifier, onSelect: (String) -> Unit) {
    Row(
        modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for ((k, label) in options) {
            val on = k == selected
            Box(
                Modifier.clip(RoundedCornerShape(50)).background(if (on) LocalAccent.current else C.card2)
                    .clickable { onSelect(k) }.padding(horizontal = 14.dp, vertical = 7.dp),
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                    color = if (on) MaterialTheme.colorScheme.onPrimary else C.text)
            }
        }
    }
}

@Composable
fun Rating(r: Double?, modifier: Modifier = Modifier) {
    if (r == null || r <= 0) return
    val c = when {
        r >= 8 -> Color(0xFF1E88FF)
        r >= 7 -> Color(0xFF14B86A)
        r >= 6 -> Color(0xFFF0A020)
        else -> Color(0xFFE5383B)
    }
    Box(modifier.clip(RoundedCornerShape(6.dp)).background(c).padding(horizontal = 5.dp, vertical = 1.dp)) {
        Text(dec(r, 1), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun StarButton(on: Boolean, onToggle: () -> Unit, size: Dp = 20.dp) {
    IconButton(onClick = onToggle, modifier = Modifier.size(36.dp)) {
        Icon(if (on) Icons.Rounded.Star else Icons.Rounded.StarBorder, null, Modifier.size(size), tint = if (on) C.gold else C.mute)
    }
}

@Composable
fun Skeleton(rows: Int = 6, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "sk")
    val a by t.animateFloat(0.35f, 0.75f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "a")
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(rows) { i ->
            Box(Modifier.fillMaxWidth(if (i % 3 == 0) 1f else 0.92f).height(if (i == 0) 90.dp else 54.dp).alpha(a).clip(RoundedCornerShape(16.dp)).background(C.card2))
        }
    }
}

@Composable
fun ErrorView(onRetry: () -> Unit, text: String = "اتصال به اینترنت برقرار نشد") {
    Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Rounded.WifiOff, null, Modifier.size(42.dp), tint = C.mute)
        Spacer(Modifier.height(10.dp))
        Text(text, color = C.mute, textAlign = TextAlign.Center)
        TextButton(onClick = onRetry) { Text("تلاش دوباره", color = LocalAccent.current, fontWeight = FontWeight.Bold) }
    }
}

@Composable
fun EmptyView(text: String, icon: ImageVector? = null) {
    Column(Modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (icon != null) { Icon(icon, null, Modifier.size(40.dp), tint = C.mute.copy(alpha = 0.6f)); Spacer(Modifier.height(10.dp)) }
        Text(text, color = C.mute, textAlign = TextAlign.Center)
    }
}

/* ---------- loading data ---------- */

sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Ok<T>(val v: T) : Load<T>
    data class Err(val e: Throwable) : Load<Nothing>
}

class Loader<T> {
    var state by mutableStateOf<Load<T>>(Load.Loading)
    var tick by mutableIntStateOf(0)
    fun reload() { tick++ }
    val value: T? get() = (state as? Load.Ok<T>)?.v
}

/**
 * Loads data for a screen and, when [poll] returns a delay, keeps refreshing it while the screen is visible.
 * [peek] gives an instant first paint from the last saved copy.
 */
@Composable
fun <T> rememberLoader(vararg keys: Any?, peek: (() -> T?)? = null, poll: (T) -> Long? = { null }, block: suspend () -> T): Loader<T> {
    val l = remember(*keys) { Loader<T>().apply { peek?.invoke()?.let { state = Load.Ok(it) } } }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(l, l.tick) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                try {
                    l.state = Load.Ok(block())
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (l.state !is Load.Ok) l.state = Load.Err(e)
                }
                val v = l.value ?: break
                delay(poll(v) ?: break)
            }
        }
    }
    return l
}

@Composable
fun <T> LoadBox(l: Loader<T>, skeletonRows: Int = 6, content: @Composable (T) -> Unit) {
    when (val s = l.state) {
        is Load.Loading -> Skeleton(skeletonRows)
        is Load.Err -> ErrorView({ l.state = Load.Loading; l.reload() })
        is Load.Ok -> content(s.v)
    }
}

/* ---------- match row ---------- */

/** Score as separate pieces so the home goals always sit on the right in RTL. */
@Composable
fun ScoreText(h: Int?, a: Int?, size: Int = 15, color: Color = C.text, weight: FontWeight = FontWeight.ExtraBold) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("${h ?: "-"}", fontSize = size.sp, fontWeight = weight, color = color, style = MaterialTheme.typography.titleMedium)
        Text("-", fontSize = (size - 2).sp, color = C.mute, style = MaterialTheme.typography.titleMedium)
        Text("${a ?: "-"}", fontSize = size.sp, fontWeight = weight, color = color, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun MatchRow(m: MatchItem, onClick: () -> Unit, showDate: Boolean = false, sub: String? = null, star: Boolean = true, flash: Boolean = false) {
    val accent = LocalAccent.current
    Row(
        Modifier.fillMaxWidth().background(if (flash) accent.copy(alpha = 0.16f) else Color.Transparent)
            .clickable(onClick = onClick).padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // status column
        Column(Modifier.width(54.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            when {
                m.live -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    LiveDot(size = 6.dp)
                    Ltr(statusText(m), color = C.live, weight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                }
                m.finished || m.cancelled -> Text(statusText(m), color = C.mute, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                else -> {
                    Text(timeOf(m.time), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
            }
            if (showDate) Text(shortDate(m.time), color = C.mute, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
        Column(Modifier.weight(1f).padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TeamLine(m.home.id, m.home.name, if (m.hasScore) m.home.score else null, m.live, winner(m, true), m.penHome)
            TeamLine(m.away.id, m.away.name, if (m.hasScore) m.away.score else null, m.live, winner(m, false), m.penAway)
            if (sub != null) Text(sub, color = C.mute, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (star) StarButton(Store.follows(m), { Store.toggleMatch(m.id) }, 18.dp) else Spacer(Modifier.width(8.dp))
    }
}

private fun winner(m: MatchItem, home: Boolean): Boolean? {
    if (!m.finished) return null
    val h = m.home.score ?: return null
    val a = m.away.score ?: return null
    val ph = m.penHome ?: 0
    val pa = m.penAway ?: 0
    if (h == a && ph == pa) return null
    val homeWon = h > a || (h == a && ph > pa)
    return if (home) homeWon else !homeWon
}

@Composable
private fun TeamLine(id: Int, name: String, score: Int?, live: Boolean, won: Boolean?, pen: Int?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Crest(id, 20.dp)
        Spacer(Modifier.width(8.dp))
        Ltr(name, Modifier.weight(1f), color = if (won == false) C.mute else C.text, weight = if (won == true) FontWeight.Bold else FontWeight.Medium,
            align = TextAlign.Start)
        if (pen != null) Text("($pen)", color = C.mute, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(end = 4.dp))
        if (score != null) Text("$score", fontWeight = FontWeight.ExtraBold, color = if (live) C.live else if (won == false) C.mute else C.text,
            modifier = Modifier.widthIn(min = 16.dp), textAlign = TextAlign.Center)
    }
}

@Composable
fun TopBar(title: String, onBack: (() -> Unit)?, modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) IconButton(onClick = onBack) {
            // auto-mirrored: points right in RTL
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "بازگشت", tint = C.text, modifier = Modifier.size(24.dp))
        } else Spacer(Modifier.width(12.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        actions()
    }
}
