package com.gridiron.superbowl.model

import kotlinx.serialization.Serializable

enum class Conference { AFC, NFC }

enum class Division(val conference: Conference, val label: String) {
    AFC_EAST(Conference.AFC, "AFC East"),
    AFC_NORTH(Conference.AFC, "AFC North"),
    AFC_SOUTH(Conference.AFC, "AFC South"),
    AFC_WEST(Conference.AFC, "AFC West"),
    NFC_EAST(Conference.NFC, "NFC East"),
    NFC_NORTH(Conference.NFC, "NFC North"),
    NFC_SOUTH(Conference.NFC, "NFC South"),
    NFC_WEST(Conference.NFC, "NFC West"),
}

data class Team(val id: Int, val abbr: String, val name: String, val division: Division) {
    val conference: Conference get() = division.conference
}

enum class SeasonPhase { REGULAR, POSTSEASON }

/** One scheduled or completed game. Scores are null until the game is final. */
@Serializable
data class Game(
    val id: String,
    val season: Int,
    val phase: SeasonPhase,
    val week: Int,
    val date: String,
    val home: String,
    val away: String,
    val neutral: Boolean,
    val homeScore: Int? = null,
    val awayScore: Int? = null,
) {
    val isFinal: Boolean get() = homeScore != null && awayScore != null

    /** Home margin of victory, defined only for final games. */
    val homeMargin: Int? get() = homeScore?.let { h -> awayScore?.let { a -> h - a } }
}

/** Playoff rounds a team can reach, in order. */
enum class Round(val label: String) {
    MAKE_PLAYOFFS("Make playoffs"),
    DIVISIONAL("Divisional"),
    CONFERENCE("Conf. title game"),
    SUPER_BOWL("Super Bowl"),
    CHAMPION("Win Super Bowl"),
}
