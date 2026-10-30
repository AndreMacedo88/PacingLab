package app.pacinglab.core.analytics.load

import app.pacinglab.core.analytics.weather.HeatAdjustment
import app.pacinglab.core.analytics.zones.ZoneDistribution
import app.pacinglab.core.analytics.zones.Zones
import app.pacinglab.core.model.HeartRateAnchors
import app.pacinglab.core.model.SessionKind
import app.pacinglab.core.model.Sport
import app.pacinglab.core.model.ZoneModel
import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.jupiter.api.Test
import java.time.LocalDate

class WeeklyTargetsTest {
    private val monday = LocalDate.of(2026, 11, 16)

    @Test
    fun `the load band is 0_8 to 1_3 usual weeks at the fitness the week starts with`() {
        val series = FitnessModel.series((0L..60L).associate { monday.minusDays(it) to 50.0 }, monday)

        val band = WeeklyTargets.loadBands(series, listOf(monday)).getValue(monday)

        assertThat(band.fitness).isCloseTo(50.0, 1e-6)
        assertThat(band.low).isCloseTo(280.0, 1e-4)
        assertThat(band.high).isCloseTo(455.0, 1e-4)
        assertThat(WeeklyTargets.loadBands(series, listOf(monday.minusWeeks(20))).isEmpty()).isEqualTo(true)
    }

    @Test
    fun `running tolerance is 30 percent over the four weeks before`() {
        val runs = listOf(
            monday to 15_000.0,
            monday.minusWeeks(1) to 10_000.0,
            monday.minusWeeks(2) to 8_000.0,
            monday.minusWeeks(3) to 12_000.0,
            monday.minusWeeks(4) to 10_000.0,
        )

        val weeks = WeeklyTargets.runningWeeks(runs, until = monday.plusDays(3), count = 6)

        assertThat(weeks.first().km).isCloseTo(15.0, 1e-9)
        assertThat(weeks.first().usualKm!!).isCloseTo(10.0, 1e-9)
        assertThat(weeks.first().toleranceKm!!).isCloseTo(13.0, 1e-9)
        assertThat(weeks.first().above).isEqualTo(true)
        assertThat(weeks.last().toleranceKm).isNull()
    }

    @Test
    fun `a day takes the colour of its hardest meaningful intensity`() {
        assertThat(TrainingCalendar.dominant(longArrayOf(70, 10, 20))).isEqualTo(IntensityDomain.ABOVE_VT2)
        assertThat(TrainingCalendar.dominant(longArrayOf(70, 25, 5))).isEqualTo(IntensityDomain.VT1_TO_VT2)
        assertThat(TrainingCalendar.dominant(longArrayOf(90, 5, 5))).isEqualTo(IntensityDomain.BELOW_VT1)
        assertThat(TrainingCalendar.dominant(longArrayOf(0, 0, 0))).isNull()
    }

    @Test
    fun `heat raises speed-based estimates by 0_35 percent per degree of WBGT above 15`() {
        assertThat(HeatAdjustment.speedFactor(12.0)).isEqualTo(1.0)
        assertThat(HeatAdjustment.speedFactor(25.0)).isCloseTo(1.035, 1e-9)
        // The cost above rest scales: 3.5 + 1.035 x (50 - 3.5).
        assertThat(HeatAdjustment.vo2max(50.0, 25.0)).isCloseTo(51.6275, 1e-9)
    }

    private val polarized = Zones.of(ZoneModel.POLARIZED_THREE, HeartRateAnchors(maxHr = 186, thresholdHr = 167, restingHr = 50))

    private fun load(id: String, sport: Sport, millis: LongArray) = WorkoutLoad(
        workoutId = id,
        date = monday,
        durationMillis = millis.sum(),
        scores = null,
        intensity = ZoneDistribution(polarized, millis),
        sport = sport,
    )

    @Test
    fun `gym days are not coloured by heart rate`() {
        val gymOnly = TrainingCalendar.days(listOf(load("g", Sport.STRENGTH, longArrayOf(60, 0, 0))))
        val withRun = TrainingCalendar.days(
            listOf(load("g", Sport.STRENGTH, longArrayOf(60, 0, 0)), load("r", Sport.RUN, longArrayOf(30, 2, 10))),
        )

        assertThat(gymOnly.getValue(monday).intensity).isNull()
        assertThat(withRun.getValue(monday).intensity).isEqualTo(IntensityDomain.ABOVE_VT2)
    }

    @Test
    fun `a named run keeps its goal although its heart rate crossed VT2, other runs go by heart rate`() {
        // A threshold session's warm-up and reps: 18 % of the time above VT2, as on a real threshold day.
        val warmUp = load("w", Sport.RUN, longArrayOf(10, 0, 0))
        val reps = load("i", Sport.RUN, longArrayOf(22, 50, 18))
        val kinds = mapOf("w" to SessionKind.THRESHOLD_INTERVALS, "i" to SessionKind.THRESHOLD_INTERVALS)

        assertThat(TrainingCalendar.days(listOf(warmUp, reps), kinds).getValue(monday).intensity).isEqualTo(IntensityDomain.VT1_TO_VT2)
        assertThat(TrainingCalendar.days(listOf(warmUp, reps)).getValue(monday).intensity).isEqualTo(IntensityDomain.ABOVE_VT2)
        // A plain run says nothing about its goal, so its heart rate decides; the hardest session sets the day.
        val plain = mapOf("w" to SessionKind.EASY_RUN, "i" to SessionKind.RUN)
        assertThat(TrainingCalendar.days(listOf(warmUp, reps), plain).getValue(monday).intensity).isEqualTo(IntensityDomain.ABOVE_VT2)
    }
}
