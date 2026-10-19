package com.gridiron.superbowl

import com.gridiron.superbowl.data.EspnHttpSource
import com.gridiron.superbowl.data.EspnScoreboardParser
import kotlin.test.Test
import kotlin.test.assertTrue

/** Hits the real ESPN API. Opt in with `NFL_LIVE=1 ./gradlew jvmTest` so CI stays hermetic. */
class LiveEspnTest {
    @Test
    fun liveScoreboardParses() {
        if (System.getenv("NFL_LIVE") != "1") return
        val games = EspnScoreboardParser.parse(EspnHttpSource().fetch(2026, 2, 1).getOrThrow())
        assertTrue(games.size in 14..16)
        assertTrue(games.all { it.isFinal })
    }
}
