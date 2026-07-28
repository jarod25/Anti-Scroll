package fr.jarodkohler.antiscroll.dashboard

import fr.jarodkohler.antiscroll.domain.application.ApplicationPackageName
import fr.jarodkohler.antiscroll.domain.application.MonitoredApplication
import fr.jarodkohler.antiscroll.domain.observation.AccessibilityMonitoringStatus
import fr.jarodkohler.antiscroll.domain.observation.CollectionCheckpoint
import fr.jarodkohler.antiscroll.domain.observation.CollectionGap
import fr.jarodkohler.antiscroll.domain.observation.CollectionStatus
import fr.jarodkohler.antiscroll.domain.observation.DailyApplicationUsage
import fr.jarodkohler.antiscroll.domain.observation.DailyUsageRepository
import fr.jarodkohler.antiscroll.domain.observation.DataCompleteness
import fr.jarodkohler.antiscroll.domain.observation.MonitoredApplicationRepository
import fr.jarodkohler.antiscroll.domain.observation.MonitoringHealth
import fr.jarodkohler.antiscroll.domain.observation.ObservationBaselineStatus
import fr.jarodkohler.antiscroll.domain.observation.ObservationStateRepository
import fr.jarodkohler.antiscroll.domain.observation.ObservationWindow
import fr.jarodkohler.antiscroll.domain.observation.UsageAccessStatus
import fr.jarodkohler.antiscroll.domain.observation.UsageEventSource
import fr.jarodkohler.antiscroll.engine.observation.ObservationBaselineCalculator
import fr.jarodkohler.antiscroll.engine.observation.ObservationBaselinePolicy
import fr.jarodkohler.antiscroll.engine.observation.TimeZoneProvider
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUpMainDispatcher() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun localUsageHealthAndReadyBaselineArePublishedAfterUtcDayBoundary() = runTest(testDispatcher) {
        val date = LocalDate.of(2026, 7, 29)
        val packageName = ApplicationPackageName("com.zhiliaoapp.musically")
        val currentUsage = DailyApplicationUsage(
            date = date,
            packageName = packageName,
            foregroundDuration = Duration.ofMinutes(12),
            estimatedOpeningCount = 3,
            completeness = DataCompleteness.COMPLETE
        )
        val historicalUsage = (1L..7L).map { daysBefore ->
            DailyApplicationUsage(
                date = date.minusDays(daysBefore),
                packageName = packageName,
                foregroundDuration = Duration.ofMinutes(10 + daysBefore),
                estimatedOpeningCount = daysBefore.toInt(),
                completeness = DataCompleteness.COMPLETE
            )
        }
        val health = MonitoringHealth(
            usageAccessStatus = UsageAccessStatus.GRANTED,
            accessibilityStatus = AccessibilityMonitoringStatus.UNSUPPORTED,
            collectionStatus = CollectionStatus.HEALTHY,
            lastSuccessfulReconciliationAt = Instant.parse("2026-07-28T23:00:00Z")
        )
        val application = MonitoredApplication(
            packageName = packageName,
            isEnabled = true,
            addedAt = Instant.parse("2026-07-21T22:00:00Z")
        )
        val dailyUsageRepository = FakeDailyUsageRepository(listOf(currentUsage) + historicalUsage)

        val viewModel = DashboardViewModel(
            dailyUsageRepository = dailyUsageRepository,
            observationStateRepository = FakeObservationStateRepository(health),
            monitoredApplicationRepository = FakeMonitoredApplicationRepository(listOf(application)),
            baselineCalculator = ObservationBaselineCalculator(ObservationBaselinePolicy(requiredReliableDays = 7)),
            clock = Clock.fixed(Instant.parse("2026-07-28T23:14:00Z"), ZoneOffset.UTC),
            timeZoneProvider = TimeZoneProvider { ZoneId.of("Europe/Paris") }
        )
        advanceUntilIdle()

        assertEquals(date, viewModel.uiState.value.date)
        assertEquals(listOf(currentUsage), viewModel.uiState.value.dailyUsage)
        assertEquals(health, viewModel.uiState.value.monitoringHealth)
        assertEquals(ObservationBaselineStatus.READY, viewModel.uiState.value.observationBaseline?.status)
    }
}

private class FakeDailyUsageRepository(usage: List<DailyApplicationUsage>) : DailyUsageRepository {
    private val usage = MutableStateFlow(usage)

    override fun observe(date: LocalDate): Flow<List<DailyApplicationUsage>> =
        flowOf(usage.value.filter { item -> item.date == date })

    override fun observeRange(
        fromInclusive: LocalDate,
        toInclusive: LocalDate
    ): Flow<List<DailyApplicationUsage>> = flowOf(
        usage.value.filter { item -> item.date in fromInclusive..toInclusive }
    )

    override suspend fun replace(date: LocalDate, usage: List<DailyApplicationUsage>) {
        this.usage.value = this.usage.value.filterNot { item -> item.date == date } + usage
    }
}

private class FakeMonitoredApplicationRepository(
    applications: List<MonitoredApplication>
) : MonitoredApplicationRepository {
    private val applications = MutableStateFlow(applications)

    override fun observeAll(): Flow<List<MonitoredApplication>> = applications

    override suspend fun allApplications(): List<MonitoredApplication> = applications.value

    override suspend fun enabledApplications(): List<MonitoredApplication> =
        applications.value.filter(MonitoredApplication::isEnabled)

    override suspend fun save(application: MonitoredApplication) {
        applications.value = applications.value.filterNot { item -> item.packageName == application.packageName } + application
    }
}

private class FakeObservationStateRepository(initialHealth: MonitoringHealth) : ObservationStateRepository {
    private val health = MutableStateFlow(initialHealth)

    override fun observeHealth(): Flow<MonitoringHealth> = health

    override suspend fun health(): MonitoringHealth = health.value

    override suspend fun saveHealth(health: MonitoringHealth) {
        this.health.value = health
    }

    override suspend fun checkpoint(source: UsageEventSource): CollectionCheckpoint? = null

    override suspend fun saveCheckpoint(checkpoint: CollectionCheckpoint) = Unit

    override fun observeGaps(window: ObservationWindow): Flow<List<CollectionGap>> = flowOf(emptyList())

    override suspend fun gapsIn(window: ObservationWindow, source: UsageEventSource): List<CollectionGap> = emptyList()

    override suspend fun recordGap(gap: CollectionGap) = Unit
}
