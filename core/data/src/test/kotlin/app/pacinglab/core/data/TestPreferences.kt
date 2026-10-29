package app.pacinglab.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import app.pacinglab.core.data.repository.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import java.nio.file.Files

/** An empty preferences store backed by a fresh file in a temporary directory. */
fun testDataStore(): DataStore<Preferences> {
    val directory = Files.createTempDirectory("prefs").toFile()
    return PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + SupervisorJob())) {
        File(directory, "test.preferences_pb")
    }
}

/** Preferences backed by a fresh file in a temporary directory, with every value at its default. */
fun testPreferences(dataStore: DataStore<Preferences> = testDataStore()): UserPreferencesRepository = UserPreferencesRepository(dataStore)
