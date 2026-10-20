package com.gridiron.superbowl

import com.gridiron.superbowl.sync.IndexDef
import com.gridiron.superbowl.sync.MigrationStep
import com.gridiron.superbowl.sync.PocketBaseSchema
import com.gridiron.superbowl.sync.SqlDialect
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PocketBaseSchemaTest {
    @Test
    fun namesFollowHungarianSnakeCaseConventions() {
        val prefixes = listOf("str_", "int_", "dbl_", "bool_", "json_", "dt_", "rel_")
        PocketBaseSchema.collections.forEach { c ->
            assertTrue(c.name.matches(Regex("^[imo]_[a-z_]+$")), c.name)
            val names = c.fields.map { it.name } + c.relations.map { it.name }
            assertEquals(names.size, names.toSet().size)
            names.forEach { n -> assertTrue(prefixes.any(n::startsWith) && n == n.lowercase(), n) }
            assertTrue(c.fields.map { it.name }.containsAll(c.upsertKey + PocketBaseSchema.auditColumns))
            c.relations.forEach { r -> assertTrue(PocketBaseSchema.collections.indexOfFirst { it.name == r.target } < PocketBaseSchema.collections.indexOf(c)) }
        }
    }

    @Test
    fun importJsonCarriesRelationsAndUniqueKeys() {
        val outlooks = PocketBaseSchema.importJson().map { it.jsonObject }.first { it["name"]?.jsonPrimitive?.content == "o_team_outlooks" }
        val rel = outlooks.getValue("fields").jsonArray.map { it.jsonObject }.first { it["name"]?.jsonPrimitive?.content == "rel_run" }
        assertEquals("m_simulation_runs", rel.getValue("collectionId").jsonPrimitive.content)
        assertEquals("CREATE UNIQUE INDEX `uq_o_team_outlooks__run_id_team_code` ON `o_team_outlooks` (`str_run_id`, `str_team_code`)", outlooks.getValue("indexes").jsonArray[0].jsonPrimitive.content)
        assertEquals("", outlooks.getValue("listRule").jsonPrimitive.content)
        assertEquals(JsonNull, outlooks.getValue("createRule"))
        val base = PocketBaseSchema.outlooks.baseJson()
        assertFalse(base.getValue("fields").jsonArray.any { it.jsonObject["type"]?.jsonPrimitive?.content == "relation" })
        assertEquals(0, base.getValue("indexes").jsonArray.size)
    }

    @Test
    fun recordsForARun() {
        val rows = PocketBaseSchema.runRecords(ReportsTest.report, 42)
        val runId = PocketBaseSchema.runId(ReportsTest.report)
        assertEquals("2026-10-19_s2026_w4_seed9", runId)
        assertEquals(listOf("m_simulation_runs") + List(32) { "o_team_outlooks" } + List(ReportsTest.report.superBowlMatchups.size) { "o_superbowl_matchups" }, rows.map { it.collection })
        assertTrue(rows.all { it.data["str_run_id"]?.jsonPrimitive?.content == runId && it.data["int_created_epoch_ms_utc"] == JsonPrimitive(42) })
        assertEquals("$runId|${ReportsTest.report.byChampionOdds.first().abbr}", rows[1].key(PocketBaseSchema.outlooks))
        assertEquals(32, PocketBaseSchema.teamRecords(0).size)
        val game = PocketBaseSchema.gameRecords(listOf(Fixtures.game("KC", "BUF")), 0).single()
        assertEquals(JsonNull, game.data["int_home_score"])
        assertEquals(mapOf("rel_home_team" to "KC", "rel_away_team" to "BUF"), game.relations)
    }

    @Test
    fun sameContentToleratesPocketBaseNormalisation() {
        val desired = JsonObject(mapOf(
            "a" to JsonNull, "b" to JsonPrimitive(0.0), "c" to JsonArray(listOf(JsonPrimitive(0.0), JsonPrimitive(0.5))),
            "d" to JsonPrimitive("x"), "e" to JsonPrimitive(false), "int_updated_epoch_ms_utc" to JsonPrimitive(1),
        ))
        val stored = JsonObject(mapOf(
            "a" to JsonPrimitive(0), "b" to JsonPrimitive(0), "c" to JsonArray(listOf(JsonPrimitive(0), JsonPrimitive(0.5))),
            "d" to JsonPrimitive("x"), "e" to JsonPrimitive(false), "int_updated_epoch_ms_utc" to JsonPrimitive(99),
        ))
        assertTrue(PocketBaseSchema.sameContent(desired, stored))
        assertFalse(PocketBaseSchema.sameContent(desired, JsonObject(stored + ("d" to JsonPrimitive("y")))))
        assertFalse(PocketBaseSchema.sameContent(desired, JsonObject(stored + ("c" to JsonArray(listOf(JsonPrimitive(0)))))))
        assertFalse(PocketBaseSchema.sameContent(JsonObject(mapOf("s" to JsonPrimitive("7"))), JsonObject(mapOf("s" to JsonPrimitive("7.0")))))
        assertFalse(PocketBaseSchema.sameContent(JsonObject(mapOf("n" to JsonNull)), JsonObject(mapOf("n" to JsonPrimitive(3)))))
        assertFalse(PocketBaseSchema.sameContent(JsonObject(mapOf("o" to JsonArray(emptyList()))), JsonObject(mapOf("o" to JsonPrimitive(1)))))
        assertTrue(PocketBaseSchema.sameContent(JsonObject(mapOf("missing" to JsonPrimitive(""))), JsonObject(emptyMap())))
        assertTrue(PocketBaseSchema.sameContent(JsonObject(mapOf("f" to JsonPrimitive(false))), JsonObject(emptyMap())))
        assertTrue(PocketBaseSchema.sameContent(JsonObject(mapOf("t" to JsonPrimitive(true))), JsonObject(mapOf("t" to JsonPrimitive(true)))))
        assertFalse(PocketBaseSchema.sameContent(JsonObject(mapOf("t" to JsonPrimitive(true))), JsonObject(mapOf("t" to JsonPrimitive(false)))))
        assertFalse(PocketBaseSchema.sameContent(JsonObject(mapOf("j" to JsonPrimitive("x"))), JsonObject(mapOf("j" to JsonObject(emptyMap())))))
        assertTrue(PocketBaseSchema.sameContent(JsonObject(mapOf("o" to JsonObject(emptyMap()))), JsonObject(mapOf("o" to JsonObject(emptyMap())))))
    }

    @Test
    fun indexesCoverKeysForeignKeysAndQueries() {
        val all = (listOf(PocketBaseSchema.migrationsLedger) + PocketBaseSchema.collections).flatMap { c -> c.indexes.map { c to it } }
        assertEquals(all.size, all.map { (c, i) -> i.name(c.name) }.toSet().size, "index names must be unique")
        (listOf(PocketBaseSchema.migrationsLedger) + PocketBaseSchema.collections).forEach { c ->
            assertEquals(IndexDef(c.upsertKey, unique = true), c.indexes.first())
            val columns = c.fields.map { it.name } + c.relations.map { it.name } + SqlDialect.SYSTEM_TIMESTAMPS
            c.indexes.forEach { i -> assertTrue(columns.containsAll(i.columns), "${c.name}: ${i.columns}") }
            // Every foreign key is the leading column of some index.
            c.relations.forEach { r -> assertTrue(c.indexes.any { it.columns.first() == r.name }, "${c.name}.${r.name} unindexed") }
        }
        assertEquals(
            "CREATE INDEX `ix_i_nfl_games__season_year_kickoff_utc` ON `i_nfl_games` (`int_season_year`, `dt_kickoff_utc`) WHERE bool_is_final = FALSE",
            PocketBaseSchema.games.indexSql().last(),
        )
        assertTrue(PocketBaseSchema.outlooks.indexSql().contains("CREATE UNIQUE INDEX `uq_o_team_outlooks__run_team` ON `o_team_outlooks` (`rel_run`, `rel_team`)"))
        // Plain FK indexes are skipped when an extra index already leads with that column.
        assertEquals(1, PocketBaseSchema.games.indexes.count { it.columns.first() == "rel_home_team" })
    }

    @Test
    fun relationsAreRequiredNonCascadingForeignKeys() {
        val rel = PocketBaseSchema.outlooks.relationJson(PocketBaseSchema.outlooks.relations.first(), "abc")
        assertEquals("abc", rel.getValue("collectionId").jsonPrimitive.content)
        assertEquals("true", rel.getValue("required").jsonPrimitive.content)
        assertEquals("false", rel.getValue("cascadeDelete").jsonPrimitive.content)
        assertEquals("1", rel.getValue("maxSelect").jsonPrimitive.content)
    }

    @Test
    fun migrationsAreOrderedAndFingerprinted() {
        val steps = PocketBaseSchema.migrations
        assertEquals(listOf("001_base_tables", "002_foreign_keys", "003_indexes", "004_summary_views"), steps.map { it.id })
        assertTrue(steps.all { it.fingerprint.length == 8 })
        assertEquals(steps.size, steps.map { it.fingerprint }.toSet().size)
        assertEquals(MigrationStep("x", "", "a").fingerprint, MigrationStep("y", "other", "a").fingerprint)
        val export = PocketBaseSchema.importJson().map { it.jsonObject.getValue("name").jsonPrimitive.content }
        assertEquals(listOf("tbl_schema_migrations", "i_nfl_teams", "i_nfl_games", "m_simulation_runs", "o_team_outlooks", "o_superbowl_matchups", "s_latest_team_outlooks"), export)
        val view = PocketBaseSchema.latestOutlooks
        assertTrue(view.query.startsWith("SELECT o.id") && view.query.contains("str_status_code = 'ACTIVE'"))
        assertEquals("view", view.json().getValue("type").jsonPrimitive.content)
    }

    @Test
    fun datesAreNormalisedToPocketBaseUtcForm() {
        assertEquals("2026-10-19 00:00:00.000Z", PocketBaseSchema.pbDate("2026-10-19"))
        assertEquals("2026-10-18 17:00:00.000Z", PocketBaseSchema.pbDate("2026-10-18T17:00Z"))
        assertEquals("2026-10-18 17:00:05.500Z", PocketBaseSchema.pbDate("2026-10-18T17:00:05.5Z"))
        assertEquals("2026-10-18 07:03:09.123Z", PocketBaseSchema.pbDate("2026-10-18T7:3:9.1234Z"))
        val runs = PocketBaseSchema.runs.baseJson().getValue("fields").jsonArray.map { it.jsonObject }
        assertEquals("date", runs.first { it["name"]?.jsonPrimitive?.content == "dt_run_date" }.getValue("type").jsonPrimitive.content)
        val created = runs.first { it["name"]?.jsonPrimitive?.content == "created" }
        assertEquals("autodate", created.getValue("type").jsonPrimitive.content)
        assertEquals("false", created.getValue("onUpdate").jsonPrimitive.content)
        assertEquals("true", runs.first { it["name"]?.jsonPrimitive?.content == "updated" }.getValue("onUpdate").jsonPrimitive.content)
        assertEquals("number[]", PocketBaseSchema.outlooks.fields.first { it.name == "json_seed_odds" }.tsType)
        assertEquals(listOf("NflTeam", "NflGame", "SimulationRun", "TeamOutlook", "SuperBowlMatchup"), PocketBaseSchema.collections.map { it.typeName })
    }
}
