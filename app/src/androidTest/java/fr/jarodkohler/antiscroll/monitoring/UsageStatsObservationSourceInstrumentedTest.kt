package fr.jarodkohler.antiscroll.monitoring

import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fr.jarodkohler.antiscroll.MainActivity
import fr.jarodkohler.antiscroll.domain.application.ApplicationPackageName
import fr.jarodkohler.antiscroll.domain.observation.ObservationWindow
import fr.jarodkohler.antiscroll.domain.observation.UsageAccessStatus
import fr.jarodkohler.antiscroll.domain.observation.UsageCollectionResult
import fr.jarodkohler.antiscroll.domain.observation.UsageEventType
import fr.jarodkohler.antiscroll.monitoring.accessibility.AccessibilityMonitoringConnection
import fr.jarodkohler.antiscroll.monitoring.permission.AndroidMonitoringPermissionReader
import fr.jarodkohler.antiscroll.monitoring.usagestats.AndroidUsageStatsEventGateway
import fr.jarodkohler.antiscroll.monitoring.usagestats.UsageStatsEventNormalizer
import fr.jarodkohler.antiscroll.monitoring.usagestats.UsageStatsObservationSource
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UsageStatsObservationSourceInstrumentedTest {
    @Test
    fun recentTargetActivityIsCollectedAndNormalized() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val permissionReader = AndroidMonitoringPermissionReader(
            context = context,
            accessibilityConnection = AccessibilityMonitoringConnection()
        )
        assumeTrue(permissionReader.read().usageAccessStatus == UsageAccessStatus.GRANTED)

        val windowStart = Instant.now().minusSeconds(5)
        ActivityScenario.launch<MainActivity>(MainActivity::class.java).use {
            instrumentation.waitForIdleSync()
            SystemClock.sleep(500L)

            val source = UsageStatsObservationSource(
                permissionReader = permissionReader,
                eventGateway = AndroidUsageStatsEventGateway(context),
                normalizer = UsageStatsEventNormalizer()
            )
            val result = source.collect(
                window = ObservationWindow(
                    startInclusive = windowStart,
                    endExclusive = Instant.now().plusMillis(1L)
                ),
                packageNames = setOf(ApplicationPackageName(context.packageName))
            )

            assertTrue(result is UsageCollectionResult.Collected)
            val events = (result as UsageCollectionResult.Collected).events
            assertTrue(events.any { event -> event.type == UsageEventType.FOREGROUND_ENTERED })
        }
    }
}
