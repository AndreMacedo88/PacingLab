package app.pacinglab.feature.settings.datasources

import app.pacinglab.core.healthconnect.probe.ProbeReport
import java.time.Instant

/** The parts of a [ProbeReport] worth showing on screen. The full report goes to the file. */
data class ProbeHighlights(val types: List<TypeRow>, val sessions: List<SessionRow>, val errorCount: Int) {
    /**
     * @property records null when the type could not be read (no access or an error).
     */
    data class TypeRow(
        val name: String,
        val granted: Boolean,
        val failed: Boolean,
        val records: Int?,
        val earliest: Instant?,
        val mainOrigin: String?,
    )

    /**
     * @property heartRateGapSeconds median gap between heart rate samples, null without heart rate.
     * @property routePoints null when no route is available; see [routeStatus].
     */
    data class SessionRow(
        val start: Instant,
        val typeName: String,
        val durationMinutes: Int,
        val heartRateGapSeconds: Double?,
        val routeStatus: String,
        val routePoints: Int?,
        val laps: Int,
    )

    companion object {
        fun from(report: ProbeReport): ProbeHighlights = ProbeHighlights(
            types = report.recordTypes.map { type ->
                val failed = type.error != null
                TypeRow(
                    name = type.type,
                    granted = type.granted,
                    failed = failed,
                    records = if (type.granted && !failed) type.recordsLast30Days else null,
                    earliest = type.earliest?.let(Instant::parse),
                    mainOrigin = type.origins.maxByOrNull { it.value }?.key,
                )
            },
            sessions = report.sessions.map { session ->
                SessionRow(
                    start = Instant.parse(session.start),
                    typeName = session.exerciseTypeName,
                    durationMinutes = (session.durationS / 60).toInt(),
                    heartRateGapSeconds = session.heartRate
                        ?.spacing
                        ?.medianGapMs
                        ?.let { it / 1000.0 },
                    routeStatus = session.route.status,
                    routePoints = session.route.points.takeIf { session.route.status == "data" },
                    laps = session.laps,
                )
            },
            errorCount = report.errors.size +
                report.recordTypes.count { it.error != null } +
                report.sessions.sumOf { it.errors.size },
        )
    }
}
