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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darkrockstudios.app.securecamera.ConfirmDeletePhotoDialog
import com.darkrockstudios.app.securecamera.R
import com.darkrockstudios.app.securecamera.camera.PhotoDef
import com.darkrockstudios.app.securecamera.navigation.NavController
import com.darkrockstudios.app.securecamera.navigation.ObfuscatePhoto
import com.darkrockstudios.app.securecamera.ui.HandleUiEvents
import net.engawapg.lib.zoomable.ExperimentalZoomableApi
import net.engawapg.lib.zoomable.rememberZoomState
import net.engawapg.lib.zoomable.zoomableWithScroll
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@SuppressLint("UnusedBoxWithConstraintsScope")
@OptIn(ExperimentalZoomableApi::class)
@Composable
fun ViewPhotoContent(
	initialPhoto: PhotoDef,
	navController: NavController,
	modifier: Modifier = Modifier,
	snackbarHostState: SnackbarHostState,
	paddingValues: PaddingValues
) {
	val viewModel: ViewPhotoViewModel =
		koinViewModel(key = initialPhoto.photoName) { parametersOf(initialPhoto.photoName) }
	val context = LocalContext.current

	val uiState by viewModel.uiState.collectAsStateWithLifecycle()

	LaunchedEffect(uiState.photoDeleted) {
		if (uiState.photoDeleted) {
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
			showDecoyButton = uiState.hasPoisonPill,
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
					viewModel.deleteCurrentPhoto()
					viewModel.hideDeleteConfirmation()
				},
				onDismiss = {
					viewModel.hideDeleteConfirmation()
				}
			)
		}

		if (uiState.photos.isNotEmpty()) {
			val listState = remember { LazyListState(firstVisibleItemIndex = uiState.currentIndex) }

			LaunchedEffect(listState) {
				snapshotFlow {
					listState.firstVisibleItemIndex to
							listState.firstVisibleItemScrollOffset
				}.collect { (idx, off) ->
					if (listState.firstVisibleItemIndex != uiState.currentIndex) {
						viewModel.setCurrentPhotoIndex(listState.firstVisibleItemIndex)
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
				items(count = uiState.photos.size, key = { uiState.photos[it].photoName }) { index ->
					val photo = uiState.photos[index]

					ViewPhoto(
						modifier = Modifier
							.fillParentMaxSize()
							.padding(bottom = paddingValues.calculateBottomPadding()),
						photo = photo,
						viewModel = viewModel,
					)
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
