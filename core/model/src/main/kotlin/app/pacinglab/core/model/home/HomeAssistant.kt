package app.pacinglab.core.model.home

import java.io.IOException
import java.time.Instant
import java.time.LocalDate

/** A Home Assistant server, as its configuration names it. */
data class HomeServer(val locationName: String?, val version: String?)

/**
 * A sensor in Home Assistant.
 *
 * @property deviceClass what it measures, such as "temperature", "humidity" or "carbon_dioxide".
 */
data class HomeSensor(val entityId: String, val name: String, val deviceClass: String?, val unit: String?, val state: String?)

/** One period of a sensor's statistics as Home Assistant's recorder keeps them. */
data class SensorStatistic(val start: Instant, val end: Instant, val mean: Double?, val min: Double?, val max: Double?)

/** How often statistics are wanted: five-minute ones are kept about ten days, hourly ones for good. */
enum class StatisticsPeriod(val apiName: String) {
    FIVE_MINUTES("5minute"),
    HOUR("hour"),
}

/** The bedroom during one night's sleep, averaged from falling asleep to the final awakening. */
data class NightClimate(
    val date: LocalDate,
    val temperature: Double? = null,
    val humidity: Double? = null,
    val co2: Double? = null,
    val co2Max: Double? = null,
) {
    val isEmpty: Boolean get() = temperature == null && humidity == null && co2 == null
}

/** Home Assistant refused the token. */
class HomeAssistantAuthException(message: String) : IOException(message)

/**
 * A Home Assistant server reached over HTTPS with a long-lived access token. Every call may throw
 * [HomeAssistantAuthException] when the token is refused and [IOException] when the server cannot be reached.
 */
interface HomeAssistantSource {
    suspend fun server(url: String, token: String): HomeServer

    /** The sensors whose values are numbers, such as thermometers. */
    suspend fun sensors(url: String, token: String): List<HomeSensor>

    /** Mean, minimum and maximum of each of [entityIds] in [from, to), temperatures in degrees Celsius. */
    suspend fun statistics(
        url: String,
        token: String,
        entityIds: List<String>,
        from: Instant,
        to: Instant,
        period: StatisticsPeriod,
    ): Map<String, List<SensorStatistic>>

    /** Sends [json] to the webhook [webhookId]; webhooks take no token. */
    suspend fun sendWebhook(url: String, webhookId: String, json: String)
}
