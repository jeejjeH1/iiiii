package app.goallive.live.ui

import app.goallive.live.L

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.goallive.live.data.Clubs
import app.goallive.live.data.Predictions
import app.goallive.live.data.Store

/** One shortcut: a story circle on home and a row on the "بیشتر" tab. */
private class Shortcut(val key: String, val title: String, val sub: String, val icon: ImageVector?, val mark: String?, val color: Color, val fresh: Boolean)

@Composable
private fun shortcuts(): List<Shortcut> {
    val accent = LocalAccent.current
    val p = Store.prefs()
    val today = dayKey(startOfDay(0))
    val answered = if (p.getString("quizDay", "") == today) (p.getString("quizAns", "") ?: "").split(',').count { it.isNotEmpty() } else 0
    val fantasy = fantasySub()
    return listOfNotNull(
        Shortcut("tv", L("پخش زنده"), L("نماوا، آپارات اسپورت و تلوبیون"), Ic.tv, null, C.live, true),
        Shortcut("ai", L("دستیار"), L("سؤال فوتبالی بپرس، با نوشتن یا با صدا"), Ic.mic, null, C.home, !Store.tipSeen("assistant")),
        Shortcut("predict", L("پیش‌بینی"), if (Predictions.all.isEmpty()) L("نتیجه بازی‌ها را حدس بزن و امتیاز بگیر") else L("{0} امتیاز · {1}", fa(Predictions.total), predictLevel(Predictions.total).first),
            null, "🔮", C.gold, Predictions.all.isEmpty()),
        Shortcut("fantasy", L("فانتزی"), fantasy, null, "⚽", C.win, !fantasy.startsWith(L("مجموع"))),
        Shortcut("board", L("تخته تاکتیک"), L("ترکیب بچین، فلش بکش و بفرست"), null, "📋", C.home, false),
        Shortcut("quiz", L("کوئیز امروز"), if (answered >= 6) L("انجام شد · فردا دوباره") else if (answered > 0) L("ادامه بده") else L("۶ سؤال تازه"), null, "?", accent, answered < 6),
        Shortcut("calendar", L("تقویم"), Store.myTeam?.let { L("بازی‌های ") + (Clubs.get(it.id)?.fa ?: it.name) } ?: L("بازی‌های تیم‌های دنبال‌شده"), Ic.cal, null, C.home, false),
        Store.myTeam?.let { Shortcut("my", Clubs.get(it.id)?.fa ?: it.name, L("صفحه تیم من"), null, null, accentFor(Clubs.get(it.id), it.color), false) },
    )
}

private fun go(key: String, nav: Nav, tv: () -> Unit) = when (key) {
    "tv" -> tv()
    "fantasy" -> nav.fantasy()
    "predict" -> nav.predictions()
    "board" -> nav.board()
    "quiz" -> nav.quiz()
    "calendar" -> nav.calendar()
    "ai" -> { Store.markTip("assistant"); nav.assistant() }
    "my" -> Store.myTeam?.let { nav.team(it.id) } ?: Unit
    else -> Unit
}

/** Home: the shortcuts as a row of story circles; a bright ring means something new is waiting there. */
@Composable
fun StoryRow() {
    val nav = LocalNav.current
    val accent = LocalAccent.current
    var tv by remember { mutableStateOf(false) }
    val list = shortcuts()
    LazyRow(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp), horizontalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(horizontal = 2.dp)) {
        items(list.size) { i ->
            val s = list[i]
            Column(
                Modifier.width(70.dp).clip(RoundedCornerShape(16.dp)).clickable { go(s.key, nav) { tv = true } }
                    .semantics(mergeDescendants = true) { role = Role.Button; contentDescription = L("{0}، {1}", s.title, s.sub) }.padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val ring = if (s.fresh) Brush.sweepGradient(listOf(s.color, accent, C.live, s.color)) else Brush.linearGradient(listOf(C.line2, C.line2))
                Box(Modifier.size(64.dp).clip(CircleShape).background(ring).padding(if (s.fresh) 2.5.dp else 1.5.dp).clip(CircleShape).background(C.bg).padding(3.dp)
                    .clip(CircleShape).background(Brush.linearGradient(listOf(s.color, darken(s.color, 0.55f)))), contentAlignment = Alignment.Center) {
                    when {
                        s.key == "my" -> Crest(Store.myTeam?.id ?: 0, 36.dp, large = true)
                        s.icon != null -> Icon(s.icon, null, Modifier.size(26.dp), tint = inkOn(s.color))
                        else -> T(s.mark ?: "", 24, FontWeight.Black, inkOn(s.color))
                    }
                    if (s.key == "tv") Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 3.dp).clip(RoundedCornerShape(4.dp)).background(Color.White)
                        .padding(horizontal = 4.dp)) { T("LIVE", 12, FontWeight.Black, C.live) }
                }
                Spacer(Modifier.height(6.dp))
                T(s.title, 12, FontWeight.Bold, if (s.fresh) C.ink else C.mute, maxLines = 1, align = TextAlign.Center)
            }
        }
    }
    if (tv) StationsSheet { tv = false }
}

/** The live-streaming services, picked from a sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationsSheet(onClose: () -> Unit) {
    val ctx = LocalContext.current
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = C.cardSolid,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LiveDot(); T(L("پخش زنده"), 18, FontWeight.Black)
            }
            T(L("برنامه خود سرویس باز می‌شود (اگر نصب نباشد، سایتش در مرورگر)؛ با حساب خودتان وارد شوید."), 13, color = C.mute,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
            Card {
                STATIONS.forEachIndexed { i, st ->
                    ListRow(first = i == 0, onClick = { onClose(); openStation(ctx, st) }) {
                        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(if (st.key == "namava") C.live else C.card2), contentAlignment = Alignment.Center) {
                            Icon(Ic.tv, null, Modifier.size(22.dp), tint = if (st.key == "namava") Color.White else C.ink)
                        }
                        Column(Modifier.weight(1f)) { T(st.name, 15, FontWeight.Bold); T(st.sub, 12, color = C.mute, maxLines = 1) }
                        Icon(Ic.chev, null, Modifier.size(18.dp), tint = C.mute)
                    }
                }
            }
        }
    }
}

/** "بیشتر" tab: everything that is not a list of matches. */
@Composable
fun MoreScreen() {
    val nav = LocalNav.current
    var tv by remember { mutableStateOf(false) }
    var widgetHelp by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = BottomSpace)) {
        item {
            T(L("بیشتر"), 16, FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp))
            Card {
                shortcuts().forEachIndexed { i, s ->
                    ListRow(first = i == 0, onClick = { go(s.key, nav) { tv = true } }) {
                        Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(Brush.linearGradient(listOf(s.color, darken(s.color, 0.55f)))),
                            contentAlignment = Alignment.Center) {
                            when {
                                s.key == "my" -> Crest(Store.myTeam?.id ?: 0, 28.dp)
                                s.icon != null -> Icon(s.icon, null, Modifier.size(22.dp), tint = inkOn(s.color))
                                else -> T(s.mark ?: "", 20, FontWeight.Black, inkOn(s.color))
                            }
                        }
                        Column(Modifier.weight(1f)) { T(s.title, 15, FontWeight.Bold); T(s.sub, 12, color = C.mute, maxLines = 1) }
                        if (s.fresh) Box(Modifier.size(8.dp).clip(CircleShape).background(C.live))
                        Icon(Ic.chev, null, Modifier.size(18.dp), tint = C.mute)
                    }
                }
            }
            SectionTitle(L("اپلیکیشن"))
            Card {
                MoreRow(Ic.search, L("جستجو"), L("تیم، بازیکن یا لیگ"), true) { nav.tab("search") }
                MoreRow(Ic.trophy, L("همه لیگ‌ها"), L("جدول، برنامه و آمار")) { nav.tab("leagues") }
                MoreRow(Ic.grid, L("ویجت‌های صفحه اصلی"), L("نتیجه زنده روی صفحه گوشی")) { widgetHelp = true }
                MoreRow(Ic.sliders, L("تنظیمات"), L("ظاهر، اعلان‌ها، گزارش صوتی و نام فارسی تیم‌ها")) { nav.tab("settings") }
            }
        }
    }
    if (tv) StationsSheet { tv = false }
    if (widgetHelp) AlertDialog(
        onDismissRequest = { widgetHelp = false }, containerColor = C.cardSolid,
        title = { T(L("ویجت‌های صفحه اصلی"), 16, FontWeight.ExtraBold) },
        text = {
            T(L("روی جای خالی صفحه اصلی گوشی انگشت را نگه دارید، «ویجت‌ها» را بزنید و «گل‌لایو» را پیدا کنید. سه ویجت دارد: تیم من (بزرگ)، تیم من (کوچک) و بازی‌های امروز."), 14)
        },
        confirmButton = { TextButton(onClick = { widgetHelp = false }) { T(L("باشه"), 14, FontWeight.Bold, LocalAccent.current) } },
    )
}

@Composable
private fun MoreRow(icon: ImageVector, title: String, sub: String, first: Boolean = false, onClick: () -> Unit) {
    ListRow(first = first, onClick = onClick) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(C.card2), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(22.dp), tint = C.ink)
        }
        Column(Modifier.weight(1f)) { T(title, 15, FontWeight.Bold); T(sub, 12, color = C.mute, maxLines = 1) }
        Icon(Ic.chev, null, Modifier.size(18.dp), tint = C.mute)
    }
}
