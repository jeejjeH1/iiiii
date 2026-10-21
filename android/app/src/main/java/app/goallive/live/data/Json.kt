package app.goallive.live.data

import org.json.JSONArray
import org.json.JSONObject

// Null-safe readers for FotMob's loosely typed JSON.
fun JSONObject?.o(k: String): JSONObject? = this?.optJSONObject(k)
fun JSONObject?.a(k: String): JSONArray? = this?.optJSONArray(k)

fun JSONObject?.s(k: String): String? {
    if (this == null || !has(k) || isNull(k)) return null
    val v = opt(k)
    return if (v is JSONObject || v is JSONArray) null else v.toString().takeIf { it.isNotEmpty() }
}

fun JSONObject?.i(k: String): Int? = when (val v = this?.opt(k)) {
    is Number -> v.toInt()
    is String -> v.toIntOrNull() ?: v.toDoubleOrNull()?.toInt()
    else -> null
}

fun JSONObject?.l(k: String): Long? = when (val v = this?.opt(k)) {
    is Number -> v.toLong()
    is String -> v.toLongOrNull()
    else -> null
}

fun JSONObject?.d(k: String): Double? = when (val v = this?.opt(k)) {
    is Number -> v.toDouble()
    is String -> v.toDoubleOrNull()
    else -> null
}

fun JSONObject?.b(k: String): Boolean = when (val v = this?.opt(k)) {
    is Boolean -> v
    is String -> v == "true"
    is Number -> v.toInt() != 0
    else -> false
}

fun JSONArray?.objs(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
fun JSONArray?.arrs(): List<JSONArray> = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONArray(it) }
fun JSONArray?.strs(): List<String> = if (this == null) emptyList() else (0 until length()).mapNotNull { if (isNull(it)) null else opt(it)?.toString() }

/** Walk a dotted path: json.path("header.status.liveTime"). */
fun JSONObject?.path(p: String): JSONObject? {
    var cur = this
    for (k in p.split('.')) cur = cur?.optJSONObject(k) ?: return null
    return cur
}
