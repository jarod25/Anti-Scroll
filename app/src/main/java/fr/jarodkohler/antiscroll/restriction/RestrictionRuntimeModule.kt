package fr.jarodkohler.antiscroll.restriction

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import fr.jarodkohler.antiscroll.domain.restriction.RestrictionProfileSource
import fr.jarodkohler.antiscroll.domain.restriction.SharedSessionEventSource
import fr.jarodkohler.antiscroll.domain.restriction.SharedSessionRuntimeStateSource
import fr.jarodkohler.antiscroll.engine.restriction.RestrictionEngine
import fr.jarodkohler.antiscroll.engine.restriction.SessionLimitRule
import fr.jarodkohler.antiscroll.engine.restriction.SharedSessionReducer
import java.time.Duration
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationCoroutineScope

@Module
@InstallIn(SingletonComponent::class)
abstract class RestrictionRuntimeBindingModule {
    @Binds
    @Singleton
    abstract fun bindRestrictionProfileSource(source: InMemoryRestrictionProfileSource): RestrictionProfileSource

    @Binds
    @IntoSet
    abstract fun bindForegroundSharedSessionEventSource(
        source: ForegroundSignalSharedSessionEventSource
    ): SharedSessionEventSource

    @Binds
    @IntoSet
    abstract fun bindUsageStatsSharedSessionEventSource(
        source: UsageStatsSharedSessionEventSource
    ): SharedSessionEventSource

    @Binds
    @Singleton
    abstract fun bindSharedSessionRuntimeStateSource(runtime: SharedSessionRuntime): SharedSessionRuntimeStateSource

    @Binds
    @Singleton
    abstract fun bindSharedSessionRuntimeClock(clock: AndroidSharedSessionRuntimeClock): SharedSessionRuntimeClock
}

@Module
@InstallIn(SingletonComponent::class)
object RestrictionRuntimeProvisionModule {
    @Provides
    @Singleton
    @ApplicationCoroutineScope
    fun provideApplicationCoroutineScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun provideSharedSessionReducer(): SharedSessionReducer = SharedSessionReducer()

    @Provides
    @Singleton
    fun provideRestrictionEngine(): RestrictionEngine = RestrictionEngine(rules = listOf(SessionLimitRule()))

    @Provides
    @Singleton
    fun provideUsageStatsSessionReconciliationPolicy(): UsageStatsSessionReconciliationPolicy =
        UsageStatsSessionReconciliationPolicy(
            pollingInterval = Duration.ofSeconds(2),
            overlap = Duration.ofSeconds(5),
            eventSettlementDelay = Duration.ofSeconds(1)
        )
}
