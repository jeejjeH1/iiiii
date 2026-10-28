package app.goallive.live.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/** The player in the prediction league, as the server knows them; [phone] is shown masked, null until a number is added. */
data class Player(val id: Int, val name: String, val phone: String?, val picks: Int, val points: Int, val exact: Int, val email: String? = null)

/** An answer the server turned down: [code] is its reason ("wrong", "expired", "limit", "login"…). */
class LeagueError(val code: String, val http: Int) : Exception(code)

/**
 * The prediction league account. It is optional: predictions work on the phone without it. A name is enough to start
 * (a guest account); adding a phone number keeps it, so the same number brings everything back on any phone.
 * The server's key for this phone is kept in the app's settings.
 */
object Account {
    private const val BASE = Api.SITE + "/p"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    var token by mutableStateOf(Store.prefs().getString("league.token", null)); private set
    var me by mutableStateOf(Store.prefs().getString("league.me", null)?.let { runCatching { player(JSONObject(it)) }.getOrNull() }); private set
    /** A league code from an invite link, waiting for the league screen. */
    var pendingCode by mutableStateOf<String?>(null)

    val signedIn get() = token != null

    private fun player(o: JSONObject) = Player(o.optInt("id"), o.optString("name"), o.optString("phone").takeIf { it.isNotEmpty() && it != "null" },
        o.optInt("picks"), o.optInt("points"), o.optInt("exact"), o.optString("email").takeIf { it.isNotEmpty() && it != "null" })

    /** Whether the account can come back on another phone (a number or a Google account). */
    val kept get() = me?.phone != null || me?.email != null

    /** The server's Google sign-in client; null while Google sign-in is not set up (the button stays hidden). */
    var googleClient by mutableStateOf(Store.prefs().getString("league.google", null)
        ?: app.goallive.live.BuildConfig.GOOGLE_CLIENT.takeIf { it.isNotEmpty() }); private set

    /** Asks the server whether Google sign-in is on. */
    fun loadConfig() {
        scope.launch {
            runCatching { call("/config") }.onSuccess { j ->
                googleClient = j.optString("google").takeIf { it.isNotEmpty() && it != "null" }
                Store.prefs().edit().putString("league.google", googleClient).apply()
            }
        }
    }

    /** Logs in with a Google account picked on the phone; as with a number, an account seen before comes back here. */
    suspend fun google(activity: android.app.Activity) {
        val client = googleClient ?: throw LeagueError("google", 0)
        val option = com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption.Builder(client).build()
        val cred = androidx.credentials.CredentialManager.create(activity)
            .getCredential(activity, androidx.credentials.GetCredentialRequest.Builder().addCredentialOption(option).build()).credential
        if (cred !is androidx.credentials.CustomCredential || cred.type != com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)
            throw LeagueError("google", 0)
        val g = com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.createFrom(cred.data)
        val j = call("/google", JSONObject().put("token", g.idToken).put("name", g.displayName ?: ""))
        keep(j.getString("token"), j.getJSONObject("user"))
        pullPicks()
        pushAll()
        Backup.restore()
    }

    private fun keep(t: String?, u: JSONObject?) {
        token = t ?: token
        u?.let { me = player(it) }
        Store.prefs().edit().putString("league.token", token).putString("league.me", u?.toString() ?: Store.prefs().getString("league.me", null)).apply()
    }

    fun signOut() {
        token = null; me = null
        Store.prefs().edit().remove("league.token").remove("league.me").apply()
    }

    private suspend fun call(path: String, body: JSONObject? = null): JSONObject = withContext(Dispatchers.IO) {
        val rb = Request.Builder().url(BASE + path)
        token?.let { rb.header("Authorization", "Bearer $it") }
        if (body != null) rb.post(body.toString().toRequestBody("application/json".toMediaType()))
        Api.client.newCall(rb.build()).execute().use { r ->
            val text = r.body?.string().orEmpty()
            val j = runCatching { JSONObject(text) }.getOrElse { JSONObject() }
            if (!r.isSuccessful) {
                if (r.code == 401 && j.optString("error") == "login") signOut()
                throw LeagueError(j.optString("error").ifEmpty { "net" }, r.code)
            }
            j
        }
    }

    /** Starts with just a name; this phone's predictions go up with it. */
    suspend fun startAsGuest(name: String) {
        val j = call("/guest", JSONObject().put("name", name))
        keep(j.getString("token"), j.getJSONObject("user"))
        pushAll()
        Backup.upload()
    }

    /** Asks for a login code; returns the seconds to wait before another, and the code itself while the server is in test mode. */
    suspend fun askCode(phone: String): Pair<Int, String?> {
        val j = call("/otp", JSONObject().put("phone", phone))
        if (!j.optBoolean("ok")) return j.optInt("wait", 60) to null
        return j.optInt("wait", 90) to j.optString("test").takeIf { it.isNotEmpty() }
    }

    /** Logs in with the code: a number seen before brings its account (and its predictions) back to this phone. */
    suspend fun verify(phone: String, code: String, name: String?) {
        val j = call("/verify", JSONObject().put("phone", phone).put("code", code).put("name", name ?: ""))
        keep(j.getString("token"), j.getJSONObject("user"))
        pullPicks()
        pushAll()
        // follows, my team, fantasy, tactics and settings from the account (or this phone's, sent up)
        Backup.restore()
    }

    suspend fun refresh() { if (signedIn) keep(null, call("/me").getJSONObject("user")) }

    /** Deletes the account on the server and logs out here (the predictions stay on this phone). */
    suspend fun deleteAccount() { call("/delete", JSONObject()); signOut() }

    suspend fun rename(name: String) { keep(null, call("/name", JSONObject().put("name", name)).getJSONObject("user")) }

    /** The predictions the account made on other phones, added to this one's. */
    private suspend fun pullPicks() {
        val a = call("/picks").optJSONArray("picks") ?: return
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            val id = o.optInt("m")
            if (Predictions.get(id) != null) continue
            Predictions.restore(Pick(id, o.optInt("h"), o.optInt("a"), o.optInt("hi"), o.optInt("ai"), o.optString("hn"), o.optString("an"),
                o.optLong("t"), o.optString("l"), if (o.optInt("done") == 1 && !o.isNull("rh")) o.optInt("rh") to o.optInt("ra") else null))
        }
    }

    /** Every prediction on this phone for a match still to come, sent up (the server keeps only those made before kick-off). */
    suspend fun pushAll() {
        val open = Predictions.all.values.filter { it.result == null && it.time > System.currentTimeMillis() }
        if (open.isEmpty() || !signedIn) return
        runCatching { call("/picks", JSONObject().put("picks", JSONArray().apply { open.forEach { put(pickJson(it)) } })) }
    }

    /** One new or changed prediction, sent in the background. */
    fun push(p: Pick) {
        if (!signedIn) return
        scope.launch { runCatching { call("/picks", JSONObject().put("picks", JSONArray().put(pickJson(p)))) } }
    }

    private fun pickJson(p: Pick) = JSONObject().put("m", p.matchId).put("h", p.home).put("a", p.away)

    /** The account's copy of the app's other things (see [Backup]); null when it has none yet. */
    suspend fun getBackup(): String? = call("/backup").let { j -> if (j.isNull("data")) null else j.optString("data") }
    suspend fun putBackup(data: String) { call("/backup", JSONObject().put("data", data)) }

    suspend fun leagues(): JSONObject = call("/leagues")
    suspend fun create(name: String): JSONObject = call("/leagues", JSONObject().put("name", name)).getJSONObject("league")
    suspend fun join(code: String): JSONObject = call("/join", JSONObject().put("code", code)).getJSONObject("league")
    suspend fun leave(id: Int) { call("/leave", JSONObject().put("id", id)) }
    suspend fun table(id: Int, week: Boolean): JSONObject = call("/league?id=$id&span=" + if (week) "week" else "all")

    /* fantasy */

    /** This gameweek's squad, sent while it can still change (the server takes it only before [first] kicks off). */
    fun fantasyTeam(comp: Int, round: String, first: Int, team: List<Int>, bench: List<Int>, captain: Int) {
        if (!signedIn || team.isEmpty() || first <= 0) return
        scope.launch {
            runCatching { call("/fteam", JSONObject().put("comp", comp).put("round", round).put("first", first).put("team", JSONArray(team)).put("bench", JSONArray(bench)).put("captain", captain)) }
        }
    }

    /** A finished gameweek's points, for the fantasy tables. */
    fun fantasyPoints(comp: Int, round: String, pts: Int) {
        if (!signedIn) return
        scope.launch { runCatching { call("/fpoints", JSONObject().put("comp", comp).put("round", round).put("pts", pts)) } }
    }

    suspend fun fantasyLeagues(comp: Int): JSONObject = call("/fleagues?comp=$comp")
    suspend fun fantasyCreate(name: String, comp: Int): JSONObject = call("/fleagues", JSONObject().put("name", name).put("comp", comp)).getJSONObject("league")
    suspend fun fantasyJoin(code: String): JSONObject = call("/fjoin", JSONObject().put("code", code)).getJSONObject("league")
    suspend fun fantasyLeave(id: Int) { call("/fleave", JSONObject().put("id", id)) }
    suspend fun fantasyTable(id: Int, comp: Int, round: String?): JSONObject =
        call("/fleague?id=$id&comp=$comp" + (round?.let { "&round=" + java.net.URLEncoder.encode(it, "UTF-8") } ?: ""))

    /** The invite link for a league: it opens the app on the league, or the app's page on Cafe Bazaar. */
    fun inviteLink(code: String) = Api.SITE + "/j/" + code
}
