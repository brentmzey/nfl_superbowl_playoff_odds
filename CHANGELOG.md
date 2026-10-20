# Changelog

All notable changes to this project follow [Semantic Versioning](https://semver.org/) with `-RC#` release candidates.

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
