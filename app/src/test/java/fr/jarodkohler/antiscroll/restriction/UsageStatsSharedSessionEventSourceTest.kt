package fr.jarodkohler.antiscroll.restriction

import fr.jarodkohler.antiscroll.domain.application.ApplicationPackageName
import fr.jarodkohler.antiscroll.domain.application.MonitoredApplication
import fr.jarodkohler.antiscroll.domain.monitoring.ForegroundApplicationSignal
import fr.jarodkohler.antiscroll.domain.monitoring.ForegroundApplicationSignalSource
import fr.jarodkohler.antiscroll.domain.observation.MonitoredApplicationRepository
import fr.jarodkohler.antiscroll.domain.observation.ObservationWindow
import fr.jarodkohler.antiscroll.domain.restriction.RestrictionProfile
import fr.jarodkohler.antiscroll.domain.restriction.RestrictionProfileIdentifier
import fr.jarodkohler.antiscroll.domain.restriction.RestrictionProfileSource
import fr.jarodkohler.antiscroll.domain.restriction.SharedSessionEvent
import fr.jarodkohler.antiscroll.domain.restriction.SharedSessionPolicy
import fr.jarodkohler.antiscroll.monitoring.usagestats.UsageStatsActivityEventType
import fr.jarodkohler.antiscroll.monitoring.usagestats.UsageStatsEventGateway
import fr.jarodkohler.antiscroll.monitoring.usagestats.UsageStatsEventRecord
import fr.jarodkohler.antiscroll.monitoring.usagestats.UsageStatsQueryResult
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UsageStatsSharedSessionEventSourceTest {
    private val tikTok = ApplicationPackageName("com.zhiliaoapp.musically")
    private val origin = Instant.parse("2026-08-05T10:00:00Z")
    private val observationProfile = RestrictionProfile(
        identifier = RestrictionProfileIdentifier("observation"),
        version = 1,
        sharedSessionPolicy = null
    )
    private val activeProfile = RestrictionProfile(
        identifier = RestrictionProfileIdentifier("normal"),
        version = 1,
        sharedSessionPolicy = SharedSessionPolicy(
            maximumDuration = Duration.ofMinutes(10),
            inactivityTimeout = Duration.ofMinutes(2)
        )
    )

    @Test
    fun observationProfileKeepsUsageStatsReconciliationDormant() = runTest {
        val gateway = FakeUsageStatsEventGateway(UsageStatsQueryResult.Events(emptyList()))
        val source = source(
            profileSource = FakeRestrictionProfileSource(observationProfile),
            gateway = gateway
        )
        val collection = backgroundScope.launch { source.events.collect() }

        runCurrent()
        advanceTimeBy(Duration.ofSeconds(10).toMillis())
        runCurrent()

        assertTrue(gateway.windows.isEmpty())
        collection.cancel()
    }

    @Test
    fun activeProfileMapsUsageStatsHistoryToExplicitSessionEvents() = runTest {
        val gateway = FakeUsageStatsEventGateway(
            UsageStatsQueryResult.Events(
                listOf(
                    record(UsageStatsActivityEventType.RESUMED, second = 10),
                    record(UsageStatsActivityEventType.PAUSED, second = 15)
                )
            )
        )
        val source = source(
            profileSource = FakeRestrictionProfileSource(activeProfile),
            gateway = gateway
        )

        val events = async { source.events.take(2).toList() }
        runCurrent()

        val collected = events.await()
        assertTrue(collected[0] is SharedSessionEvent.ApplicationForegrounded)
        assertTrue(collected[1] is SharedSessionEvent.ApplicationBackgrounded)
        assertEquals(1, gateway.windows.size)
        assertEquals(
            origin.plusSeconds(20).minus(Duration.ofMinutes(12)).minusSeconds(5),
            gateway.windows.single().startInclusive
        )
    }

    @Test
    fun accessibilityHintAllowsPauseOnlyHistoryToCorrectForegroundState() = runTest {
        val releaseQuery = CompletableDeferred<Unit>()
        val gateway = FakeUsageStatsEventGateway(
            result = UsageStatsQueryResult.Events(
                listOf(record(UsageStatsActivityEventType.PAUSED, second = 15))
            ),
            releaseQuery = releaseQuery
        )
        val signalSource = FakeForegroundApplicationSignalSource()
        val source = source(
            profileSource = FakeRestrictionProfileSource(activeProfile),
            gateway = gateway,
            signalSource = signalSource
        )

        val correction = async { source.events.first() }
        runCurrent()
        signalSource.emit(
            ForegroundApplicationSignal(
                packageName = tikTok,
                observedAt = origin.plusSeconds(10),
                elapsedRealtime = Duration.ofMinutes(30)
            )
        )
        runCurrent()
        releaseQuery.complete(Unit)
        runCurrent()

        assertTrue(correction.await() is SharedSessionEvent.ApplicationBackgrounded)
    }

    private fun source(
        profileSource: RestrictionProfileSource,
        gateway: UsageStatsEventGateway,
        signalSource: ForegroundApplicationSignalSource = FakeForegroundApplicationSignalSource()
    ): UsageStatsSharedSessionEventSource = UsageStatsSharedSessionEventSource(
        signalSource = signalSource,
        profileSource = profileSource,
        monitoredApplicationRepository = FakeMonitoredApplicationRepository(
            listOf(MonitoredApplication(tikTok, isEnabled = true, addedAt = origin))
        ),
        eventGateway = gateway,
        clock = FakeSharedSessionRuntimeClock(
            currentInstant = origin.plusSeconds(20),
            currentElapsedRealtime = Duration.ofHours(1)
        ),
        reconciliationPolicy = UsageStatsSessionReconciliationPolicy(
            pollingInterval = Duration.ofSeconds(2),
            overlap = Duration.ofSeconds(5)
        )
    )

    private fun record(
        type: UsageStatsActivityEventType,
        second: Long
    ): UsageStatsEventRecord = UsageStatsEventRecord(
        packageName = tikTok.value,
        activityClassName = "FeedActivity",
        eventType = type,
        occurredAtEpochMillis = origin.plusSeconds(second).toEpochMilli()
    )

    private class FakeForegroundApplicationSignalSource : ForegroundApplicationSignalSource {
        private val mutableSignals = MutableSharedFlow<ForegroundApplicationSignal>(extraBufferCapacity = 4)

        override val signals: Flow<ForegroundApplicationSignal> = mutableSignals.asSharedFlow()

        fun emit(signal: ForegroundApplicationSignal) {
            check(mutableSignals.tryEmit(signal)) { "Foreground signal buffer is full" }
        }
    }

    private class FakeRestrictionProfileSource(initialProfile: RestrictionProfile) : RestrictionProfileSource {
        private val mutableActiveProfile = MutableStateFlow(initialProfile)

        override val activeProfile: StateFlow<RestrictionProfile> = mutableActiveProfile.asStateFlow()
    }

    private class FakeMonitoredApplicationRepository(
        applications: List<MonitoredApplication>
    ) : MonitoredApplicationRepository {
        private val mutableApplications = MutableStateFlow(applications)

        override fun observeAll(): Flow<List<MonitoredApplication>> = mutableApplications.asStateFlow()

        override suspend fun allApplications(): List<MonitoredApplication> = mutableApplications.value

        override suspend fun enabledApplications(): List<MonitoredApplication> =
            mutableApplications.value.filter(MonitoredApplication::isEnabled)

        override suspend fun save(application: MonitoredApplication) {
            mutableApplications.value = mutableApplications.value
                .filterNot { current -> current.packageName == application.packageName } + application
        }
    }

    private class FakeUsageStatsEventGateway(
        private val result: UsageStatsQueryResult,
        private val releaseQuery: CompletableDeferred<Unit>? = null
    ) : UsageStatsEventGateway {
        val windows = mutableListOf<ObservationWindow>()

        override suspend fun query(
            window: ObservationWindow,
            packageNames: Set<ApplicationPackageName>
        ): UsageStatsQueryResult {
            windows += window
            releaseQuery?.await()
            return result
        }
    }

    private data class FakeSharedSessionRuntimeClock(
        val currentInstant: Instant,
        val currentElapsedRealtime: Duration
    ) : SharedSessionRuntimeClock {
        override fun now(): Instant = currentInstant

        override fun elapsedRealtime(): Duration = currentElapsedRealtime
    }
}
