package fr.jarodkohler.antiscroll.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.jarodkohler.antiscroll.domain.observation.AccessibilityMonitoringStatus
import fr.jarodkohler.antiscroll.domain.observation.CollectionStatus
import fr.jarodkohler.antiscroll.domain.observation.DailyApplicationUsage
import fr.jarodkohler.antiscroll.domain.observation.DailyUsageRepository
import fr.jarodkohler.antiscroll.domain.observation.MonitoringHealth
import fr.jarodkohler.antiscroll.domain.observation.ObservationStateRepository
import fr.jarodkohler.antiscroll.domain.observation.UsageAccessStatus
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableStateFlow

private data class DailyUsageSnapshot(
    val date: LocalDate,
    val usage: List<DailyApplicationUsage>,
    val loadFailed: Boolean
)

private data class MonitoringHealthSnapshot(
    val health: MonitoringHealth,
    val loadFailed: Boolean
)

data class DashboardUiState(
    val date: LocalDate,
    val dailyUsage: List<DailyApplicationUsage> = emptyList(),
    val monitoringHealth: MonitoringHealth = DEFAULT_MONITORING_HEALTH,
    val loadFailed: Boolean = false
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    dailyUsageRepository: DailyUsageRepository,
    observationStateRepository: ObservationStateRepository,
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

    val uiState = combine(dailyUsage, monitoringHealth) { usageSnapshot, healthSnapshot ->
        DashboardUiState(
            date = usageSnapshot.date,
            dailyUsage = usageSnapshot.usage,
            monitoringHealth = healthSnapshot.health,
            loadFailed = usageSnapshot.loadFailed || healthSnapshot.loadFailed
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = DashboardUiState(date = currentDate.value)
    )

    fun refreshDate() {
        currentDate.value = LocalDate.now(clock)
    }
}

private val DEFAULT_MONITORING_HEALTH = MonitoringHealth(
    usageAccessStatus = UsageAccessStatus.UNAVAILABLE,
    accessibilityStatus = AccessibilityMonitoringStatus.UNSUPPORTED,
    collectionStatus = CollectionStatus.NOT_STARTED,
    lastSuccessfulReconciliationAt = null
)
