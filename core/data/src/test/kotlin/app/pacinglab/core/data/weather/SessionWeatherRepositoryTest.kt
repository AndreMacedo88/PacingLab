package app.pacinglab.core.data.weather

import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import app.pacinglab.core.data.testPreferences
import app.pacinglab.core.database.PacingLabDatabase
import app.pacinglab.core.database.store.SessionWeatherStore
import app.pacinglab.core.model.Coordinates
import app.pacinglab.core.model.WeatherCondition
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Config.NEWEST_SDK])
class SessionWeatherRepositoryTest {
    private val database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PacingLabDatabase::class.java)
        .setDriver(AndroidSQLiteDriver())
        .build()

    // Copenhagen City Hall.
    private val cityHall = Coordinates(55.6761, 12.5683)

    // Two hours of made-up values at 16:00 and 17:00 UTC.
    private val hourly = """{"hourly":{"time":[1792512000,1792515600],"temperature_2m":[20.0,22.0],"relative_humidity_2m":[60,70],""" +
        """"dew_point_2m":[12.0,14.0],"apparent_temperature":[19.0,21.0],"wind_speed_10m":[3.0,5.0],"wind_gusts_10m":[6.0,9.0],""" +
        """"precipitation":[0.0,0.4],"weather_code":[1,61]}}"""

    private val asked = mutableListOf<Pair<Coordinates, Boolean>>()
    private var online = true
    private val service = object : PastWeatherService {
        override suspend fun fetch(place: Coordinates, from: LocalDate, to: LocalDate, recent: Boolean): String {
            if (!online) throw IOException("offline")
            asked += place to recent
            return hourly
        }
    }
    private val preferences = testPreferences()
    private val repository = SessionWeatherRepository(
        SessionWeatherStore(database),
        service,
        preferences,
        Clock.fixed(Instant.parse("2026-10-21T08:00:00Z"), ZoneOffset.UTC),
    )
    private val start = Instant.ofEpochSecond(1792512000).plusSeconds(15 * 60)
    private val end = start.plusSeconds(30 * 60)

    @After
    fun tearDown() = database.close()

    @Test
    fun `the weather at the middle of the session is interpolated and kept`() = runTest {
        val state = repository.weatherOf("hc:1", cityHall, start, end)

        val weather = (state as SessionWeatherState.Found).weather
        // 16:30 is halfway between the two hours.
        assertThat(weather.temperature).isCloseTo(21.0, 1e-9)
        assertThat(weather.humidity).isCloseTo(65.0, 1e-9)
        assertThat(weather.precipitation).isEqualTo(0.4)
        assertThat(weather.condition).isEqualTo(WeatherCondition.RAIN)
        // Rounded to two decimals before it leaves the phone; recent days use the forecast service.
        assertThat(asked).containsExactly(Coordinates(55.68, 12.57) to true)

        online = false
        assertThat(repository.weatherOf("hc:1", cityHall, start, end)).isEqualTo(state)
    }

    @Test
    fun `nothing is looked up without a place, while off, or when offline`() = runTest {
        assertThat(repository.weatherOf("hc:1", null, start, end)).isEqualTo(SessionWeatherState.NoPlace)

        online = false
        assertThat(repository.weatherOf("hc:1", cityHall, start, end)).isEqualTo(SessionWeatherState.Unreachable)

        preferences.setSessionWeather(false)
        online = true
        assertThat(repository.weatherOf("hc:1", cityHall, start, end)).isEqualTo(SessionWeatherState.Off)
        assertThat(asked).isEqualTo(emptyList<Pair<Coordinates, Boolean>>())
    }

    @Test
    fun `a session outside the hours is remembered as missing`() = runTest {
        val late = Instant.ofEpochSecond(1792515600).plusSeconds(3_600)

        assertThat(repository.weatherOf("hc:2", cityHall, late, late.plusSeconds(600))).isInstanceOf(SessionWeatherState.Missing::class)
        assertThat(repository.weatherOf("hc:2", cityHall, late, late.plusSeconds(600))).isEqualTo(SessionWeatherState.Missing)
        assertThat(asked.size).isEqualTo(1)
    }
}
