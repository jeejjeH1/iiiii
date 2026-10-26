package app.goallive.live.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.goallive.live.L
import app.goallive.live.data.a
import app.goallive.live.data.b
import app.goallive.live.data.d
import app.goallive.live.data.i
import app.goallive.live.data.o
import app.goallive.live.data.objs
import app.goallive.live.data.path
import app.goallive.live.data.s
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/*
 * "تحلیلگر گل‌لایو": a written reading of the match, worked out only from its own numbers.
 *  - the fair result: every shot is a coin weighted by its xG; the exact chances of every score follow from that (no random draws),
 *    so "the fair score was 2-1, 64% a home win" is a sum, not a guess;
 *  - what decided it: finishing against chances, big chances missed, the keepers, red cards and substitutions measured by what
 *    the team did in the minutes before and after, late goals, comebacks, set pieces, possession that didn't pay;
 *  - every finding carries a weight so the strongest come first; nothing is said that the numbers don't show.
 */

/** One finding: [home] is the side it is about (null for both). */
class Finding(val icon: String, val title: String, val text: String, val home: Boolean?, val weight: Double)

/** A moment that turned the match. */
class Turn(val minute: Int, val icon: String, val text: String, val home: Boolean?)

class Analysis(
    val hXg: Double, val aXg: Double,
    /** Chances of a home win, a draw and an away win from the chances made. */
    val pHome: Double, val pDraw: Double, val pAway: Double,
    /** The most likely scores, best first: (home, away, chance). */
    val likely: List<Triple<Int, Int, Double>>,
    val insights: List<Finding>,
    val turns: List<Turn>,
) {
    val fair: Pair<Int, Int> get() = likely.firstOrNull()?.let { it.first to it.second } ?: (0 to 0)
}

private const val MAXG = 10

/** The chance of each number of goals (0…10) from a list of shot xGs, each shot scoring or not on its own. */
internal fun goalChances(xgs: List<Double>): DoubleArray {
    var dist = DoubleArray(MAXG + 1).also { it[0] = 1.0 }
    for (p0 in xgs) {
        val p = p0.coerceIn(0.0, 0.99)
        val next = DoubleArray(MAXG + 1)
        for (k in 0..MAXG) {
            if (dist[k] == 0.0) continue
            next[k] += dist[k] * (1 - p)
            next[minOf(k + 1, MAXG)] += dist[k] * p
        }
        dist = next
    }
    return dist
}

private val SET_PIECES = setOf("FromCorner", "SetPiece", "FreeKick", "ThrowInSetPiece")

private fun minuteOf(e: JSONObject) = e.i("time") ?: e.i("min") ?: 0
private fun shotMin(s: JSONObject) = (s.i("min") ?: 0)
private fun pct(p: Double) = fa((p * 100).roundToInt()) + "٪"
private fun x2(v: Double) = fa(dec(v, 2))

/** A player's number for a FotMob stat key, wherever it sits in his groups. */
private fun playerStat(p: JSONObject?, key: String): Double? {
    for (g in p.a("stats").objs()) {
        val all = g.o("stats") ?: continue
        for (t in all.keys()) { val one = all.optJSONObject(t) ?: continue; if (one.s("key") == key) return one.o("stat").d("value") }
    }
    return null
}

fun analyse(m: MatchData, ratings: Map<Int, PlayerRating>): Analysis {
    val hn = teamName(m.homeId, m.homeName); val an = teamName(m.awayId, m.awayName)
    fun team(home: Boolean) = if (home) hn else an
    val shots = m.shots.filter { !it.b("isPenaltyShootoutEvent") }
    val isHome = { s: JSONObject -> s.i("teamId") == m.homeId }
    val xg = { s: JSONObject -> s.d("expectedGoals") ?: 0.0 }
    val hShots = shots.filter(isHome); val aShots = shots.filterNot(isHome)
    val hXg = hShots.sumOf(xg); val aXg = aShots.sumOf(xg)
    val hd = goalChances(hShots.map(xg)); val ad = goalChances(aShots.map(xg))
    var pH = 0.0; var pD = 0.0; var pA = 0.0
    val scores = ArrayList<Triple<Int, Int, Double>>()
    for (h in 0..MAXG) for (a in 0..MAXG) {
        val p = hd[h] * ad[a]
        when { h > a -> pH += p; h == a -> pD += p; else -> pA += p }
        if (p > 0.004) scores += Triple(h, a, p)
    }
    val likely = scores.sortedByDescending { it.third }.take(6)

    val goals = m.events.filter { it.s("type") == "Goal" && !it.b("isPenaltyShootoutEvent") }
    val hG = m.home.i("score") ?: goals.count { it.b("isHome") }
    val aG = m.away.i("score") ?: goals.count { !it.b("isHome") }
    val live = m.live
    val ins = ArrayList<Finding>()
    val turns = ArrayList<Turn>()

    // 1. the verdict: the result against the chances
    if (shots.size >= 4) {
        val sofar = if (live) L("تا اینجا") + " " else ""
        val diff = hXg - aXg
        when {
            hG != aG -> {
                val wHome = hG > aG
                val w = team(wHome); val l = team(!wHome)
                val pw = if (wHome) pH else pA
                val wx = if (wHome) hXg else aXg; val lx = if (wHome) aXg else hXg
                when {
                    pw >= 0.6 -> ins += Finding("✅", sofar + L("برتری شایسته"), L("{0} موقعیت‌های بهتری ساخت (xG {1} در برابر {2}). از روی همین موقعیت‌ها، شانس بردش {3} بود.", w, x2(wx), x2(lx), pct(pw)), wHome, 100.0)
                    // only when the loser really made the better chances
                    lx > wx + 0.25 -> ins += Finding("🍀", sofar + L("برتری با کمک شانس"), L("{0} موقعیت‌های بهتری داشت (xG {1} در برابر {2})، ولی {3} کارآمدتر بود. شانس جلو افتادن {3} از روی موقعیت‌ها فقط {4} بود.", l, x2(lx), x2(wx), w, pct(pw)), wHome, 100.0)
                    // even chances, but the winner scored far more than they were worth
                    (if (wHome) hG else aG) - wx >= 1.2 -> ins += Finding("🎯", sofar + L("برتری با تمام‌کنندگی"), L("موقعیت‌های دو تیم نزدیک بود (xG {0} در برابر {1})؛ فرق را دقت {2} جلوی دروازه ساخت. از روی موقعیت‌ها شانس بردش فقط {3} بود.", x2(wx), x2(lx), w, pct(pw)), wHome, 100.0)
                    else -> ins += Finding("⚖️", sofar + L("برتری در بازی نزدیک"), L("موقعیت‌ها نزدیک بود (xG {0} در برابر {1}) و {2} توانست فرق را بسازد. شانس بردش {3} بود.", x2(wx), x2(lx), w, pct(pw)), wHome, 95.0)
                }
            }
            abs(diff) >= 0.9 -> {
                val bHome = diff > 0
                ins += Finding("😤", sofar + L("{0} لایق برد بود", team(bHome)), L("xG {0} در برابر {1}: با این موقعیت‌ها شانس برد {2} {3} بود، ولی نتیجه مساوی شد.", x2(if (bHome) hXg else aXg), x2(if (bHome) aXg else hXg), team(bHome), pct(if (bHome) pH else pA)), bHome, 98.0)
            }
            else -> ins += Finding("⚖️", sofar + L("تساوی منصفانه"), L("موقعیت‌های دو تیم نزدیک بود (xG {0} در برابر {1}) و مساوی نتیجه‌ی منطقی بازی است.", x2(hXg), x2(aXg)), null, 90.0)
        }
    }

    // 2. finishing against chances, and the big chances that went begging
    for (home in listOf(true, false)) {
        val own = goals.count { it.b("isHome") == home && !it.b("ownGoal") }
        val txg = if (home) hXg else aXg
        val big = (if (home) hShots else aShots).filter { xg(it) >= 0.3 && it.s("eventType") != "Goal" }
        val d = own - txg
        // the verdict already said a close game was won on finishing: no second card saying the same
        val saidAlready = ins.any { it.icon == "🎯" && it.home == home }
        if (d >= 1.2 && !saidAlready) ins += Finding("🎯", L("تمام‌کنندگی عالی {0}", team(home)), L("{0} از موقعیت‌هایی به ارزش {1} گل، {2} گل زد؛ یعنی بیشتر از انتظار.", team(home), x2(txg), fa(own)), home, 70 + d * 10)
        if (d <= -1.2) ins += Finding("😩", L("بی‌دقتی {0} جلوی دروازه", team(home)), L("موقعیت‌هایی به ارزش {0} گل ساخت و فقط {1} گل زد{2}.", x2(txg), fa(own),
            if (big.isNotEmpty()) L("؛ {0} موقعیت عالی از دست رفت", fa(big.size)) else ""), home, 70 - d * 10)
        else if (big.size >= 2) ins += Finding("🙈", L("موقعیت‌های سوخته‌ی {0}", team(home)), L("{0} موقعیت عالی (هر کدام با بیش از ۳۰٪ شانس گل) گل نشد؛ مجموعاً {1} xG.", fa(big.size), x2(big.sumOf(xg))), home, 50.0 + big.size * 4)
        // a lot of possession and nothing to show
        if (own == 0 && txg >= 1.5) ins += Finding("🧱", L("{0} راهی به گل پیدا نکرد", team(home)), L("با وجود {0} xG از {1} شوت، گلی نزد.", x2(txg), fa((if (home) hShots else aShots).size)), home, 60 + txg * 5)
    }

    // 3. the keepers: goals prevented beyond what the shots on target were worth
    val stats = m.content.o("playerStats")
    if (stats != null) for (k in stats.keys()) {
        val p = stats.optJSONObject(k) ?: continue
        if (!p.b("isGoalkeeper")) continue
        val gp = playerStat(p, "goals_prevented") ?: continue
        val home = p.i("teamId") == m.homeId
        val name = personName(p.s("name") ?: "")
        val saves = playerStat(p, "saves")?.toInt() ?: 0
        if (gp >= 0.8) ins += Finding("🧤", L("{0} دیوار شد", name), L("{0} سیو و {1} گل جلوگیری‌شده: بیشتر از آنچه از یک دروازه‌بان انتظار می‌رود، {2} را نجات داد.", fa(saves), x2(gp), team(home)), home, 65 + gp * 12)
        if (gp <= -0.8) ins += Finding("😬", L("شب سخت {0}", name), L("از شوت‌هایی که روی دروازه‌اش آمد، حدود {0} گل بیشتر از انتظار خورد.", x2(-gp)), home, 55 - gp * 8)
    }

    // xG a side made between two minutes
    fun xgIn(home: Boolean, from: Int, to: Int) = (if (home) hShots else aShots).filter { shotMin(it) in from until to }.sumOf(xg)
    val mom = momentumOf(m).associate { (it.d("minute") ?: 0.0).toInt() to (it.d("value") ?: 0.0) }
    fun momIn(from: Int, to: Int): Double? { val v = (from until to).mapNotNull { mom[it] }; return if (v.size < 4) null else v.average() }
    val end = max(90, shots.maxOfOrNull(::shotMin) ?: 90)

    // 4. red cards and substitutions, by what changed after them
    for (e in m.events) {
        val t = minuteOf(e); val home = e.b("isHome")
        val who = personName(e.o("player").s("lastName") ?: e.s("nameStr") ?: e.o("player").s("name") ?: "")
        when (e.s("type")) {
            "Card" -> if (e.s("card") == "Red" || e.s("card") == "YellowRed") {
                val before = momIn(maxOf(1, t - 15), t); val after = momIn(t, minOf(end, t + 15))
                // momentum is positive for the home side: what the carded side lost
                val swing = if (before != null && after != null) (if (home) before - after else after - before) else null
                turns += Turn(t, "🟥", L("اخراج {0}؛ {1} ده نفره شد", who, team(home)), home)
                val oppXgAfter = xgIn(!home, t, end)
                ins += Finding("🟥", L("اخراج دقیقه‌ی {0}", fa(t)), if (swing != null && swing > 15) L("بعد از اخراج {0}، بازی به سود {1} برگشت و {1} در باقی بازی {2} xG ساخت.", who, team(!home), x2(oppXgAfter))
                    else L("{0} با اخراج {1} ده نفره شد؛ {2} در باقی بازی {3} xG ساخت.", team(home), who, team(!home), x2(oppXgAfter)), home, 75.0)
            }
            "Substitution" -> {
                if (t < 30 || t > end - 8) continue
                val sw = e.a("swap").objs()
                val inName = personName(sw.getOrNull(0).s("name") ?: ""); val inId = sw.getOrNull(0).i("id")
                val span = minOf(20, end - t)
                val before = xgIn(home, t - span, t); val after = xgIn(home, t, t + span)
                // did the man who came on score or set one up?
                val decisive = goals.any { g -> g.b("isHome") == home && minuteOf(g) >= t && (inId != null && (g.i("playerId") == inId || g.i("assistPlayerId") == inId)) }
                if (decisive) {
                    ins += Finding("🔄", L("تعویض طلایی"), L("{0} در دقیقه‌ی {1} وارد شد و در گل {2} نقش داشت.", inName, fa(t), team(home)), home, 72.0)
                    turns += Turn(t, "🔄", L("ورود {0} برای {1}", inName, team(home)), home)
                } else if (after >= 0.5 && after >= 2 * max(before, 0.1)) {
                    ins += Finding("🔄", L("تعویضی که بازی را عوض کرد"), L("بعد از ورود {0} (دقیقه‌ی {1})، xG {2} در {3} دقیقه از {4} به {5} رسید.", inName, fa(t), team(home), fa(span), x2(before), x2(after)), home, 58 + after * 10)
                    turns += Turn(t, "🔄", L("ورود {0}؛ {1} خطرناک‌تر شد", inName, team(home)), home)
                }
            }
        }
    }

    // 5. goals as turning points, late drama and comebacks
    var h = 0; var a = 0; var hTrailed = false; var aTrailed = false
    val ordered = goals.sortedBy(::minuteOf)
    ordered.forEachIndexed { k, g ->
        val home = g.b("isHome"); val t = minuteOf(g)
        if (home) h++ else a++
        if (h < a) hTrailed = true; if (a < h) aTrailed = true
        val who = personName(g.o("player").s("lastName") ?: g.s("nameStr") ?: g.o("player").s("name") ?: "")
        // the scorer's side after the goal
        val mine = if (home) h else a; val theirs = if (home) a else h
        val what = when { mine == theirs -> L("مساوی کرد"); mine == theirs + 1 -> L("جلو افتاد"); mine < theirs -> L("فاصله را کم کرد"); else -> L("فاصله را بیشتر کرد") }
        // the score with the home side first, as everywhere in the app
        turns += Turn(t, "⚽", L("گل {0}: {1} {2} ({3})", who, team(home), what, fa("$h-$a")), home)
        // a late goal that settled the final result: the last goal, a draw it made or a one-goal win it gave
        if (!live && k == ordered.lastIndex && t >= 80 && (mine == theirs || mine == theirs + 1))
            ins += Finding("⏱️", L("درام دقایق پایانی"), L("گل دقیقه‌ی {0} {1}، {2}.", fa(t), who, if (mine == theirs) L("بازی را مساوی کرد") else L("برنده را مشخص کرد")), home, 68.0)
    }
    if (!live) {
        if (hTrailed && hG >= aG) ins += Finding("💪", L("بازگشت {0}", hn), L("{0} عقب افتاده بود ولی {1}.", hn, if (hG > aG) L("بازی را برد") else L("نتیجه را مساوی کرد")), true, 74.0)
        if (aTrailed && aG >= hG) ins += Finding("💪", L("بازگشت {0}", an), L("{0} عقب افتاده بود ولی {1}.", an, if (aG > hG) L("بازی را برد") else L("نتیجه را مساوی کرد")), false, 74.0)
    }

    // 6. where the danger came from: set pieces, long range
    for (home in listOf(true, false)) {
        val list = if (home) hShots else aShots
        val tot = list.sumOf(xg)
        if (tot < 0.6 || list.size < 5) continue
        val sp = list.filter { it.s("situation") in SET_PIECES }.sumOf(xg)
        if (sp / tot >= 0.45) ins += Finding("🚩", L("خطر ضربات ایستگاهی {0}", team(home)), L("{0} از xG {1} از کرنر و ضربه‌ی آزاد بود ({2} از {3}).", pct(sp / tot), team(home), x2(sp), x2(tot)), home, 45 + sp / tot * 20)
        val far = list.count { !insideBox(it) }
        if (list.size >= 8 && far * 1.0 / list.size >= 0.55 && tot / list.size < 0.08)
            ins += Finding("🎯", L("شوت‌های دور و کم‌خطر {0}", team(home)), L("{0} شوت از {1} شوت از بیرون محوطه بود؛ میانگین xG هر شوت فقط {2}.", fa(far), fa(list.size), x2(tot / list.size)), home, 42.0)
    }

    // 7. possession that didn't pay, and a defence that held
    val poss = m.content.path("stats.Periods.All").a("stats").objs().flatMap { it.a("stats").objs() }.firstOrNull { it.s("key") == "BallPossesion" }
        ?.a("stats")?.let { (it.optString(0).filter(Char::isDigit).toIntOrNull() ?: 0) to (it.optString(1).filter(Char::isDigit).toIntOrNull() ?: 0) }
    if (!live && poss != null && hG != aG) {
        val wHome = hG > aG
        val wp = if (wHome) poss.first else poss.second
        if (wp in 1..40) ins += Finding("🔁", L("برد با توپ کمتر"), L("{0} فقط با {1}٪ مالکیت برد؛ کیفیت از کمیت مهم‌تر بود.", team(wHome), fa(wp)), wHome, 55.0)
    }
    for (home in listOf(true, false)) {
        val conceded = if (home) aG else hG
        val oppXg = if (home) aXg else hXg
        if (!live && conceded == 0 && oppXg >= 1.2) ins += Finding("🛡️", L("دفاع جانانه‌ی {0}", team(home)), L("حریف موقعیت‌هایی به ارزش {0} گل ساخت ولی {1} گلی نخورد.", x2(oppXg), team(home)), home, 60 + oppXg * 5)
    }

    // 8. the player of the match, and a finisher beyond his chances
    ratings.values.filter { it.minutes >= 30 }.maxByOrNull { it.value }?.let { best ->
        val why = best.groups.take(2).joinToString(L(" و ")) { ratingGroupName(it.first) }
        ins += Finding("⭐", L("ستاره‌ی بازی: {0}", personName(best.name)), L("نمره‌ی {0}، بیشترین سهمش از {1}.", fa(dec(best.value, 1)), why), best.home, 52 + (best.value - 7) * 8)
    }
    if (stats != null) for (k in stats.keys()) {
        val p = stats.optJSONObject(k) ?: continue
        val id = p.i("id") ?: k.toIntOrNull() ?: continue
        val g = goals.count { it.i("playerId") == id && !it.b("ownGoal") }
        val pxg = playerStat(p, "expected_goals") ?: continue
        if (g >= 2 && g - pxg >= 1.0) ins += Finding("🔥", L("{0} بی‌رحم بود", personName(p.s("name") ?: "")), L("{0} گل از موقعیت‌هایی به ارزش فقط {1} xG.", fa(g), x2(pxg)), p.i("teamId") == m.homeId, 62.0 + g * 3)
    }

    // 9. live: who is on top right now
    if (live) {
        val now = shots.maxOfOrNull(::shotMin) ?: 0
        val cur = cleanMinuteInt(m.minute) ?: now
        val recent = momIn(maxOf(1, cur - 10), cur + 1)
        val hx = xgIn(true, cur - 10, cur + 1); val ax = xgIn(false, cur - 10, cur + 1)
        if (recent != null && abs(recent) > 25) {
            val home = recent > 0
            ins += Finding("📡", L("الان {0} مسلط است", team(home)), L("در ۱۰ دقیقه‌ی اخیر بیشتر در نیمه‌ی حریف بوده{0}.", if ((if (home) hx else ax) >= 0.4) L(" و {0} xG ساخته؛ گل نزدیک است", x2(if (home) hx else ax)) else ""), home, 110.0)
        }
    }

    return Analysis(hXg, aXg, pH, pD, pA, likely, ins.sortedByDescending { it.weight }.distinctBy { it.title }.take(9), turns.sortedBy { it.minute })
}

/* ---------- win probability, minute by minute ---------- */

/** The chances of each final result at one minute of the match (home win, draw, away win). */
class WinPoint(val minute: Int, val h: Double, val d: Double, val a: Double)

private fun poisson(l: Double): DoubleArray {
    val out = DoubleArray(MAXG + 1); var p = kotlin.math.exp(-l)
    for (k in 0..MAXG) { out[k] = p; p *= l / (k + 1) }
    return out
}

/** Final-result chances from the score now ([dh] − [da] ahead) and the goals each side may still score. */
internal fun outcome(dh: Int, da: Int, lh: Double, la: Double): Triple<Double, Double, Double> {
    val ph = poisson(lh); val pa = poisson(la)
    var h = 0.0; var d = 0.0; var a = 0.0
    for (i in 0..MAXG) for (j in 0..MAXG) {
        val p = ph[i] * pa[j]
        val diff = dh + i - (da + j)
        when { diff > 0 -> h += p; diff == 0 -> d += p; else -> a += p }
    }
    val t = h + d + a
    return Triple(h / t, d / t, a / t)
}

/** Goals a side scores in an average game: the starting guess before the match shows its own pace. */
private const val PRIOR_GOALS = 1.35
/** How many minutes of play that guess is worth against what the match itself shows. */
private const val PRIOR_MINUTES = 30.0

/**
 * The win probability through the match. At each minute: the score so far, plus the goals each side may still score, from
 * its pace in this match (xG per minute, eased in from an average game's pace early on) times the minutes left; a side down
 * to ten men is slowed and its opponent sped up. Only what had happened by that minute is used. A finished match ends on its result.
 */
fun winTimeline(m: MatchData): List<WinPoint> {
    val shots = m.shots.filter { !it.b("isPenaltyShootoutEvent") }
    val goals = m.events.filter { it.s("type") == "Goal" && !it.b("isPenaltyShootoutEvent") }
    val reds = m.events.filter { it.s("type") == "Card" && (it.s("card") == "Red" || it.s("card") == "YellowRed") }
    val now = if (m.live) (cleanMinuteInt(m.minute) ?: 0) else max(90, goals.maxOfOrNull(::minuteOf) ?: 90)
    if (now < 5) return emptyList()
    val out = ArrayList<WinPoint>()
    for (t in 0..now) {
        val hg = goals.count { it.b("isHome") && minuteOf(it) <= t }; val ag = goals.count { !it.b("isHome") && minuteOf(it) <= t }
        val left = max(0.0, 93.0 - t)   // about three minutes of stoppage time
        fun pace(home: Boolean): Double {
            val xg = shots.filter { (it.i("teamId") == m.homeId) == home && shotMin(it) < t }.sumOf { it.d("expectedGoals") ?: 0.0 }
            var r = (PRIOR_GOALS * PRIOR_MINUTES / 90 + xg) / (PRIOR_MINUTES + t)
            val mine = reds.count { it.b("isHome") == home && minuteOf(it) <= t }; val theirs = reds.count { it.b("isHome") != home && minuteOf(it) <= t }
            repeat(mine) { r *= 0.7 }; repeat(theirs) { r *= 1.25 }
            return r
        }
        val (h, d, a) = outcome(hg, ag, pace(true) * left, pace(false) * left)
        out += WinPoint(t, h, d, a)
    }
    if (m.finished) {
        val hG = m.home.i("score") ?: 0; val aG = m.away.i("score") ?: 0
        out += WinPoint(now + 1, if (hG > aG) 1.0 else 0.0, if (hG == aG) 1.0 else 0.0, if (aG > hG) 1.0 else 0.0)
    }
    return out
}

private fun cleanMinuteInt(s: String) = Regex("\\d+").findAll(s).map { it.value.toInt() }.toList().takeIf { it.isNotEmpty() }?.sum()

/* ---------- the tab ---------- */

@Composable
fun AnalystTab(m: MatchData) {
    val ratings = LocalRatings.current
    val an = remember(m.d) { runCatching { analyse(m, ratings) }.getOrNull() } ?: return
    Card(pad = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            EmojiBadge("🧠", 44.dp, LocalAccent.current)
            Column(Modifier.weight(1f)) {
                T(L("تحلیلگر گل‌لایو"), 17, FontWeight.Black)
                T(if (m.live) L("تحلیل زنده؛ با هر به‌روزرسانی تازه می‌شود") else L("بازی از روی اعداد خودش، به زبان ساده"), 12, color = C.mute)
            }
            Pill(L("استوری"), PillKind.Accent, icon = Ic.share, onClick = { StoryStudio.story = Story.Analysis(m) })
        }
    }
    FairResult(m, an)
    WinChart(m)
    an.insights.forEach { InsightCard(it, m) }
    if (an.turns.isNotEmpty()) TurnsCard(an.turns, m)
    RealShape(m)
    T(L("همه‌چیز از روی موقعیت‌ها (xG)، شوت‌ها، مومنتوم و آمار بازیکنان همین بازی حساب شده است."), 11, color = C.faint,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), align = TextAlign.Center)
}

/** "نتیجه‌ی منصفانه": the score the chances deserved, the three outcomes as one bar, the likeliest scores. */
@Composable
private fun FairResult(m: MatchData, an: Analysis) {
    val grow = remember { Animatable(0f) }
    LaunchedEffect(m.matchId) { grow.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
    val hG = m.home.i("score") ?: 0; val aG = m.away.i("score") ?: 0
    Card(pad = PaddingValues(16.dp)) {
        T(if (m.live) L("نتیجه‌ی منصفانه تا اینجا") else L("نتیجه‌ی منصفانه"), 15, FontWeight.ExtraBold)
        T(L("اگر این بازی با همین موقعیت‌ها بارها تکرار می‌شد"), 12, color = C.mute)
        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Crest(m.homeId, 40.dp); T("xG " + x2(an.hXg), 13, FontWeight.Bold, m.homeColor)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ScoreText(an.fair.first, an.fair.second, 40)
                T(L("نتیجه‌ی واقعی {0}", fa("$hG-$aG")), 12, FontWeight.Bold, C.mute)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Crest(m.awayId, 40.dp); T("xG " + x2(an.aXg), 13, FontWeight.Bold, m.awayColor)
            }
        }
        // home win, draw, away win as one bar
        val parts = listOf(Triple(an.pHome, m.homeColor, L("برد {0}", teamName(m.homeId, m.homeName))), Triple(an.pDraw, C.draw, L("مساوی")), Triple(an.pAway, m.awayColor, L("برد {0}", teamName(m.awayId, m.awayName))))
        Row(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp)), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            parts.forEach { (p, c, _) -> Box(Modifier.weight(max(0.001, p * grow.value + (1 - grow.value) / 3).toFloat()).fillMaxHeight().background(c)) }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            parts.forEachIndexed { k, (p, c, label) ->
                Column(Modifier.weight(1f), horizontalAlignment = when (k) { 0 -> Alignment.Start; 1 -> Alignment.CenterHorizontally; else -> Alignment.End }) {
                    T(pct(p), 18, FontWeight.Black, if (k == 1) C.ink else c)
                    T(label, 11, color = C.mute, maxLines = 1)
                }
            }
        }
        if (an.likely.size >= 3) {
            T(L("محتمل‌ترین نتیجه‌ها"), 12, FontWeight.Bold, C.mute, Modifier.padding(top = 14.dp, bottom = 6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                an.likely.take(5).forEach { (h, a, p) ->
                    val real = h == hG && a == aG
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(if (real) LocalAccent.current.copy(alpha = 0.18f) else C.card2)
                        .border(1.dp, if (real) LocalAccent.current else C.line, RoundedCornerShape(14.dp)).padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        ScoreText(h, a, 16)
                        T(pct(p), 11, FontWeight.Bold, C.mute)
                    }
                }
            }
        }
    }
}

/**
 * "شانس برد در طول بازی": the three outcomes stacked minute by minute (home win at the top in its colour, the draw in the
 * middle, away win at the bottom), goals and red cards marked, and the moment that swung it most.
 */
@Composable
private fun WinChart(m: MatchData) {
    val pts = remember(m.d) { runCatching { winTimeline(m) }.getOrDefault(emptyList()) }
    if (pts.size < 10) return
    val grow = remember { Animatable(0f) }
    LaunchedEffect(m.matchId) { grow.animateTo(1f, tween(1200, easing = FastOutSlowInEasing)) }
    val hc = m.homeColor; val ac = m.awayColor; val dc = C.draw.copy(alpha = 0.55f)
    val marks = m.events.filter { e -> !e.b("isPenaltyShootoutEvent") && (e.s("type") == "Goal" || (e.s("type") == "Card" && e.s("card") in setOf("Red", "YellowRed"))) }
    // the biggest jump in the home side's chance from one minute to the next, and what caused it
    val swing = pts.zipWithNext().filter { (p, q) -> q.minute <= (pts.last().minute - if (m.finished) 1 else 0) }
        .maxByOrNull { (p, q) -> abs(q.h - p.h) + abs(q.a - p.a) }
    val last = pts.last()
    Card(pad = PaddingValues(16.dp)) {
        T(L("شانس برد در طول بازی"), 15, FontWeight.ExtraBold)
        T(L("در هر دقیقه، از روی نتیجه تا آن لحظه و موقعیت‌هایی که هر تیم ساخته بود"), 12, color = C.mute, modifier = Modifier.padding(bottom = 10.dp))
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
            Column {
                androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(14.dp))) {
                    val n = pts.size; val k = grow.value
                    fun x(i: Int) = size.width * i / (n - 1)
                    val shown = max(2, (n * k).toInt())
                    // bands from the top: home win, draw, away win
                    fun band(top: (WinPoint) -> Double, bottom: (WinPoint) -> Double, col: Color) {
                        val path = androidx.compose.ui.graphics.Path()
                        for (i in 0 until shown) { val y = (top(pts[i]) * size.height).toFloat(); if (i == 0) path.moveTo(x(i), y) else path.lineTo(x(i), y) }
                        for (i in shown - 1 downTo 0) path.lineTo(x(i), (bottom(pts[i]) * size.height).toFloat())
                        path.close(); drawPath(path, col)
                    }
                    band({ 0.0 }, { it.h }, hc)
                    band({ it.h }, { it.h + it.d }, dc)
                    band({ it.h + it.d }, { 1.0 }, ac)
                    // half time and the even line
                    val half = pts.indexOfFirst { it.minute >= 45 }.takeIf { it > 0 }
                    half?.let { drawLine(Color.White.copy(alpha = 0.35f), androidx.compose.ui.geometry.Offset(x(it), 0f), androidx.compose.ui.geometry.Offset(x(it), size.height), 2f) }
                    marks.forEach { e ->
                        val i = pts.indexOfFirst { it.minute >= minuteOf(e) }.takeIf { it >= 0 && it < shown } ?: return@forEach
                        val goal = e.s("type") == "Goal"
                        val cx = x(i); val cy = if (e.b("isHome")) 10.dp.toPx() else size.height - 10.dp.toPx()
                        drawCircle(Color.White, if (goal) 7.dp.toPx() else 5.dp.toPx(), androidx.compose.ui.geometry.Offset(cx, cy))
                        drawCircle(if (goal) Color(0xFF111111) else Color(0xFFE5434B), if (goal) 4.dp.toPx() else 3.5.dp.toPx(), androidx.compose.ui.geometry.Offset(cx, cy))
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    listOf("۰′", "۴۵′", "۹۰′").forEachIndexed { k, l -> T(l, 11, color = C.mute, modifier = Modifier.weight(1f), align = when (k) { 0 -> TextAlign.Start; 1 -> TextAlign.Center; else -> TextAlign.End }) }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            for ((col, label) in listOf(hc to L("برد {0}", teamName(m.homeId, m.homeName)), dc to L("مساوی"), ac to L("برد {0}", teamName(m.awayId, m.awayName)))) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(col)); T(label, 11, color = C.mute, maxLines = 1)
            }
        }
        if (m.live) T(L("الان: برد {0} {1} · مساوی {2} · برد {3} {4}", teamName(m.homeId, m.homeName), pct(last.h), pct(last.d), teamName(m.awayId, m.awayName), pct(last.a)), 13, FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
        swing?.let { (p, q) ->
            // what happened in that minute
            val e = marks.firstOrNull { minuteOf(it) == q.minute } ?: m.events.firstOrNull { it.s("type") == "Goal" && minuteOf(it) == q.minute }
            val what = e?.let { ev -> if (ev.s("type") == "Goal") L("گل {0}", personName(ev.o("player").s("lastName") ?: ev.s("nameStr") ?: "")) else L("اخراج {0}", personName(ev.o("player").s("lastName") ?: ev.s("nameStr") ?: "")) }
            // told for the side whose chance moved most
            val home = abs(q.h - p.h) >= abs(q.a - p.a)
            T(L("بزرگ‌ترین چرخش: دقیقه‌ی {0}{1}؛ شانس برد {2} از {3} به {4} رسید.", fa(q.minute), what?.let { " (" + it + ")" } ?: "",
                if (home) teamName(m.homeId, m.homeName) else teamName(m.awayId, m.awayName), pct(if (home) p.h else p.a), pct(if (home) q.h else q.a)),
                13, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun InsightCard(x: Finding, m: MatchData) {
    val color = when (x.home) { true -> m.homeColor; false -> m.awayColor; null -> C.mute }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(C.card).border(1.dp, C.line, RoundedCornerShape(22.dp)).height(IntrinsicSize.Min)) {
        Box(Modifier.width(5.dp).fillMaxHeight().background(color))
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(42.dp).clip(CircleShape).background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(color, lerpColor(color, Color.Black, 0.35f)))),
                contentAlignment = Alignment.Center) { Glyph(x.icon, 24.dp, Color.White) }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (x.home != null) Crest(if (x.home) m.homeId else m.awayId, 18.dp)
                    T(x.title, 15, FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                }
                T(x.text, 13, color = C.ink.copy(alpha = 0.85f), modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

/** A finding's or turning point's drawn icon. */
@Composable
internal fun Glyph(icon: String, size: androidx.compose.ui.unit.Dp, ink: Color) {
    androidx.compose.foundation.Canvas(Modifier.size(size)) {
        drawIntoCanvas { drawGlyph(it.nativeCanvas, icon, this.size.width / 2, this.size.height / 2, this.size.minDimension / 2, ink.toArgb()) }
    }
}


/** "نقاط عطف": the goals, red cards and the substitutions that changed something, down a line. */
@Composable
private fun TurnsCard(turns: List<Turn>, m: MatchData) {
    Card(pad = PaddingValues(vertical = 12.dp)) {
        T(L("نقاط عطف بازی"), 15, FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        turns.forEachIndexed { k, t ->
            val color = when (t.home) { true -> m.homeColor; false -> m.awayColor; null -> C.mute }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.width(40.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.width(2.dp).height(8.dp).background(if (k == 0) Color.Transparent else C.line2))
                    Box(Modifier.clip(RoundedCornerShape(10.dp)).background(color).padding(horizontal = 6.dp, vertical = 2.dp)) { T(fa(t.minute) + "'", 12, FontWeight.Black, inkOn(color)) }
                    Box(Modifier.width(2.dp).weight(1f).background(if (k == turns.lastIndex) Color.Transparent else C.line2))
                }
                Row(Modifier.weight(1f).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Glyph(t.icon, 20.dp, C.ink); T(t.text, 13, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/* ---------- the real shape ---------- */

/** A team's lines from back to front ([xs] its outfield players' mean depth, 0 = own goal): split where the gaps are widest. */
internal fun linesOf(xs: List<Float>): List<Int> {
    if (xs.size < 7) return emptyList()
    val s = xs.sorted()
    val gaps = (0 until s.size - 1).map { it to s[it + 1] - s[it] }
    // three lines at least, four at most; a fourth only for a clear gap
    val cuts = gaps.sortedByDescending { it.second }.let { g -> g.take(2) + g.drop(2).take(1).filter { it.second >= 7f } }.map { it.first }.sorted()
    val out = ArrayList<Int>(); var start = 0
    for (c in cuts) { out += c + 1 - start; start = c + 1 }
    out += s.size - start
    return out
}

/**
 * "شکل واقعی تیم‌ها": the formation each side really played, read from where its players stood on average (their heatmaps),
 * next to the one announced, and how high the back line held.
 */
@Composable
private fun RealShape(m: MatchData) {
    val loaded by produceState<Map<Int, List<Pair<Float, Float>>>?>(heatCache[m.matchId], m.matchId) {
        if (value == null) value = runCatching { heatPoints(m.matchId) }.getOrNull()?.also { heatCache[m.matchId] = it }
    }
    val all = loaded ?: return
    val dots = remember(all, m.d) { averageDots(m, all) }
    val rows = listOf(true, false).mapNotNull { home ->
        // depth from the side's own goal: the away side's dots were turned round to face the home side
        val xs = dots.filter { it.home == home }.map { if (home) it.x else 105f - it.x }
        if (xs.size < 10) return@mapNotNull null
        val outfield = xs.sorted().drop(1)
        val lines = linesOf(outfield)
        // a shape football knows: 3 to 5 at the back, 1 to 3 up front, nobody alone in a middle line of a three-line shape
        if (lines.size < 3 || lines.first() !in 3..5 || lines.last() !in 1..3 || (lines.size == 3 && lines[1] < 2)) return@mapNotNull null
        val back = outfield.take(lines.first()).average()
        val announced = m.content.path("lineup.${if (home) "homeTeam" else "awayTeam"}").s("formation") ?: ""
        Triple(home, lines.joinToString("-"), announced to back)
    }
    if (rows.isEmpty()) return
    Card(pad = PaddingValues(16.dp)) {
        T(L("شکل واقعی تیم‌ها"), 15, FontWeight.ExtraBold)
        T(L("از روی جای میانگین بازیکنان؛ تقریبی"), 12, color = C.mute, modifier = Modifier.padding(bottom = 8.dp))
        if (rows.size == 2) {
            val (h, a) = rows[0].third.second to rows[1].third.second
            if (abs(h - a) >= 5) T(L("خط دفاع {0} حدود {1} متر بالاتر از {2} بود.", teamName(if (h > a) m.homeId else m.awayId, if (h > a) m.homeName else m.awayName),
                fa(abs(h - a).roundToInt()), teamName(if (h > a) m.awayId else m.homeId, if (h > a) m.awayName else m.homeName)), 13, modifier = Modifier.padding(bottom = 6.dp))
        }
        rows.forEach { (home, real, rest) ->
            val (announced, back) = rest
            val id = if (home) m.homeId else m.awayId
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Crest(id, 30.dp)
                Column(Modifier.weight(1f)) {
                    TeamText(id, if (home) m.homeName else m.awayName, size = 14, w = FontWeight.ExtraBold)
                    if (announced.isNotEmpty() && announced != real) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        T(L("اعلام‌شده"), 12, color = C.mute); PitchPill(fa(announced), C.card2, C.mute)
                    }
                }
                PitchPill(fa(real), if (home) m.homeColor else m.awayColor)
            }
        }
    }
}
