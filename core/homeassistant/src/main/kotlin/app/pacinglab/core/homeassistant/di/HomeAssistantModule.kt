package app.pacinglab.core.homeassistant.di

import app.pacinglab.core.homeassistant.OkHttpHomeAssistantSource
import app.pacinglab.core.model.home.HomeAssistantSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface HomeAssistantModule {
    @Binds
    fun source(source: OkHttpHomeAssistantSource): HomeAssistantSource
}
