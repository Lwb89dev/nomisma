package com.nomisma.app.ui.theme.scene

import androidx.compose.ui.graphics.drawscope.DrawScope
import com.nomisma.app.theme.ThemeEventEnvelope
import com.nomisma.app.theme.ThemeFinancialState
import com.nomisma.app.theme.ThemePresentationEvent
import com.nomisma.app.theme.ThemeRegistry
import com.nomisma.app.theme.motion.AnimationTimeline
import com.nomisma.app.theme.motion.MotionBody
import com.nomisma.app.theme.motion.MotionEngine
import com.nomisma.app.theme.motion.ParticlePool
import com.nomisma.app.theme.motion.Spring1D
import com.nomisma.app.theme.motion.Vec2
import com.nomisma.app.theme.motion.smoothStep
import com.nomisma.app.ui.theme.ThemeRenderQuality

/**
 * VESSEL scene: glass pitcher with two-tone liquid. Owns the fill spring, the
 * ambient clock, the event timelines and the splash droplet simulation.
 *
 * The old shell drove this scene with hardcoded `Animatable` keyframes and a
 * parametric fake-physics splash; here events are `AnimationTimeline`s stepped
 * by the fixed-step [MotionEngine], and droplets live in a [ParticlePool] whose
 * collisions against the liquid surface and the vessel walls are resolved with
 * a scratch [MotionBody] (restitution, grounding = merging into the liquid).
 */
internal class VesselSceneController : SceneController {

    override val themeId: String = ThemeRegistry.VESSEL

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
    private val droplets = ParticlePool(DROPLET_CAPACITY)
    private val dropletScratchBody = MotionBody(restitution = DROPLET_RESTITUTION)
    private val frame = VesselFrame()

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

    // Collision geometry captured when a splash spawns (pixels).
    private var splashSurfaceY = 0f

    init {
        engine.add(MotionEngine.Simulatable { dt -> fillSpring.step(dt) })
        engine.add(MotionEngine.Simulatable { dt -> stepTimeline(dt) })
        engine.add(MotionEngine.Simulatable { dt -> stepDroplets(dt) })
    }

    override val isAnimating: Boolean
        get() = activeTimeline?.running == true || pendingEvents.isNotEmpty() ||
            droplets.activeCount > 0 || !fillSpring.atRest

    override val displayFill: Float
        get() = if (contributionActive) {
            val progress = activeTimeline?.progress ?: 0f
            contributionFromFill + (contributionTargetFill - contributionFromFill) *
                smoothStep(0.32f, 0.92f, progress)
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
        droplets.clear()
        contributionActive = false
        settlementActive = false
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
        frame.state = state
        frame.fill = displayFill
        frame.ambient = if (environment.reducedMotion) {
            0.25f
        } else {
            (engine.timeSeconds % AMBIENT_PERIOD_SECONDS) / AMBIENT_PERIOD_SECONDS
        }
        frame.contribution = if (contributionActive) activeTimeline?.progress ?: 0f else 0f
        frame.settlement = if (settlementActive) activeTimeline?.progress ?: 0f else 0f
        frame.quality = environment.quality
        frame.materials = environment.materials
        frame.assets = environment.assets
        frame.eventSeed = activeEventSeed
        frame.contributingParticipantId = activeContributorId
        frame.droplets = droplets
        scope.drawVesselScene(frame)
    }

    override fun debugSummary(): String {
        val stage = activeTimeline?.currentStage?.id ?: "idle"
        return "vessel fill=${(displayFill * 100).toInt() / 100f} " +
            "spring=${(fillSpring.value * 1000).toInt() / 1000f} " +
            "droplets=${droplets.activeCount} stage=$stage queue=${pendingEvents.size}"
    }

    // ------------------------------------------------------------------ events

    private fun interruptActiveEvent() {
        val interrupted = activeEvent ?: return
        activeTimeline?.cancel()
        activeEvent = null
        activeTimeline = null
        contributionActive = false
        settlementActive = false
        droplets.clear()
        interrupted.onComplete()
    }

    private fun finishActiveEvent() {
        val finished = activeEvent ?: return
        activeEvent = null
        activeTimeline = null
        contributionActive = false
        settlementActive = false
        finished.onComplete()
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
                        AnimationTimeline.Stage("pour", 0.19f),
                        AnimationTimeline.Stage("stream", 0.53f),
                        AnimationTimeline.Stage("impact", 0.36f),
                        AnimationTimeline.Stage("fade", 0.57f)
                    ),
                    onComplete = ::finishActiveEvent
                )
            }
            is ThemePresentationEvent.SettlementCompleted -> {
                settlementActive = true
                activeEventSeed = next.envelope.id.hashCode()
                AnimationTimeline(
                    id = "settlement:${next.envelope.id}",
                    stages = listOf(
                        AnimationTimeline.Stage("surge", 0.36f),
                        AnimationTimeline.Stage("crest", 0.46f),
                        AnimationTimeline.Stage("calm", 0.66f),
                        AnimationTimeline.Stage("rest", 0.77f)
                    ),
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
                stages = listOf(AnimationTimeline.Stage("tick", 0.10f)),
                onComplete = ::finishActiveEvent
            )
        }
        activeTimeline = timeline
        timeline.start()
    }

    private fun stepTimeline(dt: Float) {
        if (activeTimeline == null) startNextPendingEvent()
        val timeline = activeTimeline ?: return
        val playingEvent = activeEvent
        val beforeSplash = contributionActive && timeline.progress <= SPLASH_START_PROGRESS
        timeline.update(dt)
        // The timeline may have completed inside update() (firing
        // finishActiveEvent); only spawn if the same event is still playing.
        if (contributionActive && activeEvent === playingEvent &&
            beforeSplash && timeline.progress > SPLASH_START_PROGRESS
        ) {
            spawnSplash()
        }
    }

    // ---------------------------------------------------------------- droplets

    private fun spawnSplash() {
        val state = sceneState ?: return
        if (viewportWidth <= 0f || viewportHeight <= 0f) return
        val participantId = activeContributorId ?: return
        val fill = displayFill
        // The neck has no room for a splash: droplets would clip through the
        // lip and bounce on a degenerate span. The spout pour replaces them.
        if (VesselGeometry.spillsOver(fill)) return
        val surfaceV = VesselGeometry.surfaceV(fill)
        val (leftU, rightU) = VesselGeometry.spanAtV(surfaceV)
        val fromLeft = participantId == state.participantA?.id
        val side = if (fromLeft) -1f else 1f
        val pourU = ((leftU + rightU) / 2f + side * (rightU - leftU) * 0.12f).coerceIn(leftU, rightU)
        val surfaceY = VesselGeometry.canvasY(viewportHeight, surfaceV)
        val floorY = VesselGeometry.canvasY(viewportHeight, VesselGeometry.FILL_BOTTOM_V)
        splashSurfaceY = if (fill > 0.01f) surfaceY else floorY
        val impactX = VesselGeometry.canvasX(viewportWidth, pourU)
        val impactY = splashSurfaceY
        val dropCount = when (environment.quality) {
            ThemeRenderQuality.LOW -> 14
            ThemeRenderQuality.MEDIUM -> 23
            ThemeRenderQuality.HIGH -> 34
        }
        val gravity = 132f * density
        repeat(dropCount) { index ->
            val seededIndex = index xor activeEventSeed
            val direction = if (index % 2 == 0) -1f else 1f
            // Per-droplet rate replaces the old fake time scale
            // t = splashTime * (0.73 + pseudo(...)): scaling velocity by r and
            // gravity by r^2 reproduces exactly that trajectory over real time.
            val rate = 0.73f + pseudo(seededIndex, 0.57f) * 0.44f
            val speedX = direction * (18f + pseudo(seededIndex, 0.31f) * 73f) * density
            val speedY = (30f + pseudo(seededIndex, 0.82f) * 88f) * density
            droplets.spawn(
                x = impactX,
                y = impactY,
                vx = speedX * rate,
                vy = -speedY * rate,
                gravity = gravity * rate * rate,
                lifetime = SPLASH_LIFETIME_SECONDS,
                size = 1.0f + pseudo(seededIndex, 0.11f) * 2.35f, // dp; converted at draw time
                seed = seededIndex
            )
        }
    }

    private fun stepDroplets(dt: Float) {
        if (droplets.activeCount <= 0) return
        droplets.update(dt)
        val wallInset = 4f * density
        droplets.forEachActive { particle ->
            val particleV = VesselGeometry.vAt(viewportHeight, particle.y)
            if (viewportHeight > 0f && particleV < VesselGeometry.FILL_TOP_V) {
                particle.active = false
                return@forEachActive
            }
            val (leftU, rightU) = VesselGeometry.spanAtV(particleV)
            val left = VesselGeometry.canvasX(viewportWidth, leftU) + wallInset
            val right = VesselGeometry.canvasX(viewportWidth, rightU) - wallInset
            val hitsFloor = particle.vy > 0f && particle.y >= splashSurfaceY
            val hitsWall = right > left && (
                (particle.x < left && particle.vx < 0f) ||
                    (particle.x > right && particle.vx > 0f)
                )
            if (!hitsFloor && !hitsWall) return@forEachActive
            val body = dropletScratchBody
            body.position = Vec2(particle.x, particle.y)
            body.velocity = Vec2(particle.vx, particle.vy)
            body.gravity = particle.gravity
            body.radius = 0f
            body.wake()
            var merged = false
            if (hitsFloor) {
                // Weak impacts ground the body on the surface: the droplet has
                // merged back into the liquid and is removed.
                val bounced = body.bounceOnFloor(splashSurfaceY)
                if (!bounced && body.grounded) merged = true
            }
            if (!merged && hitsWall) {
                body.bounceOnWalls(left, right)
            }
            particle.x = body.position.x
            particle.y = body.position.y
            particle.vx = body.velocity.x
            particle.vy = body.velocity.y
            if (merged) particle.active = false
        }
    }

    private companion object {
        const val AMBIENT_PERIOD_SECONDS = 16f
        const val SPLASH_START_PROGRESS = 0.29f
        const val SPLASH_LIFETIME_SECONDS = 0.99f
        const val DROPLET_RESTITUTION = 0.38f
        const val DROPLET_CAPACITY = 36
    }
}
