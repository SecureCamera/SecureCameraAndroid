package com.darkrockstudios.app.securecamera.introduction

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darkrockstudios.app.securecamera.R
import com.darkrockstudios.app.securecamera.ui.NotificationPermissionRationale

/**
 * Content for PIN creation
 */
@Composable
fun PinCreationContent(
	viewModel: IntroductionViewModel,
	modifier: Modifier = Modifier
) {
	val uiState by viewModel.uiState.collectAsStateWithLifecycle()
	var pin by rememberSaveable { mutableStateOf<String>("") }
	var confirmPin by rememberSaveable { mutableStateOf<String>("") }

	var pinVisible by rememberSaveable { mutableStateOf(false) }

	Box(modifier = modifier) {
		Column(
			modifier = Modifier
				.verticalScroll(rememberScrollState())
				.widthIn(max = 512.dp)
				.align(Alignment.Center),
			horizontalAlignment = Alignment.CenterHorizontally,
			verticalArrangement = Arrangement.Center
		) {
			Icon(
				modifier = Modifier
					.size(96.dp)
					.padding(16.dp),
				imageVector = Icons.Filled.Pin,
				contentDescription = stringResource(id = R.string.pin_verification_icon),
				tint = MaterialTheme.colorScheme.onBackground
			)

			Text(
				text = stringResource(R.string.pin_creation_title),
				style = MaterialTheme.typography.headlineMedium,
				textAlign = TextAlign.Center,
				modifier = Modifier.padding(bottom = 8.dp)
			)

			Text(
				text = stringResource(R.string.pin_creation_description),
				style = MaterialTheme.typography.bodyLarge,
				textAlign = TextAlign.Center,
				modifier = Modifier.padding(bottom = 24.dp)
			)

			Text(
				text = stringResource(R.string.pin_creation_warning),
				style = MaterialTheme.typography.bodyMedium,
				fontStyle = FontStyle.Italic,
				color = Color.Red,
				textAlign = TextAlign.Center,
				modifier = Modifier.padding(bottom = 24.dp)
			)

			// PIN input
			OutlinedTextField(
				value = pin,
				onValueChange = { newPin ->
					if (newPin.length <= uiState.pinSize.max() && newPin.all { char -> char.isDigit() }) {
						pin = newPin
					}
				},
				label = { Text(stringResource(R.string.pin_creation_hint)) },
				visualTransformation = if (pinVisible) VisualTransformation.None else PasswordVisualTransformation(),
				keyboardOptions = KeyboardOptions(
					keyboardType = KeyboardType.NumberPassword,
					imeAction = ImeAction.Next
				),
				trailingIcon = {
					IconButton(onClick = { pinVisible = !pinVisible }) {
						Icon(
							imageVector = if (pinVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
							contentDescription = if (pinVisible)
								stringResource(R.string.pin_hide_description)
							else
								stringResource(R.string.pin_show_description)
						)
					}
				},
				singleLine = true,
				modifier = Modifier
					.fillMaxWidth()
					.padding(bottom = 16.dp)
			)

			// Confirm PIN input
			OutlinedTextField(
				value = confirmPin,
				onValueChange = { newConfirmPin ->
					if (newConfirmPin.length <= uiState.pinSize.max() && newConfirmPin.all { char -> char.isDigit() }) {
						confirmPin = newConfirmPin
					}
				},
				label = { Text(stringResource(R.string.pin_creation_confirm_hint)) },
				visualTransformation = PasswordVisualTransformation(),
				keyboardOptions = KeyboardOptions(
					keyboardType = KeyboardType.NumberPassword,
					imeAction = ImeAction.Done
				),
				singleLine = true,
				modifier = Modifier
					.fillMaxWidth()
					.padding(bottom = 24.dp)
			)

			// Error message
			uiState.errorMessage?.let { errorMessage ->
				Text(
					text = errorMessage,
					color = MaterialTheme.colorScheme.error,
					style = MaterialTheme.typography.bodyMedium,
					modifier = Modifier
						.padding(bottom = 16.dp)
						.testTag("pin-error")
				)
			}

			// Create PIN button
			Button(
				onClick = { viewModel.createPin(pin, confirmPin) },
				enabled = (pin.length in uiState.pinSize && confirmPin.length in uiState.pinSize) && !uiState.isCreatingPin,
				modifier = Modifier.fillMaxWidth()
			) {
				Text(stringResource(R.string.pin_creation_button))
			}

			// Loading indicator
			if (uiState.isCreatingPin) {
				Row(
					modifier = Modifier
						.fillMaxWidth()
						.padding(top = 16.dp),
					horizontalArrangement = Arrangement.Center,
					verticalAlignment = Alignment.CenterVertically
				) {
					CircularProgressIndicator(
						modifier = Modifier.size(24.dp),
						strokeWidth = 2.dp
					)
					Spacer(modifier = Modifier.width(8.dp))
					Text(
						text = stringResource(R.string.pin_creating_vault),
						style = MaterialTheme.typography.bodyMedium
					)
				}
			}
		}
	}

	NotificationPermissionRationale(
		title = R.string.pin_create_notification_rationale_title,
		text = R.string.pin_create_notification_rationale_text,
	)
}
