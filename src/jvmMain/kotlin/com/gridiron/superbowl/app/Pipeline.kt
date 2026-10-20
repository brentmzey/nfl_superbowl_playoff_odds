package com.gridiron.superbowl.app

import com.gridiron.superbowl.data.EspnClient
import com.gridiron.superbowl.data.ScoreboardSource
import com.gridiron.superbowl.engine.EloModel
import com.gridiron.superbowl.engine.SeasonSimulator
import com.gridiron.superbowl.engine.SimulationConfig
import com.gridiron.superbowl.model.Game
import com.gridiron.superbowl.report.MarkdownReport
import com.gridiron.superbowl.report.OddsSnapshot
import com.gridiron.superbowl.report.Reports
import com.gridiron.superbowl.report.SimulationReport
import com.gridiron.superbowl.report.SvgCharts
import com.gridiron.superbowl.replica.BackupStore
import com.gridiron.superbowl.replica.PocketHostSnapshot
import com.gridiron.superbowl.replica.PostgresConfig
import com.gridiron.superbowl.replica.PostgresReplica
import com.gridiron.superbowl.replica.SqliteReplica
import com.gridiron.superbowl.sync.PocketBaseClient
import com.gridiron.superbowl.sync.PocketBaseSchema
import com.gridiron.superbowl.sync.SyncSummary
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
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

/** One refresh: the report plus every game it was built from (both seasons), for the PocketHost sync. */
data class Refresh(val report: SimulationReport, val games: List<Game>)

/** Where a refresh's PocketHost data was copied: backup folder, SQLite counts, and the Postgres outcome (if attempted). */
data class ReplicationSummary(
    val backup: Path,
    val sqlite: Path,
    val sqliteCounts: Map<String, Int>,
    val postgres: Result<PostgresReplica.Result>?,
)

/** End-to-end refresh: fetch -> rate -> simulate -> write datasets, charts and README sections. */
class Pipeline(private val source: ScoreboardSource, private val root: Path, private val elo: EloModel = EloModel()) {
    private val json = Json { prettyPrint = true; encodeDefaults = true }

    fun run(options: PipelineOptions): Result<Refresh> = runCatching {
        val client = EspnClient(source)
        val prior = client.season(options.season - 1, includePostseason = true).getOrThrow()
        val current = client.season(options.season, includePostseason = false).getOrThrow()
        val ratings = elo.rateSeasons(prior, current)
        val tallies = SeasonSimulator(elo, options.config).simulate(ratings, current)
        val report = Reports.build(options.today.toString(), options.season, current, ratings, tallies, options.config.seed)
        write(report, options.today)
        Refresh(report, prior + current)
    }

    private fun write(report: SimulationReport, today: LocalDate) {
        val output = root.resolve("output")
        val history = output.resolve("history")
        val charts = root.resolve("docs/charts")
        val schema = root.resolve("docs/schema")
        listOf(history, charts, schema).forEach(Files::createDirectories)
        Files.writeString(schema.resolve("pockethost_collections.json"), json.encodeToString(PocketBaseSchema.importJson()) + "\n")

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

    /** Pushes a finished report to PocketBase (schema bootstrap + idempotent upserts). */
    fun publish(refresh: Refresh, client: PocketBaseClient, epochMs: Long = System.currentTimeMillis()): Result<SyncSummary> =
        runCatching { client.sync(refresh.report, refresh.games, epochMs) }

    /**
     * Pulls everything back out of PocketHost (the source of truth), writes a timestamped JSON backup, and rebuilds
     * the local SQLite file and, when [postgres] is given, the local PostgreSQL schema from scratch.
     */
    fun replicate(client: PocketBaseClient, sourceUrl: String, postgres: PostgresConfig?, now: Instant = Instant.now()): Result<ReplicationSummary> =
        runCatching {
            val snapshot = PocketHostSnapshot.pull(client, sourceUrl, now)
            val backup = BackupStore(root.resolve("backups/pockethost")).write(snapshot)
            val sqlite = root.resolve("output/db/nfl_superbowl_playoff_odds.sqlite")
            val counts = SqliteReplica.rebuild(sqlite, snapshot)
            ReplicationSummary(backup, sqlite, counts, postgres?.let { cfg -> runCatching { PostgresReplica.rebuild(cfg, snapshot) } })
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
