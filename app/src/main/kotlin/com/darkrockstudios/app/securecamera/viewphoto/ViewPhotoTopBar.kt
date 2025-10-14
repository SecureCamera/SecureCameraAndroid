package com.darkrockstudios.app.securecamera.viewphoto

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.darkrockstudios.app.securecamera.R
import com.darkrockstudios.app.securecamera.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewPhotoTopBar(
	navController: NavController,
	onDeleteClick: () -> Unit,
	onShareClick: () -> Unit,
	onInfoClick: () -> Unit,
	onObfuscateClick: () -> Unit,
	showDecoyButton: Boolean = false,
	isDecoy: Boolean = false,
	isDecoyLoading: Boolean = false,
	onDecoyClick: () -> Unit = {},
) {
	TopAppBar(
		title = {
			Text(
				stringResource(id = R.string.photo_title),
				color = MaterialTheme.colorScheme.onPrimaryContainer,
			)
		},
		colors = TopAppBarDefaults.topAppBarColors(
			containerColor = MaterialTheme.colorScheme.primaryContainer,
			titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
		),
		navigationIcon = {
			IconButton(
				onClick = { navController.navigateUp() },
				modifier = Modifier.padding(8.dp)
			) {
				Icon(
					imageVector = Icons.Filled.Close,
					contentDescription = stringResource(id = R.string.close_photo_content_description),
					tint = MaterialTheme.colorScheme.onPrimaryContainer,
				)
			}
		},
		actions = {
			if (showDecoyButton && isDecoyLoading) {
				CircularProgressIndicator(
					modifier = Modifier.size(24.dp),
					color = MaterialTheme.colorScheme.onPrimaryContainer,
					strokeWidth = 2.dp
				)
			}
			IconButton(
				onClick = onShareClick,
				modifier = Modifier.padding(8.dp)
			) {
				Icon(
					imageVector = Icons.Filled.Share,
					contentDescription = stringResource(id = R.string.share_photo_content_description),
					tint = MaterialTheme.colorScheme.onPrimaryContainer,
				)
			}

			var showMoreMenu by remember { mutableStateOf(false) }

			IconButton(
				onClick = { showMoreMenu = true },
				modifier = Modifier.padding(8.dp)
			) {
				Icon(
					imageVector = Icons.Filled.MoreVert,
					contentDescription = stringResource(id = R.string.camera_more_options_content_description),
					tint = MaterialTheme.colorScheme.onPrimaryContainer,
				)
			}

			DropdownMenu(
				expanded = showMoreMenu,
				onDismissRequest = { showMoreMenu = false }
			) {
				DropdownMenuItem(
					text = { Text(stringResource(id = R.string.info_button)) },
					onClick = {
						onInfoClick()
						showMoreMenu = false
					},
					leadingIcon = {
						Icon(
							imageVector = Icons.Filled.Info,
							contentDescription = null
						)
					}
				)
				if (showDecoyButton) {
					DropdownMenuItem(
						text = {
							if (isDecoy) {
								Text(stringResource(id = R.string.decoy_photo_remove_menu_item))
							} else {
								Text(stringResource(id = R.string.decoy_photo_set_menu_item))
							}
						},
						enabled = isDecoyLoading.not(),
						onClick = {
							onDecoyClick()
							showMoreMenu = false
						},
						leadingIcon = {
							Icon(
								imageVector = if (isDecoy) Icons.Filled.RemoveCircleOutline else Icons.Filled.AddCircle,
								contentDescription = null
							)
						}
					)
				}

				DropdownMenuItem(
					text = { Text(stringResource(id = R.string.obfuscate_photo_button)) },
					onClick = {
						onObfuscateClick()
						showMoreMenu = false
					},
					leadingIcon = {
						Icon(
							imageVector = Icons.Filled.BlurOn,
							contentDescription = null
						)
					}
				)
				DropdownMenuItem(
					text = { Text(stringResource(id = R.string.delete_button)) },
					onClick = {
						onDeleteClick()
						showMoreMenu = false
					},
					leadingIcon = {
						Icon(
							imageVector = Icons.Filled.Delete,
							contentDescription = null
						)
					}
				)
			}
		}
	)
}
