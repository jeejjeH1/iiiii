package app.goallive.live.ui

import app.goallive.live.L

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

/** A player's name in Persian when we know it (مسی for Lionel Messi). */
private fun playerFa(name: String?): String {
    val n = name ?: return ""
    if (app.goallive.live.Lang.en) return n
    return PLAYERS.entries.filter { it.value.equals(n, true) }.maxByOrNull { it.key.length }?.key ?: app.goallive.live.data.FaNames.of(n)
}

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
    523 -> L("لیگ برتر ایران"); 47 -> L("لیگ برتر انگلیس"); 87 -> L("لالیگا"); 55 -> L("سری آ"); 54 -> L("بوندسلیگا"); 53 -> L("لوشامپیونه")
    42 -> L("لیگ قهرمانان اروپا"); 73 -> L("لیگ اروپا"); 10216 -> L("لیگ کنفرانس اروپا"); 525 -> L("لیگ نخبگان آسیا"); else -> leagueName(id, name)
}

/** Everyday Persian nicknames, searched by their English name. */
private val NICKS = mapOf(
    "بارسا" to "Barcelona", "رئال" to "Real Madrid", "سیتی" to "Manchester City", "منچستر سیتی" to "Manchester City",
    "منچستر یونایتد" to "Manchester United", "یونایتد" to "Manchester United", "من یونایتد" to "Manchester United", "بایرن" to "Bayern Munich",
    "پی اس جی" to "Paris Saint-Germain", "پاری سن ژرمن" to "Paris Saint-Germain", "اتلتیکو" to "Atletico Madrid", "یووه" to "Juventus",
    "دورتموند" to "Borussia Dortmund", "تیم ملی" to "Iran", "تیم ملی ایران" to "Iran", "ایران" to "Iran", "آث میلان" to "AC Milan",
    "اینتر میلان" to "Inter", "لیور" to "Liverpool", "آرسنال" to "Arsenal", "چلسی" to "Chelsea", "تاتنهام" to "Tottenham",
)

/** FotMob's English return dates ("Early January 2027", "Back in training") in Persian. */
/** FotMob's expected return ("Late October", "Doubtful") in Persian. */
fun returnFa(s: String): String {
    if (app.goallive.live.Lang.en) return s
    var t = s
    listOf("Back in training" to "به تمرین برگشته", "Doubtful" to "مشکوک به بازی", "Unknown" to "نامشخص", "Early" to "اوایل", "Mid" to "اواسط",
        "Late" to "اواخر", "January" to "ژانویه", "February" to "فوریه", "March" to "مارس", "April" to "آوریل", "May" to "مه", "June" to "ژوئن",
        "July" to "ژوئیه", "August" to "اوت", "September" to "سپتامبر", "October" to "اکتبر", "November" to "نوامبر", "December" to "دسامبر",
    ).forEach { (en, f) -> t = t.replace(en, f, ignoreCase = true) }
    return fa(t)
}

private val BIG = setOf(42, 47, 87, 55, 54, 53, 73, 523, 77, 50, 525, 10216)

/* ---------- English questions ---------- */

private val EN_LEAGUES = listOf(
    listOf("persian gulf", "iran league", "iranian league", "pro league") to 523,
    listOf("afc champions league", "asian champions league", "acl elite") to 525,
    listOf("champions league", "ucl") to 42,
    listOf("europa league") to 73,
    listOf("conference league") to 10216,
    listOf("laliga", "la liga", "spanish league") to 87,
    listOf("serie a", "italian league") to 55,
    listOf("bundesliga", "german league") to 54,
    listOf("ligue 1", "french league") to 53,
    listOf("premier league", "epl", "english league") to 47,
)

private val EN_NICKS = mapOf(
    "barca" to "Barcelona", "man city" to "Manchester City", "man utd" to "Manchester United", "man united" to "Manchester United",
    "psg" to "Paris Saint-Germain", "spurs" to "Tottenham", "atleti" to "Atletico Madrid", "juve" to "Juventus", "bayern" to "Bayern Munich",
    "dortmund" to "Borussia Dortmund", "bvb" to "Borussia Dortmund", "milan" to "AC Milan", "inter" to "Inter", "real" to "Real Madrid",
    "iran" to "Iran", "team melli" to "Iran",
)

/** The Persian words the question rules look for, for each English phrase in [raw]. */
private fun englishHints(raw: String): String {
    val q = " " + raw.lowercase().replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ") + " "
    fun hit(vararg ws: String) = ws.any { q.contains(" $it ") || (it.endsWith("*") && q.contains(" " + it.dropLast(1))) }
    val out = ArrayList<String>()
    if (hit("help", "what can you do", "what do you know")) out += "راهنما"
    if (hit("day after tomorrow")) out += "پس فردا" else if (hit("tomorrow")) out += "فردا"
    if (hit("day before yesterday")) out += "پریروز" else if (hit("yesterday", "last night")) out += "دیروز"
    if (hit("today", "tonight")) out += "امروز"
    if (hit("live", "now", "right now", "currently")) out += "زنده"
    if (hit("next", "upcoming")) out += "بعدی"
    if (hit("when", "what time", "kick off", "kickoff")) out += "کی"
    if (hit("fixtures", "fixture", "schedule", "next matches", "next games", "upcoming matches")) out += "برنامه"
    if (hit("last match", "last game", "previous match", "previous game")) out += "آخرین بازی"
    if (hit("results", "recent matches", "recent games", "recent results")) out += "نتایج"
    if (hit("result", "score")) out += "نتیجه"
    if (hit("position", "rank", "ranking", "standing*", "table", "place", "points")) out += "چندم"
    if (hit("top of", "leader*", "first place")) out += "صدر"
    if (hit("bottom", "relegation", "last place")) out += "ته جدول"
    if (hit("form", "how are", "how is")) out += "فرم"
    if (hit("coach", "manager", "head coach", "boss")) out += "سرمربی"
    if (hit("injur*", "injured", "out injured", "absent", "missing")) out += "مصدوم"
    if (hit("squad", "players", "roster", "lineup", "line up")) out += "بازیکنان"
    if (hit("stadium", "ground", "venue", "arena")) out += "ورزشگاه"
    if (hit("trophies", "trophy", "titles", "honours", "honors", "champion*", "won")) out += "افتخار"
    if (hit("attack", "defence", "defense", "stats", "statistics", "possession", "clean sheet*", "goals conceded")) out += "آمار"
    if (hit("home")) out += "خانه"
    if (hit("away")) out += "خارج"
    if (hit("predict*", "prediction", "who will win", "who wins", "chance*", "favourite", "favorite")) out += "پیش بینی"
    if (hit("head to head", "h2h", "meeting*")) out += "رودررو"
    if (hit("top scorer*", "scorer*", "golden boot", "best player", "star")) out += "گلزن"
    if (hit("assist*", "playmaker")) out += "پاس گل"
    if (hit("goal*", "scored")) out += "گل"
    if (hit("rating*")) out += "نمره"
    if (hit("age", "old", "height", "tall", "value", "worth", "price", "foot", "contract", "nationality", "country", "shirt", "number")) out += "سن"
    if (hit("clubs", "career", "previous clubs", "history")) out += "باشگاه"
    if (hit("compare", "comparison", "vs", "versus", "or")) out += "مقایسه"
    if (hit("matches", "games")) out += "بازی ها"
    if (hit("match", "game", "play*", "playing")) out += "بازی"
    if (hit("team")) out += "تیم"
    // "fourth" → 4 (numbers like "4th" are read as they are)
    listOf("first" to 1, "second" to 2, "third" to 3, "fourth" to 4, "fifth" to 5, "sixth" to 6, "seventh" to 7, "eighth" to 8, "ninth" to 9, "tenth" to 10)
        .firstOrNull { hit(it.first) }?.let { out += "رتبه ${it.second}" }
    return out.joinToString(" ")
}

/** "امروز / فردا / دیروز …" as a day offset, or null when no day is named. */
private fun dayOffset(q: String): Int? = when {
    has(q, "پس فردا") -> 2
    has(q, "پریروز") -> -2
    has(q, "فردا") -> 1
    has(q, "دیروز", "دیشب") -> -1
    has(q, "امروز", "امشب") -> 0
    else -> null
}

private fun dayWord(off: Int) = when (off) { 0 -> L("امروز"); 1 -> L("فردا"); -1 -> L("دیروز"); 2 -> L("پس‌فردا"); -2 -> L("پریروز"); else -> "" }

/** A plain number written in the question ("تیم چهارم"، "رتبه 5"). */
private fun numberIn(q: String): Int? {
    Regex("\\d+").find(q)?.value?.toIntOrNull()?.let { return it }
    val words = listOf("اول" to 1, "دوم" to 2, "سوم" to 3, "چهارم" to 4, "پنجم" to 5, "ششم" to 6, "هفتم" to 7, "هشتم" to 8, "نهم" to 9, "دهم" to 10,
        "یازدهم" to 11, "دوازدهم" to 12, "سیزدهم" to 13, "چهاردهم" to 14, "پانزدهم" to 15, "شانزدهم" to 16, "هفدهم" to 17, "هجدهم" to 18,
        "نوزدهم" to 19, "بیستم" to 20, "آخر" to -1)
    return words.sortedByDescending { it.first.length }.firstOrNull { word(q, it.first) }?.second
}

private val HELP = L("""این‌ها را می‌توانم جواب بدهم:
• تیم: بازی بعدی، نتیجه آخرین بازی، نتایج و برنامه، رتبه در جدول، فرم، گلزن و پاسور برتر، سرمربی، بازیکنان، مصدوم‌ها، ورزشگاه، افتخارات، آمار حمله و دفاع، کارنامه در خانه و بیرون
• دو تیم: مقایسه، رودررو، و اینکه بازی بینشان را به حساب آمار کی می‌برد
• بازیکن: گل و پاس گل و نمره این فصل، سن و قد و ارزش، آخرین بازی، باشگاه‌های قبلی، مقایسه دو بازیکن
• لیگ: صدر و ته جدول، تیم nام، آقای گل، بهترین پاسور، بازی‌های هفته بعد
• روزها: بازی‌های امروز، فردا، دیروز و بازی‌های زنده
مثال: «پرسپولیس و استقلال را مقایسه کن»، «گلزن برتر سپاهان کیست؟»، «تیم چهارم لالیگا»، «بازی‌های فردا»""")

val EXAMPLES = listOf(
    L("بازی بعدی پرسپولیس کی است؟"), L("طارمی امسال چند گل زده؟"), L("استقلال چندم است؟"), L("آقای گل لیگ برتر انگلیس"),
    L("پرسپولیس و استقلال را مقایسه کن"), L("بازی‌های فردا"), L("مصدوم‌های رئال مادرید"), L("راهنما"),
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
        // English questions: the Persian words the rules look for are added after the question; names stay as typed
        val q = norm(if (app.goallive.live.Lang.en) raw + " " + englishHints(raw) else raw)
        if (q.isEmpty()) return Msg(false, L("سؤالتان را بنویسید."))
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
        return Msg(false, L("متوجه نشدم 🤔 نام یک تیم، بازیکن یا لیگ را بنویسید؛ یا «راهنما» را بزنید تا ببینید چه چیزهایی را می‌توانم جواب بدهم."))
    }

    /* ---------- finding names ---------- */

    private suspend fun findTeams(q: String): List<Pair<Int, String>> {
        var rest = " $q "
        val out = ArrayList<Pair<Int, String>>()
        // clubs with themes (have ids), longest names first so "استقلال خوزستان" beats "استقلال"
        val clubs = Clubs.ids.mapNotNull { id -> Clubs.get(id)?.let { id to it.fa } }
        // in English the names to look for are FotMob's own ones
        val named = (clubs.map { Triple(it.first, it.second, null as String?) } +
            (if (app.goallive.live.Lang.en) TEAMS_FA.keys.flatMap { k -> listOf(k, k.replace(Regex(" (FC|SC|F\\.C\\.|CF)$"), "")).distinct().map { Triple(0, it, k) } } + EN_NICKS.map { Triple(0, it.key, it.value) }
            else TEAMS_FA.entries.map { Triple(0, it.value, it.key) } + NICKS.entries.map { Triple(0, it.key, it.value) })).sortedByDescending { it.second.length }
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
        val names = if (app.goallive.live.Lang.en) PLAYERS.values.distinct().flatMap { listOf(it to it, it.substringAfterLast(' ') to it) }.distinctBy { it.first }
            else PLAYERS.entries.map { it.key to it.value }
        for ((fa, en) in names.sortedByDescending { it.first.length }) {
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

    private fun findLeague(q: String): Int? = (if (app.goallive.live.Lang.en) EN_LEAGUES else LEAGUES).firstOrNull { (words, _) -> has(q, *words.toTypedArray()) }?.second

    private fun playerQuestion(q: String) = has(q, "گل", "پاس", "نمره", "سن", "قد", "ارزش", "قیمت", "مصدوم", "آخرین بازی", "باشگاه", "قرارداد", "شماره", "پا ")

    /* ---------- teams ---------- */

    private class TeamData(val id: Int, val name: String, val d: JSONObject) {
        val ov get() = d.o("overview")
        private val tables get() = d.a("table").objs().flatMap { t -> listOfNotNull(t.path("data.table")) + t.o("data").a("tables").objs().mapNotNull { it.o("table") } }
        val table get() = tables.firstOrNull { tb -> tb.a("all").objs().any { it.i("id") == id } }
        val row get() = table?.a("all").objs().firstOrNull { it.i("id") == id }
        val leagueName get() = d.a("table").objs().firstOrNull()?.o("data").let { leagueFa(it.i("leagueId") ?: 0, it.s("leagueName") ?: L("لیگ")) }
        val fixtures get() = d.path("fixtures.allFixtures").a("fixtures").objs()
        val form get() = ov.a("teamForm").objs().takeLast(5).mapNotNull { it.s("resultString") }
        /** Season leader; when FotMob has no stats page for the league (e.g. Iran), counted from the squad list. */
        fun stat(header: String): JSONObject? {
            d.o("stats").a("players").objs().firstOrNull { it.s("header") == header }?.o("participant")?.let { return it }
            val key = when (header) { "Top scorer" -> "goals"; "Assists" -> "assists"; "FotMob rating" -> "rating"; else -> return null }
            val best = d.o("squad").a("squad").objs().flatMap { it.a("members").objs() }.maxByOrNull { it.d(key) ?: 0.0 } ?: return null
            if ((best.d(key) ?: 0.0) <= 0.0) return null
            return JSONObject().put("id", best.i("id")).put("name", best.s("name")).put("value", best.d(key))
        }
        fun rank(header: String) = d.o("stats").a("teams").objs().firstOrNull { it.s("header") == header }?.o("participant")
        val ppg: Double get() = row?.let { (it.i("pts") ?: 0).toDouble() / (it.i("played") ?: 1).coerceAtLeast(1) } ?: 1.2
    }

    private suspend fun teamData(t: Pair<Int, String>) = TeamData(t.first, t.second, Api.fm("teams", mapOf("id" to t.first, "ccode3" to "IRN"), 300_000))

    private fun formFa(f: List<String>) = f.joinToString(L("، ")) { when (it) { "W" -> L("برد"); "L" -> L("باخت"); else -> L("مساوی") } }

    private fun opp(m: MatchItem, id: Int) = if (m.home.id == id) teamName(m.away.id, m.away.name) else teamName(m.home.id, m.home.name)

    private fun describe(m: MatchItem, id: Int, name: String): String {
        val where = if (m.home.id == id) L("در خانه") else L("در خانه حریف")
        return when {
            m.live -> L("{0} همین الان با {1} بازی دارد: {2}-{3}، {4}.", name, opp(m, id), fa(m.home.score ?: 0), fa(m.away.score ?: 0), statusText(m))
            m.finished -> {
                val us = if (m.home.id == id) m.home.score ?: 0 else m.away.score ?: 0
                val them = if (m.home.id == id) m.away.score ?: 0 else m.home.score ?: 0
                val r = when { us > them -> L("برد"); us < them -> L("باخت"); else -> L("مساوی کرد") }
                L("{0} {1} {2} مقابل {3} {4}-{5} {6}.", name, persianDate(m.time, "EEEE d MMMM"), where, opp(m, id), fa(us), fa(them), r)
            }
            else -> {
                val days = ((m.time - startOfDay(0)) / 86_400_000L).toInt()
                val whenTxt = when (days) { 0 -> L("امروز"); 1 -> L("فردا"); else -> persianDate(m.time, "EEEE d MMMM") }
                L("بازی بعدی {0} مقابل {1} است، {2} ساعت {3}، {4}", name, opp(m, id), whenTxt, fa(timeOf(m.time)), where) +
                    (if (m.leagueName.isNotEmpty()) " (${m.leagueName})" else "") + "."
            }
        }
    }

    /** Goal scorers of a finished or live match, as one line. */
    private suspend fun scorers(matchId: Int): String? = runCatching {
        val d = Api.fm("matchDetails", mapOf("matchId" to matchId), 120_000)
        val goals = d.path("content.matchFacts.events").a("events").objs().filter { it.s("type") == "Goal" }
        if (goals.isEmpty()) null else L("گل‌ها: ") + goals.joinToString(L("، ")) {
            playerFa(it.o("player").s("name") ?: it.s("nameStr") ?: "") + " " + fa((it.s("time") ?: "") + "'") + if (it.b("ownGoal")) L(" (به خودی)") else ""
        }
    }.getOrNull()

    private suspend fun team(t: Pair<Int, String>, q: String, day: Int?): Msg {
        lastTeam = t; lastPlayer = null
        val (id, name) = t
        val td = teamData(t)
        val links = listOf(Link(L("صفحه {0}", name)) { it.team(id) })
        fun matchOf(key: String) = td.ov.o(key)?.let { parseMatch(it) }
        val row = td.row
        val position = row?.let {
            L("{0} در {1} رتبه {2} را دارد با {3} امتیاز از {4} بازی ", name, td.leagueName, fa(it.i("idx") ?: 0), fa(it.i("pts") ?: 0), fa(it.i("played") ?: 0)) +
                L("({0} برد، {1} مساوی، {2} باخت، گل {3}).", fa(it.i("wins") ?: 0), fa(it.i("draws") ?: 0), fa(it.i("losses") ?: 0), fa(it.s("scoresStr") ?: ""))
        }
        return when {
            // a specific day
            day != null -> {
                val list = runCatching { parseMatchList(Api.fm("matches", Api.matchesParams(dayKey(startOfDay(day))), 30_000)) }.getOrDefault(emptyList())
                val m = list.flatMap { it.matches }.firstOrNull { it.home.id == id || it.away.id == id }
                if (m == null) Msg(false, L("{0} {1} بازی ندارد.", name, dayWord(day)) + (matchOf("nextMatch")?.let { " " + describe(it, id, name) } ?: ""), links = links)
                else Msg(false, describe(m, id, name) + (if (m.started) scorers(m.id)?.let { "\n$it" } ?: "" else ""), listOf(m), links)
            }
            has(q, "پیش بینی", "پیشبینی", "کی می برد", "کی میبرد", "می برد", "شانس") -> {
                val next = matchOf("nextMatch") ?: return Msg(false, L("بازی بعدی {0} هنوز اعلام نشده.", name), links = links)
                val oid = if (next.home.id == id) next.away.id else next.home.id
                val other = runCatching { teamData(oid to opp(next, id)) }.getOrNull()
                Msg(false, describe(next, id, name) + "\n" + predict(td, other, next.home.id == id), listOf(next), links)
            }
            has(q, "برنامه", "بازی های بعدی", "بازیهای بعدی", "بازی های آینده", "چند بازی بعد") -> {
                val up = td.fixtures.map { parseMatch(it) }.filter { !it.started && !it.cancelled }.take(5)
                if (up.isEmpty()) Msg(false, L("برنامه بازی‌های بعدی {0} هنوز اعلام نشده.", name), links = links)
                else Msg(false, L("{0} بازی بعدی {1}:", fa(up.size), name), up, links)
            }
            has(q, "نتایج", "بازی های اخیر", "بازیهای اخیر", "بازی های قبلی", "آخرین بازی ها") -> {
                val past = td.fixtures.map { parseMatch(it) }.filter { it.finished }.takeLast(5).reversed()
                Msg(false, L("نتایج اخیر {0} (فرم: {1}):", name, formFa(td.form).ifEmpty { "-" }), past, links)
            }
            has(q, "بعدی", "بعد", "کی بازی", "چه ساعتی", "ساعت چند", "کی بازی داره") ->
                matchOf("nextMatch")?.let { Msg(false, describe(it, id, name), listOf(it), links) } ?: Msg(false, L("بازی بعدی {0} هنوز اعلام نشده.", name), links = links)
            has(q, "نتیجه", "قبلی", "آخرین بازی", "چند چند", "چند شد", "بازی آخر") ->
                matchOf("lastMatch")?.let { Msg(false, describe(it, id, name) + (scorers(it.id)?.let { s -> "\n$s" } ?: ""), listOf(it), links) }
                    ?: Msg(false, L("بازی اخیری برای {0} پیدا نشد.", name), links = links)
            has(q, "الان", "زنده") -> {
                val live = td.ov.b("hasOngoingMatch")
                val m = matchOf("nextMatch")?.takeIf { it.live } ?: matchOf("lastMatch")?.takeIf { it.live }
                if (live && m != null) Msg(false, describe(m, id, name), listOf(m), links)
                else Msg(false, L("{0} الان بازی ندارد. ", name) + (matchOf("nextMatch")?.let { describe(it, id, name) } ?: ""), links = links)
            }
            has(q, "مصدوم", "آسیب", "غایب") -> {
                val hurt = td.d.o("squad").a("squad").objs().flatMap { it.a("members").objs() }.filter { it.opt("injury") is JSONObject }
                if (hurt.isEmpty()) Msg(false, L("طبق FotMob، {0} الان بازیکن مصدومی ندارد.", name), links = links)
                else Msg(false, L("مصدوم‌های {0}:\n", name) + hurt.joinToString("\n") { p ->
                    val inj = p.o("injury")
                    "• ${playerFa(p.s("name"))}" + (inj.s("expectedReturn")?.let { L(" (بازگشت: {0})", returnFa(it)) } ?: "")
                }, links = links)
            }
            has(q, "سرمربی", "مربی") -> {
                val coach = td.d.o("squad").a("squad").objs().firstOrNull { it.s("title") == "coach" }?.a("members").objs().firstOrNull()?.s("name")
                Msg(false, coach?.let { L("سرمربی {0}: {1}", name, playerFa(it)) } ?: L("نام سرمربی {0} پیدا نشد.", name), links = links)
            }
            has(q, "پاس گل", "پاسور", "پاس") -> td.stat("Assists")?.let { p ->
                Msg(false, L("بهترین پاسور {0} این فصل {1} است با {2} پاس گل.", name, playerFa(p.s("name")), fa(dec(p.d("value"), 0))), links = listOf(Link(L("صفحه {0}", playerFa(p.s("name")))) { it.player(p.i("id") ?: 0) }) + links)
            } ?: Msg(false, L("آمار پاس گل {0} در دسترس نیست.", name), links = links)
            has(q, "گلزن", "آقای گل", "گل زن", "بهترین بازیکن", "ستاره", "نمره") -> {
                val top = td.stat("Top scorer"); val rated = td.stat("FotMob rating")
                val lines = listOfNotNull(
                    top?.let { L("بهترین گلزن {0} این فصل {1} است با {2} گل.", name, playerFa(it.s("name")), fa(dec(it.d("value"), 0))) },
                    rated?.let { L("بهترین نمره را {0} گرفته ({1}).", playerFa(it.s("name")), fa(dec(it.d("value"), 2))) },
                )
                Msg(false, lines.joinToString("\n").ifEmpty { L("آمار بازیکنان {0} در دسترس نیست.", name) },
                    links = listOfNotNull(top?.let { p -> Link(L("صفحه {0}", playerFa(p.s("name")))) { it.player(p.i("id") ?: 0) } }) + links)
            }
            has(q, "بازیکنان", "بازیکن ها", "لیست", "اسکواد", "ترکیب تیم") -> {
                val groups = td.d.o("squad").a("squad").objs().filter { it.s("title") != "coach" }
                val lines = mapOf("keepers" to L("دروازه‌بان‌ها"), "defenders" to L("مدافعان"), "midfielders" to L("هافبک‌ها"), "attackers" to L("مهاجمان"))
                Msg(false, L("بازیکنان {0}:\n", name) + groups.joinToString("\n") { g ->
                    "${lines[g.s("title")] ?: g.s("title")}: " + g.a("members").objs().joinToString(L("، ")) { playerFa(it.s("name")) }
                }, links = links)
            }
            has(q, "ورزشگاه", "استادیوم", "زمین خانگی") -> {
                val v = td.ov.o("venue"); val w = v.o("widget")
                val pairs = v.a("statPairs")?.let { arr -> (0 until arr.length()).mapNotNull { arr.optJSONArray(it) }.associate { it.optString(0) to it.opt(1) } } ?: emptyMap()
                Msg(false, w?.s("name")?.let { n ->
                    L("ورزشگاه {0}: {1}", name, n) + (w.s("city")?.let { L("، شهر {0}", it) } ?: "") +
                        (pairs["Capacity"]?.let { L("، ظرفیت {0} نفر", fa(it)) } ?: "") + (pairs["Opened"]?.let { L("، افتتاح {0}", fa(it)) } ?: "") + "."
                } ?: L("اطلاعات ورزشگاه {0} پیدا نشد.", name), links = links)
            }
            has(q, "قهرمان", "افتخار", "جام", "عنوان") -> {
                val tl = td.d.o("history").a("trophyList").objs().sortedByDescending { it.i("won") ?: 0 }.filter { (it.i("won") ?: 0) > 0 }
                if (tl.isEmpty()) Msg(false, L("افتخارات {0} در FotMob ثبت نشده.", name), links = links)
                else Msg(false, L("افتخارات {0}:\n", name) + tl.take(8).joinToString("\n") { L("• {0}: {1} قهرمانی", (it.s("name") ?: "").let { n -> tr(n).takeIf { t -> t != n } ?: leagueName(0, n) }, fa(it.i("won") ?: 0)) +
                    (it.s("seasonsWon")?.substringBefore(',')?.let { s -> L(" (آخرین بار {0})", fa(personName(s))) } ?: "") }, links = links)
            }
            has(q, "خانه", "خارج", "بیرون", "میزبان", "مهمان") -> {
                fun rec(k: String) = td.table?.a(k).objs().firstOrNull { it.i("id") == id }?.let {
                    L("{0} برد، {1} مساوی، {2} باخت (گل {3})", fa(it.i("wins") ?: 0), fa(it.i("draws") ?: 0), fa(it.i("losses") ?: 0), fa(it.s("scoresStr") ?: ""))
                }
                Msg(false, listOfNotNull(rec("home")?.let { L("{0} در خانه: {1}", name, it) }, rec("away")?.let { L("{0} بیرون از خانه: {1}", name, it) })
                    .joinToString("\n").ifEmpty { L("کارنامه خانه و بیرون {0} در دسترس نیست.", name) }, links = links)
            }
            has(q, "حمله", "دفاع", "گل زده", "گل خورده", "چند گل", "آمار", "مالکیت", "کلین شیت") -> {
                val r = listOf("Goals per match" to L("گل زده در هر بازی"), "Goals conceded per match" to L("گل خورده در هر بازی"),
                    "Average possession" to L("میانگین مالکیت٪"), "Clean sheets" to L("کلین‌شیت"), "Expected goals" to L("گل مورد انتظار (xG)"), "Big chances" to L("موقعیت عالی"))
                    .mapNotNull { (h, label) -> td.rank(h)?.let { L("• {0}: {1} (رتبه {2} لیگ)", label, dec(it.d("value"), 1), fa(it.i("rank") ?: 0)) } }
                Msg(false, (listOfNotNull(position) + (if (r.isEmpty()) emptyList() else listOf(L("آمار فصل:")) + r)).joinToString("\n")
                    .ifEmpty { L("آمار {0} در دسترس نیست.", name) }, links = links)
            }
            has(q, "فرم", "وضعیت", "حال و روز", "اوضاع") -> Msg(false,
                if (td.form.isEmpty()) L("فرم اخیر {0} در دسترس نیست.", name) else L("پنج بازی اخیر {0} (از قدیم به جدید): {1}.", name, formFa(td.form)) +
                    (position?.let { "\n$it" } ?: ""), links = links)
            has(q, "چندم", "رتبه", "جدول", "امتیاز", "صدر") -> {
                val rows = td.table?.a("all").objs().orEmpty()
                val idx = rows.indexOfFirst { it.i("id") == id }
                val around = if (idx < 0) "" else L("\nاطراف {0} در جدول:\n", name) + rows.subList((idx - 2).coerceAtLeast(0), (idx + 3).coerceAtMost(rows.size)).joinToString("\n") {
                    L("{0}. {1}: {2} امتیاز", fa(it.i("idx") ?: 0), teamName(it.i("id") ?: 0, it.s("name") ?: ""), fa(it.i("pts") ?: 0))
                }
                Msg(false, (position ?: L("{0} در جدول لیگی پیدا نشد.", name)) + around, links = links)
            }
            else -> {
                val last = matchOf("lastMatch"); val next = matchOf("nextMatch")
                Msg(false, listOfNotNull(position, td.form.takeIf { it.isNotEmpty() }?.let { L("فرم اخیر: {0}.", formFa(it)) }, last?.let { describe(it, id, name) }, next?.let { describe(it, id, name) })
                    .joinToString("\n").ifEmpty { L("اطلاعاتی از {0} پیدا نشد.", name) }, listOfNotNull(next ?: last), links)
            }
        }
    }

    /** "کی می‌برد؟" from the numbers only: points per game, recent form and home advantage. */
    private fun predict(a: TeamData, b: TeamData?, aHome: Boolean): String {
        if (b == null) return L("برای پیش‌بینی، آمار حریف در دسترس نیست.")
        fun power(t: TeamData, home: Boolean) = t.ppg + t.form.count { it == "W" } * 0.15 - t.form.count { it == "L" } * 0.15 + if (home) 0.3 else 0.0
        val pa = power(a, aHome); val pb = power(b, !aHome)
        val diff = pa - pb
        val share = (50 + diff * 22).coerceIn(15.0, 85.0).roundToInt()
        return when {
            abs(diff) < 0.25 -> L("به حساب آمار، دو تیم تقریباً هم‌سطح‌اند؛ احتمال مساوی بالاست.")
            diff > 0 -> L("به حساب آمار (امتیاز در هر بازی، فرم و میزبانی) {0} شانس بیشتری دارد؛ حدود {1}٪.", a.name, fa(share))
            else -> L("به حساب آمار (امتیاز در هر بازی، فرم و میزبانی) {0} شانس بیشتری دارد؛ حدود {1}٪.", b.name, fa(100 - share))
        } + L(" (این فقط برآورد آماری است، نه قطعی.)")
    }

    private suspend fun twoTeams(x: Pair<Int, String>, y: Pair<Int, String>, q: String): Msg = coroutineScope {
        lastTeam = x
        val ad = async { teamData(x) }; val bd = async { teamData(y) }
        val a = ad.await(); val b = bd.await()
        val links = listOf(Link(L("صفحه {0}", a.name)) { it.team(a.id) }, Link(L("صفحه {0}", b.name)) { it.team(b.id) })
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
                    L("رودررو: {0} {1} برد، {2} {3} برد، {4} مساوی.", a.name, fa(aWins), b.name, fa(bWins), fa(draws))
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
            t.row?.let { append(L("رتبه {0} در {1}، {2} امتیاز از {3} بازی، گل {4}", fa(it.i("idx") ?: 0), t.leagueName, fa(it.i("pts") ?: 0), fa(it.i("played") ?: 0), fa(it.s("scoresStr") ?: ""))) }
            if (t.form.isNotEmpty()) append(L("؛ فرم: {0}", formFa(t.form)))
            t.stat("Top scorer")?.let { append(L("؛ گلزن برتر: {0} ({1})", playerFa(it.s("name")), fa(it.i("value") ?: 0))) }
        }
        val better = when {
            a.ppg > b.ppg + 0.2 -> L("{0} این فصل نتایج بهتری گرفته.", a.name)
            b.ppg > a.ppg + 0.2 -> L("{0} این فصل نتایج بهتری گرفته.", b.name)
            else -> L("دو تیم این فصل نتایج نزدیکی داشته‌اند.")
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
        return L("{0} در {1} (فصل {2}): ", playerFa(d.s("name")), leagueFa(ml.i("leagueId") ?: 0, ml.s("leagueName") ?: L("لیگ")), fa(ml.s("season") ?: "")) +
            L("{0} گل و {1} پاس گل در {2} بازی", fa(st["Goals"] ?: 0), fa(st["Assists"] ?: 0), fa(st["Matches"] ?: 0)) +
            (st["Minutes played"]?.let { L("، {0} دقیقه", fa(it)) } ?: "") + (if (rating != null && rating > 0) L("، میانگین نمره {0}", dec(rating, 2)) else "") + "."
    }

    private suspend fun player(id: Int, q: String): Msg {
        lastPlayer = id
        val d = playerData(id)
        val name = playerFa(d.s("name"))
        val team = d.o("primaryTeam")
        val inf = info(d)
        val links = listOfNotNull(Link(L("صفحه {0}", name)) { it.player(id) }, team?.i("teamId")?.let { tid -> Link(L("صفحه {0}", teamName(tid, team.s("teamName") ?: ""))) { it.team(tid) } })
        val teamTxt = team?.s("teamName")?.let { teamName(team.i("teamId") ?: 0, it) }
        return when {
            has(q, "مصدوم", "آسیب") -> Msg(false, d.o("injuryInformation")?.let { L("{0} مصدوم است", name) + (it.s("name")?.let { n -> " (${tr(n)})" } ?: "") +
                (it.o("expectedReturn")?.s("fallback")?.let { r -> L("؛ بازگشت: {0}", returnFa(r)) } ?: "") + "." } ?: L("{0} طبق FotMob مصدوم نیست.", name), links = links)
            has(q, "آخرین بازی", "بازی قبلی", "بازی آخر", "دیشب") -> {
                val m = d.a("recentMatches").objs().firstOrNull()
                Msg(false, m?.let {
                    L("آخرین بازی {0}: {1} {2}-{3} {4}", name, teamName(it.i("teamId") ?: 0, it.s("teamName") ?: ""), fa(it.i("homeScore") ?: 0), fa(it.i("awayScore") ?: 0), teamName(it.i("opponentTeamId") ?: 0, it.s("opponentTeamName") ?: "")) +
                        L(" ({0})؛ {1} دقیقه بازی، {2} گل، {3} پاس گل", it.s("leagueName") ?: "", fa(it.i("minutesPlayed") ?: 0), fa(it.i("goals") ?: 0), fa(it.i("assists") ?: 0)) +
                        (it.o("ratingProps")?.s("rating")?.let { r -> L("، نمره {0}", fa(r)) } ?: "") + "."
                } ?: L("بازی اخیری برای {0} پیدا نشد.", name), links = links)
            }
            has(q, "باشگاه", "تیم های قبلی", "تیمهای قبلی", "کارنامه", "سابقه") -> {
                val career = d.path("careerHistory.careerItems.senior").a("teamEntries").objs()
                Msg(false, if (career.isEmpty()) L("سابقه باشگاهی {0} پیدا نشد.", name) else L("باشگاه‌های {0}:\n", name) + career.take(8).joinToString("\n") {
                    L("• {0} ({1}-{2}): {3} بازی، {4} گل", teamName(it.i("teamId") ?: 0, it.s("team") ?: ""), fa(it.s("startDate")?.take(4) ?: ""), fa(it.s("endDate")?.take(4) ?: L("اکنون")), fa(it.s("appearances") ?: "?"), fa(it.s("goals") ?: "?"))
                }, links = links)
            }
            has(q, "سن", "قد", "ارزش", "قیمت", "پای", "شماره", "ملیت", "کشور", "قرارداد", "چند سال") -> Msg(false, buildList {
                add("$name" + (teamTxt?.let { L("، بازیکن {0}", it) } ?: "") + (d.path("positionDescription.primaryPosition").s("label")?.let { L("، پست: {0}", tr(it)) } ?: ""))
                inf["Age"]?.let { add(L("سن: {0}", fa(it))) }; inf["Height"]?.let { add(L("قد: {0}", fa(it))) }
                inf["Preferred foot"]?.let { add(L("پای تخصصی: {0}", if (it == "Left") L("چپ") else if (it == "Right") L("راست") else it)) }
                inf["Shirt"]?.let { add(L("شماره پیراهن: {0}", fa(it))) }; inf["Country"]?.let { add(L("کشور: {0}", faTeamName(0, it) ?: it)) }
                inf["Market value"]?.let { add(L("ارزش بازار: {0}", fa(it))) }; inf["Contract end"]?.let { add(L("پایان قرارداد: {0}", fa(it))) }
            }.joinToString("\n"), links = links)
            else -> Msg(false, listOfNotNull(seasonLine(d), teamTxt?.let { L("تیم فعلی: {0}.", it) }, inf["Age"]?.let { L("سن: {0}.", fa(it)) }).joinToString(" ")
                .ifEmpty { L("آماری برای {0} پیدا نشد.", name) }, links = links)
        }
    }

    private suspend fun comparePlayers(x: Int, y: Int): Msg = coroutineScope {
        lastPlayer = x
        val ad = async { playerData(x) }; val bd = async { playerData(y) }
        val a = ad.await(); val b = bd.await()
        fun st(d: JSONObject) = d.o("mainLeague").a("stats").objs().associate { (it.s("title") ?: "") to ((it.opt("value") as? Number)?.toDouble() ?: 0.0) }
        val sa = st(a); val sb = st(b)
        val na = playerFa(a.s("name")); val nb = playerFa(b.s("name"))
        fun line(n: String, st: Map<String, Double>) = L("• {0}: {1} گل، {2} پاس گل در {3} بازی", n, fa(dec(st["Goals"], 0)), fa(dec(st["Assists"], 0)), fa(dec(st["Matches"], 0))) +
            (st["Rating"]?.takeIf { it > 0 }?.let { L("، نمره {0}", fa(dec(it, 2))) } ?: "")
        val rows = listOf(line(na, sa), line(nb, sb))
        val ga = (sa["Goals"] ?: 0.0) + (sa["Assists"] ?: 0.0); val gb = (sb["Goals"] ?: 0.0) + (sb["Assists"] ?: 0.0)
        val verdict = when { ga > gb -> L("{0} این فصل در گل و پاس گل جلوتر است.", na); gb > ga -> L("{0} این فصل در گل و پاس گل جلوتر است.", nb); else -> L("در گل و پاس گل برابرند.") }
        Msg(false, L("مقایسه این فصل (لیگ اصلی هر بازیکن):\n") + rows.joinToString("\n") + "\n" + verdict,
            links = listOf(Link(L("مقایسه کامل با نمودار")) { it.compare(x, y) }, Link(L("صفحه {0}", na)) { it.player(x) }, Link(L("صفحه {0}", nb)) { it.player(y) }))
    }

    /* ---------- leagues ---------- */

    private suspend fun league(lg: Int, q: String, day: Int?): Msg {
        lastLeague = lg; lastTeam = null; lastPlayer = null
        val d = Api.fm("leagues", mapOf("id" to lg, "ccode3" to "IRN"), 600_000)
        val name = leagueFa(lg, d.o("details").s("name") ?: "")
        val links = listOf(Link(L("صفحه {0}", name)) { it.league(lg) })
        val rows = d.a("table").objs().firstOrNull()?.path("data.table").a("all").objs()
        fun rowTxt(r: JSONObject) = L("{0}. {1}: {2} امتیاز از {3} بازی", fa(r.i("idx") ?: 0), teamName(r.i("id") ?: 0, r.s("name") ?: ""), fa(r.i("pts") ?: 0), fa(r.i("played") ?: 0))
        fun top(header: String) = d.o("stats").a("players").objs().firstOrNull { it.s("header") == header }?.a("topThree").objs().orEmpty()
        return when {
            day != null || has(q, "بازی های", "بازیهای", "هفته", "برنامه") -> {
                if (day != null) {
                    val list = runCatching { parseMatchList(Api.fm("matches", Api.matchesParams(dayKey(startOfDay(day))), 30_000)) }.getOrDefault(emptyList())
                    val ms = list.filter { it.primaryId == lg }.flatMap { it.matches }
                    return if (ms.isEmpty()) Msg(false, L("{0} {1} بازی ندارد.", name, dayWord(day)), links = links) else Msg(false, L("بازی‌های {0} {1}:", name, dayWord(day)), ms, links)
                }
                val all = d.o("fixtures").a("allMatches").objs()
                val from = d.path("fixtures.firstUnplayedMatch").i("firstUnplayedMatchIndex") ?: all.indexOfFirst { !it.path("status").b("finished") }.coerceAtLeast(0)
                val next = all.drop(from).take(10).map { parseMatch(it) }
                if (next.isEmpty()) Msg(false, L("بازی بعدی‌ای برای {0} ثبت نشده.", name), links = links) else Msg(false, L("بازی‌های پیش روی {0}:", name), next, links)
            }
            has(q, "پاس گل", "پاسور") -> top("Assists").let { t ->
                if (t.isEmpty()) Msg(false, L("آمار پاس گل {0} هنوز منتشر نشده.", name), links = links)
                else Msg(false, L("بهترین پاسورهای {0}:\n", name) + t.mapIndexed { k, p -> L("{0}. {1} ({2}) {3} پاس گل", fa(k + 1), playerFa(p.s("name")), teamName(p.i("teamId") ?: 0, p.s("teamName") ?: ""), fa(p.i("value") ?: 0)) }.joinToString("\n"), links = links)
            }
            has(q, "آقای گل", "گلزن", "گل زن", "بهترین بازیکن") -> top("Top scorer").let { t ->
                if (t.isEmpty()) Msg(false, L("آمار گلزنان {0} هنوز منتشر نشده.", name), links = links)
                else Msg(false, L("گلزنان برتر {0}:\n", name) + t.mapIndexed { k, p -> L("{0}. {1} ({2}) {3} گل", fa(k + 1), playerFa(p.s("name")), teamName(p.i("teamId") ?: 0, p.s("teamName") ?: ""), fa(p.i("value") ?: 0)) }.joinToString("\n"),
                    links = t.firstOrNull()?.let { p -> listOf(Link(L("صفحه {0}", playerFa(p.s("name")))) { it.player(p.i("id") ?: 0) }) }.orEmpty() + links)
            }
            rows.isEmpty() -> Msg(false, L("جدول {0} در دسترس نیست.", name), links = links)
            has(q, "سقوط", "ته جدول", "آخر جدول", "قعر") -> Msg(false, L("ته جدول {0}:\n", name) + rows.takeLast(4).joinToString("\n") { rowTxt(it) }, links = links)
            numberIn(q) != null && has(q, "تیم", "رتبه", "چندم", "کیه", "کیست") -> {
                val n = numberIn(q)!!.let { if (it == -1) rows.size else it }
                rows.getOrNull(n - 1)?.let { Msg(false, L("رتبه {0} {1}: ", fa(n), name) + rowTxt(it).substringAfter(". "), links = links) }
                    ?: Msg(false, L("{0} فقط {1} تیم دارد.", name, fa(rows.size)), links = links)
            }
            else -> Msg(false, L("صدر جدول {0}:\n", name) + rows.take(5).joinToString("\n") { rowTxt(it) } +
                (top("Top scorer").firstOrNull()?.let { L("\nآقای گل: {0} با {1} گل", playerFa(it.s("name")), fa(it.i("value") ?: 0)) } ?: ""), links = links)
        }
    }

    /* ---------- days ---------- */

    private suspend fun dayMatches(day: Int, q: String): Msg {
        val list = parseMatchList(Api.fm("matches", Api.matchesParams(dayKey(startOfDay(day))), 20_000))
        if (has(q, "زنده", "الان")) {
            val live = list.flatMap { b -> b.matches.filter { it.live }.map { it to b.primaryId } }.sortedByDescending { (_, l) -> if (l in BIG) 1 else 0 }.map { it.first }
            return if (live.isEmpty()) Msg(false, L("الان هیچ بازی زنده‌ای در جریان نیست.")) else Msg(false, L("{0} بازی الان زنده است؛ مهم‌ترین‌ها:", fa(live.size)), live.take(8))
        }
        val big = list.filter { it.primaryId in BIG }.flatMap { it.matches }.sortedBy { it.time }
        val mine = list.flatMap { it.matches }.filter { app.goallive.live.data.Store.follows(it) && it !in big }
        val shown = (mine + big).take(10)
        return if (shown.isEmpty()) Msg(false, L("{0} در لیگ‌های مهم بازی‌ای نیست.", dayWord(day)))
        else Msg(false, L("بازی‌های مهم {0} ({1} بازی در کل):", dayWord(day), fa(list.sumOf { it.matches.size })), shown)
    }
}

/* ---------- Persian search ---------- */

private fun normFa(s: String) = s.replace('ي', 'ی').replace('ك', 'ک').replace("‌", "").replace(" ", "").replace("‌", "").lowercase()

/** Rough Latin letters for a Persian word, for names no list knows (طارمی → tarmi); search is forgiving enough to find many. */
private fun latinOf(fa: String): String {
    val map = mapOf('ا' to "a", 'آ' to "a", 'ب' to "b", 'پ' to "p", 'ت' to "t", 'ث' to "s", 'ج' to "j", 'چ' to "ch", 'ح' to "h", 'خ' to "kh", 'د' to "d",
        'ذ' to "z", 'ر' to "r", 'ز' to "z", 'ژ' to "zh", 'س' to "s", 'ش' to "sh", 'ص' to "s", 'ض' to "z", 'ط' to "t", 'ظ' to "z", 'ع' to "", 'غ' to "gh",
        'ف' to "f", 'ق' to "gh", 'ک' to "k", 'گ' to "g", 'ل' to "l", 'م' to "m", 'ن' to "n", 'و' to "o", 'ه' to "h", 'ی' to "i", 'ئ' to "i", 'ء' to "", ' ' to " ")
    return fa.replace('ي', 'ی').replace('ك', 'ک').map { map[it] ?: if (it.isLetterOrDigit() && it.code < 128) it.toString() else "" }.joinToString("")
}

/**
 * The English names to search FotMob for when the user typed Persian («پرسپولیس», «رونالدو», «لیگ برتر»): every team, player,
 * nickname and league whose Persian name contains what was typed (or the other way round), best matches first, and a rough
 * spelling of the words themselves as a last try.
 */
fun persianSearchTerms(q: String): List<String> {
    val n = normFa(q)
    if (n.length < 2) return emptyList()
    val hits = ArrayList<Pair<Int, String>>()
    fun take(fa: String, en: String) {
        val f = normFa(fa)
        val score = when { f == n -> 0; f.startsWith(n) -> 1; f.contains(n) -> 2; n.contains(f) && f.length >= 3 -> 3; else -> return }
        hits += score to en
    }
    PLAYERS.forEach { (fa, en) -> take(fa, en) }
    NICKS.forEach { (fa, en) -> take(fa, en) }
    TEAMS_FA.forEach { (en, fa) -> take(fa, en) }
    Clubs.ids.forEach { id -> Clubs.get(id)?.let { c -> TEAMS_FA.entries.firstOrNull { it.value == c.fa }?.key?.let { en -> take(c.fa, en) } } }
    listOf(523 to "Persian Gulf Pro League", 47 to "Premier League", 87 to "LaLiga", 55 to "Serie A", 54 to "Bundesliga", 53 to "Ligue 1",
        42 to "Champions League", 73 to "Europa League", 525 to "AFC Champions League").forEach { (id, en) -> take(leagueFa(id, en), en) }
    val terms = hits.sortedBy { it.first }.map { it.second }.distinct().take(3).toMutableList()
    if (terms.isEmpty()) latinOf(q.trim()).trim().takeIf { it.length >= 3 }?.let { terms += it }
    return terms
}
