package app.goallive.live.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.goallive.live.L

/*
 * The app's sheets, one look everywhere: a deep card rising with a soft light of the accent at its top, a handle, sections
 * marked by a short accent bar, two or three choices as a segmented control with a sliding pill, more as rounded chips,
 * and on / off choices with a small switch.
 */

/** A sheet from the bottom in the app's look ([containerColor], [shape] and [dragHandle] are taken for the same call shape as Material's and set here). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    @Suppress("UNUSED_PARAMETER") containerColor: Color = C.cardSolid,
    @Suppress("UNUSED_PARAMETER") shape: Shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
    @Suppress("UNUSED_PARAMETER") dragHandle: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val accent = LocalAccent.current
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismissRequest, sheetState = sheetState, containerColor = C.cardSolid,
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp), scrimColor = Color.Black.copy(alpha = 0.6f), tonalElevation = 0.dp,
        dragHandle = {
            Box(Modifier.padding(top = 10.dp, bottom = 6.dp).width(42.dp).height(5.dp).clip(RoundedCornerShape(5.dp))
                .background(Brush.horizontalGradient(listOf(C.ink.copy(alpha = 0.18f), accent.copy(alpha = 0.7f), C.ink.copy(alpha = 0.18f)))))
        },
    ) {
        Box(Modifier.fillMaxWidth()) {
            // the accent's light falling from the top edge
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(0f to accent.copy(alpha = 0.09f), 0.3f to Color.Transparent)))
            Column(Modifier.fillMaxWidth(), content = content)
        }
    }
}

/** A sheet's title row: an optional mark, the title and a line under it, and a round close button at the end. */
@Composable
fun SheetHeader(title: String, sub: String? = null, onClose: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 2.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            T(title, 20, FontWeight.Black, maxLines = 1)
            if (sub != null) T(sub, 12, color = C.mute, maxLines = 2, modifier = Modifier.padding(top = 2.dp))
        }
        if (onClose != null) Box(Modifier.size(36.dp).clip(CircleShape).background(C.card2).border(1.dp, C.line2, CircleShape).clickable(onClick = onClose),
            contentAlignment = Alignment.Center) { Icon(Ic.x, L("بستن"), Modifier.size(16.dp), tint = C.mute) }
    }
}

/** A section's name: a short accent bar and the words. */
@Composable
internal fun StudioLabel(s: String) {
    Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.width(3.dp).height(14.dp).clip(RoundedCornerShape(2.dp)).background(LocalAccent.current))
        T(s, 13, FontWeight.ExtraBold, C.ink.copy(alpha = 0.86f))
    }
}

/** A choice: two or three short options as a segmented control with a sliding pill; more as a row of chips. */
@Composable
internal fun <K> StudioChips(options: List<Pair<K, String>>, selected: K, onPick: (K) -> Unit) {
    val accent = LocalAccent.current
    if (options.size in 2..3 && options.all { it.second.length <= 16 }) {
        val idx = options.indexOfFirst { it.first == selected }
        BoxWithConstraints(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(46.dp).clip(RoundedCornerShape(16.dp)).background(C.card2)
            .border(1.dp, C.line2, RoundedCornerShape(16.dp)).padding(4.dp)) {
            val w = maxWidth / options.size
            val x by animateDpAsState(w * idx.coerceAtLeast(0), spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow), label = "seg")
            if (idx >= 0) Box(Modifier.offset(x = x).width(w).fillMaxHeight().glow(accent.copy(alpha = 0.3f), 6.dp, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp)).background(accent))
            Row(Modifier.fillMaxSize()) {
                options.forEachIndexed { k, (key, label) ->
                    val on = k == idx
                    val ink by animateColorAsState(if (on) LocalAccentInk.current else C.ink.copy(alpha = 0.75f), label = "segInk")
                    Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(12.dp)).clickable { onPick(key) }, contentAlignment = Alignment.Center) {
                        T(label, 13, if (on) FontWeight.Black else FontWeight.Bold, ink, maxLines = 1)
                    }
                }
            }
        }
        return
    }
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(options.size) { k ->
            val (key, label) = options[k]
            val on = key == selected
            val bg by animateColorAsState(if (on) accent else C.card2, label = "chip")
            Box(Modifier.height(40.dp).then(if (on) Modifier.glow(accent.copy(alpha = 0.3f), 6.dp, RoundedCornerShape(14.dp)) else Modifier)
                .clip(RoundedCornerShape(14.dp)).background(bg).border(1.dp, if (on) Color.Transparent else C.line2, RoundedCornerShape(14.dp))
                .clickable { onPick(key) }.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                T(label, 13, if (on) FontWeight.Black else FontWeight.Bold, if (on) LocalAccentInk.current else C.ink, maxLines = 1)
            }
        }
    }
}

/** An on / off choice: its name beside a small switch. */
@Composable
internal fun Toggle(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    val accent = LocalAccent.current
    val track by animateColorAsState(if (on) accent else C.line2, label = "track")
    val knob by animateDpAsState(if (on) 14.dp else 2.dp, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "knob")
    Row(Modifier.height(40.dp).clip(RoundedCornerShape(14.dp)).background(if (on) accent.copy(alpha = 0.12f) else C.card2)
        .border(1.dp, if (on) accent.copy(alpha = 0.6f) else C.line2, RoundedCornerShape(14.dp)).clickable { onChange(!on) }.padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        // the switch drawn with its knob on the far side when on, in reading order
        Box(Modifier.width(30.dp).height(18.dp).clip(RoundedCornerShape(9.dp)).background(track)) {
            Box(Modifier.offset(x = knob).align(Alignment.CenterStart).size(14.dp).clip(CircleShape).background(Color.White))
        }
        T(label, 13, FontWeight.Bold, if (on) C.ink else C.mute, maxLines = 1)
    }
}
