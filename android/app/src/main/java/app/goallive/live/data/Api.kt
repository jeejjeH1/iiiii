package app.goallive.live.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
        mem[url]?.let { (t, v) -> if (System.currentTimeMillis() - t < maxAge) return@withContext v }
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

    fun fetch(url: String): String {
        val req = Request.Builder().url(url).header("Accept", "application/json").build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            return r.body?.string() ?: throw IOException("empty")
        }
    }

    private fun parse(text: String): Any = JSONTokener(text).nextValue()

    private fun snapFile(url: String) = File(snapDir, Integer.toHexString(url.hashCode()) + ".json")
}
