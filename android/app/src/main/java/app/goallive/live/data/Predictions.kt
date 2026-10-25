package app.goallive.live.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

/** One guessed score; [result] is filled in once the match is over. */
data class Pick(
    val matchId: Int, val home: Int, val away: Int,
    val homeId: Int, val awayId: Int, val homeName: String, val awayName: String,
    val time: Long, val league: String, val result: Pair<Int, Int>? = null,
) {
    /** 3 for the exact score, 1 for the right winner (or a draw), 0 otherwise; null until the match is over. */
    val points: Int? get() = result?.let { (h, a) ->
        when {
            h == home && a == away -> 3
            Integer.signum(h - a) == Integer.signum(home - away) -> 1
            else -> 0
        }
    }
}

/** The user's score predictions, kept on the phone. Points: exact score 3, right outcome 1. */
object Predictions {
    private lateinit var p: SharedPreferences
    var all by mutableStateOf<Map<Int, Pick>>(emptyMap()); private set

    fun init(ctx: Context) {
        p = ctx.getSharedPreferences("goallive", Context.MODE_PRIVATE)
        all = runCatching {
            val a = JSONArray(p.getString("predictions", "[]"))
            (0 until a.length()).map { a.getJSONObject(it) }.map { o ->
                Pick(
                    o.getInt("m"), o.getInt("h"), o.getInt("a"), o.optInt("hi"), o.optInt("ai"), o.optString("hn"), o.optString("an"),
                    o.optLong("t"), o.optString("l"), if (o.has("rh")) o.getInt("rh") to o.getInt("ra") else null,
                )
            }.associateBy { it.matchId }
        }.getOrDefault(emptyMap())
    }

    fun get(matchId: Int) = all[matchId]

    fun put(pick: Pick) { all = all + (pick.matchId to pick); save(); Telemetry.event("predict:made") }

    /** Records the final score; returns the points the pick earned. */
    fun settle(matchId: Int, home: Int, away: Int): Int? {
        val pk = all[matchId] ?: return null
        if (pk.result != home to away) { all = all + (matchId to pk.copy(result = home to away)); save() }
        return all[matchId]?.points
    }

    val total get() = all.values.sumOf { it.points ?: 0 }
    val settled get() = all.values.filter { it.result != null }
    val exact get() = settled.count { it.points == 3 }
    val right get() = settled.count { (it.points ?: 0) > 0 }

    /** Correct picks in a row, newest first. */
    val streak: Int get() {
        var n = 0
        for (pk in settled.sortedByDescending { it.time }) if ((pk.points ?: 0) > 0) n++ else break
        return n
    }

    private fun save() {
        val a = JSONArray()
        // the last 300 are plenty
        for (pk in all.values.sortedByDescending { it.time }.take(300)) a.put(JSONObject().apply {
            put("m", pk.matchId); put("h", pk.home); put("a", pk.away); put("hi", pk.homeId); put("ai", pk.awayId)
            put("hn", pk.homeName); put("an", pk.awayName); put("t", pk.time); put("l", pk.league)
            pk.result?.let { put("rh", it.first); put("ra", it.second) }
        })
        p.edit().putString("predictions", a.toString()).apply()
    }
}
