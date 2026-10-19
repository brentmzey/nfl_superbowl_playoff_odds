package com.gridiron.superbowl.app

import com.gridiron.superbowl.data.CachingSource
import com.gridiron.superbowl.data.EspnHttpSource
import com.gridiron.superbowl.engine.SimulationConfig
import com.gridiron.superbowl.report.SvgCharts.pct
import java.nio.file.Path
import java.time.LocalDate
import kotlin.system.exitProcess

/** CLI: `./gradlew run -PsimArgs="--season 2026 --iterations 20000 --seed 2026"`. */
fun main(args: Array<String>) {
    val flags = args.toList().chunked(2).filter { it.size == 2 }.associate { it[0].removePrefix("--") to it[1] }
    val today = LocalDate.now()
    val season = flags["season"]?.toInt() ?: if (today.monthValue >= 3) today.year else today.year - 1
    val config = SimulationConfig(
        iterations = flags["iterations"]?.toInt() ?: 20_000,
        seed = flags["seed"]?.toLong() ?: season.toLong(),
    )
    val root = Path.of("").toAbsolutePath()
    val source = CachingSource(EspnHttpSource(), root.resolve(".cache/espn"), season)

    Pipeline(source, root).run(PipelineOptions(season, config, today)).fold(
        onSuccess = { report ->
            println("Season ${report.season} through week ${report.throughWeek}: ${report.iterations} simulations")
            report.byChampionOdds.take(10).forEach {
                println("  %-24s %-6s playoffs %6s  SB %6s  win %6s".format(it.name, it.record, pct(it.pPlayoffs), pct(it.pSuperBowl), pct(it.pChampion)))
            }
        },
        onFailure = { e ->
            System.err.println("Refresh failed: ${e.message}")
            exitProcess(1)
        },
    )
}
