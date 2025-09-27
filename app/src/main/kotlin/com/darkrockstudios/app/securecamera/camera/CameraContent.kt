package com.darkrockstudios.app.securecamera.camera

import android.Manifest
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.darkrockstudios.app.securecamera.KeepScreenOnEffect
import com.darkrockstudios.app.securecamera.navigation.NavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

@OptIn(ExperimentalPermissionsApi::class)
@Composable
internal fun CameraContent(
	capturePhoto: MutableState<Boolean?>,
	navController: NavController,
	modifier: Modifier,
	paddingValues: PaddingValues,
) {
	KeepScreenOnEffect()

	val permissionsState = rememberMultiplePermissionsState(
		permissions = listOf(
			Manifest.permission.CAMERA,
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
			)
		} else {
			NoCameraPermission(navController, permissionsState)
		}
	}
}

