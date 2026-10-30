package app.pacinglab.feature.settings

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import app.pacinglab.core.data.repository.DarkMode
import app.pacinglab.core.data.repository.PlacedCard
import app.pacinglab.core.data.repository.ThemeChoice
import app.pacinglab.core.data.repository.UserPreferences
import app.pacinglab.core.data.repository.WidgetChart
import app.pacinglab.core.designsystem.theme.PacingLabTheme
import app.pacinglab.core.designsystem.theme.ThemePalette
import app.pacinglab.core.ui.WidgetMeasure
import assertk.assertThat
import assertk.assertions.isEqualTo
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.DayOfWeek
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [Config.NEWEST_SDK], qualifiers = TALL_PHONE)
class SettingsScreenScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val preferences = UserPreferences(sleepNeed = Duration.ofMinutes(480))

    private val widget = WidgetMeasure.entries.map { PlacedCard(it.id, !it.hiddenByDefault) }

    @Test
    @Config(qualifiers = PHONE)
    fun settings() {
        compose.setContent { PacingLabTheme { SettingsScreen(onOpenSection = {}, onOpenAbout = {}, onBack = {}) } }
        compose.onRoot().captureRoboImage()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun profile() = capture(SettingsSection.PROFILE)

    @Test
    @Config(qualifiers = PHONE)
    fun training() = capture(SettingsSection.TRAINING)

    @Test
    @Config(qualifiers = PHONE)
    fun sleep() = capture(SettingsSection.SLEEP)

    @Test
    @Config(qualifiers = PHONE)
    fun sleepAutomatic() = capture(SettingsSection.SLEEP, preferences.copy(sleepNeed = null))

    @Test
    @Config(qualifiers = PHONE)
    fun trainingChanged() = capture(
        SettingsSection.TRAINING,
        preferences.copy(sessionMergeGap = Duration.ofMinutes(12), runningIncreasePercent = 15),
    )

    @Test
    @Config(qualifiers = PHONE)
    fun appearance() = capture(SettingsSection.APPEARANCE)

    @Test
    @Config(qualifiers = PHONE)
    fun appearanceAmberDark() = capture(
        SettingsSection.APPEARANCE,
        preferences.copy(theme = ThemeChoice.AMBER, darkMode = DarkMode.DARK),
        ThemePalette.AMBER,
        dark = true,
    )

    @Test
    @Config(qualifiers = PHONE)
    fun appearanceGreenDark() = capture(
        SettingsSection.APPEARANCE,
        preferences.copy(theme = ThemeChoice.GREEN, darkMode = DarkMode.DARK),
        ThemePalette.GREEN,
        dark = true,
    )

    @Test
    @Config(qualifiers = PHONE)
    fun appearanceBlueLight() = capture(
        SettingsSection.APPEARANCE,
        preferences.copy(theme = ThemeChoice.BLUE, darkMode = DarkMode.LIGHT),
        ThemePalette.BLUE,
        dark = false,
    )

    @Test
    fun widgetMeasures() = capture(SettingsSection.WIDGET)

    @Test
    @Config(qualifiers = PHONE)
    fun data() = capture(SettingsSection.DATA)

    @Test
    @Config(qualifiers = PHONE)
    fun about() {
        compose.setContent { PacingLabTheme { AboutScreen(onBack = {}, version = "1.0") } }
        compose.onRoot().captureRoboImage()
    }

    @Test
    fun openingAGroupReportsIt() {
        var opened: SettingsSection? = null
        compose.setContent { PacingLabTheme { SettingsScreen(onOpenSection = { opened = it }, onOpenAbout = {}, onBack = {}) } }

        compose.onNodeWithText("Sleep").performClick()

        assertThat(opened).isEqualTo(SettingsSection.SLEEP)
    }

    @Test
    fun choosingAColourReportsIt() {
        var chosen: ThemeChoice? = null
        compose.setContent {
            PacingLabTheme {
                SettingsSectionScreen(SettingsSection.APPEARANCE, preferences, SettingsActions(onTheme = { chosen = it }), onBack = {})
            }
        }

        compose.onNodeWithText("Green").performClick()

        assertThat(chosen).isEqualTo(ThemeChoice.GREEN)
    }

    @Test
    fun aFreeDayIsAddedAndRemoved() {
        var days: Set<DayOfWeek>? = null
        compose.setContent {
            PacingLabTheme {
                SettingsSectionScreen(SettingsSection.SLEEP, preferences, SettingsActions(onFreeDays = { days = it }), onBack = {})
            }
        }

        compose.onNodeWithText("Fri").performClick()
        assertThat(days).isEqualTo(setOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY))
        compose.onNodeWithText("Sun").performClick()
        assertThat(days).isEqualTo(setOf(DayOfWeek.SATURDAY))
    }

    @Test
    fun aFixedSleepNeedStartsFromTheAutomaticOneToTheQuarterHour() {
        var need: Duration? = Duration.ZERO
        compose.setContent {
            PacingLabTheme {
                SettingsSectionScreen(
                    SettingsSection.SLEEP,
                    preferences.copy(sleepNeed = null),
                    SettingsActions(onSleepNeed = { need = it }),
                    onBack = {},
                    automaticSleepNeed = AUTOMATIC_NEED,
                )
            }
        }

        compose.onNodeWithText("Fixed").performClick()

        assertThat(need).isEqualTo(Duration.ofMinutes(7 * 60 + 45))
    }

    @Test
    fun theMergeGapIsSavedWhenTheSliderIsLetGo() {
        var gap: Duration? = null
        compose.setContent {
            PacingLabTheme {
                SettingsSectionScreen(SettingsSection.TRAINING, preferences, SettingsActions(onMergeGap = { gap = it }), onBack = {})
            }
        }

        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress)).onFirst()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(12f) }

        assertThat(gap).isEqualTo(Duration.ofMinutes(12))
    }

    @Test
    fun theDefaultIsOneTapAway() {
        var percent: Int? = null
        compose.setContent {
            PacingLabTheme {
                SettingsSectionScreen(
                    SettingsSection.TRAINING,
                    preferences.copy(runningIncreasePercent = 15),
                    SettingsActions(onRunningIncrease = { percent = it }),
                    onBack = {},
                )
            }
        }

        compose.onNodeWithText("Default: 30 %").performScrollTo().performClick()

        assertThat(percent).isEqualTo(30)
    }

    @Test
    fun choosingWhatTheWidgetChartsReportsIt() {
        var chart: WidgetChart? = null
        compose.setContent {
            PacingLabTheme {
                SettingsSectionScreen(
                    SettingsSection.WIDGET,
                    preferences,
                    SettingsActions(onWidgetChart = { chart = it }),
                    onBack = {},
                    widget = widget,
                )
            }
        }

        compose.onAllNodesWithText("Sleep").onFirst().performClick()

        assertThat(chart).isEqualTo(WidgetChart.SLEEP_SCORE)
    }

    @Test
    fun aRestoreReportsWhatCameBack() {
        compose.setContent {
            PacingLabTheme {
                SettingsSectionScreen(
                    SettingsSection.DATA,
                    preferences,
                    SettingsActions(),
                    onBack = {},
                    backupMessage = BackupMessage.Restored(entries = 12),
                )
            }
        }

        compose.onNodeWithText("Restored 12 entries and the settings").assertExists()
    }

    private fun capture(
        section: SettingsSection,
        preferences: UserPreferences = this.preferences,
        palette: ThemePalette = ThemePalette.INDIGO,
        dark: Boolean = false,
    ) {
        compose.setContent {
            PacingLabTheme(darkTheme = dark, palette = palette) {
                SettingsSectionScreen(
                    section,
                    preferences,
                    SettingsActions(),
                    onBack = {},
                    widget = widget,
                    automaticSleepNeed = AUTOMATIC_NEED,
                )
            }
        }
        compose.onRoot().captureRoboImage()
    }

    private companion object {
        /** A made-up need worked out from the nights: 7:47. */
        val AUTOMATIC_NEED: Duration = Duration.ofMinutes(7 * 60 + 47)
    }
}
