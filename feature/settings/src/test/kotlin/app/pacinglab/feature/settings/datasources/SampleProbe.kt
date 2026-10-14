package app.pacinglab.feature.settings.datasources

import app.pacinglab.core.healthconnect.HealthConnectAvailability
import app.pacinglab.core.healthconnect.HealthConnectFeatureSupport
import java.io.File
import java.time.Instant

/** Realistic fixed data for screenshot tests. */
internal object SampleProbe {
    private const val ORIGIN = "com.google.android.apps.fitbit"

    private fun readable(name: String, records: Int, earliest: String) = ProbeHighlights.TypeRow(
        name = name,
        granted = true,
        failed = false,
        records = records,
        earliest = Instant.parse(earliest),
        mainOrigin = ORIGIN,
    )

    val highlights = ProbeHighlights(
        types = listOf(
            readable("ExerciseSession", 18, "2023-04-02T06:12:00Z"),
            readable("HeartRate", 41_230, "2023-04-02T00:00:00Z"),
            readable("HeartRateVariabilityRmssd", 29, "2023-04-03T03:10:00Z"),
            ProbeHighlights.TypeRow("Speed", true, false, 0, null, null),
            ProbeHighlights.TypeRow("Power", false, false, null, null, null),
            ProbeHighlights.TypeRow("OxygenSaturation", true, true, null, null, null),
        ),
        sessions = listOf(
            session("2026-10-13T16:05:00Z", "running", 52, heartRateGap = 1.0, route = "data", routePoints = 3_121),
            session("2026-10-11T07:30:00Z", "high_intensity_interval_training", 41, heartRateGap = 5.2, route = "no_data"),
            session("2026-10-09T17:45:00Z", "weightlifting", 63, heartRateGap = null, route = "consent_required"),
        ),
        errorCount = 1,
    )

    private fun session(start: String, type: String, minutes: Int, heartRateGap: Double?, route: String, routePoints: Int? = null) =
        ProbeHighlights.SessionRow(Instant.parse(start), type, minutes, heartRateGap, route, routePoints, laps = 0)

    val available = DataSourcesUiState(
        availability = HealthConnectAvailability.AVAILABLE,
        grantedCount = 19,
        requestedCount = 23,
        features = HealthConnectFeatureSupport(readHistory = true, readInBackground = true),
    )

    val finished = available.copy(
        grantedCount = 23,
        sync = SyncCardState(lastSuccess = Instant.parse("2026-10-14T13:05:00Z"), importComplete = true, workoutCount = 315),
        probe = ProbeState.Finished(highlights, File("report.json")),
        export = ExportState.Finished(8, File("raw.json")),
    )
}
