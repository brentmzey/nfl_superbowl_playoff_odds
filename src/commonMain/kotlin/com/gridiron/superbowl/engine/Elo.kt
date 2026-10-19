package com.gridiron.superbowl.engine

import com.gridiron.superbowl.model.Game
import com.gridiron.superbowl.model.League
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow

/**
 * Margin-of-victory Elo in the style popularised by FiveThirtyEight's NFL model.
 *
 * - Expected score: `1 / (1 + 10^(-diff/400))`, where `diff` includes home-field advantage.
 * - Update: `K * movMultiplier * (actual - expected)`; the multiplier dampens blowouts by favourites
 *   so ratings do not run away from autocorrelation.
 * - Between seasons every team is regressed a third of the way back to the league mean.
 */
data class EloParams(
    val k: Double = 20.0,
    val homeField: Double = 48.0,
    val mean: Double = 1505.0,
    val seasonRegression: Double = 1.0 / 3.0,
    val eloPerPoint: Double = 25.0,
)

/** Immutable ratings indexed by [com.gridiron.superbowl.model.Team.id]. */
data class Ratings(val values: List<Double>) {
    operator fun get(abbr: String): Double = values[League.team(abbr).id]
    operator fun get(id: Int): Double = values[id]

    companion object {
        fun uniform(value: Double): Ratings = Ratings(List(League.size) { value })
    }
}

class EloModel(val params: EloParams = EloParams()) {

    fun expected(ratingDiff: Double): Double = 1.0 / (1.0 + 10.0.pow(-ratingDiff / 400.0))

    /** Home rating edge including home field (zero at neutral sites). */
    fun homeEdge(home: Double, away: Double, neutral: Boolean): Double =
        home - away + if (neutral) 0.0 else params.homeField

    /** Projected home point spread (positive = home favoured). */
    fun spread(edge: Double): Double = edge / params.eloPerPoint

    fun movMultiplier(margin: Int, winnerEdge: Double): Double =
        if (margin == 0) 1.0 else ln(abs(margin) + 1.0) * (2.2 / (winnerEdge * 0.001 + 2.2))

    /** Rating points moved from away to home by a result with the given home [margin]. */
    fun delta(edge: Double, margin: Int): Double {
        val actual = when {
            margin > 0 -> 1.0
            margin < 0 -> 0.0
            else -> 0.5
        }
        val winnerEdge = if (margin >= 0) edge else -edge
        return params.k * movMultiplier(margin, winnerEdge) * (actual - expected(edge))
    }

    /** Applies one result in place; used on the hot path of the simulator. */
    fun applyInPlace(ratings: DoubleArray, home: Int, away: Int, neutral: Boolean, margin: Int) {
        val d = delta(homeEdge(ratings[home], ratings[away], neutral), margin)
        ratings[home] += d
        ratings[away] -= d
    }

    fun update(ratings: Ratings, game: Game): Ratings =
        game.homeMargin?.let { margin ->
            val arr = ratings.values.toDoubleArray()
            applyInPlace(arr, League.team(game.home).id, League.team(game.away).id, game.neutral, margin)
            Ratings(arr.toList())
        } ?: ratings

    fun regressToMean(ratings: Ratings): Ratings =
        Ratings(ratings.values.map { it + (params.mean - it) * params.seasonRegression })

    /** Folds final games in kick-off order over a starting rating set. */
    fun rate(start: Ratings, games: List<Game>): Ratings =
        games.filter { it.isFinal }.sortedBy { it.date }.fold(start) { r, g -> update(r, g) }

    /** Prior season (regular + postseason) -> regression -> current season to date. */
    fun rateSeasons(priorSeason: List<Game>, currentSeason: List<Game>): Ratings =
        rate(regressToMean(rate(Ratings.uniform(params.mean), priorSeason)), currentSeason)
}
