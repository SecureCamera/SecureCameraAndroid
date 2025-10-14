package com.darkrockstudios.app.securecamera.camera

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun ZoomMeter(
	cameraState: CameraState,
	modifier: Modifier = Modifier.Companion,
) {
	val zoom = cameraState.getZoomState() ?: return
	val zoomState by zoom.observeAsState()

	// Track visibility state
	var isVisible by remember { mutableStateOf(false) }
	// Track previous zoom value to detect changes
	var previousZoomRatio by remember { mutableStateOf<Float?>(null) }
	// Auto-hide timer
	var hideTimer by remember { mutableStateOf(0L) }

	zoomState?.let { state ->
		val min = state.minZoomRatio
		val max = state.maxZoomRatio
		val current = state.zoomRatio

		// Check if zoom has changed
		LaunchedEffect(current) {
			val hasChanged = previousZoomRatio != null && previousZoomRatio != current

			if (hasChanged) {
				// Show meter when zoom changes
				isVisible = true
				// Reset hide timer
				hideTimer = System.currentTimeMillis()
			} else if (previousZoomRatio == null) {
				// Initialize previous zoom value
				hideTimer = System.currentTimeMillis()
			}

			// Update previous zoom value
			previousZoomRatio = current
		}

		// Auto-hide timer
		LaunchedEffect(hideTimer) {
			if (isVisible) {
				// Wait for 3 seconds of inactivity before hiding
				delay(3000)
				isVisible = false
			}
		}

		val primaryColor = MaterialTheme.colorScheme.primary

		AnimatedVisibility(
			visible = isVisible,
			modifier = modifier,
			enter = fadeIn(),
			exit = fadeOut()
		) {
			Box(
				modifier = Modifier
					.width(200.dp)
					.height(50.dp)
			) {
				Canvas(
					modifier = Modifier.fillMaxSize()
				) {
					val canvasWidth = size.width
					val canvasHeight = size.height
					val centerY = canvasHeight / 2
					val lineStartX = 20f
					val lineEndX = canvasWidth - 20f
					val lineLength = lineEndX - lineStartX

					drawLine(
						color = Color.LightGray,
						start = Offset(lineStartX, centerY),
						end = Offset(lineEndX, centerY),
						strokeWidth = 2.dp.toPx(),
						cap = StrokeCap.Round
					)

					// Draw tick marks at 1.0x, 2.0x, and 4.0x
					val tickValues = listOf(1.0f, 2.0f, 4.0f)
					val tickHeight = 10.dp.toPx()

					for (tickValue in tickValues) {
						// Only draw tick if it's within the zoom range
						if (tickValue >= min && tickValue <= max) {
							val normalizedTickPosition = (tickValue - min) / (max - min)
							val tickX = lineStartX + (normalizedTickPosition * lineLength)

							drawLine(
								color = Color.White,
								start = Offset(tickX, centerY),
								end = Offset(tickX, centerY - tickHeight),
								strokeWidth = 1.5.dp.toPx(),
								cap = StrokeCap.Round
							)
						}
					}

					// Draw the current zoom indicator (dot)
					val normalizedZoom = (current - min) / (max - min)
					val dotX = lineStartX + (normalizedZoom * lineLength)
					drawCircle(
						color = primaryColor,
						radius = 6.dp.toPx(),
						center = Offset(dotX, centerY)
					)
				}

				// Min zoom text
				Text(
					text = String.format("%.1fx", min),
					color = Color.White,
					style = MaterialTheme.typography.bodySmall,
					modifier = Modifier
						.align(Alignment.BottomStart)
						.padding(start = 8.dp)
				)

				// Max zoom text
				Text(
					text = String.format("%.1fx", max),
					color = Color.White,
					style = MaterialTheme.typography.bodySmall,
					modifier = Modifier
						.align(Alignment.BottomEnd)
						.padding(end = 8.dp)
				)

				// Current zoom text
				Text(
					text = String.format("%.1fx", current),
					color = Color.White,
					style = MaterialTheme.typography.bodySmall,
					modifier = Modifier
						.align(Alignment.TopCenter)
						.padding(top = 4.dp)
				)
			}
		}
	}
}
