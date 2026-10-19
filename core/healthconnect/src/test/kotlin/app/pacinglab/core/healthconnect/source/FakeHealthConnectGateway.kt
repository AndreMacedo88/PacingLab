package app.pacinglab.core.healthconnect.source

import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import app.pacinglab.core.healthconnect.HealthConnectPermissions
import app.pacinglab.core.healthconnect.timingOf
import java.time.Instant
import kotlin.reflect.KClass

/**
 * In-memory stand-in for Health Connect.
 *
 * Time filtering mimics the strict case: a record is returned only if it *starts* inside the
 * requested window, so code under test has to pad its windows to catch records that begin earlier.
 * Pages are deliberately small to exercise paging. Changes follow the platform: upserted records
 * and deleted ids arrive as two lists, with no order between them.
 */
class FakeHealthConnectGateway(var granted: Set<String> = HealthConnectPermissions.all, private val pageSize: Int = 3) :
    HealthConnectGateway {
    private val records = mutableListOf<Record>()
    private val log = mutableListOf<Pair<KClass<out Record>, Any>>()

    /** Ids passed to [readOne], to check how many single reads happen. */
    val singleReads = mutableListOf<String>()

    fun insert(vararg added: Record) {
        for (record in added) {
            records.removeAll { it.metadata.id == record.metadata.id }
            records += record
            log += record::class to record
        }
    }

    fun delete(type: KClass<out Record>, id: String) {
        records.removeAll { it.metadata.id == id }
        log += type to id
    }

    override suspend fun grantedPermissions(): Set<String> = granted

    override suspend fun <T : Record> readPage(
        type: KClass<T>,
        from: Instant,
        to: Instant,
        origins: Set<String>,
        ascending: Boolean,
        pageSize: Int,
        pageToken: String?,
    ): RecordPage<T> {
        @Suppress("UNCHECKED_CAST")
        val matching = records
            .filter { type.isInstance(it) }
            .filter { origins.isEmpty() || it.metadata.dataOrigin.packageName in origins }
            .filter { timingOf(it).start.let { start -> start >= from && start < to } }
            .sortedBy { timingOf(it).start }
            .let { if (ascending) it else it.reversed() } as List<T>
        val size = minOf(pageSize, this.pageSize)
        val offset = pageToken?.toInt() ?: 0
        val page = matching.drop(offset).take(size)
        val next = (offset + size).takeIf { it < matching.size }?.toString()
        return RecordPage(page, next)
    }

    override suspend fun <T : Record> readOne(type: KClass<T>, id: String): T {
        singleReads += id
        @Suppress("UNCHECKED_CAST")
        return records.first { type.isInstance(it) && it.metadata.id == id } as T
    }

    override suspend fun changesToken(type: KClass<out Record>): String = "${type.qualifiedName}@${log.size}"

    override suspend fun changes(token: String): ChangesPage {
        val (typeName, position) = token.split("@").let { it[0] to it[1].toInt() }
        val relevant = log.drop(position).filter { it.first.qualifiedName == typeName }
        // Like the platform: upserts come as the record's current state, and only if it still exists.
        val upsertedIds = relevant.mapNotNull { (it.second as? Record)?.metadata?.id }.distinct()
        return ChangesPage(
            upserted = upsertedIds.mapNotNull { id -> records.firstOrNull { it.metadata.id == id } },
            deletedIds = relevant.mapNotNull { it.second as? String },
            nextToken = "$typeName@${log.size}",
            hasMore = false,
            tokenExpired = false,
        )
    }

    companion object {
        private val SENSE = Device(Device.TYPE_WATCH, "Fitbit", "Sense")

        /**
         * Metadata as Health Connect would return it for a record written by another app. Its full
         * constructor is internal to the client library, so it is called reflectively.
         */
        fun metadata(
            id: String,
            origin: String,
            lastModified: Instant = Instant.EPOCH,
            recordingMethod: Int = Metadata.RECORDING_METHOD_ACTIVELY_RECORDED,
        ): Metadata = Metadata::class.java
            .getConstructor(
                Int::class.javaPrimitiveType,
                String::class.java,
                DataOrigin::class.java,
                Instant::class.java,
                String::class.java,
                Long::class.javaPrimitiveType,
                Device::class.java,
            )
            .newInstance(recordingMethod, id, DataOrigin(origin), lastModified, null, 0L, SENSE)
    }
}
