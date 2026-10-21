package app.pacinglab.core.analytics.load

import app.pacinglab.core.analytics.workout.workout
import app.pacinglab.core.analytics.zones.ZoneDistribution
import app.pacinglab.core.analytics.zones.Zones
import app.pacinglab.core.model.HeartRateAnchors
import app.pacinglab.core.model.PhysiologyEntry
import app.pacinglab.core.model.PhysiologyHistory
import app.pacinglab.core.model.RecordingMethod
import app.pacinglab.core.model.Sport
import app.pacinglab.core.model.ZoneModel
import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class TrainingLoadsTest {
    private val day = LocalDate.of(2026, 10, 20)

    @Test
    fun `recordings left running for half a day count for nothing`() {
        assertThat(TrainingLoads.counts(workout("a", Sport.RUN, startMinute = 0, minutes = 3 * 60))).isEqualTo(true)
        assertThat(TrainingLoads.counts(workout("b", Sport.OTHER, startMinute = 0, minutes = 12 * 60))).isEqualTo(true)
        assertThat(TrainingLoads.counts(workout("c", Sport.OTHER, startMinute = 0, minutes = 34 * 60))).isEqualTo(false)
    }

    @Test
    fun `detected walks and rides are everyday movement, detected runs are training`() {
        fun detected(sport: Sport) = workout("a", sport, startMinute = 0, minutes = 30).copy(recordingMethod = RecordingMethod.AUTOMATIC)

        assertThat(TrainingLoads.counts(detected(Sport.WALK))).isEqualTo(false)
        assertThat(TrainingLoads.counts(detected(Sport.RIDE))).isEqualTo(false)
        assertThat(TrainingLoads.counts(detected(Sport.OTHER))).isEqualTo(false)
        assertThat(TrainingLoads.counts(detected(Sport.RUN))).isEqualTo(true)
        assertThat(TrainingLoads.counts(workout("b", Sport.WALK, startMinute = 0, minutes = 30))).isEqualTo(true)
    }

    @Test
    fun `a workout belongs to the day it started where it was recorded`() {
        val late = workout("a", Sport.RUN, startMinute = 0, minutes = 30).copy(
            start = Instant.parse("2026-10-20T22:30:00Z"),
            end = Instant.parse("2026-10-20T23:00:00Z"),
        )

        assertThat(TrainingLoads.dateOf(late.copy(zoneOffset = ZoneOffset.ofHours(2)), ZoneOffset.UTC)).isEqualTo(day.plusDays(1))
        assertThat(TrainingLoads.dateOf(late, ZoneId.of("UTC"))).isEqualTo(day)
    }

    @Test
    fun `daily loads add up stress scores and skip unscored workouts`() {
        fun scores(stress: Double) = LoadScores(trimp = stress * 1.7, stressScore = stress, edwards = stress * 2, heartRateMillis = 1)
        val loads = listOf(
            WorkoutLoad("a", day, 0, scores(6.4)),
            WorkoutLoad("b", day, 0, scores(46.5)),
            WorkoutLoad("c", day.plusDays(1), 0, null),
            WorkoutLoad("d", day.plusDays(2), 0, scores(10.0)),
        )

        val daily = TrainingLoads.daily(loads)

        assertThat(daily.keys).isEqualTo(setOf(day, day.plusDays(2)))
        assertThat(daily.getValue(day)).isCloseTo(52.9, 1e-9)
    }

    @Test
    fun `weeks run from Monday and add up load, time and intensity`() {
        // 2026-10-20 is a Tuesday.
        val polarized = Zones.of(ZoneModel.POLARIZED_THREE, HeartRateAnchors(maxHr = 186, thresholdHr = 167))
        fun load(id: String, date: LocalDate, stress: Double, minutes: Long, vararg zoneMinutes: Long) = WorkoutLoad(
            workoutId = id,
            date = date,
            durationMillis = minutes * 60_000,
            scores = LoadScores(stress * 1.7, stress, stress * 2, minutes * 60_000),
            intensity = ZoneDistribution(polarized, LongArray(3) { zoneMinutes[it] * 60_000 }),
        )
        val loads = listOf(
            load("sun", LocalDate.of(2026, 10, 18), 40.0, 45, 40, 5, 0),
            load("mon", LocalDate.of(2026, 10, 19), 20.0, 30, 30, 0, 0),
            load("tue", day, 53.0, 36, 15, 9, 12),
            WorkoutLoad("walk", day, 30 * 60_000, null, null),
        )

        val weeks = TrainingLoads.weeks(loads, until = LocalDate.of(2026, 10, 21), count = 3)

        assertThat(
            weeks.map {
                it.start
            },
        ).isEqualTo(listOf(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 5)))
        val current = weeks[0]
        assertThat(current.load).isCloseTo(73.0, 1e-9)
        assertThat(current.durationMillis).isEqualTo(96 * 60_000L)
        assertThat(current.workouts).isEqualTo(3)
        assertThat(current.intensityMillis.toList()).isEqualTo(listOf(45 * 60_000L, 9 * 60_000L, 12 * 60_000L))
        assertThat(current.strain!!.load).isCloseTo(73.0, 1e-9)
        assertThat(weeks[1].load).isCloseTo(40.0, 1e-9)
        assertThat(weeks[2].workouts).isEqualTo(0)
        assertThat(weeks[2].strain).isNull()
    }

    @Test
    fun `the athlete's resting heart rate wins over the measured one`() {
        val history = PhysiologyHistory(listOf(PhysiologyEntry(day, maxHr = 186, restingHr = 57)))
        val timeline = AnchorTimeline(history, mapOf(day to 49.0))

        assertThat(timeline.at(day)!!.restingHr).isEqualTo(57)
    }

    @Test
    fun `without a resting heart rate the median of the last 30 days of measurements is used`() {
        val history = PhysiologyHistory(listOf(PhysiologyEntry(day, maxHr = 186)))
        val measured = mapOf(
            day.minusDays(40) to 70.0,
            day.minusDays(29) to 52.0,
            day.minusDays(10) to 55.0,
            day.minusDays(3) to 51.0,
            day.minusDays(1) to 60.0,
            day.plusDays(1) to 80.0,
        )
        val timeline = AnchorTimeline(history, measured)

        // 51, 52, 55, 60 -> 53.5, rounded half up.
        assertThat(timeline.at(day)!!.restingHr).isEqualTo(54)
        assertThat(timeline.at(day)!!.thresholdHr).isEqualTo(167)
        // Long before any measurement, the first 30 days of measurements stand in: 70 and 52.
        assertThat(timeline.at(day.minusDays(60))!!.restingHr).isEqualTo(61)
    }

    @Test
    fun `days far from any measurement use the nearest stretch of measurements`() {
        val history = PhysiologyHistory(listOf(PhysiologyEntry(day, maxHr = 186)))
        val measured = (0L until 6L).associate { day.plusDays(it) to 56.0 + it }
        val timeline = AnchorTimeline(history, measured)

        // A workout a year before the watch was first worn at night: median of 56..61.
        assertThat(timeline.at(day.minusDays(365))!!.restingHr).isEqualTo(59)
        // Long after the last night: the 30 days up to that night.
        assertThat(timeline.measuredRestingAt(day.plusDays(200))).isEqualTo(59)
        assertThat(AnchorTimeline(history, emptyMap()).measuredRestingAt(day)).isNull()
    }

    @Test
    fun `no maximal heart rate means no anchors`() {
        assertThat(AnchorTimeline(PhysiologyHistory.EMPTY, mapOf(day to 50.0)).at(day)).isNull()
    }

    @Test
    fun `implausible measurements are ignored`() {
        val history = PhysiologyHistory(listOf(PhysiologyEntry(day, maxHr = 186)))

        assertThat(AnchorTimeline(history, mapOf(day to 170.0)).at(day)!!.restingHr).isNull()
    }
}
