package fr.jarodkohler.antiscroll.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.jarodkohler.antiscroll.domain.application.MonitoredApplication
import fr.jarodkohler.antiscroll.domain.observation.AccessibilityMonitoringStatus
import fr.jarodkohler.antiscroll.domain.observation.CollectionStatus
import fr.jarodkohler.antiscroll.domain.observation.DailyApplicationUsage
import fr.jarodkohler.antiscroll.domain.observation.DailyUsageRepository
import fr.jarodkohler.antiscroll.domain.observation.MonitoredApplicationRepository
import fr.jarodkohler.antiscroll.domain.observation.MonitoringHealth
import fr.jarodkohler.antiscroll.domain.observation.ObservationBaseline
import fr.jarodkohler.antiscroll.domain.observation.ObservationStateRepository
import fr.jarodkohler.antiscroll.domain.observation.UsageAccessStatus
import fr.jarodkohler.antiscroll.engine.observation.ObservationBaselineCalculator
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

private data class DailyUsageSnapshot(
    val date: LocalDate,
    val usage: List<DailyApplicationUsage>,
    val loadFailed: Boolean
)

private data class MonitoringHealthSnapshot(val health: MonitoringHealth, val loadFailed: Boolean)

private data class MonitoredApplicationsSnapshot(val applications: List<MonitoredApplication>, val loadFailed: Boolean)

private data class BaselineRequest(
    val currentDate: LocalDate,
    val applications: List<MonitoredApplication>,
    val loadFailed: Boolean
)

private data class BaselineSnapshot(val baseline: ObservationBaseline?, val loadFailed: Boolean)

data class DashboardUiState(
    val date: LocalDate,
    val dailyUsage: List<DailyApplicationUsage> = emptyList(),
    val monitoringHealth: MonitoringHealth = DEFAULT_MONITORING_HEALTH,
    val observationBaseline: ObservationBaseline? = null,
    val loadFailed: Boolean = false
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    dailyUsageRepository: DailyUsageRepository,
    observationStateRepository: ObservationStateRepository,
    monitoredApplicationRepository: MonitoredApplicationRepository,
    baselineCalculator: ObservationBaselineCalculator,
    private val clock: Clock
) : ViewModel() {
    private val currentDate = MutableStateFlow(LocalDate.now(clock))

    private val dailyUsage = currentDate.flatMapLatest { date ->
        dailyUsageRepository.observe(date)
            .map { usage -> DailyUsageSnapshot(date, usage, loadFailed = false) }
            .catch { emit(DailyUsageSnapshot(date, emptyList(), loadFailed = true)) }
    }

    private val monitoringHealth = observationStateRepository.observeHealth()
        .map { health -> MonitoringHealthSnapshot(health, loadFailed = false) }
        .catch {
            emit(
                MonitoringHealthSnapshot(
                    health = DEFAULT_MONITORING_HEALTH,
                    loadFailed = true
                )
            )
        }

    private val monitoredApplications = monitoredApplicationRepository.observeAll()
        .map { applications -> MonitoredApplicationsSnapshot(applications, loadFailed = false) }
        .catch { emit(MonitoredApplicationsSnapshot(emptyList(), loadFailed = true)) }

    private val observationBaseline: Flow<BaselineSnapshot> = combine(
        currentDate,
        monitoredApplications
    ) { date, applicationsSnapshot ->
        BaselineRequest(
            currentDate = date,
            applications = applicationsSnapshot.applications,
            loadFailed = applicationsSnapshot.loadFailed
        )
    }.flatMapLatest { request ->
        if (request.loadFailed) {
            flowOf(BaselineSnapshot(baseline = null, loadFailed = true))
        } else {
            observeBaseline(
                request = request,
                dailyUsageRepository = dailyUsageRepository,
                baselineCalculator = baselineCalculator
            )
        }
    }

    val uiState = combine(
        dailyUsage,
        monitoringHealth,
        observationBaseline
    ) { usageSnapshot, healthSnapshot, baselineSnapshot ->
        DashboardUiState(
            date = usageSnapshot.date,
            dailyUsage = usageSnapshot.usage,
            monitoringHealth = healthSnapshot.health,
            observationBaseline = baselineSnapshot.baseline,
            loadFailed = usageSnapshot.loadFailed || healthSnapshot.loadFailed || baselineSnapshot.loadFailed
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = DashboardUiState(date = currentDate.value)
    )

    fun refreshDate() {
        currentDate.value = LocalDate.now(clock)
    }

    private fun observeBaseline(
        request: BaselineRequest,
        dailyUsageRepository: DailyUsageRepository,
        baselineCalculator: ObservationBaselineCalculator
    ): Flow<BaselineSnapshot> {
        val enabledApplications = request.applications.filter(MonitoredApplication::isEnabled)
        val startDate = enabledApplications.maxOfOrNull { application ->
            application.addedAt.atZone(clock.zone).toLocalDate()
        }
        val endDate = request.currentDate.minusDays(1)

        if (startDate == null || endDate.isBefore(startDate)) {
            return flowOf(
                BaselineSnapshot(
                    baseline = baselineCalculator.calculate(
                        monitoredApplications = request.applications,
                        dailyUsage = emptyList(),
                        currentDate = request.currentDate,
                        zoneId = clock.zone
                    ),
                    loadFailed = false
                )
            )
        }

        return dailyUsageRepository.observeRange(startDate, endDate)
            .map { usage ->
                BaselineSnapshot(
                    baseline = baselineCalculator.calculate(
                        monitoredApplications = request.applications,
                        dailyUsage = usage,
                        currentDate = request.currentDate,
                        zoneId = clock.zone
                    ),
                    loadFailed = false
                )
            }.catch {
                emit(BaselineSnapshot(baseline = null, loadFailed = true))
            }
    }
}

private val DEFAULT_MONITORING_HEALTH = MonitoringHealth(
    usageAccessStatus = UsageAccessStatus.UNAVAILABLE,
    accessibilityStatus = AccessibilityMonitoringStatus.UNSUPPORTED,
    collectionStatus = CollectionStatus.NOT_STARTED,
    lastSuccessfulReconciliationAt = null
)
