package app.goallive.live.ui

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
import app.goallive.live.data.TEAMS_FA
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

/* ---------- understanding the question ---------- */

/** Arabic letters, Persian digits and half-spaces folded so "پرسپولیس" matches however it was typed. */
private fun norm(s: String) = buildString {
    for (ch in s.lowercase()) append(
        when (ch) {
            'ي', 'ى' -> 'ی'; 'ك' -> 'ک'; 'ة' -> 'ه'; 'أ', 'إ', 'آ' -> 'ا'
            '‌', '‏', '‎', '‌' -> ' '
            in '۰'..'۹' -> '0' + (ch - '۰')
            in '٠'..'٩' -> '0' + (ch - '٠')
            else -> ch
        },
    )
}.replace(Regex("[؟?!.,،:;«»\"()]"), " ").replace(Regex("\\s+"), " ").trim()

/** Whole-word match, so a short name like "کین" is not found inside another word. */
private fun word(q: String, w: String) = " ${norm(q)} ".contains(" ${norm(w)} ")

private fun has(q: String, vararg words: String) = words.any { q.contains(norm(it)) }

/** Famous players in Persian, as FotMob spells them. */
private val PLAYERS = mapOf(
    "طارمی" to "Mehdi Taremi", "آزمون" to "Sardar Azmoun", "جهانبخش" to "Alireza Jahanbakhsh", "بیرانوند" to "Alireza Beiranvand",
    "قلی زاده" to "Ali Gholizadeh", "قلیزاده" to "Ali Gholizadeh", "انصاری فرد" to "Karim Ansarifard", "نوراللهی" to "Ahmad Nourollahi",
    "حسینی" to "Hossein Hosseini", "کنعانی" to "Hossein Kanaanizadegan", "محمدی" to "Milad Mohammadi", "عزت اللهی" to "Saeid Ezatolahi",
    "مسی" to "Lionel Messi", "رونالدو" to "Cristiano Ronaldo", "صلاح" to "Mohamed Salah", "هالند" to "Erling Haaland",
    "امباپه" to "Kylian Mbappé", "یامال" to "Lamine Yamal", "وینیسیوس" to "Vinicius Junior", "بلینگهام" to "Jude Bellingham",
    "کین" to "Harry Kane", "لواندوفسکی" to "Robert Lewandowski", "رافینیا" to "Raphinha", "پدری" to "Pedri", "ساکا" to "Bukayo Saka",
    "پالمر" to "Cole Palmer", "اودگارد" to "Martin Ødegaard", "دی بروینه" to "Kevin De Bruyne", "رودری" to "Rodri", "فودن" to "Phil Foden",
    "لائوتارو" to "Lautaro Martínez", "اوسیمن" to "Victor Osimhen", "نیمار" to "Neymar", "بنزما" to "Karim Benzema", "مودریچ" to "Luka Modrić",
    "والورده" to "Federico Valverde", "رودریگو" to "Rodrygo", "گریزمان" to "Antoine Griezmann", "آلوارز" to "Julián Álvarez",
    "موسیالا" to "Jamal Musiala", "ویرتز" to "Florian Wirtz", "لیائو" to "Rafael Leão", "ولاهوویچ" to "Dušan Vlahović",
    "ایساک" to "Alexander Isak", "دمبله" to "Ousmane Dembélé", "حکیمی" to "Achraf Hakimi", "فن دایک" to "Virgil van Dijk",
    "آلیسون" to "Alisson", "کورتوا" to "Thibaut Courtois", "سون" to "Son Heung-Min", "برونو" to "Bruno Fernandes", "راشفورد" to "Marcus Rashford",
)

private val LEAGUES = listOf(
    listOf("لیگ برتر ایران", "خلیج فارس", "لیگ ایران", "لیگ برتر خلیج") to 523,
    listOf("لیگ قهرمانان", "چمپیونز", "champions") to 42,
    listOf("لیگ اروپا", "europa") to 73,
    listOf("لالیگا", "لیگ اسپانیا", "اسپانیا", "laliga") to 87,
    listOf("سری آ", "سریا", "لیگ ایتالیا", "ایتالیا", "serie a") to 55,
    listOf("بوندسلیگا", "بوندس لیگا", "لیگ آلمان", "آلمان", "bundesliga") to 54,
    listOf("لوشامپیونه", "لیگ 1", "لیگ فرانسه", "فرانسه", "ligue 1") to 53,
    listOf("لیگ برتر انگلیس", "لیگ انگلیس", "انگلیس", "پریمیر", "premier league") to 47,
    listOf("لیگ برتر") to 47,
)

/** A team named in the question: club themes first, then the big Persian name list (resolved through search). */
private suspend fun findTeam(q: String): Pair<Int, String>? {
    // longest names first, so "استقلال خوزستان" wins over "استقلال"
    Clubs.ids.mapNotNull { id -> Clubs.get(id)?.let { id to it.fa } }.sortedByDescending { it.second.length }
        .firstOrNull { (_, fa) -> word(q, fa) }?.let { return it.first to (Clubs.get(it.first)?.fa ?: "") }
    val en = TEAMS_FA.entries.sortedByDescending { it.value.length }.firstOrNull { word(q, it.value) }?.key
    val latin = Regex("[A-Za-z][A-Za-z .'-]{2,}").find(q)?.value?.trim()
    val term = en ?: latin ?: return null
    val t = runCatching { search(term).teams.firstOrNull() }.getOrNull() ?: return null
    val id = t.s("id")?.toIntOrNull() ?: return null
    return id to (faTeamName(id, t.s("name") ?: term) ?: t.s("name") ?: term)
}

/** A player named in Persian from the list, or (with [latin]) any player written in English. */
private suspend fun findPlayer(q: String, latin: Boolean = false): JSONObject? {
    val en = PLAYERS.entries.sortedByDescending { it.key.length }.firstOrNull { word(q, it.key) }?.value
        ?: (if (latin) Regex("[A-Za-z][A-Za-z .'-]{2,}").find(q)?.value?.trim() else null) ?: return null
    return runCatching { search(en).players.firstOrNull() }.getOrNull()
}

private fun findLeague(q: String): Int? = LEAGUES.firstOrNull { (words, _) -> has(q, *words.toTypedArray()) }?.second

/* ---------- answers ---------- */

private suspend fun answer(raw: String): Msg {
    val q = norm(raw)
    suspend fun today() = parseMatchList(Api.fm("matches", Api.matchesParams(dayKey(System.currentTimeMillis())), 20_000))
    val bigLeagues = setOf(42, 47, 87, 55, 54, 53, 73, 523, 77, 50)

    suspend fun playerAnswer(p: JSONObject): Msg? {
        val id = p.s("id")?.toIntOrNull() ?: return null
        val d = Api.fm("playerData", mapOf("id" to id), 600_000)
        val name = d.s("name") ?: p.s("name") ?: ""
        val ml = d.o("mainLeague")
        val st = ml.a("stats").objs().associate { (it.s("title") ?: "") to it.opt("value") }
        val goals = st["Goals"]; val assists = st["Assists"]; val games = st["Matches"]; val rating = (st["Rating"] as? Number)?.toDouble()
        val team = d.o("primaryTeam")
        val text = if (ml == null) "$name: آماری برای این فصل پیدا نشد."
        else buildString {
            append("$name در ${ml.s("leagueName") ?: "لیگ"} (فصل ${fa(ml.s("season") ?: "")}): ")
            append("${fa(goals ?: 0)} گل و ${fa(assists ?: 0)} پاس گل در ${fa(games ?: 0)} بازی")
            if (rating != null && rating > 0) append("، میانگین نمره ${dec(rating, 2)}")
            append(".")
            team?.s("teamName")?.let { append(" تیم فعلی: ${teamName(team.i("teamId") ?: 0, it)}.") }
        }
        return Msg(false, text, links = listOf(Link("صفحه $name") { it.player(id) }))
    }
    findPlayer(raw)?.let { p -> playerAnswer(p)?.let { return it } }

    val team = findTeam(raw)
    // an English name that is not a team: maybe a player ("Salah")
    if (team == null) findPlayer(raw, latin = true)?.let { p -> playerAnswer(p)?.let { return it } }
    if (team == null) {
        // what is on now / today
        if (has(q, "زنده", "الان") && !has(q, "گل زده", "چند گل")) {
            val live = today().flatMap { b -> b.matches.filter { it.live }.map { it to b.primaryId } }
                .sortedByDescending { (_, l) -> if (l in bigLeagues) 1 else 0 }.map { it.first }
            return if (live.isEmpty()) Msg(false, "الان هیچ بازی زنده‌ای در جریان نیست.")
            else Msg(false, "${fa(live.size)} بازی الان زنده است؛ مهم‌ترین‌ها:", live.take(6))
        }
        val lg = findLeague(q)
        if (lg != null) {
            val d = Api.fm("leagues", mapOf("id" to lg, "ccode3" to "IRN"), 600_000)
            val lname = d.o("details").s("name") ?: ""
            if (has(q, "آقای گل", "گلزن", "بهترین گلزن", "گل زن")) {
                val top = d.o("stats").a("players").objs().firstOrNull { it.s("header") == "Top scorer" }?.a("topThree").objs()
                if (top.isNullOrEmpty()) return Msg(false, "آمار گلزنان $lname هنوز منتشر نشده.")
                return Msg(false, "گلزنان برتر $lname:\n" + top.mapIndexed { k, p ->
                    "${fa(k + 1)}. ${p.s("name")} (${teamName(p.i("teamId") ?: 0, p.s("teamName") ?: "")}) ${fa(p.i("value") ?: 0)} گل"
                }.joinToString("\n"), links = listOf(Link("صفحه $lname") { it.league(lg) }))
            }
            val rows = d.a("table").objs().firstOrNull()?.path("data.table").a("all").objs()
            if (rows.isEmpty()) return Msg(false, "جدول $lname در دسترس نیست.", links = listOf(Link("صفحه $lname") { it.league(lg) }))
            return Msg(false, "صدر جدول $lname:\n" + rows.take(5).joinToString("\n") { r ->
                "${fa(r.i("idx") ?: 0)}. ${teamName(r.i("id") ?: 0, r.s("name") ?: "")}: ${fa(r.i("pts") ?: 0)} امتیاز از ${fa(r.i("played") ?: 0)} بازی"
            }, links = listOf(Link("جدول کامل") { it.league(lg) }))
        }
        if (has(q, "امروز", "بازی ها", "بازیهای")) {
            val list = today().filter { it.primaryId in bigLeagues }.flatMap { it.matches }.sortedBy { it.time }
            return if (list.isEmpty()) Msg(false, "امروز در لیگ‌های مهم بازی‌ای نیست.") else Msg(false, "بازی‌های مهم امروز:", list.take(8))
        }
        return Msg(false, "متوجه نشدم 🤔 نام یک تیم، بازیکن یا لیگ را بنویسید. مثلاً «بازی بعدی پرسپولیس کی است؟» یا «طارمی امسال چند گل زده؟»")
    }

    val (id, fname) = team
    val d = Api.fm("teams", mapOf("id" to id, "ccode3" to "IRN"), 300_000)
    val ov = d.o("overview")
    val links = listOf(Link("صفحه $fname") { it.team(id) })
    fun matchOf(key: String) = ov.o(key)?.let { parseMatch(it) }
    val rows = d.a("table").objs().flatMap { t -> listOfNotNull(t.path("data.table")) + t.o("data").a("tables").objs().mapNotNull { it.o("table") } }
    val table = rows.firstOrNull { tb -> tb.a("all").objs().any { it.i("id") == id } }
    val row = table?.a("all").objs().firstOrNull { it.i("id") == id }
    val leagueName = d.a("table").objs().firstOrNull()?.o("data")?.s("leagueName") ?: "لیگ"
    val position = row?.let { "$fname در $leagueName ${fa(it.i("idx") ?: 0)}م است با ${fa(it.i("pts") ?: 0)} امتیاز از ${fa(it.i("played") ?: 0)} بازی (${fa(it.i("wins") ?: 0)} برد، ${fa(it.i("draws") ?: 0)} مساوی، ${fa(it.i("losses") ?: 0)} باخت)." }

    fun describe(m: MatchItem): String {
        val opp = if (m.home.id == id) teamName(m.away.id, m.away.name) else teamName(m.home.id, m.home.name)
        val where = if (m.home.id == id) "در خانه" else "در خانه حریف"
        return when {
            m.live -> "$fname همین الان با $opp بازی دارد: ${fa(m.home.score ?: 0)}-${fa(m.away.score ?: 0)}، ${statusText(m)}."
            m.finished -> {
                val us = if (m.home.id == id) m.home.score ?: 0 else m.away.score ?: 0
                val them = if (m.home.id == id) m.away.score ?: 0 else m.home.score ?: 0
                val r = when { us > them -> "برد"; us < them -> "باخت"; else -> "مساوی کرد" }
                "$fname در آخرین بازی $where مقابل $opp ${fa(us)}-${fa(them)} $r (${persianDate(m.time, "EEEE d MMMM")})."
            }
            else -> "بازی بعدی $fname مقابل $opp است، $where، ${persianDate(m.time, "EEEE d MMMM")} ساعت ${fa(timeOf(m.time))}" +
                (if (m.leagueName.isNotEmpty()) " در ${m.leagueName}" else "") + "."
        }
    }

    return when {
        has(q, "چندم", "رتبه", "جدول", "امتیاز") -> Msg(false, position ?: "$fname در جدول لیگی پیدا نشد.", links = links)
        has(q, "بعدی", "بعد", "کی بازی", "چه ساعتی", "کی بازی داره", "کی بازی دارد") ->
            matchOf("nextMatch")?.let { Msg(false, describe(it), listOf(it), links) } ?: Msg(false, "بازی بعدی $fname هنوز اعلام نشده.", links = links)
        has(q, "نتیجه", "قبلی", "آخرین", "دیشب", "دیروز", "چند چند") ->
            matchOf("lastMatch")?.let { Msg(false, describe(it), listOf(it), links) } ?: Msg(false, "بازی اخیری برای $fname پیدا نشد.", links = links)
        has(q, "فرم", "وضعیت", "حال و روز") -> {
            val form = ov.a("teamForm").objs().takeLast(5).mapNotNull { it.s("resultString") }
            val seq = form.joinToString("، ") { when (it) { "W" -> "برد"; "L" -> "باخت"; else -> "مساوی" } }
            Msg(false, if (form.isEmpty()) "فرم اخیر $fname در دسترس نیست." else "پنج بازی اخیر $fname (از قدیم به جدید): $seq.", links = links)
        }
        has(q, "گلزن", "آقای گل", "بهترین بازیکن", "ستاره") -> {
            val p = d.o("stats").a("players").objs().firstOrNull { it.s("header") == "Top scorer" }?.o("participant")
            if (p == null) Msg(false, "آمار گلزنان $fname در دسترس نیست.", links = links)
            else Msg(false, "بهترین گلزن $fname این فصل ${p.s("name")} است با ${fa(p.i("value") ?: 0)} گل.",
                links = listOf(Link("صفحه ${p.s("name")}") { it.player(p.i("id") ?: 0) }) + links)
        }
        has(q, "مربی", "سرمربی") -> {
            val coach = d.o("squad").a("squad").objs().firstOrNull { it.s("title") == "coach" }?.a("members").objs()?.firstOrNull()?.s("name")
            Msg(false, coach?.let { "سرمربی $fname: $it" } ?: "نام سرمربی $fname پیدا نشد.", links = links)
        }
        else -> {
            // a short overview: where they stand, last result, next match
            val last = matchOf("lastMatch"); val next = matchOf("nextMatch")
            Msg(false, listOfNotNull(position, last?.let(::describe), next?.let(::describe)).joinToString("\n").ifEmpty { "اطلاعاتی از $fname پیدا نشد." },
                listOfNotNull(next ?: last), links)
        }
    }
}

private val EXAMPLES = listOf(
    "بازی بعدی پرسپولیس کی است؟", "طارمی امسال چند گل زده؟", "استقلال چندم است؟", "آقای گل لیگ برتر انگلیس",
    "الان چه بازی‌هایی زنده است؟", "نتیجه آخرین بازی رئال مادرید", "فرم بارسلونا", "صدر جدول لالیگا",
)

/** "دستیار گل‌لایو": ask in Persian (typed or spoken), get the answer from live data, hear it read out if you like. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AssistantScreen() {
    val ctx = LocalContext.current
    val nav = LocalNav.current
    val accent = LocalAccent.current
    val scope = rememberCoroutineScope()
    val msgs = remember { mutableStateListOf(Msg(false, "سلام! من دستیار گل‌لایو هستم ⚽\nدرباره تیم‌ها، بازیکن‌ها، جدول و بازی‌ها به فارسی بپرسید؛ با نوشتن یا با صدا.")) }
    var text by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var speak by rememberSaveable { mutableStateOf(false) }
    val speaker = remember { lazy { Speaker(ctx, CoroutineScope(SupervisorJob() + Dispatchers.IO)) } }
    DisposableEffect(Unit) { onDispose { if (speaker.isInitialized()) speaker.value.close() } }
    val list = rememberLazyListState()

    fun ask(q: String) {
        val clean = q.trim()
        if (clean.isEmpty() || busy) return
        msgs += Msg(true, clean)
        text = ""
        busy = true
        scope.launch {
            val a = runCatching { answer(clean) }.getOrElse { Msg(false, "اتصال برقرار نشد؛ دوباره امتحان کنید.") }
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
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fa-IR")
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "سؤالتان را بپرسید")
        runCatching { voice.launch(i) }.onFailure { msgs += Msg(false, "تشخیص صدا روی این گوشی فعال نیست؛ لطفاً سؤال را بنویسید.") }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            T("🤖 دستیار گل‌لایو", 17, FontWeight.Black, modifier = Modifier.weight(1f))
            Pill(if (speak) "پاسخ با صدا: روشن" else "پاسخ با صدا: خاموش", if (speak) PillKind.Accent else PillKind.Plain, onClick = { speak = !speak }, icon = Ic.mic)
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(msgs) { m -> Bubble(m, nav) }
            if (busy) item { Row(Modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) { repeat(3) { LiveDot(C.mute, 7.dp) } } }
            if (msgs.size == 1) item {
                T("مثلاً بپرسید:", 13, FontWeight.Bold, C.mute, Modifier.padding(top = 6.dp, bottom = 6.dp))
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
                if (text.isEmpty()) T("سؤالتان را بنویسید…", 14, color = C.faint)
                BasicTextField(text, { text = it }, Modifier.fillMaxWidth(), textStyle = vz(15.sp).copy(color = C.ink), cursorBrush = SolidColor(accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { ask(text) }), maxLines = 3)
            }
            val send = text.isNotBlank()
            Box(Modifier.size(50.dp).clip(CircleShape).background(accent).clickable { if (send) ask(text) else listen() }, contentAlignment = Alignment.Center) {
                Icon(if (send) Ic.chev else Ic.mic, if (send) "فرستادن" else "پرسیدن با صدا", Modifier.size(24.dp), tint = LocalAccentInk.current)
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
