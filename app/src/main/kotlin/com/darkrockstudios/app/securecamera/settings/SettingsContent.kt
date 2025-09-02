package com.darkrockstudios.app.securecamera.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darkrockstudios.app.securecamera.LocationPermissionStatus
import com.darkrockstudios.app.securecamera.R
import com.darkrockstudios.app.securecamera.navigation.About
import com.darkrockstudios.app.securecamera.navigation.Introduction
import com.darkrockstudios.app.securecamera.navigation.NavController
import com.darkrockstudios.app.securecamera.navigation.navigateClearingBackStack
import com.darkrockstudios.app.securecamera.preferences.AppPreferencesDataSourceImpl.Companion.SESSION_TIMEOUT_10_MIN
import com.darkrockstudios.app.securecamera.preferences.AppPreferencesDataSourceImpl.Companion.SESSION_TIMEOUT_1_MIN
import com.darkrockstudios.app.securecamera.preferences.AppPreferencesDataSourceImpl.Companion.SESSION_TIMEOUT_5_MIN
import com.darkrockstudios.app.securecamera.security.SecurityLevel
import com.darkrockstudios.app.securecamera.ui.HandleUiEvents
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/**
 * Settings screen content
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(
	navController: NavController,
	modifier: Modifier = Modifier,
	paddingValues: PaddingValues,
	snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
	val viewModel: SettingsViewModel = koinViewModel()
	val uiState by viewModel.uiState.collectAsStateWithLifecycle()
	val coroutineScope = rememberCoroutineScope()
	val context = LocalContext.current

	LaunchedEffect(uiState.securityResetComplete) {
		if (uiState.securityResetComplete) {
			navController.navigateClearingBackStack(Introduction)
		}
	}

	if (uiState.showLocationDialog) {
		LocationDialog(uiState.locationPermissionStatus, context) {
			viewModel.dismissLocationDialog()
		}
	}

	Column(
		modifier = modifier
			.fillMaxSize()
			.background(MaterialTheme.colorScheme.background),
		horizontalAlignment = Alignment.CenterHorizontally,
	) {
		TopAppBar(
			title = {
				Text(
					text = stringResource(id = R.string.settings_title),
					color = MaterialTheme.colorScheme.onPrimaryContainer
				)
			},
			colors = TopAppBarDefaults.topAppBarColors(
				containerColor = MaterialTheme.colorScheme.primaryContainer,
				titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
			),
			navigationIcon = {
				IconButton(onClick = { navController.popBackStack() }) {
					Icon(
						imageVector = Icons.AutoMirrored.Filled.ArrowBack,
						contentDescription = stringResource(id = R.string.settings_back_description),
						tint = MaterialTheme.colorScheme.onPrimaryContainer
					)
				}
			},
			actions = {
				IconButton(
					onClick = { navController.navigate(About) },
					modifier = Modifier.padding(8.dp)
				) {
					Icon(
						imageVector = Icons.Filled.Info,
						contentDescription = stringResource(id = R.string.settings_about_button),
						tint = MaterialTheme.colorScheme.onPrimaryContainer,
					)
				}
			}
		)

		// Settings content
		Column(
			modifier = Modifier
				.widthIn(max = 512.dp)
				.fillMaxSize()
				.verticalScroll(rememberScrollState())
				.padding(
					start = 16.dp,
					end = 16.dp,
					bottom = paddingValues.calculateBottomPadding(),
					top = 8.dp
				),
			verticalArrangement = Arrangement.Top,
			horizontalAlignment = Alignment.Start
		) {
			Spacer(modifier = Modifier.height(24.dp))

			// Sharing section
			Text(
				text = stringResource(id = R.string.settings_sharing_section),
				style = MaterialTheme.typography.titleLarge
			)

			Spacer(modifier = Modifier.height(8.dp))

			// Sanitize file name checkbox
			Row(
				modifier = Modifier.fillMaxWidth(),
				verticalAlignment = Alignment.CenterVertically
			) {
				Column(modifier = Modifier.weight(1f)) {
					Text(
						text = stringResource(id = R.string.settings_sanitize_filename),
						style = MaterialTheme.typography.bodyLarge
					)
					Text(
						text = stringResource(id = R.string.settings_sanitize_filename_description),
						style = MaterialTheme.typography.bodyMedium,
						color = MaterialTheme.colorScheme.onSurfaceVariant
					)
				}
				Switch(
					checked = uiState.sanitizeFileName,
					onCheckedChange = { checked ->
						viewModel.setSanitizeFileName(checked)
					}
				)
			}

			Spacer(modifier = Modifier.height(16.dp))

			// Sanitize metadata checkbox
			Row(
				modifier = Modifier.fillMaxWidth(),
				verticalAlignment = Alignment.CenterVertically
			) {
				Column(modifier = Modifier.weight(1f)) {
					Text(
						text = stringResource(id = R.string.settings_sanitize_metadata),
						style = MaterialTheme.typography.bodyLarge
					)
					Text(
						text = stringResource(id = R.string.settings_sanitize_metadata_description),
						style = MaterialTheme.typography.bodyMedium,
						color = MaterialTheme.colorScheme.onSurfaceVariant
					)
				}
				Switch(
					checked = uiState.sanitizeMetadata,
					onCheckedChange = { checked ->
						viewModel.setSanitizeMetadata(checked)
					}
				)
			}

			Spacer(modifier = Modifier.height(24.dp))

			// Location section
			Text(
				text = stringResource(id = R.string.settings_location_section),
				style = MaterialTheme.typography.titleLarge
			)

			Spacer(modifier = Modifier.height(8.dp))

			// Location permission status
			Row(
				modifier = Modifier
					.fillMaxWidth()
					.padding(top = 8.dp, bottom = 8.dp)
					.clickable { viewModel.showLocationDialog() },
				verticalAlignment = Alignment.CenterVertically
			) {
				Column(modifier = Modifier.weight(1f)) {
					Text(
						text = stringResource(id = R.string.settings_location_status),
						style = MaterialTheme.typography.bodyLarge
					)
					Text(
						text = stringResource(id = R.string.settings_location_description),
						style = MaterialTheme.typography.bodyMedium,
						color = MaterialTheme.colorScheme.onSurfaceVariant
					)
				}
				Text(
					text = when (uiState.locationPermissionStatus) {
						LocationPermissionStatus.DENIED -> stringResource(id = R.string.settings_location_status_denied)
						LocationPermissionStatus.COARSE -> stringResource(id = R.string.settings_location_status_coarse)
						LocationPermissionStatus.FINE -> stringResource(id = R.string.settings_location_status_fine)
					},
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.primary
				)
			}

			Spacer(modifier = Modifier.height(24.dp))

			// Security section
			Text(
				text = stringResource(id = R.string.settings_security_section),
				style = MaterialTheme.typography.titleLarge
			)

			Spacer(modifier = Modifier.height(8.dp))

			// Security status row
			Row(
				modifier = Modifier
					.fillMaxWidth()
					.padding(top = 8.dp, bottom = 8.dp),
				verticalAlignment = Alignment.CenterVertically
			) {
				Column(modifier = Modifier.weight(1f)) {
					Text(
						text = stringResource(id = R.string.settings_security_status),
						style = MaterialTheme.typography.bodyLarge
					)
					Text(
						text = stringResource(id = R.string.settings_security_status_description),
						style = MaterialTheme.typography.bodyMedium,
						color = MaterialTheme.colorScheme.onSurfaceVariant
					)
				}
				Text(
					text = when (uiState.securityLevel) {
						SecurityLevel.STRONGBOX -> stringResource(id = R.string.security_intro_security_level_strong)
						SecurityLevel.TEE -> stringResource(id = R.string.security_intro_security_level_normal)
						SecurityLevel.SOFTWARE -> stringResource(id = R.string.security_intro_security_level_weak)
					},
					style = MaterialTheme.typography.bodyMedium,
					fontWeight = FontWeight.Bold,
					color = when (uiState.securityLevel) {
						SecurityLevel.STRONGBOX -> Color.Green
						SecurityLevel.TEE -> MaterialTheme.colorScheme.primary
						SecurityLevel.SOFTWARE -> Color.Red
					}
				)
			}

			Spacer(modifier = Modifier.height(16.dp))

			// Session timeout dropdown
			Row(
				modifier = Modifier
					.fillMaxWidth()
					.padding(top = 8.dp, bottom = 8.dp),
				verticalAlignment = Alignment.CenterVertically
			) {
				Column(modifier = Modifier.weight(1f)) {
					Text(
						text = stringResource(id = R.string.settings_session_timeout),
						style = MaterialTheme.typography.bodyLarge
					)
					Text(
						text = stringResource(id = R.string.settings_session_timeout_description),
						style = MaterialTheme.typography.bodyMedium,
						color = MaterialTheme.colorScheme.onSurfaceVariant
					)
				}

				// Dropdown menu for session timeout
				var expanded by remember { mutableStateOf(false) }
				Box {
					Text(
						text = when (uiState.sessionTimeout) {
							SESSION_TIMEOUT_1_MIN -> stringResource(id = R.string.settings_session_timeout_1_min)
							SESSION_TIMEOUT_5_MIN -> stringResource(id = R.string.settings_session_timeout_5_min)
							SESSION_TIMEOUT_10_MIN -> stringResource(id = R.string.settings_session_timeout_10_min)
							else -> stringResource(id = R.string.settings_session_timeout_5_min)
						},
						modifier = Modifier
							.clickable { expanded = true }
							.padding(8.dp),
						color = MaterialTheme.colorScheme.primary
					)
					DropdownMenu(
						expanded = expanded,
						onDismissRequest = { expanded = false }
					) {
						DropdownMenuItem(
							text = { Text(stringResource(id = R.string.settings_session_timeout_1_min)) },
							onClick = {
								viewModel.setSessionTimeout(SESSION_TIMEOUT_1_MIN)
								expanded = false
							}
						)
						DropdownMenuItem(
							text = { Text(stringResource(id = R.string.settings_session_timeout_5_min)) },
							onClick = {
								viewModel.setSessionTimeout(SESSION_TIMEOUT_5_MIN)
								expanded = false
							}
						)
						DropdownMenuItem(
							text = { Text(stringResource(id = R.string.settings_session_timeout_10_min)) },
							onClick = {
								viewModel.setSessionTimeout(SESSION_TIMEOUT_10_MIN)
								expanded = false
							}
						)
					}
				}
			}

			Spacer(modifier = Modifier.height(16.dp))

			// Poison Pill option
			Row(
				modifier = Modifier
					.fillMaxWidth()
					.padding(top = 8.dp, bottom = 8.dp)
					.clickable {
						if (uiState.hasPoisonPillPin) {
							viewModel.showRemovePoisonPillDialog()
						} else {
							viewModel.showPoisonPillDialog()
						}
					},
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.SpaceBetween
			) {
				Column(modifier = Modifier.weight(1f)) {
					Text(
						text = stringResource(
							id = if (uiState.hasPoisonPillPin) {
								R.string.settings_remove_poison_pill
							} else {
								R.string.settings_poison_pill
							}
						),
						style = MaterialTheme.typography.bodyLarge
					)
					Text(
						text = stringResource(
							id = if (uiState.hasPoisonPillPin) {
								R.string.settings_remove_poison_pill_description
							} else {
								R.string.settings_poison_pill_description
							}
						),
						style = MaterialTheme.typography.bodyMedium,
						color = MaterialTheme.colorScheme.onSurfaceVariant
					)
				}
			}

			Spacer(modifier = Modifier.height(16.dp))

			// Security Reset option
			Row(
				modifier = Modifier
					.fillMaxWidth()
					.padding(top = 8.dp, bottom = 8.dp)
					.clickable { viewModel.showSecurityResetDialog() },
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.SpaceBetween
			) {
				Column(modifier = Modifier.weight(1f)) {
					Text(
						text = stringResource(id = R.string.settings_security_reset),
						style = MaterialTheme.typography.bodyLarge
					)
					Text(
						text = stringResource(id = R.string.settings_security_reset_description),
						style = MaterialTheme.typography.bodyMedium,
						color = MaterialTheme.colorScheme.onSurfaceVariant
					)
				}
				Icon(
					imageVector = Icons.Filled.Warning,
					contentDescription = null,
					tint = MaterialTheme.colorScheme.error,
					modifier = Modifier.size(32.dp)
				)
			}

			Spacer(modifier = Modifier.height(24.dp))
		}
	}

	if (uiState.showSecurityResetDialog) {
		val msg = stringResource(R.string.security_reset_complete_toast)
		SecurityResetDialog(
			onDismiss = { viewModel.dismissSecurityResetDialog() },
			onConfirm = {
				coroutineScope.launch {
					viewModel.performSecurityReset()
					snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Long)
				}
			}
		)
	}

	if (uiState.showPoisonPillDialog) {
		PoisonPillDialog(
			onDismiss = { viewModel.dismissPoisonPillDialog() },
			onContinue = {
				viewModel.showPoisonPillPinCreationDialog()
			}
		)
	}

	if (uiState.showPoisonPillPinCreationDialog) {
		PoisonPillPinCreationDialog(
			viewModel = viewModel,
			onDismiss = { viewModel.dismissPoisonPillPinCreationDialog() },
			onPinCreated = { pin ->
				viewModel.setPoisonPillPin(pin)
			}
		)
	}

	if (uiState.showDecoyPhotoExplanationDialog) {
		val setupCompleteMsg = stringResource(R.string.poison_pill_setup_complete)
		DecoyPhotoExplanationDialog(
			onDismiss = {
				viewModel.dismissDecoyPhotoExplanationDialog()
				coroutineScope.launch {
					snackbarHostState.showSnackbar(
						setupCompleteMsg,
						duration = SnackbarDuration.Long
					)
				}
			}
		)
	}

	if (uiState.showRemovePoisonPillDialog) {
		val removeCompleteMsg = stringResource(R.string.remove_poison_pill_complete)
		RemovePoisonPillDialog(
			onDismiss = { viewModel.dismissRemovePoisonPillDialog() },
			onConfirm = {
				viewModel.removePoisonPillPin()
				coroutineScope.launch {
					snackbarHostState.showSnackbar(
						removeCompleteMsg,
						duration = SnackbarDuration.Long
					)
				}
			}
		)
	}

	HandleUiEvents(viewModel.events, snackbarHostState, navController)
}
