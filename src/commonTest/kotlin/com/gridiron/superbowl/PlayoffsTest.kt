package com.gridiron.superbowl

import com.gridiron.superbowl.engine.GamePlayer
import com.gridiron.superbowl.engine.Playoffs
import com.gridiron.superbowl.model.Round
import kotlin.test.Test
import kotlin.test.assertEquals

class PlayoffsTest {
    private val afc = listOf(10, 11, 12, 13, 14, 15, 16)
    private val nfc = listOf(20, 21, 22, 23, 24, 25, 26)

    @Test
    fun chalkBracketSendsTopSeedsThrough() {
        val calls = mutableListOf<Triple<Int, Int, Boolean>>()
        val chalk = GamePlayer { home, away, neutral -> calls += Triple(home, away, neutral); minOf(home, away) }
        val b = Playoffs.play(afc, nfc, chalk)
        assertEquals(10, b.afcChampion)
        assertEquals(20, b.nfcChampion)
        assertEquals(10, b.champion)
        assertEquals(Round.CHAMPION, b.reached[10])
        assertEquals(Round.SUPER_BOWL, b.reached[20])
        assertEquals(Round.CONFERENCE, b.reached[11])
        assertEquals(Round.DIVISIONAL, b.reached[13])
        assertEquals(Round.MAKE_PLAYOFFS, b.reached[16])
        assertEquals(14, b.reached.size)
        // 6 wild card + 4 divisional + 2 conference + 1 Super Bowl; only the Super Bowl is neutral.
        assertEquals(13, calls.size)
        assertEquals(listOf(true), calls.filter { it.third }.map { it.third })
        // #1 hosts the lowest remaining seed (#4) in the divisional round.
        assertEquals(true, Triple(10, 13, false) in calls)
    }

    @Test
    fun upsetsReseedTheDivisionalRound() {
        val calls = mutableListOf<Pair<Int, Int>>()
        val underdog = GamePlayer { home, away, _ -> calls += home to away; maxOf(home, away) }
        val b = Playoffs.play(afc, nfc, underdog)
        // All road teams win wild card: 7, 6, 5 advance. #1 hosts #7.
        assertEquals(true, (10 to 16) in calls)
        assertEquals(Round.DIVISIONAL, b.reached[10])
        assertEquals(26, b.champion)
    }
}
