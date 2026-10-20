package com.gridiron.superbowl

import com.gridiron.superbowl.app.Pipeline
import com.gridiron.superbowl.data.ScoreboardSource
import com.gridiron.superbowl.replica.BackupStore
import com.gridiron.superbowl.replica.DbFactory
import com.gridiron.superbowl.replica.PocketHostSnapshot
import com.gridiron.superbowl.replica.PostgresConfig
import com.gridiron.superbowl.replica.PostgresReplica
import com.gridiron.superbowl.replica.SqliteReplica
import com.gridiron.superbowl.sync.Backoff
import com.gridiron.superbowl.sync.PocketBaseClient
import com.gridiron.superbowl.sync.PocketHostConfig
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.net.Socket
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager
import java.sql.SQLException
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * PocketHost (fake) -> backup -> SQLite -> PostgreSQL, using the real JDBC drivers. The PostgreSQL cases run
 * against `~/personal/local-db-stack` (localhost:15432; start it with `just db-up`) and are skipped when it is down.
 */
class ReplicationTest {
    private val pb = FakePocketBase()
    private val root = Files.createTempDirectory("replica")
    private val client = PocketBaseClient(PocketHostConfig(pb.url, "admin@example.com", "s3cret"), backoff = Backoff(attempts = 1)).also {
        it.sync(ReportsTest.report, ReportsTest.season, 1_000)
    }
    private val snapshot = PocketHostSnapshot.pull(client, pb.url, Instant.parse("2026-10-19T18:00:00Z"))
    private val pg = PostgresConfig.resolve(mapOf("NFL_PG_DB" to "nfl_superbowl_playoff_odds_test", "NFL_PG_SCHEMA" to "nfl_test"), emptyList())
    private val pgUp = runCatching { Socket(pg.host, pg.port).close() }.isSuccess

    @AfterTest
    fun stop() = pb.close()

    private fun expectedCounts() = PocketHostSnapshot.collections.associate { it.name to snapshot.count(it.name) }

    @Test
    fun snapshotPullsEveryCollectionInDependencyOrder() {
        assertEquals(listOf("tbl_schema_migrations", "i_nfl_teams", "i_nfl_games", "m_simulation_runs", "o_team_outlooks", "o_superbowl_matchups"), snapshot.rows.keys.toList())
        assertEquals(32, snapshot.count("i_nfl_teams"))
        assertEquals(ReportsTest.season.size, snapshot.count("i_nfl_games"))
        assertEquals(4, snapshot.count("tbl_schema_migrations"))
    }

    @Test
    fun backupsRoundTripAndArePruned() {
        val store = BackupStore(root.resolve("backups"), keep = 2)
        val first = store.write(snapshot)
        assertEquals("20261019T180000Z", first.fileName.toString())
        assertTrue(Files.readString(first.resolve("manifest.json")).contains("\"o_team_outlooks\": 32"))
        val restored = store.read(first)
        assertEquals(snapshot, restored)
        listOf(1L, 2L).forEach { store.write(snapshot.copy(takenAt = snapshot.takenAt.plusSeconds(it))) }
        assertEquals(listOf("20261019T180001Z", "20261019T180002Z"), store.list().map { it.fileName.toString() })
        Files.delete(store.list().first().resolve("o_superbowl_matchups.json"))
        assertEquals(0, store.read(store.list().first()).count("o_superbowl_matchups"))
        assertEquals(emptyList(), BackupStore(root.resolve("none")).list())
    }

    @Test
    fun sqliteReplicaIsRebuiltWithForeignKeysIndexesAndView() {
        val file = root.resolve("db/nfl.sqlite")
        assertEquals(expectedCounts(), SqliteReplica.rebuild(file, snapshot))
        assertEquals(expectedCounts(), SqliteReplica.rebuild(file, snapshot)) // from scratch again, no duplicates
        DriverManager.getConnection("jdbc:sqlite:$file").use { conn ->
            conn.createStatement().execute("PRAGMA foreign_keys = ON")
            fun one(sql: String) = conn.createStatement().executeQuery(sql).use { it.next(); it.getString(1) }
            assertEquals("32", one("SELECT COUNT(*) FROM s_latest_team_outlooks"))
            assertEquals(ReportsTest.report.byChampionOdds.first().abbr, one("SELECT str_team_code FROM s_latest_team_outlooks ORDER BY int_champion_rank LIMIT 1"))
            val indexes = one("SELECT COUNT(*) FROM sqlite_master WHERE type = 'index' AND name LIKE 'uq_%' OR name LIKE 'ix_%'").toInt()
            assertEquals(PocketHostSnapshot.collections.sumOf { it.indexes.size }, indexes)
            assertEquals("1", one("SELECT COUNT(*) FROM i_nfl_games WHERE bool_is_final = FALSE AND str_game_id = '${ReportsTest.season.first { !it.isFinal }.id}'"))
            assertFailsWith<SQLException> {
                conn.createStatement().execute("INSERT INTO o_team_outlooks (id, str_run_id, str_team_code, dt_run_date, str_status_code, rel_run, rel_team) VALUES ('x','r','KC','d','ACTIVE','missing','missing')")
            }
        }
    }

    @Test
    fun postgresReplicaUsesDbFactoryAndRebuildsTheSchema() {
        if (!pgUp) return
        DriverManager.getConnection(pg.url("postgres"), pg.user, pg.password).use { it.createStatement().execute("DROP DATABASE IF EXISTS ${pg.database} WITH (FORCE)") }
        val first = PostgresReplica.rebuild(pg, snapshot)
        assertTrue(first.created)
        assertEquals(expectedCounts(), first.counts)
        val again = PostgresReplica.rebuild(pg, snapshot)
        assertFalse(again.created)
        DriverManager.getConnection(pg.url(pg.database), pg.user, pg.password).use { conn ->
            fun one(sql: String) = conn.createStatement().executeQuery(sql).use { it.next(); it.getString(1) }
            assertEquals("32", one("SELECT COUNT(*) FROM nfl_test.s_latest_team_outlooks"))
            assertEquals("en_US.UTF-8", one("SELECT datcollate FROM pg_database WHERE datname = '${pg.database}'"))
            assertEquals("8", one("SELECT jsonb_array_length(json_seed_odds) FROM nfl_test.o_team_outlooks LIMIT 1"))
            assertEquals("7", one("SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema = 'nfl_test' AND constraint_type = 'FOREIGN KEY'"))
            assertEquals("boolean", one("SELECT data_type FROM information_schema.columns WHERE table_schema = 'nfl_test' AND table_name = 'i_nfl_games' AND column_name = 'bool_is_final'"))
            assertFailsWith<SQLException> {
                conn.createStatement().execute("INSERT INTO nfl_test.o_team_outlooks (id, str_run_id, str_team_code, dt_run_date, str_status_code, rel_run, rel_team) VALUES ('x','r','KC','d','ACTIVE','missing','missing')")
            }
        }
    }

    @Test
    fun pipelineReplicateWritesBackupSqliteAndReportsPostgresOutcome() {
        val pipeline = Pipeline(ScoreboardSource { _, _, _ -> Result.success("{}") }, root)
        val unreachable = pg.copy(port = 1)
        val summary = pipeline.replicate(client, pb.url, unreachable, snapshot.takenAt).getOrThrow()
        assertTrue(Files.exists(summary.backup.resolve("manifest.json")))
        assertEquals(root.resolve("output/db/nfl_superbowl_playoff_odds.sqlite"), summary.sqlite)
        assertEquals(expectedCounts(), summary.sqliteCounts)
        assertTrue(summary.postgres!!.isFailure)
        assertEquals(null, pipeline.replicate(client, pb.url, null, snapshot.takenAt).getOrThrow().postgres)
        pb.close()
        assertTrue(pipeline.replicate(client, pb.url, null).isFailure)
    }

    @Test
    fun configDefaultsMatchLocalDbStackAndHidePassword() {
        val d = PostgresConfig.resolve(emptyMap(), listOf(Path.of("/nonexistent/.env")))
        assertEquals(PostgresConfig("localhost", 15432, "local_user", "local_password", "nfl_superbowl_playoff_odds", "nfl"), d)
        assertFalse(d.toString().contains("local_password"))
        assertEquals("jdbc:postgresql://localhost:15432/postgres", d.url("postgres"))
        assertTrue(DbFactory.createStatement("nfl_x", "local_user").contains("TEMPLATE template0 ENCODING = 'UTF8' LC_COLLATE = 'en_US.UTF-8' LC_CTYPE = 'en_US.UTF-8' CONNECTION LIMIT = -1"))
        assertFailsWith<IllegalArgumentException> { DbFactory.createStatement("x; DROP", "u") }
    }

    @Test
    fun blankDatesBecomeNullAndFailedBuildsRollBack() {
        val games = snapshot.rows.getValue("i_nfl_games")
        val blank = JsonObject(games.first() + ("dt_kickoff_utc" to JsonPrimitive("")))
        val edited = snapshot.copy(rows = snapshot.rows + ("i_nfl_games" to listOf(blank) + games.drop(1)))
        val file = root.resolve("blank.sqlite")
        SqliteReplica.rebuild(file, edited)
        DriverManager.getConnection("jdbc:sqlite:$file").use { conn ->
            val nulls = conn.createStatement().executeQuery("SELECT COUNT(*) FROM i_nfl_games WHERE dt_kickoff_utc IS NULL").use { it.next(); it.getInt(1) }
            assertEquals(1, nulls)
        }
        if (pgUp) PostgresReplica.rebuild(pg.copy(schema = "nfl_blank"), edited)

        // An orphaned FK makes the load fail; the whole transaction (DDL + rows) rolls back.
        val orphan = JsonObject(snapshot.rows.getValue("o_team_outlooks").first() + ("rel_team" to JsonPrimitive("missing")))
        val broken = snapshot.copy(rows = snapshot.rows + ("o_team_outlooks" to listOf(orphan)))
        val brokenFile = root.resolve("broken.sqlite")
        assertFailsWith<SQLException> { SqliteReplica.rebuild(brokenFile, broken) }
        DriverManager.getConnection("jdbc:sqlite:$brokenFile").use { conn ->
            val tables = conn.createStatement().executeQuery("SELECT COUNT(*) FROM sqlite_master WHERE type = 'table'").use { it.next(); it.getInt(1) }
            assertEquals(0, tables)
        }
    }
}
