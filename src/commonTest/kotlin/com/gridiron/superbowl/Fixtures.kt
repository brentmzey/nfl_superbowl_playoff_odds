package com.gridiron.superbowl

import com.gridiron.superbowl.model.Conference
import com.gridiron.superbowl.model.Game
import com.gridiron.superbowl.model.League
import com.gridiron.superbowl.model.SeasonPhase

object Fixtures {
    private var counter = 0

    fun game(
        home: String,
        away: String,
        homeScore: Int? = null,
        awayScore: Int? = null,
        week: Int = 1,
        neutral: Boolean = false,
        phase: SeasonPhase = SeasonPhase.REGULAR,
        season: Int = 2026,
    ) = Game(
        id = "g${counter++}",
        season = season,
        phase = phase,
        week = week,
        date = "$season-09-${(week + 9).toString().padStart(2, '0')}T17:00Z#${counter.toString().padStart(5, '0')}",
        home = home,
        away = away,
        neutral = neutral,
        homeScore = homeScore,
        awayScore = awayScore,
    )

    /** Each conference plays a single round robin (15 games per team); the first [playedWeeks] are final. */
    fun conferenceRoundRobin(playedWeeks: Int, season: Int = 2026): List<Game> =
        Conference.entries.flatMap { c ->
            val teams = League.conference(c)
            teams.flatMapIndexed { i, a ->
                teams.drop(i + 1).mapIndexed { j, b ->
                    val week = (i + j) % 15 + 1
                    val final = week <= playedWeeks
                    val strong = a.id % 3 == 0
                    game(
                        home = a.abbr, away = b.abbr,
                        homeScore = if (final) (if (strong) 27 else 17) else null,
                        awayScore = if (final) (if (strong) 20 else 24) else null,
                        week = week, season = season,
                    )
                }
            }
        }
}
