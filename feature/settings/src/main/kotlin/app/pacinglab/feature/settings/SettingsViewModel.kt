package app.pacinglab.feature.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pacinglab.core.data.repository.BackupFiles
import app.pacinglab.core.data.repository.BackupFormatException
import app.pacinglab.core.data.repository.BackupRepository
import app.pacinglab.core.data.repository.DarkMode
import app.pacinglab.core.data.repository.Sex
import app.pacinglab.core.data.repository.ThemeChoice
import app.pacinglab.core.data.repository.UserPreferences
import app.pacinglab.core.data.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import javax.inject.Inject

/** The outcome of a backup or restore, shown once. */
sealed interface BackupMessage {
    data class Saved(val entries: Int) : BackupMessage

    data class Restored(val entries: Int) : BackupMessage

    data class Failed(val problem: BackupProblem) : BackupMessage
}

enum class BackupProblem { NOT_SAVED, NOT_OPENED, NOT_A_BACKUP, NEWER_VERSION, UNREADABLE_VALUE }

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: UserPreferencesRepository,
    private val backupFiles: BackupFiles,
    private val clock: Clock,
) : ViewModel() {
    val preferences: StateFlow<UserPreferences> =
        repository.preferences.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UserPreferences())

    private val _backupMessage = MutableStateFlow<BackupMessage?>(null)
    val backupMessage: StateFlow<BackupMessage?> = _backupMessage.asStateFlow()

    fun setTheme(theme: ThemeChoice) = launch { repository.setTheme(theme) }

    fun setDarkMode(mode: DarkMode) = launch { repository.setDarkMode(mode) }

    fun setStrengthInLoad(count: Boolean) = launch { repository.setStrengthInLoad(count) }

    fun setMergeGap(gap: Duration) = launch { repository.setSessionMergeGap(gap) }

    fun setSpeedByDefault(speed: Boolean) = launch { repository.setSpeedByDefault(speed) }

    fun setSleepNeed(need: Duration?) = launch { repository.setSleepNeed(need) }

    fun setSex(sex: Sex) = launch { repository.setSex(sex) }

    /** The name the file picker suggests for a backup made today. */
    fun backupFileName(): String = BackupRepository.fileName(LocalDate.now(clock))

    fun backUp(uri: Uri) = launch {
        _backupMessage.value = try {
            BackupMessage.Saved(backupFiles.save(uri).entries)
        } catch (_: IOException) {
            BackupMessage.Failed(BackupProblem.NOT_SAVED)
        } catch (_: SecurityException) {
            BackupMessage.Failed(BackupProblem.NOT_SAVED)
        }
    }

    fun restore(uri: Uri) = launch {
        _backupMessage.value = try {
            BackupMessage.Restored(backupFiles.restore(uri).entries)
        } catch (_: IOException) {
            BackupMessage.Failed(BackupProblem.NOT_OPENED)
        } catch (_: SecurityException) {
            BackupMessage.Failed(BackupProblem.NOT_OPENED)
        } catch (e: BackupFormatException) {
            BackupMessage.Failed(
                when (e.reason) {
                    BackupFormatException.Reason.NOT_A_BACKUP -> BackupProblem.NOT_A_BACKUP
                    BackupFormatException.Reason.NEWER_VERSION -> BackupProblem.NEWER_VERSION
                    BackupFormatException.Reason.UNREADABLE_VALUE -> BackupProblem.UNREADABLE_VALUE
                },
            )
        }
    }

    fun backupMessageShown() {
        _backupMessage.value = null
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
