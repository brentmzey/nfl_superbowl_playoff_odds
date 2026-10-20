package com.gridiron.superbowl.sync

/**
 * DDL for the local replicas (SQLite file and PostgreSQL). Both are generated from the same
 * [PocketBaseSchema] definitions as PocketHost, so all three stores share tables, keys and indexes:
 * `id` primary key (the PocketBase record id), `rel_` columns as real `REFERENCES` foreign keys, and the same
 * unique/query/partial indexes.
 */
enum class SqlDialect {
    SQLITE,
    POSTGRES;

    fun ident(name: String): String = "\"$name\""

    fun columnType(f: FieldDef): String = when (f.type) {
        FieldType.TEXT -> "TEXT"
        FieldType.BOOL -> if (this == POSTGRES) "BOOLEAN" else "INTEGER"
        FieldType.JSON -> if (this == POSTGRES) "JSONB" else "TEXT"
        FieldType.NUMBER -> when {
            f.name.startsWith("int_") -> if (this == POSTGRES) "BIGINT" else "INTEGER"
            else -> if (this == POSTGRES) "DOUBLE PRECISION" else "REAL"
        }
    }

    fun createTable(def: CollectionDef): String {
        val columns = listOf("${ident("id")} TEXT PRIMARY KEY") +
            def.fields.map { f -> "${ident(f.name)} ${columnType(f)}${if (f.required) " NOT NULL" else ""}" } +
            def.relations.map { r -> "${ident(r.name)} TEXT NOT NULL REFERENCES ${ident(r.target)} (${ident("id")})" }
        return "CREATE TABLE ${ident(def.name)} (\n  ${columns.joinToString(",\n  ")}\n)"
    }

    fun createIndex(def: CollectionDef, index: IndexDef): String =
        "CREATE ${if (index.unique) "UNIQUE " else ""}INDEX ${ident(index.name(def.name))} ON ${ident(def.name)} " +
            "(${index.columns.joinToString(", ") { ident(it) }})" + index.where?.let { " WHERE $it" }.orEmpty()

    fun createView(view: ViewDef): String = "CREATE VIEW ${ident(view.name)} AS\n${view.query}"

    /** Tables + indexes + views, in dependency order. */
    fun ddl(collections: List<CollectionDef>, views: List<ViewDef>): List<String> =
        collections.map(::createTable) + collections.flatMap { c -> c.indexes.map { createIndex(c, it) } } + views.map(::createView)

    fun columns(def: CollectionDef): List<String> = listOf("id") + def.fields.map { it.name } + def.relations.map { it.name }

    fun insert(def: CollectionDef): String {
        val cols = columns(def)
        val placeholders = cols.joinToString(", ") { col ->
            if (this == POSTGRES && def.fields.any { it.name == col && it.type == FieldType.JSON }) "CAST(? AS JSONB)" else "?"
        }
        return "INSERT INTO ${ident(def.name)} (${cols.joinToString(", ") { ident(it) }}) VALUES ($placeholders)"
    }
}
