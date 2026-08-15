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
import fr.jarodkohler.antiscroll.engine.observation.TimeZoneProvider
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

private data class DashboardDateContext(val date: LocalDate, val zoneId: ZoneId)

private data class DailyUsageSnapshot(
    val date: LocalDate,
    val usage: List<DailyApplicationUsage>,
    val loadFailed: Boolean
)

private data class MonitoringHealthSnapshot(val health: MonitoringHealth, val loadFailed: Boolean)

private data class MonitoredApplicationsSnapshot(val applications: List<MonitoredApplication>, val loadFailed: Boolean)

private data class BaselineRequest(
    val currentDate: LocalDate,
    val zoneId: ZoneId,
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

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    dailyUsageRepository: DailyUsageRepository,
    observationStateRepository: ObservationStateRepository,
    monitoredApplicationRepository: MonitoredApplicationRepository,
    baselineCalculator: ObservationBaselineCalculator,
    private val clock: Clock,
    private val timeZoneProvider: TimeZoneProvider
) : ViewModel() {
    private val dateContext = MutableStateFlow(currentDateContext())

    private val dailyUsage = dateContext.flatMapLatest { context ->
        dailyUsageRepository.observe(context.date)
            .map { usage -> DailyUsageSnapshot(context.date, usage, loadFailed = false) }
            .catch { emit(DailyUsageSnapshot(context.date, emptyList(), loadFailed = true)) }
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
        dateContext,
        monitoredApplications
    ) { context, applicationsSnapshot ->
        BaselineRequest(
            currentDate = context.date,
            zoneId = context.zoneId,
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
        initialValue = DashboardUiState(date = dateContext.value.date)
    )

    fun refreshDate() {
        dateContext.value = currentDateContext()
    }

    private fun currentDateContext(): DashboardDateContext {
        val zoneId = timeZoneProvider.currentZoneId()
        return DashboardDateContext(
            date = clock.instant().atZone(zoneId).toLocalDate(),
            zoneId = zoneId
        )
    }

    private fun observeBaseline(
        request: BaselineRequest,
        dailyUsageRepository: DailyUsageRepository,
        baselineCalculator: ObservationBaselineCalculator
    ): Flow<BaselineSnapshot> {
        val enabledApplications = request.applications.filter(MonitoredApplication::isEnabled)
        val startDate = enabledApplications.maxOfOrNull { application ->
            application.addedAt.atZone(request.zoneId).toLocalDate()
        }
        val endDate = request.currentDate.minusDays(1)

        if (startDate == null || endDate.isBefore(startDate)) {
            return flowOf(
                BaselineSnapshot(
                    baseline = baselineCalculator.calculate(
                        monitoredApplications = request.applications,
                        dailyUsage = emptyList(),
                        currentDate = request.currentDate,
                        zoneId = request.zoneId
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
                        zoneId = request.zoneId
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
