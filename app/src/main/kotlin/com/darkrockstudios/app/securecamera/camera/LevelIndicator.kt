package com.darkrockstudios.app.securecamera.camera

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlin.math.absoluteValue
import kotlin.math.atan2
import kotlin.math.tan

@Composable
fun LevelIndicator(
	modifier: Modifier = Modifier,
	deviceRotation: Float = 0f,
) {
	// Snap to discrete rotation values (animated values may not be exactly 0/90/180/270)
	val snappedRotation = when {
		(deviceRotation - 90f).absoluteValue < 45f -> 90f
		(deviceRotation - 180f).absoluteValue < 45f -> 180f
		(deviceRotation - 270f).absoluteValue < 45f -> 270f
		else -> 0f
	}
	val isLandscapeDevice = snappedRotation == 90f || snappedRotation == 270f
	val maxAngle = 15f
	val lineWidth = 128.dp

	val context = LocalContext.current

	var deviceAngle by remember { mutableStateOf(0f) }

	val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
	val gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)

	val sensorEventListener = remember {
		object : SensorEventListener {
			override fun onSensorChanged(event: SensorEvent) {
				if (event.sensor.type == Sensor.TYPE_GRAVITY) {
					// Calculate the angle based on gravity values
					val x = event.values[0]
					val y = event.values[1]

					// Calculate the angle in degrees
					val angle = Math.toDegrees(atan2(y.toDouble(), x.toDouble())).toFloat()
					deviceAngle = angle
				}
			}

			override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
				// Not needed for this implementation
			}
		}
	}

	DisposableEffect(Unit) {
		sensorManager.registerListener(
			sensorEventListener,
			gravitySensor,
			SensorManager.SENSOR_DELAY_UI
		)

		onDispose {
			sensorManager.unregisterListener(sensorEventListener)
		}
	}

	// Use the snapped device rotation to determine angle adjustment
	val orientationAdjustedAngle = when (snappedRotation) {
		270f -> deviceAngle - 90f  // Landscape left
		90f -> deviceAngle + 90f   // Landscape right
		180f -> deviceAngle + 180f // Portrait upside down
		else -> deviceAngle        // Normal portrait
	}
	val rawAdjustedAngle = orientationAdjustedAngle - 90f

	// Normalize the angle to be between 0 and 360 degrees
	val normalizedAngle = ((rawAdjustedAngle % 180f) + 180f) % 180f

	// Calculate the minimum distance from 0 or 360
	val adjustedAngle = minOf(normalizedAngle, 180f - normalizedAngle)

	if (adjustedAngle.absoluteValue < maxAngle) {
		Box(
			modifier = modifier
				.then(
					if (isLandscapeDevice) {
						Modifier
							.fillMaxHeight()
							.width(100.dp)
							.padding(end = 64.dp)
							.rotate(deviceRotation)
					} else {
						Modifier
							.fillMaxWidth()
							.height(100.dp)
							.padding(top = 64.dp)
					}
				)
		) {
			Canvas(
				modifier = Modifier.fillMaxSize()
			) {
				val canvasWidth = size.width
				val canvasHeight = size.height
				val centerY = (canvasHeight / 2) + 20.dp.toPx() // Slightly below the middle
				val lineWidth = lineWidth.toPx()
				val startX = (canvasWidth - lineWidth) / 2
				val endX = startX + lineWidth

				// Draw the fixed horizontal line
				drawLine(
					color = Color.LightGray,
					start = Offset(startX, centerY),
					end = Offset(endX, centerY),
					strokeWidth = 2.dp.toPx(),
					cap = StrokeCap.Round
				)

				val color = when {
					adjustedAngle.absoluteValue < 1f -> Color.Green
					adjustedAngle.absoluteValue < 10f -> Color.Yellow
					else -> Color.Red
				}

				val angleInRadians = Math.toRadians((orientationAdjustedAngle + 90).toDouble())
				val halfLineWidth = lineWidth / 2
				val rawYOffset = (halfLineWidth * tan(angleInRadians)).toFloat()
				val maxOffset = canvasHeight / 2
				val yOffset = rawYOffset.coerceIn(-maxOffset, maxOffset)

				// Draw the tilted line representing the current device orientation
				drawLine(
					color = color,
					start = Offset(startX, centerY + yOffset),
					end = Offset(endX, centerY - yOffset),
					strokeWidth = 2.dp.toPx(),
					cap = StrokeCap.Round
				)
			}

			Text(
				text = "${adjustedAngle.toInt()}°",
				color = Color.White,
				style = MaterialTheme.typography.bodyMedium,
				modifier = Modifier
					.align(Alignment.Center)
					.padding(top = 16.dp)
			)
		}
	}
}