package app.pacinglab.core.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import app.pacinglab.core.data.home.HomeAssistantPayload
import app.pacinglab.core.data.home.HomeAssistantPreferences
import app.pacinglab.core.data.home.HomeAssistantSync
import app.pacinglab.core.data.home.HomeAssistantValues
import app.pacinglab.core.data.home.KeystoreSecretBox
import app.pacinglab.core.data.home.SecretBox
import app.pacinglab.core.data.reminder.GymEffortReminder
import app.pacinglab.core.data.reminder.MorningCheck
import app.pacinglab.core.data.reminder.MorningCheckAlarm
import app.pacinglab.core.data.reminder.MorningReminder
import app.pacinglab.core.data.reminder.PlatformMorningCheckAlarm
import app.pacinglab.core.data.repository.DashboardMigration
import app.pacinglab.core.data.strain.StillHeartRateFiller
import app.pacinglab.core.data.sync.AfterSync
import app.pacinglab.core.data.sync.AppVisibility
import app.pacinglab.core.data.sync.ProcessAppVisibility
import app.pacinglab.core.data.weather.ApproximateLocation
import app.pacinglab.core.data.weather.FileWeatherCache
import app.pacinglab.core.data.weather.OpenMeteoPastService
import app.pacinglab.core.data.weather.OpenMeteoService
import app.pacinglab.core.data.weather.PastWeatherService
import app.pacinglab.core.data.weather.PlatformApproximateLocation
import app.pacinglab.core.data.weather.RunWeatherFiller
import app.pacinglab.core.data.weather.WeatherCache
import app.pacinglab.core.data.weather.WeatherService
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dagger.multibindings.Multibinds
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

    @Binds
    fun morningCheckAlarm(alarm: PlatformMorningCheckAlarm): MorningCheckAlarm

    @Binds
    @IntoSet
    fun morningReminder(reminder: MorningReminder): AfterSync

    /** Sets the morning checks again after a restart or an update, which clear alarms. */
    @Binds
    @IntoSet
    fun morningCheck(check: MorningCheck): AfterSync

    @Binds
    @IntoSet
    fun gymEffortReminder(reminder: GymEffortReminder): AfterSync

    @Binds
    @IntoSet
    fun runWeather(filler: RunWeatherFiller): AfterSync

    @Binds
    @IntoSet
    fun stillHeartRate(filler: StillHeartRateFiller): AfterSync

    @Binds
    @IntoSet
    fun homeAssistant(sync: HomeAssistantSync): AfterSync

    @Binds
    fun secretBox(box: KeystoreSecretBox): SecretBox

    @Binds
    fun homeAssistantValues(payload: HomeAssistantPayload): HomeAssistantValues

    /** Other modules may add work after syncs; without any the set is empty. */
    @Multibinds
    fun afterSync(): Set<AfterSync>
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
        PreferenceDataStoreFactory.create(scope = scope, migrations = listOf(DashboardMigration.dataMigration)) {
            context.preferencesDataStoreFile("user_preferences")
        }

    /** The Home Assistant link's settings, apart from [preferences] so that backups never copy its token. */
    @Provides
    @Singleton
    @HomeAssistantPreferences
    fun homeAssistantPreferences(@ApplicationContext context: Context, @ApplicationScope scope: CoroutineScope): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope) { context.preferencesDataStoreFile("home_assistant") }
}
