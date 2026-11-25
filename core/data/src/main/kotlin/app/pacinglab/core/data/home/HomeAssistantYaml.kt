package app.pacinglab.core.data.home

/**
 * The Home Assistant configuration that turns PacingLab's webhook into sensors: one trigger-based template
 * sensor per value, which keeps its state through restarts and is recorded like any other sensor. Meant
 * for a file of its own in the `packages` folder.
 *
 * A second trigger at midnight clears the values of the day ([Value.day]: readiness, last night, vitals,
 * alertness) until the app sends the new night, so that Home Assistant's daily statistics hold only the
 * values the app shows for that day; every other sensor keeps its state through it.
 */
object HomeAssistantYaml {
    /**
     * One value PacingLab sends: its key in the JSON ([HomeAssistantReport]), its sensor's name, unit and icon.
     *
     * @property numeric recorded with long-term statistics (state_class measurement).
     * @property day belongs to one day and clears at midnight.
     * @property attributes each attribute's name and the JSON key it comes from.
     */
    data class Value(
        val key: String,
        val name: String,
        val unit: String? = null,
        val icon: String,
        val numeric: Boolean = true,
        val day: Boolean = false,
        val deviceClass: String? = null,
        val attributes: Map<String, String> = emptyMap(),
    )

    val VALUES = listOf(
        Value("readiness", "PacingLab readiness", icon = "mdi:gauge", day = true),
        Value("readiness_level", "PacingLab readiness level", icon = "mdi:gauge", numeric = false, day = true),
        Value("sleep_score", "PacingLab sleep score", icon = "mdi:sleep", day = true),
        Value("sleep_rating", "PacingLab sleep rating", icon = "mdi:sleep", numeric = false, day = true),
        Value(
            "asleep_hours",
            "PacingLab time asleep",
            unit = "h",
            icon = "mdi:bed-clock",
            day = true,
            attributes = mapOf("minutes" to "asleep_minutes"),
        ),
        Value("sleep_health", "PacingLab sleep health", icon = "mdi:bed", day = true),
        Value("sleep_debt_hours", "PacingLab sleep debt", unit = "h", icon = "mdi:bed-clock", day = true),
        Value("sleep_need_hours", "PacingLab sleep need", unit = "h", icon = "mdi:bed-clock"),
        Value("hrv", "PacingLab HRV", unit = "ms", icon = "mdi:heart-pulse", day = true),
        Value("resting_hr", "PacingLab resting heart rate", unit = "bpm", icon = "mdi:heart", day = true),
        Value(
            "vitals_in_range",
            "PacingLab vitals in range",
            icon = "mdi:stethoscope",
            numeric = false,
            day = true,
            attributes = mapOf("measured" to "vitals_judged", "outside" to "vitals_outside"),
        ),
        Value("alertness", "PacingLab alertness", unit = "%", icon = "mdi:brain", day = true),
        Value("alertness_level", "PacingLab alertness level", icon = "mdi:brain", numeric = false, day = true),
        Value("sharpest_from", "PacingLab sharpest from", icon = "mdi:clock-start", numeric = false, day = true, deviceClass = "timestamp"),
        Value("sharpest_until", "PacingLab sharpest until", icon = "mdi:clock-end", numeric = false, day = true, deviceClass = "timestamp"),
        Value("strain", "PacingLab daytime strain", icon = "mdi:heart-flash"),
        Value("strain_level", "PacingLab daytime strain level", icon = "mdi:heart-flash", numeric = false),
        Value("form", "PacingLab form", icon = "mdi:run-fast"),
        Value("fitness", "PacingLab fitness", icon = "mdi:run"),
        Value("fatigue", "PacingLab fatigue", icon = "mdi:run"),
        Value(
            "week_load",
            "PacingLab week load",
            icon = "mdi:chart-bar",
            attributes = mapOf("band_low" to "week_load_low", "band_high" to "week_load_high"),
        ),
        Value(
            "week_running_km",
            "PacingLab week running",
            unit = "km",
            icon = "mdi:shoe-sneaker",
            attributes = mapOf("tolerance_km" to "week_running_tolerance_km"),
        ),
        Value(
            "vo2max",
            "PacingLab VO2max",
            unit = "ml/kg/min",
            icon = "mdi:lungs",
            attributes = mapOf("change_4_weeks" to "vo2max_change"),
        ),
        Value("race_5k", "PacingLab 5 km prediction", icon = "mdi:timer-outline", numeric = false),
        Value("training_status", "PacingLab training status", icon = "mdi:chart-line", numeric = false),
        Value(
            "last_workout",
            "PacingLab last workout",
            icon = "mdi:history",
            numeric = false,
            attributes = mapOf("load" to "last_workout_load", "minutes" to "last_workout_minutes", "km" to "last_workout_km"),
        ),
        Value("last_workout_end", "PacingLab last workout end", icon = "mdi:flag-checkered", numeric = false, deviceClass = "timestamp"),
        Value(
            "to_do",
            "PacingLab to do",
            icon = "mdi:checkbox-marked-circle-outline",
            numeric = false,
            attributes = mapOf("items" to "to_do_items"),
        ),
        Value("updated", "PacingLab updated", icon = "mdi:cellphone-arrow-down", numeric = false, deviceClass = "timestamp"),
    )

    /** Values shown as on or off. */
    val BINARY = listOf(
        Value("vitals_alert", "PacingLab vitals alert", icon = "mdi:alert-circle-outline", numeric = false, day = true),
    )

    fun of(webhookId: String): String = buildString {
        appendLine("# PacingLab values, sent by the app after each sync.")
        appendLine("# Save as /config/packages/pacinglab.yaml, then restart Home Assistant.")
        appendLine("template:")
        appendLine("  - triggers:")
        appendLine("      - trigger: webhook")
        appendLine("        webhook_id: \"$webhookId\"")
        appendLine("        allowed_methods: [POST]")
        appendLine("        # The phone reaches Home Assistant through Tailscale, whose addresses are not local to it.")
        appendLine("        local_only: false")
        appendLine("      # At midnight the day's values clear until the app sends the new night.")
        appendLine("      - trigger: time")
        appendLine("        at: \"00:00:00\"")
        appendLine("    sensor:")
        for (value in VALUES) entity(value)
        appendLine("    binary_sensor:")
        for (value in BINARY) entity(value)
    }

    private fun StringBuilder.entity(value: Value) {
        appendLine("      - name: \"${value.name}\"")
        appendLine("        unique_id: pacinglab_${value.key}")
        appendLine("        icon: ${value.icon}")
        appendLine("        state: \"{{ ${sent(value.key)} if $FROM_APP else ${if (value.day) "none" else "this.state"} }}\"")
        val kept = if (value.day) "false" else "this.state not in ['unknown', 'unavailable']"
        appendLine("        availability: \"{{ (${sent(value.key)} is not none) if $FROM_APP else $kept }}\"")
        if (value.unit != null) appendLine("        unit_of_measurement: \"${value.unit}\"")
        if (value.deviceClass != null) appendLine("        device_class: ${value.deviceClass}")
        if (value.numeric) appendLine("        state_class: measurement")
        if (value.attributes.isNotEmpty()) {
            appendLine("        attributes:")
            for ((name, key) in value.attributes) {
                val otherwise = if (value.day) "none" else "this.attributes.get('$name')"
                appendLine("          $name: \"{{ ${sent(key)} if $FROM_APP else $otherwise }}\"")
            }
        }
    }

    private fun sent(key: String) = "trigger.json.get('$key')"

    private const val FROM_APP = "trigger.platform == 'webhook'"
}
