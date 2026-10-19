package com.gridiron.superbowl

import com.gridiron.superbowl.engine.SeasonTable
import com.gridiron.superbowl.engine.Seeding
import com.gridiron.superbowl.model.Conference
import com.gridiron.superbowl.model.Division
import com.gridiron.superbowl.model.League
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SeedingTest {
    private fun id(a: String) = League.team(a).id
    private val rng = Random(1)

    @Test
    fun divisionTieBrokenHeadToHead() {
        val t = SeasonTable()
        t.record(id("KC"), id("DEN"), 3)       // KC beats DEN
        t.record(id("DEN"), id("BUF"), 10)     // both 1-1 overall
        t.record(id("KC"), id("BUF"), -10)
        assertEquals(id("KC"), Seeding.breakTie(listOf(id("KC"), id("DEN")), Seeding.divisionCriteria, t, rng))
    }

    @Test
    fun skipsHeadToHeadWhenClubsHaveNotMetAndFallsThroughToConferenceThenPoints() {
        val t = SeasonTable()
        t.record(id("BUF"), id("KC"), 3)       // BUF: 1-0 conference
        t.record(id("SF"), id("HOU"), 3)       // HOU: 0-1 non-conference
        t.record(id("HOU"), id("SEA"), 3)
        val tied = listOf(id("BUF"), id("HOU"))
        assertEquals(id("BUF"), Seeding.breakTie(tied, Seeding.wildCardCriteria, t, rng))

        val p = SeasonTable()
        p.record(id("BUF"), id("SF"), 20)
        p.record(id("HOU"), id("SF"), 3)
        assertEquals(id("BUF"), Seeding.breakTie(tied, Seeding.wildCardCriteria, p, rng))
    }

    @Test
    fun coinFlipWhenEverythingIsEqual() {
        val winners = (0 until 200).map {
            Seeding.breakTie(listOf(id("NYJ"), id("MIA")), Seeding.divisionCriteria, SeasonTable(), Random(it))
        }.toSet()
        assertEquals(setOf(id("NYJ"), id("MIA")), winners)
    }

    @Test
    fun divisionOrderIsByWinPct() {
        val t = SeasonTable()
        repeat(3) { t.record(id("PIT"), id("SF"), 7) }
        repeat(2) { t.record(id("BAL"), id("SF"), 7) }
        t.record(id("CIN"), id("SF"), 7)
        assertEquals(listOf("PIT", "BAL", "CIN", "CLE"), Seeding.divisionOrder(Division.AFC_NORTH, t, rng).map { League.teams[it].abbr })
    }

    @Test
    fun seedsAreSevenDistinctClubsWithDivisionWinnersOnTop() {
        val t = SeasonTable()
        League.conference(Conference.AFC).forEachIndexed { i, team -> repeat(i) { t.record(team.id, id("SF"), 3) } }
        val seeds = Seeding.seeds(Conference.AFC, t, rng)
        assertEquals(7, seeds.toSet().size)
        val champs = seeds.take(4)
        assertEquals(4, champs.map { League.teams[it].division }.toSet().size)
        assertTrue(seeds.all { League.teams[it].conference == Conference.AFC })
        // LAC has the most wins in the AFC, so it is the #1 seed.
        assertEquals(id("LAC"), seeds.first())
        // Wild cards: the best non-champions are the other three AFC West clubs.
        assertEquals(setOf("DEN", "KC", "LV"), seeds.drop(4).map { League.teams[it].abbr }.toSet())
    }

    @Test
    fun wildCardRespectsDivisionRank() {
        val t = SeasonTable()
        repeat(3) { t.record(id("KC"), id("SF"), 3) }
        t.record(id("KC"), id("DEN"), 3)
        // DEN and LV both 2-1; DEN won the head-to-head, LV has the better point differential.
        t.record(id("DEN"), id("LV"), 1)
        t.record(id("DEN"), id("SF"), 3)
        t.record(id("LV"), id("BUF"), 20)
        t.record(id("LV"), id("SF"), 3)
        repeat(3) { t.record(id("BUF"), id("SF"), 3) }
        listOf(3, 3, -3).forEach { t.record(id("NE"), id("SF"), it) }
        val seeds = Seeding.seeds(Conference.AFC, t, rng)
        assertTrue(id("KC") in seeds.take(4))
        assertTrue(id("BUF") in seeds.take(4))
        assertEquals(id("DEN"), seeds[4])
        assertTrue(seeds[5] in setOf(id("LV"), id("NE")))
    }
}
