package app.goallive.live

import android.app.Application
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.goallive.live.data.Api
import app.goallive.live.data.Store
import app.goallive.live.data.parseMatchList
import app.goallive.live.ui.C
import app.goallive.live.ui.GoalLiveTheme
import app.goallive.live.ui.LivePitch
import app.goallive.live.ui.Lime
import app.goallive.live.ui.MatchData
import app.goallive.live.ui.dayKey
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The live pitch on its own: a match being played now (when there is one) and the big finished match. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestApp::class, qualifiers = "w400dp-h860dp-xxhdpi")
class LiveShot {
    @get:Rule val rule = createComposeRule()

    private fun settle(ms: Long) {
        var t = 0L
        while (t < ms) { Thread.sleep(50); shadowOf(Looper.getMainLooper()).idle(); rule.mainClock.advanceTimeBy(100); t += 100 }
    }

    @OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
    private fun shot(name: String) {
        Watch.mark(name)
        runCatching { com.github.takahirom.roborazzi.captureScreenRoboImage("build/screens/$name.png") }
            .onFailure { System.err.println("SHOT $name capture failed: $it") }
    }

    @Test
    fun livePitch() {
        val ctx = ApplicationProvider.getApplicationContext<Application>()
        Api.init(ctx); Store.init(ctx)
        rule.mainClock.autoAdvance = false
        val list = runBlocking { runCatching { parseMatchList(Api.fm("matches", Api.matchesParams(dayKey(System.currentTimeMillis())))) }.getOrDefault(emptyList()) }
        val live = list.flatMap { it.matches }.filter { it.live }.sortedByDescending { it.home.score ?: 0 }.take(2)
        val ids = live.map { it.id } + 5795484
        var md by mutableStateOf<MatchData?>(null)
        rule.setContent {
            GoalLiveTheme(Lime) {
                Column(Modifier.fillMaxSize().background(C.bg).verticalScroll(rememberScrollState()).padding(12.dp)) { md?.let { LivePitch(it) } }
            }
        }
        ids.forEachIndexed { k, id ->
            md = runBlocking { runCatching { MatchData(Api.fm("matchDetails", mapOf("matchId" to id))) }.getOrNull() } ?: return@forEachIndexed
            settle(4000); shot("60-live-pitch-$k")
            settle(1300); shot("60-live-pitch-$k-b")
        }
    }
}
