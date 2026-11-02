package app.pacinglab.core.data.home

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import app.pacinglab.core.data.repository.NightRepository
import app.pacinglab.core.data.testDataStore
import app.pacinglab.core.data.testPreferences
import app.pacinglab.core.database.PacingLabDatabase
import app.pacinglab.core.database.store.DailyVitalsStore
import app.pacinglab.core.database.store.MeasurementStore
import app.pacinglab.core.database.store.NightClimateStore
import app.pacinglab.core.model.SleepSession
import app.pacinglab.core.model.SleepStage
import app.pacinglab.core.model.SleepStageType
import app.pacinglab.core.model.home.HomeAssistantAuthException
import app.pacinglab.core.model.home.HomeAssistantSource
import app.pacinglab.core.model.home.HomeSensor
import app.pacinglab.core.model.home.HomeServer
import app.pacinglab.core.model.home.SensorStatistic
import app.pacinglab.core.model.home.StatisticsPeriod
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Config.NEWEST_SDK])
class HomeAssistantRepositoryTest {
    private val zone = ZoneOffset.ofHours(1)
    private val now = Instant.parse("2026-11-03T12:00:00Z")
    private val clock = Clock.fixed(now, zone)
    private val url = "https://homeassistant.example.ts.net"
    private val token = "made-up-token"

    /** Answers like a server with one bedroom thermometer at 19.5 °C. */
    private inner class FakeSource : HomeAssistantSource {
        var refuse = false
        var offline = false
        val sent = mutableListOf<Pair<String, String>>()
        val asked = mutableListOf<StatisticsPeriod>()

        private fun guard(given: String) {
            if (offline) throw IOException("No route")
            if (refuse || given != token) throw HomeAssistantAuthException("refused")
        }

        override suspend fun server(url: String, token: String): HomeServer {
            guard(token)
            return HomeServer("Home", "2026.11.0")
        }

        override suspend fun sensors(url: String, token: String): List<HomeSensor> {
            guard(token)
            return listOf(HomeSensor("sensor.bedroom_temperature", "Bedroom temperature", "temperature", "°C", "19.5"))
        }

        override suspend fun statistics(
            url: String,
            token: String,
            entityIds: List<String>,
            from: Instant,
            to: Instant,
            period: StatisticsPeriod,
        ): Map<String, List<SensorStatistic>> {
            guard(token)
            asked += period
            val step = if (period == StatisticsPeriod.HOUR) Duration.ofHours(1) else Duration.ofMinutes(5)
            val periods = generateSequence(from.minus(step)) { it.plus(step) }.takeWhile { it < to }
                .map { SensorStatistic(it, it.plus(step), 19.5, 19.4, 19.6) }.toList()
            return entityIds.associateWith { periods }
        }

        override suspend fun sendWebhook(url: String, webhookId: String, json: String) {
            if (offline) throw IOException("No route")
            sent += webhookId to json
        }
    }

    /** Stores secrets as they are; the Keystore is not available on the JVM. */
    private val box = object : SecretBox {
        override fun seal(plain: String) = "sealed:$plain"

        override fun open(sealed: String) = sealed.removePrefix("sealed:").takeIf { sealed.startsWith("sealed:") }
    }

    private val source = FakeSource()
    private lateinit var database: PacingLabDatabase
    private lateinit var measurements: MeasurementStore
    private lateinit var climate: NightClimateStore
    private lateinit var repository: HomeAssistantRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), PacingLabDatabase::class.java)
            .setDriver(AndroidSQLiteDriver())
            .build()
        measurements = MeasurementStore(database)
        climate = NightClimateStore(database)
        val nights = NightRepository(measurements, DailyVitalsStore(database), testPreferences(), clock)
        repository = HomeAssistantRepository(source, testDataStore(), box, climate, nights, { """{"readiness": 72}""" }, clock)
    }

    @After
    fun tearDown() = database.close()

    /** A night of seven and a half hours asleep ending on the morning [daysAgo] days before now. */
    private suspend fun sleep(daysAgo: Long) {
        val start = now.minus(Duration.ofDays(daysAgo)).minus(Duration.ofHours(13))
        val end = start.plus(Duration.ofMinutes(450))
        val stages = listOf(SleepStage(start, end, SleepStageType.LIGHT))
        measurements.saveSleep(listOf(SleepSession("s$daysAgo", start, end, zone, "com.fitbit.FitbitMobile", stages)))
    }

    @Test
    fun `only https addresses and accepted tokens connect`() = runTest {
        assertThat(repository.connect("http://192.168.0.10", token)).isEqualTo(ConnectResult.NotHttps)
        assertThat(repository.connect(url, "wrong")).isEqualTo(ConnectResult.TokenRejected)
        assertThat(repository.status.first().connected).isFalse()

        assertThat(repository.connect("$url/", " $token ")).isEqualTo(ConnectResult.Connected)

        val status = repository.status.first()
        assertThat(status.connected).isTrue()
        assertThat(status.url).isEqualTo(url)
        assertThat(status.serverName).isEqualTo("Home")
        assertThat(repository.yaml()!!).contains("local_only: false")
    }

    @Test
    fun `nights take the chosen thermometer's mean, recent ones from five-minute statistics`() = runTest {
        sleep(1)
        sleep(20)
        repository.connect(url, token)
        repository.setSensor(ClimateRole.TEMPERATURE, repository.sensors().single())

        assertThat(repository.sync()).isNull()

        val stored = climate.observe().first()
        assertThat(stored.size).isEqualTo(2)
        assertThat(stored.values.first().temperature!!).isCloseTo(19.5, 1e-9)
        assertThat(source.asked.toSet()).isEqualTo(setOf(StatisticsPeriod.FIVE_MINUTES, StatisticsPeriod.HOUR))
        assertThat(repository.status.first().nights).isEqualTo(2)
        // Nothing more to read: both nights ended long before they were read.
        source.asked.clear()
        repository.sync()
        assertThat(source.asked.isEmpty()).isTrue()
    }

    @Test
    fun `values are sent only when the athlete turned it on`() = runTest {
        repository.connect(url, token)
        repository.sync()
        assertThat(source.sent.isEmpty()).isTrue()

        repository.setSending(true)
        repository.sync()

        assertThat(source.sent.single().second).isEqualTo("""{"readiness": 72}""")
        assertThat(source.sent.single().first.startsWith("pacinglab_")).isTrue()
        assertThat(repository.status.first().lastSent).isEqualTo(now)
    }

    @Test
    fun `problems are recorded and cleared, and disconnecting forgets everything`() = runTest {
        repository.connect(url, token)
        // With nothing chosen and nothing to send, there is nothing to fail.
        source.offline = true
        assertThat(repository.sync()).isNull()
        repository.setSending(true)
        sleep(1)
        source.offline = false
        repository.setSensor(ClimateRole.TEMPERATURE, repository.sensors().single())
        source.offline = true
        assertThat(repository.sync()).isEqualTo(HomeAssistantProblem.UNREACHABLE)
        source.offline = false
        source.refuse = true
        assertThat(repository.sync()).isEqualTo(HomeAssistantProblem.TOKEN_REJECTED)
        assertThat(repository.status.first().problem).isEqualTo(HomeAssistantProblem.TOKEN_REJECTED)
        source.refuse = false
        assertThat(repository.sync()).isNull()
        assertThat(repository.status.first().problem).isNull()

        repository.disconnect()

        assertThat(repository.status.first()).isEqualTo(HomeAssistantStatus())
        assertThat(repository.yaml()).isNull()
    }

    @Test
    fun `the configuration names the webhook and declares every value`() {
        val yaml = HomeAssistantYaml.of("pacinglab_0123")

        assertThat(yaml).contains("webhook_id: \"pacinglab_0123\"")
        for (value in HomeAssistantYaml.VALUES) assertThat(yaml).contains("unique_id: pacinglab_${value.key}")
        assertThat(yaml).contains("unit_of_measurement: \"ms\"")
    }
}
