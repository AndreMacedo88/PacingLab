package app.pacinglab.feature.settings

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import app.pacinglab.core.data.repository.DarkMode
import app.pacinglab.core.data.repository.ThemeChoice
import app.pacinglab.core.data.repository.UserPreferences
import app.pacinglab.core.designsystem.theme.PacingLabTheme
import app.pacinglab.core.designsystem.theme.ThemePalette
import assertk.assertThat
import assertk.assertions.isEqualTo
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [Config.NEWEST_SDK], qualifiers = TALL_PHONE)
class SettingsScreenScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val preferences = UserPreferences(sleepNeed = Duration.ofMinutes(480))

    @Test
    fun settings() = capture(preferences, ThemePalette.INDIGO, dark = false)

    @Test
    fun settingsAmberDark() =
        capture(preferences.copy(theme = ThemeChoice.AMBER, darkMode = DarkMode.DARK), ThemePalette.AMBER, dark = true)

    @Test
    fun settingsGreenDark() =
        capture(preferences.copy(theme = ThemeChoice.GREEN, darkMode = DarkMode.DARK), ThemePalette.GREEN, dark = true)

    @Test
    fun settingsBlueLight() =
        capture(preferences.copy(theme = ThemeChoice.BLUE, darkMode = DarkMode.LIGHT), ThemePalette.BLUE, dark = false)

    @Test
    @Config(qualifiers = PHONE)
    fun about() {
        compose.setContent { PacingLabTheme { AboutScreen(onBack = {}, version = "1.0") } }
        compose.onRoot().captureRoboImage()
    }

    @Test
    fun choosingAColourReportsIt() {
        var chosen: ThemeChoice? = null
        compose.setContent {
            PacingLabTheme {
                SettingsScreen(
                    preferences,
                    SettingsActions(onTheme = {
                        chosen = it
                    }),
                    onOpenPhysiology = {},
                    onOpenDataSources = {},
                    onBack = {},
                )
            }
        }

        compose.onNodeWithText("Green").performClick()

        assertThat(chosen).isEqualTo(ThemeChoice.GREEN)
    }

    @Test
    fun aRestoreReportsWhatCameBack() {
        compose.setContent {
            PacingLabTheme {
                SettingsScreen(
                    preferences,
                    SettingsActions(),
                    onOpenPhysiology = {},
                    onOpenDataSources = {},
                    onBack = {},
                    backupMessage = BackupMessage.Restored(entries = 12),
                )
            }
        }

        compose.onNodeWithText("Restored 12 entries and the settings").assertExists()
    }

    private fun capture(preferences: UserPreferences, palette: ThemePalette, dark: Boolean) {
        compose.setContent {
            PacingLabTheme(darkTheme = dark, palette = palette) {
                SettingsScreen(preferences, SettingsActions(), onOpenPhysiology = {}, onOpenDataSources = {}, onBack = {})
            }
        }
        compose.onRoot().captureRoboImage()
    }
}
