package com.gridiron.superbowl

import com.gridiron.superbowl.data.EspnScoreboardParser
import com.gridiron.superbowl.model.SeasonPhase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EspnScoreboardParserTest {
    private fun event(id: String, type: Int, home: String, away: String, hs: String, `as`: String, completed: Boolean, neutral: Boolean = false) = """
        {"id":"$id","date":"2026-10-18T17:00Z","season":{"year":2026,"type":$type},"week":{"number":6},
         "competitions":[{"neutralSite":$neutral,"status":{"type":{"completed":$completed}},
           "competitors":[
             {"homeAway":"home","score":"$hs","team":{"abbreviation":"$home"}},
             {"homeAway":"away","score":"$`as`","team":{"abbreviation":"$away"}}]}]}
    """.trimIndent()

    @Test
    fun parsesFinalsScheduledAndPostseasonAndDropsOthers() {
        val payload = """{"events":[
            ${event("1", 2, "GB", "DAL", "23", "13", true)},
            ${event("2", 2, "SF", "WSH", "0", "0", false)},
            ${event("3", 3, "NE", "SEA", "13", "29", true, neutral = true)},
            ${event("4", 3, "AFC", "NFC", "30", "20", true)},
            ${event("5", 1, "KC", "LV", "10", "3", true)},
            {"id":"6"}
        ]}"""
        val games = EspnScoreboardParser.parse(payload)
        assertEquals(listOf("1", "2", "3"), games.map { it.id })
        val final = games[0]
        assertEquals(10, final.homeMargin)
        assertEquals(6, final.week)
        assertEquals(2026, final.season)
        assertEquals(SeasonPhase.REGULAR, final.phase)
        assertTrue(final.isFinal)
        assertNull(games[1].homeScore)
        assertNull(games[1].homeMargin)
        assertEquals(SeasonPhase.POSTSEASON, games[2].phase)
        assertTrue(games[2].neutral)
    }

    @Test
    fun emptyOrMissingEvents() {
        assertEquals(emptyList(), EspnScoreboardParser.parse("""{"events":[]}"""))
        assertEquals(emptyList(), EspnScoreboardParser.parse("""{}"""))
    }

    @Test
    fun toleratesMalformedEvents() {
        val payload = """{"events":[
            {"id":"a","competitions":[]},
            {"id":"b","season":{"type":2},"competitions":[{"competitors":[{"homeAway":"home","team":{"abbreviation":"GB"}}]}]},
            {"id":"c","season":{"type":2},"competitions":[{"competitors":[{"homeAway":"home"},{"homeAway":"away","team":{}}]}]},
            {"season":{"type":2},"competitions":[{"status":{},"competitors":[
              {"homeAway":"home","score":"x","team":{"abbreviation":"GB"}},
              {"homeAway":"away","team":{"abbreviation":"CHI"}}]}]},
            {"id":"e","season":{"type":2},"competitions":[{"neutralSite":"maybe","status":{"type":{"completed":true}},"competitors":[
              {"homeAway":"home","score":"x","team":{"abbreviation":"GB"}},
              {"homeAway":"away","team":{"abbreviation":"CHI"}}]}]},
            {"id":"f","competitions":[{"competitors":[
              {"homeAway":"home","team":{"abbreviation":"GB"}},{"homeAway":"away","team":{"abbreviation":"CHI"}}]}]}
        ]}"""
        val games = EspnScoreboardParser.parse(payload)
        assertEquals(2, games.size)
        games.forEach { g ->
            assertEquals(0, g.week)
            assertEquals(0, g.season)
            assertEquals("", g.date)
            assertEquals(false, g.neutral)
            assertNull(g.homeScore)
            assertNull(g.awayScore)
        }
        assertEquals("", games[0].id)
    }
}
