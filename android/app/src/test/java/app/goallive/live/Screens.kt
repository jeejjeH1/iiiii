package app.goallive.live

import android.app.Application
import android.os.Looper
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.core.app.ApplicationProvider
import app.goallive.live.data.Api
import app.goallive.live.data.Store
import app.goallive.live.data.parseMatchList
import app.goallive.live.ui.Look
import app.goallive.live.ui.NavHook
import app.goallive.live.ui.teamName
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

    // the whole screen, so bottom sheets and dialogs (their own windows) are included
    @OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
    private fun shot(name: String) {
        Watch.mark(name)
        runCatching { com.github.takahirom.roborazzi.captureScreenRoboImage("build/screens/$name.png") }
            .onFailure { e ->
                System.err.println("SHOT $name screen capture failed: $e")
                runCatching { rule.onAllNodes(androidx.compose.ui.test.isRoot())[0].captureRoboImage("build/screens/$name.png") }
                    .onFailure { e2 -> System.err.println("SHOT $name root capture failed: $e2"); e2.stackTrace.take(12).forEach { System.err.println("SHOT    at $it") } }
            }
    }

    private fun tap(text: String, exact: Boolean = false) {
        val n = rule.onAllNodesWithText(text, substring = !exact)
        // call the click action directly: tabs scrolled out of view can't be touched
        // the last match is the control further down the page (team names also appear in the hero)
        val count = n.fetchSemanticsNodes().size
        if (count > 0) runCatching { n[count - 1].performSemanticsAction(SemanticsActions.OnClick) }
    }

    private fun askAssistant(q: String) {
        runCatching {
            rule.onAllNodes(androidx.compose.ui.test.hasSetTextAction())[0].performTextInput(q)
            settle(600) // the mic button turns into "send" once there is text
            rule.onAllNodes(androidx.compose.ui.test.hasContentDescription("فرستادن"))[0].performSemanticsAction(SemanticsActions.OnClick)
        }.onFailure { System.err.println("SHOT ask failed: $it") }
    }

    @Test
    fun screens() {
        val ctx = ApplicationProvider.getApplicationContext<Application>()
        Api.init(ctx)
        Store.init(ctx); app.goallive.live.data.Predictions.init(ctx)
        Store.onboarded = true
        Store.prefs().edit().putBoolean("askedNotify", true).apply()
        Store.pickMyTeam(app.goallive.live.data.MyTeam(8634, "Barcelona", null))
        rule.mainClock.autoAdvance = false
        // the online commentator voice, straight from the voice service
        runBlocking {
            for (v in listOf("fa-IR-FaridNeural", "fa-IR-DilaraNeural")) {
                val bytes = runCatching { app.goallive.live.notify.EdgeVoice.speak("گل! چه گلی! این صدای گزارشگر گل‌لایو است.", v) }
                System.err.println("SHOT tts $v -> ${bytes.getOrNull()?.size ?: bytes.exceptionOrNull()}")
            }
        }
        app.goallive.live.ui.sharedCrests = false

        // a finished match from a big league today (fallback: any match) for the match screens
        val list = runBlocking { runCatching { parseMatchList(Api.fm("matches", Api.matchesParams(dayKey(System.currentTimeMillis())))) }.getOrDefault(emptyList()) }
        val big = setOf(42, 47, 87, 55, 54, 53, 73)
        val match = list.filter { it.primaryId in big }.flatMap { it.matches }.firstOrNull { it.finished }
            ?: list.flatMap { it.matches }.firstOrNull { it.finished } ?: list.flatMap { it.matches }.firstOrNull()

        // an upcoming big match for the opponent radar
        val upcoming = list.filter { it.primaryId in big }.flatMap { it.matches }.firstOrNull { !it.started && !it.cancelled }
            ?: list.flatMap { it.matches }.firstOrNull { !it.started && !it.cancelled && it.home.id > 0 }

        rule.setContent { Root(mutableIntStateOf(0)) }
        settle(2500); shot("00-intro")
        settle(9000); shot("01-home")
        if (match != null) {
            NavHook.nav?.match(match.id); settle(9000); shot("02-match-summary")
            // further down the summary: the match story and the attack map
            repeat(2) { k -> runCatching { rule.onAllNodes(androidx.compose.ui.test.hasScrollAction())[0].performTouchInput { swipeUp(durationMillis = 400) } }; settle(1500); shot("02d-summary-down$k") }
            runCatching { rule.onAllNodes(androidx.compose.ui.test.hasScrollAction())[0].performTouchInput { swipeDown(durationMillis = 300); swipeDown(durationMillis = 300) } }; settle(800)
            tap("زنده", exact = true); settle(3000); shot("02b-match-pitch")
            tap("گزارش متنی", exact = true); settle(4000); shot("02c-match-commentary")
            tap("آمار", exact = true); settle(3000); shot("03-match-stats")
            tap("شوت‌ها", exact = true); settle(3000); shot("03b-match-shots")
            tap("بازیکنان", exact = true); settle(3000); shot("04b-match-players")
            tap("ترکیب", exact = true); settle(3000); shot("04-match-lineup")
            tap(teamName(match.home.id, match.home.name), exact = true); settle(2000); shot("04c-lineup-one-team")
            tap("فهرست"); settle(1500); shot("04d-lineup-list")
            tap("مربی تو"); settle(3000); shot("04e-coach-guide")
            tap("فهمیدم"); settle(4000); shot("04e-coach")
            tap("رودررو", exact = true); settle(3000); shot("05-match-h2h")
            tap("جدول", exact = true); settle(6000); shot("06-match-table")
        }
        // a big finished match with every part of the match page: summary, momentum, attack map, stats and both line-ups
        NavHook.nav?.match(5795484); settle(12000); shot("40-big-summary")
        repeat(4) { k -> runCatching { rule.onAllNodes(androidx.compose.ui.test.hasScrollAction())[0].performTouchInput { swipeUp(durationMillis = 500) } }; settle(1500); shot("40-big-down$k") }
        runCatching { rule.onAllNodes(androidx.compose.ui.test.hasScrollAction())[0].performTouchInput { repeat(6) { swipeDown(durationMillis = 200) } } }; settle(800)
        tap("آمار", exact = true); settle(2500); shot("41-big-stats")
        tap("ترکیب", exact = true); settle(3000); shot("42-big-lineup")
        runCatching { rule.onAllNodes(androidx.compose.ui.test.hasScrollAction())[0].performTouchInput { swipeUp(durationMillis = 500) } }; settle(1500); shot("42-big-lineup-down")
        tap("ناتینگهام فارست", exact = true); settle(2000); shot("43-big-lineup-away")
        // a player's heatmap, then next to a team-mate
        tap("کاونتری", exact = true); settle(800); tap("تاتنهام", exact = true); settle(800)
        tap("مادیسون", exact = true); settle(6000); shot("44-heatmap")
        // the sheet's own scroll is the last scrollable node on screen
        fun sheetUp(n: Int) = repeat(n) { runCatching { rule.onAllNodes(androidx.compose.ui.test.hasScrollAction()).let { it[it.fetchSemanticsNodes().size - 1] }.performTouchInput { swipeUp(durationMillis = 400) } }; settle(900) }
        sheetUp(1); shot("44b-sheet-heat")
        tap("بزرگ", exact = true); settle(1500); shot("46-heatmap-full")
        tap("افقی", exact = true); settle(800)
        tap("سولانکه"); settle(1500); sheetUp(2); shot("45-heatmap-compare")
        sheetUp(3); shot("47-sheet-stats")
        NavHook.nav?.team(8634); settle(9000); shot("07-team")
        tap("تاریخچه"); settle(2000); shot("08-team-story")
        NavHook.nav?.league(47); settle(9000); shot("09-league")
        NavHook.nav?.tab("leagues"); settle(7000); shot("10-leagues")
        NavHook.nav?.tab("settings"); settle(1500); shot("11-settings")
        NavHook.nav?.tab("following"); settle(4000); shot("12-following")
        NavHook.nav?.tab("more"); settle(2000); shot("12b-more")
        NavHook.nav?.tab("search"); settle(2000); shot("12c-search-empty")
        NavHook.nav?.quiz(); settle(9000); shot("13-quiz")
        tap("۱"); tap("۲"); settle(1500); shot("13b-quiz-answered")
        NavHook.nav?.calendar(); settle(8000); shot("14-calendar")
        NavHook.nav?.compare(30981, 737066); settle(12000); shot("15-compare")
        // fantasy: pick the Premier League and buy a few players through the picker
        NavHook.nav?.fantasy(); settle(2000); shot("16-fantasy-guide")
        tap("فهمیدم"); settle(1500); shot("16-fantasy-start")
        tap("لیگ برتر انگلیس"); settle(25000); shot("17-fantasy")
        tap("+ افزودن"); settle(2500); shot("18-fantasy-picker")
        // the assistant: one typed question through an example chip
        NavHook.nav?.assistant(); settle(2000); shot("22-assistant")
        tap("استقلال چندم است؟", exact = true); settle(9000); shot("22b-assistant-answer")
        // typed questions, one screenshot per answer (the chat scrolls to the newest)
        listOf(
            "پرسپولیس و استقلال را مقایسه کن", "بازی بعدیش کی است؟", "گلزن برتر سپاهان کیست؟", "مصدوم های رئال مادرید",
            "تیم چهارم لالیگا", "طارمی امسال چند گل زده؟", "مسی و رونالدو را مقایسه کن", "بازی های فردا", "افتخارات بارسا",
        ).forEachIndexed { k, q -> askAssistant(q); settle(12000); shot("22q$k-assistant") }
        if (upcoming != null) {
            NavHook.nav?.match(upcoming.id); settle(20000); shot("23-match-radar")
            // a prediction: two goals for the home side, one for the away side
            tap("+", exact = true); settle(300); tap("+", exact = true); settle(300)
            runCatching { rule.onAllNodesWithText("+", substring = false)[0].performSemanticsAction(SemanticsActions.OnClick) }
            settle(1500); shot("26-predict")
        }
        NavHook.nav?.predictions(); settle(3000); shot("27-predictions")
        if (match != null) { NavHook.nav?.board(match.id, "h"); settle(6000); shot("29-board") }
        if (upcoming != null) {
            NavHook.nav?.match(upcoming.id); settle(4000)
            runCatching { rule.onAllNodes(androidx.compose.ui.test.hasScrollAction())[0].performTouchInput { swipeUp(durationMillis = 400) } }; settle(2000); shot("30-preview")
        }
        // the share picture of the finished match
        if (match != null) runCatching {
            val md = runBlocking { app.goallive.live.ui.MatchData(Api.fm("matchDetails", mapOf("matchId" to match.id))) }
            // drawn off the main thread: the crests are loaded through the main looper, which settle() keeps turning
            var drawn: android.graphics.Bitmap? = null
            val t = Thread { drawn = runBlocking { app.goallive.live.ui.drawCard(ctx, md) } }.apply { start() }
            var waited = 0L
            while (t.isAlive && waited < 30_000) { settle(500); waited += 500 }
            val bmp = drawn ?: error("not drawn in 30 s")
            java.io.File("build/screens").mkdirs()
            java.io.FileOutputStream("build/screens/28-share-card.png").use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }.onFailure { System.err.println("SHOT share card failed: $it") }
        // Iran's league: a match of the day and the league page
        list.firstOrNull { it.primaryId == 523 }?.matches?.firstOrNull()?.let { NavHook.nav?.match(it.id); settle(20000); shot("24-iran-match") }
        NavHook.nav?.league(523); settle(9000); shot("25-iran-league")
        // the day look ("بازی روز")
        Store.pickTheme("light"); Look.mode = "light"
        NavHook.nav?.tab("home"); settle(500); NavHook.nav?.root("home"); settle(6000); shot("19-home-light")
        if (match != null) { NavHook.nav?.match(match.id); settle(6000); shot("20-match-light") }
        NavHook.nav?.tab("settings"); settle(1500); shot("21-settings-light")
    }
}
