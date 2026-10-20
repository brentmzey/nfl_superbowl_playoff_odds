// AUTO-GENERATED from PocketBaseSchema.kt by the Kotlin pipeline (`just refresh` / `just schema-ts`).
// Do not edit by hand: `TypeScriptSchemaTest` fails the Kotlin build if this file drifts.

export type Conference = 'AFC' | 'NFC';
export type SeasonPhase = 'REGULAR' | 'POSTSEASON';
export type StatusCode = 'ACTIVE' | 'INACTIVE' | 'SUPERSEDED' | 'ARCHIVED';
export type FieldKind = 'text' | 'number' | 'bool' | 'json' | 'date' | 'relation' | 'autodate';

export interface FieldSpec {
  readonly kind: FieldKind;
  readonly required: boolean;
}

/** Metadata PocketBase adds to every record. */
export interface RecordMeta {
  id: string;
  collectionId: string;
  collectionName: string;
}

/** Server-managed `autodate` columns. */
export interface Timestamps {
  created: string;
  updated: string;
}

/** `tbl_schema_migrations`. Upsert key: `str_migration_id`. */
export interface SchemaMigration extends RecordMeta, Timestamps {
  str_migration_id: string;
  str_description: string;
  str_fingerprint: string;
  int_applied_epoch_ms_utc: number;
  bool_is_active: boolean;
  str_status_code: StatusCode;
  int_created_epoch_ms_utc: number;
  int_updated_epoch_ms_utc: number;
}

/** `i_nfl_teams`. Upsert key: `str_team_code`. */
export interface NflTeam extends RecordMeta, Timestamps {
  str_team_code: string;
  str_team_name: string;
  str_conference: Conference;
  str_division: string;
  bool_is_active: boolean;
  str_status_code: StatusCode;
  int_created_epoch_ms_utc: number;
  int_updated_epoch_ms_utc: number;
}

/** `i_nfl_games`. Upsert key: `str_game_id`. */
export interface NflGame extends RecordMeta, Timestamps {
  str_game_id: string;
  int_season_year: number;
  str_phase: SeasonPhase;
  int_week: number;
  /** UTC, PocketBase form `YYYY-MM-DD HH:MM:SS.sssZ`. */
  dt_kickoff_utc: string;
  str_home_code: string;
  str_away_code: string;
  bool_neutral_site: boolean;
  bool_is_final: boolean;
  int_home_score: number;
  int_away_score: number;
  bool_is_active: boolean;
  str_status_code: StatusCode;
  int_created_epoch_ms_utc: number;
  int_updated_epoch_ms_utc: number;
  /** FK → `i_nfl_teams.id`. */
  rel_home_team: string;
  /** FK → `i_nfl_teams.id`. */
  rel_away_team: string;
}

/** `m_simulation_runs`. Upsert key: `str_run_id`. */
export interface SimulationRun extends RecordMeta, Timestamps {
  str_run_id: string;
  /** UTC, PocketBase form `YYYY-MM-DD HH:MM:SS.sssZ`. */
  dt_run_date: string;
  int_season_year: number;
  int_through_week: number;
  int_games_played: number;
  int_games_remaining: number;
  int_total_iterations: number;
  int_random_seed: number;
  str_engine_version: string;
  str_top_favorite_code: string;
  dbl_top_favorite_prob: number;
  bool_is_active: boolean;
  str_status_code: StatusCode;
  int_created_epoch_ms_utc: number;
  int_updated_epoch_ms_utc: number;
}

/** `o_team_outlooks`. Upsert key: `str_run_id`, `str_team_code`. */
export interface TeamOutlook extends RecordMeta, Timestamps {
  str_run_id: string;
  str_team_code: string;
  /** UTC, PocketBase form `YYYY-MM-DD HH:MM:SS.sssZ`. */
  dt_run_date: string;
  int_season_year: number;
  int_wins: number;
  int_losses: number;
  int_ties: number;
  dbl_elo_rating: number;
  dbl_projected_wins: number;
  dbl_division_prob: number;
  dbl_top_seed_prob: number;
  dbl_playoff_prob: number;
  dbl_divisional_prob: number;
  dbl_conference_prob: number;
  dbl_superbowl_prob: number;
  dbl_champion_prob: number;
  int_champion_rank: number;
  json_seed_odds: number[];
  bool_is_active: boolean;
  str_status_code: StatusCode;
  int_created_epoch_ms_utc: number;
  int_updated_epoch_ms_utc: number;
  /** FK → `m_simulation_runs.id`. */
  rel_run: string;
  /** FK → `i_nfl_teams.id`. */
  rel_team: string;
}

/** `o_superbowl_matchups`. Upsert key: `str_run_id`, `str_afc_code`, `str_nfc_code`. */
export interface SuperBowlMatchup extends RecordMeta, Timestamps {
  str_run_id: string;
  str_afc_code: string;
  str_nfc_code: string;
  int_rank: number;
  dbl_probability: number;
  bool_is_active: boolean;
  str_status_code: StatusCode;
  int_created_epoch_ms_utc: number;
  int_updated_epoch_ms_utc: number;
  /** FK → `m_simulation_runs.id`. */
  rel_run: string;
  /** FK → `i_nfl_teams.id`. */
  rel_afc_team: string;
  /** FK → `i_nfl_teams.id`. */
  rel_nfc_team: string;
}

/** `s_latest_team_outlooks` (read-only view). */
export interface LatestTeamOutlook extends RecordMeta {
  str_team_code: string;
  str_team_name: string;
  str_conference: Conference;
  str_division: string;
  str_run_id: string;
  /** UTC, PocketBase form `YYYY-MM-DD HH:MM:SS.sssZ`. */
  dt_run_date: string;
  int_season_year: number;
  int_through_week: number;
  int_wins: number;
  int_losses: number;
  int_ties: number;
  dbl_elo_rating: number;
  dbl_projected_wins: number;
  dbl_division_prob: number;
  dbl_playoff_prob: number;
  dbl_divisional_prob: number;
  dbl_conference_prob: number;
  dbl_superbowl_prob: number;
  dbl_champion_prob: number;
  int_champion_rank: number;
  json_seed_odds: number[];
}

export const Collections = {
  SchemaMigration: 'tbl_schema_migrations',
  NflTeam: 'i_nfl_teams',
  NflGame: 'i_nfl_games',
  SimulationRun: 'm_simulation_runs',
  TeamOutlook: 'o_team_outlooks',
  SuperBowlMatchup: 'o_superbowl_matchups',
  LatestTeamOutlook: 's_latest_team_outlooks',
} as const;

export type CollectionName = (typeof Collections)[keyof typeof Collections];

export interface CollectionRecords {
  tbl_schema_migrations: SchemaMigration;
  i_nfl_teams: NflTeam;
  i_nfl_games: NflGame;
  m_simulation_runs: SimulationRun;
  o_team_outlooks: TeamOutlook;
  o_superbowl_matchups: SuperBowlMatchup;
  s_latest_team_outlooks: LatestTeamOutlook;
}

export const FIELD_SPECS: { readonly [C in CollectionName]: Readonly<Record<string, FieldSpec>> } = {
  tbl_schema_migrations: {
      str_migration_id: { kind: 'text', required: true },
      str_description: { kind: 'text', required: false },
      str_fingerprint: { kind: 'text', required: true },
      int_applied_epoch_ms_utc: { kind: 'number', required: false },
      bool_is_active: { kind: 'bool', required: false },
      str_status_code: { kind: 'text', required: true },
      int_created_epoch_ms_utc: { kind: 'number', required: false },
      int_updated_epoch_ms_utc: { kind: 'number', required: false },
      created: { kind: 'autodate', required: true },
      updated: { kind: 'autodate', required: true },
  },
  i_nfl_teams: {
      str_team_code: { kind: 'text', required: true },
      str_team_name: { kind: 'text', required: true },
      str_conference: { kind: 'text', required: true },
      str_division: { kind: 'text', required: true },
      bool_is_active: { kind: 'bool', required: false },
      str_status_code: { kind: 'text', required: true },
      int_created_epoch_ms_utc: { kind: 'number', required: false },
      int_updated_epoch_ms_utc: { kind: 'number', required: false },
      created: { kind: 'autodate', required: true },
      updated: { kind: 'autodate', required: true },
  },
  i_nfl_games: {
      str_game_id: { kind: 'text', required: true },
      int_season_year: { kind: 'number', required: false },
      str_phase: { kind: 'text', required: true },
      int_week: { kind: 'number', required: false },
      dt_kickoff_utc: { kind: 'date', required: false },
      str_home_code: { kind: 'text', required: true },
      str_away_code: { kind: 'text', required: true },
      bool_neutral_site: { kind: 'bool', required: false },
      bool_is_final: { kind: 'bool', required: false },
      int_home_score: { kind: 'number', required: false },
      int_away_score: { kind: 'number', required: false },
      bool_is_active: { kind: 'bool', required: false },
      str_status_code: { kind: 'text', required: true },
      int_created_epoch_ms_utc: { kind: 'number', required: false },
      int_updated_epoch_ms_utc: { kind: 'number', required: false },
      rel_home_team: { kind: 'relation', required: true },
      rel_away_team: { kind: 'relation', required: true },
      created: { kind: 'autodate', required: true },
      updated: { kind: 'autodate', required: true },
  },
  m_simulation_runs: {
      str_run_id: { kind: 'text', required: true },
      dt_run_date: { kind: 'date', required: true },
      int_season_year: { kind: 'number', required: false },
      int_through_week: { kind: 'number', required: false },
      int_games_played: { kind: 'number', required: false },
      int_games_remaining: { kind: 'number', required: false },
      int_total_iterations: { kind: 'number', required: false },
      int_random_seed: { kind: 'number', required: false },
      str_engine_version: { kind: 'text', required: false },
      str_top_favorite_code: { kind: 'text', required: false },
      dbl_top_favorite_prob: { kind: 'number', required: false },
      bool_is_active: { kind: 'bool', required: false },
      str_status_code: { kind: 'text', required: true },
      int_created_epoch_ms_utc: { kind: 'number', required: false },
      int_updated_epoch_ms_utc: { kind: 'number', required: false },
      created: { kind: 'autodate', required: true },
      updated: { kind: 'autodate', required: true },
  },
  o_team_outlooks: {
      str_run_id: { kind: 'text', required: true },
      str_team_code: { kind: 'text', required: true },
      dt_run_date: { kind: 'date', required: true },
      int_season_year: { kind: 'number', required: false },
      int_wins: { kind: 'number', required: false },
      int_losses: { kind: 'number', required: false },
      int_ties: { kind: 'number', required: false },
      dbl_elo_rating: { kind: 'number', required: false },
      dbl_projected_wins: { kind: 'number', required: false },
      dbl_division_prob: { kind: 'number', required: false },
      dbl_top_seed_prob: { kind: 'number', required: false },
      dbl_playoff_prob: { kind: 'number', required: false },
      dbl_divisional_prob: { kind: 'number', required: false },
      dbl_conference_prob: { kind: 'number', required: false },
      dbl_superbowl_prob: { kind: 'number', required: false },
      dbl_champion_prob: { kind: 'number', required: false },
      int_champion_rank: { kind: 'number', required: false },
      json_seed_odds: { kind: 'json', required: false },
      bool_is_active: { kind: 'bool', required: false },
      str_status_code: { kind: 'text', required: true },
      int_created_epoch_ms_utc: { kind: 'number', required: false },
      int_updated_epoch_ms_utc: { kind: 'number', required: false },
      rel_run: { kind: 'relation', required: true },
      rel_team: { kind: 'relation', required: true },
      created: { kind: 'autodate', required: true },
      updated: { kind: 'autodate', required: true },
  },
  o_superbowl_matchups: {
      str_run_id: { kind: 'text', required: true },
      str_afc_code: { kind: 'text', required: true },
      str_nfc_code: { kind: 'text', required: true },
      int_rank: { kind: 'number', required: false },
      dbl_probability: { kind: 'number', required: false },
      bool_is_active: { kind: 'bool', required: false },
      str_status_code: { kind: 'text', required: true },
      int_created_epoch_ms_utc: { kind: 'number', required: false },
      int_updated_epoch_ms_utc: { kind: 'number', required: false },
      rel_run: { kind: 'relation', required: true },
      rel_afc_team: { kind: 'relation', required: true },
      rel_nfc_team: { kind: 'relation', required: true },
      created: { kind: 'autodate', required: true },
      updated: { kind: 'autodate', required: true },
  },
  s_latest_team_outlooks: {
      str_team_code: { kind: 'text', required: true },
      str_team_name: { kind: 'text', required: true },
      str_conference: { kind: 'text', required: true },
      str_division: { kind: 'text', required: true },
      str_run_id: { kind: 'text', required: true },
      dt_run_date: { kind: 'date', required: true },
      int_season_year: { kind: 'number', required: false },
      int_through_week: { kind: 'number', required: false },
      int_wins: { kind: 'number', required: false },
      int_losses: { kind: 'number', required: false },
      int_ties: { kind: 'number', required: false },
      dbl_elo_rating: { kind: 'number', required: false },
      dbl_projected_wins: { kind: 'number', required: false },
      dbl_division_prob: { kind: 'number', required: false },
      dbl_playoff_prob: { kind: 'number', required: false },
      dbl_divisional_prob: { kind: 'number', required: false },
      dbl_conference_prob: { kind: 'number', required: false },
      dbl_superbowl_prob: { kind: 'number', required: false },
      dbl_champion_prob: { kind: 'number', required: false },
      int_champion_rank: { kind: 'number', required: false },
      json_seed_odds: { kind: 'json', required: false },
  },
};

/** Migration step fingerprints this file was generated from (compare with `tbl_schema_migrations`). */
export const SCHEMA_FINGERPRINTS: Readonly<Record<string, string>> = {
  '001_base_tables': 'b3b2f31e',
  '002_foreign_keys': 'a3526e00',
  '003_indexes': '0b652f96',
  '004_summary_views': 'c0ac36b9',
};
