package fr.jarodkohler.antiscroll.restriction

import fr.jarodkohler.antiscroll.domain.restriction.RestrictionDecision
import fr.jarodkohler.antiscroll.domain.restriction.RestrictionEvaluationContext
import fr.jarodkohler.antiscroll.domain.restriction.RestrictionProfile
import fr.jarodkohler.antiscroll.domain.restriction.RestrictionProfileSource
import fr.jarodkohler.antiscroll.domain.restriction.SharedSessionEndReason
import fr.jarodkohler.antiscroll.domain.restriction.SharedSessionEvent
import fr.jarodkohler.antiscroll.domain.restriction.SharedSessionEventSource
import fr.jarodkohler.antiscroll.domain.restriction.SharedSessionRuntimeSnapshot
import fr.jarodkohler.antiscroll.domain.restriction.SharedSessionRuntimeStateSource
import fr.jarodkohler.antiscroll.domain.restriction.SharedSessionState
import fr.jarodkohler.antiscroll.domain.restriction.SharedSessionTransition
import fr.jarodkohler.antiscroll.engine.restriction.RestrictionEngine
import fr.jarodkohler.antiscroll.engine.restriction.SharedSessionReducer
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class SharedSessionRuntime
@Inject
constructor(
    private val eventSources: Set<@JvmSuppressWildcards SharedSessionEventSource>,
    private val profileSource: RestrictionProfileSource,
    private val reducer: SharedSessionReducer,
    private val restrictionEngine: RestrictionEngine,
    private val clock: SharedSessionRuntimeClock,
    @param:ApplicationCoroutineScope private val applicationScope: CoroutineScope
) : SharedSessionRuntimeStateSource {
    private val started = AtomicBoolean(false)
    private val processingMutex = Mutex()
    private val mutableSnapshots = MutableStateFlow(
        SharedSessionRuntimeSnapshot(
            profile = profileSource.activeProfile.value,
            state = SharedSessionState.Inactive,
            lastTransition = null,
            lastDecision = null
        )
    )

    override val snapshots: StateFlow<SharedSessionRuntimeSnapshot> = mutableSnapshots.asStateFlow()

    internal val isStarted: Boolean
        get() = started.get()

    fun start(): Boolean {
        if (!started.compareAndSet(false, true)) return false

        profileSource.activeProfile
            .onEach { profile ->
                processingMutex.withLock {
                    observeProfile(profile)
                }
            }
            .launchIn(applicationScope)

        eventSources.forEach { source ->
            source.events
                .onEach { event ->
                    processingMutex.withLock {
                        processEvent(event)
                    }
                }
                .launchIn(applicationScope)
        }

        return true
    }

    private fun observeProfile(profile: RestrictionProfile) {
        val current = mutableSnapshots.value
        if (profile == current.profile) return

        val activeState = current.state as? SharedSessionState.Active
        if (activeState == null) {
            mutableSnapshots.value = SharedSessionRuntimeSnapshot(
                profile = profile,
                state = SharedSessionState.Inactive,
                lastTransition = null,
                lastDecision = null
            )
            return
        }

        val currentPolicy = checkNotNull(current.profile.sharedSessionPolicy) {
            "An active shared session requires an active session policy"
        }
        val endElapsedRealtime = maxOf(
            clock.elapsedRealtime(),
            activeState.lastObservedElapsedRealtime
        )
        val reduction = reducer.reduce(
            state = activeState,
            event = SharedSessionEvent.EndRequested(
                reason = SharedSessionEndReason.PROFILE_CHANGED,
                observedAt = clock.now(),
                elapsedRealtime = endElapsedRealtime
            ),
            policy = currentPolicy
        )

        mutableSnapshots.value = SharedSessionRuntimeSnapshot(
            profile = profile,
            state = reduction.state,
            lastTransition = reduction.transition,
            lastDecision = null
        )
    }

    private fun processEvent(event: SharedSessionEvent) {
        val current = mutableSnapshots.value
        val policy = current.profile.sharedSessionPolicy ?: return
        val reduction = reducer.reduce(
            state = current.state,
            event = event,
            policy = policy
        )
        val decision = decisionFor(
            event = event,
            transition = reduction.transition,
            state = reduction.state,
            profile = current.profile
        )

        mutableSnapshots.value = current.copy(
            state = reduction.state,
            lastTransition = reduction.transition,
            lastDecision = decision
        )
    }

    private fun decisionFor(
        event: SharedSessionEvent,
        transition: SharedSessionTransition,
        state: SharedSessionState,
        profile: RestrictionProfile
    ): RestrictionDecision? {
        val foregroundEvent = event as? SharedSessionEvent.ApplicationForegrounded ?: return null
        if (transition == SharedSessionTransition.IgnoredStaleSignal) return null

        return restrictionEngine.evaluate(
            RestrictionEvaluationContext(
                targetPackageName = foregroundEvent.packageName,
                evaluatedAt = foregroundEvent.observedAt,
                elapsedRealtime = foregroundEvent.elapsedRealtime,
                profile = profile,
                sharedSessionState = state
            )
        )
    }
}
