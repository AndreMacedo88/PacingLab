package app.pacinglab.core.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import app.pacinglab.core.data.sync.AppVisibility
import app.pacinglab.core.data.sync.ProcessAppVisibility
import app.pacinglab.core.data.weather.ApproximateLocation
import app.pacinglab.core.data.weather.FileWeatherCache
import app.pacinglab.core.data.weather.OpenMeteoPastService
import app.pacinglab.core.data.weather.OpenMeteoService
import app.pacinglab.core.data.weather.PastWeatherService
import app.pacinglab.core.data.weather.PlatformApproximateLocation
import app.pacinglab.core.data.weather.WeatherCache
import app.pacinglab.core.data.weather.WeatherService
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

    @Binds
    fun weather(service: OpenMeteoService): WeatherService

    @Binds
    fun pastWeather(service: OpenMeteoPastService): PastWeatherService

    @Binds
    fun location(location: PlatformApproximateLocation): ApproximateLocation

    @Binds
    fun weatherCache(cache: FileWeatherCache): WeatherCache
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
