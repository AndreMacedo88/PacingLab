package app.pacinglab.feature.workouts.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import app.pacinglab.feature.workouts.detail.SessionRoute
import kotlinx.serialization.Serializable

@Serializable
data class SessionKey(val sessionId: String) : NavKey

/** The session page; the list of sessions is a view of Training, which the app composes. */
fun EntryProviderScope<NavKey>.workoutsEntries(navigateBack: () -> Unit, openPhysiology: () -> Unit) {
    entry<SessionKey> { key ->
        SessionRoute(sessionId = key.sessionId, onBack = navigateBack, onOpenPhysiology = openPhysiology)
    }
}
