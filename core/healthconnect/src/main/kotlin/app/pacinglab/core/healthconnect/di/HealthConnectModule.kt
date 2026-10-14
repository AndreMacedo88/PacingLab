package app.pacinglab.core.healthconnect.di

import app.pacinglab.core.healthconnect.source.ClientHealthConnectGateway
import app.pacinglab.core.healthconnect.source.HealthConnectDataSource
import app.pacinglab.core.healthconnect.source.HealthConnectGateway
import app.pacinglab.core.model.source.HealthDataSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface HealthConnectModule {
    @Binds
    fun gateway(gateway: ClientHealthConnectGateway): HealthConnectGateway

    @Binds
    fun dataSource(source: HealthConnectDataSource): HealthDataSource
}
