package com.nomisma.app.ui.theme

import android.animation.ValueAnimator
import android.app.ActivityManager
import android.content.Context
import android.os.PowerManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nomisma.app.BuildConfig
import com.nomisma.app.theme.ThemeEventEnvelope
import com.nomisma.app.theme.ThemeFinancialState
import com.nomisma.app.theme.ThemeIdentityRegistry
import com.nomisma.app.theme.ThemeRegistry
import com.nomisma.app.ui.theme.scene.SceneControllerFactory
import com.nomisma.app.ui.theme.scene.SceneEnvironment
import com.nomisma.app.ui.theme.scene.sceneColor
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

enum class ThemeRenderQuality { LOW, MEDIUM, HIGH }

enum class MinerAppearance {
    MAN,
    WOMAN;

    companion object {
        fun fromStorage(value: String?): MinerAppearance =
            entries.firstOrNull { it.name == value } ?: MAN
    }
}

enum class ThemeQualityPreference {
    AUTO,
    LOW,
    MEDIUM,
    HIGH;

    companion object {
        fun fromStorage(value: String?): ThemeQualityPreference =
            entries.firstOrNull { it.name == value } ?: AUTO
    }
}

enum class ThemeMotionPreference {
    SYSTEM,
    REDUCED;

    companion object {
        fun fromStorage(value: String?): ThemeMotionPreference =
            entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}

/**
 * Composable shell for the themed scene. All simulation and drawing lives in the
 * per-theme [com.nomisma.app.ui.theme.scene.SceneController]; this shell only owns
 * the lifecycle-aware frame loop (monotonic deltas in seconds), feeds events FIFO
 * and renders the overlay chrome. Event staleness is no longer discarded here:
 * backlogs collapse inside `ThemeEventCoordinator`.
 */
@Composable
fun NomismaThemeScene(
    state: ThemeFinancialState,
    themeId: String,
    qualityPreference: ThemeQualityPreference = ThemeQualityPreference.AUTO,
    motionPreference: ThemeMotionPreference = ThemeMotionPreference.SYSTEM,
    minerAppearances: Map<String, MinerAppearance> = emptyMap(),
    events: List<ThemeEventEnvelope> = emptyList(),
    onEventConsumed: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var lifecycleActive by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, _ ->
            lifecycleActive = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val quality = remember(context, qualityPreference) { resolveQuality(context, qualityPreference) }
    val reducedMotion = remember(motionPreference) {
        motionPreference == ThemeMotionPreference.REDUCED || !ValueAnimator.areAnimatorsEnabled()
    }
    val sceneMaterials = remember(quality) {
        if (quality == ThemeRenderQuality.LOW) null else SceneMaterialsFactory.create()
    }
    val sceneAssets = rememberThemeAssetPack(context, themeId, quality)

    val controller = remember(themeId) { SceneControllerFactory.create(themeId) }
    val environment = remember(quality, reducedMotion, sceneMaterials, sceneAssets, minerAppearances) {
        SceneEnvironment(quality, reducedMotion, sceneMaterials, sceneAssets, minerAppearances)
    }

    // frameTick invalidates the Canvas draw; displayFill feeds the overlay chrome.
    val frameTick = remember(controller) { mutableLongStateOf(0L) }
    val displayFill = remember(controller) { mutableFloatStateOf(state.normalizedFill.coerceIn(0f, 1f)) }
    var viewportWidth by remember(controller) { mutableFloatStateOf(0f) }
    var viewportHeight by remember(controller) { mutableFloatStateOf(0f) }

    // Push authoritative state/config into the controller. Under reduced motion the
    // frame loop never runs, so snap and invalidate explicitly.
    LaunchedEffect(controller, environment, state) {
        controller.updateEnvironment(environment)
        controller.onSceneState(state)
        if (reducedMotion) {
            controller.settle()
            displayFill.floatValue = controller.displayFill
            frameTick.longValue++
        }
    }

    // Feed events FIFO; the controller queues, interrupts and completes them.
    val enqueuedEventIds = remember(controller) { mutableSetOf<String>() }
    LaunchedEffect(controller, events) {
        val ledgerEvents = events.filter { it.ledgerId == state.ledgerId }
        enqueuedEventIds.retainAll(ledgerEvents.mapTo(HashSet()) { it.id })
        ledgerEvents.forEach { envelope ->
            if (enqueuedEventIds.add(envelope.id)) {
                controller.enqueue(envelope) {
                    onEventConsumed(envelope.id)
                }
            }
        }
    }

    LaunchedEffect(controller, reducedMotion, lifecycleActive) {
        if (reducedMotion || !lifecycleActive) return@LaunchedEffect
        var lastFrameNanos = -1L
        while (isActive) {
            withFrameNanos { now ->
                if (lastFrameNanos < 0L) {
                    lastFrameNanos = now
                } else {
                    val dtSeconds = ((now - lastFrameNanos) / 1_000_000_000f).coerceIn(0f, 0.1f)
                    lastFrameNanos = now
                    controller.update(dtSeconds, viewportWidth, viewportHeight)
                    displayFill.floatValue = controller.displayFill
                    frameTick.longValue = now
                }
            }
        }
    }

    val displayFillValue = displayFill.floatValue
    val sceneShape = RoundedCornerShape(32.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(400.dp)
            .clip(sceneShape)
            .background(Color(0xFF080A0E))
            .semantics {
                contentDescription = buildString {
                    append("Visualizzazione ")
                    append(ThemeRegistry.definitions().firstOrNull { it.id == themeId }?.displayName ?: "Vessel")
                    append(", ciclo al ")
                    append((displayFillValue * 100).roundToInt())
                    append(" per cento")
                    if (state.overflowRatio > 0f) append(", soglia superata")
                }
            }
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .onSizeChanged {
                    viewportWidth = it.width.toFloat()
                    viewportHeight = it.height.toFloat()
                }
        ) {
            frameTick.longValue
            controller.render(this)
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(color = Color.Black.copy(alpha = 0.34f), shape = CircleShape) {
                Text(
                    text = when (themeId) {
                        ThemeRegistry.WOODLAND -> "WOODLAND"
                        ThemeRegistry.MEMPOOL -> "MEMPOOL"
                        else -> "VESSEL"
                    },
                    color = Color.White.copy(alpha = 0.82f),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp)
                )
            }
            Surface(
                color = if (state.overflowRatio > 0f) Color(0xFF6E281D).copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.34f),
                shape = CircleShape
            ) {
                Text(
                    text = if (state.overflowRatio > 0f) {
                        "OLTRE SOGLIA +${(state.overflowRatio * 100).roundToInt()}%"
                    } else {
                        "${(displayFillValue * 100).roundToInt()}%"
                    },
                    color = if (state.overflowRatio > 0f) Color(0xFFFFC0AA) else Color.White.copy(alpha = 0.86f),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp)
                )
            }
        }

        Row(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            listOfNotNull(state.participantA, state.participantB).forEach { participant ->
                val participantColor = participant.colorHex.sceneColor(Color.White)
                Surface(color = Color(0xFF080A0E).copy(alpha = 0.78f), shape = CircleShape) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(Modifier.size(7.dp).background(participantColor, CircleShape))
                        Text(
                            "${ThemeIdentityRegistry.symbol(participant.identityId)} ${participant.name}",
                            color = Color.White.copy(alpha = 0.92f),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }

        if (BuildConfig.DEBUG) {
            frameTick.longValue
            Text(
                text = controller.debugSummary(),
                color = Color.White.copy(alpha = 0.6f),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 14.dp, bottom = 14.dp)
            )
        }
    }
}

private fun resolveQuality(context: Context, preference: ThemeQualityPreference): ThemeRenderQuality {
    when (preference) {
        ThemeQualityPreference.LOW -> return ThemeRenderQuality.LOW
        ThemeQualityPreference.MEDIUM -> return ThemeRenderQuality.MEDIUM
        ThemeQualityPreference.HIGH -> return ThemeRenderQuality.HIGH
        ThemeQualityPreference.AUTO -> Unit
    }
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return when {
        activityManager.isLowRamDevice || powerManager.isPowerSaveMode -> ThemeRenderQuality.LOW
        android.os.Build.VERSION.SDK_INT >= 31 -> ThemeRenderQuality.HIGH
        else -> ThemeRenderQuality.MEDIUM
    }
}
