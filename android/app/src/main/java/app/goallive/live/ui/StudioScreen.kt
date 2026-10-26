package app.goallive.live.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.goallive.live.L
import app.goallive.live.data.Store
import app.goallive.live.data.i
import app.goallive.live.data.s
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import kotlin.math.sin

/** The bottom bar's mark for the studio: a board with a run and a pass. */
val StudioIcon: ImageVector by lazy {
    val b = ImageVector.Builder("studio", 24.dp, 24.dp, 24f, 24f)
    for (p in listOf("M5 4h14a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z", "M12 4v16", "M7 15c1.5-4 4-5 8-6", "M13 7.5l2 1.5-1.5 2"))
        b.addPath(pathData = addPathNodes(p), stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
    b.build()
}

private fun toolName(t: String) = when (t) {
    "move" -> L("جابه‌جایی"); "pass" -> L("پاس"); "run" -> L("حرکت"); "dribble" -> L("دریبل"); "curve" -> L("منحنی"); "lob" -> L("پاس بلند")
    "both" -> L("دوطرفه"); "zone" -> L("ناحیه"); "rect" -> L("مستطیل"); "circle" -> L("دایره"); "dist" -> L("فاصله"); "hline" -> L("خط دفاع")
    "spot" -> L("ذره‌بین"); "route" -> L("مسیر"); "analyze" -> L("تحلیل"); else -> L("متن")
}

private val TOOLS = listOf("move", "analyze", "route", "pass", "run", "dribble", "curve", "lob", "both", "zone", "rect", "circle", "dist", "hline", "spot", "text")

/**
 * "استودیو تاکتیک": two sides on a pitch (real clubs with their last line-up and squad, or made up), moved by hand,
 * with passes, runs, dribbles, curves, zones, distances, lines, notes and a spotlight on layers; saved on the phone.
 */
@Composable
fun StudioScreen() {
    val board = remember { StudioBoard() }
    val ed = remember { StudioEditor() }
    var ready by remember { mutableStateOf(false) }
    var teamSheet by remember { mutableIntStateOf(-1) }
    var library by remember { mutableStateOf(false) }
    var viewSheet by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val saved = Store.prefs().getString("studio.current", null)
        if (saved == null || runCatching { board.load(JSONObject(saved)) }.isFailure || board.players.isEmpty()) board.fresh()
        board.reset(); ready = true
    }
    // playing the move: time runs through the steps, each at its own length, at the chosen speed; slower into a shot,
    // and a pause on each event while it shows
    LaunchedEffect(ed.playing) {
        if (!ed.playing) return@LaunchedEffect
        board.syncOut()
        val n = board.frames.size
        if (n < 2) { ed.playing = false; return@LaunchedEffect }
        if (ed.playT == null || (ed.playT ?: 0f) >= n - 1.001f) { ed.playT = 0f; ed.result = null; ed.fx = null }
        var reached = kotlin.math.floor(ed.playT ?: 0f).toInt()
        var last = withFrameNanos { it }
        while (ed.playing) {
            withFrameNanos { now ->
                val dt = ((now - last) / 1e9f).coerceAtMost(0.05f); last = now
                ed.fx = ed.fx?.let { (i, sec) -> if (sec + dt > 2.6f) null else i to sec + dt }
                if (ed.hold > 0f) { ed.hold -= dt; return@withFrameNanos }
                val t = ed.playT ?: 0f
                if (t >= n - 1) {
                    if (StudioPrefs.loop) { ed.playT = 0f; reached = 0 } else ed.playing = false
                    return@withFrameNanos
                }
                val k = kotlin.math.floor(t).toInt().coerceIn(0, n - 2)
                val next = board.frames[k + 1]
                val slow = if (next.ev?.kind == "shot" && t - k > 0.55f) 0.38f else 1f
                var nt = t + dt * StudioPrefs.speed * slow / next.dur.coerceAtLeast(0.2f)
                if (nt >= k + 1 && reached != k + 1) {
                    reached = k + 1; nt = (k + 1).toFloat()
                    next.ev?.let { e ->
                        ed.fx = (k + 1) to 0f
                        ed.hold = when { e.kind == "shot" && e.outcome == "goal" -> 2.1f; e.kind == "offside" -> 1.2f; e.kind == "shot" -> 1.1f; else -> 0.7f }
                        if (e.kind == "shot") ed.result = e
                    }
                }
                ed.playT = nt.coerceAtMost((n - 1).toFloat())
            }
        }
    }
    // full screen: no bars, the pitch alone with the playback controls
    val full = StudioChrome.full
    androidx.activity.compose.BackHandler(enabled = full) { StudioChrome.full = false; StudioChrome.landscape = false }
    val view = androidx.compose.ui.platform.LocalView.current
    DisposableEffect(full) {
        val win = (view.context as? android.app.Activity)?.window
        val ctl = win?.let { androidx.core.view.WindowCompat.getInsetsController(it, view) }
        if (full) {
            ctl?.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            ctl?.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
        onDispose { ctl?.show(androidx.core.view.WindowInsetsCompat.Type.systemBars()) }
    }
    DisposableEffect(Unit) { onDispose { StudioChrome.full = false; StudioChrome.landscape = false } }
    // turned on its side: the screen held in landscape while it lasts
    val wide = full && StudioChrome.landscape
    DisposableEffect(wide) {
        val act = view.context as? android.app.Activity
        if (wide) act?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose { act?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }
    if (full) { FullStudio(board, ed, ready); SheetsOf(board, ed, teamSheet, library, viewSheet, { teamSheet = -1 }, { library = false }, { viewSheet = false }); return }
    Column(Modifier.fillMaxSize().padding(bottom = BottomSpace - 14.dp)) {
        // title and the board's actions
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 8.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                T(L("استودیو تاکتیک"), 19, FontWeight.Black, maxLines = 1)
                if (board.title.isNotBlank()) T(board.title, 12, color = C.mute, maxLines = 1)
            }
            IconBtn(Ic.list, L("تاکتیک‌های من"), C.ink, C.card2, 19.dp) { library = true }
            Spacer(Modifier.width(6.dp))
            UndoBtn(true, board.canUndo) { board.undo() }
            Spacer(Modifier.width(6.dp))
            UndoBtn(false, board.canRedo) { board.redo() }
            Spacer(Modifier.width(6.dp))
            IconBtn(Ic.sliders, L("نما"), C.ink, C.card2, 19.dp) { viewSheet = true }
            Spacer(Modifier.width(6.dp))
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(C.card2).clickable { StudioChrome.full = true }, contentAlignment = Alignment.Center) {
                FullGlyph(true, C.ink)
            }
        }
        // the two sides
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TeamChip(board.home, Modifier.weight(1f)) { teamSheet = 0 }
            T(L("مقابل"), 12, FontWeight.Bold, C.mute)
            TeamChip(board.away, Modifier.weight(1f), dim = !board.showAway) { teamSheet = 1 }
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(if (board.showAway) C.card2 else Color.Transparent)
                .border(1.dp, C.line2, RoundedCornerShape(12.dp)).clickable { board.showAway = !board.showAway; board.commit() }, contentAlignment = Alignment.Center) {
                EyeGlyph(board.showAway)
            }
        }
        // the pitch
        PitchArea(board, ed, ready, Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp))
        Timeline(board, ed)
        ContextBar(board, ed)
        // the tools
        LazyRow(Modifier.fillMaxWidth().padding(top = 6.dp), contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(TOOLS.size) { k ->
                val t = TOOLS[k]
                val on = ed.tool == t
                val accent = LocalAccent.current
                Column(Modifier.width(62.dp).clip(RoundedCornerShape(16.dp)).background(if (on) accent.copy(alpha = 0.18f) else C.card2)
                    .border(if (on) 1.5.dp else 1.dp, if (on) accent else C.line2, RoundedCornerShape(16.dp))
                    .clickable { if (t == "analyze") ed.analysis = true else { ed.tool = t; ed.selMark = -1 } }.padding(vertical = 7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    ToolGlyph(t, if (on) accent else C.ink)
                    T(toolName(t), 10, FontWeight.Bold, if (on) accent else C.mute, maxLines = 1, modifier = Modifier.padding(top = 3.dp))
                }
            }
        }
    }
    SheetsOf(board, ed, teamSheet, library, viewSheet, { teamSheet = -1 }, { library = false }, { viewSheet = false })
}

/** The sheets and questions the studio opens. */
@Composable
private fun SheetsOf(board: StudioBoard, ed: StudioEditor, teamSheet: Int, library: Boolean, viewSheet: Boolean, closeTeam: () -> Unit, closeLibrary: () -> Unit, closeView: () -> Unit) {
    if (teamSheet >= 0) TeamSheet(board, teamSheet, closeTeam)
    if (library) LibrarySheet(board, ed, closeLibrary)
    if (viewSheet) ViewSheet(closeView)
    ed.editPlayer?.let { p -> PlayerSheet(board, ed, p) { ed.editPlayer = null } }
    if (ed.frameMenu in board.frames.indices) FrameSheet(board, ed, ed.frameMenu) { ed.frameMenu = -1 }
    if (ed.analysis) AnalysisSheet(board) { ed.analysis = false }
    ed.textAt?.let { at ->
        TextAsk(L("نوشته روی زمین"), "") { s ->
            if (s != null && s.isNotBlank()) { board.marks += SMark("text", listOf(at), ed.pen, ed.width, ed.alpha, s.trim(), ed.layer); board.commit() }
            ed.textAt = null
        }
    }
}

/** Whether the studio is full screen (the app's bars hide). */
object StudioChrome {
    var full by mutableStateOf(false)
    /** Full screen on its side: the phone turned and the pitch lying left to right. */
    var landscape by mutableStateOf(false)
}

/** The pitch with what goes over it: the step's caption, the shot's result, the zoom. */
@Composable
private fun PitchArea(board: StudioBoard, ed: StudioEditor, ready: Boolean, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        if (ready) StudioPitch(board, ed)
        // the step's note, like a caption
        val noteAt = ed.playT?.let { t -> board.frames.getOrNull(kotlin.math.ceil(t).toInt().coerceAtMost(board.frames.size - 1)) } ?: board.frames.getOrNull(board.cur)
        noteAt?.note?.takeIf { it.isNotBlank() }?.let { n ->
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp, start = 24.dp, end = 24.dp).clip(RoundedCornerShape(14.dp))
                .background(Color.Black.copy(alpha = 0.72f)).padding(horizontal = 14.dp, vertical = 8.dp)) { T(n, 13, FontWeight.Bold, Color.White, maxLines = 2) }
        }
        // the last shot: what came of it and its chance
        ed.result?.takeIf { !ed.playing || ed.fx == null }?.let { r ->
            val col = when (r.outcome) { "goal" -> Color(0xFFF5C542); "save" -> Color(0xFF35B6FF); "post" -> Color.White; "block" -> Color(0xFFFF9F43); else -> Color(0xFFB0B8C4) }
            Row(Modifier.align(Alignment.TopCenter).padding(top = 10.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xE60B0F14))
                .border(1.dp, col.copy(alpha = 0.6f), RoundedCornerShape(18.dp)).clickable { ed.result = null }.padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                T(outcomeName(r.outcome), 18, FontWeight.Black, col)
                Column {
                    T(L("احتمال گل این شوت: {0}٪", fa(Math.round(r.xg * 100))), 12, FontWeight.Bold, Color.White)
                    T(when (r.decide) { "auto" -> L("نتیجه از روی احتمال"); "luck" -> L("نتیجه با تاس"); else -> L("نتیجه‌ی دستی") }, 11, color = Color.White.copy(alpha = 0.65f))
                }
            }
        }
        if (ed.zoom > 1.01f && !ed.playing) Box(Modifier.align(Alignment.TopEnd).padding(8.dp).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.6f))
            .clickable { ed.zoom = 1f; ed.pan = Offset.Zero }.padding(horizontal = 12.dp, vertical = 6.dp)) {
            T(L("بزرگ‌نمایی {0}×", fa(String.format(java.util.Locale.US, "%.1f", ed.zoom))), 12, FontWeight.Bold, Color.White)
        }
    }
}

/** Full screen: the pitch on black, and under it the steps, play, back and forward, and the way out. */
@Composable
private fun FullStudio(board: StudioBoard, ed: StudioEditor, ready: Boolean) {
    val accent = LocalAccent.current
    Box(Modifier.fillMaxSize().background(Color(0xFF05070A))) {
        PitchArea(board, ed, ready, Modifier.fillMaxSize().padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = if (StudioChrome.landscape) 70.dp else 84.dp))
        Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RoundBtn(Color.White.copy(alpha = 0.12f), { StudioChrome.full = false; StudioChrome.landscape = false }) { FullGlyph(false, Color.White) }
            Spacer(Modifier.width(8.dp))
            RoundBtn(if (StudioChrome.landscape) accent else Color.White.copy(alpha = 0.12f), { StudioChrome.landscape = !StudioChrome.landscape }) {
                RotateGlyph(if (StudioChrome.landscape) LocalAccentInk.current else Color.White)
            }
            Spacer(Modifier.weight(1f))
            val n = board.frames.size
            RoundBtn(Color.White.copy(alpha = 0.12f), { ed.playing = false; ed.playT = null; board.show(board.cur + (if (app.goallive.live.Lang.en) -1 else 1)) }) { StepGlyph(!app.goallive.live.Lang.en) }
            Box(Modifier.size(58.dp).glow(accent.copy(alpha = 0.4f), 10.dp, CircleShape).clip(CircleShape).background(if (n >= 2) accent else Color.White.copy(alpha = 0.12f))
                .clickable(enabled = n >= 2) { if (ed.playing) ed.playing = false else { board.syncOut(); ed.playing = true } }, contentAlignment = Alignment.Center) {
                PlayGlyph(ed.playing, if (n >= 2) LocalAccentInk.current else Color.White.copy(alpha = 0.4f))
            }
            RoundBtn(Color.White.copy(alpha = 0.12f), { ed.playing = false; ed.playT = null; board.show(board.cur + (if (app.goallive.live.Lang.en) 1 else -1)) }) { StepGlyph(app.goallive.live.Lang.en) }
            Spacer(Modifier.weight(1f))
            Box(Modifier.height(44.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.12f)).clickable {
                StudioPrefs.speed = when (StudioPrefs.speed) { 0.5f -> 1f; 1f -> 1.8f; else -> 0.5f }; StudioPrefs.keep()
            }.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                T(fa(when (StudioPrefs.speed) { 0.5f -> "0.5"; 1f -> "1"; else -> "1.8" }) + "×", 14, FontWeight.Black, Color.White)
            }
        }
        // which step, top corner
        Box(Modifier.align(Alignment.TopStart).statusBarsPadding().padding(12.dp).clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 10.dp, vertical = 4.dp)) {
            val at = ed.playT?.let { kotlin.math.round(it).toInt() } ?: board.cur
            T(L("مرحله‌ی {0} از {1}", fa(at + 1), fa(board.frames.size)), 12, FontWeight.Bold, Color.White)
        }
    }
}

@Composable
private fun RoundBtn(bg: Color, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(Modifier.size(44.dp).clip(CircleShape).background(bg).clickable(onClick = onClick), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun PlayGlyph(playing: Boolean, ink: Color) {
    Canvas(Modifier.size(22.dp)) {
        val w = size.width
        if (playing) { drawRect(ink, Offset(w * 0.18f, w * 0.12f), androidx.compose.ui.geometry.Size(w * 0.22f, w * 0.76f)); drawRect(ink, Offset(w * 0.6f, w * 0.12f), androidx.compose.ui.geometry.Size(w * 0.22f, w * 0.76f)) }
        else drawPath(Path().apply { moveTo(w * 0.22f, w * 0.1f); lineTo(w * 0.9f, w * 0.5f); lineTo(w * 0.22f, w * 0.9f); close() }, ink)
    }
}

/** A step to the left or right: a bar and a triangle. */
@Composable
private fun StepGlyph(right: Boolean) {
    Canvas(Modifier.size(18.dp)) {
        val w = size.width
        fun X(x: Float) = if (right) x * w else (1 - x) * w
        drawPath(Path().apply { moveTo(X(0.15f), w * 0.15f); lineTo(X(0.7f), w * 0.5f); lineTo(X(0.15f), w * 0.85f); close() }, Color.White)
        drawRect(Color.White, Offset(minOf(X(0.74f), X(0.88f)), w * 0.15f), androidx.compose.ui.geometry.Size(w * 0.14f, w * 0.7f))
    }
}

/** A phone turning on its side. */
@Composable
private fun RotateGlyph(ink: Color) {
    Canvas(Modifier.size(20.dp)) {
        val w = size.width; val s = Stroke(1.7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawRoundRect(ink, Offset(w * 0.12f, w * 0.38f), androidx.compose.ui.geometry.Size(w * 0.76f, w * 0.48f), androidx.compose.ui.geometry.CornerRadius(w * 0.08f), style = s)
        val arc = Path().apply { moveTo(w * 0.2f, w * 0.26f); quadraticBezierTo(w * 0.5f, w * 0.02f, w * 0.82f, w * 0.22f) }
        drawPath(arc, ink, style = s)
        drawLine(ink, Offset(w * 0.82f, w * 0.22f), Offset(w * 0.66f, w * 0.2f), s.width, StrokeCap.Round)
        drawLine(ink, Offset(w * 0.82f, w * 0.22f), Offset(w * 0.78f, w * 0.06f), s.width, StrokeCap.Round)
    }
}

/** Four corners pointing out (to full screen) or in (back). */
@Composable
private fun FullGlyph(out: Boolean, ink: Color) {
    Canvas(Modifier.size(20.dp)) {
        val w = size.width; val s = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val a = 0.12f; val b = 0.4f
        for ((cx, cy) in listOf(0f to 0f, 1f to 0f, 0f to 1f, 1f to 1f)) {
            fun P(x: Float, y: Float) = Offset((if (cx == 0f) x else 1 - x) * w, (if (cy == 0f) y else 1 - y) * w)
            val p = if (out) Path().apply { moveTo(P(a, b).x, P(a, b).y); lineTo(P(a, a).x, P(a, a).y); lineTo(P(b, a).x, P(b, a).y) }
                else Path().apply { moveTo(P(b, a).x, P(b, a).y); lineTo(P(b, b).x, P(b, b).y); lineTo(P(a, b).x, P(a, b).y) }
            drawPath(p, ink, style = s)
        }
    }
}

/* ---------- small parts ---------- */

@Composable
private fun TeamChip(t: STeam, modifier: Modifier, dim: Boolean = false, onClick: () -> Unit) {
    Row(modifier.height(40.dp).clip(RoundedCornerShape(14.dp)).background(C.card2).border(1.dp, C.line2, RoundedCornerShape(14.dp)).clickable(onClick = onClick)
        .padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (t.teamId > 0) Crest(t.teamId, 22.dp) else Box(Modifier.size(18.dp).clip(CircleShape).background(Color(t.color)).border(1.dp, Color.White.copy(alpha = 0.6f), CircleShape))
        T(t.name, 13, FontWeight.ExtraBold, if (dim) C.faint else C.ink, maxLines = 1, modifier = Modifier.weight(1f))
        Icon(Ic.down, null, Modifier.size(14.dp), tint = C.mute)
    }
}

@Composable
private fun UndoBtn(undo: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(C.card2).clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        val ink = if (enabled) C.ink else C.faint
        Canvas(Modifier.size(20.dp)) {
            val w = size.width; val s = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            // a hooked arrow, pointing back for undo
            val dir = if (undo) 1f else -1f
            fun X(x: Float) = if (dir > 0) x * w else (1 - x) * w
            val p = Path().apply { moveTo(X(0.32f), w * 0.22f); lineTo(X(0.14f), w * 0.4f); lineTo(X(0.32f), w * 0.58f) }
            drawPath(p, ink, style = s)
            val arc = Path().apply { moveTo(X(0.16f), w * 0.4f); lineTo(X(0.62f), w * 0.4f); quadraticBezierTo(X(0.88f), w * 0.4f, X(0.88f), w * 0.62f); quadraticBezierTo(X(0.88f), w * 0.84f, X(0.6f), w * 0.84f) }
            drawPath(arc, ink, style = s)
        }
    }
}

@Composable
private fun EyeGlyph(open: Boolean) {
    Canvas(Modifier.size(20.dp)) {
        val w = size.width; val ink = if (open) C.ink else C.faint
        val s = Stroke(1.7.dp.toPx(), cap = StrokeCap.Round)
        val p = Path().apply { moveTo(w * 0.08f, w * 0.5f); quadraticBezierTo(w * 0.5f, w * 0.08f, w * 0.92f, w * 0.5f); quadraticBezierTo(w * 0.5f, w * 0.92f, w * 0.08f, w * 0.5f) }
        drawPath(p, ink, style = s); drawCircle(ink, w * 0.13f, Offset(w / 2, w / 2))
        if (!open) drawLine(ink, Offset(w * 0.15f, w * 0.85f), Offset(w * 0.85f, w * 0.15f), s.width, cap = StrokeCap.Round)
    }
}

/** Each tool drawn as what it makes. */
@Composable
private fun ToolGlyph(t: String, ink: Color) {
    Canvas(Modifier.size(24.dp)) {
        val w = size.width
        val s = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun head(x: Float, y: Float, ang: Float) {
            val a = Math.toRadians(ang.toDouble()); val l = w * 0.24f
            drawLine(ink, Offset(x, y), Offset(x - l * kotlin.math.cos(a - 0.5).toFloat(), y - l * kotlin.math.sin(a - 0.5).toFloat()), s.width, StrokeCap.Round)
            drawLine(ink, Offset(x, y), Offset(x - l * kotlin.math.cos(a + 0.5).toFloat(), y - l * kotlin.math.sin(a + 0.5).toFloat()), s.width, StrokeCap.Round)
        }
        when (t) {
            "move" -> {
                drawCircle(ink, w * 0.16f, Offset(w * 0.5f, w * 0.5f))
                for (a in listOf(0f, 90f, 180f, 270f)) {
                    val r = Math.toRadians(a.toDouble()); val x = w / 2 + w * 0.42f * kotlin.math.cos(r).toFloat(); val y = w / 2 + w * 0.42f * kotlin.math.sin(r).toFloat()
                    head(x, y, a)
                }
            }
            "analyze" -> {
                // a pitch cut into two sides' spaces, with a pass between two men
                drawRect(ink.copy(alpha = 0.22f), Offset(w * 0.1f, w * 0.15f), androidx.compose.ui.geometry.Size(w * 0.45f, w * 0.7f))
                drawRect(ink, Offset(w * 0.1f, w * 0.15f), androidx.compose.ui.geometry.Size(w * 0.8f, w * 0.7f), style = s)
                drawLine(ink, Offset(w * 0.55f, w * 0.15f), Offset(w * 0.5f, w * 0.85f), s.width)
                drawCircle(ink, w * 0.07f, Offset(w * 0.3f, w * 0.62f)); drawCircle(ink, w * 0.07f, Offset(w * 0.72f, w * 0.36f))
                drawLine(ink, Offset(w * 0.3f, w * 0.62f), Offset(w * 0.72f, w * 0.36f), s.width * 0.7f, StrokeCap.Round, PathEffect.dashPathEffect(floatArrayOf(4f, 4f)))
            }
            "route" -> {
                drawCircle(ink, w * 0.12f, Offset(w * 0.2f, w * 0.78f))
                val p = Path().apply { moveTo(w * 0.3f, w * 0.7f); quadraticBezierTo(w * 0.75f, w * 0.75f, w * 0.55f, w * 0.42f); quadraticBezierTo(w * 0.4f, w * 0.15f, w * 0.86f, w * 0.16f) }
                drawPath(p, ink, style = Stroke(s.width, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(w * 0.1f, w * 0.08f))))
                head(w * 0.86f, w * 0.16f, 0f)
            }
            "pass" -> { drawLine(ink, Offset(w * 0.12f, w * 0.8f), Offset(w * 0.86f, w * 0.18f), s.width, StrokeCap.Round); head(w * 0.86f, w * 0.18f, -40f) }
            "both" -> { drawLine(ink, Offset(w * 0.14f, w * 0.78f), Offset(w * 0.86f, w * 0.22f), s.width, StrokeCap.Round); head(w * 0.86f, w * 0.22f, -38f); head(w * 0.14f, w * 0.78f, 142f) }
            "run" -> { drawLine(ink, Offset(w * 0.12f, w * 0.8f), Offset(w * 0.86f, w * 0.18f), s.width, StrokeCap.Round, PathEffect.dashPathEffect(floatArrayOf(w * 0.12f, w * 0.1f))); head(w * 0.86f, w * 0.18f, -40f) }
            "dribble" -> {
                val p = Path(); for (k in 0..30) { val x = w * (0.1f + 0.72f * k / 30); val y = w * 0.5f + w * 0.13f * sin(k / 30f * 4 * Math.PI.toFloat()); if (k == 0) p.moveTo(x, y) else p.lineTo(x, y) }
                drawPath(p, ink, style = s); head(w * 0.9f, w * 0.5f, 0f)
            }
            "curve", "lob" -> {
                val p = Path().apply { moveTo(w * 0.1f, w * 0.82f); quadraticBezierTo(w * 0.2f, w * 0.1f, w * 0.88f, w * 0.3f) }
                drawPath(p, ink, style = if (t == "lob") Stroke(s.width, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, w * 0.14f))) else s)
                head(w * 0.88f, w * 0.3f, 18f)
            }
            "zone" -> {
                val p = Path().apply { moveTo(w * 0.2f, w * 0.3f); quadraticBezierTo(w * 0.6f, w * 0.05f, w * 0.85f, w * 0.4f); quadraticBezierTo(w * 0.9f, w * 0.85f, w * 0.45f, w * 0.85f); quadraticBezierTo(w * 0.05f, w * 0.75f, w * 0.2f, w * 0.3f) }
                drawPath(p, ink.copy(alpha = 0.25f)); drawPath(p, ink, style = s)
            }
            "rect" -> { drawRect(ink.copy(alpha = 0.25f), Offset(w * 0.15f, w * 0.25f), androidx.compose.ui.geometry.Size(w * 0.7f, w * 0.5f)); drawRect(ink, Offset(w * 0.15f, w * 0.25f), androidx.compose.ui.geometry.Size(w * 0.7f, w * 0.5f), style = s) }
            "circle" -> { drawCircle(ink.copy(alpha = 0.25f), w * 0.36f, Offset(w / 2, w / 2)); drawCircle(ink, w * 0.36f, Offset(w / 2, w / 2), style = s) }
            "dist" -> {
                drawLine(ink, Offset(w * 0.12f, w * 0.55f), Offset(w * 0.88f, w * 0.55f), s.width, StrokeCap.Round)
                drawLine(ink, Offset(w * 0.12f, w * 0.38f), Offset(w * 0.12f, w * 0.72f), s.width, StrokeCap.Round); drawLine(ink, Offset(w * 0.88f, w * 0.38f), Offset(w * 0.88f, w * 0.72f), s.width, StrokeCap.Round)
            }
            "hline" -> {
                drawLine(ink, Offset(w * 0.05f, w * 0.5f), Offset(w * 0.95f, w * 0.5f), s.width, StrokeCap.Round, PathEffect.dashPathEffect(floatArrayOf(w * 0.14f, w * 0.08f)))
                for (x in listOf(0.22f, 0.5f, 0.78f)) drawCircle(ink, w * 0.08f, Offset(w * x, w * 0.5f))
            }
            "spot" -> {
                drawCircle(ink, w * 0.13f, Offset(w / 2, w * 0.62f))
                val p = Path().apply { moveTo(w * 0.36f, w * 0.08f); lineTo(w * 0.64f, w * 0.08f); lineTo(w * 0.86f, w * 0.78f); lineTo(w * 0.14f, w * 0.78f); close() }
                drawPath(p, ink.copy(alpha = 0.22f)); drawOval(ink, Offset(w * 0.14f, w * 0.7f), androidx.compose.ui.geometry.Size(w * 0.72f, w * 0.18f), style = s)
            }
            else -> {
                drawLine(ink, Offset(w * 0.2f, w * 0.2f), Offset(w * 0.8f, w * 0.2f), s.width, StrokeCap.Round)
                drawLine(ink, Offset(w * 0.5f, w * 0.2f), Offset(w * 0.5f, w * 0.84f), s.width, StrokeCap.Round)
            }
        }
    }
}

/** Under the pitch: the pen for a drawing tool, the selected drawing's look (and delete), or the selected players' count. */
@Composable
private fun ContextBar(board: StudioBoard, ed: StudioEditor) {
    val sel = board.marks.getOrNull(ed.selMark)
    val drawing = ed.tool !in setOf("move", "spot")
    Column(Modifier.fillMaxWidth().heightIn(min = 46.dp).padding(top = 6.dp)) {
        when {
            sel != null -> {
                fun set(m: SMark) { board.marks[ed.selMark] = m; board.commit() }
                PenRow(sel.color, sel.width, sel.alpha, sel.layer, { set(sel.copy(color = it)) }, { set(sel.copy(width = it)) }, { set(sel.copy(alpha = it)) }, { set(sel.copy(layer = it)) }) {
                    board.marks.removeAt(ed.selMark); ed.selMark = -1; board.commit()
                }
            }
            drawing -> PenRow(ed.pen, ed.width, ed.alpha, ed.layer, { ed.pen = it }, { ed.width = it }, { ed.alpha = it }, { ed.layer = it }, null)
            ed.selected.size > 1 -> Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                T(L("{0} بازیکن انتخاب شده؛ با کشیدن یکی، همه جابه‌جا می‌شوند", fa(ed.selected.size)), 12, FontWeight.Bold, C.mute, modifier = Modifier.weight(1f))
                Pill(L("لغو انتخاب"), PillKind.Plain, onClick = { ed.selected.clear() })
            }
            ed.tool == "move" && board.cur > 0 && ed.playT == null -> Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val ev = board.frames.getOrNull(board.cur)?.ev
                T(L("اتفاق مرحله‌ی {0}:", fa(board.cur + 1)), 12, FontWeight.Bold, C.mute)
                for ((kind, label) in listOf("shot" to L("شوت"), "tackle" to L("تکل"), "intercept" to L("قطع توپ"), "offside" to L("آفساید"))) {
                    val on = ev?.kind == kind
                    Box(Modifier.height(34.dp).clip(RoundedCornerShape(10.dp)).background(if (on) LocalAccent.current else C.card2)
                        .clickable { if (on) ed.frameMenu = board.cur else board.setEvent(board.cur, kind) }.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                        T(label, 12, FontWeight.Bold, if (on) LocalAccentInk.current else C.ink)
                    }
                }
                if (ev?.kind == "shot") T(outcomeName(ev.outcome) + " · " + fa(Math.round(ev.xg * 100)) + "٪", 12, FontWeight.Black, LocalAccent.current)
            }
            else -> T(when (ed.tool) {
                "spot" -> L("روی بازیکن بزن تا نور روی او بیفتد؛ دوباره بزن تا خاموش شود")
                "route" -> L("از روی یک بازیکن، مسیر دویدنش را با انگشت بکش؛ در مرحله‌ی اول، مرحله‌ی تازه‌ای ساخته می‌شود")
                else -> L("بازیکن، توپ یا هر رسمی را بکش؛ روی بازیکن بزن برای ویرایش؛ دور چند بازیکن کادر بکش برای انتخاب گروهی؛ با دو انگشت بزرگ‌نمایی کن")
            }, 11, color = C.mute, modifier = Modifier.padding(horizontal = 16.dp), maxLines = 2)
        }
    }
}

@Composable
private fun PenRow(color: Int, width: Float, alpha: Float, layer: String, onColor: (Int) -> Unit, onWidth: (Float) -> Unit, onAlpha: (Float) -> Unit,
                   onLayer: (String) -> Unit, onDelete: (() -> Unit)?) {
    val accent = LocalAccent.current
    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onDelete != null) item {
            Box(Modifier.size(34.dp).clip(CircleShape).background(C.live.copy(alpha = 0.18f)).clickable(onClick = onDelete), contentAlignment = Alignment.Center) {
                Icon(Ic.x, L("حذف"), Modifier.size(16.dp), tint = C.live)
            }
        }
        items(PENS.size) { k ->
            val c = PENS[k]; val on = c == color
            Box(Modifier.size(30.dp).clip(CircleShape).background(Color(c)).border(if (on) 3.dp else 1.dp, if (on) accent else C.line2, CircleShape).clickable { onColor(c) })
        }
        item { Spacer(Modifier.width(6.dp)) }
        items(3) { k ->
            val wv = listOf(2f, 3.5f, 6f)[k]; val on = kotlin.math.abs(width - wv) < 0.6f
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(if (on) accent.copy(alpha = 0.2f) else C.card2).clickable { onWidth(wv) }, contentAlignment = Alignment.Center) {
                Box(Modifier.width(18.dp).height((1.5f + k * 2f).dp).clip(RoundedCornerShape(3.dp)).background(C.ink))
            }
        }
        item {
            val on = alpha < 0.9f
            Box(Modifier.height(34.dp).clip(RoundedCornerShape(10.dp)).background(if (on) accent.copy(alpha = 0.2f) else C.card2).clickable { onAlpha(if (on) 1f else 0.55f) }
                .padding(horizontal = 10.dp), contentAlignment = Alignment.Center) { T(L("کم‌رنگ"), 12, FontWeight.Bold, if (on) accent else C.ink) }
        }
        item { Spacer(Modifier.width(6.dp)) }
        items(LAYERS.size) { k ->
            val l = LAYERS[k]; val on = l == layer
            Box(Modifier.height(34.dp).clip(RoundedCornerShape(10.dp)).background(if (on) accent else C.card2).clickable { onLayer(l) }.padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center) { T(layerName(l), 12, FontWeight.Bold, if (on) LocalAccentInk.current else C.ink) }
        }
    }
}

/* ---------- sheets ---------- */

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun Sheet(onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onClose, containerColor = C.cardSolid, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp).verticalScroll(rememberScrollState()), content = content)
    }
}

/** Picking a side: a real club (its last line-up or its squad) or a made-up one; and its formation. */
@Composable
private fun TeamSheet(board: StudioBoard, side: Int, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf("club") }
    var q by remember { mutableStateOf("") }
    var found by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val team = if (side == 0) board.home else board.away
    LaunchedEffect(q) {
        if (q.trim().length < 2) { found = emptyList(); return@LaunchedEffect }
        delay(300)
        found = runCatching { search(q.trim()).teams }.getOrDefault(emptyList()).take(12)
    }
    Sheet(onClose) {
        T(if (side == 0) L("تیم پایین زمین") else L("تیم بالای زمین"), 18, FontWeight.Black, modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp))
        StudioChips(listOf("club" to L("باشگاه یا تیم ملی"), "own" to L("تیم دلخواه")), mode) { mode = it }
        if (mode == "club") {
            Box(Modifier.padding(horizontal = 16.dp)) { SearchField(q, L("نام تیم؛ فارسی یا انگلیسی…")) { q = it } }
            if (busy) T(L("در حال آوردن ترکیب…"), 13, color = C.mute, modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp))
            if (failed) T(L("آوردن این تیم ممکن نشد؛ دوباره امتحان کن"), 13, color = C.live, modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp))
            found.forEach { t ->
                val id = t.s("id")?.toIntOrNull() ?: t.i("id") ?: 0
                Row(Modifier.fillMaxWidth().clickable(enabled = !busy) {
                    busy = true; failed = false
                    scope.launch {
                        val r = runCatching { loadTeam(id, side) }
                        r.getOrNull()?.let { board.setSide(side, it); board.commit(); onClose() } ?: run { failed = true }
                        busy = false
                    }
                }.padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Crest(id, 28.dp)
                    T(teamName(id, t.s("name") ?: ""), 15, FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f))
                }
            }
            if (q.isBlank()) T(L("ترکیب آخرین بازی تیم با عکس و شماره‌ی بازیکن‌ها روی زمین می‌آید."), 12, color = C.mute, modifier = Modifier.padding(horizontal = 18.dp))
        } else {
            var name by remember { mutableStateOf(if (team.teamId == 0) team.name else "") }
            var color by remember { mutableIntStateOf(team.color) }
            StudioLabel(L("نام تیم"))
            Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.card2).border(1.dp, C.line2, RoundedCornerShape(14.dp)).padding(14.dp)) {
                if (name.isEmpty()) T(L("مثلاً: تیم من"), 14, color = C.faint)
                BasicTextField(name, { name = it.take(24) }, Modifier.fillMaxWidth(), singleLine = true, textStyle = vz(15.sp).copy(color = C.ink), cursorBrush = SolidColor(LocalAccent.current))
            }
            StudioLabel(L("رنگ پیراهن"))
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(KIT_COLORS.size) { k ->
                    val c = KIT_COLORS[k]; val on = c == color
                    Box(Modifier.size(38.dp).clip(CircleShape).background(Color(c)).border(if (on) 3.dp else 1.dp, if (on) LocalAccent.current else C.line2, CircleShape).clickable { color = c })
                }
            }
            Box(Modifier.padding(16.dp).fillMaxWidth().height(48.dp).clip(RoundedCornerShape(16.dp)).background(LocalAccent.current).clickable {
                val t = STeam(name.ifBlank { if (side == 0) L("تیم من") else L("حریف") }, color, inkFor(color), 0, team.formation)
                // a made-up side keeps the players already there, in its new colours
                val keep = board.players.filter { it.side == side }
                if (team.teamId != 0 || keep.size < 11) board.setSide(side, LoadedTeam(t, plainEleven(side, t.formation), emptyList()))
                else if (side == 0) board.home = t else board.away = t
                board.commit(); onClose()
            }, contentAlignment = Alignment.Center) { T(L("ثبت"), 15, FontWeight.Black, LocalAccentInk.current) }
        }
        StudioLabel(L("ترکیب"))
        StudioChips(STUDIO_FORMATIONS.map { it to fa(it) }, team.formation) { board.reform(side, it); board.commit() }
        Spacer(Modifier.height(10.dp))
    }
}

/** A player: name and number, the spotlight, swapping in someone from the squad, or taking him off. */
@Composable
private fun PlayerSheet(board: StudioBoard, ed: StudioEditor, p: SPlayer, onClose: () -> Unit) {
    var name by remember { mutableStateOf(p.name) }
    var num by remember { mutableStateOf(p.num) }
    val team = if (p.side == 0) board.home else board.away
    fun done() { if (name != p.name || num != p.num) { p.name = name; p.num = num; board.commit() }; onClose() }
    Sheet(::done) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (p.pid > 0) Face(p.pid, 46.dp, Color(team.color), 2.dp)
            Column(Modifier.weight(1f)) {
                T(p.name.ifBlank { L("بازیکن شماره {0}", fa(p.num)) }, 17, FontWeight.Black, maxLines = 1)
                T(team.name, 12, color = C.mute, maxLines = 1)
            }
        }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field(name, L("نام"), Modifier.weight(1f)) { name = it.take(20) }
            Field(num, L("شماره"), Modifier.width(90.dp)) { num = it.filter(Char::isLetterOrDigit).take(3) }
        }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Toggle(L("ذره‌بین روی او"), p.spot) { p.spot = it; board.commit() }
            Pill(L("برداشتن از زمین"), PillKind.Plain, onClick = { board.players.remove(p); ed.selected.remove(p.key); board.commit(); onClose() })
        }
        val squad = board.squads[team.teamId].orEmpty().filter { s -> board.players.none { it.pid == s.first && it.side == p.side } }
        if (squad.isNotEmpty()) {
            StudioLabel(L("تعویض با"))
            squad.forEach { (pid, n, number) ->
                Row(Modifier.fillMaxWidth().clickable {
                    p.pid = pid; p.name = n; if (number.isNotEmpty()) p.num = number; board.commit(); onClose()
                }.padding(horizontal = 18.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Face(pid, 32.dp, Color(team.color), 1.5.dp)
                    T(n, 14, FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f))
                    if (number.isNotEmpty()) T(fa(number), 13, FontWeight.Black, C.mute)
                }
            }
        }
    }
}

@Composable
private fun Field(v: String, hint: String, modifier: Modifier, onChange: (String) -> Unit) {
    Box(modifier.clip(RoundedCornerShape(14.dp)).background(C.card2).border(1.dp, C.line2, RoundedCornerShape(14.dp)).padding(14.dp)) {
        if (v.isEmpty()) T(hint, 14, color = C.faint)
        BasicTextField(v, onChange, Modifier.fillMaxWidth(), singleLine = true, textStyle = vz(15.sp).copy(color = C.ink), cursorBrush = SolidColor(LocalAccent.current))
    }
}

/** The tactics kept on the phone: start a new one, save this one, open or delete a saved one. */
@Composable
private fun LibrarySheet(board: StudioBoard, ed: StudioEditor, onClose: () -> Unit) {
    var list by remember { mutableStateOf(StudioLibrary.all()) }
    var asking by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf("ready") }
    Sheet(onClose) {
        T(L("کتابخانه‌ی تاکتیک"), 18, FontWeight.Black, modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp))
        StudioChips(listOf("ready" to L("سناریوهای آماده"), "mine" to L("تاکتیک‌های من")), tab) { tab = it }
        if (tab == "ready") {
            T(L("با تیم‌هایی که روی زمین هستند پخش می‌شود؛ بعد هر چیزی را می‌توانی تغییر بدهی."), 12, color = C.mute,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp))
            SCENARIOS.forEachIndexed { i, sc ->
                val accent = LocalAccent.current
                Row(Modifier.fillMaxWidth().clickable {
                    ed.playing = false; ed.playT = null; ed.result = null; ed.fx = null; ed.selected.clear(); ed.selMark = -1
                    board.loadScenario(sc); onClose(); ed.playing = true
                }.padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(accent.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                        T(fa(i + 1), 15, FontWeight.Black, accent)
                    }
                    Column(Modifier.weight(1f)) {
                        T(sc.name, 15, FontWeight.ExtraBold, maxLines = 1)
                        T(sc.about, 12, color = C.mute, maxLines = 2)
                    }
                    Box(Modifier.size(34.dp).clip(CircleShape).background(accent), contentAlignment = Alignment.Center) { PlayGlyph(false, LocalAccentInk.current) }
                }
            }
        } else {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill(L("ذخیره‌ی همین"), PillKind.Accent, onClick = { asking = true })
            Pill(L("تاکتیک تازه"), PillKind.Plain, onClick = { board.fresh(); board.commit(); onClose() })
        }
        if (list.isEmpty()) T(L("هنوز تاکتیکی ذخیره نکرده‌ای."), 13, color = C.mute, modifier = Modifier.padding(18.dp))
        list.forEach { t ->
            Row(Modifier.fillMaxWidth().clickable {
                runCatching { board.load(JSONObject(t.data)) }; board.commit(); onClose()
            }.padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(C.card2), contentAlignment = Alignment.Center) { Icon(StudioIcon, null, Modifier.size(20.dp), tint = C.ink) }
                Column(Modifier.weight(1f)) {
                    T(t.name, 15, FontWeight.Bold, maxLines = 1)
                    T(persianDate(t.time, "d MMMM") + " · " + timeOf(t.time), 12, color = C.mute)
                }
                IconBtn(Ic.x, L("حذف"), C.mute, size = 16.dp) { StudioLibrary.delete(t.id); list = StudioLibrary.all() }
            }
        }
        }
    }
    if (asking) TextAsk(L("نام تاکتیک"), board.title) { s ->
        asking = false
        if (s != null && s.isNotBlank()) { board.title = s.trim(); StudioLibrary.save(s.trim(), board.json().toString()); board.commit(); list = StudioLibrary.all() }
    }
}

/** How the board looks: camera, how much pitch, the players' style, names, snapping, the grass and which layers show. */
@Composable
private fun ViewSheet(onClose: () -> Unit) {
    Sheet({ StudioPrefs.keep(); onClose() }) {
        T(L("نمای زمین"), 18, FontWeight.Black, modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp))
        StudioLabel(L("دوربین"))
        StudioChips(listOf(false to L("از بالا"), true to L("تلویزیونی")), StudioPrefs.tilt) { StudioPrefs.tilt = it }
        StudioLabel(L("محدوده"))
        StudioChips(listOf("full" to L("کل زمین"), "half" to L("نیمه‌ی حریف"), "third" to L("یک‌سوم پایانی")), StudioPrefs.range) { StudioPrefs.range = it }
        StudioLabel(L("شکل بازیکن‌ها"))
        StudioChips(listOf("photo" to L("عکس"), "shirt" to L("پیراهن"), "disc" to L("دایره")), StudioPrefs.style) { StudioPrefs.style = it }
        StudioLabel(L("زمین"))
        StudioChips(listOf("green" to L("چمن"), "dark" to L("تیره"), "board" to L("تخته‌ی سفید")), StudioPrefs.grass) { StudioPrefs.grass = it }
        StudioLabel(L("بیشتر"))
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Toggle(L("نام بازیکن‌ها"), StudioPrefs.names) { StudioPrefs.names = it }
            Toggle(L("چسبیدن به خطوط"), StudioPrefs.snap) { StudioPrefs.snap = it }
        }
        StudioLabel(L("پخش حرکت"))
        StudioChips(listOf(0.5f to L("آهسته"), 1f to L("معمولی"), 1.8f to L("تند")), StudioPrefs.speed) { StudioPrefs.speed = it }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.padding(horizontal = 16.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Toggle(L("تکرار"), StudioPrefs.loop) { StudioPrefs.loop = it }
            Toggle(L("رد حرکت"), StudioPrefs.trails) { StudioPrefs.trails = it }
            Toggle(L("دوربین دنبال توپ"), StudioPrefs.follow) { StudioPrefs.follow = it }
            Toggle(L("مرحله‌ی قبل کم‌رنگ"), StudioPrefs.onion) { StudioPrefs.onion = it }
        }
        StudioLabel(L("لایه‌های پیدا"))
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LAYERS.forEach { l -> Toggle(layerName(l), l !in StudioPrefs.hidden) { on -> if (on) StudioPrefs.hidden.remove(l) else if (l !in StudioPrefs.hidden) StudioPrefs.hidden += l } }
        }
        Spacer(Modifier.height(12.dp))
    }
}

/** A one-line question with a text box; [onDone] gets null when cancelled. */
@Composable
private fun TextAsk(title: String, initial: String, onDone: (String?) -> Unit) {
    var v by remember { mutableStateOf(initial) }
    androidx.compose.material3.AlertDialog(onDismissRequest = { onDone(null) }, containerColor = C.cardSolid,
        title = { T(title, 16, FontWeight.Black) },
        text = { Field(v, title, Modifier.fillMaxWidth()) { v = it.take(40) } },
        confirmButton = { androidx.compose.material3.TextButton({ onDone(v) }) { T(L("ثبت"), 14, FontWeight.Bold, LocalAccent.current) } },
        dismissButton = { androidx.compose.material3.TextButton({ onDone(null) }) { T(L("انصراف"), 14, color = C.mute) } })
}


/* ---------- the move's steps ---------- */

/** Play / pause, then each step as a small picture of the pitch, and a + for a new step. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun Timeline(board: StudioBoard, ed: StudioEditor) {
    val accent = LocalAccent.current
    val n = board.frames.size
    Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val can = n >= 2
        Box(Modifier.size(50.dp).then(if (can) Modifier.glow(accent.copy(alpha = 0.35f), 8.dp, CircleShape) else Modifier).clip(CircleShape)
            .background(if (can) accent else C.card2).clickable(enabled = can) {
                if (ed.playing) ed.playing = false else { board.syncOut(); ed.playing = true }
            }, contentAlignment = Alignment.Center) {
            val ink = if (can) LocalAccentInk.current else C.faint
            Canvas(Modifier.size(20.dp)) {
                val w = size.width
                if (ed.playing) { drawRect(ink, Offset(w * 0.18f, w * 0.12f), androidx.compose.ui.geometry.Size(w * 0.22f, w * 0.76f)); drawRect(ink, Offset(w * 0.6f, w * 0.12f), androidx.compose.ui.geometry.Size(w * 0.22f, w * 0.76f)) }
                else drawPath(Path().apply { moveTo(w * 0.22f, w * 0.1f); lineTo(w * 0.9f, w * 0.5f); lineTo(w * 0.22f, w * 0.9f); close() }, ink)
            }
        }
        LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            items(n) { k ->
                val playingHere = ed.playT?.let { kotlin.math.round(it).toInt() == k } ?: false
                val on = if (ed.playT != null) playingHere else k == board.cur
                FrameThumb(board, k, on, Modifier.combinedClickable(
                    onClick = {
                        if (ed.playT != null) { ed.playing = false; ed.playT = null }
                        if (k == board.cur) ed.frameMenu = k else board.show(k)
                    },
                    onLongClick = { ed.playing = false; ed.playT = null; board.show(k); ed.frameMenu = k },
                ))
            }
            item {
                Box(Modifier.size(40.dp, 54.dp).clip(RoundedCornerShape(10.dp)).border(1.5.dp, C.line2, RoundedCornerShape(10.dp))
                    .clickable { ed.playing = false; ed.playT = null; board.addFrame() }, contentAlignment = Alignment.Center) {
                    T("+", 22, FontWeight.Black, C.mute)
                }
            }
        }
    }
    if (n < 2) T(L("برای ساختن حرکت، با + مرحله‌ی تازه بساز و بازیکن‌ها و توپ را جابه‌جا کن؛ بعد پخش را بزن"), 11, color = C.mute,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp), maxLines = 2)
}

/** What happens as step [k] is reached: nothing, a shot (and how it ends), a tackle, an interception or an offside. */
@Composable
private fun EventControls(board: StudioBoard, k: Int) {
    val ev = board.frames[k].ev
    StudioLabel(L("اتفاق این مرحله"))
    StudioChips(listOf<Pair<String?, String>>(null to L("هیچ"), "shot" to L("شوت"), "tackle" to L("تکل"), "intercept" to L("قطع توپ"), "offside" to L("آفساید")), ev?.kind) {
        board.setEvent(k, it)
    }
    if (ev?.kind == "shot") {
        StudioLabel(L("نتیجه‌ی شوت را چه کسی تعیین کند؟"))
        StudioChips(listOf("manual" to L("خودم"), "auto" to L("از روی احتمال گل"), "luck" to L("شانسی")), ev.decide) {
            board.setEvent(k, "shot", ev.outcome.ifEmpty { "goal" }, it, reroll = it == "luck")
        }
        if (ev.decide == "manual") {
            Spacer(Modifier.height(8.dp))
            StudioChips(SHOT_OUTCOMES.map { it to outcomeName(it) }, ev.outcome) { board.setEvent(k, "shot", it, "manual") }
        }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.card2).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // the chance as a ring
            val accent = LocalAccent.current
            Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val st = Stroke(5.dp.toPx(), cap = StrokeCap.Round)
                    drawArc(Color.White.copy(alpha = 0.12f), 0f, 360f, false, style = st)
                    drawArc(accent, -90f, 360f * ev.xg, false, style = st)
                }
                T(fa(Math.round(ev.xg * 100)) + "٪", 13, FontWeight.Black)
            }
            Column(Modifier.weight(1f)) {
                T(L("احتمال گل: {0}٪", fa(Math.round(ev.xg * 100))), 14, FontWeight.ExtraBold)
                T(L("از روی فاصله، زاویه‌ی دید دروازه و مدافع‌های جلوی توپ"), 11, color = C.mute, maxLines = 2)
                T(L("نتیجه: {0}", outcomeName(ev.outcome)), 13, FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
            }
        }
        if (ev.decide == "luck") Box(Modifier.padding(horizontal = 16.dp)) {
            Pill(L("دوباره تاس بریز"), PillKind.Accent, onClick = { board.setEvent(k, "shot", ev.outcome, "luck", reroll = true) })
        }
    }
}

/** A step drawn small: the pitch, each player as a dot in his colour, the ball, and the step's number. */
@Composable
private fun FrameThumb(board: StudioBoard, k: Int, on: Boolean, modifier: Modifier) {
    val accent = LocalAccent.current
    val f = board.frames.getOrNull(k) ?: return
    Box(modifier.size(40.dp, 54.dp).clip(RoundedCornerShape(10.dp)).border(if (on) 2.dp else 1.dp, if (on) accent else C.line2, RoundedCornerShape(10.dp))) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            drawRect(Color(0xFF1E7A42))
            drawLine(Color.White.copy(alpha = 0.4f), Offset(0f, h / 2), Offset(w, h / 2), 1f)
            drawCircle(Color.White.copy(alpha = 0.4f), w * 0.15f, Offset(w / 2, h / 2), style = Stroke(1f))
            for (p in board.players) {
                if (p.side == 1 && !board.showAway) continue
                val at = f.pos[p.key] ?: continue
                drawCircle(Color((if (p.side == 0) board.home else board.away).color), w * 0.05f, Offset(at.x * w, at.y * h))
            }
            drawCircle(Color.White, w * 0.045f, Offset(f.ball.x * w, f.ball.y * h))
            drawCircle(Color.Black, w * 0.045f, Offset(f.ball.x * w, f.ball.y * h), style = Stroke(1f))
        }
        Box(Modifier.align(Alignment.TopStart).padding(3.dp).clip(RoundedCornerShape(6.dp)).background(if (on) accent else Color.Black.copy(alpha = 0.55f)).padding(horizontal = 4.dp)) {
            T(fa(k + 1), 10, FontWeight.Black, if (on) LocalAccentInk.current else Color.White)
        }
        if (f.air) Box(Modifier.align(Alignment.BottomEnd).padding(3.dp).size(6.dp).clip(CircleShape).background(Color(0xFF35B6FF)))
    }
}

/** A step's settings: how long it takes, the ball in the air, a note, and copy / move / delete. */
@Composable
private fun FrameSheet(board: StudioBoard, ed: StudioEditor, k: Int, onClose: () -> Unit) {
    val f = board.frames[k]
    var note by remember(k) { mutableStateOf(f.note) }
    fun close() { if (note != board.frames.getOrNull(k)?.note) board.frames.getOrNull(k)?.let { board.setFrame(k, it.copy(note = note.trim())) }; onClose() }
    Sheet(::close) {
        T(L("مرحله‌ی {0}", fa(k + 1)), 18, FontWeight.Black, modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp))
        if (k > 0) {
            StudioLabel(L("زمان رسیدن به این مرحله"))
            StudioChips(listOf(0.6f to L("{0} ثانیه", fa("0.6")), 1.2f to L("{0} ثانیه", fa("1.2")), 2f to L("{0} ثانیه", fa(2)), 3f to L("{0} ثانیه", fa(3))), f.dur) {
                board.setFrame(k, board.frames[k].copy(dur = it))
            }
            StudioLabel(L("توپ"))
            StudioChips(listOf(false to L("روی زمین"), true to L("هوایی")), f.air) { board.setFrame(k, board.frames[k].copy(air = it)) }
            EventControls(board, k)
        }
        StudioLabel(L("زیرنویس این مرحله"))
        Field(note, L("مثلاً: هافبک بین خطوط می‌چرخد"), Modifier.padding(horizontal = 16.dp).fillMaxWidth()) { note = it.take(80) }
        Row(Modifier.padding(16.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill(L("کپی این مرحله"), PillKind.Plain, onClick = { board.duplicateFrame(k); onClose() })
            if (k > 0) Pill(L("یکی عقب‌تر"), PillKind.Plain, onClick = { board.moveFrame(k, -1); onClose() })
            if (k < board.frames.size - 1) Pill(L("یکی جلوتر"), PillKind.Plain, onClick = { board.moveFrame(k, 1); onClose() })
            if (board.frames.size > 1) Pill(L("حذف مرحله"), PillKind.Plain, onClick = { board.deleteFrame(k); onClose() })
        }
    }
}


/** The analysis panel: overlays to switch on, and team moves (press, the back line, the offside trap) made on a new step. */
@Composable
private fun AnalysisSheet(board: StudioBoard, onClose: () -> Unit) {
    var newStep by remember { mutableStateOf(true) }
    Sheet({ StudioPrefs.keep(); onClose() }) {
        T(L("تحلیل تیمی"), 18, FontWeight.Black, modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp))
        StudioLabel(L("روی زمین نشان بده"))
        Row(Modifier.padding(horizontal = 16.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Toggle(L("کنترل فضا"), StudioPrefs.space) { StudioPrefs.space = it }
            Toggle(L("خطوط پاس باز و بسته"), StudioPrefs.lanes) { StudioPrefs.lanes = it }
            Toggle(L("بلوک و فشردگی تیم"), StudioPrefs.block) { StudioPrefs.block = it }
        }
        T(L("کنترل فضا: هر تکه از زمین به رنگ تیمی که بازیکنش به آن نزدیک‌تر است. خطوط پاس: سبز یعنی پاس باز، قرمز یعنی حریف جلوی آن است."),
            11, color = C.mute, modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp))
        StudioLabel(L("جاگیری"))
        Row(Modifier.padding(horizontal = 16.dp)) { Toggle(L("تیم‌ها با حرکت توپ جابه‌جا شوند"), StudioPrefs.auto) { StudioPrefs.auto = it } }
        StudioLabel(L("حرکت‌های تیمی"))
        Row(Modifier.padding(horizontal = 16.dp)) { Toggle(L("در یک مرحله‌ی تازه (برای انیمیشن)"), newStep) { newStep = it } }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.padding(horizontal = 16.dp)) { Pill(L("پرس روی صاحب توپ"), PillKind.Accent, onClick = { board.press(newStep); onClose() }) }
        for (side in listOf(0, 1)) {
            if (side == 1 && !board.showAway) continue
            val t = if (side == 0) board.home else board.away
            Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (t.teamId > 0) Crest(t.teamId, 20.dp) else Box(Modifier.size(14.dp).clip(CircleShape).background(Color(t.color)))
                T(L("خط دفاع {0}", t.name), 14, FontWeight.Bold, maxLines = 1)
            }
            Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(L("۵ متر بالاتر"), PillKind.Plain, onClick = { board.shiftLine(side, 5f, newStep); onClose() })
                Pill(L("۵ متر عقب‌تر"), PillKind.Plain, onClick = { board.shiftLine(side, -5f, newStep); onClose() })
                Pill(L("تله‌ی آفساید"), PillKind.Plain, onClick = { board.offsideTrap(side, newStep); onClose() })
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}
