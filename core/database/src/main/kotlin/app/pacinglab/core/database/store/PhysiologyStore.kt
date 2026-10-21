package app.pacinglab.core.database.store

import app.pacinglab.core.database.PacingLabDatabase
import app.pacinglab.core.database.entity.PhysiologyEntity
import app.pacinglab.core.model.PhysiologyEntry
import app.pacinglab.core.model.PhysiologyHistory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Persists the athlete's heart rate values and the dates they apply from. */
@Singleton
class PhysiologyStore @Inject constructor(database: PacingLabDatabase) {
    private val dao = database.physiologyDao()

    fun observe(): Flow<PhysiologyHistory> = dao.observeAll().map { rows -> PhysiologyHistory(rows.map { it.toDomain() }) }

    /**
     * Saves [entry]. When it edits an existing entry, [replacing] is that entry's date, so that moving
     * an entry to another day does not leave the old one behind. An entry already on the new date is
     * overwritten.
     */
    suspend fun save(entry: PhysiologyEntry, replacing: LocalDate? = null) = dao.replace(replacing?.toEpochDay(), entry.toEntity())

    suspend fun delete(effectiveFrom: LocalDate) = dao.delete(effectiveFrom.toEpochDay())

    private fun PhysiologyEntry.toEntity() = PhysiologyEntity(effectiveFrom.toEpochDay(), maxHr, thresholdHr, restingHr)

    private fun PhysiologyEntity.toDomain() = PhysiologyEntry(LocalDate.ofEpochDay(effectiveFromEpochDay), maxHr, thresholdHr, restingHr)
}
