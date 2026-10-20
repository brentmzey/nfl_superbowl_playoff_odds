# Changelog

All notable changes to this project follow [Semantic Versioning](https://semver.org/) with `-RC#` release candidates.

## [0.1.0-RC5] - 2026-10-19

### Added
- Target instance `https://nfl-superbowl-predictions.pockethost.io` via `POCKETHOST_URL`. A local `.env` is git-ignored and `.env.example` documents every variable.
- `clients/ts`: strictly typed PocketBase client (bun, TypeScript strict). It has generated schema types, JDBI-style `{:param}` query templates with compile-time parameter inference, injection-safe binding, runtime record validation, timeouts and retries, and `Result`/`Optional` returns. Ships with compile-time, unit, and opt-in live tests.
- Kotlin generator plus golden test for the TypeScript typings (`TypeScriptSchema.kt`).
- JDBI SqlObject query templates (`ReplicaQueries.kt`) over the SQLite and PostgreSQL replicas, tested on both.

### Changed
- `dt_` columns are PocketBase `date` fields holding normalised UTC values, `TIMESTAMPTZ` in PostgreSQL.
- Every collection has server-managed `created` / `updated` autodate fields (for `sort: '-created'`), mirrored in the replicas. Added a `(int_season_year, created)` index on runs.
- Collections and views carry semantic type names (`NflTeam`, `TeamOutlook`, …). The view SQL is built from typed column definitions.

## [0.1.0-RC4] - 2026-10-19

### Added
- Replication from PocketHost (source of truth) after every sync: timestamped JSON backups with a manifest (newest 30 kept, restorable), a SQLite file rebuilt from scratch, and a local PostgreSQL schema rebuilt from scratch.
- `SqlDialect` generates SQLite/PostgreSQL DDL from the shared schema, so every store has the same primary keys, `REFERENCES` foreign keys, unique/FK/query/partial indexes, and the summary view.
- DbFactory PostgreSQL provisioning (maintenance DB check, `template0`, UTF8, `en_US.UTF-8`, owner grant), with defaults matching local-db-stack.
- `--postgres auto|required|off` and `just db-up`.

## [0.1.0-RC3] - 2026-10-19

### Added
- Numbered stepwise migrations (`001_base_tables`, `002_foreign_keys`, `003_indexes`, `004_summary_views`) recorded with fingerprints in a `tbl_schema_migrations` ledger.
- Foreign keys: every `rel_` relation is now required and non-cascading, and validated by the server on write.
- Indexes: unique natural keys, an index leading on every FK, query indexes (weekly schedule, latest active run, leaderboard rank, team trends), a unique `(rel_run, rel_team)` constraint, and a partial index on unplayed games.
- `s_latest_team_outlooks` view joining each season's ACTIVE run to team metadata.
- `docs/DATABASE.md` with an ER diagram, the migration flow, and the reasoning behind each index.

### Changed
- Indexes moved out of collection creation into step 003, so they can cover relation columns. Migrations now also repair drift (missing columns, indexes, changed views) and never drop anything.

## [0.1.0-RC2] - 2026-10-19

### Added
- PocketHost / PocketBase (>= 0.23) sync, required by default (`--db off` to skip). It covers `i_nfl_teams`, `i_nfl_games`,
  `m_simulation_runs`, `o_team_outlooks`, and `o_superbowl_matchups`, with Hungarian snake_case columns, audit columns, and `rel_` relations.
- Idempotent stepwise REST migrations (base collections first, then relations, additive only) and an exported import JSON in `docs/schema/`.
- Upsert by natural key that skips unchanged rows, preserves creation time, and marks earlier runs `SUPERSEDED`. Rows are never deleted.
- Exponential back-off with jitter for 429 / 5xx / network errors. Settings come from `POCKETHOST_URL` / `POCKETHOST_USER` / `POCKETHOST_PASSWORD`
  (legacy `POCKETHOST_ADMIN_*` accepted) via env vars, `./.env`, or `~/.env`, and the run fails fast without them.
- `just refresh-local` and `just db-health`.

## [0.1.0-RC1] - 2026-10-19

### Added
- ESPN scoreboard ingestion (2025 prior season + live 2026 season) with on-disk caching of completed seasons.
- Margin-of-victory Elo with home field, a between-season regression to the mean, and in-simulation ("hot") updates.
- Monte Carlo simulation of the remaining regular season, NFL seeding with condensed tiebreakers, and the 14-team playoff bracket.
- Outputs: `output/latest.json`, `output/team_outlook.csv`, dated history snapshots.
- Dependency-free SVG charts (Super Bowl odds, playoff survival heatmap, Elo ratings, odds trend) with light/dark themes.
- Auto-generated README leaderboard, Mermaid projected bracket, and top Super Bowl matchups.
