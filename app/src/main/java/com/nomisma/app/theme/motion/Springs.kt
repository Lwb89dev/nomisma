package com.nomisma.app.theme.motion

import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Damped harmonic oscillator for scalar values (fill levels, leans, slosh offsets).
 *
 * [stiffness] is the spring constant k, [dampingRatio] zeta:
 * < 1 under-damped (overshoot), 1 critically damped, > 1 over-damped.
 * Integration is semi-implicit Euler with sub-stepping, stable for any dt the
 * engine can deliver after clamping.
 */
class Spring1D(
    var stiffness: Float = 90f,
    var dampingRatio: Float = 1f,
    initial: Float = 0f
) {
    var value: Float = initial
        private set
    var velocity: Float = 0f
        private set
    var target: Float = initial

    val atRest: Boolean
        get() = kotlin.math.abs(value - target) < REST_EPSILON &&
            kotlin.math.abs(velocity) < REST_EPSILON

    fun snapTo(newValue: Float) {
        value = newValue
        target = newValue
        velocity = 0f
    }

    /** Adds energy to the spring (e.g. a splash hitting the liquid surface). */
    fun kick(impulse: Float) {
        velocity += impulse
    }

    fun step(dtSeconds: Float): Float {
        var remaining = dtSeconds.coerceIn(0f, MAX_STEP_SECONDS)
        if (remaining <= 0f) return value
        val damping = 2f * dampingRatio * sqrt(stiffness)
        // Sub-step so the explicit integrator stays stable even for stiff springs.
        val subSteps = maxOf(1, (remaining / MAX_SUBSTEP).toInt() + 1)
        val h = remaining / subSteps
        repeat(subSteps) {
            val acceleration = -stiffness * (value - target) - damping * velocity
            velocity += acceleration * h
            value += velocity * h
        }
        if (!value.isFinite() || !velocity.isFinite()) {
            value = target
            velocity = 0f
        }
        if (atRest) {
            value = target
            velocity = 0f
        }
        return value
    }

    companion object {
        const val MAX_STEP_SECONDS = 1f / 30f
        private const val MAX_SUBSTEP = 1f / 240f
        private const val REST_EPSILON = 1e-3f
    }
}

/** Convenience spring for 2D targets (character anchors, tool tips). */
class Spring2D(stiffness: Float = 90f, dampingRatio: Float = 1f, initial: Vec2 = Vec2.Zero) {
    val x = Spring1D(stiffness, dampingRatio, initial.x)
    val y = Spring1D(stiffness, dampingRatio, initial.y)

    var target: Vec2
        get() = Vec2(x.target, y.target)
        set(value) {
            x.target = value.x
            y.target = value.y
        }

    val value: Vec2 get() = Vec2(x.value, y.value)
    val atRest: Boolean get() = x.atRest && y.atRest

    fun snapTo(newValue: Vec2) {
        x.snapTo(newValue.x)
        y.snapTo(newValue.y)
    }

    fun kick(impulse: Vec2) {
        x.kick(impulse.x)
        y.kick(impulse.y)
    }

    fun step(dtSeconds: Float): Vec2 {
        x.step(dtSeconds)
        y.step(dtSeconds)
        return value
    }
}

/** Exponential decay helper for effects that fade toward zero (glow, flash energy). */
fun decayTowardZero(value: Float, ratePerSecond: Float, dtSeconds: Float): Float {
    if (value == 0f) return 0f
    val next = value * exp(-ratePerSecond * dtSeconds.coerceAtLeast(0f))
    return if (kotlin.math.abs(next) < 1e-3f) 0f else next
}
