package app.goallive.live

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import app.goallive.live.data.Api
import app.goallive.live.data.Store
import app.goallive.live.ui.CardOptions
import app.goallive.live.ui.GoalLiveTheme
import app.goallive.live.ui.Lime
import app.goallive.live.ui.MatchData
import app.goallive.live.ui.Story
import app.goallive.live.ui.StorySheet
import app.goallive.live.ui.load
import app.goallive.live.ui.loadGoal
import app.goallive.live.ui.scene
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/** Every kind of story as its finished picture, a few frames of the animation, and the studio itself. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestApp::class, qualifiers = "w400dp-h860dp-xxhdpi")
class StoryShots {
    @get:Rule val rule = createComposeRule()

    private fun settle(ms: Long) {
        var t = 0L
        while (t < ms) { Thread.sleep(50); shadowOf(Looper.getMainLooper()).idle(); rule.mainClock.advanceTimeBy(100); t += 100 }
    }

    private fun png(name: String, b: Bitmap) {
        File("build/screens").mkdirs()
        FileOutputStream("build/screens/$name.png").use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Runs [work] off the main thread (pictures load through the main looper, which settle() keeps turning). */
    private fun <T> off(work: () -> T): T? {
        var out: T? = null
        val t = Thread { out = runCatching(work).onFailure { System.err.println("SHOT story work failed: $it") }.getOrNull() }.apply { start() }
        var waited = 0L
        while (t.isAlive && waited < 60_000) { settle(500); waited += 500 }
        return out
    }

    @OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
    @Test
    fun stories() {
        val ctx = ApplicationProvider.getApplicationContext<Application>()
        Api.init(ctx); Store.init(ctx)
        rule.mainClock.autoAdvance = false
        val rankings = runBlocking { runCatching { Api.get("https://github.com/jeejjeH1/iiiii/releases/download/rankings/rankings.json", 0L) as JSONObject }.getOrNull() }
        val season = rankings?.optJSONObject("seasons")?.optJSONObject(rankings.optString("current"))
        val pick = season?.optJSONArray("ballonDor")?.let { a -> (0 until a.length()).map { a.getJSONObject(it) } }.orEmpty()
        fun player(id: Int) = runBlocking { runCatching { Api.fm("playerData", mapOf("id" to id)) }.getOrNull() }
        val stories = mutableListOf<Pair<String, Story>>()
        runBlocking { runCatching { MatchData(Api.fm("matchDetails", mapOf("matchId" to 5795484))) }.getOrNull() }?.let { stories += "match" to Story.Match(it); stories += "stats" to Story.Stats(it); stories += "analysis" to Story.Analysis(it) }
        val p1 = pick.getOrNull(0)?.optInt("id")?.let(::player)
        val p2 = pick.getOrNull(1)?.optInt("id")?.let(::player)
        p1?.let { stories += "player" to Story.Player(it) }
        runBlocking { runCatching { Api.fm("leagues", mapOf("id" to 47, "ccode3" to "IRN")) }.getOrNull() }?.let { d ->
            val root = d.optJSONArray("table")?.optJSONObject(0)?.let { it.optJSONObject("data") ?: it }
            val t = root?.optJSONArray("tables")?.optJSONObject(0) ?: root
            val rows = t?.optJSONObject("table")?.optJSONArray("all")?.let { a -> (0 until a.length()).map { a.getJSONObject(it) } }.orEmpty()
            val legend = t?.optJSONArray("legend")?.let { a -> (0 until a.length()).map { a.getJSONObject(it) } }.orEmpty()
            if (rows.isNotEmpty()) stories += "table" to Story.Table(47, t?.optString("leagueName") ?: "Premier League", rows, legend, rows.getOrNull(12)?.optInt("id") ?: 0)
        }
        if (pick.isNotEmpty()) stories += "ballon" to Story.Ballon(season?.optInt("ballonYear") ?: 2027, pick, false)
        if (p1 != null && p2 != null) stories += "duel" to Story.Duel(p1, p2)
        val drawn = off {
            stories.map { (name, s) ->
                val a = runBlocking { s.load(ctx) }
                val sc = s.scene(CardOptions(theme = s.theme, caption = if (name == "player") "بهترین بازیکن فصل! 🔥" else "", sticker = when (name) { "duel" -> "fire"; "match" -> "goal"; "analysis" -> "crown"; else -> "" }), a)
                val frames = listOf(0.4f, 0.9f, 1.6f, 2.6f, 6.0f).map { t ->
                    val b = Bitmap.createBitmap(540, 960, Bitmap.Config.ARGB_8888)
                    val c = Canvas(b); c.scale(0.5f, 0.5f); sc.frame(c, t); t to b
                }
                Triple(name, sc.still(0.6f), frames)
            }
        }.orEmpty()
        for ((name, still, frames) in drawn) {
            png("70-story-$name", still)
            for ((t, b) in frames) png("71-story-$name-t${(t * 10).toInt()}", b)
        }
        System.err.println("SHOT stories: ${drawn.map { it.first }}")
        // every design on the match, the stats and the player stories, small, plus a moment of the moving ones
        val themed = off {
            stories.filter { it.first in setOf("match", "stats", "player", "analysis") }.flatMap { (name, s) ->
                val a = runBlocking { s.load(ctx) }
                app.goallive.live.ui.CARD_THEMES.map { t ->
                    val sc = s.scene(CardOptions(theme = t, sticker = if (name == "player") "star" else ""), a)
                    Triple("74-theme-$name-$t", sc.still(0.5f), if (name == "match" && t in setOf("fire", "ice", "neon", "stadium", "holo", "aurora")) listOf(1.0f, 2.2f).map { tt ->
                        val b = Bitmap.createBitmap(540, 960, Bitmap.Config.ARGB_8888); val c = Canvas(b); c.scale(0.5f, 0.5f); sc.frame(c, tt); tt to b
                    } else emptyList())
                }
            }
        }.orEmpty()
        for ((name, still, frames) in themed) {
            png(name, still)
            for ((t, b) in frames) png("$name-t${(t * 10).toInt()}", b)
        }
        // the line-up posters: El Clásico (each team, both, post size) and the team of the week, in every design
        val posters = off {
            val out = mutableListOf<Pair<String, Bitmap>>()
            val md = runBlocking { runCatching { MatchData(Api.fm("matchDetails", mapOf("matchId" to 5868105))) }.getOrNull() }
            fun objs(a: org.json.JSONArray?) = a?.let { x -> (0 until x.length()).map { x.getJSONObject(it) } }.orEmpty()
            if (md != null) {
                val lu = md.content!!.optJSONObject("lineup")!!
                fun team(side: String, home: Boolean, rival: Story.Lineup?): Story.Lineup {
                    val t = lu.optJSONObject(side)!!
                    val ev = { p: JSONObject -> objs(p.optJSONObject("performance")?.optJSONArray("events")).map { it.optString("type") } }
                    val subEv = { p: JSONObject, k: String -> objs(p.optJSONObject("performance")?.optJSONArray("substitutionEvents")).firstOrNull { it.optString("type") == k }?.optString("time") }
                    val xi = objs(t.optJSONArray("starters")).mapNotNull { p ->
                        val v = p.optJSONObject("verticalLayout") ?: return@mapNotNull null
                        val e = ev(p)
                        app.goallive.live.ui.PitchMan(p.optInt("id"), p.optString("lastName"), 0.89f - v.optDouble("x").toFloat() * 0.78f,
                            0.86f - (v.optDouble("y").toFloat() - 0.1f) / 0.77f * 0.74f, p.optString("shirtNumber"), p.optJSONObject("performance")?.optDouble("rating")?.takeIf { !it.isNaN() },
                            e.count { it == "goal" }, 0, e.count { it == "assist" }, "yellowCard" in e, "redCard" in e, subEv(p, "subOut"), p.optBoolean("isCaptain"))
                    }.let { men -> val b = men.maxByOrNull { it.rating ?: 0.0 }; men.map { m -> if (m !== b) m else app.goallive.live.ui.PitchMan(m.id, m.name, m.x, m.y, m.number, m.rating, m.goals, m.own, m.assists, m.yellow, m.red, m.outAt, m.captain, star = true) } }
                    val bench = objs(t.optJSONArray("subs")).filter { subEv(it, "subIn") != null }.take(5).map { p ->
                        app.goallive.live.ui.BenchMan(p.optInt("id"), p.optString("lastName"), subEv(p, "subIn"), p.optJSONObject("performance")?.optDouble("rating")?.takeIf { !it.isNaN() },
                            ev(p).count { it == "goal" }, ev(p).count { it == "assist" }, "yellowCard" in ev(p))
                    } + listOfNotNull(t.optJSONObject("coach")?.let { app.goallive.live.ui.BenchMan(it.optInt("id"), it.optString("lastName").ifEmpty { it.optString("name") }, coach = true) })
                    val id = t.optInt("id")
                    return Story.Lineup(t.optString("name"), "لالیگا · یکشنبه ۳ آبان", if (home) md.homeColor.toArgb() else md.awayColor.toArgb(), xi, id, null, t.optString("formation"), null, "x",
                        kicker = "ترکیب اصلی", teamName = t.optString("name"), bench = bench,
                        score = app.goallive.live.ui.PosterScore(md.homeId, md.homeName, md.home?.optInt("score") ?: 0, md.awayId, md.awayName, md.away?.optInt("score") ?: 0), home = home, rival = rival)
                }
                val away0 = team("awayTeam", false, null)
                val home = team("homeTeam", true, away0)
                val away = team("awayTeam", false, home)
                for ((tag, l) in listOf("home" to home, "away" to away)) {
                    val a = runBlocking { l.load(ctx) }
                    for (t in app.goallive.live.ui.POSTER_THEMES) out += "77-poster-$tag-$t" to l.scene(CardOptions(theme = t), a).still(0.5f)
                    out += "77-poster-$tag-post" to l.scene(CardOptions(theme = "team", format = app.goallive.live.ui.CardFormat.PORTRAIT), a).still(0.5f)
                    if (tag == "home") for (t in listOf("team", "neon", "light")) out += "77-poster-both-$t" to l.scene(CardOptions(theme = t, both = true), a).still(0.5f)
                    if (tag == "home") out += "77-poster-home-frame" to Bitmap.createBitmap(540, 960, Bitmap.Config.ARGB_8888).also { b -> val c = Canvas(b); c.scale(0.5f, 0.5f); l.scene(CardOptions(theme = "team"), a).frame(c, 1.4f) }
                }
            }
            // the team of the week from the published rankings
            val wk = rankings?.optJSONArray("weeks")?.optJSONObject(0)
            if (wk != null) {
                val spots = mapOf("GK" to listOf(0.5f to 0.88f), "RB" to listOf(0.87f to 0.67f), "LB" to listOf(0.13f to 0.67f), "CB" to listOf(0.63f to 0.7f, 0.37f to 0.7f),
                    "CM" to listOf(0.5f to 0.45f, 0.8f to 0.42f, 0.2f to 0.42f), "RW" to listOf(0.82f to 0.17f), "LW" to listOf(0.18f to 0.17f), "ST" to listOf(0.5f to 0.12f))
                val used = HashMap<String, Int>()
                val all = objs(wk.optJSONArray("all"))
                val best = all.maxByOrNull { it.optDouble("rating") }
                val men = all.map { p ->
                    val sl = p.optString("slot"); val k = used.getOrDefault(sl, 0); used[sl] = k + 1
                    val (x, y) = spots[sl]?.getOrNull(k) ?: (0.5f to 0.5f)
                    app.goallive.live.ui.PitchMan(p.optInt("id"), p.optString("name").split(" ").last(), x, y - 0.02f, rating = p.optDouble("rating"),
                        goals = p.optInt("goals"), assists = p.optInt("assists"), crest = p.optInt("team").takeIf { it > 0 }, star = p === best)
                }
                val strip = listOf(47, 87, 55, 54, 53).mapNotNull { lg -> objs(wk.optJSONObject("leagues")?.optJSONArray(lg.toString())).maxByOrNull { it.optDouble("rating") }?.let { b ->
                    app.goallive.live.ui.BenchMan(b.optInt("id"), b.optString("name").split(" ").last(), rating = b.optDouble("rating"), crest = b.optInt("team"), note = "لیگ $lg") } }
                val l = Story.Lineup("بهترین ۱۱ هفته", "۵ لیگ معتبر · ۲۷ مهر – ۳ آبان", 0xFFB8860B.toInt(), men, null, null, "۴-۳-۳", null, "x",
                    kicker = "تیم هفته‌ی گل‌لایو", bench = strip, benchLabel = "بهترین بازیکن هر لیگ", accent = 0xFFC6FF3D.toInt())
                val a = runBlocking { l.load(ctx) }
                for (t in app.goallive.live.ui.POSTER_THEMES) out += "78-totw-$t" to l.scene(CardOptions(theme = t), a).still(0.5f)
            }
            out
        }.orEmpty()
        for ((name, b) in posters) png(name, b)
        System.err.println("SHOT posters: ${posters.size}")
        // the tactics studio's export: a goal of El Clásico rebuilt, drawn as the video's frames and a step picture
        val tactic = off {
            val out = mutableListOf<Pair<String, Bitmap>>()
            val sm = runBlocking { app.goallive.live.ui.loadStudioMatch(5868105) }
            if (sm != null) {
                sm.goals.forEachIndexed { gi, g ->
                    val b = app.goallive.live.ui.StudioBoard(); b.fresh(); b.loadGoal(sm, g)
                    val art = runBlocking { app.goallive.live.ui.studioArt(ctx, b) }
                    val plan = app.goallive.live.ui.StudioPlan(b)
                    val times = if (gi == 0) listOf(0.4f, plan.seconds * 0.45f, plan.seconds - 2.4f, plan.seconds - 1.6f) else listOf(plan.seconds - 2.2f)
                    times.forEachIndexed { k, sec ->
                        val (t, fx) = plan.at(sec)
                        val bmp = Bitmap.createBitmap(540, 960, Bitmap.Config.ARGB_8888)
                        val c = Canvas(bmp); c.scale(0.5f, 0.5f)
                        app.goallive.live.ui.drawStudioStory(c, b, art, t, fx)
                        out += "79-tactic-goal$gi-$k" to bmp
                    }
                }
            } else System.err.println("SHOT tactic: no match")
            out
        }.orEmpty()
        for ((name, b) in tactic) png(name, b)
        System.err.println("SHOT tactic frames: ${tactic.size}")
        // every sticker on one sheet, on dark and on light
        off {
            val tf = android.graphics.Typeface.DEFAULT_BOLD
            val sheet = Bitmap.createBitmap(1400, 640, Bitmap.Config.ARGB_8888)
            val c = Canvas(sheet)
            c.drawColor(0xFF10141A.toInt())
            val p = android.graphics.Paint().apply { color = 0xFFF2EEE6.toInt() }
            c.drawRect(0f, 320f, 1400f, 640f, p)
            app.goallive.live.ui.STICKERS.filter { it.isNotEmpty() }.forEachIndexed { i, k ->
                for (row in 0..1) app.goallive.live.ui.drawSticker(c, k, 60f + i * 105f, 160f + row * 320f, 44f, 0xFF1E5BD8.toInt(), tf)
            }
            png("75-stickers", sheet)
            // each sticker large on a clear ground, for the promo video
            app.goallive.live.ui.STICKERS.filter { it.isNotEmpty() }.forEach { k ->
                val b = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
                app.goallive.live.ui.drawSticker(Canvas(b), k, 200f, 200f, 130f, 0xFF1E5BD8.toInt(), tf)
                png("76-sticker-$k", b)
            }
        }
        // the match reel: a frame every two seconds
        (stories.firstOrNull { it.first == "match" }?.second as? Story.Match)?.let { st ->
            val reel = off {
                val r = Story.Reel(st.m)
                val sc = r.scene(CardOptions(), runBlocking { r.load(ctx) })
                System.err.println("SHOT reel seconds=${sc.seconds}")
                generateSequence(0.8f) { it + 2f }.takeWhile { it < sc.seconds }.map { t ->
                    val b = Bitmap.createBitmap(432, 768, Bitmap.Config.ARGB_8888)
                    val c = Canvas(b); c.scale(0.4f, 0.4f); sc.frame(c, t); t to b
                }.toList()
            }.orEmpty()
            for ((t, b) in reel) png("73-reel-t${"%03d".format((t * 10).toInt())}", b)
        }
        // the studio over the player (or the match)
        val shown = stories.firstOrNull { it.first == "player" }?.second ?: stories.firstOrNull()?.second ?: return
        rule.setContent { GoalLiveTheme(Lime) { StorySheet(shown) { } } }
        settle(9000)
        runCatching { com.github.takahirom.roborazzi.captureScreenRoboImage("build/screens/72-story-studio.png") }
            .onFailure { System.err.println("SHOT studio capture failed: $it") }
    }
}
