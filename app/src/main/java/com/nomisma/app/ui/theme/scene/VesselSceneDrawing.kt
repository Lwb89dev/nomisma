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

    // Liquid and glass share the photo's rectangle. The cavity is the inner
    // wall of pitcher.webp, not a separate carafe silhouette.
    val viewportWidth = size.width
    val viewportHeight = size.height
    val placeLeft = VesselGeometry.pitcherLeft(viewportWidth)
    val placeTop = VesselGeometry.pitcherTop(viewportHeight)
    val placeWidth = VesselGeometry.pitcherWidth(viewportWidth)
    val placeHeight = VesselGeometry.pitcherHeight(viewportHeight)
    val cavity = VesselGeometry.buildCavity(frame.vesselPath, viewportWidth, viewportHeight)
    val surfaceV = VesselGeometry.surfaceV(fill)
    val (surfaceLeftU, surfaceRightU) = VesselGeometry.spanAtV(surfaceV)
    val liquidTop = VesselGeometry.canvasY(viewportHeight, surfaceV)
    val liquidBottom = VesselGeometry.canvasY(viewportHeight, VesselGeometry.FILL_BOTTOM_V)
    val surfaceLeft = VesselGeometry.canvasX(viewportWidth, surfaceLeftU)
    val surfaceRight = VesselGeometry.canvasX(viewportWidth, surfaceRightU)
    val (bellyLeftU, bellyRightU) = VesselGeometry.spanAtV(VesselGeometry.BELLY_V)
    val bellyLeft = VesselGeometry.canvasX(viewportWidth, bellyLeftU)
    val bellyRight = VesselGeometry.canvasX(viewportWidth, bellyRightU)
    val bodyCenterX = (surfaceLeft + surfaceRight) / 2f

    drawOval(
        Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.82f), Color.Transparent)),
        topLeft = Offset(VesselGeometry.canvasX(viewportWidth, 0.18f), VesselGeometry.canvasY(viewportHeight, 0.90f)),
        size = Size(placeWidth * 0.58f, placeHeight * 0.09f)
    )

    if (fill > 0f) {
        val total = state.participantAContributionMinor + state.participantBContributionMinor
        val ratioA = if (total <= 0L) 0.5f else state.participantAContributionMinor.toFloat() / total.toFloat()
        val liquidBrush = frame.materials?.liquid(
            width = viewportWidth,
            height = viewportHeight,
            time = frame.ambient,
            ratioA = ratioA,
            energy = contributionGlow,
            colorA = participantAColor,
            colorB = participantBColor
        ) ?: conservedLiquidBrush(participantAColor, participantBColor, ratioA, bellyLeft, bellyRight)
        val (detailLeftU, detailRightU) = VesselGeometry.spanAtV((surfaceV + VesselGeometry.FILL_BOTTOM_V) * 0.5f)
        clipPath(cavity) {
            drawRect(
                liquidBrush,
                Offset(placeLeft, liquidTop),
                Size(placeWidth, (liquidBottom - liquidTop).coerceAtLeast(1f))
            )
            drawLiquidVolumeDetail(
                liquidTop = liquidTop,
                liquidBottom = liquidBottom,
                left = VesselGeometry.canvasX(viewportWidth, detailLeftU),
                right = VesselGeometry.canvasX(viewportWidth, detailRightU),
                colorA = participantAColor,
                colorB = participantBColor,
                ratioA = ratioA,
                frame = frame
            )
            drawLiquidSurface(
                frame = frame,
                liquidTop = liquidTop,
                left = surfaceLeft,
                right = surfaceRight,
                impactWave = impactWave
            )
        }
    }

    if (frame.contribution > 0f) {
        val pourSide = if (frame.contributingParticipantId == state.participantA?.id) -1f else 1f
        val pourU = ((surfaceLeftU + surfaceRightU) / 2f + pourSide * (surfaceRightU - surfaceLeftU) * 0.12f)
            .coerceIn(surfaceLeftU, surfaceRightU)
        drawLiquidContribution(
            frame = frame,
            colorA = participantAColor,
            colorB = participantBColor,
            centerX = VesselGeometry.canvasX(viewportWidth, pourU),
            vesselTop = VesselGeometry.canvasY(viewportHeight, 0.24f),
            vesselBottom = liquidBottom,
            liquidSurfaceY = if (fill > 0.01f) liquidTop else liquidBottom,
            cavityLeft = bellyLeft,
            cavityRight = bellyRight
        )
    }

    val pitcher = frame.assets?.pitcher
    if (pitcher != null) {
        drawAssetInRect(
            image = pitcher,
            left = placeLeft,
            top = placeTop,
            width = placeWidth,
            height = placeHeight
        )
    } else {
        drawPath(cavity, Color(0xFFBBD6E5).copy(alpha = 0.13f), style = Stroke(13.dp.toPx(), join = StrokeJoin.Round))
        drawPath(
            cavity,
            Brush.horizontalGradient(
                0f to Color.White.copy(alpha = 0.75f),
                0.14f to participantAColor.copy(alpha = 0.34f),
                0.46f to Color.White.copy(alpha = 0.055f),
                0.78f to participantBColor.copy(alpha = 0.34f),
                1f to Color.White.copy(alpha = 0.72f),
                startX = bellyLeft,
                endX = bellyRight
            ),
            style = Stroke(2.3.dp.toPx(), join = StrokeJoin.Round)
        )
    }

    if (state.overflowRatio > 0f && frame.settlement == 0f) {
        drawVesselOverflow(
            state,
            frame,
            participantAColor,
            participantBColor,
            VesselGeometry.canvasX(viewportWidth, VesselGeometry.SPOUT_U),
            VesselGeometry.canvasY(viewportHeight, VesselGeometry.SPOUT_V)
        )
    }
    if (settlementGlow > 0f) {
        drawCircle(
            Brush.radialGradient(listOf(Color.White.copy(alpha = settlementGlow * 0.42f), Color.Transparent)),
            radius = size.minDimension * (0.18f + settlementGlow * 0.34f),
            center = Offset(bodyCenterX, VesselGeometry.canvasY(viewportHeight, 0.55f))
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
