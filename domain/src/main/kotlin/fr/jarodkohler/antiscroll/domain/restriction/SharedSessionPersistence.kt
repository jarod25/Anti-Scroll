package fr.jarodkohler.antiscroll.domain.restriction

import fr.jarodkohler.antiscroll.domain.application.ApplicationPackageName
import java.time.Duration
import java.time.Instant

@JvmInline
value class DeviceBootIdentifier(val value: Int) {
    init {
        require(value >= 0) { "Device boot identifier must not be negative" }
    }
}

data class SharedSessionCheckpoint(
    val profileIdentifier: RestrictionProfileIdentifier,
    val profileVersion: Int,
    val bootIdentifier: DeviceBootIdentifier,
    val startedAt: Instant,
    val startedAtElapsedRealtime: Duration,
    val accumulatedForegroundDuration: Duration,
    val foregroundApplication: ApplicationPackageName?,
    val foregroundSinceElapsedRealtime: Duration?,
    val inactiveSinceElapsedRealtime: Duration?,
    val lastObservedAt: Instant,
    val lastObservedElapsedRealtime: Duration
) {
    init {
        require(profileVersion > 0) { "Checkpoint profile version must be positive" }
        require(!startedAtElapsedRealtime.isNegative) {
            "Checkpoint session start elapsed realtime must not be negative"
        }
        require(!accumulatedForegroundDuration.isNegative) {
            "Checkpoint accumulated foreground duration must not be negative"
        }
        require(!lastObservedElapsedRealtime.isNegative) {
            "Checkpoint last observed elapsed realtime must not be negative"
        }
        require(startedAtElapsedRealtime <= lastObservedElapsedRealtime) {
            "Checkpoint last observation must not precede the session start"
        }

        if (foregroundApplication == null) {
            require(foregroundSinceElapsedRealtime == null) {
                "A paused checkpoint must not have a foreground start"
            }
            require(inactiveSinceElapsedRealtime != null) {
                "A paused checkpoint must record when inactivity started"
            }
        } else {
            require(foregroundSinceElapsedRealtime != null) {
                "A foreground checkpoint must record its foreground start"
            }
            require(inactiveSinceElapsedRealtime == null) {
                "A foreground checkpoint must not be marked inactive"
            }
        }

        foregroundSinceElapsedRealtime?.let { foregroundSince ->
            require(!foregroundSince.isNegative) {
                "Checkpoint foreground start elapsed realtime must not be negative"
            }
            require(foregroundSince <= lastObservedElapsedRealtime) {
                "Checkpoint foreground start must not follow the last observation"
            }
        }
        inactiveSinceElapsedRealtime?.let { inactiveSince ->
            require(!inactiveSince.isNegative) {
                "Checkpoint inactivity start elapsed realtime must not be negative"
            }
            require(inactiveSince <= lastObservedElapsedRealtime) {
                "Checkpoint inactivity start must not follow the last observation"
            }
        }
    }

    fun toState(): SharedSessionState.Active = SharedSessionState.Active(
        startedAt = startedAt,
        startedAtElapsedRealtime = startedAtElapsedRealtime,
        accumulatedForegroundDuration = accumulatedForegroundDuration,
        foregroundApplication = foregroundApplication,
        foregroundSinceElapsedRealtime = foregroundSinceElapsedRealtime,
        inactiveSinceElapsedRealtime = inactiveSinceElapsedRealtime,
        lastObservedAt = lastObservedAt,
        lastObservedElapsedRealtime = lastObservedElapsedRealtime
    )

    companion object {
        fun from(
            profile: RestrictionProfile,
            state: SharedSessionState.Active,
            bootIdentifier: DeviceBootIdentifier
        ): SharedSessionCheckpoint = SharedSessionCheckpoint(
            profileIdentifier = profile.identifier,
            profileVersion = profile.version,
            bootIdentifier = bootIdentifier,
            startedAt = state.startedAt,
            startedAtElapsedRealtime = state.startedAtElapsedRealtime,
            accumulatedForegroundDuration = state.accumulatedForegroundDuration,
            foregroundApplication = state.foregroundApplication,
            foregroundSinceElapsedRealtime = state.foregroundSinceElapsedRealtime,
            inactiveSinceElapsedRealtime = state.inactiveSinceElapsedRealtime,
            lastObservedAt = state.lastObservedAt,
            lastObservedElapsedRealtime = state.lastObservedElapsedRealtime
        )
    }
}

interface SharedSessionStateRepository {
    suspend fun load(): SharedSessionCheckpoint?

    suspend fun save(checkpoint: SharedSessionCheckpoint)

    suspend fun clear()
}
