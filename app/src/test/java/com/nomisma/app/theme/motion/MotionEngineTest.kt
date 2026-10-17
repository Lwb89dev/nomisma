package com.nomisma.app.theme.motion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class MotionEngineTest {

    // ---------------------------------------------------------------- MotionBody

    @Test
    fun `integration is frame-rate independent within tolerance`() {
        fun run(steps: Int, dt: Float): MotionBody {
            val body = MotionBody(position = Vec2(0f, 0f), velocity = Vec2(100f, -50f), gravity = 980f)
            repeat(steps) { body.integrate(dt) }
            return body
        }
        val coarse = run(60, 1f / 60f)
        val fine = run(1200, 1f / 1200f)
        // Semi-implicit Euler is exact for x (no horizontal acceleration) and for
        // accumulated velocity; vertical position carries the expected O(dt) error.
        assertEquals(fine.position.x, coarse.position.x, 0.5f)
        assertEquals(fine.velocity.y, coarse.velocity.y, 0.1f)
        assertEquals(fine.position.y, coarse.position.y, 10f)
    }

    @Test
    fun `gravity accelerates the body downward`() {
        val body = MotionBody(gravity = 100f)
        repeat(30) { body.integrate(1f / 60f) } // 0.5 s in sub-steps
        assertEquals(50f, body.velocity.y, 1e-2f)
        assertTrue(body.position.y > 0f)
    }

    @Test
    fun `damping reduces velocity over time`() {
        val body = MotionBody(velocity = Vec2(100f, 0f), damping = 2f)
        repeat(60) { body.integrate(1f / 60f) }
        assertTrue(body.velocity.x < 20f)
        assertTrue(body.velocity.x >= 0f)
    }

    @Test
    fun `floor bounce respects restitution and eventually settles`() {
        val body = MotionBody(position = Vec2(0f, -100f), gravity = 980f, restitution = 0.4f, radius = 5f)
        var bounced = false
        repeat(600) {
            body.integrate(1f / 120f)
            if (body.bounceOnFloor(0f)) bounced = true
        }
        assertTrue(bounced)
        assertEquals(-5f, body.position.y, 1e-3f)
        assertEquals(0f, body.velocity.y, 1e-3f)
    }

    @Test
    fun `walls mirror horizontal velocity`() {
        val body = MotionBody(position = Vec2(99f, 0f), velocity = Vec2(50f, 0f), restitution = 0.5f, radius = 2f)
        body.integrate(1f / 60f)
        body.bounceOnWalls(0f, 100f)
        assertEquals(98f, body.position.x, 1e-3f)
        assertTrue(body.velocity.x < 0f)
    }

    @Test
    fun `non-finite input is sanitized instead of corrupting state`() {
        val body = MotionBody(position = Vec2(Float.NaN, 0f), velocity = Vec2(Float.POSITIVE_INFINITY, 0f), gravity = 10f)
        body.integrate(1f / 60f)
        assertTrue(body.position.isFinite())
        assertTrue(body.velocity.isFinite())
    }

    @Test
    fun `idle bodies fall asleep and impulses wake them`() {
        val body = MotionBody(velocity = Vec2(0.5f, 0f))
        repeat(240) { body.integrate(1f / 60f) }
        assertTrue(body.asleep)
        val before = body.position
        body.integrate(1f / 60f)
        assertEquals(before, body.position)
        body.applyImpulse(Vec2(10f, 0f))
        assertFalse(body.asleep)
    }

    // ----------------------------------------------------------------- Spring1D

    @Test
    fun `critically damped spring converges without overshoot`() {
        val spring = Spring1D(stiffness = 120f, dampingRatio = 1f)
        spring.target = 1f
        var maxValue = 0f
        repeat(600) {
            spring.step(1f / 120f)
            maxValue = maxOf(maxValue, spring.value)
        }
        assertEquals(1f, spring.value, 1e-3f)
        assertTrue("no overshoot expected", maxValue <= 1.001f)
        assertTrue(spring.atRest)
    }

    @Test
    fun `under-damped spring oscillates but remains stable`() {
        val spring = Spring1D(stiffness = 160f, dampingRatio = 0.2f)
        spring.target = 1f
        repeat(1200) { spring.step(1f / 120f) }
        assertEquals(1f, spring.value, 5e-3f)
        assertTrue(spring.atRest)
    }

    @Test
    fun `spring survives a huge delta time`() {
        val spring = Spring1D(stiffness = 400f, dampingRatio = 0.7f)
        spring.target = 5f
        spring.step(10f) // clamped + sub-stepped internally
        assertTrue(spring.value.isFinite())
        assertTrue(spring.velocity.isFinite())
    }

    @Test
    fun `spring kick adds energy`() {
        val spring = Spring1D(stiffness = 80f, dampingRatio = 0.5f)
        spring.kick(30f)
        spring.step(1f / 120f)
        assertTrue(spring.value != 0f)
    }

    // -------------------------------------------------------------- ParticlePool

    @Test
    fun `pool never exceeds capacity and recycles slots`() {
        val pool = ParticlePool(capacity = 8)
        repeat(20) { pool.spawn(0f, 0f, 0f, 0f, lifetime = 100f) }
        var live = 0
        pool.forEachActive { live++ }
        assertEquals(8, live)
        assertEquals(8, pool.activeCount)
    }

    @Test
    fun `particles expire and integrate gravity deterministically`() {
        val pool = ParticlePool(capacity = 4)
        pool.spawn(x = 0f, y = 0f, vx = 10f, vy = -20f, gravity = 100f, lifetime = 1f)
        repeat(66) { pool.update(1f / 60f) }
        assertEquals(0, pool.activeCount)
    }

    @Test
    fun `seeded random is reproducible`() {
        val a = SeededRandom(42)
        val b = SeededRandom(42)
        repeat(32) {
            assertEquals(a.nextFloat(), b.nextFloat(), 0f)
        }
        val c = SeededRandom(43)
        assertTrue(abs(c.nextFloat() - SeededRandom(42).nextFloat()) > 1e-6f)
    }

    // ---------------------------------------------------------------- Timeline

    private fun miningTimeline(
        markers: MutableList<String>,
        completed: MutableList<Boolean>
    ) = AnimationTimeline(
        id = "swing",
        stages = listOf(
            AnimationTimeline.Stage("prepare", 0.30f),
            AnimationTimeline.Stage("backswing", 0.25f),
            AnimationTimeline.Stage("swing", 0.12f),
            AnimationTimeline.Stage("impact", 0.05f, marker = "impact"),
            AnimationTimeline.Stage("recover", 0.40f)
        ),
        onStageEnter = { it.marker?.let(markers::add) },
        onComplete = { completed += true }
    )

    @Test
    fun `timeline fires markers at stage boundaries and completes once`() {
        val markers = mutableListOf<String>()
        val completed = mutableListOf<Boolean>()
        val timeline = miningTimeline(markers, completed)
        timeline.start()
        var impactAt = -1f
        var t = 0f
        while (timeline.running) {
            timeline.update(1f / 120f)
            t += 1f / 120f
            if (markers.isNotEmpty() && impactAt < 0f) impactAt = t
        }
        assertTrue(completed.size == 1)
        assertEquals(listOf("impact"), markers)
        // Impact must land exactly at the end of prepare+backswing+swing = 0.67 s.
        assertEquals(0.67f, impactAt, 2f / 120f)
    }

    @Test
    fun `timeline progress tracks stages`() {
        val timeline = miningTimeline(mutableListOf(), mutableListOf())
        timeline.start()
        repeat(36) { timeline.update(1f / 120f) } // 0.30 s -> end of prepare
        assertEquals(1f, timeline.progressOf("prepare"), 1e-2f)
        assertEquals("backswing", timeline.currentStage?.id)
        assertFalse(timeline.isPast("swing"))
    }

    @Test
    fun `cancelled timeline does not complete`() {
        val completed = mutableListOf<Boolean>()
        val timeline = miningTimeline(mutableListOf(), completed)
        timeline.start()
        timeline.update(0.1f)
        timeline.cancel()
        assertFalse(timeline.update(0.1f))
        assertTrue(completed.isEmpty())
        assertFalse(timeline.completed)
    }

    // --------------------------------------------------------- ActorStateMachine

    @Test
    fun `actor follows declared transitions`() {
        val actor = ActorStateMachine()
        assertEquals(ActorState.IDLE, actor.state)
        assertTrue(actor.transitionTo(ActorState.PREPARING))
        assertTrue(actor.transitionTo(ActorState.INTERACTING))
        assertTrue(actor.transitionTo(ActorState.RECOVERING))
        assertTrue(actor.transitionTo(ActorState.IDLE))
    }

    @Test
    fun `actor rejects undeclared jumps`() {
        val rejected = mutableListOf<Pair<ActorState, ActorState>>()
        val actor = ActorStateMachine(onRejected = { from, to -> rejected += from to to })
        assertFalse(actor.transitionTo(ActorState.RECOVERING))
        assertEquals(ActorState.IDLE, actor.state)
        assertEquals(listOf(ActorState.IDLE to ActorState.RECOVERING), rejected)
    }

    // -------------------------------------------------------------- MotionEngine

    @Test
    fun `engine consumes time in fixed steps regardless of frame pacing`() {
        var stepped = 0f
        val engine = MotionEngine()
        engine.add { dt -> stepped += dt }
        // Same total time delivered as 60 fps frames vs one 120 Hz stream.
        repeat(60) { engine.advance(1f / 60f) }
        assertEquals(1f, stepped, engine.fixedStepSeconds)
        assertEquals(1f, engine.timeSeconds, engine.fixedStepSeconds)
    }

    @Test
    fun `engine caps catch-up after a stall and drops the debt`() {
        var steps = 0
        val engine = MotionEngine(maxStepsPerFrame = 4)
        engine.add { steps++ }
        engine.advance(5f) // backgrounding stall
        assertEquals(4, steps)
        assertTrue(engine.droppedTimeLastFrame)
        // Next normal frame behaves normally, no backlog storm.
        steps = 0
        engine.advance(1f / 60f)
        assertTrue(steps in 1..2)
        assertFalse(engine.droppedTimeLastFrame)
    }

    @Test
    fun `engine ignores negative or zero deltas`() {
        var steps = 0
        val engine = MotionEngine()
        engine.add { steps++ }
        engine.advance(-1f)
        engine.advance(0f)
        assertEquals(0, steps)
    }
}
