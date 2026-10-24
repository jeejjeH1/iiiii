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
class CardAssets(val home: Bitmap?, val away: Bitmap?, val logo: Bitmap?, val best: Bitmap?, val black: Typeface, val bold: Typeface)

private suspend fun image(ctx: Context, url: String, px: Int): Bitmap? = runCatching {
    kotlinx.coroutines.withTimeoutOrNull(6_000) { ctx.imageLoader.execute(ImageRequest.Builder(ctx).data(url).allowHardware(false).build()) }?.drawable?.toBitmap(px, px)
}.getOrNull()

internal suspend fun cardAssets(ctx: Context, m: MatchData, best: PlayerRating?): CardAssets {
    val en = Lang.en
    return CardAssets(
        image(ctx, teamLogo(m.homeId, true), 320), image(ctx, teamLogo(m.awayId, true), 320),
        BitmapFactory.decodeResource(ctx.resources, R.drawable.logo),
        best?.let { image(ctx, playerFace(it.id), 260) },
        if (en) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else ResourcesCompat.getFont(ctx, R.font.vazir_black) ?: Typeface.DEFAULT_BOLD,
        if (en) Typeface.DEFAULT_BOLD else ResourcesCompat.getFont(ctx, R.font.vazir_bold) ?: Typeface.DEFAULT_BOLD,
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
                Box(Modifier.fillMaxWidth(if (o.format == CardFormat.STORY) 0.62f else 0.86f).aspectRatio(ratio).clip(RoundedCornerShape(18.dp))
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
                        Box(Modifier.size(58.dp, 82.dp).clip(RoundedCornerShape(12.dp)).background(themeSwatch(t, m)).border(if (on) 3.dp else 1.dp, if (on) accent else C.line2, RoundedCornerShape(12.dp)))
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
private fun themeSwatch(t: String, m: MatchData): Brush {
    val hc = deep(m.homeColor); val ac = deep(m.awayColor)
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

/** The share picture: header, crests and score, then the chosen parts as long as they fit, and the footer. Drawn at [scale]. */
internal fun drawCard(ctx: Context, m: MatchData, o: CardOptions, a: CardAssets, best: PlayerRating?, scale: Float): Bitmap {
    val W = o.format.w; val H = o.format.h
    val bmp = Bitmap.createBitmap((W * scale).toInt(), (H * scale).toInt(), Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.scale(scale, scale)
    val en = Lang.en
    val hc = deep(m.homeColor); val ac = deep(m.awayColor)
    val look = background(c, o.theme, W, H, hc, ac)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    val story = o.format == CardFormat.STORY
    val sq = o.format == CardFormat.SQUARE

    // header: the app, then the competition and the day
    var y = if (story) 90f else 54f
    a.logo?.let { logo ->
        val r = if (story) 46f else 36f
        p.color = 0xFFFFFFFF.toInt(); c.drawCircle(W / 2f, y + r, r + 6, p)
        c.drawBitmap(logo, null, RectF(W / 2f - r, y, W / 2f + r, y + 2 * r), p)
        y += 2 * r + 14
    }
    val league = leagueName(m.leagueId, m.general.s("leagueName") ?: "")
    val round = m.general.s("leagueRoundName")?.let { if (it.all(Char::isDigit)) L(" · هفته {0}", fa(it)) else "" } ?: ""
    pill(c, league + round + "  ·  " + persianDate(m.time, "d MMMM yyyy"), W / 2f, y, if (story) 34f else 30f, look.ink, look.panel, a.bold, 900)
    y += if (story) 100f else 76f

    // crests, names and the score
    val homeX = if (en) W * 0.24f else W * 0.76f
    val awayX = W - homeX
    val cr = if (story) 135f else if (sq) 100f else 115f
    for ((bm, x, col) in listOf(Triple(a.home, homeX, hc), Triple(a.away, awayX, ac))) {
        // a halo in the team's colour behind each crest
        p.shader = RadialGradient(x, y + cr, cr * 1.5f, intArrayOf(col or 0xFF000000.toInt(), col and 0x00FFFFFF), null, Shader.TileMode.CLAMP)
        p.alpha = 150; c.drawCircle(x, y + cr, cr * 1.5f, p); p.shader = null; p.alpha = 255
        bm?.let { c.drawBitmap(it, null, RectF(x - cr, y, x + cr, y + 2 * cr), null) }
    }
    val nameTop = y + 2 * cr + 18
    text(c, teamName(m.homeId, m.homeName), homeX, nameTop, if (story) 48f else 40f, look.ink, a.black, (W * 0.4f).toInt(), 2)
    text(c, teamName(m.awayId, m.awayName), awayX, nameTop, if (story) 48f else 40f, look.ink, a.black, (W * 0.4f).toInt(), 2)
    // the score between the crests, big, with a soft glow
    val sy = y + cr - (if (story) 120f else 95f)
    val size = if (story) 190f else if (sq) 140f else 160f
    if (m.started) {
        val sc = fa(if (en) "${m.home.i("score") ?: 0} - ${m.away.i("score") ?: 0}" else "${m.away.i("score") ?: 0} - ${m.home.i("score") ?: 0}")
        glowText(c, sc, W / 2f, sy, size, look.ink, look.accent, a.black)
    } else glowText(c, fa(timeOf(m.time)), W / 2f, sy + 20, size * 0.6f, look.ink, look.accent, a.black)
    val pens = m.status.o("reason")?.optJSONArray("penalties")?.takeIf { it.length() == 2 }
    val state = when {
        pens != null -> L("پنالتی ") + fa("${pens.optInt(0)} - ${pens.optInt(1)}")
        m.finished -> L("پایان بازی")
        m.live -> L("زنده · {0}", fa(m.minute))
        else -> L("پیش از بازی")
    }
    pill(c, state, W / 2f, sy + size * 1.08f, if (story) 34f else 28f, if (m.live) 0xFFFFFFFF.toInt() else look.ink,
        if (m.live) C.live.toArgb() else look.panel, a.black, 420)
    y = nameTop + (if (story) 140f else 110f)

    // the chosen parts, top to bottom, each only if it still fits above the footer
    val footer = if (story) 150f else 110f
    val gap = if (story) 34f else 22f
    fun fits(h: Float) = y + h <= H - footer
    if (o.caption.isNotBlank() && fits(110f)) {
        text(c, "« " + o.caption.trim() + " »", W / 2f, y, if (story) 52f else 44f, look.accent, a.black, W - 140, 2)
        y += (if (story) 120f else 100f) + gap
    }
    val goals = m.events.filter { it.s("type") == "Goal" && !it.b("isPenaltyShootoutEvent") }
    if (o.scorers && goals.isNotEmpty()) {
        fun lines(home: Boolean) = goals.filter { it.b("isHome") == home }.take(if (story) 6 else 4).map { g ->
            val n = g.o("player").s("lastName") ?: g.s("nameStr")?.substringAfterLast(' ') ?: g.o("player").s("name") ?: ""
            "⚽ " + (if (en) n else FaNames.of(n)) + " " + fa(minuteText(g) + "'") + if (g.b("ownGoal")) L(" (به خودی)") else if (isPen(g)) L(" (پ)") else ""
        }
        val hl = lines(true); val al = lines(false)
        val rows = maxOf(hl.size, al.size)
        val h = rows * 50f + 40f
        if (fits(h)) {
            panel(c, 60f, y, W - 60f, y + h, look.panel)
            text(c, hl.joinToString("\n"), homeX, y + 22, 34f, look.ink, a.bold, (W * 0.42f).toInt(), rows)
            text(c, al.joinToString("\n"), awayX, y + 22, 34f, look.ink, a.bold, (W * 0.42f).toInt(), rows)
            y += h + gap
        }
    }
    if (o.stats && m.started) {
        val top = m.content.path("stats.Periods.All").a("stats").objs().firstOrNull()?.a("stats").objs().orEmpty()
        val rows = listOf("BallPossesion" to L("مالکیت توپ"), "expected_goals" to L("گل مورد انتظار (xG)"), "total_shots" to L("شوت"), "ShotsOnTarget" to L("شوت در چارچوب"))
            .mapNotNull { (k, label) -> top.firstOrNull { it.s("key") == k }?.let { label to it } }.take(if (story) 4 else 3)
        val h = rows.size * 78f + 40f
        if (rows.isNotEmpty() && fits(h)) {
            panel(c, 60f, y, W - 60f, y + h, look.panel)
            var ry = y + 24
            for ((label, st) in rows) {
                val v = st.a("stats")
                val hv = v?.optString(0) ?: ""; val av = v?.optString(1) ?: ""
                val hn = hv.substringBefore(' ').replace("%", "").toDoubleOrNull() ?: 0.0; val an = av.substringBefore(' ').replace("%", "").toDoubleOrNull() ?: 0.0
                val left = if (en) hv else av; val right = if (en) av else hv
                text(c, fa(left), 150f, ry, 34f, look.ink, a.black, 160)
                text(c, label, W / 2f, ry + 2, 28f, look.sub, a.bold, 520)
                text(c, fa(right), W - 150f, ry, 34f, look.ink, a.black, 160)
                // a bar split in the two colours
                val tot = (hn + an).takeIf { it > 0 } ?: 1.0
                val lw = ((if (en) hn else an) / tot).toFloat()
                val bx = 110f; val bw = W - 220f; val by = ry + 48
                p.color = if (en) hc else ac; c.drawRoundRect(RectF(bx, by, bx + bw * lw - 3, by + 10), 5f, 5f, p)
                p.color = if (en) ac else hc; c.drawRoundRect(RectF(bx + bw * lw + 3, by, bx + bw, by + 10), 5f, 5f, p)
                ry += 78f
            }
            y += h + gap
        }
    }
    if (o.best && best != null && fits(170f)) {
        panel(c, 60f, y, W - 60f, y + 170f, look.panel)
        val fx = if (en) 160f else W - 160f
        p.color = look.accent; c.drawCircle(fx, y + 85, 66f, p)
        a.best?.let { f ->
            c.save(); val clip = Path().apply { addCircle(fx, y + 85, 60f, Path.Direction.CW) }; c.clipPath(clip)
            c.drawBitmap(f, null, RectF(fx - 60, y + 25, fx + 60, y + 145), null); c.restore()
        }
        val tx = if (en) W / 2f + 40 else W / 2f - 40
        text(c, "⭐ " + L("بهترین بازیکن گل‌لایو"), tx, y + 30, 28f, look.accent, a.bold, 560)
        text(c, personName(best.name), tx, y + 74, 44f, look.ink, a.black, 560)
        // the rating badge
        val rx = if (en) W - 150f else 150f
        p.color = ratingColor(best.value); c.drawRoundRect(RectF(rx - 62, y + 50, rx + 62, y + 120), 22f, 22f, p)
        text(c, fa(dec(best.value, 1)), rx, y + 56, 46f, 0xFFFFFFFF.toInt(), a.black, 140)
        y += 170f + gap
    }
    if (o.momentum && m.started) {
        val mom = momentumOf(m)
        val h = 170f
        if (mom.size > 5 && fits(h)) {
            panel(c, 60f, y, W - 60f, y + h, look.panel)
            text(c, L("مومنتوم بازی"), W / 2f, y + 14, 28f, look.sub, a.bold, 600)
            val mid = y + 105; val left = 100f; val width = W - 200f
            val maxMin = maxOf(90.0, mom.maxOf { it.d("minute") ?: 0.0 }).toFloat()
            p.color = look.sub; p.alpha = 90; c.drawRect(left, mid - 1, left + width, mid + 1, p); p.alpha = 255
            for (pt in mom) {
                val v = (pt.d("value") ?: 0.0).toFloat(); val hh = kotlin.math.abs(v) / 100f * 48f
                val x = left + (pt.d("minute") ?: 0.0).toFloat() / maxMin * width
                // time runs from the reading start: right to left in Persian
                val xx = if (en) x else left + width - (x - left)
                p.color = if (v >= 0) hc else ac
                c.drawRect(xx, if (v >= 0) mid - hh else mid, xx + width / maxMin * 0.8f, if (v >= 0) mid else mid + hh, p)
            }
            y += h + gap
        }
    }
    if (o.prediction) Predictions.get(m.matchId)?.let { pk ->
        if (fits(90f)) {
            val line = L("پیش‌بینی من: {0}", fa("${pk.home} - ${pk.away}")) + when (pk.points) { 3 -> L("  🎯 +۳"); 1 -> L("  ✓ +۱"); 0 -> "  ✗"; else -> "" }
            pill(c, line, W / 2f, y, 36f, look.accent, look.panel, a.black, 820)
            y += 90f + gap
        }
    }
    // the sticker, big in a top corner, a little tilted
    if (o.sticker.isNotEmpty()) {
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = if (story) 150f else 110f }
        c.save(); val sx2 = if (en) W - 150f else 150f
        c.rotate(if (en) 12f else -12f, sx2, 150f); c.drawText(o.sticker, sx2 - tp.measureText(o.sticker) / 2, 200f, tp); c.restore()
    }
    // footer
    text(c, L("گل‌لایو · نتایج زنده فوتبال"), W / 2f, H - footer + 30, if (story) 38f else 32f, look.ink, a.black, 980)
    text(c, L("دانلود از کافه‌بازار"), W / 2f, H - footer + (if (story) 82f else 70f), if (story) 28f else 24f, look.sub, a.bold, 980)
    return bmp
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

private fun panel(c: Canvas, l: Float, t: Float, r: Float, b: Float, color: Int) {
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    c.drawRoundRect(RectF(l, t, r, b), 36f, 36f, p)
}

/** Text in a rounded pill, centred on [cx]. */
private fun pill(c: Canvas, s: String, cx: Float, top: Float, size: Float, ink: Int, bg: Int, tf: Typeface, maxW: Int) {
    val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; typeface = tf }
    val w = min(maxW.toFloat(), tp.measureText(s) + size * 1.6f)
    panel(c, cx - w / 2, top, cx + w / 2, top + size * 2f, bg)
    text(c, s, cx, top + size * 0.42f, size, ink, tf, (w - size).toInt())
}

/** Big text with a soft glow of [glow] behind it. */
private fun glowText(c: Canvas, s: String, cx: Float, top: Float, size: Float, ink: Int, glow: Int, tf: Typeface) {
    val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = glow; textSize = size; typeface = tf; maskFilter = BlurMaskFilter(size * 0.18f, BlurMaskFilter.Blur.NORMAL); alpha = 150 }
    val l = StaticLayout.Builder.obtain(s, 0, s.length, tp, 900).setAlignment(Layout.Alignment.ALIGN_CENTER).setMaxLines(1).setIncludePad(false).build()
    c.save(); c.translate(cx - 450f, top); l.draw(c); c.restore()
    text(c, s, cx, top, size, ink, tf, 900)
}

/** Centered text from [top], wrapped to [width] (Persian is shaped and laid out right to left by the layout itself). */
private fun text(c: Canvas, s: String, cx: Float, top: Float, size: Float, color: Int, tf: Typeface, width: Int, maxLines: Int = 1) {
    if (s.isBlank()) return
    val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; textSize = size; typeface = tf }
    val l = StaticLayout.Builder.obtain(s, 0, s.length, tp, width).setAlignment(Layout.Alignment.ALIGN_CENTER)
        .setMaxLines(maxLines).setEllipsize(TextUtils.TruncateAt.END).setIncludePad(false).build()
    c.save(); c.translate(cx - width / 2f, top); l.draw(c); c.restore()
}
