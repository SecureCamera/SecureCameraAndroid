package com.darkrockstudios.app.securecamera.camera

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.darkrockstudios.app.securecamera.Flashlight
import com.darkrockstudios.app.securecamera.FlashlightOff
import com.darkrockstudios.app.securecamera.R

@Composable
fun TopCameraControlsBar(
	isFlashOn: Boolean,
	isFaceTrackingWorker: Boolean,
	isVisible: Boolean,
	onFlashToggle: (Boolean) -> Unit,
	onFaceTrackingToggle: (Boolean) -> Unit,
	onLensToggle: () -> Unit,
	onClose: () -> Unit,
	paddingValues: PaddingValues? = null,
	iconRotation: Float = 0f,
) {
	AnimatedVisibility(
		visible = isVisible,
		enter = slideInHorizontally(
			initialOffsetX = { fullWidth -> fullWidth },
			animationSpec = tween(durationMillis = 300)
		) + fadeIn(animationSpec = tween(durationMillis = 300)),
		exit = slideOutHorizontally(
			targetOffsetX = { fullWidth -> fullWidth },
			animationSpec = tween(durationMillis = 300)
		) + fadeOut(animationSpec = tween(durationMillis = 300))
	) {
		Surface(
			modifier = Modifier
				.padding(
					end = 16.dp,
					top = paddingValues?.calculateTopPadding()?.plus(16.dp) ?: 16.dp,
				),
			color = Color.Black.copy(alpha = 0.6f),
			shape = RoundedCornerShape(16.dp)
		) {
			Column(
				modifier = Modifier.padding(12.dp),
				horizontalAlignment = Alignment.CenterHorizontally,
				verticalArrangement = Arrangement.spacedBy(8.dp),
			) {
				CompactControlButton(
					onClick = onClose,
					icon = Icons.Filled.Close,
					contentDescription = stringResource(id = R.string.camera_close_controls_content_description),
					iconRotation = iconRotation,
				)

				CompactControlButton(
					onClick = onLensToggle,
					icon = Icons.Filled.Cameraswitch,
					contentDescription = stringResource(id = R.string.camera_toggle_content_description),
					iconRotation = iconRotation,
				)

				HorizontalDivider(
					modifier = Modifier.width(40.dp),
					color = Color.White.copy(alpha = 0.3f),
				)

				CompactToggleButton(
					checked = isFlashOn,
					onCheckedChange = onFlashToggle,
					icon = if (isFlashOn) Flashlight else FlashlightOff,
					contentDescription = stringResource(id = R.string.camera_flash_text),
					testTag = "flash-switch",
					iconRotation = iconRotation,
				)

				CompactToggleButton(
					checked = isFaceTrackingWorker,
					onCheckedChange = onFaceTrackingToggle,
					icon = if (isFaceTrackingWorker) FaceTrackingOn else FaceTrackingOff,
					contentDescription = stringResource(id = R.string.camera_face_tracking),
					testTag = "face-switch",
					iconRotation = iconRotation,
				)
			}
		}
	}
}

@Composable
private fun CompactControlButton(
	onClick: () -> Unit,
	icon: ImageVector,
	contentDescription: String,
	iconRotation: Float,
) {
	FilledTonalButton(
		onClick = onClick,
		modifier = Modifier.size(48.dp),
		contentPadding = PaddingValues(0.dp),
		colors = ButtonDefaults.filledTonalButtonColors(
			containerColor = MaterialTheme.colorScheme.primary
		)
	) {
		Icon(
			imageVector = icon,
			contentDescription = contentDescription,
			tint = MaterialTheme.colorScheme.onPrimary,
			modifier = Modifier
				.size(24.dp)
				.rotate(iconRotation),
		)
	}
}

@Composable
private fun CompactToggleButton(
	checked: Boolean,
	onCheckedChange: (Boolean) -> Unit,
	icon: ImageVector,
	contentDescription: String,
	testTag: String,
	iconRotation: Float,
) {
	FilledIconToggleButton(
		checked = checked,
		onCheckedChange = onCheckedChange,
		modifier = Modifier
			.size(48.dp)
			.testTag(testTag),
		colors = IconButtonDefaults.filledIconToggleButtonColors(
			containerColor = Color.White.copy(alpha = 0.2f),
			contentColor = Color.White,
			checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
			checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
		),
	) {
		Icon(
			imageVector = icon,
			contentDescription = contentDescription,
			modifier = Modifier
				.size(24.dp)
				.rotate(iconRotation),
		)
	}
}
