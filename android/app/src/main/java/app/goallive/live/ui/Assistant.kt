package app.goallive.live.ui

import app.goallive.live.L

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.goallive.live.data.Api
import app.goallive.live.data.Clubs
import app.goallive.live.data.MatchItem
import app.goallive.live.data.a
import app.goallive.live.data.d
import app.goallive.live.data.faTeamName
import app.goallive.live.data.i
import app.goallive.live.data.o
import app.goallive.live.data.objs
import app.goallive.live.data.parseMatch
import app.goallive.live.data.parseMatchList
import app.goallive.live.data.path
import app.goallive.live.data.s
import app.goallive.live.notify.Speaker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Something the assistant can open: a match, team, player or league. */
class Link(val label: String, val go: (Nav) -> Unit)

/** One chat bubble. */
class Msg(val mine: Boolean, val text: String, val matches: List<MatchItem> = emptyList(), val links: List<Link> = emptyList())

/** "دستیار گل‌لایو": ask in Persian (typed or spoken), get the answer from live data, hear it read out if you like. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AssistantScreen() {
    val ctx = LocalContext.current
    val nav = LocalNav.current
    val accent = LocalAccent.current
    val scope = rememberCoroutineScope()
    val msgs = remember { mutableStateListOf(Msg(false, L("سلام! من دستیار گل‌لایو هستم ⚽\nدرباره تیم‌ها، بازیکن‌ها، لیگ‌ها و بازی‌ها به فارسی بپرسید؛ با نوشتن یا با صدا. برای دیدن همه کارهایی که بلدم بنویسید «راهنما»."))) }
    var text by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var speak by rememberSaveable { mutableStateOf(false) }
    val speaker = remember { lazy { Speaker(ctx, CoroutineScope(SupervisorJob() + Dispatchers.IO)) } }
    DisposableEffect(Unit) { onDispose { if (speaker.isInitialized()) speaker.value.close() } }
    val list = rememberLazyListState()
    // remembers the team / player being talked about, so "بازی بعدیش کی است؟" works as a follow-up
    val brain = remember { Brain() }

    fun ask(q: String) {
        val clean = q.trim()
        if (clean.isEmpty() || busy) return
        msgs += Msg(true, clean)
        text = ""
        busy = true
        scope.launch {
            val a = runCatching { brain.answer(clean) }.getOrElse { Msg(false, L("اتصال برقرار نشد؛ دوباره امتحان کنید.")) }
            msgs += a
            busy = false
            if (speak) speaker.value.say(a.text)
        }
    }
    LaunchedEffect(msgs.size, busy) { list.animateScrollToItem(msgs.size + if (busy) 1 else 0) }

    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { speak = true; ask(it) }
    }
    val listen = {
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (app.goallive.live.Lang.en) "en-US" else "fa-IR")
            .putExtra(RecognizerIntent.EXTRA_PROMPT, L("سؤالتان را بپرسید"))
        runCatching { voice.launch(i) }.onFailure { msgs += Msg(false, L("تشخیص صدا روی این گوشی فعال نیست؛ لطفاً سؤال را بنویسید.")) }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            T(L("🤖 دستیار گل‌لایو"), 17, FontWeight.Black, modifier = Modifier.weight(1f))
            Pill(if (speak) L("پاسخ با صدا: روشن") else L("پاسخ با صدا: خاموش"), if (speak) PillKind.Accent else PillKind.Plain, onClick = { speak = !speak }, icon = Ic.mic)
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(msgs) { m -> Bubble(m, nav) }
            if (busy) item { Row(Modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) { repeat(3) { LiveDot(C.mute, 7.dp) } } }
            if (msgs.size == 1) item {
                T(L("مثلاً بپرسید:"), 13, FontWeight.Bold, C.mute, Modifier.padding(top = 6.dp, bottom = 6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    EXAMPLES.forEach { e -> Pill(e, onClick = { ask(e) }) }
                }
            }
        }
        // input bar (this page hides the tab bar, so it sits at the bottom and rises with the keyboard)
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f).heightIn(min = 50.dp).clip(RoundedCornerShape(18.dp)).background(C.card).border(1.dp, C.line2, RoundedCornerShape(18.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp), contentAlignment = Alignment.CenterStart) {
                if (text.isEmpty()) T(L("سؤالتان را بنویسید…"), 14, color = C.faint)
                BasicTextField(text, { text = it }, Modifier.fillMaxWidth(), textStyle = vz(15.sp).copy(color = C.ink), cursorBrush = SolidColor(accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { ask(text) }), maxLines = 3)
            }
            val send = text.isNotBlank()
            Box(Modifier.size(50.dp).clip(CircleShape).background(accent).clickable { if (send) ask(text) else listen() }, contentAlignment = Alignment.Center) {
                Icon(if (send) Ic.chev else Ic.mic, if (send) L("فرستادن") else L("پرسیدن با صدا"), Modifier.size(24.dp), tint = LocalAccentInk.current)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Bubble(m: Msg, nav: Nav) {
    val accent = LocalAccent.current
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (m.mine) Alignment.Start else Alignment.End) {
        val shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = if (m.mine) 4.dp else 18.dp, bottomEnd = if (m.mine) 18.dp else 4.dp)
        Box(Modifier.widthIn(max = 320.dp).clip(shape).background(if (m.mine) accent else C.card).then(if (m.mine) Modifier else Modifier.border(1.dp, C.line, shape))
            .padding(horizontal = 14.dp, vertical = 10.dp)) {
            T(m.text, 14, if (m.mine) FontWeight.Bold else FontWeight.Normal, if (m.mine) LocalAccentInk.current else C.ink)
        }
        if (m.matches.isNotEmpty()) Card(Modifier.padding(top = 6.dp)) {
            m.matches.forEachIndexed { k, x -> MatchRow(x, { nav.match(x.id) }, showDate = true, sub = x.leagueName.ifEmpty { null }, star = false, first = k == 0) }
        }
        if (m.links.isNotEmpty()) FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            m.links.forEach { l -> Pill(l.label, PillKind.Accent, onClick = { l.go(nav) }, icon = Ic.chev) }
        }
    }
}
