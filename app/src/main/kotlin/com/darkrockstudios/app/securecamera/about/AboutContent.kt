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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
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

		// Get URL strings (actual targets)
		val repositoryUrl = stringResource(id = R.string.about_repository_url)
		val privacyPolicyUrl = stringResource(id = R.string.about_privacy_policy_url)
		val reportBugsUrl = stringResource(id = R.string.about_report_bugs_url)

		// Display strings for URLs
		val repositoryUrlDisplay = stringResource(id = R.string.about_repository_url_display)
		val privacyPolicyUrlDisplay = stringResource(id = R.string.about_privacy_policy_url_display)
		val reportBugsUrlDisplay = stringResource(id = R.string.about_report_bugs_url_display)


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
			// App description + website
			SectionCard(
				elevation = 0.dp
			) {
				Icon(
					painter = painterResource(id = R.drawable.ic_launcher_foreground),
					contentDescription = stringResource(id = R.string.app_name),
					tint = MaterialTheme.colorScheme.onSurface,
					modifier = Modifier
						.size(128.dp)
						.align(Alignment.CenterHorizontally)
				)

				Text(
					text = stringResource(id = R.string.about_description),
					style = MaterialTheme.typography.bodyLarge
				)
				Spacer(modifier = Modifier.height(8.dp))
				val websiteUrl = stringResource(id = R.string.about_promo_url)
				val websiteUrlDisplay = stringResource(id = R.string.about_promo_url_display)
				Text(
					text = websiteUrlDisplay,
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.primary,
					textDecoration = TextDecoration.Underline,
					modifier = Modifier.clickable { openUrl(context, websiteUrl) }
				)
			}

			Spacer(modifier = Modifier.height(12.dp))

			// Community section
			SectionCard {
				Text(
					text = stringResource(R.string.about_community),
					style = MaterialTheme.typography.titleMedium
				)
				Spacer(modifier = Modifier.height(8.dp))
				Text(
					text = stringResource(id = R.string.about_community_description),
					style = MaterialTheme.typography.bodyLarge
				)
				Spacer(modifier = Modifier.height(8.dp))
				val discordUrl = stringResource(id = R.string.about_discord_url)
				val discordUrlDisplay = stringResource(id = R.string.about_discord_url_display)
				Text(
					text = discordUrlDisplay,
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.primary,
					textDecoration = TextDecoration.Underline,
					modifier = Modifier.clickable { openUrl(context, discordUrl) }
				)
			}

			Spacer(modifier = Modifier.height(12.dp))

			// Open Source section
			SectionCard {
				Text(
					text = stringResource(id = R.string.about_open_source),
					style = MaterialTheme.typography.titleMedium
				)
				Spacer(modifier = Modifier.height(8.dp))
				Text(
					text = stringResource(id = R.string.about_open_source_description),
					style = MaterialTheme.typography.bodyLarge
				)
				Spacer(modifier = Modifier.height(8.dp))
				Text(
					text = repositoryUrlDisplay,
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.primary,
					textDecoration = TextDecoration.Underline,
					modifier = Modifier.clickable { openUrl(context, repositoryUrl) }
				)
			}

			Spacer(modifier = Modifier.height(12.dp))

			// Privacy Policy section
			SectionCard {
				Text(
					text = stringResource(id = R.string.about_privacy_policy),
					style = MaterialTheme.typography.titleMedium
				)
				Spacer(modifier = Modifier.height(8.dp))
				Text(
					text = stringResource(id = R.string.about_privacy_policy_description),
					style = MaterialTheme.typography.bodyLarge
				)
				Spacer(modifier = Modifier.height(8.dp))
				Text(
					text = privacyPolicyUrlDisplay,
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.primary,
					textDecoration = TextDecoration.Underline,
					modifier = Modifier.clickable { openUrl(context, privacyPolicyUrl) }
				)
			}

			Spacer(modifier = Modifier.height(12.dp))

			// Report Bugs section
			SectionCard {
				Text(
					text = stringResource(id = R.string.about_report_bugs),
					style = MaterialTheme.typography.titleMedium
				)
				Spacer(modifier = Modifier.height(8.dp))
				Text(
					text = stringResource(id = R.string.about_report_bugs_description),
					style = MaterialTheme.typography.bodyLarge
				)
				Spacer(modifier = Modifier.height(8.dp))
				Text(
					text = reportBugsUrlDisplay,
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.primary,
					textDecoration = TextDecoration.Underline,
					modifier = Modifier.clickable { openUrl(context, reportBugsUrl) }
				)
			}

			Spacer(modifier = Modifier.height(12.dp))

			// Version info
			SectionCard {
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
			}

			Spacer(modifier = Modifier.height(24.dp))
		}
	}
}

@Composable
private fun SectionCard(
	modifier: Modifier = Modifier,
	elevation: Dp = 4.dp,
	content: @Composable ColumnScope.() -> Unit
) {
	Card(
		modifier = modifier.fillMaxWidth(),
		colors = CardDefaults.cardColors(
			containerColor = MaterialTheme.colorScheme.surface
		),
		shape = MaterialTheme.shapes.medium,
		elevation = CardDefaults.cardElevation(defaultElevation = elevation)
	) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.padding(16.dp)
		) {
			content()
		}
	}
}

private fun openUrl(context: android.content.Context, url: String) {
	val intent = Intent(Intent.ACTION_VIEW, url.toUri())
	context.startActivity(intent)
}
