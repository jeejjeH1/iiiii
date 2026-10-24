package app.goallive.live.ui

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import app.goallive.live.L
import app.goallive.live.Lang
import app.goallive.live.R
import app.goallive.live.data.FaNames
import app.goallive.live.data.Predictions
import app.goallive.live.data.Store
import app.goallive.live.data.a
import app.goallive.live.data.b
import app.goallive.live.data.d
import app.goallive.live.data.i
import app.goallive.live.data.o
import app.goallive.live.data.objs
import app.goallive.live.data.path
import app.goallive.live.data.s
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

const val BAZAAR_URL = "https://cafebazaar.ir/app/app.goallive.live"

/* ---------- what the user can choose ---------- */

enum class CardFormat(val w: Int, val h: Int) { STORY(1080, 1920), PORTRAIT(1080, 1350), SQUARE(1080, 1080) }

val CARD_THEMES = listOf("teams", "night", "gold", "light", "pitch", "neon")
private fun themeName(k: String) = when (k) {
    "teams" -> L("رنگ تیم‌ها"); "night" -> L("شب"); "gold" -> L("طلایی"); "light" -> L("روشن"); "pitch" -> L("چمن"); else -> L("نئون")
}

/** Everything that shapes the picture; kept between shares. */
data class CardOptions(
    val format: CardFormat = CardFormat.STORY,
    val theme: String = "teams",
    val scorers: Boolean = true,
    val stats: Boolean = true,
    val best: Boolean = true,
    val momentum: Boolean = true,
    val prediction: Boolean = true,
    val caption: String = "",
    val sticker: String = "",
)

private fun loadOptions(): CardOptions = runCatching {
    val p = Store.prefs()
    CardOptions(
        format = CardFormat.valueOf(p.getString("card.format", "STORY") ?: "STORY"),
        theme = p.getString("card.theme", "teams") ?: "teams",
        scorers = p.getBoolean("card.scorers", true), stats = p.getBoolean("card.stats", true), best = p.getBoolean("card.best", true),
        momentum = p.getBoolean("card.momentum", true), prediction = p.getBoolean("card.prediction", true),
    )
}.getOrDefault(CardOptions())

private fun saveOptions(o: CardOptions) = runCatching {
    Store.prefs().edit().putString("card.format", o.format.name).putString("card.theme", o.theme).putBoolean("card.scorers", o.scorers)
        .putBoolean("card.stats", o.stats).putBoolean("card.best", o.best).putBoolean("card.momentum", o.momentum)
        .putBoolean("card.prediction", o.prediction).apply()
}

/** The match whose share studio is open (shown by the app's root, over any screen). */
object ShareStudio {
    var match by mutableStateOf<MatchData?>(null)
}

/** Opens the share studio for [m]: the user styles the picture, then shares or saves it. */
fun shareMatchCard(@Suppress("UNUSED_PARAMETER") ctx: Context, m: MatchData) { ShareStudio.match = m }

/* ---------- the studio ---------- */

/** Pictures the card needs, fetched once per match. */
class CardAssets(val home: Bitmap?, val away: Bitmap?, val logo: Bitmap?, val best: Bitmap?, val black: Typeface, val bold: Typeface, val hc: Int, val ac: Int, val league: Bitmap?)

private suspend fun image(ctx: Context, url: String, px: Int): Bitmap? = runCatching {
    kotlinx.coroutines.withTimeoutOrNull(6_000) { ctx.imageLoader.execute(ImageRequest.Builder(ctx).data(url).allowHardware(false).build()) }?.drawable?.toBitmap(px, px)
}.getOrNull()

internal suspend fun cardAssets(ctx: Context, m: MatchData, best: PlayerRating?): CardAssets {
    val en = Lang.en
    val home = image(ctx, teamLogo(m.homeId, true), 320); val away = image(ctx, teamLogo(m.awayId, true), 320)
    return CardAssets(
        home, away,
        BitmapFactory.decodeResource(ctx.resources, R.drawable.logo),
        best?.let { image(ctx, playerFace(it.id), 260) },
        if (en) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else ResourcesCompat.getFont(ctx, R.font.vazir_black) ?: Typeface.DEFAULT_BOLD,
        if (en) Typeface.DEFAULT_BOLD else ResourcesCompat.getFont(ctx, R.font.vazir_bold) ?: Typeface.DEFAULT_BOLD,
        cardColor(m.homeColor, home, 0xFF1E5BD8), cardColor(m.awayColor, away, 0xFFD62839),
        image(ctx, "https://images.fotmob.com/image_resources/logo/leaguelogo/${m.leagueId}.png", 200),
    )
}

/** GoalLive's best player of the match, if it was rated. */
internal fun bestOf(m: MatchData): PlayerRating? =
    runCatching { rateMatch(m) }.getOrDefault(emptyMap()).values.filter { it.minutes >= 20 }.maxByOrNull { it.value }

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ShareStudioSheet(m: MatchData, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val accent = LocalAccent.current
    var o by remember { mutableStateOf(loadOptions()) }
    val best = remember(m.d) { bestOf(m) }
    var art by remember { mutableStateOf<CardAssets?>(null) }
    LaunchedEffect(m.matchId) { art = withContext(Dispatchers.IO) { cardAssets(ctx, m, best) } }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(o, art) {
        val a = art ?: return@LaunchedEffect
        preview = withContext(Dispatchers.Default) { drawCard(ctx, m, o, a, best, 0.42f) }
        saveOptions(o)
    }
    var busy by remember { mutableStateOf(false) }
    fun full(then: (Bitmap) -> Unit) {
        val a = art ?: return
        busy = true
        scope.launch {
            val bmp = withContext(Dispatchers.Default) { drawCard(ctx, m, o, a, best, 1f) }
            busy = false; then(bmp)
        }
    }
    val state = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onClose, sheetState = state, containerColor = C.bg,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), dragHandle = {
            Box(Modifier.padding(top = 10.dp, bottom = 4.dp).width(44.dp).height(5.dp).clip(RoundedCornerShape(5.dp)).background(C.line2))
        }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 16.dp)) {
            T(L("✨ استودیوی اشتراک‌گذاری"), 18, FontWeight.Black, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
            // the picture, as it will be sent
            Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                val ratio = o.format.w.toFloat() / o.format.h
                Box(Modifier.height(if (o.format == CardFormat.STORY) 440.dp else 380.dp).aspectRatio(ratio).clip(RoundedCornerShape(18.dp))
                    .background(C.card2).border(1.dp, C.line2, RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
                    preview?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize()) } ?: T(L("در حال ساختن…"), 13, color = C.mute)
                }
            }
            StudioLabel(L("اندازه"))
            StudioChips(listOf(CardFormat.STORY to L("استوری"), CardFormat.PORTRAIT to L("پست عمودی"), CardFormat.SQUARE to L("پست مربع")), o.format) { o = o.copy(format = it) }
            StudioLabel(L("طرح"))
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(CARD_THEMES.size) { k ->
                    val t = CARD_THEMES[k]
                    val on = t == o.theme
                    Column(Modifier.clip(RoundedCornerShape(16.dp)).clickable { o = o.copy(theme = t) }.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(58.dp, 82.dp).clip(RoundedCornerShape(12.dp)).background(themeSwatch(t, art?.hc ?: deep(m.homeColor), art?.ac ?: deep(m.awayColor))).border(if (on) 3.dp else 1.dp, if (on) accent else C.line2, RoundedCornerShape(12.dp)))
                        T(themeName(t), 11, if (on) FontWeight.ExtraBold else FontWeight.Normal, if (on) accent else C.mute, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            StudioLabel(L("روی عکس باشد"))
            FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Toggle(L("گلزن‌ها"), o.scorers) { o = o.copy(scorers = it) }
                Toggle(L("آمار کلیدی"), o.stats) { o = o.copy(stats = it) }
                Toggle(L("بهترین بازیکن"), o.best) { o = o.copy(best = it) }
                Toggle(L("مومنتوم"), o.momentum) { o = o.copy(momentum = it) }
                if (Predictions.get(m.matchId) != null) Toggle(L("پیش‌بینی من"), o.prediction) { o = o.copy(prediction = it) }
            }
            StudioLabel(L("نوشته‌ی خودت"))
            Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.card2).border(1.dp, C.line2, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp)) {
                if (o.caption.isEmpty()) T(L("مثلاً: بهترین بازی فصل! 🔥"), 14, color = C.faint)
                BasicTextField(o.caption, { o = o.copy(caption = it.take(60)) }, Modifier.fillMaxWidth(), singleLine = true,
                    textStyle = vz(14.sp).copy(color = C.ink), cursorBrush = SolidColor(accent))
            }
            StudioLabel(L("استیکر"))
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val stickers = listOf("", "🔥", "⚽", "🏆", "😍", "💙", "❤️", "💛", "💚", "😭", "🤯", "👑", "🐐", "💪")
                items(stickers.size) { k ->
                    val st = stickers[k]
                    val on = st == o.sticker
                    Box(Modifier.size(44.dp).clip(CircleShape).background(if (on) accent.copy(alpha = 0.25f) else C.card2).border(if (on) 2.dp else 0.dp, accent, CircleShape)
                        .clickable { o = o.copy(sticker = st) }, contentAlignment = Alignment.Center) {
                        if (st.isEmpty()) T("✕", 14, color = C.mute) else T(st, 20)
                    }
                }
            }
            Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(accent).clickable(enabled = !busy && art != null) {
                    full { bmp -> sendCard(ctx, m, bmp) }
                }.padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
                    T(if (busy) L("صبر کنید…") else L("اشتراک‌گذاری"), 15, FontWeight.ExtraBold, LocalAccentInk.current)
                }
                Box(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).border(1.dp, C.line2, RoundedCornerShape(16.dp)).clickable(enabled = !busy && art != null) {
                    full { bmp -> saveCard(ctx, m, bmp) }
                }.padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
                    T(L("ذخیره در گالری"), 15, FontWeight.ExtraBold)
                }
            }
        }
    }
}

@Composable
private fun StudioLabel(s: String) = T(s, 13, FontWeight.ExtraBold, C.mute, Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp))

@Composable
private fun <K> StudioChips(options: List<Pair<K, String>>, selected: K, onPick: (K) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(options.size) { k ->
            val (key, label) = options[k]
            val on = key == selected
            Box(Modifier.clip(RoundedCornerShape(50)).background(if (on) LocalAccent.current else C.card2).clickable { onPick(key) }.padding(horizontal = 16.dp, vertical = 8.dp)) {
                T(label, 13, FontWeight.Bold, if (on) LocalAccentInk.current else C.ink)
            }
        }
    }
}

@Composable
private fun Toggle(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    val accent = LocalAccent.current
    Row(Modifier.clip(RoundedCornerShape(50)).background(if (on) accent.copy(alpha = 0.18f) else C.card2).border(1.dp, if (on) accent else C.line2, RoundedCornerShape(50))
        .clickable { onChange(!on) }.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        T(if (on) "✓ " else "", 12, FontWeight.Black, accent); T(label, 13, FontWeight.Bold, if (on) C.ink else C.mute)
    }
}

/** A small look at each design, for the picker. */
private fun themeSwatch(t: String, hc: Int, ac: Int): Brush {
    return when (t) {
        "teams" -> Brush.linearGradient(listOf(Color(hc), Color(hc), Color(ac), Color(ac)))
        "night" -> Brush.verticalGradient(listOf(Color(0xFF1A2350), Color(0xFF05060D)))
        "gold" -> Brush.verticalGradient(listOf(Color(0xFF4A3608), Color(0xFF0D0A04)))
        "light" -> Brush.verticalGradient(listOf(Color(0xFFFFFFFF), Color(0xFFEDE7DA)))
        "pitch" -> Brush.verticalGradient(listOf(Color(0xFF1F7A45), Color(0xFF0E3D22)))
        else -> Brush.linearGradient(listOf(Color(0xFF05050A), Color(hc).copy(alpha = 0.7f), Color(0xFF05050A), Color(ac).copy(alpha = 0.7f)))
    }
}

/** White writing sits on the team colours, so a light kit colour is taken down to a mid tone first. */
private fun deep(c: Color) = (if (c.luminance() > 0.45f) androidx.compose.ui.graphics.lerp(c, Color.Black, 0.55f) else c).toArgb()

private fun sendCard(ctx: Context, m: MatchData, bmp: Bitmap) {
    val text = "${teamName(m.homeId, m.homeName)} ${if (m.started) "${m.home.i("score") ?: 0}-${m.away.i("score") ?: 0}" else timeOf(m.time)} " +
        "${teamName(m.awayId, m.awayName)}\n" + L("نتایج زنده فوتبال در گل‌لایو") + "\n$BAZAAR_URL"
    val send = Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT, text)
    runCatching {
        val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
        val f = File(dir, "goallive-${m.matchId}.png").also { f -> f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
        send.setType("image/png").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }.onFailure { send.setType("text/plain") }
    ctx.startActivity(Intent.createChooser(send, L("اشتراک‌گذاری")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** Saves into Pictures/GoalLive (no permission needed from Android 10). */
private fun saveCard(ctx: Context, m: MatchData, bmp: Bitmap) {
    val ok = runCatching {
        if (Build.VERSION.SDK_INT < 29) error("old")
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "goallive-${m.matchId}-${System.currentTimeMillis() / 1000}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/GoalLive")
        }
        val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("no uri")
        ctx.contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } ?: error("no stream")
    }.isSuccess
    Toast.makeText(ctx, if (ok) L("در گالری ذخیره شد") else L("ذخیره نشد؛ از «اشتراک‌گذاری» استفاده کنید"), Toast.LENGTH_SHORT).show()
}

/* ---------- drawing ---------- */

private class CardLook(val ink: Int, val sub: Int, val panel: Int, val accent: Int, val stroke: Int)

/** The share picture: competition at the top, the crests and the score, then the chosen parts spread over the space, and the brand. Drawn at [scale]. */
internal fun drawCard(ctx: Context, m: MatchData, o: CardOptions, a: CardAssets, best: PlayerRating?, scale: Float): Bitmap {
    val W = o.format.w; val H = o.format.h
    val bmp = Bitmap.createBitmap((W * scale).toInt(), (H * scale).toInt(), Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.scale(scale, scale)
    val en = Lang.en
    val hc = a.hc; val ac = a.ac
    val look = background(c, o.theme, W, H, hc, ac)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    val story = o.format == CardFormat.STORY
    val sq = o.format == CardFormat.SQUARE
    val k = if (story) 1f else if (sq) 0.78f else 0.86f   // overall size of the writing
    val homeX = if (en) W * 0.25f else W * 0.75f
    val awayX = W - homeX

    // both crests, huge and faint, behind everything
    val wm = W * 0.95f
    for ((bm, x, y0) in listOf(Triple(a.home, homeX, H * 0.05f), Triple(a.away, awayX, H * 0.55f))) bm?.let {
        p.alpha = if (o.theme == "light") 16 else 22
        c.drawBitmap(it, null, RectF(x - wm / 2, y0, x + wm / 2, y0 + wm), p); p.alpha = 255
    }
    grain(c, W, H, o.theme == "light")

    // header: the competition's badge, its name and round, the day under it
    var y = if (story) 80f else if (sq) 46f else 54f
    val lr = 36f * k
    a.league?.let { lg ->
        p.color = 0xFFFFFFFF.toInt(); p.setShadowLayer(18f, 0f, 6f, 0x55000000); c.drawCircle(W / 2f, y + lr, lr + 10, p); p.clearShadowLayer()
        c.drawBitmap(lg, null, RectF(W / 2f - lr, y, W / 2f + lr, y + 2 * lr), p)
        y += 2 * lr + 26 * k
    }
    val round = m.general.s("leagueRoundName")?.let { if (it.all(Char::isDigit)) L(" | هفته {0}", fa(it)) else "" } ?: ""
    text(c, leagueName(m.leagueId, m.general.s("leagueName") ?: "") + round, W / 2f, y, 36f * k, look.ink, a.black, W - 160)
    y += 36f * k * 1.5f
    text(c, fa(persianDate(m.time, "EEEE d MMMM yyyy")), W / 2f, y, 27f * k, look.sub, a.bold, W - 160)
    val top = y + 27f * k * 1.4f + (if (story) 50f else 24f)
    val footer = if (story) 160f else if (sq) 110f else 120f

    // each part: its height, and how to draw it from a given top
    class Block(val h: Float, val draw: (Float) -> Unit)
    val blocks = mutableListOf<Block>()
    val pens = m.status.o("reason")?.optJSONArray("penalties")?.takeIf { it.length() == 2 }
    val state = when {
        m.finished -> L("پایان بازی")
        m.live -> L("زنده | {0}", fa(m.minute))
        else -> L("پیش از بازی")
    }
    val hs = m.home.i("score") ?: 0; val aws = m.away.i("score") ?: 0
    fun crest(bm: Bitmap?, x: Float, cy: Float, r: Float, col: Int) {
        // the crest on a glass disc ringed in the team's colour, with a glow of that colour behind
        p.shader = RadialGradient(x, cy, r * 1.9f, intArrayOf(brighten(col) and 0x00FFFFFF or 0x99000000.toInt(), brighten(col) and 0x00FFFFFF), null, Shader.TileMode.CLAMP)
        c.drawCircle(x, cy, r * 1.9f, p); p.shader = null
        p.color = look.panel; p.setShadowLayer(30f, 0f, 14f, 0x66000000); c.drawCircle(x, cy, r * 1.22f, p); p.clearShadowLayer()
        p.style = Paint.Style.STROKE; p.strokeWidth = 6f * k
        p.shader = LinearGradient(x, cy - r, x, cy + r, brighten(col), brighten(col) and 0x00FFFFFF or 0x55000000, Shader.TileMode.CLAMP)
        c.drawCircle(x, cy, r * 1.22f, p); p.shader = null; p.style = Paint.Style.FILL
        bm?.let { c.drawBitmap(it, null, RectF(x - r * 0.86f, cy - r * 0.86f, x + r * 0.86f, cy + r * 0.86f), null) }
    }
    // the score: each side's number on its own side, the loser's a little dimmer
    fun numbers(cy: Float, size: Float, gapX: Float) {
        if (!m.started) { bigText(c, fa(timeOf(m.time)), W / 2f, cy - size * 0.36f, size * 0.62f, look.ink, a.black, 1f); return }
        bigText(c, fa(hs), homeX.let { if (en) W / 2f - gapX else W / 2f + gapX }, cy - size * 0.6f, size, look.ink, a.black, if (hs < aws && m.finished) 0.5f else 1f)
        bigText(c, fa(aws), if (en) W / 2f + gapX else W / 2f - gapX, cy - size * 0.6f, size, look.ink, a.black, if (aws < hs && m.finished) 0.5f else 1f)
        p.color = look.sub; c.drawRoundRect(RectF(W / 2f - size * 0.12f, cy - size * 0.04f, W / 2f + size * 0.12f, cy + size * 0.04f), 8f, 8f, p)
    }
    val stateInk = if (m.live) 0xFFFFFFFF.toInt() else look.ink
    val stateBg = if (m.live) C.live.toArgb() else look.panel
    if (sq) {
        val cr = 80f; val nm = 34f; val size = 150f
        blocks += Block(2 * cr * 1.22f + 20 + nm * 2.5f) { t ->
            val cy = t + cr * 1.22f
            crest(a.home, if (en) 175f else W - 175f, cy, cr, hc); crest(a.away, if (en) W - 175f else 175f, cy, cr, ac)
            numbers(cy, size, 110f)
            pill(c, state + (pens?.let { "  " + L("پنالتی ") + fa("${it.optInt(if (en) 0 else 1)} - ${it.optInt(if (en) 1 else 0)}") } ?: ""), W / 2f, cy + size * 0.5f, 22f, stateInk, stateBg, a.black, 420, look.stroke)
            text(c, teamName(m.homeId, m.homeName), if (en) 175f else W - 175f, t + 2 * cr * 1.22f + 22, nm, look.ink, a.black, 330, 2)
            text(c, teamName(m.awayId, m.awayName), if (en) W - 175f else 175f, t + 2 * cr * 1.22f + 22, nm, look.ink, a.black, 330, 2)
        }
    } else {
        val cr = if (story) 100f else 84f; val nm = if (story) 46f else 38f; val size = if (story) 220f else 180f
        blocks += Block(2 * cr * 1.22f + 24 + nm * 2.5f + size * 1.05f + 60f * k) { t ->
            val cy = t + cr * 1.22f
            crest(a.home, homeX, cy, cr, hc); crest(a.away, awayX, cy, cr, ac)
            text(c, "VS", W / 2f, cy - 30f * k, 44f * k, look.sub, Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC), 200)
            val nt = t + 2 * cr * 1.22f + 24
            text(c, teamName(m.homeId, m.homeName), homeX, nt, nm, look.ink, a.black, (W * 0.44f).toInt(), 2)
            text(c, teamName(m.awayId, m.awayName), awayX, nt, nm, look.ink, a.black, (W * 0.44f).toInt(), 2)
            val sy = nt + nm * 2.5f + size * 0.55f
            numbers(sy, size, size * 0.62f)
            pill(c, state, W / 2f, sy + size * 0.52f, 28f * k, stateInk, stateBg, a.black, 420, look.stroke)
            pens?.let { pk -> text(c, L("پنالتی ") + fa("${pk.optInt(if (en) 0 else 1)} - ${pk.optInt(if (en) 1 else 0)}"), W / 2f, sy + size * 0.52f + 70f * k, 30f * k, look.accent, a.black, 600) }
        }
    }
    if (o.caption.isNotBlank()) {
        val sz = 56f * k
        blocks += Block(sz * 1.6f) { t ->
            // the user's words between two short rules in the accent colour
            val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = sz; typeface = a.black }
            val w = min(W - 260f, tp.measureText(o.caption.trim()))
            p.color = look.accent
            c.drawRoundRect(RectF(W / 2f - w / 2 - 90, t + sz * 0.62f, W / 2f - w / 2 - 30, t + sz * 0.62f + 6), 3f, 3f, p)
            c.drawRoundRect(RectF(W / 2f + w / 2 + 30, t + sz * 0.62f, W / 2f + w / 2 + 90, t + sz * 0.62f + 6), 3f, 3f, p)
            text(c, o.caption.trim(), W / 2f, t, sz, look.accent, a.black, W - 240, 1)
        }
    }
    val goals = m.events.filter { it.s("type") == "Goal" && !it.b("isPenaltyShootoutEvent") }
    if (o.scorers && goals.isNotEmpty()) {
        fun lines(home: Boolean): CharSequence {
            val sb = android.text.SpannableStringBuilder()
            for (g in goals.filter { it.b("isHome") == home }.take(if (story) 5 else 4)) {
                val n = g.o("player").s("lastName") ?: g.s("nameStr")?.substringAfterLast(' ') ?: g.o("player").s("name") ?: ""
                if (sb.isNotEmpty()) sb.append("\n")
                sb.append(if (en) n else FaNames.of(n))
                val tail = " " + fa(minuteText(g) + "'") + if (g.b("ownGoal")) L(" (به خودی)") else if (isPen(g)) L(" (پ)") else ""
                val s0 = sb.length; sb.append(tail)
                sb.setSpan(android.text.style.ForegroundColorSpan(look.accent), s0, sb.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            return sb
        }
        val rows = maxOf(goals.count { it.b("isHome") }.coerceAtMost(if (story) 5 else 4), goals.count { !it.b("isHome") }.coerceAtMost(if (story) 5 else 4))
        val sz = 33f * k; val lh = sz * 1.5f; val h = rows * lh + 56f * k
        blocks += Block(h) { t ->
            glass(c, 56f, t, W - 56f, t + h, look)
            ball(c, W / 2f, t + h / 2, 22f * k, look)
            p.color = look.stroke; c.drawRect(W / 2f - 1, t + 24, W / 2f + 1, t + h / 2 - 34 * k, p); c.drawRect(W / 2f - 1, t + h / 2 + 34 * k, W / 2f + 1, t + h - 24, p)
            spanText(c, lines(true), homeX + (if (en) -10f else 10f), t + 28f * k, sz, look.ink, a.bold, (W * 0.4f).toInt())
            spanText(c, lines(false), awayX + (if (en) 10f else -10f), t + 28f * k, sz, look.ink, a.bold, (W * 0.4f).toInt())
        }
    }
    if (o.best && best != null) {
        val h = 200f * k
        blocks += Block(h) { t ->
            glass(c, 56f, t, W - 56f, t + h, look)
            // the face in a ring of the accent colour on the reading-start side, the rating on the other
            val fr = 70f * k; val fx = if (en) 56f + 40 + fr else W - 56f - 40 - fr; val fy = t + h / 2
            p.color = look.accent; c.drawCircle(fx, fy, fr + 6, p)
            p.color = 0xFF1B2028.toInt(); c.drawCircle(fx, fy, fr, p)
            a.best?.let { f ->
                c.save(); c.clipPath(Path().apply { addCircle(fx, fy, fr, Path.Direction.CW) })
                c.drawBitmap(f, null, RectF(fx - fr, fy - fr + 6, fx + fr, fy + fr + 6), null); c.restore()
            }
            text(c, "★ " + L("بهترین بازیکن گل‌لایو"), W / 2f, t + h / 2 - 50f * k, 27f * k, look.accent, a.bold, 520)
            text(c, personName(best.name), W / 2f, t + h / 2 - 8f * k, 46f * k, look.ink, a.black, 520)
            val rx = if (en) W - 56f - 40 - 70f * k else 56f + 40 + 70f * k
            p.color = ratingColor(best.value); p.setShadowLayer(20f, 0f, 8f, ratingColor(best.value) and 0x00FFFFFF or 0x88000000.toInt())
            c.drawRoundRect(RectF(rx - 72 * k, fy - 42 * k, rx + 72 * k, fy + 42 * k), 28f * k, 28f * k, p); p.clearShadowLayer()
            text(c, fa(dec(best.value, 1)), rx, fy - 30f * k, 50f * k, 0xFFFFFFFF.toInt(), a.black, 160)
        }
    }
    if (o.stats && m.started) {
        val all = m.content.path("stats.Periods.All").a("stats").objs().firstOrNull()?.a("stats").objs()
        val rows = listOf("BallPossesion" to L("مالکیت توپ"), "expected_goals" to L("گل مورد انتظار (xG)"), "total_shots" to L("شوت"), "ShotsOnTarget" to L("شوت در چارچوب"))
            .mapNotNull { (key, label) -> all.firstOrNull { it.s("key") == key }?.let { label to it } }.take(3)
        val rh = 84f * k
        if (rows.isNotEmpty()) blocks += Block(rows.size * rh + 40f * k) { t ->
            glass(c, 56f, t, W - 56f, t + rows.size * rh + 40f * k, look)
            var ry = t + 24f * k
            for ((label, st) in rows) {
                val v = st.a("stats")
                val hv = v?.optString(0) ?: ""; val av = v?.optString(1) ?: ""
                val hn = hv.substringBefore(' ').replace("%", "").toDoubleOrNull() ?: 0.0; val an = av.substringBefore(' ').replace("%", "").toDoubleOrNull() ?: 0.0
                text(c, fa(hv), homeX.let { if (en) 150f else W - 150f }, ry, 38f * k, if (hn >= an) look.ink else look.sub, a.black, 220)
                text(c, label, W / 2f, ry + 6 * k, 28f * k, look.sub, a.bold, 520)
                text(c, fa(av), if (en) W - 150f else 150f, ry, 38f * k, if (an >= hn) look.ink else look.sub, a.black, 220)
                // two bars from the middle outwards, each as long as that side's share
                val tot = (hn + an).takeIf { it > 0 } ?: 1.0
                val half = (W - 240f) / 2; val by = ry + rh - 34f * k; val bh = 12f * k
                p.color = look.stroke
                c.drawRoundRect(RectF(120f, by, W - 120f, by + bh), bh / 2, bh / 2, p)
                val hw = (half * hn / tot * 2).toFloat().coerceAtMost(half); val aw = (half * an / tot * 2).toFloat().coerceAtMost(half)
                val hSide = if (en) -1 else 1
                p.color = brighten(hc); if (hSide > 0) c.drawRoundRect(RectF(W / 2f + 4, by, W / 2f + 4 + hw, by + bh), bh / 2, bh / 2, p) else c.drawRoundRect(RectF(W / 2f - 4 - hw, by, W / 2f - 4, by + bh), bh / 2, bh / 2, p)
                p.color = brighten(ac); if (hSide > 0) c.drawRoundRect(RectF(W / 2f - 4 - aw, by, W / 2f - 4, by + bh), bh / 2, bh / 2, p) else c.drawRoundRect(RectF(W / 2f + 4, by, W / 2f + 4 + aw, by + bh), bh / 2, bh / 2, p)
                ry += rh
            }
        }
    }
    val mom = if (o.momentum && m.started) momentumOf(m) else emptyList()
    if (mom.size > 5) {
        val h = 220f * k
        blocks += Block(h) { t ->
            glass(c, 56f, t, W - 56f, t + h, look)
            text(c, L("مومنتوم بازی"), W / 2f, t + 18f * k, 27f * k, look.sub, a.bold, 600)
            val mid = t + h * 0.6f; val left = 100f; val width = W - 200f; val amp = h * 0.3f
            val maxMin = maxOf(90.0, mom.maxOf { it.d("minute") ?: 0.0 }).toFloat()
            fun xOf(mn: Float) = if (en) left + mn / maxMin * width else left + width - mn / maxMin * width
            // a smooth curve, filled above the line for the home side and below it for the away side
            val curve = Path().apply {
                moveTo(xOf(0f), mid)
                var px = xOf(0f); var py = mid
                for (pt in mom) {
                    val x = xOf((pt.d("minute") ?: 0.0).toFloat()); val yv = mid - (pt.d("value") ?: 0.0).toFloat() / 100f * amp
                    quadTo(px, py, (px + x) / 2, (py + yv) / 2); px = x; py = yv
                }
                lineTo(px, mid); close()
            }
            c.save(); c.clipRect(0f, t, W.toFloat(), mid)
            p.shader = LinearGradient(0f, mid - amp, 0f, mid, brighten(hc), brighten(hc) and 0x00FFFFFF or 0x33000000, Shader.TileMode.CLAMP); c.drawPath(curve, p); c.restore()
            c.save(); c.clipRect(0f, mid, W.toFloat(), t + h)
            p.shader = LinearGradient(0f, mid, 0f, mid + amp, brighten(ac) and 0x00FFFFFF or 0x33000000, brighten(ac), Shader.TileMode.CLAMP); c.drawPath(curve, p); c.restore()
            p.shader = null
            p.color = look.sub; p.alpha = 110; c.drawRect(left, mid - 1.5f, left + width, mid + 1.5f, p); p.alpha = 255
            // half time and the goals on the line
            p.color = look.stroke; c.drawRect(xOf(45f) - 1.5f, mid - amp, xOf(45f) + 1.5f, mid + amp, p)
            for (g in goals) {
                val mn = (minuteText(g).substringBefore('+').filter(Char::isDigit).toFloatOrNull() ?: continue).coerceAtMost(maxMin)
                ball(c, xOf(mn), if (g.b("isHome")) mid - amp - 6 else mid + amp + 6, 13f * k, look)
            }
        }
    }
    if (o.prediction) Predictions.get(m.matchId)?.let { pk ->
        val line = L("پیش‌بینی من: {0}", fa(if (en) "${pk.home} - ${pk.away}" else "${pk.away} - ${pk.home}")) + when (pk.points) { 3 -> L("  🎯 +۳"); 1 -> L("  ✓ +۱"); 0 -> "  ✗"; else -> "" }
        blocks += Block(36f * k * 2.1f) { t -> pill(c, line, W / 2f, t, 34f * k, look.accent, look.panel, a.black, 860, look.stroke) }
    }

    // keep the parts that fit, in order, and spread them evenly between the header and the footer
    val avail = H - footer - top
    val minGap = if (story) 40f else 22f
    val shown = mutableListOf<Block>(); var used = 0f
    for (b in blocks) { val need = b.h + if (shown.isEmpty()) 0f else minGap; if (used + need <= avail) { used += need; shown += b } }
    val gap = if (shown.size > 1) min(minGap * 2.8f, minGap + (avail - used) / (shown.size + 1)) else 0f
    val total = shown.sumOf { it.h.toDouble() }.toFloat() + gap * (shown.size - 1).coerceAtLeast(0)
    y = top + (avail - total) / 2
    for (b in shown) { b.draw(y); y += b.h + gap }

    // the sticker, big in a top corner, a little tilted
    if (o.sticker.isNotEmpty()) {
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 170f * k }
        val sx2 = if (en) W - 120f * k else 120f * k; val sy2 = if (story) 90f else 40f
        c.save(); c.rotate(if (en) 14f else -14f, sx2, sy2 + 80 * k); c.drawText(o.sticker, sx2 - tp.measureText(o.sticker) / 2, sy2 + tp.textSize * 0.85f, tp); c.restore()
    }
    // the brand: the app's mark and name side by side
    val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 44f * k; typeface = a.black }
    val name = L("گل‌لایو"); val nw = tp.measureText(name); val lr2 = 30f * k
    val bw = nw + 2 * lr2 + 20
    val by = H - footer / 2 - lr2 - (if (story) 10f else 0f)
    val lx = if (en) W / 2f - bw / 2 + lr2 else W / 2f + bw / 2 - lr2
    a.logo?.let { logo ->
        p.color = 0xFFFFFFFF.toInt(); c.drawCircle(lx, by + lr2, lr2 + 4, p)
        c.drawBitmap(logo, null, RectF(lx - lr2, by, lx + lr2, by + 2 * lr2), p)
    }
    text(c, name, if (en) W / 2f - bw / 2 + 2 * lr2 + 20 + nw / 2 else W / 2f - bw / 2 + nw / 2, by + lr2 - 44f * k * 0.62f, 44f * k, look.ink, a.black, (nw + 20).toInt())
    return bmp
}

/** A little ball: a white disc with a dark pentagon pattern. */
private fun ball(c: Canvas, x: Float, y: Float, r: Float, look: CardLook) {
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.color = 0xFFFFFFFF.toInt(); p.setShadowLayer(r * 0.6f, 0f, r * 0.2f, 0x66000000); c.drawCircle(x, y, r, p); p.clearShadowLayer()
    p.color = 0xFF15171C.toInt()
    fun pent(cx: Float, cy: Float, rr: Float) {
        val path = Path()
        for (i in 0 until 5) { val an = Math.toRadians(-90.0 + i * 72); val px = cx + rr * cos(an).toFloat(); val py = cy + rr * sin(an).toFloat(); if (i == 0) path.moveTo(px, py) else path.lineTo(px, py) }
        path.close(); c.drawPath(path, p)
    }
    pent(x, y, r * 0.38f)
    c.save(); c.clipPath(Path().apply { addCircle(x, y, r, Path.Direction.CW) })
    for (i in 0 until 5) { val an = Math.toRadians(-90.0 + i * 72); pent(x + r * 0.95f * cos(an).toFloat(), y + r * 0.95f * sin(an).toFloat(), r * 0.32f) }
    c.restore()
    p.style = Paint.Style.STROKE; p.strokeWidth = r * 0.08f; p.color = look.stroke or 0x22000000; c.drawCircle(x, y, r, p)
}

/** Fine film grain, so flat colours don't look flat. */
private fun grain(c: Canvas, W: Int, H: Int, light: Boolean) {
    val p = Paint()
    val rnd = java.util.Random(11)
    repeat(W * H / 120) {
        p.color = if (rnd.nextBoolean()) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
        p.alpha = (if (light) 4 else 6) + rnd.nextInt(8)
        val x = rnd.nextFloat() * W; val y = rnd.nextFloat() * H
        c.drawRect(x, y, x + 2.5f, y + 2.5f, p)
    }
}

/** A frosted panel: the theme's glass colour, a light sheen at the top and a hairline edge. */
private fun glass(c: Canvas, l: Float, t: Float, r: Float, b: Float, look: CardLook) {
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    val rect = RectF(l, t, r, b); val rad = 40f
    p.color = look.panel; p.setShadowLayer(40f, 0f, 16f, 0x33000000); c.drawRoundRect(rect, rad, rad, p); p.clearShadowLayer()
    p.shader = LinearGradient(0f, t, 0f, t + (b - t) * 0.5f, 0x1AFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP); c.drawRoundRect(rect, rad, rad, p); p.shader = null
    p.style = Paint.Style.STROKE; p.strokeWidth = 2.5f; p.color = look.stroke; c.drawRoundRect(rect, rad, rad, p)
}

/** A big number: a soft shadow under it and a top-to-bottom sheen in it; [fade] dims it. */
private fun bigText(c: Canvas, s: String, cx: Float, top: Float, size: Float, ink: Int, tf: Typeface, fade: Float) {
    val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; typeface = tf }
    val w = tp.measureText(s); val x = cx - w / 2
    val base = top + size * 0.95f
    tp.color = 0x66000000; tp.maskFilter = BlurMaskFilter(size * 0.08f, BlurMaskFilter.Blur.NORMAL)
    c.drawText(s, x, base + size * 0.05f, tp); tp.maskFilter = null
    tp.shader = LinearGradient(0f, top + size * 0.2f, 0f, base, ink, androidx.core.graphics.ColorUtils.blendARGB(ink, 0xFF888888.toInt(), 0.35f), Shader.TileMode.CLAMP)
    tp.alpha = (255 * fade).toInt()
    c.drawText(s, x, base, tp)
}

/** Centred styled text (spans keep their colours), wrapped to [width]. */
private fun spanText(c: Canvas, s: CharSequence, cx: Float, top: Float, size: Float, color: Int, tf: Typeface, width: Int) {
    if (s.isEmpty()) return
    val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; textSize = size; typeface = tf }
    val l = StaticLayout.Builder.obtain(s, 0, s.length, tp, width).setAlignment(Layout.Alignment.ALIGN_CENTER).setLineSpacing(size * 1.5f - tp.fontSpacing, 1f)
        .setIncludePad(false).build()
    c.save(); c.translate(cx - width / 2f, top); l.draw(c); c.restore()
}

/** A team colour lifted a little so thin bars stay visible on dark backgrounds. */
private fun brighten(c: Int): Int {
    val hsv = FloatArray(3); android.graphics.Color.colorToHSV(c or 0xFF000000.toInt(), hsv)
    hsv[2] = maxOf(hsv[2], 0.55f)
    return android.graphics.Color.HSVToColor(hsv)
}

/** The crest's main colour: the most common strong hue among its pixels, or null for a plain black-and-white crest. */
private fun crestColor(b: Bitmap?): Int? {
    b ?: return null
    val s = Bitmap.createScaledBitmap(b, 40, 40, true)
    val weight = FloatArray(24); val sum = Array(24) { FloatArray(3) }
    val hsv = FloatArray(3)
    for (x in 0 until 40) for (y in 0 until 40) {
        val px = s.getPixel(x, y)
        if (android.graphics.Color.alpha(px) < 200) continue
        android.graphics.Color.colorToHSV(px, hsv)
        if (hsv[1] < 0.35f || hsv[2] < 0.25f) continue
        val k = (hsv[0] / 15f).toInt().coerceIn(0, 23)
        weight[k] += hsv[1]; sum[k][0] += android.graphics.Color.red(px).toFloat(); sum[k][1] += android.graphics.Color.green(px).toFloat(); sum[k][2] += android.graphics.Color.blue(px).toFloat()
    }
    val k = weight.indices.maxByOrNull { weight[it] } ?: return null
    if (weight[k] < 40f) return null
    var n = 0
    for (x in 0 until 40) for (y in 0 until 40) { android.graphics.Color.colorToHSV(s.getPixel(x, y), hsv); if (android.graphics.Color.alpha(s.getPixel(x, y)) >= 200 && hsv[1] >= 0.35f && hsv[2] >= 0.25f && (hsv[0] / 15f).toInt().coerceIn(0, 23) == k) n++ }
    return android.graphics.Color.rgb((sum[k][0] / n).toInt(), (sum[k][1] / n).toInt(), (sum[k][2] / n).toInt())
}

/** The team's colour for the card: FotMob's when it has one, else the crest's, darkened so white writing reads on it. */
private fun cardColor(c: Color, crest: Bitmap?, fallback: Long): Int {
    val hsv = FloatArray(3); android.graphics.Color.colorToHSV(c.toArgb(), hsv)
    val picked = if (hsv[1] >= 0.2f) c else crestColor(crest)?.let { Color(it) } ?: Color(fallback)
    return deep(picked)
}

private fun ratingColor(r: Double) = when { r >= 9 -> 0xFF1D9BF0; r >= 7.5 -> 0xFF14B866; r >= 6.5 -> 0xFF9FB21B; r >= 5.5 -> 0xFFF08A24; else -> 0xFFE5434B }.toInt()

/** Paints the chosen design and returns its colours. */
private fun background(c: Canvas, theme: String, W: Int, H: Int, hc: Int, ac: Int): CardLook {
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    val w = W.toFloat(); val h = H.toFloat()
    fun radial(x: Float, y: Float, r: Float, color: Int, alpha: Int) {
        p.shader = RadialGradient(x, y, r, intArrayOf((color and 0x00FFFFFF) or (alpha shl 24), color and 0x00FFFFFF), null, Shader.TileMode.CLAMP)
        c.drawCircle(x, y, r, p); p.shader = null
    }
    return when (theme) {
        "night" -> {
            p.shader = LinearGradient(0f, 0f, 0f, h, intArrayOf(0xFF141B3D.toInt(), 0xFF070912.toInt(), 0xFF040509.toInt()), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w, h, p); p.shader = null
            radial(w * 0.1f, h * 0.18f, w * 0.75f, hc, 0x88); radial(w * 0.9f, h * 0.55f, w * 0.75f, ac, 0x70)
            // a few stars
            p.color = 0xFFFFFFFF.toInt()
            val rnd = java.util.Random(7)
            repeat(70) { p.alpha = 40 + rnd.nextInt(120); c.drawCircle(rnd.nextFloat() * w, rnd.nextFloat() * h * 0.7f, 1.5f + rnd.nextFloat() * 2.5f, p) }
            p.alpha = 255
            CardLook(0xFFFFFFFF.toInt(), 0xCCB9C3E6.toInt(), 0x22FFFFFF, 0xFF7CF5FF.toInt(), 0x33FFFFFF)
        }
        "gold" -> {
            p.shader = LinearGradient(0f, 0f, 0f, h, intArrayOf(0xFF2E2206.toInt(), 0xFF120D04.toInt(), 0xFF070502.toInt()), null, Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w, h, p); p.shader = null
            // light rays from the top
            p.color = 0xFFF5C542.toInt()
            for (k in 0 until 18) {
                val a0 = Math.toRadians(20.0 + k * 140.0 / 17); val spread = Math.toRadians(2.2); val len = h * 1.4f
                val path = Path().apply {
                    moveTo(w / 2, -40f)
                    lineTo(w / 2 + len * cos(a0 - spread).toFloat(), -40f + len * sin(a0 - spread).toFloat())
                    lineTo(w / 2 + len * cos(a0 + spread).toFloat(), -40f + len * sin(a0 + spread).toFloat())
                    close()
                }
                p.alpha = if (k % 2 == 0) 22 else 12; c.drawPath(path, p)
            }
            p.alpha = 255
            radial(w / 2, h * 0.3f, w * 0.7f, 0xFFF5C542.toInt(), 0x55)
            CardLook(0xFFFFF4D6.toInt(), 0xCCE8D9A8.toInt(), 0x26F5C542, 0xFFF5C542.toInt(), 0x55F5C542)
        }
        "light" -> {
            p.color = 0xFFF7F4EE.toInt(); c.drawRect(0f, 0f, w, h, p)
            // a band in both colours across the top
            p.shader = LinearGradient(0f, 0f, w, 0f, intArrayOf(hc, hc, ac, ac), floatArrayOf(0f, 0.48f, 0.52f, 1f), Shader.TileMode.CLAMP)
            val band = Path().apply { moveTo(0f, 0f); lineTo(w, 0f); lineTo(w, h * 0.16f); lineTo(0f, h * 0.24f); close() }
            c.drawPath(band, p); p.shader = null
            radial(w / 2, h * 0.42f, w * 0.6f, 0xFFFFFFFF.toInt(), 0xFF)
            CardLook(0xFF15171C.toInt(), 0xFF5D6470.toInt(), 0x14000000, hc or 0xFF000000.toInt(), 0x22000000)
        }
        "pitch" -> {
            val n = 12
            for (k in 0 until n) { p.color = if (k % 2 == 0) 0xFF1D6E3F.toInt() else 0xFF186236.toInt(); c.drawRect(0f, h * k / n, w, h * (k + 1) / n + 1, p) }
            p.style = Paint.Style.STROKE; p.strokeWidth = 5f; p.color = 0x55FFFFFF
            c.drawRect(40f, 40f, w - 40f, h - 40f, p); c.drawLine(40f, h / 2, w - 40f, h / 2, p); c.drawCircle(w / 2, h / 2, w * 0.17f, p)
            c.drawRect(w * 0.2f, 40f, w * 0.8f, h * 0.14f, p); c.drawRect(w * 0.2f, h * 0.86f, w * 0.8f, h - 40f, p)
            p.style = Paint.Style.FILL
            p.shader = RadialGradient(w / 2, h / 2, h * 0.75f, intArrayOf(0x00000000, 0xAA000000.toInt()), null, Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w, h, p); p.shader = null
            CardLook(0xFFFFFFFF.toInt(), 0xDDDFF5E6.toInt(), 0x44000000, 0xFFFFE14D.toInt(), 0x33FFFFFF)
        }
        "neon" -> {
            p.color = 0xFF05050A.toInt(); c.drawRect(0f, 0f, w, h, p)
            // glowing rings in both colours
            p.style = Paint.Style.STROKE
            for ((col, x, yy) in listOf(Triple(hc, w * 0.15f, h * 0.2f), Triple(ac, w * 0.85f, h * 0.75f))) {
                for (k in 0 until 4) {
                    p.color = col or 0xFF000000.toInt(); p.strokeWidth = 6f; p.alpha = 200 - k * 40
                    p.maskFilter = BlurMaskFilter(18f, BlurMaskFilter.Blur.NORMAL)
                    c.drawCircle(x, yy, w * (0.25f + k * 0.12f), p)
                }
            }
            p.maskFilter = null; p.style = Paint.Style.FILL; p.alpha = 255
            radial(w / 2, h * 0.3f, w * 0.5f, 0xFFFFFFFF.toInt(), 0x22)
            CardLook(0xFFFFFFFF.toInt(), 0xCCC8C8E0.toInt(), 0x1FFFFFFF, 0xFF39FFB4.toInt(), 0x55FFFFFF)
        }
        else -> {
            // the two club colours split on a slant, darkened towards the bottom, with a light behind the score and fine stripes
            p.color = 0xFF0B0F14.toInt(); c.drawRect(0f, 0f, w, h, p)
            for ((col, right) in listOf(hc to !Lang.en, ac to Lang.en)) {
                val path = Path().apply {
                    if (right) { moveTo(w * 0.6f, 0f); lineTo(w, 0f); lineTo(w, h); lineTo(w * 0.4f, h) }
                    else { moveTo(0f, 0f); lineTo(w * 0.6f, 0f); lineTo(w * 0.4f, h); lineTo(0f, h) }
                    close()
                }
                p.shader = LinearGradient(0f, 0f, 0f, h, intArrayOf(col or 0xFF000000.toInt(), col or 0xFF000000.toInt(), 0xFF0B0F14.toInt()), floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)
                c.drawPath(path, p); p.shader = null
            }
            p.color = 0x0FFFFFFF; p.strokeWidth = 3f
            var x = -h; while (x < w) { c.drawLine(x, h, x + h * 0.6f, 0f, p); x += 46f }
            radial(w / 2, h * 0.3f, w * 0.55f, 0xFFFFFFFF.toInt(), 0x40)
            p.shader = LinearGradient(0f, h * 0.5f, 0f, h, 0x00000000, 0xD9000000.toInt(), Shader.TileMode.CLAMP)
            c.drawRect(0f, h * 0.5f, w, h, p); p.shader = null
            CardLook(0xFFFFFFFF.toInt(), 0xCCFFFFFF.toInt(), 0x40000000, 0xFFF5C542.toInt(), 0x33FFFFFF)
        }
    }
}

/** Text in a rounded pill with a hairline edge, centred on [cx]. */
private fun pill(c: Canvas, s: String, cx: Float, top: Float, size: Float, ink: Int, bg: Int, tf: Typeface, maxW: Int, stroke: Int = 0) {
    val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; typeface = tf }
    val w = min(maxW.toFloat(), tp.measureText(s) + size * 1.8f)
    val r = RectF(cx - w / 2, top, cx + w / 2, top + size * 2f)
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bg }
    c.drawRoundRect(r, size, size, p)
    if (stroke != 0) { p.style = Paint.Style.STROKE; p.strokeWidth = 2f; p.color = stroke; c.drawRoundRect(r, size, size, p) }
    text(c, s, cx, top + size * 0.42f, size, ink, tf, (w - size).toInt())
}

/** Centered text from [top], wrapped to [width] (Persian is shaped and laid out right to left by the layout itself). */
private fun text(c: Canvas, s: String, cx: Float, top: Float, size: Float, color: Int, tf: Typeface, width: Int, maxLines: Int = 1) {
    if (s.isBlank()) return
    val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; textSize = size; typeface = tf }
    val l = StaticLayout.Builder.obtain(s, 0, s.length, tp, width).setAlignment(Layout.Alignment.ALIGN_CENTER)
        .setMaxLines(maxLines).setEllipsize(TextUtils.TruncateAt.END).setIncludePad(false).build()
    c.save(); c.translate(cx - width / 2f, top); l.draw(c); c.restore()
}
