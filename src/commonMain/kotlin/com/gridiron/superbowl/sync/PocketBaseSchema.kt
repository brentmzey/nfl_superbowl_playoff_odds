package com.gridiron.superbowl.sync

import com.gridiron.superbowl.model.Game
import com.gridiron.superbowl.model.League
import com.gridiron.superbowl.report.SimulationReport
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.math.abs

/** PocketBase field types used by this schema (PocketBase >= 0.23 `fields` format). */
enum class FieldType(val wire: String) { TEXT("text"), NUMBER("number"), BOOL("bool"), JSON("json") }

data class FieldDef(val name: String, val type: FieldType, val required: Boolean = false)

/** A single-record relation (`rel_` column) to another collection, added in migration step 2. */
data class RelationDef(val name: String, val target: String)

/**
 * One PocketBase collection. [upsertKey] columns identify a row (backed by a unique index), so re-running a
 * refresh updates rows rather than duplicating them. The collection [id] defaults to its name so the exported
 * import JSON can reference relation targets deterministically.
 */
data class CollectionDef(
    val name: String,
    val fields: List<FieldDef>,
    val upsertKey: List<String>,
    val relations: List<RelationDef> = emptyList(),
    val indexes: List<String> = emptyList(),
) {
    val id: String get() = name

    fun fieldJson(f: FieldDef): JsonObject = buildJsonObject {
        put("name", f.name)
        put("type", f.type.wire)
        put("required", f.required)
        if (f.type == FieldType.JSON) put("maxSize", 0)
    }

    fun relationJson(r: RelationDef, targetId: String): JsonObject = buildJsonObject {
        put("name", r.name)
        put("type", "relation")
        put("collectionId", targetId)
        put("maxSelect", 1)
        put("cascadeDelete", false)
        put("required", false)
    }

    /** Step 1 payload: base columns + unique key, no relations. */
    fun baseJson(): JsonObject = buildJsonObject {
        put("id", id)
        put("name", name)
        put("type", "base")
        // Public read (for dashboards); writes are superuser-only.
        put("listRule", "")
        put("viewRule", "")
        put("createRule", JsonNull)
        put("updateRule", JsonNull)
        put("deleteRule", JsonNull)
        put("fields", JsonArray(fields.map(::fieldJson)))
        put("indexes", buildJsonArray {
            add(JsonPrimitive("CREATE UNIQUE INDEX idx_${name}_key ON $name (${upsertKey.joinToString(", ")})"))
            indexes.forEach { add(JsonPrimitive(it)) }
        })
    }

    /** Full definition (both steps) for PocketBase's "Import collections" screen. */
    fun importJson(): JsonObject = JsonObject(
        baseJson() + ("fields" to JsonArray(fields.map(::fieldJson) + relations.map { relationJson(it, it.target) })),
    )
}

/**
 * A row destined for [collection]. [data] holds the Hungarian-prefixed columns; [relations] maps each `rel_`
 * column to the natural key of the target row, which the client resolves to a PocketBase record id.
 */
data class PbRecord(val collection: String, val data: JsonObject, val relations: Map<String, String> = emptyMap()) {
    fun key(def: CollectionDef): String = PocketBaseSchema.naturalKey(def, data)
}

/**
 * Hungarian-notation PocketBase schema shared with the sibling sports/econometrics repos:
 * collection prefixes `i_` (input data), `m_` (model runs), `o_` (model output); column prefixes `str_`, `int_`,
 * `dbl_`, `bool_`, `json_`, `dt_`, `rel_`; snake_case everywhere; audit columns on every row; rows are never
 * deleted, only re-statused (ACTIVE / INACTIVE / SUPERSEDED / ARCHIVED).
 * Numbers and booleans are never `required` because PocketBase treats 0 / false as blank.
 */
object PocketBaseSchema {
    const val ENGINE_VERSION = "0.1.0-RC2-elo-mc"
    const val ACTIVE = "ACTIVE"
    const val SUPERSEDED = "SUPERSEDED"
    val auditColumns = setOf("bool_is_active", "str_status_code", "int_created_epoch_ms_utc", "int_updated_epoch_ms_utc")

    private val audit = listOf(
        FieldDef("bool_is_active", FieldType.BOOL),
        FieldDef("str_status_code", FieldType.TEXT, required = true),
        FieldDef("int_created_epoch_ms_utc", FieldType.NUMBER),
        FieldDef("int_updated_epoch_ms_utc", FieldType.NUMBER),
    )

    private fun text(n: String, required: Boolean = false) = FieldDef(n, FieldType.TEXT, required)
    private fun num(n: String) = FieldDef(n, FieldType.NUMBER)
    private fun bool(n: String) = FieldDef(n, FieldType.BOOL)

    val teams = CollectionDef(
        "i_nfl_teams",
        listOf(text("str_team_code", true), text("str_team_name", true), text("str_conference", true), text("str_division", true)) + audit,
        upsertKey = listOf("str_team_code"),
    )

    val games = CollectionDef(
        "i_nfl_games",
        listOf(
            text("str_game_id", true), num("int_season_year"), text("str_phase", true), num("int_week"), text("dt_kickoff_utc"),
            text("str_home_code", true), text("str_away_code", true), bool("bool_neutral_site"), bool("bool_is_final"),
            num("int_home_score"), num("int_away_score"),
        ) + audit,
        upsertKey = listOf("str_game_id"),
        relations = listOf(RelationDef("rel_home_team", "i_nfl_teams"), RelationDef("rel_away_team", "i_nfl_teams")),
        indexes = listOf("CREATE INDEX idx_i_nfl_games_season ON i_nfl_games (int_season_year, str_phase, int_week)"),
    )

    val runs = CollectionDef(
        "m_simulation_runs",
        listOf(
            text("str_run_id", true), text("dt_run_date", true), num("int_season_year"), num("int_through_week"),
            num("int_games_played"), num("int_games_remaining"), num("int_total_iterations"), num("int_random_seed"),
            text("str_engine_version"), text("str_top_favorite_code"), num("dbl_top_favorite_prob"),
        ) + audit,
        upsertKey = listOf("str_run_id"),
        indexes = listOf("CREATE INDEX idx_m_simulation_runs_season ON m_simulation_runs (int_season_year, dt_run_date)"),
    )

    val outlooks = CollectionDef(
        "o_team_outlooks",
        listOf(
            text("str_run_id", true), text("str_team_code", true), text("dt_run_date", true), num("int_season_year"),
            num("int_wins"), num("int_losses"), num("int_ties"), num("dbl_elo_rating"), num("dbl_projected_wins"),
            num("dbl_division_prob"), num("dbl_top_seed_prob"), num("dbl_playoff_prob"), num("dbl_divisional_prob"),
            num("dbl_conference_prob"), num("dbl_superbowl_prob"), num("dbl_champion_prob"), num("int_champion_rank"),
            FieldDef("json_seed_odds", FieldType.JSON),
        ) + audit,
        upsertKey = listOf("str_run_id", "str_team_code"),
        relations = listOf(RelationDef("rel_run", "m_simulation_runs"), RelationDef("rel_team", "i_nfl_teams")),
        indexes = listOf("CREATE INDEX idx_o_team_outlooks_team ON o_team_outlooks (str_team_code, dt_run_date)"),
    )

    val matchups = CollectionDef(
        "o_superbowl_matchups",
        listOf(text("str_run_id", true), text("str_afc_code", true), text("str_nfc_code", true), num("int_rank"), num("dbl_probability")) + audit,
        upsertKey = listOf("str_run_id", "str_afc_code", "str_nfc_code"),
        relations = listOf(
            RelationDef("rel_run", "m_simulation_runs"),
            RelationDef("rel_afc_team", "i_nfl_teams"),
            RelationDef("rel_nfc_team", "i_nfl_teams"),
        ),
    )

    /** Dependency order: relation targets come before the collections that point at them. */
    val collections: List<CollectionDef> = listOf(teams, games, runs, outlooks, matchups)

    /** Collections whose rows belong to one run; the client only prefetches the current run's rows for these. */
    val runScoped: Set<String> = setOf(runs.name, outlooks.name, matchups.name)

    fun byName(name: String): CollectionDef = collections.first { it.name == name }

    fun importJson(): JsonArray = JsonArray(collections.map { it.importJson() })

    fun naturalKey(def: CollectionDef, row: JsonObject): String =
        def.upsertKey.joinToString("|") { row.getValue(it).jsonPrimitive.content }

    fun runId(report: SimulationReport): String = "${report.generatedAt}_s${report.season}_w${report.throughWeek}_seed${report.seed}"

    private fun audit(epochMs: Long): Map<String, JsonElement> = mapOf(
        "bool_is_active" to JsonPrimitive(true),
        "str_status_code" to JsonPrimitive(ACTIVE),
        "int_created_epoch_ms_utc" to JsonPrimitive(epochMs),
        "int_updated_epoch_ms_utc" to JsonPrimitive(epochMs),
    )

    private fun toJson(v: Any?): JsonElement = when (v) {
        null -> JsonNull
        is JsonElement -> v
        is Number -> JsonPrimitive(v)
        is Boolean -> JsonPrimitive(v)
        else -> JsonPrimitive(v.toString())
    }

    private fun row(def: CollectionDef, epochMs: Long, relations: Map<String, String>, vararg values: Pair<String, Any?>) =
        PbRecord(def.name, JsonObject(values.associate { (k, v) -> k to toJson(v) } + audit(epochMs)), relations)

    fun teamRecords(epochMs: Long): List<PbRecord> = League.teams.map { t ->
        row(teams, epochMs, emptyMap(), "str_team_code" to t.abbr, "str_team_name" to t.name, "str_conference" to t.conference.name, "str_division" to t.division.label)
    }

    fun gameRecords(games: List<Game>, epochMs: Long): List<PbRecord> = games.map { g ->
        row(
            this.games, epochMs, mapOf("rel_home_team" to g.home, "rel_away_team" to g.away),
            "str_game_id" to g.id, "int_season_year" to g.season, "str_phase" to g.phase.name, "int_week" to g.week,
            "dt_kickoff_utc" to g.date, "str_home_code" to g.home, "str_away_code" to g.away, "bool_neutral_site" to g.neutral,
            "bool_is_final" to g.isFinal, "int_home_score" to g.homeScore, "int_away_score" to g.awayScore,
        )
    }

    fun runRecords(report: SimulationReport, epochMs: Long): List<PbRecord> {
        val runId = runId(report)
        val ranked = report.byChampionOdds
        val top = ranked.first()
        val run = row(
            runs, epochMs, emptyMap(),
            "str_run_id" to runId, "dt_run_date" to report.generatedAt, "int_season_year" to report.season,
            "int_through_week" to report.throughWeek, "int_games_played" to report.gamesPlayed,
            "int_games_remaining" to report.gamesRemaining, "int_total_iterations" to report.iterations,
            "int_random_seed" to report.seed, "str_engine_version" to ENGINE_VERSION,
            "str_top_favorite_code" to top.abbr, "dbl_top_favorite_prob" to top.pChampion,
        )
        val outlookRows = ranked.mapIndexed { i, t ->
            row(
                outlooks, epochMs, mapOf("rel_run" to runId, "rel_team" to t.abbr),
                "str_run_id" to runId, "str_team_code" to t.abbr, "dt_run_date" to report.generatedAt,
                "int_season_year" to report.season, "int_wins" to t.wins, "int_losses" to t.losses, "int_ties" to t.ties,
                "dbl_elo_rating" to t.elo, "dbl_projected_wins" to t.projectedWins, "dbl_division_prob" to t.pDivision,
                "dbl_top_seed_prob" to t.pTopSeed, "dbl_playoff_prob" to t.pPlayoffs, "dbl_divisional_prob" to t.pDivisional,
                "dbl_conference_prob" to t.pConference, "dbl_superbowl_prob" to t.pSuperBowl, "dbl_champion_prob" to t.pChampion,
                "int_champion_rank" to i + 1, "json_seed_odds" to JsonArray(t.seedOdds.map(::JsonPrimitive)),
            )
        }
        val matchupRows = report.superBowlMatchups.mapIndexed { i, m ->
            row(
                matchups, epochMs, mapOf("rel_run" to runId, "rel_afc_team" to m.afc, "rel_nfc_team" to m.nfc),
                "str_run_id" to runId, "str_afc_code" to m.afc, "str_nfc_code" to m.nfc, "int_rank" to i + 1, "dbl_probability" to m.probability,
            )
        }
        return listOf(run) + outlookRows + matchupRows
    }

    /**
     * True when [existing] (as returned by PocketBase) already holds every non-audit value in [desired].
     * PocketBase stores null numbers as 0 and null booleans as false, and may print 0.0 as 0, so values are
     * compared numerically and blanks are treated as equal.
     */
    fun sameContent(desired: JsonObject, existing: JsonObject): Boolean =
        desired.filterKeys { it !in auditColumns }.all { (k, v) -> sameValue(v, existing[k] ?: JsonNull) }

    private fun blank(e: JsonElement): Boolean = e is JsonNull ||
        (e is JsonPrimitive && (e.content == "" || e.content == "false" || e.content.toDoubleOrNull() == 0.0))

    private fun sameValue(a: JsonElement, b: JsonElement): Boolean = when {
        a is JsonArray && b is JsonArray -> a.size == b.size && a.indices.all { sameValue(a[it], b[it]) }
        a is JsonNull || b is JsonNull -> blank(a) && blank(b)
        a is JsonPrimitive && b is JsonPrimitive -> {
            val x = a.jsonPrimitive.content.toDoubleOrNull()
            val y = b.jsonPrimitive.content.toDoubleOrNull()
            if (!a.isString && x != null && y != null) abs(x - y) < 1e-12 else a.content == b.content
        }
        else -> a == b
    }
}
