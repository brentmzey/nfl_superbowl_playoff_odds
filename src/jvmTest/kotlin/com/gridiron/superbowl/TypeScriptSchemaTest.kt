package com.gridiron.superbowl

import com.gridiron.superbowl.sync.TypeScriptSchema
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Golden test: the committed TypeScript typings must match what the Kotlin schema generates. */
class TypeScriptSchemaTest {
    @Test
    fun committedTypingsMatchTheSchema() {
        val committed = Path.of(TypeScriptSchema.PATH)
        val generated = TypeScriptSchema.generate()
        assertTrue(Files.exists(committed), "Run `just schema-ts` to generate ${TypeScriptSchema.PATH}")
        assertEquals(generated, Files.readString(committed), "Typings drifted: run `just schema-ts`")
        assertTrue(generated.contains("export interface TeamOutlook extends RecordMeta, Timestamps {"))
        assertTrue(generated.contains("  json_seed_odds: number[];"))
        assertTrue(generated.contains("  str_conference: Conference;"))
        assertTrue(generated.contains("  /** FK → `m_simulation_runs.id`. */\n  rel_run: string;"))
        assertTrue(generated.contains("  s_latest_team_outlooks: LatestTeamOutlook;"))
        assertTrue(generated.contains("  /** UTC, PocketBase form `YYYY-MM-DD HH:MM:SS.sssZ`. */\n  dt_kickoff_utc: string;"))
    }
}
