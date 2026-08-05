package fr.jarodkohler.antiscroll.engine.restriction

import fr.jarodkohler.antiscroll.domain.restriction.SharedSessionEvent
import fr.jarodkohler.antiscroll.domain.restriction.SharedSessionPolicy
import fr.jarodkohler.antiscroll.domain.restriction.SharedSessionReduction
import fr.jarodkohler.antiscroll.domain.restriction.SharedSessionState
import fr.jarodkohler.antiscroll.domain.restriction.SharedSessionTransition
import java.time.Duration

class SharedSessionReducer {
    fun reduce(
        state: SharedSessionState,
        event: SharedSessionEvent,
        policy: SharedSessionPolicy
    ): SharedSessionReduction = when (state) {
        SharedSessionState.Inactive -> reduceInactive(event)
        is SharedSessionState.Active -> reduceActive(state, event, policy)
    }

    private fun reduceInactive(event: SharedSessionEvent): SharedSessionReduction = when (event) {
        is SharedSessionEvent.ApplicationForegrounded -> SharedSessionReduction(
            state = startSession(event),
            transition = SharedSessionTransition.Started(event.packageName)
        )

        is SharedSessionEvent.ApplicationBackgrounded,
        is SharedSessionEvent.EndRequested -> SharedSessionReduction(
            state = SharedSessionState.Inactive,
            transition = SharedSessionTransition.IgnoredIrrelevantSignal
        )
    }

    private fun reduceActive(
        state: SharedSessionState.Active,
        event: SharedSessionEvent,
        policy: SharedSessionPolicy
    ): SharedSessionReduction {
        if (event.elapsedRealtime < state.lastObservedElapsedRealtime) {
            return SharedSessionReduction(
                state = state,
                transition = SharedSessionTransition.IgnoredStaleSignal
            )
        }

        return when (event) {
            is SharedSessionEvent.ApplicationForegrounded ->
                foregroundApplication(state, event, policy)

            is SharedSessionEvent.ApplicationBackgrounded ->
                backgroundApplication(state, event)

            is SharedSessionEvent.EndRequested -> SharedSessionReduction(
                state = SharedSessionState.Inactive,
                transition = SharedSessionTransition.Ended(event.reason)
            )
        }
    }

    private fun foregroundApplication(
        state: SharedSessionState.Active,
        event: SharedSessionEvent.ApplicationForegrounded,
        policy: SharedSessionPolicy
    ): SharedSessionReduction {
        val foregroundApplication = state.foregroundApplication
        if (foregroundApplication != null) {
            if (foregroundApplication == event.packageName) {
                return SharedSessionReduction(
                    state = state.copy(
                        lastObservedAt = event.observedAt,
                        lastObservedElapsedRealtime = event.elapsedRealtime
                    ),
                    transition = SharedSessionTransition.DuplicateSignal
                )
            }

            val accumulatedDuration = state.accumulatedForegroundDuration.plus(
                event.elapsedRealtime.minus(checkNotNull(state.foregroundSinceElapsedRealtime))
            )
            return SharedSessionReduction(
                state = state.copy(
                    accumulatedForegroundDuration = accumulatedDuration,
                    foregroundApplication = event.packageName,
                    foregroundSinceElapsedRealtime = event.elapsedRealtime,
                    inactiveSinceElapsedRealtime = null,
                    lastObservedAt = event.observedAt,
                    lastObservedElapsedRealtime = event.elapsedRealtime
                ),
                transition = SharedSessionTransition.ApplicationChanged(
                    previousPackageName = foregroundApplication,
                    packageName = event.packageName
                )
            )
        }

        val inactivityDuration = event.elapsedRealtime.minus(
            checkNotNull(state.inactiveSinceElapsedRealtime)
        )
        if (inactivityDuration >= policy.inactivityTimeout) {
            return SharedSessionReduction(
                state = startSession(event),
                transition = SharedSessionTransition.RestartedAfterInactivity(event.packageName)
            )
        }

        return SharedSessionReduction(
            state = state.copy(
                foregroundApplication = event.packageName,
                foregroundSinceElapsedRealtime = event.elapsedRealtime,
                inactiveSinceElapsedRealtime = null,
                lastObservedAt = event.observedAt,
                lastObservedElapsedRealtime = event.elapsedRealtime
            ),
            transition = SharedSessionTransition.Resumed(event.packageName)
        )
    }

    private fun backgroundApplication(
        state: SharedSessionState.Active,
        event: SharedSessionEvent.ApplicationBackgrounded
    ): SharedSessionReduction {
        if (state.foregroundApplication != event.packageName) {
            return SharedSessionReduction(
                state = state.copy(
                    lastObservedAt = event.observedAt,
                    lastObservedElapsedRealtime = event.elapsedRealtime
                ),
                transition = SharedSessionTransition.IgnoredIrrelevantSignal
            )
        }

        val accumulatedDuration = state.accumulatedForegroundDuration.plus(
            event.elapsedRealtime.minus(checkNotNull(state.foregroundSinceElapsedRealtime))
        )
        return SharedSessionReduction(
            state = state.copy(
                accumulatedForegroundDuration = accumulatedDuration,
                foregroundApplication = null,
                foregroundSinceElapsedRealtime = null,
                inactiveSinceElapsedRealtime = event.elapsedRealtime,
                lastObservedAt = event.observedAt,
                lastObservedElapsedRealtime = event.elapsedRealtime
            ),
            transition = SharedSessionTransition.Paused(event.packageName)
        )
    }

    private fun startSession(event: SharedSessionEvent.ApplicationForegrounded): SharedSessionState.Active =
        SharedSessionState.Active(
            startedAt = event.observedAt,
            startedAtElapsedRealtime = event.elapsedRealtime,
            accumulatedForegroundDuration = Duration.ZERO,
            foregroundApplication = event.packageName,
            foregroundSinceElapsedRealtime = event.elapsedRealtime,
            inactiveSinceElapsedRealtime = null,
            lastObservedAt = event.observedAt,
            lastObservedElapsedRealtime = event.elapsedRealtime
        )
}
