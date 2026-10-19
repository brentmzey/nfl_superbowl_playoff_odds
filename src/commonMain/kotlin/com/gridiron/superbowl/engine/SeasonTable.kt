package com.gridiron.superbowl.engine

import com.gridiron.superbowl.model.League

/**
 * Mutable, array-backed standings for one simulated season. Mutation is confined to the simulator's
 * inner loop; everything that leaves the engine is an immutable snapshot.
 */
class SeasonTable private constructor(
    private val wins: DoubleArray,
    private val games: IntArray,
    private val divWins: DoubleArray,
    private val divGames: IntArray,
    private val confWins: DoubleArray,
    private val confGames: IntArray,
    private val netPoints: IntArray,
    private val h2hWins: Array<DoubleArray>,
    private val h2hGames: Array<IntArray>,
) {
    constructor() : this(
        DoubleArray(N), IntArray(N), DoubleArray(N), IntArray(N), DoubleArray(N), IntArray(N), IntArray(N),
        Array(N) { DoubleArray(N) }, Array(N) { IntArray(N) },
    )

    fun copy(): SeasonTable = SeasonTable(
        wins.copyOf(), games.copyOf(), divWins.copyOf(), divGames.copyOf(), confWins.copyOf(), confGames.copyOf(),
        netPoints.copyOf(), Array(N) { h2hWins[it].copyOf() }, Array(N) { h2hGames[it].copyOf() },
    )

    fun record(home: Int, away: Int, homeMargin: Int) {
        val homeResult = when {
            homeMargin > 0 -> 1.0
            homeMargin < 0 -> 0.0
            else -> 0.5
        }
        val h = League.teams[home]
        val a = League.teams[away]
        tally(home, away, homeResult, homeMargin, h.division == a.division, h.conference == a.conference)
        tally(away, home, 1.0 - homeResult, -homeMargin, h.division == a.division, h.conference == a.conference)
    }

    private fun tally(team: Int, opp: Int, result: Double, margin: Int, sameDiv: Boolean, sameConf: Boolean) {
        wins[team] += result
        games[team] += 1
        netPoints[team] += margin
        h2hWins[team][opp] += result
        h2hGames[team][opp] += 1
        if (sameDiv) {
            divWins[team] += result
            divGames[team] += 1
        }
        if (sameConf) {
            confWins[team] += result
            confGames[team] += 1
        }
    }

    fun winPct(team: Int): Double = pct(wins[team], games[team])
    fun divisionPct(team: Int): Double = pct(divWins[team], divGames[team])
    fun conferencePct(team: Int): Double = pct(confWins[team], confGames[team])
    fun netPoints(team: Int): Int = netPoints[team]
    fun wins(team: Int): Double = wins[team]
    fun gamesPlayed(team: Int): Int = games[team]

    /** Head-to-head win pct of [team] against the other [group] members, or null if they have not all met. */
    fun headToHeadPct(team: Int, group: List<Int>): Double? {
        val opponents = group.filter { it != team }
        val allMet = opponents.all { h2hGames[team][it] > 0 }
        return if (!allMet) null
        else pct(opponents.sumOf { h2hWins[team][it] }, opponents.sumOf { h2hGames[team][it] })
    }

    private fun pct(w: Double, g: Int): Double = if (g == 0) 0.0 else w / g

    companion object {
        private val N = League.size
    }
}
