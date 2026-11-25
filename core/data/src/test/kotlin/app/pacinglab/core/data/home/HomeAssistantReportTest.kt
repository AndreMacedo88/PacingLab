package app.pacinglab.core.data.home

import app.pacinglab.core.analytics.load.FitnessModel
import app.pacinglab.core.analytics.load.LoadScores
import app.pacinglab.core.analytics.load.TrainingLoads
import app.pacinglab.core.analytics.load.WorkoutLoad
import app.pacinglab.core.analytics.performance.MarathonShape
import app.pacinglab.core.analytics.performance.RaceDistance
import app.pacinglab.core.analytics.performance.RacePredictions
import app.pacinglab.core.analytics.recovery.Night
import app.pacinglab.core.analytics.recovery.SleepHistory
import app.pacinglab.core.model.Sport
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isIn
import assertk.assertions.isTrue
import assertk.assertions.matches
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import kotlin.math.roundToLong

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Config.NEWEST_SDK])
class HomeAssistantReportTest {
    private val today = LocalDate.of(2026, 11, 25)
    private val now = Instant.parse("2026-11-25T09:30:00Z")

    private fun night(date: LocalDate, hrv: Double = 60.0, resting: Double = 50.0): Night {
        val end = Instant.parse("${date}T06:00:00Z")
        return Night(date, end.minusSeconds(8 * 3600), end, 8 * 3_600_000L, emptyMap(), hrv, resting, 14.0)
    }

    /** Forty nights alternating a little around the usual values, up to the night before [until]. */
    private fun usualNights(until: LocalDate): List<Night> = (40 downTo 1).map { ago ->
        val wobble = if (ago % 2 == 0) 1.0 else -1.0
        night(until.minusDays(ago.toLong()), hrv = 60 + 3 * wobble, resting = 50 + wobble)
    }

    /** A run of 45 minutes with a stress score of 50 every other day for ten weeks. */
    private val loads = (70 downTo 0 step 2).map { ago ->
        WorkoutLoad(
            workoutId = "run-$ago",
            date = today.minusDays(ago.toLong()),
            durationMillis = 45 * 60_000L,
            scores = LoadScores(trimp = 60.0, stressScore = 50.0, edwards = 100.0, heartRateMillis = 45 * 60_000L),
            sport = Sport.RUN,
            distanceMeters = 9_000.0,
        )
    }
    private val form = FitnessModel.series(TrainingLoads.daily(loads), until = today)

    private fun report(inputs: HomeAssistantInputs) = JSONObject(HomeAssistantReport.json(inputs))

    @Test
    fun `values of the day wait for last night while the others are sent`() {
        val json = report(HomeAssistantInputs(today, now, SleepHistory.of(usualNights(today)), form, loads = loads))

        for (key in listOf(
            "readiness",
            "readiness_level",
            "sleep_score",
            "asleep_hours",
            "hrv",
            "resting_hr",
            "vitals_alert",
            "alertness",
        )) {
            assertThat(json.has(key), key).isFalse()
        }
        assertThat(json.has("fitness")).isTrue()
        assertThat(json.has("week_load")).isTrue()
        assertThat(json.getString("updated")).isEqualTo(now.toString())
    }

    @Test
    fun `last night brings readiness in the app's words and the vitals`() {
        val nights = usualNights(today) + night(today, hrv = 40.0, resting = 58.0)
        val json = report(HomeAssistantInputs(today, now, SleepHistory.of(nights), form, loads = loads))

        assertThat(json.getString("readiness_level")).isIn("Ready to train", "Train with care", "Take it easy")
        assertThat(json.getDouble("asleep_hours")).isEqualTo(8.0)
        assertThat(json.getInt("asleep_minutes")).isEqualTo(480)
        assertThat(json.getBoolean("vitals_alert")).isTrue()
        val outside = json.getJSONArray("vitals_outside").let { array -> List(array.length()) { array.getString(it) } }
        assertThat(outside.toSet()).isEqualTo(setOf("HRV below", "Resting HR above"))
        assertThat(json.getInt("vitals_in_range")).isEqualTo(json.getInt("vitals_judged") - 2)
    }

    @Test
    fun `the week's load, band and running distance`() {
        val json = report(HomeAssistantInputs(today, now, SleepHistory.EMPTY, form, loads = loads))

        // Monday 23 November to today: runs on the 23rd and the 25th.
        assertThat(json.getInt("week_load")).isEqualTo(100)
        assertThat(json.getDouble("week_running_km")).isEqualTo(18.0)
        assertThat(json.getInt("week_load_low") < json.getInt("week_load_high")).isTrue()
        assertThat(json.has("week_running_tolerance_km")).isTrue()
    }

    @Test
    fun `last workout, to do items and the race time`() {
        val end = Instant.parse("2026-11-24T18:10:00Z")
        val json = report(
            HomeAssistantInputs(
                today,
                now,
                SleepHistory.EMPTY,
                form,
                vo2max = 50.0,
                lastSession = LastSession("Threshold intervals", end, load = 71.6, movingMillis = 52 * 60_000L, distanceMeters = 10_340.0),
                toDo = listOf(HomeAssistantReport.TODO_MORNING, HomeAssistantReport.TODO_TEST),
            ),
        )

        assertThat(json.getString("last_workout")).isEqualTo("Threshold intervals")
        assertThat(json.getString("last_workout_end")).isEqualTo(end.toString())
        assertThat(json.getInt("last_workout_load")).isEqualTo(72)
        assertThat(json.getInt("last_workout_minutes")).isEqualTo(52)
        assertThat(json.getDouble("last_workout_km")).isEqualTo(10.3)
        assertThat(json.getInt("to_do")).isEqualTo(2)
        assertThat(json.getJSONArray("to_do_items").getString(1)).isEqualTo("Reaction test")
        assertThat(json.getString("race_5k")).matches(Regex("\\d{2}:\\d{2}"))
    }

    @Test
    fun `the 5 km time allows for the marathon shape as the app does`() {
        fun race(shape: MarathonShape?) = report(HomeAssistantInputs(today, now, SleepHistory.EMPTY, form, vo2max = 50.0, shape = shape))
            .getString("race_5k")
        val short = MarathonShape(
            percent = 2.0,
            weeklyKm = 20.0,
            targetWeeklyKm = 85.0,
            longRunKm = 30.0,
            longRuns = 0,
            longRunShare = 0.0,
            weeklyDays = 182,
        )
        val expected = RacePredictions.of(50.0, null, short).first { it.distance == RaceDistance.K5 }.fromVo2Max!!.roundToLong()

        assertThat(race(short)).isEqualTo("%d:%02d".format(expected / 60, expected % 60))
        assertThat(race(short) > race(null)).isTrue()
    }

    @Test
    fun `nothing known sends no values`() {
        val json = report(HomeAssistantInputs(today, now, SleepHistory.EMPTY, emptyList()))

        assertThat(json.has("week_load")).isFalse()
        assertThat(json.has("fitness")).isFalse()
        assertThat(json.getInt("to_do")).isEqualTo(0)
    }
}
