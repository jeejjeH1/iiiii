package app.goallive.live

import app.goallive.live.ui.XgRow
import app.goallive.live.ui.forecast
import app.goallive.live.ui.goalChances
import app.goallive.live.ui.linesOf
import app.goallive.live.ui.outcome
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

    @Test fun evenTeamsEvenChances() {
        val (h, d, a) = outcome(0, 0, 1.3, 1.3)
        assertEquals(1.0, h + d + a, 1e-6); assertEquals(h, a, 1e-9)
    }

    @Test fun aLeadWithNoTimeLeftIsAWin() = assertEquals(1.0, outcome(2, 1, 0.0, 0.0).first, 1e-9)

    @Test fun homeSideFavouredWhenEqual() {
        val rows = listOf(XgRow(1, "A", 10, 15, 15.0, 13.0, 13.0), XgRow(2, "B", 10, 15, 15.0, 13.0, 13.0),
            XgRow(3, "C", 10, 15, 15.0, 13.0, 13.0), XgRow(4, "D", 10, 15, 15.0, 13.0, 13.0))
        val f = forecast(rows, 1, 2)!!
        assertEquals(1.0, f.h + f.d + f.a, 1e-6)
        assert(f.h > f.a)
    }
}
