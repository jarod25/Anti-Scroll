package fr.jarodkohler.antiscroll.engine.restriction

import fr.jarodkohler.antiscroll.domain.application.ApplicationPackageName
import fr.jarodkohler.antiscroll.domain.restriction.RestrictionEvaluationContext
import fr.jarodkohler.antiscroll.domain.restriction.RestrictionProfile
import fr.jarodkohler.antiscroll.domain.restriction.RestrictionProfileIdentifier
import fr.jarodkohler.antiscroll.domain.restriction.RestrictionReasonCode
import fr.jarodkohler.antiscroll.domain.restriction.RestrictionRuleOutcome
import fr.jarodkohler.antiscroll.domain.restriction.SharedSessionEvent
import fr.jarodkohler.antiscroll.domain.restriction.SharedSessionPolicy
import fr.jarodkohler.antiscroll.domain.restriction.SharedSessionState
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionLimitRuleTest {
    private val rule = SessionLimitRule()
    private val reducer = SharedSessionReducer()
    private val tikTok = ApplicationPackageName("com.zhiliaoapp.musically")
    private val origin = Instant.parse("2026-08-05T10:00:00Z")
    private val policy = SharedSessionPolicy(
        maximumDuration = Duration.ofMinutes(10),
        inactivityTimeout = Duration.ofMinutes(2)
    )
    private val profile = RestrictionProfile(
        identifier = RestrictionProfileIdentifier("normal"),
        version = 1,
        sharedSessionPolicy = policy
    )

    @Test
    fun activeSessionBelowLimitIsAllowed() {
        val result = rule.evaluate(context(activeSession(), minute = 9))

        assertEquals(RestrictionRuleOutcome.ALLOW, result.outcome)
    }

    @Test
    fun activeSessionAtLimitIsBlockedWithStableMetadata() {
        val result = rule.evaluate(context(activeSession(), minute = 10))

        assertEquals(RestrictionRuleOutcome.BLOCK, result.outcome)
        assertEquals(RestrictionReasonCode.SESSION_LIMIT_REACHED, result.reasonCode)
        assertEquals("600000", result.metadata[SessionLimitRule.SESSION_DURATION_MILLIS])
        assertEquals("600000", result.metadata[SessionLimitRule.SESSION_LIMIT_MILLIS])
    }

    @Test
    fun inactiveSessionAndProfileWithoutSessionPolicyAreNotApplicable() {
        val inactiveResult = rule.evaluate(context(SharedSessionState.Inactive, minute = 10))
        val observationResult = rule.evaluate(
            context(
                state = activeSession(),
                minute = 10,
                activeProfile = RestrictionProfile(
                    identifier = RestrictionProfileIdentifier("observation"),
                    version = 1,
                    sharedSessionPolicy = null
                )
            )
        )

        assertEquals(RestrictionRuleOutcome.NOT_APPLICABLE, inactiveResult.outcome)
        assertEquals(RestrictionRuleOutcome.NOT_APPLICABLE, observationResult.outcome)
    }

    @Test
    fun pausedSessionDoesNotAccumulateUnmonitoredTime() {
        val paused = reducer.reduce(
            activeSession(),
            SharedSessionEvent.ApplicationBackgrounded(
                packageName = tikTok,
                observedAt = origin.plusSeconds(240),
                elapsedRealtime = Duration.ofMinutes(4)
            ),
            policy
        ).state
        val result = rule.evaluate(context(paused, minute = 20))

        assertEquals(RestrictionRuleOutcome.ALLOW, result.outcome)
    }

    private fun activeSession(): SharedSessionState = reducer.reduce(
        SharedSessionState.Inactive,
        SharedSessionEvent.ApplicationForegrounded(
            packageName = tikTok,
            observedAt = origin,
            elapsedRealtime = Duration.ZERO
        ),
        policy
    ).state

    private fun context(
        state: SharedSessionState,
        minute: Long,
        activeProfile: RestrictionProfile = profile
    ): RestrictionEvaluationContext = RestrictionEvaluationContext(
        targetPackageName = tikTok,
        evaluatedAt = origin.plusSeconds(minute * 60),
        elapsedRealtime = Duration.ofMinutes(minute),
        profile = activeProfile,
        sharedSessionState = state
    )
}
