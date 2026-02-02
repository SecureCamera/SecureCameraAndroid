package com.darkrockstudios.app.securecamera.camera

import android.content.Context
import android.location.Location
import androidx.camera.core.ImageCapture
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import com.ashampoo.kim.model.GpsCoordinates
import com.darkrockstudios.app.securecamera.LocationRepository
import com.darkrockstudios.app.securecamera.R
import com.darkrockstudios.app.securecamera.RequestLocationPermission
import com.darkrockstudios.app.securecamera.auth.AuthorizationRepository
import com.darkrockstudios.app.securecamera.gallery.vibrateDevice
import com.darkrockstudios.app.securecamera.navigation.Camera
import com.darkrockstudios.app.securecamera.navigation.NavController
import com.darkrockstudios.app.securecamera.navigation.PinVerification
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import timber.log.Timber
import kotlin.uuid.ExperimentalUuidApi
import java.util.Locale as JavaLocale

@Composable
fun CameraControls(
	cameraController: CameraState,
	capturePhoto: MutableState<Boolean?>,
	navController: NavController,
	paddingValues: PaddingValues,
	cameraRotation: Float = 0f,
) {
	val scope = rememberCoroutineScope()
	var isFlashOn by rememberSaveable(cameraController.flashMode) { mutableStateOf(cameraController.flashMode == ImageCapture.FLASH_MODE_ON) }
	var isTopControlsVisible by rememberSaveable { mutableStateOf(false) }
	var activeJobs by remember { mutableStateOf(listOf<kotlinx.coroutines.Job>()) }
	val isLoading by remember { derivedStateOf { activeJobs.isNotEmpty() } }
	var isFlashing by rememberSaveable { mutableStateOf(false) }
	val imageSaver = koinInject<SecureImageRepository>()
	val authManager = koinInject<AuthorizationRepository>()
	val locationRepository = koinInject<LocationRepository>()
	val context = LocalContext.current

	// Video recording state
	val captureMode = cameraController.captureMode
	val isRecording = cameraController.isRecording
	val recordingDurationMs = cameraController.recordingDurationMs

	var locationPermissionState by rememberSaveable { mutableStateOf(false) }
	RequestLocationPermission {
		locationPermissionState = true
	}

	fun doCapturePhoto() {
		if (authManager.checkSessionValidity()) {
			isFlashing = true

			val job = scope.launch(Dispatchers.IO) {
				val location = if (locationPermissionState) {
					locationRepository.currentLocation()
				} else {
					null
				}

				try {
					handleImageCapture(
						cameraController = cameraController,
						imageSaver = imageSaver,
						location = location,
						isFlashOn = isFlashOn,
						context = context,
						deviceRotation = cameraRotation.toInt(),
					)
				} finally {
					activeJobs =
						activeJobs
							.filterNot { it === this }
							.filter { it.isCompleted.not() && it.isCancelled.not() }
							.toMutableList()
				}
			}
			activeJobs = (activeJobs + job).toMutableList()
		} else {
			navController.navigate(PinVerification(Camera))
		}
	}

	fun doToggleRecording() {
		if (authManager.checkSessionValidity()) {
			if (isRecording) {
				cameraController.stopRecording()
				vibrateDevice(context)
			} else {
				val outputFile = cameraController.startRecording(context)
				if (outputFile != null) {
					Timber.i("Recording to: ${outputFile.absolutePath}")
					vibrateDevice(context)
				}
			}
		} else {
			navController.navigate(PinVerification(Camera))
		}
	}

	LaunchedEffect(capturePhoto.value) {
		if (capturePhoto.value != null) {
			doCapturePhoto()
		}
	}

	LaunchedEffect(isFlashing) {
		if (isFlashing) {
			delay(250)
			isFlashing = false
		}
	}

	Box(modifier = Modifier.fillMaxSize()) {
		FlashEffect(isFlashing = isFlashing)

		ZoomMeter(
			cameraController,
			modifier = Modifier
				.align(Alignment.TopCenter)
				.padding(top = paddingValues.calculateTopPadding().plus(64.dp)),
			textRotation = cameraRotation,
		)

		LevelIndicator(
			modifier = Modifier
				.align(Alignment.Center)
				.padding(top = paddingValues.calculateTopPadding().plus(16.dp)),
			deviceRotation = cameraRotation,
		)

		if (isRecording) {
			RecordingIndicator(
				durationMs = recordingDurationMs,
				modifier = Modifier
					.align(Alignment.TopStart)
					.padding(start = 16.dp, top = paddingValues.calculateTopPadding().plus(16.dp))
			)
		}

		if (!isTopControlsVisible && !isRecording) {
			ElevatedButton(
				onClick = { isTopControlsVisible = true },
				modifier = Modifier
					.testTag("more-button")
					.align(Alignment.TopEnd)
					.padding(
						top = paddingValues.calculateTopPadding().plus(16.dp),
						end = 16.dp
					)
			) {
				Icon(
					imageVector = Icons.Filled.MoreVert,
					contentDescription = stringResource(id = R.string.camera_more_options_content_description),
					modifier = Modifier.rotate(cameraRotation),
				)
			}
		}

		if (activeJobs.isNotEmpty() && !isRecording) {
			CircularProgressIndicator(
				modifier = Modifier
					.padding(start = 16.dp, top = paddingValues.calculateTopPadding().plus(16.dp))
					.size(40.dp)
					.align(Alignment.TopStart),
				color = MaterialTheme.colorScheme.primary
			)
		}

		Box(
			modifier = Modifier
				.align(Alignment.TopEnd)
		) {
			TopCameraControlsBar(
				isFlashOn = isFlashOn,
				isFaceTrackingWorker = cameraController.faceTracking == true,
				isVisible = isTopControlsVisible && !isRecording,
				onFlashToggle = {
					isFlashOn = !isFlashOn
				},
				onFaceTrackingToggle = { enabled ->
					cameraController.setEnableFaceTracking(enabled)
				},
				onLensToggle = { cameraController.toggleLens() },
				onClose = { isTopControlsVisible = false },
				paddingValues = paddingValues,
				iconRotation = cameraRotation,
			)
		}

		BottomCameraControls(
			modifier = Modifier
				.align(Alignment.BottomCenter)
				.padding(bottom = paddingValues.calculateBottomPadding()),
			captureMode = captureMode,
			isRecording = isRecording,
			isLoading = isLoading,
			navController = navController,
			onCapture = { doCapturePhoto() },
			onToggleRecording = { doToggleRecording() },
			onModeChange = { mode -> cameraController.switchCaptureMode(mode) },
			iconRotation = cameraRotation,
		)
	}
}

@OptIn(ExperimentalUuidApi::class)
private suspend fun handleImageCapture(
	cameraController: CameraState,
	imageSaver: SecureImageRepository,
	context: Context,
	location: Location?,
	isFlashOn: Boolean,
	deviceRotation: Int,
) {
	val gpsCoordinates = location?.let {
		GpsCoordinates(
			latitude = it.latitude,
			longitude = it.longitude,
		)
	}

	cameraController.flashMode = if (isFlashOn) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
	val result = cameraController.capturePhoto()

	val image = result.getOrNull()
	if (result.isSuccess && image != null) {
		vibrateDevice(context)
		val path = imageSaver.saveImage(
			image = image,
			applyRotation = true,
			deviceRotation = deviceRotation,
			latLng = gpsCoordinates,
		)
		Timber.i("Image saved at: $path")
	} else {
		Timber.e(result.exceptionOrNull(), "Image Capture Error")
	}
}

@Composable
private fun FlashEffect(isFlashing: Boolean) {
	AnimatedVisibility(
		visible = isFlashing,
		enter = fadeIn(animationSpec = tween(durationMillis = 50)),
		exit = fadeOut(animationSpec = tween(durationMillis = 100))
	) {
		Surface(
			modifier = Modifier.fillMaxSize(),
			color = Color.White
		) {}
	}
}

@Composable
private fun RecordingIndicator(
	durationMs: Long,
	modifier: Modifier = Modifier,
) {
	val locale: Locale = Locale.current
	val javaLocale = JavaLocale.forLanguageTag(locale.toLanguageTag())
	val seconds = (durationMs / 1000) % 60
	val minutes = (durationMs / 1000) / 60
	val timeString = String.format(javaLocale, "%02d:%02d", minutes, seconds)

	Row(
		modifier = modifier
			.background(
				color = Color.Black.copy(alpha = 0.6f),
				shape = RoundedCornerShape(8.dp)
			)
			.padding(horizontal = 12.dp, vertical = 8.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Icon(
			imageVector = Icons.Filled.FiberManualRecord,
			contentDescription = stringResource(R.string.camera_recording_indicator),
			tint = Color.Red,
			modifier = Modifier.size(16.dp),
		)
		Spacer(modifier = Modifier.width(8.dp))
		Text(
			text = timeString,
			color = Color.White,
			style = MaterialTheme.typography.titleMedium,
		)
	}
}
