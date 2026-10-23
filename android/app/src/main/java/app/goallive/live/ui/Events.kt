package app.goallive.live.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.goallive.live.L
import app.goallive.live.data.a
import app.goallive.live.data.b
import app.goallive.live.data.i
import app.goallive.live.data.o
import app.goallive.live.data.objs
import app.goallive.live.data.path
import app.goallive.live.data.s
import org.json.JSONObject

/**
 * "رویدادها": one clear list, newest first while the match is live. Each row shows the minute, what happened, who did it
 * and for which team (crest and a stripe in the team's color); goals stand out with the new score. A switch narrows it to goals,
 * cards or substitutions.
 */
@Composable
fun EventsCard(m: MatchData) {
    val all = m.events.filter { it.s("type") in setOf("Goal", "Card", "Substitution", "VAR", "Half", "AddedTime") }
    if (all.none { it.s("type") != "Half" && it.s("type") != "AddedTime" }) return
    var only by rememberSaveable(m.matchId) { mutableStateOf("all") }
    val kinds = listOf("all" to L("همه"), "Goal" to L("گل‌ها"), "Card" to L("کارت‌ها"), "Substitution" to L("تعویض‌ها"))
        .filter { (k, _) -> k == "all" || all.any { it.s("type") == k } }
    val shown = all.filter { only == "all" || it.s("type") == only }.let { if (m.live) it.reversed() else it }
    Card {
        CardTitle(L("رویدادها"), sub = L("{0} گل · {1} کارت", fa(all.count { it.s("type") == "Goal" }), fa(all.count { it.s("type") == "Card" })))
        if (kinds.size > 2) Box(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 8.dp)) { Seg(kinds, only) { only = it } }
        Column(Modifier.padding(bottom = 10.dp)) { shown.forEach { EventLine(it, m) } }
    }
}

@Composable
private fun EventLine(e: JSONObject, m: MatchData) {
    val nav = LocalNav.current
    val accent = LocalAccent.current
    when (e.s("type")) {
        "Half" -> {
            val label = when (e.s("halfStrShort")) { "HT" -> L("پایان نیمه اول"); "FT" -> L("پایان بازی"); else -> tr(e.s("halfStrShort")) }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f).height(1.dp).background(C.line2))
                T(label, 12, FontWeight.ExtraBold, C.mute)
                ScoreText(e.i("homeScore") ?: 0, e.i("awayScore") ?: 0, 13, C.mute)
                Box(Modifier.weight(1f).height(1.dp).background(C.line2))
            }
            return
        }
        "AddedTime" -> {
            Box(Modifier.fillMaxWidth().padding(vertical = 2.dp), contentAlignment = Alignment.Center) {
                T(L("+{0} دقیقه وقت اضافه", fa(e.s("minutesAddedInput") ?: "")), 11, FontWeight.Bold, C.faint)
            }
            return
        }
    }
    val type = e.s("type")
    val home = e.b("isHome")
    val teamId = if (home) m.homeId else m.awayId
    val color = if (home) m.homeColor else m.awayColor
    val goal = type == "Goal"
    val pid = e.o("player").i("id") ?: 0
    val name = e.s("nameStr") ?: e.o("player").s("name") ?: ""
    val (title, sub) = when (type) {
        "Goal" -> name to when {
            e.b("ownGoal") -> L("گل به خودی")
            isPen(e) -> L("پنالتی")
            e.s("assistInput") != null -> L("پاس گل: {0}", personName(e.s("assistInput") ?: ""))
            else -> L("گل")
        }
        "Card" -> name to (when (e.s("card")) { "Red" -> L("کارت قرمز"); "YellowRed" -> L("کارت زرد دوم و اخراج"); else -> L("کارت زرد") } +
            (e.path("cardReason").s("defaultText")?.let { " · " + tr(it) } ?: ""))
        "Substitution" -> {
            val sw = e.a("swap").objs()
            (sw.getOrNull(0).s("name") ?: "") to L("به جای {0}", personName(sw.getOrNull(1).s("name") ?: "")) + if (e.b("injuredPlayerOut")) L(" · مصدوم") else ""
        }
        else -> "VAR" to tr(e.path("VAR.decision").s("value") ?: "")
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 3.dp).clip(RoundedCornerShape(14.dp))
            .background(if (goal) Brush.horizontalGradient(listOf(color.copy(alpha = 0.22f), Color.Transparent)) else Brush.horizontalGradient(listOf(C.card2.copy(alpha = 0.55f), C.card2.copy(alpha = 0.25f))))
            .clickable(enabled = pid > 0) { nav.player(pid) }.height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // the team's stripe on the start edge
        Box(Modifier.width(4.dp).fillMaxHeight().background(color))
        Box(Modifier.padding(start = 10.dp).width(38.dp), contentAlignment = Alignment.Center) {
            T(fa(minuteText(e)) + "'", 13, FontWeight.Black, if (goal) C.ink else C.mute)
        }
        Box(Modifier.padding(horizontal = 8.dp).size(32.dp).clip(CircleShape).background(if (goal) accent else C.cardSolid).border(1.dp, C.line, CircleShape),
            contentAlignment = Alignment.Center) {
            when (type) {
                "Goal" -> Icon(Ic.ball, null, Modifier.size(18.dp), tint = if (e.b("ownGoal")) C.live else LocalAccentInk.current)
                "Card" -> Box(Modifier.size(12.dp, 16.dp).clip(RoundedCornerShape(2.5.dp)).background(if (e.s("card") == "Yellow") C.gold else C.live))
                "Substitution" -> Icon(Ic.swap, null, Modifier.size(16.dp), tint = C.win)
                else -> T("VAR", 10, FontWeight.Black, C.mute)
            }
        }
        Column(Modifier.weight(1f).padding(vertical = 9.dp)) {
            if (type == "VAR") T(title, 14, FontWeight.ExtraBold) else Ltr(title, style = vz(14.sp, FontWeight.ExtraBold), color = if (type == "Substitution") C.win else C.ink)
            T(sub, 12, color = C.mute, maxLines = 2)
        }
        if (goal) e.a("newScore")?.takeIf { it.length() == 2 }?.let { sc ->
            Box(Modifier.padding(end = 6.dp).clip(RoundedCornerShape(10.dp)).background(C.cardSolid).padding(horizontal = 8.dp, vertical = 4.dp)) {
                ScoreText(sc.optInt(0), sc.optInt(1), 14)
            }
        }
        Box(Modifier.padding(end = 10.dp)) { Crest(teamId, 22.dp) }
    }
}

/** FotMob's stat notes in Persian, by their text id; the {T} slot is the team the note is about. Unknown ones stay in English. */
private val INSIGHTS_FA = mapOf(
    "insights_goals_team" to "{T} در {1} بازی اخیر {0} گل زده است.",
    "insights_player_most_chances_for_team" to "{0} بیشترین موقعیت عالی را برای {1} ساخته است ({2}).",
    "insights_player_most_shots_on_target_for_team" to "{0} بیشترین شوت در چارچوب در هر بازی را برای {1} دارد ({2}).",
    "insights_not_clean_sheet" to "{T} در {0} بازی اخیر کلین‌شیت نداشته است.",
    "insights_player_saves_per_match_rank_in_competition" to "{0} در سیو در هر بازی رتبه {1} رقابت‌هاست ({2}).",
    "insights_player_chances_created_rank_in_competition" to "{0} در ساختن موقعیت عالی رتبه {1} رقابت‌هاست ({2}).",
    "insights_not_won_team" to "{T} در {0} بازی اخیر برنده نشده است.",
    "h2h_undefeated" to "{0} در {2} دیدار اخیر به {1} نباخته است ({3} برد، {4} مساوی).",
    "h2h_winstreak" to "{0} {2} بازی قبلی مقابل {1} را برده است.",
    "h2h_no_draws_v2" to "{0} و {1} در {2} دیدار اخیرشان هیچ تساوی نداشته‌اند.",
    "insights_clean_sheets" to "{T} بیشترین کلین‌شیت رقابت‌ها را دارد ({0}).",
    "insights_penalties_awarded" to "{T} بیشترین پنالتی این فصل را گرفته است ({0}).",
    "insights_penalties_conceded" to "{T} بیشترین پنالتی این فصل را داده است ({0}).",
    "insights_best_away" to "{T} در بازی‌های خارج از خانه این فصل رتبه {0} را دارد.",
    "insights_best_home" to "{T} در بازی‌های خانگی این فصل رتبه {0} را دارد.",
    "insights_undefeated_team" to "{T} در {0} بازی اخیر شکست نخورده است.",
    "insights_average_goals_per_match" to "در بازی‌های {T} به‌طور میانگین {0} گل زده می‌شود.",
    "insights_bad_form_team" to "{T} {0} بازی اخیرش را باخته است.",
    "insights_player_goals_scored" to "{0} آقای گل رقابت‌هاست ({1}).",
)

/** One stat note in the app's language. */
fun insightText(i: JSONObject, m: MatchData): String {
    val en = i.s("text") ?: ""
    if (app.goallive.live.Lang.en) return en
    var t = INSIGHTS_FA[i.s("localizedTextId")] ?: return en
    val tid = i.i("teamId") ?: 0
    t = t.replace("{T}", teamName(tid, if (tid == m.homeId) m.homeName else if (tid == m.awayId) m.awayName else ""))
    i.a("statValues").objs().forEachIndexed { k, v ->
        val shown = when (v.s("type")) {
            "team" -> teamName(v.i("value") ?: 0, v.s("name") ?: "")
            "player" -> personName(v.s("name") ?: "")
            "double" -> fa(dec(v.optDouble("value"), 2))
            else -> fa(v.s("value") ?: "")
        }
        t = t.replace("{$k}", shown)
    }
    return t
}
