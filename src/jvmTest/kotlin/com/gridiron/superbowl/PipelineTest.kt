package com.gridiron.superbowl

import com.gridiron.superbowl.app.Pipeline
import com.gridiron.superbowl.app.PipelineOptions
import com.gridiron.superbowl.data.ScoreboardSource
import com.gridiron.superbowl.engine.SimulationConfig
import java.nio.file.Files
import javax.xml.parsers.DocumentBuilderFactory
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PipelineTest {
    private val week6 = javaClass.getResource("/espn_2026_week6.json")!!.readText()
    private val source = ScoreboardSource { season, type, week ->
        Result.success(if (season == 2026 && type == 2 && week == 6) week6 else """{"events":[]}""")
    }
    private val readme = listOf("SUMMARY", "LEADERBOARD", "BRACKET", "MATCHUPS", "TREND")
        .joinToString("\n") { "## $it\n<!-- $it:START -->\n<!-- $it:END -->" }

    @Test
    fun writesDatasetsChartsAndReadmeAndBuildsTrendFromHistory() {
        val root = Files.createTempDirectory("pipeline")
        Files.writeString(root.resolve("README.md"), readme)
        val pipeline = Pipeline(source, root)
        val config = SimulationConfig(iterations = 200, seed = 1)

        val first = pipeline.run(PipelineOptions(2026, config, LocalDate.of(2026, 10, 12))).getOrThrow().report
        assertEquals(13, first.gamesPlayed)
        assertEquals(1.0, first.teams.sumOf { it.pChampion }, 1e-9)
        listOf("output/latest.json", "output/team_outlook.csv", "output/history/2026-10-12.json",
            "docs/charts/superbowl_odds.svg", "docs/charts/playoff_survival.svg", "docs/charts/elo_ratings.svg",
        ).forEach { assertTrue(Files.exists(root.resolve(it)), it) }
        assertFalse(Files.exists(root.resolve("docs/charts/odds_trend.svg")))
        Files.list(root.resolve("docs/charts")).forEach { svg ->
            DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(svg.toFile())
        }
        assertTrue(Files.readString(root.resolve("README.md")).contains("second weekly snapshot"))

        pipeline.run(PipelineOptions(2026, config, LocalDate.of(2026, 10, 19))).getOrThrow()
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(root.resolve("docs/charts/odds_trend.svg").toFile())
        val doc = Files.readString(root.resolve("README.md"))
        assertTrue(doc.contains("odds_trend.svg"))
        assertTrue(doc.contains("```mermaid"))
        assertTrue(doc.contains("| 1 |"))
        assertEquals(listOf("Oct 12", "Oct 19"), pipeline.loadHistory(root.resolve("output/history")).map { it.label })
    }

    @Test
    fun skipsReadmeWhenAbsentAndPropagatesSourceFailures() {
        val root = Files.createTempDirectory("pipeline-bare")
        val ok = Pipeline(source, root).run(PipelineOptions(2026, SimulationConfig(iterations = 50)))
        assertTrue(ok.isSuccess)
        assertFalse(Files.exists(root.resolve("README.md")))

        val broken = ScoreboardSource { _, _, _ -> Result.failure(IllegalStateException("offline")) }
        val failed = Pipeline(broken, root).run(PipelineOptions(2026))
        assertEquals("offline", failed.exceptionOrNull()?.message)
    }
}
