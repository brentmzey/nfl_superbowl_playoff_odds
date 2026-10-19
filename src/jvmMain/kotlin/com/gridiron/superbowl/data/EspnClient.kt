package com.gridiron.superbowl.data

import com.gridiron.superbowl.model.Game
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

/** Fetches raw scoreboard JSON for a season/phase/week. Abstracted so tests can point at a local server. */
fun interface ScoreboardSource {
    fun fetch(season: Int, seasonType: Int, week: Int): Result<String>
}

class EspnHttpSource(
    private val baseUrl: String = "https://site.api.espn.com/apis/site/v2/sports/football/nfl/scoreboard",
    private val client: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
) : ScoreboardSource {
    override fun fetch(season: Int, seasonType: Int, week: Int): Result<String> = runCatching {
        val request = HttpRequest.newBuilder(URI.create("$baseUrl?dates=$season&seasontype=$seasonType&week=$week"))
            .header("Accept", "application/json")
            .timeout(Duration.ofSeconds(20))
            .GET().build()
        client.send(request, HttpResponse.BodyHandlers.ofString()).let { response ->
            check(response.statusCode() == 200) { "ESPN returned HTTP ${response.statusCode()} for $season/$seasonType/$week" }
            response.body()
        }
    }
}

/**
 * Caches completed seasons on disk (they never change) and always refetches the in-progress season.
 */
class CachingSource(private val delegate: ScoreboardSource, private val dir: Path, private val currentSeason: Int) :
    ScoreboardSource {
    override fun fetch(season: Int, seasonType: Int, week: Int): Result<String> {
        val file = dir.resolve("espn_${season}_${seasonType}_$week.json")
        return if (season < currentSeason && Files.exists(file)) runCatching { Files.readString(file) }
        else delegate.fetch(season, seasonType, week).onSuccess {
            Files.createDirectories(dir)
            Files.writeString(file, it)
        }
    }
}

class EspnClient(private val source: ScoreboardSource) {

    /** Regular season (weeks 1-18) plus postseason (weeks 1-5; week 4 is the Pro Bowl and is filtered out). */
    fun season(season: Int, includePostseason: Boolean): Result<List<Game>> {
        val weeks: List<Pair<Int, Int>> =
            (1..REGULAR_WEEKS).map { 2 to it } + if (includePostseason) (1..5).map { 3 to it } else emptyList()
        return weeks.fold(Result.success(emptyList<Game>())) { acc, (type, week) ->
            acc.mapCatching { games -> games + EspnScoreboardParser.parse(source.fetch(season, type, week).getOrThrow()) }
        }.map { games -> games.distinctBy { it.id } }
    }

    companion object {
        const val REGULAR_WEEKS = 18
    }
}
