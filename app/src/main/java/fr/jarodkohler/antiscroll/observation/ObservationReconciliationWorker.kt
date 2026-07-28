package fr.jarodkohler.antiscroll.observation

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import fr.jarodkohler.antiscroll.engine.observation.DailyUsageProjectionCoordinator
import fr.jarodkohler.antiscroll.engine.observation.ObservationReconciliationCoordinator

@HiltWorker
class ObservationReconciliationWorker
@AssistedInject
constructor(
    @Assisted appContext: Context,
    @Assisted workerParameters: WorkerParameters,
    private val reconciliationCoordinator: ObservationReconciliationCoordinator,
    private val projectionCoordinator: DailyUsageProjectionCoordinator
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result = runCatching {
        val reconciliationReport = reconciliationCoordinator.reconcile()
        val projectionReport = projectionCoordinator.rebuild(reconciliationReport)
        reconciliationReport to projectionReport
    }.fold(
        onSuccess = { (reconciliationReport, projectionReport) ->
            if (reconciliationReport.shouldRetry) {
                Result.retry()
            } else {
                Result.success(
                    Data.Builder()
                        .putInt(OUTPUT_INSERTED_EVENT_COUNT, reconciliationReport.insertedEventCount)
                        .putInt(OUTPUT_DUPLICATE_EVENT_COUNT, reconciliationReport.duplicateEventCount)
                        .putString(
                            OUTPUT_COLLECTION_STATUS,
                            reconciliationReport.health.collectionStatus.name
                        ).putInt(OUTPUT_REBUILT_DATE_COUNT, projectionReport.rebuiltDates.size)
                        .putInt(OUTPUT_PROJECTED_ROW_COUNT, projectionReport.projectedRowCount)
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
        const val OUTPUT_REBUILT_DATE_COUNT = "rebuilt_date_count"
        const val OUTPUT_PROJECTED_ROW_COUNT = "projected_row_count"
    }
}
