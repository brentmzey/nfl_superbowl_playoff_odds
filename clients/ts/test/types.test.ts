import { expect, test } from 'bun:test';
import { NflQueries, defineQuery, type ParamsOf, type QueryParams, type RecordOf, type SortKey } from '../src/index.ts';
import type { NflGame, SimulationRun, TeamOutlook } from '../src/generated/schema.ts';

/**
 * Compile-time contract of the query templates. `tsc` (run by `bun run typecheck`) fails if any
 * `@ts-expect-error` below stops being an error, i.e. if inference ever degrades to `unknown`/`any`.
 */
test('parameter types are inferred from the columns they are compared with', () => {
  const week: ParamsOf<typeof NflQueries.weekGames> = { season: 2026, phase: 'REGULAR', week: 6 };
  // @ts-expect-error - str_phase is SeasonPhase, so 'PRESEASON' is rejected
  const badPhase: ParamsOf<typeof NflQueries.weekGames> = { season: 2026, phase: 'PRESEASON', week: 6 };
  // @ts-expect-error - int_season_year is a number
  const badSeason: ParamsOf<typeof NflQueries.leaderboard> = { season: '2026' };
  // @ts-expect-error - missing required parameter `week`
  const missing: ParamsOf<typeof NflQueries.weekGames> = { season: 2026, phase: 'REGULAR' };
  // @ts-expect-error - unknown parameter
  const extra: ParamsOf<typeof NflQueries.leaderboard> = { season: 2026, year: 2026 };
  const runs: ParamsOf<typeof NflQueries.runs> = { season: 2026, status: 'SUPERSEDED' };
  // @ts-expect-error - str_status_code is StatusCode
  const badStatus: ParamsOf<typeof NflQueries.runs> = { season: 2026, status: 'DONE' };
  const repeated: ParamsOf<typeof NflQueries.teamGames> = { season: 2026, team: 'KC' };
  const noParams: ParamsOf<typeof NflQueries.teams> = {};
  expect([week, badPhase, badSeason, missing, extra, runs, badStatus, repeated, noParams]).toHaveLength(9);
});

test('operators, parentheses, OR clauses and literals parse', () => {
  type P = QueryParams<NflGame, "(int_week >= {:from} && int_week <= {:to}) || str_home_code != {:team} && bool_is_final = true">;
  const p: P = { from: 1, to: 18, team: 'KC' };
  // @ts-expect-error - `from` compares against int_week (number)
  const bad: P = { from: '1', to: 18, team: 'KC' };
  type Free = QueryParams<NflGame, 'str_home_code ~ {:needle}'>;
  const free: Free = { needle: 'K' };
  expect([p, bad, free]).toHaveLength(3);
});

test('sort keys and result types are checked against the schema', () => {
  const sort: SortKey<SimulationRun>[] = ['-created', 'dt_run_date', '+int_season_year'];
  // @ts-expect-error - not a column of m_simulation_runs
  const badSort: SortKey<SimulationRun> = '-dbl_champion_prob';
  defineQuery({
    name: 'bad',
    collection: 'o_team_outlooks',
    filter: '',
    // @ts-expect-error - sort column must exist on o_team_outlooks
    sort: ['-str_game_id'],
  });
  const row: RecordOf<typeof NflQueries.teamTrend> = {} as TeamOutlook;
  expect([sort, badSort, row]).toHaveLength(3);
});
