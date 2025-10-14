package com.darkrockstudios.app.securecamera.viewphoto

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.darkrockstudios.app.securecamera.R
import com.darkrockstudios.app.securecamera.camera.PhotoDef
import com.darkrockstudios.app.securecamera.camera.PhotoMetaData
import com.darkrockstudios.app.securecamera.camera.SecureImageRepository
import org.koin.compose.koinInject

@Composable
fun PhotoInfoDialog(
	photo: PhotoDef,
	dismiss: () -> Unit
) {
	val imageManager = koinInject<SecureImageRepository>()

	var metadata by remember { mutableStateOf<PhotoMetaData?>(null) }

	LaunchedEffect(photo) {
		metadata = imageManager.getPhotoMetaData(photo)
	}

	AlertDialog(
		onDismissRequest = dismiss,
		title = { Text(stringResource(id = R.string.info_dialog_title)) },
		text = {
			Column(modifier = Modifier.verticalScroll(rememberScrollState())) {

				InfoRow(
					title = stringResource(id = R.string.photo_name_label),
					value = photo.photoName,
				)

				InfoRow(
					title = stringResource(id = R.string.photo_resolution_label),
					value = metadata?.resolutionString() ?: stringResource(R.string.photo_no_data),
				)

				InfoRow(
					title = stringResource(id = R.string.photo_date_label),
					value = metadata?.dateTaken?.toString() ?: stringResource(R.string.photo_no_data),
				)

				InfoRow(
					title = stringResource(id = R.string.photo_location_label),
					value = metadata?.location?.latLongString ?: stringResource(R.string.photo_no_data),
				)

				InfoRow(
					title = stringResource(id = R.string.photo_orientation_label),
					value = metadata?.orientation?.toString() ?: stringResource(R.string.photo_no_data),
				)
			}
		},
		confirmButton = {
			TextButton(onClick = dismiss) {
				Text(stringResource(id = R.string.ok_button))
			}
		}
	)
}

@Composable
private fun InfoRow(
	title: String,
	value: String
) {
	Text(
		text = title,
		style = MaterialTheme.typography.bodyLarge,
		color = MaterialTheme.colorScheme.onSurface
	)
	Spacer(modifier = Modifier.height(4.dp))

	Text(
		text = value,
		style = MaterialTheme.typography.bodyLarge,
		color = MaterialTheme.colorScheme.onSurface
	)

	Spacer(modifier = Modifier.height(16.dp))
}