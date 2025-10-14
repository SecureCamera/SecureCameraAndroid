package com.darkrockstudios.app.securecamera.introduction

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darkrockstudios.app.securecamera.R
import com.darkrockstudios.app.securecamera.security.SecurityLevel

@Composable
fun SecurityContent(modifier: Modifier, viewModel: IntroductionViewModel) {
	val uiState by viewModel.uiState.collectAsStateWithLifecycle()
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
				imageVector = Icons.Filled.Lock,
				contentDescription = stringResource(id = R.string.security_intro_icon),
				tint = MaterialTheme.colorScheme.onBackground
			)

			Text(
				text = stringResource(R.string.security_intro_supported_security_label),
				style = MaterialTheme.typography.headlineMedium,
				textAlign = TextAlign.Center,
				modifier = Modifier.padding(bottom = 8.dp)
			)

			when (uiState.securityLevel) {
				SecurityLevel.STRONGBOX -> {
					StrongSecurityContent(viewModel = viewModel)
				}

				SecurityLevel.TEE -> {
					TeeSecurityContent(viewModel = viewModel)
				}

				SecurityLevel.SOFTWARE -> {
					SoftwareSecurityContent(viewModel = viewModel)
				}
			}
		}
	}
}

@Composable
fun ColumnScope.StrongSecurityContent(modifier: Modifier = Modifier, viewModel: IntroductionViewModel) {
	Text(
		text = stringResource(R.string.security_intro_security_level_strong),
		style = MaterialTheme.typography.headlineMedium,
		fontWeight = FontWeight.Bold,
		color = Color.Green,
		textAlign = TextAlign.Center,
		modifier = Modifier.padding(bottom = 8.dp)
	)

	Text(
		text = stringResource(R.string.security_intro_security_level_strong_explainer),
		style = MaterialTheme.typography.bodyLarge,
		fontWeight = FontWeight.Bold,
		modifier = Modifier.padding(bottom = 8.dp)
	)

	AdvancedHardwareSection(viewModel)
}

@Composable
fun ColumnScope.TeeSecurityContent(viewModel: IntroductionViewModel) {
	Text(
		text = stringResource(R.string.security_intro_security_level_normal),
		style = MaterialTheme.typography.headlineMedium,
		fontWeight = FontWeight.Bold,
		textAlign = TextAlign.Center,
		modifier = Modifier.padding(bottom = 8.dp)
	)

	Text(
		text = stringResource(R.string.security_intro_security_level_normal_explainer),
		style = MaterialTheme.typography.bodyLarge,
		modifier = Modifier.padding(bottom = 8.dp)
	)

	AdvancedHardwareSection(viewModel)
}

@Composable
fun ColumnScope.SoftwareSecurityContent(viewModel: IntroductionViewModel) {
	Text(
		text = stringResource(R.string.security_intro_security_level_weak),
		style = MaterialTheme.typography.headlineMedium,
		color = Color.Red,
		textAlign = TextAlign.Center,
		fontWeight = FontWeight.Bold,
		fontStyle = FontStyle.Italic,
		modifier = Modifier.padding(bottom = 8.dp)
	)

	Text(
		text = stringResource(R.string.security_intro_security_level_weak_explainer),
		style = MaterialTheme.typography.bodyLarge,
		modifier = Modifier.padding(bottom = 8.dp)
	)
}

@Composable
fun ColumnScope.AdvancedHardwareSection(viewModel: IntroductionViewModel) {
	val uiState by viewModel.uiState.collectAsStateWithLifecycle()
	var expanded by remember { mutableStateOf(false) }
	// Advanced section header
	Card(modifier = Modifier.padding(16.dp)) {
		Row(
			modifier = Modifier
				.clickable { expanded = !expanded }
				.fillMaxWidth()
				.padding(8.dp),
			verticalAlignment = Alignment.CenterVertically
		) {
			Text(
				text = stringResource(R.string.security_intro_advanced_section),
				style = MaterialTheme.typography.bodyLarge,
				fontWeight = FontWeight.Bold,
				modifier = Modifier.padding(vertical = 8.dp)
			)
			Icon(
				imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
				contentDescription = if (expanded) stringResource(R.string.security_intro_collapse) else stringResource(
					R.string.security_intro_expand
				)
			)
		}

		// Expandable content
		AnimatedVisibility(
			visible = expanded,
			enter = expandVertically(animationSpec = tween(300)),
			exit = shrinkVertically(animationSpec = tween(300))
		) {
			Column(modifier = Modifier.padding(8.dp)) {
				Text(
					text = stringResource(R.string.security_intro_string_ephemeral_label),
					style = MaterialTheme.typography.headlineSmall,
					modifier = Modifier.padding(start = 8.dp, bottom = 16.dp, end = 8.dp),
				)

				Row(verticalAlignment = Alignment.Top) {
					Checkbox(
						modifier = Modifier.padding(bottom = 8.dp),
						checked = uiState.ephemeralKey,
						onCheckedChange = { viewModel.toggleEphemeralKey() },
					)

					Text(
						text = stringResource(R.string.security_intro_string_ephemeral_explainer),
						style = MaterialTheme.typography.bodyMedium,
						modifier = Modifier.padding(bottom = 8.dp),
					)
				}
//				Text(
//					text = stringResource(R.string.security_intro_string_biometric_explainer),
//					style = MaterialTheme.typography.bodyLarge,
//					modifier = Modifier.padding(bottom = 8.dp),
//				)
//
//				Row(verticalAlignment = Alignment.CenterVertically) {
//					Checkbox(
//						modifier = Modifier.padding(bottom = 8.dp),
//						checked = uiState.requireBiometrics,
//						onCheckedChange = { viewModel.toggleBiometricsRequired() },
//					)
//
//					Text(
//						text = stringResource(R.string.security_intro_string_biometric_checkbox),
//						style = MaterialTheme.typography.bodyLarge,
//						modifier = Modifier.padding(bottom = 8.dp),
//					)
//				}
			}
		}
	}
}
