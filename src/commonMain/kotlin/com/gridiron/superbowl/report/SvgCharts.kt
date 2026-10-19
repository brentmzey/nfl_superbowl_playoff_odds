package com.gridiron.superbowl.report

import com.gridiron.superbowl.model.Round
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

/** A dated championship-odds snapshot used for the trend chart. */
data class OddsSnapshot(val label: String, val odds: Map<String, Double>)

/**
 * Dependency-free SVG charts. Each file carries its own light/dark tokens (via `prefers-color-scheme`)
 * and native `<title>` tooltips, so they render well on GitHub and in any browser.
 */
object SvgCharts {
    private val SERIES = listOf("#2a78d6", "#eb6834", "#1baf7a", "#eda100", "#e87ba4", "#008300")
    private val SERIES_DARK = listOf("#3987e5", "#d95926", "#199e70", "#c98500", "#d55181", "#008300")

    private fun style(): String {
        fun series(colors: List<String>) = colors.mapIndexed { i, c -> ".s${i + 1}{fill:$c} .l${i + 1}{stroke:$c}" }.joinToString(" ")
        return """
        <style>
          .bg{fill:#fcfcfb} .t{fill:#0b0b0b} .t2{fill:#52514e} .m{fill:#898781}
          .grid{stroke:#e1e0d9;stroke-width:1} .axis{stroke:#c3c2b7;stroke-width:1} .ring{stroke:#fcfcfb;stroke-width:2}
          .pos{fill:#2a78d6} .neg{fill:#e34948} .on{fill:#ffffff} ${series(SERIES)}
          @media (prefers-color-scheme: dark){
            .bg{fill:#1a1a19} .t{fill:#ffffff} .t2{fill:#c3c2b7}
            .grid{stroke:#2c2c2a} .axis{stroke:#383835} .ring{stroke:#1a1a19}
            .pos{fill:#3987e5} .neg{fill:#e66767} ${series(SERIES_DARK)}
          }
          .title{font-size:16px;font-weight:600} .sub{font-size:12px}
          text{font-family:system-ui,-apple-system,"Segoe UI",sans-serif;font-size:12px}
          .num{font-variant-numeric:tabular-nums}
          .hover:hover{opacity:.82}
        </style>""".trimIndent()
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    fun pct(p: Double): String = when {
        p <= 0.0 -> "0%"
        p < 0.001 -> "<0.1%"
        p < 0.10 -> "${(p * 1000).roundToInt() / 10.0}%"
        else -> "${(p * 100).roundToInt()}%"
    }

    private fun svg(w: Int, h: Int, title: String, subtitle: String, body: String) = listOf(
        "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 $w $h\" width=\"$w\" height=\"$h\" role=\"img\" aria-label=\"${esc(title)}\">",
        style(),
        "<rect class=\"bg\" width=\"$w\" height=\"$h\" rx=\"8\"/>",
        "<text class=\"t title\" x=\"24\" y=\"32\">${esc(title)}</text>",
        "<text class=\"t2 sub\" x=\"24\" y=\"52\">${esc(subtitle)}</text>",
        body,
        "</svg>",
    ).joinToString("\n", postfix = "\n")

    /** Rounded-end horizontal bar anchored at x0 (square on the baseline side). */
    private fun hbar(x0: Double, y: Double, len: Double, h: Double, cls: String): String {
        if (len < 0.5) return ""
        val r = minOf(4.0, len, h / 2)
        val x1 = x0 + len
        return "<path d=\"M$x0 $y H${x1 - r} Q$x1 $y $x1 ${y + r} V${y + h - r} Q$x1 ${y + h} ${x1 - r} ${y + h} H$x0 Z\" class=\"$cls\"/>"
    }

    private fun hbarLeft(x0: Double, y: Double, len: Double, h: Double, cls: String): String {
        if (len < 0.5) return ""
        val r = minOf(4.0, len, h / 2)
        val x1 = x0 - len
        return "<path d=\"M$x0 $y H${x1 + r} Q$x1 $y $x1 ${y + r} V${y + h - r} Q$x1 ${y + h} ${x1 + r} ${y + h} H$x0 Z\" class=\"$cls\"/>"
    }

    /** Evenly spaced probability ticks on round values, covering [maxValue]. */
    internal fun probabilityTicks(maxValue: Double): List<Double> {
        val step = listOf(0.01, 0.02, 0.025, 0.05, 0.1, 0.2, 0.25).first { it * 5 >= maxValue }
        val count = max(1, ceil(maxValue / step - 1e-9).toInt())
        return (0..count).map { it * step }
    }

    fun championshipOdds(report: SimulationReport, top: Int = 16): String {
        val rows = report.byChampionOdds.take(top)
        val left = 150.0
        val width = 760
        val plotW = width - left - 80
        val rowH = 26.0
        val top0 = 84.0
        val ticks = probabilityTicks(rows.maxOf { it.pChampion })
        val maxV = ticks.last()
        val height = (top0 + rows.size * rowH + 40).toInt()
        val grid = ticks.joinToString("\n") { t ->
            val x = left + plotW * t / maxV
            "<line class=\"grid\" x1=\"$x\" x2=\"$x\" y1=\"${top0 - 8}\" y2=\"${top0 + rows.size * rowH}\"/>" +
                "<text class=\"m num\" x=\"$x\" y=\"${top0 + rows.size * rowH + 18}\" text-anchor=\"middle\">${pct(t)}</text>"
        }
        val bars = rows.mapIndexed { i, t ->
            val y = top0 + i * rowH
            val len = plotW * t.pChampion / maxV
            """<g class="hover"><title>${esc(t.name)} (${t.record}) - win Super Bowl ${pct(t.pChampion)}, reach ${pct(t.pSuperBowl)}, make playoffs ${pct(t.pPlayoffs)}</title>
              <rect x="0" y="$y" width="$width" height="$rowH" fill="transparent"/>
              <text class="t" x="24" y="${y + 17}" font-weight="600">${t.abbr}</text>
              <text class="t2 num" x="68" y="${y + 17}">${t.record}</text>
              <text class="m num" x="${left - 10}" y="${y + 17}" text-anchor="end">${t.elo.roundToInt()}</text>
              ${hbar(left, y + 5, len, rowH - 10, "s1")}
              <text class="t num" x="${left + len + 6}" y="${y + 17}">${pct(t.pChampion)}</text></g>"""
        }.joinToString("\n")
        val header = "<text class=\"m\" x=\"${left - 10}\" y=\"${top0 - 12}\" text-anchor=\"end\">Elo</text>"
        return svg(
            width, height,
            "Super Bowl ${report.season + 1} win probability",
            "Top $top teams · ${report.iterations} simulations · through week ${report.throughWeek}",
            "$header\n$grid\n<line class=\"axis\" x1=\"$left\" x2=\"$left\" y1=\"${top0 - 8}\" y2=\"${top0 + rows.size * rowH}\"/>\n$bars",
        )
    }

    fun playoffSurvival(report: SimulationReport, top: Int = 20): String {
        val rows = report.byChampionOdds.take(top)
        val rounds = Round.entries
        val left = 150.0
        val cellW = 118.0
        val cellH = 26.0
        val gap = 2.0
        val top0 = 96.0
        val width = (left + rounds.size * cellW + 24).toInt()
        val height = (top0 + rows.size * cellH + 40).toInt()
        val headers = rounds.mapIndexed { j, r ->
            "<text class=\"t2\" x=\"${left + j * cellW + cellW / 2}\" y=\"${top0 - 12}\" text-anchor=\"middle\">${r.label}</text>"
        }.joinToString("\n")
        val cells = rows.mapIndexed { i, t ->
            val y = top0 + i * cellH
            val label = "<text class=\"t\" x=\"24\" y=\"${y + 17}\" font-weight=\"600\">${t.abbr}</text>" +
                "<text class=\"t2 num\" x=\"68\" y=\"${y + 17}\">${t.record}</text>"
            label + rounds.mapIndexed { j, r ->
                val p = t.probability(r)
                val opacity = (p.pow(0.6) * 100).roundToInt() / 100.0
                val textAttrs = if (opacity > 0.55) "class=\"on num\"" else "class=\"t num\""
                val x = left + j * cellW
                """<g class="hover"><title>${esc(t.name)}: ${r.label} ${pct(p)}</title>
                  <rect x="${x + gap / 2}" y="${y + gap / 2}" width="${cellW - gap}" height="${cellH - gap}" rx="3" class="s1" fill-opacity="$opacity"/>
                  <rect x="${x + gap / 2}" y="${y + gap / 2}" width="${cellW - gap}" height="${cellH - gap}" rx="3" fill="none" class="grid"/>
                  <text $textAttrs x="${x + cellW / 2}" y="${y + 17}" text-anchor="middle">${pct(p)}</text></g>"""
            }.joinToString("")
        }.joinToString("\n")
        return svg(
            width, height,
            "Playoff survival: probability of reaching each round",
            "Top $top by title odds · darker = more likely · ${report.iterations} simulations",
            "$headers\n$cells",
        )
    }

    fun eloRatings(report: SimulationReport, mean: Double = 1505.0): String {
        val rows = report.teams.sortedByDescending { it.elo }
        val width = 760
        val center = 400.0
        val half = 300.0
        val rowH = 20.0
        val top0 = 84.0
        val span = max(50.0, ceil(rows.maxOf { abs(it.elo - mean) } / 50.0) * 50.0)
        val height = (top0 + rows.size * rowH + 40).toInt()
        val ticks = listOf(-span, -span / 2, 0.0, span / 2, span).joinToString("\n") { d ->
            val x = center + half * d / span
            "<line class=\"grid\" x1=\"$x\" x2=\"$x\" y1=\"${top0 - 6}\" y2=\"${top0 + rows.size * rowH}\"/>" +
                "<text class=\"m num\" x=\"$x\" y=\"${top0 + rows.size * rowH + 18}\" text-anchor=\"middle\">${(mean + d).roundToInt()}</text>"
        }
        val bars = rows.mapIndexed { i, t ->
            val y = top0 + i * rowH
            val d = t.elo - mean
            val len = half * abs(d) / span
            val bar = if (d >= 0) hbar(center, y + 3, len, rowH - 6, "pos")
            else hbarLeft(center, y + 3, len, rowH - 6, "neg")
            val labelX = if (d >= 0) center - 8 else center + 8
            val anchor = if (d >= 0) "end" else "start"
            """<g class="hover"><title>${esc(t.name)} (${t.record}): Elo ${t.elo.roundToInt()} (${if (d >= 0) "+" else ""}${d.roundToInt()} vs avg)</title>
              <rect x="0" y="$y" width="$width" height="$rowH" fill="transparent"/>$bar
              <text class="t2" x="$labelX" y="${y + 14}" text-anchor="$anchor" font-size="11">${t.abbr}</text></g>"""
        }.joinToString("\n")
        return svg(
            width, height,
            "Elo power ratings vs. league average (${mean.roundToInt()})",
            "Margin-of-victory Elo: 2025 season regressed 1/3 to the mean, then 2026 results",
            "$ticks\n<line class=\"axis\" x1=\"$center\" x2=\"$center\" y1=\"${top0 - 6}\" y2=\"${top0 + rows.size * rowH}\"/>\n$bars",
        )
    }

    /** Line chart of title odds over time for the current top teams; needs at least two snapshots. */
    fun oddsTrend(history: List<OddsSnapshot>, current: SimulationReport, top: Int = 6): String? {
        if (history.size < 2) return null
        val teams = current.byChampionOdds.take(minOf(top, SERIES.size)).map { it.abbr }
        val width = 760
        val left = 60.0
        val right = 90.0
        val top0 = 80.0
        val plotH = 260.0
        val plotW = width - left - right
        val height = (top0 + plotH + 50).toInt()
        val ticks = probabilityTicks(history.flatMap { s -> teams.map { s.odds[it] ?: 0.0 } }.max())
        val maxV = ticks.last()
        val xAt = { i: Int -> left + plotW * i / (history.size - 1) }
        val yAt = { p: Double -> top0 + plotH * (1 - p / maxV) }
        val grid = ticks.withIndex().joinToString("\n") { (k, v) ->
            "<line class=\"${if (k == 0) "axis" else "grid"}\" x1=\"$left\" x2=\"${left + plotW}\" y1=\"${yAt(v)}\" y2=\"${yAt(v)}\"/>" +
                "<text class=\"m num\" x=\"${left - 8}\" y=\"${yAt(v) + 4}\" text-anchor=\"end\">${pct(v)}</text>"
        }
        val xLabels = history.mapIndexed { i, s -> i to s }
            .filter { (i, _) -> i == 0 || i == history.lastIndex || history.size <= 8 }
            .joinToString("\n") { (i, s) ->
                "<text class=\"m\" x=\"${xAt(i)}\" y=\"${top0 + plotH + 18}\" text-anchor=\"middle\">${esc(s.label)}</text>"
            }
        val lines = teams.mapIndexed { k, abbr ->
            val pts = history.mapIndexed { i, s -> xAt(i) to yAt(s.odds[abbr] ?: 0.0) }
            val path = pts.joinToString(" ") { (x, y) -> "${x.roundToInt()},${y.roundToInt()}" }
            val dots = history.mapIndexed { i, s ->
                "<circle cx=\"${xAt(i)}\" cy=\"${yAt(s.odds[abbr] ?: 0.0)}\" r=\"4\" class=\"s${k + 1} ring\"><title>$abbr ${esc(s.label)}: ${pct(s.odds[abbr] ?: 0.0)}</title></circle>"
            }.joinToString("")
            val (lx, ly) = pts.last()
            """<polyline points="$path" fill="none" class="l${k + 1}" stroke-width="2" stroke-linejoin="round"/>$dots
              <text class="t" x="${lx + 10}" y="${ly + 4}" font-weight="600">$abbr ${pct(history.last().odds[abbr] ?: 0.0)}</text>"""
        }.joinToString("\n")
        val legend = teams.mapIndexed { k, abbr ->
            val x = left + k * 80
            "<rect x=\"$x\" y=\"62\" width=\"12\" height=\"3\" rx=\"1\" class=\"s${k + 1}\"/><text class=\"t2\" x=\"${x + 16}\" y=\"67\">$abbr</text>"
        }.joinToString("")
        return svg(width, height, "Super Bowl odds over the season", "Current top $top teams", "$legend\n$grid\n$xLabels\n$lines")
    }
}
