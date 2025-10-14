package com.darkrockstudios.app.securecamera.gallery

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.darkrockstudios.app.securecamera.R
import com.darkrockstudios.app.securecamera.navigation.ImportPhotos
import com.darkrockstudios.app.securecamera.navigation.NavController
import com.darkrockstudios.app.securecamera.navigation.PhotoImportJob

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryTopNav(
	navController: NavController,
	onDeleteClick: () -> Unit = {},
	onShareClick: () -> Unit = {},
	onSelectAll: () -> Unit = {},
	isSelectionMode: Boolean = false,
	selectedCount: Int = 0,
	onCancelSelection: () -> Unit = {}
) {
	val openPhotoPicker = rememberPhotoPickerLauncher { uris ->
		navController.navigate(ImportPhotos(PhotoImportJob(uris)))
	}

	TopAppBar(
		title = {
			Text(
				if (isSelectionMode) {
					stringResource(id = R.string.gallery_selected_count, selectedCount)
				} else {
					stringResource(id = R.string.gallery_title)
				},
				color = MaterialTheme.colorScheme.onPrimaryContainer,
			)
		},
		colors = TopAppBarDefaults.topAppBarColors(
			containerColor = MaterialTheme.colorScheme.primaryContainer,
			titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
		),
		navigationIcon = {
			IconButton(
				onClick = {
					if (isSelectionMode) {
						// Exit selection mode
						onCancelSelection()
					} else {
						navController.navigateUp()
					}
				},
				modifier = Modifier.padding(8.dp)
			) {
				Icon(
					imageVector = Icons.Filled.Close,
					contentDescription = if (isSelectionMode) {
						stringResource(id = R.string.gallery_cancel_selection_content_description)
					} else {
						stringResource(id = R.string.gallery_close_content_description)
					},
					tint = MaterialTheme.colorScheme.onPrimaryContainer,
				)
			}
		},
		actions = {
			if (isSelectionMode) {
				// Show select all button
				IconButton(onClick = onSelectAll) {
					Icon(
						imageVector = Icons.Filled.SelectAll,
						contentDescription = stringResource(id = R.string.gallery_select_all_content_description),
						tint = MaterialTheme.colorScheme.onSurface
					)
				}

				if (selectedCount > 0) {
					// Show delete button
					IconButton(onClick = onDeleteClick) {
						Icon(
							imageVector = Icons.Filled.Delete,
							contentDescription = stringResource(id = R.string.gallery_delete_selected_content_description),
							tint = MaterialTheme.colorScheme.onSurface
						)
					}

					// Show share button
					IconButton(onClick = onShareClick) {
						Icon(
							imageVector = Icons.Filled.Share,
							contentDescription = stringResource(id = R.string.gallery_share_selected_content_description),
							tint = MaterialTheme.colorScheme.onSurface
						)
					}
				}
			} else {
				// Show More menu when not in selection mode
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
						text = { Text(stringResource(id = R.string.import_photos_title)) },
						onClick = {
							openPhotoPicker()
							showMoreMenu = false
						},
						leadingIcon = {
							Icon(
								imageVector = Icons.Filled.AddPhotoAlternate,
								contentDescription = null
							)
						}
					)
				}
			}
		}
	)
}