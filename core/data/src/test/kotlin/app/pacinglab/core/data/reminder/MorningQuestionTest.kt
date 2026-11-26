package app.pacinglab.core.data.reminder

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime

class MorningQuestionTest {
    private val ten = LocalTime.of(10, 0)

    @Test
    fun `opens once last night is in, never before five`() {
        assertThat(MorningQuestion.isOpen(LocalTime.of(0, 30), nightIn = true, ten)).isFalse()
        assertThat(MorningQuestion.isOpen(LocalTime.of(4, 59), nightIn = true, ten)).isFalse()
        assertThat(MorningQuestion.isOpen(LocalTime.of(5, 0), nightIn = true, ten)).isTrue()
        assertThat(MorningQuestion.isOpen(LocalTime.of(7, 10), nightIn = true, ten)).isTrue()
    }

    @Test
    fun `opens at the fallback time without last night`() {
        assertThat(MorningQuestion.isOpen(LocalTime.of(9, 59), nightIn = false, ten)).isFalse()
        assertThat(MorningQuestion.isOpen(LocalTime.of(10, 0), nightIn = false, ten)).isTrue()
        assertThat(MorningQuestion.isOpen(LocalTime.of(23, 59), nightIn = false, ten)).isTrue()
    }

    @Test
    fun `changes next at five, at the fallback time and at midnight`() {
        val day = LocalDateTime.of(2026, 11, 26, 0, 0)

        assertThat(MorningQuestion.nextChange(day.withHour(0).withMinute(30), ten)).isEqualTo(day.withHour(5))
        assertThat(MorningQuestion.nextChange(day.withHour(5), ten)).isEqualTo(day.withHour(10))
        assertThat(MorningQuestion.nextChange(day.withHour(10).withMinute(1), ten)).isEqualTo(day.plusDays(1))
    }
}
