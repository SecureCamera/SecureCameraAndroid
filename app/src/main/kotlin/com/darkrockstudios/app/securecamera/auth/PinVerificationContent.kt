package com.darkrockstudios.app.securecamera.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import com.darkrockstudios.app.securecamera.R
import com.darkrockstudios.app.securecamera.navigation.NavController
import com.darkrockstudios.app.securecamera.navigation.navigateClearingBackStack
import com.darkrockstudios.app.securecamera.ui.HandleUiEvents
import org.koin.androidx.compose.koinViewModel

/**
 * Screen for PIN verification
 */
@Composable
fun PinVerificationContent(
	navController: NavController,
	snackbarHostState: SnackbarHostState,
	returnKey: NavKey,
	modifier: Modifier = Modifier
) {
	val viewModel: PinVerificationViewModel = koinViewModel()
	val uiState by viewModel.uiState.collectAsStateWithLifecycle()
	val focusRequester = remember { FocusRequester() }

	var pin by rememberSaveable { mutableStateOf("") }

	val errorMessage = when (uiState.error) {
		PinVerificationError.EMPTY_PIN -> stringResource(R.string.pin_verification_empty_error)
		PinVerificationError.INVALID_PIN -> stringResource(R.string.pin_verification_invalid_error)
		PinVerificationError.NONE -> null
	}

	LaunchedEffect(Unit) {
		viewModel.invalidateSession()
		focusRequester.requestFocus()
	}

	Box(
		modifier = modifier
			.fillMaxSize()
			.background(color = MaterialTheme.colorScheme.background)
	) {
		Column(
			modifier = Modifier
				.padding(16.dp)
				.widthIn(max = 512.dp)
				.align(Alignment.Center),
			horizontalAlignment = Alignment.CenterHorizontally,
			verticalArrangement = Arrangement.Center
		) {
			Icon(
				modifier = Modifier
					.size(96.dp)
					.padding(16.dp),
				imageVector = Icons.Filled.Camera,
				contentDescription = stringResource(id = R.string.pin_verification_icon),
				tint = MaterialTheme.colorScheme.onBackground
			)

			Text(
				text = stringResource(R.string.pin_verification_title),
				style = MaterialTheme.typography.headlineMedium,
				modifier = Modifier.padding(bottom = 24.dp)
			)

			if (uiState.failedAttempts >= 3) {
				Text(
					text = stringResource(
						R.string.pin_verification_remaining_attempts,
						AuthorizationRepository.MAX_FAILED_ATTEMPTS - uiState.failedAttempts,
						AuthorizationRepository.MAX_FAILED_ATTEMPTS
					),
					style = MaterialTheme.typography.headlineSmall,
					color = MaterialTheme.colorScheme.error,
					modifier = Modifier.padding(bottom = 12.dp)
				)
			}

			fun verifyPin() {
				viewModel.verify(
					pin = pin,
					returnKey = returnKey,
					onNavigate = { destKey ->
						navController.navigateClearingBackStack(destKey)
					},
					onFailure = { pin = "" }
				)
			}

			OutlinedTextField(
				value = pin,
				onValueChange = {
					if (viewModel.validatePin(it)) {
						pin = it
					}
				},
				label = { Text(stringResource(R.string.pin_verification_label)) },
				visualTransformation = PasswordVisualTransformation(),
				singleLine = true,
				keyboardOptions = KeyboardOptions(
					keyboardType = KeyboardType.NumberPassword,
					imeAction = ImeAction.Done
				),
				keyboardActions = KeyboardActions(
					onDone = { verifyPin() }
				),
				isError = errorMessage != null,
				enabled = !uiState.isVerifying && !uiState.isBackoffActive,
				modifier = Modifier
					.fillMaxWidth()
					.focusRequester(focusRequester)
			)

			errorMessage?.let {
				Text(
					text = it,
					color = MaterialTheme.colorScheme.error,
					style = MaterialTheme.typography.bodySmall,
					modifier = Modifier.padding(top = 4.dp),
					textAlign = TextAlign.Center,
				)
			}

			Spacer(modifier = Modifier.height(24.dp))

			val isLastAttempt = (uiState.failedAttempts >= (AuthorizationRepository.MAX_FAILED_ATTEMPTS - 1))
			val butColors = if (isLastAttempt) {
				ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer)
			} else {
				ButtonDefaults.buttonColors()
			}

			Button(
				onClick = { verifyPin() },
				enabled = !uiState.isVerifying && !uiState.isBackoffActive && (pin.length >= 4),
				modifier = Modifier.fillMaxWidth(),
				colors = butColors
			) {
				if (uiState.isBackoffActive) {
					Text(
						stringResource(
							R.string.pin_verification_verify_with_countdown,
							uiState.remainingBackoffSeconds
						)
					)
				} else {
					if (isLastAttempt) {
						Text(
							stringResource(R.string.pin_verification_verify_or_wipe),
							color = MaterialTheme.colorScheme.onErrorContainer
						)
					} else {
						Text(stringResource(R.string.pin_verification_button))
					}
				}
			}

			if (uiState.failedAttempts >= 3) {
				Text(
					text = stringResource(
						R.string.pin_verification_wipe_warning,
						AuthorizationRepository.MAX_FAILED_ATTEMPTS
					),
					style = MaterialTheme.typography.labelLarge,
					color = MaterialTheme.colorScheme.error,
					textAlign = TextAlign.Center,
					modifier = Modifier.padding(top = 8.dp)
				)
			}

			if (uiState.isVerifying) {
				CircularProgressIndicator(
					modifier = Modifier.padding(top = 16.dp)
				)
			}
		}
	}

	HandleUiEvents(viewModel.events, snackbarHostState, navController)
}
