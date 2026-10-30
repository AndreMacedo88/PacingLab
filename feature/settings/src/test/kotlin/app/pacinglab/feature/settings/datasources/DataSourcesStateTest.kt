package app.pacinglab.feature.settings.datasources

import app.pacinglab.core.data.sync.SyncInfo
import app.pacinglab.core.data.sync.SyncOutcome
import app.pacinglab.core.data.sync.SyncStatus
import app.pacinglab.core.healthconnect.HealthConnectAvailability
import app.pacinglab.core.healthconnect.HealthConnectFeatureSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class DataSourcesStateTest {
    private val requested = setOf("heart_rate", "sleep", "exercise", "routes")
    private val features = HealthConnectFeatureSupport(readHistory = true, readInBackground = true)

    private fun access(granted: Set<String>, availability: HealthConnectAvailability = HealthConnectAvailability.AVAILABLE) =
        DataSourcesUiState().withAccess(availability, requested, granted, features, routes = "routes")

    @Test
    fun `every permission granted`() {
        val state = access(requested)

        assertEquals(4, state.grantedCount)
        assertTrue(state.allGranted)
        assertFalse(state.onlyRoutesMissing)
        assertEquals(features, state.features)
    }

    @Test
    fun `only the route permission missing is told apart, since the dialog cannot grant it`() {
        assertTrue(access(requested - "routes").onlyRoutesMissing)
        assertFalse(access(requested - "routes" - "sleep").onlyRoutesMissing)
        assertEquals(2, access(requested - "routes" - "sleep").grantedCount)
    }

    @Test
    fun `without Health Connect nothing counts as granted`() {
        val state = access(requested, HealthConnectAvailability.UNAVAILABLE)

        assertEquals(HealthConnectAvailability.UNAVAILABLE, state.availability)
        assertEquals(0, state.requestedCount)
        assertFalse(state.allGranted)
    }

    @Test
    fun `the sync card shows the first problem of the last run and whether Health Connect asked for a pause`() {
        val info = SyncInfo(lastSuccess = Instant.parse("2026-10-30T07:00:00Z"), importComplete = true, importedSince = null)
        val outcome =
            SyncOutcome(savedWorkouts = 2, deletedWorkouts = 0, importComplete = true, rateLimited = true, errors = listOf("a", "b"))

        val card = SyncCardState.of(SyncStatus.Idle, info, outcome, workoutCount = 120)

        assertEquals(SyncCardState(SyncStatus.Idle, info.lastSuccess, true, 120, problem = "a", paused = true), card)
        assertEquals(SyncCardState(SyncStatus.Idle, info.lastSuccess, true, 120), SyncCardState.of(SyncStatus.Idle, info, null, 120))
    }
}
