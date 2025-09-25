package com.darkrockstudios.app.securecamera.about

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darkrockstudios.app.securecamera.R
import com.darkrockstudios.app.securecamera.navigation.NavController
import org.koin.androidx.compose.koinViewModel

/**
 * About screen content
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutContent(
	navController: NavController,
	modifier: Modifier = Modifier,
	paddingValues: PaddingValues,
	viewModel: AboutViewModel = koinViewModel<AboutViewModelImpl>()
) {
	Column(
		modifier = modifier
			.fillMaxSize()
			.background(MaterialTheme.colorScheme.background)
	) {
		TopAppBar(
			title = {
				Text(
					text = stringResource(id = R.string.about_title),
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
						contentDescription = stringResource(id = R.string.about_back_description),
						tint = MaterialTheme.colorScheme.onPrimaryContainer
					)
				}
			}
		)

		val context = LocalContext.current
		val uiState by viewModel.uiState.collectAsStateWithLifecycle()

		// About content
		Column(
			modifier = Modifier
				.fillMaxSize()
				.padding(
					start = 16.dp,
					end = 16.dp,
					bottom = paddingValues.calculateBottomPadding(),
					top = 8.dp
				)
				.verticalScroll(rememberScrollState()),
			verticalArrangement = Arrangement.Top,
			horizontalAlignment = Alignment.Start
		) {
			Spacer(modifier = Modifier.height(24.dp))

			// App description
			Text(
				text = stringResource(id = R.string.about_description),
				style = MaterialTheme.typography.bodyLarge
			)

			Spacer(modifier = Modifier.height(24.dp))

			val websiteUrl = stringResource(id = R.string.about_promo_url)
			Text(
				text = websiteUrl,
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.primary,
				textDecoration = TextDecoration.Underline,
				modifier = Modifier.clickable {
					openUrl(context, websiteUrl)
				}
			)

			Spacer(modifier = Modifier.height(24.dp))

			// Open Source section
			Text(
				text = stringResource(id = R.string.about_open_source),
				style = MaterialTheme.typography.titleMedium
			)

			Spacer(modifier = Modifier.height(8.dp))

			Text(
				text = stringResource(id = R.string.about_open_source_description),
				style = MaterialTheme.typography.bodyLarge
			)

			// Get URL strings
			val repositoryUrl = stringResource(id = R.string.about_repository_url)
			val privacyPolicyUrl = stringResource(id = R.string.about_privacy_policy_url)
			val reportBugsUrl = stringResource(id = R.string.about_report_bugs_url)

			// Repository link
			Text(
				text = repositoryUrl,
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.primary,
				textDecoration = TextDecoration.Underline,
				modifier = Modifier.clickable {
					openUrl(context, repositoryUrl)
				}
			)

			Spacer(modifier = Modifier.height(24.dp))

			// Privacy Policy section
			Text(
				text = stringResource(id = R.string.about_privacy_policy),
				style = MaterialTheme.typography.titleMedium
			)

			Spacer(modifier = Modifier.height(8.dp))

			Text(
				text = stringResource(id = R.string.about_privacy_policy_description),
				style = MaterialTheme.typography.bodyLarge
			)

			// Privacy Policy link
			Text(
				text = privacyPolicyUrl,
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.primary,
				textDecoration = TextDecoration.Underline,
				modifier = Modifier.clickable {
					openUrl(context, privacyPolicyUrl)
				}
			)

			Spacer(modifier = Modifier.height(24.dp))

			// Report Bugs section
			Text(
				text = stringResource(id = R.string.about_report_bugs),
				style = MaterialTheme.typography.titleMedium
			)

			Spacer(modifier = Modifier.height(8.dp))

			Text(
				text = stringResource(id = R.string.about_report_bugs_description),
				style = MaterialTheme.typography.bodyLarge
			)

			// Report Bugs link
			Text(
				text = reportBugsUrl,
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.primary,
				textDecoration = TextDecoration.Underline,
				modifier = Modifier.clickable {
					openUrl(context, reportBugsUrl)
				}
			)

			Spacer(modifier = Modifier.height(24.dp))

			// Version info
			Row(
				modifier = Modifier.fillMaxWidth(),
				verticalAlignment = Alignment.CenterVertically
			) {
				Text(
					text = stringResource(id = R.string.about_version),
					style = MaterialTheme.typography.bodyLarge,
					modifier = Modifier.weight(1f)
				)
				Text(
					text = uiState.versionName,
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.primary
				)
			}

			Spacer(modifier = Modifier.height(24.dp))
		}
	}
}

private fun openUrl(context: android.content.Context, url: String) {
	val intent = Intent(Intent.ACTION_VIEW, url.toUri())
	context.startActivity(intent)
}
