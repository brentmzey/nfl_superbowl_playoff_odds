package com.gridiron.superbowl

import com.gridiron.superbowl.app.Pipeline
import com.gridiron.superbowl.app.Refresh
import com.gridiron.superbowl.data.ScoreboardSource
import com.gridiron.superbowl.sync.Backoff
import com.gridiron.superbowl.sync.PocketBaseClient
import com.gridiron.superbowl.sync.PocketBaseException
import com.gridiron.superbowl.sync.PocketBaseSchema
import com.gridiron.superbowl.sync.PocketHostConfig
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Integration tests of the real HTTP client against an in-memory PocketBase. */
class PocketBaseClientTest {
    private val pb = FakePocketBase()
    private val sleeps = mutableListOf<Long>()
    private val backoff = Backoff(attempts = 3, initialMs = 10, jitter = 0.0, sleep = { sleeps += it })
    private fun client(password: String = "s3cret") = PocketBaseClient(PocketHostConfig(pb.url, "admin@example.com", password), backoff = backoff)

    private val report = ReportsTest.report
    private val games = ReportsTest.season

    @AfterTest
    fun stop() = pb.close()

    @Test
    fun firstSyncMigratesAndInsertsEverythingWithResolvedRelations() {
        val s = client().sync(report, games, epochMs = 1_000)
        assertEquals(PocketBaseSchema.collections.map { it.name }, s.collectionsCreated)
        assertEquals(7, s.fieldsAdded) // the rel_ columns added in step 2
        assertEquals(32 + games.size + 1 + 32 + report.superBowlMatchups.size, s.created)
        assertEquals(0, s.updated + s.unchanged + s.superseded)

        val teamIds = pb.rows("i_nfl_teams").associate { pb.value(it, "str_team_code") to pb.value(it, "id") }
        val runId = pb.value(pb.rows("m_simulation_runs").single(), "id")
        pb.rows("o_team_outlooks").forEach { row ->
            assertEquals(runId, pb.value(row, "rel_run"))
            assertEquals(teamIds[pb.value(row, "str_team_code")], pb.value(row, "rel_team"))
            assertEquals(8, row.getValue("json_seed_odds").jsonArray.size)
        }
        val game = pb.rows("i_nfl_games").first { pb.value(it, "bool_is_final") == "false" }
        assertEquals("0", pb.value(game, "int_home_score")) // PocketBase stores null numbers as 0
        assertEquals(teamIds[pb.value(game, "str_home_code")], pb.value(game, "rel_home_team"))
        val outlookFields = pb.collections.getValue("o_team_outlooks").getValue("fields").jsonArray
        assertTrue(outlookFields.any { it.jsonObject["name"]?.jsonPrimitive?.content == "rel_team" && it.jsonObject["collectionId"]?.jsonPrimitive?.content == "i_nfl_teams" })
    }

    @Test
    fun rerunIsIdempotentAndANewRunSupersedesTheOldOne() {
        client().sync(report, games, epochMs = 1_000)
        val again = client().sync(report, games, epochMs = 2_000)
        assertEquals(listOf(0, 0, 0, 0), listOf(again.collectionsCreated.size, again.fieldsAdded, again.created, again.updated))
        assertEquals(32 + games.size + 1 + 32 + report.superBowlMatchups.size, again.unchanged)
        assertEquals("1000", pb.value(pb.rows("i_nfl_teams").first(), "int_created_epoch_ms_utc"))

        val changedGames = games.map { if (it.isFinal) it else it.copy(homeScore = 24, awayScore = 21) }.take(games.size)
        val next = client().sync(report.copy(generatedAt = "2026-10-26"), changedGames, epochMs = 3_000)
        assertEquals(1 + 32 + report.superBowlMatchups.size, next.created)
        assertEquals(games.count { !it.isFinal }, next.updated)
        assertEquals(1, next.superseded)
        val runs = pb.rows("m_simulation_runs").associate { pb.value(it, "dt_run_date") to pb.value(it, "str_status_code") }
        assertEquals(mapOf("2026-10-19" to "SUPERSEDED", "2026-10-26" to "ACTIVE"), runs)
        val patched = pb.rows("i_nfl_games").first { pb.value(it, "int_home_score") == "24" }
        assertEquals("1000", pb.value(patched, "int_created_epoch_ms_utc"))
        assertEquals("3000", pb.value(patched, "int_updated_epoch_ms_utc"))
    }

    @Test
    fun migrationAddsMissingColumnsToExistingCollections() {
        val c = client().authenticate()
        c.ensureSchema()
        val teams = pb.collections.getValue("i_nfl_teams")
        pb.collections["i_nfl_teams"] = JsonObject(teams + ("fields" to JsonArray(teams.getValue("fields").jsonArray.drop(1))))
        val result = c.ensureSchema()
        assertEquals(emptyList(), result.created)
        assertEquals(1, result.fieldsAdded)
        assertEquals("i_nfl_teams", result.collectionIds["i_nfl_teams"])
    }

    @Test
    fun listPagesPastFiveHundredRows() {
        val c = client().authenticate()
        c.ensureSchema()
        val many = (1..1001).map { Fixtures.game("KC", "DEN", 1, 0) }
        pb.records.getValue("i_nfl_games").addAll(PocketBaseSchema.gameRecords(many, 0).mapIndexed { i, r -> JsonObject(r.data + ("id" to JsonPrimitive("x$i"))) })
        assertEquals(1001, c.list("i_nfl_games").size)
        assertEquals(3, pb.requests.count { it.startsWith("GET /api/collections/i_nfl_games/records") })
    }

    @Test
    fun retriesTransientFailuresButNotClientErrors() {
        pb.failNext.set(2)
        assertTrue(client().authenticate().let { true })
        assertEquals(listOf(10L, 20L), sleeps)

        sleeps.clear()
        val bad = assertFailsWith<PocketBaseException> { client(password = "wrong").authenticate() }
        assertEquals(400, bad.status)
        assertEquals(emptyList(), sleeps)

        pb.failNext.set(10)
        assertEquals(503, assertFailsWith<PocketBaseException> { client().authenticate() }.status)
        assertEquals(listOf(10L, 20L), sleeps)
    }

    @Test
    fun unauthenticatedWritesAreRejected() {
        val e = assertFailsWith<PocketBaseException> { client().ensureSchema() }
        assertEquals(401, e.status)
    }

    @Test
    fun healthCheck() {
        assertTrue(client().health())
        pb.failNext.set(10)
        assertFalse(client().health())
    }

    @Test
    fun pipelinePublishWrapsFailures() {
        val pipeline = Pipeline(ScoreboardSource { _, _, _ -> Result.success("{}") }, Files.createTempDirectory("pub"))
        val refresh = Refresh(report, games)
        assertTrue(pipeline.publish(refresh, client(), 5).isSuccess)
        assertEquals(400, (pipeline.publish(refresh, client(password = "nope"), 5).exceptionOrNull() as PocketBaseException).status)
    }

    @Test
    fun rateLimitsAndNetworkErrorsAreRetried() {
        pb.failNext.set(1)
        pb.failStatus = 429
        client().authenticate()
        assertEquals(listOf(10L), sleeps)

        sleeps.clear()
        val dead = PocketBaseClient(PocketHostConfig("http://127.0.0.1:1", "u", "p"), backoff = backoff)
        assertFailsWith<IOException> { dead.authenticate() }
        assertEquals(listOf(10L, 20L), sleeps)
    }

    @Test
    fun nonNotFoundSchemaErrorsAreNotSwallowed() {
        val c = client().authenticate()
        pb.failNext.set(1)
        pb.failStatus = 403
        assertEquals(403, assertFailsWith<PocketBaseException> { c.ensureSchema() }.status)
    }

    @Test
    fun unknownTeamsFailInsteadOfWritingDanglingRelations() {
        val e = assertFailsWith<IllegalStateException> { client().sync(report, games + Fixtures.game("XXX", "KC"), 1) }
        assertTrue(e.message!!.contains("Unresolved relation i_nfl_games.rel_home_team -> i_nfl_teams[XXX]"))
    }
}
