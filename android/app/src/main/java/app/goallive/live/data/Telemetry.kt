package app.goallive.live.data

import android.content.Context
import android.os.Build
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/**
 * Crash reports and anonymous usage counts, sent to our own server (worker/src/telemetry.js) a few times a day.
 * Only a random id per install, the app and Android versions, the phone model, and how many times each screen or action
 * was used per day — never names, numbers, places or what was typed. Switched off in Settings.
 */
object Telemetry {
    private lateinit var ctx: Context
    private val prefs get() = ctx.getSharedPreferences("telemetry", Context.MODE_PRIVATE)
    private val lock = Any()
    private var counts = JSONObject()
    private var started = 0L
    private const val EVERY = 6 * 3_600_000L

    /** The screen in front, for crash reports and where people leave. */
    @Volatile var screen = ""
        private set

    val on: Boolean get() = runCatching { Store.prefs().getBoolean("telemetry", true) }.getOrDefault(true)
    fun enable(v: Boolean) { Store.prefs().edit().putBoolean("telemetry", v).apply(); if (!v) synchronized(lock) { counts = JSONObject(); prefs.edit().remove("counts").remove("crashes").apply() } }

    fun init(app: Context) {
        ctx = app
        counts = runCatching { JSONObject(prefs.getString("counts", "{}") ?: "{}") }.getOrDefault(JSONObject())
        val before = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching { if (on) keepCrash(e) }
            before?.uncaughtException(t, e)
        }
    }

    private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("Asia/Tehran") }.format(Date())

    private val device: String get() = prefs.getString("id", null) ?: UUID.randomUUID().toString().also { prefs.edit().putString("id", it).apply() }

    /** One more of [name] today (screen:…, story:…, share:…). */
    fun event(name: String, n: Int = 1) {
        if (!::ctx.isInitialized || !on) return
        synchronized(lock) {
            val day = counts.optJSONObject(today()) ?: JSONObject().also { counts.put(today(), it) }
            day.put(name, day.optInt(name) + n)
            // two weeks at most
            val keys = counts.keys().asSequence().toList().sorted()
            keys.dropLast(14).forEach { counts.remove(it) }
            prefs.edit().putString("counts", counts.toString()).apply()
        }
    }

    /** The route in front changed: counted once per visit. */
    fun screen(route: String?) {
        val s = route?.substringBefore('/')?.takeIf { it.isNotEmpty() } ?: return
        if (s == screen) return
        screen = s
        event("screen:$s")
    }

    fun sessionStart() {
        started = System.currentTimeMillis()
        event("open")
        if (hasCrash() || System.currentTimeMillis() - prefs.getLong("sent", 0) > EVERY) upload()
    }

    fun sessionEnd() {
        if (started == 0L) return
        val min = (System.currentTimeMillis() - started) / 60_000
        started = 0L
        event("exit:${screen.ifEmpty { "home" }}")
        event("len:" + when { min < 1 -> "0-1"; min < 5 -> "1-5"; min < 15 -> "5-15"; else -> "15+" })
        if (System.currentTimeMillis() - prefs.getLong("sent", 0) > EVERY) upload()
    }

    private fun hasCrash() = (prefs.getString("crashes", "[]") ?: "[]").length > 2

    /** Written straight to disk: the app is about to die. */
    private fun keepCrash(e: Throwable) {
        val sw = java.io.StringWriter(); e.printStackTrace(java.io.PrintWriter(sw))
        val list = runCatching { JSONArray(prefs.getString("crashes", "[]")) }.getOrDefault(JSONArray())
        if (list.length() >= 5) return
        list.put(JSONObject().put("msg", "${e.javaClass.name}: ${e.message ?: ""}".take(400)).put("stack", sw.toString().take(6000)).put("day", today()).put("screen", screen))
        prefs.edit().putString("crashes", list.toString()).commit()
    }

    /** Sends what has piled up; it stays for next time if the server can't be reached. */
    fun upload() {
        if (!::ctx.isInitialized || !on) return
        val (sent, crashes) = synchronized(lock) {
            val c = counts; counts = JSONObject()
            val cr = prefs.getString("crashes", "[]") ?: "[]"
            prefs.edit().putString("counts", "{}").putString("crashes", "[]").apply()
            c to cr
        }
        Thread {
            val ok = runCatching {
                val v = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: ""
                val body = JSONObject().put("d", device).put("v", v).put("sdk", Build.VERSION.SDK_INT).put("m", "${Build.MANUFACTURER} ${Build.MODEL}".take(60))
                    .put("l", if (app.goallive.live.Lang.en) "en" else "fa").put("days", sent).put("crashes", JSONArray(crashes))
                val req = Request.Builder().url(Api.SITE + "/t/up").post(body.toString().toRequestBody("application/json".toMediaType())).build()
                Api.client.newCall(req).execute().use { it.isSuccessful }
            }.getOrDefault(false)
            if (ok) prefs.edit().putLong("sent", System.currentTimeMillis()).apply()
            else synchronized(lock) {
                // put it back under what came since
                for (day in sent.keys()) {
                    val add = sent.getJSONObject(day)
                    val cur = counts.optJSONObject(day) ?: JSONObject().also { counts.put(day, it) }
                    for (k in add.keys()) cur.put(k, cur.optInt(k) + add.optInt(k))
                }
                val back = JSONArray(crashes)
                val now = runCatching { JSONArray(prefs.getString("crashes", "[]")) }.getOrDefault(JSONArray())
                for (i in 0 until now.length()) if (back.length() < 5) back.put(now.get(i))
                prefs.edit().putString("counts", counts.toString()).putString("crashes", back.toString()).apply()
            }
        }.start()
    }
}
