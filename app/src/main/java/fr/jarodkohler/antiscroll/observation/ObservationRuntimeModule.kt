package fr.jarodkohler.antiscroll.observation

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.jarodkohler.antiscroll.domain.observation.DailyUsageRepository
import fr.jarodkohler.antiscroll.domain.observation.MonitoredApplicationRepository
import fr.jarodkohler.antiscroll.domain.observation.ObservationCommitRepository
import fr.jarodkohler.antiscroll.domain.observation.ObservationStateRepository
import fr.jarodkohler.antiscroll.domain.observation.UsageEventRepository
import fr.jarodkohler.antiscroll.domain.observation.UsageObservationSource
import fr.jarodkohler.antiscroll.engine.observation.DailyUsageProjectionCoordinator
import fr.jarodkohler.antiscroll.engine.observation.ObservationReconciliationCoordinator
import fr.jarodkohler.antiscroll.engine.observation.ObservationReconciliationPolicy
import fr.jarodkohler.antiscroll.engine.observation.TimeZoneProvider
import fr.jarodkohler.antiscroll.engine.observation.UsageSessionReconstructionPolicy
import fr.jarodkohler.antiscroll.engine.observation.UsageSessionReconstructor
import java.time.Clock
import java.time.ZoneId
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ObservationRuntimeBindingModule {
    @Binds
    @Singleton
    abstract fun bindObservationWorkScheduler(scheduler: AndroidObservationWorkScheduler): ObservationWorkScheduler
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
    fun provideUsageSessionReconstructionPolicy(): UsageSessionReconstructionPolicy =
        DefaultObservationProfile.sessionReconstructionPolicy

    @Provides
    @Singleton
    fun provideObservationSchedulingPolicy(): ObservationSchedulingPolicy = DefaultObservationProfile.schedulingPolicy

    @Provides
    @Singleton
    fun provideTimeZoneProvider(): TimeZoneProvider = TimeZoneProvider(ZoneId::systemDefault)

    @Provides
    @Singleton
    fun provideUsageSessionReconstructor(
        policy: UsageSessionReconstructionPolicy
    ): UsageSessionReconstructor = UsageSessionReconstructor(policy)

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

    @Provides
    @Singleton
    fun provideDailyUsageProjectionCoordinator(
        monitoredApplicationRepository: MonitoredApplicationRepository,
        usageEventRepository: UsageEventRepository,
        observationStateRepository: ObservationStateRepository,
        dailyUsageRepository: DailyUsageRepository,
        sessionReconstructor: UsageSessionReconstructor,
        sessionPolicy: UsageSessionReconstructionPolicy,
        timeZoneProvider: TimeZoneProvider
    ): DailyUsageProjectionCoordinator = DailyUsageProjectionCoordinator(
        monitoredApplicationRepository = monitoredApplicationRepository,
        usageEventRepository = usageEventRepository,
        observationStateRepository = observationStateRepository,
        dailyUsageRepository = dailyUsageRepository,
        sessionReconstructor = sessionReconstructor,
        sessionPolicy = sessionPolicy,
        timeZoneProvider = timeZoneProvider
    )
}
