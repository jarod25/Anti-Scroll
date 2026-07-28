package fr.jarodkohler.antiscroll.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.jarodkohler.antiscroll.data.repository.RoomMonitoredApplicationRepository
import fr.jarodkohler.antiscroll.domain.observation.MonitoredApplicationRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindMonitoredApplicationRepository(
        repository: RoomMonitoredApplicationRepository
    ): MonitoredApplicationRepository
}
