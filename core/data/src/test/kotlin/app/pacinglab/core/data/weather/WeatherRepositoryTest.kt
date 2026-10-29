package app.pacinglab.core.data.weather

import app.pacinglab.core.model.Coordinates
import app.pacinglab.core.model.WeatherCondition
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isTrue
import assertk.assertions.prop
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Config.NEWEST_SDK])
class WeatherRepositoryTest {
    // Copenhagen City Hall.
    private val cityHall = Coordinates(55.6761, 12.5683)
    private val start = Instant.parse("2026-10-28T17:20:00Z")

    private class MutableClock(var now: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId?): Clock = this

        override fun instant(): Instant = now
    }

    private class Fakes(var place: Coordinates?, var permitted: Boolean = true) {
        val asked = mutableListOf<Coordinates>()
        var online = true
        var stored: String? = null

        val service = object : WeatherService {
            override suspend fun fetch(place: Coordinates): String {
                if (!online) throw IOException("offline")
                asked += place
                return RESPONSE
            }
        }
        val location = object : ApproximateLocation {
            override fun permitted() = permitted

            override suspend fun current() = place
        }
        val cache = object : WeatherCache {
            override suspend fun read() = stored

            override suspend fun write(text: String) {
                stored = text
            }
        }
    }

    @Test
    fun `the response is read into the current weather and the hours from now on`() {
        val forecast = OpenMeteo.parse(RESPONSE, cityHall, start)

        assertThat(forecast.offset).isEqualTo(ZoneOffset.ofHours(1))
        assertThat(forecast.now.temperature).isEqualTo(8.4)
        assertThat(forecast.now.condition).isEqualTo(WeatherCondition.PARTLY_CLOUDY)
        assertThat(forecast.now.daylight).isEqualTo(false)
        assertThat(forecast.now.precipitationChance).isEqualTo(null)
        assertThat(forecast.hours.map { it.time.toString() })
            .containsExactly("2026-10-28T17:00:00Z", "2026-10-28T18:00:00Z", "2026-10-28T19:00:00Z")
        assertThat(forecast.hours.map { it.precipitationChance }).containsExactly(5, 40, 70)
        assertThat(forecast.hours.last().condition).isEqualTo(WeatherCondition.RAIN)
        assertThat(forecast.todayLow).isEqualTo(4.1)
        assertThat(forecast.todayHigh).isEqualTo(10.2)
    }

    @Test
    fun `only the rounded position is sent, and a recent forecast is reused nearby`() = runTest {
        val fakes = Fakes(cityHall)
        val clock = MutableClock(start)
        val repository = WeatherRepository(fakes.service, fakes.location, fakes.cache, clock)

        repository.load()
        clock.now = start.plusSeconds(10 * 60)
        fakes.place = Coordinates(55.69, 12.60)
        repository.load()
        clock.now = start.plusSeconds(30 * 60)
        repository.load()

        assertThat(fakes.asked).containsExactly(Coordinates(55.68, 12.57), Coordinates(55.69, 12.6))
    }

    @Test
    fun `without a connection the kept forecast is shown as outdated`() = runTest {
        val fakes = Fakes(cityHall)
        val clock = MutableClock(start)
        WeatherRepository(fakes.service, fakes.location, fakes.cache, clock).load()

        fakes.online = false
        clock.now = start.plusSeconds(2 * 3600)
        // A new start of the app: only the cache file remains.
        val state = WeatherRepository(fakes.service, fakes.location, fakes.cache, clock).load()

        assertThat(state).isInstanceOf<WeatherState.Ready>().prop(WeatherState.Ready::outdated).isTrue()
        assertThat((state as WeatherState.Ready).forecast.hours.map { it.time.toString() })
            .containsExactly("2026-10-28T19:00:00Z")

        clock.now = start.plusSeconds(13 * 3600)
        assertThat(WeatherRepository(fakes.service, fakes.location, fakes.cache, clock).load()).isEqualTo(WeatherState.NoConnection)
    }

    @Test
    fun `nothing is asked for before location is allowed`() = runTest {
        val fakes = Fakes(cityHall, permitted = false)

        assertThat(WeatherRepository(fakes.service, fakes.location, fakes.cache, MutableClock(start)).load())
            .isEqualTo(WeatherState.NeedsLocation)
        assertThat(fakes.asked).isEqualTo(emptyList<Coordinates>())
    }

    private companion object {
        // 2026-10-28 17:00 to 19:00 UTC; an excerpt of an Open-Meteo response with timeformat=unixtime.
        const val RESPONSE = """
            {"latitude": 55.68, "longitude": 12.57, "utc_offset_seconds": 3600, "timezone": "Europe/Copenhagen",
             "current": {"time": 1793208000, "interval": 900, "temperature_2m": 8.4, "apparent_temperature": 5.1,
               "dew_point_2m": 3.2, "precipitation": 0.0, "weather_code": 2, "wind_speed_10m": 5.8,
               "wind_gusts_10m": 11.3, "is_day": 0},
             "hourly": {"time": [1793203200, 1793206800, 1793210400, 1793214000],
               "temperature_2m": [9.0, 8.5, 8.1, 7.6], "apparent_temperature": [6.0, 5.4, 5.0, 4.4],
               "dew_point_2m": [3.5, 3.3, 3.4, 4.0], "precipitation": [0.0, 0.0, 0.1, 0.8],
               "precipitation_probability": [0, 5, 40, 70], "weather_code": [3, 2, 3, 61],
               "wind_speed_10m": [6.1, 5.8, 6.4, 7.2], "wind_gusts_10m": [12.0, 11.3, 12.5, 14.1], "is_day": [0, 0, 0, 0]},
             "daily": {"time": [1793142000, 1793228400], "temperature_2m_max": [10.2, 9.1], "temperature_2m_min": [4.1, 5.0]}}
        """
    }
}
