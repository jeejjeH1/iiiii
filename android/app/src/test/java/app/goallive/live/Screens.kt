package app.goallive.live

import android.app.Application
import android.os.Looper
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
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

    private fun tap(text: String, exact: Boolean = false, first: Boolean = false) {
        val n = rule.onAllNodesWithText(text, substring = !exact)
        // call the click action directly: tabs scrolled out of view can't be touched
        // the last match is the control further down the page (team names also appear in the hero); [first] when the same
        // words also head a card further down (the stats' «شوت‌ها» group)
        val count = n.fetchSemanticsNodes().size
        if (count > 0) runCatching { n[if (first) 0 else count - 1].performSemanticsAction(SemanticsActions.OnClick) }
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
        Store.prefs().edit().putBoolean("askedNotify", true).putInt("askedBattery", 3).putInt("newsSeen", 99999).putString("rate", "never").apply()
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
            // further down: the extra team stats and the average positions
            repeat(4) { k -> runCatching { rule.onAllNodes(androidx.compose.ui.test.hasScrollAction())[0].performTouchInput { swipeUp(durationMillis = 400) } }; settle(2500); shot("03c-stats-down$k") }
            runCatching { rule.onAllNodes(androidx.compose.ui.test.hasScrollAction())[0].performTouchInput { repeat(8) { swipeDown(durationMillis = 200) } } }; settle(800)
            tap("شوت‌ها", exact = true, first = true); settle(3000); shot("03b-match-shots")
            repeat(2) { k -> runCatching { rule.onAllNodes(androidx.compose.ui.test.hasScrollAction())[0].performTouchInput { swipeUp(durationMillis = 400) } }; settle(1500); shot("03d-shots-down$k") }
            runCatching { rule.onAllNodes(androidx.compose.ui.test.hasScrollAction())[0].performTouchInput { repeat(6) { swipeDown(durationMillis = 200) } } }; settle(800)
            tap("بازیکنان", exact = true); settle(3000); shot("04b-match-players")
            // the players' stats as a table, one FotMob group at a time
            tap("حمله", exact = true); settle(2000); shot("04f-players-attack")
            tap("دفاع", exact = true); settle(2000); shot("04g-players-defence")
            tap("خلاصه", exact = true); settle(1000)
            // the analyst: the fair result, the findings and the turning points
            tap("🧠 تحلیل", exact = true); settle(4000); shot("05a-analysis")
            repeat(5) { k -> runCatching { rule.onAllNodes(androidx.compose.ui.test.hasScrollAction())[0].performTouchInput { swipeUp(startY = bottom * 0.75f, endY = bottom * 0.35f, durationMillis = 500) } }; settle(2500); shot("05b-analysis-down$k") }
            runCatching { rule.onAllNodes(androidx.compose.ui.test.hasScrollAction())[0].performTouchInput { repeat(8) { swipeDown(durationMillis = 200) } } }; settle(800)
            tap("ترکیب", exact = true); settle(3000); shot("04-match-lineup")
            // the team switch inside the tab (the team's name elsewhere on the page opens the team)
            runCatching { rule.onAllNodesWithText(teamName(match.home.id, match.home.name), substring = false).let { n -> if (n.fetchSemanticsNodes().size >= 3) n[1].performSemanticsAction(SemanticsActions.OnClick) } }
            settle(2000); shot("04c-lineup-one-team")
            tap("ترکیب تو"); settle(3000); shot("04e-coach-guide")
            tap("فهمیدم"); settle(4000); shot("04e-coach")
            tap("رودررو", exact = true); settle(3000); shot("05-match-h2h")
            tap("جدول", exact = true); settle(6000); shot("06-match-table")
        }
        // a big finished match with every part of the match page: summary, momentum, attack map, stats and both line-ups
        NavHook.nav?.match(5795484); settle(12000); shot("40-big-summary")
        repeat(4) { k -> runCatching { rule.onAllNodes(androidx.compose.ui.test.hasScrollAction())[0].performTouchInput { swipeUp(durationMillis = 500) } }; settle(1500); shot("40-big-down$k") }
        runCatching { rule.onAllNodes(androidx.compose.ui.test.hasScrollAction())[0].performTouchInput { repeat(6) { swipeDown(durationMillis = 200) } } }; settle(800)
        // the summary again in small steps, to catch the momentum card whole
        runCatching { rule.onAllNodes(androidx.compose.ui.test.hasScrollAction())[0].performTouchInput { repeat(6) { swipeDown(durationMillis = 200) } } }; settle(800)
        repeat(6) { k -> runCatching { rule.onAllNodes(androidx.compose.ui.test.hasScrollAction())[0].performTouchInput { swipeUp(startY = bottom * 0.75f, endY = bottom * 0.4f, durationMillis = 500) } }; settle(1000); shot("40m-step$k") }
        runCatching { rule.onAllNodes(androidx.compose.ui.test.hasScrollAction())[0].performTouchInput { repeat(6) { swipeDown(durationMillis = 200) } } }; settle(800)
        tap("آمار", exact = true); settle(2500); shot("41-big-stats")
        runCatching { val n = rule.onAllNodesWithText("شوت‌ها"); val c = n.fetchSemanticsNodes().size; System.err.println("SHOT shots nodes: $c"); repeat(c) { k -> runCatching { n[k].performClick() } } }
        settle(2500); shot("41b-big-shots")
        repeat(3) { k -> runCatching { rule.onAllNodes(androidx.compose.ui.test.hasScrollAction())[0].performTouchInput { swipeUp(startY = bottom * 0.75f, endY = bottom * 0.45f, durationMillis = 500) } }; settle(1200); shot("41c-big-shots-$k") }
        runCatching { rule.onAllNodes(androidx.compose.ui.test.hasScrollAction())[0].performTouchInput { repeat(4) { swipeDown(durationMillis = 200) } } }; settle(600)
        tap("ترکیب", exact = true); settle(3000); shot("42-big-lineup")
        runCatching { rule.onAllNodes(androidx.compose.ui.test.hasScrollAction())[0].performTouchInput { swipeUp(durationMillis = 500) } }; settle(1500); shot("42-big-lineup-down")
        tap("ناتینگهام فارست", exact = true); settle(2000); shot("43-big-lineup-away")
        // a player's heatmap, then next to a team-mate
        tap("کاونتری", exact = true); settle(800); tap("تاتنهام", exact = true); settle(800)
        tap("مادیسون", exact = true); settle(6000); shot("44-heatmap")
        // the sheet's own scroll is the last scrollable node on screen
        fun sheetUp(n: Int) = repeat(n) { runCatching { rule.onNodeWithTag("player-sheet").performTouchInput { swipeUp(startY = bottom * 0.8f, endY = bottom * 0.3f, durationMillis = 500) } }; settle(900) }
        tap("نمره گل‌لایو: چرا"); settle(1200); shot("48-rating-why")
        tap("حمله و گلزنی"); settle(800); sheetUp(1); shot("48b-rating-why-open")
        tap("حمله و گلزنی"); tap("نمره گل‌لایو: چرا"); settle(800)
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
        NavHook.nav?.rankings(); settle(9000); shot("50-rank-best")
        // the team of the week: one league, further down, its story
        tap("لالیگا", exact = true); settle(4000); shot("50c-week-laliga")
        runCatching { rule.onAllNodes(androidx.compose.ui.test.hasScrollAction())[0].performTouchInput { swipeUp(durationMillis = 500) } }; settle(1500); shot("50d-week-list")
        runCatching { rule.onAllNodes(androidx.compose.ui.test.hasScrollAction())[0].performTouchInput { repeat(4) { swipeDown(durationMillis = 200) } } }; settle(800)
        tap("۵ لیگ معتبر", exact = true); settle(3000)
        runCatching { rule.onAllNodesWithContentDescription("اشتراک‌گذاری").let { n -> n[n.fetchSemanticsNodes().size - 1].performSemanticsAction(SemanticsActions.OnClick) } }
        settle(12000); shot("50e-week-studio")
        app.goallive.live.ui.StoryStudio.story = null; settle(800)
        tap("برترین‌ها", exact = true); settle(1500)
        tap("توپ طلا", exact = true); settle(1500); shot("51-rank-ballon")
        runCatching { rule.onAllNodes(androidx.compose.ui.test.hasScrollAction())[0].performTouchInput { swipeUp(durationMillis = 500) } }; settle(1200); shot("51b-rank-ballon-down")
        runCatching { rule.onAllNodes(androidx.compose.ui.test.hasScrollAction())[0].performTouchInput { swipeUp(durationMillis = 500); swipeUp(durationMillis = 500) } }; settle(1200); shot("51c-rank-ballon-history")
        runCatching { rule.onAllNodes(androidx.compose.ui.test.hasScrollAction())[0].performTouchInput { repeat(5) { swipeDown(durationMillis = 200) } } }; settle(800)
        tap("۲۰۲۳", exact = true); settle(1200); shot("51d-rank-ballon-2023")
        tap("کفش طلا", exact = true); settle(1500); shot("52-rank-shoe")
        if (match != null) { NavHook.nav?.board(match.id, "h"); settle(6000); shot("29-board") }
        if (upcoming != null) {
            NavHook.nav?.match(upcoming.id); settle(4000)
            runCatching { rule.onAllNodes(androidx.compose.ui.test.hasScrollAction())[0].performTouchInput { swipeUp(durationMillis = 400) } }; settle(2000); shot("30-preview")
        }
        // the share picture of the finished match
        if (match != null) runCatching {
            val md = runBlocking { app.goallive.live.ui.MatchData(Api.fm("matchDetails", mapOf("matchId" to 5795484))) }
            // drawn off the main thread: the crests are loaded through the main looper, which settle() keeps turning
            var drawn: List<Pair<String, android.graphics.Bitmap>>? = null
            val t = Thread {
                drawn = runBlocking {
                    val best = app.goallive.live.ui.bestOf(md)
                    val art = app.goallive.live.ui.cardAssets(ctx, md, best)
                    val ui = app.goallive.live.ui.CardOptions()
                    app.goallive.live.ui.CARD_THEMES.mapIndexed { k, th ->
                        val f = app.goallive.live.ui.CardFormat.values()[k % 3]
                        val o = ui.copy(theme = th, format = f, caption = if (k == 1) "بهترین بازی فصل!" else "", sticker = if (k == 2) "🔥" else "")
                        "28${'a' + k}-share-$th-${f.name.lowercase()}" to app.goallive.live.ui.drawCard(ctx, md, o, art, best, 0.6f)
                    }
                }
            }.apply { start() }
            var waited = 0L
            while (t.isAlive && waited < 40_000) { settle(500); waited += 500 }
            java.io.File("build/screens").mkdirs()
            for ((name, bmp) in drawn ?: error("not drawn in 40 s"))
                java.io.FileOutputStream("build/screens/$name.png").use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            // a line-up as a story picture (numbers instead of photos here)
            runCatching {
                val team = md.content!!.optJSONObject("lineup")!!.optJSONObject("homeTeam")!!
                val st = team.optJSONArray("starters")!!
                val men = (0 until st.length()).map { st.getJSONObject(it) }.mapNotNull { p ->
                    val v = p.optJSONObject("verticalLayout") ?: return@mapNotNull null
                    app.goallive.live.ui.PitchMan(p.optInt("id"), p.optString("lastName"), 0.89f - v.optDouble("x").toFloat() * 0.78f,
                        0.86f - (v.optDouble("y").toFloat() - 0.1f) / 0.77f * 0.74f, p.optString("shirtNumber"), 7.2, goals = if (p.optString("shirtNumber") == "10") 1 else 0,
                        captain = p.optBoolean("isCaptain"), star = p.optString("shirtNumber") == "10")
                }
                val bmp = app.goallive.live.ui.drawFormation("ترکیب تاتنهام", "تاتنهام ۳ - ۱ کاونتری · لیگ برتر", 0xFF132257.toInt(), men, emptyMap(), emptyMap(), null,
                    null, android.graphics.Typeface.DEFAULT_BOLD, android.graphics.Typeface.DEFAULT_BOLD, "۴-۲-۳-۱", "بهترین بازیکن گل‌لایو: مدیسون · ۸٫۰")
                java.io.FileOutputStream("build/screens/28y-share-lineup.png").use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            }.onFailure { System.err.println("SHOT share lineup failed: $it") }
            // the studio itself
            app.goallive.live.ui.StoryStudio.story = app.goallive.live.ui.Story.Match(md); settle(8000); shot("28z-share-studio")
            app.goallive.live.ui.StoryStudio.story = null; settle(800)
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
