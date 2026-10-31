package app.pacinglab.core.analytics.geo

import app.pacinglab.core.model.HeartRateSeries
import app.pacinglab.core.model.IntensityZone
import app.pacinglab.core.model.Route
import app.pacinglab.core.model.ZoneModel
import app.pacinglab.core.model.ZoneSet
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.each
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import org.junit.jupiter.api.Test

class RouteShadingTest {
    /** A route north from Copenhagen City Hall, one point a second, at the given speed (m/s) in each second. */
    private fun route(speeds: List<Double>): Route {
        val latitudes = DoubleArray(speeds.size + 1)
        latitudes[0] = 55.6761
        for (i in speeds.indices) latitudes[i + 1] = latitudes[i] + speeds[i] / METERS_PER_DEGREE
        return Route(
            times = LongArray(latitudes.size) { it * 1_000L },
            latitudes = latitudes,
            longitudes = DoubleArray(latitudes.size) { 12.5683 },
            altitudes = DoubleArray(latitudes.size) { Double.NaN },
            accuracies = DoubleArray(latitudes.size) { Double.NaN },
        )
    }

    private val zones = ZoneSet(
        ZoneModel.POLARIZED_THREE,
        listOf(IntensityZone(1, "Z1", 0), IntensityZone(2, "Z2", 140), IntensityZone(3, "Z3", 165)),
    )

    @Test
    fun `each point takes the zone of the nearest heart rate, none when it is far away`() {
        val route = route(List(5) { 3.0 })
        // Points at 0..5 s; heart rate at 0, 2 and 4 s, then nothing until 30 s.
        val heartRate = HeartRateSeries(longArrayOf(0, 2_000, 4_000, 30_000), intArrayOf(120, 150, 170, 100))

        val levels = RouteShading.zones(listOf(route), heartRate, zones).single()

        assertThat(levels.toList()).containsExactly(0, 0, 1, 1, 2, 2)
        val far = RouteShading.zones(listOf(route), HeartRateSeries(longArrayOf(40_000), intArrayOf(150)), zones).single()
        assertThat(far.toList()).each { it.isEqualTo(RouteShading.NONE) }
    }

    @Test
    fun `pace runs from the slow half to the fast half of a run`() {
        val route = route(List(300) { 3.0 } + List(300) { 4.0 })

        val shading = RouteShading.pace(listOf(route), maxStepSpeed = 9.0)

        assertThat(shading).isNotNull()
        shading!!
        assertThat(shading.slowest).isCloseTo(3.0, 0.01)
        assertThat(shading.fastest).isCloseTo(4.0, 0.01)
        assertThat(shading.levels.single()[100]).isEqualTo(0)
        assertThat(shading.levels.single()[500]).isEqualTo(RouteShading.PACE_STEPS - 1)
    }

    @Test
    fun `a steady run keeps to the middle of a scale at least 15 per cent wide`() {
        // GPS noise: alternate seconds a little faster and slower.
        val route = route(List(600) { if (it % 2 == 0) 3.4 else 3.6 })

        val shading = RouteShading.pace(listOf(route), maxStepSpeed = 9.0)!!

        assertThat(shading.fastest / shading.slowest).isCloseTo(1.15, 0.001)
        assertThat(shading.levels.single().drop(20).dropLast(20)).each { it.isEqualTo(RouteShading.PACE_STEPS / 2) }
    }

    @Test
    fun `a stretch faster than the sport allows has no pace and leaves the scale alone`() {
        // Five minutes of running, a minute in a car, then running again.
        val route = route(List(300) { 3.0 } + List(60) { 15.0 } + List(300) { 3.5 })

        val shading = RouteShading.pace(listOf(route), maxStepSpeed = 9.0)!!

        assertThat(shading.levels.single()[330]).isEqualTo(RouteShading.NONE)
        assertThat(shading.fastest).isCloseTo(3.5, 0.01)
    }

    @Test
    fun `no scale without enough moving points`() {
        assertThat(RouteShading.pace(listOf(route(List(5) { 3.0 })), maxStepSpeed = 9.0)).isNull()
        assertThat(RouteShading.pace(listOf(route(List(60) { 0.0 })), maxStepSpeed = 9.0)).isNull()
    }

    private companion object {
        const val METERS_PER_DEGREE = 111_195.0
    }
}
