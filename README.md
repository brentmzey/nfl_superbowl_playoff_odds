# 🏈 NFL Super Bowl & Playoff Survival Odds (Kotlin Multiplatform)

Live 2026 NFL season model: it pulls every result from ESPN's public scoreboard API, builds margin-of-victory
**Elo power ratings**, then runs a **Monte Carlo simulation** of the rest of the regular season, NFL seeding and
tiebreakers, and the full 14-team playoff bracket. The output is each team's chance of making the playoffs,
surviving each round, and winning **Super Bowl LXI** (February 2027).

Sibling project to [`mlb_sabermetric_worldseries_kmp`](../mlb_sabermetric_worldseries_kmp), using the same
Kotlin Multiplatform layout, Gradle wrapper, and refresh-and-sync workflow.

<!-- SUMMARY:START -->
_Updated 2026-11-12 · 2026 season through week 9 (136 games played, 136 remaining) · 20000 simulations._

**Favorites:** Buffalo Bills 15% · San Francisco 49ers 12% · Jacksonville Jaguars 10%
**Most likely Super Bowl:** BUF vs SF (6.8%)
<!-- SUMMARY:END -->

## 🏆 Super Bowl win probability

![Super Bowl win probability](docs/charts/superbowl_odds.svg)

## 🧗 Playoff survival by round

Each cell is the probability a team reaches that round, so reading across a row shows how quickly its odds fall off.

![Playoff survival heatmap](docs/charts/playoff_survival.svg)

## 📋 Full leaderboard

<!-- LEADERBOARD:START -->
| # | Team | Record | Elo | Proj. W | Division | Playoffs | Divisional | Conf. game | Super Bowl | **Win SB** |
|---:|---|:---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 1 | Buffalo Bills | 7-1 | 1680 | 13.3 | 90% | 99% | 77% | 48% | 28% | **15%** |
| 2 | San Francisco 49ers | 7-1 | 1642 | 12.8 | 81% | 95% | 73% | 44% | 24% | **12%** |
| 3 | Jacksonville Jaguars | 6-2 | 1656 | 12.2 | 92% | 97% | 67% | 37% | 19% | **10%** |
| 4 | Denver Broncos | 7-2 | 1639 | 12.4 | 71% | 96% | 64% | 34% | 17% | **8.9%** |
| 5 | Chicago Bears | 7-2 | 1631 | 11.7 | 33% | 85% | 55% | 31% | 17% | **8.2%** |
| 6 | Seattle Seahawks | 5-4 | 1638 | 10.1 | 13% | 58% | 34% | 19% | 10% | **5.3%** |
| 7 | Minnesota Vikings | 6-2 | 1574 | 11.2 | 29% | 79% | 47% | 23% | 11% | **5.0%** |
| 8 | Cincinnati Bengals | 6-2 | 1582 | 11.7 | 52% | 88% | 48% | 22% | 9.6% | **4.9%** |
| 9 | Detroit Lions | 6-2 | 1573 | 10.9 | 27% | 74% | 43% | 21% | 10% | **4.8%** |
| 10 | Pittsburgh Steelers | 6-2 | 1575 | 11.2 | 39% | 82% | 43% | 20% | 8.9% | **4.4%** |
| 11 | Green Bay Packers | 6-3 | 1570 | 10.3 | 11% | 58% | 31% | 14% | 7.0% | **3.5%** |
| 12 | Kansas City Chiefs | 6-2 | 1549 | 10.7 | 27% | 82% | 37% | 16% | 7.0% | **3.3%** |
| 13 | Los Angeles Rams | 5-4 | 1600 | 9.5 | 6.1% | 43% | 24% | 12% | 6.4% | **3.3%** |
| 14 | New England Patriots | 5-4 | 1578 | 9.6 | 9.8% | 67% | 30% | 12% | 5.7% | **3.1%** |
| 15 | New York Giants | 5-2-1 | 1497 | 9.9 | 76% | 78% | 37% | 15% | 5.8% | **2.2%** |
| 16 | Houston Texans | 3-5 | 1540 | 8.1 | 7.9% | 28% | 13% | 5.0% | 2.3% | **1.1%** |
| 17 | Tampa Bay Buccaneers | 3-6 | 1491 | 7.1 | 36% | 37% | 17% | 6.4% | 2.6% | **1.0%** |
| 18 | Cleveland Browns | 5-4 | 1460 | 8.8 | 6.5% | 35% | 12% | 3.8% | 1.5% | **0.6%** |
| 19 | Carolina Panthers | 3-5 | 1439 | 6.8 | 23% | 23% | 10% | 3.6% | 1.4% | **0.5%** |
| 20 | New Orleans Saints | 3-5 | 1437 | 6.8 | 23% | 23% | 9.7% | 3.5% | 1.3% | **0.5%** |
| 21 | Atlanta Falcons | 3-6 | 1453 | 6.6 | 18% | 19% | 8.5% | 3.3% | 1.3% | **0.5%** |
| 22 | Dallas Cowboys | 4-5 | 1464 | 7.6 | 17% | 20% | 8.8% | 3.3% | 1.2% | **0.5%** |
| 23 | Baltimore Ravens | 4-5 | 1466 | 7.7 | 1.9% | 14% | 5.2% | 1.8% | 0.7% | **0.4%** |
| 24 | Washington Commanders | 2-5-1 | 1415 | 6.1 | 6.5% | 7.0% | 3.1% | 1.2% | 0.4% | **0.2%** |
| 25 | Las Vegas Raiders | 4-5 | 1424 | 7.3 | 1.9% | 11% | 3.7% | 1.1% | 0.4% | **0.1%** |
| 26 | Indianapolis Colts | 2-7 | 1397 | 5.3 | 0.3% | 1.0% | 0.4% | 0.2% | <0.1% | **<0.1%** |
| 27 | Arizona Cardinals | 3-6 | 1396 | 6.5 | <0.1% | 1.5% | 0.5% | 0.2% | <0.1% | **<0.1%** |
| 28 | Miami Dolphins | 1-7 | 1367 | 3.8 | <0.1% | 0.1% | <0.1% | <0.1% | <0.1% | **<0.1%** |
| 29 | Los Angeles Chargers | 0-8 | 1372 | 3.2 | 0% | <0.1% | <0.1% | <0.1% | <0.1% | **<0.1%** |
| 30 | New York Jets | 2-7 | 1348 | 4.4 | <0.1% | 0.2% | <0.1% | <0.1% | <0.1% | **0%** |
| 31 | Tennessee Titans | 1-7 | 1333 | 3.6 | 0.1% | 0.1% | <0.1% | <0.1% | <0.1% | **0%** |
| 32 | Philadelphia Eagles | 2-7 | 1377 | 4.8 | 0.6% | 0.7% | 0.3% | <0.1% | <0.1% | **0%** |
<!-- LEADERBOARD:END -->

_Columns: **Division** = win the division · **Playoffs** = earn one of 7 seeds · **Divisional** = reach the
divisional round (a #1 seed bye or a wild-card win) · **Conf. game** = reach the conference championship ·
**Super Bowl** = win the conference · **Win SB** = win it all._

## 🗺️ Projected bracket (most likely seeding)

Seeds are assigned greedily: each seed goes to the team most likely to hold it. Labels show each team's
chance of reaching the divisional round.

<!-- BRACKET:START -->
```mermaid
flowchart LR
subgraph AFC_WC["AFC"]
  direction TB
  AFC1["(1) BUF 7-1<br/>div. rd 77%"] -->|bye| AFC_DIV
  AFC2["(2) JAX 6-2<br/>div. rd 67%"] --- AFC_G1(("2 v 7")) --- AFC7["(7) CLE 5-4<br/>div. rd 12%"]
  AFC3["(3) DEN 7-2<br/>div. rd 64%"] --- AFC_G2(("3 v 6")) --- AFC6["(6) NE 5-4<br/>div. rd 30%"]
  AFC4["(4) CIN 6-2<br/>div. rd 48%"] --- AFC_G3(("4 v 5")) --- AFC5["(5) KC 6-2<br/>div. rd 37%"]
  AFC_G1 --> AFC_DIV
  AFC_G2 --> AFC_DIV
  AFC_G3 --> AFC_DIV
  AFC_DIV{{"AFC Divisional"}} --> AFC_CCG{{"AFC Championship<br/>fav: BUF 28%"}}
end
subgraph NFC_WC["NFC"]
  direction TB
  NFC1["(1) SF 7-1<br/>div. rd 73%"] -->|bye| NFC_DIV
  NFC2["(2) CHI 7-2<br/>div. rd 55%"] --- NFC_G1(("2 v 7")) --- NFC7["(7) SEA 5-4<br/>div. rd 34%"]
  NFC3["(3) NYG 5-2-1<br/>div. rd 37%"] --- NFC_G2(("3 v 6")) --- NFC6["(6) GB 6-3<br/>div. rd 31%"]
  NFC4["(4) TB 3-6<br/>div. rd 17%"] --- NFC_G3(("4 v 5")) --- NFC5["(5) MIN 6-2<br/>div. rd 47%"]
  NFC_G1 --> NFC_DIV
  NFC_G2 --> NFC_DIV
  NFC_G3 --> NFC_DIV
  NFC_DIV{{"NFC Divisional"}} --> NFC_CCG{{"NFC Championship<br/>fav: SF 24%"}}
end
AFC_CCG --> SB{{"Super Bowl 2027<br/>fav: BUF 15%"}}
NFC_CCG --> SB
```
<!-- BRACKET:END -->

## 🤝 Most likely Super Bowl matchups

<!-- MATCHUPS:START -->
| AFC | NFC | Probability |
|---|---|---:|
| BUF | SF | 6.8% |
| JAX | SF | 4.7% |
| BUF | CHI | 4.6% |
| DEN | SF | 4.1% |
| JAX | CHI | 3.0% |
| BUF | MIN | 3.0% |
| BUF | SEA | 2.9% |
| BUF | DET | 2.9% |
| DEN | CHI | 2.8% |
| CIN | SF | 2.4% |
<!-- MATCHUPS:END -->

## 📈 Odds over the season

<!-- TREND:START -->
![Super Bowl odds over time](docs/charts/odds_trend.svg)
<!-- TREND:END -->

## ⚡ Power ratings

![Elo ratings](docs/charts/elo_ratings.svg)

## 🔄 How it works

```mermaid
flowchart LR
    ESPN[("ESPN scoreboard API<br/>2025 + 2026 seasons")] --> Parse["EspnScoreboardParser<br/>drop Pro Bowl + preseason"]
    Parse --> Prior["2025 Elo<br/>regular + postseason"]
    Prior --> Regress["Regress 1/3<br/>to 1505"]
    Regress --> Current["2026 Elo<br/>results to date"]
    Current --> MC{{"Monte Carlo × 20,000"}}
    Parse --> Sched[Remaining schedule]
    Sched --> MC
    subgraph Run["One simulated season"]
        direction TB
        G["Sample each margin<br/>Normal(Elo spread, 13.5)"] --> U["Update Elo after<br/>every game"]
        U --> S["Standings + tiebreakers<br/>7 seeds per conference"]
        S --> P["Wild card, Divisional,<br/>Conference, Super Bowl"]
    end
    MC --> Run
    Run --> Agg["Tally round reached,<br/>seeds, matchups"]
    Agg --> Out["output/latest.json, CSV,<br/>history snapshot"]
    Agg --> Charts["docs/charts/*.svg"]
    Agg --> Readme["README sections"]
    Agg --> PB[("PocketHost / PocketBase<br/>i_ inputs · m_ runs · o_ outputs")]
```

### Playoff format simulated

```mermaid
flowchart LR
    S1["Seed 1"] -->|bye| D1
    S2["Seed 2"] --- W1(("WC")) --- S7["Seed 7"]
    S3["Seed 3"] --- W2(("WC")) --- S6["Seed 6"]
    S4["Seed 4"] --- W3(("WC")) --- S5["Seed 5"]
    W1 & W2 & W3 --> D1{{"Divisional<br/>seed 1 hosts lowest seed left"}}
    D1 --> C{{"Conference championship"}}
    C --> SB{{"Super Bowl (neutral site)"}}
```

The higher seed hosts every game until the Super Bowl. See [docs/METHODOLOGY.md](docs/METHODOLOGY.md) for
the equations, the tiebreakers, and the model's known limitations.

## 🗄️ PocketHost database

Every refresh is written to the PocketBase instance **`https://nfl-superbowl-predictions.pockethost.io`** on
[PocketHost](https://pockethost.io), configured through `POCKETHOST_URL`. The database is
**required** by default: a run fails fast, before fetching anything, if settings are missing. Full design,
including the ER diagram, migrations, foreign keys, and index rationale, is in **[docs/DATABASE.md](docs/DATABASE.md)**.

```mermaid
flowchart LR
    T[i_nfl_teams] -->|rel_home/away_team| G[i_nfl_games]
    T -->|rel_team| O[o_team_outlooks]
    R[m_simulation_runs] -->|rel_run| O
    R -->|rel_run| M[o_superbowl_matchups]
    T -->|rel_afc/nfc_team| M
    O -.->|ACTIVE runs| V[[s_latest_team_outlooks view]]
```

- **Setup:** create an instance on pockethost.io, then copy `.env.example` to `.env` (git-ignored) and fill in
  `POCKETHOST_URL`, `POCKETHOST_USER`, and `POCKETHOST_PASSWORD`. The `PB_ADMIN_EMAIL` / `PB_ADMIN_PASSWORD` and older `POCKETHOST_ADMIN_*` names also work,
  and `.env` supports bash parameter substitution (`${VAR:-default}`, `${VAR?error message}`).
  Values are read from the environment first, then `./.env`, then `~/.env`. `just db-health` checks the instance.
- **Stepwise migrations**, recorded in `tbl_schema_migrations`: `001_base_tables`, then `002_foreign_keys` (required,
  non-cascading `rel_` relations), then `003_indexes` (unique natural keys, an index on every FK, query and partial
  indexes), then `004_summary_views`. Each step is idempotent, additive only, and repairs drift.
- **Idempotent writes:** rows are upserted on their natural key, so unchanged rows are skipped, changed rows are
  PATCHed (keeping their creation time), and new rows are POSTed. Rows are never deleted. Earlier runs become `SUPERSEDED`.
- **Resilience:** `_superusers` auth (PocketBase ≥ 0.23) with the token kept in memory only, plus exponential
  back-off with jitter on 429 / 5xx / network errors.
- **Replicas and backups:** after each sync, PocketHost (the source of truth) is pulled back into a timestamped
  JSON backup (`backups/`), a fresh SQLite file (`output/db/`), and local PostgreSQL on `localhost:15432`, set up
  per the DbFactory rule. Every store shares the same tables, foreign keys, indexes, and view.
- `just refresh-local` (`--db off`) runs without the database.

### 🔎 Querying it

- **TypeScript ([`clients/ts`](clients/ts)):** a strictly typed, read-only PocketBase client. It has types generated
  from the Kotlin schema, JDBI-style `{:param}` query templates whose parameter types are inferred at compile time,
  injection-safe binding, runtime record validation, retries, and `Result` errors. Built with bun.
- **Kotlin / JVM:** [JDBI](https://jdbi.org) SqlObject templates (`ReplicaQueries.kt`) over the SQLite and
  PostgreSQL replicas, with named parameters, typed row classes, enums, and `Instant`s. The same query catalogue
  runs on both dialects.

## 🚀 Run it

```bash
just refresh                 # fetch live data, simulate, regenerate charts + README, sync PocketHost
just refresh-local           # same, without the database
just db-health               # ping the configured PocketHost instance
just db-up                   # start local PostgreSQL (local-db-stack) for the replica
just test                    # tests + JaCoCo report + ≥90% line/branch coverage gate
just ts-check                # TypeScript client: tests + coverage, strict typecheck, build
just check-all               # both
./gradlew run -PsimArgs="--season 2026 --iterations 50000 --seed 7 --db off"
NFL_LIVE=1 ./gradlew jvmTest # also hit the real ESPN API
```

Needs JDK 21. Completed seasons are cached in `.cache/espn/`, and the in-progress season is refetched on every run.
Each run also writes `output/history/<date>.json`, and those snapshots feed the odds-over-time chart.

## 📂 Layout

| Path | What |
|---|---|
| `src/commonMain/.../model` | League registry (32 teams, divisions), `Game`, `Round` |
| `src/commonMain/.../engine` | `EloModel`, `SeasonTable`, `Seeding` (tiebreakers), `Playoffs`, `SeasonSimulator` |
| `src/commonMain/.../data` | ESPN JSON parser (multiplatform) |
| `src/commonMain/.../report` | Probabilities, Markdown fragments, dependency-free SVG charts |
| `src/jvmMain/.../data` | HTTP client and on-disk cache |
| `src/commonMain/.../sync` | PocketBase schema (collections, Hungarian columns) and record mapping |
| `src/jvmMain/.../sync` | PocketBase REST client: auth, migrations, upserts, back-off |
| `src/jvmMain/.../replica` | PocketHost snapshot, JSON backups, SQLite/PostgreSQL rebuilds, DbFactory, JDBI query templates |
| `clients/ts/` | Typed TypeScript PocketBase client (bun); `src/generated/schema.ts` is generated by the Kotlin pipeline |
| `src/jvmMain/.../app` | `Pipeline` (end-to-end refresh) and `Main` CLI |
| `output/` | `latest.json`, `team_outlook.csv`, `history/` snapshots |
| `docs/schema/` | PocketBase import JSON (generated each run) |

_Data: ESPN public scoreboard API (unofficial, not affiliated). This is a statistical model for fun, not betting advice._
