package fr.jarodkohler.antiscroll.restriction

import fr.jarodkohler.antiscroll.domain.restriction.DeviceBootIdentifier
import fr.jarodkohler.antiscroll.domain.restriction.RestrictionDecision
import fr.jarodkohler.antiscroll.domain.restriction.RestrictionEvaluationContext
import fr.jarodkohler.antiscroll.domain.restriction.RestrictionProfile
import fr.jarodkohler.antiscroll.domain.restriction.RestrictionProfileSource
import fr.jarodkohler.antiscroll.domain.restriction.SharedSessionCheckpoint
import fr.jarodkohler.antiscroll.domain.restriction.SharedSessionEndReason
import fr.jarodkohler.antiscroll.domain.restriction.SharedSessionEvent
import fr.jarodkohler.antiscroll.domain.restriction.SharedSessionEventSource
import fr.jarodkohler.antiscroll.domain.restriction.SharedSessionRuntimeSnapshot
import fr.jarodkohler.antiscroll.domain.restriction.SharedSessionRuntimeStateSource
import fr.jarodkohler.antiscroll.domain.restriction.SharedSessionRuntimeStatus
import fr.jarodkohler.antiscroll.domain.restriction.SharedSessionState
import fr.jarodkohler.antiscroll.domain.restriction.SharedSessionStateRepository
import fr.jarodkohler.antiscroll.domain.restriction.SharedSessionTransition
import fr.jarodkohler.antiscroll.engine.restriction.RestrictionEngine
import fr.jarodkohler.antiscroll.engine.restriction.SharedSessionDeadlinePlanner
import fr.jarodkohler.antiscroll.engine.restriction.SharedSessionReducer
import fr.jarodkohler.antiscroll.engine.restriction.SharedSessionRestorer
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class SharedSessionRuntime
@Inject
constructor(
    private val eventSources: Set<@JvmSuppressWildcards SharedSessionEventSource>,
    private val profileSource: RestrictionProfileSource,
    private val stateRepository: SharedSessionStateRepository,
    private val reducer: SharedSessionReducer,
    private val restorer: SharedSessionRestorer,
    private val restrictionEngine: RestrictionEngine,
    private val deadlinePlanner: SharedSessionDeadlinePlanner,
    private val deadlineScheduler: SharedSessionDeadlineScheduler,
    private val clock: SharedSessionRuntimeClock,
    private val bootIdentifierProvider: DeviceBootIdentifierProvider,
    @param:ApplicationCoroutineScope private val applicationScope: CoroutineScope
) : SharedSessionRuntimeStateSource {
    private val started = AtomicBoolean(false)
    private val processingMutex = Mutex()
    private var currentBootIdentifier: DeviceBootIdentifier? = null
    private var deadlineGeneration = 0L
    private var deadlineHandle: SharedSessionDeadlineHandle? = null
    private val mutableSnapshots = MutableStateFlow(
        SharedSessionRuntimeSnapshot(
            status = SharedSessionRuntimeStatus.STARTING,
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

        applicationScope.launch {
            bootstrap()
        }
        return true
    }

    private suspend fun bootstrap() {
        processingMutex.withLock {
            restorePersistedState()
        }

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
    }

    private suspend fun restorePersistedState() {
        val profile = profileSource.activeProfile.value
        val bootIdentifier = bootIdentifierProvider.current()
        currentBootIdentifier = bootIdentifier
        val checkpoint = stateRepository.load()
        val currentElapsedRealtime = clock.elapsedRealtime()
        val restoredState = checkpoint?.let { persisted ->
            restorer.restore(
                checkpoint = persisted,
                profile = profile,
                currentBootIdentifier = bootIdentifier,
                now = clock.now(),
                elapsedRealtime = currentElapsedRealtime
            )
        }

        if (restoredState == null) {
            if (checkpoint != null) {
                stateRepository.clear()
            }
        } else {
            stateRepository.save(
                SharedSessionCheckpoint.from(
                    profile = profile,
                    state = restoredState,
                    bootIdentifier = bootIdentifier
                )
            )
        }

        mutableSnapshots.value = SharedSessionRuntimeSnapshot(
            status = SharedSessionRuntimeStatus.READY,
            profile = profile,
            state = restoredState ?: SharedSessionState.Inactive,
            lastTransition = null,
            lastDecision = restoredState?.let { state -> decisionForRestoredState(state, profile) }
        )
        refreshDeadline()
    }

    private suspend fun observeProfile(profile: RestrictionProfile) {
        val current = mutableSnapshots.value
        if (profile == current.profile) return

        val activeState = current.state as? SharedSessionState.Active
        if (activeState == null) {
            stateRepository.clear()
            mutableSnapshots.value = SharedSessionRuntimeSnapshot(
                status = SharedSessionRuntimeStatus.READY,
                profile = profile,
                state = SharedSessionState.Inactive,
                lastTransition = null,
                lastDecision = null
            )
            refreshDeadline()
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

        persistState(profile, reduction.state)
        mutableSnapshots.value = SharedSessionRuntimeSnapshot(
            status = SharedSessionRuntimeStatus.READY,
            profile = profile,
            state = reduction.state,
            lastTransition = reduction.transition,
            lastDecision = null
        )
        refreshDeadline()
    }

    private suspend fun processEvent(event: SharedSessionEvent) {
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

        persistState(current.profile, reduction.state)
        mutableSnapshots.value = current.copy(
            state = reduction.state,
            lastTransition = reduction.transition,
            lastDecision = decision
        )
        refreshDeadline()
    }

    private suspend fun persistState(profile: RestrictionProfile, state: SharedSessionState) {
        val activeState = state as? SharedSessionState.Active
        if (activeState == null) {
            stateRepository.clear()
            return
        }

        stateRepository.save(
            SharedSessionCheckpoint.from(
                profile = profile,
                state = activeState,
                bootIdentifier = checkNotNull(currentBootIdentifier) {
                    "Shared-session persistence requires completed runtime bootstrap"
                }
            )
        )
    }

    private suspend fun onDeadlineReached(generation: Long) {
        processingMutex.withLock {
            if (generation != deadlineGeneration) return

            deadlineGeneration += 1
            deadlineHandle = null

            val current = mutableSnapshots.value
            val policy = current.profile.sharedSessionPolicy ?: return
            val activeState = current.state as? SharedSessionState.Active ?: return
            val packageName = activeState.foregroundApplication ?: return
            val elapsedRealtime = maxOf(
                clock.elapsedRealtime(),
                activeState.lastObservedElapsedRealtime
            )
            val decision = restrictionEngine.evaluate(
                RestrictionEvaluationContext(
                    targetPackageName = packageName,
                    evaluatedAt = clock.now(),
                    elapsedRealtime = elapsedRealtime,
                    profile = current.profile,
                    sharedSessionState = activeState
                )
            )

            mutableSnapshots.value = current.copy(lastDecision = decision)
            if (decision is RestrictionDecision.Allowed) {
                refreshDeadline()
            }
        }
    }

    private fun refreshDeadline() {
        deadlineGeneration += 1
        deadlineHandle?.cancel()
        deadlineHandle = null

        val current = mutableSnapshots.value
        if (current.status != SharedSessionRuntimeStatus.READY) return
        if (current.lastDecision is RestrictionDecision.Blocked) return

        val policy = current.profile.sharedSessionPolicy ?: return
        val activeState = current.state as? SharedSessionState.Active ?: return
        val deadline = deadlinePlanner.plan(activeState, policy) ?: return
        val currentElapsedRealtime = maxOf(
            clock.elapsedRealtime(),
            activeState.lastObservedElapsedRealtime
        )
        val delay = maxOf(
            Duration.ZERO,
            deadline.elapsedRealtime.minus(currentElapsedRealtime)
        )
        val scheduledGeneration = deadlineGeneration

        deadlineHandle = deadlineScheduler.schedule(delay) {
            onDeadlineReached(scheduledGeneration)
        }
    }

    private fun decisionForRestoredState(
        state: SharedSessionState.Active,
        profile: RestrictionProfile
    ): RestrictionDecision? {
        val packageName = state.foregroundApplication ?: return null
        val elapsedRealtime = maxOf(clock.elapsedRealtime(), state.lastObservedElapsedRealtime)
        return restrictionEngine.evaluate(
            RestrictionEvaluationContext(
                targetPackageName = packageName,
                evaluatedAt = clock.now(),
                elapsedRealtime = elapsedRealtime,
                profile = profile,
                sharedSessionState = state
            )
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
