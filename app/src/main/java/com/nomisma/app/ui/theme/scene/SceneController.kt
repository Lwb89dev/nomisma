package com.nomisma.app.ui.theme.scene

import androidx.compose.ui.graphics.drawscope.DrawScope
import com.nomisma.app.theme.ThemeEventEnvelope
import com.nomisma.app.theme.ThemeFinancialState
import com.nomisma.app.theme.ThemeRegistry
import com.nomisma.app.ui.theme.MinerAppearance
import com.nomisma.app.ui.theme.SceneMaterials
import com.nomisma.app.ui.theme.ThemeAssetPack
import com.nomisma.app.ui.theme.ThemeRenderQuality

/**
 * Everything a scene controller needs that is owned by the composable shell:
 * device/user preferences, shader brushes and decoded artwork. Controllers must
 * treat this as read-only configuration; simulation state lives in the controller.
 */
internal class SceneEnvironment(
    val quality: ThemeRenderQuality,
    val reducedMotion: Boolean,
    val materials: SceneMaterials?,
    val assets: ThemeAssetPack?,
    val minerAppearances: Map<String, MinerAppearance>
)

/**
 * Drives one themed environment: owns the simulation (via
 * `com.nomisma.app.theme.motion.MotionEngine`), reacts to financial presentation
 * events, and renders the current frame.
 *
 * Contract:
 * - [onSceneState] delivers the authoritative financial projection. Controllers
 *   animate **toward** it (springs, targets); the visual state must always
 *   converge to it, even after interruptions.
 * - [enqueue] schedules a presentation event. [onComplete] must be invoked
 *   exactly once per enqueued event — on normal completion, on interruption, or
 *   immediately under reduced motion. Event ordering is FIFO; a `Reconciled`,
 *   `SettlementCompleted` or `ThemeChanged` event may interrupt in-flight work,
 *   but must still complete the interrupted callbacks.
 * - [update] advances the simulation with a monotonic, clamped frame delta in
 *   seconds and the current viewport size in pixels. Never called while the
 *   scene is off-screen; under reduced motion the shell calls [settle] instead.
 * - [render] only reads simulation state and draws; it must not advance time.
 */
internal interface SceneController {
    val themeId: String

    /** True while any event animation, particle or unsettled body is active. */
    val isAnimating: Boolean

    /** Current animated fill in [0, 1]; used by the shell for labels and semantics. */
    val displayFill: Float

    fun updateEnvironment(environment: SceneEnvironment)

    fun onSceneState(state: ThemeFinancialState)

    fun enqueue(envelope: ThemeEventEnvelope, onComplete: () -> Unit)

    fun update(dtSeconds: Float, viewportWidth: Float, viewportHeight: Float)

    /** Snap every animated value to the authoritative state (reduced motion, restore). */
    fun settle()

    fun render(scope: DrawScope)

    /** One-line diagnostic for the debug overlay (debug builds only). */
    fun debugSummary(): String
}

internal object SceneControllerFactory {
    fun create(themeId: String): SceneController = when (themeId) {
        ThemeRegistry.WOODLAND -> WoodlandSceneController()
        ThemeRegistry.MEMPOOL -> MempoolSceneController()
        else -> VesselSceneController()
    }
}
