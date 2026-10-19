package com.gridiron.superbowl.app

import com.gridiron.superbowl.data.EspnClient
import com.gridiron.superbowl.data.ScoreboardSource
import com.gridiron.superbowl.engine.EloModel
import com.gridiron.superbowl.engine.SeasonSimulator
import com.gridiron.superbowl.engine.SimulationConfig
import com.gridiron.superbowl.report.MarkdownReport
import com.gridiron.superbowl.report.OddsSnapshot
import com.gridiron.superbowl.report.Reports
import com.gridiron.superbowl.report.SimulationReport
import com.gridiron.superbowl.report.SvgCharts
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.io.path.extension
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText

data class PipelineOptions(
    val season: Int,
    val config: SimulationConfig = SimulationConfig(),
    val today: LocalDate = LocalDate.now(),
)

/** End-to-end refresh: fetch -> rate -> simulate -> write datasets, charts and README sections. */
class Pipeline(private val source: ScoreboardSource, private val root: Path, private val elo: EloModel = EloModel()) {
    private val json = Json { prettyPrint = true; encodeDefaults = true }

    fun run(options: PipelineOptions): Result<SimulationReport> = runCatching {
        val client = EspnClient(source)
        val prior = client.season(options.season - 1, includePostseason = true).getOrThrow()
        val current = client.season(options.season, includePostseason = false).getOrThrow()
        val ratings = elo.rateSeasons(prior, current)
        val tallies = SeasonSimulator(elo, options.config).simulate(ratings, current)
        Reports.build(options.today.toString(), options.season, current, ratings, tallies, options.config.seed)
            .also { write(it, options.today) }
    }

    private fun write(report: SimulationReport, today: LocalDate) {
        val output = root.resolve("output")
        val history = output.resolve("history")
        val charts = root.resolve("docs/charts")
        listOf(history, charts).forEach(Files::createDirectories)

        val encoded = json.encodeToString(report)
        Files.writeString(output.resolve("latest.json"), encoded)
        Files.writeString(history.resolve("$today.json"), encoded)
        Files.writeString(output.resolve("team_outlook.csv"), MarkdownReport.csv(report))

        val snapshots = loadHistory(history)
        mapOf(
            "superbowl_odds.svg" to SvgCharts.championshipOdds(report),
            "playoff_survival.svg" to SvgCharts.playoffSurvival(report),
            "elo_ratings.svg" to SvgCharts.eloRatings(report, elo.params.mean),
            "odds_trend.svg" to SvgCharts.oddsTrend(snapshots, report),
        ).forEach { (name, svg) -> svg?.let { Files.writeString(charts.resolve(name), it) } }

        root.resolve("README.md").takeIf(Files::exists)?.let { readme ->
            val updated = listOf(
                "SUMMARY" to MarkdownReport.summary(report),
                "LEADERBOARD" to MarkdownReport.leaderboard(report),
                "BRACKET" to MarkdownReport.bracket(report),
                "MATCHUPS" to MarkdownReport.matchups(report),
                "TREND" to if (snapshots.size >= 2) "![Super Bowl odds over time](docs/charts/odds_trend.svg)"
                else "_The trend chart appears once a second weekly snapshot exists in `output/history/`._",
            ).fold(Files.readString(readme)) { doc, (marker, content) -> MarkdownReport.inject(doc, marker, content) }
            Files.writeString(readme, updated)
        }
    }

    fun loadHistory(dir: Path): List<OddsSnapshot> =
        dir.listDirectoryEntries().filter { it.extension == "json" }.sorted().map { file ->
            val r = json.decodeFromString<SimulationReport>(file.readText())
            OddsSnapshot(
                LocalDate.parse(r.generatedAt).format(DateTimeFormatter.ofPattern("MMM d")),
                r.teams.associate { it.abbr to it.pChampion },
            )
        }
}
