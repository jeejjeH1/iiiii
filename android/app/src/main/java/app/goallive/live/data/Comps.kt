package app.goallive.live.data

import app.goallive.live.L

/** Competition themes (assets/themes.js COMPS): accent, night background and glow. */
class Comp(val key: String, val fa: String, val tag: String, val accent: Long, val bg: Long, val glow: Long)

object Comps {
    private val MAP = mapOf(
        42 to Comp("ucl", L("لیگ قهرمانان اروپا"), L("شب لیگ قهرمانان"), 0xFF9CC3FF, 0xFF040B24, 0xFF3C6BFF),
        73 to Comp("uel", L("لیگ اروپا"), L("شب لیگ اروپا"), 0xFFFF8A2A, 0xFF140902, 0xFFFF6A00),
        10216 to Comp("uecl", L("لیگ کنفرانس"), L("شب لیگ کنفرانس"), 0xFF35E08A, 0xFF03120A, 0xFF12B864),
        77 to Comp("wc", L("جام جهانی"), L("جام جهانی"), 0xFFF2C96B, 0xFF16050C, 0xFFB8862B),
        78 to Comp("wc", L("جام جهانی باشگاه‌ها"), L("جام جهانی باشگاه‌ها"), 0xFFF2C96B, 0xFF0B0712, 0xFFB8862B),
        50 to Comp("euro", L("یورو"), L("یورو"), 0xFF56C8FF, 0xFF021029, 0xFF1A73FF),
        44 to Comp("copa", L("کوپا آمریکا"), L("کوپا آمریکا"), 0xFFFFD400, 0xFF00122B, 0xFF0057B8),
        525 to Comp("afc", L("لیگ نخبگان آسیا"), L("شب آسیایی"), 0xFFE3BB52, 0xFF0E0818, 0xFF7A3CFF),
        9469 to Comp("afc", L("لیگ قهرمانان آسیا ۲"), L("شب آسیایی"), 0xFFE3BB52, 0xFF0E0818, 0xFF7A3CFF),
        290 to Comp("afc", L("جام ملت‌های آسیا"), L("جام ملت‌های آسیا"), 0xFFE3BB52, 0xFF0E0818, 0xFF7A3CFF),
    )

    fun get(id: Int?): Comp? = id?.let { MAP[it] }
}
