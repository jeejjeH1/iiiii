package app.goallive.live

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import app.goallive.live.data.Api
import app.goallive.live.data.Store
import app.goallive.live.data.parseMatchList
import app.goallive.live.ui.NavHook
import app.goallive.live.ui.Root
import app.goallive.live.ui.dayKey
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The same app in English. Another Android version gives it a fresh sandbox (new statics), so the language is read from scratch
 * (the app reads it once at start-up).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = TestApp::class, qualifiers = "en-w400dp-h860dp-xhdpi")
class ScreensEn {
    @get:Rule val rule = createComposeRule()

    private fun settle(ms: Long) {
        var t = 0L
        while (t < ms) {
            Thread.sleep(100); shadowOf(Looper.getMainLooper()).idle(); rule.mainClock.advanceTimeBy(100); t += 100
        }
    }

    @OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
    private fun shot(name: String) {
        runCatching { com.github.takahirom.roborazzi.captureScreenRoboImage("build/screens/$name.png") }
            .onFailure { System.err.println("SHOT $name capture failed: $it") }
    }

    private fun ask(q: String) {
        runCatching {
            rule.onAllNodes(hasSetTextAction())[0].performTextInput(q)
            settle(600)
            rule.onAllNodes(hasContentDescription("Send"))[0].performSemanticsAction(SemanticsActions.OnClick)
        }.onFailure { System.err.println("SHOT ask failed: $it") }
    }

    private fun tap(text: String) {
        val n = rule.onAllNodesWithText(text, substring = false)
        val count = n.fetchSemanticsNodes().size
        if (count > 0) runCatching { n[count - 1].performSemanticsAction(SemanticsActions.OnClick) }
    }

    @Test
    fun screensEnglish() {
        val ctx = ApplicationProvider.getApplicationContext<Application>()
        ctx.getSharedPreferences("goallive", Context.MODE_PRIVATE).edit().putString("lang", "en").commit()
        Lang.init(ctx)
        Api.init(ctx)
        Store.init(ctx); app.goallive.live.data.Predictions.init(ctx)
        Store.onboarded = true
        Store.prefs().edit().putBoolean("askedNotify", true).apply()
        Store.pickMyTeam(app.goallive.live.data.MyTeam(8634, "Barcelona", null))
        rule.mainClock.autoAdvance = false
        app.goallive.live.ui.sharedCrests = false
        System.err.println("SHOT en strings: ${L("خلاصه")} / ${L("بازی‌های بزرگ")}")

        val list = runBlocking { runCatching { parseMatchList(Api.fm("matches", Api.matchesParams(dayKey(System.currentTimeMillis())))) }.getOrDefault(emptyList()) }
        val big = setOf(42, 47, 87, 55, 54, 53, 73)
        val match = list.filter { it.primaryId in big }.flatMap { it.matches }.firstOrNull { it.finished }
            ?: list.flatMap { it.matches }.firstOrNull { it.finished }
        val upcoming = list.filter { it.primaryId in big }.flatMap { it.matches }.firstOrNull { !it.started && !it.cancelled }
        // a match from Iran's league, when there is one today
        val iran = list.firstOrNull { it.primaryId == 523 }?.matches?.firstOrNull()

        rule.setContent { Root(mutableIntStateOf(0)) }
        settle(11000); shot("en-01-home")
        if (match != null) { NavHook.nav?.match(match.id); settle(9000); shot("en-02-match") ; tap("Line-up"); settle(3000); shot("en-03-lineup") }
        if (upcoming != null) { NavHook.nav?.match(upcoming.id); settle(20000); shot("en-04-radar") }
        if (iran != null) { NavHook.nav?.match(iran.id); settle(20000); shot("en-04b-iran-match") }
        NavHook.nav?.team(8634); settle(9000); shot("en-05-team")
        NavHook.nav?.league(523); settle(9000); shot("en-06-league-iran")
        NavHook.nav?.tab("leagues"); settle(7000); shot("en-07-leagues")
        NavHook.nav?.tab("settings"); settle(1500); shot("en-08-settings")
        NavHook.nav?.tab("more"); settle(2000); shot("en-09-more")
        NavHook.nav?.quiz(); settle(9000); shot("en-10-quiz")
        NavHook.nav?.board(); settle(2000); shot("en-13-board")
        NavHook.nav?.predictions(); settle(2000); shot("en-14-predictions")
        NavHook.nav?.assistant(); settle(2000)
        ask("Where is Esteghlal in the table?"); settle(12000); shot("en-11-assistant")
        ask("Barcelona next match"); settle(12000); shot("en-12-assistant")
    }
}
