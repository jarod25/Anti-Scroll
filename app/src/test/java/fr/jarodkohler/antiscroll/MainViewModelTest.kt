package fr.jarodkohler.antiscroll

import fr.jarodkohler.antiscroll.applicationconfig.InstalledApplicationPresentation
import fr.jarodkohler.antiscroll.applicationconfig.InstalledApplicationResolver
import fr.jarodkohler.antiscroll.applicationconfig.SupportedApplication
import fr.jarodkohler.antiscroll.domain.application.ApplicationPackageName
import fr.jarodkohler.antiscroll.domain.application.MonitoredApplication
import fr.jarodkohler.antiscroll.domain.observation.AccessibilityMonitoringStatus
import fr.jarodkohler.antiscroll.domain.observation.MonitoredApplicationRepository
import fr.jarodkohler.antiscroll.domain.observation.UsageAccessStatus
import fr.jarodkohler.antiscroll.monitoring.permission.MonitoringPermissionReader
import fr.jarodkohler.antiscroll.monitoring.permission.MonitoringPermissionSnapshot
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
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
    fun grantedUsageAccessLoadsInstalledAppsAndPersistsSelection() = runTest(testDispatcher) {
        val instagramPackage = ApplicationPackageName("com.instagram.android")
        val repository = FakeMonitoredApplicationRepository()
        val resolver = FakeInstalledApplicationResolver(
            installedApplications = listOf(
                InstalledApplicationPresentation(
                    packageName = instagramPackage,
                    label = "Instagram",
                    icon = null
                )
            )
        )
        val viewModel = MainViewModel(
            permissionReader = FakeMonitoringPermissionReader(UsageAccessStatus.GRANTED),
            monitoredApplicationRepository = repository,
            installedApplicationResolver = resolver,
            clock = Clock.fixed(Instant.ofEpochMilli(1_000L), ZoneOffset.UTC)
        )

        advanceUntilIdle()
        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(1, resolver.resolveCount)
        assertEquals("Instagram", viewModel.uiState.value.applications.single().label)
        assertFalse(viewModel.uiState.value.applications.single().isEnabled)

        viewModel.setApplicationEnabled(instagramPackage, true)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.applications.single().isEnabled)
        assertEquals(true, repository.savedApplications.single().isEnabled)
        assertEquals(Instant.ofEpochMilli(1_000L), repository.savedApplications.single().addedAt)
    }

    @Test
    fun missingUsageAccessDoesNotInspectInstalledApplications() = runTest(testDispatcher) {
        val resolver = FakeInstalledApplicationResolver(emptyList())
        val viewModel = MainViewModel(
            permissionReader = FakeMonitoringPermissionReader(UsageAccessStatus.MISSING),
            monitoredApplicationRepository = FakeMonitoredApplicationRepository(),
            installedApplicationResolver = resolver,
            clock = Clock.systemUTC()
        )

        advanceUntilIdle()
        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(0, resolver.resolveCount)
        assertTrue(viewModel.uiState.value.applications.isEmpty())
        assertEquals(
            UsageAccessStatus.MISSING,
            viewModel.uiState.value.permissionSnapshot.usageAccessStatus
        )
    }
}

private class FakeMonitoringPermissionReader(private val usageAccessStatus: UsageAccessStatus) :
    MonitoringPermissionReader {
    override fun read(): MonitoringPermissionSnapshot = MonitoringPermissionSnapshot(
        usageAccessStatus = usageAccessStatus,
        accessibilityStatus = AccessibilityMonitoringStatus.UNSUPPORTED
    )
}

private class FakeInstalledApplicationResolver(
    private val installedApplications: List<InstalledApplicationPresentation>
) : InstalledApplicationResolver {
    var resolveCount = 0
        private set

    override suspend fun resolveInstalled(catalog: List<SupportedApplication>): List<InstalledApplicationPresentation> {
        resolveCount += 1
        return installedApplications
    }
}

private class FakeMonitoredApplicationRepository : MonitoredApplicationRepository {
    private val applications = MutableStateFlow<List<MonitoredApplication>>(emptyList())
    val savedApplications = mutableListOf<MonitoredApplication>()

    override fun observeAll(): Flow<List<MonitoredApplication>> = applications

    override suspend fun allApplications(): List<MonitoredApplication> = applications.value

    override suspend fun enabledApplications(): List<MonitoredApplication> =
        applications.value.filter(MonitoredApplication::isEnabled)

    override suspend fun save(application: MonitoredApplication) {
        savedApplications += application
        applications.value =
            applications.value.filterNot { existing -> existing.packageName == application.packageName } + application
    }
}
