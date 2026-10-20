import { expect, test } from 'bun:test';
import { createNflRepository, createPocketBaseSource, urlFromEnv } from '../src/index.ts';

/** Read-only checks against the real instance. Opt in with `NFL_LIVE=1 bun test` (uses POCKETHOST_URL). */
const live = process.env['NFL_LIVE'] === '1';

test.skipIf(!live)('live instance is healthy and the leaderboard query is accepted', async () => {
  const source = createPocketBaseSource({ url: urlFromEnv().getOrThrow() });
  expect((await source.health()).getOrThrow()).toBe(true);
  const board = await createNflRepository(source).leaderboard(2026);
  // Before the first Kotlin sync the view does not exist yet (404); afterwards it returns 32 validated rows.
  board.fold(
    (e) => expect(e).toMatchObject({ kind: 'http', status: 404 }),
    (rows) => expect(rows).toHaveLength(32),
  );
}, 30_000);
