package com.darkrockstudios.app.securecamera.camera

import android.graphics.*
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

internal fun Bitmap.rotate(degrees: Int): Bitmap {
	val m = Matrix()
	m.postRotate(degrees.toFloat())
	return Bitmap.createBitmap(this, 0, 0, width, height, m, true)
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
 */
internal fun imageProxyToBitmap(proxy: ImageProxy): Bitmap? {
	return try {
		when (proxy.format) {
			ImageFormat.JPEG -> {
				val bytes = imageProxyToBytes(proxy)
				BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { bmp ->
					val rotation = proxy.imageInfo.rotationDegrees
					if (rotation == 0) bmp else bmp.rotate(rotation)
				}
			}

			ImageFormat.YUV_420_888 -> {
				val image = proxy.image ?: return null
				val nv21 = yuv420888ToNv21(image)
				val yuvImage = YuvImage(nv21, ImageFormat.NV21, proxy.width, proxy.height, null)
				val out = ByteArrayOutputStream()
				yuvImage.compressToJpeg(Rect(0, 0, proxy.width, proxy.height), 100, out)
				val jpegBytes = out.toByteArray()
				BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)?.let { bmp ->
					val rotation = proxy.imageInfo.rotationDegrees
					if (rotation == 0) bmp else bmp.rotate(rotation)
				}
			}

			else -> {
				// Fallback: try decoding first plane as JPEG
				val bytes = imageProxyToBytes(proxy)
				BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { bmp ->
					val rotation = proxy.imageInfo.rotationDegrees
					if (rotation == 0) bmp else bmp.rotate(rotation)
				}
			}
		}
	} catch (e: Exception) {
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

internal fun rotateAndEncode(proxy: ImageProxy, quality: Int = 90): ByteArray {
	val bmp = imageProxyToBitmap(proxy) ?: return ByteArray(0)
	return bmp.toJpegByteArray(quality)
}