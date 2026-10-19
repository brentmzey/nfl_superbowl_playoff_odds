package com.gridiron.superbowl.report

import com.gridiron.superbowl.engine.Ratings
import com.gridiron.superbowl.engine.SimulationTallies
import com.gridiron.superbowl.model.Conference
import com.gridiron.superbowl.model.Game
import com.gridiron.superbowl.model.League
import com.gridiron.superbowl.model.Round
import com.gridiron.superbowl.model.SeasonPhase
import kotlinx.serialization.Serializable

@Serializable
data class TeamOutlook(
    val abbr: String,
    val name: String,
    val conference: String,
    val division: String,
    val elo: Double,
    val wins: Int,
    val losses: Int,
    val ties: Int,
    val projectedWins: Double,
    val pDivision: Double,
    val pTopSeed: Double,
    val pPlayoffs: Double,
    val pDivisional: Double,
    val pConference: Double,
    val pSuperBowl: Double,
    val pChampion: Double,
    /** P(seed 1..7), then P(miss). */
    val seedOdds: List<Double>,
) {
    val record: String get() = if (ties > 0) "$wins-$losses-$ties" else "$wins-$losses"

    fun probability(round: Round): Double = when (round) {
        Round.MAKE_PLAYOFFS -> pPlayoffs
        Round.DIVISIONAL -> pDivisional
        Round.CONFERENCE -> pConference
        Round.SUPER_BOWL -> pSuperBowl
        Round.CHAMPION -> pChampion
    }
}

@Serializable
data class MatchupOdds(val afc: String, val nfc: String, val probability: Double)

@Serializable
data class SimulationReport(
    val generatedAt: String,
    val season: Int,
    val throughWeek: Int,
    val gamesPlayed: Int,
    val gamesRemaining: Int,
    val iterations: Int,
    val seed: Long,
    val teams: List<TeamOutlook>,
    val superBowlMatchups: List<MatchupOdds>,
) {
    val byChampionOdds: List<TeamOutlook> get() = teams.sortedByDescending { it.pChampion }

    fun conference(conference: Conference): List<TeamOutlook> =
        teams.filter { it.conference == conference.name }

    /**
     * Most likely seed line per conference. Each division's likeliest champion fills seeds 1-4 and the rest fill
     * 5-7. Within each group, every seed goes to the unassigned team most likely to hold it.
     */
    fun projectedSeeds(conference: Conference): List<TeamOutlook> {
        val clubs = conference(conference)
        val champions = clubs.groupBy { it.division }.values.map { div -> div.maxBy { it.pDivision } }
        fun assign(pool: List<TeamOutlook>, seeds: IntRange) =
            seeds.fold(emptyList<TeamOutlook>()) { picked, seed ->
                picked + pool.filter { it !in picked }.maxBy { it.seedOdds[seed] }
            }
        return assign(champions, 0..3) + assign(clubs - champions.toSet(), 4..6)
    }
}

object Reports {

    data class Record(val wins: Int, val losses: Int, val ties: Int)

    fun records(season: List<Game>): Map<String, Record> =
        season.filter { it.phase == SeasonPhase.REGULAR && it.isFinal }
            .flatMap { g ->
                val m = g.homeMargin!!
                listOf(g.home to m, g.away to -m)
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, margins) ->
                Record(margins.count { it > 0 }, margins.count { it < 0 }, margins.count { it == 0 })
            }

    fun build(
        generatedAt: String,
        season: Int,
        games: List<Game>,
        ratings: Ratings,
        tallies: SimulationTallies,
        seed: Long,
        topMatchups: Int = 10,
    ): SimulationReport {
        val n = tallies.iterations.toDouble()
        val records = records(games)
        val regular = games.filter { it.phase == SeasonPhase.REGULAR }
        val teams = League.teams.map { t ->
            val rec = records[t.abbr] ?: Record(0, 0, 0)
            val rounds = tallies.roundCounts[t.id].map { it / n }
            TeamOutlook(
                abbr = t.abbr,
                name = t.name,
                conference = t.conference.name,
                division = t.division.label,
                elo = ratings[t.id],
                wins = rec.wins,
                losses = rec.losses,
                ties = rec.ties,
                projectedWins = tallies.totalWins[t.id] / n,
                pDivision = tallies.divisionTitles[t.id] / n,
                pTopSeed = tallies.seedCounts[t.id][0] / n,
                pPlayoffs = rounds[Round.MAKE_PLAYOFFS.ordinal],
                pDivisional = rounds[Round.DIVISIONAL.ordinal],
                pConference = rounds[Round.CONFERENCE.ordinal],
                pSuperBowl = rounds[Round.SUPER_BOWL.ordinal],
                pChampion = rounds[Round.CHAMPION.ordinal],
                seedOdds = tallies.seedCounts[t.id].map { it / n },
            )
        }
        val matchups = tallies.matchups.entries.sortedByDescending { it.value }.take(topMatchups).map { (k, v) ->
            MatchupOdds(League.teams[k.first].abbr, League.teams[k.second].abbr, v / n)
        }
        return SimulationReport(
            generatedAt = generatedAt,
            season = season,
            throughWeek = regular.filter { it.isFinal }.maxOfOrNull { it.week } ?: 0,
            gamesPlayed = regular.count { it.isFinal },
            gamesRemaining = regular.count { !it.isFinal },
            iterations = tallies.iterations,
            seed = seed,
            teams = teams,
            superBowlMatchups = matchups,
        )
    }
}
