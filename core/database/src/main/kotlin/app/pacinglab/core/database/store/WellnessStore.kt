package app.pacinglab.core.database.store

import app.pacinglab.core.database.PacingLabDatabase
import app.pacinglab.core.database.entity.WellnessEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Persists how rested the athlete felt each morning. */
@Singleton
class WellnessStore @Inject constructor(database: PacingLabDatabase) {
    private val dao = database.wellnessDao()

    /** Ratings from 1 (exhausted) to 5 (fully rested) by date. */
    fun observeRested(): Flow<Map<LocalDate, Int>> =
        dao.observeAll().map { rows -> rows.mapNotNull { row -> row.rested?.let { LocalDate.ofEpochDay(row.epochDay) to it } }.toMap() }

    suspend fun setRested(date: LocalDate, rating: Int) {
        require(rating in RANGE) { "Rating $rating outside $RANGE" }
        dao.upsert(WellnessEntity(date.toEpochDay(), rating))
    }

    suspend fun clear(date: LocalDate) = dao.delete(date.toEpochDay())

    companion object {
        val RANGE = 1..5
    }
}
