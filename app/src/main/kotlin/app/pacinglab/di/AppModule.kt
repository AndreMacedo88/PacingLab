package app.pacinglab.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    /** Injected wherever "now" matters, so tests can fix the time. */
    @Provides
    fun clock(): Clock = Clock.systemDefaultZone()
}
