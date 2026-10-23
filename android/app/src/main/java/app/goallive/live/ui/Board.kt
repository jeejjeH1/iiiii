package app.goallive.live.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import app.goallive.live.L
import app.goallive.live.Lang
import app.goallive.live.R
import app.goallive.live.data.Api
import app.goallive.live.data.d
import app.goallive.live.data.o
import app.goallive.live.data.objs
import app.goallive.live.data.path
import app.goallive.live.data.s
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** A player disc on the board: position as a share of the pitch (0..1, own goal at the bottom). */
private class Token(val id: Int, x: Float, y: Float, label: String, val rival: Boolean) {
    var x by mutableFloatStateOf(x)
    var y by mutableFloatStateOf(y)
    var label by mutableStateOf(label)
}

/** A finger stroke, in pitch shares, ending in an arrow head. */
private class Stroke2(val color: Color, val points: MutableList<Offset> = mutableStateListOf())

/** Formation lines from the back: 4-3-3 is [4, 3, 3] (the keeper is added). */
private val FORMATIONS = listOf("4-3-3", "4-4-2", "4-2-3-1", "3-5-2", "3-4-3", "5-3-2")

private fun place(f: String, rival: Boolean, start: Int): List<Token> {
    val lines = f.split('-').mapNotNull { it.toIntOrNull() }
    // own goal at the bottom: the keeper, then each line further up; the opponent mirrored at the top
    fun y(v: Float) = if (rival) 1f - v else v
    val out = arrayListOf(Token(start, 0.5f, y(0.93f), "1", rival))
    var n = 2
    lines.forEachIndexed { li, count ->
        val depth = 0.78f - li * (0.48f / (lines.size - 1).coerceAtLeast(1))
        for (k in 0 until count) out += Token(start + n - 1, (k + 1f) / (count + 1f), y(depth), "${n++}", rival)
    }
    return out
}

/**
 * "تخته تاکتیک": a pitch with eleven discs (and optionally the opponent's) to move with a finger, arrows to draw,
 * and a picture of it all to share. Opened from a match line-up it starts with that team's real line-up and names.
 */
@Composable
fun BoardScreen(matchId: Int, side: String) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val accent = LocalAccent.current
    var formation by remember { mutableStateOf("4-3-3") }
    val tokens = remember { mutableStateListOf<Token>().apply { addAll(place("4-3-3", false, 1)) } }
    var rivals by remember { mutableStateOf(false) }
    val strokes = remember { mutableStateListOf<Stroke2>() }
    var mode by remember { mutableStateOf("move") }
    var pen by remember { mutableStateOf(Color.White) }
    var editing by remember { mutableStateOf<Token?>(null) }
    var title by remember { mutableStateOf("") }

    // from a match: that team's eleven with their names and real positions
    LaunchedEffect(matchId, side) {
        if (matchId <= 0) return@LaunchedEffect
        runCatching {
            val d = Api.fm("matchDetails", mapOf("matchId" to matchId), 600_000)
            val team = d.path("content.lineup").o(if (side == "a") "awayTeam" else "homeTeam") ?: return@runCatching
            val xi = team.optJSONArray("starters").objs()
            if (xi.size < 11) return@runCatching
            title = teamName(team.optInt("id"), team.s("name") ?: "")
            team.s("formation")?.let { formation = it }
            tokens.removeAll { !it.rival }
            xi.forEachIndexed { k, p ->
                val v = p.o("verticalLayout")
                val x = (v.d("x") ?: 0.5).toFloat(); val y = (v.d("y") ?: 0.5).toFloat()
                tokens += Token(k + 1, x, 0.95f - y * 0.72f, personName(p.s("lastName") ?: p.s("name") ?: "${k + 1}"), false)
            }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 14.dp, end = 14.dp, bottom = BottomSpace)) {
        Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            T(L("📋 تخته تاکتیک"), 18, FontWeight.Black, modifier = Modifier.weight(1f))
            if (title.isNotEmpty()) T(title, 13, FontWeight.Bold, C.mute)
        }
        // formation chips
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FORMATIONS.take(6).forEach { f ->
                val on = f == formation
                Box(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (on) accent else C.card2).clickable {
                    formation = f
                    val keep = tokens.filter { !it.rival }.associate { it.id to it.label }
                    tokens.removeAll { !it.rival }
                    // names typed or loaded stay with their shirt order
                    tokens += place(f, false, 1).onEach { t -> keep[t.id]?.let { t.label = it } }
                }.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                    Ltr(fa(f), style = vz(12.sp, FontWeight.ExtraBold), color = if (on) LocalAccentInk.current else C.ink, raw = true)
                }
            }
        }
        Seg(listOf("move" to L("جابه‌جا کردن"), "draw" to L("کشیدن فلش")), mode) { mode = it }
        Spacer(Modifier.height(8.dp))
        // the board itself: physical left/right, whatever the language
        var box by remember { mutableStateOf(IntSize.Zero) }
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Box(Modifier.fillMaxWidth().aspectRatio(68f / 100f).clip(RoundedCornerShape(18.dp)).onSizeChanged { box = it }) {
                Canvas(Modifier.fillMaxSize()) {
                    grass(vertical = true)
                    for (s in strokes) drawStroke(s, size.width, size.height)
                }
                // drawing layer on top only while drawing
                if (mode == "draw") Box(Modifier.fillMaxSize().pointerInput(pen) {
                    detectDragGestures(
                        onDragStart = { o -> strokes += Stroke2(pen).also { it.points += Offset(o.x / size.width, o.y / size.height) } },
                        onDrag = { ch, _ -> strokes.lastOrNull()?.points?.add(Offset(ch.position.x / size.width, ch.position.y / size.height)) },
                    )
                })
                val w = box.width.toFloat(); val h = box.height.toFloat()
                if (w > 0) for (t in tokens) {
                    val sizePx = 40.dp
                    // a fixed-width column so the disc's centre is the token's spot, with or without a name under it
                    Column(Modifier.offset { androidx.compose.ui.unit.IntOffset((t.x * w).toInt() - 40.dp.roundToPx(), (t.y * h).toInt() - sizePx.roundToPx() / 2) }.width(80.dp)
                        .then(if (mode == "move") Modifier.pointerInput(t) {
                            detectDragGestures { ch, drag -> ch.consume(); t.x = (t.x + drag.x / w).coerceIn(0.03f, 0.97f); t.y = (t.y + drag.y / h).coerceIn(0.03f, 0.97f) }
                        }.pointerInput(t) { detectTapGestures(onTap = { editing = t }) } else Modifier),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(sizePx).clip(CircleShape).background(if (t.rival) C.live else accent).border(2.dp, Color.White, CircleShape),
                            contentAlignment = Alignment.Center) {
                            androidx.compose.material3.Text(if (t.label.length <= 2) fa(t.label) else fa(t.id), color = if (t.rival) Color.White else LocalAccentInk.current,
                                style = vz(14.sp, FontWeight.Black))
                        }
                        if (t.label.length > 2) Box(Modifier.padding(top = 2.dp).clip(RoundedCornerShape(5.dp)).background(Color.Black.copy(alpha = 0.5f)).padding(horizontal = 4.dp)) {
                            androidx.compose.material3.Text(t.label, color = Color.White, style = vz(10.sp, FontWeight.Bold), maxLines = 1)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        if (mode == "draw") Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            for (c in listOf(Color.White, Color(0xFFFFD43B), Color(0xFFFF4D6A), Color(0xFF4DABF7))) {
                Box(Modifier.size(30.dp).clip(CircleShape).background(c).border(if (pen == c) 3.dp else 1.dp, if (pen == c) accent else C.line, CircleShape).clickable { pen = c })
            }
            Spacer(Modifier.weight(1f))
            Pill(L("برگشت"), onClick = { if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex) })
            Pill(L("پاک کردن"), onClick = { strokes.clear() })
        } else T(L("بازیکن‌ها را با انگشت جابه‌جا کن؛ برای نوشتن اسم روی هر کدام بزن."), 12, color = C.mute)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill(if (rivals) L("حذف حریف") else L("افزودن حریف"), onClick = {
                rivals = !rivals
                tokens.removeAll { it.rival }
                if (rivals) tokens += place("4-4-2", true, 100)
            })
            Pill(L("اشتراک تصویر"), PillKind.Accent, icon = Ic.share, onClick = {
                scope.launch { shareBoard(ctx, tokens.toList(), strokes.toList(), formation, title, accent) }
            })
        }
    }
    editing?.let { t ->
        var name by remember(t) { mutableStateOf(if (t.label.length > 2) t.label else "") }
        AlertDialog(onDismissRequest = { editing = null }, containerColor = C.cardSolid,
            title = { T(L("اسم بازیکن"), 16, FontWeight.ExtraBold) },
            text = { OutlinedTextField(name, { name = it.take(14) }, singleLine = true, placeholder = { T(L("مثلاً طارمی"), 13, color = C.mute) }) },
            confirmButton = { TextButton(onClick = { t.label = name.trim().ifEmpty { "${t.id}" }; editing = null }) { T(L("باشه"), 14, FontWeight.Bold, LocalAccent.current) } },
            dismissButton = { TextButton(onClick = { editing = null }) { T(L("انصراف"), 14, color = C.mute) } },
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStroke(s: Stroke2, w: Float, h: Float) {
    if (s.points.size < 2) return
    val p = Path()
    s.points.forEachIndexed { i, o -> if (i == 0) p.moveTo(o.x * w, o.y * h) else p.lineTo(o.x * w, o.y * h) }
    drawPath(p, s.color, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    val (ax, ay, ang) = arrowHead(s.points, w, h) ?: return
    val l = 14.dp.toPx()
    for (da in listOf(2.6, -2.6)) drawLine(s.color, Offset(ax, ay), Offset(ax + (l * cos(ang + da)).toFloat(), ay + (l * sin(ang + da)).toFloat()), 4.dp.toPx(), cap = StrokeCap.Round)
}

/** End point and direction of a stroke, taken over its last stretch so a shaky finger still points the right way. */
private fun arrowHead(pts: List<Offset>, w: Float, h: Float): Triple<Float, Float, Double>? {
    val end = pts.last()
    val back = pts.lastOrNull { hypot(((end.x - it.x) * w).toDouble(), ((end.y - it.y) * h).toDouble()) > 24 } ?: return null
    return Triple(end.x * w, end.y * h, atan2(((end.y - back.y) * h).toDouble(), ((end.x - back.x) * w).toDouble()))
}

/** The board as a picture (1080 wide) with the app's name, shared like the match card. */
private suspend fun shareBoard(ctx: Context, tokens: List<Token>, strokes: List<Stroke2>, formation: String, title: String, accent: Color) {
    val uri = runCatching {
        withContext(Dispatchers.IO) {
            val W = 1080; val PH = 1588; val H = PH + 220
            val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
            val c = android.graphics.Canvas(bmp)
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            c.drawColor(0xFF0B0F14.toInt())
            // grass and lines
            for (k in 0 until 10) { p.color = (if (k % 2 == 0) C.pitch else C.pitch2).toArgb(); c.drawRect(0f, 110f + PH * k / 10f, W.toFloat(), 110f + PH * (k + 1) / 10f, p) }
            p.style = Paint.Style.STROKE; p.strokeWidth = 4f; p.color = 0x8CFFFFFF.toInt()
            c.drawRect(6f, 116f, W - 6f, 110f + PH - 6f, p)
            c.drawLine(0f, 110f + PH / 2f, W.toFloat(), 110f + PH / 2f, p)
            c.drawCircle(W / 2f, 110f + PH / 2f, W * 0.13f, p)
            c.drawRect(W * 0.2f, 116f, W * 0.8f, 110f + PH * 0.15f, p)
            c.drawRect(W * 0.2f, 110f + PH * 0.85f, W * 0.8f, 110f + PH - 6f, p)
            // arrows
            p.strokeCap = Paint.Cap.ROUND; p.strokeJoin = Paint.Join.ROUND; p.strokeWidth = 10f
            for (s in strokes) {
                if (s.points.size < 2) continue
                p.color = s.color.toArgb()
                val path = android.graphics.Path()
                s.points.forEachIndexed { i, o -> val x = o.x * W; val y = 110f + o.y * PH; if (i == 0) path.moveTo(x, y) else path.lineTo(x, y) }
                c.drawPath(path, p)
                arrowHead(s.points, W.toFloat(), PH.toFloat())?.let { (ax, ay, ang) ->
                    for (da in listOf(2.6, -2.6)) c.drawLine(ax, 110f + ay, ax + (34 * cos(ang + da)).toFloat(), 110f + ay + (34 * sin(ang + da)).toFloat(), p)
                }
            }
            // discs
            val tf = if (Lang.en) Typeface.DEFAULT_BOLD else ResourcesCompat.getFont(ctx, R.font.vazir_black) ?: Typeface.DEFAULT_BOLD
            val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = tf; textAlign = Paint.Align.CENTER }
            for (t in tokens) {
                val x = t.x * W; val y = 110f + t.y * PH
                p.style = Paint.Style.FILL; p.color = if (t.rival) C.live.toArgb() else accent.toArgb(); c.drawCircle(x, y, 46f, p)
                p.style = Paint.Style.STROKE; p.strokeWidth = 6f; p.color = 0xFFFFFFFF.toInt(); c.drawCircle(x, y, 46f, p)
                tp.color = if (t.rival) 0xFFFFFFFF.toInt() else 0xFF0B1200.toInt(); tp.textSize = 40f
                c.drawText(fa(if (t.label.length <= 2) t.label else "${t.id}"), x, y + 14f, tp)
                if (t.label.length > 2) {
                    tp.textSize = 30f; tp.color = 0xFFFFFFFF.toInt()
                    p.style = Paint.Style.FILL; p.color = 0x88000000.toInt()
                    val tw = tp.measureText(t.label) / 2 + 10
                    c.drawRoundRect(x - tw, y + 54f, x + tw, y + 94f, 10f, 10f, p)
                    c.drawText(t.label, x, y + 84f, tp)
                }
            }
            // header and footer
            tp.color = 0xFFFFFFFF.toInt(); tp.textSize = 52f
            c.drawText(listOf(L("تخته تاکتیک"), title, fa(formation)).filter { it.isNotEmpty() }.joinToString(" · "), W / 2f, 76f, tp)
            tp.textSize = 36f; tp.color = 0xCCFFFFFF.toInt()
            c.drawText(L("ساخته‌شده با گل‌لایو · دانلود از بازار"), W / 2f, H - 70f, tp)
            val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
            val f = File(dir, "goallive-board.png").also { out -> out.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } }
            FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
        }
    }.getOrNull() ?: return
    val send = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        .putExtra(Intent.EXTRA_TEXT, L("تاکتیک من در گل‌لایو") + "\n" + BAZAAR_URL)
    ctx.startActivity(Intent.createChooser(send, L("اشتراک‌گذاری")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
