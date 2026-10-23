package app.goallive.live.ui

import app.goallive.live.L

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.runtime.*
import androidx.compose.ui.draw.*
import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.goallive.live.data.Api
import app.goallive.live.data.Clubs
import app.goallive.live.data.MyTeam
import app.goallive.live.data.Store
import app.goallive.live.data.path
import app.goallive.live.data.s
import app.goallive.live.notify.Commentary
import app.goallive.live.notify.LiveAlerts
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@SuppressLint("BatteryLife")
@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    var picker by remember { mutableStateOf(false) }
    var widgetHelp by remember { mutableStateOf(false) }
    var sources by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    // re-read permission/battery state whenever the user comes back from system settings
    var refresh by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(Unit) { owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { refresh++ } }
    val granted = remember(refresh) {
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }
    val batteryFree = remember(refresh) { runCatching { ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName) }.getOrDefault(true) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    val anyOn = Store.notifyGoals || Store.notifyLineups || Store.notifyStartEnd

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = BottomSpace)) {
        item {
            T(L("تنظیمات"), 16, FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp))
            Card {
                SubHead(L("تیم من"))
                val t = Store.myTeam
                ListRow(first = true, onClick = { picker = true }) {
                    if (t != null) Crest(t.id, 34.dp) else Icon(Ic.star, null, Modifier.size(20.dp), tint = C.ink)
                    Column(Modifier.weight(1f)) {
                        T(t?.let { Clubs.get(it.id)?.fa ?: it.name } ?: L("انتخاب تیم محبوب"), 14, FontWeight.Bold)
                        T(L("رنگ اپ، بازی‌های سنجاق‌شده و اعلان‌ها"), 12, color = C.mute)
                    }
                    Icon(Ic.chev, null, Modifier.size(18.dp), tint = C.mute)
                }
                SubHead(L("اعلان‌ها"))
                SetRow(Ic.bell, L("اعلان‌ها"), if (granted) L("برای تیم‌ها و بازی‌های دنبال‌شده؛ حتی وقتی اپ بسته است") else L("اجازه اعلان داده نشده؛ برای روشن کردن بزنید"),
                    first = true, on = granted && anyOn) {
                    if (!granted && Build.VERSION.SDK_INT >= 33) ask.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else if (anyOn) Store.setNotify(false, false, false) else Store.setNotify(true, true, true)
                }
                SetRow(Ic.ball, L("گل‌ها"), L("با نام گلزن و دقیقه"), on = Store.notifyGoals) { Store.setNotify(goals = !Store.notifyGoals) }
                SetRow(Ic.shirt, L("اعلام ترکیب"), L("حدود یک ساعت قبل از بازی"), on = Store.notifyLineups) { Store.setNotify(lineups = !Store.notifyLineups) }
                SetRow(Ic.whistle, L("شروع و پایان بازی"), null, on = Store.notifyStartEnd) { Store.setNotify(startEnd = !Store.notifyStartEnd) }
                SetRow(Ic.info, L("ارسال اعلان آزمایشی"), L("برای اطمینان از رسیدن اعلان‌ها")) {
                    if (!granted && Build.VERSION.SDK_INT >= 33) ask.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else LiveAlerts.notify(ctx, 1, LiveAlerts.CH_GOALS, L("⚽ گل! اعلان‌ها فعال است"), L("اعلان‌های گل این‌طور نمایش داده می‌شوند"), 0)
                }
                if (!batteryFree) SetRow(Ic.live, L("اعلان سریع‌تر وقتی گوشی قفل است"), L("بهینه‌سازی باتری را برای گل‌لایو خاموش کنید")) {
                    runCatching { ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
                }
                val am = ctx.getSystemService(android.app.AlarmManager::class.java)
                if (Build.VERSION.SDK_INT >= 31 && am?.canScheduleExactAlarms() == false) SetRow(Ic.bell, L("اعلان گل سر وقت"), L("اجازه «زنگ هشدار و یادآوری» را به گل‌لایو بدهید")) {
                    runCatching { ctx.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}"))) }
                }
                SubHead(L("ظاهر"))
                Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Seg(listOf("dark" to L("بازی شبانه"), "light" to L("بازی روز"), "auto" to L("مثل گوشی")), Store.theme,
                        icons = mapOf("dark" to Ic.moon, "light" to Ic.sun)) { Store.pickTheme(it); Look.mode = it }
                }
                SubHead("زبان · Language")
                Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // each option is written in its own language; picking one restarts the app in it
                    Seg(listOf("fa" to "فارسی", "en" to "English"), if (app.goallive.live.Lang.en) "en" else "fa") {
                        if ((it == "en") != app.goallive.live.Lang.en) app.goallive.live.Lang.switch(ctx, it == "en")
                    }
                    T(L("همه‌چیز فارسی: منوها، نام تیم‌ها، بازیکنان و لیگ‌ها و گزارش صوتی."), 12, color = C.mute)
                }
                SubHead(L("گزارش صوتی"))
                Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    T(L("اگر گوشی صدای فارسی ندارد «صدای آنلاین» را انتخاب کنید؛ گزارش با صدای گوینده فارسی از اینترنت پخش می‌شود."), 12, color = C.mute)
                    Seg(listOf("auto" to L("خودکار"), "online" to L("صدای آنلاین"), "phone" to L("صدای گوشی")), Store.voice) { Store.setVoice(mode = it) }
                    if (Store.voice != "phone") Seg(listOf("farid" to L("گوینده مرد"), "dilara" to L("گوینده زن")), Store.voiceName) { Store.setVoice(name = it) }
                }
                SetRow(Ic.mic, L("امتحان صدا"), L("یک جمله گزارش پخش می‌شود")) { Commentary.test(ctx) }
                SubHead(L("اپلیکیشن"))
                SetRow(Ic.cal, L("ویجت‌های صفحه اصلی"), L("نتیجه زنده تیم من (بزرگ و کوچک) و بازی‌های امروز"), first = true, chev = true) { widgetHelp = true }
                SubHead(L("داده‌ها"))
                SetRow(Ic.x, L("پاک کردن علاقه‌مندی‌ها"), null, first = true) { confirmClear = true }
                SetRow(Ic.list, L("منابع"), L("منبع اطلاعات، تصاویر، ویدیوها و صدا"), chev = true) { sources = true }
                SetRow(Ic.people, L("حریم خصوصی"), L("گل‌لایو اطلاعات شخصی شما را دریافت نمی‌کند"), chev = true) {
                    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_URL))) }
                }
                ListRow {
                    Icon(Ic.info, null, Modifier.size(20.dp), tint = C.ink)
                    val v = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: ""
                    T(L("گل‌لایو نسخه {0} · اطلاعات و آمار بازی‌ها از FotMob دریافت می‌شود.", v), 12, color = C.mute, modifier = Modifier.weight(1f))
                }
            }
        }
    }
    if (picker) TeamPicker { picker = false }
    if (sources) AlertDialog(
        onDismissRequest = { sources = false }, containerColor = C.cardSolid,
        title = { T(L("منابع"), 16, FontWeight.ExtraBold) },
        text = { T(SOURCES, 13) },
        confirmButton = { TextButton(onClick = { sources = false }) { T(L("باشه"), 14, FontWeight.Bold, LocalAccent.current) } },
    )
    if (widgetHelp) AlertDialog(
        onDismissRequest = { widgetHelp = false }, containerColor = C.cardSolid,
        title = { T(L("ویجت صفحه اصلی"), 16, FontWeight.ExtraBold) },
        text = {
            T((if (Store.myTeam == null) L("۰. اول از تنظیمات «تیم من» را انتخاب کنید.\n") else "") +
                L("۱. از اپ بیرون بروید و روی جای خالی صفحه اصلی گوشی انگشت را نگه دارید.\n۲. «ویجت‌ها» (Widgets) را بزنید.\n۳. «گل‌لایو» را پیدا کنید؛ سه ویجت دارد:\n• تیم من (بزرگ)\n• تیم من (کوچک، فقط نتیجه)\n• بازی‌های امروز\n۴. ویجت را روی صفحه بکشید.\n\nدر بازی زنده هر حدود یک دقیقه به‌روز می‌شود. با زدن روی ویجت اپ باز می‌شود."), 14)
        },
        confirmButton = { TextButton(onClick = { widgetHelp = false }) { T(L("باشه"), 14, FontWeight.Bold, LocalAccent.current) } },
    )
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false }, containerColor = C.cardSolid,
        title = { T(L("همه علاقه‌مندی‌ها پاک شوند؟"), 16, FontWeight.ExtraBold) },
        confirmButton = { TextButton(onClick = { Store.clearFollows(); confirmClear = false }) { T(L("پاک کن"), 14, FontWeight.Bold, C.live) } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { T(L("نه"), 14, FontWeight.Bold, C.mute) } },
    )
}

@Composable
private fun SetRow(icon: ImageVector, title: String, sub: String?, first: Boolean = false, on: Boolean? = null, chev: Boolean = false, onClick: () -> Unit) {
    ListRow(first = first, onClick = onClick) {
        Icon(icon, null, Modifier.size(20.dp), tint = C.ink)
        Column(Modifier.weight(1f)) {
            T(title, 14, FontWeight.Bold)
            if (sub != null) T(sub, 12, color = C.mute)
        }
        if (on != null) Switch(on)
        if (chev) Icon(Ic.chev, null, Modifier.size(18.dp), tint = C.mute)
    }
}

/** The site's ".switch": a pill track with a white knob, accent when on. */
@Composable
private fun Switch(on: Boolean) {
    val x by animateDpAsState(if (on) 18.dp else 0.dp, label = "knob")
    Box(Modifier.width(44.dp).height(26.dp).clip(RoundedCornerShape(50)).background(if (on) LocalAccent.current else C.line2)) {
        // RTL: the knob starts on the right and slides left when on
        Box(Modifier.align(Alignment.CenterStart).padding(horizontal = 3.dp).offset(x = x).size(20.dp).clip(CircleShape).background(Color.White))
    }
}

/** "تیم محبوبت کدومه؟": the club grid in each club's colors, plus search for any other team. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamPicker(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var q by remember { mutableStateOf("") }
    var res by remember { mutableStateOf<List<Pair<Int, String>>>(emptyList()) }
    LaunchedEffect(q) {
        if (q.trim().length < 2) { res = emptyList(); return@LaunchedEffect }
        delay(300)
        res = runCatching { search(q.trim()).teams.map { (it.s("id")?.toIntOrNull() ?: 0) to (it.s("name") ?: "") } }.getOrDefault(emptyList()).take(6)
    }
    val accent = LocalAccent.current
    val pick: (Int?, String) -> Unit = { id, name ->
        Store.onboarded = true
        if (id == null) { Store.pickMyTeam(null); onClose() }
        else scope.launch {
            // remember the club color for the widget and the app accent
            val color = if (Clubs.get(id) == null) runCatching {
                val d = Api.fm("teams", mapOf("id" to id, "ccode3" to "IRN"), 3_600_000)
                d.path("overview.teamColors").s("darkMode") ?: d.path("overview.teamColors").s("color")
            }.getOrNull() else null
            Store.pickMyTeam(MyTeam(id, name, color))
            LiveAlerts.start(ctx)
            onClose()
        }
    }
    ModalBottomSheet(onDismissRequest = { Store.onboarded = true; onClose() }, sheetState = state, containerColor = C.cardSolid,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), dragHandle = {
            Box(Modifier.padding(top = 10.dp).width(44.dp).height(5.dp).clip(RoundedCornerShape(5.dp)).background(C.line2))
        }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.padding(top = 6.dp).size(64.dp).glow(accent.copy(alpha = 0.4f), 10.dp, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp)).background(accent),
                    contentAlignment = Alignment.Center) { Icon(Ic.ball, null, Modifier.size(34.dp), tint = LocalAccentInk.current) }
                T(L("تیم محبوبت کدومه؟"), 22, FontWeight.Black, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                T(L("اپ با رنگ تیمت درمیاد، بازی‌هاش همیشه بالای صفحه است و گل‌ها و ترکیبش اعلان می‌شه."), 13, color = C.mute, align = TextAlign.Center)
            }
            Box(Modifier.padding(horizontal = 16.dp)) { SearchField(q, L("جستجوی تیم‌های دیگر (انگلیسی)…")) { q = it } }
            if (res.isNotEmpty()) Card(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp)) {
                res.forEachIndexed { i, (id, name) ->
                    ListRow(first = i == 0, onClick = { pick(id, name) }) { Crest(id, 34.dp); Ltr(name, Modifier.weight(1f)) }
                }
            }
            Clubs.ids.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { id ->
                        val c = Clubs.get(id)!!
                        val c1 = Color(c.c1)
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).drawBehind { cssLinear(150f, 0f to c1, 1f to darken(c1, 0.35f)) }
                                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(18.dp)).clickable { pick(id, c.fa) }
                                .padding(start = 4.dp, end = 4.dp, top = 12.dp, bottom = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Crest(id, 44.dp, large = true)
                            T(c.fa, 11, FontWeight.ExtraBold, Color.White, maxLines = 2, align = TextAlign.Center)
                        }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (Store.myTeam != null) Box(Modifier.weight(1f).height(46.dp).clip(RoundedCornerShape(14.dp)).border(1.dp, C.line, RoundedCornerShape(14.dp))
                    .clickable { pick(null, "") }, contentAlignment = Alignment.Center) { T(L("بدون تیم"), 14, FontWeight.Bold) }
                Box(Modifier.weight(1f).height(46.dp).clip(RoundedCornerShape(14.dp)).border(1.dp, C.line, RoundedCornerShape(14.dp))
                    .clickable { Store.onboarded = true; onClose() }, contentAlignment = Alignment.Center) { T(if (Store.myTeam != null) L("بستن") else L("بعداً"), 14, FontWeight.Bold) }
            }
        }
    }
}

const val PRIVACY_URL = "https://github.com/jeejjeH1/iiiii/blob/ccr-addbae8c-95x58c/store/PRIVACY.md"

/** Where everything shown in the app comes from (the store asks for this inside the app). */
private val SOURCES = L("• نتایج، آمار، ترکیب تیم‌ها، جدول لیگ‌ها و نشان باشگاه‌ها و عکس بازیکنان: FotMob\nhttps://www.fotmob.com\n\n") +
    L("• ویدیوی خلاصه بازی‌ها: آپارات\nhttps://www.aparat.com\n\n") +
    L("• صدای گوینده گزارش صوتی: سرویس تبدیل متن به صدای مایکروسافت (Microsoft Edge)\nhttps://www.microsoft.com\n\n") +
    L("• پخش زنده: برنامه یا سایت رسمی هر سرویس؛ گل‌لایو ویدیویی پخش نمی‌کند\nhttps://www.namava.ir · https://sport.aparat.com · https://telewebion.com\n\n") +
    L("• نام فارسی تیم‌ها، دستیار، رادار حریف، کوئیز و صداهای اعلان: ساخته خود گل‌لایو")
