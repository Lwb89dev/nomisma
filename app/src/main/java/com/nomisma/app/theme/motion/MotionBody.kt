package com.nomisma.app.theme.motion

/**
 * A simulated point mass with optional rotation. Integration is semi-implicit Euler
 * with a caller-supplied delta time, so motion speed is independent of frame rate.
 *
 * Bodies fall asleep when their kinetic energy stays below [sleepThreshold] for
 * [sleepDelaySeconds]; sleeping bodies skip integration until disturbed.
 */
class MotionBody(
    var position: Vec2 = Vec2.Zero,
    var velocity: Vec2 = Vec2.Zero,
    var gravity: Float = 0f,
    var damping: Float = 0f,
    var restitution: Float = 0f,
    var radius: Float = 0f,
    var rotation: Float = 0f,
    var angularVelocity: Float = 0f,
    var angularDamping: Float = 0f,
    var sleepThreshold: Float = 2f,
    var sleepDelaySeconds: Float = 0.35f
) {
    var asleep: Boolean = false
        private set

    /** True while the body rests on a floor: gravity no longer accumulates. */
    var grounded: Boolean = false
        private set
    private var stillSeconds = 0f

    fun wake() {
        asleep = false
        grounded = false
        stillSeconds = 0f
    }

    /** Applies an instantaneous change in velocity (e.g. a hit or a toss). */
    fun applyImpulse(impulse: Vec2) {
        velocity += impulse
        wake()
    }

    /**
     * Advances the body by [dtSeconds]. Returns without doing work while asleep.
     * Output is clamped against non-finite values so a bad input can never
     * poison later frames.
     */
    fun integrate(dtSeconds: Float) {
        if (asleep) return
        val dt = dtSeconds.coerceIn(0f, MAX_STEP_SECONDS)
        if (dt <= 0f) return
        velocity = if (grounded) {
            // Resting contact: the floor supports the body, gravity does not accumulate.
            velocity.copy(y = 0f)
        } else {
            Vec2(velocity.x, velocity.y + gravity * dt)
        }
        if (damping > 0f) {
            val factor = (1f - damping * dt).coerceIn(0f, 1f)
            velocity *= factor
        }
        position += velocity * dt
        if (angularDamping > 0f) {
            angularVelocity *= (1f - angularDamping * dt).coerceIn(0f, 1f)
        }
        rotation += angularVelocity * dt
        if (!position.isFinite() || !velocity.isFinite() || !rotation.isFinite()) {
            position = Vec2.Zero
            velocity = Vec2.Zero
            rotation = 0f
            angularVelocity = 0f
        }
        if (velocity.length() < sleepThreshold && (gravity == 0f || grounded)) {
            stillSeconds += dt
            if (stillSeconds >= sleepDelaySeconds) {
                velocity = Vec2.Zero
                angularVelocity = 0f
                asleep = true
            }
        } else {
            stillSeconds = 0f
        }
    }

    /**
     * Collides the body with a horizontal floor at [floorY] (accounting for [radius]).
     * Returns true when a bounce happened. Low-energy contacts ground the body on
     * the floor instead of micro-bouncing forever.
     */
    fun bounceOnFloor(floorY: Float): Boolean {
        val contactY = floorY - radius
        if (position.y < contactY) return false
        position = position.copy(y = contactY)
        if (velocity.y <= 0f) return false
        val impact = velocity.y
        // Rest when the impact is weaker than the gravity a couple of maximum
        // steps would re-add: without this the body jitters on the floor forever.
        val restThreshold = maxOf(sleepThreshold * 2.5f, gravity * MAX_STEP_SECONDS * 2f)
        if (impact < restThreshold || restitution <= 0f) {
            velocity = velocity.copy(y = 0f)
            grounded = true
            return false
        }
        velocity = Vec2(velocity.x * (1f - 0.25f.coerceAtMost(restitution)), -impact * restitution)
        grounded = false
        asleep = false
        stillSeconds = 0f
        return true
    }

    /** Collides the body with vertical walls, mirroring the horizontal velocity. */
    fun bounceOnWalls(minX: Float, maxX: Float) {
        val left = minX + radius
        val right = maxX - radius
        if (position.x < left && velocity.x < 0f) {
            position = position.copy(x = left)
            velocity = velocity.copy(x = -velocity.x * restitution)
        } else if (position.x > right && velocity.x > 0f) {
            position = position.copy(x = right)
            velocity = velocity.copy(x = -velocity.x * restitution)
        }
    }

    companion object {
        /** Largest single integration step; bigger deltas must be subdivided by the engine. */
        const val MAX_STEP_SECONDS = 1f / 30f
    }
}
