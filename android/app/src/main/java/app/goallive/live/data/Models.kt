package app.goallive.live.data

import org.json.JSONObject
import java.time.Instant

data class Side(val id: Int, val name: String, val score: Int?)

/** One fixture as shown in lists (home page, team fixtures, head-to-head, search). */
data class MatchItem(
    val id: Int,
    val home: Side,
    val away: Side,
    val time: Long,
    val started: Boolean,
    val finished: Boolean,
    val cancelled: Boolean,
    val minute: String,
    val reason: String,
    val leagueId: Int = 0,
    val leagueName: String = "",
    val penHome: Int? = null,
    val penAway: Int? = null,
) {
    val live get() = started && !finished && !cancelled
    val hasScore get() = (started || finished) && home.score != null
}

fun parseTime(s: String?): Long {
    if (s.isNullOrEmpty()) return 0
    return runCatching { Instant.parse(s).toEpochMilli() }.getOrElse {
        runCatching { Instant.parse(s.replace(".000", "")).toEpochMilli() }.getOrDefault(0)
    }
}

/** FotMob puts invisible direction marks and a curly quote around the minute ("45+2’"). */
fun cleanMinute(s: String?) = (s ?: "").replace("‎", "").replace("‏", "").replace("’", "'").trim()

fun side(t: JSONObject?): Side {
    val score = t.i("score") ?: t.s("score")?.toIntOrNull()
    return Side(t.i("id") ?: 0, t.s("shortName") ?: t.s("name") ?: "", score)
}

/** Works for the matches list, team fixtures and h2h entries (they share the same status shape). */
fun parseMatch(m: JSONObject, leagueId: Int = 0, leagueName: String = ""): MatchItem {
    val st = m.o("status")
    var home = side(m.o("home"))
    var away = side(m.o("away"))
    // some lists only carry the score as "2 - 1"
    val scoreStr = st.s("scoreStr")
    if (home.score == null && scoreStr != null) {
        val parts = scoreStr.split("-").map { it.trim().toIntOrNull() }
        if (parts.size == 2) { home = home.copy(score = parts[0]); away = away.copy(score = parts[1]) }
    }
    val time = m.l("timeTS") ?: parseTime(st.s("utcTime") ?: m.path("time").s("utcTime"))
    val id = m.i("id") ?: m.s("matchUrl")?.let { Regex("#(\\d+)").find(it)?.groupValues?.get(1)?.toIntOrNull() } ?: 0
    val pens = st.a("penalties")?.let { if (it.length() == 2) it.optInt(0) to it.optInt(1) else null }
    return MatchItem(
        id = id, home = home, away = away, time = time,
        started = st.b("started"), finished = st.b("finished"), cancelled = st.b("cancelled"),
        minute = cleanMinute(st.o("liveTime").s("short")),
        reason = st.o("reason").s("short") ?: "",
        leagueId = leagueId, leagueName = leagueName,
        penHome = pens?.first, penAway = pens?.second,
    )
}

data class LeagueBlock(val id: Int, val primaryId: Int, val name: String, val ccode: String, val matches: List<MatchItem>)

fun parseMatchList(d: JSONObject): List<LeagueBlock> = d.a("leagues").objs().map { l ->
    val pid = l.i("primaryId") ?: l.i("id") ?: 0
    val name = l.s("name") ?: ""
    LeagueBlock(l.i("id") ?: 0, pid, name, l.s("ccode") ?: "", l.a("matches").objs().map { parseMatch(it, pid, name) })
}
