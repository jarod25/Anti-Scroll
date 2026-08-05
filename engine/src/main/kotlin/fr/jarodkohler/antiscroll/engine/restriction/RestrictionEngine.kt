package fr.jarodkohler.antiscroll.engine.restriction

import fr.jarodkohler.antiscroll.domain.restriction.RestrictionDecision
import fr.jarodkohler.antiscroll.domain.restriction.RestrictionEvaluationContext
import fr.jarodkohler.antiscroll.domain.restriction.RestrictionRuleOutcome
import fr.jarodkohler.antiscroll.domain.restriction.RestrictionRuleResult

class RestrictionEngine(rules: List<RestrictionRule>) {
    private val rules = rules.toList()

    fun evaluate(context: RestrictionEvaluationContext): RestrictionDecision {
        val evaluatedResults = rules.map { rule -> rule.evaluate(context) }
        val blockingResults = evaluatedResults
            .withIndex()
            .filter { indexedResult ->
                indexedResult.value.outcome == RestrictionRuleOutcome.BLOCK
            }
            .sortedWith(
                compareByDescending<IndexedValue<RestrictionRuleResult>> { indexedResult ->
                    indexedResult.value.priority.value
                }.thenBy { indexedResult -> indexedResult.index }
            )
            .map { indexedResult -> indexedResult.value }

        return if (blockingResults.isEmpty()) {
            RestrictionDecision.Allowed(evaluatedResults = evaluatedResults)
        } else {
            RestrictionDecision.Blocked(
                primaryResult = blockingResults.first(),
                secondaryResults = blockingResults.drop(1),
                evaluatedResults = evaluatedResults
            )
        }
    }
}
