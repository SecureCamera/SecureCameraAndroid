package com.darkrockstudios.app.securecamera.camera

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import com.darkrockstudios.app.securecamera.navigation.NavController
import com.darkrockstudios.app.securecamera.navigation.NavOptions

private class DummyNavController : NavController {
	override fun navigate(
		key: NavKey,
		builder: (NavOptions.() -> Unit)?
	) {
	}

	override fun navigate(key: NavKey) {}
	override fun navigateUp(): Boolean = true
}

@Preview(name = "Top Camera Controls - Visible", showBackground = true)
@Composable
private fun TopCameraControlsBarVisiblePreview() {
	Surface(color = MaterialTheme.colorScheme.background) {
		Box(modifier = Modifier.fillMaxSize()) {
			TopCameraControlsBar(
				isFlashOn = false,
				isFaceTrackingWorker = false,
				isVisible = true,
				onFlashToggle = {},
				onFaceTrackingToggle = {},
				onLensToggle = {},
				onClose = {},
				paddingValues = PaddingValues(0.dp)
			)
		}
	}
}

@Preview(name = "Top Camera Controls - Flash On", showBackground = true)
@Composable
private fun TopCameraControlsBarFlashOnPreview() {
	Surface(color = MaterialTheme.colorScheme.background) {
		Box(modifier = Modifier.fillMaxSize()) {
			TopCameraControlsBar(
				isFlashOn = true,
				isFaceTrackingWorker = true,
				isVisible = true,
				onFlashToggle = {},
				onFaceTrackingToggle = {},
				onLensToggle = {},
				onClose = {},
				paddingValues = PaddingValues(0.dp)
			)
		}
	}
}

@Preview(name = "Bottom Camera Controls - Photo Mode", showBackground = true)
@Composable
private fun BottomCameraControlsPhotoPreview() {
	Surface(color = MaterialTheme.colorScheme.background) {
		Box(modifier = Modifier.fillMaxSize()) {
			BottomCameraControls(
				modifier = Modifier.align(Alignment.BottomCenter),
				captureMode = CaptureMode.PHOTO,
				isRecording = false,
				onCapture = {},
				onToggleRecording = {},
				onModeChange = {},
				isLoading = false,
				navController = DummyNavController()
			)
		}
	}
}

@Preview(name = "Bottom Camera Controls - Video Mode", showBackground = true)
@Composable
private fun BottomCameraControlsVideoPreview() {
	Surface(color = MaterialTheme.colorScheme.background) {
		Box(modifier = Modifier.fillMaxSize()) {
			BottomCameraControls(
				modifier = Modifier.align(Alignment.BottomCenter),
				captureMode = CaptureMode.VIDEO,
				isRecording = false,
				onCapture = {},
				onToggleRecording = {},
				onModeChange = {},
				isLoading = false,
				navController = DummyNavController()
			)
		}
	}
}

@Preview(name = "Bottom Camera Controls - Recording", showBackground = true)
@Composable
private fun BottomCameraControlsRecordingPreview() {
	Surface(color = MaterialTheme.colorScheme.background) {
		Box(modifier = Modifier.fillMaxSize()) {
			BottomCameraControls(
				modifier = Modifier.align(Alignment.BottomCenter),
				captureMode = CaptureMode.VIDEO,
				isRecording = true,
				onCapture = {},
				onToggleRecording = {},
				onModeChange = {},
				isLoading = false,
				navController = DummyNavController()
			)
		}
	}
}