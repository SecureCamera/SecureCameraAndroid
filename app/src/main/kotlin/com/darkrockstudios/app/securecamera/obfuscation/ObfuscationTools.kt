package com.darkrockstudios.app.securecamera.obfuscation

import android.content.Context
import android.graphics.*
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import androidx.core.graphics.scale
import java.security.SecureRandom

enum class MaskMode {
	BLACKOUT,
	PIXELATE,
	BLUR,
	NOISE
}

fun coerceRectToBitmap(rect: Rect, bitmap: Bitmap): Rect {
	// For completely outside bitmap cases, create a small valid rect at the edge
	if (rect.left >= bitmap.width || rect.right <= 0 || rect.top >= bitmap.height || rect.bottom <= 0) {
		val left = (bitmap.width - 1).coerceAtLeast(0)
		val top = (bitmap.height - 1).coerceAtLeast(0)
		return Rect(left, top, bitmap.width, bitmap.height)
	}

	// For normal cases, constrain the coordinates
	val left = rect.left.coerceIn(0, bitmap.width - 1)
	val top = rect.top.coerceIn(0, bitmap.height - 1)

	// Ensure minimum width of 1
	var right = rect.right.coerceIn(left + 1, bitmap.width)
	if (right <= left) right = left + 1

	// Ensure minimum height of 1
	var bottom = rect.bottom.coerceIn(top + 1, bitmap.height)
	if (bottom <= top) bottom = top + 1

	return Rect(left, top, right, bottom)
}

fun maskFace(bitmap: Bitmap, region: Region, context: Context, vararg modes: MaskMode) {
	val rect = region.rect
	val safeRect = coerceRectToBitmap(rect, bitmap)
	modes.forEach { mode ->
		when (mode) {
			MaskMode.BLACKOUT -> blackout(bitmap, safeRect)
			MaskMode.PIXELATE -> pixelate(bitmap, safeRect, region)
			MaskMode.NOISE -> noise(bitmap, safeRect)
			MaskMode.BLUR -> blur(bitmap, safeRect, context)
		}
	}
}

private fun blackout(bitmap: Bitmap, rect: Rect) {
	val safeRect = coerceRectToBitmap(rect, bitmap)
	val canvas = Canvas(bitmap)
	val paint = Paint().apply { color = Color.BLACK }
	canvas.drawRect(safeRect, paint)
}

private fun pixelate(bitmap: Bitmap, rect: Rect, region: Region, targetBlockSize: Int = 8, addNoise: Boolean = true) {
	val faceBitmap = Bitmap.createBitmap(bitmap, rect.left, rect.top, rect.width(), rect.height())

	val small = faceBitmap.scale(targetBlockSize, targetBlockSize, false)

	if (addNoise) {
		val random = SecureRandom.getInstanceStrong()
		val noiseCanvas = Canvas(small)
		val paint = Paint()

		// Replace random pixels entirely
		val noiseProbability = 0.25f
		for (y in 0 until small.height) {
			for (x in 0 until small.width) {
				if (random.nextFloat() <= noiseProbability) {
					paint.color = if (random.nextBoolean()) {
						Color.BLACK
					} else {
						Color.WHITE
					}
					noiseCanvas.drawPoint(x.toFloat(), y.toFloat(), paint)
				}
			}
		}
	}

	if (region is FaceRegion) {
		val face = region.face

		if (face.eyes != null) {
			val leftEyeInFace = PointF(
				face.eyes.left.x - rect.left,
				face.eyes.left.y - rect.top
			)
			val rightEyeInFace = PointF(
				face.eyes.right.x - rect.left,
				face.eyes.right.y - rect.top
			)

			val leftEyeInSmall = PointF(
				leftEyeInFace.x * small.width / faceBitmap.width,
				leftEyeInFace.y * small.height / faceBitmap.height
			)
			val rightEyeInSmall = PointF(
				rightEyeInFace.x * small.width / faceBitmap.width,
				rightEyeInFace.y * small.height / faceBitmap.height
			)

			// Blackout the eyes
			val paint = Paint().apply { color = Color.BLACK }
			val noiseCanvas = Canvas(small)

			if (leftEyeInSmall.x >= 0 && leftEyeInSmall.x < small.width &&
				leftEyeInSmall.y >= 0 && leftEyeInSmall.y < small.height &&
				rightEyeInSmall.x >= 0 && rightEyeInSmall.x < small.width &&
				rightEyeInSmall.y >= 0 && rightEyeInSmall.y < small.height
			) {
				noiseCanvas.drawLine(
					0f, leftEyeInSmall.y, 7f, rightEyeInSmall.y, paint,
				)
			} else {
				if (leftEyeInSmall.x >= 0 && leftEyeInSmall.x < small.width &&
					leftEyeInSmall.y >= 0 && leftEyeInSmall.y < small.height
				) {
					noiseCanvas.drawPoint(leftEyeInSmall.x, leftEyeInSmall.y, paint)
				}

				if (rightEyeInSmall.x >= 0 && rightEyeInSmall.x < small.width &&
					rightEyeInSmall.y >= 0 && rightEyeInSmall.y < small.height
				) {
					noiseCanvas.drawPoint(rightEyeInSmall.x, rightEyeInSmall.y, paint)
				}
			}
		}
	}

	val pixelated = small.scale(rect.width(), rect.height(), false)

	val canvas = Canvas(bitmap)
	canvas.drawBitmap(pixelated, rect.left.toFloat(), rect.top.toFloat(), null)
}

private fun blur(bitmap: Bitmap, rect: Rect, context: Context, radius: Float = 25f, rounds: Int = 10) {
	val safeRect = coerceRectToBitmap(rect, bitmap)
	val faceBitmap = Bitmap.createBitmap(bitmap, safeRect.left, safeRect.top, safeRect.width(), safeRect.height())

	val rs = RenderScript.create(context)
	val input = Allocation.createFromBitmap(rs, faceBitmap)
	val output = Allocation.createTyped(rs, input.type)
	val script = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))

	script.setRadius(radius.coerceIn(0f, 25f))

	// Apply blur for the specified number of rounds
	repeat(rounds.coerceAtLeast(1)) {
		script.setInput(input)
		script.forEach(output)
		output.copyTo(faceBitmap)

		// For subsequent rounds, we need to update the input allocation with the current blurred bitmap
		if (it < rounds - 1) {
			input.copyFrom(faceBitmap)
		}
	}

	val canvas = Canvas(bitmap)
	canvas.drawBitmap(faceBitmap, safeRect.left.toFloat(), safeRect.top.toFloat(), null)

	// Cleanup
	input.destroy()
	output.destroy()
	script.destroy()
	rs.destroy()
}

private fun noise(bitmap: Bitmap, rect: Rect) {
	val safeRect = coerceRectToBitmap(rect, bitmap)
	val random = java.util.Random()
	val canvas = Canvas(bitmap)
	val paint = Paint()

	for (y in safeRect.top until safeRect.bottom) {
		for (x in safeRect.left until safeRect.right) {
			paint.color = Color.rgb(random.nextInt(256), random.nextInt(256), random.nextInt(256))
			canvas.drawPoint(x.toFloat(), y.toFloat(), paint)
		}
	}
}
