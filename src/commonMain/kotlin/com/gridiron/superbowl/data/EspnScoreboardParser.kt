package com.gridiron.superbowl.data

import com.gridiron.superbowl.model.Game
import com.gridiron.superbowl.model.League
import com.gridiron.superbowl.model.SeasonPhase
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Parses ESPN's public scoreboard payload
 * (`site.api.espn.com/apis/site/v2/sports/football/nfl/scoreboard`). Events involving clubs outside the
 * 32-team league (e.g. the Pro Bowl) are dropped; scores are only kept once ESPN marks the game completed.
 */
object EspnScoreboardParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(payload: String): List<Game> =
        (json.parseToJsonElement(payload).obj("events") as? JsonArray).orEmpty().mapNotNull(::event)

    private fun JsonElement?.obj(key: String): JsonElement? = (this as? JsonObject)?.get(key)
    private fun JsonElement?.str(key: String): String? = obj(key)?.jsonPrimitive?.content
    private fun JsonElement?.int(key: String): Int? = obj(key)?.jsonPrimitive?.intOrNull

    private fun event(e: JsonElement): Game? {
        val competition = e.obj("competitions")?.jsonArray?.firstOrNull() ?: return null
        val competitors = competition.obj("competitors")?.jsonArray.orEmpty()
        val home = competitors.firstOrNull { it.str("homeAway") == "home" }
        val away = competitors.firstOrNull { it.str("homeAway") == "away" }
        val homeAbbr = home.obj("team").str("abbreviation")?.takeIf { League.find(it) != null }
        val awayAbbr = away.obj("team").str("abbreviation")?.takeIf { League.find(it) != null }
        val completed = competition.obj("status").obj("type")?.jsonObject?.get("completed")
            ?.jsonPrimitive?.booleanOrNull == true
        val phase = when (e.obj("season").int("type")) {
            2 -> SeasonPhase.REGULAR
            3 -> SeasonPhase.POSTSEASON
            else -> null
        }
        return if (homeAbbr == null || awayAbbr == null || phase == null) null
        else Game(
            id = e.str("id").orEmpty(),
            season = e.obj("season").int("year") ?: 0,
            phase = phase,
            week = e.obj("week").int("number") ?: 0,
            date = e.str("date").orEmpty(),
            home = homeAbbr,
            away = awayAbbr,
            neutral = competition.obj("neutralSite")?.jsonPrimitive?.booleanOrNull == true,
            homeScore = home.str("score")?.toIntOrNull()?.takeIf { completed },
            awayScore = away.str("score")?.toIntOrNull()?.takeIf { completed },
        )
    }
}
