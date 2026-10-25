package app.goallive.live

import android.app.Application
import android.os.Looper
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import app.goallive.live.data.Api
import app.goallive.live.data.Store
import app.goallive.live.ui.GoalLiveTheme
import app.goallive.live.ui.Lime
import app.goallive.live.ui.Welcome
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The first-launch welcome, step by step. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestApp::class, qualifiers = "w400dp-h860dp-xxhdpi")
class WelcomeShots {
    @get:Rule val rule = createComposeRule()

    private fun settle(ms: Long) {
        var t = 0L
        while (t < ms) { Thread.sleep(50); shadowOf(Looper.getMainLooper()).idle(); rule.mainClock.advanceTimeBy(100); t += 100 }
    }

    private fun tap(text: String) {
        val n = rule.onAllNodesWithText(text, substring = true)
        if (n.fetchSemanticsNodes().isNotEmpty()) runCatching { n[0].performSemanticsAction(SemanticsActions.OnClick) }
    }

    @OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
    private fun shot(name: String) {
        runCatching { com.github.takahirom.roborazzi.captureScreenRoboImage("build/screens/$name.png") }.onFailure { System.err.println("SHOT $name failed: $it") }
    }

    @Test
    fun welcome() {
        val ctx = ApplicationProvider.getApplicationContext<Application>()
        Api.init(ctx); Store.init(ctx)
        rule.mainClock.autoAdvance = false
        rule.setContent { GoalLiveTheme(Lime) { Welcome { } } }
        settle(2500); shot("80-welcome-0")
        tap("شروع"); settle(2500)
        tap("پرسپولیس"); tap("رئال مادرید"); settle(3000); shot("80-welcome-1-teams")
        tap("ادامه"); settle(1500)
        tap("لیگ برتر خلیج"); tap("لیگ قهرمانان"); settle(1500); shot("80-welcome-2-leagues")
        tap("ادامه"); settle(1500); shot("80-welcome-3-alerts")
    }
}
