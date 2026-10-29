package app.pacinglab.core.googlehealth.di

import app.pacinglab.core.googlehealth.GoogleHealthApi
import app.pacinglab.core.googlehealth.GoogleHealthVitalsSource
import app.pacinglab.core.googlehealth.HttpGoogleHealthApi
import app.pacinglab.core.model.source.VitalsSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface GoogleHealthModule {
    @Binds
    fun api(api: HttpGoogleHealthApi): GoogleHealthApi

    @Binds
    fun vitals(source: GoogleHealthVitalsSource): VitalsSource
}
