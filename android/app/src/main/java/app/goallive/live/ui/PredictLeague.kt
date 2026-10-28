package app.goallive.live.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.goallive.live.L
import app.goallive.live.data.Account
import app.goallive.live.data.LeagueError
import app.goallive.live.data.objs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

/*
 * "لیگ پیش‌بینی": the user's predictions against friends'. Private leagues joined by a six-letter code or an invite link,
 * a table for the week and for the season, and the league of every GoalLive player. Starting needs only a name; a phone
 * number (optional) keeps the account on any phone.
 */

/** What a refusal from the league server means, in words. */
private fun errorText(e: Throwable) = when ((e as? LeagueError)?.code) {
    "wrong" -> L("کد درست نیست")
    "expired" -> L("کد منقضی شده؛ دوباره بگیر")
    "tries" -> L("دفعه‌های زیادی اشتباه شد؛ کد تازه بگیر")
    "limit" -> L("امروز به سقف درخواست رسیدی؛ فردا دوباره امتحان کن")
    "phone" -> L("شماره‌ی موبایل درست نیست")
    "name" -> L("نام دست کم دو حرف باشد")
    "code" -> L("لیگی با این کد پیدا نشد")
    "full" -> L("این لیگ پر شده است")
    else -> if ((e as? LeagueError)?.code?.startsWith("sms") == true) L("فرستادن پیامک ممکن نشد؛ کمی بعد دوباره امتحان کن")
    else L("ارتباط برقرار نشد؛ اینترنت را بررسی کن")
}

/** A one-line box for a name, a number or a code. */
@Composable
private fun LField(value: String, hint: String, modifier: Modifier = Modifier, number: Boolean = false, big: Boolean = false, onChange: (String) -> Unit) {
    val accent = LocalAccent.current
    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.card2).border(1.5.dp, if (value.isNotEmpty()) accent.copy(alpha = 0.6f) else C.line2, RoundedCornerShape(16.dp))
        .padding(horizontal = 16.dp, vertical = if (big) 14.dp else 13.dp), contentAlignment = if (big) Alignment.Center else Alignment.CenterStart) {
        val style = (if (big) num(26.sp, FontWeight.Black) else vz(16.sp)).copy(color = C.ink, textAlign = if (big) TextAlign.Center else TextAlign.Start)
        if (value.isEmpty()) T(hint, if (big) 18 else 15, color = C.faint, align = if (big) TextAlign.Center else null, modifier = Modifier.fillMaxWidth())
        BasicTextField(value, onChange, Modifier.fillMaxWidth(), singleLine = true, textStyle = style, cursorBrush = SolidColor(accent),
            keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Phone else KeyboardType.Text))
    }
}

@Composable
private fun BigButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, busy: Boolean = false, plain: Boolean = false, onClick: () -> Unit) {
    val accent = LocalAccent.current
    Box(modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(16.dp)).background(if (plain) C.card2 else if (enabled) accent else C.card2)
        .then(if (plain) Modifier.border(1.dp, C.line2, RoundedCornerShape(16.dp)) else Modifier)
        .clickable(enabled = enabled && !busy, onClick = onClick), contentAlignment = Alignment.Center) {
        T(if (busy) L("صبر کنید…") else text, 15, FontWeight.Black, if (plain || !enabled) C.ink else LocalAccentInk.current)
    }
}

/** Sends the league's invite: a line, its code and the link that opens it. */
private fun invite(ctx: android.content.Context, name: String, code: String) {
    val t = L("بیا تو لیگ پیش‌بینی «{0}» در گل‌لایو با هم رقابت کنیم!", name) + "\n" + L("کد لیگ: {0}", code) + "\n" + Account.inviteLink(code)
    runCatching { ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, t), L("دعوت دوستان"))) }
}

/* ---------- logging in with a phone number ---------- */

/** The number, then the five-digit code sent to it (shown right here while the server is in test mode). */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PhoneSheet(onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var step by remember { mutableIntStateOf(0) }
    var wait by remember { mutableIntStateOf(0) }
    var test by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(wait) { if (wait > 0) { delay(1000); wait-- } }
    fun ask() {
        busy = true; error = null
        scope.launch {
            runCatching { Account.askCode(phone) }.onSuccess { (w, t) -> step = 1; wait = w; test = t; code = "" }.onFailure { error = errorText(it) }
            busy = false
        }
    }
    fun check(c: String) {
        busy = true; error = null
        scope.launch {
            runCatching { Account.verify(phone, c, Account.me?.name) }.onSuccess { onClose() }.onFailure { error = errorText(it) }
            busy = false
        }
    }
    GlSheet(onDismissRequest = onClose) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(bottom = 18.dp)) {
            SheetHeader(if (step == 0) L("ورود با شماره موبایل") else L("کد تأیید"),
                if (step == 0) L("حسابت به این شماره وصل می‌شود؛ روی هر گوشی با همین شماره همه چیز برمی‌گردد.")
                else L("کد ۵ رقمی که به {0} پیامک شد را بنویس", fa(phone)), onClose)
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (step == 0) {
                    LField(phone, L("مثلاً ۰۹۱۲۳۴۵۶۷۸۹"), number = true) { phone = it.take(14) }
                    BigButton(L("گرفتن کد"), enabled = phone.count { it.isDigit() } >= 10, busy = busy) { ask() }
                } else {
                    LField(code, "– – – – –", number = true, big = true) { v ->
                        code = v.filter { it.isDigit() }.take(5)
                        if (code.length == 5 && !busy) check(code)
                    }
                    test?.let { T(L("حالت آزمایشی: کد {0}", fa(it)), 13, FontWeight.Bold, C.gold, align = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
                    BigButton(L("ورود"), enabled = code.length == 5, busy = busy) { check(code) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        T(L("شماره را عوض کن"), 13, FontWeight.Bold, C.mute, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { step = 0; error = null }.padding(6.dp))
                        if (wait > 0) T(L("ارسال دوباره تا {0} ثانیه", fa(wait)), 13, color = C.faint, modifier = Modifier.padding(6.dp))
                        else T(L("ارسال دوباره"), 13, FontWeight.Bold, LocalAccent.current, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(enabled = !busy) { ask() }.padding(6.dp))
                    }
                }
                error?.let { T(it, 13, FontWeight.Bold, C.live, align = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
            }
        }
    }
}

/** One question with a box: a league's name, a code, the player's name. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun AskSheet(title: String, sub: String, hint: String, action: String, initial: String = "", code: Boolean = false, onDone: suspend (String) -> Unit, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var v by remember { mutableStateOf(initial) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    GlSheet(onDismissRequest = onClose) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(bottom = 18.dp)) {
            SheetHeader(title, sub, onClose)
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LField(v, hint, big = code) { v = if (code) it.uppercase().filter { c -> c.isLetterOrDigit() }.take(6) else it.take(24) }
                BigButton(action, enabled = v.trim().length >= 2, busy = busy) {
                    busy = true; error = null
                    scope.launch { runCatching { onDone(v.trim()) }.onSuccess { onClose() }.onFailure { error = errorText(it) }; busy = false }
                }
                error?.let { T(it, 13, FontWeight.Bold, C.live, align = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
            }
        }
    }
}

/* ---------- the league home ---------- */

@Composable
fun PredictLeagueScreen() {
    val nav = LocalNav.current
    val accent = LocalAccent.current
    var data by remember { mutableStateOf<JSONObject?>(null) }
    var failed by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf("") }
    var reload by remember { mutableIntStateOf(0) }
    val signed = Account.signedIn
    LaunchedEffect(signed, reload) {
        if (!signed) return@LaunchedEffect
        runCatching { Account.refresh(); Account.pushAll(); Account.leagues() }.onSuccess { data = it; failed = false }.onFailure { failed = true }
    }
    // an invite link: join as soon as there is an account
    LaunchedEffect(Account.pendingCode, signed) {
        val c = Account.pendingCode ?: return@LaunchedEffect
        if (!signed) return@LaunchedEffect
        Account.pendingCode = null
        runCatching { Account.join(c) }.onSuccess { nav.predictLeague(it.optInt("id")) }.onFailure { sheet = "join:$c" }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = BottomSpace), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Brush.linearGradient(listOf(C.gold.copy(alpha = 0.28f), accent.copy(alpha = 0.10f), Color.Transparent)))
                .border(1.dp, C.gold.copy(alpha = 0.35f), RoundedCornerShape(24.dp)).padding(20.dp)) {
                T(L("لیگ پیش‌بینی"), 24, FontWeight.Black)
                T(L("نتیجه‌ی بازی‌ها را پیش‌بینی کن و با دوستانت رقابت کن. نتیجه‌ی دقیق ۳ امتیاز، برنده‌ی درست ۱ امتیاز."), 13, color = C.mute, modifier = Modifier.padding(top = 4.dp))
                Account.pendingCode?.let { T(L("دعوت به لیگ با کد {0}؛ اول وارد شو", it), 13, FontWeight.Bold, C.gold, modifier = Modifier.padding(top = 8.dp)) }
            }
        }
        if (!signed) {
            item { StartCard { sheet = "phone" } }
            return@LazyColumn
        }
        // the account
        item {
            val me = Account.me
            Card(pad = PaddingValues(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(46.dp).clip(CircleShape).background(accent), contentAlignment = Alignment.Center) {
                        T((me?.name ?: "؟").take(1), 20, FontWeight.Black, LocalAccentInk.current)
                    }
                    Column(Modifier.weight(1f)) {
                        T(me?.name ?: "", 17, FontWeight.Black, maxLines = 1)
                        T(me?.phone?.let { L("شماره: {0}", fa(it)) } ?: L("بدون شماره؛ فقط روی همین گوشی"), 12, color = C.mute)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        T(fa(me?.points ?: 0), 22, FontWeight.Black, C.gold); T(L("امتیاز"), 12, color = C.mute)
                    }
                }
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (me?.phone == null) Pill(L("ثبت شماره"), PillKind.Accent, onClick = { sheet = "phone" })
                    Pill(L("تغییر نام"), PillKind.Plain, onClick = { sheet = "name" })
                    Pill(L("پیش‌بینی‌های من"), PillKind.Plain, onClick = { nav.predictions() })
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BigButton(L("ساختن لیگ"), Modifier.weight(1f)) { sheet = "new" }
                BigButton(L("پیوستن با کد"), Modifier.weight(1f), plain = true) { sheet = "join" }
            }
        }
        val d = data
        if (d == null) item { if (failed) EmptyView(L("لیگ‌ها نیامد"), null, L("اینترنت را بررسی کن"), L("دوباره")) { reload++ } else Skeleton(3) }
        else {
            item {
                val e = d.optJSONObject("everyone")
                LeagueRow(L("همه‌ی کاربران گل‌لایو"), L("{0} بازیکن", fa(e?.optInt("players") ?: 0)), e?.optInt("rank")?.takeIf { it > 0 }, true) { nav.predictLeague(0) }
            }
            val mine = d.optJSONArray("leagues").objs()
            if (mine.isEmpty()) item {
                T(L("هنوز در لیگی نیستی. یک لیگ بساز و کدش را برای دوستانت بفرست."), 13, color = C.mute, modifier = Modifier.padding(horizontal = 6.dp))
            } else {
                item { SectionTitle(L("لیگ‌های من")) }
                mine.forEach { l ->
                    item(key = "l" + l.optInt("id")) {
                        LeagueRow(l.optString("name"), L("{0} نفر · صدرنشین: {1}", fa(l.optInt("members")), l.optString("leader")), l.optInt("rank").takeIf { it > 0 }) { nav.predictLeague(l.optInt("id")) }
                    }
                }
            }
        }
        item {
            T(L("پیش‌بینی‌ها تا سوت شروع بازی قابل تغییرند. جدول بعد از هر بازی به‌روز می‌شود."), 12, color = C.faint, modifier = Modifier.padding(6.dp))
        }
    }
    val ctx = LocalContext.current
    when {
        sheet == "phone" -> PhoneSheet { sheet = ""; reload++ }
        sheet == "name" -> AskSheet(L("نام تو در جدول"), L("دوستانت تو را با این نام می‌بینند"), L("مثلاً علی"), L("ثبت"), Account.me?.name ?: "",
            onDone = { Account.rename(it) }) { sheet = ""; reload++ }
        sheet == "new" -> AskSheet(L("لیگ تازه"), L("یک نام برای لیگ؛ بعد کدش را برای دوستانت می‌فرستی"), L("مثلاً بچه‌های دانشگاه"), L("ساختن"),
            onDone = { val l = Account.create(it); invite(ctx, l.optString("name"), l.optString("code")); reload++ }) { sheet = "" }
        sheet.startsWith("join") -> AskSheet(L("پیوستن به لیگ"), L("کد ۶ حرفی که دوستت فرستاده"), "ABC123", L("پیوستن"), sheet.substringAfter(':', ""), code = true,
            onDone = { val l = Account.join(it); reload++; nav.predictLeague(l.optInt("id")) }) { sheet = "" }
    }
}

/** Before an account: a name to start with, or a phone number. */
@Composable
private fun StartCard(onPhone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Card(pad = PaddingValues(16.dp)) {
        for ((k, line) in listOf(L("یک لیگ بساز یا با کد دوستت وارد لیگش شو"), L("قبل از هر بازی نتیجه را پیش‌بینی کن"), L("جدول هفته و فصل را ببین و صدرنشین شو")).withIndex()) {
            Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(26.dp).clip(CircleShape).background(C.gold.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) { T(fa(k + 1), 13, FontWeight.Black, C.gold) }
                T(line, 14, FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(10.dp))
        LField(name, L("نام تو در جدول، مثلاً علی")) { name = it.take(24) }
        Spacer(Modifier.height(10.dp))
        BigButton(L("شروع"), enabled = name.trim().length >= 2, busy = busy) {
            busy = true; error = null
            scope.launch { runCatching { Account.startAsGuest(name.trim()) }.onFailure { error = errorText(it) }; busy = false }
        }
        Spacer(Modifier.height(8.dp))
        BigButton(L("ورود با شماره موبایل"), plain = true, onClick = onPhone)
        error?.let { T(it, 13, FontWeight.Bold, C.live, align = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) }
        T(L("ورود با شماره اختیاری است؛ با آن حسابت روی هر گوشی می‌ماند و با همان شماره برمی‌گردد."), 12, color = C.mute, modifier = Modifier.padding(top = 10.dp))
    }
}

@Composable
private fun LeagueRow(name: String, sub: String, rank: Int?, everyone: Boolean = false, onClick: () -> Unit) {
    val accent = LocalAccent.current
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(C.card).border(1.dp, if (everyone) C.gold.copy(alpha = 0.4f) else C.line, RoundedCornerShape(20.dp))
        .clickable(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(if (everyone) C.gold.copy(alpha = 0.18f) else accent.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Icon(if (everyone) Ic.trophy else Ic.people, null, Modifier.size(22.dp), tint = if (everyone) C.gold else accent)
        }
        Column(Modifier.weight(1f)) {
            T(name, 16, FontWeight.ExtraBold, maxLines = 1)
            T(sub, 12, color = C.mute, maxLines = 1)
        }
        if (rank != null) Column(horizontalAlignment = Alignment.CenterHorizontally) {
            T(fa(rank), 20, FontWeight.Black, if (rank == 1) C.gold else C.ink); T(L("رتبه"), 12, color = C.mute)
        }
        Icon(Ic.chev, null, Modifier.size(16.dp), tint = C.faint)
    }
}

/* ---------- a league's table ---------- */

@Composable
fun PredictLeagueTable(id: Int) {
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var week by remember { mutableStateOf(true) }
    var data by remember { mutableStateOf<JSONObject?>(null) }
    var failed by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    // live while open: a refresh every minute
    LaunchedEffect(id, week) {
        while (true) {
            runCatching { Account.table(id, week) }.onSuccess { data = it; failed = false }.onFailure { failed = data == null }
            delay(60_000)
        }
    }
    val info = data?.optJSONObject("league")
    val rows = data?.optJSONArray("rows").objs()
    val meId = Account.me?.id
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = BottomSpace), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Brush.linearGradient(listOf(C.gold.copy(alpha = 0.26f), Color.Transparent)))
                .border(1.dp, C.gold.copy(alpha = 0.35f), RoundedCornerShape(24.dp)).padding(18.dp)) {
                T(if (id == 0) L("همه‌ی کاربران گل‌لایو") else info?.optString("name") ?: "", 22, FontWeight.Black, maxLines = 2)
                if (id != 0 && info != null) {
                    T(L("{0} نفر · کد لیگ: {1}", fa(info.optInt("members")), info.optString("code")), 13, color = C.mute, modifier = Modifier.padding(top = 4.dp))
                    Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill(L("دعوت دوستان"), PillKind.Accent, icon = Ic.share, onClick = { invite(ctx, info.optString("name"), info.optString("code")) })
                        Pill(L("خروج از لیگ"), PillKind.Plain, onClick = { leaving = true })
                    }
                } else T(L("هر کس دست کم یک پیش‌بینی ثبت کرده باشد"), 13, color = C.mute, modifier = Modifier.padding(top = 4.dp))
            }
        }
        item { Seg(listOf("week" to L("این هفته"), "all" to L("کل فصل")), if (week) "week" else "all") { week = it == "week" } }
        if (data == null) item { if (failed) EmptyView(L("جدول نیامد"), null, L("اینترنت را بررسی کن")) else Skeleton(4) }
        else if (rows.isEmpty()) item { EmptyView(L("هنوز کسی پیش‌بینی نکرده"), null, L("یک بازی را باز کن و در «پیش‌بینی تو» نتیجه را حدس بزن."), L("بازی‌های امروز")) { nav.tab("home") } }
        else item {
            Card {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                    T("#", 12, color = C.faint, modifier = Modifier.width(30.dp))
                    T(L("بازیکن"), 12, color = C.faint, modifier = Modifier.weight(1f))
                    T(L("بازی"), 12, color = C.faint, modifier = Modifier.width(44.dp), align = TextAlign.Center)
                    T(L("دقیق"), 12, color = C.faint, modifier = Modifier.width(44.dp), align = TextAlign.Center)
                    T(L("امتیاز"), 12, color = C.faint, modifier = Modifier.width(54.dp), align = TextAlign.Center)
                }
                rows.forEachIndexed { k, r ->
                    val me = r.optInt("id") == meId
                    val medal = when (k) { 0 -> C.gold; 1 -> Color(0xFFC9D1D9); 2 -> Color(0xFFD08C4A); else -> null }
                    Row(Modifier.fillMaxWidth().background(if (me) LocalAccent.current.copy(alpha = 0.12f) else Color.Transparent).padding(horizontal = 16.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(30.dp)) {
                            Box(Modifier.size(24.dp).clip(CircleShape).background(medal ?: Color.Transparent), contentAlignment = Alignment.Center) {
                                T(fa(k + 1), 12, FontWeight.Black, if (medal != null) Color(0xFF1A1405) else C.mute)
                            }
                        }
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            T(r.optString("name") + if (me) " " + L("(تو)") else "", 14, if (me) FontWeight.Black else FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f, false))
                            if (r.optInt("live") > 0) Box(Modifier.clip(RoundedCornerShape(6.dp)).background(C.live).padding(horizontal = 5.dp)) { T(L("زنده"), 12, FontWeight.Black, Color.White) }
                        }
                        T(fa(r.optInt("played")), 13, color = C.mute, modifier = Modifier.width(44.dp), align = TextAlign.Center)
                        T(fa(r.optInt("exact")), 13, color = C.mute, modifier = Modifier.width(44.dp), align = TextAlign.Center)
                        T(fa(r.optInt("points")), 16, FontWeight.Black, if (k == 0) C.gold else C.ink, modifier = Modifier.width(54.dp), align = TextAlign.Center)
                    }
                    if (k < rows.size - 1) Box(Modifier.fillMaxWidth().height(1.dp).background(C.line))
                }
            }
        }
        val next = data?.optJSONArray("next").objs()
        if (next.isNotEmpty()) {
            item { SectionTitle(L("بازی‌های پیش‌رو در این لیگ")) }
            item {
                Card {
                    next.forEachIndexed { k, m ->
                        Row(Modifier.fillMaxWidth().clickable { nav.match(m.optInt("id")) }.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Crest(m.optInt("hid"), 22.dp)
                            T(teamName(m.optInt("hid"), m.optString("home")) + " – " + teamName(m.optInt("aid"), m.optString("away")), 13, FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f))
                            Crest(m.optInt("aid"), 22.dp)
                            T(L("{0} پیش‌بینی", fa(m.optInt("n"))), 12, color = C.mute)
                        }
                        if (k < next.size - 1) Box(Modifier.fillMaxWidth().height(1.dp).background(C.line))
                    }
                }
            }
        }
        item { T(L("هفته از شنبه شروع می‌شود. نتیجه‌ی دقیق ۳ امتیاز و برنده‌ی درست ۱ امتیاز."), 12, color = C.faint, modifier = Modifier.padding(6.dp)) }
    }
    if (leaving) androidx.compose.material3.AlertDialog(onDismissRequest = { leaving = false }, containerColor = C.cardSolid,
        title = { T(L("خروج از لیگ؟"), 16, FontWeight.Black) },
        text = { T(L("امتیازهایت می‌ماند و هر وقت با کد دوباره بیایی، سر جایت برمی‌گردی."), 14) },
        confirmButton = { androidx.compose.material3.TextButton({ leaving = false; scope.launch { runCatching { Account.leave(id) }; nav.back() } }) { T(L("خروج"), 14, FontWeight.Bold, C.live) } },
        dismissButton = { androidx.compose.material3.TextButton({ leaving = false }) { T(L("انصراف"), 14, color = C.mute) } })
}
