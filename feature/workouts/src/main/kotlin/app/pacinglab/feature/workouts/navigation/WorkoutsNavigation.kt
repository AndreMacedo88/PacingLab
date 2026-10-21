package app.pacinglab.feature.workouts.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import app.pacinglab.feature.workouts.detail.SessionRoute
import app.pacinglab.feature.workouts.list.WorkoutsRoute
import kotlinx.serialization.Serializable

@Serializable
data object WorkoutsKey : NavKey

@Serializable
data class SessionKey(val sessionId: String) : NavKey

fun EntryProviderScope<NavKey>.workoutsEntries(
    navigateTo: (NavKey) -> Unit,
    navigateBack: () -> Unit,
    openDataSources: () -> Unit,
    openPhysiology: () -> Unit,
) {
    entry<WorkoutsKey> {
        WorkoutsRoute(onOpenSession = { navigateTo(SessionKey(it)) }, onOpenDataSources = openDataSources)
    }
    entry<SessionKey> { key ->
        SessionRoute(sessionId = key.sessionId, onBack = navigateBack, onOpenPhysiology = openPhysiology)
    }
}
