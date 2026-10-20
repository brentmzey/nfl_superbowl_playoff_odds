package com.gridiron.superbowl.replica

import com.gridiron.superbowl.model.Conference
import com.gridiron.superbowl.model.SeasonPhase
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.jdbi.v3.core.Jdbi
import org.jdbi.v3.core.kotlin.KotlinPlugin
import org.jdbi.v3.core.mapper.ColumnMapper
import org.jdbi.v3.core.statement.StatementContext
import org.jdbi.v3.sqlobject.kotlin.KotlinSqlObjectPlugin
import org.jdbi.v3.sqlobject.kotlin.onDemand
import org.jdbi.v3.sqlobject.statement.SqlQuery
import java.nio.file.Path
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant

/** Record lifecycle status shared by every table. */
enum class RecordStatus { ACTIVE, INACTIVE, SUPERSEDED, ARCHIVED }

/** `json_seed_odds`: P(seed 1..7) then P(miss playoffs). */
data class SeedOdds(val probabilities: List<Double>)

data class LeaderboardRow(
    val strTeamCode: String,
    val strTeamName: String,
    val strConference: Conference,
    val strDivision: String,
    val strRunId: String,
    val dtRunDate: Instant,
    val intSeasonYear: Int,
    val intThroughWeek: Int,
    val intWins: Int,
    val intLosses: Int,
    val intTies: Int,
    val dblEloRating: Double,
    val dblProjectedWins: Double,
    val dblPlayoffProb: Double,
    val dblSuperbowlProb: Double,
    val dblChampionProb: Double,
    val intChampionRank: Int,
    val jsonSeedOdds: SeedOdds,
)

data class SimulationRunRow(
    val strRunId: String,
    val dtRunDate: Instant,
    val intSeasonYear: Int,
    val intThroughWeek: Int,
    val intTotalIterations: Int,
    val strTopFavoriteCode: String,
    val dblTopFavoriteProb: Double,
    val strStatusCode: RecordStatus,
    val created: Instant?,
)

data class TeamOutlookRow(
    val strRunId: String,
    val strTeamCode: String,
    val dtRunDate: Instant,
    val dblEloRating: Double,
    val dblPlayoffProb: Double,
    val dblChampionProb: Double,
    val intChampionRank: Int,
)

data class MatchupRow(val strAfcCode: String, val strNfcCode: String, val intRank: Int, val dblProbability: Double)

data class GameRow(
    val strGameId: String,
    val strPhase: SeasonPhase,
    val intWeek: Int,
    val dtKickoffUtc: Instant?,
    val strHomeCode: String,
    val strAwayCode: String,
    val boolIsFinal: Boolean,
    val intHomeScore: Int?,
    val intAwayScore: Int?,
)

/**
 * JDBI SqlObject query templates over the SQLite / PostgreSQL replicas. Named `:params` are bound by JDBI (never
 * concatenated); rows map onto typed Kotlin data classes (snake_case columns -> camelCase properties, text -> enums,
 * timestamps -> [Instant], JSON -> [SeedOdds]). The SQL is portable across both dialects.
 */
interface ReplicaQueries {
    @SqlQuery("SELECT * FROM s_latest_team_outlooks WHERE int_season_year = :season ORDER BY int_champion_rank")
    fun leaderboard(season: Int): List<LeaderboardRow>

    @SqlQuery(
        "SELECT * FROM s_latest_team_outlooks WHERE int_season_year = :season AND str_conference = :conference " +
            "ORDER BY int_champion_rank",
    )
    fun conferenceLeaderboard(season: Int, conference: Conference): List<LeaderboardRow>

    @SqlQuery("SELECT * FROM m_simulation_runs WHERE int_season_year = :season AND str_status_code = :status ORDER BY created DESC, str_run_id DESC")
    fun runs(season: Int, status: RecordStatus): List<SimulationRunRow>

    @SqlQuery(
        "SELECT * FROM m_simulation_runs WHERE int_season_year = :season AND str_status_code = 'ACTIVE' " +
            "ORDER BY dt_run_date DESC, created DESC LIMIT 1",
    )
    fun latestRun(season: Int): SimulationRunRow?

    @SqlQuery(
        "SELECT * FROM o_team_outlooks WHERE str_team_code = :team AND int_season_year = :season " +
            "ORDER BY dt_run_date, created",
    )
    fun teamTrend(team: String, season: Int): List<TeamOutlookRow>

    @SqlQuery("SELECT * FROM o_superbowl_matchups WHERE str_run_id = :runId ORDER BY int_rank LIMIT :limit")
    fun topMatchups(runId: String, limit: Int): List<MatchupRow>

    @SqlQuery(
        "SELECT * FROM i_nfl_games WHERE int_season_year = :season AND str_phase = :phase AND int_week = :week " +
            "ORDER BY dt_kickoff_utc, str_game_id",
    )
    fun weekGames(season: Int, phase: SeasonPhase, week: Int): List<GameRow>

    @SqlQuery(
        "SELECT * FROM i_nfl_games WHERE int_season_year = :season AND (str_home_code = :team OR str_away_code = :team) " +
            "ORDER BY dt_kickoff_utc, str_game_id",
    )
    fun teamGames(team: String, season: Int): List<GameRow>

    @SqlQuery("SELECT COUNT(*) FROM i_nfl_games WHERE int_season_year = :season AND bool_is_final = FALSE")
    fun unplayedGames(season: Int): Int
}

/** Maps both dialects' timestamps (SQLite PocketBase-form text, PostgreSQL TIMESTAMPTZ) to [Instant]. */
private object InstantMapper : ColumnMapper<Instant?> {
    override fun map(r: ResultSet, columnNumber: Int, ctx: StatementContext): Instant? =
        when (val v = r.getObject(columnNumber)) {
            null -> null
            is Timestamp -> v.toInstant()
            else -> Instant.parse(v.toString().replace(' ', 'T'))
        }
}

private object SeedOddsMapper : ColumnMapper<SeedOdds> {
    override fun map(r: ResultSet, columnNumber: Int, ctx: StatementContext): SeedOdds =
        SeedOdds(Json.parseToJsonElement(r.getString(columnNumber)).jsonArray.map { it.jsonPrimitive.content.toDouble() })
}

object ReplicaDb {
    private fun configure(jdbi: Jdbi): Jdbi = jdbi
        .installPlugin(KotlinPlugin())
        .installPlugin(KotlinSqlObjectPlugin())
        .registerColumnMapper(Instant::class.java, InstantMapper)
        .registerColumnMapper(SeedOdds::class.java, SeedOddsMapper)

    fun sqlite(file: Path): Jdbi = configure(Jdbi.create("jdbc:sqlite:$file"))

    /** Connects with `currentSchema` so the unqualified template SQL resolves inside the project schema. */
    fun postgres(cfg: PostgresConfig): Jdbi = configure(Jdbi.create("${cfg.url(cfg.database)}?currentSchema=${cfg.schema}", cfg.user, cfg.password))

    fun queries(jdbi: Jdbi): ReplicaQueries = jdbi.onDemand<ReplicaQueries>()
}
