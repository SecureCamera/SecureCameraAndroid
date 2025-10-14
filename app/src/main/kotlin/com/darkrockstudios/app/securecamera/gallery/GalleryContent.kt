package com.darkrockstudios.app.securecamera.gallery

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darkrockstudios.app.securecamera.ConfirmDeletePhotoDialog
import com.darkrockstudios.app.securecamera.R
import com.darkrockstudios.app.securecamera.camera.PhotoDef
import com.darkrockstudios.app.securecamera.camera.SecureImageRepository
import com.darkrockstudios.app.securecamera.navigation.NavController
import com.darkrockstudios.app.securecamera.navigation.ViewPhoto
import com.darkrockstudios.app.securecamera.ui.HandleUiEvents
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryContent(
	modifier: Modifier = Modifier,
	navController: NavController,
	paddingValues: PaddingValues,
	snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
	val context = LocalContext.current
	val viewModel: GalleryViewModel = koinViewModel()
	val uiState by viewModel.uiState.collectAsStateWithLifecycle()

	LaunchedEffect(Unit) {
		viewModel.loadPhotos()
	}

	val startSelectionWithVibration = { photoName: String ->
		viewModel.startSelectionMode(photoName)
		vibrateDevice(context)
	}

	Column(
		modifier = modifier
			.fillMaxSize()
			.background(MaterialTheme.colorScheme.background)
	) {
		GalleryTopNav(
			navController = navController,
			onDeleteClick = { viewModel.showDeleteConfirmation() },
			onShareClick = { viewModel.shareSelectedPhotos(context) },
			onSelectAll = { viewModel.selectAllPhotos() },
			isSelectionMode = uiState.isSelectionMode,
			selectedCount = uiState.selectedPhotos.size,
			onCancelSelection = { viewModel.clearSelection() }
		)

		if (uiState.showDeleteConfirmation) {
			ConfirmDeletePhotoDialog(
				selectedCount = uiState.selectedPhotos.size,
				onConfirm = { viewModel.deleteSelectedPhotos() },
				onDismiss = { viewModel.dismissDeleteConfirmation() }
			)
		}

		Box(
			modifier = Modifier
				.padding(
					start = paddingValues.calculateStartPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
					end = paddingValues.calculateEndPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
					bottom = 0.dp,
					top = 8.dp
				)
				.fillMaxSize(),
			contentAlignment = Alignment.Center
		) {
			if (uiState.isLoading) {
				Text(text = stringResource(id = R.string.gallery_loading))
			} else if (uiState.photos.isEmpty()) {
				Text(text = stringResource(id = R.string.gallery_empty))
			} else {
				PhotoGrid(
					photos = uiState.photos,
					paddingValues = paddingValues,
					selectedPhotoNames = uiState.selectedPhotos,
					onPhotoLongClick = startSelectionWithVibration,
					onPhotoClick = { photoName ->
						if (uiState.isSelectionMode) {
							viewModel.togglePhotoSelection(photoName)
						} else {
							navController.navigate(ViewPhoto(photoName))
						}
					},
				)
			}
		}
	}

	HandleUiEvents(viewModel.events, snackbarHostState, navController)
}

@Composable
private fun PhotoGrid(
	photos: List<PhotoDef>,
	modifier: Modifier = Modifier,
	paddingValues: PaddingValues,
	selectedPhotoNames: Set<String> = emptySet(),
	onPhotoLongClick: (String) -> Unit = {},
	onPhotoClick: (String) -> Unit = {},
) {
	val limitedDispatcher = remember {
		Dispatchers.IO.limitedParallelism(4) // Limit to 4 concurrent thumbnail loads
	}

	val imageManager = koinInject<SecureImageRepository>()
	val scope = rememberCoroutineScope()
	LazyVerticalGrid(
		columns = GridCells.Adaptive(minSize = 128.dp),
		contentPadding = PaddingValues(
			start = 8.dp,
			end = 8.dp,
			bottom = paddingValues.calculateBottomPadding(),
			top = 0.dp
		),
		horizontalArrangement = Arrangement.spacedBy(8.dp),
		verticalArrangement = Arrangement.spacedBy(8.dp),
		modifier = modifier.fillMaxSize()
	) {
		items(items = photos, key = { it.photoName }) { photo ->
			PhotoItem(
				photo = photo,
				imageManager = imageManager,
				scope = scope,
				limitedDispatcher = limitedDispatcher,
				isSelected = selectedPhotoNames.contains(photo.photoName),
				onLongClick = { onPhotoLongClick(photo.photoName) },
				onClick = { onPhotoClick(photo.photoName) }
			)
		}
	}
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun PhotoItem(
	photo: PhotoDef,
	imageManager: SecureImageRepository,
	scope: CoroutineScope,
	limitedDispatcher: CoroutineDispatcher,
	isSelected: Boolean = false,
	onLongClick: () -> Unit = {},
	onClick: () -> Unit = {},
	modifier: Modifier = Modifier
) {
	var thumbnailBitmap by remember(photo.photoName) { mutableStateOf<ImageBitmap?>(null) }
	val isDecoy = remember(photo) { imageManager.isDecoyPhoto(photo) }

	val imageAlpha by animateFloatAsState(
		targetValue = if (thumbnailBitmap != null) 1f else 0f,
		animationSpec = tween(durationMillis = 500),
		label = "imageAlpha"
	)

	LaunchedEffect(photo.photoName) {
		if (thumbnailBitmap == null) {
			scope.launch(limitedDispatcher) {
				thumbnailBitmap = imageManager.readThumbnail(photo)?.asImageBitmap()
			}
		}
	}

	val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
	val borderWidth = if (isSelected) 3.dp else 0.dp

	Box(
		modifier = modifier
			.aspectRatio(1f)
			.fillMaxWidth()
	) {
		Card(
			modifier = Modifier
				.fillMaxSize()
				.border(width = borderWidth, color = borderColor, shape = RoundedCornerShape(8.dp))
				.combinedClickable(
					onClick = onClick,
					onLongClick = onLongClick,
				),
		) {
			Box(modifier = Modifier.fillMaxSize()) {
				thumbnailBitmap?.let {
					Image(
						bitmap = it,
						contentDescription = stringResource(
							id = R.string.gallery_photo_content_description,
							photo.photoName
						),
						contentScale = ContentScale.Crop,
						modifier = Modifier
							.fillMaxSize()
							.alpha(imageAlpha)
					)
				} ?: run {
					Box(modifier = Modifier.fillMaxSize()) {
						CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
					}
				}

				Box(
					modifier = Modifier
						.padding(8.dp)
						.fillMaxSize()
				) {
					if (isDecoy) {
						Icon(
							imageVector = Icons.Filled.Warning,
							contentDescription = stringResource(R.string.gallery_decoy_indicator),
							tint = Color.LightGray,
							modifier = Modifier
								.size(24.dp)
								.align(Alignment.BottomEnd)
						)
					}

					if (isSelected) {
						Icon(
							imageVector = Icons.Filled.CheckCircle,
							contentDescription = stringResource(R.string.gallery_decoy_indicator),
							tint = MaterialTheme.colorScheme.primary,
							modifier = Modifier
								.size(24.dp)
								.align(Alignment.TopEnd)
						)
					}
				}
			}
		}
	}
}
