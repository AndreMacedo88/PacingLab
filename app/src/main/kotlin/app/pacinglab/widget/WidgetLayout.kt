package app.pacinglab.widget

import app.pacinglab.core.ui.WidgetMeasure

/**
 * One measure's place in the widget's grid: a tile at [row] and [column], or with [panel] the vitals panel
 * filling two tiles across and two down from there.
 */
data class WidgetTile(val measure: WidgetMeasure, val row: Int, val column: Int, val panel: Boolean = false)

/**
 * How a widget of a given size is filled, in dp. The width is cut into as many tiles as fit (each wide enough
 * for a label such as "Readiness"), and the height into rows of tiles. The measures take tiles in the
 * athlete's order, each where it first fits, so a later figure can fill a gap left beside the vitals panel;
 * vitals take a panel of four tiles when there is room and a single tile with a count otherwise. The status
 * line comes under the tiles when the widget is two tiles wide and a row of tiles still fits above it, and
 * the chart only when it costs no measure its place; it then grows into the height the tiles leave.
 */
data class WidgetLayout(
    val padding: Float,
    val columns: Int,
    val rows: Int,
    val tileWidth: Float,
    val tileHeight: Float,
    val tiles: List<WidgetTile>,
    val line: Boolean,
    val chart: Boolean,
    val chartHeight: Float = CHART_HEIGHT,
) {
    /** The width of the area inside the padding. */
    val innerWidth: Float get() = columns * tileWidth + (columns - 1) * GAP

    /** The chart's name goes beside it on wide widgets and above it on narrow ones. */
    val chartLabelBeside: Boolean get() = columns >= 3

    companion object {
        /** Space between tiles. */
        const val GAP = 6f

        /** Narrowest tile: room for an 11 sp label like "Readiness" at the usual font sizes. */
        const val MIN_TILE_WIDTH = 46f
        const val MAX_COLUMNS = 6

        /** Line height of widget text as a multiple of its size (the platform's text views add font padding). */
        const val LINE_FACTOR = 1.4f
        const val LABEL_SP = 11f
        const val VALUE_SP = 22f
        const val SMALL_SP = 11f
        const val CHART_HEIGHT = 40f
        const val MAX_CHART_HEIGHT = 96f
        const val LINE_GAP = 4f
        const val CHART_GAP = 8f

        /** Width of the chart's name when it sits beside the chart. */
        const val CHART_LABEL_WIDTH = 58f

        /**
         * The layout of [measures] (in order, visible ones only) on a widget [width] by [height] dp, with the
         * text scaled by [fontScale]. [line] and [chart] say whether there is a status line and a chart to
         * show at all.
         */
        fun of(width: Float, height: Float, fontScale: Float, measures: List<WidgetMeasure>, line: Boolean, chart: Boolean): WidgetLayout {
            val padding = if (width < NARROW) SMALL_PADDING else PADDING
            val innerWidth = width - 2 * padding
            val innerHeight = height - 2 * padding
            val columns = ((innerWidth + GAP) / (MIN_TILE_WIDTH + GAP)).toInt().coerceIn(1, MAX_COLUMNS)
            val tileWidth = (innerWidth - (columns - 1) * GAP) / columns
            val text = LINE_FACTOR * fontScale
            val tileHeight = (LABEL_SP + VALUE_SP + SMALL_SP) * text
            val lineHeight = LINE_GAP + SMALL_SP * text
            val grid = measures.filter { it.kind != WidgetMeasure.Kind.LINE }

            fun rowsIn(space: Float) = ((space + GAP) / (tileHeight + GAP)).toInt()
            val withLine = line && columns >= 2 && innerHeight >= tileHeight + lineHeight
            val below = if (withLine) lineHeight else 0f
            val rows = rowsIn(innerHeight - below).coerceAtLeast(1)
            val tiles = place(grid, columns, rows)

            val chartExtra = CHART_GAP + if (columns >= 3) 0f else SMALL_SP * text
            val chartRows = rowsIn(innerHeight - below - chartExtra - CHART_HEIGHT)
            val withChart = chart && columns >= 2 && chartRows >= 1 && place(grid, columns, chartRows).let { fewer ->
                fewer.size == tiles.size && fewer.count { it.panel } == tiles.count { it.panel }
            }
            val placed = if (withChart) place(grid, columns, chartRows) else tiles
            val used = placed.maxOfOrNull { it.row + if (it.panel) 2 else 1 } ?: 0
            val gridHeight = if (used == 0) 0f else used * tileHeight + (used - 1) * GAP
            return WidgetLayout(
                padding = padding,
                columns = columns,
                rows = if (withChart) chartRows else rows,
                tileWidth = tileWidth,
                tileHeight = tileHeight,
                tiles = placed,
                line = withLine,
                chart = withChart,
                chartHeight = (innerHeight - gridHeight - below - chartExtra).coerceIn(CHART_HEIGHT, MAX_CHART_HEIGHT),
            )
        }

        /** Tiles in order, each in the first free place from the top left where it fits. */
        private fun place(measures: List<WidgetMeasure>, columns: Int, rows: Int): List<WidgetTile> {
            val taken = Array(rows) { BooleanArray(columns) }
            fun free(row: Int, column: Int) = row < rows && column < columns && !taken[row][column]
            val placed = mutableListOf<WidgetTile>()
            for (measure in measures) {
                val cells = (0 until rows * columns).map { it / columns to it % columns }
                val panel = measure.kind == WidgetMeasure.Kind.PANEL && columns >= 2 && rows >= 2
                val spot = cells.firstOrNull { (r, c) -> free(r, c) && free(r, c + 1) && free(r + 1, c) && free(r + 1, c + 1) }
                if (panel && spot != null) {
                    val (r, c) = spot
                    for (dr in 0..1) for (dc in 0..1) taken[r + dr][c + dc] = true
                    placed += WidgetTile(measure, r, c, panel = true)
                    continue
                }
                val (r, c) = cells.firstOrNull { (r, c) -> free(r, c) } ?: continue
                taken[r][c] = true
                placed += WidgetTile(measure, r, c)
            }
            return placed
        }

        private const val NARROW = 100f
        private const val PADDING = 10f
        private const val SMALL_PADDING = 6f
    }
}
