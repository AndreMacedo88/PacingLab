package app.pacinglab.feature.settings.physiology

import app.pacinglab.core.model.PhysiologyEntry
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import org.junit.Test
import java.time.LocalDate

class EditorInputTest {
    private val day = LocalDate.of(2026, 10, 21)

    @Test
    fun `max heart rate alone is enough`() {
        assertThat(EditorInput("186", "", "").toEntry(day)).isEqualTo(PhysiologyEntry(day, maxHr = 186))
    }

    @Test
    fun `all three values are kept`() {
        assertThat(EditorInput("186", "170", "52").toEntry(day)).isEqualTo(PhysiologyEntry(day, 186, 170, 52))
    }

    @Test
    fun `without an estimate an empty max heart rate is incomplete but not an error`() {
        val input = EditorInput("", "", "")
        assertThat(input.maxError).isFalse()
        assertThat(input.toEntry(day)).isNull()
    }

    @Test
    fun `with an estimate every field may stay empty`() {
        assertThat(EditorInput("", "", "", estimatedMaxHr = 186).toEntry(day)).isEqualTo(PhysiologyEntry(day))
        assertThat(EditorInput("", "160", "", estimatedMaxHr = 186).toEntry(day)).isEqualTo(PhysiologyEntry(day, thresholdHr = 160))
        // The threshold must stay below the estimated maximum it will be used with.
        assertThat(EditorInput("", "186", "", estimatedMaxHr = 186).thresholdError).isTrue()
    }

    @Test
    fun `implausible values are flagged`() {
        assertThat(EditorInput("300", "", "").maxError).isTrue()
        assertThat(EditorInput("186", "190", "").thresholdError).isTrue()
        assertThat(EditorInput("186", "70", "").thresholdError).isTrue()
        assertThat(EditorInput("186", "", "15").restingError).isTrue()
        // Resting must stay below the estimated threshold (167 for 186).
        assertThat(EditorInput("186", "", "170").restingError).isTrue()
        assertThat(EditorInput("186", "", "170").toEntry(day)).isNull()
    }
}
