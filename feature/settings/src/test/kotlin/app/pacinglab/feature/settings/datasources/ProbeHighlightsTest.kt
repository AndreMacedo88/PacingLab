package app.pacinglab.feature.settings.datasources

import app.pacinglab.core.healthconnect.probe.DeviceInfo
import app.pacinglab.core.healthconnect.probe.HealthConnectInfo
import app.pacinglab.core.healthconnect.probe.ProbeReport
import app.pacinglab.core.healthconnect.probe.RecordTypeSummary
import app.pacinglab.core.healthconnect.probe.RouteSummary
import app.pacinglab.core.healthconnect.probe.SeriesSummary
import app.pacinglab.core.healthconnect.probe.SessionSummary
import app.pacinglab.core.healthconnect.probe.SpacingStats
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.Test
import java.time.Instant

class ProbeHighlightsTest {
    @Test
    fun `record types keep counts only when readable`() {
        val highlights = ProbeHighlights.from(
            report(
                types = listOf(
                    RecordTypeSummary("HeartRate", granted = true, recordsLast30Days = 10, origins = mapOf("a" to 2, "b" to 8)),
                    RecordTypeSummary("Power", granted = false),
                    RecordTypeSummary("Speed", granted = true, error = "boom"),
                ),
            ),
        )

        assertThat(highlights.types.map { it.records }).containsExactly(10, null, null)
        assertThat(highlights.types[0].mainOrigin).isEqualTo("b")
        assertThat(highlights.errorCount).isEqualTo(1)
    }

    @Test
    fun `sessions expose heart rate spacing in seconds and route points only with data`() {
        val highlights = ProbeHighlights.from(
            report(
                sessions = listOf(
                    session(medianGapMs = 1_000, route = RouteSummary("data", points = 120)),
                    session(medianGapMs = null, route = RouteSummary("no_data")),
                ),
            ),
        )

        assertThat(highlights.sessions[0].heartRateGapSeconds).isEqualTo(1.0)
        assertThat(highlights.sessions[0].routePoints).isEqualTo(120)
        assertThat(highlights.sessions[1].heartRateGapSeconds).isNull()
        assertThat(highlights.sessions[1].routePoints).isNull()
    }

    private fun report(types: List<RecordTypeSummary> = emptyList(), sessions: List<SessionSummary> = emptyList()) = ProbeReport(
        generatedAt = "2026-10-14T10:00:00Z",
        probeDurationMs = 1,
        appVersion = "test",
        device = DeviceInfo("Samsung", "SM-S931B", 36, "16"),
        healthConnect = HealthConnectInfo("AVAILABLE", true, true, emptyList(), emptyList()),
        recordTypes = types,
        sessionTypeCounts = emptyMap(),
        sessions = sessions,
        allDayHeartRate = null,
        recentDailyValues = emptyMap(),
        recentSleep = emptyList(),
        errors = emptyList(),
    )

    private fun session(medianGapMs: Long?, route: RouteSummary): SessionSummary {
        val spacing = SpacingStats(
            samples = 100,
            duplicates = 0,
            medianGapMs = medianGapMs,
            p10GapMs = null,
            p90GapMs = null,
            maxGapMs = null,
            shareWithin1s = null,
            shareWithin2s = null,
            shareWithin5s = null,
            shareWithin15s = null,
        )
        return SessionSummary(
            id = "1",
            start = Instant.parse("2026-10-13T16:05:00Z").toString(),
            end = Instant.parse("2026-10-13T16:57:00Z").toString(),
            zoneOffset = "+02:00",
            durationS = 3_120,
            exerciseType = 56,
            exerciseTypeName = "running",
            title = null,
            origin = "com.google.android.apps.fitbit",
            device = null,
            recordingMethod = "active",
            laps = 0,
            segmentTypes = emptyMap(),
            route = route,
            heartRate = medianGapMs?.let { SeriesSummary(1, spacing, 100.0, 180.0, 150.0, 0, 0) },
            speed = null,
            stepsCadence = null,
            power = null,
            distance = null,
            elevationGained = null,
            steps = null,
            activeCalories = null,
            errors = emptyList(),
        )
    }
}
