package app.pacinglab.core.data.repository

import app.pacinglab.core.data.testPreferences
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Duration

class UserPreferencesRepositoryTest {
    @Test
    fun `every preference starts at its default`() = runTest {
        assertThat(testPreferences().preferences.first()).isEqualTo(UserPreferences())
    }

    @Test
    fun `changed preferences are read back`() = runTest {
        val repository = testPreferences()

        repository.setTheme(ThemeChoice.GREEN)
        repository.setDarkMode(DarkMode.DARK)
        repository.setSpeedByDefault(true)
        repository.setSleepNeed(Duration.ofMinutes(510))
        repository.setStrengthInLoad(false)
        repository.setSessionMergeGap(Duration.ofMinutes(10))

        assertThat(repository.preferences.first()).isEqualTo(
            UserPreferences(
                sessionMergeGap = Duration.ofMinutes(10),
                theme = ThemeChoice.GREEN,
                darkMode = DarkMode.DARK,
                speedByDefault = true,
                sleepNeed = Duration.ofMinutes(510),
                strengthInLoad = false,
            ),
        )
    }

    @Test
    fun `the sleep need goes back to automatic and refuses implausible values`() = runTest {
        val repository = testPreferences()
        repository.setSleepNeed(Duration.ofHours(8))

        repository.setSleepNeed(null)

        assertThat(repository.preferences.first().sleepNeed).isEqualTo(null)
        assertFailure { repository.setSleepNeed(Duration.ofHours(4)) }.isInstanceOf(IllegalArgumentException::class)
    }
}
