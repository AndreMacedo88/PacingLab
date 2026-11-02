package app.pacinglab.core.homeassistant

import app.pacinglab.core.model.home.SensorStatistic
import app.pacinglab.core.model.home.StatisticsPeriod
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Config.NEWEST_SDK])
class HomeAssistantJsonTest {
    @Test
    fun `only numeric sensors are offered, sorted by name`() {
        val states = """
            [
              {"entity_id": "sensor.bedroom_temperature", "state": "19.6",
               "attributes": {"friendly_name": "Bedroom temperature", "device_class": "temperature",
                              "unit_of_measurement": "°C", "state_class": "measurement"}},
              {"entity_id": "light.kitchen", "state": "on", "attributes": {"friendly_name": "Kitchen"}},
              {"entity_id": "sensor.sun_next_dawn", "state": "2026-11-03T05:59:00+00:00",
               "attributes": {"friendly_name": "Next dawn", "device_class": "timestamp"}},
              {"entity_id": "sensor.office_co2", "state": "612",
               "attributes": {"friendly_name": "Air quality CO2", "device_class": "carbon_dioxide", "unit_of_measurement": "ppm"}}
            ]
        """.trimIndent()

        val sensors = HomeAssistantJson.sensors(states)

        assertThat(sensors.map { it.entityId }).containsExactly("sensor.office_co2", "sensor.bedroom_temperature")
        assertThat(sensors[1].deviceClass).isEqualTo("temperature")
        assertThat(sensors[1].unit).isEqualTo("°C")
    }

    @Test
    fun `statistics read epoch milliseconds and ISO times, and missing values`() {
        val result = JSONObject(
            """
            {"sensor.bedroom_temperature": [
               {"start": 1762124400000, "end": 1762124700000, "mean": 19.4, "min": 19.3, "max": 19.5},
               {"start": "2026-11-02T23:05:00+00:00", "end": "2026-11-02T23:10:00+00:00", "mean": null, "min": null, "max": null}
            ]}
            """.trimIndent(),
        )

        val statistics = HomeAssistantJson.statistics(result)

        assertThat(statistics.getValue("sensor.bedroom_temperature")).containsExactly(
            SensorStatistic(Instant.ofEpochMilli(1762124400000), Instant.ofEpochMilli(1762124700000), 19.4, 19.3, 19.5),
            SensorStatistic(Instant.parse("2026-11-02T23:05:00Z"), Instant.parse("2026-11-02T23:10:00Z"), null, null, null),
        )
    }

    @Test
    fun `the statistics command asks for means in degrees Celsius`() {
        val command = JSONObject(
            HomeAssistantJson.statisticsCommand(
                1,
                listOf("sensor.bedroom_temperature"),
                Instant.parse("2026-11-01T22:00:00Z"),
                Instant.parse("2026-11-02T07:00:00Z"),
                StatisticsPeriod.FIVE_MINUTES,
            ),
        )

        assertThat(command.getString("type")).isEqualTo("recorder/statistics_during_period")
        assertThat(command.getString("period")).isEqualTo("5minute")
        assertThat(command.getJSONObject("units").getString("temperature")).isEqualTo("°C")
        assertThat(command.getString("start_time")).isEqualTo("2026-11-01T22:00:00Z")
    }
}
