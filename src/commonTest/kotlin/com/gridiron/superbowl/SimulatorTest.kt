package com.gridiron.superbowl

import com.gridiron.superbowl.engine.EloModel
import com.gridiron.superbowl.engine.Ratings
import com.gridiron.superbowl.engine.SeasonSimulator
import com.gridiron.superbowl.engine.SimulationConfig
import com.gridiron.superbowl.model.League
import com.gridiron.superbowl.model.Round
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SimulatorTest {
    private val season = Fixtures.conferenceRoundRobin(playedWeeks = 5)
    private val ratings = Ratings(League.teams.map { 1400.0 + it.id * 6.0 })

    private fun run(config: SimulationConfig) = SeasonSimulator(EloModel(), config).simulate(ratings, season)

    @Test
    fun talliesAreInternallyConsistent() {
        val n = 400
        val t = run(SimulationConfig(iterations = n, seed = 7))
        assertEquals(n, t.iterations)
        assertEquals(n, t.roundCounts.sumOf { it[Round.CHAMPION.ordinal] })
        assertEquals(14 * n, t.roundCounts.sumOf { it[Round.MAKE_PLAYOFFS.ordinal] })
        assertEquals(8 * n, t.roundCounts.sumOf { it[Round.DIVISIONAL.ordinal] })
        assertEquals(4 * n, t.roundCounts.sumOf { it[Round.CONFERENCE.ordinal] })
        assertEquals(2 * n, t.roundCounts.sumOf { it[Round.SUPER_BOWL.ordinal] })
        assertEquals(8 * n, t.divisionTitles.sum())
        assertEquals(n, t.matchups.values.sum())
        t.seedCounts.forEach { assertEquals(n, it.sum()) }
        // Every team plays 15 games in the synthetic schedule.
        assertEquals(15.0 * League.size * n, t.totalWins.sum() * 2, 1e-6)
        t.roundCounts.forEach { counts -> assertTrue(counts.zipWithNext().all { (a, b) -> a >= b }) }
    }

    @Test
    fun deterministicPerSeed() {
        val a = run(SimulationConfig(iterations = 200, seed = 11))
        val b = run(SimulationConfig(iterations = 200, seed = 11))
        val c = run(SimulationConfig(iterations = 200, seed = 12))
        assertEquals(a, b)
        assertNotEquals(a.roundCounts, c.roundCounts)
    }

    @Test
    fun strongerTeamsWinMoreWithStaticRatings() {
        val t = run(SimulationConfig(iterations = 400, seed = 3, hotRatings = false))
        val best = League.team("SEA").id
        val worst = League.team("BUF").id
        assertTrue(t.roundCounts[best][Round.CHAMPION.ordinal] > t.roundCounts[worst][Round.CHAMPION.ordinal])
    }

    @Test
    fun sampledMarginsAreNeverTiesAndTrackTheSpread() {
        val sim = SeasonSimulator()
        val rng = Random(5)
        val margins = (0 until 2000).map { sim.sampleMargin(250.0, rng) }
        assertTrue(margins.none { it == 0 })
        assertEquals(10.0, margins.average(), 1.5)
    }
}
