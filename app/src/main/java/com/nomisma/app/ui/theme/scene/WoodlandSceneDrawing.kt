package com.nomisma.app.ui.theme.scene

import androidx.compose.ui.geometry.CornerRadius
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
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import com.nomisma.app.theme.ThemeFinancialState
import com.nomisma.app.theme.motion.ParticlePool
import com.nomisma.app.ui.theme.SceneMaterials
import com.nomisma.app.ui.theme.ThemeAssetPack
import com.nomisma.app.ui.theme.ThemeRenderQuality
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Everything the Woodland drawing code needs for one frame. Owned and reused by
 * [WoodlandSceneController] (mutated before every render, never allocated per frame).
 */
internal class WoodlandFrame {
    var ambient = 0f
    var contribution = 0f
    var settlement = 0f
    var reducedMotion = false
    var quality = ThemeRenderQuality.MEDIUM
    var materials: SceneMaterials? = null
    var assets: ThemeAssetPack? = null
    var eventSeed = 0
    var contributingParticipantId: String? = null

    /** 0..1 approach of each participant's animal toward the container. */
    var travelA = 0f
    var travelB = 0f

    /** Impact sparks, exposed only while at least one is alive. */
    var sparks: ParticlePool? = null

    val tokens = Array(WoodlandSceneController.TOKEN_COUNT) { WoodlandTokenRender() }
}

/** Render snapshot of one flying token (physics lives in the controller). */
internal class WoodlandTokenRender {
    var visible = false
    var isWalnut = true
    var x = 0f
    var y = 0f
    var scale = 1f
    var rotation = 0f
}

internal fun DrawScope.drawWoodlandScene(
    state: ThemeFinancialState,
    fill: Float,
    frame: WoodlandFrame
) {
    val colorA = state.participantA?.colorHex.sceneColor(Color(0xFF77C96B))
    val colorB = state.participantB?.colorHex.sceneColor(Color(0xFF6BA9E8))
    val contributionReaction = sin(frame.contribution.coerceIn(0f, 1f) * PI).toFloat().coerceAtLeast(0f)
    val settlementFlash = sin(frame.settlement.coerceIn(0f, 1f) * PI).toFloat().coerceAtLeast(0f)

    val woodlandBackground = frame.assets?.background
    if (woodlandBackground != null) {
        drawAssetCover(woodlandBackground)
        drawRect(Color(0xFF061109).copy(alpha = 0.13f))
    } else {
        val backdrop = frame.materials?.woodland(size.width, size.height, frame.ambient, colorA, colorB)
            ?: Brush.verticalGradient(listOf(Color(0xFF1A3022), Color(0xFF0B1710), Color(0xFF050906)))
        drawRect(backdrop)
    }
    val sunCenter = Offset(size.width * 0.5f, size.height * 0.06f)
    drawCircle(Brush.radialGradient(listOf(Color(0xFFFFE0A1).copy(alpha = 0.30f), Color(0xFFFFB85C).copy(alpha = 0.07f), Color.Transparent)), size.width * 0.46f, sunCenter)
    if (woodlandBackground == null) drawForestLayers(frame)

    val groundTop = size.height * 0.72f
    if (woodlandBackground == null) {
        drawOval(Color(0xFF090C08), Offset(-size.width * 0.08f, groundTop + 6.dp.toPx()), Size(size.width * 1.16f, size.height * 0.32f))
        drawOval(
            Brush.verticalGradient(listOf(Color(0xFF3E633C), Color(0xFF1A2A18), Color(0xFF0A100A))),
            Offset(-size.width * 0.04f, groundTop - 8.dp.toPx()),
            Size(size.width * 1.08f, size.height * 0.25f)
        )
        drawMossAndForestFloor(groundTop, frame)
    }

    val chestLeft = size.width * 0.24f
    val chestTop = size.height * 0.45f
    val chestWidth = size.width * 0.52f
    val chestHeight = size.height * 0.34f
    drawWoodenContainer(state, fill, frame, chestLeft, chestTop, chestWidth, chestHeight)

    val travelShift = size.width * 0.10f
    val centerA = Offset(
        size.width * 0.16f + frame.travelA.coerceIn(0f, 1f) * travelShift,
        size.height * 0.61f - contributionReaction * 5.dp.toPx()
    )
    if (!drawWoodlandAssetCharacter(
        center = centerA,
        identityId = state.participantA?.identityId ?: "bear",
        participantColor = colorA,
        facingRight = true,
        reaction = if (frame.contributingParticipantId == state.participantA?.id) contributionReaction else 0f,
        frame = frame
    )) drawWoodlandCharacter(
        center = centerA,
        identityId = state.participantA?.identityId ?: "bear",
        participantColor = colorA,
        facingRight = true,
        reaction = if (frame.contributingParticipantId == state.participantA?.id) contributionReaction else 0f,
        frame = frame
    )
    val centerB = Offset(
        size.width * 0.84f - frame.travelB.coerceIn(0f, 1f) * travelShift,
        size.height * 0.61f - contributionReaction * 5.dp.toPx()
    )
    if (!drawWoodlandAssetCharacter(
        center = centerB,
        identityId = state.participantB?.identityId ?: "squirrel",
        participantColor = colorB,
        facingRight = false,
        reaction = if (frame.contributingParticipantId == state.participantB?.id) contributionReaction else 0f,
        frame = frame
    )) drawWoodlandCharacter(
        center = centerB,
        identityId = state.participantB?.identityId ?: "squirrel",
        participantColor = colorB,
        facingRight = false,
        reaction = if (frame.contributingParticipantId == state.participantB?.id) contributionReaction else 0f,
        frame = frame
    )

    drawWoodlandContribution(frame)
    if (settlementFlash > 0f) {
        repeat(if (frame.quality == ThemeRenderQuality.HIGH) 18 else 10) { index ->
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

private fun DrawScope.drawWoodlandAssetCharacter(
    center: Offset,
    identityId: String,
    participantColor: Color,
    facingRight: Boolean,
    reaction: Float,
    frame: WoodlandFrame
): Boolean {
    val image = frame.assets?.woodlandCharacters?.get(identityId) ?: return false
    val heightRatio = when (identityId) {
        "bear" -> 0.34f
        "hedgehog" -> 0.22f
        "fox", "raccoon" -> 0.29f
        else -> 0.28f
    }
    var targetHeight = size.height * heightRatio
    var targetWidth = targetHeight * image.width / image.height.toFloat()
    val maxWidth = size.width * 0.29f
    if (targetWidth > maxWidth) {
        val factor = maxWidth / targetWidth
        targetWidth *= factor
        targetHeight *= factor
    }
    val visualCenter = Offset(center.x, size.height * 0.62f)
    drawCircle(
        Brush.radialGradient(listOf(participantColor.copy(alpha = 0.26f), Color.Transparent)),
        radius = targetWidth.coerceAtLeast(targetHeight) * 0.72f,
        center = visualCenter
    )
    val baseline = size.height * 0.78f - reaction * 6.dp.toPx()
    val left = center.x - targetWidth / 2f
    val top = baseline - targetHeight
    withTransform({
        if (!facingRight) scale(-1f, 1f, Offset(center.x, baseline - targetHeight / 2f))
        rotate((if (facingRight) 1f else -1f) * reaction * 3.2f, Offset(center.x, baseline))
    }) {
        drawAssetInRect(image, left, top, targetWidth, targetHeight)
    }
    return true
}

private fun DrawScope.drawForestLayers(frame: WoodlandFrame) {
    repeat(10) { index ->
        val x = size.width * (index / 9f) + (pseudo(index, 0.18f) - 0.5f) * 16.dp.toPx()
        val sway = sin((frame.ambient * 2f + index * 0.27f) * PI.toFloat()) * 1.7.dp.toPx()
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
    repeat(if (frame.quality == ThemeRenderQuality.HIGH) 34 else 18) { index ->
        val x = size.width * ((pseudo(index, 0.14f) + frame.ambient * (0.018f + index % 3 * 0.004f)) % 1f)
        val y = size.height * (0.16f + pseudo(index, 0.68f) * 0.56f)
        val warm = index % 5 == 0
        val pulse = 0.68f + sin(frame.ambient * PI.toFloat() * 2f + index) * 0.32f
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

private fun DrawScope.drawMossAndForestFloor(groundTop: Float, frame: WoodlandFrame) {
    val bladeCount = when (frame.quality) {
        ThemeRenderQuality.LOW -> 18
        ThemeRenderQuality.MEDIUM -> 34
        ThemeRenderQuality.HIGH -> 52
    }
    repeat(bladeCount) { index ->
        val x = size.width * pseudo(index, 0.24f)
        val baseY = groundTop + (pseudo(index, 0.72f) - 0.25f) * 30.dp.toPx()
        val length = (3f + pseudo(index, 0.49f) * 8f).dp.toPx()
        val sway = sin(frame.ambient * PI.toFloat() * 2f + index * 0.71f) * 2.dp.toPx()
        drawLine(
            Color(0xFF82A968).copy(alpha = 0.16f + pseudo(index, 0.18f) * 0.24f),
            Offset(x, baseY),
            Offset(x + sway, baseY - length),
            (0.6f + pseudo(index, 0.91f) * 0.7f).dp.toPx(),
            StrokeCap.Round
        )
    }
    repeat(if (frame.quality == ThemeRenderQuality.HIGH) 15 else 8) { index ->
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
    frame: WoodlandFrame,
    left: Float,
    top: Float,
    width: Float,
    height: Float
) {
    drawOval(
        Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.72f), Color.Transparent)),
        Offset(left - width * 0.10f, top + height * 0.76f),
        Size(width * 1.20f, height * 0.30f)
    )
    val logAsset = frame.assets?.woodlandContainer
    if (logAsset != null) {
        drawAssetInRect(logAsset, left, top, width, height)
    } else {
        drawRoundRect(
            Brush.horizontalGradient(
                listOf(Color(0xFF24150F), Color(0xFF704426), Color(0xFF9A673A), Color(0xFF3B2115)),
                left,
                left + width
            ),
            Offset(left, top + height * 0.12f),
            Size(width, height * 0.76f),
            CornerRadius(height * 0.28f)
        )
    }

    val window = Path().apply {
        moveTo(left + width * 0.31f, top + height * 0.39f)
        quadraticTo(left + width * 0.50f, top + height * 0.34f, left + width * 0.69f, top + height * 0.39f)
        lineTo(left + width * 0.67f, top + height * 0.70f)
        quadraticTo(left + width * 0.50f, top + height * 0.74f, left + width * 0.33f, top + height * 0.70f)
        close()
    }
    drawPath(window, Brush.verticalGradient(listOf(Color(0xFF050704), Color(0xFF17140D))))

    val maxObjects = when (frame.quality) {
        ThemeRenderQuality.LOW -> 12
        ThemeRenderQuality.MEDIUM -> 22
        ThemeRenderQuality.HIGH -> 34
    }
    val visible = (maxObjects * fill).roundToInt().coerceIn(0, maxObjects)
    val total = state.participantAContributionMinor + state.participantBContributionMinor
    val ratioA = if (total == 0L) 0.5f else state.participantAContributionMinor.toFloat() / total.toFloat()
    clipPath(window) {
        repeat(visible) { index ->
            val ownerA = index.toFloat() / maxObjects < ratioA
            val columns = 7
            val row = index / columns
            val column = index % columns
            val x = left + width * 0.355f + column * width * 0.047f + (pseudo(index, 0.32f) - 0.5f) * 5.dp.toPx()
            val y = top + height * 0.68f - row * 8.dp.toPx() - pseudo(index, 0.88f) * 2.dp.toPx()
            drawWoodlandToken(ownerA, Offset(x, y), 0.68f, index * 19f)
        }
        drawRect(
            Brush.linearGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent, Color.White.copy(alpha = 0.025f))),
            Offset(left + width * 0.31f, top + height * 0.36f),
            Size(width * 0.38f, height * 0.39f)
        )
    }
    drawPath(window, Color(0xFF21150D).copy(alpha = 0.88f), style = Stroke(3.6.dp.toPx(), join = StrokeJoin.Round))
    drawPath(window, Color.White.copy(alpha = 0.17f), style = Stroke(0.8.dp.toPx(), join = StrokeJoin.Round))
}

private fun DrawScope.drawWoodlandCharacter(
    center: Offset,
    identityId: String,
    participantColor: Color,
    facingRight: Boolean,
    reaction: Float,
    frame: WoodlandFrame
) {
    val direction = if (facingRight) 1f else -1f
    val palette = woodlandPalette(identityId)
    val scale = size.minDimension / 390f
    val breathing = if (frame.reducedMotion) 0f else {
        sin(frame.ambient * PI.toFloat() * 2f + center.x * 0.013f) * 0.75f * scale
    }
    val anchor = center + Offset(0f, breathing - reaction * 2.4f * scale)
    drawOval(
        Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.56f), Color.Transparent)),
        anchor + Offset(-31f * scale, 26f * scale),
        Size(62f * scale, 11f * scale)
    )
    when (identityId) {
        "rabbit" -> drawNaturalRabbit(anchor, palette, participantColor, direction, reaction, frame, scale)
        "hedgehog" -> drawNaturalHedgehog(anchor, palette, participantColor, direction, reaction, frame, scale)
        "owl" -> drawNaturalOwl(anchor, palette, participantColor, direction, reaction, frame, scale)
        else -> drawNaturalQuadruped(anchor, identityId, palette, participantColor, direction, reaction, frame, scale)
    }
}

private class WoodlandAnimalProfile(
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
    frame: WoodlandFrame,
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
    drawNaturalTail(bodyCenter, identityId, palette, direction, frame, scale)

    val gait = if (frame.reducedMotion) 0f else sin(frame.ambient * PI.toFloat() * 4f + anchor.x * 0.02f) * 0.8f * scale
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
    drawAnimalEye(headCenter + Offset(direction * 5.2f * scale, -4.3f * scale), direction, frame, scale)

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

    if (frame.quality != ThemeRenderQuality.LOW) {
        drawFurDetail(bodyCenter, headCenter, palette, direction, scale, frame.quality)
    }
}

private fun DrawScope.drawNaturalTail(
    bodyCenter: Offset,
    identityId: String,
    palette: Pair<Color, Color>,
    direction: Float,
    frame: WoodlandFrame,
    scale: Float
) {
    val sway = if (frame.reducedMotion) 0f else sin(frame.ambient * PI.toFloat() * 2f + bodyCenter.x * 0.03f) * 3f * scale
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
    frame: WoodlandFrame,
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
    drawAnimalEye(headCenter + Offset(direction * 5f * scale, -4f * scale), direction, frame, scale)
    drawOval(palette.first.darken(0.24f), bodyCenter + Offset(direction * 6f * scale, 11f * scale), Size(15f * scale, 5f * scale))
    drawLine(participantColor.copy(alpha = 0.92f), headCenter + Offset(-direction * 8f * scale, 6f * scale), headCenter + Offset(-direction * 2f * scale, 9f * scale), 2.2f * scale, StrokeCap.Round)
}

private fun DrawScope.drawNaturalHedgehog(
    anchor: Offset,
    palette: Pair<Color, Color>,
    participantColor: Color,
    direction: Float,
    reaction: Float,
    frame: WoodlandFrame,
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
    drawAnimalEye(bodyCenter + Offset(direction * 12f * scale, -4f * scale), direction, frame, scale)
    drawLine(participantColor.copy(alpha = 0.9f), bodyCenter - Offset(direction * 2f * scale, 9f * scale), bodyCenter + Offset(-direction * 5f * scale, 8f * scale), 2f * scale)
}

private fun DrawScope.drawNaturalOwl(
    anchor: Offset,
    palette: Pair<Color, Color>,
    participantColor: Color,
    direction: Float,
    reaction: Float,
    frame: WoodlandFrame,
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
    val eyeOpen = animalEyeOpen(frame, headCenter.x)
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

private fun animalEyeOpen(frame: WoodlandFrame, seed: Float): Float {
    if (frame.reducedMotion) return 1f
    val phase = (frame.ambient + pseudo(seed.roundToInt(), 0.64f) * 0.37f) % 1f
    val blinkDistance = abs(phase - 0.52f)
    return (blinkDistance / 0.018f).coerceIn(0.08f, 1f)
}

private fun DrawScope.drawAnimalEye(center: Offset, direction: Float, frame: WoodlandFrame, scale: Float) {
    val open = animalEyeOpen(frame, center.x)
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
    frame: WoodlandFrame
) {
    val sway = if (frame.reducedMotion) 0f else sin(frame.ambient * PI.toFloat() * 2f + center.x) * 3f * scale
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

private fun DrawScope.drawWoodlandToken(
    isWalnut: Boolean,
    center: Offset,
    scale: Float,
    rotationDegrees: Float = 0f
) {
    rotate(rotationDegrees, center) {
        if (isWalnut) {
            val radius = 5.dp.toPx() * scale
            drawOval(
                Brush.linearGradient(listOf(Color(0xFFE0AD68), Color(0xFF9A6133), Color(0xFF4A2A18))),
                center - Offset(radius, radius * 0.82f),
                Size(radius * 2f, radius * 1.64f)
            )
            drawPath(
                Path().apply {
                    moveTo(center.x, center.y - radius * 0.72f)
                    cubicTo(center.x - radius * 0.24f, center.y - radius * 0.30f, center.x + radius * 0.22f, center.y + radius * 0.25f, center.x, center.y + radius * 0.72f)
                },
                Color(0xFF422515).copy(alpha = 0.78f),
                style = Stroke(0.85.dp.toPx() * scale, cap = StrokeCap.Round)
            )
            drawArc(Color.White.copy(alpha = 0.20f), 198f, 86f, false, center - Offset(radius * 0.72f, radius * 0.52f), Size(radius * 1.28f, radius), style = Stroke(0.7.dp.toPx() * scale))
        } else {
            val radius = 4.7.dp.toPx() * scale
            drawOval(
                Brush.linearGradient(listOf(Color(0xFFD99A52), Color(0xFF9B5429), Color(0xFF552A18))),
                center - Offset(radius * 0.74f, radius),
                Size(radius * 1.48f, radius * 2f)
            )
            drawArc(
                Brush.verticalGradient(listOf(Color(0xFF604128), Color(0xFF2B2117))),
                180f,
                180f,
                true,
                center - Offset(radius * 0.82f, radius * 1.12f),
                Size(radius * 1.64f, radius * 0.88f)
            )
            drawLine(Color(0xFF392416), center - Offset(0f, radius * 1.08f), center - Offset(-radius * 0.16f, radius * 1.48f), 0.9.dp.toPx() * scale, StrokeCap.Round)
            drawOval(Color.White.copy(alpha = 0.22f), center - Offset(radius * 0.42f, radius * 0.62f), Size(radius * 0.22f, radius * 0.60f))
        }
    }
}

/**
 * Draws the physics-driven contribution tokens (positions come from the
 * controller's [com.nomisma.app.theme.motion.MotionBody] instances) and the
 * landing spark burst (a [ParticlePool]).
 */
private fun DrawScope.drawWoodlandContribution(frame: WoodlandFrame) {
    for (token in frame.tokens) {
        if (!token.visible) continue
        drawWoodlandToken(
            isWalnut = token.isWalnut,
            center = Offset(token.x, token.y),
            scale = token.scale,
            rotationDegrees = token.rotation
        )
    }
    frame.sparks?.forEachActive { spark ->
        drawCircle(
            Color(0xFFFFD790).copy(alpha = 1f - spark.lifeProgress),
            spark.size,
            Offset(spark.x, spark.y)
        )
    }
}
