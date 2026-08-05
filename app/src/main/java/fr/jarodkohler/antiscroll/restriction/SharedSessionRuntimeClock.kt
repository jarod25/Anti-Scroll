package fr.jarodkohler.antiscroll.restriction

import android.os.SystemClock
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

interface SharedSessionRuntimeClock {
    fun now(): Instant

    fun elapsedRealtime(): Duration
}

@Singleton
class AndroidSharedSessionRuntimeClock
@Inject
constructor(private val clock: Clock) : SharedSessionRuntimeClock {
    override fun now(): Instant = clock.instant()

    override fun elapsedRealtime(): Duration = Duration.ofMillis(SystemClock.elapsedRealtime())
}
