package app.goallive.live.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import app.goallive.live.L
import app.goallive.live.Lang
import app.goallive.live.R
import app.goallive.live.data.Clubs
import app.goallive.live.data.Predictions
import app.goallive.live.data.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * «کارت خودت»: the user as a player card. Their photo in a game-style shield in their team's colours, their name, a
 * position, and a rating made of how they play the app (predictions right and exact, fantasy points, the quiz and
 * prediction streaks, the teams they follow). Every frame is free. Shared as a story picture.
 */

/** A frame: its name, the card's two colours, its edge and its ink. "team" takes the team's own colours. */
internal class CardFrame(val key: String, val title: String, val a: Int, val b: Int, val edge: Int, val ink: Int)

internal fun cardFrames(team: Int): List<CardFrame> = listOf(
    CardFrame("gold", L("طلایی"), 0xFF6B4A10.toInt(), 0xFF1A1206.toInt(), 0xFFF5C542.toInt(), 0xFFFFE9A8.toInt()),
    CardFrame("team", L("رنگ تیم"), ColorUtils.blendARGB(team, 0xFFFFFFFF.toInt(), 0.1f), ColorUtils.blendARGB(team, 0xFF000000.toInt(), 0.6f), 0xFFF5C542.toInt(), 0xFFFFFFFF.toInt()),
    CardFrame("neon", L("نئون"), 0xFF1B2A6B.toInt(), 0xFF07081A.toInt(), 0xFF45E3FF.toInt(), 0xFFCFF8FF.toInt()),
    CardFrame("silver", L("نقره‌ای"), 0xFF5C6773.toInt(), 0xFF161A20.toInt(), 0xFFE3E8EE.toInt(), 0xFFFFFFFF.toInt()),
    CardFrame("pitch", L("زمین فوتبال"), 0xFF1E8A44.toInt(), 0xFF062312.toInt(), 0xFFE4FF3A.toInt(), 0xFFFFFFFF.toInt()),
    CardFrame("fire", L("آتش"), 0xFFB3261E.toInt(), 0xFF2A0705.toInt(), 0xFFFF8A3D.toInt(), 0xFFFFE0C2.toInt()),
    CardFrame("icon", L("اسطوره"), 0xFFF3EEE2.toInt(), 0xFFB9A98A.toInt(), 0xFF8A6A2A.toInt(), 0xFF2A1E08.toInt()),
)

internal val CARD_POSITIONS get() = listOf("ST" to L("مهاجم"), "CM" to L("هافبک"), "CB" to L("مدافع"), "GK" to L("دروازه‌بان"))

/** The six numbers on the card and its overall rating, from the user's own play; never below a respectable floor. */
internal fun cardStats(): Pair<Int, List<Pair<String, Int>>> {
    val p = Store.prefs()
    val settled = Predictions.settled.size
    val right = if (settled > 0) Predictions.right.toFloat() / settled else 0f
    val exact = if (settled > 0) Predictions.exact.toFloat() / settled else 0f
    val fantasy = runCatching { org.json.JSONObject(p.getString("fantasy", "{}")!!) }.getOrNull()?.let { o ->
        // every league's game, and the older single game
        val by = o.optJSONObject("by")
        val sum = { j: org.json.JSONObject? -> j?.optJSONObject("points")?.let { pts -> pts.keys().asSequence().sumOf { pts.optInt(it) } } ?: 0 }
        sum(o) + (by?.keys()?.asSequence()?.sumOf { sum(by.optJSONObject(it)) } ?: 0)
    } ?: 0
    val quiz = p.getInt("quizStreak", 0)
    val streak = Predictions.streak
    val follows = Store.teams.size + Store.leagues.size
    fun clamp(x: Float) = x.roundToInt().coerceIn(40, 99)
    val stats = listOf(
        L("پیش‌بینی") to clamp(if (settled == 0) 62f else 52f + 47f * right),
        L("دقت") to clamp(if (settled == 0) 58f else 50f + 49f * min(1f, exact * 2.5f)),
        L("فانتزی") to clamp(if (fantasy == 0) 60f else 58f + min(41f, fantasy / 8f)),
        L("کوئیز") to clamp(60f + quiz * 5f),
        L("پیاپی") to clamp(58f + streak * 6f),
        L("هوادار") to clamp(64f + follows * 3f),
    )
    val overall = (stats.sumOf { it.second } / stats.size.toFloat() + if (settled >= 20) 3f else 0f).roundToInt().coerceIn(60, 99)
    return overall to stats
}

private const val CW = 1080
private const val CH = 1920

/** The card as a story picture. */
internal fun drawMyCard(ctx: Context, name: String, pos: String, team: Int, teamName: String, teamColor: Int, frame: CardFrame, photo: Bitmap?, crest: Bitmap?): Bitmap {
    val bmp = Bitmap.createBitmap(CW, CH, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val black = if (Lang.en) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else ResourcesCompat.getFont(ctx, R.font.vazir_black) ?: Typeface.DEFAULT_BOLD
    val bold = if (Lang.en) Typeface.DEFAULT_BOLD else ResourcesCompat.getFont(ctx, R.font.vazir_bold) ?: Typeface.DEFAULT_BOLD
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    val w = CW.toFloat(); val h = CH.toFloat()
    // the page: deep, lit in the frame's edge colour, rays from above, sparkles
    p.shader = LinearGradient(0f, 0f, 0f, h, intArrayOf(ColorUtils.blendARGB(frame.b, 0xFF000000.toInt(), 0.2f), 0xFF05060A.toInt(), ColorUtils.blendARGB(frame.b, 0xFF000000.toInt(), 0.5f)),
        floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP); c.drawRect(0f, 0f, w, h, p)
    p.shader = RadialGradient(w / 2, h * 0.47f, w * 0.95f, intArrayOf(ColorUtils.setAlphaComponent(glowOf(frame.edge), 110), 0), null, Shader.TileMode.CLAMP); c.drawRect(0f, 0f, w, h, p); p.shader = null
    rays(c, CW, CH, glowOf(frame.edge), 1.3f)
    sparkles(c, CW, CH, 50, frame.edge, 5)
    // the title
    text(c, L("کارت من در گل‌لایو"), w / 2, 150f, 58f, 0xFFFFFFFF.toInt(), black)
    text(c, L("نمره از روی پیش‌بینی، فانتزی و کوئیز"), w / 2, 225f, 32f, 0xB3FFFFFF.toInt(), bold)

    // the card: a shield 780 wide
    val cw = 780f; val ch = cw * 1.42f
    val l = (w - cw) / 2; val top = 300f; val r = l + cw; val b = top + ch; val cx = w / 2
    val shield = Path().apply {
        moveTo(l + cw * 0.13f, top); lineTo(r - cw * 0.13f, top)
        quadTo(r - cw * 0.02f, top + ch * 0.005f, r, top + ch * 0.09f)
        lineTo(r, top + ch * 0.8f); quadTo(r, top + ch * 0.86f, r - cw * 0.09f, top + ch * 0.895f)
        lineTo(cx + cw * 0.09f, top + ch * 0.985f); quadTo(cx, b + ch * 0.008f, cx - cw * 0.09f, top + ch * 0.985f)
        lineTo(l + cw * 0.09f, top + ch * 0.895f); quadTo(l, top + ch * 0.86f, l, top + ch * 0.8f)
        lineTo(l, top + ch * 0.09f); quadTo(l + cw * 0.02f, top + ch * 0.005f, l + cw * 0.13f, top); close()
    }
    p.maskFilter = BlurMaskFilter(60f, BlurMaskFilter.Blur.OUTER); p.color = ColorUtils.setAlphaComponent(frame.edge, 200); c.drawPath(shield, p); p.maskFilter = null
    c.save(); c.clipPath(shield)
    p.shader = LinearGradient(l, top, r, b, intArrayOf(frame.a, frame.b, frame.a), floatArrayOf(0f, 0.62f, 1f), Shader.TileMode.CLAMP); c.drawRect(l, top, r, b, p)
    p.shader = LinearGradient(l, top, r, top + ch * 0.6f, intArrayOf(0x00FFFFFF, 0x38FFFFFF, 0x00FFFFFF), floatArrayOf(0.25f, 0.42f, 0.6f), Shader.TileMode.CLAMP)
    c.drawRect(l, top, r, b, p); p.shader = null
    // fine diagonal lines and a halo behind the photo
    p.color = ColorUtils.setAlphaComponent(frame.edge, 16); p.strokeWidth = 3f
    var k = -ch; while (k < cw) { c.drawLine(l + k, top, l + k + ch, b, p); k += 22f }
    p.shader = RadialGradient(cx + cw * 0.08f, top + ch * 0.3f, cw * 0.5f, intArrayOf(ColorUtils.setAlphaComponent(frame.edge, 90), 0), null, Shader.TileMode.CLAMP)
    c.drawCircle(cx + cw * 0.08f, top + ch * 0.3f, cw * 0.5f, p); p.shader = null
    // the photo, fading into the card at its foot
    val ps = cw * 0.7f; val px = if (Lang.en) r - ps - cw * 0.03f else l + cw * 0.03f; val py = top + ch * 0.06f
    if (photo != null) {
        val n = c.saveLayer(px, py, px + ps, py + ps, null)
        c.drawBitmap(photo, null, RectF(px, py, px + ps, py + ps), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        val mask = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN); shader = LinearGradient(0f, py + ps * 0.6f, 0f, py + ps, 0xFFFFFFFF.toInt(), 0, Shader.TileMode.CLAMP) }
        c.drawRect(px, py, px + ps, py + ps, mask); c.restoreToCount(n)
    } else {
        // no photo yet: a silhouette
        p.color = ColorUtils.setAlphaComponent(frame.ink, 60)
        c.drawCircle(px + ps / 2, py + ps * 0.36f, ps * 0.17f, p)
        c.drawRoundRect(RectF(px + ps * 0.22f, py + ps * 0.58f, px + ps * 0.78f, py + ps * 1.05f), ps * 0.2f, ps * 0.2f, p)
    }
    c.restore()
    // the rating column: overall, position, team crest
    val (overall, stats) = cardStats()
    val colX = if (Lang.en) l + cw * 0.16f else r - cw * 0.16f
    text(c, fa(overall), colX, top + ch * 0.13f, cw * 0.17f, frame.ink, black)
    text(c, CARD_POSITIONS.firstOrNull { it.first == pos }?.second ?: pos, colX, top + ch * 0.215f, cw * 0.05f, frame.ink, black)
    p.color = ColorUtils.setAlphaComponent(frame.ink, 150); c.drawRect(colX - cw * 0.07f, top + ch * 0.25f, colX + cw * 0.07f, top + ch * 0.25f + 4f, p)
    crest?.let { cb -> val s = cw * 0.13f; c.drawBitmap(cb, null, RectF(colX - s / 2, top + ch * 0.27f, colX + s / 2, top + ch * 0.27f + s), Paint(Paint.FILTER_BITMAP_FLAG)) }
    BitmapFactory.decodeResource(ctx.resources, R.drawable.logo)?.let { lg ->
        val s = cw * 0.1f; val y = top + ch * 0.27f + cw * 0.15f
        p.color = 0xFFFFFFFF.toInt(); c.drawRoundRect(RectF(colX - s / 2, y, colX + s / 2, y + s), s * 0.25f, s * 0.25f, p)
        c.drawBitmap(lg, null, RectF(colX - s / 2 + 5, y + 5, colX + s / 2 - 5, y + s - 5), Paint(Paint.FILTER_BITMAP_FLAG))
    }
    // the name between two rules
    val ny = top + ch * 0.6f
    p.color = ColorUtils.setAlphaComponent(frame.edge, 220)
    c.drawRect(l + cw * 0.1f, ny - cw * 0.075f, r - cw * 0.1f, ny - cw * 0.075f + 4f, p)
    c.drawRect(l + cw * 0.1f, ny + cw * 0.07f, r - cw * 0.1f, ny + cw * 0.07f + 4f, p)
    fit(c, name.ifBlank { L("هوادار") }, cx, ny, cw * 0.095f, frame.ink, black, cw * 0.8f)
    // the six numbers in two columns
    val gx = listOf(cx - cw * 0.2f, cx + cw * 0.2f); val gy0 = top + ch * 0.68f; val rowH = ch * 0.055f
    stats.forEachIndexed { i, (label, v) ->
        val col = if (Lang.en) i / 3 else 1 - i / 3; val row = i % 3
        val x = gx[col]; val y = gy0 + row * rowH
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = black; textSize = cw * 0.06f; color = frame.ink }
        val lp = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = bold; textSize = cw * 0.042f; color = ColorUtils.setAlphaComponent(frame.ink, 200) }
        val num = fa(v); val nw = tp.measureText(num); val lw = lp.measureText(label); val gap = 18f; val total = nw + gap + lw
        val fm = tp.fontMetrics; val base = y - (fm.ascent + fm.descent) / 2
        // the number on the reading start, then its name
        if (Lang.en) { c.drawText(num, x - total / 2, base, tp); c.drawText(label, x - total / 2 + nw + gap, base, lp) }
        else { c.drawText(num, x + total / 2 - nw, base, tp); c.drawText(label, x + total / 2 - nw - gap - lw, base, lp) }
    }
    p.color = ColorUtils.setAlphaComponent(frame.edge, 120); c.drawRect(cx - 2f, gy0 - rowH * 0.4f, cx + 2f, gy0 + rowH * 2.4f, p)
    // the edge, doubled
    p.style = Paint.Style.STROKE; p.strokeWidth = 12f; p.color = frame.edge; c.drawPath(shield, p)
    c.save(); c.scale(0.94f, 0.955f, cx, top + ch * 0.48f); p.strokeWidth = 3f; p.color = ColorUtils.setAlphaComponent(frame.edge, 140); c.drawPath(shield, p); c.restore()
    p.style = Paint.Style.FILL
    // the foot: the team and the app
    text(c, teamName, w / 2, b + 90f, 42f, 0xFFFFFFFF.toInt(), black)
    text(c, L("کارت خودت را در گل‌لایو بساز · کافه‌بازار"), w / 2, b + 160f, 32f, 0xB3FFFFFF.toInt(), bold)
    grain(c, CW, CH, false)
    return bmp
}

private fun text(c: Canvas, s: String, cx: Float, cy: Float, size: Float, color: Int, tf: Typeface) {
    val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; typeface = tf; this.color = color; textAlign = Paint.Align.CENTER; setShadowLayer(size * 0.12f, 0f, size * 0.05f, 0x99000000.toInt()) }
    val fm = tp.fontMetrics; c.drawText(s, cx, cy - (fm.ascent + fm.descent) / 2, tp)
}

private fun fit(c: Canvas, s: String, cx: Float, cy: Float, size: Float, color: Int, tf: Typeface, maxW: Float) {
    val tp = android.text.TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; typeface = tf; this.color = color; textAlign = Paint.Align.CENTER; setShadowLayer(size * 0.1f, 0f, 4f, 0x80000000.toInt()) }
    while (tp.measureText(s) > maxW && tp.textSize > size * 0.55f) tp.textSize -= 2f
    val t = android.text.TextUtils.ellipsize(s, tp, maxW, android.text.TextUtils.TruncateAt.END).toString()
    val fm = tp.fontMetrics; c.drawText(t, cx, cy - (fm.ascent + fm.descent) / 2, tp)
}

/** The photo picked, as a centred square of at most 900 px, kept in the app's files. */
private fun loadPhoto(ctx: Context, uri: Uri): Bitmap? = runCatching {
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    var s = 1; while (opts.outWidth / (s * 2) >= 900 && opts.outHeight / (s * 2) >= 900) s *= 2
    val raw = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = s }) } ?: return null
    // turned the way the camera held it
    val rot = runCatching { ctx.contentResolver.openInputStream(uri)?.use {
        when (android.media.ExifInterface(it).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1)) { 6 -> 90; 3 -> 180; 8 -> 270; else -> 0 }
    } ?: 0 }.getOrDefault(0)
    val turned = if (rot != 0) Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, android.graphics.Matrix().apply { postRotate(rot.toFloat()) }, true) else raw
    val side = min(turned.width, turned.height)
    // a little above the middle, where a face usually is
    val y = ((turned.height - side) * 0.3f).roundToInt()
    val sq = Bitmap.createBitmap(turned, (turned.width - side) / 2, y, side, side)
    val out = if (side > 900) Bitmap.createScaledBitmap(sq, 900, 900, true) else sq
    File(ctx.filesDir, "mycard.jpg").outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 92, it) }
    out
}.getOrNull()

@Composable
fun MyCardScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = Store.prefs()
    val my = Store.myTeam
    var name by remember { mutableStateOf(prefs.getString("mycard.name", null) ?: app.goallive.live.data.Account.me?.name ?: "") }
    var pos by remember { mutableStateOf(prefs.getString("mycard.pos", "ST")!!) }
    var frameKey by remember { mutableStateOf(prefs.getString("mycard.frame", "gold")!!) }
    var team by remember { mutableIntStateOf(prefs.getInt("mycard.team", my?.id ?: 0)) }
    var photo by remember { mutableStateOf<Bitmap?>(null) }
    var crest by remember { mutableStateOf<Bitmap?>(null) }
    var card by remember { mutableStateOf<Bitmap?>(null) }
    var version by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { photo = withContext(Dispatchers.IO) { File(ctx.filesDir, "mycard.jpg").takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) } } }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { photo = withContext(Dispatchers.IO) { loadPhoto(ctx, uri) } ?: photo }
    }
    val teams = buildList { my?.let { add(it.id to it.name) }; Store.teams.forEach { (id, n) -> if (none { it.first == id }) add(id to n) } }
    val teamName = teams.firstOrNull { it.first == team }?.let { teamName(it.first, it.second) } ?: ""
    val teamColor = Clubs.get(team)?.c1?.toInt() ?: LocalAccent.current.toArgb()
    val frames = cardFrames(teamColor)
    val frame = frames.firstOrNull { it.key == frameKey } ?: frames.first()
    LaunchedEffect(team) { crest = if (team > 0) image(ctx, teamLogo(team, true), 300) else null }
    // redrawn whenever something on it changes; it turns over each time
    val flip = remember { Animatable(1f) }
    LaunchedEffect(name, pos, frameKey, team, photo, crest) {
        prefs.edit().putString("mycard.name", name).putString("mycard.pos", pos).putString("mycard.frame", frameKey).putInt("mycard.team", team).apply()
        card = withContext(Dispatchers.Default) { drawMyCard(ctx, name, pos, team, teamName, teamColor, frame, photo, crest) }
        version++
        flip.snapTo(0f); flip.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = BottomSpace)) {
        item {
            T(L("کارت خودت"), 18, FontWeight.Black, modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp))
            T(L("عکست، تیمت و نمره‌ای که از پیش‌بینی، فانتزی و کوئیز می‌گیری؛ همه‌ی قاب‌ها رایگان"), 13, color = C.mute, modifier = Modifier.padding(horizontal = 4.dp))
        }
        item {
            Box(Modifier.padding(top = 14.dp).fillMaxWidth().aspectRatio(CW / CH.toFloat()).clip(RoundedCornerShape(24.dp)).background(C.card2)
                .graphicsLayer { rotationY = (1f - flip.value) * 90f; cameraDistance = 16f * density }, contentAlignment = Alignment.Center) {
                card?.let { Image(it.asImageBitmap(), L("کارت تو"), Modifier.fillMaxSize()) }
            }
        }
        item {
            Row(Modifier.padding(top = 14.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BigButton(if (photo == null) L("انتخاب عکس") else L("عوض کردن عکس"), Modifier.weight(1f), C.card) {
                    pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                BigButton(L("استوری کن"), Modifier.weight(1f), LocalAccent.current, LocalAccentInk.current) { card?.let { shareCard(ctx, it) } }
            }
        }
        item {
            SectionTitle(L("اسم روی کارت"))
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.card).border(1.dp, C.line, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 14.dp)) {
                if (name.isEmpty()) T(L("اسمت را بنویس"), 15, color = C.faint)
                BasicTextField(name, { if (it.length <= 18) name = it }, Modifier.fillMaxWidth(), singleLine = true,
                    textStyle = vz(15.sp, FontWeight.Bold).copy(color = C.ink), cursorBrush = SolidColor(LocalAccent.current))
            }
        }
        item {
            SectionTitle(L("پست"))
            Seg(CARD_POSITIONS, pos) { pos = it }
        }
        if (teams.isNotEmpty()) item {
            SectionTitle(L("تیم"))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(teams) { (id, n) ->
                    val on = id == team
                    Row(Modifier.clip(RoundedCornerShape(50)).background(if (on) LocalAccent.current.copy(alpha = 0.18f) else C.card)
                        .border(1.dp, if (on) LocalAccent.current else C.line, RoundedCornerShape(50)).clickable { team = id }
                        .padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Crest(id, 22.dp); Spacer(Modifier.width(6.dp)); TeamText(id, n, size = 13, w = FontWeight.Bold)
                    }
                }
            }
        }
        item {
            SectionTitle(L("قاب"))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(frames) { f ->
                    val on = f.key == frameKey
                    Box(Modifier.width(78.dp).height(104.dp).clip(RoundedCornerShape(16.dp))
                        .background(Brush.linearGradient(listOf(Color(f.a), Color(f.b))))
                        .border(if (on) 3.dp else 1.5.dp, Color(f.edge).copy(alpha = if (on) 1f else 0.6f), RoundedCornerShape(16.dp))
                        .clickable { frameKey = f.key }, contentAlignment = Alignment.BottomCenter) {
                        T(f.title, 12, FontWeight.Black, Color(f.ink), Modifier.padding(bottom = 8.dp), maxLines = 1)
                    }
                }
            }
        }
        item {
            val (overall, stats) = cardStats()
            SectionTitle(L("نمره‌ات {0} است", fa(overall)))
            Card(pad = PaddingValues(14.dp)) {
                stats.forEach { (label, v) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        T(label, 13, FontWeight.Bold, modifier = Modifier.width(80.dp))
                        Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(C.card2)) {
                            Box(Modifier.fillMaxWidth(v / 99f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(LocalAccent.current))
                        }
                        T(fa(v), 13, FontWeight.Black, modifier = Modifier.padding(start = 10.dp))
                    }
                }
                T(L("پیش‌بینی درست، فانتزی، کوئیز هر روز و دنبال کردن تیم‌ها نمره‌ات را بالا می‌برند."), 12, color = C.mute, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

@Composable
private fun BigButton(label: String, modifier: Modifier, bg: Color, ink: Color = C.ink, onClick: () -> Unit) {
    Box(modifier.height(52.dp).clip(RoundedCornerShape(18.dp)).background(bg).border(1.dp, C.line, RoundedCornerShape(18.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center) { T(label, 15, FontWeight.Black, ink) }
}

private fun shareCard(ctx: Context, b: Bitmap) {
    val uri = runCatching {
        val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
        val f = File(dir, "goallive-my-card.png").also { out -> out.outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
    }.getOrNull() ?: return
    val send = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        .putExtra(Intent.EXTRA_TEXT, L("کارت من در گل‌لایو؛ کارت خودت را بساز") + "\n" + BAZAAR_URL)
    ctx.startActivity(Intent.createChooser(send, L("اشتراک‌گذاری")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

@Suppress("unused") private val keepTrig = cos(0.0) + sin(0.0)
