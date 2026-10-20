# 🏈 NFL Super Bowl & Playoff Survival Odds (Kotlin Multiplatform)

Live 2026 NFL season model: it pulls every result from ESPN's public scoreboard API, builds margin-of-victory
**Elo power ratings**, then runs a **Monte Carlo simulation** of the rest of the regular season, NFL seeding and
tiebreakers, and the full 14-team playoff bracket. The output is each team's chance of making the playoffs,
surviving each round, and winning **Super Bowl LXI** (February 2027).

Sibling project to [`mlb_sabermetric_worldseries_kmp`](../mlb_sabermetric_worldseries_kmp), using the same
Kotlin Multiplatform layout, Gradle wrapper, and refresh-and-sync workflow.

<!-- SUMMARY:START -->
_Updated 2026-10-19 · 2026 season through week 6 (92 games played, 180 remaining) · 20000 simulations._

**Favorites:** San Francisco 49ers 15% · Buffalo Bills 9.8% · Minnesota Vikings 9.3%
**Most likely Super Bowl:** BUF vs SF (5.5%)
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
| 1 | San Francisco 49ers | 5-0 | 1660 | 13.2 | 81% | 97% | 77% | 48% | 28% | **15%** |
| 2 | Buffalo Bills | 5-1 | 1635 | 12.0 | 56% | 90% | 61% | 34% | 19% | **9.8%** |
| 3 | Minnesota Vikings | 5-0 | 1613 | 12.3 | 64% | 93% | 64% | 36% | 19% | **9.3%** |
| 4 | New England Patriots | 4-2 | 1634 | 11.0 | 43% | 83% | 52% | 29% | 16% | **8.4%** |
| 5 | Seattle Seahawks | 3-3 | 1655 | 10.3 | 16% | 74% | 46% | 26% | 15% | **8.1%** |
| 6 | Jacksonville Jaguars | 4-2 | 1621 | 11.3 | 76% | 86% | 56% | 30% | 15% | **8.0%** |
| 7 | Denver Broncos | 4-2 | 1603 | 10.9 | 39% | 76% | 45% | 23% | 12% | **6.1%** |
| 8 | Kansas City Chiefs | 5-0 | 1563 | 11.3 | 57% | 84% | 51% | 25% | 12% | **5.8%** |
| 9 | Chicago Bears | 4-2 | 1560 | 9.8 | 12% | 61% | 32% | 15% | 6.9% | **3.4%** |
| 10 | Baltimore Ravens | 4-2 | 1543 | 9.8 | 27% | 55% | 28% | 13% | 6.4% | **3.0%** |
| 11 | Carolina Panthers | 3-2 | 1516 | 9.1 | 59% | 64% | 33% | 15% | 5.9% | **2.7%** |
| 12 | Pittsburgh Steelers | 4-2 | 1542 | 9.9 | 28% | 57% | 29% | 13% | 5.7% | **2.6%** |
| 13 | Detroit Lions | 3-2 | 1530 | 9.3 | 14% | 54% | 27% | 12% | 5.5% | **2.6%** |
| 14 | Green Bay Packers | 4-2 | 1522 | 9.4 | 10% | 53% | 26% | 11% | 4.9% | **2.4%** |
| 15 | Cincinnati Bengals | 3-2 | 1515 | 9.5 | 23% | 50% | 24% | 11% | 4.7% | **2.1%** |
| 16 | Los Angeles Rams | 2-4 | 1554 | 8.0 | 2.6% | 32% | 16% | 7.5% | 3.7% | **2.0%** |
| 17 | Houston Texans | 2-4 | 1545 | 8.3 | 17% | 34% | 17% | 7.7% | 3.5% | **1.7%** |
| 18 | Cleveland Browns | 4-2 | 1476 | 9.5 | 22% | 48% | 21% | 8.2% | 3.1% | **1.3%** |
| 19 | New York Giants | 3-2-1 | 1448 | 8.1 | 41% | 45% | 20% | 7.5% | 2.8% | **1.1%** |
| 20 | Philadelphia Eagles | 2-4 | 1459 | 7.2 | 27% | 29% | 14% | 5.4% | 2.3% | **1.0%** |
| 21 | Atlanta Falcons | 2-4 | 1466 | 7.0 | 17% | 24% | 11% | 4.7% | 1.9% | **0.9%** |
| 22 | New Orleans Saints | 2-4 | 1444 | 6.9 | 17% | 21% | 9.6% | 3.7% | 1.5% | **0.7%** |
| 23 | Dallas Cowboys | 2-4 | 1431 | 6.8 | 19% | 21% | 9.4% | 3.5% | 1.3% | **0.6%** |
| 24 | Las Vegas Raiders | 3-3 | 1437 | 7.5 | 4.4% | 16% | 6.5% | 2.6% | 1.0% | **0.5%** |
| 25 | Washington Commanders | 1-3-1 | 1407 | 6.2 | 13% | 16% | 6.6% | 2.5% | 0.8% | **0.4%** |
| 26 | Tampa Bay Buccaneers | 1-5 | 1420 | 5.5 | 8.2% | 10% | 4.7% | 1.8% | 0.8% | **0.3%** |
| 27 | Indianapolis Colts | 2-4 | 1434 | 6.7 | 5.8% | 10% | 4.3% | 1.6% | 0.6% | **0.3%** |
| 28 | Arizona Cardinals | 2-4 | 1399 | 6.3 | 0.4% | 6.4% | 2.3% | 1.0% | 0.4% | **0.2%** |
| 29 | New York Jets | 2-4 | 1396 | 6.1 | 1.2% | 7.3% | 2.8% | 1.0% | 0.4% | **0.1%** |
| 30 | Tennessee Titans | 1-5 | 1350 | 4.6 | 0.9% | 1.8% | 0.7% | 0.2% | 0.1% | **<0.1%** |
| 31 | Los Angeles Chargers | 0-6 | 1407 | 4.1 | <0.1% | 0.8% | 0.3% | 0.1% | <0.1% | **<0.1%** |
| 32 | Miami Dolphins | 0-5 | 1373 | 4.0 | 0.2% | 1.0% | 0.5% | 0.2% | <0.1% | **<0.1%** |
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
  AFC2["(2) JAX 4-2<br/>div. rd 56%"] --- AFC_G1(("2 v 7")) --- AFC7["(7) CLE 4-2<br/>div. rd 21%"]
  AFC3["(3) KC 5-0<br/>div. rd 51%"] --- AFC_G2(("3 v 6")) --- AFC6["(6) DEN 4-2<br/>div. rd 45%"]
  AFC4["(4) PIT 4-2<br/>div. rd 29%"] --- AFC_G3(("4 v 5")) --- AFC5["(5) NE 4-2<br/>div. rd 52%"]
  AFC_G1 --> AFC_DIV
  AFC_G2 --> AFC_DIV
  AFC_G3 --> AFC_DIV
  AFC_DIV{{"AFC Divisional"}} --> AFC_CCG{{"AFC Championship<br/>fav: BUF 19%"}}
end
subgraph NFC_WC["NFC"]
  direction TB
  NFC1["(1) SF 5-0<br/>div. rd 77%"] -->|bye| NFC_DIV
  NFC2["(2) MIN 5-0<br/>div. rd 64%"] --- NFC_G1(("2 v 7")) --- NFC7["(7) GB 4-2<br/>div. rd 26%"]
  NFC3["(3) CAR 3-2<br/>div. rd 33%"] --- NFC_G2(("3 v 6")) --- NFC6["(6) CHI 4-2<br/>div. rd 32%"]
  NFC4["(4) NYG 3-2-1<br/>div. rd 20%"] --- NFC_G3(("4 v 5")) --- NFC5["(5) SEA 3-3<br/>div. rd 46%"]
  NFC_G1 --> NFC_DIV
  NFC_G2 --> NFC_DIV
  NFC_G3 --> NFC_DIV
  NFC_DIV{{"NFC Divisional"}} --> NFC_CCG{{"NFC Championship<br/>fav: SF 28%"}}
end
AFC_CCG --> SB{{"Super Bowl 2027<br/>fav: SF 15%"}}
NFC_CCG --> SB
```
<!-- BRACKET:END -->

## 🤝 Most likely Super Bowl matchups

<!-- MATCHUPS:START -->
| AFC | NFC | Probability |
|---|---|---:|
| BUF | SF | 5.5% |
| NE | SF | 4.5% |
| JAX | SF | 4.0% |
| BUF | MIN | 3.5% |
| DEN | SF | 3.4% |
| KC | SF | 3.2% |
| NE | MIN | 3.1% |
| JAX | MIN | 3.0% |
| BUF | SEA | 2.8% |
| JAX | SEA | 2.4% |
<!-- MATCHUPS:END -->

## 📈 Odds over the season

<!-- TREND:START -->
_The trend chart appears once a second weekly snapshot exists in `output/history/`._
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

Every refresh is written to a PocketBase instance on [PocketHost](https://pockethost.io). The database is
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

## 🚀 Run it

```bash
just refresh                 # fetch live data, simulate, regenerate charts + README, sync PocketHost
just refresh-local           # same, without the database
just db-health               # ping the configured PocketHost instance
just db-up                   # start local PostgreSQL (local-db-stack) for the replica
just test                    # tests + JaCoCo report + ≥90% line/branch coverage gate
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
| `src/jvmMain/.../replica` | PocketHost snapshot, JSON backups, SQLite/PostgreSQL rebuilds, DbFactory |
| `src/jvmMain/.../app` | `Pipeline` (end-to-end refresh) and `Main` CLI |
| `output/` | `latest.json`, `team_outlook.csv`, `history/` snapshots |
| `docs/schema/` | PocketBase import JSON (generated each run) |

_Data: ESPN public scoreboard API (unofficial, not affiliated). This is a statistical model for fun, not betting advice._
