package com.gridiron.superbowl

import com.gridiron.superbowl.model.Conference
import com.gridiron.superbowl.model.Division
import com.gridiron.superbowl.model.League
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class LeagueTest {
    @Test
    fun thirtyTwoTeamsInEightFourTeamDivisions() {
        assertEquals(32, League.size)
        assertEquals(32, League.teams.map { it.abbr }.toSet().size)
        assertEquals(League.teams.indices.toList(), League.teams.map { it.id })
        Division.entries.forEach { assertEquals(4, League.division(it).size) }
        Conference.entries.forEach { assertEquals(16, League.conference(it).size) }
        assertEquals(Conference.NFC, League.team("SF").conference)
    }

    @Test
    fun unknownTeams() {
        assertNull(League.find("AFC"))
        assertFailsWith<IllegalArgumentException> { League.team("XYZ") }
    }
}
