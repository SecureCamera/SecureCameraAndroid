package com.darkrockstudios.app.securecamera.gallery

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
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
import com.darkrockstudios.app.securecamera.camera.MediaItem
import com.darkrockstudios.app.securecamera.camera.MediaType
import com.darkrockstudios.app.securecamera.camera.PhotoDef
import com.darkrockstudios.app.securecamera.camera.SecureImageRepository
import com.darkrockstudios.app.securecamera.encryption.VideoEncryptionService
import com.darkrockstudios.app.securecamera.navigation.NavController
import com.darkrockstudios.app.securecamera.navigation.ViewMedia
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
	val encryptionState by VideoEncryptionService.encryptionState.collectAsStateWithLifecycle()

	LaunchedEffect(Unit) {
		viewModel.loadMedia()
	}

	// Reload media when encryption state changes (new video completed)
	LaunchedEffect(encryptionState) {
		viewModel.loadMedia()
	}

	val startSelectionWithVibration = { mediaName: String ->
		viewModel.startSelectionMode(mediaName)
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
			onShareClick = { viewModel.shareSelectedMedia(context) },
			onSelectAll = { viewModel.selectAllMedia() },
			isSelectionMode = uiState.isSelectionMode,
			selectedCount = uiState.selectedMedia.size,
			onCancelSelection = { viewModel.clearSelection() }
		)

		if (uiState.showDeleteConfirmation) {
			ConfirmDeletePhotoDialog(
				selectedCount = uiState.selectedMedia.size,
				onConfirm = { viewModel.deleteSelectedMedia() },
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
			} else if (uiState.mediaItems.isEmpty()) {
				Text(text = stringResource(id = R.string.gallery_empty))
			} else {
				MediaGrid(
					mediaItems = uiState.mediaItems,
					paddingValues = paddingValues,
					selectedMediaNames = uiState.selectedMedia,
					encryptionState = encryptionState,
					onMediaLongClick = startSelectionWithVibration,
					onMediaClick = { mediaName ->
						if (uiState.isSelectionMode) {
							viewModel.toggleMediaSelection(mediaName)
						} else {
							navController.navigate(ViewMedia(mediaName))
						}
					},
				)
			}
		}
	}

	HandleUiEvents(viewModel.events, snackbarHostState, navController)
}

@Composable
private fun MediaGrid(
	mediaItems: List<MediaItem>,
	modifier: Modifier = Modifier,
	paddingValues: PaddingValues,
	selectedMediaNames: Set<String> = emptySet(),
	encryptionState: Map<String, Float?> = emptyMap(),
	onMediaLongClick: (String) -> Unit = {},
	onMediaClick: (String) -> Unit = {},
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
		items(items = mediaItems, key = { it.mediaName }) { mediaItem ->
			val encryptionProgress = encryptionState[mediaItem.mediaName]
			MediaGridItem(
				mediaItem = mediaItem,
				imageManager = imageManager,
				scope = scope,
				limitedDispatcher = limitedDispatcher,
				isSelected = selectedMediaNames.contains(mediaItem.mediaName),
				encryptionProgress = encryptionProgress,
				onLongClick = { onMediaLongClick(mediaItem.mediaName) },
				onClick = { onMediaClick(mediaItem.mediaName) }
			)
		}
	}
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun MediaGridItem(
	mediaItem: MediaItem,
	imageManager: SecureImageRepository,
	scope: CoroutineScope,
	limitedDispatcher: CoroutineDispatcher,
	isSelected: Boolean = false,
	encryptionProgress: Float? = null, // null means not encrypting, Float value is progress 0.0-1.0
	onLongClick: () -> Unit = {},
	onClick: () -> Unit = {},
	modifier: Modifier = Modifier
) {
	val isEncrypting = encryptionProgress != null
	var thumbnailBitmap by remember(mediaItem.mediaName) { mutableStateOf<ImageBitmap?>(null) }
	var thumbnailFailed by remember(mediaItem.mediaName) { mutableStateOf(false) }
	val isDecoy = remember(mediaItem) {
		(mediaItem as? PhotoDef)?.let { imageManager.isDecoyPhoto(it) } ?: false
	}

	val imageAlpha by animateFloatAsState(
		targetValue = if (thumbnailBitmap != null) 1f else 0f,
		animationSpec = tween(durationMillis = 500),
		label = "imageAlpha"
	)

	// Only load thumbnail if not currently encrypting
	LaunchedEffect(mediaItem.mediaName, isEncrypting) {
		if (thumbnailBitmap == null && !isEncrypting) {
			scope.launch(limitedDispatcher) {
				thumbnailBitmap = imageManager.readMediaThumbnail(mediaItem)?.asImageBitmap()
				thumbnailFailed = (thumbnailBitmap == null)
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
				when {
					// Show encryption progress for videos being encrypted
					isEncrypting -> {
						Box(
							modifier = Modifier
								.fillMaxSize()
								.background(MaterialTheme.colorScheme.surfaceVariant),
							contentAlignment = Alignment.Center
						) {
							val progress = encryptionProgress
							if (progress != null && progress > 0f) {
								CircularProgressIndicator(
									progress = { progress },
									modifier = Modifier.size(48.dp),
									strokeWidth = 4.dp,
								)
								Text(
									text = "${(progress * 100).toInt()}%",
									style = MaterialTheme.typography.labelSmall,
									modifier = Modifier.padding(top = 64.dp)
								)
							} else {
								CircularProgressIndicator(
									modifier = Modifier.size(48.dp),
									strokeWidth = 4.dp,
								)
								Text(
									text = stringResource(R.string.encryption_notification_preparing),
									style = MaterialTheme.typography.labelSmall,
									modifier = Modifier.padding(top = 64.dp)
								)
							}
						}
					}
					// Show thumbnail if loaded
					thumbnailBitmap != null -> {
						Image(
							bitmap = thumbnailBitmap!!,
							contentDescription = stringResource(
								id = R.string.gallery_photo_content_description,
								mediaItem.mediaName
							),
							contentScale = ContentScale.Crop,
							modifier = Modifier
								.fillMaxSize()
								.alpha(imageAlpha)
						)
					}
					// Show placeholder for failed thumbnails
					thumbnailFailed -> {
						Box(
							modifier = Modifier
								.fillMaxSize()
								.background(MaterialTheme.colorScheme.surfaceVariant),
							contentAlignment = Alignment.Center
						) {
							Icon(
								imageVector = if (mediaItem.mediaType == MediaType.VIDEO)
									Icons.Filled.PlayArrow
								else
									Icons.Filled.Warning,
								contentDescription = null,
								tint = MaterialTheme.colorScheme.onSurfaceVariant,
								modifier = Modifier.size(48.dp)
							)
						}
					}
					// Show loading spinner while thumbnail loads
					else -> {
						Box(modifier = Modifier.fillMaxSize()) {
							CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
						}
					}
				}

				// Overlay indicators (only show when not encrypting)
				if (!isEncrypting) {
					Box(
						modifier = Modifier
							.padding(8.dp)
							.fillMaxSize()
					) {
						// Video play indicator
						if (mediaItem.mediaType == MediaType.VIDEO) {
							Box(
								modifier = Modifier
									.size(32.dp)
									.background(Color.Black.copy(alpha = 0.6f), CircleShape)
									.align(Alignment.Center)
							) {
								Icon(
									imageVector = Icons.Filled.PlayArrow,
									contentDescription = stringResource(R.string.gallery_video_indicator),
									tint = Color.White,
									modifier = Modifier
										.size(24.dp)
										.align(Alignment.Center)
								)
							}
						}

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
}
