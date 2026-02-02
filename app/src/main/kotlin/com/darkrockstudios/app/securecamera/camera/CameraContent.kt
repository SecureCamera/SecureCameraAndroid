package com.darkrockstudios.app.securecamera.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.view.OrientationEventListener
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.darkrockstudios.app.securecamera.KeepScreenOnEffect
import com.darkrockstudios.app.securecamera.navigation.NavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

/**
 * Locks the screen orientation to portrait while this composable is in the composition.
 * Restores the original orientation when the composable leaves.
 */
@SuppressLint("SourceLockedOrientationActivity")
@Composable
private fun LockScreenOrientationPortrait() {
	val activity = LocalActivity.current
	DisposableEffect(Unit) {
		activity ?: return@DisposableEffect onDispose { }
		val originalOrientation = activity.requestedOrientation
		activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
		onDispose {
			activity.requestedOrientation = originalOrientation
		}
	}
}

/**
 * Tracks device orientation using the accelerometer and returns the rotation angle
 * that UI elements should be rotated to appear upright.
 *
 * Returns an animated float representing degrees: 0f, 90f, 180f, or 270f
 * The animation smoothly transitions between orientations.
 */
@Composable
fun rememberDeviceRotation(): Float {
	val context = LocalContext.current
	var targetRotation by remember { mutableFloatStateOf(0f) }

	DisposableEffect(context) {
		val orientationListener = object : OrientationEventListener(context) {
			override fun onOrientationChanged(orientation: Int) {
				if (orientation == ORIENTATION_UNKNOWN) return

				// Map device orientation to UI rotation
				// When device is rotated clockwise, UI should rotate counter-clockwise to stay upright
				val newRotation = when (orientation) {
					in 45 until 135 -> 270f   // Device rotated to landscape (left)
					in 135 until 225 -> 180f  // Device upside down
					in 225 until 315 -> 90f   // Device rotated to landscape (right)
					else -> 0f                 // Portrait
				}

				if (newRotation != targetRotation) {
					targetRotation = newRotation
				}
			}
		}

		if (orientationListener.canDetectOrientation()) {
			orientationListener.enable()
		}

		onDispose {
			orientationListener.disable()
		}
	}

	// Animate the rotation smoothly
	val animatedRotation by animateFloatAsState(
		targetValue = targetRotation,
		animationSpec = tween(durationMillis = 300),
		label = "device_rotation"
	)

	return animatedRotation
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
internal fun CameraContent(
	capturePhoto: MutableState<Boolean?>,
	navController: NavController,
	modifier: Modifier,
	paddingValues: PaddingValues,
) {
	LockScreenOrientationPortrait()
	KeepScreenOnEffect()

	val permissionsState = rememberMultiplePermissionsState(
		permissions = listOf(
			Manifest.permission.CAMERA,
			Manifest.permission.RECORD_AUDIO,
		)
	)

	var showRationaleDialog by remember { mutableStateOf(false) }

	LaunchedEffect(Unit) {
		if (!permissionsState.allPermissionsGranted && permissionsState.shouldShowRationale) {
			showRationaleDialog = true
		} else {
			permissionsState.launchMultiplePermissionRequest()
		}
	}

	if (showRationaleDialog) {
		CameraPermissionRationaleDialog(
			onContinue = {
				showRationaleDialog = false
				permissionsState.launchMultiplePermissionRequest()
			},
			onDismiss = { showRationaleDialog = false }
		)
	}

	val deviceRotation = rememberDeviceRotation()

	Box(
		modifier = modifier
			.fillMaxSize()
	) {
		if (permissionsState.allPermissionsGranted) {
			val cameraState = rememberCameraState()

			CameraPreview(
				modifier = Modifier.fillMaxSize(),
				cameraState = cameraState,
			)

			CameraControls(
				cameraController = cameraState,
				capturePhoto = capturePhoto,
				navController = navController,
				paddingValues = paddingValues,
				cameraRotation = deviceRotation,
			)
		} else {
			NoCameraPermission(navController, permissionsState)
		}
	}
}

