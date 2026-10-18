package com.nomisma.app.ui.theme.scene

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

/** Deterministic pseudo-random in [0, 1] from an index and a salt. Allocation-free. */
internal fun pseudo(index: Int, salt: Float): Float {
    val value = sin(index * 12.9898f + salt * 78.233f) * 43_758.547f
    return abs(value - value.toInt()).coerceIn(0f, 1f)
}

internal fun String?.sceneColor(fallback: Color): Color = runCatching {
    Color(android.graphics.Color.parseColor(this ?: ""))
}.getOrDefault(fallback)

internal fun Color.lighten(amount: Float): Color = lerp(this, Color.White, amount.coerceIn(0f, 1f))

internal fun Color.darken(amount: Float): Color = lerp(this, Color.Black, amount.coerceIn(0f, 1f))

/** Draws [image] covering the whole canvas, center-cropped (like CSS background-size: cover). */
internal fun DrawScope.drawAssetCover(image: ImageBitmap, alpha: Float = 1f) {
    val imageRatio = image.width.toFloat() / image.height.toFloat()
    val targetRatio = size.width / size.height
    val source = if (imageRatio > targetRatio) {
        val sourceWidth = (image.height * targetRatio).roundToInt().coerceAtLeast(1)
        IntOffset((image.width - sourceWidth) / 2, 0) to IntSize(sourceWidth, image.height)
    } else {
        val sourceHeight = (image.width / targetRatio).roundToInt().coerceAtLeast(1)
        IntOffset(0, (image.height - sourceHeight) / 2) to IntSize(image.width, sourceHeight)
    }
    drawImage(
        image = image,
        srcOffset = source.first,
        srcSize = source.second,
        dstOffset = IntOffset.Zero,
        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
        alpha = alpha,
        filterQuality = FilterQuality.High
    )
}

internal fun DrawScope.drawAssetInRect(
    image: ImageBitmap,
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    alpha: Float = 1f
) {
    drawImage(
        image = image,
        dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
        dstSize = IntSize(width.roundToInt().coerceAtLeast(1), height.roundToInt().coerceAtLeast(1)),
        alpha = alpha,
        filterQuality = FilterQuality.High
    )
}
