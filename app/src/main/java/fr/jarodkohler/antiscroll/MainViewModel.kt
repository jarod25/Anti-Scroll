package fr.jarodkohler.antiscroll

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.jarodkohler.antiscroll.applicationconfig.InstalledApplicationPresentation
import fr.jarodkohler.antiscroll.applicationconfig.InstalledApplicationResolver
import fr.jarodkohler.antiscroll.applicationconfig.SupportedApplicationCatalog
import fr.jarodkohler.antiscroll.domain.application.ApplicationPackageName
import fr.jarodkohler.antiscroll.domain.application.MonitoredApplication
import fr.jarodkohler.antiscroll.domain.observation.AccessibilityMonitoringStatus
import fr.jarodkohler.antiscroll.domain.observation.MonitoredApplicationRepository
import fr.jarodkohler.antiscroll.domain.observation.UsageAccessStatus
import fr.jarodkohler.antiscroll.monitoring.permission.MonitoringPermissionReader
import fr.jarodkohler.antiscroll.monitoring.permission.MonitoringPermissionSnapshot
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class MonitoredApplicationUiModel(
    val packageName: ApplicationPackageName,
    val label: String,
    val icon: ImageBitmap?,
    val isEnabled: Boolean
)

data class MainUiState(
    val permissionSnapshot: MonitoringPermissionSnapshot = MonitoringPermissionSnapshot(
        usageAccessStatus = UsageAccessStatus.UNAVAILABLE,
        accessibilityStatus = AccessibilityMonitoringStatus.UNSUPPORTED
    ),
    val applications: List<MonitoredApplicationUiModel> = emptyList(),
    val isLoadingApplications: Boolean = false,
    val applicationLoadFailed: Boolean = false,
    val configurationSaveFailed: Boolean = false
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val permissionReader: MonitoringPermissionReader,
    private val monitoredApplicationRepository: MonitoredApplicationRepository,
    private val installedApplicationResolver: InstalledApplicationResolver,
    private val clock: Clock
) : ViewModel() {
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var persistedApplications = emptyMap<ApplicationPackageName, MonitoredApplication>()
    private var installedApplications = emptyList<InstalledApplicationPresentation>()
    private var refreshJob: Job? = null

    init {
        monitoredApplicationRepository.observeAll()
            .onEach { applications ->
                persistedApplications = applications.associateBy(MonitoredApplication::packageName)
                publishState()
            }
            .catch {
                _uiState.value = _uiState.value.copy(configurationSaveFailed = true)
            }
            .launchIn(viewModelScope)
    }

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val permissionSnapshot = permissionReader.read()
            _uiState.value = _uiState.value.copy(
                permissionSnapshot = permissionSnapshot,
                applicationLoadFailed = false
            )

            if (permissionSnapshot.usageAccessStatus != UsageAccessStatus.GRANTED) {
                installedApplications = emptyList()
                _uiState.value = _uiState.value.copy(isLoadingApplications = false)
                publishState()
                return@launch
            }

            _uiState.value = _uiState.value.copy(isLoadingApplications = true)
            runCatching {
                installedApplicationResolver.resolveInstalled(
                    SupportedApplicationCatalog.applications
                )
            }.onSuccess { applications ->
                installedApplications = applications
                _uiState.value = _uiState.value.copy(
                    isLoadingApplications = false,
                    applicationLoadFailed = false
                )
            }.onFailure {
                installedApplications = emptyList()
                _uiState.value = _uiState.value.copy(
                    isLoadingApplications = false,
                    applicationLoadFailed = true
                )
            }
            publishState()
        }
    }

    fun setApplicationEnabled(
        packageName: ApplicationPackageName,
        isEnabled: Boolean
    ) {
        val previous = persistedApplications[packageName]
        val updated = MonitoredApplication(
            packageName = packageName,
            isEnabled = isEnabled,
            addedAt = previous?.addedAt ?: clock.instant()
        )

        persistedApplications = persistedApplications + (packageName to updated)
        _uiState.value = _uiState.value.copy(configurationSaveFailed = false)
        publishState()

        viewModelScope.launch {
            runCatching {
                monitoredApplicationRepository.save(updated)
            }.onFailure {
                persistedApplications = if (previous == null) {
                    persistedApplications - packageName
                } else {
                    persistedApplications + (packageName to previous)
                }
                _uiState.value = _uiState.value.copy(configurationSaveFailed = true)
                publishState()
            }
        }
    }

    private fun publishState() {
        val applicationUiModels = installedApplications.map { application ->
            MonitoredApplicationUiModel(
                packageName = application.packageName,
                label = application.label,
                icon = application.icon,
                isEnabled = persistedApplications[application.packageName]?.isEnabled == true
            )
        }

        _uiState.value = _uiState.value.copy(applications = applicationUiModels)
    }
}
