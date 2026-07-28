package fr.jarodkohler.antiscroll.monitoring.usagestats

import fr.jarodkohler.antiscroll.domain.application.ApplicationPackageName
import fr.jarodkohler.antiscroll.domain.observation.AccessibilityMonitoringStatus
import fr.jarodkohler.antiscroll.domain.observation.CollectionGapReason
import fr.jarodkohler.antiscroll.domain.observation.DataCompleteness
import fr.jarodkohler.antiscroll.domain.observation.ObservationWindow
import fr.jarodkohler.antiscroll.domain.observation.UsageAccessStatus
import fr.jarodkohler.antiscroll.domain.observation.UsageCollectionResult
import fr.jarodkohler.antiscroll.monitoring.permission.MonitoringPermissionReader
import fr.jarodkohler.antiscroll.monitoring.permission.MonitoringPermissionSnapshot
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageStatsObservationSourceTest {
    private val packageName = ApplicationPackageName("com.instagram.android")
    private val window = ObservationWindow(
        startInclusive = Instant.ofEpochMilli(1_000L),
        endExclusive = Instant.ofEpochMilli(5_000L)
    )

    @Test
    fun missingUsageAccessStopsBeforeQueryingAndroid() = runTest {
        val gateway = FakeUsageStatsEventGateway(UsageStatsQueryResult.Events(emptyList()))
        val source = source(UsageAccessStatus.MISSING, gateway)

        val result = source.collect(window, setOf(packageName))

        assertEquals(0, gateway.queryCount)
        assertEquals(
            CollectionGapReason.USAGE_ACCESS_MISSING,
            (result as UsageCollectionResult.Unavailable).reason
        )
    }

    @Test
    fun missingUsageAccessIsReportedWithoutSelectedApplications() = runTest {
        val gateway = FakeUsageStatsEventGateway(UsageStatsQueryResult.Events(emptyList()))
        val source = source(UsageAccessStatus.MISSING, gateway)

        val result = source.collect(window, emptySet())

        assertEquals(0, gateway.queryCount)
        assertEquals(
            CollectionGapReason.USAGE_ACCESS_MISSING,
            (result as UsageCollectionResult.Unavailable).reason
        )
    }

    @Test
    fun lockedDeviceIsReportedAsUnavailable() = runTest {
        val source = source(
            UsageAccessStatus.GRANTED,
            FakeUsageStatsEventGateway(UsageStatsQueryResult.DeviceLocked)
        )

        val result = source.collect(window, setOf(packageName))

        assertEquals(
            CollectionGapReason.DEVICE_LOCKED,
            (result as UsageCollectionResult.Unavailable).reason
        )
    }

    @Test
    fun validEmptyQueryRemainsACompleteCollection() = runTest {
        val source = source(
            UsageAccessStatus.GRANTED,
            FakeUsageStatsEventGateway(UsageStatsQueryResult.Events(emptyList()))
        )

        val result = source.collect(window, setOf(packageName)) as UsageCollectionResult.Collected

        assertTrue(result.events.isEmpty())
        assertEquals(DataCompleteness.COMPLETE, result.completeness)
    }

    @Test
    fun successfulQueryNormalizesRecords() = runTest {
        val source = source(
            UsageAccessStatus.GRANTED,
            FakeUsageStatsEventGateway(
                UsageStatsQueryResult.Events(
                    listOf(
                        UsageStatsEventRecord(
                            packageName = packageName.value,
                            activityClassName = "FeedActivity",
                            eventType = UsageStatsActivityEventType.RESUMED,
                            occurredAtEpochMillis = 2_000L
                        )
                    )
                )
            )
        )

        val result = source.collect(window, setOf(packageName)) as UsageCollectionResult.Collected

        assertEquals(1, result.events.size)
        assertEquals(packageName, result.events.single().packageName)
    }

    @Test
    fun emptyPackageSelectionDoesNotQueryAndroid() = runTest {
        val gateway = FakeUsageStatsEventGateway(UsageStatsQueryResult.SourceUnavailable)
        val source = source(UsageAccessStatus.GRANTED, gateway)

        val result = source.collect(window, emptySet()) as UsageCollectionResult.Collected

        assertEquals(0, gateway.queryCount)
        assertTrue(result.events.isEmpty())
    }

    private fun source(
        usageAccessStatus: UsageAccessStatus,
        gateway: UsageStatsEventGateway
    ): UsageStatsObservationSource = UsageStatsObservationSource(
        permissionReader = FakeMonitoringPermissionReader(usageAccessStatus),
        eventGateway = gateway,
        normalizer = UsageStatsEventNormalizer()
    )
}

private class FakeMonitoringPermissionReader(private val usageAccessStatus: UsageAccessStatus) :
    MonitoringPermissionReader {
    override fun read(): MonitoringPermissionSnapshot = MonitoringPermissionSnapshot(
        usageAccessStatus = usageAccessStatus,
        accessibilityStatus = AccessibilityMonitoringStatus.UNSUPPORTED
    )
}

private class FakeUsageStatsEventGateway(private val result: UsageStatsQueryResult) : UsageStatsEventGateway {
    var queryCount: Int = 0
        private set

    override suspend fun query(
        window: ObservationWindow,
        packageNames: Set<ApplicationPackageName>
    ): UsageStatsQueryResult {
        queryCount += 1
        return result
    }
}
