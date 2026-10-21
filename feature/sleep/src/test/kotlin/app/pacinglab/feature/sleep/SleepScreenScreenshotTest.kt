package app.pacinglab.feature.sleep

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import app.pacinglab.core.analytics.recovery.Night
import app.pacinglab.core.analytics.recovery.SleepContinuity
import app.pacinglab.core.analytics.recovery.SleepHistory
import app.pacinglab.core.analytics.recovery.SleepScore
import app.pacinglab.core.analytics.recovery.SleepingHeartRate
import app.pacinglab.core.designsystem.theme.PacingLabTheme
import app.pacinglab.core.model.HeartRateSeries
import app.pacinglab.core.model.SleepStage
import app.pacinglab.core.model.SleepStageType
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.sin

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [Config.NEWEST_SDK], qualifiers = "w384dp-h3600dp-xxhdpi")
class SleepScreenScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val cest = ZoneOffset.ofHours(2)
    private val last = LocalDate.of(2026, 10, 21)

    /**
     * A night of about five sleep cycles: deep sleep early, REM growing towards the morning, a couple of
     * short awakenings; [shiftMinutes] moves bedtime, [shortenMinutes] cuts the morning.
     */
    private fun night(date: LocalDate, shiftMinutes: Long, shortenMinutes: Long = 0, index: Int = 0): Night {
        val bedtime = Instant.parse("${date.minusDays(1)}T21:05:00Z").plus(Duration.ofMinutes(shiftMinutes))
        val plan = buildList {
            add(SleepStageType.AWAKE to 18L)
            for (cycle in 0 until 5) {
                add(SleepStageType.LIGHT to 30L + cycle * 3)
                add(SleepStageType.DEEP to (40L - cycle * 9).coerceAtLeast(6))
                add(SleepStageType.LIGHT to 14L)
                add(SleepStageType.REM to 10L + cycle * 8)
                if (cycle == 2 || cycle == 4) add(SleepStageType.AWAKE to 4L + index % 3)
            }
            add(SleepStageType.LIGHT to (25L - shortenMinutes).coerceAtLeast(5))
            add(SleepStageType.AWAKE to 12L)
        }
        var t = bedtime
        val stages = plan.map { (type, minutes) -> SleepStage(t, t.plus(Duration.ofMinutes(minutes)), type).also { t = it.end } }
        val asleep = stages.filter { it.type != SleepStageType.AWAKE }.sumOf { Duration.between(it.start, it.end).toMillis() }
        val onset = stages.first { it.type != SleepStageType.AWAKE }.start
        val final = stages.last { it.type != SleepStageType.AWAKE }.end
        val wake = stages.filter { it.type == SleepStageType.AWAKE && it.start >= onset && it.end <= final }
        return Night(
            date = date,
            start = bedtime,
            end = t,
            asleepMillis = asleep,
            stageMillis = stages.groupBy { it.type }.mapValues { (_, s) -> s.sumOf { Duration.between(it.start, it.end).toMillis() } },
            hrvMs = 60.0 + 6 * sin(index * 1.3),
            restingHr = 56.0 + sin(index * 0.9),
            respiratoryRate = 12.3 + 0.4 * sin(index * 0.7),
            continuity = SleepContinuity(
                onset = onset,
                finalAwakening = final,
                latencyMillis = Duration.between(bedtime, onset).toMillis(),
                wakeAfterOnsetMillis = wake.sumOf { Duration.between(it.start, it.end).toMillis() },
                longAwakenings = wake.count { Duration.between(it.start, it.end) >= Duration.ofMinutes(5) },
            ),
            zone = cest,
            stages = stages,
            sleepingHeartRate = SleepingHeartRate.of(heartRate(bedtime, t, index)),
        )
    }

    /** A sample every 10 s: falling from about 60 to the low 50s by the middle of the night, then rising slowly. */
    private fun heartRate(from: Instant, to: Instant, index: Int): HeartRateSeries {
        val n = (Duration.between(from, to).seconds / 10).toInt()
        return HeartRateSeries(
            LongArray(n) { from.toEpochMilli() + it * 10_000L },
            IntArray(n) { i ->
                val x = i.toDouble() / n
                (60 - 14 * sin(Math.PI * minOf(x * 1.6, 1.0) / 2) + 6 * x * x + 2 * sin(i / 37.0 + index)).toInt()
            },
        )
    }

    private val nights = (29 downTo 0).map { back ->
        val i = 29 - back
        night(
            last.minusDays(back.toLong()),
            shiftMinutes = (25 * sin(i * 1.1)).toLong(),
            shortenMinutes = if (i % 5 ==
                3
            ) {
                20
            } else {
                0
            },
            index = i,
        )
    }

    private val loaded = SleepUiState.Loaded(
        nights,
        SleepScore.all(nights),
        nights.associate {
            it.date to SleepScore.need(nights, it.date)
        },
    )

    @Test
    fun lastNight() = capture(loaded)

    @Test
    fun lastNightDark() = capture(loaded, darkTheme = true)

    @Test
    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    fun noNights() = capture(SleepUiState.Loaded(emptyList(), emptyMap(), emptyMap()))

    @Test
    fun arrowsStepThroughNights() {
        compose.setContent {
            PacingLabTheme { SleepScreen(state = loaded, onOpenSettings = {}) }
        }
        compose.onNodeWithText("Wednesday 21 October").assertExists()

        compose.onNodeWithContentDescription("Previous night").performClick()
        compose.onNodeWithText("Tuesday 20 October").assertExists()
        compose.onNodeWithContentDescription("Next night").performClick()
        compose.onNodeWithText("Wednesday 21 October").assertExists()
    }

    private fun capture(state: SleepUiState, darkTheme: Boolean = false) {
        compose.setContent {
            PacingLabTheme(darkTheme = darkTheme) { SleepScreen(state = state, onOpenSettings = {}) }
        }
        compose.onRoot().captureRoboImage()
    }
}
