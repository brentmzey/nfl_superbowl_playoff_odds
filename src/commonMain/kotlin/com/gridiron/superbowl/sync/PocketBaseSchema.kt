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
enum class FieldType(val wire: String) { TEXT("text"), NUMBER("number"), BOOL("bool"), JSON("json"), DATE("date") }

/** One column. [tsType] overrides the generated TypeScript type (e.g. a JSON column holding `number[]`). */
data class FieldDef(val name: String, val type: FieldType, val required: Boolean = false, val tsType: String? = null)

/**
 * A single-record relation (`rel_` column): PocketBase's foreign key. It is validated on every write, `required`
 * so no row can be orphaned, and never cascades because rows are never deleted. Added in migration step 002.
 */
data class RelationDef(val name: String, val target: String)

/**
 * A SQLite index on a collection, added in migration step 003 (after the relations exist, so `rel_` columns can be
 * indexed). Names follow `uq_<table>__<cols>` for unique and `ix_<table>__<cols>` for plain indexes.
 */
data class IndexDef(val columns: List<String>, val unique: Boolean = false, val where: String? = null) {
    fun name(table: String): String = (if (unique) "uq_" else "ix_") + table + "__" + columns.joinToString("_") { it.substringAfter('_') }

    fun sql(table: String): String =
        "CREATE ${if (unique) "UNIQUE " else ""}INDEX `${name(table)}` ON `$table` (${columns.joinToString(", ") { "`$it`" }})" +
            where?.let { " WHERE $it" }.orEmpty()
}

/**
 * One PocketBase base collection. [upsertKey] columns identify a row (backed by a unique index), so re-running a
 * refresh updates rows rather than duplicating them. The collection [id] defaults to its name so the exported
 * import JSON can reference relation targets deterministically.
 */
data class CollectionDef(
    val name: String,
    /** Semantic singular name for generated client types, e.g. `TeamOutlook`. */
    val typeName: String,
    val fields: List<FieldDef>,
    val upsertKey: List<String>,
    val relations: List<RelationDef> = emptyList(),
    val extraIndexes: List<IndexDef> = emptyList(),
) {
    val id: String get() = name

    /** Natural-key unique index first, then a plain index on every `rel_` column (SQLite does not index FKs), then extras. */
    val indexes: List<IndexDef>
        get() = listOf(IndexDef(upsertKey, unique = true)) +
            relations.map { IndexDef(listOf(it.name)) }.filter { fk -> extraIndexes.none { it.columns.first() == fk.columns.first() } } +
            extraIndexes

    fun indexSql(): List<String> = indexes.map { it.sql(name) }

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
        put("minSelect", 0)
        put("cascadeDelete", false)
        put("required", true)
    }

    /**
     * PocketBase system timestamps (`autodate`), maintained by the server. They power `sort: '-created'` in clients
     * and complement the explicit `int_*_epoch_ms_utc` audit columns, which survive exports and replicas unchanged.
     */
    val systemFields: List<JsonObject>
        get() = listOf("created" to false, "updated" to true).map { (n, onUpdate) ->
            buildJsonObject {
                put("name", n)
                put("type", "autodate")
                put("onCreate", true)
                put("onUpdate", onUpdate)
                put("system", false)
                put("hidden", false)
            }
        }

    fun baseFieldsJson(): List<JsonObject> = fields.map(::fieldJson) + systemFields

    private fun rules(): Map<String, JsonElement> = mapOf(
        // Public read (for dashboards); writes are superuser-only.
        "listRule" to JsonPrimitive(""), "viewRule" to JsonPrimitive(""),
        "createRule" to JsonNull, "updateRule" to JsonNull, "deleteRule" to JsonNull,
    )

    /** Step 001 payload: base columns only (no relations, no indexes). */
    fun baseJson(): JsonObject = JsonObject(
        mapOf("id" to JsonPrimitive(id), "name" to JsonPrimitive(name), "type" to JsonPrimitive("base")) + rules() +
            mapOf("fields" to JsonArray(baseFieldsJson()), "indexes" to JsonArray(emptyList())),
    )

    /** Final state after every step, for PocketBase's "Import collections" screen. */
    fun importJson(): JsonObject = JsonObject(
        baseJson() + mapOf(
            "fields" to JsonArray(baseFieldsJson() + relations.map { relationJson(it, it.target) }),
            "indexes" to JsonArray(indexSql().map(::JsonPrimitive)),
        ),
    )
}

/** A view column: `alias.field`, typed by the source [field] so clients get exact types. */
data class ViewColumn(val alias: String, val field: FieldDef)

/**
 * A read-only PocketBase view collection (`s_` summary prefix), created in migration step 004. The SQL is built
 * from [columns], so the query and the generated client types cannot drift apart.
 */
data class ViewDef(val name: String, val typeName: String, val idAlias: String, val columns: List<ViewColumn>, val from: String) {
    val query: String
        get() = "SELECT $idAlias.id,\n       " + columns.joinToString(",\n       ") { "${it.alias}.${it.field.name}" } + "\n$from"

    fun json(): JsonObject = JsonObject(
        mapOf(
            "id" to JsonPrimitive(name), "name" to JsonPrimitive(name), "type" to JsonPrimitive("view"),
            "listRule" to JsonPrimitive(""), "viewRule" to JsonPrimitive(""), "viewQuery" to JsonPrimitive(query),
        ),
    )
}

/** One numbered, idempotent migration step. [fingerprint] changes whenever the step's definition changes. */
data class MigrationStep(val id: String, val description: String, val definition: String) {
    val fingerprint: String get() = definition.hashCode().toUInt().toString(16).padStart(8, '0')
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
    const val ENGINE_VERSION = "0.1.0-RC5-elo-mc"
    const val ACTIVE = "ACTIVE"
    const val SUPERSEDED = "SUPERSEDED"
    val auditColumns = setOf("bool_is_active", "str_status_code", "int_created_epoch_ms_utc", "int_updated_epoch_ms_utc")

    private val audit = listOf(
        FieldDef("bool_is_active", FieldType.BOOL),
        FieldDef("str_status_code", FieldType.TEXT, required = true, tsType = "StatusCode"),
        FieldDef("int_created_epoch_ms_utc", FieldType.NUMBER),
        FieldDef("int_updated_epoch_ms_utc", FieldType.NUMBER),
    )

    private fun text(n: String, required: Boolean = false) = FieldDef(n, FieldType.TEXT, required)
    private fun date(n: String, required: Boolean = false) = FieldDef(n, FieldType.DATE, required)
    private fun num(n: String) = FieldDef(n, FieldType.NUMBER)
    private fun bool(n: String) = FieldDef(n, FieldType.BOOL)

    val teams = CollectionDef(
        "i_nfl_teams", "NflTeam",
        listOf(text("str_team_code", true), text("str_team_name", true), FieldDef("str_conference", FieldType.TEXT, true, tsType = "Conference"), text("str_division", true)) + audit,
        upsertKey = listOf("str_team_code"),
        extraIndexes = listOf(IndexDef(listOf("str_conference", "str_division"))),
    )

    val games = CollectionDef(
        "i_nfl_games", "NflGame",
        listOf(
            text("str_game_id", true), num("int_season_year"), FieldDef("str_phase", FieldType.TEXT, true, tsType = "SeasonPhase"), num("int_week"), date("dt_kickoff_utc"),
            text("str_home_code", true), text("str_away_code", true), bool("bool_neutral_site"), bool("bool_is_final"),
            num("int_home_score"), num("int_away_score"),
        ) + audit,
        upsertKey = listOf("str_game_id"),
        relations = listOf(RelationDef("rel_home_team", "i_nfl_teams"), RelationDef("rel_away_team", "i_nfl_teams")),
        extraIndexes = listOf(
            // Schedule / results by season and week.
            IndexDef(listOf("int_season_year", "str_phase", "int_week")),
            // A team's games in a season (FK first, so it also serves plain FK lookups).
            IndexDef(listOf("rel_home_team", "int_season_year")),
            IndexDef(listOf("rel_away_team", "int_season_year")),
            // Unplayed games: the only rows a weekly refresh rewrites.
            IndexDef(listOf("int_season_year", "dt_kickoff_utc"), where = "bool_is_final = FALSE"),
        ),
    )

    val runs = CollectionDef(
        "m_simulation_runs", "SimulationRun",
        listOf(
            text("str_run_id", true), date("dt_run_date", true), num("int_season_year"), num("int_through_week"),
            num("int_games_played"), num("int_games_remaining"), num("int_total_iterations"), num("int_random_seed"),
            text("str_engine_version"), text("str_top_favorite_code"), num("dbl_top_favorite_prob"),
        ) + audit,
        upsertKey = listOf("str_run_id"),
        extraIndexes = listOf(
            // "Latest ACTIVE run of a season" and the SUPERSEDED sweep.
            IndexDef(listOf("int_season_year", "str_status_code", "dt_run_date")),
            // Newest runs first (`sort: '-created'`).
            IndexDef(listOf("int_season_year", "created")),
        ),
    )

    val outlooks = CollectionDef(
        "o_team_outlooks", "TeamOutlook",
        listOf(
            text("str_run_id", true), text("str_team_code", true), date("dt_run_date", true), num("int_season_year"),
            num("int_wins"), num("int_losses"), num("int_ties"), num("dbl_elo_rating"), num("dbl_projected_wins"),
            num("dbl_division_prob"), num("dbl_top_seed_prob"), num("dbl_playoff_prob"), num("dbl_divisional_prob"),
            num("dbl_conference_prob"), num("dbl_superbowl_prob"), num("dbl_champion_prob"), num("int_champion_rank"),
            FieldDef("json_seed_odds", FieldType.JSON, tsType = "number[]"),
        ) + audit,
        upsertKey = listOf("str_run_id", "str_team_code"),
        relations = listOf(RelationDef("rel_run", "m_simulation_runs"), RelationDef("rel_team", "i_nfl_teams")),
        extraIndexes = listOf(
            // One outlook per (run, team), also enforced on the foreign keys.
            IndexDef(listOf("rel_run", "rel_team"), unique = true),
            // Leaderboard for a run, already in rank order.
            IndexDef(listOf("rel_run", "int_champion_rank")),
            // A team's odds over time (trend charts).
            IndexDef(listOf("rel_team", "dt_run_date")),
        ),
    )

    val matchups = CollectionDef(
        "o_superbowl_matchups", "SuperBowlMatchup",
        listOf(text("str_run_id", true), text("str_afc_code", true), text("str_nfc_code", true), num("int_rank"), num("dbl_probability")) + audit,
        upsertKey = listOf("str_run_id", "str_afc_code", "str_nfc_code"),
        relations = listOf(
            RelationDef("rel_run", "m_simulation_runs"),
            RelationDef("rel_afc_team", "i_nfl_teams"),
            RelationDef("rel_nfc_team", "i_nfl_teams"),
        ),
        extraIndexes = listOf(IndexDef(listOf("rel_run", "int_rank"))),
    )

    /** Ledger of applied migration steps (bootstrapped before step 001). */
    val migrationsLedger = CollectionDef(
        "tbl_schema_migrations", "SchemaMigration",
        listOf(text("str_migration_id", true), text("str_description"), text("str_fingerprint", true), num("int_applied_epoch_ms_utc")) + audit,
        upsertKey = listOf("str_migration_id"),
    )

    /** Current outlook for every team: rows of each season's ACTIVE run, joined to team metadata. */
    val latestOutlooks: ViewDef by lazy {
        fun o(n: String) = ViewColumn("o", outlooks.fields.first { it.name == n })
        fun t(n: String) = ViewColumn("t", teams.fields.first { it.name == n })
        fun r(n: String) = ViewColumn("r", runs.fields.first { it.name == n })
        ViewDef(
            "s_latest_team_outlooks", "LatestTeamOutlook", "o",
            listOf(
                o("str_team_code"), t("str_team_name"), t("str_conference"), t("str_division"),
                r("str_run_id"), r("dt_run_date"), r("int_season_year"), r("int_through_week"),
                o("int_wins"), o("int_losses"), o("int_ties"), o("dbl_elo_rating"), o("dbl_projected_wins"),
                o("dbl_division_prob"), o("dbl_playoff_prob"), o("dbl_divisional_prob"), o("dbl_conference_prob"),
                o("dbl_superbowl_prob"), o("dbl_champion_prob"), o("int_champion_rank"), o("json_seed_odds"),
            ),
            "FROM o_team_outlooks o\nJOIN m_simulation_runs r ON r.id = o.rel_run\nJOIN i_nfl_teams t ON t.id = o.rel_team\nWHERE r.str_status_code = 'ACTIVE'",
        )
    }

    val views: List<ViewDef> get() = listOf(latestOutlooks)

    /** Dependency order: relation targets come before the collections that point at them. */
    val collections: List<CollectionDef> = listOf(teams, games, runs, outlooks, matchups)

    /** Collections whose rows belong to one run; the client only prefetches the current run's rows for these. */
    val runScoped: Set<String> = setOf(runs.name, outlooks.name, matchups.name)

    fun byName(name: String): CollectionDef = collections.first { it.name == name }

    /**
     * Stepwise migrations, applied in order and recorded in [migrationsLedger]. Every step reconciles toward its
     * definition and is safe to re-run. Steps only ever add things: columns, relations, indexes, views.
     */
    val migrations: List<MigrationStep> by lazy { listOf(
        MigrationStep("001_base_tables", "Base collections and columns (no relations, no indexes)",
            collections.joinToString("\n") { it.baseJson().toString() }),
        MigrationStep("002_foreign_keys", "Required single-record rel_ relations, no cascades",
            collections.joinToString("\n") { c -> c.relations.joinToString { "${c.name}.${it.name}->${it.target}" } }),
        MigrationStep("003_indexes", "Unique natural keys, FK indexes, query indexes",
            collections.joinToString("\n") { it.indexSql().joinToString("\n") }),
        MigrationStep("004_summary_views", "Read-only s_ views over the latest ACTIVE run",
            views.joinToString("\n") { it.json().toString() }),
    ) }

    fun importJson(): JsonArray =
        JsonArray((listOf(migrationsLedger) + collections).map { it.importJson() } + views.map { it.json() })

    fun naturalKey(def: CollectionDef, row: JsonObject): String =
        def.upsertKey.joinToString("|") { row.getValue(it).jsonPrimitive.content }

    /**
     * Normalises an ISO-8601 date or date-time (`2026-10-19`, `2026-10-18T17:00Z`, `...T17:00:05.5Z`) to the
     * canonical UTC form PocketBase stores and returns for `date` fields: `2026-10-18 17:00:00.000Z`.
     */
    fun pbDate(iso: String): String {
        val day = iso.take(10)
        val time = iso.substringAfter('T', "").removeSuffix("Z").split(':')
        val seconds = time.getOrElse(2) { "0" }.split('.')
        val millis = seconds.getOrElse(1) { "0" }.padEnd(3, '0').take(3)
        fun two(i: Int) = time.getOrElse(i) { "0" }.padStart(2, '0')
        return "$day ${two(0)}:${two(1)}:${seconds[0].padStart(2, '0')}.${millis}Z"
    }

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
            "dt_kickoff_utc" to pbDate(g.date), "str_home_code" to g.home, "str_away_code" to g.away, "bool_neutral_site" to g.neutral,
            "bool_is_final" to g.isFinal, "int_home_score" to g.homeScore, "int_away_score" to g.awayScore,
        )
    }

    fun runRecords(report: SimulationReport, epochMs: Long): List<PbRecord> {
        val runId = runId(report)
        val ranked = report.byChampionOdds
        val top = ranked.first()
        val run = row(
            runs, epochMs, emptyMap(),
            "str_run_id" to runId, "dt_run_date" to pbDate(report.generatedAt), "int_season_year" to report.season,
            "int_through_week" to report.throughWeek, "int_games_played" to report.gamesPlayed,
            "int_games_remaining" to report.gamesRemaining, "int_total_iterations" to report.iterations,
            "int_random_seed" to report.seed, "str_engine_version" to ENGINE_VERSION,
            "str_top_favorite_code" to top.abbr, "dbl_top_favorite_prob" to top.pChampion,
        )
        val outlookRows = ranked.mapIndexed { i, t ->
            row(
                outlooks, epochMs, mapOf("rel_run" to runId, "rel_team" to t.abbr),
                "str_run_id" to runId, "str_team_code" to t.abbr, "dt_run_date" to pbDate(report.generatedAt),
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
