package app.pacinglab.core.data.repository

import app.pacinglab.core.database.store.WellnessStore
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * How rested the athlete felt each morning, from 1 (exhausted) to 5 (fully rested): the satisfaction
 * part of sleep health, which no watch can measure.
 */
@Singleton
class WellnessRepository @Inject constructor(private val store: WellnessStore) {
    fun observeRested(): Flow<Map<LocalDate, Int>> = store.observeRested()

    suspend fun setRested(date: LocalDate, rating: Int) = store.setRested(date, rating)

    suspend fun clearRested(date: LocalDate) = store.clear(date)
}
