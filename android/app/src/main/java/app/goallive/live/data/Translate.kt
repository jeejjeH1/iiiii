package app.goallive.live.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * English text FotMob only has in English (the minute-by-minute commentary) put into Persian by Google's translator.
 * Translations are kept on the phone, so each line is translated once.
 */
object Translate {
    private val mem = ConcurrentHashMap<String, String>()
    private var file: File? = null

    fun init(ctx: Context) {
        file = File(ctx.cacheDir, "fa-translations.json")
        runCatching {
            val o = JSONObject(file!!.readText())
            o.keys().forEach { mem[it] = o.getString(it) }
        }
    }

    fun cached(text: String): String? = mem[text]

    /** Persian for each of [texts] (the English itself when it can't be translated). */
    suspend fun toPersian(texts: List<String>): List<String> = withContext(Dispatchers.IO) {
        val todo = texts.filter { it.isNotBlank() && mem[it] == null }.distinct()
        // one request per ~3500 characters: lines joined by new lines come back in the same lines
        // each line as asked (the key) and as sent (on one line, so the lines come back in order)
        var batch = ArrayList<Pair<String, String>>(); var len = 0
        suspend fun flush() {
            if (batch.isEmpty()) return
            val out = runCatching { request(batch.joinToString("\n") { it.second }) }.getOrNull()?.split("\n")
            if (out != null && out.size == batch.size) batch.zip(out).forEach { (en, fa) -> if (fa.isNotBlank()) mem[en.first] = fa.trim() }
            else for (one in batch) runCatching { request(one.second) }.getOrNull()?.let { mem[one.first] = it.trim() }
            batch = ArrayList(); len = 0
        }
        for (t in todo) {
            if (len + t.length > 3500) flush()
            batch += t to t.replace('\n', ' '); len += t.length + 1
        }
        flush()
        if (todo.isNotEmpty()) save()
        texts.map { mem[it] ?: it }
    }

    private fun request(text: String): String {
        val req = Request.Builder().url("https://translate.googleapis.com/translate_a/single?client=gtx&sl=en&tl=fa&dt=t")
            .post(FormBody.Builder().add("q", text).build()).header("User-Agent", "Mozilla/5.0").build()
        Api.client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw java.io.IOException("HTTP ${r.code}")
            // [[["persian","english",…],…],…]: the Persian pieces in order
            val parts = JSONArray(r.body!!.string()).getJSONArray(0)
            return buildString { for (k in 0 until parts.length()) parts.optJSONArray(k)?.optString(0)?.let { append(it) } }
        }
    }

    private fun save() = runCatching {
        // the newest 3000 lines are plenty
        val keep = mem.entries.toList().takeLast(3000)
        file?.writeText(JSONObject().apply { keep.forEach { put(it.key, it.value) } }.toString())
    }
}
