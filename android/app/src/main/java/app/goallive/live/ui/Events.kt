package app.goallive.live.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
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
import app.goallive.live.data.tr
import org.json.JSONObject

/**
 * "رویدادها": the match on one timeline down the middle, minutes in the centre, the home side's events on one half and the
 * away side's on the other (newest first while the match is live). Goals stand out with the team's colour and the new score.
 */
@Composable
fun EventsCard(m: MatchData) {
    // shoot-out kicks have their own card; here they would count as goals
    val all = m.events.filter { it.s("type") in setOf("Goal", "Card", "Substitution", "VAR", "Half", "AddedTime") && !it.b("isPenaltyShootoutEvent") }
    if (all.none { it.s("type") != "Half" && it.s("type") != "AddedTime" }) return
    val shown = if (m.live) all.reversed() else all
    Card {
        CardTitle(L("رویدادها"), sub = L("{0} گل · {1} کارت", fa(all.count { it.s("type") == "Goal" }), fa(all.count { it.s("type") == "Card" })))
        // crests over each half so it is clear which side is which
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Crest(m.homeId, 22.dp) }
            Spacer(Modifier.width(46.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Crest(m.awayId, 22.dp) }
        }
        val line = C.line2
        Box(Modifier.padding(bottom = 12.dp).drawBehind {
            val w = 2.dp.toPx()
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, line, line, Color.Transparent)), Offset(size.width / 2 - w / 2, 0f), Size(w, size.height))
        }) { Column { shown.forEach { EventLine(it, m) } } }
    }
}

@Composable
private fun EventLine(e: JSONObject, m: MatchData) {
    val nav = LocalNav.current
    val accent = LocalAccent.current
    when (e.s("type")) {
        "Half" -> {
            val label = when (e.s("halfStrShort")) { "HT" -> L("پایان نیمه اول"); "FT" -> L("پایان بازی"); else -> tr(e.s("halfStrShort")) }
            Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                Row(Modifier.clip(RoundedCornerShape(50)).background(C.cardSolid).border(1.dp, C.line2, RoundedCornerShape(50))
                    .padding(horizontal = 14.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    T(label, 11, FontWeight.ExtraBold, C.mute)
                    ScoreText(e.i("homeScore") ?: 0, e.i("awayScore") ?: 0, 12, C.ink)
                }
            }
            return
        }
        "AddedTime" -> {
            Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.clip(RoundedCornerShape(50)).background(C.cardSolid).padding(horizontal = 10.dp, vertical = 3.dp)) {
                    T(L("+{0} دقیقه وقت اضافه", fa(e.s("minutesAddedInput") ?: "")), 10, FontWeight.Bold, C.faint)
                }
            }
            return
        }
    }
    val type = e.s("type")
    val home = e.b("isHome")
    val color = if (home) m.homeColor else m.awayColor
    val goal = type == "Goal"
    val pid = e.o("player").i("id") ?: 0
    val name = e.s("nameStr") ?: e.o("player").s("name") ?: ""
    val ico: @Composable () -> Unit = {
        Box(Modifier.size(if (goal) 30.dp else 26.dp).clip(CircleShape).background(if (goal) color.copy(alpha = 0.22f) else C.card2).border(1.dp, if (goal) color.copy(alpha = 0.6f) else C.line, CircleShape),
            contentAlignment = Alignment.Center) {
            when (type) {
                "Goal" -> Icon(Ic.ball, null, Modifier.size(17.dp), tint = if (e.b("ownGoal")) C.live else C.ink)
                "Card" -> Box(Modifier.size(11.dp, 15.dp).clip(RoundedCornerShape(2.5.dp)).background(if (e.s("card") == "Yellow") C.gold else C.live))
                "Substitution" -> Icon(Ic.swap, null, Modifier.size(15.dp), tint = C.win)
                else -> T("VAR", 9, FontWeight.Black, C.mute)
            }
        }
    }
    // gradients are drawn left to right whatever the language; home sits on the start side (right in Persian)
    val scorerOnLeft = home == (androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Ltr)
    // the text hugs the timeline: towards the centre on both halves
    val inward = if (home) Alignment.End else Alignment.Start
    val textAlign = if (home) TextAlign.End else TextAlign.Start
    val who: @Composable () -> Unit = {
        Column(Modifier.clip(RoundedCornerShape(10.dp)).clickable(enabled = pid > 0) { nav.player(pid) }.padding(horizontal = 4.dp, vertical = 2.dp), horizontalAlignment = inward) {
            when (type) {
                "Goal" -> {
                    Ltr(name, style = vz(14.sp, FontWeight.ExtraBold), align = textAlign)
                    T(when {
                        e.b("ownGoal") -> L("گل به خودی"); isPen(e) -> L("پنالتی")
                        e.s("assistInput") != null -> L("پاس گل: {0}", personName(e.s("assistInput") ?: "")); else -> if (app.goallive.live.Lang.en) "Goal" else "گل"
                    }, 11, color = C.mute, align = textAlign, maxLines = 2)
                }
                "Card" -> {
                    Ltr(name, style = vz(12.sp, FontWeight.Bold), align = textAlign)
                    T(when (e.s("card")) { "Red" -> L("کارت قرمز"); "YellowRed" -> L("کارت زرد دوم و اخراج"); else -> L("کارت زرد") } +
                        (e.path("cardReason").s("defaultText")?.let { " · " + tr(it) } ?: ""), 11, color = C.mute, align = textAlign, maxLines = 2)
                }
                "Substitution" -> {
                    val sw = e.a("swap").objs()
                    Ltr(sw.getOrNull(0).s("name") ?: "", style = vz(12.sp, FontWeight.Bold), color = C.win, align = textAlign)
                    Ltr(L("بیرون: ") + personName(sw.getOrNull(1).s("name") ?: "") + if (e.b("injuredPlayerOut")) L(" · مصدوم") else "",
                        style = vz(11.sp), color = C.mute, align = textAlign, raw = true)
                }
                else -> { T("VAR", 13, FontWeight.Bold, align = textAlign); T(tr(e.path("VAR.decision").s("value") ?: ""), 11, color = C.mute, align = textAlign) }
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = if (goal) 5.dp else 2.dp)
        .then(if (goal) Modifier.padding(horizontal = 4.dp).clip(RoundedCornerShape(18.dp))
            .background(Brush.horizontalGradient(if (scorerOnLeft) listOf(color.copy(alpha = 0.24f), Color.Transparent, Color.Transparent) else listOf(Color.Transparent, Color.Transparent, color.copy(alpha = 0.24f))))
            .padding(vertical = 6.dp) else Modifier),
        verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
            if (home) { Box(Modifier.weight(1f, false)) { who() }; ico() }
        }
        Column(Modifier.padding(horizontal = 4.dp).width(46.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Box(Modifier.height(24.dp).widthIn(min = 40.dp)
                .then(if (goal) Modifier.glow(accent.copy(alpha = 0.35f), 6.dp, RoundedCornerShape(50)) else Modifier)
                .clip(RoundedCornerShape(50)).background(if (goal) accent else C.cardSolid).border(1.dp, if (goal) Color.Transparent else C.line2, RoundedCornerShape(50))
                .padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
                Ltr(fa(minuteText(e)) + "'", style = vz(11.sp, FontWeight.ExtraBold), color = if (goal) LocalAccentInk.current else C.mute, raw = true)
            }
            if (goal) e.a("newScore")?.takeIf { it.length() == 2 }?.let { sc -> ScoreText(sc.optInt(0), sc.optInt(1), 11, C.ink) }
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.Start), verticalAlignment = Alignment.CenterVertically) {
            if (!home) { ico(); Box(Modifier.weight(1f, false)) { who() } }
        }
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
