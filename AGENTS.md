# AGENTS.md — NFL Super Bowl & Playoff Survival Odds

Repo-specific rules for every agent (Claude Code, Gemini / Antigravity). Also follow `/Users/brentzey/personal/AGENTS.md`.

## Full refresh ("refresh", "update the odds", "run the gamut")
```bash
just refresh     # ./gradlew run: fetch ESPN -> Elo -> 20k sims -> output/, docs/charts/, README sections
just test        # ./gradlew check jacocoTestReport (fails below 90% line/branch coverage, Main excluded)
```
- README content between `<!-- NAME:START -->` / `<!-- NAME:END -->` markers is generated. Edit `MarkdownReport.kt` instead of the README text inside them.
- Every run writes `output/history/<date>.json`. Commit those snapshots, because they drive the odds-trend chart.
- `.cache/espn/` is git-ignored. Completed seasons are cached and the current season is always refetched.

## PocketHost / PocketBase
- The database is required: `just refresh` fails fast without `POCKETHOST_URL` / `POCKETHOST_USER` / `POCKETHOST_PASSWORD` (env, `./.env`, or `~/.env`). Use `just refresh-local` for offline work.
- Real values live only in the git-ignored `.env`. Never commit them, echo them, or put them in `.env.example`, logs, or test fixtures.
- Naming: `i_` input, `m_` model runs, `o_` model output (also `s_` summary, `f_` final if added later). Columns are Hungarian snake_case (`str_`, `int_`, `dbl_`, `bool_`, `json_`, `dt_`, `rel_`), and every row carries the audit columns.
- Schema changes go in `PocketBaseSchema.kt` only. Migrations are additive and stepwise (base columns, then relations). Never drop or rename a live column; add a new one instead.
- Never delete rows. Change `str_status_code` (ACTIVE / INACTIVE / SUPERSEDED / ARCHIVED) instead.
- Keep numbers and booleans non-`required` (PocketBase treats 0 / false as blank).

## Architecture
- `commonMain` stays pure Kotlin (no JVM APIs): model, engine, parser, report/SVG. IO lives only in `jvmMain`.
- The simulator's inner loop uses mutable arrays (`SeasonTable`, `DoubleArray` ratings) for speed. Keep that mutation local and expose only immutable data (`Ratings`, `SimulationTallies`, `SimulationReport`).
- Model parameters belong in `EloParams` / `SimulationConfig`. Document any change in `docs/METHODOLOGY.md` and `CHANGELOG.md`.
- Charts follow the dataviz palette (blue series, blue/red diverging, light/dark tokens inside each SVG). Every SVG must parse as XML (`PipelineTest` checks this).

## Testing
- Integration first: `EspnClientIntegrationTest` (local HTTP server replaying a captured ESPN payload), `PocketBaseClientTest` (real client against `FakePocketBase`, an in-memory PocketBase that enforces schema, auth, filters and paging), and `PipelineTest` (end-to-end into a temp dir).
- `NFL_LIVE=1 ./gradlew jvmTest` additionally hits the real ESPN API.
