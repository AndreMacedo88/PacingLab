package app.pacinglab.feature.settings.homeassistant

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import app.pacinglab.core.data.home.ClimateRole
import app.pacinglab.core.data.home.ConnectResult
import app.pacinglab.core.data.home.HomeAssistantStatus
import app.pacinglab.core.data.home.HomeAssistantYaml
import app.pacinglab.core.designsystem.theme.PacingLabTheme
import app.pacinglab.core.model.home.HomeSensor
import app.pacinglab.feature.settings.PHONE
import app.pacinglab.feature.settings.TALL_PHONE
import assertk.assertThat
import assertk.assertions.isEqualTo
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [Config.NEWEST_SDK], qualifiers = PHONE)
class HomeAssistantScreenScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val connected = HomeAssistantStatus(
        connected = true,
        url = "https://homeassistant.example.ts.net",
        serverName = "Home",
        sensors = mapOf(ClimateRole.TEMPERATURE to "sensor.bedroom_temperature"),
        sensorNames = mapOf(ClimateRole.TEMPERATURE to "Bedroom temperature"),
        sending = true,
        lastRead = Instant.parse("2026-11-03T07:12:00Z"),
        lastSent = Instant.parse("2026-11-03T07:12:00Z"),
        nights = 23,
    )

    @Test
    fun notConnected() = capture(HomeAssistantUiState(result = ConnectResult.TokenRejected))

    @Test
    @Config(qualifiers = TALL_PHONE)
    fun connected() = capture(HomeAssistantUiState(status = connected))

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    @Config(qualifiers = TALL_PHONE)
    fun configuration() {
        compose.setContent {
            PacingLabTheme {
                HomeAssistantScreen(
                    HomeAssistantUiState(status = connected, yaml = HomeAssistantYaml.of("pacinglab_0123456789abcdef")),
                    onBack = {},
                    zoneId = ZoneOffset.UTC,
                )
            }
        }
        captureScreenRoboImage()
    }

    @Test
    fun choosingASensorOffersOnlyItsKind() {
        var chosen: HomeSensor? = null
        val sensors = listOf(
            HomeSensor("sensor.bedroom_temperature", "Bedroom temperature", "temperature", "°C", "19.5"),
            HomeSensor("sensor.office_co2", "Office CO2", "carbon_dioxide", "ppm", "640"),
        )
        compose.setContent {
            PacingLabTheme {
                HomeAssistantScreen(
                    HomeAssistantUiState(status = connected, sensors = SensorList.Loaded(sensors)),
                    onBack = {},
                    onChoose = { _, sensor -> chosen = sensor },
                )
            }
        }
        compose.onNodeWithText("CO2").performClick()
        // Only the screen's own row names the thermometer; the dialog offers CO2 sensors alone.
        compose.onAllNodesWithText("Bedroom temperature").assertCountEquals(1)
        compose.onNodeWithText("Office CO2").performClick()

        assertThat(chosen?.entityId).isEqualTo("sensor.office_co2")
    }

    @Test
    fun connectSendsWhatWasTyped() {
        var sent: Pair<String, String>? = null
        compose.setContent {
            PacingLabTheme { HomeAssistantScreen(HomeAssistantUiState(), onBack = {}, onConnect = { url, token -> sent = url to token }) }
        }
        compose.onNodeWithText("Address").performTextInput("https://homeassistant.example.ts.net")
        compose.onNodeWithText("Long-lived access token").performTextInput("made-up")
        compose.onNodeWithText("Connect").performClick()

        assertThat(sent).isEqualTo("https://homeassistant.example.ts.net" to "made-up")
    }

    private fun capture(state: HomeAssistantUiState) {
        compose.setContent { PacingLabTheme { HomeAssistantScreen(state, onBack = {}, zoneId = ZoneOffset.UTC) } }
        compose.onRoot().captureRoboImage()
    }
}
