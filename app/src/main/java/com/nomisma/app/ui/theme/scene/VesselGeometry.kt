package com.nomisma.app.ui.theme.scene

import androidx.compose.ui.graphics.Path

/**
 * Maps the vessel photo and the liquid cavity into the same rectangle.
 *
 * The pitcher bitmap is drawn at [PITCHER_LEFT]/[PITCHER_TOP] with size
 * [PITCHER_WIDTH] x [PITCHER_HEIGHT] of the scene. [CAVITY_UV] is the inner
 * glass wall in that bitmap's own 0..1 space (spout on the left, handle
 * excluded), so the liquid stays inside the glass instead of a separate
 * procedural carafe.
 */
internal object VesselGeometry {
    const val PITCHER_LEFT = 0.01f
    const val PITCHER_TOP = 0.055f
    const val PITCHER_WIDTH = 0.91f
    const val PITCHER_HEIGHT = 0.86f

    /** Neck opening: a full vessel reaches just under the lip. */
    const val FILL_TOP_V = 0.145f

    /** Inner floor, above the thick glass base. */
    const val FILL_BOTTOM_V = 0.885f

    /** Widest inner cross-section, used for splashes that travel the belly. */
    const val BELLY_V = 0.66f

    /**
     * Spout lip on pitcher.webp (the leftmost glass around v=0.11).
     * Overflow must leave from here and fall through the transparent
     * margin; the old point (0.26, 0.10) sat on the mouth and painted
     * streaks across the neck.
     */
    const val SPOUT_U = 0.20f
    const val SPOUT_V = 0.11f

    /** Bitmap-u the falling drop drifts left, per unit of fall progress. */
    const val DROP_DRIFT_U = 0.045f

    /** Bitmap-v the drop falls. Ends near v=0.39, still left of the belly. */
    const val DROP_FALL_V = 0.28f

    /**
     * A surface above this v is in the opaque neck. An interior splash
     * there is clipped to nothing, so the pour leaves by the spout instead.
     */
    const val NECK_V = 0.19f

    private const val LIP_ROOM_V = 0.05f

    /**
     * Clockwise from the left lip. Tuned against pitcher.webp so the fill
     * sits on the inner wall and does not enter the handle or the spout.
     */
    private val cavityUv = floatArrayOf(
        0.36f, 0.145f,
        0.33f, 0.20f,
        0.305f, 0.28f,
        0.27f, 0.38f,
        0.23f, 0.48f,
        0.195f, 0.58f,
        0.185f, 0.66f,
        0.205f, 0.74f,
        0.26f, 0.82f,
        0.38f, 0.865f,
        0.50f, 0.885f,
        0.62f, 0.865f,
        0.72f, 0.81f,
        0.755f, 0.72f,
        0.745f, 0.60f,
        0.71f, 0.48f,
        0.67f, 0.36f,
        0.645f, 0.26f,
        0.63f, 0.18f,
        0.60f, 0.145f
    )

    fun pitcherLeft(viewportWidth: Float) = viewportWidth * PITCHER_LEFT

    fun pitcherTop(viewportHeight: Float) = viewportHeight * PITCHER_TOP

    fun pitcherWidth(viewportWidth: Float) = viewportWidth * PITCHER_WIDTH

    fun pitcherHeight(viewportHeight: Float) = viewportHeight * PITCHER_HEIGHT

    fun canvasX(viewportWidth: Float, u: Float) = pitcherLeft(viewportWidth) + u * pitcherWidth(viewportWidth)

    fun canvasY(viewportHeight: Float, v: Float) = pitcherTop(viewportHeight) + v * pitcherHeight(viewportHeight)

    fun vAt(viewportHeight: Float, y: Float): Float {
        val span = pitcherHeight(viewportHeight)
        if (span <= 0f) return 0f
        return (y - pitcherTop(viewportHeight)) / span
    }

    /** Surface in bitmap v. Nudged inside the lip and the floor so the span is a real segment. */
    fun surfaceV(fill: Float): Float {
        val clamped = fill.coerceIn(0f, 1f)
        val raw = FILL_BOTTOM_V - (FILL_BOTTOM_V - FILL_TOP_V) * clamped
        return raw.coerceIn(FILL_TOP_V + 0.006f, FILL_BOTTOM_V - 0.006f)
    }

    /** True when the surface has risen into the neck and a pour should exit the spout. */
    fun spillsOver(fill: Float): Boolean = surfaceV(fill) < NECK_V

    /**
     * 1 while the surface has room for a wave and a splash, 0 when it sits
     * on the lip. Keeps the surface stroke inside the cavity at the cap.
     */
    fun lipRoom(fill: Float): Float {
        val room = surfaceV(fill) - (FILL_TOP_V + 0.006f)
        return (room / LIP_ROOM_V).coerceIn(0f, 1f)
    }

    /** Overflow droplet at [t] in 0..1, as bitmap `(u, v)`. */
    fun overflowDropUv(t: Float): Pair<Float, Float> {
        val clamped = t.coerceIn(0f, 1f)
        return (SPOUT_U - DROP_DRIFT_U * clamped) to (SPOUT_V + DROP_FALL_V * clamped)
    }

    fun buildCavity(path: Path, viewportWidth: Float, viewportHeight: Float): Path {
        path.rewind()
        val count = cavityUv.size / 2
        for (index in 0 until count) {
            val x = canvasX(viewportWidth, cavityUv[index * 2])
            val y = canvasY(viewportHeight, cavityUv[index * 2 + 1])
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return path
    }

    /**
     * Horizontal slice of the cavity at bitmap [v], as `(leftU, rightU)`.
     * Returns equal endpoints when the line misses the glass.
     */
    fun spanAtV(v: Float): Pair<Float, Float> {
        var left = Float.POSITIVE_INFINITY
        var right = Float.NEGATIVE_INFINITY
        val count = cavityUv.size / 2
        for (index in 0 until count) {
            val u0 = cavityUv[index * 2]
            val v0 = cavityUv[index * 2 + 1]
            val next = (index + 1) % count
            val u1 = cavityUv[next * 2]
            val v1 = cavityUv[next * 2 + 1]
            val deltaV = v1 - v0
            if (deltaV == 0f) {
                if (v0 == v) {
                    left = minOf(left, u0, u1)
                    right = maxOf(right, u0, u1)
                }
                continue
            }
            val t = (v - v0) / deltaV
            if (t < 0f || t >= 1f) continue
            val u = u0 + (u1 - u0) * t
            if (u < left) left = u
            if (u > right) right = u
        }
        if (left >= right) return 0.48f to 0.48f
        return left to right
    }
}
