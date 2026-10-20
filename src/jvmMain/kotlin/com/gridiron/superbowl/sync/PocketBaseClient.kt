package com.gridiron.superbowl.sync

import com.gridiron.superbowl.model.Game
import com.gridiron.superbowl.report.SimulationReport
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import kotlin.random.Random

/**
 * Connection settings for a PocketBase instance hosted on PocketHost (`https://<instance>.pockethost.io`).
 * Credentials come from the environment or a git-ignored `.env`; they are never written to disk by this code.
 */
data class PocketHostConfig(val baseUrl: String, val user: String, val password: String) {
    override fun toString(): String = "PocketHostConfig(baseUrl=$baseUrl, user=$user, password=***)"

    companion object {
        /** Current names first, then the older `POCKETHOST_ADMIN_*` aliases used by the MLB repo. */
        private val URL_KEYS = listOf("POCKETHOST_URL")
        private val USER_KEYS = listOf("POCKETHOST_USER", "POCKETHOST_ADMIN_EMAIL")
        private val PASSWORD_KEYS = listOf("POCKETHOST_PASSWORD", "POCKETHOST_ADMIN_PASSWORD")

        /**
         * Resolves settings from env vars, which win over `.env` files (earlier files win over later ones).
         * Returns a failure naming the missing keys, never their values, and there is no default instance URL.
         */
        fun resolve(env: Map<String, String>, dotEnvFiles: List<Path>): Result<PocketHostConfig> {
            val merged = dotEnvFiles.reversed().fold(emptyMap<String, String>()) { acc, f -> acc + parseDotEnv(f) } + env
            fun first(keys: List<String>) = keys.firstNotNullOfOrNull { k -> merged[k]?.takeIf { it.isNotBlank() } }
            val url = first(URL_KEYS)
            val user = first(USER_KEYS)
            val password = first(PASSWORD_KEYS)
            val missing = listOf(URL_KEYS to url, USER_KEYS to user, PASSWORD_KEYS to password).filter { it.second == null }.map { it.first.first() }
            return if (url != null && user != null && password != null) Result.success(PocketHostConfig(url.trimEnd('/'), user, password))
            else Result.failure(IllegalStateException("Missing PocketHost settings: ${missing.joinToString()} (copy .env.example to .env)"))
        }

        fun parseDotEnv(file: Path): Map<String, String> =
            if (!Files.exists(file)) emptyMap()
            else Files.readAllLines(file).map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") && '=' in it }
                .associate { line ->
                    val (k, v) = line.removePrefix("export ").split("=", limit = 2)
                    k.trim() to v.trim().removeSurrounding("\"").removeSurrounding("'")
                }
    }
}

data class SyncSummary(
    val collectionsCreated: List<String>,
    val fieldsAdded: Int,
    val indexesAdded: Int,
    val viewsChanged: Int,
    val migrationsRecorded: List<String>,
    val created: Int,
    val updated: Int,
    val unchanged: Int,
    val superseded: Int,
)

class PocketBaseException(val status: Int, message: String) : IOException(message)

/**
 * Exponential back-off with jitter for 429 / 5xx / IO failures (PocketHost instances can cold-start or time out).
 * [sleep] is injectable so tests run instantly.
 */
data class Backoff(
    val attempts: Int = 5,
    val initialMs: Long = 750,
    val factor: Double = 2.0,
    val jitter: Double = 0.2,
    val sleep: (Long) -> Unit = Thread::sleep,
) {
    fun <T> run(block: () -> T): T {
        tailrec fun go(attempt: Int, delay: Long): T {
            val result = runCatching(block)
            val error = result.exceptionOrNull() ?: return result.getOrThrow()
            val retryable = error !is PocketBaseException || error.status == 429 || error.status >= 500
            if (!retryable || attempt >= attempts) throw error
            sleep((delay * (1 + jitter * Random.nextDouble())).toLong())
            return go(attempt + 1, (delay * factor).toLong())
        }
        return go(1, initialMs)
    }
}

/**
 * Raw-REST PocketBase client (PocketBase >= 0.23, `_superusers` auth; token held in memory only), in the same
 * shape as the MLB and art-market repos: stepwise idempotent migrations with a ledger, upsert-by-natural-key,
 * exponential back-off.
 */
class PocketBaseClient(
    private val config: PocketHostConfig,
    private val http: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
    private val backoff: Backoff = Backoff(),
) {
    private val json = Json { ignoreUnknownKeys = true }
    private var token: String? = null

    private fun call(method: String, path: String, body: JsonElement? = null, auth: Boolean = true): JsonObject = backoff.run {
        val builder = HttpRequest.newBuilder(URI.create(config.baseUrl + path))
            .timeout(Duration.ofSeconds(30))
            .header("Content-Type", "application/json")
            .method(method, body?.let { HttpRequest.BodyPublishers.ofString(it.toString()) } ?: HttpRequest.BodyPublishers.noBody())
        if (auth) token?.let { builder.header("Authorization", "Bearer $it") }
        val response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            throw PocketBaseException(response.statusCode(), "$method $path -> HTTP ${response.statusCode()}: ${response.body().take(300)}")
        }
        json.parseToJsonElement(response.body().ifBlank { "{}" }).jsonObject
    }

    fun authenticate(): PocketBaseClient = apply {
        val creds = buildJsonObject { put("identity", config.user); put("password", config.password) }
        token = call("POST", "/api/collections/_superusers/auth-with-password", creds, auth = false)["token"]!!.jsonPrimitive.content
    }

    private fun collection(name: String): JsonObject? =
        runCatching { call("GET", "/api/collections/$name") }
            .fold({ it }, { e -> if (e is PocketBaseException && e.status == 404) null else throw e })

    private fun fieldNames(collection: JsonObject): Set<String> =
        collection.getValue("fields").jsonArray.map { it.jsonObject.getValue("name").jsonPrimitive.content }.toSet()

    /** Additive PATCH: keeps every existing field (with its id) and appends [extra]. Returns how many were added. */
    private fun addFields(collection: JsonObject, extra: List<JsonObject>): Int {
        val have = fieldNames(collection)
        val missing = extra.filter { it.getValue("name").jsonPrimitive.content !in have }
        if (missing.isNotEmpty()) {
            val fields = JsonArray(collection.getValue("fields").jsonArray + missing)
            call("PATCH", "/api/collections/${collection.getValue("id").jsonPrimitive.content}", buildJsonObject { put("fields", fields) })
        }
        return missing.size
    }

    data class SchemaResult(
        val collectionIds: Map<String, String>,
        val created: List<String>,
        val fieldsAdded: Int,
        val indexesAdded: Int,
        val viewsChanged: Int,
        val stepsRecorded: List<String>,
    )

    private fun idOf(json: JsonObject): String = json.getValue("id").jsonPrimitive.content

    /** Creates [def] with its base columns, or adds any base columns it is missing. Returns (json, created, added). */
    private fun ensureBase(def: CollectionDef): Triple<JsonObject, Boolean, Int> =
        collection(def.name)?.let { Triple(it, false, addFields(it, def.baseFieldsJson())) }
            ?: Triple(call("POST", "/api/collections", def.baseJson()), true, 0)

    private val indexName = Regex("""INDEX\s+[`"]?(\w+)""", RegexOption.IGNORE_CASE)

    /** Adds or rebuilds this collection's declared indexes by name; indexes it does not declare are kept. */
    private fun ensureIndexes(def: CollectionDef): Int {
        val current = collection(def.name)!!
        val existing = current.getValue("indexes").jsonArray.map { it.jsonPrimitive.content }
        val desired = def.indexSql()
        val desiredNames = desired.map { indexName.find(it)!!.groupValues[1] }.toSet()
        val merged = existing.filter { indexName.find(it)!!.groupValues[1] !in desiredNames } + desired
        val added = desired.count { it !in existing }
        if (added > 0) call("PATCH", "/api/collections/${idOf(current)}", buildJsonObject { put("indexes", JsonArray(merged.map(::JsonPrimitive))) })
        return added
    }

    private fun ensureView(view: ViewDef): Int =
        when (val current = collection(view.name)) {
            null -> call("POST", "/api/collections", view.json()).let { 1 }
            else -> if (current.getValue("viewQuery").jsonPrimitive.content == view.query) 0
            else call("PATCH", "/api/collections/${idOf(current)}", buildJsonObject { put("viewQuery", view.query) }).let { 1 }
        }

    /**
     * Stepwise, idempotent migrations (see [PocketBaseSchema.migrations]):
     * 000 bootstrap the `tbl_schema_migrations` ledger, 001 base tables, 002 foreign keys (`rel_` relations),
     * 003 indexes, 004 summary views. Each step reconciles toward its definition and is then recorded in the
     * ledger with its fingerprint. Nothing is ever dropped.
     */
    fun migrate(epochMs: Long = System.currentTimeMillis()): SchemaResult {
        val ledger = PocketBaseSchema.migrationsLedger
        val (_, ledgerCreated, _) = ensureBase(ledger)
        val ledgerIndexes = ensureIndexes(ledger)
        val defs = PocketBaseSchema.collections

        val step1 = defs.map { it to ensureBase(it) }
        val ids = step1.associate { (def, r) -> def.name to idOf(r.first) }
        val relationsAdded = defs.filter { it.relations.isNotEmpty() }.sumOf { def ->
            addFields(collection(def.name)!!, def.relations.map { def.relationJson(it, ids.getValue(it.target)) })
        }
        val indexesAdded = ledgerIndexes + defs.sumOf(::ensureIndexes)
        val viewsChanged = PocketBaseSchema.views.sumOf(::ensureView)

        val recorded = PocketBaseSchema.migrations.mapNotNull { step ->
            val row = PbRecord(ledger.name, JsonObject(mapOf(
                "str_migration_id" to JsonPrimitive(step.id),
                "str_description" to JsonPrimitive(step.description),
                "str_fingerprint" to JsonPrimitive(step.fingerprint),
                "int_applied_epoch_ms_utc" to JsonPrimitive(epochMs),
                "bool_is_active" to JsonPrimitive(true),
                "str_status_code" to JsonPrimitive(PocketBaseSchema.ACTIVE),
                "int_created_epoch_ms_utc" to JsonPrimitive(epochMs),
                "int_updated_epoch_ms_utc" to JsonPrimitive(epochMs),
            )))
            val existing = list(ledger.name, filterFor(listOf("str_migration_id" to JsonPrimitive(step.id)))).firstOrNull()
            when {
                existing == null -> call("POST", "/api/collections/${ledger.name}/records", row.data).let { step.id }
                existing.getValue("str_fingerprint").jsonPrimitive.content == step.fingerprint -> null
                else -> call("PATCH", "/api/collections/${ledger.name}/records/${idOf(existing)}",
                    JsonObject(row.data + existing.filterKeys { it == "int_created_epoch_ms_utc" })).let { step.id }
            }
        }
        val created = (if (ledgerCreated) listOf(ledger.name) else emptyList()) + step1.filter { it.second.second }.map { it.first.name }
        return SchemaResult(ids, created, step1.sumOf { it.second.third } + relationsAdded, indexesAdded, viewsChanged, recorded)
    }

    private fun filterFor(clauses: List<Pair<String, JsonElement>>, op: String = "="): String =
        clauses.joinToString(" && ", "(", ")") { (k, v) ->
            val p = v.jsonPrimitive
            if (p.isString) "$k$op'${p.content.replace("'", "\\'")}'" else "$k$op${p.content}"
        }

    /** Every row matching [filter] (all rows when null), paging through PocketBase's 500-row limit. */
    fun list(collection: String, filter: String? = null): List<JsonObject> {
        val query = filter?.let { "&filter=" + URLEncoder.encode(it, UTF_8) }.orEmpty()
        tailrec fun page(n: Int, acc: List<JsonObject>): List<JsonObject> {
            val body = call("GET", "/api/collections/$collection/records?page=$n&perPage=$PAGE_SIZE&sort=id$query")
            val items = body.getValue("items").jsonArray.map { it.jsonObject }
            return if (items.size < PAGE_SIZE) acc + items else page(n + 1, acc + items)
        }
        return page(1, emptyList())
    }

    private class Tally { var created = 0; var updated = 0; var unchanged = 0 }

    /**
     * Upserts [records] of one collection against the rows already stored. Unchanged rows are skipped, changed
     * rows are PATCHed (keeping their original creation time), and new rows are POSTed. Returns natural key -> id.
     */
    private fun upsertAll(def: CollectionDef, records: List<PbRecord>, ids: Map<String, Map<String, String>>, tally: Tally): Map<String, String> {
        val scope = if (def.name in PocketBaseSchema.runScoped) records.first().data.getValue("str_run_id") else null
        val existing = list(def.name, scope?.let { filterFor(listOf("str_run_id" to it)) })
            .associateBy { PocketBaseSchema.naturalKey(def, it) }
        val base = "/api/collections/${def.name}/records"
        return records.associate { record ->
            val relationIds = record.relations.mapValues { (field, key) ->
                val target = def.relations.first { it.name == field }.target
                JsonPrimitive(ids[target]?.get(key) ?: error("Unresolved relation ${def.name}.$field -> $target[$key]"))
            }
            val data = JsonObject(record.data + relationIds)
            val key = record.key(def)
            val current = existing[key]
            val id = when {
                current == null -> call("POST", base, data).getValue("id").jsonPrimitive.content.also { tally.created++ }
                PocketBaseSchema.sameContent(data, current) -> current.getValue("id").jsonPrimitive.content.also { tally.unchanged++ }
                else -> current.getValue("id").jsonPrimitive.content.also { recordId ->
                    call("PATCH", "$base/$recordId", JsonObject(data + current.filterKeys { it == "int_created_epoch_ms_utc" }))
                    tally.updated++
                }
            }
            key to id
        }
    }

    /** Marks earlier ACTIVE runs of the same season SUPERSEDED. Rows are never deleted. */
    private fun supersede(runId: String, season: Int, epochMs: Long): Int {
        val filter = filterFor(listOf("int_season_year" to JsonPrimitive(season), "str_status_code" to JsonPrimitive(PocketBaseSchema.ACTIVE))) +
            " && " + filterFor(listOf("str_run_id" to JsonPrimitive(runId)), op = "!=")
        val stale = list(PocketBaseSchema.runs.name, filter)
        stale.forEach { row ->
            call("PATCH", "/api/collections/${PocketBaseSchema.runs.name}/records/${row.getValue("id").jsonPrimitive.content}", buildJsonObject {
                put("str_status_code", PocketBaseSchema.SUPERSEDED)
                put("bool_is_active", false)
                put("int_updated_epoch_ms_utc", epochMs)
            })
        }
        return stale.size
    }

    /** Full refresh write: auth, migrate, then idempotently upsert teams, games, the run, and its outputs. */
    fun sync(report: SimulationReport, games: List<Game>, epochMs: Long): SyncSummary {
        authenticate()
        val schema = migrate(epochMs)
        val tally = Tally()
        val batches = listOf(
            PocketBaseSchema.teams to PocketBaseSchema.teamRecords(epochMs),
            PocketBaseSchema.games to PocketBaseSchema.gameRecords(games, epochMs),
        ) + PocketBaseSchema.runRecords(report, epochMs).groupBy { it.collection }.map { (c, rows) -> PocketBaseSchema.byName(c) to rows }
        batches.fold(emptyMap<String, Map<String, String>>()) { ids, (def, rows) ->
            ids + (def.name to upsertAll(def, rows, ids, tally))
        }
        val superseded = supersede(PocketBaseSchema.runId(report), report.season, epochMs)
        return SyncSummary(schema.created, schema.fieldsAdded, schema.indexesAdded, schema.viewsChanged, schema.stepsRecorded,
            tally.created, tally.updated, tally.unchanged, superseded)
    }

    fun health(): Boolean = runCatching { call("GET", "/api/health", auth = false)["code"] == JsonPrimitive(200) }.getOrDefault(false)

    companion object {
        const val PAGE_SIZE = 500
    }
}
