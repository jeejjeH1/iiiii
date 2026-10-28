package app.goallive.live.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.goallive.live.L
import app.goallive.live.data.Account
import app.goallive.live.data.objs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

/*
 * Fantasy leagues: the same accounts as the prediction league. A league is for one competition (the fantasy game's
 * league); everyone playing that competition is one big table too. Points come from each player's finished gameweeks.
 */

/** The league names the fantasy game offers, by competition id. */
private fun compName(id: Int) = when (id) { 47 -> L("لیگ برتر انگلیس"); 42 -> L("لیگ قهرمانان اروپا"); 523 -> L("لیگ برتر خلیج فارس"); 87 -> L("لالیگا"); 55 -> L("سری آ"); 54 -> L("بوندس‌لیگا"); else -> "" }

private fun fantasyInvite(ctx: android.content.Context, name: String, code: String) {
    val t = L("بیا تو لیگ فانتزی «{0}» در گل‌لایو با هم رقابت کنیم!", name) + "\n" + L("کد لیگ: {0}", code) + "\n" + Account.inviteLink(code)
    runCatching { ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, t), L("دعوت دوستان"))) }
}

/** On the fantasy page: the leagues for this competition (or a way in when there is no account), and joining by invite. */
@Composable
internal fun FantasyLeaguesCard(lg: FLeague) {
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val signed = Account.signedIn
    var data by remember { mutableStateOf<JSONObject?>(null) }
    var failed by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf("") }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(signed, reload, lg.id) {
        if (!signed) return@LaunchedEffect
        runCatching { Account.fantasyLeagues(lg.id) }.onSuccess { data = it; failed = false }.onFailure { failed = true }
    }
    // an invite link with a fantasy code: join as soon as there is an account
    LaunchedEffect(Account.pendingCode, signed) {
        val c = Account.pendingCode?.takeIf { it.startsWith("F") } ?: return@LaunchedEffect
        if (!signed) return@LaunchedEffect
        Account.pendingCode = null
        runCatching { Account.fantasyJoin(c) }.onSuccess { reload++; nav.fantasyLeague(it.optInt("id"), it.optInt("comp")) }.onFailure { sheet = "join:$c" }
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Brush.linearGradient(listOf(C.gold.copy(alpha = 0.22f), Color.Transparent)))
        .border(1.dp, C.gold.copy(alpha = 0.35f), RoundedCornerShape(22.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Icon(Ic.trophy, null, Modifier.size(20.dp), tint = C.gold); Spacer(Modifier.width(8.dp))
            T(L("لیگ‌های فانتزی"), 16, FontWeight.Black, modifier = Modifier.weight(1f))
        }
        if (!signed) {
            T(L("وارد شو تا با دوستانت لیگ بسازی و در جدول هفته و فصل رقابت کنی."), 13, color = C.mute)
            Account.pendingCode?.takeIf { it.startsWith("F") }?.let { T(L("دعوت به لیگ با کد {0}؛ اول وارد شو", it), 13, FontWeight.Bold, C.gold) }
            BigButton(L("ورود با شماره یا گوگل")) { sheet = "phone" }
        } else {
            val d = data
            if (d == null) { if (failed) T(L("لیگ‌ها نیامد؛ اینترنت را بررسی کن"), 13, color = C.mute) else Skeleton(1) }
            else {
                val e = d.optJSONObject("everyone")
                LeagueRow(L("همه‌ی بازیکنان فانتزی {0}", compName(lg.id)), L("{0} بازیکن", fa(e?.optInt("players") ?: 0)), e?.optInt("rank")?.takeIf { it > 0 }, true) { nav.fantasyLeague(0, lg.id) }
                // only this competition's leagues: every league has its own tables and its own friends' leagues
                d.optJSONArray("leagues").objs().filter { it.optInt("comp") == lg.id }.forEach { l ->
                    val other = false
                    LeagueRow(l.optString("name"), listOfNotNull(L("{0} نفر", fa(l.optInt("members"))), l.optString("leader").takeIf { it.isNotEmpty() }?.let { L("صدرنشین: {0}", it) },
                        if (other) compName(l.optInt("comp")) else null).joinToString(" · "), l.optInt("rank").takeIf { it > 0 }) { nav.fantasyLeague(l.optInt("id"), l.optInt("comp")) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BigButton(L("ساختن لیگ"), Modifier.weight(1f)) { sheet = "new" }
                BigButton(L("پیوستن با کد"), Modifier.weight(1f), plain = true) { sheet = "join" }
            }
        }
    }
    when {
        sheet == "phone" -> PhoneSheet { sheet = ""; reload++ }
        sheet == "new" -> AskSheet(L("لیگ فانتزی تازه"), L("برای {0}؛ بعد کدش را برای دوستانت می‌فرستی", compName(lg.id)), L("مثلاً بچه‌های دانشگاه"), L("ساختن"),
            onDone = { val l = Account.fantasyCreate(it, lg.id); fantasyInvite(ctx, l.optString("name"), l.optString("code")); reload++ }) { sheet = "" }
        sheet.startsWith("join") -> AskSheet(L("پیوستن به لیگ فانتزی"), L("کد ۶ حرفی که دوستت فرستاده (با F شروع می‌شود)"), "F2AB3C", L("پیوستن"), sheet.substringAfter(':', ""), code = true,
            onDone = { val l = Account.fantasyJoin(it); reload++; nav.fantasyLeague(l.optInt("id"), l.optInt("comp")) }) { sheet = "" }
    }
}

/** A fantasy league's table (0: everyone playing [comp]): season points, or the last gameweek's. */
@Composable
fun FantasyLeagueTable(id: Int, comp: Int) { FantasyTheme(comp) { LeagueTableBody(id, comp) } }

@Composable
private fun LeagueTableBody(id: Int, comp: Int) {
    val ctx = LocalContext.current
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    var week by remember { mutableStateOf(false) }
    var data by remember { mutableStateOf<JSONObject?>(null) }
    var failed by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    LaunchedEffect(id, comp) {
        while (true) {
            runCatching { Account.fantasyTable(id, comp, null) }.onSuccess { data = it; failed = false }.onFailure { failed = data == null }
            delay(120_000)
        }
    }
    val info = data?.optJSONObject("league")
    val round = data?.optString("round")?.takeIf { it.isNotEmpty() && it != "null" }
    val rows = data?.optJSONArray("rows").objs().let { r -> if (week) r.sortedByDescending { it.optInt("last") } else r }
    val meId = Account.me?.id
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = BottomSpace), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Brush.linearGradient(listOf(C.gold.copy(alpha = 0.26f), Color.Transparent)))
                .border(1.dp, C.gold.copy(alpha = 0.35f), RoundedCornerShape(24.dp)).padding(18.dp)) {
                T(if (id == 0) L("همه‌ی بازیکنان فانتزی {0}", compName(comp)) else info?.optString("name") ?: "", 22, FontWeight.Black, maxLines = 2)
                if (id != 0 && info != null) {
                    T(L("{0} · {1} نفر · کد لیگ: {2}", compName(info.optInt("comp")), fa(info.optInt("members")), info.optString("code")), 13, color = C.mute, modifier = Modifier.padding(top = 4.dp))
                    Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill(L("دعوت دوستان"), PillKind.Accent, icon = Ic.share, onClick = { fantasyInvite(ctx, info.optString("name"), info.optString("code")) })
                        Pill(L("خروج از لیگ"), PillKind.Plain, onClick = { leaving = true })
                    }
                    Box(Modifier.padding(top = 8.dp)) { Pill(L("تیم من در این لیگ"), PillKind.Plain, onClick = { openFantasy(nav, comp) }) }
                } else T(L("هر کس تیم فانتزی‌اش را با حساب ثبت کرده باشد"), 13, color = C.mute, modifier = Modifier.padding(top = 4.dp))
            }
        }
        item {
            Seg(listOf("all" to L("کل فصل"), "week" to (round?.let { if (it.all(Char::isDigit)) L("هفته {0}", fa(it)) else it } ?: L("آخرین هفته"))), if (week) "week" else "all") { week = it == "week" }
        }
        if (data == null) item { if (failed) EmptyView(L("جدول نیامد"), null, L("اینترنت را بررسی کن")) else Skeleton(4) }
        else if (rows.isEmpty()) item { EmptyView(L("هنوز کسی امتیاز نگرفته"), null, L("امتیازها بعد از پایان هر هفته‌ی بازی‌ها اینجا می‌آید."), L("تیم فانتزی من")) { openFantasy(nav, comp) } }
        else item {
            Card {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                    T("#", 12, color = C.faint, modifier = Modifier.width(30.dp))
                    T(L("بازیکن"), 12, color = C.faint, modifier = Modifier.weight(1f))
                    T(L("هفته"), 12, color = C.faint, modifier = Modifier.width(52.dp), align = TextAlign.Center)
                    T(L("کل"), 12, color = C.faint, modifier = Modifier.width(56.dp), align = TextAlign.Center)
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
                        T(r.optString("name") + if (me) " " + L("(تو)") else "", 14, if (me) FontWeight.Black else FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f))
                        T(fa(r.optInt("last")), 14, if (week) FontWeight.Black else FontWeight.Normal, if (week && k == 0) C.gold else C.mute, modifier = Modifier.width(52.dp), align = TextAlign.Center)
                        T(fa(r.optInt("total")), 16, if (week) FontWeight.Normal else FontWeight.Black, if (!week && k == 0) C.gold else C.ink, modifier = Modifier.width(56.dp), align = TextAlign.Center)
                    }
                    if (k < rows.size - 1) Box(Modifier.fillMaxWidth().height(1.dp).background(C.line))
                }
            }
        }
        item { T(L("امتیاز هر هفته بعد از پایان بازی‌هایش و با باز کردن بخش فانتزی ثبت می‌شود."), 12, color = C.faint, modifier = Modifier.padding(6.dp)) }
    }
    if (leaving) androidx.compose.material3.AlertDialog(onDismissRequest = { leaving = false }, containerColor = C.cardSolid,
        title = { T(L("خروج از لیگ؟"), 16, FontWeight.Black) },
        text = { T(L("دیگر در جدول این لیگ نیستی؛ با کدش دوباره می‌توانی بیایی."), 14) },
        confirmButton = { androidx.compose.material3.TextButton({ leaving = false; scope.launch { runCatching { Account.fantasyLeave(id) }; nav.back() } }) { T(L("خروج"), 14, FontWeight.Bold, C.live) } },
        dismissButton = { androidx.compose.material3.TextButton({ leaving = false }) { T(L("انصراف"), 14, color = C.mute) } })
}
