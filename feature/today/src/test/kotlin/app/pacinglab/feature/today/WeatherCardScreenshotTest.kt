package app.pacinglab.feature.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import app.pacinglab.core.data.weather.WeatherState
import app.pacinglab.core.designsystem.theme.PacingLabTheme
import app.pacinglab.core.model.Coordinates
import app.pacinglab.core.model.WeatherCondition
import app.pacinglab.core.model.WeatherForecast
import app.pacinglab.core.model.WeatherReading
import assertk.assertThat
import assertk.assertions.isTrue
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [Config.NEWEST_SDK], qualifiers = "w384dp-h1250dp-xxhdpi")
class WeatherCardScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val now = Instant.parse("2026-10-28T15:20:00Z")

    // An autumn afternoon turning to rain in the evening, in Copenhagen (UTC+1).
    private val forecast = WeatherForecast(
        place = Coordinates(55.68, 12.57),
        offset = ZoneOffset.ofHours(1),
        fetched = now,
        now = reading(now, WeatherCondition.PARTLY_CLOUDY, 9.4, chance = null, daylight = true),
        hours = (0 until 24).map { h ->
            val condition = when (h) {
                in 0..1 -> WeatherCondition.PARTLY_CLOUDY
                in 2..3 -> WeatherCondition.CLOUDY
                in 4..7 -> WeatherCondition.RAIN
                in 8..9 -> WeatherCondition.DRIZZLE
                else -> WeatherCondition.CLOUDY
            }
            val chance = listOf(0, 5, 15, 30, 65, 80, 70, 55, 35, 25, 10).getOrElse(h) { 5 }
            reading(now.plusSeconds(h * 3600L - 20 * 60), condition, 9.0 - h * 0.35, chance, daylight = h < 1)
        },
        todayLow = 5.2,
        todayHigh = 10.6,
    )

    private fun reading(time: Instant, condition: WeatherCondition, temperature: Double, chance: Int?, daylight: Boolean) = WeatherReading(
        time = time,
        condition = condition,
        temperature = temperature,
        apparentTemperature = temperature - 3.2,
        dewPoint = 5.6,
        windSpeed = 6.3,
        windGusts = 11.8,
        precipitation = 0.0,
        precipitationChance = chance,
        daylight = daylight,
    )

    @Test
    fun weather() = capture(darkTheme = false)

    @Test
    fun weatherDark() = capture(darkTheme = true)

    @Test
    fun askingForLocationAsksForThePermission() {
        var asked = false
        compose.setContent {
            PacingLabTheme {
                WeatherCard(WeatherState.NeedsLocation, Locale.UK, WeatherAccess(onAllowLocation = { asked = true }))
            }
        }

        compose.onNodeWithText("Allow approximate location").performClick()

        assertThat(asked).isTrue()
    }

    private fun capture(darkTheme: Boolean) {
        compose.setContent {
            PacingLabTheme(darkTheme = darkTheme) {
                Column(
                    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    WeatherCard(WeatherState.Ready(forecast), Locale.UK, WeatherAccess())
                    WeatherCard(WeatherState.Ready(forecast, outdated = true), Locale.UK, WeatherAccess())
                    WeatherCard(WeatherState.NeedsLocation, Locale.UK, WeatherAccess())
                    WeatherCard(WeatherState.NeedsLocation, Locale.UK, WeatherAccess(locationRefused = true))
                    WeatherCard(WeatherState.NoConnection, Locale.UK, WeatherAccess())
                    WeatherCard(null, Locale.UK, WeatherAccess())
                }
            }
        }
        compose.onRoot().captureRoboImage()
    }
}
