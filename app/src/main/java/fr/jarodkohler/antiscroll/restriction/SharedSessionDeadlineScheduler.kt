package fr.jarodkohler.antiscroll.restriction

import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

fun interface SharedSessionDeadlineHandle {
    fun cancel()
}

interface SharedSessionDeadlineScheduler {
    fun schedule(delayDuration: Duration, action: suspend () -> Unit): SharedSessionDeadlineHandle
}

@Singleton
class CoroutineSharedSessionDeadlineScheduler
@Inject
constructor(
    @param:ApplicationCoroutineScope private val applicationScope: CoroutineScope
) : SharedSessionDeadlineScheduler {
    override fun schedule(delayDuration: Duration, action: suspend () -> Unit): SharedSessionDeadlineHandle {
        require(!delayDuration.isNegative) { "Session deadline delay must not be negative" }

        val job = applicationScope.launch {
            delay(delayDuration.toMillis())
            action()
        }
        return JobSharedSessionDeadlineHandle(job)
    }

    private class JobSharedSessionDeadlineHandle(private val job: Job) : SharedSessionDeadlineHandle {
        override fun cancel() {
            job.cancel()
        }
    }
}
