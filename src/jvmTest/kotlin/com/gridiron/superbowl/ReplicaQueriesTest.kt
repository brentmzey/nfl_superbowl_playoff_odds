package com.gridiron.superbowl

import com.gridiron.superbowl.model.Conference
import com.gridiron.superbowl.model.SeasonPhase
import com.gridiron.superbowl.replica.PocketHostSnapshot
import com.gridiron.superbowl.replica.PostgresConfig
import com.gridiron.superbowl.replica.PostgresReplica
import com.gridiron.superbowl.replica.RecordStatus
import com.gridiron.superbowl.replica.ReplicaDb
import com.gridiron.superbowl.replica.ReplicaQueries
import com.gridiron.superbowl.replica.SqliteReplica
import com.gridiron.superbowl.sync.Backoff
import com.gridiron.superbowl.sync.PocketBaseClient
import com.gridiron.superbowl.sync.PocketHostConfig
import java.net.Socket
import java.nio.file.Files
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The JDBI query templates, run unchanged against both replicas built from the same PocketHost snapshot. */
class ReplicaQueriesTest {
    private val pb = FakePocketBase()
    private val report = ReportsTest.report
    private val games = ReportsTest.season
    private val snapshot: PocketHostSnapshot = PocketBaseClient(PocketHostConfig(pb.url, "admin@example.com", "s3cret"), backoff = Backoff(attempts = 1))
        .let { client ->
            client.sync(report, games, 1_000)
            client.sync(report.copy(generatedAt = "2026-10-26"), games, 2_000) // second run supersedes the first
            PocketHostSnapshot.pull(client, pb.url)
        }
    private val pg = PostgresConfig.resolve(mapOf("NFL_PG_DB" to "nfl_superbowl_playoff_odds_test", "NFL_PG_SCHEMA" to "nfl_queries"), emptyList())

    @AfterTest
    fun stop() = pb.close()

    private fun sqlite(): ReplicaQueries {
        val file = Files.createTempDirectory("q").resolve("nfl.sqlite")
        SqliteReplica.rebuild(file, snapshot)
        return ReplicaDb.queries(ReplicaDb.sqlite(file))
    }

    private fun postgres(): ReplicaQueries? =
        runCatching { Socket(pg.host, pg.port).close() }.getOrNull()?.let {
            PostgresReplica.rebuild(pg, snapshot)
            ReplicaDb.queries(ReplicaDb.postgres(pg))
        }

    private fun checkAll(q: ReplicaQueries) {
        val board = q.leaderboard(2026)
        assertEquals(32, board.size)
        assertEquals((1..32).toList(), board.map { it.intChampionRank })
        assertEquals(report.byChampionOdds.first().abbr, board.first().strTeamCode)
        assertTrue(board.all { it.strRunId.startsWith("2026-10-26") }, "view shows only the ACTIVE run")
        assertEquals(Instant.parse("2026-10-26T00:00:00Z"), board.first().dtRunDate)
        assertEquals(8, board.first().jsonSeedOdds.probabilities.size)
        assertEquals(1.0, board.sumOf { it.dblChampionProb }, 1e-9)

        val nfc = q.conferenceLeaderboard(2026, Conference.NFC)
        assertEquals(16, nfc.size)
        assertTrue(nfc.all { it.strConference == Conference.NFC })

        assertEquals(listOf("2026-10-26"), q.runs(2026, RecordStatus.ACTIVE).map { it.strRunId.take(10) })
        assertEquals(listOf("2026-10-19"), q.runs(2026, RecordStatus.SUPERSEDED).map { it.strRunId.take(10) })
        assertEquals(RecordStatus.ACTIVE, q.latestRun(2026)!!.strStatusCode)
        assertTrue(q.latestRun(2026)!!.created != null)
        assertNull(q.latestRun(1999))

        val trend = q.teamTrend("SF", 2026)
        assertEquals(2, trend.size)
        assertTrue(trend[0].dtRunDate < trend[1].dtRunDate)

        val runId = q.latestRun(2026)!!.strRunId
        assertEquals(listOf(1, 2, 3), q.topMatchups(runId, 3).map { it.intRank })

        val week1 = q.weekGames(2026, SeasonPhase.REGULAR, 1)
        assertEquals(games.count { it.week == 1 }, week1.size)
        assertTrue(week1.zipWithNext().all { (a, b) -> a.dtKickoffUtc!! <= b.dtKickoffUtc!! })
        val unplayed = week1.firstOrNull { !it.boolIsFinal }
        assertTrue(unplayed == null || unplayed.intHomeScore == 0 || unplayed.intHomeScore == null)

        val sf = q.teamGames("SF", 2026)
        assertEquals(games.count { it.home == "SF" || it.away == "SF" }, sf.size)
        assertTrue(sf.all { it.strHomeCode == "SF" || it.strAwayCode == "SF" })
        // A hostile parameter is bound, not concatenated: it matches nothing and breaks nothing.
        assertEquals(emptyList(), q.teamGames("SF' OR '1'='1", 2026))

        assertEquals(games.count { !it.isFinal }, q.unplayedGames(2026))
    }

    @Test
    fun templatesRunAgainstSqlite() = checkAll(sqlite())

    @Test
    fun templatesRunAgainstPostgres() {
        postgres()?.let(::checkAll)
    }
}
