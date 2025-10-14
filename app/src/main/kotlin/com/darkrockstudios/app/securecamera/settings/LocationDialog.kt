package com.darkrockstudios.app.securecamera.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.darkrockstudios.app.securecamera.LocationPermissionStatus
import com.darkrockstudios.app.securecamera.R

@Composable
fun LocationDialog(
	locationPermissionStatus: LocationPermissionStatus,
	context: Context,
	onDismiss: () -> Unit
) {
	Dialog(onDismissRequest = onDismiss) {
		Card(
			modifier = Modifier
				.fillMaxWidth()
				.padding(16.dp),
			shape = MaterialTheme.shapes.medium
		) {
			Column(
				modifier = Modifier
					.fillMaxWidth()
					.padding(16.dp)
			) {
				Text(
					text = when (locationPermissionStatus) {
						LocationPermissionStatus.DENIED -> stringResource(id = R.string.location_dialog_title_denied)
						LocationPermissionStatus.COARSE -> stringResource(id = R.string.location_dialog_title_coarse)
						LocationPermissionStatus.FINE -> stringResource(id = R.string.location_dialog_title_fine)
					},
					style = MaterialTheme.typography.headlineSmall
				)

				Spacer(modifier = Modifier.height(8.dp))

				Text(
					text = when (locationPermissionStatus) {
						LocationPermissionStatus.DENIED -> stringResource(id = R.string.location_dialog_message_denied)
						LocationPermissionStatus.COARSE -> stringResource(id = R.string.location_dialog_message_coarse)
						LocationPermissionStatus.FINE -> stringResource(id = R.string.location_dialog_message_fine)
					},
					style = MaterialTheme.typography.bodyMedium
				)

				Spacer(modifier = Modifier.height(16.dp))

				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.End
				) {
					TextButton(onClick = onDismiss) {
						Text(stringResource(id = R.string.cancel_button))
					}

					Spacer(modifier = Modifier.width(8.dp))

					Button(
						onClick = {
							val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
								setData(Uri.fromParts("package", context.packageName, null))
							}
							context.startActivity(intent)
							onDismiss()
						}
					) {
						Text(
							text = stringResource(id = R.string.location_dialog_change_settings)
						)
					}
				}
			}
		}
	}
}