package fr.jarodkohler.antiscroll.monitoring.usagestats

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import fr.jarodkohler.antiscroll.domain.observation.UsageObservationSource
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class UsageStatsMonitoringModule {
    @Binds
    @Singleton
    abstract fun bindUsageStatsEventGateway(gateway: AndroidUsageStatsEventGateway): UsageStatsEventGateway

    @Binds
    @IntoSet
    abstract fun bindUsageStatsObservationSource(source: UsageStatsObservationSource): UsageObservationSource
}
