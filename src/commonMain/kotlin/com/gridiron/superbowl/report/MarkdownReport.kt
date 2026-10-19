package com.gridiron.superbowl.report

import com.gridiron.superbowl.model.Conference
import com.gridiron.superbowl.report.SvgCharts.pct
import kotlin.math.roundToInt

/** Generated README fragments, injected between `<!-- NAME:START -->` / `<!-- NAME:END -->` markers. */
object MarkdownReport {

    fun leaderboard(report: SimulationReport): String {
        val header = "| # | Team | Record | Elo | Proj. W | Division | Playoffs | Divisional | Conf. game | Super Bowl | **Win SB** |\n" +
            "|---:|---|:---:|---:|---:|---:|---:|---:|---:|---:|---:|"
        val rows = report.byChampionOdds.mapIndexed { i, t ->
            "| ${i + 1} | ${t.name} | ${t.record} | ${t.elo.roundToInt()} | ${(t.projectedWins * 10).roundToInt() / 10.0} | " +
                "${pct(t.pDivision)} | ${pct(t.pPlayoffs)} | ${pct(t.pDivisional)} | ${pct(t.pConference)} | " +
                "${pct(t.pSuperBowl)} | **${pct(t.pChampion)}** |"
        }
        return (listOf(header) + rows).joinToString("\n")
    }

    fun summary(report: SimulationReport): String {
        val top = report.byChampionOdds.take(3)
        val m = report.superBowlMatchups.firstOrNull()
        return listOfNotNull(
            "_Updated ${report.generatedAt} · ${report.season} season through week ${report.throughWeek} " +
                "(${report.gamesPlayed} games played, ${report.gamesRemaining} remaining) · ${report.iterations} simulations._",
            "",
            "**Favorites:** " + top.joinToString(" · ") { "${it.name} ${pct(it.pChampion)}" },
            m?.let { "**Most likely Super Bowl:** ${it.afc} vs ${it.nfc} (${pct(it.probability)})" },
        ).joinToString("\n")
    }

    fun matchups(report: SimulationReport): String =
        (listOf("| AFC | NFC | Probability |", "|---|---|---:|") +
            report.superBowlMatchups.map { "| ${it.afc} | ${it.nfc} | ${pct(it.probability)} |" }).joinToString("\n")

    /** Mermaid bracket of the most likely seeding, with each team's odds of reaching the next round. */
    fun bracket(report: SimulationReport): String {
        fun conf(c: Conference): String {
            val s = report.projectedSeeds(c)
            fun node(seed: Int) = s[seed - 1].let { "${c}$seed[\"($seed) ${it.abbr} ${it.record}<br/>div. rd ${pct(it.pDivisional)}\"]" }
            return """
              subgraph ${c}_WC["$c"]
                direction TB
                ${node(1)} -->|bye| ${c}_DIV
                ${node(2)} --- ${c}_G1(("2 v 7")) --- ${node(7)}
                ${node(3)} --- ${c}_G2(("3 v 6")) --- ${node(6)}
                ${node(4)} --- ${c}_G3(("4 v 5")) --- ${node(5)}
                ${c}_G1 --> ${c}_DIV
                ${c}_G2 --> ${c}_DIV
                ${c}_G3 --> ${c}_DIV
                ${c}_DIV{{"$c Divisional"}} --> ${c}_CCG{{"$c Championship<br/>fav: ${report.conference(c).maxBy { it.pSuperBowl }.abbr} ${pct(report.conference(c).maxOf { it.pSuperBowl })}"}}
              end""".trimIndent()
        }
        val champ = report.byChampionOdds.first()
        return listOf(
            "```mermaid",
            "flowchart LR",
            conf(Conference.AFC),
            conf(Conference.NFC),
            "AFC_CCG --> SB{{\"Super Bowl ${report.season + 1}<br/>fav: ${champ.abbr} ${pct(champ.pChampion)}\"}}",
            "NFC_CCG --> SB",
            "```",
        ).joinToString("\n")
    }

    fun inject(document: String, marker: String, content: String): String {
        val start = "<!-- $marker:START -->"
        val end = "<!-- $marker:END -->"
        val from = document.indexOf(start)
        val to = document.indexOf(end)
        return if (from < 0 || to < from) document
        else document.substring(0, from + start.length) + "\n" + content + "\n" + document.substring(to)
    }

    fun csv(report: SimulationReport): String =
        (listOf("abbr,team,conference,division,wins,losses,ties,elo,projected_wins,p_division,p_top_seed,p_playoffs,p_divisional,p_conference,p_super_bowl,p_champion") +
            report.byChampionOdds.map { t ->
                listOf(
                    t.abbr, "\"${t.name}\"", t.conference, "\"${t.division}\"", t.wins, t.losses, t.ties,
                    (t.elo * 10).roundToInt() / 10.0, (t.projectedWins * 100).roundToInt() / 100.0,
                    t.pDivision, t.pTopSeed, t.pPlayoffs, t.pDivisional, t.pConference, t.pSuperBowl, t.pChampion,
                ).joinToString(",")
            }).joinToString("\n") + "\n"
}
