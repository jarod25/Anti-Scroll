package fr.jarodkohler.antiscroll.monitoring.usagestats

import android.app.usage.UsageEvents
import android.app.usage.UsageEventsQuery
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.UserManager
import androidx.annotation.RequiresApi
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.jarodkohler.antiscroll.domain.application.ApplicationPackageName
import fr.jarodkohler.antiscroll.domain.observation.ObservationWindow
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class AndroidUsageStatsEventGateway
@Inject
constructor(@param:ApplicationContext private val context: Context) :
    UsageStatsEventGateway {
    override suspend fun query(
        window: ObservationWindow,
        packageNames: Set<ApplicationPackageName>
    ): UsageStatsQueryResult = withContext(Dispatchers.IO) {
        if (packageNames.isEmpty()) return@withContext UsageStatsQueryResult.Events(emptyList())

        val userManager = context.getSystemService(UserManager::class.java)
            ?: return@withContext UsageStatsQueryResult.SourceUnavailable
        if (!userManager.isUserUnlocked) return@withContext UsageStatsQueryResult.DeviceLocked

        val usageStatsManager = context.getSystemService(UsageStatsManager::class.java)
            ?: return@withContext UsageStatsQueryResult.SourceUnavailable

        val usageEvents = try {
            queryUsageEvents(usageStatsManager, window, packageNames)
        } catch (_: SecurityException) {
            return@withContext UsageStatsQueryResult.UsageAccessMissing
        } catch (_: RuntimeException) {
            return@withContext UsageStatsQueryResult.SourceUnavailable
        }

        if (usageEvents == null) {
            return@withContext if (userManager.isUserUnlocked) {
                UsageStatsQueryResult.SourceUnavailable
            } else {
                UsageStatsQueryResult.DeviceLocked
            }
        }

        UsageStatsQueryResult.Events(readRecords(usageEvents, packageNames))
    }

    private fun queryUsageEvents(
        usageStatsManager: UsageStatsManager,
        window: ObservationWindow,
        packageNames: Set<ApplicationPackageName>
    ): UsageEvents? = if (Build.VERSION.SDK_INT >= 35) {
        queryFiltered(usageStatsManager, window, packageNames)
    } else {
        usageStatsManager.queryEvents(
            window.startInclusive.toEpochMilli(),
            window.endExclusive.toEpochMilli()
        )
    }

    @RequiresApi(35)
    private fun queryFiltered(
        usageStatsManager: UsageStatsManager,
        window: ObservationWindow,
        packageNames: Set<ApplicationPackageName>
    ): UsageEvents? {
        val query = UsageEventsQuery.Builder(
            window.startInclusive.toEpochMilli(),
            window.endExclusive.toEpochMilli()
        ).setPackageNames(*packageNames.map { packageName -> packageName.value }.toTypedArray())
            .setEventTypes(
                UsageEvents.Event.ACTIVITY_RESUMED,
                UsageEvents.Event.ACTIVITY_PAUSED
            )
            .build()

        return usageStatsManager.queryEvents(query)
    }

    private fun readRecords(
        usageEvents: UsageEvents,
        packageNames: Set<ApplicationPackageName>
    ): List<UsageStatsEventRecord> {
        val allowedPackages = packageNames.mapTo(mutableSetOf(), ApplicationPackageName::value)
        val event = UsageEvents.Event()
        val records = mutableListOf<UsageStatsEventRecord>()

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            val eventType = event.eventType.toActivityEventType() ?: continue
            val packageName = event.packageName ?: continue
            if (packageName !in allowedPackages) continue

            records += UsageStatsEventRecord(
                packageName = packageName,
                activityClassName = event.className,
                instanceId = event.instanceId,
                eventType = eventType,
                occurredAtEpochMillis = event.timeStamp
            )
        }

        return records
    }
}

private fun Int.toActivityEventType(): UsageStatsActivityEventType? = when (this) {
    UsageEvents.Event.ACTIVITY_RESUMED -> UsageStatsActivityEventType.RESUMED
    UsageEvents.Event.ACTIVITY_PAUSED -> UsageStatsActivityEventType.PAUSED
    else -> null
}
