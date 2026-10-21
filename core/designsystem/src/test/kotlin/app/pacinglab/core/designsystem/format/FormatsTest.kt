package app.pacinglab.core.designsystem.format

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.Test
import java.util.Locale

class FormatsTest {
    @Test
    fun `durations switch to hours after sixty minutes`() {
        assertThat(Formats.duration(45 * 60_000L + 7_000)).isEqualTo("45:07")
        assertThat(Formats.duration(62 * 60_000L + 45_400)).isEqualTo("1:02:45")
        assertThat(Formats.duration(999)).isEqualTo("0:01")
    }

    @Test
    fun `kilometres use the locale's decimal separator`() {
        assertThat(Formats.kilometres(10_951.4, Locale.UK)).isEqualTo("10.95")
        assertThat(Formats.kilometres(10_951.4, Locale.forLanguageTag("da-DK"))).isEqualTo("10,95")
        assertThat(Formats.kilometres(123_456.0, Locale.UK)).isEqualTo("123.5")
    }

    @Test
    fun `pace is minutes and seconds per kilometre`() {
        assertThat(Formats.pacePerKm(durationMillis = 30 * 60_000L, meters = 7_200.0)).isEqualTo("4:10")
    }

    @Test
    fun `pace is omitted for tiny distances or standing still`() {
        assertThat(Formats.pacePerKm(60_000, 50.0)).isNull()
        assertThat(Formats.pacePerKm(3 * 3_600_000L, 1_000.0)).isNull()
    }

    @Test
    fun `speed is kilometres per hour with one decimal in the locale`() {
        assertThat(Formats.speedKmh(durationMillis = 30 * 60_000L, meters = 7_200.0, Locale.UK)).isEqualTo("14.4")
        assertThat(Formats.speedKmh(durationMillis = 30 * 60_000L, meters = 7_200.0, Locale.forLanguageTag("da-DK"))).isEqualTo("14,4")
        assertThat(Formats.speedKmh(3 * 3_600_000L, 1_000.0, Locale.UK)).isNull()
    }
}
