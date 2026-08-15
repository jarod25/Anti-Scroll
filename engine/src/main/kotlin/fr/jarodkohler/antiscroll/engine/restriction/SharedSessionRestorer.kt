package fr.jarodkohler.antiscroll.engine.restriction

import fr.jarodkohler.antiscroll.domain.restriction.DeviceBootIdentifier
import fr.jarodkohler.antiscroll.domain.restriction.RestrictionProfile
import fr.jarodkohler.antiscroll.domain.restriction.SharedSessionCheckpoint
import fr.jarodkohler.antiscroll.domain.restriction.SharedSessionState
import java.time.Duration
import java.time.Instant

class SharedSessionRestorer {
    fun restore(
        checkpoint: SharedSessionCheckpoint,
        profile: RestrictionProfile,
        currentBootIdentifier: DeviceBootIdentifier,
        now: Instant,
        elapsedRealtime: Duration
    ): SharedSessionState.Active? {
        val policy = profile.sharedSessionPolicy ?: return null
        if (checkpoint.profileIdentifier != profile.identifier) return null
        if (checkpoint.profileVersion != profile.version) return null
        if (elapsedRealtime.isNegative) return null

        val persistedState = checkpoint.state
        if (checkpoint.bootIdentifier == currentBootIdentifier) {
            if (elapsedRealtime < persistedState.lastObservedElapsedRealtime) return null
            return persistedState
        }

        val restoredAccumulatedDuration = if (persistedState.foregroundApplication == null) {
            persistedState.accumulatedForegroundDuration
        } else {
            maxOf(persistedState.accumulatedForegroundDuration, policy.maximumDuration)
        }

        return SharedSessionState.Active(
            startedAt = persistedState.startedAt,
            startedAtElapsedRealtime = elapsedRealtime,
            accumulatedForegroundDuration = restoredAccumulatedDuration,
            foregroundApplication = null,
            foregroundSinceElapsedRealtime = null,
            inactiveSinceElapsedRealtime = elapsedRealtime,
            lastObservedAt = now,
            lastObservedElapsedRealtime = elapsedRealtime
        )
    }
}
