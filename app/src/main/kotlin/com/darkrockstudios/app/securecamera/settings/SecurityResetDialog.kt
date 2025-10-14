package com.darkrockstudios.app.securecamera.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.darkrockstudios.app.securecamera.R

@Composable
fun SecurityResetDialog(
	onDismiss: () -> Unit,
	onConfirm: () -> Unit
) {
	var understandChecked by rememberSaveable { mutableStateOf(false) }

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
				Row(
					verticalAlignment = Alignment.CenterVertically,
					modifier = Modifier.fillMaxWidth()
				) {
					Icon(
						imageVector = Icons.Filled.Warning,
						contentDescription = null,
						tint = MaterialTheme.colorScheme.error,
						modifier = Modifier.size(24.dp)
					)
					Spacer(modifier = Modifier.width(8.dp))
					Text(
						text = stringResource(id = R.string.security_reset_dialog_title),
						style = MaterialTheme.typography.headlineSmall
					)
				}

				Spacer(modifier = Modifier.height(8.dp))

				Text(
					text = stringResource(id = R.string.security_reset_dialog_message),
					style = MaterialTheme.typography.bodyMedium
				)

				Spacer(modifier = Modifier.height(16.dp))

				// "I Understand" switch
				Row(
					modifier = Modifier.fillMaxWidth(),
					verticalAlignment = Alignment.CenterVertically
				) {
					Text(
						text = stringResource(id = R.string.security_reset_understand),
						style = MaterialTheme.typography.bodyLarge,
						modifier = Modifier.weight(1f)
					)
					Switch(
						checked = understandChecked,
						onCheckedChange = { understandChecked = it }
					)
				}

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
						onClick = onConfirm,
						enabled = understandChecked,
						colors = ButtonDefaults.buttonColors(
							containerColor = MaterialTheme.colorScheme.error,
							contentColor = MaterialTheme.colorScheme.onError
						)
					) {
						Text(
							text = stringResource(id = R.string.security_reset_destroy_button),
						)
					}
				}
			}
		}
	}
}
