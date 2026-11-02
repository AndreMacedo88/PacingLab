package app.pacinglab.core.data.home

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.pacinglab.core.analytics.home.NightClimates
import app.pacinglab.core.data.repository.NightRepository
import app.pacinglab.core.database.store.NightClimateStore
import app.pacinglab.core.model.home.HomeAssistantAuthException
import app.pacinglab.core.model.home.HomeAssistantSource
import app.pacinglab.core.model.home.HomeSensor
import app.pacinglab.core.model.home.StatisticsPeriod
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONException
import java.io.IOException
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton

/** The settings file of the Home Assistant link, kept apart from the preferences that backups copy. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class HomeAssistantPreferences

/** What a bedroom sensor measures, by Home Assistant's device class. */
enum class ClimateRole(val deviceClass: String) {
    TEMPERATURE("temperature"),
    HUMIDITY("humidity"),
    CO2("carbon_dioxide"),
}

enum class HomeAssistantProblem {
    /** Home Assistant refused the token: it was deleted there, or the user lost its rights. */
    TOKEN_REJECTED,

    /** The token cannot be read on this device any more, for instance after a restore: connect again. */
    TOKEN_LOST,

    /** No connection, for instance with Tailscale off; the next sync tries again. */
    UNREACHABLE,
}

/**
 * @property sensors the chosen sensor of each role, by entity id; [sensorNames] their names.
 * @property nights nights with at least one bedroom value.
 */
data class HomeAssistantStatus(
    val connected: Boolean = false,
    val url: String? = null,
    val serverName: String? = null,
    val sensors: Map<ClimateRole, String> = emptyMap(),
    val sensorNames: Map<ClimateRole, String> = emptyMap(),
    val sending: Boolean = false,
    val lastRead: Instant? = null,
    val lastSent: Instant? = null,
    val problem: HomeAssistantProblem? = null,
    val nights: Int = 0,
)

sealed interface ConnectResult {
    data object Connected : ConnectResult

    /** Only https addresses are accepted: the token must not cross the network in the clear. */
    data object NotHttps : ConnectResult

    data object TokenRejected : ConnectResult

    data class Unreachable(val reason: String?) : ConnectResult
}

/**
 * The athlete's own Home Assistant server, reached over HTTPS with a long-lived access token. After each
 * sync it reads the chosen bedroom sensors' statistics for the nights of the last [HISTORY_DAYS] days
 * ([NightClimates]) and, when the athlete turned it on, sends PacingLab's values to a webhook
 * ([HomeAssistantPayload], [HomeAssistantYaml]). The token is sealed with a Keystore key ([SecretBox]); the
 * settings live in a file of their own, which backups leave out.
 */
@Singleton
class HomeAssistantRepository @Inject constructor(
    private val source: HomeAssistantSource,
    @param:HomeAssistantPreferences private val dataStore: DataStore<Preferences>,
    private val secrets: SecretBox,
    private val climate: NightClimateStore,
    private val nights: NightRepository,
    private val payload: HomeAssistantValues,
    private val clock: Clock,
) {
    private val mutex = Mutex()

    val status: Flow<HomeAssistantStatus> = combine(dataStore.data, climate.observe()) { prefs, stored ->
        HomeAssistantStatus(
            connected = prefs[URL] != null && prefs[TOKEN] != null,
            url = prefs[URL],
            serverName = prefs[SERVER_NAME],
            sensors = ClimateRole.entries.mapNotNull { role -> prefs[sensorKey(role)]?.let { role to it } }.toMap(),
            sensorNames = ClimateRole.entries.mapNotNull { role -> prefs[sensorNameKey(role)]?.let { role to it } }.toMap(),
            sending = prefs[SENDING] == true,
            lastRead = prefs[LAST_READ]?.let(Instant::ofEpochMilli),
            lastSent = prefs[LAST_SENT]?.let(Instant::ofEpochMilli),
            problem = prefs[PROBLEM]?.let { name -> HomeAssistantProblem.entries.firstOrNull { it.name == name } },
            nights = stored.size,
        )
    }

    /** Checks [url] and [token] with the server and keeps them when it answers. */
    suspend fun connect(url: String, token: String): ConnectResult {
        val address = url.trim().trimEnd('/')
        if (!address.startsWith("https://", ignoreCase = true)) return ConnectResult.NotHttps
        val server = try {
            source.server(address, token.trim())
        } catch (_: HomeAssistantAuthException) {
            return ConnectResult.TokenRejected
        } catch (e: IOException) {
            return ConnectResult.Unreachable(e.message)
        } catch (e: JSONException) {
            return ConnectResult.Unreachable(e.message)
        }
        dataStore.edit {
            it[URL] = address
            it[TOKEN] = secrets.seal(token.trim())
            server.locationName?.let { name -> it[SERVER_NAME] = name } ?: it.remove(SERVER_NAME)
            if (it[WEBHOOK] == null) it[WEBHOOK] = newWebhookId()
            it.remove(PROBLEM)
        }
        return ConnectResult.Connected
    }

    /** Forgets the server, the token and the bedroom values read from it. */
    suspend fun disconnect() = mutex.withLock {
        dataStore.edit { it.clear() }
        climate.clear()
    }

    /** The server's numeric sensors, for choosing the bedroom's; throws when it cannot be reached. */
    suspend fun sensors(): List<HomeSensor> {
        val (url, token) = credentials() ?: throw HomeAssistantAuthException("Not connected")
        return source.sensors(url, token)
    }

    /** Chooses [sensor] for [role], or none; the bedroom values are read again with the new choice. */
    suspend fun setSensor(role: ClimateRole, sensor: HomeSensor?) = mutex.withLock {
        dataStore.edit {
            if (sensor == null) {
                it.remove(sensorKey(role))
                it.remove(sensorNameKey(role))
            } else {
                it[sensorKey(role)] = sensor.entityId
                it[sensorNameKey(role)] = sensor.name
            }
        }
        climate.clear()
    }

    suspend fun setSending(sending: Boolean) {
        dataStore.edit { it[SENDING] = sending }
    }

    /** The configuration to paste into Home Assistant; null while not connected. */
    suspend fun yaml(): String? = dataStore.data.first()[WEBHOOK]?.let(HomeAssistantYaml::of)

    /** Reads the bedroom and sends PacingLab's values when connected; never throws. */
    suspend fun sync(): HomeAssistantProblem? = mutex.withLock {
        val prefs = dataStore.data.first()
        if (prefs[URL] == null || prefs[TOKEN] == null) return@withLock null
        val credentials = credentials() ?: return@withLock record(HomeAssistantProblem.TOKEN_LOST, read = false, sent = false)
        val (url, token) = credentials
        try {
            val read = readBedroom(url, token, prefs)
            val sent = prefs[SENDING] == true && prefs[WEBHOOK]?.let { webhook ->
                source.sendWebhook(url, webhook, payload.build())
                true
            } == true
            record(null, read, sent)
        } catch (_: HomeAssistantAuthException) {
            record(HomeAssistantProblem.TOKEN_REJECTED, read = false, sent = false)
        } catch (_: IOException) {
            record(HomeAssistantProblem.UNREACHABLE, read = false, sent = false)
        } catch (_: JSONException) {
            record(HomeAssistantProblem.UNREACHABLE, read = false, sent = false)
        }
    }

    /**
     * Reads the nights that have no bedroom values yet, or were read before their statistics were complete:
     * five-minute statistics for the last [FIVE_MINUTE_DAYS] days, hourly ones before. True when it read.
     */
    private suspend fun readBedroom(url: String, token: String, prefs: Preferences): Boolean {
        val roles = ClimateRole.entries.mapNotNull { role -> prefs[sensorKey(role)]?.let { role to it } }.toMap()
        if (roles.isEmpty()) return false
        val now = clock.instant()
        val today = LocalDate.now(clock)
        val fetched = climate.fetched()
        val wanted = nights.observeNights().first().filter { night ->
            night.date > today.minusDays(HISTORY_DAYS) &&
                fetched[night.date]?.let { it < night.end.plus(COMPLETE_AFTER) } != false
        }
        if (wanted.isEmpty()) return true
        val recent = now.minus(Duration.ofDays(FIVE_MINUTE_DAYS))
        for ((period, group) in wanted.groupBy { if (it.end > recent) StatisticsPeriod.FIVE_MINUTES else StatisticsPeriod.HOUR }) {
            val statistics = source.statistics(
                url,
                token,
                roles.values.distinct(),
                group.minOf {
                    it.start
                },
                group.maxOf { it.end },
                period,
            )
            climate.save(
                group.map { night ->
                    NightClimates.of(
                        night.date,
                        night.start,
                        night.end,
                        temperature = roles[ClimateRole.TEMPERATURE]?.let { statistics[it].orEmpty() },
                        humidity = roles[ClimateRole.HUMIDITY]?.let { statistics[it].orEmpty() },
                        co2 = roles[ClimateRole.CO2]?.let { statistics[it].orEmpty() },
                    )
                },
                now,
            )
        }
        return true
    }

    private suspend fun record(problem: HomeAssistantProblem?, read: Boolean, sent: Boolean): HomeAssistantProblem? {
        dataStore.edit {
            if (problem == null) it.remove(PROBLEM) else it[PROBLEM] = problem.name
            if (read) it[LAST_READ] = clock.millis()
            if (sent) it[LAST_SENT] = clock.millis()
        }
        return problem
    }

    private suspend fun credentials(): Pair<String, String>? {
        val prefs = dataStore.data.first()
        val url = prefs[URL] ?: return null
        val token = prefs[TOKEN]?.let(secrets::open) ?: return null
        return url to token
    }

    private fun newWebhookId(): String {
        val bytes = ByteArray(WEBHOOK_BYTES).also { SecureRandom().nextBytes(it) }
        return "pacinglab_" + bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        /** Nights read back; Home Assistant keeps hourly statistics for good, so a late connection still fills them. */
        const val HISTORY_DAYS = 120L

        /** Home Assistant keeps five-minute statistics for ten days by default. */
        const val FIVE_MINUTE_DAYS = 9L

        /** A night read sooner than this after it ended is read again: its last statistics were still to come. */
        val COMPLETE_AFTER: Duration = Duration.ofHours(2)

        private const val WEBHOOK_BYTES = 16
        private val URL = stringPreferencesKey("url")
        private val TOKEN = stringPreferencesKey("token")
        private val SERVER_NAME = stringPreferencesKey("server_name")
        private val WEBHOOK = stringPreferencesKey("webhook_id")
        private val SENDING = booleanPreferencesKey("sending")
        private val LAST_READ = longPreferencesKey("last_read")
        private val LAST_SENT = longPreferencesKey("last_sent")
        private val PROBLEM = stringPreferencesKey("problem")

        private fun sensorKey(role: ClimateRole) = stringPreferencesKey("sensor_${role.name.lowercase()}")

        private fun sensorNameKey(role: ClimateRole) = stringPreferencesKey("sensor_name_${role.name.lowercase()}")
    }
}
