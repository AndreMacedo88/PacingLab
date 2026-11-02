package app.pacinglab.core.database.store

import app.pacinglab.core.database.PacingLabDatabase
import app.pacinglab.core.database.entity.NightClimateEntity
import app.pacinglab.core.model.home.NightClimate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists the bedroom climate of each night. A night is stored even when no sensor had values, so that
 * it is not asked for again; [fetched] tells when, so that nights read before their data was complete are.
 */
@Singleton
class NightClimateStore @Inject constructor(database: PacingLabDatabase) {
    private val dao = database.nightClimateDao()

    /** Every night with at least one value, by morning. */
    fun observe(): Flow<Map<LocalDate, NightClimate>> =
        dao.observeAll().map { rows -> rows.map { it.toModel() }.filterNot { it.isEmpty }.associateBy { it.date } }

    /** When each stored night was read, by morning. */
    suspend fun fetched(): Map<LocalDate, Instant> = dao.all().associate {
        LocalDate.ofEpochDay(it.epochDay) to
            Instant.ofEpochMilli(it.fetchedMs)
    }

    suspend fun save(nights: List<NightClimate>, at: Instant) {
        if (nights.isEmpty()) return
        dao.upsert(
            nights.map {
                NightClimateEntity(it.date.toEpochDay(), it.temperature, it.humidity, it.co2, it.co2Max, at.toEpochMilli())
            },
        )
    }

    suspend fun clear() = dao.clear()

    private fun NightClimateEntity.toModel() = NightClimate(LocalDate.ofEpochDay(epochDay), temperature, humidity, co2, co2Max)
}
