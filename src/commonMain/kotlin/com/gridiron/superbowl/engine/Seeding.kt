package com.gridiron.superbowl.engine

import com.gridiron.superbowl.model.Conference
import com.gridiron.superbowl.model.Division
import com.gridiron.superbowl.model.League
import kotlin.math.abs
import kotlin.random.Random

/** A tiebreak step: scores every tied team; higher is better, or null when the step does not apply. */
typealias Criterion = (team: Int, tied: List<Int>, table: SeasonTable) -> Double?

/**
 * NFL playoff seeding with a condensed version of the official tiebreaking procedure.
 *
 * Division: win pct, head-to-head, division pct, conference pct, net points, coin flip.
 * Wild card / seeding: win pct, best-of-division filter, head-to-head (only when every tied club has met),
 * conference pct, net points, coin flip. Common-games and strength-of-victory/schedule steps are omitted.
 * As in the rulebook, whenever a step eliminates some (but not all) clubs, the procedure restarts.
 */
object Seeding {
    const val SEEDS_PER_CONFERENCE = 7
    private const val EPS = 1e-9

    private val headToHead: Criterion = { t, tied, table -> table.headToHeadPct(t, tied) }
    private val divisionPct: Criterion = { t, _, table -> table.divisionPct(t) }
    private val conferencePct: Criterion = { t, _, table -> table.conferencePct(t) }
    private val netPoints: Criterion = { t, _, table -> table.netPoints(t).toDouble() }

    val divisionCriteria: List<Criterion> = listOf(headToHead, divisionPct, conferencePct, netPoints)
    val wildCardCriteria: List<Criterion> = listOf(headToHead, conferencePct, netPoints)

    /** Breaks a tie among clubs with identical win pct, returning the single winner. */
    tailrec fun breakTie(tied: List<Int>, criteria: List<Criterion>, table: SeasonTable, rng: Random): Int {
        if (tied.size == 1) return tied.first()
        val narrowed = criteria.asSequence()
            .map { c -> tied.map { it to c(it, tied, table) } }
            .filter { scored -> scored.none { it.second == null } }
            .map { scored ->
                val best = scored.maxOf { it.second!! }
                scored.filter { abs(it.second!! - best) < EPS }.map { it.first }
            }
            .firstOrNull { it.size < tied.size }
        return if (narrowed == null) tied[rng.nextInt(tied.size)]
        else breakTie(narrowed, criteria, table, rng)
    }

    private fun topByPct(pool: List<Int>, table: SeasonTable): List<Int> {
        val best = pool.maxOf { table.winPct(it) }
        return pool.filter { abs(table.winPct(it) - best) < EPS }
    }

    /** Full finishing order of a division, best first. */
    fun divisionOrder(division: Division, table: SeasonTable, rng: Random): List<Int> =
        generateSequence(League.division(division).map { it.id } to emptyList<Int>()) { (pool, order) ->
            val pick = breakTie(topByPct(pool, table), divisionCriteria, table, rng)
            (pool - pick) to (order + pick)
        }.first { it.first.isEmpty() }.second

    /** Picks the best club from [pool] using the wild-card procedure. */
    private fun pickWildCard(pool: List<Int>, divisionRank: Map<Int, Int>, table: SeasonTable, rng: Random): Int {
        val bestOfEachDivision = topByPct(pool, table)
            .groupBy { League.teams[it].division }
            .values.map { clubs -> clubs.minBy { divisionRank.getValue(it) } }
        return breakTie(bestOfEachDivision, wildCardCriteria, table, rng)
    }

    private fun rankPool(pool: List<Int>, count: Int, divisionRank: Map<Int, Int>, table: SeasonTable, rng: Random) =
        (0 until count).fold(pool to emptyList<Int>()) { (remaining, picked), _ ->
            val pick = pickWildCard(remaining, divisionRank, table, rng)
            (remaining - pick) to (picked + pick)
        }.second

    /** Seven seeds, index 0 = the #1 seed. Seeds 1-4 are division winners. */
    fun seeds(conference: Conference, table: SeasonTable, rng: Random): List<Int> {
        val orders = Division.entries.filter { it.conference == conference }.map { divisionOrder(it, table, rng) }
        val divisionRank = orders.flatMap { order -> order.mapIndexed { rank, team -> team to rank } }.toMap()
        val champions = orders.map { it.first() }
        val others = orders.flatMap { it.drop(1) }
        return rankPool(champions, champions.size, divisionRank, table, rng) +
            rankPool(others, SEEDS_PER_CONFERENCE - champions.size, divisionRank, table, rng)
    }
}
