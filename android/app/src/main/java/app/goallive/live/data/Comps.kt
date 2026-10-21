package app.goallive.live.data

/** Competition themes (assets/themes.js COMPS): accent, night background and glow. */
class Comp(val key: String, val fa: String, val tag: String, val accent: Long, val bg: Long, val glow: Long)

object Comps {
    private val MAP = mapOf(
        42 to Comp("ucl", "لیگ قهرمانان اروپا", "شب لیگ قهرمانان", 0xFF9CC3FF, 0xFF040B24, 0xFF3C6BFF),
        73 to Comp("uel", "لیگ اروپا", "شب لیگ اروپا", 0xFFFF8A2A, 0xFF140902, 0xFFFF6A00),
        10216 to Comp("uecl", "لیگ کنفرانس", "شب لیگ کنفرانس", 0xFF35E08A, 0xFF03120A, 0xFF12B864),
        77 to Comp("wc", "جام جهانی", "جام جهانی", 0xFFF2C96B, 0xFF16050C, 0xFFB8862B),
        78 to Comp("wc", "جام جهانی باشگاه‌ها", "جام جهانی باشگاه‌ها", 0xFFF2C96B, 0xFF0B0712, 0xFFB8862B),
        50 to Comp("euro", "یورو", "یورو", 0xFF56C8FF, 0xFF021029, 0xFF1A73FF),
        44 to Comp("copa", "کوپا آمریکا", "کوپا آمریکا", 0xFFFFD400, 0xFF00122B, 0xFF0057B8),
        525 to Comp("afc", "لیگ نخبگان آسیا", "شب آسیایی", 0xFFE3BB52, 0xFF0E0818, 0xFF7A3CFF),
        9469 to Comp("afc", "لیگ قهرمانان آسیا ۲", "شب آسیایی", 0xFFE3BB52, 0xFF0E0818, 0xFF7A3CFF),
        290 to Comp("afc", "جام ملت‌های آسیا", "جام ملت‌های آسیا", 0xFFE3BB52, 0xFF0E0818, 0xFF7A3CFF),
    )

    fun get(id: Int?): Comp? = id?.let { MAP[it] }
}
