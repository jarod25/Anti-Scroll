package fr.jarodkohler.antiscroll.engine.observation

import fr.jarodkohler.antiscroll.domain.application.ApplicationPackageName
import fr.jarodkohler.antiscroll.domain.observation.ObservationWindow
import java.time.Duration

/** Estimates intentional package openings without adding interruption time to foreground duration. */
class UsageOpeningEstimator(private val policy: UsageSessionReconstructionPolicy) {
    fun estimate(
        packageName: ApplicationPackageName,
        targetWindow: ObservationWindow,
        sessionsByPackage: Map<ApplicationPackageName, List<ReconstructedUsageSession>>
    ): Int {
        val packageSessions = sessionsByPackage[packageName].orEmpty()
            .sortedBy(ReconstructedUsageSession::startInclusive)

        return packageSessions.indices.count { index ->
            val session = packageSessions[index]
            if (session.startInferred || session.startInclusive !in targetWindow) {
                return@count false
            }

            val previousSession = packageSessions.getOrNull(index - 1)
                ?: return@count true

            !isBriefContinuation(
                packageName = packageName,
                previousSession = previousSession,
                currentSession = session,
                sessionsByPackage = sessionsByPackage
            )
        }
    }

    private fun isBriefContinuation(
        packageName: ApplicationPackageName,
        previousSession: ReconstructedUsageSession,
        currentSession: ReconstructedUsageSession,
        sessionsByPackage: Map<ApplicationPackageName, List<ReconstructedUsageSession>>
    ): Boolean {
        val gap = Duration.between(previousSession.endExclusive, currentSession.startInclusive)
        if (gap.isNegative || gap > policy.openingContinuationGrace) return false

        return sessionsByPackage.asSequence()
            .filter { (otherPackageName, _) -> otherPackageName != packageName }
            .flatMap { (_, sessions) -> sessions.asSequence() }
            .none { otherSession ->
                otherSession.startInclusive < currentSession.startInclusive &&
                    otherSession.endExclusive > previousSession.endExclusive
            }
    }
}
