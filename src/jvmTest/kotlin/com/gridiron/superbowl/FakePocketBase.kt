package com.gridiron.superbowl

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets.UTF_8
import java.util.concurrent.atomic.AtomicInteger

/**
 * In-memory stand-in for a PocketBase >= 0.23 instance: superuser auth, collections CRUD, and record
 * list/create/update with the filter subset this project uses. Like PocketBase, it rejects unknown columns,
 * stores blank numbers/booleans as 0/false, enforces relations (required + target row must exist), relation targets
 * on schema changes, and that every index only names existing columns.
 */
class FakePocketBase(private val user: String = "admin@example.com", private val password: String = "s3cret") : AutoCloseable {
    val collections = linkedMapOf<String, JsonObject>()
    val records = mutableMapOf<String, MutableList<JsonObject>>()
    val failNext = AtomicInteger(0)
    @Volatile var failStatus = 503
    val requests = mutableListOf<String>()
    private var nextId = 0
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    val url: String get() = "http://127.0.0.1:${server.address.port}"

    init {
        server.createContext("/") { ex -> runCatching { handle(ex) }.onFailure { respond(ex, 500, """{"message":"${it.message}"}""") } }
        server.start()
    }

    override fun close() = server.stop(0)

    private fun id(prefix: String) = "$prefix${(nextId++).toString().padStart(12, '0')}"

    private fun respond(ex: HttpExchange, status: Int, body: String) {
        val bytes = body.toByteArray()
        ex.sendResponseHeaders(status, if (bytes.isEmpty()) -1 else bytes.size.toLong())
        if (bytes.isNotEmpty()) ex.responseBody.use { it.write(bytes) } else ex.close()
    }

    private fun body(ex: HttpExchange): JsonObject = Json.parseToJsonElement(ex.requestBody.readBytes().decodeToString()).jsonObject

    private fun withFieldIds(fields: JsonArray) = JsonArray(fields.map { f ->
        val o = f.jsonObject
        if ("id" in o) o else JsonObject(o + ("id" to JsonPrimitive("f_" + o.getValue("name").jsonPrimitive.content)))
    })

    private fun find(idOrName: String) = collections.values.firstOrNull { it["id"]?.jsonPrimitive?.content == idOrName || it["name"]?.jsonPrimitive?.content == idOrName }

    private val indexColumns = Regex("""\(([^)]*)\)""")

    private fun schemaError(c: JsonObject): String? {
        val fieldNames = c.getValue("fields").jsonArray.map { it.jsonObject.getValue("name").jsonPrimitive.content }.toSet() + "id"
        val badIndex = c["indexes"]?.jsonArray.orEmpty().map { it.jsonPrimitive.content }.firstOrNull { sql ->
            indexColumns.find(sql)!!.groupValues[1].split(",").map { it.trim().trim('`') }.any { it !in fieldNames }
        }
        val badRelation = c.getValue("fields").jsonArray.map { it.jsonObject }
            .firstOrNull { it["type"]?.jsonPrimitive?.content == "relation" && find(it.getValue("collectionId").jsonPrimitive.content) == null }
        return badIndex?.let { "index references unknown column: $it" } ?: badRelation?.let { "relation to unknown collection" }
    }

    private fun relationError(c: JsonObject, data: JsonObject, partial: Boolean): String? =
        c.getValue("fields").jsonArray.map { it.jsonObject }.filter { it["type"]?.jsonPrimitive?.content == "relation" }.firstNotNullOfOrNull { f ->
            val name = f.getValue("name").jsonPrimitive.content
            val value = (data[name] as? JsonPrimitive)?.content.orEmpty()
            val target = find(f.getValue("collectionId").jsonPrimitive.content)!!.getValue("name").jsonPrimitive.content
            when {
                value.isEmpty() && (partial && name !in data) -> null
                value.isEmpty() -> if (f["required"]?.jsonPrimitive?.content == "true") "$name is required" else null
                records[target].orEmpty().none { it["id"]?.jsonPrimitive?.content == value } -> "$name points at missing $target row $value"
                else -> null
            }
        }

    private var clock = 0

    /** Mimics PocketBase `autodate` fields: `created` on insert, `updated` on every write. */
    private fun autodates(c: JsonObject, created: Boolean): Map<String, JsonElement> {
        val now = JsonPrimitive("2026-10-19 12:00:%02d.000Z".format(clock++ % 60))
        return c.getValue("fields").jsonArray.map { it.jsonObject }.filter { it["type"]?.jsonPrimitive?.content == "autodate" }
            .filter { created || it["onUpdate"]?.jsonPrimitive?.content == "true" }
            .associate { it.getValue("name").jsonPrimitive.content to now }
    }

    private fun fieldsOf(c: JsonObject) = c.getValue("fields").jsonArray.associate {
        it.jsonObject.getValue("name").jsonPrimitive.content to it.jsonObject.getValue("type").jsonPrimitive.content
    }

    private fun matches(row: JsonObject, filter: String?): Boolean = filter == null ||
        filter.replace("(", "").replace(")", "").split(" && ").all { clause ->
            val m = Regex("""(\w+)(!=|=)(?:'(.*)'|(.*))""").matchEntire(clause.trim())!!
            val (field, op) = m.destructured
            val expected = m.groupValues[3].ifEmpty { m.groupValues[4] }
            val actual = (row[field] as? JsonPrimitive)?.content ?: ""
            val equal = actual == expected || (actual.toDoubleOrNull() != null && actual.toDoubleOrNull() == expected.toDoubleOrNull())
            if (op == "=") equal else !equal
        }

    private fun handle(ex: HttpExchange) {
        val method = ex.requestMethod
        val path = ex.requestURI.rawPath
        val query = ex.requestURI.rawQuery.orEmpty().split("&").filter { "=" in it }
            .associate { it.substringBefore("=") to URLDecoder.decode(it.substringAfter("="), UTF_8) }
        synchronized(requests) { requests += "$method $path" }
        if (failNext.getAndUpdate { if (it > 0) it - 1 else 0 } > 0) return respond(ex, failStatus, """{"message":"injected"}""")
        if (path == "/api/health") return respond(ex, 200, """{"code":200,"message":"API is healthy.","data":{}}""")
        if (path == "/api/collections/_superusers/auth-with-password") {
            val b = body(ex)
            val ok = b["identity"]?.jsonPrimitive?.content == user && b["password"]?.jsonPrimitive?.content == password
            return if (ok) respond(ex, 200, """{"token":"tok"}""") else respond(ex, 400, """{"message":"Failed to authenticate."}""")
        }
        if (ex.requestHeaders.getFirst("Authorization") != "Bearer tok") return respond(ex, 401, """{"message":"unauthorized"}""")

        val parts = path.removePrefix("/api/collections").trim('/').split("/").filter { it.isNotEmpty() }
        when {
            parts.isEmpty() && method == "POST" -> {
                val b = body(ex)
                val name = b.getValue("name").jsonPrimitive.content
                val stored = JsonObject(b + ("id" to (b["id"] ?: JsonPrimitive(id("pbc_")))) + ("fields" to withFieldIds(b["fields"]?.jsonArray ?: JsonArray(emptyList()))))
                schemaError(stored)?.let { return respond(ex, 400, """{"message":"$it"}""") }
                collections[name] = stored
                records[name] = mutableListOf()
                respond(ex, 200, stored.toString())
            }
            parts.size == 1 && method == "GET" -> find(parts[0])?.let { respond(ex, 200, it.toString()) } ?: respond(ex, 404, """{"message":"missing"}""")
            parts.size == 1 && method == "PATCH" -> {
                val c = find(parts[0]) ?: return respond(ex, 404, "{}")
                val b = body(ex)
                val updated = JsonObject(c + b + (b["fields"]?.let { mapOf("fields" to withFieldIds(it.jsonArray)) } ?: emptyMap()))
                schemaError(updated)?.let { return respond(ex, 400, """{"message":"$it"}""") }
                collections[c.getValue("name").jsonPrimitive.content] = updated
                respond(ex, 200, updated.toString())
            }
            parts.size >= 2 && parts[1] == "records" -> {
                val c = find(parts[0]) ?: return respond(ex, 404, "{}")
                val name = c.getValue("name").jsonPrimitive.content
                val rows = records.getValue(name)
                val types = fieldsOf(c)
                fun normalize(data: JsonObject): JsonObject {
                    val unknown = data.keys - types.keys - "id"
                    require(data.keys.none { types[it] == "autodate" }) { "autodate fields are server-managed" }
                    require(unknown.isEmpty()) { "unknown fields $unknown" }
                    return JsonObject(data.mapValues { (k, v) ->
                        if (v !is JsonNull) v else when (types[k]) { "number" -> JsonPrimitive(0); "bool" -> JsonPrimitive(false); else -> JsonPrimitive("") }
                    })
                }
                when {
                    method == "GET" -> {
                        val all = rows.filter { matches(it, query["filter"]) }
                        val per = query["perPage"]?.toInt() ?: 30
                        val page = query["page"]?.toInt() ?: 1
                        val items = all.drop((page - 1) * per).take(per)
                        respond(ex, 200, """{"page":$page,"perPage":$per,"items":${JsonArray(items)}}""")
                    }
                    method == "POST" -> runCatching { normalize(body(ex)).also { d -> relationError(c, d, partial = false)?.let(::error) } }.fold(
                        { data -> JsonObject(data + ("id" to JsonPrimitive(id("r"))) + autodates(c, created = true)).also { rows += it }.let { respond(ex, 200, it.toString()) } },
                        { respond(ex, 400, """{"message":"${it.message}"}""") },
                    )
                    method == "PATCH" -> {
                        val idx = rows.indexOfFirst { it["id"]?.jsonPrimitive?.content == parts[2] }
                        runCatching { normalize(body(ex)).also { d -> relationError(c, d, partial = true)?.let(::error) } }.fold(
                            { data -> rows[idx] = JsonObject(rows[idx] + data + autodates(c, created = false)); respond(ex, 200, rows[idx].toString()) },
                            { respond(ex, 400, """{"message":"${it.message}"}""") },
                        )
                    }
                    else -> respond(ex, 405, "{}")
                }
            }
            else -> respond(ex, 404, "{}")
        }
    }

    fun rows(collection: String): List<JsonObject> = records[collection].orEmpty()
    fun value(row: JsonObject, field: String): String = (row[field] as JsonElement).jsonPrimitive.content
}
