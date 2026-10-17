package com.nomisma.app.theme.motion

/**
 * Owns simulation timing for a scene. Controllers register [Simulatable] entities;
 * the engine advances them with a fixed step so behaviour is identical at
 * 30/60/90/120 Hz and immune to frame stalls.
 *
 * Usage per rendered frame:
 * ```
 * engine.advance(frameDeltaSeconds)   // monotonic delta from withFrameNanos
 * ```
 * Rendering then reads entity state directly (120 Hz steps make interpolation
 * unnecessary at this scene's scale).
 */
class MotionEngine(
    val fixedStepSeconds: Float = 1f / 120f,
    private val maxStepsPerFrame: Int = 8,
    private val maxFrameDeltaSeconds: Float = 0.1f
) {
    fun interface Simulatable {
        fun step(dtSeconds: Float)
    }

    private val simulatables = mutableListOf<Simulatable>()
    private var accumulatorSeconds = 0f

    /** Monotonic simulation time in seconds; advances only inside [advance]. */
    var timeSeconds = 0f
        private set

    /** Steps executed during the last [advance] call (debug overlay). */
    var lastStepCount = 0
        private set

    /** True when the last frame had leftover debt the step cap could not drain. */
    var droppedTimeLastFrame = false
        private set

    fun add(simulatable: Simulatable) {
        simulatables += simulatable
    }

    fun remove(simulatable: Simulatable) {
        simulatables -= simulatable
    }

    fun clear() {
        simulatables.clear()
        accumulatorSeconds = 0f
    }

    /**
     * Consumes a monotonic frame delta and runs whole fixed steps.
     * Deltas are clamped and catch-up is capped, so a backgrounding stall can
     * never produce a burst of simulation work ("spiral of death").
     */
    fun advance(frameDeltaSeconds: Float): Int {
        val clamped = frameDeltaSeconds.coerceIn(0f, maxFrameDeltaSeconds)
        accumulatorSeconds += clamped
        var steps = 0
        while (accumulatorSeconds >= fixedStepSeconds && steps < maxStepsPerFrame) {
            for (simulatable in simulatables) simulatable.step(fixedStepSeconds)
            accumulatorSeconds -= fixedStepSeconds
            timeSeconds += fixedStepSeconds
            steps++
        }
        droppedTimeLastFrame = steps == maxStepsPerFrame && accumulatorSeconds >= fixedStepSeconds
        if (droppedTimeLastFrame) {
            // Drop the debt instead of simulating stale time.
            accumulatorSeconds = 0f
        }
        lastStepCount = steps
        return steps
    }
}
