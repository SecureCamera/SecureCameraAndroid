package com.darkrockstudios.app.securecamera.camera

import android.content.Context
import android.content.res.Configuration
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
	modifier: Modifier = Modifier
) {
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

	// Register and unregister the sensor listener
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

	val isLandscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
	val orientationAdjustedAngle = when {
		// Landscape left (rotation 90)
		isLandscape && deviceAngle > 0 -> deviceAngle - 90f
		// Landscape right (rotation 270)
		isLandscape && deviceAngle < 0 -> deviceAngle + 90f
		// Portrait upside down (rotation 180)
		!isLandscape && deviceAngle.absoluteValue > 90f -> deviceAngle + 180f
		// Normal portrait (rotation 0)
		else -> deviceAngle
	}
	val rawAdjustedAngle = orientationAdjustedAngle - 90f

	// Normalize the angle to be between 0 and 360 degrees
	val normalizedAngle = ((rawAdjustedAngle % 180f) + 180f) % 180f

	// Calculate the minimum distance from 0 or 360
	val adjustedAngle = minOf(normalizedAngle, 180f - normalizedAngle)

	if (adjustedAngle.absoluteValue < maxAngle) {
		Box(
			modifier = modifier
				.fillMaxWidth()
				.height(100.dp)
				.padding(top = 30.dp)
		) {
			Canvas(
				modifier = Modifier.Companion.fillMaxSize()
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
					cap = StrokeCap.Companion.Round
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
					cap = StrokeCap.Companion.Round
				)
			}

			Text(
				text = "${adjustedAngle.toInt()}°",
				color = Color.Companion.White,
				style = MaterialTheme.typography.bodyMedium,
				modifier = Modifier.Companion
					.align(Alignment.Companion.Center)
			)
		}
	}
}