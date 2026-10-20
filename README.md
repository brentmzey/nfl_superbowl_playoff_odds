# 🏈 NFL Super Bowl & Playoff Survival Odds (Kotlin Multiplatform)

Live 2026 NFL season model: it pulls every result from ESPN's public scoreboard API, builds margin-of-victory
**Elo power ratings**, then runs a **Monte Carlo simulation** of the rest of the regular season, NFL seeding and
tiebreakers, and the full 14-team playoff bracket. The output is each team's chance of making the playoffs,
surviving each round, and winning **Super Bowl LXI** (February 2027).

Sibling project to [`mlb_sabermetric_worldseries_kmp`](../mlb_sabermetric_worldseries_kmp), using the same
Kotlin Multiplatform layout, Gradle wrapper, and refresh-and-sync workflow.

<!-- SUMMARY:START -->
_Updated 2026-10-20 · 2026 season through week 6 (93 games played, 179 remaining) · 20000 simulations._

**Favorites:** San Francisco 49ers 16% · Buffalo Bills 10% · Minnesota Vikings 9.4%
**Most likely Super Bowl:** BUF vs SF (5.8%)
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
| 1 | San Francisco 49ers | 6-0 | 1666 | 13.5 | 84% | 98% | 80% | 50% | 30% | **16%** |
| 2 | Buffalo Bills | 5-1 | 1635 | 12.0 | 56% | 90% | 61% | 34% | 19% | **10%** |
| 3 | Minnesota Vikings | 5-0 | 1613 | 12.3 | 64% | 93% | 64% | 36% | 19% | **9.4%** |
| 4 | New England Patriots | 4-2 | 1634 | 11.0 | 42% | 83% | 53% | 29% | 16% | **8.4%** |
| 5 | Jacksonville Jaguars | 4-2 | 1621 | 11.3 | 77% | 86% | 56% | 30% | 15% | **7.8%** |
| 6 | Seattle Seahawks | 3-3 | 1655 | 10.3 | 13% | 74% | 46% | 25% | 14% | **7.7%** |
| 7 | Denver Broncos | 4-2 | 1603 | 10.8 | 39% | 76% | 45% | 23% | 12% | **6.0%** |
| 8 | Kansas City Chiefs | 5-0 | 1563 | 11.3 | 57% | 84% | 51% | 25% | 12% | **5.9%** |
| 9 | Chicago Bears | 4-2 | 1560 | 9.8 | 12% | 61% | 32% | 15% | 6.8% | **3.3%** |
| 10 | Pittsburgh Steelers | 4-2 | 1542 | 9.9 | 28% | 57% | 30% | 14% | 6.2% | **3.0%** |
| 11 | Baltimore Ravens | 4-2 | 1543 | 9.8 | 27% | 55% | 29% | 13% | 6.2% | **2.9%** |
| 12 | Detroit Lions | 3-2 | 1530 | 9.3 | 14% | 54% | 27% | 12% | 5.7% | **2.7%** |
| 13 | Carolina Panthers | 3-2 | 1516 | 9.1 | 58% | 63% | 32% | 14% | 5.9% | **2.6%** |
| 14 | Green Bay Packers | 4-2 | 1522 | 9.3 | 9.7% | 53% | 25% | 11% | 4.5% | **2.1%** |
| 15 | Cincinnati Bengals | 3-2 | 1515 | 9.5 | 23% | 51% | 25% | 10% | 4.4% | **2.0%** |
| 16 | Los Angeles Rams | 2-4 | 1554 | 8.0 | 2.0% | 32% | 17% | 7.6% | 3.8% | **1.9%** |
| 17 | Houston Texans | 2-4 | 1545 | 8.3 | 17% | 33% | 17% | 7.8% | 3.6% | **1.8%** |
| 18 | Cleveland Browns | 4-2 | 1476 | 9.4 | 22% | 47% | 20% | 7.6% | 3.2% | **1.4%** |
| 19 | New York Giants | 3-2-1 | 1448 | 8.1 | 43% | 46% | 21% | 7.4% | 2.8% | **1.1%** |
| 20 | Philadelphia Eagles | 2-4 | 1459 | 7.2 | 27% | 30% | 13% | 5.1% | 2.1% | **0.9%** |
| 21 | Atlanta Falcons | 2-4 | 1466 | 7.0 | 17% | 23% | 11% | 4.6% | 1.9% | **0.7%** |
| 22 | New Orleans Saints | 2-4 | 1444 | 7.0 | 17% | 21% | 9.8% | 3.9% | 1.4% | **0.6%** |
| 23 | Dallas Cowboys | 2-4 | 1431 | 6.8 | 19% | 21% | 9.2% | 3.4% | 1.1% | **0.4%** |
| 24 | Las Vegas Raiders | 3-3 | 1437 | 7.5 | 4.3% | 16% | 6.3% | 2.4% | 1.0% | **0.4%** |
| 25 | Tampa Bay Buccaneers | 1-5 | 1420 | 5.5 | 8.7% | 11% | 4.8% | 1.8% | 0.8% | **0.3%** |
| 26 | Indianapolis Colts | 2-4 | 1434 | 6.7 | 5.6% | 9.9% | 4.2% | 1.5% | 0.7% | **0.3%** |
| 27 | Washington Commanders | 1-4-1 | 1402 | 6.0 | 11% | 13% | 5.2% | 1.8% | 0.6% | **0.2%** |
| 28 | New York Jets | 2-4 | 1396 | 6.1 | 1.3% | 7.7% | 2.8% | 1.0% | 0.4% | **0.2%** |
| 29 | Arizona Cardinals | 2-4 | 1399 | 6.3 | 0.3% | 6.5% | 2.6% | 1.0% | 0.4% | **0.2%** |
| 30 | Miami Dolphins | 0-5 | 1373 | 4.0 | 0.3% | 1.0% | 0.4% | 0.2% | <0.1% | **<0.1%** |
| 31 | Tennessee Titans | 1-5 | 1350 | 4.6 | 1.0% | 1.9% | 0.7% | 0.2% | <0.1% | **<0.1%** |
| 32 | Los Angeles Chargers | 0-6 | 1407 | 4.1 | 0.1% | 0.8% | 0.3% | 0.1% | <0.1% | **<0.1%** |
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
  AFC1["(1) BUF 5-1<br/>div. rd 61%"] -->|bye| AFC_DIV
  AFC2["(2) JAX 4-2<br/>div. rd 56%"] --- AFC_G1(("2 v 7")) --- AFC7["(7) BAL 4-2<br/>div. rd 29%"]
  AFC3["(3) KC 5-0<br/>div. rd 51%"] --- AFC_G2(("3 v 6")) --- AFC6["(6) DEN 4-2<br/>div. rd 45%"]
  AFC4["(4) PIT 4-2<br/>div. rd 30%"] --- AFC_G3(("4 v 5")) --- AFC5["(5) NE 4-2<br/>div. rd 53%"]
  AFC_G1 --> AFC_DIV
  AFC_G2 --> AFC_DIV
  AFC_G3 --> AFC_DIV
  AFC_DIV{{"AFC Divisional"}} --> AFC_CCG{{"AFC Championship<br/>fav: BUF 19%"}}
end
subgraph NFC_WC["NFC"]
  direction TB
  NFC1["(1) SF 6-0<br/>div. rd 80%"] -->|bye| NFC_DIV
  NFC2["(2) MIN 5-0<br/>div. rd 64%"] --- NFC_G1(("2 v 7")) --- NFC7["(7) GB 4-2<br/>div. rd 25%"]
  NFC3["(3) CAR 3-2<br/>div. rd 32%"] --- NFC_G2(("3 v 6")) --- NFC6["(6) CHI 4-2<br/>div. rd 32%"]
  NFC4["(4) NYG 3-2-1<br/>div. rd 21%"] --- NFC_G3(("4 v 5")) --- NFC5["(5) SEA 3-3<br/>div. rd 46%"]
  NFC_G1 --> NFC_DIV
  NFC_G2 --> NFC_DIV
  NFC_G3 --> NFC_DIV
  NFC_DIV{{"NFC Divisional"}} --> NFC_CCG{{"NFC Championship<br/>fav: SF 30%"}}
end
AFC_CCG --> SB{{"Super Bowl 2027<br/>fav: SF 16%"}}
NFC_CCG --> SB
```
<!-- BRACKET:END -->

## 🤝 Most likely Super Bowl matchups

<!-- MATCHUPS:START -->
| AFC | NFC | Probability |
|---|---|---:|
| BUF | SF | 5.8% |
| NE | SF | 4.6% |
| JAX | SF | 4.4% |
| KC | SF | 3.8% |
| DEN | SF | 3.5% |
| BUF | MIN | 3.3% |
| BUF | SEA | 2.8% |
| NE | MIN | 2.8% |
| JAX | MIN | 2.7% |
| KC | MIN | 2.6% |
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
  `POCKETHOST_URL`, `POCKETHOST_USER`, and `POCKETHOST_PASSWORD`. The older `POCKETHOST_ADMIN_*` names also work.
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
