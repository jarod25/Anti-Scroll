package fr.jarodkohler.antiscroll.observation

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.jarodkohler.antiscroll.domain.observation.MonitoredApplicationRepository
import fr.jarodkohler.antiscroll.domain.observation.ObservationCommitRepository
import fr.jarodkohler.antiscroll.domain.observation.ObservationStateRepository
import fr.jarodkohler.antiscroll.domain.observation.UsageEventRepository
import fr.jarodkohler.antiscroll.domain.observation.UsageObservationSource
import fr.jarodkohler.antiscroll.engine.observation.ObservationReconciliationCoordinator
import fr.jarodkohler.antiscroll.engine.observation.ObservationReconciliationPolicy
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ObservationRuntimeBindingModule {
    @Binds
    @Singleton
    abstract fun bindObservationWorkScheduler(
        scheduler: AndroidObservationWorkScheduler
    ): ObservationWorkScheduler
}

@Module
@InstallIn(SingletonComponent::class)
object ObservationRuntimeProvisionModule {
    @Provides
    @Singleton
    fun provideObservationReconciliationPolicy(): ObservationReconciliationPolicy =
        DefaultObservationProfile.reconciliationPolicy

    @Provides
    @Singleton
    fun provideObservationSchedulingPolicy(): ObservationSchedulingPolicy =
        DefaultObservationProfile.schedulingPolicy

    @Provides
    @Singleton
    fun provideObservationReconciliationCoordinator(
        sources: Set<@JvmSuppressWildcards UsageObservationSource>,
        monitoredApplicationRepository: MonitoredApplicationRepository,
        usageEventRepository: UsageEventRepository,
        observationCommitRepository: ObservationCommitRepository,
        observationStateRepository: ObservationStateRepository,
        clock: Clock,
        policy: ObservationReconciliationPolicy
    ): ObservationReconciliationCoordinator = ObservationReconciliationCoordinator(
        sources = sources,
        monitoredApplicationRepository = monitoredApplicationRepository,
        usageEventRepository = usageEventRepository,
        observationCommitRepository = observationCommitRepository,
        observationStateRepository = observationStateRepository,
        clock = clock,
        policy = policy
    )
}
