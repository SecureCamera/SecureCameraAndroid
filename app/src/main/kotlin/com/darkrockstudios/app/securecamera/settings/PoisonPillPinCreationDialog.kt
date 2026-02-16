package com.darkrockstudios.app.securecamera.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.darkrockstudios.app.securecamera.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PoisonPillPinCreationDialog(
	viewModel: SettingsViewModel,
	onDismiss: () -> Unit,
	onPinCreated: (String) -> Unit
) {
	val uiState by viewModel.uiState.collectAsState()

	val scope = rememberCoroutineScope()
	var pin by rememberSaveable { mutableStateOf("") }
	var confirmPin by rememberSaveable { mutableStateOf("") }
	var showError by rememberSaveable { mutableStateOf<String?>(null) }

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
					.verticalScroll(rememberScrollState())
					.padding(16.dp),
				horizontalAlignment = Alignment.CenterHorizontally
			) {
				Icon(
					modifier = Modifier
						.size(64.dp)
						.padding(8.dp),
					imageVector = Icons.Filled.Pin,
					contentDescription = null,
					tint = Color.Green
				)

				Text(
					text = stringResource(R.string.poison_pill_creation_title),
					style = MaterialTheme.typography.headlineSmall,
					textAlign = TextAlign.Center,
					modifier = Modifier.padding(bottom = 8.dp)
				)

				Text(
					text = stringResource(R.string.poison_pill_creation_description),
					style = MaterialTheme.typography.bodyMedium,
					textAlign = TextAlign.Center,
					modifier = Modifier.padding(bottom = 16.dp)
				)

				// PIN input
				OutlinedTextField(
					value = pin,
					onValueChange = { newPin ->
						val isValid = if (uiState.alphanumericPinEnabled) {
							newPin.all { char -> char.isLetterOrDigit() }
						} else {
							newPin.all { char -> char.isDigit() }
						}
						if (newPin.length <= uiState.pinSize.max() && isValid) {
							pin = newPin
							showError = null
						}
					},
					label = { Text(stringResource(R.string.pin_creation_hint)) },
					visualTransformation = PasswordVisualTransformation(),
					keyboardOptions = KeyboardOptions(
						keyboardType = if (uiState.alphanumericPinEnabled) KeyboardType.Password else KeyboardType.NumberPassword,
						imeAction = ImeAction.Next
					),
					singleLine = true,
					modifier = Modifier
						.fillMaxWidth()
						.padding(bottom = 8.dp)
				)

				// Confirm PIN input
				OutlinedTextField(
					value = confirmPin,
					onValueChange = { newConfirmPin ->
						val isValid = if (uiState.alphanumericPinEnabled) {
							newConfirmPin.all { char -> char.isLetterOrDigit() }
						} else {
							newConfirmPin.all { char -> char.isDigit() }
						}
						if (newConfirmPin.length <= uiState.pinSize.max() && isValid) {
							confirmPin = newConfirmPin
							showError = null
						}
					},
					label = { Text(stringResource(R.string.pin_creation_confirm_hint)) },
					visualTransformation = PasswordVisualTransformation(),
					keyboardOptions = KeyboardOptions(
						keyboardType = if (uiState.alphanumericPinEnabled) KeyboardType.Password else KeyboardType.NumberPassword,
						imeAction = ImeAction.Done
					),
					singleLine = true,
					modifier = Modifier
						.fillMaxWidth()
						.padding(bottom = 16.dp)
				)

				// Error message
				if (showError != null) {
					Text(
						text = showError ?: "",
						color = MaterialTheme.colorScheme.error,
						style = MaterialTheme.typography.bodyMedium,
						modifier = Modifier.padding(bottom = 16.dp)
					)
				}

				// Buttons
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
							scope.launch {
								val error = viewModel.validatePoisonPillPin(pin, confirmPin)
								withContext(Dispatchers.Main) {
									if (error != null) {
										showError = error
									} else {
										onPinCreated(pin)
									}
								}
							}
						},
						enabled = pin.length in uiState.pinSize && confirmPin.length in uiState.pinSize,
					) {
						Text(stringResource(R.string.pin_creation_button))
					}
				}
			}
		}
	}
}
