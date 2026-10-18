package com.nomisma.app.ui.theme.scene

import androidx.compose.ui.graphics.drawscope.DrawScope
import com.nomisma.app.theme.ThemeEventEnvelope
import com.nomisma.app.theme.ThemeFinancialState
import com.nomisma.app.theme.ThemePresentationEvent
import com.nomisma.app.theme.ThemeRegistry
import com.nomisma.app.theme.motion.ActorState
import com.nomisma.app.theme.motion.ActorStateMachine
import com.nomisma.app.theme.motion.AnimationTimeline
import com.nomisma.app.theme.motion.MotionBody
import com.nomisma.app.theme.motion.MotionEngine
import com.nomisma.app.theme.motion.ParticlePool
import com.nomisma.app.theme.motion.Spring1D
import com.nomisma.app.theme.motion.Vec2
import com.nomisma.app.theme.motion.smoothStep
import com.nomisma.app.ui.theme.MinerAppearance
import com.nomisma.app.ui.theme.ThemeRenderQuality
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * MEMPOOL scene: two miners loading a mine cart. Owns the fill spring, the
 * ambient clock, the event timelines, the flying contribution block and the
 * impact spark burst.
 *
 * The old shell drove this scene with hardcoded `Animatable` keyframes, a
 * predetermined `PathMeasure` Bézier for the block and parametric sparks; here
 * events are `AnimationTimeline`s stepped by the fixed-step [MotionEngine]
 * (stage boundaries match the old keyframe times, and the progress fed to the
 * renderer is re-mapped through the old keyframe stops, so visual timing is
 * unchanged), the block is a [MotionBody] with gravity and a restitution bounce
 * on the cart bed, and the impact sparks live in a [ParticlePool].
 */
internal class MempoolSceneController : SceneController {

    override val themeId: String = ThemeRegistry.MEMPOOL

    private var environment = SceneEnvironment(
        quality = ThemeRenderQuality.LOW,
        reducedMotion = false,
        materials = null,
        assets = null,
        minerAppearances = emptyMap()
    )
    private var sceneState: ThemeFinancialState? = null

    private val engine = MotionEngine()
    // Critically damped replacement for the old 1050 ms fill tween: stiffness
    // 16 settles in ~1.1 s without overshoot.
    private val fillSpring = Spring1D(stiffness = 16f, dampingRatio = 1f, initial = 0f)
    private val sparks = ParticlePool(SPARK_CAPACITY)
    private val block = MotionBody(restitution = BLOCK_RESTITUTION)
    private val minerA = ActorStateMachine()
    private val minerB = ActorStateMachine()
    private val frame = MempoolFrame()

    private var viewportWidth = 0f
    private var viewportHeight = 0f
    private var density = 1f

    // Contribution splice state (replaces the old event.fromFill/targetFill splicing).
    private var contributionActive = false
    private var contributionFromFill = 0f
    private var contributionTargetFill = 0f
    private var settlementActive = false
    private var activeContributorId: String? = null
    private var activeEventSeed = 0

    private class PendingEvent(val envelope: ThemeEventEnvelope, val onComplete: () -> Unit)

    private val pendingEvents = ArrayDeque<PendingEvent>()
    private var activeEvent: PendingEvent? = null
    private var activeTimeline: AnimationTimeline? = null

    // Block flight state (pixels; geometry captured at spawn from the viewport).
    private var blockActive = false
    private var blockThrown = false
    private var blockLanded = false
    private var blockOriginX = 0f
    private var blockOriginY = 0f
    private var blockTargetX = 0f
    private var blockBedY = 0f
    private var trailStepCounter = 0

    init {
        frame.sparks = sparks
        engine.add(MotionEngine.Simulatable { dt -> fillSpring.step(dt) })
        engine.add(MotionEngine.Simulatable { dt -> stepTimeline(dt) })
        engine.add(MotionEngine.Simulatable { dt -> stepBlock(dt) })
        engine.add(MotionEngine.Simulatable { dt -> sparks.update(dt) })
        engine.add(MotionEngine.Simulatable { dt ->
            minerA.update(dt)
            minerB.update(dt)
        })
    }

    override val isAnimating: Boolean
        get() = activeTimeline?.running == true || pendingEvents.isNotEmpty() ||
            blockActive || sparks.activeCount > 0 || !fillSpring.atRest

    override val displayFill: Float
        get() = if (contributionActive) {
            contributionFromFill + (contributionTargetFill - contributionFromFill) *
                smoothStep(0.32f, 0.92f, contributionProgress())
        } else {
            fillSpring.value
        }

    override fun updateEnvironment(environment: SceneEnvironment) {
        this.environment = environment
    }

    override fun onSceneState(state: ThemeFinancialState) {
        val first = sceneState == null
        sceneState = state
        val target = state.normalizedFill.coerceIn(0f, 1f)
        if (first) {
            // Mirrors animateFloatAsState, which starts at the first target without animating.
            fillSpring.snapTo(target)
        } else {
            fillSpring.target = target
        }
    }

    override fun enqueue(envelope: ThemeEventEnvelope, onComplete: () -> Unit) {
        if (environment.reducedMotion) {
            // Snap to the authoritative state, spawn nothing, complete immediately.
            settle()
            onComplete()
            return
        }
        when (envelope.event) {
            is ThemePresentationEvent.Reconciled,
            is ThemePresentationEvent.SettlementCompleted,
            is ThemePresentationEvent.ThemeChanged -> interruptActiveEvent()
            else -> Unit
        }
        pendingEvents.addLast(PendingEvent(envelope, onComplete))
    }

    override fun update(dtSeconds: Float, viewportWidth: Float, viewportHeight: Float) {
        this.viewportWidth = viewportWidth
        this.viewportHeight = viewportHeight
        if (environment.reducedMotion) {
            settle()
            return
        }
        engine.advance(dtSeconds)
    }

    override fun settle() {
        fillSpring.snapTo(sceneState?.normalizedFill?.coerceIn(0f, 1f) ?: 0f)
        resetActionState()
        activeTimeline?.cancel()
        activeTimeline = null
        val interrupted = activeEvent
        activeEvent = null
        interrupted?.onComplete()
        while (pendingEvents.isNotEmpty()) {
            pendingEvents.removeFirst().onComplete()
        }
    }

    override fun render(scope: DrawScope) {
        density = scope.density
        val state = sceneState ?: return
        frame.ambient = if (environment.reducedMotion) {
            0.25f
        } else {
            (engine.timeSeconds % AMBIENT_PERIOD_SECONDS) / AMBIENT_PERIOD_SECONDS
        }
        frame.contribution = if (contributionActive) contributionProgress() else 0f
        frame.settlement = if (settlementActive) settlementProgress() else 0f
        frame.reducedMotion = environment.reducedMotion
        frame.quality = environment.quality
        frame.materials = environment.materials
        frame.assets = environment.assets
        frame.eventSeed = activeEventSeed
        frame.contributingParticipantId = activeContributorId
        frame.participantAMiner = state.participantA?.id?.let(environment.minerAppearances::get)
            ?: MinerAppearance.MAN
        frame.participantBMiner = state.participantB?.id?.let(environment.minerAppearances::get)
            ?: MinerAppearance.WOMAN
        frame.blockActive = blockActive
        frame.blockX = block.position.x
        frame.blockY = block.position.y
        frame.blockRotation = block.rotation
        frame.blockTravel = (frame.contribution / TRAVEL_COMPLETE_PROGRESS).coerceIn(0f, 1f)
        scope.drawMempoolScene(state, displayFill, frame)
    }

    override fun debugSummary(): String {
        val stage = activeTimeline?.currentStage?.id ?: "idle"
        return "mempool fill=${(displayFill * 100).toInt() / 100f} " +
            "spring=${(fillSpring.value * 1000).toInt() / 1000f} " +
            "sparks=${sparks.activeCount} block=$blockActive " +
            "miners=${minerA.state}/${minerB.state} stage=$stage queue=${pendingEvents.size}"
    }

    // ------------------------------------------------------------------ events

    private fun interruptActiveEvent() {
        val interrupted = activeEvent ?: return
        activeTimeline?.cancel()
        activeEvent = null
        activeTimeline = null
        resetActionState()
        interrupted.onComplete()
    }

    private fun finishActiveEvent() {
        val finished = activeEvent ?: return
        activeEvent = null
        activeTimeline = null
        resetActionState()
        finished.onComplete()
    }

    /** Clears every transient action visual and returns both actors to IDLE. */
    private fun resetActionState() {
        contributionActive = false
        settlementActive = false
        activeContributorId = null
        blockActive = false
        blockThrown = false
        blockLanded = false
        frame.trailCount = 0
        sparks.clear()
        minerA.reset()
        minerB.reset()
    }

    private fun startNextPendingEvent() {
        if (activeEvent != null) return
        val next = pendingEvents.removeFirstOrNull() ?: return
        activeEvent = next
        val timeline = when (val event = next.envelope.event) {
            is ThemePresentationEvent.ContributionAdded -> {
                contributionActive = true
                contributionFromFill = event.fromFill.coerceIn(0f, 1f)
                contributionTargetFill = event.targetFill.coerceIn(0f, 1f)
                activeContributorId = event.contribution.participantId
                activeEventSeed = next.envelope.id.hashCode()
                // The spring converges underneath the splice so the handover at
                // the end of the timeline is seamless.
                fillSpring.target = contributionTargetFill
                AnimationTimeline(
                    id = "contribution:${next.envelope.id}",
                    stages = listOf(
                        AnimationTimeline.Stage(STAGE_WINDUP, 0.19f),
                        AnimationTimeline.Stage(STAGE_THROW, 0.53f),
                        AnimationTimeline.Stage(STAGE_LOAD, 0.36f),
                        AnimationTimeline.Stage(STAGE_SETTLE, 0.57f)
                    ),
                    onStageEnter = ::onContributionStageEnter,
                    onComplete = ::finishActiveEvent
                )
            }
            is ThemePresentationEvent.SettlementCompleted -> {
                settlementActive = true
                activeEventSeed = next.envelope.id.hashCode()
                AnimationTimeline(
                    id = "settlement:${next.envelope.id}",
                    stages = listOf(
                        AnimationTimeline.Stage(STAGE_SURGE, 0.36f),
                        AnimationTimeline.Stage(STAGE_CREST, 0.46f),
                        AnimationTimeline.Stage(STAGE_CALM, 0.66f),
                        AnimationTimeline.Stage(STAGE_REST, 0.77f)
                    ),
                    onStageEnter = ::onSettlementStageEnter,
                    onComplete = ::finishActiveEvent
                )
            }
            is ThemePresentationEvent.Reconciled,
            is ThemePresentationEvent.ThemeChanged -> AnimationTimeline(
                id = "settle:${next.envelope.id}",
                stages = listOf(AnimationTimeline.Stage("settle", 0.42f)),
                onComplete = ::finishActiveEvent
            )
            else -> AnimationTimeline(
                id = "minor:${next.envelope.id}",
                stages = listOf(AnimationTimeline.Stage("tick", 0.08f)),
                onComplete = ::finishActiveEvent
            )
        }
        activeTimeline = timeline
        timeline.start()
    }

    private fun stepTimeline(dt: Float) {
        if (activeTimeline == null) startNextPendingEvent()
        activeTimeline?.update(dt)
    }

    private fun onContributionStageEnter(stage: AnimationTimeline.Stage) {
        val actor = contributingActor()
        when (stage.id) {
            STAGE_WINDUP -> {
                spawnBlock()
                actor?.transitionTo(ActorState.PREPARING)
            }
            STAGE_THROW -> {
                launchBlock()
                actor?.transitionTo(ActorState.MOVING)
            }
            STAGE_LOAD -> actor?.transitionTo(ActorState.RECOVERING)
            STAGE_SETTLE -> actor?.transitionTo(ActorState.IDLE)
        }
    }

    private fun onSettlementStageEnter(stage: AnimationTimeline.Stage) {
        when (stage.id) {
            STAGE_SURGE -> {
                minerA.transitionTo(ActorState.MOVING)
                minerB.transitionTo(ActorState.MOVING)
            }
            STAGE_CALM -> {
                minerA.transitionTo(ActorState.RECOVERING)
                minerB.transitionTo(ActorState.RECOVERING)
            }
            STAGE_REST -> {
                minerA.transitionTo(ActorState.IDLE)
                minerB.transitionTo(ActorState.IDLE)
            }
        }
    }

    private fun contributingActor(): ActorStateMachine? {
        val state = sceneState ?: return null
        val id = activeContributorId ?: return null
        return when (id) {
            state.participantA?.id -> minerA
            state.participantB?.id -> minerB
            else -> null
        }
    }

    /**
     * Piecewise-linear progress through the old `keyframes` stops, so every
     * progress-driven visual (miner strike, impact ring, fill splice) keeps the
     * exact old timing even though the timeline itself advances linearly.
     * [stops] holds (timeSeconds, value) pairs, ascending, ending at (total, 1).
     */
    private fun keyframeProgress(elapsedSeconds: Float, stops: FloatArray): Float {
        if (elapsedSeconds <= 0f) return 0f
        // Segment starts are pair indices 0, 2, …, stops.size - 4; the final
        // pair is the last segment's end, so never advance onto it.
        var index = 0
        while (index < stops.size - 4 && elapsedSeconds > stops[index + 2]) {
            index += 2
        }
        val t0 = stops[index]
        val v0 = stops[index + 1]
        val t1 = stops[index + 2]
        val v1 = stops[index + 3]
        if (elapsedSeconds >= t1) return v1
        val segment = (elapsedSeconds - t0) / (t1 - t0)
        return v0 + (v1 - v0) * segment
    }

    private fun contributionProgress(): Float =
        activeTimeline?.let { keyframeProgress(it.elapsedSeconds, CONTRIBUTION_STOPS) } ?: 0f

    private fun settlementProgress(): Float =
        activeTimeline?.let { keyframeProgress(it.elapsedSeconds, SETTLEMENT_STOPS) } ?: 0f

    // ------------------------------------------------------------------- block

    /**
     * Places the block at the contributing miner's hands. It hangs there during
     * the windup (no gravity yet), exactly like the old Bézier at travel = 0.
     */
    private fun spawnBlock() {
        if (viewportWidth <= 0f || viewportHeight <= 0f) return
        val state = sceneState ?: return
        val participantId = activeContributorId ?: return
        val fromLeft = participantId == state.participantA?.id
        blockOriginX = viewportWidth * (if (fromLeft) 0.22f else 0.78f)
        blockOriginY = viewportHeight * 0.47f
        blockTargetX = viewportWidth * 0.5f
        // Cart rect starts at 0.45 of the scene and is 0.32 tall. The bin mouth's
        // inner lip — where a block actually comes to rest — is about 17% down that rect.
        blockBedY = viewportHeight * (0.45f + 0.32f * 0.17f)
        block.position = Vec2(blockOriginX, blockOriginY)
        block.velocity = Vec2.Zero
        block.gravity = 0f
        block.damping = 0f
        block.radius = 4f * density
        block.rotation = 0f
        block.angularVelocity = BLOCK_SPIN_DEGREES_PER_SECOND
        block.angularDamping = 0f
        block.wake()
        blockActive = true
        blockThrown = false
        blockLanded = false
        frame.trailCount = 0
        trailStepCounter = 0
    }

    /**
     * Throws the block so gravity brings it down on the cart bed after
     * [FLIGHT_SECONDS], landing when the old keyframes reached the impact beat
     * (progress 0.68). The apex matches the old Bézier control point (~11.5% of
     * the viewport height above the origin).
     */
    private fun launchBlock() {
        if (!blockActive) return
        val gravity = viewportHeight * (8f * ARC_RISE_FRACTION / (FLIGHT_SECONDS * FLIGHT_SECONDS))
        block.gravity = gravity
        block.velocity = Vec2(
            (blockTargetX - blockOriginX) / FLIGHT_SECONDS,
            (blockBedY - blockOriginY) / FLIGHT_SECONDS - gravity * FLIGHT_SECONDS / 2f
        )
        block.wake()
        blockThrown = true
    }

    private fun stepBlock(dt: Float) {
        if (!blockActive) return
        block.integrate(dt)
        if (!blockThrown) return
        // Test the floor only while falling: the spawn point sits slightly below
        // the bed level, and bounceOnFloor would clamp (teleport) the block up
        // if called during the ascent. The check stays active after landing so
        // later bounces can never fall through the bed.
        if (block.velocity.y > 0f) {
            val bounced = block.bounceOnFloor(blockBedY)
            if ((bounced || block.grounded) && !blockLanded) {
                blockLanded = true
                // Kill most of the horizontal slide and the spin so the block
                // comes to rest near the cart center like the old static landing.
                block.velocity = Vec2(block.velocity.x * 0.25f, block.velocity.y)
                block.damping = 2.5f
                block.angularVelocity *= 0.3f
                block.angularDamping = 4f
                spawnImpactSparks()
            }
        }
        if (!blockLanded && !block.grounded) {
            trailStepCounter++
            if (trailStepCounter >= TRAIL_INTERVAL_STEPS) {
                trailStepCounter = 0
                recordTrailSample()
            }
        }
    }

    /** Appends the block position to the frame's chronological trail buffer. */
    private fun recordTrailSample() {
        if (frame.trailCount < MEMPOOL_TRAIL_SAMPLES) {
            frame.trailX[frame.trailCount] = block.position.x
            frame.trailY[frame.trailCount] = block.position.y
            frame.trailCount++
        } else {
            for (index in 0 until MEMPOOL_TRAIL_SAMPLES - 1) {
                frame.trailX[index] = frame.trailX[index + 1]
                frame.trailY[index] = frame.trailY[index + 1]
            }
            frame.trailX[MEMPOOL_TRAIL_SAMPLES - 1] = block.position.x
            frame.trailY[MEMPOOL_TRAIL_SAMPLES - 1] = block.position.y
        }
    }

    /**
     * Live replacement for the old 12 parametric impact sparks: same count,
     * same seeded angles, same radial speeds ((9 + index * 1.8) dp covered over
     * the old 528 ms impact window) and the same accent/orange color split
     * (the renderer keys color off `seed % 3`, so the seed is the spark index).
     */
    private fun spawnImpactSparks() {
        repeat(IMPACT_SPARK_COUNT) { index ->
            val angle = pseudo(index xor activeEventSeed, 0.91f) * PI.toFloat() * 2f
            val speed = (9f + index * 1.8f) * density / SPARK_LIFETIME_SECONDS
            sparks.spawn(
                x = blockTargetX,
                y = blockBedY,
                vx = cos(angle) * speed,
                vy = sin(angle) * speed,
                lifetime = SPARK_LIFETIME_SECONDS,
                size = 1f + index % 2 * 0.5f, // dp; converted at draw time
                seed = index
            )
        }
    }

    private companion object {
        const val AMBIENT_PERIOD_SECONDS = 16f
        const val SPARK_CAPACITY = 48
        const val IMPACT_SPARK_COUNT = 12
        const val SPARK_LIFETIME_SECONDS = 0.528f
        const val BLOCK_RESTITUTION = 0.35f
        const val TRAIL_INTERVAL_STEPS = 5

        const val STAGE_WINDUP = "windup"
        const val STAGE_THROW = "throw"
        const val STAGE_LOAD = "load"
        const val STAGE_SETTLE = "settle"
        const val STAGE_SURGE = "surge"
        const val STAGE_CREST = "crest"
        const val STAGE_CALM = "calm"
        const val STAGE_REST = "rest"

        /** Progress at which the old block reached the cart (impact beat). */
        const val IMPACT_PROGRESS = 0.68f
        /** Progress at which the old block's travel along the Bézier completed. */
        const val TRAVEL_COMPLETE_PROGRESS = 0.78f
        /** Flight time so the physics landing coincides with [IMPACT_PROGRESS]. */
        const val FLIGHT_SECONDS = IMPACT_PROGRESS * 1.65f - 0.19f
        /** Bézier apex height above the origin, as a fraction of viewport height. */
        const val ARC_RISE_FRACTION = 0.115f
        /** Old rotation was travel * 240°, travel completing at 0.78 * 1.65 s. */
        const val BLOCK_SPIN_DEGREES_PER_SECOND = 240f / (TRAVEL_COMPLETE_PROGRESS * 1.65f)

        /** Old ContributionAdded keyframes: 0@0ms, 0.12@190, 0.67@720, 0.88@1080, 1@1650. */
        val CONTRIBUTION_STOPS = floatArrayOf(
            0f, 0f,
            0.19f, 0.12f,
            0.72f, 0.67f,
            1.08f, 0.88f,
            1.65f, 1f
        )

        /** Old SettlementCompleted keyframes: 0@0ms, 0.18@360, 0.52@820, 0.82@1480, 1@2250. */
        val SETTLEMENT_STOPS = floatArrayOf(
            0f, 0f,
            0.36f, 0.18f,
            0.82f, 0.52f,
            1.48f, 0.82f,
            2.25f, 1f
        )
    }
}
