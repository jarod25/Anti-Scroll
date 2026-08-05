package fr.jarodkohler.antiscroll.observation

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

interface ObservationWorkScheduler {
    fun ensurePeriodicReconciliation()

    fun requestImmediateReconciliation()
}

@Singleton
class AndroidObservationWorkScheduler
@Inject
constructor(
    @ApplicationContext context: Context,
    private val policy: ObservationSchedulingPolicy
) : ObservationWorkScheduler {
    private val workManager = WorkManager.getInstance(context)

    override fun ensurePeriodicReconciliation() {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(policy.requiresBatteryNotLow)
            .build()
        val request = PeriodicWorkRequest.Builder(
            ObservationReconciliationWorker::class.java,
            policy.periodicInterval.toMinutes(),
            TimeUnit.MINUTES
        ).setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                policy.retryBackoff.toMillis(),
                TimeUnit.MILLISECONDS
            ).addTag(PERIODIC_TAG)
            .build()

        workManager.enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    override fun requestImmediateReconciliation() {
        val request = OneTimeWorkRequest.Builder(ObservationReconciliationWorker::class.java)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                policy.retryBackoff.toMillis(),
                TimeUnit.MILLISECONDS
            ).addTag(IMMEDIATE_TAG)
            .build()

        workManager.enqueueUniqueWork(
            IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    companion object {
        const val PERIODIC_WORK_NAME = "observation-periodic-reconciliation-v1"
        const val IMMEDIATE_WORK_NAME = "observation-immediate-reconciliation-v1"
        const val PERIODIC_TAG = "observation-periodic-reconciliation"
        const val IMMEDIATE_TAG = "observation-immediate-reconciliation"
    }
}
