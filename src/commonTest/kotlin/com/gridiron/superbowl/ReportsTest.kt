package com.gridiron.superbowl

import com.gridiron.superbowl.engine.EloModel
import com.gridiron.superbowl.engine.Ratings
import com.gridiron.superbowl.engine.SeasonSimulator
import com.gridiron.superbowl.engine.SimulationConfig
import com.gridiron.superbowl.model.Conference
import com.gridiron.superbowl.model.League
import com.gridiron.superbowl.model.Round
import com.gridiron.superbowl.report.MarkdownReport
import com.gridiron.superbowl.report.OddsSnapshot
import com.gridiron.superbowl.report.Reports
import com.gridiron.superbowl.report.SimulationReport
import com.gridiron.superbowl.report.SvgCharts
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReportsTest {
    companion object {
        val season = Fixtures.conferenceRoundRobin(playedWeeks = 4) + Fixtures.game("NYG", "DAL", 20, 20, week = 2)
        val ratings = Ratings(League.teams.map { 1400.0 + it.id * 6.0 })
        val report: SimulationReport by lazy {
            val config = SimulationConfig(iterations = 300, seed = 9)
            Reports.build("2026-10-19", 2026, season, ratings, SeasonSimulator(EloModel(), config).simulate(ratings, season), 9)
        }
    }

    @Test
    fun recordsCountWinsLossesTies() {
        val r = Reports.records(listOf(Fixtures.game("NYG", "DAL", 20, 20), Fixtures.game("NYG", "PHI", 21, 3), Fixtures.game("PHI", "DAL")))
        assertEquals(Reports.Record(1, 0, 1), r["NYG"])
        assertEquals(Reports.Record(0, 0, 1), r["DAL"])
        assertNull(r["WSH"])
    }

    @Test
    fun reportProbabilitiesAreCoherent() {
        assertEquals(32, report.teams.size)
        assertEquals(1.0, report.teams.sumOf { it.pChampion }, 1e-9)
        assertEquals(14.0, report.teams.sumOf { it.pPlayoffs }, 1e-9)
        assertEquals(4, report.throughWeek)
        assertEquals(report.gamesPlayed + report.gamesRemaining, season.size)
        assertTrue(report.superBowlMatchups.size <= 10)
        val nyg = report.teams.first { it.abbr == "NYG" }
        assertTrue(nyg.record.count { it == '-' } == 2)
        Round.entries.forEach { r -> assertTrue(report.teams.all { it.probability(r) in 0.0..1.0 }) }
        report.teams.forEach { assertEquals(1.0, it.seedOdds.sum(), 1e-9) }
        Conference.entries.forEach { c ->
            val seeds = report.projectedSeeds(c)
            assertEquals(7, seeds.toSet().size)
            assertTrue(seeds.all { it.conference == c.name })
            assertEquals(4, seeds.take(4).map { it.division }.toSet().size)
        }
    }

    @Test
    fun markdownFragments() {
        val board = MarkdownReport.leaderboard(report)
        assertEquals(34, board.lines().size)
        assertTrue(MarkdownReport.summary(report).contains("Most likely Super Bowl"))
        assertTrue(MarkdownReport.matchups(report).startsWith("| AFC | NFC |"))
        val bracket = MarkdownReport.bracket(report)
        assertTrue(bracket.startsWith("```mermaid") && bracket.contains("AFC_CCG --> SB"))
        assertEquals(33, MarkdownReport.csv(report).trim().lines().size)

        val doc = "a\n<!-- X:START -->\nold\n<!-- X:END -->\nb"
        assertEquals("a\n<!-- X:START -->\nnew\n<!-- X:END -->\nb", MarkdownReport.inject(doc, "X", "new"))
        assertEquals("no markers", MarkdownReport.inject("no markers", "X", "new"))
    }

    @Test
    fun svgCharts() {
        listOf(SvgCharts.championshipOdds(report), SvgCharts.playoffSurvival(report), SvgCharts.eloRatings(report)).forEach {
            assertTrue(it.startsWith("<svg") && it.trimEnd().endsWith("</svg>"))
            assertTrue(it.contains("prefers-color-scheme: dark"))
            assertTrue(it.contains("<title>"))
        }
        assertNull(SvgCharts.oddsTrend(listOf(OddsSnapshot("Oct 12", mapOf())), report))
        val history = (1..10).map { i -> OddsSnapshot("Wk $i", report.teams.associate { it.abbr to it.pChampion * i / 10 }) }
        val trend = SvgCharts.oddsTrend(history, report)!!
        assertTrue(trend.contains("polyline"))
        assertTrue(trend.contains("Wk 1") && trend.contains("Wk 10") && !trend.contains(">Wk 5<"))
        assertTrue(SvgCharts.oddsTrend(history.take(3), report)!!.contains(">Wk 2<"))
    }

    @Test
    fun percentFormatting() {
        assertEquals("0%", SvgCharts.pct(0.0))
        assertEquals("<0.1%", SvgCharts.pct(0.0004))
        assertEquals("4.5%", SvgCharts.pct(0.045))
        assertEquals("27%", SvgCharts.pct(0.27))
        assertEquals(listOf(0.0, 0.05, 0.1, 0.15), SvgCharts.probabilityTicks(0.148).map { (it * 1000).toInt() / 1000.0 })
        assertEquals(listOf(0.0, 0.01), SvgCharts.probabilityTicks(0.0))
        assertEquals(6, SvgCharts.probabilityTicks(0.95).size)
    }
}
