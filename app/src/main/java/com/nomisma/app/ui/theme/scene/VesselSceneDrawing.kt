package com.nomisma.app.ui.theme.scene

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.nomisma.app.theme.ThemeFinancialState
import com.nomisma.app.theme.motion.ParticlePool
import com.nomisma.app.theme.motion.smoothStep
import com.nomisma.app.ui.theme.SceneMaterials
import com.nomisma.app.ui.theme.ThemeAssetPack
import com.nomisma.app.ui.theme.ThemeRenderQuality
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Everything [drawVesselScene] needs for one frame. Owned and reused by the
 * controller: mutated before every render, never allocated per frame. Paths are
 * rewinded before being rebuilt by the drawing functions below.
 */
internal class VesselFrame {
    var state: ThemeFinancialState? = null
    var fill: Float = 0f
    var ambient: Float = 0.25f
    var contribution: Float = 0f
    var settlement: Float = 0f
    var quality: ThemeRenderQuality = ThemeRenderQuality.LOW
    var materials: SceneMaterials? = null
    var assets: ThemeAssetPack? = null
    var eventSeed: Int = 0
    var contributingParticipantId: String? = null
    var droplets: ParticlePool? = null

    val beamPathLeft = Path()
    val beamPathRight = Path()
    val vesselPath = Path()
    val handlePath = Path()
    val rimPath = Path()
    val surfacePath = Path()
    val surfaceBackPath = Path()
    val scratchPath = Path()
}

internal fun DrawScope.drawVesselScene(frame: VesselFrame) {
    val state = frame.state ?: return
    val fill = frame.fill
    val participantAColor = state.participantA?.colorHex.sceneColor(Color(0xFF38E66B))
    val participantBColor = state.participantB?.colorHex.sceneColor(Color(0xFF147DFF))
    val contributionGlow = sin(frame.contribution.coerceIn(0f, 1f) * PI).toFloat().coerceAtLeast(0f)
    val settlementGlow = sin(frame.settlement.coerceIn(0f, 1f) * PI).toFloat().coerceAtLeast(0f)
    val impactWave = dampedWave(frame.contribution, start = 0.40f, oscillations = 4.5f, damping = 3.2f)

    frame.assets?.background?.let(::drawAssetCover)
        ?: drawRect(Brush.verticalGradient(listOf(Color(0xFF121B2A), Color(0xFF070A12), Color(0xFF020307))))
    if (frame.assets?.background != null) {
        drawRect(Color(0xFF02050A).copy(alpha = 0.16f))
    }
    drawStudioLightShafts(participantAColor, participantBColor, frame)
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
    drawVesselDust(frame)

    // These measurements track the transparent glass asset rather than its
    // full bitmap bounds (which also include the handle and empty padding).
    val centerX = size.width * 0.405f
    val top = size.height * 0.125f
    val bottom = size.height * 0.875f
    val bodyLeft = size.width * 0.145f
    val bodyRight = size.width * 0.665f
    val vessel = buildVesselPath(frame.vesselPath, centerX, top, bottom, bodyLeft, bodyRight)
    val liquidBottom = bottom - 5.dp.toPx()
    val liquidHeight = (bottom - top) * 0.84f * fill.coerceIn(0f, 1f)
    val liquidTop = liquidBottom - liquidHeight

    drawOval(
        Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.82f), Color.Transparent)),
        topLeft = Offset(size.width * 0.18f, size.height * 0.79f),
        size = Size(size.width * 0.66f, size.height * 0.17f)
    )

    if (frame.assets?.pitcher == null) {
        drawPitcherHandle(frame, bodyRight, top, bottom, participantBColor)
    }

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
            val total = state.participantAContributionMinor + state.participantBContributionMinor
            val ratioA = if (total <= 0L) 0.5f else state.participantAContributionMinor.toFloat() / total.toFloat()
            val liquidBrush = frame.materials?.liquid(
                width = size.width,
                height = size.height,
                time = frame.ambient,
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
                frame = frame
            )
            drawLiquidSurface(
                frame = frame,
                liquidTop = liquidTop,
                left = bodyLeft,
                right = bodyRight,
                impactWave = impactWave
            )
        }
    }

    if (frame.contribution > 0f) {
        drawLiquidContribution(
            frame = frame,
            colorA = participantAColor,
            colorB = participantBColor,
            centerX = centerX,
            vesselTop = top,
            vesselBottom = liquidBottom,
            liquidSurfaceY = if (fill > 0.01f) liquidTop else liquidBottom,
            cavityLeft = bodyLeft,
            cavityRight = bodyRight
        )
    }

    val pitcher = frame.assets?.pitcher
    if (pitcher != null) {
        drawAssetInRect(
            image = pitcher,
            left = size.width * 0.01f,
            top = size.height * 0.055f,
            width = size.width * 0.91f,
            height = size.height * 0.86f
        )
    } else {
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
        drawVesselHighlights(frame, centerX, top, bottom, bodyLeft, bodyRight)
        drawPitcherRim(frame, centerX, top, bodyRight - bodyLeft, participantAColor, participantBColor)
    }

    if (state.overflowRatio > 0f && frame.settlement == 0f) {
        drawVesselOverflow(state, frame, participantAColor, participantBColor, centerX, top)
    }
    if (settlementGlow > 0f) {
        drawCircle(
            Brush.radialGradient(listOf(Color.White.copy(alpha = settlementGlow * 0.42f), Color.Transparent)),
            radius = size.minDimension * (0.18f + settlementGlow * 0.34f),
            center = Offset(centerX, size.height * 0.52f)
        )
    }
}

private fun DrawScope.drawStudioLightShafts(colorA: Color, colorB: Color, frame: VesselFrame) {
    val breathe = 0.86f + sin(frame.ambient * PI.toFloat() * 2f) * 0.14f
    val leftBeam = frame.beamPathLeft.apply {
        rewind()
        moveTo(0f, size.height)
        lineTo(size.width * 0.34f, 0f)
        lineTo(size.width * 0.48f, 0f)
        lineTo(size.width * 0.17f, size.height)
        close()
    }
    val rightBeam = frame.beamPathRight.apply {
        rewind()
        moveTo(size.width, size.height)
        lineTo(size.width * 0.66f, 0f)
        lineTo(size.width * 0.52f, 0f)
        lineTo(size.width * 0.83f, size.height)
        close()
    }
    drawPath(leftBeam, Brush.verticalGradient(listOf(colorA.copy(alpha = 0.02f), colorA.copy(alpha = 0.11f * breathe))))
    drawPath(rightBeam, Brush.verticalGradient(listOf(colorB.copy(alpha = 0.02f), colorB.copy(alpha = 0.11f * breathe))))
}

private fun DrawScope.drawPitcherHandle(frame: VesselFrame, right: Float, top: Float, bottom: Float, accent: Color) {
    val height = bottom - top
    val handle = frame.handlePath.apply {
        rewind()
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
    frame: VesselFrame,
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
    val lip = frame.rimPath.apply {
        rewind()
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

private fun DrawScope.drawVesselDust(frame: VesselFrame) {
    val count = when (frame.quality) {
        ThemeRenderQuality.LOW -> 8
        ThemeRenderQuality.MEDIUM -> 18
        ThemeRenderQuality.HIGH -> 30
    }
    repeat(count) { index ->
        val x = size.width * ((pseudo(index, 0.19f) + frame.ambient * (0.012f + index % 3 * 0.004f)) % 1f)
        val y = size.height * pseudo(index, 0.81f)
        drawCircle(Color.White.copy(alpha = 0.06f + pseudo(index, 0.44f) * 0.12f), (0.6f + pseudo(index, 0.55f) * 1.3f).dp.toPx(), Offset(x, y))
    }
}

private fun buildVesselPath(path: Path, centerX: Float, top: Float, bottom: Float, left: Float, right: Float): Path {
    val width = right - left
    val neckHalf = width * 0.20f
    val height = bottom - top
    return path.apply {
        rewind()
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
    if (ratioA <= 0.001f) return Brush.horizontalGradient(listOf(colorB.copy(alpha = 0.62f), colorB.copy(alpha = 0.74f)), startX, endX)
    if (ratioA >= 0.999f) return Brush.horizontalGradient(listOf(colorA.copy(alpha = 0.62f), colorA.copy(alpha = 0.74f)), startX, endX)
    val boundary = ratioA.coerceIn(0.05f, 0.95f)
    val transition = 0.11f.coerceAtMost(boundary).coerceAtMost(1f - boundary)
    return Brush.horizontalGradient(
        0f to colorA.copy(alpha = 0.66f),
        (boundary - transition) to colorA.copy(alpha = 0.72f),
        boundary to lerp(colorA, colorB, 0.5f).copy(alpha = 0.70f),
        (boundary + transition) to colorB.copy(alpha = 0.72f),
        1f to colorB.copy(alpha = 0.66f),
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
    frame: VesselFrame,
    liquidTop: Float,
    left: Float,
    right: Float,
    impactWave: Float
) {
    val idleAmplitude = when (frame.quality) {
        ThemeRenderQuality.LOW -> 0.8.dp.toPx()
        ThemeRenderQuality.MEDIUM -> 1.5.dp.toPx()
        ThemeRenderQuality.HIGH -> 2.1.dp.toPx()
    }
    val surface = frame.surfacePath.apply { rewind() }
    val backSurface = frame.surfaceBackPath.apply { rewind() }
    val segments = if (frame.quality == ThemeRenderQuality.HIGH) 42 else 26
    for (index in 0..segments) {
        val t = index / segments.toFloat()
        val x = left + (right - left) * t
        val edgeDamping = sin(t * PI.toFloat()).coerceAtLeast(0f)
        val idle = sin(t * PI.toFloat() * 3.4f + frame.ambient * PI.toFloat() * 2f) * idleAmplitude
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
    frame: VesselFrame
) {
    val liquidHeight = (liquidBottom - liquidTop).coerceAtLeast(1f)
    val causticCount = when (frame.quality) {
        ThemeRenderQuality.LOW -> 2
        ThemeRenderQuality.MEDIUM -> 5
        ThemeRenderQuality.HIGH -> 9
    }
    repeat(causticCount) { index ->
        val baseY = liquidTop + liquidHeight * (0.16f + pseudo(index, 0.63f) * 0.72f)
        val phase = frame.ambient * PI.toFloat() * 2f + index * 0.83f
        val caustic = frame.scratchPath.apply {
            rewind()
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

    if (frame.quality != ThemeRenderQuality.LOW) {
        val bubbleCount = if (frame.quality == ThemeRenderQuality.HIGH) 22 else 10
        repeat(bubbleCount) { index ->
            val speed = 0.08f + pseudo(index, 0.48f) * 0.12f
            val rise = (pseudo(index, 0.74f) + frame.ambient * speed) % 1f
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
    frame: VesselFrame,
    centerX: Float,
    top: Float,
    bottom: Float,
    left: Float,
    right: Float
) {
    val leftHighlight = frame.scratchPath.apply {
        rewind()
        moveTo(centerX - (right - left) * 0.18f, top + 14.dp.toPx())
        cubicTo(left + 18.dp.toPx(), top + 72.dp.toPx(), left + 18.dp.toPx(), bottom - 72.dp.toPx(), left + 42.dp.toPx(), bottom - 28.dp.toPx())
    }
    drawPath(leftHighlight, Color.White.copy(alpha = 0.09f), style = Stroke(13.dp.toPx(), cap = StrokeCap.Round))
    drawPath(leftHighlight, Color.White.copy(alpha = 0.44f), style = Stroke(2.4.dp.toPx(), cap = StrokeCap.Round))
    val rightHighlight = frame.scratchPath.apply {
        rewind()
        moveTo(centerX + (right - left) * 0.20f, top + 20.dp.toPx())
        cubicTo(right - 13.dp.toPx(), top + 74.dp.toPx(), right - 14.dp.toPx(), bottom - 82.dp.toPx(), right - 34.dp.toPx(), bottom - 42.dp.toPx())
    }
    drawPath(rightHighlight, Color.White.copy(alpha = 0.2f), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
    val prism = listOf(Color(0xFF68E7FF), Color(0xFF9C7BFF), Color(0xFFFF6BCB))
    prism.forEachIndexed { index, color ->
        val prismPath = frame.scratchPath.apply {
            rewind()
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
    frame: VesselFrame,
    colorA: Color,
    colorB: Color,
    centerX: Float,
    vesselTop: Float,
    vesselBottom: Float,
    liquidSurfaceY: Float,
    cavityLeft: Float,
    cavityRight: Float
) {
    val state = frame.state ?: return
    val participantId = frame.contributingParticipantId ?: return
    val progress = frame.contribution
    val eventSeed = frame.eventSeed
    val quality = frame.quality
    val cavity = frame.vesselPath
    val fromLeft = participantId == state.participantA?.id
    val color = if (fromLeft) colorA else colorB
    val side = if (fromLeft) -1f else 1f
    val streamX = centerX + side * (cavityRight - cavityLeft) * 0.075f
    val originY = -size.height * 0.09f
    val impact = Offset(streamX, liquidSurfaceY.coerceIn(vesselTop + 18.dp.toPx(), vesselBottom))
    val head = smoothStep(0.01f, 0.34f, progress)
    val tail = smoothStep(0.68f, 0.94f, progress)
    val streamFade = (1f - smoothStep(0.79f, 1f, progress)).coerceIn(0f, 1f)
    if (head > tail && streamFade > 0f) {
        val segmentCount = if (quality == ThemeRenderQuality.HIGH) 34 else 24
        fun streamPoint(t: Float): Offset {
            val y = originY + (impact.y - originY) * t
            val taper = sin(t * PI.toFloat()).coerceAtLeast(0f)
            val wobble = sin(t * 22f + progress * 18f + eventSeed * 0.001f) * 1.35.dp.toPx() * taper
            return Offset(streamX + wobble, y)
        }
        var previous = streamPoint(tail)
        repeat(segmentCount) { index ->
            val normalized = (index + 1) / segmentCount.toFloat()
            val current = streamPoint(tail + (head - tail) * normalized)
            val body = sin(normalized * PI.toFloat()).coerceAtLeast(0f)
            val breakup = 0.91f + sin(index * 1.71f + progress * 19f) * 0.09f
            val width = (3.0f + body * 3.2f) * breakup * streamFade
            drawLine(color.copy(alpha = 0.10f * streamFade), previous, current, (width + 7f).dp.toPx(), StrokeCap.Round)
            drawLine(color.copy(alpha = 0.78f * streamFade), previous, current, width.dp.toPx(), StrokeCap.Round)
            drawLine(Color.White.copy(alpha = 0.26f * streamFade), previous + Offset(-width * 0.12f, 0f), current + Offset(-width * 0.12f, 0f), (width * 0.22f).dp.toPx(), StrokeCap.Round)
            previous = current
        }
    }

    if (progress > 0.29f) {
        val splashTime = ((progress - 0.29f) / 0.60f).coerceIn(0f, 1f)
        clipPath(cavity) {
            // Droplets are simulated by the controller (gravity + restitution);
            // here we only render the live pool.
            frame.droplets?.forEachActive { particle ->
                val fade = (1f - smoothStep(0.58f, 1f, particle.lifeProgress)).coerceIn(0f, 1f)
                drawWaterDroplet(
                    center = Offset(particle.x, particle.y),
                    velocity = Offset(particle.vx, particle.vy),
                    color = color,
                    radius = particle.size.dp.toPx(),
                    alpha = fade * (0.54f + pseudo(particle.seed, 0.46f) * 0.42f)
                )
            }
            repeat(3) { ring ->
                val ringProgress = (splashTime * 1.45f - ring * 0.17f).coerceIn(0f, 1f)
                if (ringProgress > 0f) {
                    val ringWidth = (24 + ring * 10).dp.toPx() * ringProgress
                    val ringHeight = (4.5f + ring * 1.2f).dp.toPx() * ringProgress
                    drawOval(
                        color = color.copy(alpha = (1f - ringProgress) * 0.46f),
                        topLeft = impact - Offset(ringWidth / 2f, ringHeight / 2f),
                        size = Size(ringWidth, ringHeight),
                        style = Stroke((1.4f - ring * 0.18f).dp.toPx())
                    )
                }
            }
            val wetEnvelope = sin(splashTime * PI.toFloat()).coerceAtLeast(0f)
            repeat(if (quality == ThemeRenderQuality.HIGH) 10 else 6) { index ->
                val onLeft = index % 2 == 0
                val wallX = if (onLeft) cavityLeft else cavityRight
                val inset = (5f + pseudo(index xor eventSeed, 0.28f) * 12f).dp.toPx()
                val x = wallX + if (onLeft) inset else -inset
                val startY = vesselTop + (34f + pseudo(index, 0.61f) * 96f).dp.toPx()
                val length = (18f + pseudo(index, 0.76f) * 48f).dp.toPx() * wetEnvelope
                val streak = frame.scratchPath.apply {
                    rewind()
                    moveTo(x, startY)
                    cubicTo(
                        x + (if (onLeft) 1f else -1f) * 5.dp.toPx(), startY + length * 0.30f,
                        x - (if (onLeft) 1f else -1f) * 3.dp.toPx(), startY + length * 0.70f,
                        x, startY + length
                    )
                }
                drawPath(streak, color.copy(alpha = 0.23f * wetEnvelope), style = Stroke(2.1.dp.toPx(), cap = StrokeCap.Round))
                drawPath(streak, Color.White.copy(alpha = 0.20f * wetEnvelope), style = Stroke(0.65.dp.toPx(), cap = StrokeCap.Round))
                drawCircle(color.copy(alpha = 0.52f * wetEnvelope), (1.2f + pseudo(index, 0.19f) * 1.6f).dp.toPx(), Offset(x, startY + length))
            }
        }
    }
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
    frame: VesselFrame,
    colorA: Color,
    colorB: Color,
    centerX: Float,
    top: Float
) {
    val total = state.participantAContributionMinor + state.participantBContributionMinor
    val mixRatio = if (total == 0L) 0.5f else state.participantBContributionMinor.toFloat() / total.toFloat()
    val overflowColor = lerp(colorA, colorB, mixRatio)
    val pulse = 0.5f + sin(frame.ambient * PI.toFloat() * 2f) * 0.5f
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
