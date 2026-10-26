package app.goallive.live

import app.goallive.live.ui.goalChances
import app.goallive.live.ui.linesOf
import org.junit.Assert.assertEquals
import org.junit.Test

/** The analyst's arithmetic: the fair result and the real shape must be exactly right. */
class AnalystTest {
    @Test fun twoEvenShots() {
        val d = goalChances(listOf(0.5, 0.5))
        assertEquals(0.25, d[0], 1e-9); assertEquals(0.5, d[1], 1e-9); assertEquals(0.25, d[2], 1e-9)
    }

    @Test fun chancesAddUpToOne() {
        val d = goalChances(List(30) { 0.13 })
        assertEquals(1.0, d.sum(), 1e-9)
        assertEquals(30 * 0.13, d.withIndex().sumOf { it.index * it.value }, 0.05)
    }

    @Test fun noShotsNoGoals() = assertEquals(1.0, goalChances(emptyList())[0], 1e-9)

    @Test fun fourThreeThree() = assertEquals(listOf(4, 3, 3), linesOf(listOf(28f, 30f, 31f, 29f, 48f, 52f, 50f, 70f, 72f, 69f)))

    @Test fun fourTwoThreeOne() = assertEquals(listOf(4, 2, 3, 1), linesOf(listOf(30f, 31f, 29f, 30f, 44f, 45f, 60f, 61f, 59f, 80f)))
}
