package app.pacinglab.core.homeassistant

import app.pacinglab.core.model.home.HomeSensor
import app.pacinglab.core.model.home.HomeServer
import app.pacinglab.core.model.home.SensorStatistic
import app.pacinglab.core.model.home.StatisticsPeriod
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/** Reads and writes the JSON of Home Assistant's REST and WebSocket APIs. */
internal object HomeAssistantJson {
    fun server(text: String): HomeServer {
        val json = JSONObject(text)
        return HomeServer(json.optString("location_name").ifBlank { null }, json.optString("version").ifBlank { null })
    }

    /** Sensors with a unit or a measurement state class: the ones whose values are numbers. */
    fun sensors(text: String): List<HomeSensor> {
        val states = JSONArray(text)
        return (0 until states.length()).mapNotNull { i ->
            val state = states.getJSONObject(i)
            val id = state.optString("entity_id")
            if (!id.startsWith("sensor.")) return@mapNotNull null
            val attributes = state.optJSONObject("attributes") ?: JSONObject()
            val unit = attributes.optString("unit_of_measurement").ifBlank { null }
            val measurement = attributes.optString("state_class") == "measurement"
            if (unit == null && !measurement) return@mapNotNull null
            HomeSensor(
                entityId = id,
                name = attributes.optString("friendly_name").ifBlank { id },
                deviceClass = attributes.optString("device_class").ifBlank { null },
                unit = unit,
                state = state.optString("state").ifBlank { null },
            )
        }.sortedBy { it.name.lowercase() }
    }

    fun authMessage(token: String): String = JSONObject().put("type", "auth").put("access_token", token).toString()

    fun statisticsCommand(id: Int, entityIds: List<String>, from: Instant, to: Instant, period: StatisticsPeriod): String = JSONObject()
        .put("id", id)
        .put("type", "recorder/statistics_during_period")
        .put("start_time", from.toString())
        .put("end_time", to.toString())
        .put("statistic_ids", JSONArray(entityIds))
        .put("period", period.apiName)
        .put("types", JSONArray(listOf("mean", "min", "max")))
        .put("units", JSONObject().put("temperature", "°C"))
        .toString()

    /** The result of a statistics command: each entity's periods, oldest first. */
    fun statistics(result: JSONObject): Map<String, List<SensorStatistic>> = result.keys().asSequence().associateWith { id ->
        val rows = result.getJSONArray(id)
        (0 until rows.length()).map { i ->
            val row = rows.getJSONObject(i)
            SensorStatistic(
                start = time(row.get("start")),
                end = time(row.get("end")),
                mean = row.number("mean"),
                min = row.number("min"),
                max = row.number("max"),
            )
        }.sortedBy { it.start }
    }

    /** Times come as epoch milliseconds in current versions and as ISO text in older ones. */
    private fun time(value: Any): Instant = when (value) {
        is Number -> Instant.ofEpochMilli(value.toLong())
        else -> Instant.parse(value.toString())
    }

    private fun JSONObject.number(name: String): Double? = if (isNull(name) ||
        !has(name)
    ) {
        null
    } else {
        optDouble(name).takeIf { it.isFinite() }
    }
}
