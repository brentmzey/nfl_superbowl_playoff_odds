import { describe as group, expect, test } from 'bun:test';
import { createNflRepository, createPocketBaseSource, describe, isRetryable, urlFromEnv, validateRecord, type NflClientError } from '../src/index.ts';
import { json, makeRecord, mockFetch, page } from './helpers.ts';

const URL_ = 'https://nfl-superbowl-predictions.pockethost.io';
const noSleep = { sleep: async () => {}, random: () => 0 };

const errorOf = <T>(r: { fold: <U>(e: (x: NflClientError) => U, o: (v: T) => U) => U }): NflClientError =>
  r.fold((e) => e, () => { throw new Error('expected an error'); });

group('PocketBase source (real SDK over a mocked fetch)', () => {
  test('leaderboard: typed filter, sort, validated rows', async () => {
    const rows = [makeRecord('s_latest_team_outlooks', { str_team_code: 'SF', int_champion_rank: 1 })];
    const { fetch, calls } = mockFetch(() => page(rows));
    const repo = createNflRepository(createPocketBaseSource({ url: `${URL_}/`, fetch, ...noSleep }));
    const result = await repo.leaderboard(2026);
    expect(result.getOrThrow().map((r) => r.str_team_code)).toEqual(['SF']);
    const call = calls[0]!.url;
    expect(call.pathname).toBe('/api/collections/s_latest_team_outlooks/records');
    expect(call.searchParams.get('filter')).toBe('int_season_year = 2026');
    expect(call.searchParams.get('sort')).toBe('int_champion_rank');
    expect(call.searchParams.get('perPage')).toBe('500');
  });

  test('parameters are escaped by PocketBase, so they cannot inject filter syntax', async () => {
    const { fetch, calls } = mockFetch(() => page([]));
    const repo = createNflRepository(createPocketBaseSource({ url: URL_, fetch, ...noSleep }));
    await repo.teamGames('KC" || str_home_code != "', 2026);
    expect(calls[0]!.url.searchParams.get('filter')).toBe(
      'int_season_year = 2026 && (str_home_code = "KC\\" || str_home_code != \\"" || str_away_code = "KC\\" || str_home_code != \\"")',
    );
  });

  test('full lists page through 500-row batches', async () => {
    const full = Array.from({ length: 500 }, () => makeRecord('i_nfl_games'));
    const { fetch, calls } = mockFetch((url) => (url.searchParams.get('page') === '1' ? page(full) : page([makeRecord('i_nfl_games')], 2)));
    const games = await createNflRepository(createPocketBaseSource({ url: URL_, fetch, ...noSleep })).weekGames(2026, 'REGULAR', 6);
    expect(games.getOrThrow()).toHaveLength(501);
    expect(calls.map((c) => c.url.searchParams.get('page'))).toEqual(['1', '2']);
    expect(calls[0]!.url.searchParams.get('filter')).toBe('int_season_year = 2026 && str_phase = "REGULAR" && int_week = 6');
  });

  test('limited queries fetch one page and map to Optional', async () => {
    const run = makeRecord('m_simulation_runs', { str_run_id: 'r1' });
    const { fetch, calls } = mockFetch((_, n) => page(n === 1 ? [run] : [], 1, 1));
    const repo = createNflRepository(createPocketBaseSource({ url: URL_, fetch, ...noSleep }));
    expect((await repo.latestRun(2026)).getOrThrow().map((r) => r.str_run_id).getOrElse('none')).toBe('r1');
    expect((await repo.latestRun(2027)).getOrThrow().isNone()).toBe(true);
    expect(calls[0]!.url.searchParams.get('perPage')).toBe('1');
    expect(calls[0]!.url.searchParams.get('skipTotal')).toBe('true');
    expect(calls[0]!.url.searchParams.get('sort')).toBe('-dt_run_date,-created');
  });

  test('every repository method targets its collection', async () => {
    const seen: string[] = [];
    const { fetch } = mockFetch((url) => {
      const collection = url.pathname.split('/')[3] as Parameters<typeof makeRecord>[0];
      seen.push(`${collection}?${url.searchParams.get('filter') ?? ''}`);
      return page([makeRecord(collection)]);
    });
    const repo = createNflRepository(createPocketBaseSource({ url: URL_, fetch, ...noSleep }));
    for (const r of await Promise.all([
      repo.teams(), repo.conferenceLeaderboard(2026, 'NFC'), repo.runs(2026), repo.runs(2026, 'SUPERSEDED'),
      repo.teamTrend('SF', 2026), repo.topMatchups('run-1'), repo.migrations(),
    ])) expect(r.isOk()).toBe(true);
    expect(seen).toEqual([
      'i_nfl_teams?',
      's_latest_team_outlooks?int_season_year = 2026 && str_conference = "NFC"',
      'm_simulation_runs?int_season_year = 2026 && str_status_code = "ACTIVE"',
      'm_simulation_runs?int_season_year = 2026 && str_status_code = "SUPERSEDED"',
      'o_team_outlooks?str_team_code = "SF" && int_season_year = 2026',
      'o_superbowl_matchups?str_run_id = "run-1"',
      'tbl_schema_migrations?',
    ]);
  });

  test('retries 503/429 with exponential back-off, then succeeds', async () => {
    const sleeps: number[] = [];
    const { fetch, calls } = mockFetch((_, n) => (n === 1 ? json({ message: 'cold start' }, 503) : n === 2 ? json({ message: 'slow down' }, 429) : page([])));
    const source = createPocketBaseSource({ url: URL_, fetch, sleep: async (ms) => { sleeps.push(ms); }, random: () => 0.5, retry: { initialDelayMs: 100, jitter: 0.2 } });
    expect((await createNflRepository(source).teams()).isOk()).toBe(true);
    expect(calls).toHaveLength(3);
    expect(sleeps).toEqual([110, 220]);
  });

  test('default sleep really waits between retries', async () => {
    const { fetch, calls } = mockFetch((_, n) => (n === 1 ? json({ message: 'busy' }, 503) : json({ code: 200, message: 'ok', data: {} })));
    const started = performance.now();
    expect((await createPocketBaseSource({ url: URL_, fetch, retry: { initialDelayMs: 15, jitter: 0 } }).health()).isOk()).toBe(true);
    expect(calls).toHaveLength(2);
    expect(performance.now() - started).toBeGreaterThanOrEqual(10);
  });

  test('client errors are not retried and keep their status', async () => {
    const { fetch, calls } = mockFetch(() => json({ message: 'Missing collection' }, 404));
    const e = errorOf(await createNflRepository(createPocketBaseSource({ url: URL_, fetch, ...noSleep })).teams());
    expect(e.kind).toBe('http');
    expect(e.kind === 'http' && e.status).toBe(404);
    expect(calls).toHaveLength(1);
    expect(isRetryable(e)).toBe(false);
  });

  test('network failures are retried up to the limit', async () => {
    const { fetch, calls } = mockFetch(() => { throw new TypeError('fetch failed'); });
    const e = errorOf(await createNflRepository(createPocketBaseSource({ url: URL_, fetch, ...noSleep, retry: { attempts: 3 } })).teams());
    expect(e.kind).toBe('network');
    expect(calls).toHaveLength(3);
  });

  test('slow responses time out and are reported as timeouts', async () => {
    const fetch = (_: RequestInfo | URL, init?: RequestInit) =>
      new Promise<Response>((_, reject) => init?.signal?.addEventListener('abort', () => reject(new DOMException('aborted', 'AbortError'))));
    const e = errorOf(await createPocketBaseSource({ url: URL_, fetch, timeoutMs: 5, ...noSleep, retry: { attempts: 2 } }).health());
    expect(e).toEqual({ kind: 'timeout', timeoutMs: 5, url: URL_ });
  });

  test('records that break the schema are rejected with the offending field', async () => {
    const broken = { ...makeRecord('i_nfl_teams'), str_conference: 42 };
    const { fetch } = mockFetch(() => page([broken]));
    const e = errorOf(await createNflRepository(createPocketBaseSource({ url: URL_, fetch, ...noSleep })).teams());
    expect(e).toMatchObject({ kind: 'validation', collection: 'i_nfl_teams', field: 'str_conference', expected: 'text (required)', actual: 'number' });
  });

  test('wrongly typed params from untyped callers are refused before any request', async () => {
    const { fetch, calls } = mockFetch(() => page([]));
    const source = createPocketBaseSource({ url: URL_, fetch, ...noSleep });
    const untyped = source.query as (q: unknown, p: Record<string, unknown>) => ReturnType<typeof source.query>;
    const { NflQueries } = await import('../src/index.ts');
    expect(errorOf(await untyped(NflQueries.leaderboard, {}))).toEqual({ kind: 'query', query: 'leaderboard', message: 'missing parameter(s): season' });
    expect(calls).toHaveLength(0);
  });

  test('health check', async () => {
    const { fetch, calls } = mockFetch(() => json({ code: 200, message: 'API is healthy.', data: {} }));
    expect((await createPocketBaseSource({ url: URL_, fetch, ...noSleep }).health()).getOrThrow()).toBe(true);
    expect(calls[0]!.url.pathname).toBe('/api/health');
  });

  test('non-SDK exceptions map to network errors', async () => {
    const fetch = async (): Promise<Response> => { throw 'raw string failure'; };
    const e = errorOf(await createPocketBaseSource({ url: URL_, fetch, ...noSleep, retry: { attempts: 1 } }).health());
    expect(e.kind).toBe('network');
  });
});

group('config, validation and error helpers', () => {
  test('POCKETHOST_URL comes from the environment, with no hard-coded fallback', () => {
    expect(urlFromEnv({ POCKETHOST_URL: ` ${URL_}/ ` }).getOrThrow()).toBe(URL_);
    expect(errorOf(urlFromEnv({}))).toEqual({ kind: 'config', message: 'POCKETHOST_URL is not set (see .env.example)' });
    expect(urlFromEnv().isOk() || urlFromEnv().isErr()).toBe(true);
  });

  test('validator checks every field kind', () => {
    expect(validateRecord('i_nfl_teams', makeRecord('i_nfl_teams')).isOk()).toBe(true);
    expect(errorOf(validateRecord('i_nfl_teams', null))).toMatchObject({ field: 'id', actual: 'null' });
    expect(errorOf(validateRecord('i_nfl_teams', [1]))).toMatchObject({ field: 'id', actual: 'array' });
    expect(errorOf(validateRecord('i_nfl_teams', { ...makeRecord('i_nfl_teams'), str_team_code: '' }))).toMatchObject({ field: 'str_team_code' });
    expect(errorOf(validateRecord('i_nfl_games', { ...makeRecord('i_nfl_games'), rel_home_team: '' }))).toMatchObject({ field: 'rel_home_team', expected: 'relation (required)' });
    expect(errorOf(validateRecord('i_nfl_games', { ...makeRecord('i_nfl_games'), int_week: Number.NaN }))).toMatchObject({ field: 'int_week' });
    expect(errorOf(validateRecord('i_nfl_games', { ...makeRecord('i_nfl_games'), bool_is_final: 1 }))).toMatchObject({ field: 'bool_is_final', expected: 'bool' });
    expect(errorOf(validateRecord('o_team_outlooks', { ...makeRecord('o_team_outlooks'), json_seed_odds: undefined }))).toMatchObject({ field: 'json_seed_odds' });
    expect(validateRecord('i_nfl_games', { ...makeRecord('i_nfl_games'), dt_kickoff_utc: '' }).isOk()).toBe(true);
  });

  test('every error kind has a description and a retry classification', () => {
    const errors: NflClientError[] = [
      { kind: 'config', message: 'm' },
      { kind: 'http', status: 503, message: 'm', url: 'u' },
      { kind: 'network', message: 'm', url: 'u' },
      { kind: 'timeout', timeoutMs: 1, url: 'u' },
      { kind: 'validation', collection: 'c', recordId: 'r', field: 'f', expected: 'e', actual: 'a' },
      { kind: 'query', query: 'q', message: 'm' },
    ];
    expect(errors.map(describe)).toEqual([
      'config: m', 'HTTP 503 from u: m', 'network error calling u: m', 'timed out after 1ms calling u', 'c/r.f: expected e, got a', 'query q: m',
    ]);
    expect(errors.map(isRetryable)).toEqual([false, true, true, true, false, false]);
  });
});
