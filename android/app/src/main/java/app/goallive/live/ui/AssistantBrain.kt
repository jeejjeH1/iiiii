package app.goallive.live.ui

import app.goallive.live.data.Api
import app.goallive.live.data.Clubs
import app.goallive.live.data.MatchItem
import app.goallive.live.data.TEAMS_FA
import app.goallive.live.data.a
import app.goallive.live.data.b
import app.goallive.live.data.d
import app.goallive.live.data.faTeamName
import app.goallive.live.data.i
import app.goallive.live.data.o
import app.goallive.live.data.objs
import app.goallive.live.data.parseMatch
import app.goallive.live.data.parseMatchList
import app.goallive.live.data.path
import app.goallive.live.data.s
import app.goallive.live.data.tr
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.roundToInt

/* ---------- reading Persian questions ---------- */

/** Arabic letters, Persian digits, half-spaces and punctuation folded, so names match however they were typed. */
internal fun norm(s: String) = buildString {
    for (ch in s.lowercase()) append(
        when (ch) {
            'ي', 'ى' -> 'ی'; 'ك' -> 'ک'; 'ة' -> 'ه'; 'أ', 'إ', 'آ' -> 'ا'
            '‌', '‏', '‎' -> ' '
            in '۰'..'۹' -> '0' + (ch - '۰')
            in '٠'..'٩' -> '0' + (ch - '٠')
            else -> ch
        },
    )
}.replace(Regex("[؟?!.,،:;«»\"()]"), " ").replace(Regex("\\s+"), " ").trim()

/** Whole-word match, so a short name like "نیس" is not found inside "نیست". */
private fun word(q: String, w: String) = " $q ".contains(" ${norm(w)} ")

/** Any of the phrases appears in the (normalized) question. */
private fun has(q: String, vararg words: String) = words.any { q.contains(norm(it)) }

/** Famous players in Persian, as FotMob spells them. */
private val PLAYERS = mapOf(
    "طارمی" to "Mehdi Taremi", "مهدی طارمی" to "Mehdi Taremi", "آزمون" to "Sardar Azmoun", "سردار آزمون" to "Sardar Azmoun",
    "جهانبخش" to "Alireza Jahanbakhsh", "بیرانوند" to "Alireza Beiranvand", "قلی زاده" to "Ali Gholizadeh", "قلیزاده" to "Ali Gholizadeh",
    "انصاری فرد" to "Karim Ansarifard", "نوراللهی" to "Ahmad Nourollahi", "کنعانی" to "Hossein Kanaanizadegan", "عزت اللهی" to "Saeid Ezatolahi",
    "میلاد محمدی" to "Milad Mohammadi", "حاج صفی" to "Ehsan Hajsafi", "پورعلی گنجی" to "Morteza Pouraliganji", "شجاع خلیل زاده" to "Shoja Khalilzadeh",
    "محبی" to "Mohammad Mohebi", "رضاییان" to "Ramin Rezaeian", "سیدحسین حسینی" to "Hossein Hosseini", "اوستون اورونوف" to "Oston Urunov",
    "مسی" to "Lionel Messi", "رونالدو" to "Cristiano Ronaldo", "صلاح" to "Mohamed Salah", "هالند" to "Erling Haaland",
    "امباپه" to "Kylian Mbappé", "یامال" to "Lamine Yamal", "وینیسیوس" to "Vinicius Junior", "بلینگهام" to "Jude Bellingham",
    "هری کین" to "Harry Kane", "کین" to "Harry Kane", "لواندوفسکی" to "Robert Lewandowski", "رافینیا" to "Raphinha", "پدری" to "Pedri",
    "ساکا" to "Bukayo Saka", "پالمر" to "Cole Palmer", "اودگارد" to "Martin Ødegaard", "دی بروینه" to "Kevin De Bruyne", "رودری" to "Rodri",
    "فودن" to "Phil Foden", "لائوتارو" to "Lautaro Martínez", "اوسیمن" to "Victor Osimhen", "نیمار" to "Neymar", "بنزما" to "Karim Benzema",
    "مودریچ" to "Luka Modrić", "والورده" to "Federico Valverde", "رودریگو" to "Rodrygo", "گریزمان" to "Antoine Griezmann",
    "آلوارز" to "Julián Álvarez", "موسیالا" to "Jamal Musiala", "ویرتز" to "Florian Wirtz", "لیائو" to "Rafael Leão",
    "ولاهوویچ" to "Dušan Vlahović", "ایساک" to "Alexander Isak", "دمبله" to "Ousmane Dembélé", "حکیمی" to "Achraf Hakimi",
    "فن دایک" to "Virgil van Dijk", "آلیسون" to "Alisson", "کورتوا" to "Thibaut Courtois", "سون" to "Son Heung-Min",
    "برونو فرناندز" to "Bruno Fernandes", "راشفورد" to "Marcus Rashford", "گیوکرش" to "Viktor Gyökeres", "رایس" to "Declan Rice",
    "کواراتسخلیا" to "Khvicha Kvaratskhelia", "بارکولا" to "Bradley Barcola", "دوئه" to "Désiré Doué", "اولمو" to "Dani Olmo",
    "کوبارسی" to "Pau Cubarsí", "گاوی" to "Gavi", "ترنت" to "Trent Alexander-Arnold", "کامبیاسو" to "Andrea Cambiaso",
)

private val LEAGUES = listOf(
    listOf("لیگ برتر ایران", "خلیج فارس", "لیگ ایران", "لیگ برتر خلیج") to 523,
    listOf("لیگ قهرمانان آسیا", "لیگ نخبگان آسیا", "نخبگان") to 525,
    listOf("لیگ قهرمانان", "چمپیونز") to 42,
    listOf("لیگ اروپا") to 73,
    listOf("لیگ کنفرانس") to 10216,
    listOf("لالیگا", "لیگ اسپانیا") to 87,
    listOf("سری آ", "سریا", "لیگ ایتالیا") to 55,
    listOf("بوندسلیگا", "بوندس لیگا", "لیگ آلمان") to 54,
    listOf("لوشامپیونه", "لیگ 1 فرانسه", "لیگ فرانسه") to 53,
    listOf("لیگ برتر انگلیس", "لیگ انگلیس", "پریمیر لیگ", "پرمیر لیگ") to 47,
    listOf("لیگ برتر") to 47,
)

private fun leagueFa(id: Int, name: String) = when (id) {
    523 -> "لیگ برتر ایران"; 47 -> "لیگ برتر انگلیس"; 87 -> "لالیگا"; 55 -> "سری آ"; 54 -> "بوندسلیگا"; 53 -> "لوشامپیونه"
    42 -> "لیگ قهرمانان اروپا"; 73 -> "لیگ اروپا"; 10216 -> "لیگ کنفرانس اروپا"; 525 -> "لیگ نخبگان آسیا"; else -> name
}

/** Everyday Persian nicknames, searched by their English name. */
private val NICKS = mapOf(
    "بارسا" to "Barcelona", "رئال" to "Real Madrid", "سیتی" to "Manchester City", "منچستر سیتی" to "Manchester City",
    "منچستر یونایتد" to "Manchester United", "یونایتد" to "Manchester United", "من یونایتد" to "Manchester United", "بایرن" to "Bayern Munich",
    "پی اس جی" to "Paris Saint-Germain", "پاری سن ژرمن" to "Paris Saint-Germain", "اتلتیکو" to "Atletico Madrid", "یووه" to "Juventus",
    "دورتموند" to "Borussia Dortmund", "تیم ملی" to "Iran", "تیم ملی ایران" to "Iran", "ایران" to "Iran", "آث میلان" to "AC Milan",
    "اینتر میلان" to "Inter", "لیور" to "Liverpool", "آرسنال" to "Arsenal", "چلسی" to "Chelsea", "تاتنهام" to "Tottenham",
)

private val BIG = setOf(42, 47, 87, 55, 54, 53, 73, 523, 77, 50, 525, 10216)

/** "امروز / فردا / دیروز …" as a day offset, or null when no day is named. */
private fun dayOffset(q: String): Int? = when {
    has(q, "پس فردا") -> 2
    has(q, "پریروز") -> -2
    has(q, "فردا") -> 1
    has(q, "دیروز", "دیشب") -> -1
    has(q, "امروز", "امشب") -> 0
    else -> null
}

private fun dayWord(off: Int) = when (off) { 0 -> "امروز"; 1 -> "فردا"; -1 -> "دیروز"; 2 -> "پس‌فردا"; -2 -> "پریروز"; else -> "" }

/** A plain number written in the question ("تیم چهارم"، "رتبه 5"). */
private fun numberIn(q: String): Int? {
    Regex("\\d+").find(q)?.value?.toIntOrNull()?.let { return it }
    val words = listOf("اول" to 1, "دوم" to 2, "سوم" to 3, "چهارم" to 4, "پنجم" to 5, "ششم" to 6, "هفتم" to 7, "هشتم" to 8, "نهم" to 9, "دهم" to 10,
        "یازدهم" to 11, "دوازدهم" to 12, "سیزدهم" to 13, "چهاردهم" to 14, "پانزدهم" to 15, "شانزدهم" to 16, "هفدهم" to 17, "هجدهم" to 18,
        "نوزدهم" to 19, "بیستم" to 20, "آخر" to -1)
    return words.sortedByDescending { it.first.length }.firstOrNull { word(q, it.first) }?.second
}

private val HELP = """این‌ها را می‌توانم جواب بدهم:
• تیم: بازی بعدی، نتیجه آخرین بازی، نتایج و برنامه، رتبه در جدول، فرم، گلزن و پاسور برتر، سرمربی، بازیکنان، مصدوم‌ها، ورزشگاه، افتخارات، آمار حمله و دفاع، کارنامه در خانه و بیرون
• دو تیم: مقایسه، رودررو، و اینکه بازی بینشان را به حساب آمار کی می‌برد
• بازیکن: گل و پاس گل و نمره این فصل، سن و قد و ارزش، آخرین بازی، باشگاه‌های قبلی، مقایسه دو بازیکن
• لیگ: صدر و ته جدول، تیم nام، آقای گل، بهترین پاسور، بازی‌های هفته بعد
• روزها: بازی‌های امروز، فردا، دیروز و بازی‌های زنده
مثال: «پرسپولیس و استقلال را مقایسه کن»، «گلزن برتر سپاهان کیست؟»، «تیم چهارم لالیگا»، «بازی‌های فردا»"""

val EXAMPLES = listOf(
    "بازی بعدی پرسپولیس کی است؟", "طارمی امسال چند گل زده؟", "استقلال چندم است؟", "آقای گل لیگ برتر انگلیس",
    "پرسپولیس و استقلال را مقایسه کن", "بازی‌های فردا", "مصدوم‌های رئال مادرید", "راهنما",
)

/* ---------- the brain ---------- */

/**
 * The assistant's answers, built only from FotMob data (no AI service).
 * It remembers the last team / player / league it talked about, so follow-ups like "بازی بعدیش؟" work.
 */
class Brain {
    private var lastTeam: Pair<Int, String>? = null
    private var lastPlayer: Int? = null
    private var lastLeague: Int? = null

    suspend fun answer(raw: String): Msg {
        val q = norm(raw)
        if (q.isEmpty()) return Msg(false, "سؤالتان را بنویسید.")
        if (has(q, "راهنما", "کمک", "چی بلدی", "چه کارهایی", "چه سوال")) return Msg(false, HELP)

        // what is named in the question
        val players = findPlayers(raw)
        val teams = findTeams(q)
        val league = findLeague(q)
        val day = dayOffset(q)

        if (players.size >= 2) return comparePlayers(players[0], players[1])
        if (players.size == 1) return player(players[0], q)
        if (teams.size >= 2) return twoTeams(teams[0], teams[1], q)
        if (teams.size == 1) return team(teams[0], q, day)
        if (league != null) return league(league, q, day)
        if (day != null || has(q, "زنده", "الان", "بازی ها", "بازیها")) return dayMatches(day ?: 0, q)

        // nothing named: an English name, or a follow-up about what we were just talking about
        Regex("[A-Za-z][A-Za-z .'-]{2,}").find(raw)?.value?.trim()?.let { latin ->
            val found = runCatching { search(latin) }.getOrNull()
            found?.teams?.firstOrNull()?.let { t ->
                val id = t.s("id")?.toIntOrNull()
                if (id != null) return team(id to teamName(id, t.s("name") ?: latin), q, day)
            }
            found?.players?.firstOrNull()?.s("id")?.toIntOrNull()?.let { return player(it, q) }
        }
        lastPlayer?.let { if (playerQuestion(q)) return player(it, q) }
        lastTeam?.let { return team(it, q, day) }
        lastLeague?.let { return league(it, q, day) }
        return Msg(false, "متوجه نشدم 🤔 نام یک تیم، بازیکن یا لیگ را بنویسید؛ یا «راهنما» را بزنید تا ببینید چه چیزهایی را می‌توانم جواب بدهم.")
    }

    /* ---------- finding names ---------- */

    private suspend fun findTeams(q: String): List<Pair<Int, String>> {
        var rest = " $q "
        val out = ArrayList<Pair<Int, String>>()
        // clubs with themes (have ids), longest names first so "استقلال خوزستان" beats "استقلال"
        val clubs = Clubs.ids.mapNotNull { id -> Clubs.get(id)?.let { id to it.fa } }
        val named = (clubs.map { Triple(it.first, it.second, null as String?) } +
            TEAMS_FA.entries.map { Triple(0, it.value, it.key) } + NICKS.entries.map { Triple(0, it.key, it.value) }).sortedByDescending { it.second.length }
        for ((id, fa, en) in named) {
            val n = norm(fa)
            val at = rest.indexOf(" $n ")
            if (at < 0) continue
            rest = rest.replaceRange(at, at + n.length + 2, " # ")
            val pair = if (id > 0) id to fa else {
                val t = runCatching { search(en!!).teams.firstOrNull() }.getOrNull() ?: continue
                val tid = t.s("id")?.toIntOrNull() ?: continue
                tid to fa
            }
            if (out.none { it.first == pair.first }) out += pair
            if (out.size == 2) break
        }
        return out
    }

    private suspend fun findPlayers(raw: String): List<Int> {
        val q = norm(raw)
        var rest = " $q "
        val out = ArrayList<Int>()
        for ((fa, en) in PLAYERS.entries.sortedByDescending { it.key.length }) {
            val n = norm(fa)
            val at = rest.indexOf(" $n ")
            if (at < 0) continue
            rest = rest.replaceRange(at, at + n.length + 2, " # ")
            val id = runCatching { search(en).players.firstOrNull()?.s("id")?.toIntOrNull() }.getOrNull() ?: continue
            if (id !in out) out += id
            if (out.size == 2) break
        }
        return out
    }

    private fun findLeague(q: String): Int? = LEAGUES.firstOrNull { (words, _) -> has(q, *words.toTypedArray()) }?.second

    private fun playerQuestion(q: String) = has(q, "گل", "پاس", "نمره", "سن", "قد", "ارزش", "قیمت", "مصدوم", "آخرین بازی", "باشگاه", "قرارداد", "شماره", "پا ")

    /* ---------- teams ---------- */

    private class TeamData(val id: Int, val name: String, val d: JSONObject) {
        val ov get() = d.o("overview")
        private val tables get() = d.a("table").objs().flatMap { t -> listOfNotNull(t.path("data.table")) + t.o("data").a("tables").objs().mapNotNull { it.o("table") } }
        val table get() = tables.firstOrNull { tb -> tb.a("all").objs().any { it.i("id") == id } }
        val row get() = table?.a("all").objs().firstOrNull { it.i("id") == id }
        val leagueName get() = d.a("table").objs().firstOrNull()?.o("data").let { leagueFa(it.i("leagueId") ?: 0, it.s("leagueName") ?: "لیگ") }
        val fixtures get() = d.path("fixtures.allFixtures").a("fixtures").objs()
        val form get() = ov.a("teamForm").objs().takeLast(5).mapNotNull { it.s("resultString") }
        fun stat(header: String) = d.o("stats").a("players").objs().firstOrNull { it.s("header") == header }?.o("participant")
        fun rank(header: String) = d.o("stats").a("teams").objs().firstOrNull { it.s("header") == header }?.o("participant")
        val ppg: Double get() = row?.let { (it.i("pts") ?: 0).toDouble() / (it.i("played") ?: 1).coerceAtLeast(1) } ?: 1.2
    }

    private suspend fun teamData(t: Pair<Int, String>) = TeamData(t.first, t.second, Api.fm("teams", mapOf("id" to t.first, "ccode3" to "IRN"), 300_000))

    private fun formFa(f: List<String>) = f.joinToString("، ") { when (it) { "W" -> "برد"; "L" -> "باخت"; else -> "مساوی" } }

    private fun opp(m: MatchItem, id: Int) = if (m.home.id == id) teamName(m.away.id, m.away.name) else teamName(m.home.id, m.home.name)

    private fun describe(m: MatchItem, id: Int, name: String): String {
        val where = if (m.home.id == id) "در خانه" else "در خانه حریف"
        return when {
            m.live -> "$name همین الان با ${opp(m, id)} بازی دارد: ${fa(m.home.score ?: 0)}-${fa(m.away.score ?: 0)}، ${statusText(m)}."
            m.finished -> {
                val us = if (m.home.id == id) m.home.score ?: 0 else m.away.score ?: 0
                val them = if (m.home.id == id) m.away.score ?: 0 else m.home.score ?: 0
                val r = when { us > them -> "برد"; us < them -> "باخت"; else -> "مساوی کرد" }
                "$name ${persianDate(m.time, "EEEE d MMMM")} $where مقابل ${opp(m, id)} ${fa(us)}-${fa(them)} $r."
            }
            else -> {
                val days = ((m.time - startOfDay(0)) / 86_400_000L).toInt()
                val whenTxt = when (days) { 0 -> "امروز"; 1 -> "فردا"; else -> persianDate(m.time, "EEEE d MMMM") }
                "بازی بعدی $name مقابل ${opp(m, id)} است، $whenTxt ساعت ${fa(timeOf(m.time))}، $where" +
                    (if (m.leagueName.isNotEmpty()) " (${m.leagueName})" else "") + "."
            }
        }
    }

    /** Goal scorers of a finished or live match, as one line. */
    private suspend fun scorers(matchId: Int): String? = runCatching {
        val d = Api.fm("matchDetails", mapOf("matchId" to matchId), 120_000)
        val goals = d.path("content.matchFacts.events").a("events").objs().filter { it.s("type") == "Goal" }
        if (goals.isEmpty()) null else "گل‌ها: " + goals.joinToString("، ") {
            (it.o("player").s("name") ?: it.s("nameStr") ?: "") + " " + fa((it.s("time") ?: "") + "'") + if (it.b("ownGoal")) " (به خودی)" else ""
        }
    }.getOrNull()

    private suspend fun team(t: Pair<Int, String>, q: String, day: Int?): Msg {
        lastTeam = t; lastPlayer = null
        val (id, name) = t
        val td = teamData(t)
        val links = listOf(Link("صفحه $name") { it.team(id) })
        fun matchOf(key: String) = td.ov.o(key)?.let { parseMatch(it) }
        val row = td.row
        val position = row?.let {
            "$name در ${td.leagueName} رتبه ${fa(it.i("idx") ?: 0)} را دارد با ${fa(it.i("pts") ?: 0)} امتیاز از ${fa(it.i("played") ?: 0)} بازی " +
                "(${fa(it.i("wins") ?: 0)} برد، ${fa(it.i("draws") ?: 0)} مساوی، ${fa(it.i("losses") ?: 0)} باخت، گل ${fa(it.s("scoresStr") ?: "")})."
        }
        return when {
            // a specific day
            day != null -> {
                val list = runCatching { parseMatchList(Api.fm("matches", Api.matchesParams(dayKey(startOfDay(day))), 30_000)) }.getOrDefault(emptyList())
                val m = list.flatMap { it.matches }.firstOrNull { it.home.id == id || it.away.id == id }
                if (m == null) Msg(false, "$name ${dayWord(day)} بازی ندارد." + (matchOf("nextMatch")?.let { " " + describe(it, id, name) } ?: ""), links = links)
                else Msg(false, describe(m, id, name) + (if (m.started) scorers(m.id)?.let { "\n$it" } ?: "" else ""), listOf(m), links)
            }
            has(q, "پیش بینی", "پیشبینی", "کی می برد", "کی میبرد", "می برد", "شانس") -> {
                val next = matchOf("nextMatch") ?: return Msg(false, "بازی بعدی $name هنوز اعلام نشده.", links = links)
                val oid = if (next.home.id == id) next.away.id else next.home.id
                val other = runCatching { teamData(oid to opp(next, id)) }.getOrNull()
                Msg(false, describe(next, id, name) + "\n" + predict(td, other, next.home.id == id), listOf(next), links)
            }
            has(q, "برنامه", "بازی های بعدی", "بازیهای بعدی", "بازی های آینده", "چند بازی بعد") -> {
                val up = td.fixtures.map { parseMatch(it) }.filter { !it.started && !it.cancelled }.take(5)
                if (up.isEmpty()) Msg(false, "برنامه بازی‌های بعدی $name هنوز اعلام نشده.", links = links)
                else Msg(false, "${fa(up.size)} بازی بعدی $name:", up, links)
            }
            has(q, "نتایج", "بازی های اخیر", "بازیهای اخیر", "بازی های قبلی", "آخرین بازی ها") -> {
                val past = td.fixtures.map { parseMatch(it) }.filter { it.finished }.takeLast(5).reversed()
                Msg(false, "نتایج اخیر $name (فرم: ${formFa(td.form).ifEmpty { "-" }}):", past, links)
            }
            has(q, "بعدی", "بعد", "کی بازی", "چه ساعتی", "ساعت چند", "کی بازی داره") ->
                matchOf("nextMatch")?.let { Msg(false, describe(it, id, name), listOf(it), links) } ?: Msg(false, "بازی بعدی $name هنوز اعلام نشده.", links = links)
            has(q, "نتیجه", "قبلی", "آخرین بازی", "چند چند", "چند شد", "بازی آخر") ->
                matchOf("lastMatch")?.let { Msg(false, describe(it, id, name) + (scorers(it.id)?.let { s -> "\n$s" } ?: ""), listOf(it), links) }
                    ?: Msg(false, "بازی اخیری برای $name پیدا نشد.", links = links)
            has(q, "الان", "زنده") -> {
                val live = td.ov.b("hasOngoingMatch")
                val m = matchOf("nextMatch")?.takeIf { it.live } ?: matchOf("lastMatch")?.takeIf { it.live }
                if (live && m != null) Msg(false, describe(m, id, name), listOf(m), links)
                else Msg(false, "$name الان بازی ندارد. " + (matchOf("nextMatch")?.let { describe(it, id, name) } ?: ""), links = links)
            }
            has(q, "مصدوم", "آسیب", "غایب") -> {
                val hurt = td.d.o("squad").a("squad").objs().flatMap { it.a("members").objs() }.filter { it.opt("injury") is JSONObject }
                if (hurt.isEmpty()) Msg(false, "طبق FotMob، $name الان بازیکن مصدومی ندارد.", links = links)
                else Msg(false, "مصدوم‌های $name:\n" + hurt.joinToString("\n") { p ->
                    val inj = p.o("injury")
                    "• ${p.s("name")}" + (inj.s("expectedReturn")?.let { " (بازگشت: ${tr(it)})" } ?: "")
                }, links = links)
            }
            has(q, "سرمربی", "مربی") -> {
                val coach = td.d.o("squad").a("squad").objs().firstOrNull { it.s("title") == "coach" }?.a("members").objs().firstOrNull()?.s("name")
                Msg(false, coach?.let { "سرمربی $name: $it" } ?: "نام سرمربی $name پیدا نشد.", links = links)
            }
            has(q, "پاس گل", "پاسور", "پاس") -> td.stat("Assists")?.let { p ->
                Msg(false, "بهترین پاسور $name این فصل ${p.s("name")} است با ${fa(p.i("value") ?: 0)} پاس گل.", links = listOf(Link("صفحه ${p.s("name")}") { it.player(p.i("id") ?: 0) }) + links)
            } ?: Msg(false, "آمار پاس گل $name در دسترس نیست.", links = links)
            has(q, "گلزن", "آقای گل", "گل زن", "بهترین بازیکن", "ستاره", "نمره") -> {
                val top = td.stat("Top scorer"); val rated = td.stat("FotMob rating")
                val lines = listOfNotNull(
                    top?.let { "بهترین گلزن $name این فصل ${it.s("name")} است با ${fa(it.i("value") ?: 0)} گل." },
                    rated?.let { "بهترین نمره را ${it.s("name")} گرفته (${dec(it.d("value"), 2)})." },
                )
                Msg(false, lines.joinToString("\n").ifEmpty { "آمار بازیکنان $name در دسترس نیست." },
                    links = listOfNotNull(top?.let { p -> Link("صفحه ${p.s("name")}") { it.player(p.i("id") ?: 0) } }) + links)
            }
            has(q, "بازیکنان", "بازیکن ها", "لیست", "اسکواد", "ترکیب تیم") -> {
                val groups = td.d.o("squad").a("squad").objs().filter { it.s("title") != "coach" }
                val lines = mapOf("keepers" to "دروازه‌بان‌ها", "defenders" to "مدافعان", "midfielders" to "هافبک‌ها", "attackers" to "مهاجمان")
                Msg(false, "بازیکنان $name:\n" + groups.joinToString("\n") { g ->
                    "${lines[g.s("title")] ?: g.s("title")}: " + g.a("members").objs().joinToString("، ") { it.s("name") ?: "" }
                }, links = links)
            }
            has(q, "ورزشگاه", "استادیوم", "زمین خانگی") -> {
                val v = td.ov.o("venue"); val w = v.o("widget")
                val pairs = v.a("statPairs")?.let { arr -> (0 until arr.length()).mapNotNull { arr.optJSONArray(it) }.associate { it.optString(0) to it.opt(1) } } ?: emptyMap()
                Msg(false, w?.s("name")?.let { n ->
                    "ورزشگاه $name: $n" + (w.s("city")?.let { "، شهر $it" } ?: "") +
                        (pairs["Capacity"]?.let { "، ظرفیت ${fa(it)} نفر" } ?: "") + (pairs["Opened"]?.let { "، افتتاح ${fa(it)}" } ?: "") + "."
                } ?: "اطلاعات ورزشگاه $name پیدا نشد.", links = links)
            }
            has(q, "قهرمان", "افتخار", "جام", "عنوان") -> {
                val tl = td.d.o("history").a("trophyList").objs().sortedByDescending { it.i("won") ?: 0 }.filter { (it.i("won") ?: 0) > 0 }
                if (tl.isEmpty()) Msg(false, "افتخارات $name در FotMob ثبت نشده.", links = links)
                else Msg(false, "افتخارات $name:\n" + tl.take(8).joinToString("\n") { "• ${tr(it.s("name"))}: ${fa(it.i("won") ?: 0)} قهرمانی" +
                    (it.s("seasonsWon")?.substringBefore(',')?.let { s -> " (آخرین بار ${fa(s)})" } ?: "") }, links = links)
            }
            has(q, "خانه", "خارج", "بیرون", "میزبان", "مهمان") -> {
                fun rec(k: String) = td.table?.a(k).objs().firstOrNull { it.i("id") == id }?.let {
                    "${fa(it.i("wins") ?: 0)} برد، ${fa(it.i("draws") ?: 0)} مساوی، ${fa(it.i("losses") ?: 0)} باخت (گل ${fa(it.s("scoresStr") ?: "")})"
                }
                Msg(false, listOfNotNull(rec("home")?.let { "$name در خانه: $it" }, rec("away")?.let { "$name بیرون از خانه: $it" })
                    .joinToString("\n").ifEmpty { "کارنامه خانه و بیرون $name در دسترس نیست." }, links = links)
            }
            has(q, "حمله", "دفاع", "گل زده", "گل خورده", "چند گل", "آمار", "مالکیت", "کلین شیت") -> {
                val r = listOf("Goals per match" to "گل زده در هر بازی", "Goals conceded per match" to "گل خورده در هر بازی",
                    "Average possession" to "میانگین مالکیت٪", "Clean sheets" to "کلین‌شیت", "Expected goals" to "گل مورد انتظار (xG)", "Big chances" to "موقعیت عالی")
                    .mapNotNull { (h, label) -> td.rank(h)?.let { "• $label: ${dec(it.d("value"), 1)} (رتبه ${fa(it.i("rank") ?: 0)} لیگ)" } }
                Msg(false, (listOfNotNull(position) + (if (r.isEmpty()) emptyList() else listOf("آمار فصل:") + r)).joinToString("\n")
                    .ifEmpty { "آمار $name در دسترس نیست." }, links = links)
            }
            has(q, "فرم", "وضعیت", "حال و روز", "اوضاع") -> Msg(false,
                if (td.form.isEmpty()) "فرم اخیر $name در دسترس نیست." else "پنج بازی اخیر $name (از قدیم به جدید): ${formFa(td.form)}." +
                    (position?.let { "\n$it" } ?: ""), links = links)
            has(q, "چندم", "رتبه", "جدول", "امتیاز", "صدر") -> {
                val rows = td.table?.a("all").objs().orEmpty()
                val idx = rows.indexOfFirst { it.i("id") == id }
                val around = if (idx < 0) "" else "\nاطراف $name در جدول:\n" + rows.subList((idx - 2).coerceAtLeast(0), (idx + 3).coerceAtMost(rows.size)).joinToString("\n") {
                    "${fa(it.i("idx") ?: 0)}. ${teamName(it.i("id") ?: 0, it.s("name") ?: "")}: ${fa(it.i("pts") ?: 0)} امتیاز"
                }
                Msg(false, (position ?: "$name در جدول لیگی پیدا نشد.") + around, links = links)
            }
            else -> {
                val last = matchOf("lastMatch"); val next = matchOf("nextMatch")
                Msg(false, listOfNotNull(position, td.form.takeIf { it.isNotEmpty() }?.let { "فرم اخیر: ${formFa(it)}." }, last?.let { describe(it, id, name) }, next?.let { describe(it, id, name) })
                    .joinToString("\n").ifEmpty { "اطلاعاتی از $name پیدا نشد." }, listOfNotNull(next ?: last), links)
            }
        }
    }

    /** "کی می‌برد؟" from the numbers only: points per game, recent form and home advantage. */
    private fun predict(a: TeamData, b: TeamData?, aHome: Boolean): String {
        if (b == null) return "برای پیش‌بینی، آمار حریف در دسترس نیست."
        fun power(t: TeamData, home: Boolean) = t.ppg + t.form.count { it == "W" } * 0.15 - t.form.count { it == "L" } * 0.15 + if (home) 0.3 else 0.0
        val pa = power(a, aHome); val pb = power(b, !aHome)
        val diff = pa - pb
        val share = (50 + diff * 22).coerceIn(15.0, 85.0).roundToInt()
        return when {
            abs(diff) < 0.25 -> "به حساب آمار، دو تیم تقریباً هم‌سطح‌اند؛ احتمال مساوی بالاست."
            diff > 0 -> "به حساب آمار (امتیاز در هر بازی، فرم و میزبانی) ${a.name} شانس بیشتری دارد؛ حدود ${fa(share)}٪."
            else -> "به حساب آمار (امتیاز در هر بازی، فرم و میزبانی) ${b.name} شانس بیشتری دارد؛ حدود ${fa(100 - share)}٪."
        } + " (این فقط برآورد آماری است، نه قطعی.)"
    }

    private suspend fun twoTeams(x: Pair<Int, String>, y: Pair<Int, String>, q: String): Msg = coroutineScope {
        lastTeam = x
        val ad = async { teamData(x) }; val bd = async { teamData(y) }
        val a = ad.await(); val b = bd.await()
        val links = listOf(Link("صفحه ${a.name}") { it.team(a.id) }, Link("صفحه ${b.name}") { it.team(b.id) })
        // their next or last meeting, if it is one of the two
        fun meeting(key: String) = a.ov.o(key)?.let { parseMatch(it) }?.takeIf { it.home.id == b.id || it.away.id == b.id }
        val next = meeting("nextMatch") ?: a.fixtures.map { parseMatch(it) }.firstOrNull { !it.started && (it.home.id == b.id || it.away.id == b.id) }
        val last = meeting("lastMatch") ?: a.fixtures.map { parseMatch(it) }.lastOrNull { it.finished && (it.home.id == b.id || it.away.id == b.id) }
        val h2h = (next ?: last)?.let { m ->
            runCatching {
                val s = Api.fm("matchDetails", mapOf("matchId" to m.id), 600_000).path("content.h2h").a("summary")
                if (s == null || s.length() < 3) null else {
                    val homeWins = s.optInt(0); val draws = s.optInt(1); val awayWins = s.optInt(2)
                    val aWins = if (m.home.id == a.id) homeWins else awayWins; val bWins = if (m.home.id == a.id) awayWins else homeWins
                    "رودررو: ${a.name} ${fa(aWins)} برد، ${b.name} ${fa(bWins)} برد، ${fa(draws)} مساوی."
                }
            }.getOrNull()
        }
        if (has(q, "بازی", "نتیجه", "کی", "رودررو", "دیدار", "می برد", "پیش بینی")) {
            val lines = listOfNotNull(
                next?.let { describe(it, a.id, a.name) }, last?.let { describe(it, a.id, a.name) }, h2h,
                next?.let { predict(a, b, it.home.id == a.id) },
            )
            if (lines.isNotEmpty()) return@coroutineScope Msg(false, lines.joinToString("\n"), listOfNotNull(next, last), links)
        }
        // side-by-side numbers
        fun line(t: TeamData) = buildString {
            append("${t.name}: ")
            t.row?.let { append("رتبه ${fa(it.i("idx") ?: 0)} در ${t.leagueName}، ${fa(it.i("pts") ?: 0)} امتیاز از ${fa(it.i("played") ?: 0)} بازی، گل ${fa(it.s("scoresStr") ?: "")}") }
            if (t.form.isNotEmpty()) append("؛ فرم: ${formFa(t.form)}")
            t.stat("Top scorer")?.let { append("؛ گلزن برتر: ${it.s("name")} (${fa(it.i("value") ?: 0)})") }
        }
        val better = when {
            a.ppg > b.ppg + 0.2 -> "${a.name} این فصل نتایج بهتری گرفته."
            b.ppg > a.ppg + 0.2 -> "${b.name} این فصل نتایج بهتری گرفته."
            else -> "دو تیم این فصل نتایج نزدیکی داشته‌اند."
        }
        Msg(false, listOfNotNull(line(a), line(b), better, h2h).joinToString("\n"), listOfNotNull(next), links)
    }

    /* ---------- players ---------- */

    private suspend fun playerData(id: Int) = Api.fm("playerData", mapOf("id" to id), 600_000)

    private fun info(d: JSONObject) = d.a("playerInformation").objs().associate { (it.s("title") ?: "") to (it.o("value")?.s("fallback") ?: it.o("value")?.s("numberValue") ?: "") }

    private fun seasonLine(d: JSONObject): String? {
        val ml = d.o("mainLeague") ?: return null
        val st = ml.a("stats").objs().associate { (it.s("title") ?: "") to it.opt("value") }
        val rating = (st["Rating"] as? Number)?.toDouble()
        return "${d.s("name")} در ${leagueFa(ml.i("leagueId") ?: 0, ml.s("leagueName") ?: "لیگ")} (فصل ${fa(ml.s("season") ?: "")}): " +
            "${fa(st["Goals"] ?: 0)} گل و ${fa(st["Assists"] ?: 0)} پاس گل در ${fa(st["Matches"] ?: 0)} بازی" +
            (st["Minutes played"]?.let { "، ${fa(it)} دقیقه" } ?: "") + (if (rating != null && rating > 0) "، میانگین نمره ${dec(rating, 2)}" else "") + "."
    }

    private suspend fun player(id: Int, q: String): Msg {
        lastPlayer = id
        val d = playerData(id)
        val name = d.s("name") ?: ""
        val team = d.o("primaryTeam")
        val inf = info(d)
        val links = listOfNotNull(Link("صفحه $name") { it.player(id) }, team?.i("teamId")?.let { tid -> Link("صفحه ${teamName(tid, team.s("teamName") ?: "")}") { it.team(tid) } })
        val teamTxt = team?.s("teamName")?.let { teamName(team.i("teamId") ?: 0, it) }
        return when {
            has(q, "مصدوم", "آسیب") -> Msg(false, d.o("injuryInformation")?.let { "$name مصدوم است" + (it.s("name")?.let { n -> " (${tr(n)})" } ?: "") +
                (it.o("expectedReturn")?.s("fallback")?.let { r -> "؛ بازگشت: $r" } ?: "") + "." } ?: "$name طبق FotMob مصدوم نیست.", links = links)
            has(q, "آخرین بازی", "بازی قبلی", "بازی آخر", "دیشب") -> {
                val m = d.a("recentMatches").objs().firstOrNull()
                Msg(false, m?.let {
                    "آخرین بازی $name: ${teamName(it.i("teamId") ?: 0, it.s("teamName") ?: "")} ${fa(it.i("homeScore") ?: 0)}-${fa(it.i("awayScore") ?: 0)} ${teamName(it.i("opponentTeamId") ?: 0, it.s("opponentTeamName") ?: "")}" +
                        " (${it.s("leagueName") ?: ""})؛ ${fa(it.i("minutesPlayed") ?: 0)} دقیقه بازی، ${fa(it.i("goals") ?: 0)} گل، ${fa(it.i("assists") ?: 0)} پاس گل" +
                        (it.o("ratingProps")?.s("rating")?.let { r -> "، نمره ${fa(r)}" } ?: "") + "."
                } ?: "بازی اخیری برای $name پیدا نشد.", links = links)
            }
            has(q, "باشگاه", "تیم های قبلی", "تیمهای قبلی", "کارنامه", "سابقه") -> {
                val career = d.path("careerHistory.careerItems.senior").a("teamEntries").objs()
                Msg(false, if (career.isEmpty()) "سابقه باشگاهی $name پیدا نشد." else "باشگاه‌های $name:\n" + career.take(8).joinToString("\n") {
                    "• ${teamName(it.i("teamId") ?: 0, it.s("team") ?: "")} (${fa(it.s("startDate")?.take(4) ?: "")}-${fa(it.s("endDate")?.take(4) ?: "اکنون")}): ${fa(it.s("appearances") ?: "?")} بازی، ${fa(it.s("goals") ?: "?")} گل"
                }, links = links)
            }
            has(q, "سن", "قد", "ارزش", "قیمت", "پای", "شماره", "ملیت", "کشور", "قرارداد", "چند سال") -> Msg(false, buildList {
                add("$name" + (teamTxt?.let { "، بازیکن $it" } ?: "") + (d.path("positionDescription.primaryPosition").s("label")?.let { "، پست: ${tr(it)}" } ?: ""))
                inf["Age"]?.let { add("سن: ${fa(it)}") }; inf["Height"]?.let { add("قد: ${fa(it)}") }
                inf["Preferred foot"]?.let { add("پای تخصصی: ${if (it == "Left") "چپ" else if (it == "Right") "راست" else it}") }
                inf["Shirt"]?.let { add("شماره پیراهن: ${fa(it)}") }; inf["Country"]?.let { add("کشور: ${faTeamName(0, it) ?: it}") }
                inf["Market value"]?.let { add("ارزش بازار: ${fa(it)}") }; inf["Contract end"]?.let { add("پایان قرارداد: ${fa(it)}") }
            }.joinToString("\n"), links = links)
            else -> Msg(false, listOfNotNull(seasonLine(d), teamTxt?.let { "تیم فعلی: $it." }, inf["Age"]?.let { "سن: ${fa(it)}." }).joinToString(" ")
                .ifEmpty { "آماری برای $name پیدا نشد." }, links = links)
        }
    }

    private suspend fun comparePlayers(x: Int, y: Int): Msg = coroutineScope {
        lastPlayer = x
        val ad = async { playerData(x) }; val bd = async { playerData(y) }
        val a = ad.await(); val b = bd.await()
        fun st(d: JSONObject) = d.o("mainLeague").a("stats").objs().associate { (it.s("title") ?: "") to ((it.opt("value") as? Number)?.toDouble() ?: 0.0) }
        val sa = st(a); val sb = st(b)
        val rows = listOf("Goals" to "گل", "Assists" to "پاس گل", "Matches" to "بازی", "Rating" to "نمره").map { (k, label) ->
            "• $label: ${a.s("name")} ${fa(dec(sa[k], if (k == "Rating") 2 else 0))} | ${b.s("name")} ${fa(dec(sb[k], if (k == "Rating") 2 else 0))}"
        }
        val ga = (sa["Goals"] ?: 0.0) + (sa["Assists"] ?: 0.0); val gb = (sb["Goals"] ?: 0.0) + (sb["Assists"] ?: 0.0)
        val verdict = when { ga > gb -> "${a.s("name")} این فصل در گل و پاس گل جلوتر است."; gb > ga -> "${b.s("name")} این فصل در گل و پاس گل جلوتر است."; else -> "در گل و پاس گل برابرند." }
        Msg(false, "مقایسه این فصل (لیگ اصلی هر بازیکن):\n" + rows.joinToString("\n") + "\n" + verdict,
            links = listOf(Link("مقایسه کامل با نمودار") { it.compare(x, y) }, Link("صفحه ${a.s("name")}") { it.player(x) }, Link("صفحه ${b.s("name")}") { it.player(y) }))
    }

    /* ---------- leagues ---------- */

    private suspend fun league(lg: Int, q: String, day: Int?): Msg {
        lastLeague = lg; lastTeam = null; lastPlayer = null
        val d = Api.fm("leagues", mapOf("id" to lg, "ccode3" to "IRN"), 600_000)
        val name = leagueFa(lg, d.o("details").s("name") ?: "")
        val links = listOf(Link("صفحه $name") { it.league(lg) })
        val rows = d.a("table").objs().firstOrNull()?.path("data.table").a("all").objs()
        fun rowTxt(r: JSONObject) = "${fa(r.i("idx") ?: 0)}. ${teamName(r.i("id") ?: 0, r.s("name") ?: "")}: ${fa(r.i("pts") ?: 0)} امتیاز از ${fa(r.i("played") ?: 0)} بازی"
        fun top(header: String) = d.o("stats").a("players").objs().firstOrNull { it.s("header") == header }?.a("topThree").objs().orEmpty()
        return when {
            day != null || has(q, "بازی های", "بازیهای", "هفته", "برنامه") -> {
                if (day != null) {
                    val list = runCatching { parseMatchList(Api.fm("matches", Api.matchesParams(dayKey(startOfDay(day))), 30_000)) }.getOrDefault(emptyList())
                    val ms = list.filter { it.primaryId == lg }.flatMap { it.matches }
                    return if (ms.isEmpty()) Msg(false, "$name ${dayWord(day)} بازی ندارد.", links = links) else Msg(false, "بازی‌های $name ${dayWord(day)}:", ms, links)
                }
                val all = d.o("fixtures").a("allMatches").objs()
                val from = d.path("fixtures.firstUnplayedMatch").i("firstUnplayedMatchIndex") ?: all.indexOfFirst { !it.path("status").b("finished") }.coerceAtLeast(0)
                val next = all.drop(from).take(10).map { parseMatch(it) }
                if (next.isEmpty()) Msg(false, "بازی بعدی‌ای برای $name ثبت نشده.", links = links) else Msg(false, "بازی‌های پیش روی $name:", next, links)
            }
            has(q, "پاس گل", "پاسور") -> top("Assists").let { t ->
                if (t.isEmpty()) Msg(false, "آمار پاس گل $name هنوز منتشر نشده.", links = links)
                else Msg(false, "بهترین پاسورهای $name:\n" + t.mapIndexed { k, p -> "${fa(k + 1)}. ${p.s("name")} (${teamName(p.i("teamId") ?: 0, p.s("teamName") ?: "")}) ${fa(p.i("value") ?: 0)} پاس گل" }.joinToString("\n"), links = links)
            }
            has(q, "آقای گل", "گلزن", "گل زن", "بهترین بازیکن") -> top("Top scorer").let { t ->
                if (t.isEmpty()) Msg(false, "آمار گلزنان $name هنوز منتشر نشده.", links = links)
                else Msg(false, "گلزنان برتر $name:\n" + t.mapIndexed { k, p -> "${fa(k + 1)}. ${p.s("name")} (${teamName(p.i("teamId") ?: 0, p.s("teamName") ?: "")}) ${fa(p.i("value") ?: 0)} گل" }.joinToString("\n"),
                    links = t.firstOrNull()?.let { p -> listOf(Link("صفحه ${p.s("name")}") { it.player(p.i("id") ?: 0) }) }.orEmpty() + links)
            }
            rows.isEmpty() -> Msg(false, "جدول $name در دسترس نیست.", links = links)
            has(q, "سقوط", "ته جدول", "آخر جدول", "قعر") -> Msg(false, "ته جدول $name:\n" + rows.takeLast(4).joinToString("\n") { rowTxt(it) }, links = links)
            numberIn(q) != null && has(q, "تیم", "رتبه", "چندم", "کیه", "کیست") -> {
                val n = numberIn(q)!!.let { if (it == -1) rows.size else it }
                rows.getOrNull(n - 1)?.let { Msg(false, "رتبه ${fa(n)} $name: " + rowTxt(it).substringAfter(". "), links = links) }
                    ?: Msg(false, "$name فقط ${fa(rows.size)} تیم دارد.", links = links)
            }
            else -> Msg(false, "صدر جدول $name:\n" + rows.take(5).joinToString("\n") { rowTxt(it) } +
                (top("Top scorer").firstOrNull()?.let { "\nآقای گل: ${it.s("name")} با ${fa(it.i("value") ?: 0)} گل" } ?: ""), links = links)
        }
    }

    /* ---------- days ---------- */

    private suspend fun dayMatches(day: Int, q: String): Msg {
        val list = parseMatchList(Api.fm("matches", Api.matchesParams(dayKey(startOfDay(day))), 20_000))
        if (has(q, "زنده", "الان")) {
            val live = list.flatMap { b -> b.matches.filter { it.live }.map { it to b.primaryId } }.sortedByDescending { (_, l) -> if (l in BIG) 1 else 0 }.map { it.first }
            return if (live.isEmpty()) Msg(false, "الان هیچ بازی زنده‌ای در جریان نیست.") else Msg(false, "${fa(live.size)} بازی الان زنده است؛ مهم‌ترین‌ها:", live.take(8))
        }
        val big = list.filter { it.primaryId in BIG }.flatMap { it.matches }.sortedBy { it.time }
        val mine = list.flatMap { it.matches }.filter { app.goallive.live.data.Store.follows(it) && it !in big }
        val shown = (mine + big).take(10)
        return if (shown.isEmpty()) Msg(false, "${dayWord(day)} در لیگ‌های مهم بازی‌ای نیست.")
        else Msg(false, "بازی‌های مهم ${dayWord(day)} (${fa(list.sumOf { it.matches.size })} بازی در کل):", shown)
    }
}
