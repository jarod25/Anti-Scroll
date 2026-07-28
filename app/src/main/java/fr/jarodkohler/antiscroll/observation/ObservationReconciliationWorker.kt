package fr.jarodkohler.antiscroll.observation

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import fr.jarodkohler.antiscroll.engine.observation.ObservationReconciliationCoordinator

@HiltWorker
class ObservationReconciliationWorker
@AssistedInject
constructor(
    @Assisted appContext: Context,
    @Assisted workerParameters: WorkerParameters,
    private val coordinator: ObservationReconciliationCoordinator
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result = runCatching {
        coordinator.reconcile()
    }.fold(
        onSuccess = { report ->
            if (report.shouldRetry) {
                Result.retry()
            } else {
                Result.success(
                    Data.Builder()
                        .putInt(OUTPUT_INSERTED_EVENT_COUNT, report.insertedEventCount)
                        .putInt(OUTPUT_DUPLICATE_EVENT_COUNT, report.duplicateEventCount)
                        .putString(OUTPUT_COLLECTION_STATUS, report.health.collectionStatus.name)
                        .build()
                )
            }
        },
        onFailure = {
            Result.retry()
        }
    )

    companion object {
        const val OUTPUT_INSERTED_EVENT_COUNT = "inserted_event_count"
        const val OUTPUT_DUPLICATE_EVENT_COUNT = "duplicate_event_count"
        const val OUTPUT_COLLECTION_STATUS = "collection_status"
    }
}
