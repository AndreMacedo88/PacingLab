package app.pacinglab.core.analytics.strain

import app.pacinglab.core.analytics.load.FormDay
import app.pacinglab.core.model.DataSource
import app.pacinglab.core.model.HeartRateSeries
import app.pacinglab.core.model.RecordingMethod
import app.pacinglab.core.model.SleepSession
import app.pacinglab.core.model.Sport
import app.pacinglab.core.model.StepCount
import app.pacinglab.core.model.StillHeartRate
import app.pacinglab.core.model.Workout
import app.pacinglab.core.model.WorkoutSummary
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isBetween
import assertk.assertions.isCloseTo
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThanOrEqualTo
import assertk.assertions.isNull
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.Random

class DaytimeStrainTest {
    private val zone = ZoneOffset.ofHours(1)
    private val date = LocalDate.of(2026, 11, 3)

    private fun at(time: String): Instant = date.atTime(LocalTime.parse(time)).atZone(zone).toInstant()

    /** Heart rate every 5 seconds from [from] to [to], given by [bpm] of the time. */
    private fun heartRate(from: String, to: String, bpm: (Instant) -> Int): HeartRateSeries {
        val times = generateSequence(at(from)) { it.plusSeconds(5) }.takeWhile { it < at(to) }.toList()
        return HeartRateSeries(times.map { it.toEpochMilli() }.toLongArray(), times.map(bpm).toIntArray())
    }

    private fun workout(start: String, end: String, method: RecordingMethod) = Workout(
        id = start,
        source = DataSource.HEALTH_CONNECT,
        sourceRecordId = start,
        origin = "com.fitbit.FitbitMobile",
        device = "Sense",
        activity = "running",
        title = null,
        start = at(start),
        end = at(end),
        zoneOffset = zone,
        lastModified = at(start),
        sport = Sport.RUN,
        summary = WorkoutSummary(0, null, null, null, hasRoute = false, hasSpeed = false),
        laps = emptyList(),
        segments = emptyList(),
        recordingMethod = method,
    )

    @Test
    fun `only still awake minutes away from workouts count`() {
        val hr = heartRate("07:00", "23:30") { if (it >= at("15:00") && it < at("15:30")) 140 else 62 }
        // Ten minutes of walking, and a walk in the minutes before 08:00 that the first minutes settle from.
        val steps = (0 until 10).map { StepCount(at("10:00").plusSeconds(60L * it), at("10:01").plusSeconds(60L * it), 80) }
        val sleep = SleepSession("s", at("22:30"), date.plusDays(1).atTime(6, 30).atZone(zone).toInstant(), zone, "f", emptyList())
        val excluded = StillMinutes.excludedSpans(listOf(sleep), listOf(workout("12:00", "13:00", RecordingMethod.ACTIVE)))

        val still = StillMinutes.summarize(date, zone, hr, steps, excluded)

        assertThat(still.minutes[9]).isEqualTo(60)
        // 10:00 to 10:15 is walking and settling.
        assertThat(still.minutes[10]).isEqualTo(45)
        // The workout from 11:55, then an hour to come down, until 14:00.
        assertThat(still.minutes[11]).isEqualTo(55)
        assertThat(still.minutes[12]).isEqualTo(0)
        assertThat(still.minutes[13]).isEqualTo(0)
        // Over 130 bpm without steps: unrecorded exercise.
        assertThat(still.minutes[15]).isEqualTo(30)
        assertThat(still.minutes[22]).isEqualTo(30)
        assertThat(still.minutes[23]).isEqualTo(0)
        assertThat(still.mean(9)!!).isCloseTo(62.0, 1e-9)
    }

    @Test
    fun `a detected walk or ride is left out only briefly`() {
        val spans = StillMinutes.excludedSpans(emptyList(), listOf(workout("08:00", "08:20", RecordingMethod.AUTOMATIC)))

        assertThat(spans.single().endInclusive).isEqualTo(at("08:35"))
        assertThat(spans.single().start).isEqualTo(at("07:55"))
    }

    /** A day of still heart rate at [bpm] in every hour from 8 to 21, an hour of minutes each. */
    private fun day(date: LocalDate, bpm: (Int) -> Double): StillHeartRate {
        val minutes = IntArray(24) { if (it in 8..21) 50 else 0 }
        return StillHeartRate(date, minutes, DoubleArray(24) { if (it in 8..21) 50 * bpm(it) else 0.0 })
    }

    @Test
    fun `a day with heart rate above the usual hours scores high, usual days near 50`() {
        val random = Random(1)
        // Afternoons run higher than mornings: the hourly baselines take that out.
        val days = (0 until 40).map { i ->
            val first = date.minusDays(40)
            day(first.plusDays(i.toLong())) { hour -> 58 + (if (hour >= 13) 6 else 0) + random.nextGaussian() }
        } + day(date) { hour -> 64 + (if (hour >= 13) 6 else 0) + random.nextGaussian() }

        val strain = DaytimeStrain.days(days)

        val today = strain.last()
        assertThat(today.date).isEqualTo(date)
        assertThat(today.excessBpm).isBetween(5.0, 7.0)
        assertThat(today.score!!).isGreaterThanOrEqualTo(DaytimeStrain.HIGH_FROM)
        assertThat(today.level).isEqualTo(StrainLevel.HIGH)
        assertThat(strain[30].score!!).isBetween(30, 70)
    }

    @Test
    fun `the first week has no baseline and the first weeks no score`() {
        val days = (0 until 12).map { day(date.minusDays(11L - it)) { 60.0 + it % 3 } }

        val strain = DaytimeStrain.days(days)

        // Days 8 to 12 have a week of days before them.
        assertThat(strain.map { it.date }).containsExactly(*(7 until 12).map { date.minusDays(11L - it) }.toTypedArray())
        assertThat(strain.first().score).isNull()
    }

    @Test
    fun `too few still minutes give no value`() {
        val days = (0 until 10).map { day(date.minusDays(10L - it)) { 60.0 } } +
            StillHeartRate(date, IntArray(24) { if (it == 9) 60 else 0 }, DoubleArray(24) { if (it == 9) 60 * 65.0 else 0.0 })

        assertThat(DaytimeStrain.days(days).none { it.date == date }).isEqualTo(true)
    }

    @Test
    fun `vitals, hard training and poor sleep are named as other causes`() {
        val causes = DaytimeStrain.causes(
            date,
            alerts = setOf(date.plusDays(1)),
            hardTraining = setOf(date.minusDays(1)),
            poorSleep = emptySet(),
        )

        assertThat(causes).containsExactly(StrainCause.VITALS_OFF, StrainCause.HARD_TRAINING)
        assertThat(DaytimeStrain.causes(date, emptySet(), emptySet(), emptySet())).isEmpty()
    }

    @Test
    fun `hard training is a big load for the fitness going into it`() {
        val form = listOf(
            FormDay(date.minusDays(2), load = 50.0, fitness = 40.0, fatigue = 40.0, form = 0.0),
            FormDay(date.minusDays(1), load = 90.0, fitness = 41.0, fatigue = 45.0, form = 0.0),
            FormDay(date, load = 55.0, fitness = 42.0, fatigue = 46.0, form = -4.0),
        )

        assertThat(DaytimeStrain.hardTrainingDays(form)).isEqualTo(setOf(date.minusDays(1)))
        assertThat(Duration.ofMinutes(60)).isEqualTo(StillMinutes.AFTER_TRAINING)
    }
}
