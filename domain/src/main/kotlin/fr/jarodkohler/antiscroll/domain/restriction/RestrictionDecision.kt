package fr.jarodkohler.antiscroll.domain.restriction

import fr.jarodkohler.antiscroll.domain.application.ApplicationPackageName
import java.time.Duration
import java.time.Instant

@JvmInline
value class RestrictionReasonCode(val value: String) {
    init {
        require(value.isNotBlank()) { "Restriction reason code must not be blank" }
        require(value.none { character -> character.isWhitespace() }) {
            "Restriction reason code must not contain whitespace"
        }
    }

    companion object {
        val SESSION_LIMIT_REACHED = RestrictionReasonCode("session_limit_reached")
    }
}

@JvmInline
value class RestrictionPriority(val value: Int) {
    init {
        require(value >= 0) { "Restriction priority must not be negative" }
    }

    companion object {
        val NORMAL_ALLOWANCE = RestrictionPriority(0)
        val SESSION_LIMIT = RestrictionPriority(200)
        val APPLICATION_QUOTA = RestrictionPriority(300)
        val GLOBAL_QUOTA = RestrictionPriority(400)
        val COOLDOWN = RestrictionPriority(500)
        val SCHEDULED_BLOCK = RestrictionPriority(600)
        val TECHNICAL_HEALTH = RestrictionPriority(700)
    }
}

enum class RestrictionRuleOutcome {
    ALLOW,
    BLOCK,
    NOT_APPLICABLE
}

data class RestrictionRuleResult(
    val outcome: RestrictionRuleOutcome,
    val priority: RestrictionPriority,
    val reasonCode: RestrictionReasonCode? = null,
    val restrictionEnd: Instant? = null,
    val metadata: Map<String, String> = emptyMap()
) {
    init {
        require(outcome != RestrictionRuleOutcome.BLOCK || reasonCode != null) {
            "A blocking rule result must provide a reason code"
        }
        require(outcome != RestrictionRuleOutcome.NOT_APPLICABLE || reasonCode == null) {
            "A non-applicable rule result must not provide a reason code"
        }
        require(outcome == RestrictionRuleOutcome.BLOCK || restrictionEnd == null) {
            "Only a blocking rule result may provide a restriction end"
        }
    }

    companion object {
        fun allowed(priority: RestrictionPriority): RestrictionRuleResult = RestrictionRuleResult(
            outcome = RestrictionRuleOutcome.ALLOW,
            priority = priority
        )

        fun blocked(
            priority: RestrictionPriority,
            reasonCode: RestrictionReasonCode,
            restrictionEnd: Instant? = null,
            metadata: Map<String, String> = emptyMap()
        ): RestrictionRuleResult = RestrictionRuleResult(
            outcome = RestrictionRuleOutcome.BLOCK,
            priority = priority,
            reasonCode = reasonCode,
            restrictionEnd = restrictionEnd,
            metadata = metadata.toMap()
        )

        fun notApplicable(priority: RestrictionPriority): RestrictionRuleResult = RestrictionRuleResult(
            outcome = RestrictionRuleOutcome.NOT_APPLICABLE,
            priority = priority
        )
    }
}

data class RestrictionEvaluationContext(
    val targetPackageName: ApplicationPackageName,
    val evaluatedAt: Instant,
    val elapsedRealtime: Duration,
    val profile: RestrictionProfile,
    val sharedSessionState: SharedSessionState
) {
    init {
        require(!elapsedRealtime.isNegative) { "Evaluation elapsed realtime must not be negative" }
    }
}

sealed interface RestrictionDecision {
    val evaluatedResults: List<RestrictionRuleResult>

    data class Allowed(override val evaluatedResults: List<RestrictionRuleResult>) : RestrictionDecision

    data class Blocked(
        val primaryResult: RestrictionRuleResult,
        val secondaryResults: List<RestrictionRuleResult>,
        override val evaluatedResults: List<RestrictionRuleResult>
    ) : RestrictionDecision {
        init {
            require(primaryResult.outcome == RestrictionRuleOutcome.BLOCK) {
                "The primary restriction result must be blocking"
            }
            require(
                secondaryResults.all { result ->
                    result.outcome == RestrictionRuleOutcome.BLOCK
                }
            ) {
                "Secondary restriction results must be blocking"
            }
        }
    }
}
