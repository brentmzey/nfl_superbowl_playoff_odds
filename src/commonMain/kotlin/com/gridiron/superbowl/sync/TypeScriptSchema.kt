package com.gridiron.superbowl.sync

/**
 * Generates `clients/ts/src/generated/schema.ts` from [PocketBaseSchema]: one interface per collection/view
 * (semantic names, snake_case fields exactly as stored), a collection-name map, per-field runtime specs used by
 * the client's validator, and the migration fingerprints the schema was generated from.
 */
object TypeScriptSchema {
    const val PATH = "clients/ts/src/generated/schema.ts"

    private fun tsType(f: FieldDef): String = f.tsType ?: when (f.type) {
        FieldType.TEXT, FieldType.DATE -> "string"
        FieldType.NUMBER -> "number"
        FieldType.BOOL -> "boolean"
        FieldType.JSON -> "unknown"
    }

    private fun kind(f: FieldDef): String = f.type.wire

    private fun doc(f: FieldDef): String = when (f.type) {
        FieldType.DATE -> "  /** UTC, PocketBase form `YYYY-MM-DD HH:MM:SS.sssZ`. */\n"
        else -> ""
    }

    private fun spec(name: String, kind: String, required: Boolean) = "    $name: { kind: '$kind', required: $required },"

    private fun collection(c: CollectionDef): String = buildString {
        appendLine("/** `${c.name}`. Upsert key: ${c.upsertKey.joinToString { "`$it`" }}. */")
        appendLine("export interface ${c.typeName} extends RecordMeta, Timestamps {")
        c.fields.forEach { append(doc(it)); appendLine("  ${it.name}: ${tsType(it)};") }
        c.relations.forEach { appendLine("  /** FK → `${it.target}.id`. */\n  ${it.name}: string;") }
        append("}")
    }

    private fun view(v: ViewDef): String = buildString {
        appendLine("/** `${v.name}` (read-only view). */")
        appendLine("export interface ${v.typeName} extends RecordMeta {")
        v.columns.forEach { append(doc(it.field)); appendLine("  ${it.field.name}: ${tsType(it.field)};") }
        append("}")
    }

    fun generate(): String {
        val all = listOf(PocketBaseSchema.migrationsLedger) + PocketBaseSchema.collections
        val names = all.map { it.typeName to it.name } + PocketBaseSchema.views.map { it.typeName to it.name }
        val specs = all.map { c ->
            c.name to (c.fields.map { spec(it.name, kind(it), it.required) } +
                c.relations.map { spec(it.name, "relation", true) } +
                listOf(spec("created", "autodate", true), spec("updated", "autodate", true)))
        } + PocketBaseSchema.views.map { v -> v.name to v.columns.map { spec(it.field.name, kind(it.field), it.field.required) } }

        return buildString {
            appendLine("// AUTO-GENERATED from PocketBaseSchema.kt by the Kotlin pipeline (`just refresh` / `just schema-ts`).")
            appendLine("// Do not edit by hand: `TypeScriptSchemaTest` fails the Kotlin build if this file drifts.")
            appendLine()
            appendLine("export type Conference = 'AFC' | 'NFC';")
            appendLine("export type SeasonPhase = 'REGULAR' | 'POSTSEASON';")
            appendLine("export type StatusCode = 'ACTIVE' | 'INACTIVE' | 'SUPERSEDED' | 'ARCHIVED';")
            appendLine("export type FieldKind = 'text' | 'number' | 'bool' | 'json' | 'date' | 'relation' | 'autodate';")
            appendLine()
            appendLine("export interface FieldSpec {")
            appendLine("  readonly kind: FieldKind;")
            appendLine("  readonly required: boolean;")
            appendLine("}")
            appendLine()
            appendLine("/** Metadata PocketBase adds to every record. */")
            appendLine("export interface RecordMeta {")
            appendLine("  id: string;")
            appendLine("  collectionId: string;")
            appendLine("  collectionName: string;")
            appendLine("}")
            appendLine()
            appendLine("/** Server-managed `autodate` columns. */")
            appendLine("export interface Timestamps {")
            appendLine("  created: string;")
            appendLine("  updated: string;")
            appendLine("}")
            appendLine()
            all.forEach { appendLine(collection(it)); appendLine() }
            PocketBaseSchema.views.forEach { appendLine(view(it)); appendLine() }
            appendLine("export const Collections = {")
            names.forEach { (t, n) -> appendLine("  $t: '$n',") }
            appendLine("} as const;")
            appendLine()
            appendLine("export type CollectionName = (typeof Collections)[keyof typeof Collections];")
            appendLine()
            appendLine("export interface CollectionRecords {")
            names.forEach { (t, n) -> appendLine("  $n: $t;") }
            appendLine("}")
            appendLine()
            appendLine("export const FIELD_SPECS: { readonly [C in CollectionName]: Readonly<Record<string, FieldSpec>> } = {")
            specs.forEach { (n, lines) ->
                appendLine("  $n: {")
                lines.forEach { appendLine("  $it") }
                appendLine("  },")
            }
            appendLine("};")
            appendLine()
            appendLine("/** Migration step fingerprints this file was generated from (compare with `tbl_schema_migrations`). */")
            appendLine("export const SCHEMA_FINGERPRINTS: Readonly<Record<string, string>> = {")
            PocketBaseSchema.migrations.forEach { appendLine("  '${it.id}': '${it.fingerprint}',") }
            appendLine("};")
        }
    }
}
