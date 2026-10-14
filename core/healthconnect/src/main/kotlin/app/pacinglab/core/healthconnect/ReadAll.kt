package app.pacinglab.core.healthconnect

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlin.reflect.KClass

/** Largest page Health Connect accepts. Fewer round trips keep us well inside the rate limits. */
internal const val MAX_PAGE_SIZE = 5_000

/** Reads every record of [type] in [filter], page by page, handing each page to [onPage]. */
internal suspend fun <T : Record> HealthConnectClient.readAll(
    type: KClass<T>,
    filter: TimeRangeFilter,
    ascending: Boolean = true,
    onPage: (List<T>) -> Unit,
) {
    var pageToken: String? = null
    do {
        val response = readRecords(
            ReadRecordsRequest(
                recordType = type,
                timeRangeFilter = filter,
                ascendingOrder = ascending,
                pageSize = MAX_PAGE_SIZE,
                pageToken = pageToken,
            ),
        )
        onPage(response.records)
        pageToken = response.pageToken
    } while (pageToken != null)
}

/** Reads a single record at the start (ascending) or end (descending) of [filter]. */
internal suspend fun <T : Record> HealthConnectClient.readEdge(type: KClass<T>, filter: TimeRangeFilter, ascending: Boolean): T? =
    readRecords(
        ReadRecordsRequest(recordType = type, timeRangeFilter = filter, ascendingOrder = ascending, pageSize = 1),
    ).records.firstOrNull()
