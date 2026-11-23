package app.pacinglab.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import app.pacinglab.R
import app.pacinglab.core.designsystem.component.SubTabs
import app.pacinglab.feature.trends.LoadRoute
import app.pacinglab.feature.workouts.list.SessionsRoute
import app.pacinglab.feature.workouts.list.SessionsView
import app.pacinglab.navigation.TrainingKey
import app.pacinglab.navigation.TrainingView

/**
 * Training: Sessions (every session, as a list or a calendar) and Load (what they add up to) under one app bar,
 * switched by the tabs under it. Each view keeps its scroll position and view model while the other is shown.
 */
@Composable
internal fun TrainingHost(
    key: TrainingKey,
    onOpenSession: (String) -> Unit,
    onOpenDataSources: () -> Unit,
    onOpenPhysiology: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(if (key.view == TrainingView.LOAD) LOAD else SESSIONS) }
    val states = rememberSaveableStateHolder()
    val title = stringResource(R.string.destination_training)
    val titles = listOf(stringResource(R.string.training_sessions), stringResource(R.string.training_load))
    val tabs: @Composable () -> Unit = { SubTabs(titles, tab, onSelect = { tab = it }) }
    states.SaveableStateProvider(tab) {
        when (tab) {
            SESSIONS -> SessionsRoute(
                title = title,
                tabs = tabs,
                onOpenSession = onOpenSession,
                onOpenDataSources = onOpenDataSources,
                onOpenSettings = onOpenSettings,
                initialView = if (key.view == TrainingView.CALENDAR) SessionsView.CALENDAR else SessionsView.LIST,
            )

            else -> LoadRoute(
                title = title,
                tabs = tabs,
                onOpenPhysiology = onOpenPhysiology,
                onOpenSettings = onOpenSettings,
                focus = key.focus,
            )
        }
    }
}

private const val SESSIONS = 0
private const val LOAD = 1
