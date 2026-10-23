package app.goallive.live.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/** Official live-streaming services. Each one opens in its own app (or the browser); the user watches with their own account. */
class Station(val key: String, val name: String, val sub: String, val url: String)

val STATIONS = listOf(
    Station("namava", L("نماوا"), L("پخش زنده با اشتراک نماوا"), "https://www.namava.ir/"),
    Station("aparat", L("آپارات اسپورت"), L("پخش زنده لیگ برتر ایران و رقابت‌های اروپایی"), "https://sport.aparat.com/"),
    Station("varzesh", L("تلوبیون · شبکه ورزش"), L("پخش زنده شبکه ورزش صداوسیما"), "https://telewebion.com/live/varzesh"),
    Station("tv3", L("تلوبیون · شبکه ۳"), L("پخش زنده شبکه سه"), "https://telewebion.com/live/tv3"),
)

/** Hands the service's link to the phone: its own app takes it when installed, otherwise the browser does. */
fun openStation(ctx: Context, st: Station) {
    val ok = runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(st.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
    if (!ok) Toast.makeText(ctx, L("برنامه یا مرورگری برای باز کردن {0} پیدا نشد", st.name), Toast.LENGTH_SHORT).show()
}
