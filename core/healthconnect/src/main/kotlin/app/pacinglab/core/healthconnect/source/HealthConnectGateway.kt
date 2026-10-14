package app.pacinglab.core.healthconnect.source

import android.health.connect.HealthConnectException
import androidx.health.connect.client.changes.DeletionChange
import androidx.health.connect.client.changes.UpsertionChange
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.request.ChangesTokenRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import app.pacinglab.core.healthconnect.HealthConnectManager
import app.pacinglab.core.healthconnect.MAX_PAGE_SIZE
import app.pacinglab.core.model.source.RateLimitedException
import kotlinx.coroutines.CancellationException
import java.time.Instant
import javax.inject.Inject
import kotlin.reflect.KClass

data class RecordPage<T : Record>(val records: List<T>, val nextPageToken: String?)

data class ChangesPage(
    val upserted: List<Record>,
    val deletedIds: List<String>,
    val nextToken: String,
    val hasMore: Boolean,
    val tokenExpired: Boolean,
)

/**
 * The part of Health Connect that [HealthConnectDataSource] uses. Keeping it this narrow lets tests
 * replace Health Connect with an in-memory store whose behaviour they control.
 */
interface HealthConnectGateway {
    suspend fun grantedPermissions(): Set<String>

    /** One page of [type] records in [from, to), oldest first unless [ascending] is false. */
    suspend fun <T : Record> readPage(
        type: KClass<T>,
        from: Instant,
        to: Instant,
        origins: Set<String> = emptySet(),
        ascending: Boolean = true,
        pageSize: Int = MAX_PAGE_SIZE,
        pageToken: String? = null,
    ): RecordPage<T>

    /** A single record. Exercise sessions only carry their route when read this way. */
    suspend fun <T : Record> readOne(type: KClass<T>, id: String): T

    suspend fun changesToken(type: KClass<out Record>): String

    suspend fun changes(token: String): ChangesPage
}

/** Calls the real Health Connect client and turns its rate-limit errors into [RateLimitedException]. */
class ClientHealthConnectGateway @Inject constructor(private val manager: HealthConnectManager) : HealthConnectGateway {
    override suspend fun grantedPermissions(): Set<String> = call { manager.grantedPermissions() }

    override suspend fun <T : Record> readPage(
        type: KClass<T>,
        from: Instant,
        to: Instant,
        origins: Set<String>,
        ascending: Boolean,
        pageSize: Int,
        pageToken: String?,
    ): RecordPage<T> = call {
        val response = manager.client.readRecords(
            ReadRecordsRequest(
                recordType = type,
                timeRangeFilter = TimeRangeFilter.between(from, to),
                dataOriginFilter = origins.mapTo(mutableSetOf(), ::DataOrigin),
                ascendingOrder = ascending,
                pageSize = pageSize,
                pageToken = pageToken,
            ),
        )
        RecordPage(response.records, response.pageToken)
    }

    override suspend fun <T : Record> readOne(type: KClass<T>, id: String): T = call { manager.client.readRecord(type, id).record }

    override suspend fun changesToken(type: KClass<out Record>): String =
        call { manager.client.getChangesToken(ChangesTokenRequest(recordTypes = setOf(type))) }

    override suspend fun changes(token: String): ChangesPage = call {
        val response = manager.client.getChanges(token)
        ChangesPage(
            upserted = response.changes.filterIsInstance<UpsertionChange>().map { it.record },
            deletedIds = response.changes.filterIsInstance<DeletionChange>().map { it.recordId },
            nextToken = response.nextChangesToken,
            hasMore = response.hasMore,
            tokenExpired = response.changesTokenExpired,
        )
    }

    private suspend inline fun <T> call(block: () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        if (e.isRateLimit()) throw RateLimitedException("Health Connect rate limit reached", e) else throw e
    }
}

/**
 * The client library reports Health Connect's rate limit as an IllegalStateException wrapping the
 * platform exception, so the cause chain is checked for the platform error code.
 */
internal fun Throwable.isRateLimit(): Boolean = generateSequence(this) { it.cause }.take(MAX_CAUSE_DEPTH).any { t ->
    (t is HealthConnectException && t.errorCode == HealthConnectException.ERROR_RATE_LIMIT_EXCEEDED) ||
        t.message?.contains("quota exceeded", ignoreCase = true) == true
}

private const val MAX_CAUSE_DEPTH = 8
