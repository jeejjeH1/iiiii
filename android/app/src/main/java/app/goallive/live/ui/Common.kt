package app.goallive.live.ui

import app.goallive.live.L

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.runtime.*
import androidx.compose.ui.draw.*
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
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
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.compositionLocalOf
import app.goallive.live.data.Api
import app.goallive.live.data.b
import app.goallive.live.data.o
import app.goallive.live.data.s
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.goallive.live.data.faTeamName
import coil.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

private const val IMG = "https://images.fotmob.com/image_resources"
fun teamLogo(id: Int, large: Boolean = false) = "$IMG/logo/teamlogo/${id}${if (large) "" else "_small"}.png"
fun leagueLogo(id: Int) = "$IMG/logo/leaguelogo/dark/$id.png"
fun playerFace(id: Int) = "$IMG/playerimages/$id.png"
fun flagLogo(ccode: String) = "$IMG/logo/teamlogo/${ccode.lowercase()}.png"

/* ---------- backdrop ---------- */

/** The site's ".stadium": a soft accent glow from the top, mowed stripes and two floodlight beams. */
@Composable
fun Stadium(accent: Color) {
    Canvas(Modifier.fillMaxSize()) {
        drawRect(C.bg)
        val stripe = 64.dp.toPx()
        var x = 0f
        val mow = if (Look.light) Color(0x080A3C19) else Color(0x03FFFFFF)
        while (x < size.width) { drawRect(mow, Offset(x, 0f), Size(stripe, size.height)); x += stripe * 2 }
        drawRect(Brush.radialGradient(listOf(accent.copy(alpha = 0.07f), Color.Transparent), Offset(size.width / 2, -size.height * 0.1f), size.height * 0.6f))
        beam(-0.22f * size.width, -24f)
        beam(size.width * 1.22f, 24f)
    }
}

private fun DrawScope.beam(cx: Float, deg: Float) {
    val w = size.width * 0.6f
    val h = size.height * 1.5f
    val top = -size.height * 0.3f
    rotate(deg, Offset(cx, top)) {
        val p = Path().apply {
            moveTo(cx - w * 0.04f, top); lineTo(cx + w * 0.04f, top); lineTo(cx + w / 2, top + h); lineTo(cx - w / 2, top + h); close()
        }
        drawPath(p, Brush.verticalGradient(listOf(if (Look.light) Color(0x80FFFFFF) else Color(0x0AD6F0FF), Color.Transparent), top, top + h * 0.7f))
    }
}

/* ---------- images ---------- */

/** Set by Root: lets a crest fly from a list row to the match page (shared element transition). */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalShared = compositionLocalOf<SharedTransitionScope?> { null }
val LocalAnimScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** Off in the screenshot run: its software canvas can't draw the transition's layers. */
var sharedCrests = true

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.shared(key: String?): Modifier {
    if (!sharedCrests) return this
    val s = LocalShared.current
    val a = LocalAnimScope.current
    if (key == null || s == null || a == null) return this
    return with(s) { this@shared.sharedElement(rememberSharedContentState(key), a) }
}

@Composable
fun Crest(id: Int, size: Dp = 24.dp, large: Boolean = size > 40.dp, shareKey: String? = null) {
    if (id <= 0) { Spacer(Modifier.size(size)); return }
    AsyncImage(model = teamLogo(id, large), contentDescription = null, modifier = Modifier.shared(shareKey).size(size), contentScale = ContentScale.Fit)
}

@Composable
fun LeagueLogo(id: Int, size: Dp = 22.dp) {
    AsyncImage(model = leagueLogo(id), contentDescription = null, modifier = Modifier.size(size), contentScale = ContentScale.Fit)
}

@Composable
fun Face(id: Int, size: Dp = 38.dp, ring: Color? = null, ringWidth: Dp = 2.dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(C.card2)
            .border(if (ring != null) ringWidth else 1.dp, ring ?: C.line, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Ic.people, null, tint = C.faint, modifier = Modifier.size(size * 0.5f))
        AsyncImage(model = playerFace(id), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

/* ---------- text ---------- */

/** Text laid out left-to-right (Latin names, minutes like 45+2'), in the site's broadcast font by default. */
@Composable
fun Ltr(
    text: String, modifier: Modifier = Modifier, style: TextStyle = lat(15.sp), color: Color = C.ink,
    weight: FontWeight? = null, maxLines: Int = 1, align: TextAlign? = null, raw: Boolean = false,
) {
    // all-Persian mode: Latin names in Persian letters ([raw] keeps an English spelling shown on purpose)
    val shown = if (app.goallive.live.Lang.en || raw) text else app.goallive.live.data.FaNames.of(text)
    Text(shown, modifier, color = color, style = style.copy(textDirection = TextDirection.ContentOrLtr), fontWeight = weight,
        maxLines = maxLines, overflow = TextOverflow.Ellipsis, textAlign = align)
}

/** Plain text in the site's body font; never smaller than 12sp so it stays readable. */
@Composable
fun T(text: String, size: Int = 14, w: FontWeight = FontWeight.Normal, color: Color = C.ink, modifier: Modifier = Modifier,
      maxLines: Int = Int.MAX_VALUE, align: TextAlign? = null) {
    val style = vz(size.coerceAtLeast(12).sp, w)
    if (!hasIcon(text)) { Text(text, modifier, color = color, style = style, maxLines = maxLines, overflow = TextOverflow.Ellipsis, textAlign = align); return }
    // emoji become the app's own line icons, inline, in the text's colour
    val (shown, inline) = remember(text, color) { withIcons(text, color) }
    Text(shown, modifier, color = color, style = style, maxLines = maxLines, overflow = TextOverflow.Ellipsis, textAlign = align, inlineContent = inline)
}

/** A player's or coach's name inside a sentence: as FotMob writes it in English, in Persian letters in Persian. */
fun personName(n: String): String = if (app.goallive.live.Lang.en) n else app.goallive.live.data.FaNames.of(n)

/** A team's name as the user wants it: Persian when we know it and the setting is on, else FotMob's English. */
/** A league's name in the app's language, written the same way as in the league lists. */
@Suppress("UNUSED_PARAMETER")
fun leagueName(id: Int, name: String): String = if (app.goallive.live.Lang.en) name else app.goallive.live.data.FaNames.of(name)

fun teamName(id: Int, name: String): String = if (Store.faNames) faTeamName(id, name) ?: app.goallive.live.data.FaNames.of(name) else name

/** Team name: Persian in the body font, or English ([short] when given) in the broadcast font. */
@Composable
fun TeamText(id: Int, name: String, modifier: Modifier = Modifier, size: Int = 14, w: FontWeight = FontWeight.Bold, color: Color = C.ink,
             maxLines: Int = 1, align: TextAlign? = null, short: String? = null) {
    val fa = if (Store.faNames) faTeamName(id, name) ?: app.goallive.live.data.FaNames.of(name).takeIf { it != name } else null
    if (fa != null && maxLines > 1) {
        // two lines are for two words: when a single word would be split, the name gets a size smaller instead
        var s by remember(fa, size) { mutableIntStateOf(size - 1) }
        Text(fa, modifier, color = color, style = vz(s.sp, if (w >= FontWeight.Bold) FontWeight.ExtraBold else FontWeight.Bold), maxLines = maxLines,
            overflow = TextOverflow.Ellipsis, textAlign = align, onTextLayout = { r ->
                val split = (0 until r.lineCount - 1).any { i -> val e = r.getLineEnd(i); e in 1 until fa.length && fa[e - 1].isLetter() && fa[e].isLetter() }
                if ((split || r.hasVisualOverflow) && s > 10) s--
            })
    } else if (fa != null) T(fa, size - 1, if (w >= FontWeight.Bold) FontWeight.ExtraBold else FontWeight.Bold, color, modifier, maxLines, align)
    else Ltr(short ?: name, modifier, lat(size.sp, w), color, maxLines = maxLines, align = align)
}

/* ---------- surfaces ---------- */

/*
 * One scale for corners, used everywhere: 6 (badges), 10 (chips, small pills), 14 (buttons, rows, inputs),
 * 18 (inner panels, tiles), 22 (cards), 28 (sheets); tiny bars keep 2-5 and round things CircleShape.
 */
private val CardShape = RoundedCornerShape(22.dp)

@Composable
fun Card(modifier: Modifier = Modifier, pad: PaddingValues = PaddingValues(0.dp), content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(CardShape).background(C.card).border(1.dp, C.line, CardShape).padding(pad),
        content = content,
    )
}

/** ".card-h": bold card header with optional grey sub text and an end slot. */
@Composable
fun CardTitle(text: String, modifier: Modifier = Modifier, sub: String? = null, end: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        // a subtitle goes under the title: side by side, a long pair ran into each other
        if (sub != null) Column(Modifier.weight(1f)) {
            T(text, 14, FontWeight.ExtraBold, maxLines = 1)
            T(sub, 12, FontWeight.Medium, C.mute, maxLines = 2)
        } else {
            T(text, 14, FontWeight.ExtraBold)
            Spacer(Modifier.weight(1f))
        }
        end()
    }
}

/** ".section-title": a glowing accent bar and a bold label. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    Row(modifier.padding(start = 4.dp, end = 4.dp, top = 22.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(4.dp, 16.dp).glow(accent, 6.dp, RoundedCornerShape(2.dp)).clip(RoundedCornerShape(2.dp)).background(accent))
        Spacer(Modifier.width(10.dp))
        T(text, 13, FontWeight.Black)
    }
}

/** Soft colored halo around an element (the site's box-shadow glows), drawn as a blurred shape behind it. */
fun Modifier.glow(color: Color, radius: Dp, shape: Shape = RoundedCornerShape(14.dp)): Modifier = drawBehind {
    val r = radius.toPx()
    if (r <= 0f) return@drawBehind
    val outline = shape.createOutline(size, layoutDirection, this)
    val paint = androidx.compose.ui.graphics.Paint().apply {
        this.color = color
        asFrameworkPaint().maskFilter = android.graphics.BlurMaskFilter(r, android.graphics.BlurMaskFilter.Blur.NORMAL)
    }
    drawIntoCanvas { c -> c.drawOutline(outline, paint) }
}

@Composable
fun Divider(modifier: Modifier = Modifier) = Box(modifier.fillMaxWidth().height(1.dp).background(C.line))

/** ".list-row": a padded row separated by a hairline (none above the first). */
@Composable
fun ListRow(first: Boolean = false, onClick: (() -> Unit)? = null, pad: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 11.dp),
            content: @Composable RowScope.() -> Unit) {
    if (!first) Divider()
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(pad),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), content = content,
    )
}

@Composable
fun LiveDot(color: Color = C.live, size: Dp = 6.dp, pulse: Boolean = true) {
    val a = if (pulse) {
        val t = rememberInfiniteTransition(label = "dot")
        t.animateFloat(1f, 0.25f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "a").value
    } else 1f
    Box(Modifier.size(size).alpha(a).clip(CircleShape).background(color))
}

enum class PillKind { Plain, Live, Accent }

/** ".pill" (grey), ".pill.live" (red) and ".pill.accent" (tinted). */
@Composable
fun Pill(text: String, kind: PillKind = PillKind.Plain, onClick: (() -> Unit)? = null, icon: ImageVector? = null, dot: Boolean = false) {
    val accent = LocalAccent.current
    val (bg, fg) = when (kind) {
        PillKind.Live -> C.live to Color.White
        PillKind.Accent -> accent.copy(alpha = 0.14f) to accent
        PillKind.Plain -> C.card2 to C.mute
    }
    Row(
        Modifier.height(26.dp).clip(RoundedCornerShape(50)).background(bg)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (dot) LiveDot(fg, 6.dp)
        if (icon != null) Icon(icon, null, Modifier.size(14.dp), tint = fg)
        T(text, 11, FontWeight.ExtraBold, fg, maxLines = 1)
    }
}

/** ".seg": a segmented control; the selected item is inverted (white on black). */
@Composable
fun Seg(options: List<Pair<String, String>>, selected: String, modifier: Modifier = Modifier, badge: Map<String, Int> = emptyMap(),
        icons: Map<String, ImageVector> = emptyMap(), onSelect: (String) -> Unit) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.card).border(1.dp, C.line, RoundedCornerShape(18.dp)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for ((k, label) in options) {
            val on = k == selected
            Row(
                Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(14.dp)).background(if (on) C.ink else Color.Transparent)
                    .clickable { onSelect(k) }.padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
            ) {
                icons[k]?.let { Icon(it, null, Modifier.size(15.dp), tint = if (on) C.bg else C.mute); Spacer(Modifier.width(5.dp)) }
                T(label, 12, FontWeight.ExtraBold, if (on) C.bg else C.mute, maxLines = 1)
                badge[k]?.takeIf { it > 0 }?.let { n ->
                    Spacer(Modifier.width(6.dp))
                    Box(Modifier.heightIn(min = 18.dp).widthIn(min = 18.dp).clip(RoundedCornerShape(50)).background(C.live).padding(horizontal = 5.dp),
                        contentAlignment = Alignment.Center) { Text(fa(n), color = Color.White, style = vz(10.sp, FontWeight.Bold)) }
                }
            }
        }
    }
}

/** ".tabs": page tabs; the selected one is an accent chip with a glow. */
@Composable
fun Tabs(options: List<Pair<String, String>>, selected: String, modifier: Modifier = Modifier, onSelect: (String) -> Unit) {
    val accent = LocalAccent.current
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for ((k, label) in options) {
            val on = k == selected
            Box(
                Modifier.then(if (on) Modifier.glow(accent.copy(alpha = 0.4f), 8.dp) else Modifier).clip(RoundedCornerShape(14.dp))
                    .background(if (on) accent else Color.Transparent).clickable { onSelect(k) }.padding(horizontal = 14.dp, vertical = 8.dp),
            ) { T(label, 13, FontWeight.ExtraBold, if (on) LocalAccentInk.current else C.mute, maxLines = 1) }
        }
    }
}

/** ".rate": the coloured player rating badge. */
@Composable
fun Rating(r: Double?, modifier: Modifier = Modifier, small: Boolean = false) {
    if (r == null || r <= 0) return
    val c = when {
        r >= 9 -> Color(0xFF1D9BF0)
        r >= 7.5 -> Color(0xFF14B866)
        r >= 6.5 -> Color(0xFF9FB21B)
        r >= 5.5 -> Color(0xFFF08A24)
        else -> Color(0xFFE5434B)
    }
    Box(modifier.clip(RoundedCornerShape(6.dp)).background(c).padding(horizontal = if (small) 5.dp else 6.dp, vertical = 1.dp)) {
        Text(dec(r, 1), color = Color.White, style = vz(if (small) 10.sp else 11.sp, FontWeight.Black))
    }
}

@Composable
fun StarButton(on: Boolean, onToggle: () -> Unit, size: Dp = 18.dp, tint: Color = C.faint, box: Dp = 32.dp) {
    Box(Modifier.size(box).clip(RoundedCornerShape(10.dp)).clickable(onClick = onToggle), contentAlignment = Alignment.Center) {
        Icon(if (on) Ic.starOn else Ic.star, if (on) L("دنبال نکن") else L("دنبال کن"), Modifier.size(size), tint = if (on) C.gold else tint)
    }
}

@Composable
fun IconBtn(icon: ImageVector, desc: String?, tint: Color = C.ink, bg: Color = Color.Transparent, size: Dp = 20.dp, onClick: () -> Unit) {
    Box(Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(bg).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, desc, Modifier.size(size), tint = tint)
    }
}

/* ---------- states ---------- */

/** Loading placeholder shaped like what is coming: cards of match rows (crest, name, score box), with a moving shine. */
@Composable
fun Skeleton(rows: Int = 6, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "sk")
    val x by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "x")
    val brush = Brush.linearGradient(
        listOf(C.card2, C.line2, C.card2),
        start = Offset(-600f + 1400f * x, 0f), end = Offset(-200f + 1400f * x, 0f),
    )
    val bar = @Composable { w: Dp, h: Dp, m: Modifier -> Box(m.size(w, h).clip(RoundedCornerShape(6.dp)).background(brush)) }
    Column(modifier.padding(vertical = 8.dp).semantics { contentDescription = L("در حال بارگذاری") }, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        var left = rows.coerceAtLeast(2)
        var card = 0
        while (left > 0) {
            val n = minOf(left, if (card == 0) 3 else 4)
            left -= n
            Card {
                // league header: logo and name
                Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(22.dp).clip(CircleShape).background(brush))
                    Spacer(Modifier.width(10.dp))
                    bar(if (card % 2 == 0) 120.dp else 92.dp, 12.dp, Modifier)
                }
                repeat(n) { i ->
                    Divider()
                    Row(Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        bar(30.dp, 10.dp, Modifier)
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.size(21.dp).clip(CircleShape).background(brush)); bar(if ((i + card) % 2 == 0) 64.dp else 48.dp, 11.dp, Modifier)
                        }
                        Box(Modifier.size(64.dp, 34.dp).clip(RoundedCornerShape(10.dp)).background(brush))
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End)) {
                            bar(if ((i + card) % 2 == 0) 52.dp else 70.dp, 11.dp, Modifier); Box(Modifier.size(21.dp).clip(CircleShape).background(brush))
                        }
                    }
                }
            }
            card++
        }
    }
}

@Composable
fun ErrorView(onRetry: () -> Unit, text: String = L("اتصال برقرار نشد")) {
    val accent = LocalAccent.current
    Column(Modifier.fillMaxWidth().padding(vertical = 52.dp, horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Ic.info, null, Modifier.size(40.dp), tint = C.faint)
        Spacer(Modifier.height(12.dp))
        T(text, 14, color = C.mute, align = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Box(Modifier.clip(RoundedCornerShape(50)).background(accent.copy(alpha = 0.14f)).clickable(onClick = onRetry).padding(horizontal = 16.dp, vertical = 7.dp)) {
            T(L("تلاش دوباره"), 13, FontWeight.ExtraBold, accent)
        }
    }
}

/** Nothing to show: a big soft icon, a friendly line, an optional hint and a button that leads somewhere useful. */
@Composable
fun EmptyView(text: String, icon: ImageVector? = null, hint: String? = null, action: String? = null, onAction: (() -> Unit)? = null) {
    val accent = LocalAccent.current
    Column(Modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (icon != null) {
            Box(Modifier.size(76.dp).clip(CircleShape).background(accent.copy(alpha = 0.10f)).border(1.dp, accent.copy(alpha = 0.25f), CircleShape),
                contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(34.dp), tint = accent) }
            Spacer(Modifier.height(14.dp))
        }
        T(text, 15, FontWeight.ExtraBold, align = TextAlign.Center)
        if (hint != null) T(hint, 13, color = C.mute, align = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
        if (action != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            Box(Modifier.heightIn(min = 44.dp).glow(accent.copy(alpha = 0.3f), 8.dp, RoundedCornerShape(50)).clip(RoundedCornerShape(50)).background(accent)
                .clickable(onClick = onAction).padding(horizontal = 22.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                T(action, 14, FontWeight.Black, LocalAccentInk.current)
            }
        }
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
    /** Bumped after every load attempt, good or bad (ends the pull-to-refresh spinner). */
    var attempts by mutableIntStateOf(0)
    fun reload() { tick++ }
    val value: T? get() = (state as? Load.Ok<T>)?.v
}

/**
 * Loads data for a screen and, when [poll] returns a delay, keeps refreshing it while the screen is visible.
 * [peek] gives an instant first paint from the last saved copy.
 */
@Composable
fun <T> rememberLoader(vararg keys: Any?, peek: (() -> T?)? = null, poll: (T) -> Long? = { null }, block: suspend () -> T): Loader<T> {
    // the last answer for this spot on this screen: coming back to a page shows it at once (and its list can scroll back to
    // where it was) while a fresh copy is fetched
    val site = "${currentCompositeKeyHash}:" + keys.joinToString("|")
    val l = remember(*keys) {
        Loader<T>().apply {
            @Suppress("UNCHECKED_CAST")
            (runCatching { peek?.invoke() }.getOrNull() ?: LAST_VALUES[site] as? T)?.let { state = Load.Ok(it) }
        }
    }
    LaunchedEffect(l.value) { l.value?.let { v -> if (LAST_VALUES.size > 80) LAST_VALUES.clear(); LAST_VALUES[site] = v } }
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
                l.attempts++
                val v = l.value ?: break
                delay(poll(v) ?: break)
            }
        }
    }
    return l
}

private val LAST_VALUES = java.util.concurrent.ConcurrentHashMap<String, Any>()

/** Pull down to fetch fresh data for [loaders] (skipping the in-memory copies). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Refreshable(vararg loaders: Loader<*>, content: @Composable () -> Unit) {
    var busy by remember { mutableStateOf(false) }
    val state = rememberPullToRefreshState()
    val accent = LocalAccent.current
    val done = loaders.sumOf { it.attempts }
    var startedAt by remember { mutableIntStateOf(0) }
    LaunchedEffect(done) { if (busy && done >= startedAt + loaders.size) busy = false }
    LaunchedEffect(busy) { if (busy) { delay(15_000); busy = false } }
    PullToRefreshBox(
        isRefreshing = busy,
        onRefresh = { busy = true; startedAt = done; Api.invalidate(); loaders.forEach { it.reload() } },
        modifier = Modifier.fillMaxSize(), state = state,
        indicator = {
            PullToRefreshDefaults.Indicator(state = state, isRefreshing = busy, modifier = Modifier.align(Alignment.TopCenter),
                containerColor = C.cardSolid, color = accent)
        },
    ) { content() }
}

@Composable
fun <T> LoadBox(l: Loader<T>, skeletonRows: Int = 6, content: @Composable (T) -> Unit) {
    when (val s = l.state) {
        is Load.Loading -> Skeleton(skeletonRows, Modifier.padding(horizontal = 14.dp))
        is Load.Err -> ErrorView({ l.state = Load.Loading; l.reload() })
        is Load.Ok -> content(s.v)
    }
}

/* ---------- match row (".m") ---------- */

/** Score digits; the home goals sit on the right (RTL). */
@Composable
fun ScoreText(h: Int?, a: Int?, size: Int = 16, color: Color = C.ink, weight: FontWeight = FontWeight.Black, sepColor: Color = C.faint) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy((size / 3).dp)) {
        Text("${h ?: "-"}", color = color, style = vz(size.sp, weight))
        Text("-", color = sepColor, style = vz(size.sp, FontWeight.Light))
        Text("${a ?: "-"}", color = color, style = vz(size.sp, weight))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MatchRow(m: MatchItem, onClick: () -> Unit, showDate: Boolean = false, sub: String? = null, star: Boolean = true, flash: Boolean = false, first: Boolean = false,
             crestFly: Boolean = false) {
    val accent = LocalAccent.current
    // a long press shows the match in a small sheet without leaving the list
    var peek by remember { mutableStateOf(false) }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    if (peek) MatchPeek(m, { peek = false; onClick() }) { peek = false }
    if (!first) Divider()
    // one sentence for screen readers instead of a dozen small pieces
    val hn = teamName(m.home.id, m.home.name); val an = teamName(m.away.id, m.away.name)
    val desc = when {
        m.cancelled -> L("{0} و {1}، لغو شد", hn, an)
        m.live -> L("{0} {1}، {2} {3}، زنده، {4}", hn, m.home.score ?: 0, an, m.away.score ?: 0, statusText(m))
        m.finished -> L("{0} {1}، {2} {3}، پایان بازی", hn, m.home.score ?: 0, an, m.away.score ?: 0)
        else -> L("{0} و {1}، {2} ساعت {3}", hn, an, persianDate(m.time, "EEEE d MMMM"), timeOf(m.time))
    } + (sub?.let { L("، {0}", personName(it)) } ?: "")
    val datePlace = showDate && !m.live && dayKey(m.time) != dayKey(System.currentTimeMillis())
    Box(Modifier.fillMaxWidth().background(if (flash) accent.copy(alpha = 0.14f) else Color.Transparent)
        .combinedClickable(onClick = onClick, onLongClickLabel = L("پیش‌نمایش"), onLongClick = {
            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress); peek = true
        })
        .semantics { contentDescription = desc }) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(start = 10.dp, end = 6.dp, top = 10.dp, bottom = if (sub != null) 18.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // status
            Box(Modifier.width(if (datePlace) 50.dp else 44.dp).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
                val st = statusText(m).replace(L("بین دو نیمه"), L("نیمه"))
                when {
                    m.live -> Row(verticalAlignment = Alignment.CenterVertically) {
                        LiveDot(size = 6.dp); Spacer(Modifier.width(3.dp))
                        Ltr(st, style = vz(12.sp, FontWeight.ExtraBold), color = C.live)
                    }
                    // another day: weekday over the date, so "which day?" needs no thinking
                    datePlace -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        T(persianDate(m.time, "EEEE"), 12, FontWeight.ExtraBold, if (m.finished) C.mute else C.ink, maxLines = 1)
                        T(persianDate(m.time, "d MMM"), 12, FontWeight.Medium, C.mute, maxLines = 1)
                    }
                    st.isNotEmpty() -> T(st, 12, FontWeight.ExtraBold, C.mute, maxLines = 2, align = TextAlign.Center)
                }
            }
            val homeLose = m.finished && (m.home.score ?: 0) < (m.away.score ?: 0)
            val awayLose = m.finished && (m.away.score ?: 0) < (m.home.score ?: 0)
            // home (right)
            Row(Modifier.weight(1f).clearAndSetSemantics {}, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Crest(m.home.id, 21.dp, shareKey = if (crestFly) "crest-${m.id}-h" else null)
                TeamText(m.home.id, m.home.name, Modifier.weight(1f, false), 14, if (homeLose) FontWeight.SemiBold else FontWeight.Bold, if (homeLose) C.mute else C.ink, maxLines = 2)
            }
            Box(Modifier.clearAndSetSemantics {}) { ScoreBox(m) }
            // away (left): crest on the outer edge
            Row(Modifier.weight(1f).clearAndSetSemantics {}, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End)) {
                TeamText(m.away.id, m.away.name, Modifier.weight(1f, false), 14, if (awayLose) FontWeight.SemiBold else FontWeight.Bold, if (awayLose) C.mute else C.ink, maxLines = 2)
                Crest(m.away.id, 21.dp, shareKey = if (crestFly) "crest-${m.id}-a" else null)
            }
            if (star) StarButton(Store.follows(m), { Store.toggleMatch(m.id) }, 16.dp, box = 26.dp) else Spacer(Modifier.width(26.dp))
        }
        if (sub != null) T(personName(sub), 12, color = C.faint, maxLines = 1, align = TextAlign.Center,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 1.dp, start = 50.dp, end = 34.dp).clearAndSetSemantics {})
        // live minute progress along the bottom edge
        val min = m.minute.substringBefore('+').filter(Char::isDigit).toIntOrNull()
        if (m.live && min != null && min > 0) Box(
            Modifier.align(Alignment.BottomStart).fillMaxWidth((min / 90f).coerceAtMost(1f)).height(2.dp)
                .background(Brush.horizontalGradient(listOf(C.live, Color.Transparent))),
        )
    }
}

@Composable
fun ScoreBox(m: MatchItem) {
    val shape = RoundedCornerShape(10.dp)
    val bg = when { m.live -> C.live; m.finished -> Color.Transparent; else -> C.card2 }
    Box(
        Modifier.width(64.dp).height(34.dp).then(if (m.live) Modifier.glow(C.live.copy(alpha = 0.3f), 6.dp, shape) else Modifier)
            .clip(shape).background(bg).border(1.dp, if (m.live) Color.Transparent else C.line, shape),
        contentAlignment = Alignment.Center,
    ) {
        when {
            m.cancelled -> T("–", 14, FontWeight.ExtraBold)
            m.started || m.finished -> ScoreText(m.home.score, m.away.score, 15, if (m.live) Color.White else C.ink, sepColor = if (m.live) Color.White.copy(alpha = 0.6f) else C.faint)
            else -> T(timeOf(m.time), 14, FontWeight.ExtraBold)
        }
    }
}

/* ---------- page heroes ---------- */

/** Club texture over a hero (the site's patternStyle), drawn in white at low opacity. */
fun DrawScope.drawPattern(name: String, alpha: Float = 0.14f) {
    val w = Color.White.copy(alpha = alpha * 0.9f)
    when (name) {
        "stripes" -> { val s = 26.dp.toPx(); var x = 0f; while (x < size.width) { drawRect(w, Offset(x, 0f), Size(s, size.height)); x += 2 * s } }
        "flame", "devil", "cannon" -> { val s = 22.dp.toPx(); var x = -size.height; while (x < size.width) { drawLine(w, Offset(x, size.height), Offset(x + size.height * 0.47f, 0f), 3.dp.toPx()); x += s } }
        "wall" -> {
            var y = 0f; while (y < size.height) { drawRect(w, Offset(0f, y), Size(size.width, 2.dp.toPx())); y += 16.dp.toPx() }
            var x = 0f; while (x < size.width) { drawRect(w, Offset(x, 0f), Size(2.dp.toPx(), size.height)); x += 34.dp.toPx() }
        }
        "waves" -> { val st = Stroke(2.dp.toPx()); var r = 19.dp.toPx(); while (r < size.width * 1.5f) { drawCircle(w, r, Offset(0f, size.height), style = st); r += 40.dp.toPx() } }
        "diamonds" -> {
            val s = 24.dp.toPx(); var y = 0f; var row = 0
            while (y < size.height + s) {
                var x = if (row % 2 == 0) 0f else s
                while (x < size.width + s) {
                    val p = Path().apply { moveTo(x, y - s / 2); lineTo(x + s / 2, y); lineTo(x, y + s / 2); lineTo(x - s / 2, y); close() }
                    drawPath(p, w); x += 2 * s
                }
                y += s; row++
            }
        }
        else -> { val s = 18.dp.toPx(); var y = s / 2; while (y < size.height) { var x = s / 2; while (x < size.width) { drawCircle(w, 1.6.dp.toPx(), Offset(x, y)); x += s }; y += s } }
    }
}

/** ".phero": full-width club/league/player hero with rounded bottom corners. */
@Composable
fun PageHero(c1: Color, pattern: String?, ink: Color = Color.White, watermark: Int? = null, content: @Composable BoxScope.() -> Unit) {
    val shape = RoundedCornerShape(bottomStart = 30.dp, bottomEnd = 30.dp)
    Box(
        Modifier.fillMaxWidth().clip(shape)
            .background(Brush.linearGradient(listOf(c1, darken(c1, 0.35f))))
            .drawBehind {
                if (pattern != null) drawPattern(pattern)
                // a soft light from the top corner
                drawRect(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.14f), Color.Transparent), Offset(size.width * 0.8f, 0f), size.width * 0.7f))
            },
    ) {
        // the club's crest, large and faint, half off the far edge
        if (watermark != null && watermark > 0) AsyncImage(model = teamLogo(watermark, true), contentDescription = null,
            modifier = Modifier.align(Alignment.CenterEnd).offset(x = 56.dp).size(210.dp).alpha(0.12f), contentScale = ContentScale.Fit)
        Box(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 22.dp), content = content)
    }
}

/**
 * The long-press preview of a match: the teams and the score (or the kick-off), the goals and red cards with their minutes,
 * and two buttons, open the match or share it. Loaded on the spot; the list stays where it was underneath.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MatchPeek(m: MatchItem, onOpen: () -> Unit, onClose: () -> Unit) {
    val l = rememberLoader(m.id, "peek") { MatchData(Api.fm("matchDetails", mapOf("matchId" to m.id), 15_000)) }
    val accent = LocalAccent.current
    GlSheet(onDismissRequest = onClose, containerColor = C.cardSolid, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
            if (m.leagueName.isNotEmpty()) Ltr(m.leagueName, Modifier.fillMaxWidth(), style = vz(12.sp, FontWeight.Bold), color = C.mute, align = TextAlign.Center)
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                for ((side, end) in listOf(m.home to false, m.away to true)) {
                    if (end) Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(110.dp)) {
                        if (m.started && !m.cancelled) ScoreText(m.home.score, m.away.score, 34) else T(timeOf(m.time), 30, FontWeight.Black)
                        T(if (m.started || m.cancelled) statusText(m) else persianDate(m.time, "EEEE d MMMM"), 12, FontWeight.Bold, if (m.live) C.live else C.mute, maxLines = 1)
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Crest(side.id, 48.dp, large = true); TeamText(side.id, side.name, size = 14, w = FontWeight.ExtraBold, maxLines = 2, align = TextAlign.Center)
                    }
                }
            }
            val md = l.value
            val events = md?.events.orEmpty().filter { e ->
                !e.b("isPenaltyShootoutEvent") && (e.s("type") == "Goal" || (e.s("type") == "Card" && e.s("card") in setOf("Red", "YellowRed")))
            }
            when {
                md == null && m.started -> Skeleton(2)
                events.isNotEmpty() -> Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.card2).padding(vertical = 6.dp)) {
                    events.forEach { e ->
                        val home = e.b("isHome")
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Ltr(fa(minuteText(e)) + "'", Modifier.width(38.dp), style = vz(12.sp, FontWeight.Black), color = accent, raw = true)
                            if (e.s("type") == "Goal") Icon(Ic.ball, null, Modifier.size(15.dp), tint = if (e.b("ownGoal")) C.live else C.ink)
                            else Box(Modifier.size(10.dp, 13.dp).clip(RoundedCornerShape(2.dp)).background(C.live))
                            Ltr(e.s("nameStr") ?: e.o("player").s("name") ?: "", Modifier.weight(1f), style = vz(13.sp, FontWeight.Bold))
                            Crest(if (home) m.home.id else m.away.id, 18.dp)
                        }
                    }
                }
                m.started -> T(L("هنوز گل یا کارت قرمزی ثبت نشده"), 13, color = C.mute, modifier = Modifier.fillMaxWidth(), align = TextAlign.Center)
            }
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(18.dp)).background(accent).clickable(onClick = onOpen), contentAlignment = Alignment.Center) {
                    T(L("باز کردن بازی"), 15, FontWeight.Black, LocalAccentInk.current)
                }
                if (md != null && m.started) Box(Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(18.dp)).border(1.dp, C.line2, RoundedCornerShape(18.dp))
                    .clickable { onClose(); StoryStudio.story = Story.Match(md) }, contentAlignment = Alignment.Center) {
                    T(L("اشتراک‌گذاری"), 15, FontWeight.Black)
                }
            }
        }
    }
}
