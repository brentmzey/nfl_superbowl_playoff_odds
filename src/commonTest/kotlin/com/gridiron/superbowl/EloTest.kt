package com.gridiron.superbowl

import com.gridiron.superbowl.engine.EloModel
import com.gridiron.superbowl.engine.EloParams
import com.gridiron.superbowl.engine.Ratings
import com.gridiron.superbowl.model.League
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EloTest {
    private val elo = EloModel()

    @Test
    fun expectedIsLogisticAndSymmetric() {
        assertEquals(0.5, elo.expected(0.0), 1e-12)
        assertEquals(1.0, elo.expected(100.0) + elo.expected(-100.0), 1e-12)
        assertEquals(0.909, elo.expected(400.0), 1e-3)
    }

    @Test
    fun homeFieldAppliesOnlyAwayFromNeutralSites() {
        assertEquals(48.0, elo.homeEdge(1500.0, 1500.0, neutral = false))
        assertEquals(0.0, elo.homeEdge(1500.0, 1500.0, neutral = true))
        assertEquals(2.0, elo.spread(50.0))
    }

    @Test
    fun deltaDirectionAndMovDamping() {
        assertTrue(elo.delta(0.0, 7) > 0)
        assertTrue(elo.delta(0.0, -7) < 0)
        assertTrue(elo.delta(0.0, 21) > elo.delta(0.0, 3))
        // A big favourite winning by a lot moves less than an even game won by the same margin.
        assertTrue(elo.delta(300.0, 21) < elo.delta(0.0, 21))
        // Ties pull the favourite down and the underdog up.
        assertTrue(elo.delta(100.0, 0) < 0)
        assertEquals(1.0, elo.movMultiplier(0, 0.0))
    }

    @Test
    fun updateIsZeroSumAndIgnoresUnplayedGames() {
        val start = Ratings.uniform(1500.0)
        val after = elo.update(start, Fixtures.game("KC", "BUF", 30, 10))
        assertTrue(after["KC"] > 1500.0)
        assertEquals(3000.0, after["KC"] + after["BUF"], 1e-9)
        assertEquals(start, elo.update(start, Fixtures.game("KC", "BUF")))
    }

    @Test
    fun regressionAndSeasonChaining() {
        val custom = EloModel(EloParams(mean = 1500.0, seasonRegression = 0.5))
        val r = Ratings(List(League.size) { if (it == 0) 1600.0 else 1500.0 })
        assertEquals(1550.0, custom.regressToMean(r)[0], 1e-9)

        val prior = listOf(Fixtures.game("SF", "SEA", 40, 0, season = 2025))
        val current = listOf(Fixtures.game("SEA", "SF", 35, 3), Fixtures.game("SEA", "SF"))
        val rated = elo.rateSeasons(prior, current)
        assertTrue(rated["SEA"] > 1505.0 - 1e-9 || rated["SF"] < 1600.0)
        assertEquals(1505.0 * League.size, rated.values.sum(), 1e-6)
    }
}
