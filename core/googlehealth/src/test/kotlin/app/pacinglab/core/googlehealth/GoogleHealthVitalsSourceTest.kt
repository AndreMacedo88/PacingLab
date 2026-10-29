package app.pacinglab.core.googlehealth

import androidx.test.core.app.ApplicationProvider
import app.pacinglab.core.model.DailyVitals
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Config.NEWEST_SDK])
class GoogleHealthVitalsSourceTest {
    /** Serves made-up pages in the shape of the API's responses, two days per page, newest first. */
    private class FakeApi(private val pages: Map<String, List<String>>) : GoogleHealthApi {
        val requested = mutableListOf<String>()

        override suspend fun dataPoints(dataType: String, token: String, pageToken: String?): String {
            requested += "$dataType ${pageToken ?: "first"}"
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
        ),
    )

    private val source = GoogleHealthVitalsSource(GoogleHealthAuthorization(ApplicationProvider.getApplicationContext()), api)

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
            DailyVitals(LocalDate.of(2026, 10, 28), hrvMs = 52.1, restingHr = 54.0),
        )
        // The page with the 25th already reaches past the 26th, so the third page is never asked for.
        assertThat(api.requested.filter { it.startsWith("daily-heart-rate-variability") })
            .isEqualTo(listOf("daily-heart-rate-variability first", "daily-heart-rate-variability 1"))
    }
}
