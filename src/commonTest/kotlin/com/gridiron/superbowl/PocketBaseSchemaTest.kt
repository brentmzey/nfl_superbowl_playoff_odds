package com.gridiron.superbowl

import com.gridiron.superbowl.sync.PocketBaseSchema
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
        assertEquals("CREATE UNIQUE INDEX idx_o_team_outlooks_key ON o_team_outlooks (str_run_id, str_team_code)", outlooks.getValue("indexes").jsonArray[0].jsonPrimitive.content)
        assertEquals("", outlooks.getValue("listRule").jsonPrimitive.content)
        assertEquals(JsonNull, outlooks.getValue("createRule"))
        val base = PocketBaseSchema.outlooks.baseJson().getValue("fields").jsonArray
        assertFalse(base.any { it.jsonObject["type"]?.jsonPrimitive?.content == "relation" })
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
}
