package app.pacinglab.core.model

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test

class RouteColoringTest {
    @Test
    fun `a choice the session has stays as it is`() {
        val all = RouteColoring.entries.toSet()
        RouteColoring.entries.forEach { assertThat(it.within(all)).isEqualTo(it) }
    }

    @Test
    fun `without heart rate the zones give way to pace`() {
        val noHeartRate = setOf(RouteColoring.PACE, RouteColoring.PLAIN)
        assertThat(RouteColoring.ZONES_FIVE.within(noHeartRate)).isEqualTo(RouteColoring.PACE)
        assertThat(RouteColoring.ZONES_THREE.within(noHeartRate)).isEqualTo(RouteColoring.PACE)
    }

    @Test
    fun `without movement pace gives way to the five zones, and without either the line is plain`() {
        assertThat(RouteColoring.PACE.within(RouteColoring.entries.toSet() - RouteColoring.PACE)).isEqualTo(RouteColoring.ZONES_FIVE)
        assertThat(RouteColoring.ZONES_FIVE.within(setOf(RouteColoring.PLAIN))).isEqualTo(RouteColoring.PLAIN)
    }
}
