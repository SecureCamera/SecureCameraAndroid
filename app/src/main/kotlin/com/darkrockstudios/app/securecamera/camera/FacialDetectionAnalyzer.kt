package com.darkrockstudios.app.securecamera.camera

import android.graphics.RectF
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.compose.ui.unit.IntSize
import com.darkrockstudios.app.securecamera.obfuscation.FacialDetection
import kotlinx.coroutines.*

/**
 * Analyzer that returns face rects in PREVIEW DISPLAY coordinates. If the detector supports
 * RealtimeFacialDetection, it will be used; otherwise it falls back to bitmap conversion and
 * internal mapping.
 */
class FacialDetectionAnalyzer(
	private val scope: CoroutineScope,
	private val detector: FacialDetection,
	private val getPreviewSize: () -> IntSize?,
	private val isFrontCamera: () -> Boolean,
	private val onFaces: (facesInPreview: List<RectF>) -> Unit,
) : ImageAnalysis.Analyzer {
	@Volatile
	private var runningJob: Job? = null

	var enabled: Boolean = true

	override fun analyze(image: ImageProxy) {
		if (runningJob?.isActive == true || enabled.not()) {
			image.close()
			return
		}

		runningJob = scope.launch(Dispatchers.Default) {
			try {
				val preview = getPreviewSize() ?: run { image.close(); return@launch }
				val front = isFrontCamera()

				val facesInPreview: List<RectF> =
					detector.processForFacesPreview(image, preview.width, preview.height, front)

				withContext(Dispatchers.Main) {
					onFaces(facesInPreview)
				}
			} catch (_: Throwable) {
				// ignore per privacy and robustness
			} finally {
				image.close()
			}
		}
	}
}