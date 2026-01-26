package com.darkrockstudios.app.securecamera.viewphoto

import android.annotation.SuppressLint
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.darkrockstudios.app.securecamera.ConfirmDeletePhotoDialog
import com.darkrockstudios.app.securecamera.R
import com.darkrockstudios.app.securecamera.camera.MediaItem
import com.darkrockstudios.app.securecamera.camera.MediaType
import com.darkrockstudios.app.securecamera.camera.PhotoDef
import com.darkrockstudios.app.securecamera.camera.VideoDef
import com.darkrockstudios.app.securecamera.navigation.NavController
import com.darkrockstudios.app.securecamera.navigation.ObfuscatePhoto
import com.darkrockstudios.app.securecamera.ui.HandleUiEvents
import net.engawapg.lib.zoomable.ExperimentalZoomableApi
import net.engawapg.lib.zoomable.rememberZoomState
import net.engawapg.lib.zoomable.zoomableWithScroll
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import androidx.media3.common.MediaItem as ExoMediaItem

@SuppressLint("UnusedBoxWithConstraintsScope")
@OptIn(ExperimentalZoomableApi::class)
@Composable
fun ViewPhotoContent(
	initialMedia: MediaItem,
	navController: NavController,
	modifier: Modifier = Modifier,
	snackbarHostState: SnackbarHostState,
	paddingValues: PaddingValues
) {
	val viewModel: ViewPhotoViewModel =
		koinViewModel(key = initialMedia.mediaName) { parametersOf(initialMedia.mediaName) }
	val context = LocalContext.current

	val uiState by viewModel.uiState.collectAsStateWithLifecycle()

	LaunchedEffect(uiState.mediaDeleted) {
		if (uiState.mediaDeleted) {
			navController.navigateUp()
		}
	}

	Column(
		modifier = modifier
			.fillMaxSize()
			.background(MaterialTheme.colorScheme.background)
	) {
		ViewPhotoTopBar(
			navController = navController,
			mediaType = uiState.currentMediaType,
			onDeleteClick = {
				viewModel.showDeleteConfirmation()
			},
			onInfoClick = {
				viewModel.showInfoDialog()
			},
			onObfuscateClick = {
				val currentPhoto = viewModel.getCurrentPhoto()
				currentPhoto?.let {
					navController.navigate(ObfuscatePhoto(it.photoName))
				}
			},
			onShareClick = {
				viewModel.sharePhoto(context)
			},
			showDecoyButton = uiState.hasPoisonPill && uiState.currentMediaType == MediaType.PHOTO,
			isDecoy = uiState.isDecoy,
			isDecoyLoading = uiState.isDecoyLoading,
			onDecoyClick = {
				viewModel.toggleDecoyStatus()
			}
		)

		if (uiState.showDeleteConfirmation) {
			ConfirmDeletePhotoDialog(
				selectedCount = 1,
				onConfirm = {
					viewModel.deleteCurrentMedia()
					viewModel.hideDeleteConfirmation()
				},
				onDismiss = {
					viewModel.hideDeleteConfirmation()
				}
			)
		}

		if (uiState.mediaItems.isNotEmpty()) {
			val listState = remember { LazyListState(firstVisibleItemIndex = uiState.currentIndex) }

			LaunchedEffect(listState) {
				snapshotFlow {
					listState.firstVisibleItemIndex to
							listState.firstVisibleItemScrollOffset
				}.collect { (idx, off) ->
					if (listState.firstVisibleItemIndex != uiState.currentIndex) {
						viewModel.setCurrentMediaIndex(listState.firstVisibleItemIndex)
					}
				}
			}

			LazyRow(
				state = listState,
				modifier = Modifier.fillMaxSize(),
				flingBehavior = rememberSnapFlingBehavior(lazyListState = listState, snapPosition = SnapPosition.Start),
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(32.dp),
			) {
				items(count = uiState.mediaItems.size, key = { uiState.mediaItems[it].mediaName }) { index ->
					val mediaItem = uiState.mediaItems[index]

					when (mediaItem) {
						is PhotoDef -> ViewPhoto(
							modifier = Modifier
								.fillParentMaxSize()
								.padding(bottom = paddingValues.calculateBottomPadding()),
							photo = mediaItem,
							viewModel = viewModel,
						)

						is VideoDef -> ViewVideo(
							modifier = Modifier
								.fillParentMaxSize()
								.padding(bottom = paddingValues.calculateBottomPadding()),
							video = mediaItem,
							isCurrentItem = index == uiState.currentIndex,
						)
					}
				}
			}
		}

		if (uiState.showInfoDialog) {
			val currentPhoto = viewModel.getCurrentPhoto()
			currentPhoto?.let {
				PhotoInfoDialog(it) {
					viewModel.hideInfoDialog()
				}
			}
		}
	}

	HandleUiEvents(viewModel.events, snackbarHostState, navController)
}

@Composable
@OptIn(ExperimentalZoomableApi::class)
private fun ViewPhoto(
	modifier: Modifier,
	photo: PhotoDef,
	viewModel: ViewPhotoViewModel,
) {
	var imageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

	LaunchedEffect(photo) {
		imageBitmap = viewModel.loadPhotoImage(photo)
	}

	val imageAlpha by animateFloatAsState(
		targetValue = if (imageBitmap != null) 1f else 0f,
		animationSpec = tween(durationMillis = 500),
		label = "imageAlpha"
	)

	val zoomState = rememberZoomState()

	Box(
		modifier = modifier.clipToBounds(),
		contentAlignment = Alignment.Center
	) {
		if (photo.photoFile.exists()) {
			imageBitmap?.let { bitmap ->
				Image(
					contentScale = ContentScale.Fit,
					modifier = Modifier
						.fillMaxSize()
						.alpha(imageAlpha)
						.zoomableWithScroll(zoomState),
					bitmap = bitmap,
					contentDescription = stringResource(id = R.string.photo_content_description),
				)
			} ?: run {
				Row(
					modifier = Modifier.align(Alignment.Center),
					verticalAlignment = Alignment.CenterVertically
				) {
					CircularProgressIndicator(
						modifier = Modifier.size(16.dp),
						color = MaterialTheme.colorScheme.onPrimaryContainer,
						strokeWidth = 2.dp
					)
					Spacer(modifier = Modifier.size(16.dp))
					Text(
						text = stringResource(R.string.photo_content_loading),
					)
				}
			}
		} else {
			Text(
				modifier = Modifier.align(alignment = Alignment.Center),
				text = stringResource(id = R.string.photo_not_found),
			)
		}
	}
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun ViewVideo(
	modifier: Modifier,
	video: VideoDef,
	isCurrentItem: Boolean,
) {
	val context = LocalContext.current

	val exoPlayer = remember(video.videoName) {
		ExoPlayer.Builder(context).build().apply {
			val mediaItem = ExoMediaItem.fromUri(video.videoFile.toURI().toString())
			setMediaItem(mediaItem)
			prepare()
			repeatMode = Player.REPEAT_MODE_OFF
		}
	}

	// Pause when not the current item
	LaunchedEffect(isCurrentItem) {
		if (!isCurrentItem) {
			exoPlayer.pause()
		}
	}

	DisposableEffect(video.videoName) {
		onDispose {
			exoPlayer.release()
		}
	}

	Box(
		modifier = modifier.clipToBounds(),
		contentAlignment = Alignment.Center
	) {
		if (video.videoFile.exists()) {
			AndroidView(
				factory = { ctx ->
					PlayerView(ctx).apply {
						player = exoPlayer
						useController = true
					}
				},
				modifier = Modifier.fillMaxSize()
			)
		} else {
			Text(
				modifier = Modifier.align(alignment = Alignment.Center),
				text = stringResource(id = R.string.video_not_found),
			)
		}
	}
}
