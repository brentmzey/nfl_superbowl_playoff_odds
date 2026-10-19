package com.gridiron.superbowl.model

import com.gridiron.superbowl.model.Division.AFC_EAST
import com.gridiron.superbowl.model.Division.AFC_NORTH
import com.gridiron.superbowl.model.Division.AFC_SOUTH
import com.gridiron.superbowl.model.Division.AFC_WEST
import com.gridiron.superbowl.model.Division.NFC_EAST
import com.gridiron.superbowl.model.Division.NFC_NORTH
import com.gridiron.superbowl.model.Division.NFC_SOUTH
import com.gridiron.superbowl.model.Division.NFC_WEST

/** The 32-team league, keyed by ESPN abbreviations. Ids are dense 0..31 for array-backed simulation. */
object League {
    val teams: List<Team> = listOf(
        "BUF" to "Buffalo Bills" to AFC_EAST, "MIA" to "Miami Dolphins" to AFC_EAST,
        "NE" to "New England Patriots" to AFC_EAST, "NYJ" to "New York Jets" to AFC_EAST,
        "BAL" to "Baltimore Ravens" to AFC_NORTH, "CIN" to "Cincinnati Bengals" to AFC_NORTH,
        "CLE" to "Cleveland Browns" to AFC_NORTH, "PIT" to "Pittsburgh Steelers" to AFC_NORTH,
        "HOU" to "Houston Texans" to AFC_SOUTH, "IND" to "Indianapolis Colts" to AFC_SOUTH,
        "JAX" to "Jacksonville Jaguars" to AFC_SOUTH, "TEN" to "Tennessee Titans" to AFC_SOUTH,
        "DEN" to "Denver Broncos" to AFC_WEST, "KC" to "Kansas City Chiefs" to AFC_WEST,
        "LV" to "Las Vegas Raiders" to AFC_WEST, "LAC" to "Los Angeles Chargers" to AFC_WEST,
        "DAL" to "Dallas Cowboys" to NFC_EAST, "NYG" to "New York Giants" to NFC_EAST,
        "PHI" to "Philadelphia Eagles" to NFC_EAST, "WSH" to "Washington Commanders" to NFC_EAST,
        "CHI" to "Chicago Bears" to NFC_NORTH, "DET" to "Detroit Lions" to NFC_NORTH,
        "GB" to "Green Bay Packers" to NFC_NORTH, "MIN" to "Minnesota Vikings" to NFC_NORTH,
        "ATL" to "Atlanta Falcons" to NFC_SOUTH, "CAR" to "Carolina Panthers" to NFC_SOUTH,
        "NO" to "New Orleans Saints" to NFC_SOUTH, "TB" to "Tampa Bay Buccaneers" to NFC_SOUTH,
        "ARI" to "Arizona Cardinals" to NFC_WEST, "LAR" to "Los Angeles Rams" to NFC_WEST,
        "SF" to "San Francisco 49ers" to NFC_WEST, "SEA" to "Seattle Seahawks" to NFC_WEST,
    ).mapIndexed { i, (abbrName, div) -> Team(i, abbrName.first, abbrName.second, div) }

    private val byAbbr: Map<String, Team> = teams.associateBy { it.abbr }

    fun find(abbr: String): Team? = byAbbr[abbr]

    fun team(abbr: String): Team = find(abbr) ?: throw IllegalArgumentException("Unknown team: $abbr")

    fun division(division: Division): List<Team> = teams.filter { it.division == division }

    fun conference(conference: Conference): List<Team> = teams.filter { it.conference == conference }

    val size: Int get() = teams.size
}
