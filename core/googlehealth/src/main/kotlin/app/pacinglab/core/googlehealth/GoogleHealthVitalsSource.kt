package app.pacinglab.core.googlehealth

import app.pacinglab.core.model.DailyVitals
import app.pacinglab.core.model.source.SourceAuthorizationException
import app.pacinglab.core.model.source.VitalsSource
import java.time.LocalDate
import javax.inject.Inject

/**
 * Nightly values from the Google Health API: Fitbit's own daily HRV, SpO2, skin temperature,
 * breathing rate and resting heart rate. Each type is read newest first, page by page, until the
 * pages reach back past the date asked for.
 */
class GoogleHealthVitalsSource @Inject constructor(
    private val authorization: GoogleHealthAuthorization,
    private val api: GoogleHealthApi,
) : VitalsSource {
    override suspend fun readDailyVitals(since: LocalDate): List<DailyVitals> {
        val token = when (val access = authorization.authorize()) {
            is GoogleHealthAccess.Granted -> access.token
            is GoogleHealthAccess.NeedsConsent -> throw SourceAuthorizationException("Sign-in needed")
            is GoogleHealthAccess.Failed -> throw SourceAuthorizationException(access.reason)
        }
        return read(token, since)
    }

    internal suspend fun read(token: String, since: LocalDate): List<DailyVitals> {
        val days = DailyType.entries.flatMap { type ->
            val found = mutableListOf<DailyVitals>()
            var pageToken: String? = null
            for (page in 0 until MAX_PAGES) {
                val result = DailyPage.parse(api.dataPoints(type.id, token, pageToken), type)
                found += result.days.filter { it.date >= since }
                pageToken = result.next
                if (pageToken == null || result.days.any { it.date < since }) break
            }
            found
        }
        return days.groupBy { it.date }.map { (_, values) -> values.reduce { kept, other -> kept.orElse(other) } }.sortedBy { it.date }
    }

    private companion object {
        /** A safety stop; a year of one type fits in four pages. */
        const val MAX_PAGES = 20
    }
}
