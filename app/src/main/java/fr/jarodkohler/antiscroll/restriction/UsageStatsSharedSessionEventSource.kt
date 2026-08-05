package fr.jarodkohler.antiscroll.restriction

import fr.jarodkohler.antiscroll.domain.application.ApplicationPackageName
import fr.jarodkohler.antiscroll.domain.monitoring.ForegroundApplicationSignalSource
import fr.jarodkohler.antiscroll.domain.observation.MonitoredApplicationRepository
import fr.jarodkohler.antiscroll.domain.observation.ObservationWindow
import fr.jarodkohler.antiscroll.domain.restriction.RestrictionProfileSource
import fr.jarodkohler.antiscroll.domain.restriction.SharedSessionEvent
import fr.jarodkohler.antiscroll.domain.restriction.SharedSessionEventSource
import fr.jarodkohler.antiscroll.domain.restriction.SharedSessionPolicy
import fr.jarodkohler.antiscroll.monitoring.usagestats.UsageStatsEventGateway
import fr.jarodkohler.antiscroll.monitoring.usagestats.UsageStatsQueryResult
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class UsageStatsSharedSessionEventSource
@Inject
constructor(
    private val signalSource: ForegroundApplicationSignalSource,
    private val profileSource: RestrictionProfileSource,
    private val monitoredApplicationRepository: MonitoredApplicationRepository,
    private val eventGateway: UsageStatsEventGateway,
    private val clock: SharedSessionRuntimeClock,
    private val reconciliationPolicy: UsageStatsSessionReconciliationPolicy
) : SharedSessionEventSource {
    override val events: Flow<SharedSessionEvent> =
        combine(
            profileSource.activeProfile,
            monitoredApplicationRepository.observeAll().map { applications ->
                applications.asSequence()
                    .filter { application -> application.isEnabled }
                    .map { application -> application.packageName }
                    .toSet()
            }
        ) { profile, packageNames ->
            val sessionPolicy = profile.sharedSessionPolicy
            if (sessionPolicy == null || packageNames.isEmpty()) {
                ReconciliationTarget.Inactive
            } else {
                ReconciliationTarget.Active(
                    packageNames = packageNames,
                    initialLookback = initialLookback(sessionPolicy)
                )
            }
        }.distinctUntilChanged()
            .flatMapLatest { target ->
                when (target) {
                    ReconciliationTarget.Inactive -> emptyFlow()
                    is ReconciliationTarget.Active -> reconcile(target)
                }
            }

    private fun reconcile(target: ReconciliationTarget.Active): Flow<SharedSessionEvent> = channelFlow {
        val tracker = UsageStatsSharedSessionEventTracker()
        val trackerMutex = Mutex()
        val signalCollection = launch {
            signalSource.signals.collect { signal ->
                if (signal.packageName in target.packageNames) {
                    trackerMutex.withLock {
                        tracker.markForeground(signal.packageName)
                    }
                }
            }
        }
        var lastSuccessfulQueryAt: Instant? = null

        try {
            while (currentCoroutineContext().isActive) {
                val clockSnapshot = ReconciliationClockSnapshot(
                    observedAt = clock.now(),
                    elapsedRealtime = clock.elapsedRealtime()
                )
                val queryStart = queryStart(
                    lastSuccessfulQueryAt = lastSuccessfulQueryAt,
                    clockSnapshot = clockSnapshot,
                    initialLookback = target.initialLookback
                )
                val queryWindow = ObservationWindow(
                    startInclusive = queryStart,
                    endExclusive = clockSnapshot.observedAt.plusMillis(1)
                )

                when (val result = eventGateway.query(queryWindow, target.packageNames)) {
                    is UsageStatsQueryResult.Events -> {
                        val corrections = trackerMutex.withLock {
                            tracker.accept(
                                records = result.records,
                                allowedPackages = target.packageNames,
                                clockSnapshot = clockSnapshot,
                                retainFrom = queryStart,
                                processThrough = clockSnapshot.observedAt.minus(
                                    reconciliationPolicy.eventSettlementDelay
                                ),
                                settlementWindow = reconciliationPolicy.eventSettlementDelay
                            )
                        }
                        corrections.forEach { event -> send(event) }
                        lastSuccessfulQueryAt = clockSnapshot.observedAt
                    }

                    UsageStatsQueryResult.DeviceLocked,
                    UsageStatsQueryResult.SourceUnavailable,
                    UsageStatsQueryResult.UsageAccessMissing -> Unit
                }

                delay(reconciliationPolicy.pollingInterval.toMillis())
            }
        } finally {
            signalCollection.cancelAndJoin()
        }
    }

    private fun queryStart(
        lastSuccessfulQueryAt: Instant?,
        clockSnapshot: ReconciliationClockSnapshot,
        initialLookback: Duration
    ): Instant {
        if (lastSuccessfulQueryAt == null) {
            return clockSnapshot.observedAt.minus(initialLookback)
        }

        val overlappingStart = lastSuccessfulQueryAt.minus(reconciliationPolicy.overlap)
        val safeRecentStart = clockSnapshot.observedAt.minus(reconciliationPolicy.overlap)
        return minOf(overlappingStart, safeRecentStart)
    }

    private fun initialLookback(sessionPolicy: SharedSessionPolicy): Duration =
        sessionPolicy.maximumDuration
            .plus(sessionPolicy.inactivityTimeout)
            .plus(reconciliationPolicy.overlap)
            .plus(reconciliationPolicy.eventSettlementDelay)

    private sealed interface ReconciliationTarget {
        data object Inactive : ReconciliationTarget

        data class Active(
            val packageNames: Set<ApplicationPackageName>,
            val initialLookback: Duration
        ) : ReconciliationTarget
    }
}
