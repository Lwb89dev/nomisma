package com.nomisma.app.theme.motion

/**
 * Fixed-capacity particle pool. Particles are reused, never allocated per frame:
 * [spawn] recycles the oldest slot when the pool is full.
 *
 * Units are caller-defined (the scene uses pixels and seconds).
 */
class ParticlePool(val capacity: Int) {
    class Particle {
        var active = false
        var x = 0f
        var y = 0f
        var vx = 0f
        var vy = 0f
        var gravity = 0f
        var drag = 0f
        var age = 0f
        var lifetime = 1f
        var size = 1f
        var seed = 0

        /** 0 (alive at start) … 1 (expired). */
        val lifeProgress: Float get() = if (lifetime <= 0f) 1f else (age / lifetime).coerceIn(0f, 1f)
    }

    @PublishedApi
    internal val particles = Array(capacity) { Particle() }
    private var nextSlot = 0

    var activeCount = 0
        private set

    fun spawn(
        x: Float,
        y: Float,
        vx: Float,
        vy: Float,
        gravity: Float = 0f,
        drag: Float = 0f,
        lifetime: Float = 1f,
        size: Float = 1f,
        seed: Int = 0
    ): Particle {
        val particle = particles[nextSlot]
        nextSlot = (nextSlot + 1) % capacity
        if (!particle.active) activeCount++
        particle.active = true
        particle.x = x
        particle.y = y
        particle.vx = vx
        particle.vy = vy
        particle.gravity = gravity
        particle.drag = drag
        particle.age = 0f
        particle.lifetime = lifetime
        particle.size = size
        particle.seed = seed
        return particle
    }

    fun update(dtSeconds: Float) {
        val dt = dtSeconds.coerceIn(0f, MotionBody.MAX_STEP_SECONDS)
        if (dt <= 0f) return
        var alive = 0
        for (particle in particles) {
            if (!particle.active) continue
            particle.age += dt
            if (particle.age >= particle.lifetime) {
                particle.active = false
                continue
            }
            particle.vy += particle.gravity * dt
            if (particle.drag > 0f) {
                val factor = (1f - particle.drag * dt).coerceIn(0f, 1f)
                particle.vx *= factor
                particle.vy *= factor
            }
            particle.x += particle.vx * dt
            particle.y += particle.vy * dt
            if (!particle.x.isFinite() || !particle.y.isFinite()) {
                particle.active = false
                continue
            }
            alive++
        }
        activeCount = alive
    }

    fun clear() {
        for (particle in particles) particle.active = false
        activeCount = 0
    }

    /** Iterates live particles without allocating an iterator snapshot. */
    inline fun forEachActive(action: (Particle) -> Unit) {
        for (particle in particles) {
            if (particle.active) action(particle)
        }
    }
}
