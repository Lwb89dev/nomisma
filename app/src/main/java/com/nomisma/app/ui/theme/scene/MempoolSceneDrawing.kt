package com.nomisma.app.ui.theme.scene

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import com.nomisma.app.theme.ThemeFinancialState
import com.nomisma.app.theme.motion.ParticlePool
import com.nomisma.app.theme.motion.pulse
import com.nomisma.app.theme.motion.smoothStep
import com.nomisma.app.ui.theme.MinerAppearance
import com.nomisma.app.ui.theme.SceneMaterials
import com.nomisma.app.ui.theme.ThemeAssetPack
import com.nomisma.app.ui.theme.ThemeRenderQuality
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Samples kept from the falling block's trajectory to draw its motion trail. */
internal const val MEMPOOL_TRAIL_SAMPLES = 9

/**
 * Per-frame render model for the Mempool scene. Owned and mutated in place by
 * `MempoolSceneController` — never allocated per frame. Environment fields are
 * read-only configuration; the block fields are a snapshot of the simulated
 * `MotionBody`, and [sparks] is the controller's live particle pool.
 */
internal class MempoolFrame {
    var ambient = 0.25f
    var contribution = 0f
    var settlement = 0f
    var reducedMotion = false
    var quality = ThemeRenderQuality.MEDIUM
    var materials: SceneMaterials? = null
    var assets: ThemeAssetPack? = null
    var eventSeed = 0
    var contributingParticipantId: String? = null
    var participantAMiner = MinerAppearance.MAN
    var participantBMiner = MinerAppearance.WOMAN

    var blockActive = false
    var blockX = 0f
    var blockY = 0f
    var blockRotation = 0f
    /** 0..1 horizontal flight fraction; drives trail alpha and block scale. */
    var blockTravel = 0f
    /** Chronological trail samples; only the first [trailCount] entries are valid. */
    val trailX = FloatArray(MEMPOOL_TRAIL_SAMPLES)
    val trailY = FloatArray(MEMPOOL_TRAIL_SAMPLES)
    var trailCount = 0
    var sparks: ParticlePool? = null
}

internal fun DrawScope.drawMempoolScene(
    state: ThemeFinancialState,
    fill: Float,
    frame: MempoolFrame
) {
    val colorA = state.participantA?.colorHex.sceneColor(Color(0xFF38E66B))
    val colorB = state.participantB?.colorHex.sceneColor(Color(0xFF147DFF))
    val orange = Color(0xFFF7932E)
    val contributionReaction = sin(frame.contribution.coerceIn(0f, 1f) * PI).toFloat().coerceAtLeast(0f)
    val settlementEnergy = sin(frame.settlement.coerceIn(0f, 1f) * PI).toFloat().coerceAtLeast(0f)
    val settlementStrike = pulse(frame.settlement, 0.16f, 0.34f, 0.52f)

    val mineBackground = frame.assets?.background
    if (mineBackground != null) {
        drawAssetCover(mineBackground)
        drawRect(Color(0xFF02060A).copy(alpha = 0.16f))
    } else {
        val backdrop = frame.materials?.mempool(size.width, size.height, frame.ambient, settlementEnergy, colorA, colorB)
            ?: Brush.verticalGradient(listOf(Color(0xFF111B27), Color(0xFF080D14), Color(0xFF020407)))
        drawRect(backdrop)
        drawTechChamber(frame, colorA, colorB, orange)
        drawEnergyPylon(Offset(size.width * 0.14f, size.height * 0.31f), colorA, frame, settlementEnergy)
        drawEnergyPylon(Offset(size.width * 0.86f, size.height * 0.31f), colorB, frame, settlementEnergy)
    }

    val railY = size.height * 0.80f
    if (mineBackground == null) {
        drawLine(Brush.horizontalGradient(listOf(Color(0xFF20272E), Color(0xFFB4C4D1), Color(0xFF293039))), Offset(0f, railY), Offset(size.width, railY), 4.dp.toPx())
        drawLine(Brush.horizontalGradient(listOf(Color(0xFF151A1F), Color(0xFF788691), Color(0xFF171C21))), Offset(0f, railY + 17.dp.toPx()), Offset(size.width, railY + 17.dp.toPx()), 5.dp.toPx())
        repeat(12) { index ->
            val x = size.width * index / 11f
            drawLine(Color(0xFF47515B), Offset(x - 8.dp.toPx(), railY - 5.dp.toPx()), Offset(x + 9.dp.toPx(), railY + 23.dp.toPx()), 3.dp.toPx())
            drawCircle(orange.copy(alpha = 0.22f), 1.3.dp.toPx(), Offset(x, railY + 8.dp.toPx()))
        }
    }

    if (!drawMinerAsset(
        center = Offset(size.width * 0.16f, size.height * 0.61f),
        image = if (frame.participantAMiner == MinerAppearance.MAN) frame.assets?.minerLeft else frame.assets?.minerRight,
        facingRight = true,
        sourceFacesRight = frame.participantAMiner == MinerAppearance.MAN,
        accent = colorA,
        strike = maxOf(if (frame.contributingParticipantId == state.participantA?.id) contributionReaction else 0f, settlementStrike),
        frame = frame
    )) drawMiningOperator(
        center = Offset(size.width * 0.16f, size.height * 0.61f),
        facingRight = true,
        accent = colorA,
        strike = maxOf(if (frame.contributingParticipantId == state.participantA?.id) contributionReaction else 0f, settlementStrike),
        frame = frame
    )
    if (!drawMinerAsset(
        center = Offset(size.width * 0.84f, size.height * 0.61f),
        image = if (frame.participantBMiner == MinerAppearance.MAN) frame.assets?.minerLeft else frame.assets?.minerRight,
        facingRight = false,
        sourceFacesRight = frame.participantBMiner == MinerAppearance.MAN,
        accent = colorB,
        strike = if (frame.contributingParticipantId == state.participantB?.id) contributionReaction else settlementStrike * 0.38f,
        frame = frame
    )) drawMiningOperator(
        center = Offset(size.width * 0.84f, size.height * 0.61f),
        facingRight = false,
        accent = colorB,
        strike = if (frame.contributingParticipantId == state.participantB?.id) contributionReaction else settlementStrike * 0.38f,
        frame = frame
    )

    val cartLeft = size.width * 0.28f
    val cartTop = size.height * 0.45f
    val cartWidth = size.width * 0.44f
    val cartHeight = size.height * 0.32f
    drawMineCart(state, fill, frame, cartLeft, cartTop, cartWidth, cartHeight)

    if (frame.contribution > 0f || frame.blockActive) {
        drawMempoolContribution(state, frame, cartLeft, cartTop, cartWidth)
    }
    if (frame.settlement > 0f) drawMempoolSettlement(frame, orange, colorA, colorB, cartTop)
}

private fun DrawScope.drawMinerAsset(
    center: Offset,
    image: ImageBitmap?,
    facingRight: Boolean,
    sourceFacesRight: Boolean,
    accent: Color,
    strike: Float,
    frame: MempoolFrame
): Boolean {
    image ?: return false
    var targetHeight = size.height * 0.47f
    var targetWidth = targetHeight * image.width / image.height.toFloat()
    val maxWidth = size.width * 0.31f
    if (targetWidth > maxWidth) {
        val factor = maxWidth / targetWidth
        targetWidth *= factor
        targetHeight *= factor
    }
    val baseline = size.height * 0.82f - strike * 5.dp.toPx()
    drawCircle(
        Brush.radialGradient(listOf(accent.copy(alpha = 0.24f), Color.Transparent)),
        radius = targetHeight * 0.48f,
        center = Offset(center.x, baseline - targetHeight * 0.48f)
    )
    val left = center.x - targetWidth / 2f
    val top = baseline - targetHeight
    withTransform({
        if (facingRight != sourceFacesRight) {
            scale(-1f, 1f, Offset(center.x, baseline - targetHeight / 2f))
        }
        rotate((center.x / size.width - 0.5f) * strike * 5f, Offset(center.x, baseline))
    }) {
        drawAssetInRect(image, left, top, targetWidth, targetHeight)
    }
    if (strike > 0.55f && frame.quality != ThemeRenderQuality.LOW) {
        drawCircle(Color(0xFFFFD59A).copy(alpha = strike * 0.55f), 3.dp.toPx(), Offset(center.x, baseline - targetHeight * 0.18f))
    }
    return true
}

private fun DrawScope.drawTechChamber(frame: MempoolFrame, colorA: Color, colorB: Color, orange: Color) {
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

    repeat(if (frame.quality == ThemeRenderQuality.HIGH) 28 else 15) { index ->
        val x = size.width * ((pseudo(index, 0.31f) + frame.ambient * (0.012f + index % 4 * 0.003f)) % 1f)
        val y = size.height * (0.18f + pseudo(index, 0.67f) * 0.56f)
        drawCircle(Color(0xFFBFD8E7).copy(alpha = 0.06f + pseudo(index, 0.48f) * 0.10f), (0.65f + index % 3 * 0.4f).dp.toPx(), Offset(x, y))
    }

    val horizon = size.height * 0.43f
    drawLine(Brush.horizontalGradient(listOf(Color.Transparent, orange.copy(alpha = 0.30f), Color.Transparent)), Offset(0f, horizon), Offset(size.width, horizon), 1.dp.toPx())
}

private fun DrawScope.drawEnergyPylon(
    center: Offset,
    accent: Color,
    frame: MempoolFrame,
    settlementEnergy: Float
) {
    val pulse = 0.78f + sin(frame.ambient * PI.toFloat() * 2f + center.x * 0.02f) * 0.22f
    val glow = 0.18f + pulse * 0.10f + settlementEnergy * 0.22f
    drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = glow), accent.copy(alpha = 0.035f), Color.Transparent)), 74.dp.toPx(), center)
    val frameOutline = Path().apply {
        moveTo(center.x - 11.dp.toPx(), center.y + 18.dp.toPx())
        lineTo(center.x - 8.dp.toPx(), center.y - 17.dp.toPx())
        lineTo(center.x, center.y - 25.dp.toPx())
        lineTo(center.x + 8.dp.toPx(), center.y - 17.dp.toPx())
        lineTo(center.x + 11.dp.toPx(), center.y + 18.dp.toPx())
        close()
    }
    drawPath(frameOutline, Brush.horizontalGradient(listOf(Color(0xFF27323C), Color(0xFFB7C8D3), Color(0xFF202A33))))
    drawPath(frameOutline, accent.copy(alpha = 0.48f), style = Stroke(1.2.dp.toPx(), join = StrokeJoin.Round))
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
    frame: MempoolFrame
) {
    val direction = if (facingRight) 1f else -1f
    val scale = size.minDimension / 390f
    val breath = if (frame.reducedMotion) 0f else {
        sin(frame.ambient * PI.toFloat() * 2f + center.x * 0.017f) * 0.7f * scale
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
        pulse(frame.contribution, 0.72f, 0.84f, 0.98f),
        pulse(frame.settlement, 0.22f, 0.36f, 0.53f)
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
    frame: MempoolFrame,
    left: Float,
    top: Float,
    width: Float,
    height: Float
) {
    val settleEnvelope = sin(frame.settlement.coerceIn(0f, 1f) * PI.toFloat()).coerceAtLeast(0f)
    val shake = if (frame.reducedMotion) 0f else {
        sin(frame.settlement * PI.toFloat() * 12f) * settleEnvelope * 3.2.dp.toPx()
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

    val cartAsset = frame.assets?.mineCart
    if (cartAsset != null) {
        drawAssetInRect(cartAsset, left + shake, top, width, height)
        // Opening of cart.webp: the bin mouth, not the front plate.
        val mouthLeft = left + width * 0.18f + shake
        val mouthRight = left + width * 0.82f + shake
        val mouthTop = top + height * 0.09f
        val mouthBottom = top + height * 0.19f
        val cavity = Path().apply {
            moveTo(mouthLeft, mouthTop)
            lineTo(mouthRight, mouthTop)
            lineTo(left + width * 0.86f + shake, mouthBottom)
            lineTo(left + width * 0.14f + shake, mouthBottom)
            close()
        }
        val maxBlocks = when (frame.quality) {
            ThemeRenderQuality.LOW -> 8
            ThemeRenderQuality.MEDIUM -> 15
            ThemeRenderQuality.HIGH -> 24
        }
        val visible = (maxBlocks * fill).roundToInt().coerceIn(0, maxBlocks)
        val total = state.participantAContributionMinor + state.participantBContributionMinor
        val ratioA = if (total == 0L) 0.5f else state.participantAContributionMinor.toFloat() / total.toFloat()
        val columns = 6
        val blockWidth = (mouthRight - mouthLeft) / columns * 0.78f
        val blockHeight = (mouthBottom - mouthTop) / 2.2f
        clipPath(cavity) {
            repeat(visible) { index ->
                val row = index / columns
                val column = index % columns
                val x = mouthLeft + (mouthRight - mouthLeft) * ((column + 0.5f) / columns) - blockWidth / 2f
                val y = mouthBottom - (row + 1) * blockHeight * 0.92f
                val ownerColor = if (index.toFloat() / maxBlocks < ratioA) {
                    state.participantA?.colorHex.sceneColor(Color(0xFF38E66B))
                } else {
                    state.participantB?.colorHex.sceneColor(Color(0xFF147DFF))
                }
                drawRoundRect(
                    Brush.linearGradient(
                        listOf(Color(0xFFFFD18A), orange, Color(0xFF713316), ownerColor.copy(alpha = 0.78f)),
                        Offset(x, y),
                        Offset(x + blockWidth, y + blockHeight)
                    ),
                    Offset(x, y),
                    Size(blockWidth, blockHeight),
                    CornerRadius(1.4.dp.toPx())
                )
                drawLine(Color.White.copy(alpha = 0.34f), Offset(x + 1.5.dp.toPx(), y + 1.5.dp.toPx()), Offset(x + blockWidth * 0.64f, y + 1.5.dp.toPx()), 0.6.dp.toPx())
            }
        }
        return
    }

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

    val maxBlocks = when (frame.quality) {
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

/**
 * Contribution flight, upgraded from the old predetermined PathMeasure Bézier:
 * the block position/rotation/trail come from the controller's `MotionBody`
 * simulation (gravity, restitution bounce on the cart bed), and the impact
 * sparks are live `ParticlePool` particles instead of a parametric formula.
 * The dashed guide arc, impact ring, colors and anchors are unchanged.
 */
private fun DrawScope.drawMempoolContribution(
    state: ThemeFinancialState,
    frame: MempoolFrame,
    cartLeft: Float,
    cartTop: Float,
    cartWidth: Float
) {
    val participantId = frame.contributingParticipantId ?: return
    val fromLeft = participantId == state.participantA?.id
    val accent = if (fromLeft) {
        state.participantA?.colorHex.sceneColor(Color(0xFF38E66B))
    } else {
        state.participantB?.colorHex.sceneColor(Color(0xFF147DFF))
    }
    val orange = Color(0xFFF7932E)
    val progress = frame.contribution
    val travel = frame.blockTravel.coerceIn(0f, 1f)
    val origin = Offset(if (fromLeft) size.width * 0.22f else size.width * 0.78f, size.height * 0.47f)
    val target = Offset(cartLeft + cartWidth / 2f, cartTop + 5.dp.toPx())
    val path = Path().apply {
        moveTo(origin.x, origin.y)
        quadraticTo(size.width * 0.5f, size.height * 0.25f, target.x, target.y)
    }
    val scale = 1f + sin(travel * PI).toFloat() * 0.35f

    val trailSamples = frame.trailCount
    for (sample in 0 until trailSamples) {
        val age = trailSamples - 1 - sample
        val alpha = (1f - age / 9f) * (0.12f + travel * 0.42f)
        val radius = (5f - age * 0.38f).coerceAtLeast(1.1f).dp.toPx()
        drawCircle(
            Brush.radialGradient(listOf(accent.copy(alpha = alpha), orange.copy(alpha = alpha * 0.58f), Color.Transparent)),
            radius,
            Offset(frame.trailX[sample], frame.trailY[sample])
        )
    }
    drawPath(path, accent.copy(alpha = 0.10f * sin(travel * PI).toFloat()), style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 8.dp.toPx()), travel * 28.dp.toPx())))

    if (frame.blockActive) {
        val position = Offset(frame.blockX, frame.blockY)
        rotate(frame.blockRotation, position) {
            drawCircle(
                Brush.radialGradient(listOf(accent.copy(alpha = 0.34f), orange.copy(alpha = 0.15f), Color.Transparent)),
                21.dp.toPx() * scale,
                position
            )
            drawRoundRect(
                Brush.linearGradient(listOf(Color(0xFFFFD78C), orange, Color(0xFF8F3517), accent.copy(alpha = 0.88f))),
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
    }
    if (progress > 0.68f) {
        val impact = ((progress - 0.68f) / 0.32f).coerceIn(0f, 1f)
        drawCircle(
            orange.copy(alpha = (1f - impact) * 0.58f),
            (5f + impact * 28f).dp.toPx(),
            target,
            style = Stroke((2f - impact).coerceAtLeast(0.6f).dp.toPx())
        )
    }
    frame.sparks?.forEachActive { particle ->
        val sparkColor = if (particle.seed % 3 == 0) accent else orange
        drawLine(
            sparkColor.copy(alpha = 1f - particle.lifeProgress),
            Offset(particle.x - particle.vx * 0.09f, particle.y - particle.vy * 0.09f),
            Offset(particle.x, particle.y),
            particle.size.dp.toPx(),
            StrokeCap.Round
        )
    }
}

private fun DrawScope.drawMempoolSettlement(
    frame: MempoolFrame,
    orange: Color,
    colorA: Color,
    colorB: Color,
    cartTop: Float
) {
    val progress = frame.settlement.coerceIn(0f, 1f)
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
            val angle = index / 12f * PI.toFloat() * 2f + frame.ambient * 0.65f
            val distance = (30f + (1f - charge) * 48f + pseudo(index xor frame.eventSeed, 0.37f) * 16f).dp.toPx()
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
            val seededIndex = index xor frame.eventSeed
            val angle = pseudo(seededIndex, 0.14f) * PI.toFloat() * 2f
            val speed = (42f + pseudo(seededIndex, 0.71f) * 130f).dp.toPx()
            val gravity = 105f.dp.toPx()
            val t = release
            val originOffset = Offset((pseudo(seededIndex, 0.44f) - 0.5f) * 28.dp.toPx(), 0f)
            val particle = center + originOffset + Offset(cos(angle) * speed * t, sin(angle) * speed * t + gravity * t * t)
            val previous = center + originOffset + Offset(cos(angle) * speed * (t - 0.045f).coerceAtLeast(0f), sin(angle) * speed * (t - 0.045f).coerceAtLeast(0f) + gravity * (t - 0.045f).coerceAtLeast(0f) * (t - 0.045f).coerceAtLeast(0f))
            val particleColor = when (index % 3) {
                0 -> colorA
                1 -> colorB
                else -> orange
            }
            drawLine(particleColor.copy(alpha = fade * (0.45f + pseudo(seededIndex, 0.19f) * 0.45f)), previous, particle, (0.7f + index % 3 * 0.45f).dp.toPx(), StrokeCap.Round)
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
