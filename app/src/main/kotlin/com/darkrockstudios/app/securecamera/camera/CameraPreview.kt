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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
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
	state: CameraState,
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
			state.displaySize = displaySize
		}

		// Pinch‑to‑zoom transformable
		val zoomState = rememberTransformableState { zoomChange, _, _ ->
			state.camera?.let { cam ->
				val current = cam.cameraInfo.zoomState.value?.zoomRatio ?: 1f
				val newZoom = (current * zoomChange).coerceIn(state.minZoom, state.maxZoom)
				state.setZoomRatio(newZoom)
			}
		}

		// focus indicator fade animation
		val indicatorAlpha by animateFloatAsState(
			targetValue = if (state.focusOffset != null) 1f else 0f,
			animationSpec = tween(durationMillis = 300),
			label = "focus_alpha"
		)

		Box(
			modifier = Modifier
				.fillMaxSize()
				.transformable(zoomState)
				.pointerInput(Unit) {
					detectTapGestures { offset ->
						state.focusAt(offset)

						scope.launch {
							delay(800)
							state.clearFocusOffset()
						}
					}
				}
		) {
			state.surfaceRequest?.let { request ->
				CameraXViewfinder(
					surfaceRequest = request,
					modifier = Modifier.fillMaxSize()
				)
			}

			// Draw focus ring
			state.focusOffset?.let { pos ->
				Canvas(
					modifier = Modifier
						.fillMaxSize()
						.pointerInput(Unit) {} // intercept taps
				) {
					drawCircle(
						color = Color.White.copy(alpha = indicatorAlpha),
						radius = 40f,
						center = pos,
						style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f)
					)
				}
			}
		}
	}
}
