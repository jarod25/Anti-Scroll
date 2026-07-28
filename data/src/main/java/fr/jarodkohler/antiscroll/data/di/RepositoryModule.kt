package fr.jarodkohler.antiscroll.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.jarodkohler.antiscroll.data.repository.RoomDailyUsageRepository
import fr.jarodkohler.antiscroll.data.repository.RoomMonitoredApplicationRepository
import fr.jarodkohler.antiscroll.data.repository.RoomObservationRepository
import fr.jarodkohler.antiscroll.domain.observation.DailyUsageRepository
import fr.jarodkohler.antiscroll.domain.observation.MonitoredApplicationRepository
import fr.jarodkohler.antiscroll.domain.observation.ObservationCommitRepository
import fr.jarodkohler.antiscroll.domain.observation.ObservationStateRepository
import fr.jarodkohler.antiscroll.domain.observation.UsageEventRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindMonitoredApplicationRepository(
        repository: RoomMonitoredApplicationRepository
    ): MonitoredApplicationRepository

    @Binds
    @Singleton
    abstract fun bindUsageEventRepository(repository: RoomObservationRepository): UsageEventRepository

    @Binds
    @Singleton
    abstract fun bindObservationStateRepository(repository: RoomObservationRepository): ObservationStateRepository

    @Binds
    @Singleton
    abstract fun bindObservationCommitRepository(repository: RoomObservationRepository): ObservationCommitRepository

    @Binds
    @Singleton
    abstract fun bindDailyUsageRepository(repository: RoomDailyUsageRepository): DailyUsageRepository
}
