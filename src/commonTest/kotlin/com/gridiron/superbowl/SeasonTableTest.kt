package com.gridiron.superbowl

import com.gridiron.superbowl.engine.SeasonTable
import com.gridiron.superbowl.model.League
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SeasonTableTest {
    private fun id(a: String) = League.team(a).id

    @Test
    fun tracksOverallDivisionConferenceAndHeadToHead() {
        val t = SeasonTable()
        t.record(id("BUF"), id("MIA"), 7)   // division
        t.record(id("BUF"), id("KC"), -3)   // conference
        t.record(id("SF"), id("BUF"), 0)    // inter-conference tie
        assertEquals(1.5, t.wins(id("BUF")))
        assertEquals(3, t.gamesPlayed(id("BUF")))
        assertEquals(0.5, t.winPct(id("BUF")))
        assertEquals(1.0, t.divisionPct(id("BUF")))
        assertEquals(0.5, t.conferencePct(id("BUF")))
        assertEquals(4, t.netPoints(id("BUF")))
        assertEquals(1.0, t.headToHeadPct(id("BUF"), listOf(id("BUF"), id("MIA"))))
        assertNull(t.headToHeadPct(id("BUF"), listOf(id("BUF"), id("NYJ"))))
        assertEquals(0.0, t.winPct(id("TEN")))
    }

    @Test
    fun copyIsIndependent() {
        val t = SeasonTable()
        t.record(id("DAL"), id("PHI"), 3)
        val c = t.copy()
        c.record(id("DAL"), id("PHI"), -3)
        assertEquals(1, t.gamesPlayed(id("DAL")))
        assertEquals(2, c.gamesPlayed(id("DAL")))
    }
}
