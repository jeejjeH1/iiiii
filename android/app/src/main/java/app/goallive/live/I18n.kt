@file:JvmName("I18n")

package app.goallive.live

import android.content.Context
import android.content.Intent
import org.json.JSONObject

/**
 * The app's language: Persian (the source text) or English.
 * Every Persian string in the code goes through [L]; in English it is looked up in assets/en.json.
 * The language is read once at start-up and a change restarts the app, so lists built once stay in one language.
 */
object Lang {
    @JvmField var en = false
    private var table: Map<String, String> = emptyMap()

    fun init(ctx: Context) {
        en = ctx.getSharedPreferences("goallive", Context.MODE_PRIVATE).getString("lang", "fa") == "en"
        if (en) table = runCatching {
            val o = JSONObject(ctx.assets.open("en.json").bufferedReader().use { it.readText() })
            o.keys().asSequence().associateWith { o.getString(it) }
        }.getOrDefault(emptyMap())
    }

    internal fun lookup(key: String) = table[key]

    /** Saves the choice and starts the app again in that language. */
    fun switch(ctx: Context, english: Boolean) {
        ctx.getSharedPreferences("goallive", Context.MODE_PRIVATE).edit().putString("lang", if (english) "en" else "fa").commit()
        val start = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName) ?: return
        ctx.startActivity(Intent.makeRestartActivityTask(start.component))
        Runtime.getRuntime().exit(0)
    }
}

/** A piece of text in the current language; {0}, {1}… in [key] are filled with [args]. */
fun L(key: String, vararg args: Any?): String {
    var s = if (Lang.en) Lang.lookup(key) ?: key else key
    for (i in args.indices) s = s.replace("{$i}", args[i]?.toString() ?: "")
    return s
}
