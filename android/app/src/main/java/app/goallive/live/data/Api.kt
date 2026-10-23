package app.goallive.live.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * The app talks only to its own server (the Vercel functions in /api), which proxies FotMob and Aparat.
 * Responses are kept in memory for [maxAge] and the last copy of each is saved to disk,
 * so screens open instantly with old data and still work when the network drops.
 */
object Api {
    const val SITE = "https://iiiii-psi.vercel.app"

    private lateinit var http: OkHttpClient
    private lateinit var snapDir: File
    private val mem = ConcurrentHashMap<String, Pair<Long, Any>>()
    /** Pull-to-refresh: anything kept in memory from before this moment is fetched again. */
    @Volatile private var freshAfter = 0L

    fun invalidate() { freshAfter = System.currentTimeMillis() }

    fun init(ctx: Context) {
        http = OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
        snapDir = File(ctx.cacheDir, "snap").apply { mkdirs() }
    }

    val client: OkHttpClient get() = http

    fun fmUrl(e: String, params: Map<String, Any?>): String {
        val q = params.filter { it.value != null && it.value.toString().isNotEmpty() }
            .entries.joinToString("") { "&${it.key}=${URLEncoder.encode(it.value.toString(), "UTF-8")}" }
        return "$SITE/api/fm?e=$e$q"
    }

    suspend fun fm(e: String, params: Map<String, Any?> = emptyMap(), maxAge: Long = 15_000): JSONObject =
        get(fmUrl(e, params), maxAge) as? JSONObject ?: throw IOException("bad response")

    suspend fun fmArray(e: String, params: Map<String, Any?> = emptyMap(), maxAge: Long = 15_000): JSONArray =
        get(fmUrl(e, params), maxAge) as? JSONArray ?: throw IOException("bad response")

    suspend fun obj(path: String, maxAge: Long = 60_000): JSONObject =
        get(SITE + path, maxAge) as? JSONObject ?: throw IOException("bad response")

    fun matchesParams(date: String) = mapOf("date" to date, "timezone" to TimeZone.getDefault().id, "ccode3" to "IRN")

    /** Last good copy, from memory or disk, without touching the network. */
    fun peek(url: String): Any? = mem[url]?.second ?: runCatching {
        val f = snapFile(url)
        if (f.exists()) parse(f.readText()) else null
    }.getOrNull()

    suspend fun get(url: String, maxAge: Long): Any = withContext(Dispatchers.IO) {
        mem[url]?.let { (t, v) -> if (t > freshAfter && System.currentTimeMillis() - t < maxAge) return@withContext v }
        try {
            val text = fetch(url)
            val v = parse(text)
            mem[url] = System.currentTimeMillis() to v
            if (mem.size > 300) mem.keys.take(100).forEach { mem.remove(it) }
            runCatching { snapFile(url).writeText(text) }
            v
        } catch (e: Exception) {
            peek(url) ?: throw e
        }
    }

    /** Set when our server fails (down, out of quota, filtered): FotMob is then asked directly for a while. */
    @Volatile private var serverDownUntil = 0L

    /** FotMob paths behind /api/fm?e=… (same list as api/fm.js). */
    private val FM_PATHS = mapOf(
        "matches" to "data/matches", "matchDetails" to "data/matchDetails", "tltable" to "data/tltable", "ltc" to "data/ltc",
        "leagues" to "data/leagues", "teams" to "data/teams", "playerData" to "data/playerData", "suggest" to "data/search/suggest",
        "allLeagues" to "data/allLeagues",
    )

    /** The same request straight to FotMob, or null when [url] is not one of our FotMob proxy calls. */
    private fun directUrl(url: String): String? {
        val u = url.toHttpUrlOrNull() ?: return null
        if (!url.startsWith("$SITE/api/fm?")) return null
        val e = u.queryParameter("e") ?: return null
        val path = FM_PATHS[e] ?: return null
        val b = "https://www.fotmob.com/api/$path".toHttpUrl().newBuilder()
        if (e == "ltc") {
            // the server builds this from the match id; do the same here
            val id = u.queryParameter("matchId")?.filter(Char::isDigit) ?: return null
            b.addQueryParameter("ltcUrl", "http://data.fotmob.com/webcl/ltc/gsm/${id}_en.json.gz")
            b.addQueryParameter("teams", u.queryParameter("teams") ?: "[]")
        } else for (name in u.queryParameterNames) if (name != "e") u.queryParameter(name)?.let { b.addQueryParameter(name, it) }
        return b.build().toString()
    }

    fun fetch(url: String): String {
        val direct = directUrl(url)
        if (direct != null && System.currentTimeMillis() < serverDownUntil) return fetchRaw(direct, fotmob = true)
        return try {
            fetchRaw(url, fotmob = false)
        } catch (e: IOException) {
            if (direct == null) throw e
            serverDownUntil = System.currentTimeMillis() + 10 * 60_000
            fetchRaw(direct, fotmob = true)
        }
    }

    private fun fetchRaw(url: String, fotmob: Boolean): String {
        val req = Request.Builder().url(url).header("Accept", "application/json").apply {
            if (fotmob) header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124 Mobile Safari/537.36").header("Referer", "https://www.fotmob.com/")
        }.build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            return r.body?.string() ?: throw IOException("empty")
        }
    }

    private fun parse(text: String): Any = JSONTokener(text).nextValue()

    private fun snapFile(url: String) = File(snapDir, Integer.toHexString(url.hashCode()) + ".json")
}
