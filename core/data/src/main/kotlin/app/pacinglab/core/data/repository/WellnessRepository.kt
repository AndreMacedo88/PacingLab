package app.pacinglab.core.data.repository

import app.pacinglab.core.data.reminder.QuestionNotifier
import app.pacinglab.core.database.store.WellnessStore
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * How rested the athlete felt each morning, from 1 (exhausted) to 5 (fully rested): the satisfaction
 * part of sleep health, which no watch can measure. An answer given in the app takes the morning's
 * notification away.
 */
@Singleton
class WellnessRepository @Inject constructor(
    private val store: WellnessStore,
    private val notifier: QuestionNotifier = QuestionNotifier.NONE,
) {
    fun observeRested(): Flow<Map<LocalDate, Int>> = store.observeRested()

    /** Saves the answer; [fromNotification] leaves the notification it came from to show that it was saved. */
    suspend fun setRested(date: LocalDate, rating: Int, fromNotification: Boolean = false) {
        store.setRested(date, rating)
        if (!fromNotification) notifier.closeRested(date)
    }

    suspend fun clearRested(date: LocalDate) = store.clear(date)
}
