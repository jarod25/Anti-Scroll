package fr.jarodkohler.antiscroll.engine.observation

import fr.jarodkohler.antiscroll.domain.application.ApplicationPackageName
import fr.jarodkohler.antiscroll.domain.observation.NormalizedUsageEvent
import fr.jarodkohler.antiscroll.domain.observation.ObservationWindow
import fr.jarodkohler.antiscroll.domain.observation.UsageEventType
import java.time.Duration
import java.time.Instant

/** One package-level foreground session reconstructed from activity-level transitions. */
data class ReconstructedUsageSession(
    val packageName: ApplicationPackageName,
    val startInclusive: Instant,
    val endExclusive: Instant,
    val startInferred: Boolean,
    val endInferred: Boolean
) {
    init {
        require(startInclusive < endExclusive) { "Usage session end must be after its start" }
    }
}

class UsageSessionReconstructor(private val policy: UsageSessionReconstructionPolicy) {
    fun reconstruct(
        packageName: ApplicationPackageName,
        window: ObservationWindow,
        events: Collection<NormalizedUsageEvent>,
        eventBeforeWindow: NormalizedUsageEvent?
    ): List<ReconstructedUsageSession> {
        require(
            eventBeforeWindow == null ||
                (
                    eventBeforeWindow.packageName == packageName &&
                        eventBeforeWindow.source == policy.source &&
                        eventBeforeWindow.occurredAt < window.startInclusive
                    )
        ) {
            "Session seed event must match the package, source and requested window"
        }

        val orderedEvents = events.asSequence()
            .filter { event ->
                event.packageName == packageName &&
                    event.source == policy.source &&
                    event.occurredAt in window
            }.sortedWith(
                compareBy<NormalizedUsageEvent>(NormalizedUsageEvent::occurredAt)
                    .thenBy { event -> event.type.ordinal }
                    .thenBy { event -> event.id.value }
            ).toList()

        val sessions = mutableListOf<ReconstructedUsageSession>()
        val activeActivities = linkedSetOf<String>()
        var anonymousActivityActive = false
        var sessionStart: Instant? = null
        var sessionStartInferred = false
        var pendingExit: Instant? = null

        fun resetActiveState() {
            activeActivities.clear()
            anonymousActivityActive = false
        }

        fun closeSession(endExclusive: Instant, endInferred: Boolean) {
            val start = sessionStart ?: return
            if (start < endExclusive) {
                sessions += ReconstructedUsageSession(
                    packageName = packageName,
                    startInclusive = start,
                    endExclusive = endExclusive,
                    startInferred = sessionStartInferred,
                    endInferred = endInferred
                )
            }
            sessionStart = null
            sessionStartInferred = false
            pendingExit = null
            resetActiveState()
        }

        if (eventBeforeWindow?.type == UsageEventType.FOREGROUND_ENTERED) {
            sessionStart = window.startInclusive
            sessionStartInferred = true
            eventBeforeWindow.activityClassName?.let(activeActivities::add)
                ?: run { anonymousActivityActive = true }
        }

        orderedEvents.forEach { event ->
            pendingExit?.let { exitAt ->
                if (Duration.between(exitAt, event.occurredAt) > policy.internalTransitionGrace) {
                    closeSession(exitAt, endInferred = false)
                }
            }

            when (event.type) {
                UsageEventType.FOREGROUND_ENTERED -> {
                    if (sessionStart == null) {
                        sessionStart = event.occurredAt
                        sessionStartInferred = false
                    }
                    pendingExit = null

                    val activityClassName = event.activityClassName
                    if (activityClassName == null) {
                        anonymousActivityActive = true
                    } else {
                        if (anonymousActivityActive && activeActivities.isEmpty()) {
                            anonymousActivityActive = false
                        }
                        activeActivities += activityClassName
                    }
                }

                UsageEventType.FOREGROUND_EXITED -> {
                    if (sessionStart == null) return@forEach

                    val activityClassName = event.activityClassName
                    when {
                        activityClassName == null && activeActivities.isEmpty() -> {
                            anonymousActivityActive = false
                        }

                        activityClassName == null -> Unit

                        activeActivities.remove(activityClassName) -> Unit

                        activeActivities.isNotEmpty() -> Unit

                        anonymousActivityActive -> anonymousActivityActive = false
                    }

                    if (activeActivities.isEmpty() && !anonymousActivityActive) {
                        pendingExit = maxOf(pendingExit ?: event.occurredAt, event.occurredAt)
                    }
                }
            }
        }

        val finalStart = sessionStart
        if (finalStart != null) {
            val finalExit = pendingExit
            if (finalExit == null) {
                closeSession(window.endExclusive, endInferred = true)
            } else {
                closeSession(finalExit, endInferred = false)
            }
        }

        return sessions
    }
}
