package com.darkrockstudios.app.securecamera.camera

import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


@Composable
fun rememberCameraState(
	initialLensFacing: Int = CameraSelector.LENS_FACING_BACK,
	initialFlashMode: Int = ImageCapture.FLASH_MODE_OFF,
): CameraState {
	val context = androidx.compose.ui.platform.LocalContext.current
	val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current

	return remember {
		val providerFuture = ProcessCameraProvider.getInstance(context).get()
		CameraState(
			lifecycleOwner = lifecycleOwner,
			providerFuture = providerFuture,
			initialLensFacing = initialLensFacing,
			initialFlashMode = initialFlashMode
		).also { cameraState ->
			cameraState.bindCamera()
		}
	}.also { cameraState ->
		DisposableEffect(cameraState) {
			onDispose {
				cameraState.cleanup()
			}
		}
	}
}

@Composable
fun CameraPreview(
	cameraState: CameraState,
	modifier: Modifier = Modifier
) {
	val scope = rememberCoroutineScope()

	// Track the display size for focus calculations
	BoxWithConstraints(
		modifier = modifier
	) {
		val displaySize = androidx.compose.ui.unit.IntSize(
			width = constraints.maxWidth,
			height = constraints.maxHeight
		)

		// Update the camera state with current display size
		LaunchedEffect(displaySize) {
			cameraState.displaySize = displaySize
		}

		// Pinch‑to‑zoom transformable
		val zoomState = rememberTransformableState { zoomChange, _, _ ->
			cameraState.camera?.let { cam ->
				val current = cam.cameraInfo.zoomState.value?.zoomRatio ?: 1f
				val newZoom = (current * zoomChange).coerceIn(cameraState.minZoom, cameraState.maxZoom)
				cameraState.setZoomRatio(newZoom)
			}
		}

		// focus indicator fade animation (manual tap)
		val indicatorAlpha by animateFloatAsState(
			targetValue = if (cameraState.manualFocusOffset != null) 1f else 0f,
			animationSpec = tween(durationMillis = 300),
			label = "focus_alpha"
		)
		val ringRadius = 40f
		var clearFocusJob = remember<Job?> { null }

		fun autoClearFocus() {
			clearFocusJob?.cancel()
			clearFocusJob = scope.launch {
				delay(cameraState.manualFocusAutoClear)
				// Only auto-clear if faces are currently present
				if (cameraState.faces.isNotEmpty()) {
					cameraState.clearFocusOffset()
				}
				clearFocusJob = null
			}
		}

		// Edge-trigger: when we transition from no faces -> faces while a manual focus exists, schedule auto-clear
		var lastHadFaces by remember { mutableStateOf(false) }
		LaunchedEffect(cameraState.faces) {
			val hasFaces = cameraState.faces.isNotEmpty()
			if (hasFaces && !lastHadFaces && cameraState.manualFocusOffset != null) {
				autoClearFocus()
			}
			lastHadFaces = hasFaces
		}

		Box(
			modifier = Modifier
				.fillMaxSize()
				.transformable(zoomState)
				.pointerInput(Unit) {
					detectTapGestures { offset ->
						// If user taps on the existing manual focus ring, clear it instead of setting a new focus
						val existing = cameraState.manualFocusOffset
						if (existing != null) {
							val ringRadius = ringRadius * 1.25f
							val dist = (offset - existing).getDistance()
							val inRing = dist < ringRadius
							if (inRing) {
								cameraState.clearFocusOffset()
								return@detectTapGestures
							}
						}

						cameraState.manualFocusAt(offset)

						autoClearFocus()
					}
				}
		) {
			cameraState.surfaceRequest?.let { request ->
				CameraXViewfinder(
					surfaceRequest = request,
					modifier = Modifier.fillMaxSize()
				)
			}

			val faceBoxStrokeWidth = 3.dp
			// Face focus overlay: draw only the focused face rect
			cameraState.faceFocusRect?.let { r ->
				Canvas(
					modifier = Modifier
						.fillMaxSize()
						.pointerInput(Unit) {}
				) {
					drawRoundRect(
						color = Color(0xFFCDD5DC),
						topLeft = Offset(r.left, r.top),
						size = Size(r.width(), r.height()),
						cornerRadius = CornerRadius(12f, 12f),
						style = Stroke(width = faceBoxStrokeWidth.toPx())
					)
				}
			}

			val manualSpotStrokeWidth = 2.dp
			// Draw focus ring for manual tap
			cameraState.manualFocusOffset?.let { pos ->
				Canvas(
					modifier = Modifier
						.fillMaxSize()
						.pointerInput(Unit) {} // intercept taps
				) {
					drawCircle(
						color = Color.White.copy(alpha = indicatorAlpha),
						radius = ringRadius,
						center = pos,
						style = Stroke(width = manualSpotStrokeWidth.toPx())
					)
				}
			}
		}
	}
}
