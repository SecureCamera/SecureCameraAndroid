package com.darkrockstudios.app.securecamera.camera

import android.graphics.RectF
import androidx.compose.ui.unit.IntSize
import kotlin.math.max
import kotlin.math.min

/** UI model for a face box in preview display coordinates (px) */
data class FaceBox(
	val boundingBox: RectF,
)

/**
 * Maps rectangles from a source image basis (e.g., analyzer bitmap) into the preview display
 * coordinate system using a centerCrop (fill) strategy, accounting for rotation and mirroring.
 * Pure function for easy unit testing.
 */
fun mapRectToPreview(
	sourceWidth: Int,
	sourceHeight: Int,
	rotationDegrees: Int,
	isFrontCamera: Boolean,
	previewSizePx: IntSize,
	fillScale: Boolean = true,
): (RectF) -> RectF {
	val srcW = sourceWidth.toFloat()
	val srcH = sourceHeight.toFloat()

	val basisW = if (rotationDegrees % 180 == 0) srcW else srcH
	val basisH = if (rotationDegrees % 180 == 0) srcH else srcW

	val viewW = previewSizePx.width.toFloat()
	val viewH = previewSizePx.height.toFloat()

	val scale = if (fillScale) {
		max(viewW / basisW, viewH / basisH)
	} else {
		min(viewW / basisW, viewH / basisH)
	}
	val scaledW = basisW * scale
	val scaledH = basisH * scale
	val dx = (viewW - scaledW) / 2f
	val dy = (viewH - scaledH) / 2f

	return { rSensor ->
		val r = rotateRect(rSensor, srcW, srcH, 0)
		val basisRect = if (isFrontCamera) mirrorHorizontally(r, basisW) else r
		RectF(
			basisRect.left * scale + dx,
			basisRect.top * scale + dy,
			basisRect.right * scale + dx,
			basisRect.bottom * scale + dy,
		)
	}
}

private fun rotateRect(r: RectF, w: Float, h: Float, deg: Int): RectF = when ((deg % 360 + 360) % 360) {
	0 -> RectF(r)
	90 -> RectF(h - r.bottom, r.left, h - r.top, r.right)
	180 -> RectF(w - r.right, h - r.bottom, w - r.left, h - r.top)
	270 -> RectF(r.top, w - r.right, r.bottom, w - r.left)
	else -> RectF(r) // non-right-angle rotations are not expected from CameraX
}

private fun mirrorHorizontally(r: RectF, basisW: Float): RectF = RectF(
	basisW - r.right,
	r.top,
	basisW - r.left,
	r.bottom
)
