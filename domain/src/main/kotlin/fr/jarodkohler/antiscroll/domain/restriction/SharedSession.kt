package fr.jarodkohler.antiscroll.domain.restriction

import fr.jarodkohler.antiscroll.domain.application.ApplicationPackageName
import java.time.Duration
import java.time.Instant

sealed interface SharedSessionState {
    data object Inactive : SharedSessionState

    data class Active(
        val startedAt: Instant,
        val startedAtElapsedRealtime: Duration,
        val accumulatedForegroundDuration: Duration,
        val foregroundApplication: ApplicationPackageName?,
        val foregroundSinceElapsedRealtime: Duration?,
        val inactiveSinceElapsedRealtime: Duration?,
        val lastObservedAt: Instant,
        val lastObservedElapsedRealtime: Duration
    ) : SharedSessionState {
        init {
            require(!startedAtElapsedRealtime.isNegative) {
                "Session start elapsed realtime must not be negative"
            }
            require(!accumulatedForegroundDuration.isNegative) {
                "Accumulated foreground duration must not be negative"
            }
            require(!lastObservedElapsedRealtime.isNegative) {
                "Last observed elapsed realtime must not be negative"
            }
            require(startedAtElapsedRealtime <= lastObservedElapsedRealtime) {
                "Last observation must not precede the session start"
            }

            if (foregroundApplication == null) {
                require(foregroundSinceElapsedRealtime == null) {
                    "A paused session must not have a foreground start"
                }
                require(inactiveSinceElapsedRealtime != null) {
                    "A paused session must record when inactivity started"
                }
            } else {
                require(foregroundSinceElapsedRealtime != null) {
                    "An active foreground application must record its start"
                }
                require(inactiveSinceElapsedRealtime == null) {
                    "An active foreground application must not be marked inactive"
                }
            }

            foregroundSinceElapsedRealtime?.let { foregroundSince ->
                require(!foregroundSince.isNegative) {
                    "Foreground start elapsed realtime must not be negative"
                }
                require(foregroundSince <= lastObservedElapsedRealtime) {
                    "Foreground start must not follow the last observation"
                }
            }
            inactiveSinceElapsedRealtime?.let { inactiveSince ->
                require(!inactiveSince.isNegative) {
                    "Inactivity start elapsed realtime must not be negative"
                }
                require(inactiveSince <= lastObservedElapsedRealtime) {
                    "Inactivity start must not follow the last observation"
                }
            }
        }

        fun foregroundDurationAt(elapsedRealtime: Duration): Duration {
            require(elapsedRealtime >= lastObservedElapsedRealtime) {
                "Evaluation elapsed realtime must not precede the last observation"
            }

            val currentForegroundDuration = foregroundSinceElapsedRealtime
                ?.let { foregroundSince -> elapsedRealtime.minus(foregroundSince) }
                ?: Duration.ZERO
            return accumulatedForegroundDuration.plus(currentForegroundDuration)
        }
    }
}

sealed interface SharedSessionEvent {
    val observedAt: Instant
    val elapsedRealtime: Duration

    data class ApplicationForegrounded(
        val packageName: ApplicationPackageName,
        override val observedAt: Instant,
        override val elapsedRealtime: Duration
    ) : SharedSessionEvent {
        init {
            requireValidElapsedRealtime(elapsedRealtime)
        }
    }

    data class ApplicationBackgrounded(
        val packageName: ApplicationPackageName,
        override val observedAt: Instant,
        override val elapsedRealtime: Duration
    ) : SharedSessionEvent {
        init {
            requireValidElapsedRealtime(elapsedRealtime)
        }
    }

    data class EndRequested(
        val reason: SharedSessionEndReason,
        override val observedAt: Instant,
        override val elapsedRealtime: Duration
    ) : SharedSessionEvent {
        init {
            requireValidElapsedRealtime(elapsedRealtime)
        }
    }
}

enum class SharedSessionEndReason {
    LIMIT_REACHED,
    INACTIVITY,
    PRIORITY_RESTRICTION,
    DAY_BOUNDARY,
    PROFILE_CHANGED
}

data class SharedSessionReduction(val state: SharedSessionState, val transition: SharedSessionTransition)

sealed interface SharedSessionTransition {
    data class Started(val packageName: ApplicationPackageName) : SharedSessionTransition

    data class ApplicationChanged(
        val previousPackageName: ApplicationPackageName,
        val packageName: ApplicationPackageName
    ) : SharedSessionTransition

    data class Paused(val packageName: ApplicationPackageName) : SharedSessionTransition

    data class Resumed(val packageName: ApplicationPackageName) : SharedSessionTransition

    data class RestartedAfterInactivity(val packageName: ApplicationPackageName) : SharedSessionTransition

    data class Ended(val reason: SharedSessionEndReason) : SharedSessionTransition

    data object DuplicateSignal : SharedSessionTransition

    data object IgnoredStaleSignal : SharedSessionTransition

    data object IgnoredIrrelevantSignal : SharedSessionTransition
}

private fun requireValidElapsedRealtime(elapsedRealtime: Duration) {
    require(!elapsedRealtime.isNegative) { "Elapsed realtime must not be negative" }
}
