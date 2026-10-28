package app.goallive.live

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.goallive.live.data.Api
import app.goallive.live.data.Store
import androidx.compose.ui.graphics.toArgb
import app.goallive.live.ui.BenchMan
import app.goallive.live.ui.CardOptions
import app.goallive.live.ui.MatchData
import app.goallive.live.ui.PitchMan
import app.goallive.live.ui.PosterScore
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
        // El Clásico's home line-up as cards and in the stadium, with the epic track
        runBlocking { runCatching { MatchData(Api.fm("matchDetails", mapOf("matchId" to 5868105))) }.getOrNull() }?.let { md -> lineup(md) }?.let { l ->
            val a = runBlocking { l.load(ctx) }
            for (t in listOf("cards", "stadium")) {
                val sc = l.scene(CardOptions(theme = t), a)
                val f = File(out, "story-lineup-$t.mp4")
                val t0 = System.currentTimeMillis()
                val ok = StoryVideo.make(ctx, f, 720, 1280, STORY_SECONDS, R.raw.story_epic, {}, { true }) { c, tt -> sc.frame(c, tt) }
                println("VIDEO lineup-$t ok=$ok bytes=${f.length()} ms=${System.currentTimeMillis() - t0}")
                assertTrue("video lineup-$t not made", ok && f.length() > 50_000)
            }
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

    /** A team's line-up from the match's data, as the studio gets it: positions, numbers, ratings, what each did, the best player. */
    private fun lineup(md: MatchData): Story.Lineup? {
        val lu = md.content?.optJSONObject("lineup") ?: return null
        fun objs(a: org.json.JSONArray?) = a?.let { x -> (0 until x.length()).map { x.getJSONObject(it) } }.orEmpty()
        val t = lu.optJSONObject("homeTeam") ?: return null
        val ev = { p: JSONObject -> objs(p.optJSONObject("performance")?.optJSONArray("events")).map { it.optString("type") } }
        val subEv = { p: JSONObject, k: String -> objs(p.optJSONObject("performance")?.optJSONArray("substitutionEvents")).firstOrNull { it.optString("type") == k }?.optString("time") }
        val xi = objs(t.optJSONArray("starters")).mapNotNull { p ->
            val v = p.optJSONObject("verticalLayout") ?: return@mapNotNull null
            val e = ev(p)
            PitchMan(p.optInt("id"), p.optString("lastName"), 0.89f - v.optDouble("x").toFloat() * 0.78f, 0.86f - (v.optDouble("y").toFloat() - 0.1f) / 0.77f * 0.74f,
                p.optString("shirtNumber"), p.optJSONObject("performance")?.optDouble("rating")?.takeIf { !it.isNaN() },
                e.count { it == "goal" }, 0, e.count { it == "assist" }, "yellowCard" in e, "redCard" in e, subEv(p, "subOut"), p.optBoolean("isCaptain"))
        }.let { men -> val b = men.maxByOrNull { it.rating ?: 0.0 }; men.map { m -> if (m !== b) m else PitchMan(m.id, m.name, m.x, m.y, m.number, m.rating, m.goals, m.own, m.assists, m.yellow, m.red, m.outAt, m.captain, star = true) } }
        if (xi.isEmpty()) return null
        val bench = objs(t.optJSONArray("subs")).filter { subEv(it, "subIn") != null }.take(5).map { p ->
            BenchMan(p.optInt("id"), p.optString("lastName"), subEv(p, "subIn"), p.optJSONObject("performance")?.optDouble("rating")?.takeIf { !it.isNaN() },
                ev(p).count { it == "goal" }, ev(p).count { it == "assist" }, "yellowCard" in ev(p))
        }
        return Story.Lineup(t.optString("name"), "لالیگا", md.homeColor.toArgb(), xi, t.optInt("id"), null, t.optString("formation"), null, "x",
            kicker = "ترکیب اصلی", teamName = t.optString("name"), bench = bench,
            score = PosterScore(md.homeId, md.homeName, md.home?.optInt("score") ?: 0, md.awayId, md.awayName, md.away?.optInt("score") ?: 0), home = true)
    }
}
