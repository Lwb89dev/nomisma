package com.nomisma.app.theme.motion

/**
 * A choreographed sequence of named stages. Stages own their durations; completion
 * is explicit (all stages elapsed) rather than inferred from wall-clock timeouts.
 *
 * Stage boundaries fire [onStageEnter], which controllers use to synchronize
 * world reactions (e.g. an impact burst starts exactly when the SWING stage ends,
 * never before).
 */
class AnimationTimeline(
    val id: String,
    stages: List<Stage>,
    private val onStageEnter: (Stage) -> Unit = {},
    private val onComplete: () -> Unit = {}
) {
    class Stage(
        val id: String,
        val durationSeconds: Float,
        /** Optional semantic marker fired when this stage begins. */
        val marker: String? = null
    ) {
        init {
            require(durationSeconds >= 0f) { "Stage duration must be >= 0" }
        }
    }

    private val stages = stages.toList()
    private val totalDuration = stages.sumOf { it.durationSeconds.toDouble() }.toFloat()

    var elapsedSeconds = 0f
        private set
    var stageIndex = 0
        private set
    var running = false
        private set
    var completed = false
        private set

    val currentStage: Stage? get() = stages.getOrNull(stageIndex)

    /** Seconds elapsed inside the current stage. */
    val stageElapsed: Float
        get() {
            var offset = 0f
            for (index in 0 until stageIndex) offset += stages[index].durationSeconds
            return (elapsedSeconds - offset).coerceAtLeast(0f)
        }

    /** 0..1 progress of the whole timeline. */
    val progress: Float
        get() = if (totalDuration <= 0f) 1f else (elapsedSeconds / totalDuration).coerceIn(0f, 1f)

    /** 0..1 progress inside the stage with the given id, or 0 when not active/passed. */
    fun progressOf(stageId: String): Float {
        var offset = 0f
        for (stage in stages) {
            if (stage.id == stageId) {
                if (elapsedSeconds < offset) return 0f
                if (stage.durationSeconds <= 0f) return if (elapsedSeconds >= offset) 1f else 0f
                return ((elapsedSeconds - offset) / stage.durationSeconds).coerceIn(0f, 1f)
            }
            offset += stage.durationSeconds
        }
        return 0f
    }

    /** True once the stage with [stageId] has fully elapsed. */
    fun isPast(stageId: String): Boolean {
        var offset = 0f
        for (stage in stages) {
            offset += stage.durationSeconds
            if (stage.id == stageId) return elapsedSeconds >= offset
        }
        return false
    }

    fun start() {
        elapsedSeconds = 0f
        stageIndex = 0
        running = true
        completed = false
        stages.firstOrNull()?.let(onStageEnter)
    }

    /**
     * Advances the timeline. Stage indices are recomputed from [elapsedSeconds], so a
     * large frame delta can cross several stages in one call; every stage entered
     * along the way fires [onStageEnter] in order (markers are never skipped).
     * When it completes, [onComplete] fires exactly once and [running] becomes false.
     * Returns true while still running.
     */
    fun update(dtSeconds: Float): Boolean {
        if (!running) return false
        elapsedSeconds += dtSeconds.coerceAtLeast(0f)
        var offset = 0f
        var newIndex = 0
        while (newIndex < stages.size && elapsedSeconds >= offset + stages[newIndex].durationSeconds) {
            offset += stages[newIndex].durationSeconds
            newIndex++
        }
        if (newIndex != stageIndex) {
            val lastEntered = newIndex.coerceAtMost(stages.size - 1)
            for (index in (stageIndex + 1)..lastEntered) {
                onStageEnter(stages[index])
            }
            stageIndex = newIndex
        }
        if (stageIndex >= stages.size) {
            running = false
            completed = true
            onComplete()
        }
        return running
    }

    /** Stops the timeline without firing [onComplete]. The controller decides recovery. */
    fun cancel() {
        running = false
    }
}

/**
 * Explicit actor animation states. Transitions must be declared up-front; an
 * undeclared transition throws in tests and is ignored (logged via [onRejected])
 * in production, so an actor can never jump between unrelated poses silently.
 */
class ActorStateMachine(
    private val allowed: Map<ActorState, Set<ActorState>> = DEFAULT_TRANSITIONS,
    private val onRejected: (ActorState, ActorState) -> Unit = { _, _ -> }
) {
    var state: ActorState = ActorState.IDLE
        private set
    var stateElapsedSeconds = 0f
        private set

    fun transitionTo(next: ActorState): Boolean {
        if (next == state) {
            stateElapsedSeconds = 0f
            return true
        }
        val permitted = allowed[state].orEmpty()
        if (next !in permitted) {
            onRejected(state, next)
            return false
        }
        state = next
        stateElapsedSeconds = 0f
        return true
    }

    fun update(dtSeconds: Float) {
        stateElapsedSeconds += dtSeconds.coerceAtLeast(0f)
    }

    fun reset() {
        state = ActorState.IDLE
        stateElapsedSeconds = 0f
    }

    companion object {
        val DEFAULT_TRANSITIONS: Map<ActorState, Set<ActorState>> = mapOf(
            ActorState.IDLE to setOf(ActorState.PREPARING, ActorState.MOVING),
            ActorState.PREPARING to setOf(ActorState.MOVING, ActorState.INTERACTING, ActorState.IDLE),
            ActorState.MOVING to setOf(ActorState.INTERACTING, ActorState.RECOVERING, ActorState.IDLE),
            ActorState.INTERACTING to setOf(ActorState.RECOVERING, ActorState.IDLE),
            ActorState.RECOVERING to setOf(ActorState.IDLE, ActorState.PREPARING)
        )
    }
}

enum class ActorState { IDLE, PREPARING, MOVING, INTERACTING, RECOVERING }
