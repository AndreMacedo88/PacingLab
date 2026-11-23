package app.pacinglab.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pacinglab.core.data.repository.Dashboard
import app.pacinglab.core.data.repository.DashboardRepository
import app.pacinglab.feature.today.TodayCard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the app frame does for every screen: pinning cards of other screens to Today. */
@HiltViewModel
class ShellViewModel @Inject constructor(private val dashboards: DashboardRepository) : ViewModel() {
    /** The ids of the cards Today shows now. */
    val onToday: StateFlow<Set<String>> = dashboards.layout(Dashboard.TODAY)
        .map { layout -> layout.arrange(TodayCard.catalog).filter { it.visible }.mapTo(HashSet()) { it.id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptySet())

    fun pin(id: String, pinned: Boolean) {
        viewModelScope.launch { dashboards.setVisible(Dashboard.TODAY, TodayCard.catalog, id, pinned) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
