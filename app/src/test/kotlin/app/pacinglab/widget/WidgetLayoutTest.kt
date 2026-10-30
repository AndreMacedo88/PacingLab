package app.pacinglab.widget

import app.pacinglab.core.ui.WidgetMeasure
import app.pacinglab.core.ui.WidgetMeasure.FORM
import app.pacinglab.core.ui.WidgetMeasure.HRV
import app.pacinglab.core.ui.WidgetMeasure.READINESS
import app.pacinglab.core.ui.WidgetMeasure.SLEEP_SCORE
import app.pacinglab.core.ui.WidgetMeasure.STATUS
import app.pacinglab.core.ui.WidgetMeasure.VITALS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The widget's layout at the cell sizes a Galaxy launcher with a dense grid reports (63 by 95 dp a cell,
 * 251 by 206 for four by two) and at a slightly smaller font, as on the owner's phone.
 */
class WidgetLayoutTest {
    private val measures = listOf(VITALS, READINESS, SLEEP_SCORE, STATUS, FORM)

    private fun layout(width: Float, height: Float, measures: List<WidgetMeasure> = this.measures, fontScale: Float = 0.9f) =
        WidgetLayout.of(width, height, fontScale, measures, line = true, chart = true)

    @Test
    fun `one cell holds one measure, the first in the list`() {
        val one = layout(63f, 95f, listOf(READINESS, SLEEP_SCORE))

        assertEquals(1, one.columns)
        assertEquals(listOf(WidgetTile(READINESS, 0, 0)), one.tiles)
        assertFalse(one.line)
        assertFalse(one.chart)
    }

    @Test
    fun `vitals without room for their panel take one tile`() {
        val one = layout(63f, 95f)

        assertEquals(listOf(WidgetTile(VITALS, 0, 0)), one.tiles)
    }

    @Test
    fun `four cells by two hold the vitals panel, three figures, the status line and the chart`() {
        val wide = layout(251f, 206f)

        assertEquals(4, wide.columns)
        assertEquals(
            listOf(
                WidgetTile(VITALS, 0, 0, panel = true),
                WidgetTile(READINESS, 0, 2),
                WidgetTile(SLEEP_SCORE, 0, 3),
                WidgetTile(FORM, 1, 2),
            ),
            wide.tiles,
        )
        assertTrue(wide.line)
        assertTrue(wide.chart)
    }

    @Test
    fun `a later figure fills the gap the panel leaves in the row above it`() {
        val tall = layout(251f, 317f, listOf(READINESS, SLEEP_SCORE, FORM, VITALS, HRV))

        assertEquals(
            listOf(
                WidgetTile(READINESS, 0, 0),
                WidgetTile(SLEEP_SCORE, 0, 1),
                WidgetTile(FORM, 0, 2),
                WidgetTile(VITALS, 1, 0, panel = true),
                WidgetTile(HRV, 0, 3),
            ),
            tall.tiles,
        )
        assertTrue(tall.chart)
        assertTrue(tall.chartHeight > WidgetLayout.CHART_HEIGHT)
    }

    @Test
    fun `the panel shrinks to a tile when the row after it is taken`() {
        val wide = layout(251f, 206f, listOf(READINESS, SLEEP_SCORE, FORM, VITALS, HRV))

        assertEquals(WidgetTile(VITALS, 0, 3), wide.tiles.single { it.measure == VITALS })
    }

    @Test
    fun `the chart gives way when it would shrink the vitals panel`() {
        val wide = layout(251f, 206f, fontScale = 1f)

        assertFalse(wide.chart)
        assertTrue(wide.tiles.single { it.measure == VITALS }.panel)
    }

    @Test
    fun `one row of four cells shows the figures and the status line`() {
        val row = layout(251f, 95f)

        assertEquals(listOf(VITALS, READINESS, SLEEP_SCORE, FORM), row.tiles.map { it.measure })
        assertTrue(row.tiles.none { it.panel })
        assertTrue(row.line)
        assertFalse(row.chart)
    }

    @Test
    fun `two cells side by side fit two tiles`() {
        assertEquals(2, layout(121f, 95f).columns)
        assertEquals(3, layout(184f, 206f).columns)
    }
}
