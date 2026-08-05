package fr.jarodkohler.antiscroll.engine.restriction

import fr.jarodkohler.antiscroll.domain.restriction.RestrictionEvaluationContext
import fr.jarodkohler.antiscroll.domain.restriction.RestrictionPriority
import fr.jarodkohler.antiscroll.domain.restriction.RestrictionReasonCode
import fr.jarodkohler.antiscroll.domain.restriction.RestrictionRuleResult
import fr.jarodkohler.antiscroll.domain.restriction.SharedSessionState

class SessionLimitRule : RestrictionRule {
    override fun evaluate(context: RestrictionEvaluationContext): RestrictionRuleResult {
        val policy = context.profile.sharedSessionPolicy
            ?: return RestrictionRuleResult.notApplicable(RestrictionPriority.SESSION_LIMIT)
        val activeSession = context.sharedSessionState as? SharedSessionState.Active
            ?: return RestrictionRuleResult.notApplicable(RestrictionPriority.SESSION_LIMIT)
        val foregroundDuration = activeSession.foregroundDurationAt(context.elapsedRealtime)

        return if (foregroundDuration >= policy.maximumDuration) {
            RestrictionRuleResult.blocked(
                priority = RestrictionPriority.SESSION_LIMIT,
                reasonCode = RestrictionReasonCode.SESSION_LIMIT_REACHED,
                metadata = mapOf(
                    SESSION_DURATION_MILLIS to foregroundDuration.toMillis().toString(),
                    SESSION_LIMIT_MILLIS to policy.maximumDuration.toMillis().toString()
                )
            )
        } else {
            RestrictionRuleResult.allowed(RestrictionPriority.SESSION_LIMIT)
        }
    }

    companion object {
        const val SESSION_DURATION_MILLIS = "session_duration_millis"
        const val SESSION_LIMIT_MILLIS = "session_limit_millis"
    }
}
