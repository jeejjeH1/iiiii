package app.goallive.live.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/* ---------- where things go on the screen ---------- */

/**
 * From pitch shares to the canvas and back. [v0]..[v1] is the stretch of pitch in view (all of it, the top half or the
 * top third); [tilt] narrows the far end like a broadcast camera. Margins of grass go round the lines.
 */
class Proj(val w: Float, val h: Float, val v0: Float, val v1: Float, val tilt: Boolean, val horiz: Boolean = false) {
    private val mu = 0.05f
    private val mv = 0.035f
    private fun t(v: Float) = (v - v0 + mv) / (v1 - v0 + 2 * mv)
    /** How big things are at depth [v]: smaller far away when tilted. */
    fun s(v: Float) = if (tilt && !horiz) 0.6f + 0.4f * t(v) else 1f
    // lying on its side (a phone turned to landscape) the pitch runs left to right, the home side attacking to the right
    private val wide = (if (horiz) h else w) / (1 + 2 * mu)
    fun at(u: Float, v: Float) = if (horiz) Offset((1 - t(v)) * w, h / 2 + (u - 0.5f) * wide) else Offset(w / 2 + (u - 0.5f) * wide * s(v), t(v) * h)
    fun at(p: Offset) = at(p.x, p.y)
    fun back(p: Offset): Offset {
        if (horiz) {
            val tv = 1 - p.x / w
            return Offset(0.5f + (p.y - h / 2) / wide, tv * (v1 - v0 + 2 * mv) + v0 - mv)
        }
        val tv = p.y / h
        val v = tv * (v1 - v0 + 2 * mv) + v0 - mv
        val sc = if (tilt) 0.6f + 0.4f * tv else 1f
        return Offset(0.5f + (p.x - w / 2) / (wide * sc), v)
    }
    /** A player's radius at depth [v]: bigger when less of the pitch is in view. */
    fun r(v: Float) = wide * 0.042f * (1f / (v1 - v0)).pow(0.35f) * s(v)
    /** Pixels per metre across, at depth [v]. */
    fun perMetre(v: Float) = wide * s(v) / 68f
}

/** How the board's view (zoomed and moved by two fingers) maps the canvas: screen = (p - c) · zoom + c + pan. */
class View2(val zoom: Float, val pan: Offset, val c: Offset) {
    fun toScreen(p: Offset) = (p - c) * zoom + c + pan
    fun toCanvas(s: Offset) = (s - c - pan) / zoom + c
}

/** The pitch's height for its width: 68 m across, the stretch in view long, flattened when tilted. */
fun pitchAspect(range: String, tilt: Boolean): Float {
    val (v0, v1) = rangeOf(range)
    return (105f * (v1 - v0 + 0.07f)) / (68f * 1.1f) * (if (tilt) 0.74f else 1f)
}

fun rangeOf(r: String) = when (r) { "half" -> 0f to 0.5f; "third" -> 0f to 0.36f; else -> 0f to 1f }

/* ---------- what the editor is doing ---------- */

/** The studio's tools and selection. */
class StudioEditor {
    var tool by mutableStateOf("move")
    var pen by mutableIntStateOf(PENS[0])
    var width by mutableFloatStateOf(3f)
    var alpha by mutableFloatStateOf(1f)
    var layer by mutableStateOf("attack")
    val selected = mutableStateListOf<Int>()
    var selMark by mutableIntStateOf(-1)
    var draft by mutableStateOf<SMark?>(null)
    var lasso by mutableStateOf<Pair<Offset, Offset>?>(null)
    var editPlayer by mutableStateOf<SPlayer?>(null)
    var textAt by mutableStateOf<Offset?>(null)
    var zoom by mutableFloatStateOf(1f)
    var pan by mutableStateOf(Offset.Zero)
    /** The move playing: [playT] is the time in steps while it plays or is paused, null when editing. */
    var playing by mutableStateOf(false)
    var playT by mutableStateOf<Float?>(null)
    /** The step whose menu is open. */
    var frameMenu by mutableIntStateOf(-1)
    /** An event being shown: the step it happened on and the seconds since; a pause after it; the last shot's result for the banner. */
    var fx by mutableStateOf<Pair<Int, Float>?>(null)
    var hold by mutableFloatStateOf(0f)
    var result by mutableStateOf<SEvent?>(null)
    /** The analysis panel is open. */
    var analysis by mutableStateOf(false)
    /** The share panel is open; the match whose panel is open. */
    var shareSheet by mutableStateOf(false)
    var matchSheet by mutableIntStateOf(0)
}

/* ---------- the canvas ---------- */

/**
 * The board: the pitch in its look and view, the drawings on their layers, the players in their style and the ball;
 * one finger works the current tool, two fingers zoom and move the view.
 */
@Composable
fun StudioPitch(board: StudioBoard, ed: StudioEditor, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val density = LocalDensity.current
    val tf = remember { (if (app.goallive.live.Lang.en) null else androidx.core.content.res.ResourcesCompat.getFont(ctx, app.goallive.live.R.font.vazir_black)) ?: Typeface.DEFAULT_BOLD }
    // the players' photos, fetched as they come on the board
    val faces = remember { mutableStateMapOf<Int, ImageBitmap>() }
    val wanted = board.players.map { it.pid }.filter { it > 0 }.distinct()
    LaunchedEffect(wanted, StudioPrefs.style) {
        if (StudioPrefs.style != "photo") return@LaunchedEffect
        for (pid in wanted) if (pid !in faces) image(ctx, playerFace(pid), 180)?.let { faces[pid] = it.asImageBitmap() }
    }
    val (v0, v1) = rangeOf(StudioPrefs.range)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val horiz = StudioChrome.full && StudioChrome.landscape
        val aspect = if (horiz) (68f * 1.1f) / (105f * (v1 - v0 + 0.07f)) else pitchAspect(StudioPrefs.range, StudioPrefs.tilt)
        val wDp = if (maxWidth * aspect > maxHeight) maxHeight / aspect else maxWidth
        val hDp = wDp * aspect
        val wPx = with(density) { wDp.toPx() }; val hPx = with(density) { hDp.toPx() }
        val proj = Proj(wPx, hPx, v0, v1, StudioPrefs.tilt, horiz)
        val projNow by rememberUpdatedState(proj)
        // the camera: while the move plays it closes in a little and follows the ball, then eases back out
        LaunchedEffect(ed.playing, StudioPrefs.follow) {
            if (!ed.playing || !StudioPrefs.follow) {
                repeat(24) { androidx.compose.runtime.withFrameNanos { }; ed.zoom += (1f - ed.zoom) * 0.2f; ed.pan *= 0.8f }
                ed.zoom = 1f; ed.pan = Offset.Zero
                return@LaunchedEffect
            }
            while (true) {
                androidx.compose.runtime.withFrameNanos { }
                val t = ed.playT ?: continue
                val pr = projNow
                val b = pr.at(board.at(t).ball)
                val z = ed.zoom + (1.45f - ed.zoom) * 0.06f
                val c = Offset(pr.w / 2, pr.h / 2)
                val want = (c - b) * z
                val maxPan = Offset(pr.w * (z - 1) / 2, pr.h * (z - 1) / 2)
                val target = Offset(want.x.coerceIn(-maxPan.x, maxPan.x), want.y.coerceIn(-maxPan.y, maxPan.y))
                ed.zoom = z; ed.pan += (target - ed.pan) * 0.08f
            }
        }
        Canvas(Modifier.size(wDp, hDp).clipToBounds().pointerInput(Unit) {
            awaitEachGesture {
                val first = awaitFirstDown(requireUnconsumed = false)
                val view0 = View2(ed.zoom, ed.pan, Offset(size.width / 2f, size.height / 2f))
                var multi = false
                var moved = 0f
                var last = first.position
                val g = Gesture(board, ed, projNow)
                // a touch during the move stops it and brings back the step being edited
                val previewing = ed.playT != null
                if (previewing) { ed.playing = false; ed.playT = null; board.applyFrame(board.frames[board.cur]) }
                else g.start(view0.toCanvas(first.position))
                do {
                    val ev = awaitPointerEvent()
                    val down = ev.changes.filter { it.pressed }
                    if (down.size >= 2) {
                        if (!multi) { multi = true; g.cancel() }
                        ed.zoom = (ed.zoom * ev.calculateZoom()).coerceIn(1f, 4f)
                        val maxPan = Offset(size.width * (ed.zoom - 1) / 2, size.height * (ed.zoom - 1) / 2)
                        val p = ed.pan + ev.calculatePan()
                        ed.pan = Offset(p.x.coerceIn(-maxPan.x, maxPan.x), p.y.coerceIn(-maxPan.y, maxPan.y))
                    } else if (!multi && !previewing) down.firstOrNull()?.let { c ->
                        moved += (c.position - last).getDistance(); last = c.position
                        g.move(View2(ed.zoom, ed.pan, Offset(size.width / 2f, size.height / 2f)).toCanvas(c.position))
                    }
                    ev.changes.forEach { it.consume() }
                } while (ev.changes.any { it.pressed })
                if (!multi && !previewing) g.end(moved < 18f)
            }
        }) {
            val view = View2(ed.zoom, ed.pan, Offset(size.width / 2, size.height / 2))
            withTransform({ translate(view.pan.x, view.pan.y); scale(view.zoom, view.zoom, view.c) }) {
                drawBoardScene(board, ed, proj, faces, tf)
            }
        }
    }
}

/** Everything on the board at the editor's moment: grass, drawings, the analyst's layers, players, ball, spotlight and the event being shown. */
fun DrawScope.drawBoardScene(board: StudioBoard, ed: StudioEditor, proj: Proj, faces: Map<Int, ImageBitmap>, tf: Typeface) {
    drawGrass(proj, StudioPrefs.grass)
    // the drawings, layer by layer, under the players
    board.marks.forEachIndexed { i, m -> if (m.layer !in StudioPrefs.hidden) drawMark(proj, m, i == ed.selMark, tf) }
    ed.draft?.let { drawMark(proj, it, false, tf) }
    val t = ed.playT
    val now = t?.let { board.at(it) }
    fun where(p: SPlayer) = now?.pos?.get(p.key) ?: Offset(p.u, p.v)
    if (t != null && now != null) {
        // trails: where each player and the ball have just been, fading
        if (StudioPrefs.trails) for (p in board.players) {
            if (p.side == 1 && !board.showAway) continue
            val team = if (p.side == 0) board.home else board.away
            val pts = (0..12).map { k -> board.at(t - k * 0.06f).pos[p.key] ?: where(p) }.map { proj.at(it) }
            if ((pts.first() - pts.last()).getDistance() < 6f) continue
            for (k in 0 until pts.size - 1) drawLine(Color(team.color).copy(alpha = 0.55f * (1 - k / 12f)), pts[k], pts[k + 1], proj.r(p.v) * 0.5f * (1 - k / 14f), cap = StrokeCap.Round)
        }
        val bpts = (0..8).map { k -> board.at(t - k * 0.035f) }.map { proj.at(it.ball) - Offset(0f, it.height * proj.perMetre(it.ball.y) * 68f) }
        if ((bpts.first() - bpts.last()).getDistance() > 14f)
            for (k in 0 until bpts.size - 1) drawLine(Color.White.copy(alpha = 0.7f * (1 - k / 8f)), bpts[k], bpts[k + 1], proj.r(now.ball.y) * 0.45f * (1 - k / 9f), cap = StrokeCap.Round)
    } else if (StudioPrefs.onion && board.cur > 0) {
        // the step before, faintly, and how each player gets from there
        val prev = board.frames[board.cur - 1]
        val routes = board.frames[board.cur].paths
        for (p in board.players) {
            if (p.side == 1 && !board.showAway) continue
            val from = prev.pos[p.key] ?: continue
            val to = Offset(p.u, p.v)
            val team = if (p.side == 0) board.home else board.away
            val route = routes[p.key]
            if ((from - to).getDistance() > 0.01f || route != null) {
                val pts = (route ?: listOf(from, to)).map { proj.at(it) }
                val path = Path().apply { pts.forEachIndexed { i, q -> if (i == 0) moveTo(q.x, q.y) else lineTo(q.x, q.y) } }
                drawPath(path, Color.White.copy(alpha = 0.55f), style = Stroke(2.2f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 9f))))
                drawCircle(Color(team.color).copy(alpha = 0.35f), proj.r(from.y) * 0.8f, proj.at(from))
                drawCircle(Color.White.copy(alpha = 0.4f), proj.r(from.y) * 0.8f, proj.at(from), style = Stroke(1.5f))
            }
        }
        if ((prev.ball - board.ball).getDistance() > 0.01f) {
            drawLine(Color.White.copy(alpha = 0.6f), proj.at(prev.ball), proj.at(board.ball), 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
            drawCircle(Color.White.copy(alpha = 0.35f), proj.r(prev.ball.y) * 0.4f, proj.at(prev.ball))
        }
    }
    // the analyst's overlays, live as the move plays
    if (StudioPrefs.space || StudioPrefs.lanes || StudioPrefs.block) drawAnalysis(board, proj, ::where, now?.ball ?: board.ball, tf)
    // an event being shown: its frame, its kind and how long ago
    val fxNow = ed.fx?.let { (i, sec) -> board.frames.getOrNull(i)?.ev?.let { Triple(i, it, sec) } }
    var shown = now?.pos
    var ballAt = now?.ball ?: board.ball
    if (fxNow != null && t != null) {
        val (i, ev, sec) = fxNow
        val frame = board.frames[i]
        // the scorers celebrate: hopping on the spot
        if (ev.kind == "shot" && ev.outcome == "goal") {
            val side = board.players.firstOrNull { it.key == ev.by }?.side ?: 0
            val m = HashMap(shown ?: board.players.associate { it.key to Offset(it.u, it.v) })
            board.players.filter { it.side == side }.forEach { p -> m[p.key]?.let { m[p.key] = it - Offset(0f, abs(sin(sec * 9f + p.key)) * 0.012f * (1 - (sec / 2.6f)).coerceAtLeast(0f)) } }
            shown = m
        }
        // off the post: the ball comes back out
        if (ev.kind == "shot" && ev.outcome == "post") {
            val out = if (frame.ball.y < 0.5f) 1f else -1f
            ballAt = frame.ball + Offset((0.5f - frame.ball.x) * 0.6f, out * 0.09f) * easeOutF(min(1f, sec / 0.9f))
        }
    }
    drawPlayers(board, ed, proj, faces, tf, shown)
    drawBall(proj, ballAt, now?.height ?: 0f, now?.roll ?: 0f)
    if (fxNow != null && t != null) drawEvent(proj, board, fxNow.first, fxNow.second, fxNow.third, tf)
    // the spotlight: everything dimmed but the players it is on
    val lit = board.players.filter { it.spot && (it.side == 0 || board.showAway) }
    if (lit.isNotEmpty()) {
        drawContext.canvas.saveLayer(Rect(Offset.Zero, size), androidx.compose.ui.graphics.Paint())
        drawRect(Color.Black.copy(alpha = 0.5f))
        lit.forEach { p -> val w = where(p); drawCircle(Color.Black, proj.r(w.y) * 2.6f, proj.at(w), blendMode = BlendMode.Clear) }
        drawContext.canvas.restore()
        lit.forEach { p ->
            val w = where(p); val c = proj.at(w); val r = proj.r(w.y) * 2.6f
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Transparent), c, r), r, c)
            drawCircle(Color.White.copy(alpha = 0.8f), r, c, style = Stroke(2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))))
        }
    }
    // the selection box being dragged
    ed.lasso?.let { (a, b) ->
        val pa = proj.at(a); val pb = proj.at(b)
        val tl = Offset(min(pa.x, pb.x), min(pa.y, pb.y)); val sz = Size(abs(pa.x - pb.x), abs(pa.y - pb.y))
        drawRect(Color.White.copy(alpha = 0.08f), tl, sz)
        drawRect(Color.White.copy(alpha = 0.8f), tl, sz, style = Stroke(2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))))
    }
}

/* ---------- one gesture with one finger ---------- */

private class Gesture(val board: StudioBoard, val ed: StudioEditor, val proj: Proj) {
    private var what = ""
    private var startP = Offset.Zero
    private var lastP = Offset.Zero
    private var player: SPlayer? = null
    private var handle = -1
    private var markAt = -1
    private var ballFrom = Offset.Zero

    private fun hitPlayer(c: Offset): SPlayer? = board.players.filter { it.side == 0 || board.showAway }
        .minByOrNull { (proj.at(it.u, it.v) - c).getDistance() }?.takeIf { (proj.at(it.u, it.v) - c).getDistance() < proj.r(it.v) * 1.7f }

    private fun hitMark(c: Offset): Int {
        var best = -1; var bestD = 34f
        board.marks.forEachIndexed { i, m ->
            if (m.layer in StudioPrefs.hidden) return@forEachIndexed
            val d = markDistance(proj, m, c)
            if (d < bestD) { bestD = d; best = i }
        }
        return best
    }

    fun start(c: Offset) {
        val p = proj.back(c)
        startP = p; lastP = p
        when (val t = ed.tool) {
            "move" -> {
                val sel = board.marks.getOrNull(ed.selMark)
                handle = sel?.let { m -> handles(m).indexOfFirst { (proj.at(it) - c).getDistance() < 40f } } ?: -1
                when {
                    handle >= 0 -> what = "handle"
                    (proj.at(board.ball) - c).getDistance() < proj.r(board.ball.y) * 1.2f -> { what = "ball"; ballFrom = board.ball }
                    hitPlayer(c) != null -> {
                        player = hitPlayer(c); what = "player"
                        if (player!!.key !in ed.selected) { ed.selected.clear(); ed.selected += player!!.key }
                        ed.selMark = -1
                    }
                    hitMark(c).also { markAt = it } >= 0 -> { what = "mark"; ed.selMark = markAt; ed.selected.clear() }
                    else -> { what = "lasso"; ed.lasso = p to p }
                }
            }
            "spot", "text", "hline" -> what = "tap"
            "route" -> hitPlayer(c)?.let { pl ->
                player = pl; what = "route"
                ed.draft = SMark("run", listOf(Offset(pl.u, pl.v), p), 0xFFFFFFFF.toInt(), 3f, 0.9f)
            }
            else -> {
                what = "draw"
                ed.selMark = -1
                ed.draft = SMark(t, if (t == "curve" || t == "lob") listOf(p, p, p) else listOf(p, p), ed.pen, ed.width, ed.alpha, layer = ed.layer)
            }
        }
    }

    fun move(c: Offset) {
        val p = proj.back(c)
        val d = p - lastP
        when (what) {
            "player" -> board.players.filter { it.key in ed.selected }.forEach { it.u = (it.u + d.x).coerceIn(-0.03f, 1.03f); it.v = (it.v + d.y).coerceIn(-0.03f, 1.03f) }
            "ball" -> board.ball = Offset((board.ball.x + d.x).coerceIn(-0.03f, 1.03f), (board.ball.y + d.y).coerceIn(-0.03f, 1.03f))
            "mark" -> board.marks.getOrNull(markAt)?.let { m -> board.marks[markAt] = m.copy(pts = m.pts.map { it + d }) }
            "handle" -> board.marks.getOrNull(ed.selMark)?.let { m -> board.marks[ed.selMark] = m.copy(pts = m.pts.mapIndexed { i, q -> if (i == handleIndex(m, handle)) q + d else q }) }
            "lasso" -> ed.lasso = startP to p
            "route" -> ed.draft?.let { m -> if ((p - m.pts.last()).getDistance() > 0.006f) ed.draft = m.copy(pts = m.pts + p) }
            "draw" -> ed.draft?.let { m ->
                ed.draft = when (m.kind) {
                    "curve", "lob" -> { val a = m.pts[0]; m.copy(pts = listOf(a, bendOf(a, p, if (m.kind == "lob") 0.12f else 0.22f), p)) }
                    in TWO_POINT -> m.copy(pts = listOf(m.pts[0], p))
                    // free hand: a point each time the finger has gone a little way
                    else -> if ((p - m.pts.last()).getDistance() > 0.006f) m.copy(pts = m.pts + p) else m
                }
            }
        }
        lastP = p
    }

    fun end(tap: Boolean) {
        when (what) {
            "player" -> {
                if (tap) { ed.editPlayer = player } else {
                    if (StudioPrefs.snap) board.players.filter { it.key in ed.selected }.forEach { it.u = snapU(it.u); it.v = snapV(it.v) }
                    // moved by hand: a route drawn for this step no longer leads there
                    board.frames.getOrNull(board.cur)?.let { f -> if (ed.selected.any { it in f.paths }) board.frames[board.cur] = f.copy(paths = f.paths - ed.selected.toSet()) }
                    board.commit()
                }
            }
            "ball" -> if (!tap) { if (StudioPrefs.auto) board.followBall(board.ball - ballFrom); board.commit() }
            "mark", "handle" -> if (!tap) board.commit()
            "lasso" -> {
                val (a, b) = ed.lasso ?: (startP to startP)
                ed.lasso = null
                ed.selected.clear()
                if (tap) { ed.selMark = -1 } else {
                    val r = Rect(Offset(min(a.x, b.x), min(a.y, b.y)), Offset(max(a.x, b.x), max(a.y, b.y)))
                    board.players.filter { (it.side == 0 || board.showAway) && r.contains(Offset(it.u, it.v)) }.forEach { ed.selected += it.key }
                }
            }
            "tap" -> {
                val c = proj.at(startP)
                when (ed.tool) {
                    "spot" -> hitPlayer(c)?.let { it.spot = !it.spot; board.commit() }
                    "text" -> ed.textAt = startP
                    "hline" -> { board.marks += SMark("hline", listOf(startP), ed.pen, ed.width, ed.alpha, layer = ed.layer); board.commit() }
                }
            }
            "route" -> ed.draft?.let { m ->
                ed.draft = null
                val pl = player
                if (pl != null && m.pts.size >= 3 && (m.pts.last() - m.pts.first()).getDistance() > 0.015f) {
                    // a route ends a step: drawn on the first one, it starts a new step after it
                    if (board.cur == 0) board.addFrame()
                    val from = board.frames[board.cur - 1].pos[pl.key] ?: m.pts.first()
                    val route = smooth(listOf(from) + m.pts.drop(1))
                    pl.u = route.last().x; pl.v = route.last().y
                    val f = board.frameNow(board.frames[board.cur])
                    board.frames[board.cur] = f.copy(paths = f.paths + (pl.key to route))
                    board.commit()
                }
            }
            "draw" -> ed.draft?.let { m ->
                ed.draft = null
                val len = (m.pts.last() - m.pts.first()).getDistance() + (if (m.kind in setOf("run", "dribble", "zone")) m.pts.size * 0.002f else 0f)
                if (len > 0.02f) {
                    val pts = if (m.kind in setOf("run", "dribble", "zone")) smooth(m.pts) else m.pts
                    board.marks += m.copy(pts = pts); board.commit()
                }
            }
        }
        what = ""; player = null
    }

    fun cancel() {
        ed.draft = null; ed.lasso = null
        if (what == "player" || what == "ball" || what == "mark" || what == "handle") board.commit()
        what = ""
    }
}

/** A bend for a curve from [a] to [b]: the middle pushed to the right of the way by [k] of its length. */
private fun bendOf(a: Offset, b: Offset, k: Float): Offset {
    val m = (a + b) / 2f; val d = b - a
    return m + Offset(-d.y, d.x) * k
}

/** The points of [m] that can be dragged: both ends, and the bend of a curve. */
fun handles(m: SMark): List<Offset> = when (m.kind) {
    "curve", "lob" -> if (m.pts.size >= 3) listOf(m.pts[0], m.pts[1], m.pts[2]) else m.pts
    in TWO_POINT -> m.pts.take(2)
    else -> emptyList()
}
private fun handleIndex(m: SMark, h: Int) = h

private fun snapU(u: Float) = (u * 16).roundToInt() / 16f
private fun snapV(v: Float) = (v * 20).roundToInt() / 20f

/** A finger's path evened out: every other point averaged with its neighbours. */
private fun smooth(p: List<Offset>): List<Offset> {
    if (p.size < 4) return p
    val out = arrayListOf(p.first())
    for (i in 1 until p.size - 1) out += (p[i - 1] + p[i] * 2f + p[i + 1]) / 4f
    out += p.last()
    return out.filterIndexed { i, _ -> i == 0 || i == out.size - 1 || i % 2 == 0 }
}

/** How far the screen point [c] is from mark [m], in pixels. */
private fun markDistance(proj: Proj, m: SMark, c: Offset): Float {
    val pts = screenPath(proj, m)
    if (m.kind in setOf("zone", "rect", "circle") && pts.size > 2) {
        val xs = pts.map { it.x }; val ys = pts.map { it.y }
        if (c.x in xs.min()..xs.max() && c.y in ys.min()..ys.max()) return 6f
    }
    if (m.kind == "text") return (proj.at(m.pts.first()) - c).getDistance() * 0.6f
    var best = Float.MAX_VALUE
    for (i in 0 until pts.size - 1) best = min(best, segDist(c, pts[i], pts[i + 1]))
    return best
}

private fun segDist(p: Offset, a: Offset, b: Offset): Float {
    val ab = b - a; val len2 = ab.x * ab.x + ab.y * ab.y
    if (len2 == 0f) return (p - a).getDistance()
    val t = (((p - a).x * ab.x + (p - a).y * ab.y) / len2).coerceIn(0f, 1f)
    return (p - (a + ab * t)).getDistance()
}

/** The screen points along mark [m]. */
private fun screenPath(proj: Proj, m: SMark): List<Offset> = when (m.kind) {
    "curve", "lob" -> if (m.pts.size >= 3) (0..24).map { k -> val t = k / 24f; proj.at(quad(m.pts[0], m.pts[1], m.pts[2], t)) } else m.pts.map { proj.at(it) }
    "rect" -> if (m.pts.size >= 2) { val a = m.pts[0]; val b = m.pts[1]; listOf(a, Offset(b.x, a.y), b, Offset(a.x, b.y), a).map { proj.at(it) } } else emptyList()
    "circle" -> if (m.pts.size >= 2) {
        val c = m.pts[0]; val r = (m.pts[1] - c)
        val rr = sqrt((r.x * 68).pow(2) + (r.y * 105).pow(2))
        (0..40).map { k -> val a = k / 40f * 2 * Math.PI.toFloat(); proj.at(c.x + rr * cos(a) / 68f, c.y + rr * sin(a) / 105f) }
    } else emptyList()
    "hline" -> listOf(proj.at(-0.02f, m.pts[0].y), proj.at(1.02f, m.pts[0].y))
    else -> m.pts.map { proj.at(it) }
}

private fun quad(a: Offset, c: Offset, b: Offset, t: Float) = a * ((1 - t) * (1 - t)) + c * (2 * (1 - t) * t) + b * (t * t)

/* ---------- drawing ---------- */

private fun DrawScope.drawGrass(proj: Proj, look: String) {
    val (base, stripe, line) = when (look) {
        "dark" -> Triple(Color(0xFF1A2028), Color(0xFF1F262F), Color.White.copy(alpha = 0.38f))
        "board" -> Triple(Color(0xFFF2F1EC), Color(0xFFECEBE5), Color(0xFF1F2937).copy(alpha = 0.55f))
        else -> Triple(Color(0xFF1E7A42), Color(0xFF237F47), Color.White.copy(alpha = 0.75f))
    }
    // the turf out to the edges, then mown stripes across it
    fun quadPath(u0: Float, va: Float, u1: Float, vb: Float) = Path().apply {
        moveTo(proj.at(u0, va).x, proj.at(u0, va).y); lineTo(proj.at(u1, va).x, proj.at(u1, va).y)
        lineTo(proj.at(u1, vb).x, proj.at(u1, vb).y); lineTo(proj.at(u0, vb).x, proj.at(u0, vb).y); close()
    }
    val va = proj.back(Offset(0f, 0f)).y; val vb = proj.back(Offset(size.width, size.height)).y
    val outer = quadPath(-0.06f, min(va, vb), 1.06f, max(va, vb))
    drawPath(outer, base)
    clipPath(outer) {
        val bands = 18
        for (k in 0 until bands) if (k % 2 == 0) drawPath(quadPath(-0.1f, k / bands.toFloat(), 1.1f, (k + 1) / bands.toFloat()), stripe)
        if (look == "green") {
            drawRect(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent), Offset.Zero, Offset(size.width * 0.7f, size.height * 0.4f)))
            drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.3f)), Offset(size.width / 2, size.height * 0.5f), size.height * 0.8f))
        }
        // the markings: 68 m across, 105 m long
        val st = Stroke(max(1.5f, proj.perMetre(0.5f) * 0.18f), cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun poly(p: List<Offset>, closed: Boolean = false) {
            val path = Path(); p.forEachIndexed { i, q -> val s = proj.at(q); if (i == 0) path.moveTo(s.x, s.y) else path.lineTo(s.x, s.y) }
            if (closed) path.close(); drawPath(path, line, style = st)
        }
        fun ring(cu: Float, cv: Float, rm: Float, a0: Float = 0f, a1: Float = 360f) {
            val n = 48; val path = Path()
            for (k in 0..n) {
                val a = Math.toRadians((a0 + (a1 - a0) * k / n).toDouble())
                val s = proj.at(cu + (rm * cos(a)).toFloat() / 68f, cv + (rm * sin(a)).toFloat() / 105f)
                if (k == 0) path.moveTo(s.x, s.y) else path.lineTo(s.x, s.y)
            }
            drawPath(path, line, style = st)
        }
        poly(listOf(Offset(0f, 0f), Offset(1f, 0f), Offset(1f, 1f), Offset(0f, 1f)), closed = true)
        poly(listOf(Offset(0f, 0.5f), Offset(1f, 0.5f)))
        ring(0.5f, 0.5f, 9.15f)
        drawCircle(line, max(2f, proj.perMetre(0.5f) * 0.3f), proj.at(0.5f, 0.5f))
        for (top in listOf(true, false)) {
            val g = if (top) 0f else 1f; val d = if (top) 1f else -1f
            val b = 16.5f / 105f; val sx = 5.5f / 105f
            poly(listOf(Offset(0.2035f, g), Offset(0.2035f, g + d * b), Offset(0.7965f, g + d * b), Offset(0.7965f, g)))
            poly(listOf(Offset(0.3653f, g), Offset(0.3653f, g + d * sx), Offset(0.6347f, g + d * sx), Offset(0.6347f, g)))
            val spot = g + d * 11f / 105f
            drawCircle(line, max(2f, proj.perMetre(spot) * 0.25f), proj.at(0.5f, spot))
            // the D: the part of the circle round the spot outside the box
            if (top) ring(0.5f, spot, 9.15f, 37f, 143f) else ring(0.5f, spot, 9.15f, 217f, 323f)
            // the goal and its net, just behind the line
            val gl = 0.4462f; val gr = 0.5538f; val gb = g - d * 2.2f / 105f
            val net = Path(); for (k in 0..6) { val u = gl + (gr - gl) * k / 6; val a = proj.at(u, g); val z = proj.at(u, gb); net.moveTo(a.x, a.y); net.lineTo(z.x, z.y) }
            drawPath(net, line.copy(alpha = line.alpha * 0.4f), style = Stroke(1f))
            poly(listOf(Offset(gl, g), Offset(gl, gb), Offset(gr, gb), Offset(gr, g)))
            // corner arcs
            ring(0f, g, 1f, if (top) 0f else 270f, if (top) 90f else 360f); ring(1f, g, 1f, if (top) 90f else 180f, if (top) 180f else 270f)
        }
    }
}

private fun DrawScope.arrowHead(tip: Offset, from: Offset, size: Float, color: Color) {
    val a = atan2(tip.y - from.y, tip.x - from.x)
    val p = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(tip.x - size * cos(a - 0.45f), tip.y - size * sin(a - 0.45f))
        lineTo(tip.x - size * 0.6f * cos(a), tip.y - size * 0.6f * sin(a))
        lineTo(tip.x - size * cos(a + 0.45f), tip.y - size * sin(a + 0.45f)); close()
    }
    drawPath(p, color)
}

/** One drawing, in its kind's line, with its handles when selected. */
private fun DrawScope.drawMark(proj: Proj, m: SMark, selected: Boolean, tf: Typeface) {
    if (m.pts.isEmpty()) return
    val color = Color(m.color).copy(alpha = m.alpha)
    val depth = m.pts.map { it.y }.average().toFloat()
    val w = m.width * proj.s(depth) * (proj.perMetre(0.5f) / 9f).coerceIn(0.7f, 1.8f)
    val pts = screenPath(proj, m)
    fun pathOf(ps: List<Offset>) = Path().apply { ps.forEachIndexed { i, q -> if (i == 0) moveTo(q.x, q.y) else lineTo(q.x, q.y) } }
    val head = w * 4.2f + 8f
    // a soft dark edge under every line, so it reads on any grass
    val under = Color.Black.copy(alpha = 0.25f * m.alpha)
    when (m.kind) {
        "pass", "both", "curve" -> {
            if (pts.size < 2) return
            drawPath(pathOf(pts), under, style = Stroke(w + 3f, cap = StrokeCap.Round))
            drawPath(pathOf(pts), color, style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round))
            arrowHead(pts.last(), pts[max(0, pts.size - 3)], head, color)
            if (m.kind == "both") arrowHead(pts.first(), pts[min(pts.size - 1, 2)], head, color)
        }
        "lob" -> {
            if (pts.size < 2) return
            // the ball's flight dashed above its shadow on the grass
            drawLine(Color.Black.copy(alpha = 0.22f * m.alpha), pts.first(), pts.last(), w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 10f)), cap = StrokeCap.Round)
            drawPath(pathOf(pts), color, style = Stroke(w, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(w * 0.1f + 1f, w * 2.2f + 6f))))
            arrowHead(pts.last(), pts[max(0, pts.size - 3)], head, color)
        }
        "run" -> {
            if (pts.size < 2) return
            drawPath(pathOf(pts), color, style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(w * 3 + 6, w * 2.4f + 6))))
            arrowHead(pts.last(), pts[max(0, pts.size - 3)], head, color)
        }
        "dribble" -> {
            if (pts.size < 2) return
            drawPath(pathOf(wave(pts, w * 1.6f + 4f, w * 5f + 14f)), color, style = Stroke(w * 0.9f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            arrowHead(pts.last(), pts[max(0, pts.size - 3)], head, color)
        }
        "zone", "rect", "circle" -> {
            if (pts.size < 2) return
            val p = pathOf(pts).apply { close() }
            drawPath(p, color.copy(alpha = m.alpha * 0.28f))
            drawPath(p, color, style = Stroke(w * 0.8f, join = StrokeJoin.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(w * 3 + 8, w * 2 + 6))))
        }
        "dist" -> {
            if (pts.size < 2) return
            val a = pts[0]; val b = pts[1]
            drawLine(color, a, b, w, cap = StrokeCap.Round)
            val ang = atan2(b.y - a.y, b.x - a.x); val n = Offset(-sin(ang), cos(ang)) * (w * 3 + 6)
            drawLine(color, a - n, a + n, w); drawLine(color, b - n, b + n, w)
            val d = m.pts[1] - m.pts[0]
            val metres = sqrt((d.x * 68).pow(2) + (d.y * 105).pow(2))
            label(fa("${metres.roundToInt()}") + " " + app.goallive.live.L("متر"), (a + b) / 2f - n * 1.6f, 13f * density, color, tf)
        }
        "hline" -> {
            if (pts.size < 2) return
            drawLine(color, pts[0], pts[1], w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(w * 4 + 10, w * 2 + 8)), cap = StrokeCap.Round)
            if (m.text.isNotEmpty()) label(m.text, Offset(pts[0].x + 70f * density / 3, pts[0].y - 12f * density), 11f * density, color, tf)
        }
        "text" -> label(m.text, proj.at(m.pts[0]), (12f + m.width * 1.2f) * density * proj.s(m.pts[0].y), color, tf, pill = true)
    }
    if (selected) {
        handles(m).forEach { h -> val s = proj.at(h); drawCircle(Color.White, 9f * density / 2.5f + 6f, s); drawCircle(Color(0xFF35B6FF), 9f * density / 2.5f + 6f, s, style = Stroke(3f)) }
        if (m.kind in setOf("zone", "run", "dribble", "text", "hline")) {
            val xs = pts.map { it.x }; val ys = pts.map { it.y }
            if (xs.isNotEmpty()) drawRect(Color(0xFF35B6FF), Offset(xs.min() - 10, ys.min() - 10), Size(xs.max() - xs.min() + 20, ys.max() - ys.min() + 20),
                style = Stroke(2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))))
        }
    }
}

/** A path wiggled side to side (a dribble). */
private fun wave(pts: List<Offset>, amp: Float, period: Float): List<Offset> {
    val out = ArrayList<Offset>()
    var dist = 0f
    for (i in 0 until pts.size - 1) {
        val a = pts[i]; val b = pts[i + 1]; val seg = (b - a).getDistance()
        if (seg == 0f) continue
        val dir = (b - a) / seg; val n = Offset(-dir.y, dir.x)
        var s = 0f
        while (s < seg) {
            val fade = min(1f, min(dist + s, 9999f) / period)
            out += a + dir * s + n * (amp * fade * sin((dist + s) / period * 2 * Math.PI.toFloat()))
            s += 4f
        }
        dist += seg
    }
    out += pts.last()
    return out
}

/** Writing on the board: plain, or on a small dark pill. */
private fun DrawScope.label(s: String, at: Offset, size: Float, color: Color, tf: Typeface, pill: Boolean = false) {
    if (s.isBlank()) return
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; typeface = tf; textAlign = Paint.Align.CENTER; this.color = android.graphics.Color.argb((color.alpha * 255).toInt(), (color.red * 255).toInt(), (color.green * 255).toInt(), (color.blue * 255).toInt()) }
    val w = p.measureText(s)
    val fm = p.fontMetrics
    if (pill) drawRoundRect(Color.Black.copy(alpha = 0.62f), Offset(at.x - w / 2 - size * 0.55f, at.y - size * 0.85f), Size(w + size * 1.1f, size * 1.7f), CornerRadius(size * 0.6f))
    else drawContext.canvas.nativeCanvas.drawText(s, at.x + 1.5f, at.y - (fm.ascent + fm.descent) / 2 + 1.5f, Paint(p).apply { this.color = 0x88000000.toInt() })
    drawContext.canvas.nativeCanvas.drawText(s, at.x, at.y - (fm.ascent + fm.descent) / 2, p)
}

private fun Color.argb(): Int = android.graphics.Color.argb((alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt())

/** The players, far ones first, in the chosen style: photo discs, shirts or plain numbered discs. */
private fun DrawScope.drawPlayers(board: StudioBoard, ed: StudioEditor, proj: Proj, faces: Map<Int, ImageBitmap>, tf: Typeface, pos: Map<Int, Offset>? = null) {
    fun at(p: SPlayer) = pos?.get(p.key) ?: Offset(p.u, p.v)
    val shown = board.players.filter { it.side == 0 || board.showAway }.sortedBy { at(it).y }
    for (p in shown) {
        val team = if (p.side == 0) board.home else board.away
        val w0 = at(p)
        val c = proj.at(w0); val r = proj.r(w0.y)
        val kit = Color(team.color); val ink = Color(team.ink)
        val sel = p.key in ed.selected
        // a shadow on the grass
        drawOval(Color.Black.copy(alpha = 0.35f), Offset(c.x - r * 0.95f, c.y + r * 0.55f), Size(r * 1.9f, r * 0.7f))
        if (sel) drawCircle(Color(0xFF35B6FF).copy(alpha = 0.35f), r * 1.55f, c)
        when (StudioPrefs.style) {
            "shirt" -> {
                val s = r * 1.15f
                val shirt = Path().apply {
                    moveTo(c.x - s * 0.35f, c.y - s * 0.95f); lineTo(c.x - s, c.y - s * 0.55f); lineTo(c.x - s * 0.72f, c.y - s * 0.05f)
                    lineTo(c.x - s * 0.55f, c.y - s * 0.2f); lineTo(c.x - s * 0.55f, c.y + s * 0.95f); lineTo(c.x + s * 0.55f, c.y + s * 0.95f)
                    lineTo(c.x + s * 0.55f, c.y - s * 0.2f); lineTo(c.x + s * 0.72f, c.y - s * 0.05f); lineTo(c.x + s, c.y - s * 0.55f)
                    lineTo(c.x + s * 0.35f, c.y - s * 0.95f); quadraticBezierTo(c.x, c.y - s * 0.6f, c.x - s * 0.35f, c.y - s * 0.95f); close()
                }
                drawPath(shirt, kit)
                drawPath(shirt, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Black.copy(alpha = 0.18f)), c.y - s, c.y + s))
                drawPath(shirt, if (sel) Color(0xFF35B6FF) else Color.Black.copy(alpha = 0.35f), style = Stroke(if (sel) 3f else 1.5f))
                centerText(p.num, Offset(c.x, c.y + s * 0.15f), s * 0.85f, ink, tf)
            }
            "photo" -> {
                drawCircle(kit, r * 1.08f, c)
                drawCircle(Brush.radialGradient(listOf(lerpC(kit, Color.White, 0.25f), lerpC(kit, Color.Black, 0.45f)), c - Offset(0f, r * 0.3f), r * 1.4f), r * 0.92f, c)
                val face = faces[p.pid]
                if (face != null) {
                    val clip = Path().apply { addOval(Rect(c, r * 0.92f)) }
                    clipPath(clip) {
                        drawImage(face, IntOffset.Zero, IntSize(face.width, face.height), IntOffset((c.x - r * 0.92f).roundToInt(), (c.y - r * 0.82f).roundToInt()),
                            IntSize((r * 1.84f).roundToInt(), (r * 1.84f).roundToInt()))
                    }
                } else centerText(p.num, c, r * 0.95f, ink, tf)
                drawCircle(if (sel) Color(0xFF35B6FF) else Color.White.copy(alpha = 0.9f), r * 1.08f, c, style = Stroke(if (sel) 3.5f else 2f))
                // the shirt number in a small badge
                if (face != null && p.num.isNotEmpty()) {
                    val b = c + Offset(-r * 0.78f, r * 0.62f)
                    drawCircle(kit, r * 0.42f, b); drawCircle(Color.White, r * 0.42f, b, style = Stroke(1.5f))
                    centerText(p.num, b, r * 0.48f, ink, tf)
                }
            }
            else -> {
                drawCircle(kit, r, c)
                drawCircle(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.25f), Color.Transparent), c.y - r, c.y + r), r, c)
                drawCircle(if (sel) Color(0xFF35B6FF) else Color.White.copy(alpha = 0.85f), r, c, style = Stroke(if (sel) 3.5f else 2f))
                centerText(p.num, c, r * 0.95f, ink, tf)
            }
        }
        if (StudioPrefs.names && p.name.isNotBlank()) {
            val fs = r * 0.62f
            val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = fs; typeface = tf }
            val w = min(tp.measureText(p.name), r * 4.2f)
            val top = c.y + r * 1.22f
            drawRoundRect(Color(0xE60B0F14), Offset(c.x - w / 2 - fs * 0.45f, top), Size(w + fs * 0.9f, fs * 1.45f), CornerRadius(fs * 0.5f))
            centerText(android.text.TextUtils.ellipsize(p.name, android.text.TextPaint(tp), r * 4.2f, android.text.TextUtils.TruncateAt.END).toString(),
                Offset(c.x, top + fs * 0.72f), fs, Color.White, tf)
        }
    }
}

private fun lerpC(a: Color, b: Color, t: Float) = androidx.compose.ui.graphics.lerp(a, b, t)

private fun DrawScope.centerText(s: String, c: Offset, size: Float, color: Color, tf: Typeface) {
    if (s.isEmpty()) return
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; typeface = tf; textAlign = Paint.Align.CENTER; this.color = color.argb() }
    val fm = p.fontMetrics
    drawContext.canvas.nativeCanvas.drawText(s, c.x, c.y - (fm.ascent + fm.descent) / 2, p)
}

private fun DrawScope.drawBall(proj: Proj, b: Offset, height: Float = 0f, roll: Float = 0f) {
    val g = proj.at(b)
    val lift = height * proj.perMetre(b.y) * 68f
    // in the air: the ball rises and grows a little, its shadow stays on the grass and fades
    val r = proj.r(b.y) * 0.5f * (1f + height * 3f)
    val c = g - Offset(0f, lift)
    drawOval(Color.Black.copy(alpha = 0.35f / (1f + height * 12f)), Offset(g.x - r, g.y + r * 0.6f - (if (height > 0) r * 0.5f else 0f)), Size(r * 2, r * 0.8f))
    drawCircle(Color.White, r, c)
    // it spins as it travels
    val spin = roll * 3600.0
    val pent = Path()
    for (i in 0 until 5) { val a = Math.toRadians(-90.0 + i * 72 + spin); val x = c.x + r * 0.42f * cos(a).toFloat(); val y = c.y + r * 0.42f * sin(a).toFloat(); if (i == 0) pent.moveTo(x, y) else pent.lineTo(x, y) }
    pent.close(); drawPath(pent, Color(0xFF111111))
    drawCircle(Color(0xFF111111), r, c, style = Stroke(max(1f, r * 0.12f)))
}

@Suppress("unused") private fun keep(a: Float) = hypot(a, a)


/* ---------- what happens: goals, saves, tackles, offsides ---------- */

private fun easeOutF(t: Float) = 1 - (1 - t) * (1 - t) * (1 - t)
private fun popIn(t: Float): Float { val q = (t - 1); return 1 + q * q * (2.7f * q + 1.7f) }

fun outcomeName(o: String) = when (o) {
    "goal" -> app.goallive.live.L("گل!"); "save" -> app.goallive.live.L("سیو!"); "post" -> app.goallive.live.L("تیرک!")
    "wide" -> app.goallive.live.L("بیرون!"); "block" -> app.goallive.live.L("مهار شد!"); "tackle" -> app.goallive.live.L("تکل!")
    "intercept" -> app.goallive.live.L("قطع توپ!"); "offside" -> app.goallive.live.L("آفساید!"); else -> ""
}

/** The moment of an event, [sec] seconds after it: the net shaking, light and confetti for a goal; a ring for a save or the post; sparks for a tackle; the line for an offside; and its word. */
private fun DrawScope.drawEvent(proj: Proj, board: StudioBoard, i: Int, ev: SEvent, sec: Float, tf: Typeface) {
    val f = board.frames[i]
    val fade = (1f - ((sec - 1.8f) / 0.8f)).coerceIn(0f, 1f)
    val by = board.players.firstOrNull { it.key == ev.by }
    val team = if ((by?.side ?: 0) == 0) board.home else board.away
    val kit = Color(team.color)
    val gold = Color(0xFFF5C542)
    val word = outcomeName(if (ev.kind == "shot") ev.outcome else ev.kind)
    val ball = proj.at(f.ball)
    when {
        ev.kind == "shot" && ev.outcome == "goal" -> {
            val goalV = if (f.ball.y < 0.5f) 0f else 1f
            val g = proj.at(0.5f, goalV)
            val r = proj.perMetre(goalV) * 9f
            // light bursting from the goal
            drawCircle(Brush.radialGradient(listOf(gold.copy(alpha = 0.55f * fade), kit.copy(alpha = 0.25f * fade), Color.Transparent), g, r * 2.6f), r * 2.6f, g)
            // the net shaking
            val back = if (goalV == 0f) -1f else 1f
            val amp = proj.perMetre(goalV) * 0.9f * kotlin.math.exp(-sec * 2.5f)
            for (k in 0..8) {
                val u = 0.4462f + 0.1076f * k / 8
                val a = proj.at(u, goalV); val z = proj.at(u, goalV + back * 2.2f / 105f)
                val wob = sin(sec * 38f + k * 1.3f) * amp
                drawLine(Color.White.copy(alpha = 0.8f * fade), a, z + Offset(wob, wob * 0.5f), 2f)
            }
            // confetti from the goal, falling
            for (k in 0 until 46) {
                val ang = (k * 137.5f) * (Math.PI.toFloat() / 180f)
                val sp = r * (1.1f + (k % 7) * 0.22f)
                val p = g + Offset(cos(ang) * sp * min(sec, 1.2f), sin(ang) * sp * 0.6f * min(sec, 1.2f) + 60f * sec * sec)
                val c = when (k % 4) { 0 -> gold; 1 -> kit; 2 -> Color.White; else -> lerpC(kit, Color.White, 0.5f) }
                withTransform({ rotate(sec * 400f + k * 40f, p) }) { drawRect(c.copy(alpha = fade), p - Offset(5f, 3f), Size(10f, 6f)) }
            }
            // GOAL!
            val sz = size.width * 0.2f * popIn(min(1f, sec / 0.45f))
            val mid = Offset(size.width / 2, size.height * 0.42f)
            label(word, mid + Offset(0f, 6f), sz, Color.Black.copy(alpha = 0.45f * fade), tf)
            label(word, mid, sz, gold.copy(alpha = fade), tf)
        }
        ev.kind == "shot" -> {
            val col = when (ev.outcome) { "save" -> Color(0xFF35B6FF); "post" -> Color.White; "block" -> Color(0xFFFF9F43); else -> Color(0xFFB0B8C4) }
            val rr = proj.r(f.ball.y) * (1f + sec * 3f)
            drawCircle(col.copy(alpha = (0.9f - sec * 0.6f).coerceIn(0f, 1f)), rr, ball, style = Stroke(4f))
            label(word, ball + Offset(0f, (if (f.ball.y < 0.5f) 1 else -1) * proj.r(f.ball.y) * 3.2f), proj.r(f.ball.y) * 1.5f * popIn(min(1f, sec / 0.4f)), col.copy(alpha = fade), tf, pill = true)
        }
        ev.kind == "tackle" || ev.kind == "intercept" -> {
            val c = Color(((if ((board.players.firstOrNull { it.key == ev.by }?.side ?: 0) == 0) board.home else board.away).color))
            val rr = proj.r(f.ball.y)
            for (k in 0 until 10) {
                val a = k * 36f * (Math.PI.toFloat() / 180f)
                val d0 = rr * (0.6f + sec * 2f); val d1 = d0 + rr * 0.8f * (1 - min(1f, sec))
                drawLine(gold.copy(alpha = fade), ball + Offset(cos(a), sin(a)) * d0, ball + Offset(cos(a), sin(a)) * d1, 3f, cap = StrokeCap.Round)
            }
            drawCircle(c.copy(alpha = fade), rr * 0.9f, ball, style = Stroke(4f))
            label(word, ball - Offset(0f, rr * 2.6f), rr * 1.3f * popIn(min(1f, sec / 0.4f)), Color.White.copy(alpha = fade), tf, pill = true)
        }
        ev.kind == "offside" -> {
            // the line of the last defender but one
            val attacker = board.holder(i - 1)
            val defending = board.players.filter { it.side != (attacker?.side ?: 0) }.mapNotNull { f.pos[it.key] }
            val up = (attacker?.side ?: 0) == 0
            val v = defending.sortedBy { if (up) it.y else -it.y }.getOrNull(1)?.y ?: 0.3f
            val c = Color(0xFFF5E142)
            drawLine(c.copy(alpha = fade), proj.at(-0.02f, v), proj.at(1.02f, v), 4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 10f)))
            // the linesman's flag
            val fl = proj.at(1.0f, v) + Offset(-6f, 0f)
            drawLine(Color.White.copy(alpha = fade), fl, fl - Offset(0f, 46f), 3f)
            drawRect(c.copy(alpha = fade), fl - Offset(26f, 46f), Size(26f, 18f))
            label(word, proj.at(0.5f, v) - Offset(0f, 34f), proj.r(v) * 1.3f * popIn(min(1f, sec / 0.4f)), c.copy(alpha = fade), tf, pill = true)
        }
    }
}


/* ---------- the analyst's overlays ---------- */

/**
 * Who controls which space (each patch of grass in the colour of the side whose player is nearest), the passes open and closed
 * from the man on the ball (closed when an opponent stands within a couple of metres of the line), and each side's block
 * (its outfield players' outline with its width and depth in metres).
 */
private fun DrawScope.drawAnalysis(board: StudioBoard, proj: Proj, where: (SPlayer) -> Offset, ball: Offset, tf: Typeface) {
    val on = board.players.filter { it.side == 0 || board.showAway }
    val at = on.associateWith { where(it) }
    fun metres(a: Offset, b: Offset) = sqrt(((a.x - b.x) * 68f).pow(2) + ((a.y - b.y) * 105f).pow(2))
    if (StudioPrefs.space && on.isNotEmpty()) {
        val cols = 34; val rows = 52
        val home = Color(board.home.color); val away = Color(board.away.color)
        for (j in 0 until rows) for (i in 0 until cols) {
            val u0 = i / cols.toFloat(); val v0 = j / rows.toFloat(); val u1 = (i + 1) / cols.toFloat(); val v1 = (j + 1) / rows.toFloat()
            val c = Offset((u0 + u1) / 2, (v0 + v1) / 2)
            val near = on.minByOrNull { metres(at[it]!!, c) } ?: continue
            val col = if (near.side == 0) home else away
            val a = proj.at(u0, v0); val b = proj.at(u1, v0); val cc = proj.at(u1, v1); val d = proj.at(u0, v1)
            drawPath(Path().apply { moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(cc.x, cc.y); lineTo(d.x, d.y); close() }, col.copy(alpha = 0.2f))
        }
    }
    if (StudioPrefs.block) for (side in 0..1) {
        if (side == 1 && !board.showAway) continue
        val keeper = board.keeperOf(side)
        val pts = on.filter { it.side == side && it != keeper }.map { at[it]!! }
        if (pts.size < 3) continue
        val hull = convexHull(pts).map { proj.at(it) }
        val team = Color((if (side == 0) board.home else board.away).color)
        val path = Path().apply { hull.forEachIndexed { i, q -> if (i == 0) moveTo(q.x, q.y) else lineTo(q.x, q.y) }; close() }
        drawPath(path, team.copy(alpha = 0.14f))
        drawPath(path, team.copy(alpha = 0.9f), style = Stroke(2.5f, join = StrokeJoin.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 8f))))
        val wM = (pts.maxOf { it.x } - pts.minOf { it.x }) * 68f; val dM = (pts.maxOf { it.y } - pts.minOf { it.y }) * 105f
        val top = hull.minByOrNull { it.y }!!
        label(app.goallive.live.L("عرض {0} · عمق {1} متر", fa(wM.roundToInt()), fa(dM.roundToInt())), top - Offset(0f, 16f), proj.r(0.5f) * 0.75f, Color.White, tf, pill = true)
    }
    if (StudioPrefs.lanes) {
        val holder = on.minByOrNull { metres(at[it]!!, ball) } ?: return
        if (metres(at[holder]!!, ball) > 4f) return
        val from = at[holder]!!
        val rivals = on.filter { it.side != holder.side }.map { at[it]!! }
        for (m in on.filter { it.side == holder.side && it != holder }) {
            val to = at[m]!!
            // closed when a rival stands within 2.2 m of the pass, away from both ends
            val closed = rivals.any { r ->
                val ab = to - from; val len2 = ab.x * ab.x * 68f * 68f + ab.y * ab.y * 105f * 105f
                val t = if (len2 == 0f) 0f else (((r - from).x * ab.x * 68f * 68f + (r - from).y * ab.y * 105f * 105f) / len2)
                t in 0.08f..0.95f && metres(r, from + ab * t) < 2.2f
            }
            val a = proj.at(from); val b = proj.at(to)
            if (closed) drawLine(Color(0xFFFF5A6E).copy(alpha = 0.7f), a, b, 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 10f)), cap = StrokeCap.Round)
            else {
                drawLine(Color(0xFF39D98A).copy(alpha = 0.35f), a, b, 9f, cap = StrokeCap.Round)
                drawLine(Color(0xFF39D98A), a, b, 3.5f, cap = StrokeCap.Round)
            }
            drawCircle(if (closed) Color(0xFFFF5A6E) else Color(0xFF39D98A), 6f, b)
        }
    }
}

/** The outline round [p]: its convex hull, in order. */
private fun convexHull(p: List<Offset>): List<Offset> {
    val pts = p.distinct().sortedWith(compareBy<Offset> { it.x }.thenBy { it.y })
    if (pts.size < 3) return pts
    fun cross(o: Offset, a: Offset, b: Offset) = (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)
    val lower = ArrayList<Offset>(); for (q in pts) { while (lower.size >= 2 && cross(lower[lower.size - 2], lower.last(), q) <= 0) lower.removeAt(lower.size - 1); lower += q }
    val upper = ArrayList<Offset>(); for (q in pts.reversed()) { while (upper.size >= 2 && cross(upper[upper.size - 2], upper.last(), q) <= 0) upper.removeAt(upper.size - 1); upper += q }
    return lower.dropLast(1) + upper.dropLast(1)
}
