package app.goallive.live

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.goallive.live.data.Api
import app.goallive.live.data.Store
import app.goallive.live.ui.CardOptions
import app.goallive.live.ui.MatchData
import app.goallive.live.ui.STORY_SECONDS
import app.goallive.live.ui.Story
import app.goallive.live.ui.StoryVideo
import app.goallive.live.ui.load
import app.goallive.live.ui.scene
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Makes real story videos with the phone's encoder; the workflow pulls them off the emulator to be looked at. */
@RunWith(AndroidJUnit4::class)
class StoryVideoTest {
    @Test
    fun videos() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        Api.init(ctx); Store.init(ctx)
        val out = File(ctx.getExternalFilesDir(null), "videos").apply { mkdirs() }
        val stories = mutableListOf<Pair<String, Story>>()
        runBlocking { runCatching { MatchData(Api.fm("matchDetails", mapOf("matchId" to 5795484))) }.getOrNull() }?.let { stories += "match" to Story.Match(it) }
        val rankings = runBlocking { runCatching { Api.get("https://github.com/jeejjeH1/iiiii/releases/download/rankings/rankings.json", 0L) as JSONObject }.getOrNull() }
        val season = rankings?.optJSONObject("seasons")?.optJSONObject(rankings.optString("current"))
        val pick = season?.optJSONArray("ballonDor")?.let { a -> (0 until a.length()).map { a.getJSONObject(it) } }.orEmpty()
        if (pick.isNotEmpty()) stories += "ballon" to Story.Ballon(season?.optInt("ballonYear") ?: 2027, pick, false)
        pick.firstOrNull()?.optInt("id")?.let { id -> runBlocking { runCatching { Api.fm("playerData", mapOf("id" to id)) }.getOrNull() } }
            ?.let { stories += "player" to Story.Player(it) }
        assertTrue("no data", stories.isNotEmpty())
        // the match reel, with its own sound
        (stories.firstOrNull { it.first == "match" }?.second as? Story.Match)?.let { st ->
            val r = Story.Reel(st.m)
            val sc = r.scene(CardOptions(), runBlocking { r.load(ctx) }) as app.goallive.live.ui.ReelScene
            val t0 = System.currentTimeMillis()
            val audio = app.goallive.live.ui.StoryAudio.make(ctx, sc.seconds, R.raw.reel_music, 0.5f, true, sc.fx)
            val f = File(out, "story-reel.mp4")
            val ok = StoryVideo.make(ctx, f, 720, 1280, sc.seconds, null, {}, { true }, bitrate = 4_000_000, audio = audio) { c, t -> sc.frame(c, t) }
            println("VIDEO reel ok=$ok audio=${audio != null} seconds=${sc.seconds} bytes=${f.length()} ms=${System.currentTimeMillis() - t0}")
            assertTrue("reel not made", ok && f.length() > 200_000 && audio != null)
        }
        val music = listOf(R.raw.story_energy, R.raw.story_epic, R.raw.story_chill)
        stories.forEachIndexed { k, (name, s) ->
            val a = runBlocking { s.load(ctx) }
            val sc = s.scene(CardOptions(theme = s.theme), a)
            val f = File(out, "story-$name.mp4")
            val t0 = System.currentTimeMillis()
            val ok = StoryVideo.make(ctx, f, 720, 1280, STORY_SECONDS, music[k % 3], {}, { true }) { c, t -> sc.frame(c, t) }
            println("VIDEO $name ok=$ok bytes=${f.length()} ms=${System.currentTimeMillis() - t0}")
            assertTrue("video $name not made", ok && f.length() > 50_000)
        }
    }
}
