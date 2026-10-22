package app.pacinglab.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pacinglab.core.data.repository.DarkMode
import app.pacinglab.core.data.repository.Sex
import app.pacinglab.core.data.repository.ThemeChoice
import app.pacinglab.core.data.repository.UserPreferences
import app.pacinglab.core.data.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(private val repository: UserPreferencesRepository) : ViewModel() {
    val preferences: StateFlow<UserPreferences> =
        repository.preferences.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UserPreferences())

    fun setTheme(theme: ThemeChoice) = launch { repository.setTheme(theme) }

    fun setDarkMode(mode: DarkMode) = launch { repository.setDarkMode(mode) }

    fun setStrengthInLoad(count: Boolean) = launch { repository.setStrengthInLoad(count) }

    fun setMergeGap(gap: Duration) = launch { repository.setSessionMergeGap(gap) }

    fun setSpeedByDefault(speed: Boolean) = launch { repository.setSpeedByDefault(speed) }

    fun setSleepNeed(need: Duration?) = launch { repository.setSleepNeed(need) }

    fun setSex(sex: Sex) = launch { repository.setSex(sex) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
