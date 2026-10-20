package com.gridiron.superbowl.replica

import com.gridiron.superbowl.sync.CollectionDef
import com.gridiron.superbowl.sync.FieldType
import com.gridiron.superbowl.sync.PocketBaseClient
import com.gridiron.superbowl.sync.PocketBaseSchema
import com.gridiron.superbowl.sync.PocketHostConfig
import com.gridiron.superbowl.sync.SqlDialect
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.Types
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

/** Every row of every PocketHost collection at one moment. PocketHost is the single source of truth. */
data class PocketHostSnapshot(val sourceUrl: String, val takenAt: Instant, val rows: Map<String, List<JsonObject>>) {
    fun count(collection: String): Int = rows[collection].orEmpty().size

    companion object {
        /** Ledger first, then collections in dependency order (so replicas can insert with FKs enforced). */
        val collections: List<CollectionDef> = listOf(PocketBaseSchema.migrationsLedger) + PocketBaseSchema.collections

        fun pull(client: PocketBaseClient, sourceUrl: String, now: Instant = Instant.now()): PocketHostSnapshot =
            PocketHostSnapshot(sourceUrl, now, collections.associate { it.name to client.list(it.name) })
    }
}

/**
 * Timestamped JSON backups of a [PocketHostSnapshot]: one file per collection plus a manifest, under
 * `backups/pockethost/<UTC timestamp>/`. The newest [keep] backups are retained and older ones are pruned.
 */
class BackupStore(private val dir: Path, private val keep: Int = 30) {
    private val json = Json { prettyPrint = true }
    private val stamp = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)

    fun write(snapshot: PocketHostSnapshot): Path {
        val target = dir.resolve(stamp.format(snapshot.takenAt))
        Files.createDirectories(target)
        snapshot.rows.forEach { (name, rows) -> Files.writeString(target.resolve("$name.json"), json.encodeToString(JsonArray.serializer(), JsonArray(rows))) }
        val manifest = buildJsonObject {
            put("str_source_url", snapshot.sourceUrl)
            put("dt_taken_at_utc", snapshot.takenAt.toString())
            put("json_row_counts", JsonObject(snapshot.rows.mapValues { JsonPrimitive(it.value.size) }))
            put("json_migrations", JsonObject(PocketBaseSchema.migrations.associate { it.id to JsonPrimitive(it.fingerprint) }))
        }
        Files.writeString(target.resolve("manifest.json"), json.encodeToString(JsonObject.serializer(), manifest))
        prune()
        return target
    }

    private fun prune() {
        dir.listDirectoryEntries().filter { it.isDirectory() }.sortedByDescending { it.name }.drop(keep).forEach { old ->
            Files.walk(old).sorted(Comparator.reverseOrder()).forEach(Files::delete)
        }
    }

    fun list(): List<Path> = if (Files.exists(dir)) dir.listDirectoryEntries().filter { it.isDirectory() }.sorted() else emptyList()

    /** Reads a backup back into a snapshot (for restores or re-building replicas offline). */
    fun read(backup: Path): PocketHostSnapshot {
        val manifest = Json.parseToJsonElement(Files.readString(backup.resolve("manifest.json"))).jsonObject
        val rows = PocketHostSnapshot.collections.associate { c ->
            c.name to backup.resolve("${c.name}.json").let { f ->
                if (Files.exists(f)) (Json.parseToJsonElement(Files.readString(f)) as JsonArray).map { it.jsonObject } else emptyList()
            }
        }
        return PocketHostSnapshot(
            manifest.getValue("str_source_url").jsonPrimitive.content,
            Instant.parse(manifest.getValue("dt_taken_at_utc").jsonPrimitive.content),
            rows,
        )
    }
}

/** Rebuilds a relational replica from scratch (never patched): DDL from the shared schema, then every row. */
object JdbcReplica {
    fun rebuild(conn: Connection, dialect: SqlDialect, snapshot: PocketHostSnapshot): Map<String, Int> {
        conn.autoCommit = false
        return runCatching {
            conn.createStatement().use { st -> dialect.ddl(PocketHostSnapshot.collections, PocketBaseSchema.views).forEach(st::execute) }
            val counts = PocketHostSnapshot.collections.associate { def ->
                conn.prepareStatement(dialect.insert(def)).use { ps ->
                    snapshot.rows[def.name].orEmpty().forEach { row -> bind(ps, dialect, def, row); ps.addBatch() }
                    def.name to ps.executeBatch().size
                }
            }
            conn.commit()
            counts
        }.onFailure { conn.rollback() }.getOrThrow()
    }

    private enum class Kind { TIMESTAMP, JSON, BOOL, INT, DOUBLE, TEXT }

    private fun kind(def: CollectionDef, column: String): Kind = when (def.fields.firstOrNull { it.name == column }?.type) {
        FieldType.DATE -> Kind.TIMESTAMP
        FieldType.JSON -> Kind.JSON
        FieldType.BOOL -> Kind.BOOL
        FieldType.NUMBER -> if (column.startsWith("int_")) Kind.INT else Kind.DOUBLE
        FieldType.TEXT -> Kind.TEXT
        null -> if (column in SqlDialect.SYSTEM_TIMESTAMPS) Kind.TIMESTAMP else Kind.TEXT // id, rel_*
    }

    private fun bind(ps: PreparedStatement, dialect: SqlDialect, def: CollectionDef, row: JsonObject) {
        dialect.columns(def).forEachIndexed { i, column ->
            val idx = i + 1
            val value = row[column]?.takeUnless { it is JsonNull }
            val text = (value as? JsonPrimitive)?.content
            when {
                value == null -> ps.setNull(idx, Types.NULL)
                else -> when (kind(def, column)) {
                    // PocketBase stores an unset date as "" -> SQL NULL.
                    Kind.TIMESTAMP -> when {
                        text.isNullOrBlank() -> ps.setNull(idx, Types.NULL)
                        dialect == SqlDialect.POSTGRES -> ps.setObject(idx, OffsetDateTime.ofInstant(Instant.parse(text.replace(' ', 'T')), ZoneOffset.UTC))
                        else -> ps.setString(idx, text)
                    }
                    Kind.JSON -> ps.setString(idx, value.toString())
                    Kind.BOOL -> text.toBoolean().let { b -> if (dialect == SqlDialect.POSTGRES) ps.setBoolean(idx, b) else ps.setInt(idx, if (b) 1 else 0) }
                    Kind.INT -> ps.setLong(idx, text!!.toDouble().toLong())
                    Kind.DOUBLE -> ps.setDouble(idx, text!!.toDouble())
                    Kind.TEXT -> ps.setString(idx, text)
                }
            }
        }
    }
}

object SqliteReplica {
    /** Deletes and recreates the SQLite file with foreign keys enforced. */
    fun rebuild(file: Path, snapshot: PocketHostSnapshot): Map<String, Int> {
        Files.createDirectories(file.parent)
        Files.deleteIfExists(file)
        return DriverManager.getConnection("jdbc:sqlite:$file").use { conn ->
            conn.createStatement().use { it.execute("PRAGMA foreign_keys = ON") }
            JdbcReplica.rebuild(conn, SqlDialect.SQLITE, snapshot)
        }
    }
}

/** Local PostgreSQL target (defaults match `~/personal/local-db-stack`: localhost:15432, local_user). */
data class PostgresConfig(
    val host: String,
    val port: Int,
    val user: String,
    val password: String,
    val database: String,
    val schema: String,
) {
    override fun toString(): String = "PostgresConfig($user@$host:$port/$database.$schema, password=***)"

    fun url(db: String): String = "jdbc:postgresql://$host:$port/$db"

    companion object {
        fun resolve(env: Map<String, String>, dotEnvFiles: List<Path>): PostgresConfig {
            val merged = dotEnvFiles.reversed().fold(emptyMap<String, String>()) { acc, f -> acc + PocketHostConfig.parseDotEnv(f) } + env
            fun v(key: String, default: String) = merged[key]?.takeIf { it.isNotBlank() } ?: default
            return PostgresConfig(
                host = v("LOCAL_PG_HOST", "localhost"),
                port = v("LOCAL_PG_PORT", "15432").toInt(),
                user = v("LOCAL_PG_USER", "local_user"),
                password = v("LOCAL_PG_PASSWORD", "local_password"),
                database = v("NFL_PG_DB", "nfl_superbowl_playoff_odds"),
                schema = v("NFL_PG_SCHEMA", "nfl"),
            )
        }
    }
}

/**
 * Provisions the target database with the shared DbFactory rule (`~/.gemini/config/rules/db_factory.md`):
 * connect to the `postgres` maintenance DB, check `pg_database`, create with explicit owner/template/encoding/
 * locale/connection limit if missing, then grant all privileges to the owner.
 */
object DbFactory {
    private val safeName = Regex("^[a-z_][a-z0-9_]*$")

    fun createStatement(database: String, owner: String): String {
        require(safeName.matches(database) && safeName.matches(owner)) { "Unsafe database or owner name" }
        return "CREATE DATABASE $database WITH OWNER = $owner TEMPLATE template0 ENCODING = 'UTF8' " +
            "LC_COLLATE = 'en_US.UTF-8' LC_CTYPE = 'en_US.UTF-8' CONNECTION LIMIT = -1"
    }

    /** Returns true when the database had to be created. */
    fun ensureDatabase(cfg: PostgresConfig): Boolean =
        DriverManager.getConnection(cfg.url("postgres"), cfg.user, cfg.password).use { conn ->
            val exists = conn.prepareStatement("SELECT 1 FROM pg_database WHERE datname = ?").use { ps ->
                ps.setString(1, cfg.database)
                ps.executeQuery().use { it.next() }
            }
            if (!exists) conn.createStatement().use { st ->
                st.execute(createStatement(cfg.database, cfg.user))
                st.execute("GRANT ALL PRIVILEGES ON DATABASE ${cfg.database} TO ${cfg.user}")
            }
            !exists
        }
}

object PostgresReplica {
    data class Result(val created: Boolean, val counts: Map<String, Int>)

    /** DbFactory, then drop + recreate the project schema and load the snapshot (rebuilt, never patched). */
    fun rebuild(cfg: PostgresConfig, snapshot: PocketHostSnapshot): Result {
        val created = DbFactory.ensureDatabase(cfg)
        val counts = DriverManager.getConnection(cfg.url(cfg.database), cfg.user, cfg.password).use { conn ->
            val schema = SqlDialect.POSTGRES.ident(cfg.schema)
            conn.createStatement().use { st ->
                st.execute("DROP SCHEMA IF EXISTS $schema CASCADE")
                st.execute("CREATE SCHEMA $schema AUTHORIZATION ${SqlDialect.POSTGRES.ident(cfg.user)}")
                st.execute("SET search_path TO $schema")
            }
            JdbcReplica.rebuild(conn, SqlDialect.POSTGRES, snapshot)
        }
        return Result(created, counts)
    }
}
