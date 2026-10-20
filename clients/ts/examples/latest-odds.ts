/**
 * The PocketHost starter snippet, expanded:
 *
 *   const client = new PocketBase(url)
 *   const records = await client.collection('posts').getFullList({ sort: '-created' })
 *
 * becomes a typed, validated, retried query against the real collections. Run with:
 *   POCKETHOST_URL=https://nfl-superbowl-predictions.pockethost.io bun examples/latest-odds.ts
 */
import { createNflRepository, createPocketBaseSource, describe, urlFromEnv } from '../src/index.ts';

const season = Number(process.env['NFL_SEASON'] ?? new Date().getFullYear());

const output = await urlFromEnv()
  .map((url) => createNflRepository(createPocketBaseSource({ url })))
  .fold(
    async (e) => `error: ${describe(e)}`,
    async (repo) => {
      const runs = await repo.runs(season); // newest first: sort '-created'
      const board = await repo.leaderboard(season);
      return board.fold(
        (e) => `error: ${describe(e)}`,
        (rows) =>
          [
            `${runs.getOrElse([]).length} active run(s) for ${season}`,
            ...rows.slice(0, 10).map((r) => `${String(r.int_champion_rank).padStart(2)}. ${r.str_team_name.padEnd(24)} ${(r.dbl_champion_prob * 100).toFixed(1)}%`),
          ].join('\n'),
      );
    },
  );

console.log(output);
