package app.pacinglab.di

import android.content.Context
import app.pacinglab.core.maps.MapLibreSnapshots
import app.pacinglab.core.maps.MapSnapshots
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    /** Injected wherever "now" matters, so tests can fix the time. */
    @Provides
    fun clock(): Clock = Clock.systemDefaultZone()

    /** One renderer for the whole app, so that its image cache survives navigation. */
    @Provides
    @Singleton
    fun mapSnapshots(@ApplicationContext context: Context): MapSnapshots = MapLibreSnapshots(context)
}
