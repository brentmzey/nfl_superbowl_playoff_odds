package com.gridiron.superbowl.engine

import com.gridiron.superbowl.model.Conference
import com.gridiron.superbowl.model.Game
import com.gridiron.superbowl.model.League
import com.gridiron.superbowl.model.Round
import com.gridiron.superbowl.model.SeasonPhase
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

data class SimulationConfig(
    val iterations: Int = 20_000,
    val seed: Long = 2026L,
    /** Std. dev. of the final margin around the Elo spread (NFL historical ~13-14 points). */
    val marginSd: Double = 13.5,
    /** Re-rate teams after each simulated game so hot/cold streaks persist within a run. */
    val hotRatings: Boolean = true,
)

/** Raw Monte Carlo tallies; [com.gridiron.superbowl.report.Reports] turns these into probabilities. */
data class SimulationTallies(
    val iterations: Int,
    val totalWins: List<Double>,
    val divisionTitles: List<Int>,
    /** seedCounts[team][s] = runs finishing as seed s+1; index 7 = missed playoffs. */
    val seedCounts: List<List<Int>>,
    /** roundCounts[team][r] = runs reaching at least [Round.entries][r]. */
    val roundCounts: List<List<Int>>,
    /** (AFC champion id, NFC champion id) -> number of simulated Super Bowls. */
    val matchups: Map<Pair<Int, Int>, Int>,
)

class SeasonSimulator(
    private val elo: EloModel = EloModel(),
    private val config: SimulationConfig = SimulationConfig(),
) {
    private fun gaussian(rng: Random): Double {
        val u1 = 1.0 - rng.nextDouble()
        val u2 = rng.nextDouble()
        return sqrt(-2.0 * ln(u1)) * cos(2.0 * PI * u2)
    }

    /** Samples a non-zero home margin around the Elo spread. */
    internal tailrec fun sampleMargin(edge: Double, rng: Random): Int {
        val m = (elo.spread(edge) + gaussian(rng) * config.marginSd).roundToInt()
        return if (m != 0) m else sampleMargin(edge, rng)
    }

    fun simulate(ratings: Ratings, season: List<Game>): SimulationTallies {
        val regular = season.filter { it.phase == SeasonPhase.REGULAR }
        val base = SeasonTable().also { t ->
            regular.filter { it.isFinal }.forEach { g -> t.record(id(g.home), id(g.away), g.homeMargin!!) }
        }
        val remaining = regular.filterNot { it.isFinal }.sortedBy { it.date }
            .map { Triple(id(it.home), id(it.away), it.neutral) }

        val n = League.size
        val totalWins = DoubleArray(n)
        val divisionTitles = IntArray(n)
        val seedCounts = Array(n) { IntArray(Seeding.SEEDS_PER_CONFERENCE + 1) }
        val roundCounts = Array(n) { IntArray(Round.entries.size) }
        val matchups = HashMap<Pair<Int, Int>, Int>()
        val rng = Random(config.seed)

        repeat(config.iterations) {
            val r = ratings.values.toDoubleArray()
            val table = base.copy()
            val player = GamePlayer { home, away, neutral -> playGame(r, home, away, neutral, rng, null) }

            remaining.forEach { (home, away, neutral) -> playGame(r, home, away, neutral, rng, table) }
            (0 until n).forEach { totalWins[it] += table.wins(it) }

            val afc = Seeding.seeds(Conference.AFC, table, rng)
            val nfc = Seeding.seeds(Conference.NFC, table, rng)
            val seededTeams = (afc.withIndex() + nfc.withIndex()).associate { it.value to it.index }
            (0 until n).forEach { team ->
                seedCounts[team][seededTeams[team] ?: Seeding.SEEDS_PER_CONFERENCE]++
                if ((seededTeams[team] ?: Int.MAX_VALUE) < 4) divisionTitles[team]++
            }

            val bracket = Playoffs.play(afc, nfc, player)
            bracket.reached.forEach { (team, round) -> (0..round.ordinal).forEach { roundCounts[team][it]++ } }
            val key = bracket.afcChampion to bracket.nfcChampion
            matchups[key] = (matchups[key] ?: 0) + 1
        }

        return SimulationTallies(
            iterations = config.iterations,
            totalWins = totalWins.toList(),
            divisionTitles = divisionTitles.toList(),
            seedCounts = seedCounts.map { it.toList() },
            roundCounts = roundCounts.map { it.toList() },
            matchups = matchups,
        )
    }

    private fun playGame(r: DoubleArray, home: Int, away: Int, neutral: Boolean, rng: Random, table: SeasonTable?): Int {
        val margin = sampleMargin(elo.homeEdge(r[home], r[away], neutral), rng)
        table?.record(home, away, margin)
        if (config.hotRatings) elo.applyInPlace(r, home, away, neutral, margin)
        return if (margin > 0) home else away
    }

    private fun id(abbr: String): Int = League.team(abbr).id
}
