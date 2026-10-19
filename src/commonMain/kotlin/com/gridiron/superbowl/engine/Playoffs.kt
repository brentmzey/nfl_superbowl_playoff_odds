package com.gridiron.superbowl.engine

import com.gridiron.superbowl.model.Round

/** Result of one simulated postseason: furthest [Round] reached per team id, plus the finalists. */
data class Bracket(val reached: Map<Int, Round>, val champion: Int, val afcChampion: Int, val nfcChampion: Int)

/**
 * Plays a seeded game; the higher seed (lower index) hosts unless [neutral]. Returns the winner.
 */
fun interface GamePlayer {
    fun play(home: Int, away: Int, neutral: Boolean): Int
}

object Playoffs {

    /** Plays one conference bracket. [seeds] index 0 is the #1 seed. Returns team id -> furthest round. */
    fun conference(seeds: List<Int>, player: GamePlayer): Pair<Int, Map<Int, Round>> {
        val wildCardWinners = listOf(1 to 6, 2 to 5, 3 to 4).map { (hi, lo) -> winner(seeds, hi, lo, player) }
        val divisionalField = (listOf(0) + wildCardWinners).sorted()
        val divisionalWinners = listOf(
            winner(seeds, divisionalField[0], divisionalField[3], player),
            winner(seeds, divisionalField[1], divisionalField[2], player),
        ).sorted()
        val champSeed = winner(seeds, divisionalWinners[0], divisionalWinners[1], player)

        val reached = seeds.indices.associate { idx ->
            seeds[idx] to when (idx) {
                champSeed -> Round.SUPER_BOWL
                in divisionalWinners -> Round.CONFERENCE
                in divisionalField -> Round.DIVISIONAL
                else -> Round.MAKE_PLAYOFFS
            }
        }
        return seeds[champSeed] to reached
    }

    private fun winner(seeds: List<Int>, higher: Int, lower: Int, player: GamePlayer): Int =
        if (player.play(seeds[higher], seeds[lower], neutral = false) == seeds[higher]) higher else lower

    fun play(afcSeeds: List<Int>, nfcSeeds: List<Int>, player: GamePlayer): Bracket {
        val (afc, afcReached) = conference(afcSeeds, player)
        val (nfc, nfcReached) = conference(nfcSeeds, player)
        val champion = player.play(afc, nfc, neutral = true)
        return Bracket(afcReached + nfcReached + (champion to Round.CHAMPION), champion, afc, nfc)
    }
}
