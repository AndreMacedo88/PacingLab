package app.pacinglab.core.data.repository

import app.pacinglab.core.database.store.PhysiologyStore
import app.pacinglab.core.model.PhysiologyEntry
import app.pacinglab.core.model.PhysiologyHistory
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** The athlete's heart rate values over time (maximal, threshold and resting heart rate). */
@Singleton
class PhysiologyRepository @Inject constructor(private val store: PhysiologyStore) {
    fun observeHistory(): Flow<PhysiologyHistory> = store.observe()

    /** Saves [entry]; [replacing] is the date of the entry being edited, if any. */
    suspend fun save(entry: PhysiologyEntry, replacing: LocalDate? = null) = store.save(entry, replacing)

    suspend fun delete(effectiveFrom: LocalDate) = store.delete(effectiveFrom)
}
