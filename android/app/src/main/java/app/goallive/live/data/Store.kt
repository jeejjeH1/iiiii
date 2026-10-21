package app.goallive.live.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject

data class MyTeam(val id: Int, val name: String, val color: String?)

/**
 * User choices: followed teams/leagues/matches, "my team" and notification switches.
 * Compose state, so every screen updates as soon as something is starred.
 */
object Store {
    private lateinit var p: SharedPreferences
    // the home-screen widget reads "my team" from here (same place the old app used)
    private lateinit var widgetPrefs: SharedPreferences

    var teams by mutableStateOf<Map<Int, String>>(emptyMap()); private set
    var leagues by mutableStateOf<Map<Int, String>>(emptyMap()); private set
    var matches by mutableStateOf<Set<Int>>(emptySet()); private set
    var myTeam by mutableStateOf<MyTeam?>(null); private set

    var notifyGoals by mutableStateOf(true); private set
    var notifyLineups by mutableStateOf(true); private set
    var notifyStartEnd by mutableStateOf(true); private set

    /** Called whenever follows change, so the alert checker can start or stop. */
    var onFollowChange: (() -> Unit)? = null

    fun init(ctx: Context) {
        p = ctx.getSharedPreferences("goallive", Context.MODE_PRIVATE)
        widgetPrefs = ctx.getSharedPreferences("CapacitorStorage", Context.MODE_PRIVATE)
        teams = readMap("teams")
        leagues = readMap("leagues")
        matches = p.getStringSet("matches", emptySet())!!.mapNotNull { it.toIntOrNull() }.toSet()
        myTeam = widgetPrefs.getString("myTeam", null)?.let { raw ->
            runCatching { JSONObject(raw).let { MyTeam(it.getString("id").toInt(), it.optString("name"), it.optString("color").ifEmpty { null }) } }.getOrNull()
        }
        notifyGoals = p.getBoolean("nGoals", true)
        notifyLineups = p.getBoolean("nLineups", true)
        notifyStartEnd = p.getBoolean("nStartEnd", true)
    }

    private fun readMap(key: String): Map<Int, String> = runCatching {
        val o = JSONObject(p.getString(key, "{}")!!)
        o.keys().asSequence().mapNotNull { k -> k.toIntOrNull()?.let { it to o.optString(k) } }.toMap()
    }.getOrDefault(emptyMap())

    private fun writeMap(key: String, m: Map<Int, String>) {
        p.edit().putString(key, JSONObject(m.mapKeys { it.key.toString() }).toString()).apply()
    }

    fun isTeam(id: Int) = id in teams || myTeam?.id == id
    fun isLeague(id: Int) = id in leagues

    fun toggleTeam(id: Int, name: String) {
        teams = if (id in teams) teams - id else teams + (id to name)
        writeMap("teams", teams)
        onFollowChange?.invoke()
    }

    fun toggleLeague(id: Int, name: String) {
        leagues = if (id in leagues) leagues - id else leagues + (id to name)
        writeMap("leagues", leagues)
    }

    fun toggleMatch(id: Int) {
        matches = if (id in matches) matches - id else matches + id
        p.edit().putStringSet("matches", matches.map { it.toString() }.toSet()).apply()
        onFollowChange?.invoke()
    }

    fun pickMyTeam(t: MyTeam?) {
        myTeam = t
        if (t == null) widgetPrefs.edit().remove("myTeam").apply()
        else {
            val fa = Clubs.get(t.id)?.fa ?: t.name
            val o = JSONObject().put("id", t.id.toString()).put("name", t.name).put("fa", fa)
            t.color?.let { o.put("color", it) }
            widgetPrefs.edit().putString("myTeam", o.toString()).apply()
            if (t.id !in teams) toggleTeam(t.id, t.name)
        }
    }

    fun setNotify(goals: Boolean = notifyGoals, lineups: Boolean = notifyLineups, startEnd: Boolean = notifyStartEnd) {
        notifyGoals = goals; notifyLineups = lineups; notifyStartEnd = startEnd
        p.edit().putBoolean("nGoals", goals).putBoolean("nLineups", lineups).putBoolean("nStartEnd", startEnd).apply()
    }

    /** A match is "followed" when the user starred it or one of its teams / its league. */
    fun follows(m: MatchItem) = m.id in matches || isTeam(m.home.id) || isTeam(m.away.id)
    fun followsWithLeague(m: MatchItem) = follows(m) || isLeague(m.leagueId)

    fun prefs() = p
}
