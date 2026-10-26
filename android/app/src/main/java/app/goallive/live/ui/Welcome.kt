package app.goallive.live.ui

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.goallive.live.L
import app.goallive.live.R
import app.goallive.live.data.Clubs
import app.goallive.live.data.MyTeam
import app.goallive.live.data.Store
import app.goallive.live.data.Telemetry
import app.goallive.live.data.s
import app.goallive.live.notify.LiveAlerts
import kotlinx.coroutines.delay

/** The leagues offered on the welcome screen: FotMob id, English name (kept with the follow) and Persian name. */
private val WELCOME_LEAGUES = listOf(
    Triple(523, "Persian Gulf Pro League", "لیگ برتر ایران"), Triple(42, "Champions League", "لیگ قهرمانان اروپا"),
    Triple(47, "Premier League", "لیگ برتر انگلیس"), Triple(87, "LaLiga", "لالیگا"), Triple(55, "Serie A", "سری آ"),
    Triple(54, "Bundesliga", "بوندس‌لیگا"), Triple(53, "Ligue 1", "لیگ ۱ فرانسه"), Triple(73, "Europa League", "لیگ اروپا"),
)

/**
 * First launch, in four short steps instead of a pile of pop-ups: what the app does, your team(s), your leagues, and
 * goal alerts (with the background permission they need). Every step can be skipped; it is shown once.
 */
@Composable
fun Welcome(onDone: () -> Unit) {
    val ctx = LocalContext.current
    var step by rememberSaveable { mutableIntStateOf(0) }
    val teams = remember { mutableStateListOf<Pair<Int, String>>() }
    val leagues = remember { mutableStateListOf<Int>() }
    val notify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) Telemetry.event("welcome:notify")
        if (!batteryFree(ctx)) askBatteryFree(ctx)
        finish(ctx, teams, leagues, false, onDone)
    }
    BackHandler(step > 0) { step-- }
    val accent = LocalAccent.current
    Box(Modifier.fillMaxSize().background(C.bg).clickable(remember { MutableInteractionSource() }, null) {}) {
        // a soft glow of the app colour behind everything
        Box(Modifier.fillMaxSize().drawBehind {
            drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.16f), Color.Transparent), center = androidx.compose.ui.geometry.Offset(size.width * 0.8f, 0f), radius = size.width),
                radius = size.width, center = androidx.compose.ui.geometry.Offset(size.width * 0.8f, 0f))
        })
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            // progress and skip
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(4) { k -> Box(Modifier.height(6.dp).width(if (k == step) 28.dp else 10.dp).clip(RoundedCornerShape(3.dp)).background(if (k <= step) accent else C.line2)) }
                }
                T(L("رد شدن"), 14, FontWeight.Bold, C.mute, Modifier.clip(RoundedCornerShape(10.dp)).clickable {
                    Telemetry.event("welcome:skip"); finish(ctx, teams, leagues, true, onDone)
                }.padding(horizontal = 10.dp, vertical = 6.dp))
            }
            AnimatedContent(step, Modifier.weight(1f), transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (slideInHorizontally { -dir * it / 3 } + fadeIn()) togetherWith (slideOutHorizontally { dir * it / 3 } + fadeOut())
            }, label = "welcome") { s ->
                when (s) {
                    0 -> Hello()
                    1 -> PickTeams(teams)
                    2 -> PickLeagues(leagues)
                    else -> Alerts()
                }
            }
            // the main button
            val label = when (step) {
                0 -> L("شروع")
                1 -> if (teams.isEmpty()) L("بدون تیم، ادامه") else L("ادامه ({0} تیم)", fa(teams.size))
                2 -> if (leagues.isEmpty()) L("بدون لیگ، ادامه") else L("ادامه ({0} لیگ)", fa(leagues.size))
                else -> L("🔔 اعلان‌ها را روشن کن")
            }
            Box(Modifier.padding(horizontal = 20.dp, vertical = 12.dp).fillMaxWidth().height(56.dp).clip(RoundedCornerShape(18.dp)).background(accent)
                .clickable {
                    if (step < 3) step++
                    else if (Build.VERSION.SDK_INT >= 33) notify.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    else { if (!batteryFree(ctx)) askBatteryFree(ctx); finish(ctx, teams, leagues, false, onDone) }
                }, contentAlignment = Alignment.Center) {
                T(label, 17, FontWeight.Black, LocalAccentInk.current)
            }
            if (step == 3) T(L("بعداً"), 14, FontWeight.Bold, C.mute, Modifier.align(Alignment.CenterHorizontally).clip(RoundedCornerShape(10.dp))
                .clickable { finish(ctx, teams, leagues, false, onDone) }.padding(horizontal = 16.dp, vertical = 8.dp))
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** Saves the choices and closes the welcome for good. */
private fun finish(ctx: android.content.Context, teams: List<Pair<Int, String>>, leagues: List<Int>, skipped: Boolean, onDone: () -> Unit) {
    // the first team picked is "my team" (colours, widget, top of the home page); the rest are followed
    teams.firstOrNull()?.let { (id, name) -> Store.pickMyTeam(MyTeam(id, name, null)) }
    teams.drop(1).forEach { (id, name) -> if (!Store.isTeam(id)) Store.toggleTeam(id, name) }
    leagues.forEach { id -> if (!Store.isLeague(id)) Store.toggleLeague(id, WELCOME_LEAGUES.firstOrNull { it.first == id }?.second ?: "") }
    if (teams.isNotEmpty()) Telemetry.event("welcome:team")
    if (!skipped) Telemetry.event("welcome:done")
    Store.onboarded = true
    markNewsSeen(ctx)
    Store.prefs().edit().putBoolean("askedNotify", true).putInt("askedBattery", 1).putLong("askedBatteryAt", System.currentTimeMillis()).apply()
    LiveAlerts.start(ctx)
    onDone()
}

@Composable
private fun Title(emoji: String, title: String, sub: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        T(emoji, 40)
        T(title, 26, FontWeight.Black, modifier = Modifier.padding(top = 6.dp), align = TextAlign.Center)
        T(sub, 14, color = C.mute, modifier = Modifier.padding(top = 6.dp), align = TextAlign.Center)
    }
}

/** Step 1: what GoalLive is. */
@Composable
private fun Hello() {
    var shown by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { repeat(5) { delay(160); shown++ } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(20.dp))
        Image(painterResource(R.drawable.logo), null, Modifier.size(104.dp).clip(RoundedCornerShape(30.dp)).background(Color.White).padding(8.dp))
        T(L("به گل‌لایو خوش اومدی"), 28, FontWeight.Black, modifier = Modifier.padding(top = 18.dp), align = TextAlign.Center)
        T(L("نتایج زنده‌ی فوتبال، کاملاً فارسی"), 15, color = C.mute, modifier = Modifier.padding(top = 6.dp, bottom = 22.dp))
        listOf(
            "⚽" to L("نتیجه‌ی زنده و اعلان گل، حتی وقتی برنامه بسته است"),
            "⭐" to L("نمره‌ی گل‌لایو برای هر بازیکن و تیم هفته"),
            "🎬" to L("عکس و ویدیوی استوری از هر بازی، با یک لمس"),
            "📊" to L("آمار کامل: شوت‌ها، ترکیب، مومنتوم و جدول"),
        ).forEachIndexed { k, (e, s) ->
            androidx.compose.animation.AnimatedVisibility(shown > k, enter = fadeIn() + slideInHorizontally { it / 4 }) {
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(18.dp)).background(C.card).border(1.dp, C.line, RoundedCornerShape(18.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    T(e, 24); T(s, 15, FontWeight.Bold, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/** Step 2: the clubs, in their colours; the first one picked becomes "my team". Search finds any other. */
@Composable
private fun PickTeams(picked: MutableList<Pair<Int, String>>) {
    var q by remember { mutableStateOf("") }
    var res by remember { mutableStateOf<List<Pair<Int, String>>>(emptyList()) }
    LaunchedEffect(q) {
        if (q.trim().length < 2) { res = emptyList(); return@LaunchedEffect }
        delay(300)
        res = runCatching { search(q.trim()).teams.map { (it.s("id")?.toIntOrNull() ?: 0) to (it.s("name") ?: "") } }.getOrDefault(emptyList()).take(6)
    }
    fun toggle(id: Int, name: String) { val i = picked.indexOfFirst { it.first == id }; if (i >= 0) picked.removeAt(i) else picked.add(id to name) }
    val accent = LocalAccent.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Title("❤️", L("تیم‌های محبوبت؟"), L("اولین تیمی که بزنی «تیم من» می‌شه: اپ با رنگش درمیاد و بازی‌هاش همیشه بالای صفحه است."))
        Box(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) { SearchField(q, L("جستجوی تیم‌های دیگر (انگلیسی)…")) { q = it } }
        if (res.isNotEmpty()) Card(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            res.forEachIndexed { i, (id, name) ->
                val on = picked.any { it.first == id }
                ListRow(first = i == 0, onClick = { toggle(id, name) }) {
                    Crest(id, 34.dp); Ltr(name, Modifier.weight(1f))
                    if (on) T("✓", 18, FontWeight.Black, accent)
                }
            }
        }
        Clubs.ids.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { id ->
                    val c = Clubs.get(id)!!
                    val c1 = Color(c.c1)
                    val at = picked.indexOfFirst { it.first == id }
                    Box(Modifier.weight(1f)) {
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).drawBehind { cssLinear(150f, 0f to c1, 1f to darken(c1, 0.35f)) }
                                .border(if (at >= 0) 3.dp else 1.dp, if (at >= 0) accent else Color.White.copy(alpha = 0.1f), RoundedCornerShape(18.dp))
                                .clickable { toggle(id, c.fa) }.padding(start = 4.dp, end = 4.dp, top = 12.dp, bottom = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Crest(id, 44.dp, large = true)
                            T(c.fa, 11, FontWeight.ExtraBold, Color.White, maxLines = 2, align = TextAlign.Center)
                        }
                        if (at >= 0) Box(Modifier.align(Alignment.TopEnd).padding(5.dp).clip(RoundedCornerShape(50)).background(accent).padding(horizontal = 6.dp, vertical = 1.dp)) {
                            T(if (at == 0) L("تیم من") else "✓", 10, FontWeight.Black, LocalAccentInk.current)
                        }
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

/** Step 3: the leagues whose matches come first on the home page. */
@Composable
private fun PickLeagues(picked: MutableList<Int>) {
    val accent = LocalAccent.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Title("🏆", L("کدوم لیگ‌ها رو دنبال می‌کنی؟"), L("بازی‌های این لیگ‌ها در «دنبال‌شده‌ها» جمع می‌شه و اعلان‌هاشون برات میاد."))
        WELCOME_LEAGUES.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (id, _, fa) ->
                    val on = id in picked
                    Row(Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(if (on) accent.copy(alpha = 0.16f) else C.card)
                        .border(if (on) 2.dp else 1.dp, if (on) accent else C.line, RoundedCornerShape(18.dp))
                        .clickable { if (on) picked.remove(id) else picked.add(id) }.padding(horizontal = 12.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        // the badges are drawn for a dark background
                        Box(Modifier.size(38.dp).clip(CircleShape).background(Color(0xFF1B222C)).padding(5.dp), contentAlignment = Alignment.Center) { LeagueLogo(id, 26.dp) }
                        T(L(fa), 14, FontWeight.ExtraBold, modifier = Modifier.weight(1f), maxLines = 2)
                        if (on) T("✓", 16, FontWeight.Black, accent)
                    }
                }
            }
        }
    }
}

/** Step 4: why to allow alerts, with a sample of what arrives. */
@Composable
private fun Alerts() {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Title("🔔", L("گل که خورد، خبردار شو"), L("برای تیم‌ها و لیگ‌هایی که انتخاب کردی: گل، ترکیب، شروع و پایان بازی."))
        Spacer(Modifier.height(10.dp))
        listOf(L("⚽ گل! پرسپولیس ۱ - ۰ استقلال") to L("دقیقه ۳۴ · گلزن: …"), L("📋 ترکیب رئال مادرید اعلام شد") to L("یک ساعت قبل از بازی"), L("🏁 پایان بازی") to L("بارسلونا ۲ - ۱ اتلتیکو")).forEach { (h, s) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(22.dp)).background(C.card).border(1.dp, C.line, RoundedCornerShape(22.dp))
                .padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Image(painterResource(R.drawable.logo), null, Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(Color.White).padding(3.dp))
                Column(Modifier.weight(1f)) { T(h, 15, FontWeight.ExtraBold); T(s, 12, color = C.mute) }
            }
        }
        T(L("برای رسیدن اعلان وقتی برنامه بسته است، در پنجره‌های بعد «اجازه» را بزنید."), 13, color = C.mute, modifier = Modifier.padding(top = 14.dp), align = TextAlign.Center)
    }
}
