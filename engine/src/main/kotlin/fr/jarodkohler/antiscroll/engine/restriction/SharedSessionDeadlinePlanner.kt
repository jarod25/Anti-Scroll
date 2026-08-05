package fr.jarodkohler.antiscroll.engine.restriction

import fr.jarodkohler.antiscroll.domain.application.ApplicationPackageName
import fr.jarodkohler.antiscroll.domain.restriction.SharedSessionPolicy
import fr.jarodkohler.antiscroll.domain.restriction.SharedSessionState
import java.time.Duration

data class SharedSessionDeadline(val packageName: ApplicationPackageName, val elapsedRealtime: Duration) {
    init {
        require(!elapsedRealtime.isNegative) { "Session deadline elapsed realtime must not be negative" }
    }
}

class SharedSessionDeadlinePlanner {
    fun plan(state: SharedSessionState, policy: SharedSessionPolicy): SharedSessionDeadline? {
        val activeState = state as? SharedSessionState.Active ?: return null
        val packageName = activeState.foregroundApplication ?: return null
        val foregroundDuration = activeState.foregroundDurationAt(activeState.lastObservedElapsedRealtime)
        val remainingDuration = policy.maximumDuration.minus(foregroundDuration)
        val nonNegativeRemainingDuration = maxOf(Duration.ZERO, remainingDuration)

        return SharedSessionDeadline(
            packageName = packageName,
            elapsedRealtime = activeState.lastObservedElapsedRealtime.plus(nonNegativeRemainingDuration)
        )
    }
}
