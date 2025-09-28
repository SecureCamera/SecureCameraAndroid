package com.darkrockstudios.app.securecamera.camera

import android.annotation.SuppressLint
import android.graphics.RectF
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.LifecycleOwner
import com.darkrockstudios.app.securecamera.obfuscation.FacialDetection
import kotlinx.coroutines.*
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Holds the mutable state and low‑level camera plumbing so that the UI composable is lightweight.
 */
@Stable
class CameraState internal constructor(
	private val lifecycleOwner: LifecycleOwner,
	private val providerFuture: ProcessCameraProvider,
	initialLensFacing: Int = CameraSelector.LENS_FACING_BACK,
	initialFlashMode: Int = ImageCapture.FLASH_MODE_OFF,
) : KoinComponent {

	private val clock: Clock by inject()
	private val facialDetection: FacialDetection by inject()

	private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
	private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
	private val analysisScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

	private var lastAutoFocusAt: Instant = Instant.DISTANT_PAST
	private val minAutoFocusInterval: Duration = 800.milliseconds
	val manualFocusAutoClear: Duration = 3.seconds

	var surfaceRequest by mutableStateOf<SurfaceRequest?>(null)
		private set

	var lensFacing by mutableIntStateOf(initialLensFacing)
		private set

	var camera: Camera? by mutableStateOf(null)
		private set

	var minZoom by mutableFloatStateOf(1f)
		private set

	var maxZoom by mutableFloatStateOf(1f)
		private set

	var manualFocusOffset by mutableStateOf<Offset?>(null)
		private set

	var faceFocusRect by mutableStateOf<RectF?>(null)
		private set

	var displaySize by mutableStateOf<IntSize?>(null)
		internal set

	var faces by mutableStateOf<List<FaceBox>>(emptyList())
		private set

	fun clearFocusOffset() {
		manualFocusOffset = null
	}

	private var imageCapture: ImageCapture? = null
	private var imageAnalysis: ImageAnalysis? = null

	private var _flashMode by mutableIntStateOf(initialFlashMode)
	var flashMode: Int
		get() = _flashMode
		set(value) {
			if (value !in arrayOf(
					ImageCapture.FLASH_MODE_OFF,
					ImageCapture.FLASH_MODE_ON,
					ImageCapture.FLASH_MODE_AUTO
				)
			) return

			_flashMode = value
			imageCapture?.flashMode = value
		}

	fun toggleLens() {
		switchLens(
			if (lensFacing == CameraSelector.LENS_FACING_BACK)
				CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
		)
	}

	/**
	 * Switch explicitly to the given lens facing (use [CameraSelector.LENS_FACING_BACK] or
	 * [CameraSelector.LENS_FACING_FRONT]). If already on that lens, this is a no‑op.
	 */
	fun switchLens(facing: Int) {
		if (facing == lensFacing) return
		lensFacing = facing
		bindCamera()
	}

	/** Call after external zoom gesture math. */
	fun setZoomRatio(ratio: Float) {
		camera?.cameraControl?.setZoomRatio(ratio)
	}

	fun getZoomState() = camera?.cameraInfo?.zoomState

	fun manualFocusAt(offset: Offset) {
		Timber.tag("camera").d("manualFocusAt")
		faceFocusRect = null
		focusAt(offset)
		manualFocusOffset = offset
	}

	/** Focus + meter at the given px location from Compose coordinates. */
	private fun focusAt(offset: Offset) {
		camera?.let { cam ->
			val displaySize = this.displaySize ?: return

			// Create metering point factory for the display size
			val factory = SurfaceOrientedMeteringPointFactory(
				displaySize.width.toFloat(),
				displaySize.height.toFloat()
			)

			val point = factory.createPoint(offset.x, offset.y)
			val action = FocusMeteringAction.Builder(
				point,
				FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE or FocusMeteringAction.FLAG_AWB
			).setAutoCancelDuration(3, TimeUnit.SECONDS).build()

			cam.cameraControl.startFocusAndMetering(action)
		}
	}

	/**
	 * Suspend version of capturePhoto that returns a Result containing the JPEG bytes on success
	 * or an exception on failure.
	 */
	@SuppressLint("MissingPermission")
	suspend fun capturePhoto(): Result<CapturedImage> = suspendCancellableCoroutine { continuation ->
		val capture = imageCapture ?: run {
			continuation.resume(Result.failure(IllegalStateException("ImageCapture not ready")))
			return@suspendCancellableCoroutine
		}

		capture.flashMode = flashMode

		capture.takePicture(
			cameraExecutor,
			object : ImageCapture.OnImageCapturedCallback() {
				override fun onCaptureSuccess(image: ImageProxy) {
					try {
						val captured = CapturedImage(
							sensorBitmap = image.toBitmap(),
							timestamp = Clock.System.now(),
							rotationDegrees = image.imageInfo.rotationDegrees,
						)
						continuation.resume(Result.success(captured))
					} catch (t: Throwable) {
						continuation.resume(Result.failure(t))
					} finally {
						image.close()
					}
				}

				override fun onError(exception: ImageCaptureException) {
					continuation.resume(Result.failure(exception))
				}
			}
		)
	}

	internal fun bindCamera() {
		val provider = providerFuture

		val preview = Preview.Builder().build().also { preview ->
			preview.setSurfaceProvider { surfaceRequest ->
				this.surfaceRequest = surfaceRequest
			}
		}

		imageCapture = ImageCapture.Builder()
			.setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
			.build()

		imageAnalysis = ImageAnalysis.Builder()
			.setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
			.build().apply {
				val analyzer = createFaceDetector(facialDetection)
				setAnalyzer(analysisExecutor, analyzer)
			}

		val selector = CameraSelector.Builder()
			.requireLensFacing(lensFacing)
			.build()

		provider.unbindAll()

		try {
			camera = provider.bindToLifecycle(
				lifecycleOwner,
				selector,
				preview,
				imageCapture,
				imageAnalysis
			).apply {
				cameraInfo.zoomState.value?.let { zoomState ->
					minZoom = zoomState.minZoomRatio
					maxZoom = zoomState.maxZoomRatio
				}
			}
		} catch (e: Exception) {
			Timber.e(e)
		}
	}

	/**
	 * Enable face detection and internal auto-focus behavior. Business logic lives here.
	 */
	private fun createFaceDetector(detector: FacialDetection): FacialDetectionAnalyzer {
		return FacialDetectionAnalyzer(
				scope = analysisScope,
				detector = detector,
				getPreviewSize = { displaySize },
				isFrontCamera = { lensFacing == CameraSelector.LENS_FACING_FRONT },
				onFaces = { rects: List<RectF> ->
					faces = rects.map { r -> FaceBox(boundingBox = r) }
					if (rects.isEmpty()) {
						faceFocusRect = null
						return@FacialDetectionAnalyzer
					}

					// Prefer manual tap focus over face auto-focus
					if (manualFocusOffset != null) return@FacialDetectionAnalyzer

					val now = clock.now()

					val largest = rects.maxByOrNull { it.width() * it.height() }
					largest?.let { r ->
						val center = Offset(r.centerX(), r.centerY())
						faceFocusRect = r
						if ((now - lastAutoFocusAt) >= minAutoFocusInterval) {
							Timber.tag("camera").d("focus on face")
							focusAt(center)
							lastAutoFocusAt = now
						}
					}
				}
			)
	}

	internal fun cleanup() {
		imageAnalysis?.clearAnalyzer()
		analysisScope.cancel()
		cameraExecutor.shutdown()
		analysisExecutor.shutdown()
	}
}