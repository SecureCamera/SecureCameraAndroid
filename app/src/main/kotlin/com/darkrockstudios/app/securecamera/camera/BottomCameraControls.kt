package com.darkrockstudios.app.securecamera.camera

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.darkrockstudios.app.securecamera.R
import com.darkrockstudios.app.securecamera.navigation.Gallery
import com.darkrockstudios.app.securecamera.navigation.NavController
import com.darkrockstudios.app.securecamera.navigation.Settings

@Composable
fun BottomCameraControls(
	modifier: Modifier = Modifier,
	captureMode: CaptureMode,
	isRecording: Boolean,
	onCapture: (() -> Unit)?,
	onToggleRecording: (() -> Unit)?,
	onModeChange: (CaptureMode) -> Unit,
	isLoading: Boolean,
	navController: NavController,
) {
	val context = LocalContext.current

	Column(
		modifier = modifier
			.fillMaxWidth()
			.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
		horizontalAlignment = Alignment.CenterHorizontally,
	) {
		// Mode toggle chips
		Row(
			modifier = Modifier.padding(bottom = 16.dp),
		) {
			FilterChip(
				selected = captureMode == CaptureMode.PHOTO,
				onClick = { onModeChange(CaptureMode.PHOTO) },
				enabled = !isRecording && !isLoading,
				label = { Text(stringResource(R.string.camera_mode_photo)) },
				leadingIcon = {
					Icon(
						imageVector = Icons.Filled.Camera,
						contentDescription = null,
						modifier = Modifier.size(18.dp),
					)
				}
			)
			Spacer(modifier = Modifier.width(8.dp))
			FilterChip(
				selected = captureMode == CaptureMode.VIDEO,
				onClick = { onModeChange(CaptureMode.VIDEO) },
				enabled = !isRecording && !isLoading,
				label = { Text(stringResource(R.string.camera_mode_video)) },
				leadingIcon = {
					Icon(
						imageVector = Icons.Filled.Videocam,
						contentDescription = null,
						modifier = Modifier.size(18.dp),
					)
				}
			)
		}

		// Main controls row
		Box(
			modifier = Modifier.fillMaxWidth(),
		) {
			ElevatedButton(
				onClick = { navController.navigate(Settings) },
				enabled = !isLoading && !isRecording,
				modifier = Modifier.align(Alignment.CenterStart),
			) {
				Icon(
					imageVector = Icons.Filled.Settings,
					contentDescription = stringResource(R.string.camera_settings_button),
					modifier = Modifier.size(32.dp),
				)
			}

			// Capture/Record button
			when (captureMode) {
				CaptureMode.PHOTO -> {
					if (onCapture != null) {
						FilledTonalButton(
							onClick = onCapture,
							modifier = Modifier
								.size(80.dp)
								.clip(CircleShape)
								.align(Alignment.Center)
								.semantics {
									contentDescription = context.getString(R.string.camera_shutter_button_desc)
								},
							colors = ButtonDefaults.filledTonalButtonColors(
								containerColor = MaterialTheme.colorScheme.primary,
							),
						) {
							Icon(
								imageVector = Icons.Filled.Camera,
								contentDescription = stringResource(id = R.string.camera_capture_content_description),
								tint = MaterialTheme.colorScheme.onPrimary,
								modifier = Modifier.size(32.dp),
							)
						}
					}
				}

				CaptureMode.VIDEO -> {
					if (onToggleRecording != null) {
						FilledTonalButton(
							onClick = onToggleRecording,
							modifier = Modifier
								.size(80.dp)
								.clip(CircleShape)
								.align(Alignment.Center)
								.then(
									if (isRecording) {
										Modifier.border(3.dp, Color.Red, CircleShape)
									} else {
										Modifier
									}
								)
								.semantics {
									contentDescription = if (isRecording) {
										context.getString(R.string.camera_stop_recording_description)
									} else {
										context.getString(R.string.camera_start_recording_description)
									}
								},
							colors = ButtonDefaults.filledTonalButtonColors(
								containerColor = if (isRecording) Color.Red else MaterialTheme.colorScheme.primary,
							),
						) {
							Icon(
								imageVector = if (isRecording) Icons.Filled.Stop else Icons.Filled.FiberManualRecord,
								contentDescription = null,
								tint = Color.White,
								modifier = Modifier.size(32.dp),
							)
						}
					}
				}
			}

			ElevatedButton(
				onClick = { navController.navigate(Gallery) },
				enabled = !isLoading && !isRecording,
				modifier = Modifier.align(Alignment.CenterEnd),
			) {
				Icon(
					imageVector = Icons.Filled.PhotoLibrary,
					contentDescription = stringResource(id = R.string.camera_gallery_content_description),
					modifier = Modifier.size(32.dp),
				)
			}
		}
	}
}