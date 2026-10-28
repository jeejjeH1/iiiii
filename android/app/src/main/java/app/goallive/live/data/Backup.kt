package app.goallive.live.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Everything else the app keeps, kept with the league account too: followed teams, leagues and matches, my team, the fantasy
 * side, the coach game, the quiz streak, saved tactics and the settings. Sent a little after each change while logged in,
 * and brought back when the same number logs in on another phone (follows are merged, the rest comes from the account).
 * Predictions travel on their own (see [Account]).
 */
object Backup {
    private lateinit var app: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pending: Job? = null
    private var lastSent = ""

    private val KEYS = setOf("teams", "leagues", "matches", "nGoals", "nLineups", "nStartEnd", "fastAlerts", "faNames", "voice", "voiceName",
        "theme", "lang", "fantasy", "coachHistory", "quizStreak", "quizLastDone", "story.video", "story.music", "reel.sound", "onboarded")
    private val PREFIXES = listOf("studio.", "card.", "tip_")
    private fun kept(k: String) = k in KEYS || PREFIXES.any { k.startsWith(it) }

    private val main get() = app.getSharedPreferences("goallive", Context.MODE_PRIVATE)
    private val widget get() = app.getSharedPreferences("CapacitorStorage", Context.MODE_PRIVATE)

    // held here: SharedPreferences keeps its listeners weakly
    private val watch = SharedPreferences.OnSharedPreferenceChangeListener { _, k -> if (k == null || kept(k) || k == "myTeam") soon() }

    fun init(ctx: Context) {
        app = ctx.applicationContext
        main.registerOnSharedPreferenceChangeListener(watch)
        widget.registerOnSharedPreferenceChangeListener(watch)
    }

    /** This phone's things as one JSON text. */
    private fun snapshot(): String {
        val o = JSONObject()
        for ((k, v) in main.all) if (kept(k)) o.put(k, when (v) {
            is String -> JSONObject().put("s", v)
            is Boolean -> JSONObject().put("b", v)
            is Int -> JSONObject().put("i", v)
            is Long -> JSONObject().put("l", v)
            is Float -> JSONObject().put("f", v.toDouble())
            is Set<*> -> JSONObject().put("set", JSONArray(v.map { it.toString() }))
            else -> continue
        })
        widget.getString("myTeam", null)?.let { o.put("w:myTeam", JSONObject().put("s", it)) }
        return o.toString()
    }

    /** Sends the snapshot a little after the last change (several changes in a row go up once). */
    fun soon() {
        if (!Account.signedIn) return
        pending?.cancel()
        pending = scope.launch { delay(15_000); runCatching { upload() } }
    }

    suspend fun upload() {
        if (!Account.signedIn) return
        val now = snapshot()
        if (now == lastSent) return
        Account.putBackup(now)
        lastSent = now
    }

    /** After logging in: the account's copy brought to this phone (follows merged), then the result sent back up. */
    suspend fun restore() {
        val data = runCatching { Account.getBackup() }.getOrNull()
        if (data != null) kotlinx.coroutines.withContext(Dispatchers.Main) { runCatching { apply(JSONObject(data)) } }
        runCatching { upload() }
    }

    private fun apply(o: JSONObject) {
        val e = main.edit()
        for (k in o.keys()) {
            val v = o.optJSONObject(k) ?: continue
            if (k == "w:myTeam") { widget.edit().putString("myTeam", v.optString("s")).apply(); continue }
            if (!kept(k)) continue
            when {
                // follows: what this phone had and what the account had, together
                k == "teams" || k == "leagues" -> {
                    val mine = runCatching { JSONObject(main.getString(k, "{}")!!) }.getOrDefault(JSONObject())
                    val theirs = runCatching { JSONObject(v.optString("s")) }.getOrDefault(JSONObject())
                    for (x in theirs.keys()) if (!mine.has(x)) mine.put(x, theirs.opt(x))
                    e.putString(k, mine.toString())
                }
                k == "matches" -> {
                    val a = v.optJSONArray("set") ?: JSONArray()
                    e.putStringSet(k, (main.getStringSet(k, emptySet())!! + (0 until a.length()).map { a.optString(it) }).toSet())
                }
                v.has("s") -> e.putString(k, v.optString("s"))
                v.has("b") -> e.putBoolean(k, v.optBoolean("b"))
                v.has("i") -> e.putInt(k, v.optInt("i"))
                v.has("l") -> e.putLong(k, v.optLong("l"))
                v.has("f") -> e.putFloat(k, v.optDouble("f").toFloat())
                v.has("set") -> v.optJSONArray("set")?.let { a -> e.putStringSet(k, (0 until a.length()).map { a.optString(it) }.toSet()) }
            }
        }
        e.commit()
        // the app's state read again from what is now saved
        Store.init(app)
        Store.onFollowChange?.invoke()
    }
}
