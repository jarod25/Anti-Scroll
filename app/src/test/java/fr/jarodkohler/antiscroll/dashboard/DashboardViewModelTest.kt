package fr.jarodkohler.antiscroll.dashboard

import fr.jarodkohler.antiscroll.domain.application.ApplicationPackageName
import fr.jarodkohler.antiscroll.domain.observation.AccessibilityMonitoringStatus
import fr.jarodkohler.antiscroll.domain.observation.CollectionCheckpoint
import fr.jarodkohler.antiscroll.domain.observation.CollectionGap
import fr.jarodkohler.antiscroll.domain.observation.CollectionStatus
import fr.jarodkohler.antiscroll.domain.observation.DailyApplicationUsage
import fr.jarodkohler.antiscroll.domain.observation.DailyUsageRepository
import fr.jarodkohler.antiscroll.domain.observation.DataCompleteness
import fr.jarodkohler.antiscroll.domain.observation.MonitoringHealth
import fr.jarodkohler.antiscroll.domain.observation.ObservationStateRepository
import fr.jarodkohler.antiscroll.domain.observation.ObservationWindow
import fr.jarodkohler.antiscroll.domain.observation.UsageAccessStatus
import fr.jarodkohler.antiscroll.domain.observation.UsageEventSource
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
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
    fun currentDailyUsageAndMonitoringHealthArePublished() = runTest(testDispatcher) {
        val date = LocalDate.of(2026, 7, 28)
        val packageName = ApplicationPackageName("com.zhiliaoapp.musically")
        val usage = DailyApplicationUsage(
            date = date,
            packageName = packageName,
            foregroundDuration = Duration.ofMinutes(12),
            estimatedOpeningCount = 3,
            completeness = DataCompleteness.COMPLETE
        )
        val health = MonitoringHealth(
            usageAccessStatus = UsageAccessStatus.GRANTED,
            accessibilityStatus = AccessibilityMonitoringStatus.UNSUPPORTED,
            collectionStatus = CollectionStatus.HEALTHY,
            lastSuccessfulReconciliationAt = Instant.parse("2026-07-28T12:00:00Z")
        )
        val dailyUsageRepository = FakeDailyUsageRepository(date, listOf(usage))
        val observationStateRepository = FakeObservationStateRepository(health)

        val viewModel = DashboardViewModel(
            dailyUsageRepository = dailyUsageRepository,
            observationStateRepository = observationStateRepository,
            clock = Clock.fixed(Instant.parse("2026-07-28T14:00:00Z"), ZoneOffset.UTC)
        )
        advanceUntilIdle()

        assertEquals(date, viewModel.uiState.value.date)
        assertEquals(listOf(usage), viewModel.uiState.value.dailyUsage)
        assertEquals(health, viewModel.uiState.value.monitoringHealth)
    }
}

private class FakeDailyUsageRepository(
    date: LocalDate,
    usage: List<DailyApplicationUsage>
) : DailyUsageRepository {
    private val usageByDate = mutableMapOf(date to MutableStateFlow(usage))

    override fun observe(date: LocalDate): Flow<List<DailyApplicationUsage>> =
        usageByDate.getOrPut(date) { MutableStateFlow(emptyList()) }

    override suspend fun replace(date: LocalDate, usage: List<DailyApplicationUsage>) {
        usageByDate.getOrPut(date) { MutableStateFlow(emptyList()) }.value = usage
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
