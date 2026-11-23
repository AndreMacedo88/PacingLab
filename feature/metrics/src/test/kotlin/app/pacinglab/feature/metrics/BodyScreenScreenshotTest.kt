package app.pacinglab.feature.metrics

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import app.pacinglab.core.analytics.recovery.HrvVariation
import app.pacinglab.core.analytics.recovery.Night
import app.pacinglab.core.analytics.recovery.NightlyMetric
import app.pacinglab.core.analytics.recovery.RecoveryTrend
import app.pacinglab.core.analytics.strain.DaytimeStrain
import app.pacinglab.core.analytics.strain.StrainDay
import app.pacinglab.core.analytics.zones.Zones
import app.pacinglab.core.data.strain.StrainHistory
import app.pacinglab.core.designsystem.theme.PacingLabTheme
import app.pacinglab.core.model.HeartRateAnchors
import app.pacinglab.core.model.HeartRateSeries
import app.pacinglab.core.model.SleepStageType
import app.pacinglab.core.model.ZoneModel
import assertk.assertThat
import assertk.assertions.isEqualTo
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.exp
import kotlin.math.sin

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [Config.NEWEST_SDK], qualifiers = "w384dp-h2300dp-xxhdpi")
class BodyScreenScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 10, 21)

    /**
     * Ten weeks of made-up nights: HRV wobbling around 60 ms with a dip in the last week, resting heart rate rising
     * then, and the Google Health values in the last month.
     */
    private val nights = (69 downTo 0).map { back ->
        val date = today.minusDays(back.toLong())
        val start = Instant.parse("${date.minusDays(1)}T22:00:00Z")
        val dip = if (back < 6) 0.82 else 1.0
        Night(
            date = date,
            start = start,
            end = start.plusSeconds(8 * 3_600L),
            asleepMillis = 7 * 3_600_000L,
            stageMillis = mapOf(SleepStageType.LIGHT to 7 * 3_600_000L),
            hrvMs = 60.0 * dip * exp(0.09 * sin(back * 1.3)),
            restingHr = 54.0 + 1.2 * sin(back * 0.8) + if (back < 6) 3.5 else 0.0,
            respiratoryRate = 12.5 + 0.3 * sin(back * 0.6),
            zone = ZoneOffset.UTC,
            oxygenSaturation = if (back < 30) 95.4 + 0.5 * sin(back * 0.9) else null,
            skinTemperature = if (back < 30) 0.2 * sin(back * 1.1) else null,
        )
    }

    /** A day of heart rate every ten seconds from midnight to 19:30, with a run in the late afternoon. */
    private val heartRate = run {
        val from = today.atStartOfDay().toInstant(ZoneOffset.UTC)
        val seconds = (0 until (19 * 60 + 30) * 60 step 10).toList()
        HeartRateSeries(
            LongArray(seconds.size) { from.plusSeconds(seconds[it].toLong()).toEpochMilli() },
            IntArray(seconds.size) { i ->
                val minute = seconds[i] / 60
                when {
                    minute < 6 * 60 -> 52 + (minute / 20) % 3
                    minute in 17 * 60..17 * 60 + 40 -> 150 + (minute % 8) * 2
                    else -> 70 + (minute / 15) % 9
                }
            },
        )
    }

    private val strain = StrainHistory(
        today,
        (0 until 28).map { i ->
            val score = (50 + 9 * sin(i * 1.3)).toInt()
            StrainDay(today.minusDays(27L - i), (score - 50) / 4.0, 380, score, DaytimeStrain.levelOf(score))
        },
        progress = 21,
    )

    private val loaded = BodyUiState.Loaded(
        today = today,
        vitals = vitalsOf(nights, today),
        tiles = tilesOf(nights, today),
        recovery = RecoveryTrend.of(nights, today),
        variation = HrvVariation.of(nights, today),
        heartRate = heartRate,
        zones = Zones.of(ZoneModel.LTHR_FIVE, HeartRateAnchors(maxHr = 186, thresholdHr = 167, restingHr = 52)),
        restingHr = nights.last().restingHr,
        strain = strain,
    )

    @Test
    fun body() = capture(loaded)

    @Test
    fun bodyDark() = capture(loaded, darkTheme = true)

    /** Opened from Today's strain tile: scrolled to daytime strain, which flashes once. */
    @Test
    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    fun openedAtStrain() = capture(loaded, focus = BodyCardKind.STRAIN.id)

    @Test
    fun aTileOpensItsMeasure() {
        var opened: NightlyMetric? = null
        compose.setContent {
            PacingLabTheme {
                BodyScreen(loaded, onOpenMetric = { opened = it }, onOpenHeartRate = {}, onOpenSettings = {}, zoneId = ZoneOffset.UTC)
            }
        }

        compose.onAllNodesWithText("Breathing rate")[0].performClick()

        assertThat(opened).isEqualTo(NightlyMetric.BREATHING_RATE)
    }

    private fun capture(state: BodyUiState, darkTheme: Boolean = false, focus: String? = null) {
        compose.setContent {
            PacingLabTheme(darkTheme = darkTheme) {
                BodyScreen(state, onOpenMetric = {}, onOpenHeartRate = {}, onOpenSettings = {}, focus = focus, zoneId = ZoneOffset.UTC)
            }
        }
        compose.onRoot().captureRoboImage()
    }
}
