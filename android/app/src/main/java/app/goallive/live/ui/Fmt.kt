package app.goallive.live.ui

import app.goallive.live.L

import android.icu.text.SimpleDateFormat
import android.icu.util.ULocale
import app.goallive.live.data.MatchItem
import app.goallive.live.data.tr
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** Dates in the app's language: the Persian calendar, or the Gregorian one in English. */
val FA: ULocale get() = if (app.goallive.live.Lang.en) ULocale("en_GB@calendar=gregorian") else ULocale("fa_IR@calendar=persian")

/** Persian digits (the app font already draws them; this is for notifications and the system UI). */
fun fa(s: Any?): String {
    val str = s?.toString() ?: return ""
    if (app.goallive.live.Lang.en) return str
    val b = StringBuilder(str.length)
    for (ch in str) b.append(if (ch in '0'..'9') '۰' + (ch - '0') else if (ch == '.') '٫' else ch)
    return b.toString()
}

fun timeOf(ms: Long): String = java.text.SimpleDateFormat("HH:mm", Locale.US).format(Date(ms))

fun persianDate(ms: Long, pattern: String = "EEEE d MMMM"): String = SimpleDateFormat(pattern, FA).format(Date(ms))

fun dayKey(ms: Long): String = java.text.SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(ms))

fun startOfDay(offset: Int): Long = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    add(Calendar.DAY_OF_YEAR, offset)
}.timeInMillis

fun dayName(ms: Long): String {
    val k = dayKey(ms)
    return when (k) {
        dayKey(startOfDay(0)) -> L("امروز")
        dayKey(startOfDay(-1)) -> L("دیروز")
        dayKey(startOfDay(1)) -> L("فردا")
        else -> persianDate(ms, "EEEE")
    }
}

fun shortDate(ms: Long): String = when (dayKey(ms)) {
    dayKey(startOfDay(0)) -> L("امروز")
    dayKey(startOfDay(-1)) -> L("دیروز")
    dayKey(startOfDay(1)) -> L("فردا")
    else -> persianDate(ms, "d MMMM")
}

fun countdown(ms: Long): String {
    val d = ms - System.currentTimeMillis()
    if (d <= 0) return L("به‌زودی")
    val h = (d / 3_600_000).toInt()
    val m = ((d % 3_600_000) / 60_000).toInt()
    return when {
        h >= 48 -> L("{0} روز دیگر", h / 24)
        h > 0 -> L("{0} ساعت و {1} دقیقه دیگر", h, m)
        else -> L("{0} دقیقه دیگر", m)
    }
}

/** Short status for a list row: minute when live, FT/HT etc. when not. */
fun statusText(m: MatchItem): String = when {
    m.cancelled -> tr(m.reason).ifEmpty { L("لغو") }
    m.finished -> tr(m.reason.ifEmpty { "FT" })
    m.live -> if (m.minute == "HT") L("بین دو نیمه") else m.minute
    else -> ""
}

/** A number with up to [digits] decimals, in Persian digits with the Persian decimal mark (٫) in Persian. */
fun dec(v: Double?, digits: Int = 2): String {
    if (v == null) return "–"
    if (v == v.roundToInt().toDouble()) return fa(v.roundToInt())
    return fa(String.format(Locale.US, "%.${digits}f", v).trimEnd('0').trimEnd('.'))
}

fun money(v: Long?): String {
    if (v == null || v <= 0) return ""
    return when {
        v >= 1_000_000 -> "€${dec(v / 1_000_000.0, 1)}M"
        v >= 1_000 -> "€${v / 1000}K"
        else -> "€$v"
    }
}

/** A Latin name inside a Persian sentence, isolated so the sentence's word order survives (Unicode FSI…PDI). */
fun iso(s: String) = "⁨$s⁩"
