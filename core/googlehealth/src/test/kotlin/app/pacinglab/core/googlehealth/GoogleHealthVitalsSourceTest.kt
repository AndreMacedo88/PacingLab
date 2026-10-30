package app.pacinglab.core.googlehealth

import androidx.test.core.app.ApplicationProvider
import app.pacinglab.core.model.BriefAwakening
import app.pacinglab.core.model.DailyVitals
import app.pacinglab.core.model.source.SourceAuthorizationException
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Config.NEWEST_SDK])
class GoogleHealthVitalsSourceTest {
    /**
     * Serves made-up pages in the shape of the API's responses, two days per page, newest first. Types in
     * [refused] answer as without the scope they need.
     */
    private class FakeApi(private val pages: Map<String, List<String>>, private val refused: Set<String> = emptySet()) : GoogleHealthApi {
        val requested = mutableListOf<String>()

        override suspend fun dataPoints(dataType: String, token: String, pageToken: String?): String {
            requested += "$dataType ${pageToken ?: "first"}"
            if (dataType in refused) throw SourceAuthorizationException("Google Health API answered 403 for $dataType")
            val list = pages[dataType] ?: return "{}"
            val index = pageToken?.toInt() ?: 0
            val next = if (index + 1 < list.size) ""","nextPageToken":"${index + 1}"""" else ""
            return """{"dataPoints":[${list[index]}]$next}"""
        }
    }

    private fun date(day: Int) = """"date":{"year":2026,"month":10,"day":$day}"""

    private fun hrv(day: Int, ms: Double) = """{"dailyHeartRateVariability":{${date(day)},"averageHeartRateVariabilityMilliseconds":$ms}}"""

    private val api = FakeApi(
        mapOf(
            "daily-heart-rate-variability" to listOf(
                hrv(28, 52.1) + "," + hrv(27, 61.4),
                hrv(26, 66.0) + "," + hrv(25, 55.2),
                hrv(24, 59.5) + "," + hrv(23, 64.8),
            ),
            "daily-resting-heart-rate" to listOf(
                """{"dailyRestingHeartRate":{${date(
                    28,
                )},"beatsPerMinute":"54","dailyRestingHeartRateMetadata":{"calculationMethod":"WITH_SLEEP"}}},""" +
                    """{"dailyRestingHeartRate":{${date(
                        27,
                    )},"beatsPerMinute":"63","dailyRestingHeartRateMetadata":{"calculationMethod":"WITHOUT_SLEEP"}}}""",
            ),
            "daily-oxygen-saturation" to listOf(
                """{"dailyOxygenSaturation":{${date(
                    26,
                )},"averagePercentage":96.1,"lowerBoundPercentage":93.4,"upperBoundPercentage":98.0}}""",
            ),
            "daily-sleep-temperature-derivations" to listOf(
                """{"dailySleepTemperatureDerivations":{${date(27)},"nightlyTemperatureCelsius":33.1,"baselineTemperatureCelsius":33.5}}""",
            ),
            "daily-vo2-max" to listOf(
                """{"dailyVo2Max":{${date(28)},"vo2Max":51.4,"cardioFitnessLevel":"VERY_GOOD","vo2MaxCovariance":1.9}},""" +
                    """{"dailyVo2Max":{${date(20)},"vo2Max":50.6,"vo2MaxCovariance":1.5}}""",
            ),
        ),
    )

    private val clock = Clock.fixed(Instant.parse("2026-10-28T12:00:00Z"), ZoneId.of("Europe/Copenhagen"))

    private val source = GoogleHealthVitalsSource(GoogleHealthAuthorization(ApplicationProvider.getApplicationContext()), api, clock)

    @Test
    fun `each type is read back to the date asked for and the days are combined`() = runTest {
        val days = source.read("token", since = LocalDate.of(2026, 10, 26))

        assertThat(days).containsExactly(
            DailyVitals(
                LocalDate.of(2026, 10, 26),
                hrvMs = 66.0,
                oxygenSaturation = 96.1,
                oxygenSaturationLow = 93.4,
                oxygenSaturationHigh = 98.0,
            ),
            DailyVitals(LocalDate.of(2026, 10, 27), hrvMs = 61.4, skinTemperature = 33.1, skinTemperatureBaseline = 33.5),
            DailyVitals(LocalDate.of(2026, 10, 28), hrvMs = 52.1, restingHr = 54.0, vo2max = 51.4, vo2maxVariance = 1.9),
        )
        // The page with the 25th already reaches past the 26th, so the third page is never asked for.
        assertThat(api.requested.filter { it.startsWith("daily-heart-rate-variability") })
            .isEqualTo(listOf("daily-heart-rate-variability first", "daily-heart-rate-variability 1"))
    }

    @Test
    fun `VO2max is read back to its own date`() = runTest {
        val days = source.read("token", since = LocalDate.of(2026, 10, 28), vo2maxSince = LocalDate.of(2026, 10, 1))

        assertThat(days.mapNotNull { day -> day.vo2max?.let { day.date to it } })
            .containsExactly(LocalDate.of(2026, 10, 20) to 50.6, LocalDate.of(2026, 10, 28) to 51.4)
        assertThat(days.last().vo2maxVariance).isEqualTo(1.9)
    }

    @Test
    fun `without the activity scope the nightly values still come`() = runTest {
        val refusing = GoogleHealthVitalsSource(
            GoogleHealthAuthorization(ApplicationProvider.getApplicationContext()),
            FakeApi(mapOf("daily-heart-rate-variability" to listOf(hrv(28, 52.1))), refused = setOf("daily-vo2-max")),
            clock,
        )

        val days = refusing.read("token", since = LocalDate.of(2026, 10, 28))

        assertThat(days).containsExactly(DailyVitals(LocalDate.of(2026, 10, 28), hrvMs = 52.1))
    }

    @Test
    fun `brief awakenings come from each main sleep by the local morning it ended`() = runTest {
        fun sleep(start: String, end: String, main: Boolean, stages: Boolean, awakenings: List<Pair<String, String>>?) =
            """{"sleep":{"interval":{"startTime":"$start","startUtcOffset":"0s","endTime":"$end","endUtcOffset":"0s"},""" +
                (if (stages) """"stages":[{"startTime":"$start","endTime":"$end","type":"LIGHT"}],""" else """"stages":[],""") +
                """"metadata":{"processed":true,${if (main) "\"mainSleep\":true" else "\"nap\":true"}}""" +
                (
                    awakenings?.let { list ->
                        ""","shortAwakenings":[""" +
                            list.joinToString(",") { (from, to) -> """{"startTime":"$from","endTime":"$to","type":"LIGHT"}""" } + "]"
                    } ?: ""
                    ) + "}}"
        val sleeping = GoogleHealthVitalsSource(
            GoogleHealthAuthorization(ApplicationProvider.getApplicationContext()),
            FakeApi(
                mapOf(
                    "sleep" to listOf(
                        // Ends at 05:30 in Copenhagen on the 28th, although 04:30 UTC.
                        sleep(
                            "2026-10-27T22:00:00Z",
                            "2026-10-28T04:30:00Z",
                            main = true,
                            stages = true,
                            awakenings = listOf(
                                "2026-10-28T02:47:00Z" to "2026-10-28T02:48:00Z",
                                "2026-10-28T02:41:00Z" to "2026-10-28T02:43:00Z",
                            ),
                        ) + "," + sleep("2026-10-27T15:00:00Z", "2026-10-27T15:20:00Z", main = false, stages = true, awakenings = null),
                        sleep("2026-10-26T22:00:00Z", "2026-10-27T05:00:00Z", main = true, stages = true, awakenings = null) + "," +
                            sleep("2026-10-25T22:00:00Z", "2026-10-26T05:00:00Z", main = true, stages = false, awakenings = null),
                    ),
                ),
            ),
            clock,
        )

        val days = sleeping.read("token", since = LocalDate.of(2026, 10, 25))

        val awakening = BriefAwakening(Instant.parse("2026-10-28T02:41:00Z"), Instant.parse("2026-10-28T02:43:00Z"))
        assertThat(days).containsExactly(
            DailyVitals(LocalDate.of(2026, 10, 27), briefAwakenings = emptyList()),
            DailyVitals(
                LocalDate.of(2026, 10, 28),
                briefAwakenings = listOf(
                    awakening,
                    BriefAwakening(Instant.parse("2026-10-28T02:47:00Z"), Instant.parse("2026-10-28T02:48:00Z")),
                ),
            ),
        )
    }
}
