package app.pacinglab.core.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import app.pacinglab.core.data.sync.AppVisibility
import app.pacinglab.core.data.sync.ProcessAppVisibility
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface DataBindings {
    @Binds
    fun visibility(visibility: ProcessAppVisibility): AppVisibility
}

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides
    @Singleton
    @ApplicationScope
    fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun preferences(@ApplicationContext context: Context, @ApplicationScope scope: CoroutineScope): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope) { context.preferencesDataStoreFile("user_preferences") }
}
