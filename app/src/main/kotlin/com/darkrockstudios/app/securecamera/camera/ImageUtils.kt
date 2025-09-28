package com.darkrockstudios.app.securecamera.camera

import android.graphics.*
import android.graphics.Bitmap.createBitmap
import androidx.camera.core.ImageProxy
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

internal fun Bitmap.rotate(degrees: Int): Bitmap {
	val m = Matrix()
	m.postRotate(degrees.toFloat())
	return createBitmap(this, 0, 0, width, height, m, true)
}

internal fun Bitmap.toJpegByteArray(quality: Int = 90): ByteArray {
	val out = ByteArrayOutputStream()
	compress(Bitmap.CompressFormat.JPEG, quality, out)
	return out.toByteArray()
}

internal fun imageProxyToBytes(proxy: ImageProxy): ByteArray {
	val buffer: ByteBuffer = proxy.planes[0].buffer
	return ByteArray(buffer.remaining()).also { buffer.get(it) }
}

/**
 * Convert an ImageProxy to a Bitmap, handling JPEG and YUV_420_888 formats.
 * Returns a rotated bitmap matching the display orientation.
 * Optionally returns the bitmap in the desiredConfig to avoid extra conversions.
 */
internal fun imageProxyToBitmap(
	proxy: ImageProxy,
	desiredConfig: Bitmap.Config = Bitmap.Config.ARGB_8888,
): Bitmap? {
	return try {
		val rotated: Bitmap? = when (proxy.format) {
			ImageFormat.JPEG -> {
				val bytes = imageProxyToBytes(proxy)
				val opts = BitmapFactory.Options().apply { inPreferredConfig = desiredConfig }
				BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)?.let { bmp ->
					val rotation = proxy.imageInfo.rotationDegrees
					if (rotation == 0) bmp else bmp.rotate(rotation)
				}
			}

			// TODO: This path is horrendously optimized, must refactor it for efficiency
			ImageFormat.YUV_420_888 -> {
				val image = proxy.image ?: return null
				val nv21 = yuv420888ToNv21(image)
				val yuvImage = YuvImage(nv21, ImageFormat.NV21, proxy.width, proxy.height, null)
				val out = ByteArrayOutputStream()
				yuvImage.compressToJpeg(Rect(0, 0, proxy.width, proxy.height), 100, out)
				val jpegBytes = out.toByteArray()
				val opts = BitmapFactory.Options().apply { inPreferredConfig = desiredConfig }
				BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size, opts)?.let { bmp ->
					val rotation = proxy.imageInfo.rotationDegrees
					if (rotation == 0) bmp else bmp.rotate(rotation)
				}
			}

			else -> {
				// Fallback: try decoding first plane as JPEG
				val bytes = imageProxyToBytes(proxy)
				val opts = BitmapFactory.Options().apply { inPreferredConfig = desiredConfig }
				BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)?.let { bmp ->
					val rotation = proxy.imageInfo.rotationDegrees
					if (rotation == 0) bmp else bmp.rotate(rotation)
				}
			}
		}

		rotated?.let { r ->
			if (r.config == desiredConfig) {
				r
			} else {
				// Consolidate conversion to desired config here to avoid extra allocations later
				createBitmap(r.width, r.height, desiredConfig).also { target ->
					Canvas(target).drawBitmap(r, 0f, 0f, null)
					r.recycle()
				}
			}
		}
	} catch (e: Exception) {
		Timber.w(e, "Failed to convert image")
		null
	}
}

private fun yuv420888ToNv21(image: android.media.Image): ByteArray {
	val width = image.width
	val height = image.height
	val ySize = width * height
	val uvSize = width * height / 2
	val out = ByteArray(ySize + uvSize)

	// Copy Y plane
	val yPlane = image.planes[0]
	val yBuffer = yPlane.buffer
	val yRowStride = yPlane.rowStride
	var pos = 0
	for (row in 0 until height) {
		val rowStart = row * yRowStride
		for (col in 0 until width) {
			out[pos++] = yBuffer.get(rowStart + col)
		}
	}

	// Copy interleaved VU (NV21) from U and V planes
	val uPlane = image.planes[1]
	val vPlane = image.planes[2]
	val uBuffer = uPlane.buffer
	val vBuffer = vPlane.buffer
	val uRowStride = uPlane.rowStride
	val vRowStride = vPlane.rowStride
	val uPixelStride = uPlane.pixelStride
	val vPixelStride = vPlane.pixelStride

	for (row in 0 until height / 2) {
		val uRowStart = row * uRowStride
		val vRowStart = row * vRowStride
		for (col in 0 until width / 2) {
			val uIndex = uRowStart + col * uPixelStride
			val vIndex = vRowStart + col * vPixelStride
			out[pos++] = vBuffer.get(vIndex)
			out[pos++] = uBuffer.get(uIndex)
		}
	}

	return out
}
