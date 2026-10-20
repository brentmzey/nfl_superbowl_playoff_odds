import { fromNullable, type Optional, type Result } from './fp.ts';
import type { NflClientError } from './errors.ts';
import type {
  Conference, LatestTeamOutlook, NflGame, NflTeam, SchemaMigration, SeasonPhase, SimulationRun, StatusCode, SuperBowlMatchup, TeamOutlook,
} from './generated/schema.ts';
import { defineQuery } from './query.ts';
import type { RecordSource } from './source.ts';

/**
 * The named query catalogue (the JDBI `@SqlQuery` equivalent). Every template is type-checked against the
 * generated schema: column names in `filter`/`sort` and the parameter types are all verified at compile time.
 */
export const NflQueries = {
  teams: defineQuery({ name: 'teams', collection: 'i_nfl_teams', filter: '', sort: ['str_conference', 'str_division', 'str_team_code'] }),
  leaderboard: defineQuery({
    name: 'leaderboard',
    collection: 's_latest_team_outlooks',
    filter: 'int_season_year = {:season}',
    sort: ['int_champion_rank'],
  }),
  conferenceLeaderboard: defineQuery({
    name: 'conferenceLeaderboard',
    collection: 's_latest_team_outlooks',
    filter: 'int_season_year = {:season} && str_conference = {:conference}',
    sort: ['int_champion_rank'],
  }),
  runs: defineQuery({
    name: 'runs',
    collection: 'm_simulation_runs',
    filter: 'int_season_year = {:season} && str_status_code = {:status}',
    sort: ['-created'],
  }),
  latestRun: defineQuery({
    name: 'latestRun',
    collection: 'm_simulation_runs',
    filter: "int_season_year = {:season} && str_status_code = 'ACTIVE'",
    sort: ['-dt_run_date', '-created'],
    limit: 1,
  }),
  teamTrend: defineQuery({
    name: 'teamTrend',
    collection: 'o_team_outlooks',
    filter: 'str_team_code = {:team} && int_season_year = {:season}',
    sort: ['dt_run_date', 'created'],
  }),
  topMatchups: defineQuery({ name: 'topMatchups', collection: 'o_superbowl_matchups', filter: 'str_run_id = {:runId}', sort: ['int_rank'] }),
  weekGames: defineQuery({
    name: 'weekGames',
    collection: 'i_nfl_games',
    filter: 'int_season_year = {:season} && str_phase = {:phase} && int_week = {:week}',
    sort: ['dt_kickoff_utc', 'str_game_id'],
  }),
  teamGames: defineQuery({
    name: 'teamGames',
    collection: 'i_nfl_games',
    filter: 'int_season_year = {:season} && (str_home_code = {:team} || str_away_code = {:team})',
    sort: ['dt_kickoff_utc'],
  }),
  migrations: defineQuery({ name: 'migrations', collection: 'tbl_schema_migrations', filter: '', sort: ['str_migration_id'] }),
} as const;

type Out<T> = Promise<Result<T, NflClientError>>;

export interface NflRepository {
  teams(): Out<NflTeam[]>;
  /** Current title odds for a season, best first (from the `s_latest_team_outlooks` view). */
  leaderboard(season: number): Out<LatestTeamOutlook[]>;
  conferenceLeaderboard(season: number, conference: Conference): Out<LatestTeamOutlook[]>;
  /** Simulation runs, newest first (`sort: '-created'`). */
  runs(season: number, status?: StatusCode): Out<SimulationRun[]>;
  latestRun(season: number): Out<Optional<SimulationRun>>;
  /** One team's outlook across every run of a season, oldest first. */
  teamTrend(team: string, season: number): Out<TeamOutlook[]>;
  topMatchups(runId: string): Out<SuperBowlMatchup[]>;
  weekGames(season: number, phase: SeasonPhase, week: number): Out<NflGame[]>;
  teamGames(team: string, season: number): Out<NflGame[]>;
  migrations(): Out<SchemaMigration[]>;
}

export const createNflRepository = (source: RecordSource): NflRepository => ({
  teams: () => source.query(NflQueries.teams, {}),
  leaderboard: (season) => source.query(NflQueries.leaderboard, { season }),
  conferenceLeaderboard: (season, conference) => source.query(NflQueries.conferenceLeaderboard, { season, conference }),
  runs: (season, status = 'ACTIVE') => source.query(NflQueries.runs, { season, status }),
  latestRun: async (season) => (await source.query(NflQueries.latestRun, { season })).map((rows) => fromNullable(rows[0])),
  teamTrend: (team, season) => source.query(NflQueries.teamTrend, { team, season }),
  topMatchups: (runId) => source.query(NflQueries.topMatchups, { runId }),
  weekGames: (season, phase, week) => source.query(NflQueries.weekGames, { season, phase, week }),
  teamGames: (team, season) => source.query(NflQueries.teamGames, { team, season }),
  migrations: () => source.query(NflQueries.migrations, {}),
});
