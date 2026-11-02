package app.pacinglab.core.data.home

/**
 * The Home Assistant configuration that turns PacingLab's webhook into sensors: one trigger-based template
 * sensor per value, which keeps its state through restarts and is recorded like any other sensor. Meant
 * for a file of its own in the `packages` folder.
 */
object HomeAssistantYaml {
    /** One value PacingLab sends: its key in the JSON, its sensor's name, unit and icon. */
    data class Value(val key: String, val name: String, val unit: String? = null, val icon: String, val numeric: Boolean = true)

    val VALUES = listOf(
        Value("readiness", "PacingLab readiness", icon = "mdi:gauge"),
        Value("readiness_level", "PacingLab readiness level", icon = "mdi:gauge", numeric = false),
        Value("sleep_score", "PacingLab sleep score", icon = "mdi:sleep"),
        Value("sleep_rating", "PacingLab sleep rating", icon = "mdi:sleep", numeric = false),
        Value("asleep_hours", "PacingLab time asleep", unit = "h", icon = "mdi:bed-clock"),
        Value("sleep_health", "PacingLab sleep health", icon = "mdi:bed"),
        Value("sleep_debt_hours", "PacingLab sleep debt", unit = "h", icon = "mdi:bed-clock"),
        Value("hrv", "PacingLab HRV", unit = "ms", icon = "mdi:heart-pulse"),
        Value("resting_hr", "PacingLab resting heart rate", unit = "bpm", icon = "mdi:heart"),
        Value("strain", "PacingLab daytime strain", icon = "mdi:heart-flash"),
        Value("strain_level", "PacingLab daytime strain level", icon = "mdi:heart-flash", numeric = false),
        Value("form", "PacingLab form", icon = "mdi:run-fast"),
        Value("fitness", "PacingLab fitness", icon = "mdi:run"),
        Value("fatigue", "PacingLab fatigue", icon = "mdi:run"),
        Value("vo2max", "PacingLab VO2max", unit = "ml/kg/min", icon = "mdi:lungs"),
        Value("training_status", "PacingLab training status", icon = "mdi:chart-line", numeric = false),
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
        appendLine("    sensor:")
        for (value in VALUES) {
            appendLine("      - name: \"${value.name}\"")
            appendLine("        unique_id: pacinglab_${value.key}")
            appendLine("        icon: ${value.icon}")
            appendLine("        state: \"{{ trigger.json.get('${value.key}') }}\"")
            appendLine("        availability: \"{{ trigger.json.get('${value.key}') is not none }}\"")
            if (value.unit != null) appendLine("        unit_of_measurement: \"${value.unit}\"")
            if (value.numeric) appendLine("        state_class: measurement")
        }
        appendLine("      - name: \"PacingLab updated\"")
        appendLine("        unique_id: pacinglab_updated")
        appendLine("        device_class: timestamp")
        appendLine("        state: \"{{ trigger.json.get('updated') }}\"")
    }
}
