package com.darkrockstudios.app.securecamera.obfuscation

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.camera.core.ImageProxy
import androidx.compose.ui.unit.IntSize
import com.darkrockstudios.app.securecamera.camera.mapRectToPreview
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import kotlin.coroutines.resume

class MlFacialDetection : FacialDetection {
	private val accurateDetector = FaceDetection.getClient(
		FaceDetectorOptions.Builder()
			.setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
			.setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
			.setMinFaceSize(0.02f)
			.build()
	)

	private val realTimeDetector = FaceDetection.getClient(
		FaceDetectorOptions.Builder()
			.setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
			.setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
			.setMinFaceSize(0.1f)
			.build()
	)

	override suspend fun processForFaces(bitmap: Bitmap): List<FacialDetection.FoundFace> {
		val inputImage = InputImage.fromBitmap(bitmap, 0)

		return suspendCancellableCoroutine { continuation ->
			accurateDetector.process(inputImage)
				.addOnSuccessListener { foundFaces ->
					val newRegions = foundFaces.map { face ->
						val leftEye =
							face.allLandmarks.find { it.landmarkType == FaceLandmark.LEFT_EYE }
						val rightEye =
							face.allLandmarks.find { it.landmarkType == FaceLandmark.RIGHT_EYE }
						val eyes = if (leftEye != null && rightEye != null) {
							FacialDetection.FoundFace.Eyes(
								left = leftEye.position,
								right = rightEye.position,
							)
						} else {
							null
						}
						FacialDetection.FoundFace(
							boundingBox = face.boundingBox,
							eyes = eyes
						)
					}
					continuation.resume(newRegions)
				}.addOnFailureListener { e ->
					Timber.e(e, "Failed face detection in Image")
					continuation.resume(emptyList())
				}
		}
	}

	override suspend fun processForFacesPreview(
		image: ImageProxy,
		previewWidth: Int,
		previewHeight: Int,
		isFrontCamera: Boolean,
	): List<RectF> {
		val mediaImage = image.image ?: return emptyList()
		val rotation = image.imageInfo.rotationDegrees
		val inputImage = InputImage.fromMediaImage(mediaImage, rotation)

		return suspendCancellableCoroutine { continuation ->
			realTimeDetector.process(inputImage)
				.addOnSuccessListener { foundFaces ->
					val mapper = mapRectToPreview(
						sourceWidth = inputImage.width,
						sourceHeight = inputImage.height,
						rotationDegrees = rotation,
						isFrontCamera = isFrontCamera,
						previewSizePx = IntSize(previewWidth, previewHeight),
					)
					val rects = foundFaces.map { face ->
						mapper(RectF(face.boundingBox))
					}
					continuation.resume(rects)
				}.addOnFailureListener { e ->
					Timber.e(e, "Failed face detection in Image (realtime)")
					continuation.resume(emptyList())
				}
		}
	}
}