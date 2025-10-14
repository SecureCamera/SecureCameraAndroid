package com.darkrockstudios.app.securecamera.camera

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
	onCapture: (() -> Unit)?,
	isLoading: Boolean,
	navController: NavController,
) {
	val context = LocalContext.current

	Box(
		modifier = modifier
			.fillMaxWidth()
			.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
	) {
		ElevatedButton(
			onClick = { navController.navigate(Settings) },
			enabled = isLoading.not(),
			modifier = Modifier.align(Alignment.BottomStart),
		) {
			Icon(
				imageVector = Icons.Filled.Settings,
				contentDescription = stringResource(R.string.camera_settings_button),
				modifier = Modifier.size(32.dp),
			)
		}

		if (onCapture != null) {
			FilledTonalButton(
				onClick = onCapture,
				modifier = Modifier
					.size(80.dp)
					.clip(CircleShape)
					.align(Alignment.BottomCenter)
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

		ElevatedButton(
			onClick = { navController.navigate(Gallery) },
			enabled = isLoading.not(),
			modifier = Modifier.align(Alignment.BottomEnd),
		) {
			Icon(
				imageVector = Icons.Filled.PhotoLibrary,
				contentDescription = stringResource(id = R.string.camera_gallery_content_description),
				modifier = Modifier.size(32.dp),
			)
		}
	}
}