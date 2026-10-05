package com.nomisma.app.ui.theme

import android.animation.ValueAnimator
import android.app.ActivityManager
import android.content.Context
import android.os.PowerManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nomisma.app.theme.ThemeFinancialState
import com.nomisma.app.theme.ThemeIdentityRegistry
import com.nomisma.app.theme.ThemePresentationEvent
import com.nomisma.app.theme.ThemePresentationEventResolver
import com.nomisma.app.theme.ThemeRegistry
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

enum class ThemeRenderQuality { LOW, MEDIUM, HIGH }

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

private data class SceneMotion(
    val ambient: Float,
    val contribution: Float,
    val settlement: Float,
    val reducedMotion: Boolean,
    val quality: ThemeRenderQuality,
    val materials: SceneMaterials?
)

private interface VisualThemeRenderer {
    val id: String
    fun render(scope: DrawScope, state: ThemeFinancialState, fill: Float, motion: SceneMotion)
}

private object VesselThemeRenderer : VisualThemeRenderer {
    override val id = ThemeRegistry.VESSEL
    override fun render(scope: DrawScope, state: ThemeFinancialState, fill: Float, motion: SceneMotion) =
        with(scope) { drawVesselScene(state, fill, motion) }
}

private object WoodlandThemeRenderer : VisualThemeRenderer {
    override val id = ThemeRegistry.WOODLAND
    override fun render(scope: DrawScope, state: ThemeFinancialState, fill: Float, motion: SceneMotion) =
        with(scope) { drawWoodlandScene(state, fill, motion) }
}

private object MempoolThemeRenderer : VisualThemeRenderer {
    override val id = ThemeRegistry.MEMPOOL
    override fun render(scope: DrawScope, state: ThemeFinancialState, fill: Float, motion: SceneMotion) =
        with(scope) { drawMempoolScene(state, fill, motion) }
}

private val VisualThemeRenderers = listOf(
    VesselThemeRenderer,
    WoodlandThemeRenderer,
    MempoolThemeRenderer
).associateBy(VisualThemeRenderer::id)

@Composable
fun NomismaThemeScene(
    state: ThemeFinancialState,
    themeId: String,
    qualityPreference: ThemeQualityPreference = ThemeQualityPreference.AUTO,
    motionPreference: ThemeMotionPreference = ThemeMotionPreference.SYSTEM,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val quality = remember(context, qualityPreference) { resolveQuality(context, qualityPreference) }
    val reducedMotion = remember(motionPreference) {
        motionPreference == ThemeMotionPreference.REDUCED || !ValueAnimator.areAnimatorsEnabled()
    }
    val sceneMaterials = remember(quality) {
        if (quality == ThemeRenderQuality.LOW) null else SceneMaterialsFactory.create()
    }
    val animatedFill by animateFloatAsState(
        targetValue = state.normalizedFill.coerceIn(0f, 1f),
        animationSpec = tween(
            durationMillis = if (reducedMotion) 0 else 1_050,
            easing = CubicBezierEasing(0.18f, 0.82f, 0.22f, 1f)
        ),
        label = "scene-fill"
    )
    val contribution = remember { Animatable(0f) }
    val settlement = remember { Animatable(0f) }
    var previousState by remember(state.ledgerId) { mutableStateOf<ThemeFinancialState?>(state) }

    LaunchedEffect(state) {
        val events = ThemePresentationEventResolver.resolve(previousState, state)
        previousState = state
        if (!reducedMotion) {
            if (events.any { it is ThemePresentationEvent.ContributionAdded }) {
                launch {
                    contribution.snapTo(0f)
                    contribution.animateTo(
                        1f,
                        keyframes {
                            durationMillis = 1_650
                            0f at 0
                            0.12f at 190
                            0.67f at 720
                            0.88f at 1_080
                        }
                    )
                    contribution.snapTo(0f)
                }
            }
            if (events.any { it is ThemePresentationEvent.SettlementCompleted }) {
                launch {
                    settlement.snapTo(0f)
                    settlement.animateTo(
                        1f,
                        keyframes {
                            durationMillis = 2_250
                            0f at 0
                            0.18f at 360
                            0.52f at 820
                            0.82f at 1_480
                        }
                    )
                    settlement.snapTo(0f)
                }
            }
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "scene-ambient")
    val ambientPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(16_000, easing = LinearEasing)),
        label = "scene-ambient-phase"
    )
    val motion = SceneMotion(
        ambient = if (reducedMotion) 0.25f else ambientPhase,
        contribution = contribution.value,
        settlement = settlement.value,
        reducedMotion = reducedMotion,
        quality = quality,
        materials = sceneMaterials
    )
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
                    append((animatedFill * 100).roundToInt())
                    append(" per cento")
                    if (state.overflowRatio > 0f) append(", soglia superata")
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            (VisualThemeRenderers[themeId] ?: VesselThemeRenderer).render(this, state, animatedFill, motion)
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
                        "${(animatedFill * 100).roundToInt()}%"
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

private fun DrawScope.drawVesselScene(
    state: ThemeFinancialState,
    fill: Float,
    motion: SceneMotion
) {
    val participantAColor = state.participantA?.colorHex.sceneColor(Color(0xFF38E66B))
    val participantBColor = state.participantB?.colorHex.sceneColor(Color(0xFF147DFF))
    val contributionGlow = sin(motion.contribution.coerceIn(0f, 1f) * PI).toFloat().coerceAtLeast(0f)
    val settlementGlow = sin(motion.settlement.coerceIn(0f, 1f) * PI).toFloat().coerceAtLeast(0f)
    val impactWave = dampedWave(motion.contribution, start = 0.40f, oscillations = 4.5f, damping = 3.2f)

    drawRect(Brush.verticalGradient(listOf(Color(0xFF121B2A), Color(0xFF070A12), Color(0xFF020307))))
    drawStudioLightShafts(participantAColor, participantBColor, motion)
    drawCircle(
        Brush.radialGradient(listOf(participantAColor.copy(alpha = 0.30f), participantAColor.copy(alpha = 0.06f), Color.Transparent)),
        radius = size.minDimension * 0.76f,
        center = Offset(-size.width * 0.04f, size.height * 0.66f)
    )
    drawCircle(
        Brush.radialGradient(listOf(participantBColor.copy(alpha = 0.30f), participantBColor.copy(alpha = 0.06f), Color.Transparent)),
        radius = size.minDimension * 0.76f,
        center = Offset(size.width * 1.04f, size.height * 0.66f)
    )
    drawVesselDust(motion)

    val centerX = size.width * 0.47f
    val top = size.height * 0.145f
    val bottom = size.height * 0.87f
    val bodyLeft = size.width * 0.24f
    val bodyRight = size.width * 0.69f
    val vessel = vesselPath(centerX, top, bottom, bodyLeft, bodyRight)

    drawOval(
        Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.82f), Color.Transparent)),
        topLeft = Offset(size.width * 0.18f, size.height * 0.79f),
        size = Size(size.width * 0.66f, size.height * 0.17f)
    )

    drawPitcherHandle(bodyRight, top, bottom, participantBColor)

    clipPath(vessel) {
        drawRect(
            Brush.linearGradient(
                listOf(
                    participantAColor.copy(alpha = 0.10f),
                    Color(0xFFB9D8EA).copy(alpha = 0.035f),
                    participantBColor.copy(alpha = 0.11f)
                ),
                start = Offset(bodyLeft, top),
                end = Offset(bodyRight, bottom)
            ),
            topLeft = Offset(bodyLeft, top),
            size = Size(bodyRight - bodyLeft, bottom - top)
        )
        if (fill > 0f) {
            val liquidBottom = bottom - 5.dp.toPx()
            val liquidHeight = (bottom - top) * 0.84f * fill
            val liquidTop = liquidBottom - liquidHeight
            val total = state.participantAContributionMinor + state.participantBContributionMinor
            val ratioA = if (total <= 0L) 0.5f else state.participantAContributionMinor.toFloat() / total.toFloat()
            val liquidBrush = motion.materials?.liquid(
                width = size.width,
                height = size.height,
                time = motion.ambient,
                ratioA = ratioA,
                energy = contributionGlow,
                colorA = participantAColor,
                colorB = participantBColor
            ) ?: conservedLiquidBrush(participantAColor, participantBColor, ratioA, bodyLeft, bodyRight)
            drawRect(
                liquidBrush,
                Offset(bodyLeft - 8.dp.toPx(), liquidTop - 8.dp.toPx()),
                Size(bodyRight - bodyLeft + 16.dp.toPx(), liquidHeight + 18.dp.toPx())
            )
            drawLiquidVolumeDetail(
                liquidTop = liquidTop,
                liquidBottom = liquidBottom,
                left = bodyLeft,
                right = bodyRight,
                colorA = participantAColor,
                colorB = participantBColor,
                ratioA = ratioA,
                motion = motion
            )
            drawLiquidSurface(
                liquidTop = liquidTop,
                left = bodyLeft,
                right = bodyRight,
                impactWave = impactWave,
                ambient = motion.ambient,
                quality = motion.quality
            )
        }
    }

    drawPath(vessel, Color(0xFFBBD6E5).copy(alpha = 0.13f), style = Stroke(13.dp.toPx(), join = StrokeJoin.Round))
    drawPath(
        vessel,
        Brush.horizontalGradient(
            0f to Color.White.copy(alpha = 0.75f),
            0.14f to participantAColor.copy(alpha = 0.34f),
            0.46f to Color.White.copy(alpha = 0.055f),
            0.78f to participantBColor.copy(alpha = 0.34f),
            1f to Color.White.copy(alpha = 0.72f),
            startX = bodyLeft,
            endX = bodyRight
        ),
        style = Stroke(2.3.dp.toPx(), join = StrokeJoin.Round)
    )
    drawVesselHighlights(centerX, top, bottom, bodyLeft, bodyRight)
    drawPitcherRim(centerX, top, bodyRight - bodyLeft, participantAColor, participantBColor)

    if (motion.contribution > 0f) {
        drawLiquidContribution(state, motion.contribution, participantAColor, participantBColor, centerX, top)
    }
    if (state.overflowRatio > 0f && motion.settlement == 0f) {
        drawVesselOverflow(state, motion, participantAColor, participantBColor, centerX, top)
    }
    if (settlementGlow > 0f) {
        drawCircle(
            Brush.radialGradient(listOf(Color.White.copy(alpha = settlementGlow * 0.42f), Color.Transparent)),
            radius = size.minDimension * (0.18f + settlementGlow * 0.34f),
            center = Offset(centerX, size.height * 0.52f)
        )
    }
}

private fun DrawScope.drawStudioLightShafts(colorA: Color, colorB: Color, motion: SceneMotion) {
    val breathe = 0.86f + sin(motion.ambient * PI.toFloat() * 2f) * 0.14f
    val leftBeam = Path().apply {
        moveTo(0f, size.height)
        lineTo(size.width * 0.34f, 0f)
        lineTo(size.width * 0.48f, 0f)
        lineTo(size.width * 0.17f, size.height)
        close()
    }
    val rightBeam = Path().apply {
        moveTo(size.width, size.height)
        lineTo(size.width * 0.66f, 0f)
        lineTo(size.width * 0.52f, 0f)
        lineTo(size.width * 0.83f, size.height)
        close()
    }
    drawPath(leftBeam, Brush.verticalGradient(listOf(colorA.copy(alpha = 0.02f), colorA.copy(alpha = 0.11f * breathe))))
    drawPath(rightBeam, Brush.verticalGradient(listOf(colorB.copy(alpha = 0.02f), colorB.copy(alpha = 0.11f * breathe))))
}

private fun DrawScope.drawPitcherHandle(right: Float, top: Float, bottom: Float, accent: Color) {
    val height = bottom - top
    val handle = Path().apply {
        moveTo(right - 2.dp.toPx(), top + height * 0.28f)
        cubicTo(
            right + size.width * 0.22f,
            top + height * 0.22f,
            right + size.width * 0.23f,
            top + height * 0.67f,
            right - 1.dp.toPx(),
            top + height * 0.73f
        )
    }
    drawPath(handle, Color.Black.copy(alpha = 0.62f), style = Stroke(31.dp.toPx(), cap = StrokeCap.Round))
    drawPath(handle, Color(0xFFD9F0FA).copy(alpha = 0.20f), style = Stroke(24.dp.toPx(), cap = StrokeCap.Round))
    drawPath(handle, Color(0xFF09131A).copy(alpha = 0.78f), style = Stroke(15.dp.toPx(), cap = StrokeCap.Round))
    drawPath(handle, accent.copy(alpha = 0.20f), style = Stroke(10.dp.toPx(), cap = StrokeCap.Round))
    drawPath(handle, Color.White.copy(alpha = 0.56f), style = Stroke(1.7.dp.toPx(), cap = StrokeCap.Round))
}

private fun DrawScope.drawPitcherRim(
    centerX: Float,
    top: Float,
    bodyWidth: Float,
    colorA: Color,
    colorB: Color
) {
    val lipLeft = centerX - bodyWidth * 0.21f
    val lipRight = centerX + bodyWidth * 0.27f
    val spoutTip = centerX + bodyWidth * 0.55f
    drawOval(
        Brush.verticalGradient(listOf(Color(0xFF02070B).copy(alpha = 0.88f), Color(0xFF6F9AAE).copy(alpha = 0.18f))),
        Offset(lipLeft, top - 4.dp.toPx()),
        Size(lipRight - lipLeft, 11.dp.toPx())
    )
    val lip = Path().apply {
        moveTo(lipLeft, top + 1.dp.toPx())
        cubicTo(
            centerX - bodyWidth * 0.08f, top - 6.dp.toPx(),
            centerX + bodyWidth * 0.15f, top - 5.dp.toPx(),
            lipRight, top
        )
        quadraticTo(centerX + bodyWidth * 0.42f, top - 2.dp.toPx(), spoutTip, top - 13.dp.toPx())
        quadraticTo(centerX + bodyWidth * 0.46f, top + 5.dp.toPx(), lipRight, top + 7.dp.toPx())
    }
    drawPath(lip, Color(0xFFBBD6E5).copy(alpha = 0.20f), style = Stroke(7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawPath(
        lip,
        Brush.horizontalGradient(
            listOf(Color.White.copy(alpha = 0.76f), colorA.copy(alpha = 0.36f), Color.White.copy(alpha = 0.18f), colorB.copy(alpha = 0.40f), Color.White.copy(alpha = 0.72f)),
            lipLeft,
            spoutTip
        ),
        style = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
    drawArc(
        Color.White.copy(alpha = 0.34f),
        188f,
        158f,
        false,
        Offset(lipLeft + 3.dp.toPx(), top - 2.dp.toPx()),
        Size(lipRight - lipLeft - 6.dp.toPx(), 8.dp.toPx()),
        style = Stroke(1.dp.toPx(), cap = StrokeCap.Round)
    )
}

private fun DrawScope.drawVesselDust(motion: SceneMotion) {
    val count = when (motion.quality) {
        ThemeRenderQuality.LOW -> 8
        ThemeRenderQuality.MEDIUM -> 18
        ThemeRenderQuality.HIGH -> 30
    }
    repeat(count) { index ->
        val x = size.width * ((pseudo(index, 0.19f) + motion.ambient * (0.012f + index % 3 * 0.004f)) % 1f)
        val y = size.height * pseudo(index, 0.81f)
        drawCircle(Color.White.copy(alpha = 0.06f + pseudo(index, 0.44f) * 0.12f), (0.6f + pseudo(index, 0.55f) * 1.3f).dp.toPx(), Offset(x, y))
    }
}

private fun vesselPath(centerX: Float, top: Float, bottom: Float, left: Float, right: Float): Path {
    val width = right - left
    val neckHalf = width * 0.20f
    val height = bottom - top
    return Path().apply {
        moveTo(centerX - neckHalf, top + height * 0.025f)
        lineTo(centerX - neckHalf * 0.96f, top + height * 0.13f)
        cubicTo(left + width * 0.12f, top + height * 0.19f, left, top + height * 0.34f, left, top + height * 0.53f)
        lineTo(left, bottom - height * 0.12f)
        cubicTo(left, bottom - height * 0.035f, left + width * 0.13f, bottom, centerX - width * 0.02f, bottom)
        cubicTo(right - width * 0.13f, bottom, right, bottom - height * 0.035f, right, bottom - height * 0.13f)
        lineTo(right, top + height * 0.46f)
        cubicTo(right, top + height * 0.30f, right - width * 0.07f, top + height * 0.20f, centerX + neckHalf, top + height * 0.14f)
        lineTo(centerX + neckHalf * 1.04f, top + height * 0.07f)
        quadraticTo(centerX + neckHalf * 2.08f, top + height * 0.055f, centerX + neckHalf * 2.74f, top - height * 0.018f)
        quadraticTo(centerX + neckHalf * 2.22f, top + height * 0.10f, centerX + neckHalf * 0.92f, top + height * 0.105f)
        lineTo(centerX - neckHalf, top + height * 0.06f)
        close()
    }
}

private fun conservedLiquidBrush(
    colorA: Color,
    colorB: Color,
    ratioA: Float,
    startX: Float,
    endX: Float
): Brush {
    if (ratioA <= 0.001f) return Brush.horizontalGradient(listOf(colorB, colorB.copy(alpha = 0.78f)), startX, endX)
    if (ratioA >= 0.999f) return Brush.horizontalGradient(listOf(colorA.copy(alpha = 0.78f), colorA), startX, endX)
    val boundary = ratioA.coerceIn(0.05f, 0.95f)
    val transition = 0.11f.coerceAtMost(boundary).coerceAtMost(1f - boundary)
    return Brush.horizontalGradient(
        0f to colorA.copy(alpha = 0.92f),
        (boundary - transition) to colorA.copy(alpha = 0.9f),
        boundary to lerp(colorA, colorB, 0.5f).copy(alpha = 0.9f),
        (boundary + transition) to colorB.copy(alpha = 0.9f),
        1f to colorB.copy(alpha = 0.92f),
        startX = startX,
        endX = endX
    )
}

private fun dampedWave(progress: Float, start: Float, oscillations: Float, damping: Float): Float {
    if (progress <= start) return 0f
    val time = ((progress - start) / (1f - start)).coerceIn(0f, 1f)
    return sin(time * PI.toFloat() * 2f * oscillations) * exp(-damping * time)
}

private fun DrawScope.drawLiquidSurface(
    liquidTop: Float,
    left: Float,
    right: Float,
    impactWave: Float,
    ambient: Float,
    quality: ThemeRenderQuality
) {
    val idleAmplitude = when (quality) {
        ThemeRenderQuality.LOW -> 0.8.dp.toPx()
        ThemeRenderQuality.MEDIUM -> 1.5.dp.toPx()
        ThemeRenderQuality.HIGH -> 2.1.dp.toPx()
    }
    val surface = Path()
    val backSurface = Path()
    val segments = if (quality == ThemeRenderQuality.HIGH) 42 else 26
    for (index in 0..segments) {
        val t = index / segments.toFloat()
        val x = left + (right - left) * t
        val edgeDamping = sin(t * PI.toFloat()).coerceAtLeast(0f)
        val idle = sin(t * PI.toFloat() * 3.4f + ambient * PI.toFloat() * 2f) * idleAmplitude
        val impact = sin(t * PI.toFloat() * 5f - impactWave * 2f) * impactWave * 11.dp.toPx() * edgeDamping
        val y = liquidTop + idle + impact
        if (index == 0) {
            surface.moveTo(x, y)
            backSurface.moveTo(x, y + 2.5.dp.toPx())
        } else {
            surface.lineTo(x, y)
            backSurface.lineTo(x, y + 2.5.dp.toPx())
        }
    }
    drawPath(backSurface, Color.Black.copy(alpha = 0.28f), style = Stroke(5.dp.toPx(), cap = StrokeCap.Round))
    drawPath(surface, Color.White.copy(alpha = 0.52f), style = Stroke(1.25.dp.toPx(), cap = StrokeCap.Round))
    drawPath(surface, Color(0xFFBDEEFF).copy(alpha = 0.16f), style = Stroke(5.dp.toPx(), cap = StrokeCap.Round))
}

private fun DrawScope.drawLiquidVolumeDetail(
    liquidTop: Float,
    liquidBottom: Float,
    left: Float,
    right: Float,
    colorA: Color,
    colorB: Color,
    ratioA: Float,
    motion: SceneMotion
) {
    val liquidHeight = (liquidBottom - liquidTop).coerceAtLeast(1f)
    val causticCount = when (motion.quality) {
        ThemeRenderQuality.LOW -> 2
        ThemeRenderQuality.MEDIUM -> 5
        ThemeRenderQuality.HIGH -> 9
    }
    repeat(causticCount) { index ->
        val baseY = liquidTop + liquidHeight * (0.16f + pseudo(index, 0.63f) * 0.72f)
        val phase = motion.ambient * PI.toFloat() * 2f + index * 0.83f
        val caustic = Path().apply {
            val start = left + (right - left) * pseudo(index, 0.21f) * 0.65f
            moveTo(start, baseY)
            cubicTo(
                start + 14.dp.toPx(), baseY + sin(phase) * 6.dp.toPx(),
                start + 30.dp.toPx(), baseY - cos(phase) * 7.dp.toPx(),
                start + 48.dp.toPx(), baseY + sin(phase * 1.3f) * 4.dp.toPx()
            )
        }
        drawPath(caustic, Color.White.copy(alpha = 0.045f + pseudo(index, 0.91f) * 0.08f), style = Stroke((0.7f + index % 3 * 0.45f).dp.toPx(), cap = StrokeCap.Round))
    }

    if (motion.quality != ThemeRenderQuality.LOW) {
        val bubbleCount = if (motion.quality == ThemeRenderQuality.HIGH) 22 else 10
        repeat(bubbleCount) { index ->
            val speed = 0.08f + pseudo(index, 0.48f) * 0.12f
            val rise = (pseudo(index, 0.74f) + motion.ambient * speed) % 1f
            val x = left + (right - left) * (0.08f + pseudo(index, 0.13f) * 0.84f)
            val y = liquidBottom - rise * liquidHeight
            val radius = (0.65f + pseudo(index, 0.35f) * 1.7f).dp.toPx()
            drawCircle(Color.White.copy(alpha = 0.08f), radius * 1.7f, Offset(x, y))
            drawCircle(Color.White.copy(alpha = 0.28f), radius, Offset(x, y), style = Stroke(0.65.dp.toPx()))
            drawCircle(Color.White.copy(alpha = 0.36f), radius * 0.24f, Offset(x - radius * 0.28f, y - radius * 0.3f))
        }
    }

    val boundaryX = left + (right - left) * ratioA.coerceIn(0.08f, 0.92f)
    drawOval(
        Brush.radialGradient(listOf(lerp(colorA, colorB, 0.5f).copy(alpha = 0.16f), Color.Transparent)),
        Offset(boundaryX - 34.dp.toPx(), liquidTop + liquidHeight * 0.25f),
        Size(68.dp.toPx(), liquidHeight * 0.56f)
    )
    drawRect(
        Brush.horizontalGradient(
            listOf(Color.White.copy(alpha = 0.14f), Color.Transparent, Color.Transparent, Color.White.copy(alpha = 0.10f)),
            left,
            right
        ),
        Offset(left, liquidTop),
        Size(right - left, liquidHeight)
    )
}

private fun DrawScope.drawVesselHighlights(
    centerX: Float,
    top: Float,
    bottom: Float,
    left: Float,
    right: Float
) {
    val leftHighlight = Path().apply {
        moveTo(centerX - (right - left) * 0.18f, top + 14.dp.toPx())
        cubicTo(left + 18.dp.toPx(), top + 72.dp.toPx(), left + 18.dp.toPx(), bottom - 72.dp.toPx(), left + 42.dp.toPx(), bottom - 28.dp.toPx())
    }
    drawPath(leftHighlight, Color.White.copy(alpha = 0.09f), style = Stroke(13.dp.toPx(), cap = StrokeCap.Round))
    drawPath(leftHighlight, Color.White.copy(alpha = 0.44f), style = Stroke(2.4.dp.toPx(), cap = StrokeCap.Round))
    val rightHighlight = Path().apply {
        moveTo(centerX + (right - left) * 0.20f, top + 20.dp.toPx())
        cubicTo(right - 13.dp.toPx(), top + 74.dp.toPx(), right - 14.dp.toPx(), bottom - 82.dp.toPx(), right - 34.dp.toPx(), bottom - 42.dp.toPx())
    }
    drawPath(rightHighlight, Color.White.copy(alpha = 0.2f), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
    val prism = listOf(Color(0xFF68E7FF), Color(0xFF9C7BFF), Color(0xFFFF6BCB))
    prism.forEachIndexed { index, color ->
        val prismPath = Path().apply {
            moveTo(left + 25.dp.toPx() + index * 1.4.dp.toPx(), top + 82.dp.toPx())
            cubicTo(
                left + 8.dp.toPx(), top + 128.dp.toPx(),
                left + 18.dp.toPx(), bottom - 94.dp.toPx(),
                left + 34.dp.toPx() + index * 1.2.dp.toPx(), bottom - 62.dp.toPx()
            )
        }
        drawPath(prismPath, color.copy(alpha = 0.11f), style = Stroke(1.15.dp.toPx(), cap = StrokeCap.Round))
    }
    repeat(10) { index ->
        val x = left + (right - left) * (0.08f + pseudo(index, 0.41f) * 0.84f)
        val y = top + (bottom - top) * (0.18f + pseudo(index, 0.87f) * 0.68f)
        val radius = (0.55f + pseudo(index, 0.12f) * 1.2f).dp.toPx()
        drawCircle(Color.White.copy(alpha = 0.18f), radius, Offset(x, y), style = Stroke(0.65.dp.toPx()))
        drawCircle(Color.White.copy(alpha = 0.32f), radius * 0.22f, Offset(x - radius * 0.25f, y - radius * 0.28f))
    }
}

private fun DrawScope.drawLiquidContribution(
    state: ThemeFinancialState,
    progress: Float,
    colorA: Color,
    colorB: Color,
    centerX: Float,
    vesselTop: Float
) {
    val expense = state.latestContribution ?: return
    val fromLeft = expense.authorId == state.participantA?.id
    val color = if (fromLeft) colorA else colorB
    val direction = if (fromLeft) 1f else -1f
    val origin = Offset(if (fromLeft) size.width * 0.055f else size.width * 0.945f, size.height * 0.34f)
    val impact = Offset(centerX + direction * 7.dp.toPx(), vesselTop + 7.dp.toPx())
    val stream = Path().apply {
        moveTo(origin.x, origin.y)
        cubicTo(
            if (fromLeft) size.width * 0.22f else size.width * 0.78f,
            size.height * 0.20f,
            centerX - direction * size.width * 0.12f,
            size.height * 0.19f,
            impact.x,
            impact.y
        )
    }
    val measure = PathMeasure().apply { setPath(stream, false) }
    val head = smoothStep(0.02f, 0.54f, progress)
    val tail = smoothStep(0.58f, 0.88f, progress)
    val startDistance = measure.length * tail * 0.96f
    val endDistance = measure.length * head
    val streamFade = (1f - smoothStep(0.79f, 1f, progress)).coerceIn(0f, 1f)
    if (endDistance > startDistance && streamFade > 0f) {
        val segmentCount = 30
        var previous = measure.getPosition(startDistance)
        repeat(segmentCount) { index ->
            val normalized = (index + 1) / segmentCount.toFloat()
            val distance = startDistance + (endDistance - startDistance) * normalized
            val current = measure.getPosition(distance)
            val body = sin(normalized * PI.toFloat()).coerceAtLeast(0f)
            val breakup = 0.86f + sin(index * 1.71f + progress * 19f) * 0.14f
            val width = (2.0f + body * 4.5f) * breakup * streamFade
            drawLine(color.copy(alpha = 0.14f * streamFade), previous, current, (width + 6f).dp.toPx(), StrokeCap.Round)
            drawLine(color.copy(alpha = 0.90f * streamFade), previous, current, width.dp.toPx(), StrokeCap.Round)
            if (index % 3 == 0) {
                val highlightEnd = Offset(
                    previous.x + (current.x - previous.x) * 0.82f,
                    previous.y + (current.y - previous.y) * 0.82f
                )
                drawLine(Color.White.copy(alpha = 0.28f * streamFade), previous, highlightEnd, (width * 0.23f).dp.toPx(), StrokeCap.Round)
            }
            previous = current
        }
    }

    if (head > 0.18f && head < 0.98f) {
        repeat(6) { index ->
            val lag = (head - 0.055f - index * 0.052f).coerceIn(0f, 1f)
            val position = measure.getPosition(measure.length * lag)
            val wobble = sin(progress * 31f + index * 2.3f) * (1.2f + index * 0.25f).dp.toPx()
            drawWaterDroplet(
                center = position + Offset(-direction * wobble, wobble * 0.45f),
                velocity = Offset(direction * 1.2f, 2.7f),
                color = color,
                radius = (2.8f - index * 0.25f).dp.toPx(),
                alpha = streamFade * (0.82f - index * 0.07f)
            )
        }
    }

    if (progress > 0.46f) {
        val splashTime = ((progress - 0.46f) / 0.54f).coerceIn(0f, 1f)
        val splashFade = (1f - splashTime).coerceIn(0f, 1f)
        repeat(13) { index ->
            val side = if (index % 2 == 0) -1f else 1f
            val speedX = side * (14f + pseudo(index, 0.31f) * 28f).dp.toPx()
            val speedY = (24f + pseudo(index, 0.82f) * 42f).dp.toPx()
            val t = (splashTime * (0.82f + pseudo(index, 0.57f) * 0.34f)).coerceIn(0f, 1f)
            val gravity = 88.dp.toPx()
            val center = impact + Offset(speedX * t, -speedY * t + 0.5f * gravity * t * t)
            val velocity = Offset(speedX, -speedY + gravity * t)
            drawWaterDroplet(center, velocity, color, (1.35f + pseudo(index, 0.11f) * 2.1f).dp.toPx(), splashFade)
        }
        repeat(3) { ring ->
            val ringProgress = (splashTime * 1.35f - ring * 0.18f).coerceIn(0f, 1f)
            if (ringProgress > 0f) {
                drawOval(
                    color.copy(alpha = (1f - ringProgress) * 0.48f),
                    impact - Offset((12 + ring * 4).dp.toPx() * ringProgress, 2.2.dp.toPx() * ringProgress),
                    Size((24 + ring * 8).dp.toPx() * ringProgress, 4.4.dp.toPx() * ringProgress),
                    style = Stroke((1.4f - ring * 0.2f).dp.toPx())
                )
            }
        }
    }
    if (progress > 0.66f) {
        val slide = ((progress - 0.66f) / 0.34f).coerceIn(0f, 1f)
        val wallX = centerX + direction * size.width * 0.145f
        val wallY = vesselTop + 42.dp.toPx() + slide * 52.dp.toPx()
        drawOval(
            Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.36f), color.copy(alpha = 0.72f)), wallY - 6.dp.toPx(), wallY + 8.dp.toPx()),
            Offset(wallX - 3.2.dp.toPx(), wallY - 6.dp.toPx()),
            Size(6.4.dp.toPx(), 14.dp.toPx() + slide * 5.dp.toPx())
        )
    }
}

private fun smoothStep(edge0: Float, edge1: Float, value: Float): Float {
    val x = ((value - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

private fun DrawScope.drawWaterDroplet(
    center: Offset,
    velocity: Offset,
    color: Color,
    radius: Float,
    alpha: Float
) {
    if (alpha <= 0f || radius <= 0f) return
    val speed = sqrt(velocity.x * velocity.x + velocity.y * velocity.y).coerceAtLeast(0.001f)
    val stretch = (1.15f + speed / 95f).coerceIn(1.15f, 2.15f)
    val angle = atan2(velocity.y, velocity.x) * 180f / PI.toFloat() + 90f
    rotate(angle, center) {
        drawOval(
            Brush.verticalGradient(
                listOf(Color.White.copy(alpha = 0.42f * alpha), color.copy(alpha = 0.95f * alpha), color.copy(alpha = 0.68f * alpha)),
                center.y - radius * stretch,
                center.y + radius * stretch
            ),
            center - Offset(radius, radius * stretch),
            Size(radius * 2f, radius * 2f * stretch)
        )
        drawOval(
            Color.White.copy(alpha = 0.38f * alpha),
            center - Offset(radius * 0.36f, radius * stretch * 0.68f),
            Size(radius * 0.34f, radius * stretch * 0.72f)
        )
    }
}

private fun DrawScope.drawVesselOverflow(
    state: ThemeFinancialState,
    motion: SceneMotion,
    colorA: Color,
    colorB: Color,
    centerX: Float,
    top: Float
) {
    val total = state.participantAContributionMinor + state.participantBContributionMinor
    val mixRatio = if (total == 0L) 0.5f else state.participantBContributionMinor.toFloat() / total.toFloat()
    val overflowColor = lerp(colorA, colorB, mixRatio)
    val pulse = 0.5f + sin(motion.ambient * PI.toFloat() * 2f) * 0.5f
    drawOval(
        Brush.horizontalGradient(listOf(colorA, overflowColor, colorB)),
        Offset(centerX - size.width * 0.11f, top - 2.dp.toPx()),
        Size(size.width * 0.22f, 8.dp.toPx())
    )
    repeat(3) { index ->
        val x = centerX + (index - 1) * 16.dp.toPx()
        val length = (12 + index * 8).dp.toPx() * (0.65f + pulse * 0.35f)
        drawLine(overflowColor.copy(alpha = 0.74f), Offset(x, top + 1.dp.toPx()), Offset(x + (index - 1) * 3.dp.toPx(), top + length), 3.dp.toPx(), StrokeCap.Round)
        drawCircle(overflowColor.copy(alpha = 0.82f), 3.dp.toPx(), Offset(x + (index - 1) * 3.dp.toPx(), top + length + 2.dp.toPx()))
    }
}

private fun DrawScope.drawWoodlandScene(
    state: ThemeFinancialState,
    fill: Float,
    motion: SceneMotion
) {
    val colorA = state.participantA?.colorHex.sceneColor(Color(0xFF77C96B))
    val colorB = state.participantB?.colorHex.sceneColor(Color(0xFF6BA9E8))
    val contributionReaction = sin(motion.contribution.coerceIn(0f, 1f) * PI).toFloat().coerceAtLeast(0f)
    val settlementFlash = sin(motion.settlement.coerceIn(0f, 1f) * PI).toFloat().coerceAtLeast(0f)

    val backdrop = motion.materials?.woodland(size.width, size.height, motion.ambient, colorA, colorB)
        ?: Brush.verticalGradient(listOf(Color(0xFF1A3022), Color(0xFF0B1710), Color(0xFF050906)))
    drawRect(backdrop)
    val sunCenter = Offset(size.width * 0.5f, size.height * 0.06f)
    drawCircle(Brush.radialGradient(listOf(Color(0xFFFFE0A1).copy(alpha = 0.30f), Color(0xFFFFB85C).copy(alpha = 0.07f), Color.Transparent)), size.width * 0.46f, sunCenter)
    drawForestLayers(motion)

    val groundTop = size.height * 0.72f
    drawOval(Color(0xFF090C08), Offset(-size.width * 0.08f, groundTop + 6.dp.toPx()), Size(size.width * 1.16f, size.height * 0.32f))
    drawOval(
        Brush.verticalGradient(listOf(Color(0xFF3E633C), Color(0xFF1A2A18), Color(0xFF0A100A))),
        Offset(-size.width * 0.04f, groundTop - 8.dp.toPx()),
        Size(size.width * 1.08f, size.height * 0.25f)
    )
    drawMossAndForestFloor(groundTop, motion)

    val chestLeft = size.width * 0.32f
    val chestTop = size.height * 0.47f
    val chestWidth = size.width * 0.36f
    val chestHeight = size.height * 0.27f
    drawWoodenContainer(state, fill, motion, chestLeft, chestTop, chestWidth, chestHeight)

    drawWoodlandCharacter(
        center = Offset(size.width * 0.16f, size.height * 0.61f - contributionReaction * 5.dp.toPx()),
        identityId = state.participantA?.identityId ?: "bear",
        participantColor = colorA,
        facingRight = true,
        reaction = if (state.latestContribution?.authorId == state.participantA?.id) contributionReaction else 0f,
        motion = motion
    )
    drawWoodlandCharacter(
        center = Offset(size.width * 0.84f, size.height * 0.61f - contributionReaction * 5.dp.toPx()),
        identityId = state.participantB?.identityId ?: "squirrel",
        participantColor = colorB,
        facingRight = false,
        reaction = if (state.latestContribution?.authorId == state.participantB?.id) contributionReaction else 0f,
        motion = motion
    )

    if (motion.contribution > 0f) {
        drawWoodlandContribution(state, motion.contribution, chestLeft, chestTop, chestWidth)
    }
    if (settlementFlash > 0f) {
        repeat(if (motion.quality == ThemeRenderQuality.HIGH) 18 else 10) { index ->
            val angle = pseudo(index, 0.26f) * PI.toFloat() * 2f
            val radius = settlementFlash * (22 + index * 2).dp.toPx()
            val center = Offset(chestLeft + chestWidth / 2f, chestTop + chestHeight * 0.4f)
            drawCircle(
                Color(0xFFFFD98A).copy(alpha = (1f - settlementFlash * 0.55f) * 0.62f),
                (1.2f + index % 3).dp.toPx(),
                center + Offset(cos(angle) * radius, sin(angle) * radius)
            )
        }
    }
}

private fun DrawScope.drawForestLayers(motion: SceneMotion) {
    repeat(10) { index ->
        val x = size.width * (index / 9f) + (pseudo(index, 0.18f) - 0.5f) * 16.dp.toPx()
        val sway = sin((motion.ambient * 2f + index * 0.27f) * PI.toFloat()) * 1.7.dp.toPx()
        val depth = pseudo(index, 0.73f)
        val trunkWidth = (8f + depth * 10f).dp.toPx()
        drawRoundRect(
            Brush.horizontalGradient(
                listOf(Color(0xFF111910), Color(0xFF35412A), Color(0xFF151D13)),
                x - trunkWidth / 2f,
                x + trunkWidth / 2f
            ),
            Offset(x - trunkWidth / 2f + sway, size.height * (0.07f + depth * 0.08f)),
            Size(trunkWidth, size.height * (0.67f - depth * 0.04f)),
            CornerRadius(trunkWidth * 0.34f)
        )
        repeat(3) { bark ->
            val barkY = size.height * (0.18f + bark * 0.16f + depth * 0.03f)
            drawLine(
                Color(0xFF8B7650).copy(alpha = 0.08f + depth * 0.06f),
                Offset(x - trunkWidth * 0.28f + sway, barkY),
                Offset(x + trunkWidth * 0.19f + sway, barkY + 7.dp.toPx()),
                0.8.dp.toPx(),
                StrokeCap.Round
            )
        }
        val branchY = size.height * (0.25f + depth * 0.06f)
        drawLine(
            Color(0xFF303926).copy(alpha = 0.78f),
            Offset(x + sway, branchY),
            Offset(x + (if (index % 2 == 0) -1 else 1) * (24 + depth * 18).dp.toPx(), branchY - 24.dp.toPx()),
            (2.2f + depth * 2f).dp.toPx(),
            StrokeCap.Round
        )

        repeat(5) { crown ->
            val side = (crown - 2) * (11 + depth * 7).dp.toPx()
            val crownCenter = Offset(x + side + sway, size.height * (0.10f + (crown % 2) * 0.055f) + depth * 18.dp.toPx())
            val crownRadius = (24f + depth * 19f + (crown % 3) * 3f).dp.toPx()
            drawCircle(
                Brush.radialGradient(
                    listOf(Color(0xFF4F7047).copy(alpha = 0.34f), Color(0xFF1A2A1B).copy(alpha = 0.48f), Color.Transparent)
                ),
                crownRadius,
                crownCenter
            )
        }
    }
    drawOval(
        Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.045f), Color.Transparent)),
        Offset(-size.width * 0.12f, size.height * 0.37f),
        Size(size.width * 1.24f, size.height * 0.23f)
    )
    repeat(if (motion.quality == ThemeRenderQuality.HIGH) 34 else 18) { index ->
        val x = size.width * ((pseudo(index, 0.14f) + motion.ambient * (0.018f + index % 3 * 0.004f)) % 1f)
        val y = size.height * (0.16f + pseudo(index, 0.68f) * 0.56f)
        val warm = index % 5 == 0
        val pulse = 0.68f + sin(motion.ambient * PI.toFloat() * 2f + index) * 0.32f
        drawCircle(
            (if (warm) Color(0xFFFFD58A) else Color(0xFFB8D79E)).copy(alpha = (if (warm) 0.32f else 0.13f) * pulse),
            (if (warm) 1.75f else 1.05f).dp.toPx(),
            Offset(x, y)
        )
        if (warm) {
            drawCircle(
                Brush.radialGradient(listOf(Color(0xFFFFD58A).copy(alpha = 0.13f * pulse), Color.Transparent)),
                8.dp.toPx(),
                Offset(x, y)
            )
        }
    }
}

private fun DrawScope.drawMossAndForestFloor(groundTop: Float, motion: SceneMotion) {
    val bladeCount = when (motion.quality) {
        ThemeRenderQuality.LOW -> 18
        ThemeRenderQuality.MEDIUM -> 34
        ThemeRenderQuality.HIGH -> 52
    }
    repeat(bladeCount) { index ->
        val x = size.width * pseudo(index, 0.24f)
        val baseY = groundTop + (pseudo(index, 0.72f) - 0.25f) * 30.dp.toPx()
        val length = (3f + pseudo(index, 0.49f) * 8f).dp.toPx()
        val sway = sin(motion.ambient * PI.toFloat() * 2f + index * 0.71f) * 2.dp.toPx()
        drawLine(
            Color(0xFF82A968).copy(alpha = 0.16f + pseudo(index, 0.18f) * 0.24f),
            Offset(x, baseY),
            Offset(x + sway, baseY - length),
            (0.6f + pseudo(index, 0.91f) * 0.7f).dp.toPx(),
            StrokeCap.Round
        )
    }
    repeat(if (motion.quality == ThemeRenderQuality.HIGH) 15 else 8) { index ->
        val center = Offset(
            size.width * pseudo(index, 0.39f),
            groundTop + 8.dp.toPx() + pseudo(index, 0.83f) * 34.dp.toPx()
        )
        val stoneWidth = (5f + pseudo(index, 0.61f) * 13f).dp.toPx()
        drawOval(
            Brush.linearGradient(listOf(Color(0xFF61705A), Color(0xFF20291F))),
            center - Offset(stoneWidth / 2f, stoneWidth * 0.24f),
            Size(stoneWidth, stoneWidth * 0.48f)
        )
        drawOval(Color.White.copy(alpha = 0.07f), center - Offset(stoneWidth * 0.24f, stoneWidth * 0.18f), Size(stoneWidth * 0.32f, stoneWidth * 0.10f))
    }
}

private fun DrawScope.drawWoodenContainer(
    state: ThemeFinancialState,
    fill: Float,
    motion: SceneMotion,
    left: Float,
    top: Float,
    width: Float,
    height: Float
) {
    val settlementLift = sin(motion.settlement.coerceIn(0f, 1f) * PI).toFloat().coerceAtLeast(0f)
    drawOval(
        Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.72f), Color.Transparent)),
        Offset(left - width * 0.16f, top + height * 0.82f),
        Size(width * 1.32f, height * 0.36f)
    )
    val container = Path().apply {
        moveTo(left + width * 0.08f, top)
        quadraticTo(left, top + height * 0.42f, left + width * 0.10f, top + height)
        quadraticTo(left + width * 0.50f, top + height * 1.06f, left + width * 0.90f, top + height)
        quadraticTo(left + width, top + height * 0.42f, left + width * 0.92f, top)
        close()
    }
    drawPath(
        container,
        Brush.horizontalGradient(
            listOf(Color(0xFF402315), Color(0xFFA96B37), Color(0xFFC4884A), Color(0xFF70401F), Color(0xFF301B12)),
            left,
            left + width
        )
    )
    drawPath(container, Color(0xFFFFD79B).copy(alpha = 0.23f), style = Stroke(2.dp.toPx(), join = StrokeJoin.Round))

    repeat(8) { index ->
        val grainY = top + height * (0.10f + index * 0.115f)
        val wave = Path().apply {
            moveTo(left + width * 0.09f, grainY)
            cubicTo(
                left + width * 0.31f, grainY + sin(index * 1.3f) * 4.dp.toPx(),
                left + width * 0.67f, grainY - cos(index * 0.9f) * 4.dp.toPx(),
                left + width * 0.91f, grainY + sin(index * 0.7f) * 2.dp.toPx()
            )
        }
        drawPath(wave, Color(0xFF2E170E).copy(alpha = 0.24f), style = Stroke((0.7f + index % 3 * 0.25f).dp.toPx(), cap = StrokeCap.Round))
    }

    val window = Path().apply {
        moveTo(left + width * 0.20f, top + height * 0.30f)
        quadraticTo(left + width * 0.50f, top + height * 0.24f, left + width * 0.80f, top + height * 0.30f)
        lineTo(left + width * 0.75f, top + height * 0.78f)
        quadraticTo(left + width * 0.50f, top + height * 0.84f, left + width * 0.25f, top + height * 0.78f)
        close()
    }
    drawPath(window, Brush.verticalGradient(listOf(Color(0xFF080B08), Color(0xFF1B2116))))

    val maxObjects = when (motion.quality) {
        ThemeRenderQuality.LOW -> 10
        ThemeRenderQuality.MEDIUM -> 18
        ThemeRenderQuality.HIGH -> 28
    }
    val visible = (maxObjects * fill).roundToInt().coerceIn(0, maxObjects)
    val total = state.participantAContributionMinor + state.participantBContributionMinor
    val ratioA = if (total == 0L) 0.5f else state.participantAContributionMinor.toFloat() / total.toFloat()
    clipPath(window) {
        drawRect(
            Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.035f), Color.Transparent)),
            Offset(left + width * 0.18f, top + height * 0.24f),
            Size(width * 0.64f, height * 0.62f)
        )
        repeat(visible) { index ->
            val ownerA = index.toFloat() / maxObjects < ratioA
            val identity = if (ownerA) state.participantA?.identityId.orEmpty() else state.participantB?.identityId.orEmpty()
            val x = left + width * 0.26f + pseudo(index, 0.32f) * width * 0.48f
            val row = index / 7
            val y = top + height * 0.76f - row * 11.dp.toPx() - pseudo(index, 0.88f) * 4.dp.toPx()
            drawWoodlandMaterial(identity, Offset(x, y), 0.72f)
        }
    }
    drawPath(window, Color(0xFFD8B47B).copy(alpha = 0.46f), style = Stroke(2.2.dp.toPx(), join = StrokeJoin.Round))
    drawPath(window, Color.White.copy(alpha = 0.08f), style = Stroke(6.dp.toPx(), join = StrokeJoin.Round))

    repeat(2) { band ->
        val bandY = top + height * (0.18f + band * 0.70f)
        drawLine(
            Brush.horizontalGradient(listOf(Color(0xFF33261D), Color(0xFFC2935C), Color(0xFF463124)), left, left + width),
            Offset(left + width * 0.06f, bandY),
            Offset(left + width * 0.94f, bandY),
            4.5.dp.toPx(),
            StrokeCap.Round
        )
    }

    val slotWidth = width * 0.42f
    rotate(-settlementLift * 13f, pivot = Offset(left + width / 2f, top)) {
        drawRoundRect(
            Brush.verticalGradient(listOf(Color(0xFFE1AB67), Color(0xFF8A5228), Color(0xFF4B2A18))),
            Offset(left - 4.dp.toPx(), top - 15.dp.toPx() - settlementLift * 11.dp.toPx()),
            Size(width + 8.dp.toPx(), 27.dp.toPx()),
            CornerRadius(13.dp.toPx())
        )
        drawRoundRect(
            Color(0xFF2A190F),
            Offset(left + (width - slotWidth) / 2f, top - 7.dp.toPx() - settlementLift * 11.dp.toPx()),
            Size(slotWidth, 5.5.dp.toPx()),
            CornerRadius(4.dp.toPx())
        )
        drawLine(
            Color.White.copy(alpha = 0.18f),
            Offset(left + 10.dp.toPx(), top - 10.dp.toPx() - settlementLift * 11.dp.toPx()),
            Offset(left + width - 10.dp.toPx(), top - 10.dp.toPx() - settlementLift * 11.dp.toPx()),
            1.dp.toPx(),
            StrokeCap.Round
        )
    }
}

private fun DrawScope.drawWoodlandCharacter(
    center: Offset,
    identityId: String,
    participantColor: Color,
    facingRight: Boolean,
    reaction: Float,
    motion: SceneMotion
) {
    val direction = if (facingRight) 1f else -1f
    val palette = woodlandPalette(identityId)
    val scale = size.minDimension / 390f
    val breathing = if (motion.reducedMotion) 0f else {
        sin(motion.ambient * PI.toFloat() * 2f + center.x * 0.013f) * 0.75f * scale
    }
    val anchor = center + Offset(0f, breathing - reaction * 2.4f * scale)
    drawOval(
        Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.56f), Color.Transparent)),
        anchor + Offset(-31f * scale, 26f * scale),
        Size(62f * scale, 11f * scale)
    )
    when (identityId) {
        "rabbit" -> drawNaturalRabbit(anchor, palette, participantColor, direction, reaction, motion, scale)
        "hedgehog" -> drawNaturalHedgehog(anchor, palette, participantColor, direction, reaction, motion, scale)
        "owl" -> drawNaturalOwl(anchor, palette, participantColor, direction, reaction, motion, scale)
        else -> drawNaturalQuadruped(anchor, identityId, palette, participantColor, direction, reaction, motion, scale)
    }
}

private data class WoodlandAnimalProfile(
    val length: Float,
    val height: Float,
    val headRadius: Float,
    val snout: Float,
    val legLength: Float,
    val headLift: Float
)

private fun woodlandAnimalProfile(identityId: String): WoodlandAnimalProfile = when (identityId) {
    "bear" -> WoodlandAnimalProfile(50f, 29f, 15f, 8f, 15f, 3f)
    "fox" -> WoodlandAnimalProfile(52f, 22f, 13f, 15f, 18f, 8f)
    "squirrel" -> WoodlandAnimalProfile(39f, 20f, 12f, 8f, 15f, 7f)
    "raccoon" -> WoodlandAnimalProfile(46f, 24f, 13f, 9f, 16f, 5f)
    "otter" -> WoodlandAnimalProfile(55f, 19f, 12f, 8f, 12f, 2f)
    else -> WoodlandAnimalProfile(47f, 23f, 13f, 9f, 16f, 5f)
}

private fun DrawScope.drawNaturalQuadruped(
    anchor: Offset,
    identityId: String,
    palette: Pair<Color, Color>,
    participantColor: Color,
    direction: Float,
    reaction: Float,
    motion: SceneMotion,
    scale: Float
) {
    val profile = woodlandAnimalProfile(identityId)
    val bodyCenter = anchor + Offset(-direction * 4f * scale, 5f * scale)
    val halfLength = profile.length * 0.5f * scale
    val halfHeight = profile.height * 0.5f * scale
    val headCenter = bodyCenter + Offset(
        direction * (profile.length * 0.5f + profile.headRadius * 0.55f) * scale,
        -(profile.headLift + profile.height * 0.20f) * scale - reaction * 1.8f * scale
    )
    drawNaturalTail(bodyCenter, identityId, palette, direction, motion, scale)

    val gait = if (motion.reducedMotion) 0f else sin(motion.ambient * PI.toFloat() * 4f + anchor.x * 0.02f) * 0.8f * scale
    val legColor = palette.first.darken(0.23f)
    listOf(-0.31f to gait, 0.30f to -gait).forEachIndexed { index, (position, step) ->
        val hip = bodyCenter + Offset(direction * profile.length * position * scale, halfHeight * 0.58f)
        val knee = hip + Offset(direction * (if (index == 0) -2f else 2f) * scale, profile.legLength * 0.52f * scale)
        val paw = knee + Offset(direction * (3f * scale + step), profile.legLength * 0.46f * scale)
        drawLine(legColor, hip, knee, (if (identityId == "bear") 8f else 6f) * scale, StrokeCap.Round)
        drawLine(palette.first.darken(0.12f), knee, paw, (if (identityId == "bear") 7f else 5f) * scale, StrokeCap.Round)
        drawOval(
            palette.first.darken(0.34f),
            paw - Offset(5f * scale, 2.4f * scale),
            Size(11f * scale, 4.8f * scale)
        )
    }

    val body = Path().apply {
        moveTo(bodyCenter.x - halfLength, bodyCenter.y)
        cubicTo(
            bodyCenter.x - halfLength * 0.82f, bodyCenter.y - halfHeight,
            bodyCenter.x + halfLength * 0.45f, bodyCenter.y - halfHeight * 1.14f,
            bodyCenter.x + halfLength, bodyCenter.y - halfHeight * 0.28f
        )
        cubicTo(
            bodyCenter.x + halfLength * 0.98f, bodyCenter.y + halfHeight * 0.82f,
            bodyCenter.x - halfLength * 0.62f, bodyCenter.y + halfHeight,
            bodyCenter.x - halfLength, bodyCenter.y
        )
        close()
    }
    drawPath(
        body,
        Brush.linearGradient(
            listOf(palette.first.lighten(0.18f), palette.first, palette.first.darken(0.34f)),
            bodyCenter - Offset(halfLength, halfHeight),
            bodyCenter + Offset(halfLength, halfHeight)
        )
    )
    drawPath(body, Color.Black.copy(alpha = 0.27f), style = Stroke(1f * scale, join = StrokeJoin.Round))

    val neck = bodyCenter + Offset(direction * halfLength * 0.77f, -halfHeight * 0.17f)
    drawLine(palette.first.darken(0.08f), neck, headCenter, profile.headRadius * 1.18f * scale, StrokeCap.Round)
    drawWoodlandEars(headCenter, identityId, palette, direction, scale)
    drawCircle(
        Brush.linearGradient(
            listOf(palette.first.lighten(0.20f), palette.first, palette.first.darken(0.30f)),
            headCenter - Offset(profile.headRadius * scale, profile.headRadius * scale),
            headCenter + Offset(profile.headRadius * scale, profile.headRadius * scale)
        ),
        profile.headRadius * scale,
        headCenter
    )

    if (identityId == "raccoon") {
        val maskCenter = headCenter + Offset(direction * 4f * scale, -1.5f * scale)
        drawOval(Color(0xFF292D2C).copy(alpha = 0.92f), maskCenter - Offset(11f * scale, 4.5f * scale), Size(22f * scale, 9f * scale))
    }
    val muzzleCenter = headCenter + Offset(direction * (profile.headRadius * 0.78f + profile.snout * 0.38f) * scale, 3f * scale)
    drawOval(
        Brush.linearGradient(listOf(palette.second.lighten(0.18f), palette.second.darken(0.08f))),
        muzzleCenter - Offset(profile.snout * 0.62f * scale, 4.2f * scale),
        Size(profile.snout * 1.24f * scale, 8.4f * scale)
    )
    val nose = muzzleCenter + Offset(direction * profile.snout * 0.57f * scale, -0.4f * scale)
    drawCircle(Color(0xFF171714), (if (identityId == "bear") 2.4f else 1.9f) * scale, nose)
    drawAnimalEye(headCenter + Offset(direction * 5.2f * scale, -4.3f * scale), direction, motion, scale)

    if (identityId == "fox") {
        val chest = neck + Offset(-direction * 3f * scale, 7f * scale)
        drawPath(
            Path().apply {
                moveTo(chest.x, chest.y - 8f * scale)
                quadraticTo(chest.x + direction * 8f * scale, chest.y, chest.x, chest.y + 12f * scale)
            },
            palette.second.copy(alpha = 0.88f),
            style = Stroke(5f * scale, cap = StrokeCap.Round)
        )
    }
    drawLine(participantColor.copy(alpha = 0.92f), neck + Offset(0f, -5f * scale), neck + Offset(0f, 5f * scale), 2.2f * scale, StrokeCap.Round)

    if (motion.quality != ThemeRenderQuality.LOW) {
        drawFurDetail(bodyCenter, headCenter, palette, direction, scale, motion.quality)
    }
}

private fun DrawScope.drawNaturalTail(
    bodyCenter: Offset,
    identityId: String,
    palette: Pair<Color, Color>,
    direction: Float,
    motion: SceneMotion,
    scale: Float
) {
    val sway = if (motion.reducedMotion) 0f else sin(motion.ambient * PI.toFloat() * 2f + bodyCenter.x * 0.03f) * 3f * scale
    val root = bodyCenter - Offset(direction * 20f * scale, 2f * scale)
    when (identityId) {
        "fox", "raccoon" -> {
            val tail = Path().apply {
                moveTo(root.x, root.y - 5f * scale)
                cubicTo(root.x - direction * 22f * scale, root.y - 13f * scale, root.x - direction * 31f * scale, root.y + 3f * scale + sway, root.x - direction * 24f * scale, root.y + 11f * scale + sway)
                cubicTo(root.x - direction * 14f * scale, root.y + 16f * scale, root.x - direction * 5f * scale, root.y + 9f * scale, root.x, root.y + 4f * scale)
                close()
            }
            drawPath(tail, Brush.linearGradient(listOf(palette.first.darken(0.24f), palette.first, if (identityId == "fox") Color(0xFFE9DDC6) else palette.second)))
            if (identityId == "raccoon") repeat(3) { stripe ->
                val x = root.x - direction * (11f + stripe * 5f) * scale
                drawLine(Color(0xFF292C2B), Offset(x, root.y - 6f * scale + sway * 0.35f), Offset(x - direction * 2f * scale, root.y + 9f * scale + sway * 0.55f), 3.2f * scale, StrokeCap.Round)
            }
        }
        "squirrel" -> {
            val tail = Path().apply {
                moveTo(root.x, root.y + 5f * scale)
                cubicTo(root.x - direction * 29f * scale, root.y + 8f * scale, root.x - direction * 31f * scale, root.y - 33f * scale + sway, root.x - direction * 10f * scale, root.y - 38f * scale + sway)
                cubicTo(root.x + direction * 2f * scale, root.y - 28f * scale, root.x - direction * 7f * scale, root.y - 6f * scale, root.x, root.y + 5f * scale)
                close()
            }
            drawPath(tail, Brush.linearGradient(listOf(palette.first.darken(0.20f), palette.first.lighten(0.20f), palette.second)))
        }
        "otter" -> {
            val tail = Path().apply {
                moveTo(root.x, root.y - 3f * scale)
                cubicTo(root.x - direction * 18f * scale, root.y + 1f * scale, root.x - direction * 34f * scale, root.y + 12f * scale, root.x - direction * 42f * scale, root.y + 17f * scale + sway)
                cubicTo(root.x - direction * 27f * scale, root.y + 17f * scale, root.x - direction * 9f * scale, root.y + 9f * scale, root.x, root.y + 3f * scale)
                close()
            }
            drawPath(tail, Brush.linearGradient(listOf(palette.first, palette.first.darken(0.38f))))
        }
        "bear" -> drawCircle(palette.first.darken(0.14f), 4.5f * scale, root)
    }
}

private fun DrawScope.drawNaturalRabbit(
    anchor: Offset,
    palette: Pair<Color, Color>,
    participantColor: Color,
    direction: Float,
    reaction: Float,
    motion: SceneMotion,
    scale: Float
) {
    val bodyCenter = anchor + Offset(-direction * 6f * scale, 7f * scale)
    val headCenter = anchor + Offset(direction * 14f * scale, -7f * scale - reaction * 2f * scale)
    drawCircle(Color.White.copy(alpha = 0.82f), 6f * scale, bodyCenter - Offset(direction * 22f * scale, 0f))
    drawOval(palette.first.darken(0.18f), bodyCenter - Offset(18f * scale, 15f * scale), Size(36f * scale, 30f * scale))
    drawOval(palette.first, bodyCenter - Offset(10f * scale, 18f * scale), Size(28f * scale, 28f * scale))
    repeat(2) { index ->
        val earX = headCenter.x + (index * 2 - 1) * 4f * scale
        val lean = direction * (3f + index * 2f) * scale
        drawOval(palette.first.darken(0.10f), Offset(earX - 4f * scale + lean, headCenter.y - 31f * scale), Size(8f * scale, 29f * scale))
        drawOval(palette.second.copy(alpha = 0.78f), Offset(earX - 1.8f * scale + lean, headCenter.y - 27f * scale), Size(3.6f * scale, 21f * scale))
    }
    drawCircle(Brush.radialGradient(listOf(palette.first.lighten(0.20f), palette.first.darken(0.10f))), 12f * scale, headCenter)
    val muzzle = headCenter + Offset(direction * 10f * scale, 4f * scale)
    drawOval(Color(0xFFE8DAD4), muzzle - Offset(6f * scale, 3.7f * scale), Size(12f * scale, 7.4f * scale))
    drawCircle(Color(0xFF5B3C3D), 1.5f * scale, muzzle + Offset(direction * 5f * scale, -1f * scale))
    drawAnimalEye(headCenter + Offset(direction * 5f * scale, -4f * scale), direction, motion, scale)
    drawOval(palette.first.darken(0.24f), bodyCenter + Offset(direction * 6f * scale, 11f * scale), Size(15f * scale, 5f * scale))
    drawLine(participantColor.copy(alpha = 0.92f), headCenter + Offset(-direction * 8f * scale, 6f * scale), headCenter + Offset(-direction * 2f * scale, 9f * scale), 2.2f * scale, StrokeCap.Round)
}

private fun DrawScope.drawNaturalHedgehog(
    anchor: Offset,
    palette: Pair<Color, Color>,
    participantColor: Color,
    direction: Float,
    reaction: Float,
    motion: SceneMotion,
    scale: Float
) {
    val bodyCenter = anchor + Offset(-direction * 3f * scale, 7f * scale)
    val quills = Path().apply {
        moveTo(bodyCenter.x - direction * 27f * scale, bodyCenter.y + 12f * scale)
        repeat(15) { index ->
            val t = index / 14f
            val angle = PI.toFloat() * (1f + t)
            val radius = (if (index % 2 == 0) 27f else 22f) * scale
            lineTo(bodyCenter.x + cos(angle) * radius, bodyCenter.y + sin(angle) * radius * 0.72f)
        }
        lineTo(bodyCenter.x + direction * 24f * scale, bodyCenter.y + 13f * scale)
        close()
    }
    drawPath(quills, Brush.linearGradient(listOf(Color(0xFF30271F), palette.second, Color(0xFF211B17))))
    val face = Path().apply {
        moveTo(bodyCenter.x - direction * 4f * scale, bodyCenter.y - 10f * scale)
        quadraticTo(bodyCenter.x + direction * 17f * scale, bodyCenter.y - 9f * scale - reaction * 2f * scale, bodyCenter.x + direction * 29f * scale, bodyCenter.y + 2f * scale)
        quadraticTo(bodyCenter.x + direction * 12f * scale, bodyCenter.y + 14f * scale, bodyCenter.x - direction * 7f * scale, bodyCenter.y + 10f * scale)
        close()
    }
    drawPath(face, Brush.linearGradient(listOf(palette.first.lighten(0.22f), palette.first.darken(0.12f))))
    drawCircle(Color(0xFF161513), 2f * scale, bodyCenter + Offset(direction * 28f * scale, 2f * scale))
    drawAnimalEye(bodyCenter + Offset(direction * 12f * scale, -4f * scale), direction, motion, scale)
    drawLine(participantColor.copy(alpha = 0.9f), bodyCenter - Offset(direction * 2f * scale, 9f * scale), bodyCenter + Offset(-direction * 5f * scale, 8f * scale), 2f * scale)
}

private fun DrawScope.drawNaturalOwl(
    anchor: Offset,
    palette: Pair<Color, Color>,
    participantColor: Color,
    direction: Float,
    reaction: Float,
    motion: SceneMotion,
    scale: Float
) {
    val bodyCenter = anchor + Offset(0f, 3f * scale)
    val body = Path().apply {
        moveTo(bodyCenter.x, bodyCenter.y - 22f * scale)
        cubicTo(bodyCenter.x - 20f * scale, bodyCenter.y - 18f * scale, bodyCenter.x - 19f * scale, bodyCenter.y + 16f * scale, bodyCenter.x, bodyCenter.y + 25f * scale)
        cubicTo(bodyCenter.x + 19f * scale, bodyCenter.y + 16f * scale, bodyCenter.x + 20f * scale, bodyCenter.y - 18f * scale, bodyCenter.x, bodyCenter.y - 22f * scale)
        close()
    }
    drawPath(body, Brush.verticalGradient(listOf(palette.first.lighten(0.18f), palette.first, palette.first.darken(0.35f))))
    repeat(2) { sideIndex ->
        val side = if (sideIndex == 0) -1f else 1f
        drawArc(palette.second.copy(alpha = 0.32f), if (side < 0f) 85f else 185f, 92f, false, bodyCenter + Offset(side * 17f * scale - 12f * scale, -5f * scale), Size(24f * scale, 31f * scale), style = Stroke(5f * scale, cap = StrokeCap.Round))
    }
    val headCenter = bodyCenter - Offset(0f, 17f * scale + reaction * 1.5f * scale)
    drawCircle(palette.first.lighten(0.08f), 17f * scale, headCenter)
    drawWoodlandEars(headCenter, "owl", palette, direction, scale)
    val eyeOpen = animalEyeOpen(motion, headCenter.x)
    listOf(-6.5f, 6.5f).forEach { x ->
        val eye = headCenter + Offset(x * scale, -1f * scale)
        drawCircle(palette.second.lighten(0.24f), 6.7f * scale, eye)
        drawOval(Color(0xFF0B0C09), eye - Offset(1.8f * scale, 2.4f * scale * eyeOpen), Size(3.6f * scale, 4.8f * scale * eyeOpen.coerceAtLeast(0.12f)))
        if (eyeOpen > 0.5f) drawCircle(Color.White.copy(alpha = 0.9f), 0.7f * scale, eye - Offset(0.6f * scale, 1f * scale))
    }
    val beak = Path().apply {
        moveTo(headCenter.x - 3f * scale, headCenter.y + 4f * scale)
        lineTo(headCenter.x + direction * 7f * scale, headCenter.y + 7f * scale)
        lineTo(headCenter.x - 2f * scale, headCenter.y + 9f * scale)
        close()
    }
    drawPath(beak, Color(0xFFD9A03C))
    drawLine(participantColor.copy(alpha = 0.95f), bodyCenter - Offset(7f * scale, 1f * scale), bodyCenter + Offset(7f * scale, 1f * scale), 2.2f * scale, StrokeCap.Round)
    repeat(2) { claw ->
        val x = bodyCenter.x + (claw * 2 - 1) * 6f * scale
        drawLine(Color(0xFFD2A15A), Offset(x, bodyCenter.y + 21f * scale), Offset(x + direction * 3f * scale, bodyCenter.y + 26f * scale), 1.4f * scale, StrokeCap.Round)
    }
}

private fun animalEyeOpen(motion: SceneMotion, seed: Float): Float {
    if (motion.reducedMotion) return 1f
    val phase = (motion.ambient + pseudo(seed.roundToInt(), 0.64f) * 0.37f) % 1f
    val blinkDistance = abs(phase - 0.52f)
    return (blinkDistance / 0.018f).coerceIn(0.08f, 1f)
}

private fun DrawScope.drawAnimalEye(center: Offset, direction: Float, motion: SceneMotion, scale: Float) {
    val open = animalEyeOpen(motion, center.x)
    drawOval(Color(0xFF0B0C09), center - Offset(2.1f * scale, 2.5f * scale * open), Size(4.2f * scale, 5f * scale * open.coerceAtLeast(0.10f)))
    if (open > 0.45f) {
        drawCircle(Color(0xFF8D6036), 1.25f * scale, center)
        drawCircle(Color.White.copy(alpha = 0.94f), 0.55f * scale, center - Offset(direction * 0.45f * scale, 0.65f * scale))
    }
}

private fun DrawScope.drawWoodlandTail(
    center: Offset,
    identityId: String,
    palette: Pair<Color, Color>,
    direction: Float,
    scale: Float,
    motion: SceneMotion
) {
    val sway = if (motion.reducedMotion) 0f else sin(motion.ambient * PI.toFloat() * 2f + center.x) * 3f * scale
    when (identityId) {
        "squirrel", "fox", "raccoon" -> {
            val tail = Path().apply {
                moveTo(center.x - direction * 12f * scale, center.y + 8f * scale)
                cubicTo(
                    center.x - direction * 42f * scale, center.y + 12f * scale,
                    center.x - direction * 44f * scale, center.y - 29f * scale + sway,
                    center.x - direction * 21f * scale, center.y - 35f * scale + sway
                )
                cubicTo(
                    center.x - direction * 9f * scale, center.y - 23f * scale,
                    center.x - direction * 16f * scale, center.y - 3f * scale,
                    center.x - direction * 12f * scale, center.y + 8f * scale
                )
                close()
            }
            drawPath(tail, Brush.linearGradient(listOf(palette.first.darken(0.24f), palette.first.lighten(0.12f), palette.second)))
            drawPath(tail, Color.Black.copy(alpha = 0.24f), style = Stroke(1.1f * scale))
            if (identityId == "raccoon") {
                repeat(3) { stripe ->
                    val y = center.y - (11f + stripe * 8f) * scale + sway * 0.7f
                    drawLine(Color(0xFF303331).copy(alpha = 0.88f), Offset(center.x - direction * 33f * scale, y), Offset(center.x - direction * 18f * scale, y - 3f * scale), 4f * scale, StrokeCap.Round)
                }
            }
            if (identityId == "fox") {
                drawPath(tail, Color(0xFFF3E8D4).copy(alpha = 0.38f), style = Stroke(2.2f * scale, cap = StrokeCap.Round))
            }
        }
        "rabbit" -> drawCircle(palette.second.lighten(0.24f), 7f * scale, center + Offset(-direction * 15f * scale, 9f * scale))
        "otter" -> {
            val tail = Path().apply {
                moveTo(center.x - direction * 12f * scale, center.y + 12f * scale)
                cubicTo(center.x - direction * 28f * scale, center.y + 25f * scale, center.x - direction * 36f * scale, center.y + 24f * scale, center.x - direction * 41f * scale, center.y + 18f * scale)
            }
            drawPath(tail, palette.first.darken(0.32f), style = Stroke(9f * scale, cap = StrokeCap.Round))
        }
        else -> Unit
    }
}

private fun DrawScope.drawHedgehogQuills(center: Offset, direction: Float, scale: Float) {
    repeat(15) { index ->
        val t = index / 14f
        val angle = PI.toFloat() * (0.72f + t * 0.72f)
        val origin = center + Offset(cos(angle) * 18f * scale - direction * 3f * scale, sin(angle) * 22f * scale)
        val length = (7f + (index % 4) * 1.5f) * scale
        drawLine(Color(0xFF3B2D22), origin, origin + Offset(cos(angle) * length, sin(angle) * length), 2.1f * scale, StrokeCap.Round)
        drawLine(Color(0xFF9A7958).copy(alpha = 0.45f), origin, origin + Offset(cos(angle) * length * 0.65f, sin(angle) * length * 0.65f), 0.7f * scale, StrokeCap.Round)
    }
}

private fun DrawScope.drawWoodlandEars(
    headCenter: Offset,
    identityId: String,
    palette: Pair<Color, Color>,
    direction: Float,
    scale: Float
) {
    when (identityId) {
        "rabbit" -> repeat(2) { index ->
            val x = headCenter.x + (index * 2 - 1) * 7f * scale
            drawOval(palette.first.darken(0.12f), Offset(x - 4.5f * scale, headCenter.y - 35f * scale), Size(9f * scale, 29f * scale))
            drawOval(palette.second.copy(alpha = 0.72f), Offset(x - 2.1f * scale, headCenter.y - 31f * scale), Size(4.2f * scale, 21f * scale))
        }
        "fox", "squirrel" -> repeat(2) { index ->
            val side = (index * 2 - 1).toFloat()
            val ear = Path().apply {
                moveTo(headCenter.x + side * 10f * scale, headCenter.y - 7f * scale)
                lineTo(headCenter.x + side * 14f * scale, headCenter.y - 24f * scale)
                lineTo(headCenter.x + side * 2f * scale, headCenter.y - 13f * scale)
                close()
            }
            drawPath(ear, palette.first.darken(0.12f))
            drawPath(ear, palette.second.copy(alpha = 0.55f), style = Stroke(1.4f * scale, join = StrokeJoin.Round))
        }
        "owl" -> {
            drawLine(palette.first.lighten(0.14f), headCenter - Offset(10f * scale, 8f * scale), headCenter - Offset(15f * scale, 19f * scale), 4f * scale, StrokeCap.Round)
            drawLine(palette.first.lighten(0.14f), headCenter + Offset(10f * scale, -8f * scale), headCenter + Offset(15f * scale, -19f * scale), 4f * scale, StrokeCap.Round)
        }
        else -> repeat(2) { index ->
            val side = (index * 2 - 1).toFloat()
            drawCircle(palette.first.darken(0.16f), 6.5f * scale, headCenter + Offset(side * 10f * scale, -11f * scale))
            drawCircle(palette.second.copy(alpha = 0.72f), 3.4f * scale, headCenter + Offset(side * 10f * scale, -11f * scale))
        }
    }
}

private fun DrawScope.drawFurDetail(
    bodyCenter: Offset,
    headCenter: Offset,
    palette: Pair<Color, Color>,
    direction: Float,
    scale: Float,
    quality: ThemeRenderQuality
) {
    val count = if (quality == ThemeRenderQuality.HIGH) 16 else 8
    repeat(count) { index ->
        val x = bodyCenter.x + (pseudo(index, 0.22f) - 0.5f) * 27f * scale
        val y = bodyCenter.y + (pseudo(index, 0.72f) - 0.5f) * 34f * scale
        val length = (2.5f + pseudo(index, 0.51f) * 3.5f) * scale
        drawLine(
            palette.second.copy(alpha = 0.13f + pseudo(index, 0.81f) * 0.13f),
            Offset(x, y),
            Offset(x + direction * length, y + length * 0.28f),
            0.65f * scale,
            StrokeCap.Round
        )
    }
    drawArc(
        Color.White.copy(alpha = 0.10f),
        205f,
        105f,
        false,
        headCenter - Offset(12f * scale, 13f * scale),
        Size(24f * scale, 26f * scale),
        style = Stroke(1.1f * scale, cap = StrokeCap.Round)
    )
}

private fun Color.lighten(amount: Float): Color = lerp(this, Color.White, amount.coerceIn(0f, 1f))

private fun Color.darken(amount: Float): Color = lerp(this, Color.Black, amount.coerceIn(0f, 1f))

private fun woodlandPalette(identityId: String): Pair<Color, Color> = when (identityId) {
    "bear" -> Color(0xFF8B5B36) to Color(0xFF5C371F)
    "squirrel" -> Color(0xFFC47637) to Color(0xFF8E4826)
    "fox" -> Color(0xFFD66E35) to Color(0xFFEEE0C5)
    "rabbit" -> Color(0xFFBFC0B8) to Color(0xFFE6B7B7)
    "raccoon" -> Color(0xFF8A8C86) to Color(0xFF4B4D49)
    "hedgehog" -> Color(0xFFA8845F) to Color(0xFF58412D)
    "owl" -> Color(0xFFA97845) to Color(0xFFE2C28D)
    "otter" -> Color(0xFF8D6247) to Color(0xFFCFAC84)
    else -> Color(0xFF9B7752) to Color(0xFF5B402A)
}

private fun DrawScope.drawWoodlandMaterial(identityId: String, center: Offset, scale: Float) {
    when (identityId) {
        "bear" -> {
            drawCircle(Color(0xFFE5A329), 5.dp.toPx() * scale, center)
            drawCircle(Color(0xFFFFE3A0).copy(alpha = 0.58f), 1.4.dp.toPx() * scale, center - Offset(1.5.dp.toPx(), 1.5.dp.toPx()))
        }
        "squirrel", "hedgehog" -> {
            drawOval(Color(0xFF8B552F), center - Offset(4.dp.toPx(), 5.dp.toPx()) * scale, Size(8.dp.toPx() * scale, 10.dp.toPx() * scale))
            drawArc(Color(0xFFD5A56C), 185f, 170f, false, center - Offset(4.dp.toPx(), 5.dp.toPx()) * scale, Size(8.dp.toPx() * scale, 7.dp.toPx() * scale), style = Stroke(2.dp.toPx() * scale))
        }
        "fox" -> {
            drawCircle(Color(0xFFB8423C), 4.5.dp.toPx() * scale, center)
            drawLine(Color(0xFF5B843F), center - Offset(0f, 4.dp.toPx() * scale), center + Offset(2.dp.toPx() * scale, -8.dp.toPx() * scale), 1.5.dp.toPx() * scale)
        }
        "rabbit" -> {
            drawOval(Color(0xFFE88739), center - Offset(3.dp.toPx(), 6.dp.toPx()) * scale, Size(6.dp.toPx() * scale, 12.dp.toPx() * scale))
            drawLine(Color(0xFF75A85A), center - Offset(0f, 5.dp.toPx() * scale), center + Offset(3.dp.toPx() * scale, -9.dp.toPx() * scale), 1.5.dp.toPx() * scale)
        }
        "raccoon" -> {
            drawCircle(Color(0xFFD7B35E), 4.5.dp.toPx() * scale, center)
            drawCircle(Color(0xFFFFE49C), 3.dp.toPx() * scale, center, style = Stroke(1.2.dp.toPx() * scale))
        }
        else -> drawCircle(Color(0xFFD7A25A), 4.dp.toPx() * scale, center)
    }
}

private fun DrawScope.drawWoodlandContribution(
    state: ThemeFinancialState,
    progress: Float,
    chestLeft: Float,
    chestTop: Float,
    chestWidth: Float
) {
    val expense = state.latestContribution ?: return
    val fromLeft = expense.authorId == state.participantA?.id
    val origin = Offset(if (fromLeft) size.width * 0.20f else size.width * 0.80f, size.height * 0.48f)
    val target = Offset(chestLeft + chestWidth / 2f, chestTop - 8.dp.toPx())
    val arc = Path().apply {
        moveTo(origin.x, origin.y)
        quadraticTo(size.width * 0.5f, size.height * 0.24f, target.x, target.y)
    }
    val measure = PathMeasure().apply { setPath(arc, false) }
    val travel = (progress / 0.78f).coerceIn(0f, 1f)
    val position = measure.getPosition(measure.length * travel)
    val identity = if (fromLeft) state.participantA.identityId else state.participantB?.identityId.orEmpty()
    drawWoodlandMaterial(identity, position, 1f + sin(travel * PI).toFloat() * 0.45f)
    if (progress > 0.72f) {
        val impact = ((progress - 0.72f) / 0.28f).coerceIn(0f, 1f)
        repeat(5) { index ->
            val angle = PI.toFloat() * (1.1f + index * 0.2f)
            drawCircle(
                Color(0xFFFFD790).copy(alpha = 1f - impact),
                1.5.dp.toPx(),
                target + Offset(cos(angle) * impact * 15.dp.toPx(), sin(angle) * impact * 9.dp.toPx())
            )
        }
    }
}

private fun DrawScope.drawMempoolScene(
    state: ThemeFinancialState,
    fill: Float,
    motion: SceneMotion
) {
    val colorA = state.participantA?.colorHex.sceneColor(Color(0xFF38E66B))
    val colorB = state.participantB?.colorHex.sceneColor(Color(0xFF147DFF))
    val orange = Color(0xFFF7932E)
    val contributionReaction = sin(motion.contribution.coerceIn(0f, 1f) * PI).toFloat().coerceAtLeast(0f)
    val settlementEnergy = sin(motion.settlement.coerceIn(0f, 1f) * PI).toFloat().coerceAtLeast(0f)
    val settlementStrike = pulse(motion.settlement, 0.16f, 0.34f, 0.52f)

    val backdrop = motion.materials?.mempool(size.width, size.height, motion.ambient, settlementEnergy, colorA, colorB)
        ?: Brush.verticalGradient(listOf(Color(0xFF111B27), Color(0xFF080D14), Color(0xFF020407)))
    drawRect(backdrop)
    drawTechChamber(motion, colorA, colorB, orange)
    drawEnergyPylon(Offset(size.width * 0.14f, size.height * 0.31f), colorA, motion, settlementEnergy)
    drawEnergyPylon(Offset(size.width * 0.86f, size.height * 0.31f), colorB, motion, settlementEnergy)

    val railY = size.height * 0.80f
    drawLine(Brush.horizontalGradient(listOf(Color(0xFF20272E), Color(0xFFB4C4D1), Color(0xFF293039))), Offset(0f, railY), Offset(size.width, railY), 4.dp.toPx())
    drawLine(Brush.horizontalGradient(listOf(Color(0xFF151A1F), Color(0xFF788691), Color(0xFF171C21))), Offset(0f, railY + 17.dp.toPx()), Offset(size.width, railY + 17.dp.toPx()), 5.dp.toPx())
    repeat(12) { index ->
        val x = size.width * index / 11f
        drawLine(Color(0xFF47515B), Offset(x - 8.dp.toPx(), railY - 5.dp.toPx()), Offset(x + 9.dp.toPx(), railY + 23.dp.toPx()), 3.dp.toPx())
        drawCircle(orange.copy(alpha = 0.22f), 1.3.dp.toPx(), Offset(x, railY + 8.dp.toPx()))
    }

    drawMiningOperator(
        center = Offset(size.width * 0.16f, size.height * 0.61f),
        facingRight = true,
        accent = colorA,
        strike = maxOf(if (state.latestContribution?.authorId == state.participantA?.id) contributionReaction else 0f, settlementStrike),
        motion = motion
    )
    drawMiningOperator(
        center = Offset(size.width * 0.84f, size.height * 0.61f),
        facingRight = false,
        accent = colorB,
        strike = if (state.latestContribution?.authorId == state.participantB?.id) contributionReaction else settlementStrike * 0.38f,
        motion = motion
    )

    val cartLeft = size.width * 0.31f
    val cartTop = size.height * 0.49f
    val cartWidth = size.width * 0.38f
    val cartHeight = size.height * 0.24f
    drawMineCart(state, fill, motion, cartLeft, cartTop, cartWidth, cartHeight)

    if (motion.contribution > 0f) {
        drawMempoolContribution(state, motion.contribution, cartLeft, cartTop, cartWidth)
    }
    if (motion.settlement > 0f) drawMempoolSettlement(motion, orange, colorA, colorB, cartTop)
}

private fun pulse(value: Float, start: Float, peak: Float, end: Float): Float = when {
    value <= start || value >= end -> 0f
    value <= peak -> smoothStep(start, peak, value)
    else -> 1f - smoothStep(peak, end, value)
}

private fun DrawScope.drawTechChamber(motion: SceneMotion, colorA: Color, colorB: Color, orange: Color) {
    val ceiling = Path().apply {
        moveTo(0f, 0f)
        lineTo(0f, size.height * 0.18f)
        lineTo(size.width * 0.10f, size.height * 0.12f)
        lineTo(size.width * 0.22f, size.height * 0.19f)
        lineTo(size.width * 0.34f, size.height * 0.10f)
        lineTo(size.width * 0.50f, size.height * 0.16f)
        lineTo(size.width * 0.65f, size.height * 0.09f)
        lineTo(size.width * 0.78f, size.height * 0.18f)
        lineTo(size.width * 0.91f, size.height * 0.12f)
        lineTo(size.width, size.height * 0.18f)
        lineTo(size.width, 0f)
        close()
    }
    drawPath(ceiling, Brush.verticalGradient(listOf(Color(0xFF1A222B), Color(0xFF090E14))))
    drawPath(ceiling, Color(0xFF9CB2C1).copy(alpha = 0.12f), style = Stroke(1.dp.toPx()))

    repeat(7) { index ->
        val x = size.width * (0.08f + index * 0.14f)
        drawLine(Color(0xFF81909C).copy(alpha = 0.11f), Offset(x, size.height * 0.16f), Offset(x, size.height * 0.71f), 1.dp.toPx())
        val nodeY = size.height * (0.26f + pseudo(index, 0.52f) * 0.31f)
        val nodeColor = if (index < 3) colorA else if (index > 3) colorB else orange
        drawCircle(nodeColor.copy(alpha = 0.12f), 7.dp.toPx(), Offset(x, nodeY))
        drawCircle(nodeColor.copy(alpha = 0.55f), 1.6.dp.toPx(), Offset(x, nodeY))
    }

    repeat(if (motion.quality == ThemeRenderQuality.HIGH) 28 else 15) { index ->
        val x = size.width * ((pseudo(index, 0.31f) + motion.ambient * (0.012f + index % 4 * 0.003f)) % 1f)
        val y = size.height * (0.18f + pseudo(index, 0.67f) * 0.56f)
        drawCircle(Color(0xFFBFD8E7).copy(alpha = 0.06f + pseudo(index, 0.48f) * 0.10f), (0.65f + index % 3 * 0.4f).dp.toPx(), Offset(x, y))
    }

    val horizon = size.height * 0.43f
    drawLine(Brush.horizontalGradient(listOf(Color.Transparent, orange.copy(alpha = 0.30f), Color.Transparent)), Offset(0f, horizon), Offset(size.width, horizon), 1.dp.toPx())
}

private fun DrawScope.drawEnergyPylon(
    center: Offset,
    accent: Color,
    motion: SceneMotion,
    settlementEnergy: Float
) {
    val pulse = 0.78f + sin(motion.ambient * PI.toFloat() * 2f + center.x * 0.02f) * 0.22f
    val glow = 0.18f + pulse * 0.10f + settlementEnergy * 0.22f
    drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = glow), accent.copy(alpha = 0.035f), Color.Transparent)), 74.dp.toPx(), center)
    val frame = Path().apply {
        moveTo(center.x - 11.dp.toPx(), center.y + 18.dp.toPx())
        lineTo(center.x - 8.dp.toPx(), center.y - 17.dp.toPx())
        lineTo(center.x, center.y - 25.dp.toPx())
        lineTo(center.x + 8.dp.toPx(), center.y - 17.dp.toPx())
        lineTo(center.x + 11.dp.toPx(), center.y + 18.dp.toPx())
        close()
    }
    drawPath(frame, Brush.horizontalGradient(listOf(Color(0xFF27323C), Color(0xFFB7C8D3), Color(0xFF202A33))))
    drawPath(frame, accent.copy(alpha = 0.48f), style = Stroke(1.2.dp.toPx(), join = StrokeJoin.Round))
    drawRoundRect(Color(0xFF05080B), center - Offset(5.dp.toPx(), 12.dp.toPx()), Size(10.dp.toPx(), 25.dp.toPx()), CornerRadius(5.dp.toPx()))
    drawLine(accent.copy(alpha = 0.84f), center - Offset(0f, 9.dp.toPx()), center + Offset(0f, 10.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
    repeat(3) { index ->
        val y = center.y - 7.dp.toPx() + index * 7.dp.toPx()
        drawCircle(Color.White.copy(alpha = 0.72f - index * 0.14f), 0.9.dp.toPx(), Offset(center.x, y))
    }
}

private fun DrawScope.drawMiningOperator(
    center: Offset,
    facingRight: Boolean,
    accent: Color,
    strike: Float,
    motion: SceneMotion
) {
    val direction = if (facingRight) 1f else -1f
    val scale = size.minDimension / 390f
    val breath = if (motion.reducedMotion) 0f else {
        sin(motion.ambient * PI.toFloat() * 2f + center.x * 0.017f) * 0.7f * scale
    }
    val lift = strike.coerceIn(0f, 1f)
    val lean = direction * (1f - lift) * 2.5f * scale
    val anchor = center + Offset(lean, breath + (1f - lift) * 1.5f * scale)
    val ground = anchor + Offset(0f, 42f * scale)

    drawOval(
        Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.58f), Color.Transparent)),
        ground - Offset(27f * scale, 4f * scale),
        Size(54f * scale, 9f * scale)
    )

    // Work boots and articulated legs form a grounded, human stance.
    val rearHip = anchor + Offset(-direction * 6f * scale, 8f * scale)
    val rearKnee = anchor + Offset(-direction * (10f + lift * 2f) * scale, 25f * scale)
    val rearBoot = anchor + Offset(-direction * 14f * scale, 40f * scale)
    drawLine(Color(0xFF243745), rearHip, rearKnee, 9f * scale, StrokeCap.Round)
    drawLine(Color(0xFF1B2B36), rearKnee, rearBoot, 8f * scale, StrokeCap.Round)
    drawLine(Color(0xFF6F8A9A).copy(alpha = 0.42f), rearHip, rearKnee, 1.1f * scale, StrokeCap.Round)
    drawRoundRect(
        Brush.verticalGradient(listOf(Color(0xFF4A4035), Color(0xFF191714))),
        rearBoot - Offset(8f * scale, 3.5f * scale),
        Size(17f * scale, 7f * scale),
        CornerRadius(2.5f * scale)
    )

    val frontHip = anchor + Offset(direction * 6f * scale, 9f * scale)
    val frontKnee = anchor + Offset(direction * (10f - lift * 1.5f) * scale, 25f * scale)
    val frontBoot = anchor + Offset(direction * 18f * scale, 40f * scale)
    drawLine(Color(0xFF30495A), frontHip, frontKnee, 9.5f * scale, StrokeCap.Round)
    drawLine(Color(0xFF223642), frontKnee, frontBoot, 8f * scale, StrokeCap.Round)
    drawLine(Color(0xFF7A96A5).copy(alpha = 0.40f), frontHip, frontKnee, 1.2f * scale, StrokeCap.Round)
    drawRoundRect(
        Brush.verticalGradient(listOf(Color(0xFF594B3B), Color(0xFF1B1814))),
        frontBoot - Offset(8f * scale, 3.5f * scale),
        Size(19f * scale, 7f * scale),
        CornerRadius(2.5f * scale)
    )

    val hipCenter = anchor + Offset(0f, 7f * scale)
    drawOval(Color(0xFF1A2A34), hipCenter - Offset(12f * scale, 7f * scale), Size(24f * scale, 14f * scale))

    // Shirt, overalls, seams and suspenders replace the previous robot armour.
    val torso = Path().apply {
        moveTo(anchor.x - 13f * scale, anchor.y - 21f * scale)
        quadraticTo(anchor.x, anchor.y - 27f * scale, anchor.x + 13f * scale, anchor.y - 20f * scale)
        lineTo(anchor.x + 10f * scale, anchor.y + 8f * scale)
        lineTo(anchor.x - 10f * scale, anchor.y + 8f * scale)
        close()
    }
    drawPath(
        torso,
        Brush.verticalGradient(
            listOf(Color(0xFFC59B62), Color(0xFF855E3B), Color(0xFF4A3526)),
            anchor.y - 24f * scale,
            anchor.y + 10f * scale
        )
    )
    drawPath(torso, Color(0xFFFFE1B2).copy(alpha = 0.24f), style = Stroke(0.9f * scale, join = StrokeJoin.Round))

    val bib = Path().apply {
        moveTo(anchor.x - 8f * scale, anchor.y - 14f * scale)
        lineTo(anchor.x + 8f * scale, anchor.y - 14f * scale)
        lineTo(anchor.x + 7f * scale, anchor.y + 7f * scale)
        lineTo(anchor.x - 7f * scale, anchor.y + 7f * scale)
        close()
    }
    drawPath(bib, Brush.verticalGradient(listOf(Color(0xFF42677C), Color(0xFF1E3441))))
    drawPath(bib, Color(0xFF9AB2BE).copy(alpha = 0.36f), style = Stroke(0.8f * scale, join = StrokeJoin.Round))
    drawLine(Color(0xFF274756), anchor + Offset(-10f * scale, -21f * scale), anchor + Offset(-5f * scale, -8f * scale), 2.2f * scale, StrokeCap.Round)
    drawLine(Color(0xFF274756), anchor + Offset(10f * scale, -21f * scale), anchor + Offset(5f * scale, -8f * scale), 2.2f * scale, StrokeCap.Round)
    drawRoundRect(accent.copy(alpha = 0.76f), anchor + Offset(-4f * scale, -6f * scale), Size(8f * scale, 3f * scale), CornerRadius(1f * scale))

    val neck = anchor - Offset(0f, 26f * scale)
    drawLine(Color(0xFF9A6948), neck, neck + Offset(0f, 7f * scale), 7f * scale, StrokeCap.Round)
    val headCenter = anchor + Offset(direction * 1.5f * scale, -35f * scale)
    drawOval(
        Brush.linearGradient(listOf(Color(0xFFE3AA75), Color(0xFFA96E49), Color(0xFF69452F))),
        headCenter - Offset(9f * scale, 10f * scale),
        Size(18f * scale, 21f * scale)
    )
    drawCircle(Color(0xFFB47C55), 2.7f * scale, headCenter - Offset(direction * 8f * scale, 0f))
    val nose = headCenter + Offset(direction * 9f * scale, 1f * scale)
    drawCircle(Color(0xFFC68B61), 2.2f * scale, nose)
    drawLine(Color(0xFF4A3126), headCenter + Offset(direction * 5f * scale, 6f * scale), headCenter + Offset(direction * 9f * scale, 5f * scale), 1f * scale, StrokeCap.Round)
    drawCircle(Color(0xFF201A16), 1.25f * scale, headCenter + Offset(direction * 5f * scale, -2.5f * scale))
    drawCircle(Color.White.copy(alpha = 0.74f), 0.35f * scale, headCenter + Offset(direction * 4.7f * scale, -2.9f * scale))

    // Hard hat, brim and lamp read clearly even at compact scene sizes.
    val helmetTop = headCenter.y - 12f * scale
    drawArc(
        Brush.verticalGradient(listOf(Color(0xFFFFD35A), Color(0xFFD98A1F))),
        180f,
        180f,
        true,
        Offset(headCenter.x - 11f * scale, helmetTop),
        Size(22f * scale, 17f * scale)
    )
    drawLine(Color(0xFFFFD96A), Offset(headCenter.x - 13f * scale, helmetTop + 8f * scale), Offset(headCenter.x + direction * 15f * scale, helmetTop + 8f * scale), 3.2f * scale, StrokeCap.Round)
    val lampCenter = Offset(headCenter.x + direction * 7f * scale, helmetTop + 3f * scale)
    drawCircle(Color(0xFF3D3327), 3.4f * scale, lampCenter)
    drawCircle(Color(0xFFFFF0B0), 2.1f * scale, lampCenter)
    drawCircle(
        Brush.radialGradient(listOf(Color(0xFFFFE79A).copy(alpha = 0.16f), Color.Transparent)),
        13f * scale,
        lampCenter
    )

    val toolPivot = anchor + Offset(direction * 8f * scale, -17f * scale)
    val toolAngle = direction * (37f - lift * 112f)
    val impactFlash = maxOf(
        pulse(motion.contribution, 0.72f, 0.84f, 0.98f),
        pulse(motion.settlement, 0.22f, 0.36f, 0.53f)
    )
    rotate(toolAngle, pivot = toolPivot) {
        val rearShoulder = anchor + Offset(-direction * 10f * scale, -18f * scale)
        val rearElbow = toolPivot + Offset(-direction * 5f * scale, 12f * scale)
        val rearHand = toolPivot + Offset(direction * 8f * scale, 17f * scale)
        drawLine(Color(0xFF7D563B), rearShoulder, rearElbow, 7f * scale, StrokeCap.Round)
        drawLine(Color(0xFFC48A61), rearElbow, rearHand, 5.5f * scale, StrokeCap.Round)

        val leadShoulder = anchor + Offset(direction * 11f * scale, -17f * scale)
        val leadElbow = toolPivot + Offset(direction * 13f * scale, 9f * scale)
        val leadHand = toolPivot + Offset(direction * 25f * scale, 13f * scale)
        drawLine(Color(0xFF916441), leadShoulder, leadElbow, 7.5f * scale, StrokeCap.Round)
        drawLine(Color(0xFFD39869), leadElbow, leadHand, 5.5f * scale, StrokeCap.Round)
        drawCircle(Color(0xFF70472F), 3.2f * scale, rearHand)
        drawCircle(Color(0xFF70472F), 3.2f * scale, leadHand)

        val shaftStart = toolPivot + Offset(-direction * 2f * scale, 17f * scale)
        val pickHead = toolPivot + Offset(direction * 49f * scale, 12f * scale)
        drawLine(Color(0xFF3E2819), shaftStart, pickHead, 4.2f * scale, StrokeCap.Round)
        drawLine(Color(0xFFB48752).copy(alpha = 0.72f), shaftStart + Offset(0f, -0.7f * scale), pickHead + Offset(0f, -0.7f * scale), 1.1f * scale, StrokeCap.Round)
        val across = Offset(1.5f * scale, 12f * scale)
        drawLine(Color(0xFF252B2E), pickHead - across, pickHead + across, 5f * scale, StrokeCap.Round)
        drawLine(Color(0xFFAEB9BC), pickHead - across * 0.92f, pickHead + across * 0.92f, 1.3f * scale, StrokeCap.Round)
        if (impactFlash > 0f) {
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = impactFlash * 0.78f), accent.copy(alpha = impactFlash * 0.34f), Color.Transparent)), (8f + impactFlash * 12f) * scale, pickHead)
            repeat(7) { index ->
                val angle = pseudo(index, 0.79f) * PI.toFloat() * 2f
                val distance = impactFlash * (7f + index * 1.5f) * scale
                drawLine(
                    Color(0xFFFFC66C).copy(alpha = impactFlash),
                    pickHead + Offset(cos(angle) * distance * 0.45f, sin(angle) * distance * 0.45f),
                    pickHead + Offset(cos(angle) * distance, sin(angle) * distance),
                    1.1f * scale,
                    StrokeCap.Round
                )
            }
        }
    }
}

private fun DrawScope.drawMineCart(
    state: ThemeFinancialState,
    fill: Float,
    motion: SceneMotion,
    left: Float,
    top: Float,
    width: Float,
    height: Float
) {
    val settleEnvelope = sin(motion.settlement.coerceIn(0f, 1f) * PI.toFloat()).coerceAtLeast(0f)
    val shake = if (motion.reducedMotion) 0f else {
        sin(motion.settlement * PI.toFloat() * 12f) * settleEnvelope * 3.2.dp.toPx()
    }
    val orange = Color(0xFFF7932E)
    val center = Offset(left + width / 2f + shake, top + height * 0.52f)
    drawOval(
        Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.62f), Color.Transparent)),
        Offset(left - width * 0.08f + shake, top + height * 0.86f),
        Size(width * 1.16f, height * 0.58f)
    )
    drawCircle(
        Brush.radialGradient(
            listOf(
                orange.copy(alpha = 0.08f + fill * 0.13f + settleEnvelope * 0.12f),
                orange.copy(alpha = 0.025f),
                Color.Transparent
            )
        ),
        width * 0.62f,
        center
    )

    val cart = Path().apply {
        moveTo(left + shake, top)
        lineTo(left + width + shake, top)
        lineTo(left + width * 0.84f + shake, top + height)
        lineTo(left + width * 0.16f + shake, top + height)
        close()
    }

    val cavity = Path().apply {
        moveTo(left + width * 0.07f + shake, top + 5.dp.toPx())
        lineTo(left + width * 0.93f + shake, top + 5.dp.toPx())
        lineTo(left + width * 0.79f + shake, top + height * 0.83f)
        lineTo(left + width * 0.21f + shake, top + height * 0.83f)
        close()
    }
    drawPath(cart, Brush.verticalGradient(listOf(Color(0xFF87949D), Color(0xFF35414A), Color(0xFF111920)), top, top + height))
    drawPath(cavity, Brush.verticalGradient(listOf(Color(0xFF030608), Color(0xFF12100D), Color(0xFF29180B)), top, top + height))
    drawPath(cavity, orange.copy(alpha = 0.14f + fill * 0.12f), style = Stroke(5.dp.toPx(), join = StrokeJoin.Round))

    val maxBlocks = when (motion.quality) {
        ThemeRenderQuality.LOW -> 8
        ThemeRenderQuality.MEDIUM -> 14
        ThemeRenderQuality.HIGH -> 22
    }
    val visible = (maxBlocks * fill).roundToInt().coerceIn(0, maxBlocks)
    val total = state.participantAContributionMinor + state.participantBContributionMinor
    val ratioA = if (total == 0L) 0.5f else state.participantAContributionMinor.toFloat() / total.toFloat()
    clipPath(cavity) {
        repeat(visible) { index ->
            val columns = 6
            val row = index / columns
            val column = index % columns
            val blockWidth = width * 0.105f
            val x = left + width * 0.18f + column * blockWidth * 1.08f + shake
            val y = top + height - 9.dp.toPx() - (row + 1) * 12.dp.toPx()
            val ownerColor = if (index.toFloat() / maxBlocks < ratioA) {
                state.participantA?.colorHex.sceneColor(Color(0xFF38E66B))
            } else {
                state.participantB?.colorHex.sceneColor(Color(0xFF147DFF))
            }
            drawRoundRect(
                Brush.linearGradient(
                    listOf(Color(0xFFFFC86E), orange, Color(0xFF7E3618), ownerColor.copy(alpha = 0.88f)),
                    Offset(x, y),
                    Offset(x + blockWidth, y + 9.dp.toPx())
                ),
                Offset(x, y),
                Size(blockWidth, 9.dp.toPx()),
                CornerRadius(1.7.dp.toPx())
            )
            drawLine(Color.White.copy(alpha = 0.42f), Offset(x + 2.dp.toPx(), y + 2.dp.toPx()), Offset(x + blockWidth * 0.66f, y + 2.dp.toPx()), 0.65.dp.toPx())
            drawCircle(ownerColor.copy(alpha = 0.92f), 0.8.dp.toPx(), Offset(x + blockWidth - 2.dp.toPx(), y + 2.dp.toPx()))
        }
        drawRect(
            Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.055f), Color.Transparent)),
            Offset(left + width * 0.08f + shake, top),
            Size(width * 0.84f, height * 0.76f)
        )
    }

    repeat(3) { band ->
        val x = left + width * (0.25f + band * 0.25f) + shake
        drawLine(Color(0xFF0E151B).copy(alpha = 0.78f), Offset(x, top + 5.dp.toPx()), Offset(x - width * 0.06f, top + height - 4.dp.toPx()), 4.dp.toPx())
        drawLine(Color(0xFFB9C6CD).copy(alpha = 0.27f), Offset(x - 1.dp.toPx(), top + 7.dp.toPx()), Offset(x - width * 0.06f - 1.dp.toPx(), top + height - 7.dp.toPx()), 0.8.dp.toPx())
    }
    drawPath(cart, Color(0xFFDCE4E8).copy(alpha = 0.50f), style = Stroke(1.7.dp.toPx(), join = StrokeJoin.Round))
    drawLine(
        Brush.horizontalGradient(listOf(Color(0xFF28323A), Color(0xFFE0E8EC), orange, Color(0xFF1B242B))),
        Offset(left - 2.dp.toPx() + shake, top),
        Offset(left + width + 2.dp.toPx() + shake, top),
        5.dp.toPx(),
        StrokeCap.Round
    )
    repeat(5) { rivet ->
        val x = left + width * (0.12f + rivet * 0.19f) + shake
        drawCircle(Color(0xFFE0E8EC).copy(alpha = 0.62f), 1.4.dp.toPx(), Offset(x, top + height * 0.69f))
        drawCircle(Color.Black.copy(alpha = 0.40f), 0.65.dp.toPx(), Offset(x + 0.4.dp.toPx(), top + height * 0.69f + 0.4.dp.toPx()))
    }
    repeat(2) { wheel ->
        val wheelCenter = Offset(left + width * (0.25f + wheel * 0.50f) + shake, top + height + 8.dp.toPx())
        drawCircle(Color(0xFF070A0D), 14.dp.toPx(), wheelCenter)
        drawCircle(Brush.radialGradient(listOf(Color(0xFF98A6AE), Color(0xFF29343C), Color(0xFF0B1014))), 9.dp.toPx(), wheelCenter)
        drawCircle(orange.copy(alpha = 0.58f), 6.dp.toPx(), wheelCenter, style = Stroke(1.dp.toPx()))
        drawCircle(Color(0xFFBCC9CF), 2.3.dp.toPx(), wheelCenter)
    }
}

private fun DrawScope.drawMempoolContribution(
    state: ThemeFinancialState,
    progress: Float,
    cartLeft: Float,
    cartTop: Float,
    cartWidth: Float
) {
    val expense = state.latestContribution ?: return
    val fromLeft = expense.authorId == state.participantA?.id
    val accent = if (fromLeft) {
        state.participantA.colorHex.sceneColor(Color(0xFF38E66B))
    } else {
        state.participantB?.colorHex.sceneColor(Color(0xFF147DFF))
    }
    val origin = Offset(if (fromLeft) size.width * 0.22f else size.width * 0.78f, size.height * 0.47f)
    val target = Offset(cartLeft + cartWidth / 2f, cartTop + 5.dp.toPx())
    val path = Path().apply {
        moveTo(origin.x, origin.y)
        quadraticTo(size.width * 0.5f, size.height * 0.25f, target.x, target.y)
    }
    val measure = PathMeasure().apply { setPath(path, false) }
    val travel = (progress / 0.78f).coerceIn(0f, 1f)
    val position = measure.getPosition(measure.length * travel)
    val scale = 1f + sin(travel * PI).toFloat() * 0.35f

    repeat(9) { index ->
        val trailT = (travel - index * 0.035f).coerceIn(0f, 1f)
        if (trailT > 0f) {
            val trailPosition = measure.getPosition(measure.length * trailT)
            val alpha = (1f - index / 9f) * (0.12f + travel * 0.42f)
            val radius = (5f - index * 0.38f).coerceAtLeast(1.1f).dp.toPx()
            drawCircle(
                Brush.radialGradient(listOf(accent.copy(alpha = alpha), Color(0xFFF7932E).copy(alpha = alpha * 0.58f), Color.Transparent)),
                radius,
                trailPosition
            )
        }
    }
    drawPath(path, accent.copy(alpha = 0.10f * sin(travel * PI).toFloat()), style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 8.dp.toPx()), travel * 28.dp.toPx())))

    rotate(travel * 240f, position) {
        drawCircle(
            Brush.radialGradient(listOf(accent.copy(alpha = 0.34f), Color(0xFFF7932E).copy(alpha = 0.15f), Color.Transparent)),
            21.dp.toPx() * scale,
            position
        )
        drawRoundRect(
            Brush.linearGradient(listOf(Color(0xFFFFD78C), Color(0xFFF7932E), Color(0xFF8F3517), accent.copy(alpha = 0.88f))),
            position - Offset(8.dp.toPx() * scale, 6.dp.toPx() * scale),
            Size(16.dp.toPx() * scale, 12.dp.toPx() * scale),
            CornerRadius(3.dp.toPx())
        )
        drawRoundRect(
            Color.White.copy(alpha = 0.22f),
            position - Offset(8.dp.toPx() * scale, 6.dp.toPx() * scale),
            Size(16.dp.toPx() * scale, 12.dp.toPx() * scale),
            CornerRadius(3.dp.toPx()),
            style = Stroke(0.9.dp.toPx())
        )
        drawLine(Color.White.copy(alpha = 0.62f), position - Offset(4.dp.toPx() * scale, 2.dp.toPx()), position + Offset(4.dp.toPx() * scale, -2.dp.toPx()), 0.8.dp.toPx())
        drawLine(accent.copy(alpha = 0.82f), position + Offset(-4.dp.toPx() * scale, 2.dp.toPx()), position + Offset(2.dp.toPx() * scale, 2.dp.toPx()), 0.8.dp.toPx())
        drawCircle(Color.White.copy(alpha = 0.78f), 0.9.dp.toPx(), position + Offset(4.dp.toPx() * scale, 2.dp.toPx()))
    }
    if (progress > 0.68f) {
        val impact = ((progress - 0.68f) / 0.32f).coerceIn(0f, 1f)
        drawCircle(
            Color(0xFFF7932E).copy(alpha = (1f - impact) * 0.58f),
            (5f + impact * 28f).dp.toPx(),
            target,
            style = Stroke((2f - impact).coerceAtLeast(0.6f).dp.toPx())
        )
        repeat(12) { index ->
            val angle = pseudo(index, 0.91f) * PI.toFloat() * 2f
            val distance = impact * (9 + index * 1.8f).dp.toPx()
            val sparkStart = target + Offset(cos(angle) * distance * 0.48f, sin(angle) * distance * 0.48f)
            val sparkEnd = target + Offset(cos(angle) * distance, sin(angle) * distance)
            val sparkColor = if (index % 3 == 0) accent else Color(0xFFF7932E)
            drawLine(sparkColor.copy(alpha = 1f - impact), sparkStart, sparkEnd, (1f + index % 2 * 0.5f).dp.toPx(), StrokeCap.Round)
        }
    }
}

private fun DrawScope.drawMempoolSettlement(
    motion: SceneMotion,
    orange: Color,
    colorA: Color,
    colorB: Color,
    cartTop: Float
) {
    val progress = motion.settlement.coerceIn(0f, 1f)
    val center = Offset(size.width * 0.5f, cartTop + 8.dp.toPx())
    val charge = smoothStep(0f, 0.30f, progress) * (1f - smoothStep(0.42f, 0.62f, progress))
    val flash = pulse(progress, 0.24f, 0.39f, 0.57f)
    val release = smoothStep(0.36f, 0.90f, progress)
    val fade = 1f - smoothStep(0.82f, 1f, progress)

    if (charge > 0f) {
        repeat(5) { ring ->
            val phase = ((charge * 1.45f + ring / 5f) % 1f)
            val radius = (70f - phase * 54f).dp.toPx()
            drawCircle(
                if (ring % 2 == 0) colorA.copy(alpha = phase * 0.24f) else colorB.copy(alpha = phase * 0.24f),
                radius,
                center,
                style = Stroke((0.8f + phase * 1.8f).dp.toPx())
            )
        }
        repeat(12) { index ->
            val angle = index / 12f * PI.toFloat() * 2f + motion.ambient * 0.65f
            val distance = (30f + (1f - charge) * 48f + pseudo(index, 0.37f) * 16f).dp.toPx()
            val dot = center + Offset(cos(angle) * distance, sin(angle) * distance * 0.46f)
            drawCircle(if (index % 2 == 0) colorA.copy(alpha = charge * 0.72f) else colorB.copy(alpha = charge * 0.72f), (1f + index % 3 * 0.45f).dp.toPx(), dot)
        }
    }

    if (flash > 0f) {
        drawCircle(
            Brush.radialGradient(
                listOf(Color.White.copy(alpha = flash * 0.92f), orange.copy(alpha = flash * 0.62f), orange.copy(alpha = flash * 0.12f), Color.Transparent)
            ),
            (18f + flash * 92f).dp.toPx(),
            center
        )
        val beamTop = size.height * 0.12f
        drawRect(
            Brush.horizontalGradient(listOf(Color.Transparent, orange.copy(alpha = flash * 0.18f), Color.White.copy(alpha = flash * 0.72f), orange.copy(alpha = flash * 0.18f), Color.Transparent)),
            Offset(center.x - (5f + flash * 22f).dp.toPx(), beamTop),
            Size((10f + flash * 44f).dp.toPx(), center.y - beamTop)
        )
        drawLine(Color.White.copy(alpha = flash * 0.92f), Offset(center.x, beamTop), center, (0.8f + flash * 2.2f).dp.toPx())
    }

    if (release > 0f && fade > 0f) {
        repeat(3) { ring ->
            val local = ((release - ring * 0.16f) / (1f - ring * 0.16f)).coerceIn(0f, 1f)
            if (local > 0f) {
                drawOval(
                    orange.copy(alpha = (1f - local) * fade * (0.48f - ring * 0.08f)),
                    center - Offset((18f + local * 125f).dp.toPx(), (5f + local * 36f).dp.toPx()),
                    Size((36f + local * 250f).dp.toPx(), (10f + local * 72f).dp.toPx()),
                    style = Stroke((2.2f - local).coerceAtLeast(0.55f).dp.toPx())
                )
            }
        }

        repeat(24) { index ->
            val angle = pseudo(index, 0.14f) * PI.toFloat() * 2f
            val speed = (42f + pseudo(index, 0.71f) * 130f).dp.toPx()
            val gravity = 105f.dp.toPx()
            val t = release
            val originOffset = Offset((pseudo(index, 0.44f) - 0.5f) * 28.dp.toPx(), 0f)
            val particle = center + originOffset + Offset(cos(angle) * speed * t, sin(angle) * speed * t + gravity * t * t)
            val previous = center + originOffset + Offset(cos(angle) * speed * (t - 0.045f).coerceAtLeast(0f), sin(angle) * speed * (t - 0.045f).coerceAtLeast(0f) + gravity * (t - 0.045f).coerceAtLeast(0f) * (t - 0.045f).coerceAtLeast(0f))
            val particleColor = when (index % 3) {
                0 -> colorA
                1 -> colorB
                else -> orange
            }
            drawLine(particleColor.copy(alpha = fade * (0.45f + pseudo(index, 0.19f) * 0.45f)), previous, particle, (0.7f + index % 3 * 0.45f).dp.toPx(), StrokeCap.Round)
        }

        val assembly = smoothStep(0.42f, 0.72f, progress) * fade
        if (assembly > 0f) {
            repeat(3) { layer ->
                val layerProgress = ((assembly - layer * 0.13f) / (1f - layer * 0.13f)).coerceIn(0f, 1f)
                val y = center.y - (18f + layer * 10f + (1f - layerProgress) * 38f).dp.toPx()
                val halfWidth = (12f + layer * 4f).dp.toPx()
                val panel = Path().apply {
                    moveTo(center.x - halfWidth, y)
                    lineTo(center.x - halfWidth * 0.48f, y - 6.dp.toPx())
                    lineTo(center.x + halfWidth * 0.52f, y - 6.dp.toPx())
                    lineTo(center.x + halfWidth, y)
                    lineTo(center.x + halfWidth * 0.48f, y + 6.dp.toPx())
                    lineTo(center.x - halfWidth * 0.52f, y + 6.dp.toPx())
                    close()
                }
                drawPath(panel, Brush.horizontalGradient(listOf(colorA.copy(alpha = 0.76f * layerProgress), orange.copy(alpha = 0.94f * layerProgress), colorB.copy(alpha = 0.76f * layerProgress))))
                drawPath(panel, Color.White.copy(alpha = 0.46f * layerProgress), style = Stroke(0.8.dp.toPx(), join = StrokeJoin.Round))
            }
        }
    }
}

private fun pseudo(index: Int, salt: Float): Float {
    val value = sin(index * 12.9898f + salt * 78.233f) * 43_758.547f
    return abs(value - value.toInt()).coerceIn(0f, 1f)
}

private fun String?.sceneColor(fallback: Color): Color = runCatching {
    Color(android.graphics.Color.parseColor(this ?: ""))
}.getOrDefault(fallback)
