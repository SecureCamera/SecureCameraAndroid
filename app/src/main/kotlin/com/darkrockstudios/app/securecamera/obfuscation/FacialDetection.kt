package com.darkrockstudios.app.securecamera.obfuscation

import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import androidx.camera.core.ImageProxy

interface FacialDetection {
	suspend fun processForFaces(bitmap: Bitmap): List<FoundFace>

	suspend fun processForFacesPreview(
		image: ImageProxy,
		previewWidth: Int,
		previewHeight: Int,
		isFrontCamera: Boolean,
	): List<RectF>

	data class FoundFace(
		val boundingBox: Rect,
		val eyes: Eyes?
	) {
		data class Eyes(
			val left: PointF,
			val right: PointF,
		)
	}
}
