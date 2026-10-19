# Methodology

## 1. Ratings: margin-of-victory Elo

Every team starts the 2025 season at the league mean (1505). Each final game, regular season and playoffs, then
moves rating points from the loser to the winner:

$$E_{home} = \frac{1}{1 + 10^{-(R_{home} - R_{away} + HFA)/400}}, \qquad HFA = 48 \text{ (0 at neutral sites)}$$

$$\Delta = K \cdot \ln(|MOV| + 1) \cdot \frac{2.2}{0.001 \cdot \text{EloDiff}_{winner} + 2.2} \cdot (S - E_{home}), \qquad K = 20$$

`S` is 1 for a win, 0 for a loss, and ½ for a tie. The second factor dampens favourites' blowouts so that
ratings don't inflate through autocorrelation. Ratings are zero-sum.

Between seasons every rating is regressed one third of the way back to 1505, and the 2026 games are then applied
in kickoff order. That prior matters early in the year. A 3-3 Seattle still rates near the top because it won
Super Bowl LX.

## 2. Game model

The Elo edge converts to a point spread of `edge / 25`. Each simulated game draws a final margin:

$$M \sim \mathcal{N}\left(\frac{R_{home} - R_{away} + HFA}{25},\ 13.5^2\right)$$

The draw is rounded and redrawn on 0, so simulated games never tie. A sampled margin is needed because point
differential is one of the tiebreakers. After every simulated game the ratings update with the same Elo rule
(the "hot" simulation), so a team that starts winning in one run keeps getting stronger in that run. That widens
the spread of outcomes in the right way: the further ahead a game is, the more uncertain its result.

## 3. Seeding and tiebreakers

Seven seeds per conference: the four division winners take seeds 1-4, ranked against each other, and the three
best remaining clubs are wild cards (5-7). Ties follow a condensed version of the NFL procedure:

| Step | Division ties | Wild-card / seeding ties |
|---|---|---|
| 0 | — | Keep only the highest-ranked club from each division |
| 1 | Head-to-head win % | Head-to-head win %, only when every tied club has met |
| 2 | Division win % | Conference win % |
| 3 | Conference win % | Net points |
| 4 | Net points | Coin flip |
| 5 | Coin flip | |

As in the rulebook, whenever a step eliminates some clubs but not all, the procedure restarts from step 1
with the clubs that remain. Common games, strength of victory, and strength of schedule are **not** modeled.
They rarely decide seeds, but they occasionally do.

## 4. Playoffs

Wild card: 2v7, 3v6, 4v5, with the #1 seed on a bye. Divisional: #1 hosts the lowest remaining seed, and the
other two winners meet. The higher seed hosts through the conference championship, and the Super Bowl is
neutral. Each team's furthest round is tallied, so `P(reach round r)` is non-increasing by construction.

## 5. Known limitations

- **No roster information.** Injuries, QB changes, and trades only show up after they affect results.
- **Prior carries over from 2025.** Offseason changes are captured only by the one-third regression.
- **No market blend.** Betting markets carry information this model lacks, so treat large gaps from market odds
  as a reason to investigate, not as an edge.
- Simulated games never tie, and real ties (about 0.3% of games) are only counted when they have already happened.
- Home field is a flat 48 Elo points (about 2 points of spread) for every venue.

## 6. Reproducibility

The default run is 20,000 iterations with the seed set to the season year, so a run on the same data gives the
same numbers. With 20,000 iterations, a 10% probability has a Monte Carlo standard error of about ±0.2 points.
