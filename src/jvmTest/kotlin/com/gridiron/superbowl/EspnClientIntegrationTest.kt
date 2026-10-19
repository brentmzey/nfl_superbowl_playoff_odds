package com.gridiron.superbowl

import com.gridiron.superbowl.data.CachingSource
import com.gridiron.superbowl.data.EspnClient
import com.gridiron.superbowl.data.EspnHttpSource
import com.gridiron.superbowl.data.ScoreboardSource
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Exercises the real HTTP client against a local server replaying a captured ESPN payload. */
class EspnClientIntegrationTest {
    private val week6 = javaClass.getResource("/espn_2026_week6.json")!!.readText()
    private val requests = mutableListOf<String>()
    private lateinit var server: HttpServer
    private lateinit var baseUrl: String

    @BeforeTest
    fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/scoreboard") { ex ->
                val query = ex.requestURI.query
                synchronized(requests) { requests += query }
                val (status, body) = when {
                    query.contains("dates=1999") -> 503 to "down"
                    query.contains("seasontype=2&week=6") -> 200 to week6
                    else -> 200 to """{"events":[]}"""
                }
                val bytes = body.toByteArray()
                ex.sendResponseHeaders(status, bytes.size.toLong())
                ex.responseBody.use { it.write(bytes) }
            }
            start()
        }
        baseUrl = "http://127.0.0.1:${server.address.port}/scoreboard"
    }

    @AfterTest
    fun stop() = server.stop(0)

    @Test
    fun fetchesEveryWeekAndParsesTheCapturedSlate() {
        val games = EspnClient(EspnHttpSource(baseUrl)).season(2026, includePostseason = false).getOrThrow()
        assertEquals(18, requests.size)
        assertTrue(requests.contains("dates=2026&seasontype=2&week=1"))
        assertEquals(14, games.size)
        assertEquals(13, games.count { it.isFinal })
        val gb = games.first { it.home == "GB" }
        assertEquals("DAL", gb.away)
        assertEquals(10, gb.homeMargin)
        assertTrue(games.first { it.home == "JAX" }.neutral)
    }

    @Test
    fun includesPostseasonWeeksWhenAsked() {
        EspnClient(EspnHttpSource(baseUrl)).season(2025, includePostseason = true).getOrThrow()
        assertEquals(23, requests.size)
        assertTrue(requests.contains("dates=2025&seasontype=3&week=5"))
    }

    @Test
    fun httpErrorsSurfaceAsFailures() {
        val result = EspnClient(EspnHttpSource(baseUrl)).season(1999, includePostseason = false)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("HTTP 503"))
    }

    @Test
    fun cachingSourceReusesCompletedSeasonsOnly() {
        val dir = Files.createTempDirectory("espn-cache")
        val calls = AtomicInteger()
        val counting = ScoreboardSource { _, _, _ -> calls.incrementAndGet(); Result.success("""{"events":[]}""") }
        val cache = CachingSource(counting, dir, currentSeason = 2026)
        repeat(2) { cache.fetch(2025, 2, 1).getOrThrow() }
        assertEquals(1, calls.get())
        repeat(2) { cache.fetch(2026, 2, 1).getOrThrow() }
        assertEquals(3, calls.get())
        assertTrue(Files.exists(dir.resolve("espn_2025_2_1.json")))
    }
}
