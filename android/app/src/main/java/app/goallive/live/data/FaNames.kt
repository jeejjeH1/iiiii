package app.goallive.live.data

import java.text.Normalizer

/**
 * FotMob's Latin names (players, coaches, leagues, smaller clubs) in Persian script, for the all-Persian mode.
 * Known names come from dictionaries; the rest are written out letter by letter, the way Persian sports media spell foreign names.
 */
object FaNames {
    private val cache = HashMap<String, String>()

    /** [text] in Persian script, or as it is when it does not look like a name. */
    fun of(text: String): String {
        if (!looksLikeName(text)) return text
        return synchronized(cache) { cache.getOrPut(text) { convert(text) } }
    }

    /** A short run of Latin words: no digits, no Persian, no all-capital abbreviation like VAR or PSG. */
    fun looksLikeName(t: String): Boolean {
        if (t.length > 48 || t.isBlank() || t.trim() in NOT_NAMES) return false
        var latin = false
        for (ch in t) {
            if (ch.isDigit() || ch in '؀'..'ۿ') return false
            if (ch.isLetter()) latin = true else if (ch !in " .'’-·@&()") return false
        }
        if (!latin) return false
        val letters = t.filter { it.isLetter() }
        if (letters.length <= 5 && letters.all { it.isUpperCase() }) return false
        return t.split(' ').size <= 6
    }

    private fun convert(text: String): String {
        TEAM_EN[text.lowercase()]?.let { return it }
        PHRASES[text.lowercase()]?.let { return it }
        var s = " " + text.trim() + " "
        // known phrases inside a longer name ("AFC Champions League Elite Grp. B")
        for ((en, fa) in PHRASE_LIST) s = s.replace(Regex("(?i)(?<=[\\s(])${Regex.escape(en)}(?=[\\s).])"), fa)
        val words = s.trim().split(Regex("\\s+"))
        val out = ArrayList<String>()
        var country: String? = null
        for ((i, w) in words.withIndex()) {
            if (w.any { it in '؀'..'ۿ' }) { out += w; continue }
            val k = w.lowercase().trimEnd('.')
            when {
                k == "grp" || k == "group" -> out += "گروه"
                w.length == 1 && w[0].isUpperCase() && i > 0 -> out += w           // group letter: "گروه A"
                (k == "fc" || k == "cf" || k == "sc") && i > 0 -> {}               // club suffixes are not written in Persian
                i == 0 && COUNTRY[k] != null && words.size > 1 -> country = COUNTRY[k]
                WORDS[k] != null -> out += WORDS[k]!!
                COUNTRY[k] != null -> out += COUNTRY[k]!!
                else -> out += w.split('-', '’', '\'').filter { it.isNotEmpty() }.joinToString(" ") { word(it) }
            }
        }
        // "Australian Championship Grp. A" → "چمپیونشیپ استرالیا گروه A"
        if (country != null) {
            val g = out.indexOf("گروه").takeIf { it >= 0 } ?: out.size
            out.add(g, country)
        }
        return out.filter { it.isNotEmpty() }.joinToString(" ")
    }

    /** One Latin word written in Persian letters. */
    fun word(raw: String): String {
        val lower = raw.lowercase()
        NAMES[lower]?.let { return it }
        // Persian surnames: "-zadeh", "-pour", "-nia", "-far", "-i"
        for ((suf, fa) in SUFFIXES) if (lower.length > suf.length + 2 && lower.endsWith(suf)) {
            val stem = lower.dropLast(suf.length)
            NAMES[stem]?.let { return it + fa }
        }
        var w = Normalizer.normalize(lower.map { FOLD[it] ?: it.toString() }.joinToString(""), Normalizer.Form.NFD)
            .filter { it in 'a'..'z' }
        if (w.isEmpty()) return raw
        val b = StringBuilder()
        // initial s + consonant takes a leading alef: Stamford → استمفورد
        if (w.length > 2 && w[0] == 's' && w[1] !in VOWELS && w[1] != 'h' && w[1] != 'y') { b.append('ا') }
        var i = 0; val n = w.length
        fun vowel(c: Char) = c in VOWELS
        while (i < n) {
            val c = w[i]; val first = i == 0 && b.isEmpty(); val last = i == n - 1
            val two = if (i + 1 < n) w.substring(i, i + 2) else ""
            if (vowel(c) || (c == 'y' && i > 0 && (last || !vowel(w[i + 1])))) {
                when {
                    two == "aa" -> { b.append(if (first) "آ" else "ا"); i += 2; continue }
                    two in setOf("ee", "ea", "ie", "ei", "ey", "ay", "ai", "ii") -> { b.append(if (first) "ای" else "ی"); i += 2; continue }
                    two in setOf("oo", "ou", "uu") -> { b.append(if (first) "او" else "و"); i += 2; continue }
                    two in setOf("au", "ao") -> { b.append(if (first) "آو" else "ائو"); i += 2; continue }
                    two in setOf("oe", "ue") && i + 2 < n -> { b.append(if (first) "او" else "و"); i += 2; continue }
                }
                when (c) {
                    'a' -> b.append(if (first) "آ" else "ا")
                    'e' -> if (first) b.append('ا') else if (last) b.append('ه')        // an e inside a word is not written
                    'i', 'y' -> b.append(if (first) "ای" else "ی")
                    else -> b.append(if (first) "او" else "و")
                }
                i++; continue
            }
            if (c == 'y') { b.append('ی'); i++; continue }
            if (c == 'c' && i + 1 < n && w[i + 1] in "eiy") { b.append('س'); i++; continue }
            val rule = CONS.firstOrNull { w.startsWith(it.first, i) }
            if (rule == null) { i++; continue }
            var fa = rule.second
            if (rule.first == "h" && last) fa = "ه"
            // doubled letters are written once (Messi → مسی)
            if (rule.first.length == 1 && i > 0 && w[i - 1] == c) fa = ""
            b.append(fa); i += rule.first.length
        }
        return b.toString()
    }

    private val NOT_NAMES = setOf("xG", "xGOT", "xA", "xGA", "npxG", "FotMob", "YouTube", "GoalLive", "Opta", "LIVE FOOTBALL")
    private const val VOWELS = "aeiou"
    private val FOLD = mapOf('ø' to "o", 'æ' to "ae", 'ß' to "ss", 'ł' to "l", 'đ' to "dj", 'ı' to "i", 'ś' to "sh", 'ş' to "sh", 'š' to "sh",
        'ž' to "zh", 'ź' to "z", 'ż' to "zh", 'č' to "ch", 'ć' to "ch", 'ç' to "s", 'ğ' to "gh", 'ñ' to "ny", 'œ' to "oe", 'þ' to "th")
    private val CONS = listOf("sch" to "ش", "tch" to "چ", "sh" to "ش", "ch" to "چ", "zh" to "ژ", "kh" to "خ", "gh" to "غ", "ph" to "ف", "th" to "ت",
        "ck" to "ک", "qu" to "کو", "dj" to "ج", "ts" to "تس", "tz" to "تس", "ng" to "نگ", "b" to "ب", "c" to "ک", "d" to "د", "f" to "ف", "g" to "گ",
        "h" to "ه", "j" to "ج", "k" to "ک", "l" to "ل", "m" to "م", "n" to "ن", "p" to "پ", "q" to "ک", "r" to "ر", "s" to "س", "t" to "ت",
        "v" to "و", "w" to "و", "x" to "کس", "z" to "ز")
    private val SUFFIXES = listOf("zadeh" to "‌زاده", "pour" to "‌پور", "nia" to "‌نیا", "far" to "فر", "nejad" to "‌نژاد", "vand" to "وند", "i" to "ی")

    /** Team names FotMob writes in English, from the app's Persian list. */
    private val TEAM_EN: Map<String, String> by lazy { TEAMS_FA.entries.associate { it.key.lowercase() to it.value } }

    private val PHRASES = mapOf(
        "persian gulf" to "لیگ برتر خلیج فارس", "persian gulf pro league" to "لیگ برتر خلیج فارس", "hazfi cup" to "جام حذفی", "azadegan league" to "لیگ آزادگان",
        "premier league" to "لیگ برتر", "laliga" to "لالیگا", "la liga" to "لالیگا", "serie a" to "سری آ", "bundesliga" to "بوندس‌لیگا", "ligue 1" to "لوشامپیونه",
        "champions league" to "لیگ قهرمانان اروپا", "europa league" to "لیگ اروپا", "conference league" to "لیگ کنفرانس", "world cup" to "جام جهانی",
        "fifa world cup" to "جام جهانی", "club world cup" to "جام جهانی باشگاه‌ها", "afc champions league elite" to "لیگ نخبگان آسیا",
        "afc champions league two" to "لیگ قهرمانان آسیا ۲", "afc challenge league" to "لیگ چلنج آسیا", "asian cup" to "جام ملت‌های آسیا",
        "fa cup" to "جام حذفی انگلیس", "efl cup" to "جام اتحادیه انگلیس", "copa del rey" to "کوپا دل ری", "coppa italia" to "کوپا ایتالیا",
        "dfb pokal" to "جام حذفی آلمان", "super cup" to "سوپرجام", "friendlies" to "دوستانه", "club friendlies" to "دوستانه باشگاهی",
        "nations league" to "لیگ ملت‌ها", "euro" to "یورو", "copa america" to "کوپا آمریکا", "championship" to "چمپیونشیپ انگلیس",
    )
    private val PHRASE_LIST = listOf(
        "AFC Champions League Elite" to "لیگ نخبگان آسیا", "AFC Champions League Two" to "لیگ قهرمانان آسیا ۲", "AFC Challenge League" to "لیگ چلنج آسیا",
        "Champions League" to "لیگ قهرمانان", "Europa League" to "لیگ اروپا", "Conference League" to "لیگ کنفرانس", "Nations League" to "لیگ ملت‌ها",
        "Club World Cup" to "جام جهانی باشگاه‌ها", "World Cup" to "جام جهانی", "Premier League" to "لیگ برتر", "Super Cup" to "سوپرجام",
        "Pro League" to "لیگ حرفه‌ای", "Super League" to "سوپرلیگ", "Qualification" to "مقدماتی", "Women" to "زنان", "Play-offs" to "پلی‌آف",
        "Playoff" to "پلی‌آف", "Cup" to "جام", "League" to "لیگ",
    )
    private val WORDS = mapOf(
        "united" to "یونایتد", "city" to "سیتی", "town" to "تاون", "forest" to "فارست", "real" to "رئال", "athletic" to "اتلتیک", "atletico" to "اتلتیکو",
        "sporting" to "اسپورتینگ", "dynamo" to "دینامو", "inter" to "اینتر", "rovers" to "روورز", "wanderers" to "واندررز", "albion" to "آلبیون",
        "hotspur" to "هاتسپر", "villa" to "ویلا", "palace" to "پالاس", "crystal" to "کریستال", "county" to "کانتی", "saint" to "سنت", "st" to "سنت",
        "division" to "دسته", "first" to "یک", "second" to "دو", "liga" to "لیگا", "serie" to "سری", "ligue" to "لیگ", "primera" to "پریمرا",
        "segunda" to "سگوندا", "championship" to "چمپیونشیپ", "youth" to "جوانان", "reserves" to "ذخیره‌ها", "and" to "و", "de" to "د", "van" to "فن",
        "von" to "فون", "der" to "در", "al" to "ال", "el" to "ال", "jr" to "جونیور", "junior" to "جونیور",
    )
    private val COUNTRY = mapOf(
        "iran" to "ایران", "iranian" to "ایران", "england" to "انگلیس", "english" to "انگلیس", "spain" to "اسپانیا", "spanish" to "اسپانیا",
        "italy" to "ایتالیا", "italian" to "ایتالیا", "germany" to "آلمان", "german" to "آلمان", "france" to "فرانسه", "french" to "فرانسه",
        "portugal" to "پرتغال", "portuguese" to "پرتغال", "netherlands" to "هلند", "dutch" to "هلند", "belgium" to "بلژیک", "belgian" to "بلژیک",
        "turkey" to "ترکیه", "turkish" to "ترکیه", "scotland" to "اسکاتلند", "scottish" to "اسکاتلند", "australia" to "استرالیا", "australian" to "استرالیا",
        "brazil" to "برزیل", "brazilian" to "برزیل", "argentina" to "آرژانتین", "argentine" to "آرژانتین", "usa" to "آمریکا", "american" to "آمریکا",
        "mexico" to "مکزیک", "mexican" to "مکزیک", "japan" to "ژاپن", "japanese" to "ژاپن", "korea" to "کره", "korean" to "کره", "china" to "چین",
        "chinese" to "چین", "saudi" to "عربستان", "qatar" to "قطر", "qatari" to "قطر", "uae" to "امارات", "emirati" to "امارات", "iraq" to "عراق",
        "iraqi" to "عراق", "egypt" to "مصر", "egyptian" to "مصر", "russia" to "روسیه", "russian" to "روسیه", "ukraine" to "اوکراین", "ukrainian" to "اوکراین",
        "greece" to "یونان", "greek" to "یونان", "denmark" to "دانمارک", "danish" to "دانمارک", "sweden" to "سوئد", "swedish" to "سوئد", "norway" to "نروژ",
        "norwegian" to "نروژ", "switzerland" to "سوئیس", "swiss" to "سوئیس", "austria" to "اتریش", "austrian" to "اتریش", "poland" to "لهستان",
        "polish" to "لهستان", "croatia" to "کرواسی", "croatian" to "کرواسی", "serbia" to "صربستان", "serbian" to "صربستان", "czech" to "چک",
        "uzbekistan" to "ازبکستان", "uzbek" to "ازبکستان", "india" to "هند", "indian" to "هند", "asian" to "آسیا", "asia" to "آسیا",
        "european" to "اروپا", "europe" to "اروپا", "african" to "آفریقا", "africa" to "آفریقا", "international" to "بین‌المللی",
    )
    /** Common name parts, mostly Iranian ones, which letter-by-letter spelling would get wrong (Mohammad → محمد). */
    private val NAMES = mapOf(
        "mohammad" to "محمد", "mohamed" to "محمد", "muhammad" to "محمد", "mohammed" to "محمد", "ali" to "علی", "hossein" to "حسین", "hussein" to "حسین",
        "hassan" to "حسن", "mehdi" to "مهدی", "mahdi" to "مهدی", "reza" to "رضا", "alireza" to "علیرضا", "mohammadreza" to "محمدرضا",
        "mohammadhossein" to "محمدحسین", "mohammadali" to "محمدعلی", "mohammadmehdi" to "محمدمهدی", "amirhossein" to "امیرحسین", "ehsan" to "احسان",
        "esmaeil" to "اسماعیل", "esmail" to "اسماعیل", "ismail" to "اسماعیل", "saeid" to "سعید", "saeed" to "سعید", "said" to "سعید", "amir" to "امیر",
        "hamid" to "حمید", "ahmad" to "احمد", "ahmed" to "احمد", "hadi" to "هادی", "milad" to "میلاد", "majid" to "مجید", "morteza" to "مرتضی",
        "mojtaba" to "مجتبی", "abolfazl" to "ابوالفضل", "javad" to "جواد", "omid" to "امید", "farshad" to "فرشاد", "karim" to "کریم", "sajjad" to "سجاد",
        "vahid" to "وحید", "arash" to "آرش", "yasin" to "یاسین", "amin" to "امین", "masoud" to "مسعود", "iman" to "ایمان", "behnam" to "بهنام",
        "kamal" to "کمال", "kamran" to "کامران", "mehran" to "مهران", "danial" to "دانیال", "pouya" to "پویا", "nima" to "نیما", "kaveh" to "کاوه",
        "rasoul" to "رسول", "shahab" to "شهاب", "payam" to "پیام", "sina" to "سینا", "saman" to "سامان", "soroush" to "سروش", "siavash" to "سیاوش",
        "mehrdad" to "مهرداد", "farzad" to "فرزاد", "farhad" to "فرهاد", "babak" to "بابک", "bahman" to "بهمن", "ramin" to "رامین", "rouzbeh" to "روزبه",
        "aref" to "عارف", "arya" to "آریا", "aria" to "آریا", "abbas" to "عباس", "jalal" to "جلال", "jafar" to "جعفر", "mostafa" to "مصطفی", "mustafa" to "مصطفی",
        "yousef" to "یوسف", "youssef" to "یوسف", "yusuf" to "یوسف", "ibrahim" to "ابراهیم", "ebrahim" to "ابراهیم", "abdul" to "عبدال", "abdullah" to "عبدالله",
        "omar" to "عمر", "khalid" to "خالد", "hamza" to "حمزه", "karimi" to "کریمی", "hosseini" to "حسینی", "mohammadi" to "محمدی", "ahmadi" to "احمدی",
        "rezaei" to "رضایی", "rezaeian" to "رضاییان", "hashemi" to "هاشمی", "sadeghi" to "صادقی", "moradi" to "مرادی", "rahmani" to "رحمانی",
        "taremi" to "طارمی", "azmoun" to "آزمون", "jahanbakhsh" to "جهانبخش", "beiranvand" to "بیرانوند", "gholizadeh" to "قلی‌زاده",
        "ezatolahi" to "عزت‌اللهی", "nourollahi" to "نوراللهی", "hajsafi" to "حاج‌صفی", "pouraliganji" to "پورعلی‌گنجی", "kanaanizadegan" to "کنعانی‌زادگان",
        "khalilzadeh" to "خلیل‌زاده", "ansarifard" to "انصاری‌فرد", "mohebi" to "محبی", "alipour" to "علیپور", "safi" to "صفی", "haji" to "حاجی",
        "gholi" to "قلی", "zadeh" to "زاده", "pour" to "پور", "golzar" to "گلزار", "sardar" to "سردار", "alireza" to "علیرضا",
        "messi" to "مسی", "ronaldo" to "رونالدو", "haaland" to "هالند", "mbappe" to "امباپه", "salah" to "صلاح", "kane" to "کین", "rice" to "رایس",
        "palmer" to "پالمر", "saka" to "ساکا", "bellingham" to "بلینگهام", "vinicius" to "وینیسیوس", "lewandowski" to "لواندوفسکی", "yamal" to "یامال",
        "lamine" to "لامین", "jude" to "جود", "harry" to "هری", "cole" to "کول", "declan" to "دکلان", "bukayo" to "بوکایو", "erling" to "ارلینگ",
        "kylian" to "کیلیان", "lionel" to "لیونل", "cristiano" to "کریستیانو", "mason" to "میسون", "james" to "جیمز", "john" to "جان", "george" to "جرج",
        "michael" to "مایکل", "david" to "دیوید", "daniel" to "دنیل", "jordan" to "جردن", "luke" to "لوک", "jack" to "جک", "joe" to "جو", "kevin" to "کوین",
        "thomas" to "توماس", "william" to "ویلیام", "jacob" to "جیکوب", "joshua" to "جاشوا", "ryan" to "رایان", "kyle" to "کایل", "jamie" to "جیمی",
        "town" to "تاون", "south" to "ساوت", "north" to "نورث", "west" to "وست", "east" to "ایست", "new" to "نیو",
    )
}
