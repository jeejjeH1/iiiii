package app.goallive.live.ui

import androidx.compose.ui.geometry.Offset
import app.goallive.live.L

/*
 * Ready-made moves for the tactics studio. Each starts from both sides in a formation, puts the players named by their slot
 * ("h9" the home side's slot 9: its formation's spots from the keeper, back line left to right, then forwards) where the move
 * begins, and then goes step by step: where the ball goes, who runs where, a caption, and what happens (a shot and how it ends,
 * a tackle, an offside). Players not named drift a little with the ball, so the shape stays alive. The home side attacks up.
 */

class ScenarioStep(
    val ball: Offset, val moves: Map<String, Offset> = emptyMap(), val note: String = "", val dur: Float = 1.1f, val air: Boolean = false,
    val ev: String? = null, val outcome: String = "goal", val decide: String = "manual",
)

class Scenario(val name: String, val about: String, val homeF: String, val awayF: String, val setup: Map<String, Offset>, val ball: Offset, val steps: List<ScenarioStep>)

private fun o(u: Float, v: Float) = Offset(u, v)

/** The players of [side] in [formation]'s slot order: each spot takes the nearest player not yet placed. */
private fun StudioBoard.slotsOf(side: Int, formation: String): List<SPlayer> {
    val left = players.filter { it.side == side }.toMutableList()
    return formationSpots(formation, side == 1).mapNotNull { s ->
        left.minByOrNull { (Offset(it.u, it.v) - s).getDistance() }?.also { left.remove(it) }
    }
}

/** Plays [s] onto the board with the sides already on it (their names, colours and players), ready to press play. */
fun StudioBoard.loadScenario(s: Scenario) {
    showAway = true
    marks.clear()
    // both sides back in shape: a full eleven each
    for (side in 0..1) if (players.count { it.side == side } < 11) {
        players.removeAll { it.side == side }; players += plainEleven(side, if (side == 0) s.homeF else s.awayF)
    }
    reform(0, s.homeF); reform(1, s.awayF)
    val slots = mapOf("h" to slotsOf(0, s.homeF), "a" to slotsOf(1, s.awayF))
    fun pl(k: String) = slots[k.take(1)]?.getOrNull(k.drop(1).toIntOrNull() ?: -1)
    s.setup.forEach { (k, at) -> pl(k)?.let { it.u = at.x; it.v = at.y } }
    players.forEach { it.spot = false }
    ball = s.ball
    frames.clear(); cur = 0; frames += frameNow(null)
    val keepers = setOfNotNull(keeperOf(0), keeperOf(1))
    for (st in s.steps) {
        val d = st.ball - ball
        val named = st.moves.keys.mapNotNull { pl(it) }.toSet()
        frames.add(cur + 1, frames[cur].copy(paths = emptyMap(), note = "", air = false, ev = null)); cur += 1
        players.filter { it !in named }.forEach { p ->
            val k = if (p in keepers) 0.08f else 0.22f
            p.u = (p.u + d.x * k).coerceIn(0.02f, 0.98f); p.v = (p.v + d.y * k * 0.6f).coerceIn(0.01f, 0.99f)
        }
        st.moves.forEach { (k, at) -> pl(k)?.let { it.u = at.x; it.v = at.y } }
        ball = st.ball
        frames[cur] = frameNow(null).copy(dur = st.dur, air = st.air, note = st.note)
        if (st.ev != null) setEvent(cur, st.ev, st.outcome, st.decide)
    }
    title = s.name
    cur = 0; applyFrame(frames[0])
    commit()
}

/** The library, in the order it is shown. */
val SCENARIOS: List<Scenario> by lazy {
    // the away side's back four and midfield four at the top, as they stand when defending in their own half
    val awayDeep = mapOf(
        "a0" to o(0.5f, 0.03f), "a1" to o(0.8f, 0.17f), "a2" to o(0.6f, 0.15f), "a3" to o(0.4f, 0.15f), "a4" to o(0.2f, 0.17f),
        "a5" to o(0.82f, 0.3f), "a6" to o(0.6f, 0.28f), "a7" to o(0.4f, 0.28f), "a8" to o(0.18f, 0.3f), "a9" to o(0.58f, 0.44f), "a10" to o(0.42f, 0.44f),
    )
    // the home side settled in the opponents' half
    val homeHigh = mapOf(
        "h0" to o(0.5f, 0.86f), "h1" to o(0.12f, 0.45f), "h2" to o(0.36f, 0.6f), "h3" to o(0.64f, 0.6f), "h4" to o(0.88f, 0.45f),
        "h5" to o(0.32f, 0.38f), "h6" to o(0.5f, 0.46f), "h7" to o(0.66f, 0.34f), "h8" to o(0.14f, 0.24f), "h9" to o(0.5f, 0.2f), "h10" to o(0.86f, 0.24f),
    )
    listOf(
        Scenario(L("شروع بازی از عقب"), L("دروازه‌بان و مدافع‌های میانی باز می‌شوند، هافبک دفاعی پایین می‌آید و توپ از پرس رد می‌شود"), "4-3-3", "4-4-2",
            mapOf("h0" to o(0.5f, 0.93f), "h2" to o(0.3f, 0.86f), "h3" to o(0.7f, 0.86f), "h1" to o(0.08f, 0.7f), "h4" to o(0.92f, 0.7f), "h6" to o(0.5f, 0.8f),
                "h5" to o(0.32f, 0.62f), "h7" to o(0.68f, 0.62f), "h8" to o(0.14f, 0.45f), "h9" to o(0.5f, 0.42f), "h10" to o(0.86f, 0.45f),
                "a9" to o(0.6f, 0.8f), "a10" to o(0.4f, 0.8f), "a5" to o(0.85f, 0.62f), "a6" to o(0.6f, 0.6f), "a7" to o(0.4f, 0.6f), "a8" to o(0.15f, 0.62f),
                "a1" to o(0.82f, 0.4f), "a2" to o(0.6f, 0.42f), "a3" to o(0.4f, 0.42f), "a4" to o(0.18f, 0.4f), "a0" to o(0.5f, 0.05f)),
            o(0.5f, 0.91f), listOf(
                ScenarioStep(o(0.3f, 0.86f), mapOf("a10" to o(0.34f, 0.82f)), L("دروازه‌بان به مدافع میانی که کنار رفته پاس می‌دهد")),
                ScenarioStep(o(0.5f, 0.77f), mapOf("h6" to o(0.5f, 0.77f), "a9" to o(0.56f, 0.78f)), L("هافبک دفاعی بین دو مهاجم حریف پایین می‌آید و توپ را می‌گیرد")),
                ScenarioStep(o(0.35f, 0.56f), mapOf("h5" to o(0.35f, 0.56f), "h6" to o(0.48f, 0.72f)), L("پاس رو به جلو به هافبکی که بین خطوط ایستاده"), 1.0f),
                ScenarioStep(o(0.12f, 0.36f), mapOf("h8" to o(0.12f, 0.36f), "h1" to o(0.08f, 0.52f)), L("وینگر چپ توپ را می‌گیرد؛ پرس حریف شکسته شد"), 1.2f),
            )),
        Scenario(L("ضدحمله‌ی سریع و گل"), L("قطع توپ در زمین خودی، دو پاس رو به جلو و فرار وینگر"), "4-3-3", "4-3-3",
            mapOf("h0" to o(0.5f, 0.93f), "h1" to o(0.18f, 0.8f), "h2" to o(0.4f, 0.82f), "h3" to o(0.6f, 0.82f), "h4" to o(0.82f, 0.8f),
                "h5" to o(0.34f, 0.68f), "h6" to o(0.48f, 0.7f), "h7" to o(0.64f, 0.68f), "h8" to o(0.22f, 0.55f), "h9" to o(0.5f, 0.52f), "h10" to o(0.78f, 0.55f),
                "a0" to o(0.5f, 0.06f), "a1" to o(0.8f, 0.45f), "a2" to o(0.6f, 0.42f), "a3" to o(0.4f, 0.42f), "a4" to o(0.2f, 0.45f),
                "a5" to o(0.7f, 0.6f), "a6" to o(0.5f, 0.6f), "a7" to o(0.3f, 0.6f), "a8" to o(0.82f, 0.72f), "a9" to o(0.48f, 0.74f), "a10" to o(0.18f, 0.72f)),
            o(0.47f, 0.73f), listOf(
                ScenarioStep(o(0.47f, 0.71f), mapOf("h6" to o(0.47f, 0.71f)), L("هافبک دفاعی توپ را از مهاجم حریف می‌گیرد"), 0.8f, ev = "tackle"),
                ScenarioStep(o(0.5f, 0.5f), mapOf("h9" to o(0.5f, 0.5f), "h8" to o(0.2f, 0.36f), "h10" to o(0.8f, 0.36f)), L("پاس سریع به مهاجم؛ دو وینگر فرار می‌کنند"), 0.9f),
                ScenarioStep(o(0.74f, 0.22f), mapOf("h10" to o(0.74f, 0.22f), "a1" to o(0.76f, 0.3f), "a2" to o(0.6f, 0.26f), "a3" to o(0.44f, 0.26f), "h9" to o(0.52f, 0.34f)),
                    L("پاس در عمق برای وینگری که پشت مدافع‌ها رفته"), 1.0f),
                ScenarioStep(o(0.64f, 0.12f), mapOf("h10" to o(0.64f, 0.12f), "h9" to o(0.5f, 0.17f), "a2" to o(0.6f, 0.16f)), L("وینگر به سمت دروازه می‌برد"), 0.9f),
                ScenarioStep(o(0.64f, 0.12f), note = L("شوت با پای داخل به گوشه‌ی دور"), dur = 0.6f, ev = "shot"),
            )),
        Scenario(L("ضدحمله و سیو دروازه‌بان"), L("همان ضدحمله، این بار دروازه‌بان حریف درست جاگیری می‌کند"), "4-3-3", "4-3-3",
            mapOf("h9" to o(0.5f, 0.45f), "h10" to o(0.78f, 0.4f), "h8" to o(0.22f, 0.4f), "a2" to o(0.6f, 0.3f), "a3" to o(0.4f, 0.3f), "a1" to o(0.8f, 0.34f),
                "a4" to o(0.2f, 0.34f), "a0" to o(0.5f, 0.05f)),
            o(0.5f, 0.45f), listOf(
                ScenarioStep(o(0.7f, 0.2f), mapOf("h10" to o(0.7f, 0.2f), "a1" to o(0.74f, 0.25f)), L("پاس در عمق برای وینگر"), 1.0f),
                ScenarioStep(o(0.62f, 0.12f), mapOf("h10" to o(0.62f, 0.12f), "a0" to o(0.56f, 0.04f)), L("دروازه‌بان زاویه را می‌بندد"), 0.9f),
                ScenarioStep(o(0.62f, 0.12f), note = L("شوت؛ دروازه‌بان آماده است"), dur = 0.6f, ev = "shot", outcome = "save"),
            )),
        Scenario(L("اورلپ مدافع کناری و سانتر"), L("وینگر به داخل می‌زند، مدافع کناری از پشتش می‌دود و سانتر می‌کند"), "4-3-3", "4-4-2",
            homeHigh + awayDeep + mapOf("h10" to o(0.84f, 0.3f), "h4" to o(0.88f, 0.44f)), o(0.84f, 0.3f), listOf(
                ScenarioStep(o(0.72f, 0.27f), mapOf("h10" to o(0.72f, 0.27f), "h4" to o(0.9f, 0.24f), "a1" to o(0.76f, 0.22f)), L("وینگر به داخل می‌زند و مدافع کناری از پشتش اورلپ می‌کند"), 1.1f),
                ScenarioStep(o(0.9f, 0.13f), mapOf("h4" to o(0.9f, 0.13f), "a5" to o(0.84f, 0.2f)), L("پاس به مدافع کناری در پشت مدافع حریف"), 0.9f),
                ScenarioStep(o(0.55f, 0.07f), mapOf("h9" to o(0.55f, 0.07f), "h8" to o(0.38f, 0.09f), "a2" to o(0.6f, 0.09f)), L("سانتر روی تیر دوم"), 1.0f, air = true),
                ScenarioStep(o(0.55f, 0.07f), note = L("ضربه‌ی سر"), dur = 0.5f, ev = "shot"),
            )),
        Scenario(L("یک‌دو در لبه‌ی محوطه"), L("هافبک به مهاجم پاس می‌دهد، جلو می‌رود و توپ را پس می‌گیرد"), "4-2-3-1", "4-4-2",
            homeHigh + awayDeep + mapOf("h8" to o(0.5f, 0.27f), "h10" to o(0.56f, 0.17f)), o(0.5f, 0.27f), listOf(
                ScenarioStep(o(0.56f, 0.18f), mapOf("h10" to o(0.56f, 0.18f)), L("پاس به مهاجمی که پشت به دروازه ایستاده"), 0.8f),
                ScenarioStep(o(0.44f, 0.13f), mapOf("h8" to o(0.44f, 0.13f), "a3" to o(0.47f, 0.16f)), L("پاس برگشتی به هافبکی که از کنار مدافع رد شده"), 0.8f),
                ScenarioStep(o(0.44f, 0.13f), note = L("شوت از داخل محوطه"), dur = 0.5f, ev = "shot"),
            )),
        Scenario(L("پاس‌کاری کوتاه در یک‌سوم پایانی"), L("پاس‌های کوتاه پشت سر هم تا باز شدن راه، بعد پاس در عمق"), "4-3-3", "4-4-2",
            homeHigh + awayDeep, o(0.5f, 0.46f), listOf(
                ScenarioStep(o(0.32f, 0.38f), mapOf("h5" to o(0.32f, 0.38f)), L("پاس کوتاه"), 0.7f),
                ScenarioStep(o(0.15f, 0.26f), mapOf("h8" to o(0.15f, 0.26f)), L("پاس به کناره"), 0.7f),
                ScenarioStep(o(0.36f, 0.3f), mapOf("h5" to o(0.36f, 0.3f)), L("پاس برگشتی به داخل"), 0.7f),
                ScenarioStep(o(0.6f, 0.3f), mapOf("h7" to o(0.6f, 0.3f), "a7" to o(0.45f, 0.27f)), L("تغییر جهت؛ بلوک حریف کش می‌آید"), 0.8f),
                ScenarioStep(o(0.52f, 0.12f), mapOf("h9" to o(0.52f, 0.12f), "a2" to o(0.6f, 0.15f), "a3" to o(0.42f, 0.15f)), L("پاس در عمق بین دو مدافع میانی"), 0.9f),
                ScenarioStep(o(0.52f, 0.12f), note = L("تمام‌کننده"), dur = 0.5f, ev = "shot"),
            )),
        Scenario(L("کرنر: تیر اول"), L("سانتر روی تیر اول و ضربه‌ی سر"), "4-3-3", "4-4-2",
            mapOf("h10" to o(0.99f, 0.0f), "h9" to o(0.5f, 0.13f), "h3" to o(0.6f, 0.13f), "h2" to o(0.42f, 0.14f), "h7" to o(0.36f, 0.12f), "h6" to o(0.5f, 0.24f),
                "h5" to o(0.3f, 0.28f), "h8" to o(0.2f, 0.2f), "h4" to o(0.72f, 0.4f), "h1" to o(0.25f, 0.45f), "h0" to o(0.5f, 0.75f),
                "a0" to o(0.5f, 0.02f), "a2" to o(0.6f, 0.08f), "a3" to o(0.45f, 0.08f), "a1" to o(0.56f, 0.04f), "a4" to o(0.4f, 0.11f), "a6" to o(0.52f, 0.12f),
                "a7" to o(0.38f, 0.14f), "a5" to o(0.66f, 0.16f), "a8" to o(0.3f, 0.2f), "a9" to o(0.5f, 0.32f), "a10" to o(0.3f, 0.4f)),
            o(0.99f, 0.005f), listOf(
                ScenarioStep(o(0.58f, 0.05f), mapOf("h3" to o(0.58f, 0.05f), "a2" to o(0.6f, 0.06f)), L("سانتر چرخشی روی تیر اول؛ مدافع بلند جلوتر از یار خود می‌رسد"), 1.1f, air = true),
                ScenarioStep(o(0.58f, 0.05f), note = L("ضربه‌ی سر از تیر اول"), dur = 0.5f, ev = "shot"),
            )),
        Scenario(L("کرنر: تیر دوم"), L("سانتر بلند روی تیر دوم برای مهاجمی که دیر وارد می‌شود"), "4-3-3", "4-4-2",
            mapOf("h10" to o(0.99f, 0.0f), "h9" to o(0.5f, 0.13f), "h3" to o(0.6f, 0.13f), "h2" to o(0.32f, 0.16f), "h7" to o(0.42f, 0.12f), "h6" to o(0.5f, 0.24f),
                "a0" to o(0.5f, 0.02f), "a2" to o(0.6f, 0.08f), "a3" to o(0.45f, 0.08f), "a1" to o(0.56f, 0.04f), "a4" to o(0.4f, 0.11f), "a6" to o(0.52f, 0.12f)),
            o(0.99f, 0.005f), listOf(
                ScenarioStep(o(0.37f, 0.06f), mapOf("h2" to o(0.37f, 0.06f), "a4" to o(0.4f, 0.08f)), L("سانتر بلند روی تیر دوم"), 1.3f, air = true),
                ScenarioStep(o(0.37f, 0.06f), note = L("ضربه‌ی سر"), dur = 0.5f, ev = "shot", outcome = "post"),
            )),
        Scenario(L("کرنر کوتاه"), L("پاس کوتاه، برگشت و سانتر از زاویه‌ی بهتر"), "4-3-3", "4-4-2",
            mapOf("h10" to o(0.99f, 0.0f), "h4" to o(0.86f, 0.06f), "h9" to o(0.5f, 0.12f), "h3" to o(0.58f, 0.13f), "h6" to o(0.48f, 0.22f),
                "a0" to o(0.5f, 0.02f), "a5" to o(0.88f, 0.1f), "a2" to o(0.58f, 0.08f), "a3" to o(0.45f, 0.08f)),
            o(0.99f, 0.005f), listOf(
                ScenarioStep(o(0.88f, 0.06f), mapOf("h4" to o(0.88f, 0.06f)), L("کرنر کوتاه"), 0.7f),
                ScenarioStep(o(0.97f, 0.03f), mapOf("h10" to o(0.97f, 0.03f), "a5" to o(0.9f, 0.07f)), L("برگشت به زننده‌ی کرنر"), 0.7f),
                ScenarioStep(o(0.5f, 0.2f), mapOf("h6" to o(0.5f, 0.2f)), L("سانتر به هافبکی که بیرون محوطه آزاد مانده"), 1.0f, air = true),
                ScenarioStep(o(0.5f, 0.2f), note = L("شوت از راه دور"), dur = 0.6f, ev = "shot", outcome = "goal", decide = "luck"),
            )),
        Scenario(L("ضربه‌ی آزاد مستقیم"), L("دیوار چهارنفره و ضربه‌ی چرخشی؛ نتیجه با تاس"), "4-3-3", "4-4-2",
            mapOf("h7" to o(0.5f, 0.25f), "h9" to o(0.54f, 0.26f), "a0" to o(0.47f, 0.02f), "a5" to o(0.44f, 0.16f), "a6" to o(0.48f, 0.16f), "a7" to o(0.52f, 0.16f),
                "a8" to o(0.56f, 0.16f), "a2" to o(0.62f, 0.1f), "a3" to o(0.36f, 0.1f), "h8" to o(0.34f, 0.14f), "h10" to o(0.66f, 0.14f)),
            o(0.5f, 0.24f), listOf(
                ScenarioStep(o(0.5f, 0.24f), note = L("ضربه از بالای دیوار"), dur = 0.6f, ev = "shot", decide = "luck"),
            )),
        Scenario(L("ضربه‌ی آزاد کناری"), L("سانتر از ضربه‌ی آزاد کناری و ضربه‌ی سر"), "4-3-3", "4-4-2",
            mapOf("h7" to o(0.86f, 0.22f), "h9" to o(0.5f, 0.16f), "h3" to o(0.58f, 0.17f), "h2" to o(0.42f, 0.17f), "a5" to o(0.8f, 0.17f), "a6" to o(0.83f, 0.16f),
                "a2" to o(0.6f, 0.12f), "a3" to o(0.44f, 0.12f), "a0" to o(0.5f, 0.03f)),
            o(0.86f, 0.22f), listOf(
                ScenarioStep(o(0.48f, 0.07f), mapOf("h9" to o(0.48f, 0.07f), "a3" to o(0.46f, 0.09f)), L("سانتر بین دروازه‌بان و مدافع‌ها"), 1.1f, air = true),
                ScenarioStep(o(0.48f, 0.07f), note = L("ضربه‌ی سر؛ دروازه‌بان واکنش نشان می‌دهد"), dur = 0.5f, ev = "shot", outcome = "save"),
            )),
        Scenario(L("پنالتی"), L("نتیجه با تاس و احتمال واقعی یک پنالتی"), "4-3-3", "4-4-2",
            mapOf("h9" to o(0.5f, 0.125f), "a0" to o(0.5f, 0.0f), "h7" to o(0.4f, 0.18f), "h8" to o(0.6f, 0.18f), "a2" to o(0.45f, 0.18f), "a3" to o(0.55f, 0.18f),
                "a6" to o(0.35f, 0.17f), "h10" to o(0.65f, 0.17f)),
            o(0.5f, 0.105f), listOf(
                ScenarioStep(o(0.5f, 0.105f), note = L("پنالتی"), dur = 0.6f, ev = "shot", decide = "luck"),
            )),
        Scenario(L("گگن‌پرسینگ"), L("توپ از دست می‌رود و سه بازیکن بلافاصله پرس می‌کنند تا پس بگیرند"), "4-3-3", "4-4-2",
            homeHigh + awayDeep + mapOf("a6" to o(0.52f, 0.31f)), o(0.5f, 0.32f), listOf(
                ScenarioStep(o(0.53f, 0.3f), mapOf("a6" to o(0.53f, 0.3f)), L("پاس قطع می‌شود"), 0.6f, ev = "intercept"),
                ScenarioStep(o(0.55f, 0.32f), mapOf("a6" to o(0.55f, 0.32f), "h7" to o(0.56f, 0.34f), "h9" to o(0.58f, 0.28f), "h6" to o(0.5f, 0.36f)),
                    L("سه نفر در چند ثانیه دورش را می‌گیرند"), 0.7f),
                ScenarioStep(o(0.56f, 0.34f), mapOf("h7" to o(0.56f, 0.34f)), L("توپ پس گرفته شد"), 0.5f, ev = "tackle"),
                ScenarioStep(o(0.5f, 0.15f), mapOf("h9" to o(0.5f, 0.15f), "a2" to o(0.58f, 0.18f)), L("پاس فوری به مهاجم قبل از اینکه حریف جمع شود"), 0.8f),
                ScenarioStep(o(0.5f, 0.15f), note = L("شوت"), dur = 0.5f, ev = "shot", decide = "auto"),
            )),
        Scenario(L("تله‌ی آفساید"), L("خط دفاع درست لحظه‌ی پاس با هم بالا می‌آید و مهاجم آفساید می‌شود"), "4-4-2", "4-3-3",
            mapOf("h1" to o(0.18f, 0.76f), "h2" to o(0.4f, 0.77f), "h3" to o(0.6f, 0.77f), "h4" to o(0.82f, 0.76f), "h0" to o(0.5f, 0.94f),
                "a6" to o(0.5f, 0.55f), "a9" to o(0.45f, 0.75f), "a8" to o(0.22f, 0.68f), "a10" to o(0.78f, 0.7f)),
            o(0.5f, 0.56f), listOf(
                ScenarioStep(o(0.5f, 0.57f), mapOf("h1" to o(0.18f, 0.7f), "h2" to o(0.4f, 0.7f), "h3" to o(0.6f, 0.7f), "h4" to o(0.82f, 0.7f)),
                    L("خط دفاع با هم بالا می‌آید"), 0.7f),
                ScenarioStep(o(0.45f, 0.76f), mapOf("a9" to o(0.45f, 0.76f)), L("پاس در عمق؛ مهاجم پشت خط دفاع مانده"), 0.9f, ev = "offside"),
            )),
        Scenario(L("حمله در برابر دفاع بسته"), L("حریف با ده نفر جلوی محوطه؛ توپ چرخانده می‌شود و شوت با مدافع برخورد می‌کند"), "4-3-3", "5-4-1",
            homeHigh + mapOf("a0" to o(0.5f, 0.03f), "a1" to o(0.86f, 0.14f), "a2" to o(0.68f, 0.12f), "a3" to o(0.5f, 0.11f), "a4" to o(0.32f, 0.12f),
                "a5" to o(0.14f, 0.14f), "a6" to o(0.8f, 0.24f), "a7" to o(0.6f, 0.23f), "a8" to o(0.4f, 0.23f), "a9" to o(0.2f, 0.24f), "a10" to o(0.5f, 0.36f)),
            o(0.5f, 0.46f), listOf(
                ScenarioStep(o(0.32f, 0.38f), mapOf("h5" to o(0.32f, 0.38f)), L("توپ از یک طرف به طرف دیگر"), 0.8f),
                ScenarioStep(o(0.68f, 0.36f), mapOf("h7" to o(0.68f, 0.36f)), L("تغییر جهت؛ بلوک حریف فقط کمی جابه‌جا می‌شود"), 1.0f, air = true),
                ScenarioStep(o(0.56f, 0.28f), mapOf("h7" to o(0.56f, 0.28f)), L("نفوذ به داخل"), 0.8f),
                ScenarioStep(o(0.56f, 0.28f), note = L("شوت از بیرون محوطه"), dur = 0.6f, ev = "shot", outcome = "block"),
            )),
    )
}
