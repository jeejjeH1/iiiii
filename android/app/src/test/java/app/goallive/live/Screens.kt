package app.goallive.live

import android.app.Application
import android.os.Looper
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.core.app.ApplicationProvider
import app.goallive.live.data.Api
import app.goallive.live.data.Store
import app.goallive.live.data.parseMatchList
import app.goallive.live.ui.NavHook
import app.goallive.live.ui.Root
import app.goallive.live.ui.dayKey
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Plain application: the real one starts WorkManager and alarms, which the screenshot run doesn't need. */
class TestApp : Application()

/**
 * Not a test of behaviour: renders each screen with live data so the look can be compared with the website.
 * Run with: ./gradlew recordRoborazziDebug  (images land in app/build/screens)
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestApp::class, qualifiers = "w400dp-h860dp-xhdpi")
class Screens {
    @get:Rule val rule = createComposeRule()

    private fun settle(ms: Long) {
        var t = 0L
        while (t < ms) {
            Thread.sleep(100)
            shadowOf(Looper.getMainLooper()).idle()
            rule.mainClock.advanceTimeBy(100)
            t += 100
        }
    }

    private fun shot(name: String) = rule.onRoot().captureRoboImage("build/screens/$name.png")

    private fun tap(text: String) {
        val n = rule.onAllNodesWithText(text, substring = true)
        // call the click action directly: tabs scrolled out of view can't be touched
        // the last match is the control further down the page (team names also appear in the hero)
        val count = n.fetchSemanticsNodes().size
        if (count > 0) runCatching { n[count - 1].performSemanticsAction(SemanticsActions.OnClick) }
    }

    @Test
    fun screens() {
        val ctx = ApplicationProvider.getApplicationContext<Application>()
        Api.init(ctx)
        Store.init(ctx)
        Store.onboarded = true
        Store.prefs().edit().putBoolean("askedNotify", true).apply()
        Store.pickMyTeam(app.goallive.live.data.MyTeam(8634, "Barcelona", null))
        rule.mainClock.autoAdvance = false

        // a finished match from a big league today (fallback: any match) for the match screens
        val list = runBlocking { runCatching { parseMatchList(Api.fm("matches", Api.matchesParams(dayKey(System.currentTimeMillis())))) }.getOrDefault(emptyList()) }
        val big = setOf(42, 47, 87, 55, 54, 53, 73)
        val match = list.filter { it.primaryId in big }.flatMap { it.matches }.firstOrNull { it.finished }
            ?: list.flatMap { it.matches }.firstOrNull { it.finished } ?: list.flatMap { it.matches }.firstOrNull()

        rule.setContent { Root(mutableIntStateOf(0)) }
        settle(2500); shot("00-intro")
        settle(9000); shot("01-home")
        if (match != null) {
            NavHook.nav?.match(match.id); settle(9000); shot("02-match-summary")
            tap("زمین زنده"); settle(3000); shot("02b-match-pitch")
            tap("آمار"); settle(3000); shot("03-match-stats")
            tap("شوت‌ها"); settle(3000); shot("03b-match-shots")
            tap("ترکیب"); settle(3000); shot("04-match-lineup")
            tap(match.home.name); settle(2000); shot("04c-lineup-one-team")
            tap("فهرست"); settle(1500); shot("04d-lineup-list")
            tap("بازیکنان"); settle(3000); shot("04b-match-players")
            tap("مربی تو"); settle(6000); shot("04e-coach")
            tap("رودررو"); settle(3000); shot("05-match-h2h")
            tap("جدول"); settle(6000); shot("06-match-table")
        }
        NavHook.nav?.team(8634); settle(9000); shot("07-team")
        tap("تاریخچه"); settle(2000); shot("08-team-story")
        NavHook.nav?.league(47); settle(9000); shot("09-league")
        NavHook.nav?.tab("leagues"); settle(7000); shot("10-leagues")
        NavHook.nav?.tab("settings"); settle(1500); shot("11-settings")
        NavHook.nav?.tab("following"); settle(4000); shot("12-following")
        NavHook.nav?.quiz(); settle(9000); shot("13-quiz")
        tap("۱"); tap("۲"); settle(1500); shot("13b-quiz-answered")
        NavHook.nav?.calendar(); settle(8000); shot("14-calendar")
        NavHook.nav?.compare(30981, 737066); settle(12000); shot("15-compare")
        // fantasy: pick the Premier League and buy a few players through the picker
        NavHook.nav?.fantasy(); settle(2000); shot("16-fantasy-start")
        tap("لیگ برتر انگلیس"); settle(25000); shot("17-fantasy")
        tap("+ افزودن"); settle(2500); shot("18-fantasy-picker")
    }
}
