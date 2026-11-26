package app.pacinglab.core.data.reminder

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * When the morning question ("How rested do you feel?") is open, the one rule for the Today card and the
 * notification: once last night has synced, or from the athlete's fallback time when it has not (watch left
 * off or not synced), and never before [EARLIEST], so that a question does not wait at midnight for a night
 * that has not happened yet.
 */
object MorningQuestion {
    /**
     * No question before this time. A night is closed only once the watch syncs after it, which can happen
     * after a long awakening at 3 or 4 o'clock; this keeps such a split night from asking before morning.
     */
    val EARLIEST: LocalTime = LocalTime.of(5, 0)

    val DEFAULT_FALLBACK: LocalTime = LocalTime.of(10, 0)

    /** The fallback times the athlete can choose, before the reminder's last time ([MorningReminder.LATEST]). */
    val FALLBACK_RANGE: ClosedRange<LocalTime> = LocalTime.of(6, 0)..LocalTime.of(12, 0)

    /** Whether the question of the day is open at [time]; [nightIn] is true once a night ending today has synced. */
    fun isOpen(time: LocalTime, nightIn: Boolean, fallback: LocalTime): Boolean = time >= EARLIEST && (nightIn || time >= fallback)

    /** The next moment after [now] at which [isOpen] can change without new data: earliest, fallback or midnight. */
    fun nextChange(now: LocalDateTime, fallback: LocalTime): LocalDateTime {
        val today = now.toLocalDate()
        return listOf(today.atTime(EARLIEST), today.atTime(fallback), today.plusDays(1).atStartOfDay()).first { it > now }
    }

    /**
     * The time now, again at each [nextChange], with the fallback in force: what a screen needs to open or close
     * the question while it is shown.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeClock(clock: Clock, fallback: Flow<LocalTime>): Flow<MorningClock> = fallback.flatMapLatest { chosen ->
        flow {
            while (true) {
                val now = LocalDateTime.now(clock)
                emit(MorningClock(now, chosen))
                // A second's margin, so that the next emission lands after the boundary rather than on it.
                delay(Duration.between(now, nextChange(now, chosen)).toMillis() + 1_000)
            }
        }
    }
}

/** The time of day with the fallback time in force. */
data class MorningClock(val now: LocalDateTime, val fallback: LocalTime) {
    fun isOpen(nightIn: Boolean): Boolean = MorningQuestion.isOpen(now.toLocalTime(), nightIn, fallback)
}
