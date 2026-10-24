package app.goallive.live

import android.app.Application
import android.os.Looper
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performScrollToIndex
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

/** A small phone with large text (the accessibility setting many people use): the match rows and cards must still fit. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestApp::class, qualifiers = "w360dp-h740dp-xhdpi", fontScale = 1.3f)
class ScreensBig {
    @get:Rule val rule = createComposeRule()

    private fun settle(ms: Long) {
        var t = 0L
        while (t < ms) { Thread.sleep(100); shadowOf(Looper.getMainLooper()).idle(); rule.mainClock.advanceTimeBy(100); t += 100 }
    }

    @OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
    private fun shot(name: String) {
        Watch.mark(name)
        runCatching { com.github.takahirom.roborazzi.captureScreenRoboImage("build/screens/$name.png") }
            .onFailure { System.err.println("SHOT $name capture failed: $it") }
    }

    @Test
    fun bigText() {
        val ctx = ApplicationProvider.getApplicationContext<Application>()
        Api.init(ctx); Store.init(ctx); app.goallive.live.data.Predictions.init(ctx)
        Store.onboarded = true
        Store.prefs().edit().putBoolean("askedNotify", true).putInt("askedBattery", 3).apply()
        Store.pickMyTeam(app.goallive.live.data.MyTeam(8634, "Barcelona", null))
        rule.mainClock.autoAdvance = false
        app.goallive.live.ui.sharedCrests = false
        val list = runBlocking { runCatching { parseMatchList(Api.fm("matches", Api.matchesParams(dayKey(System.currentTimeMillis())))) }.getOrDefault(emptyList()) }
        rule.setContent { Root(mutableIntStateOf(0)) }
        settle(11000); shot("big-01-home")
        // further down the day: the league blocks with their match rows
        NavHook.nav?.root("home"); settle(500)
        runCatching { rule.onAllNodes(androidx.compose.ui.test.hasScrollToIndexAction())[0].performScrollToIndex(6) }; settle(2500); shot("big-02-home-rows")
        list.flatMap { it.matches }.firstOrNull { it.finished }?.let { NavHook.nav?.match(it.id); settle(9000); shot("big-03-match") }
    }
}
