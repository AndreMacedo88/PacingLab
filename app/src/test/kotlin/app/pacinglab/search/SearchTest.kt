package app.pacinglab.search

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.navigation3.runtime.NavKey
import app.pacinglab.core.designsystem.theme.PacingLabTheme
import app.pacinglab.feature.performance.PerformanceCardKind
import app.pacinglab.feature.performance.navigation.PerformanceKey
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [Config.NEWEST_SDK], qualifiers = "w384dp-h832dp-xxhdpi")
class SearchTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `a word matches the start of a word of the title, a keyword or the place`() {
        assertEquals(0, rank("crit", "Critical speed", emptyList(), "Performance › Capacity"))
        assertEquals(1, rank("speed", "Critical speed", emptyList(), "Performance › Capacity"))
        assertEquals(2, rank("lthr", "Heart rate zones", listOf("lthr", "max heart rate"), "Performance › Capacity"))
        assertEquals(4, rank("capacity", "Critical speed", emptyList(), "Performance › Capacity"))
        assertNull(rank("cadence", "Critical speed", emptyList(), "Performance › Capacity"))
    }

    @Test
    fun `case, accents and punctuation do not matter`() {
        assertNotNull(rank("HRV", "Heart rate variability", listOf("hrv", "rmssd"), "Body"))
        assertEquals(1, rank("vo2 max", "VO2max", emptyList(), "Performance"))
        assertNotNull(rank("pa:hr", "Aerobic decoupling", listOf("pa:hr"), "Performance"))
        assertEquals(1, rank("éfficiency", "Aerobic efficiency", emptyList(), "Performance"))
    }

    @Test
    fun nothingTyped() = capture("")

    @Test
    fun typing() = capture("thresh")

    @Test
    fun nothingFound() = capture("cadence")

    @Test
    fun aResultOpensItsCard() {
        var opened: NavKey? = null
        compose.setContent {
            PacingLabTheme { SearchScreen(onOpen = { opened = it }, onBack = {}, initialQuery = "critical", autoFocus = false) }
        }

        compose.onNodeWithText("Critical speed").performClick()

        assertEquals(PerformanceKey(PerformanceCardKind.CRITICAL_SPEED.id), opened)
    }

    private fun capture(query: String) {
        compose.setContent { PacingLabTheme { SearchScreen(onOpen = {}, onBack = {}, initialQuery = query, autoFocus = false) } }
        compose.onRoot().captureRoboImage()
    }
}
