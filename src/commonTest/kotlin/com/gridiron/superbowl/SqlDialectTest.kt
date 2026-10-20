package com.gridiron.superbowl

import com.gridiron.superbowl.sync.PocketBaseSchema
import com.gridiron.superbowl.sync.SqlDialect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SqlDialectTest {
    @Test
    fun tablesCarryPrimaryKeysNotNullsAndForeignKeys() {
        val pg = SqlDialect.POSTGRES.createTable(PocketBaseSchema.outlooks)
        assertTrue(pg.startsWith("CREATE TABLE \"o_team_outlooks\" (\n  \"id\" TEXT PRIMARY KEY"))
        assertTrue(pg.contains("\"str_run_id\" TEXT NOT NULL"))
        assertTrue(pg.contains("\"dbl_champion_prob\" DOUBLE PRECISION,"))
        assertTrue(pg.contains("\"int_wins\" BIGINT,"))
        assertTrue(pg.contains("\"json_seed_odds\" JSONB"))
        assertTrue(pg.contains("\"rel_run\" TEXT NOT NULL REFERENCES \"m_simulation_runs\" (\"id\")"))
        val lite = SqlDialect.SQLITE.createTable(PocketBaseSchema.games)
        assertTrue(lite.contains("\"bool_is_final\" INTEGER") && lite.contains("\"int_week\" INTEGER") && lite.contains("\"rel_home_team\" TEXT NOT NULL REFERENCES \"i_nfl_teams\""))
        assertTrue(SqlDialect.SQLITE.createTable(PocketBaseSchema.outlooks).contains("\"json_seed_odds\" TEXT") )
        assertTrue(SqlDialect.SQLITE.createTable(PocketBaseSchema.outlooks).contains("\"dbl_elo_rating\" REAL"))
        assertTrue(SqlDialect.POSTGRES.createTable(PocketBaseSchema.games).contains("\"bool_is_final\" BOOLEAN"))
    }

    @Test
    fun ddlOrderAndInserts() {
        val ddl = SqlDialect.SQLITE.ddl(PocketBaseSchema.collections, PocketBaseSchema.views)
        val tables = ddl.takeWhile { it.startsWith("CREATE TABLE") }
        assertEquals(PocketBaseSchema.collections.size, tables.size)
        assertEquals(PocketBaseSchema.collections.sumOf { it.indexes.size }, ddl.count { it.contains(" INDEX ") })
        assertTrue(ddl.last().startsWith("CREATE VIEW \"s_latest_team_outlooks\" AS\nSELECT"))
        assertTrue(ddl.contains("CREATE INDEX \"ix_i_nfl_games__season_year_kickoff_utc\" ON \"i_nfl_games\" (\"int_season_year\", \"dt_kickoff_utc\") WHERE bool_is_final = FALSE"))
        assertTrue(SqlDialect.POSTGRES.insert(PocketBaseSchema.outlooks).contains("CAST(? AS JSONB)"))
        assertTrue(!SqlDialect.SQLITE.insert(PocketBaseSchema.outlooks).contains("CAST"))
        assertEquals(3 + PocketBaseSchema.outlooks.fields.size + 2, SqlDialect.SQLITE.columns(PocketBaseSchema.outlooks).size)
    }
}
