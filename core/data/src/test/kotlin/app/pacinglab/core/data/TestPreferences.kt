package app.pacinglab.core.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.pacinglab.core.data.repository.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import java.nio.file.Files

/** Preferences backed by a fresh file in a temporary directory, with every value at its default. */
fun testPreferences(): UserPreferencesRepository {
    val directory = Files.createTempDirectory("prefs").toFile()
    val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + SupervisorJob())) {
        File(directory, "test.preferences_pb")
    }
    return UserPreferencesRepository(store)
}
