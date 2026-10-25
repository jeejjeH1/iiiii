package app.goallive.live.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.goallive.live.L
import app.goallive.live.data.Store
import app.goallive.live.data.Telemetry
import kotlinx.coroutines.delay

/** Cafe Bazaar's own rating page for the app, or its web page when Bazaar isn't installed. */
fun openBazaarRate(ctx: Context) {
    val pkg = ctx.packageName
    val rate = Intent(Intent.ACTION_EDIT, Uri.parse("bazaar://details?id=$pkg")).setPackage("com.farsitel.bazaar").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (runCatching { ctx.startActivity(rate) }.isSuccess) return
    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BAZAAR_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** The version whose news the user has seen; the welcome marks a fresh install as up to date. */
fun markNewsSeen(ctx: Context) {
    Store.prefs().edit().putInt("newsSeen", appVersion(ctx)).apply()
}

private fun appVersion(ctx: Context): Int = runCatching {
    val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
    if (android.os.Build.VERSION.SDK_INT >= 28) pi.longVersionCode.toInt() else @Suppress("DEPRECATION") pi.versionCode
}.getOrDefault(0)

/** What came with this version: emoji, title, one line, and where «ببین» goes (null: nowhere). */
private class News(val emoji: String, val title: String, val sub: String, val go: ((Nav) -> Unit)?)

private fun news(): List<News> = listOf(
    News("🎬", L("بازی در یک دقیقه"), L("بعد از هر بازی، کل بازی در یک ویدیو: گل‌ها روی زمین، آمار، بهترین بازیکن و صدای ورزشگاه"), null),
    News("🎞️", L("استودیو استوری"), L("عکس و ویدیوی متحرک با آهنگ از بازی، بازیکن، جدول، توپ طلا و مقایسه؛ مستقیم به اینستاگرام و تلگرام"), null),
    News("⭐", L("تیم هفته"), L("بهترین ۱۱ بازیکن هفته از ۵ لیگ معتبر و تیم هفته‌ی هر لیگ"), { it.rankings() }),
    News("🏟️", L("ترکیب تازه"), L("زمین و ترکیب شیک‌تر، با ساخت عکس ترکیب برای استوری"), null),
    News("🧹", L("صفحه‌ی اول خلوت‌تر"), L("روزها و فیلترها موقع پایین رفتن همیشه بالای صفحه می‌مانند"), null),
    News("🎯", L("نمره‌دهی دقیق‌تر"), L("نمره‌ی گل‌لایو سخت‌گیرتر و منصفانه‌تر شد"), null),
)

/** After an update: what is new, once per version (never on a fresh install, which saw the welcome). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsNew(nav: Nav) {
    val ctx = LocalContext.current
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val p = Store.prefs()
        val v = appVersion(ctx)
        val seen = p.getInt("newsSeen", 0)
        if (v == 0 || seen >= v) return@LaunchedEffect
        delay(1500)
        show = true
    }
    if (!show) return
    val close = { show = false; markNewsSeen(ctx) }
    val accent = LocalAccent.current
    ModalBottomSheet(onDismissRequest = close, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = C.cardSolid,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
            T(L("✨ چه خبر تازه؟"), 22, FontWeight.Black)
            T(L("گل‌لایو به‌روز شد؛ این‌ها تازه‌اند:"), 13, color = C.mute, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp))
            news().forEach { n ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(18.dp)).background(C.card2).border(1.dp, C.line, RoundedCornerShape(18.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    T(n.emoji, 26)
                    Column(Modifier.weight(1f)) { T(n.title, 15, FontWeight.ExtraBold); T(n.sub, 12, color = C.mute) }
                    n.go?.let { go ->
                        T(L("ببین"), 13, FontWeight.ExtraBold, accent, Modifier.clip(RoundedCornerShape(10.dp)).clickable { close(); go(nav) }.padding(horizontal = 8.dp, vertical = 6.dp))
                    }
                }
            }
            Box(Modifier.padding(top = 12.dp).fillMaxWidth().height(52.dp).clip(RoundedCornerShape(16.dp)).background(accent).clickable { close() }, contentAlignment = Alignment.Center) {
                T(L("عالیه!"), 16, FontWeight.Black, LocalAccentInk.current)
            }
        }
    }
}

/**
 * Asks for a rating on Cafe Bazaar, gently: only after the app has been used on several days over at least three, never in the
 * first seconds of a visit, at most three times a month apart, and never again after «امتیاز می‌دم» or «نه، ممنون».
 */
@Composable
fun RateAsk() {
    val ctx = LocalContext.current
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val p = Store.prefs()
        val now = System.currentTimeMillis()
        val first = p.getLong("firstOpen", 0L).takeIf { it > 0 } ?: now.also { p.edit().putLong("firstOpen", it).apply() }
        // count days with a visit, not visits
        val today = now / 86_400_000L
        if (p.getLong("lastOpenDay", 0L) != today) p.edit().putLong("lastOpenDay", today).putInt("openDays", p.getInt("openDays", 0) + 1).apply()
        if (p.getString("rate", "") in setOf("done", "never")) return@LaunchedEffect
        if (p.getInt("openDays", 0) < 4 || now - first < 3 * 86_400_000L) return@LaunchedEffect
        if (p.getInt("rateAsks", 0) >= 3 || now - p.getLong("rateAt", 0L) < 30 * 86_400_000L) return@LaunchedEffect
        delay(40_000)
        p.edit().putInt("rateAsks", p.getInt("rateAsks", 0) + 1).putLong("rateAt", System.currentTimeMillis()).apply()
        show = true
    }
    if (!show) return
    fun answer(a: String) {
        show = false
        Telemetry.event("rate:$a")
        if (a != "later") Store.prefs().edit().putString("rate", if (a == "yes") "done" else "never").apply()
        if (a == "yes") openBazaarRate(ctx)
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { answer("later") }, containerColor = C.cardSolid,
        title = { T(L("از گل‌لایو راضی هستی؟ 💚"), 17, FontWeight.Black) },
        text = { T(L("اگه برنامه به کارت میاد، با یه امتیاز ⭐ و یه نظر کوتاه در کافه‌بازار کمکمون کن تا بیشتر دیده بشیم. اگه هم چیزی اذیتت کرده، همون‌جا بنویس تا درستش کنیم."), 14, color = C.mute, align = TextAlign.Start) },
        confirmButton = { androidx.compose.material3.TextButton({ answer("yes") }) { T(L("⭐ امتیاز می‌دم"), 14, FontWeight.ExtraBold, LocalAccent.current) } },
        dismissButton = {
            Row {
                androidx.compose.material3.TextButton({ answer("never") }) { T(L("نه، ممنون"), 13, color = C.mute) }
                androidx.compose.material3.TextButton({ answer("later") }) { T(L("بعداً"), 13, FontWeight.Bold, C.ink) }
            }
        },
    )
}
