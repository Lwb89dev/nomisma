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
import com.nomisma.app.ui.theme.ThemeRenderQuality
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Woodland scene controller: forest diorama with two animal characters and a
 * wooden container. Owns the whole simulation — fill spring, contribution /
 * settlement timelines, flying token projectiles ([MotionBody]), landing
 * sparks ([ParticlePool]) and per-participant [ActorStateMachine]s — and feeds
 * a reused [WoodlandFrame] to the drawing code in WoodlandSceneDrawing.kt.
 */
internal class WoodlandSceneController : SceneController {

    override val themeId: String = ThemeRegistry.WOODLAND

    private val engine = MotionEngine()

    /** Critically damped; replaces the old 1050 ms animateFloatAsState. */
    private val fillSpring = Spring1D(stiffness = FILL_SPRING_STIFFNESS, dampingRatio = 1f, initial = 0f)

    /** 0..1 approach of each animal toward the container while MOVING/INTERACTING. */
    private val travelSpringA = Spring1D(stiffness = TRAVEL_SPRING_STIFFNESS, dampingRatio = 1f, initial = 0f)
    private val travelSpringB = Spring1D(stiffness = TRAVEL_SPRING_STIFFNESS, dampingRatio = 1f, initial = 0f)

    private val actorA = ActorStateMachine()
    private val actorB = ActorStateMachine()

    private val sparkPool = ParticlePool(SPARK_CAPACITY)

    private var environment = SceneEnvironment(
        quality = ThemeRenderQuality.LOW,
        reducedMotion = false,
        materials = null,
        assets = null,
        minerAppearances = emptyMap()
    )
    private var sceneState: ThemeFinancialState? = null
    private var viewportWidth = 0f
    private var viewportHeight = 0f

    private class PendingEvent(
        val envelope: ThemeEventEnvelope,
        val onComplete: () -> Unit
    )

    private val pendingEvents = ArrayDeque<PendingEvent>()
    private var activeEvent: PendingEvent? = null

    /** Countdown for events with no timeline (settle 0.42 s / minor 0.10 s); <= 0 means inactive. */
    private var activeTimerSeconds = -1f

    private var contributionTimeline: AnimationTimeline? = null
    private var settlementTimeline: AnimationTimeline? = null
    private var contributionFromFill = 0f
    private var contributionTargetFill = 0f
    private var contributingParticipantId: String? = null
    private var eventSeed = 0

    /** A tossed acorn/walnut: a projectile launched toward the container opening. */
    private class FlyingToken {
        val body = MotionBody(restitution = TOKEN_RESTITUTION)
        var launched = false
        var absorbed = false
        var burstSpawned = false
        var ageSeconds = 0f
        var isWalnut = true
        var floorY = 0f

        fun reset() {
            launched = false
            absorbed = false
            burstSpawned = false
            ageSeconds = 0f
            body.position = Vec2.Zero
            body.velocity = Vec2.Zero
            body.gravity = 0f
            body.rotation = 0f
            body.angularVelocity = 0f
        }
    }

    private val flyingTokens = Array(TOKEN_COUNT) { FlyingToken() }

    /** Reused every frame; mutated in [render], never reallocated. */
    private val frame = WoodlandFrame()

    init {
        engine.add(MotionEngine.Simulatable { dt -> stepWorld(dt) })
    }

    override val isAnimating: Boolean
        get() = activeEvent != null ||
            pendingEvents.isNotEmpty() ||
            contributionTimeline?.running == true ||
            settlementTimeline?.running == true ||
            sparkPool.activeCount > 0 ||
            flyingTokens.any { it.launched && !it.absorbed } ||
            !fillSpring.atRest ||
            !travelSpringA.atRest ||
            !travelSpringB.atRest

    override val displayFill: Float
        get() {
            val timeline = contributionTimeline
            return if (timeline != null && timeline.running) {
                contributionFromFill +
                    (contributionTargetFill - contributionFromFill) * smoothStep(0.32f, 0.92f, timeline.progress)
            } else {
                fillSpring.value
            }
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
            settle()
            onComplete()
            return
        }
        pendingEvents.addLast(PendingEvent(envelope, onComplete))
        when (envelope.event) {
            is ThemePresentationEvent.Reconciled,
            is ThemePresentationEvent.SettlementCompleted,
            is ThemePresentationEvent.ThemeChanged -> interruptActiveEvent()
            else -> Unit
        }
        pumpQueue()
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
        contributionTimeline?.cancel()
        settlementTimeline?.cancel()
        contributionTimeline = null
        settlementTimeline = null
        cleanupContributionVisuals()
        activeTimerSeconds = -1f
        fillSpring.snapTo(sceneState?.normalizedFill?.coerceIn(0f, 1f) ?: 0f)
        travelSpringA.snapTo(0f)
        travelSpringB.snapTo(0f)
        // Every enqueued event must complete exactly once, even when snapped.
        val flushed = mutableListOf<PendingEvent>()
        activeEvent?.let(flushed::add)
        activeEvent = null
        flushed.addAll(pendingEvents)
        pendingEvents.clear()
        flushed.forEach { it.onComplete() }
    }

    override fun render(scope: DrawScope) {
        val state = sceneState ?: return
        val reduced = environment.reducedMotion
        frame.ambient = if (reduced) REDUCED_AMBIENT else (engine.timeSeconds % AMBIENT_LOOP_SECONDS) / AMBIENT_LOOP_SECONDS
        val contribution = contributionTimeline
        frame.contribution = if (contribution != null && contribution.running) contribution.progress else 0f
        val settlement = settlementTimeline
        frame.settlement = if (settlement != null && settlement.running) settlement.progress else 0f
        frame.reducedMotion = reduced
        frame.quality = environment.quality
        frame.materials = environment.materials
        frame.assets = environment.assets
        frame.eventSeed = eventSeed
        frame.contributingParticipantId = contributingParticipantId
        frame.travelA = travelSpringA.value
        frame.travelB = travelSpringB.value
        frame.sparks = if (sparkPool.activeCount > 0) sparkPool else null
        for (index in 0 until TOKEN_COUNT) {
            val token = flyingTokens[index]
            val render = frame.tokens[index]
            render.visible = token.launched && !token.absorbed
            if (render.visible) {
                render.isWalnut = token.isWalnut
                render.x = token.body.position.x
                render.y = token.body.position.y
                val flight = (token.ageSeconds / TOKEN_FLIGHT_SECONDS).coerceIn(0f, 1f)
                render.scale = 0.92f + sin(flight * PI.toFloat()) * 0.40f
                render.rotation = token.body.rotation
            }
        }
        scope.drawWoodlandScene(state, displayFill, frame)
    }

    override fun debugSummary(): String {
        val stage = contributionTimeline?.takeIf { it.running }?.currentStage?.id
            ?: settlementTimeline?.takeIf { it.running }?.currentStage?.id
            ?: "idle"
        val airborne = flyingTokens.count { it.launched && !it.absorbed }
        val actor = contributorActor()?.state ?: ActorState.IDLE
        return "woodland fill=%.2f sparks=%d tokens=%d stage=%s actor=%s".format(
            displayFill, sparkPool.activeCount, airborne, stage, actor
        )
    }

    // ------------------------------------------------------------------ events

    private fun pumpQueue() {
        if (activeEvent != null) return
        val next = pendingEvents.removeFirstOrNull() ?: return
        activeEvent = next
        when (val event = next.envelope.event) {
            is ThemePresentationEvent.ContributionAdded -> startContribution(next.envelope, event)
            is ThemePresentationEvent.SettlementCompleted -> startSettlement()
            is ThemePresentationEvent.Reconciled,
            is ThemePresentationEvent.ThemeChanged -> activeTimerSeconds = SETTLE_SECONDS
            else -> activeTimerSeconds = MINOR_SECONDS
        }
    }

    /**
     * Reconciled / SettlementCompleted / ThemeChanged interrupt in-flight work:
     * the interrupted event's callback still fires exactly once, timelines are
     * cancelled without completion, and the fill eases from the interrupted
     * splice point toward the authoritative state.
     */
    private fun interruptActiveEvent() {
        val interrupted = activeEvent ?: return
        contributionTimeline?.let { timeline ->
            if (timeline.running) {
                timeline.cancel()
                fillSpring.snapTo(
                    contributionFromFill +
                        (contributionTargetFill - contributionFromFill) * smoothStep(0.32f, 0.92f, timeline.progress)
                )
                sceneState?.let { fillSpring.target = it.normalizedFill.coerceIn(0f, 1f) }
            }
        }
        contributionTimeline = null
        settlementTimeline?.cancel()
        settlementTimeline = null
        cleanupContributionVisuals()
        activeTimerSeconds = -1f
        activeEvent = null
        interrupted.onComplete()
    }

    private fun cleanupContributionVisuals() {
        contributingParticipantId = null
        for (token in flyingTokens) token.reset()
        sparkPool.clear()
        actorA.reset()
        actorB.reset()
    }

    private fun completeActiveEvent() {
        val finished = activeEvent
        activeEvent = null
        activeTimerSeconds = -1f
        finished?.onComplete()
        pumpQueue()
    }

    private fun startContribution(envelope: ThemeEventEnvelope, event: ThemePresentationEvent.ContributionAdded) {
        eventSeed = envelope.id.hashCode()
        contributionFromFill = event.fromFill
        contributionTargetFill = event.targetFill
        contributingParticipantId = event.contribution.participantId
        for (token in flyingTokens) token.reset()
        contributorActor()?.transitionTo(ActorState.PREPARING)
        val timeline = AnimationTimeline(
            id = "woodland-contribution",
            stages = listOf(
                AnimationTimeline.Stage(STAGE_WINDUP, 0.19f),
                AnimationTimeline.Stage(STAGE_TRAVEL, 0.53f),
                AnimationTimeline.Stage(STAGE_DROP, 0.36f),
                AnimationTimeline.Stage(STAGE_RECOVER, 0.57f)
            ),
            onStageEnter = { stage ->
                when (stage.id) {
                    STAGE_WINDUP -> contributorActor()?.transitionTo(ActorState.PREPARING)
                    STAGE_TRAVEL -> contributorActor()?.transitionTo(ActorState.MOVING)
                    STAGE_DROP -> contributorActor()?.transitionTo(ActorState.INTERACTING)
                    STAGE_RECOVER -> contributorActor()?.transitionTo(ActorState.RECOVERING)
                }
            },
            onComplete = { finishContribution() }
        )
        contributionTimeline = timeline
        timeline.start()
    }

    private fun finishContribution() {
        contributorActor()?.transitionTo(ActorState.IDLE)
        contributingParticipantId = null
        contributionTimeline = null
        // The splice ends exactly at targetFill; hand control back to the spring
        // without a discontinuity.
        fillSpring.snapTo(contributionTargetFill.coerceIn(0f, 1f))
        sceneState?.let { fillSpring.target = it.normalizedFill.coerceIn(0f, 1f) }
        completeActiveEvent()
    }

    private fun startSettlement() {
        val timeline = AnimationTimeline(
            id = "woodland-settlement",
            stages = listOf(
                AnimationTimeline.Stage("arise", 0.36f),
                AnimationTimeline.Stage("crest", 0.46f),
                AnimationTimeline.Stage("ease", 0.66f),
                AnimationTimeline.Stage("fade", 0.77f)
            ),
            onComplete = {
                settlementTimeline = null
                completeActiveEvent()
            }
        )
        settlementTimeline = timeline
        timeline.start()
    }

    private fun contributorActor(): ActorStateMachine? {
        val state = sceneState ?: return null
        val id = contributingParticipantId ?: return null
        return when (id) {
            state.participantA?.id -> actorA
            state.participantB?.id -> actorB
            else -> null
        }
    }

    // -------------------------------------------------------------- simulation

    private fun stepWorld(dt: Float) {
        fillSpring.step(dt)

        travelSpringA.target = if (actorA.state == ActorState.MOVING || actorA.state == ActorState.INTERACTING) 1f else 0f
        travelSpringB.target = if (actorB.state == ActorState.MOVING || actorB.state == ActorState.INTERACTING) 1f else 0f
        travelSpringA.step(dt)
        travelSpringB.step(dt)
        actorA.update(dt)
        actorB.update(dt)

        contributionTimeline?.let { timeline ->
            if (timeline.running) {
                timeline.update(dt)
                if (timeline.running) maybeLaunchTokens(timeline.progress)
            }
        }
        settlementTimeline?.let { timeline ->
            if (timeline.running) timeline.update(dt)
        }

        stepTokens(dt)
        sparkPool.update(dt)

        if (activeTimerSeconds > 0f) {
            activeTimerSeconds -= dt
            if (activeTimerSeconds <= 0f) {
                activeTimerSeconds = -1f
                completeActiveEvent()
            }
        }
    }

    private fun maybeLaunchTokens(progress: Float) {
        if (viewportWidth <= 0f || viewportHeight <= 0f) return
        for (index in 0 until TOKEN_COUNT) {
            val token = flyingTokens[index]
            if (token.launched || progress < index * TOKEN_STAGGER) continue
            launchToken(token, index)
        }
    }

    /**
     * Ballistic launch replacing the old PathMeasure-sampled quadratic Bézier:
     * the initial velocity is solved so the body reaches the container opening
     * after [TOKEN_FLIGHT_SECONDS] under constant gravity.
     */
    private fun launchToken(token: FlyingToken, index: Int) {
        val state = sceneState
        val fromLeft = contributingParticipantId == state?.participantA?.id
        val width = viewportWidth
        val height = viewportHeight
        val originX = if (fromLeft) width * 0.20f else width * 0.80f
        val originY = height * 0.48f + index * 0.0075f * height
        val targetX = width * 0.50f
        val targetY = height * 0.465f
        val gravity = TOKEN_GRAVITY_HEIGHTS * height
        val flight = TOKEN_FLIGHT_SECONDS
        token.body.position = Vec2(originX, originY)
        token.body.velocity = Vec2(
            (targetX - originX) / flight,
            (targetY - originY) / flight - 0.5f * gravity * flight
        )
        token.body.gravity = gravity
        token.body.radius = 0.0125f * height
        token.body.rotation = 0f
        token.body.angularVelocity = (270f + index * 45f) * (if (fromLeft) 1f else -1f) / flight
        token.body.wake()
        token.floorY = targetY
        token.isWalnut = fromLeft
        token.launched = true
        token.absorbed = false
        token.burstSpawned = false
        token.ageSeconds = 0f
    }

    private fun stepTokens(dt: Float) {
        for (token in flyingTokens) {
            if (!token.launched || token.absorbed) continue
            token.body.integrate(dt)
            token.ageSeconds += dt
            val bounced = token.body.bounceOnFloor(token.floorY)
            if (bounced && !token.burstSpawned) {
                spawnImpactBurst(token)
                token.burstSpawned = true
            }
            if (token.body.grounded) {
                if (!token.burstSpawned) {
                    spawnImpactBurst(token)
                    token.burstSpawned = true
                }
                token.absorbed = true
            }
        }
    }

    /** Landing sparks; angles match the old fixed burst, motion is now integrated. */
    private fun spawnImpactBurst(token: FlyingToken) {
        val height = viewportHeight
        val originX = token.body.position.x
        val originY = token.floorY
        for (index in 0 until SPARKS_PER_BURST) {
            val angle = PI.toFloat() * (1.1f + index * 0.2f + pseudo(index xor eventSeed, 0.46f) * 0.08f)
            sparkPool.spawn(
                x = originX,
                y = originY,
                vx = cos(angle) * SPARK_SPEED_X_HEIGHTS * height,
                vy = sin(angle) * SPARK_SPEED_Y_HEIGHTS * height,
                lifetime = SPARK_LIFETIME_SECONDS,
                size = SPARK_SIZE_HEIGHTS * height,
                seed = eventSeed + index
            )
        }
    }

    companion object {
        internal const val TOKEN_COUNT = 3

        /** Token i launches once contribution progress reaches i * TOKEN_STAGGER (old 0.105 offset). */
        private const val TOKEN_STAGGER = 0.105f

        /** Old flight span: delayed = (progress - stagger) / 0.78 over a 1.65 s timeline. */
        private const val TOKEN_FLIGHT_SECONDS = 1.287f

        /** Gravity in scene heights / s^2; chosen so the arc apex (~0.135 h) matches the old Bézier. */
        private const val TOKEN_GRAVITY_HEIGHTS = 0.65f
        private const val TOKEN_RESTITUTION = 0.32f

        private const val SPARK_CAPACITY = 24
        private const val SPARKS_PER_BURST = 5

        /** Old burst: 15 dp over 0.462 s horizontally, 9 dp vertically on a 400 dp-tall scene. */
        private const val SPARK_SPEED_X_HEIGHTS = 0.081f
        private const val SPARK_SPEED_Y_HEIGHTS = 0.049f
        private const val SPARK_LIFETIME_SECONDS = 0.462f
        private const val SPARK_SIZE_HEIGHTS = 0.00375f

        /** ~1050 ms settle of the old fill tween. */
        private const val FILL_SPRING_STIFFNESS = 22f
        private const val TRAVEL_SPRING_STIFFNESS = 48f

        private const val AMBIENT_LOOP_SECONDS = 16f
        private const val REDUCED_AMBIENT = 0.25f
        private const val SETTLE_SECONDS = 0.42f
        private const val MINOR_SECONDS = 0.10f

        private const val STAGE_WINDUP = "windup"
        private const val STAGE_TRAVEL = "travel"
        private const val STAGE_DROP = "drop"
        private const val STAGE_RECOVER = "recover"
    }
}
