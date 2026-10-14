package app.pacinglab.feature.settings.datasources

import app.pacinglab.core.healthconnect.HealthConnectAvailability
import app.pacinglab.core.healthconnect.HealthConnectFeatureSupport
import app.pacinglab.core.healthconnect.probe.ProbeProgress
import java.io.File

data class DataSourcesUiState(
    /** Null while availability is being checked. */
    val availability: HealthConnectAvailability? = null,
    val grantedCount: Int = 0,
    val requestedCount: Int = 0,
    /**
     * True when everything is granted except reading exercise routes. Health Connect does not
     * offer that permission in its dialog; the user has to allow it in Health Connect's settings.
     */
    val onlyRoutesMissing: Boolean = false,
    val features: HealthConnectFeatureSupport? = null,
    val probe: ProbeState = ProbeState.Idle,
    val export: ExportState = ExportState.Idle,
) {
    val allGranted: Boolean get() = requestedCount > 0 && grantedCount == requestedCount
}

sealed interface ProbeState {
    data object Idle : ProbeState

    data class Running(val progress: ProbeProgress?) : ProbeState

    data class Finished(val highlights: ProbeHighlights, val file: File) : ProbeState

    data class Failed(val message: String) : ProbeState
}

sealed interface ExportState {
    data object Idle : ExportState

    data object Running : ExportState

    data class Finished(val sessionCount: Int, val file: File) : ExportState

    data class Failed(val message: String) : ExportState
}
