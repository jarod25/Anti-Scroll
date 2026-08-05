package fr.jarodkohler.antiscroll.engine.restriction

import fr.jarodkohler.antiscroll.domain.restriction.RestrictionEvaluationContext
import fr.jarodkohler.antiscroll.domain.restriction.RestrictionRuleResult

fun interface RestrictionRule {
    fun evaluate(context: RestrictionEvaluationContext): RestrictionRuleResult
}
