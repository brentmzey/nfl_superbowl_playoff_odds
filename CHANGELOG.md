# Changelog

All notable changes to this project follow [Semantic Versioning](https://semver.org/) with `-RC#` release candidates.

## [0.1.0-RC1] - 2026-10-19

### Added
- ESPN scoreboard ingestion (2025 prior season + live 2026 season) with on-disk caching of completed seasons.
- Margin-of-victory Elo with home field, a between-season regression to the mean, and in-simulation ("hot") updates.
- Monte Carlo simulation of the remaining regular season, NFL seeding with condensed tiebreakers, and the 14-team playoff bracket.
- Outputs: `output/latest.json`, `output/team_outlook.csv`, dated history snapshots.
- Dependency-free SVG charts (Super Bowl odds, playoff survival heatmap, Elo ratings, odds trend) with light/dark themes.
- Auto-generated README leaderboard, Mermaid projected bracket, and top Super Bowl matchups.
