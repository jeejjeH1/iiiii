package app.goallive.live.ui

import app.goallive.live.L

import android.content.Intent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.goallive.live.data.Api
import app.goallive.live.data.Clubs
import app.goallive.live.data.Store
import app.goallive.live.data.parseMatchList
import kotlin.random.Random

class Question(val text: String, val options: List<String>, val answer: Int, val crest: Int = 0, val note: String = "")

/**
 * Today's quiz: everyone gets the same questions on the same day (seeded by the date).
 * Two come from yesterday's real results, the rest from the clubs' history.
 */
suspend fun buildQuiz(day: Long): List<Question> {
    val rnd = Random(dayKey(day).toLong())
    val qs = ArrayList<Question>()
    val big = setOf(42, 47, 87, 55, 54, 53, 73, 290, 10216)
    val yesterday = runCatching { parseMatchList(Api.fm("matches", Api.matchesParams(dayKey(day - 86_400_000L)), 3_600_000)) }.getOrDefault(emptyList())
    val played = yesterday.filter { it.primaryId in big }.flatMap { it.matches }.filter { it.finished && it.home.score != null }.shuffled(rnd)
    played.take(1).forEach { m ->
        val right = "${m.home.score} - ${m.away.score}"
        val wrong = generateSequence { "${rnd.nextInt(0, 5)} - ${rnd.nextInt(0, 5)}" }.filter { it != right }.distinct().take(3).toList()
        val opts = (wrong + right).shuffled(rnd)
        qs += Question(L("بازی دیروز {0} و {1} ({2}) چند شد؟", iso(teamName(m.home.id, m.home.name)), iso(teamName(m.away.id, m.away.name)), iso(leagueName(0, m.leagueName))), opts.map { fa(it) }, opts.indexOf(right), m.home.id, L("عدد سمت راست: گل‌های میزبان"))
    }
    played.drop(1).take(1).forEach { m ->
        val h = m.home.score ?: 0; val a = m.away.score ?: 0
        val opts = listOf(L("برد {0}", iso(teamName(m.home.id, m.home.name))), L("تساوی"), L("برد {0}", iso(teamName(m.away.id, m.away.name))))
        qs += Question(L("دیروز در بازی {0} و {1} چه نتیجه‌ای رقم خورد؟", iso(teamName(m.home.id, m.home.name)), iso(teamName(m.away.id, m.away.name))), opts, if (h > a) 0 else if (h == a) 1 else 2, m.away.id)
    }
    val clubs = Clubs.ids.shuffled(rnd)
    val makers = listOf<(Int) -> Question?>(
        { id -> val c = Clubs.get(id)!!; if (c.founded <= 0) null else {
            val right = c.founded
            val opts = (listOf(right) + generateSequence { right + rnd.nextInt(-25, 26) }.filter { it != right }.distinct().take(3)).shuffled(rnd)
            Question(L("{0} در چه سالی تأسیس شد؟", c.fa), opts.map { fa(it) }, opts.indexOf(right), id)
        } },
        { id -> val c = Clubs.get(id)!!
            val others = Clubs.ids.filter { it != id }.map { Clubs.get(it)!!.stadium }.filter { it != c.stadium }.distinct().shuffled(rnd).take(3)
            val opts = (others + c.stadium).shuffled(rnd)
            Question(L("ورزشگاه خانگی {0} کدام است؟", c.fa), opts, opts.indexOf(c.stadium), id) },
        { id -> val c = Clubs.get(id)!!; if (c.legends.isEmpty()) null else {
            val right = c.legends[rnd.nextInt(c.legends.size)]
            val others = Clubs.ids.filter { it != id }.flatMap { Clubs.get(it)!!.legends }.filter { it !in c.legends }.distinct().shuffled(rnd).take(3)
            val opts = (others + right).shuffled(rnd)
            Question(L("کدام یک از اسطوره‌های {0} است؟", c.fa), opts, opts.indexOf(right), id)
        } },
        { id -> val c = Clubs.get(id)!!
            val others = Clubs.ids.filter { it != id }.map { Clubs.get(it)!!.fa }.shuffled(rnd).take(3)
            val opts = (others + c.fa).shuffled(rnd)
            Question(L("لقب «{0}» مال کدام باشگاه است؟", c.nick), opts, opts.indexOf(c.fa)) },
        { id -> val c = Clubs.get(id)!!; c.years.filter { it.first != c.founded }.randomOrNull(rnd)?.let { (y, t) ->
            val opts = (listOf(y) + generateSequence { y + rnd.nextInt(-12, 13) }.filter { it != y }.distinct().take(3)).shuffled(rnd)
            Question(L("{0}: «{1}» در چه سالی بود؟", c.fa, t), opts.map { fa(it) }, opts.indexOf(y), id)
        } },
    )
    var i = 0
    while (qs.size < 6 && i < clubs.size * 2) {
        makers[(i + rnd.nextInt(makers.size)) % makers.size](clubs[i % clubs.size])?.let { q -> if (qs.none { it.text == q.text }) qs += q }
        i++
    }
    return qs
}

@Composable
fun QuizScreen() {
    val ctx = LocalContext.current
    val today = startOfDay(0)
    val key = dayKey(today)
    val l = rememberLoader(key) { buildQuiz(System.currentTimeMillis()) }
    val p = Store.prefs()
    // answers are saved so the quiz can't be retaken the same day
    val picked = remember { mutableStateListOf<Int>().apply {
        if (p.getString("quizDay", "") == key) addAll((p.getString("quizAns", "") ?: "").split(',').mapNotNull { it.toIntOrNull() })
    } }
    var current by remember { mutableIntStateOf(picked.size) }
    val accent = LocalAccent.current
    LoadBox(l, 4) { qs ->
        if (qs.isEmpty()) { EmptyView(L("کوئیز امروز آماده نشد؛ کمی بعد دوباره سر بزنید"), Ic.info); return@LoadBox }
        val score = qs.indices.count { it < picked.size && picked[it] == qs[it].answer }
        val done = picked.size >= qs.size
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = BottomSpace)) {
            item {
                Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(64.dp).glow(accent.copy(alpha = 0.4f), 10.dp, RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp)).background(accent),
                        contentAlignment = Alignment.Center) { T("?", 34, FontWeight.Black, LocalAccentInk.current) }
                    T(L("کوئیز فوتبالی امروز"), 22, FontWeight.Black, modifier = Modifier.padding(top = 8.dp))
                    T(persianDate(today, "EEEE d MMMM") + L(" · {0} سؤال · رکورد پیاپی: {1} روز", qs.size, p.getInt("quizStreak", 0)), 12, color = C.mute)
                }
            }
            item {
                // progress dots
                Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    qs.indices.forEach { i ->
                        val c = when { i >= picked.size -> if (i == current) accent.copy(alpha = 0.5f) else C.card2; picked[i] == qs[i].answer -> C.win; else -> C.loss }
                        Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(c))
                    }
                }
            }
            if (done) item {
                Result(score, qs.size) {
                    val text = L("امروز در کوئیز گل‌لایو {0} از {1} گرفتم ⚽\n{2}", fa(score), fa(qs.size), BAZAAR_URL)
                    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), L("اشتراک‌گذاری")))
                }
            }
            qs.forEachIndexed { qi, q ->
                if (qi > current) return@forEachIndexed
                item(key = "q$qi") {
                    QuestionCard(qi, q, picked.getOrNull(qi)) { choice ->
                        if (picked.size != qi) return@QuestionCard
                        picked.add(choice)
                        p.edit().putString("quizDay", key).putString("quizAns", picked.joinToString(",")).apply()
                        if (picked.size == qs.size) {
                            // a day counts toward the streak when at least half the answers are right
                            val s = qs.indices.count { picked[it] == qs[it].answer }
                            val last = p.getString("quizLastDone", "")
                            val streak = if (s * 2 >= qs.size) (if (last == dayKey(startOfDay(-1))) p.getInt("quizStreak", 0) + 1 else 1) else 0
                            p.edit().putString("quizLastDone", key).putInt("quizStreak", streak).apply()
                        }
                        current = (qi + 1).coerceAtMost(qs.size)
                    }
                }
            }
        }
    }
}

@Composable
private fun QuestionCard(n: Int, q: Question, chosen: Int?, onPick: (Int) -> Unit) {
    val pop = remember { Animatable(0.92f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(Spring.DampingRatioMediumBouncy)) }
    Card(Modifier.padding(bottom = 12.dp).scale(pop.value)) {
        Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(28.dp).clip(RoundedCornerShape(10.dp)).background(C.card2), contentAlignment = Alignment.Center) { T("${n + 1}", 13, FontWeight.Black) }
            T(q.text, 15, FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            if (q.crest > 0) Crest(q.crest, 30.dp)
        }
        if (q.note.isNotEmpty()) T(q.note, 11, color = C.mute, modifier = Modifier.padding(horizontal = 54.dp))
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            q.options.forEachIndexed { i, o ->
                val state = when {
                    chosen == null -> 0
                    i == q.answer -> 1
                    i == chosen -> 2
                    else -> 3
                }
                val bg = when (state) { 1 -> C.win.copy(alpha = 0.18f); 2 -> C.loss.copy(alpha = 0.18f); else -> C.card2 }
                val border = when (state) { 1 -> C.win; 2 -> C.loss; else -> C.line }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(bg).border(1.dp, border, RoundedCornerShape(14.dp))
                        .clickable(enabled = chosen == null) { onPick(i) }.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // right-to-left so a score reads like everywhere in the app: home goals on the right
                    T(o, 14, FontWeight.Bold, if (state == 3) C.mute else C.ink, Modifier.weight(1f), maxLines = 2)
                    when (state) {
                        1 -> T("✓", 16, FontWeight.Black, C.win)
                        2 -> T("✗", 16, FontWeight.Black, C.loss)
                    }
                }
            }
        }
    }
}

@Composable
private fun Result(score: Int, total: Int, onShare: () -> Unit) {
    val good = score * 2 >= total
    val accent = LocalAccent.current
    Column(
        Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(22.dp)).drawBehind {
            cssLinear(160f, 0f to (if (good) accent else C.loss).copy(alpha = 0.35f), 1f to C.card)
        }.border(1.dp, C.line2, RoundedCornerShape(22.dp)).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        T(if (score == total) "🏆" else if (good) "⚽" else "🥅", 44)
        T(L("{0} از {1}", score, total), 30, FontWeight.Black)
        T(when { score == total -> L("بی‌نقص! مثل یک کارشناس فوتبال"); good -> L("آفرین! فردا هم سر بزن"); else -> L("این بار نشد؛ فردا سؤال‌های تازه می‌آیند") }, 14, color = C.mute, align = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Pill(L("اشتراک‌گذاری نتیجه"), PillKind.Accent, onClick = onShare, icon = Ic.share)
    }
}
