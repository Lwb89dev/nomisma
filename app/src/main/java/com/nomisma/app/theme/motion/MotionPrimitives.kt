package com.nomisma.app.theme.motion

import kotlin.math.sqrt

/** Minimal 2D vector used by the motion simulation. Immutable and allocation-light. */
data class Vec2(val x: Float, val y: Float) {
    operator fun plus(other: Vec2) = Vec2(x + other.x, y + other.y)
    operator fun minus(other: Vec2) = Vec2(x - other.x, y - other.y)
    operator fun times(scalar: Float) = Vec2(x * scalar, y * scalar)
    operator fun div(scalar: Float) = Vec2(x / scalar, y / scalar)
    fun length() = sqrt(x * x + y * y)
    fun isFinite() = x.isFinite() && y.isFinite()

    companion object {
        val Zero = Vec2(0f, 0f)
    }
}

/** Deterministic random source so particle bursts are reproducible in tests. */
class SeededRandom(seed: Int) {
    private var state: Long = mixSeed(seed)

    private fun nextBits(): Long {
        var x = state
        x = x xor (x shl 13)
        x = x xor (x ushr 7)
        x = x xor (x shl 17)
        state = x
        return x
    }

    /** Uniform value in [0, 1). */
    fun nextFloat(): Float = (nextBits() ushr 40).toFloat() / (1L shl 24).toFloat()

    /** Uniform value in [min, max). */
    fun nextFloat(min: Float, max: Float): Float = min + nextFloat() * (max - min)

    /** Uniform value in [-magnitude, magnitude). */
    fun spread(magnitude: Float): Float = nextFloat(-magnitude, magnitude)

    private companion object {
        /** SplitMix64 mixing so that adjacent seeds produce unrelated streams. */
        fun mixSeed(seed: Int): Long {
            var x = seed.toLong() + 0x9E3779B97F4A7C15uL.toLong()
            x = (x xor (x ushr 30)) * 0xBF58476D1CE4E5B9uL.toLong()
            x = (x xor (x ushr 27)) * 0x94D049BB133111EBuL.toLong()
            return x xor (x ushr 31)
        }
    }
}

fun smoothStep(edge0: Float, edge1: Float, value: Float): Float {
    val x = ((value - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

/** Triangle pulse: 0 before [start], 1 at [peak], 0 after [end]. */
fun pulse(value: Float, start: Float, peak: Float, end: Float): Float = when {
    value <= start || value >= end -> 0f
    value <= peak -> smoothStep(start, peak, value)
    else -> 1f - smoothStep(peak, end, value)
}
