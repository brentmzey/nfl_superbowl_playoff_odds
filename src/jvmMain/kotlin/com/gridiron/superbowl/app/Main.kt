package com.gridiron.superbowl.app

import com.gridiron.superbowl.data.CachingSource
import com.gridiron.superbowl.data.EspnHttpSource
import com.gridiron.superbowl.engine.SimulationConfig
import com.gridiron.superbowl.replica.PostgresConfig
import com.gridiron.superbowl.report.SvgCharts.pct
import com.gridiron.superbowl.sync.PocketBaseClient
import com.gridiron.superbowl.sync.PocketHostConfig
import java.nio.file.Path
import java.time.LocalDate
import kotlin.system.exitProcess

/**
 * CLI: `./gradlew run -PsimArgs="--season 2026 --iterations 20000 --seed 2026 --db auto"`.
 * PocketHost is required by default (`--db required`): the run fails fast if settings are missing, before any work.
 * `--db off` skips the database for offline/local runs. After a sync, PocketHost is pulled back into a timestamped
 * JSON backup, a SQLite file, and local PostgreSQL (`--postgres auto` tries and warns, `required` fails, `off` skips).
 */
fun main(args: Array<String>) {
    val flags = args.toList().chunked(2).filter { it.size == 2 }.associate { it[0].removePrefix("--") to it[1] }
    val today = LocalDate.now()
    val season = flags["season"]?.toInt() ?: if (today.monthValue >= 3) today.year else today.year - 1
    val config = SimulationConfig(
        iterations = flags["iterations"]?.toInt() ?: 20_000,
        seed = flags["seed"]?.toLong() ?: season.toLong(),
    )
    val dbMode = flags["db"] ?: "required"
    val pgMode = flags["postgres"] ?: "auto"
    val root = Path.of("").toAbsolutePath()
    val dotEnvs = listOf(root.resolve(".env"), Path.of(System.getProperty("user.home"), ".env"))
    val source = CachingSource(EspnHttpSource(), root.resolve(".cache/espn"), season)
    if (flags["schema-only"] == "true") {
        Pipeline(source, root).writeSchemaArtifacts()
        println("Schema artifacts written (docs/schema, clients/ts/src/generated)")
        return
    }
    val dbConfig = if (dbMode == "off") null
    else PocketHostConfig.resolve(System.getenv(), dotEnvs)
        .getOrElse { fail("${it.message}. Use --db off for a local-only run.") }

    val pipeline = Pipeline(source, root)
    val refresh = pipeline.run(PipelineOptions(season, config, today)).getOrElse { fail("Refresh failed: ${it.message}") }
    val report = refresh.report
    println("Season ${report.season} through week ${report.throughWeek}: ${report.iterations} simulations")
    report.byChampionOdds.take(10).forEach {
        println("  %-24s %-6s playoffs %6s  SB %6s  win %6s".format(it.name, it.record, pct(it.pPlayoffs), pct(it.pSuperBowl), pct(it.pChampion)))
    }

    dbConfig?.let { db ->
        pipeline.publish(refresh, PocketBaseClient(db)).fold(
            onSuccess = {
                println("PocketHost sync -> ${db.baseUrl}: ${it.created} created, ${it.updated} updated, ${it.unchanged} unchanged, " +
                    "${it.superseded} older runs superseded; new collections ${it.collectionsCreated}, " +
                    "fields +${it.fieldsAdded}, indexes +${it.indexesAdded}, views ~${it.viewsChanged}, migrations recorded ${it.migrationsRecorded}")
            },
            onFailure = { fail("PocketHost sync to ${db.baseUrl} failed (local outputs were written): ${it.message}") },
        )
        val client = PocketBaseClient(db).authenticate()
        val pg = if (pgMode == "off") null else PostgresConfig.resolve(System.getenv(), dotEnvs)
        val rep = pipeline.replicate(client, db.baseUrl, pg).getOrElse { fail("Replication from PocketHost failed: ${it.message}") }
        println("Backup: ${root.relativize(rep.backup)} · SQLite: ${root.relativize(rep.sqlite)} (${rep.sqliteCounts.values.sum()} rows)")
        rep.postgres?.fold(
            onSuccess = { println("PostgreSQL ${pg}: ${it.counts.values.sum()} rows${if (it.created) " (database created via DbFactory)" else ""}") },
            onFailure = { e ->
                val msg = "PostgreSQL replica skipped (${pg}): ${e.message}"
                if (pgMode == "required") fail(msg) else System.err.println("WARN $msg")
            },
        ) ?: println("PostgreSQL replica: off")
    } ?: println("PocketHost sync: off")
}

private fun fail(message: String): Nothing {
    System.err.println(message)
    exitProcess(1)
}
