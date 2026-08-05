package fr.jarodkohler.antiscroll.engine.restriction

import fr.jarodkohler.antiscroll.domain.application.ApplicationPackageName
import fr.jarodkohler.antiscroll.domain.restriction.RestrictionDecision
import fr.jarodkohler.antiscroll.domain.restriction.RestrictionEvaluationContext
import fr.jarodkohler.antiscroll.domain.restriction.RestrictionPriority
import fr.jarodkohler.antiscroll.domain.restriction.RestrictionProfile
import fr.jarodkohler.antiscroll.domain.restriction.RestrictionProfileIdentifier
import fr.jarodkohler.antiscroll.domain.restriction.RestrictionReasonCode
import fr.jarodkohler.antiscroll.domain.restriction.RestrictionRuleResult
import fr.jarodkohler.antiscroll.domain.restriction.SharedSessionState
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RestrictionEngineTest {
    private val context = RestrictionEvaluationContext(
        targetPackageName = ApplicationPackageName("com.zhiliaoapp.musically"),
        evaluatedAt = Instant.parse("2026-08-05T10:00:00Z"),
        elapsedRealtime = Duration.ofHours(1),
        profile = RestrictionProfile(
            identifier = RestrictionProfileIdentifier("normal"),
            version = 1,
            sharedSessionPolicy = null
        ),
        sharedSessionState = SharedSessionState.Inactive
    )

    @Test
    fun noBlockingResultProducesAllowedDecision() {
        val engine = RestrictionEngine(
            listOf(
                fixedRule(RestrictionRuleResult.allowed(RestrictionPriority.NORMAL_ALLOWANCE)),
                fixedRule(RestrictionRuleResult.notApplicable(RestrictionPriority.SESSION_LIMIT))
            )
        )

        val decision = engine.evaluate(context)

        assertTrue(decision is RestrictionDecision.Allowed)
        assertEquals(2, decision.evaluatedResults.size)
    }

    @Test
    fun highestPriorityBlockBecomesPrimaryAndOthersRemainAvailable() {
        val sessionResult = blocked("session", RestrictionPriority.SESSION_LIMIT)
        val cooldownResult = blocked("cooldown", RestrictionPriority.COOLDOWN)
        val engine = RestrictionEngine(
            listOf(fixedRule(sessionResult), fixedRule(cooldownResult))
        )

        val decision = engine.evaluate(context) as RestrictionDecision.Blocked

        assertEquals(cooldownResult, decision.primaryResult)
        assertEquals(listOf(sessionResult), decision.secondaryResults)
    }

    @Test
    fun equalPrioritiesKeepRuleRegistrationOrder() {
        val first = blocked("first", RestrictionPriority.SESSION_LIMIT)
        val second = blocked("second", RestrictionPriority.SESSION_LIMIT)
        val engine = RestrictionEngine(listOf(fixedRule(first), fixedRule(second)))

        val decision = engine.evaluate(context) as RestrictionDecision.Blocked

        assertEquals(first, decision.primaryResult)
        assertEquals(listOf(second), decision.secondaryResults)
    }

    @Test
    fun sameContextAndRulesProduceSameDecision() {
        val engine = RestrictionEngine(
            listOf(fixedRule(blocked("session", RestrictionPriority.SESSION_LIMIT)))
        )

        assertEquals(engine.evaluate(context), engine.evaluate(context))
    }

    private fun fixedRule(result: RestrictionRuleResult): RestrictionRule =
        RestrictionRule { result }

    private fun blocked(
        reason: String,
        priority: RestrictionPriority
    ): RestrictionRuleResult = RestrictionRuleResult.blocked(
        priority = priority,
        reasonCode = RestrictionReasonCode(reason)
    )
}
