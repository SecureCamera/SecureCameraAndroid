package com.darkrockstudios.app.securecamera.navigation

import android.os.Build
import android.view.RoundedCorner
import androidx.compose.animation.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.*
import androidx.navigation3.ui.NavDisplay
import com.darkrockstudios.app.securecamera.R
import com.darkrockstudios.app.securecamera.about.AboutContent
import com.darkrockstudios.app.securecamera.auth.AuthorizationRepository
import com.darkrockstudios.app.securecamera.auth.PinVerificationContent
import com.darkrockstudios.app.securecamera.camera.CameraContent
import com.darkrockstudios.app.securecamera.camera.SecureImageRepository
import com.darkrockstudios.app.securecamera.gallery.GalleryContent
import com.darkrockstudios.app.securecamera.import.ImportPhotosContent
import com.darkrockstudios.app.securecamera.introduction.IntroductionContent
import com.darkrockstudios.app.securecamera.obfuscation.ObfuscatePhotoContent
import com.darkrockstudios.app.securecamera.settings.SettingsContent
import com.darkrockstudios.app.securecamera.viewphoto.ViewPhotoContent
import kotlin.io.encoding.ExperimentalEncodingApi

@Composable
private fun rememberScreenCornerRadius(): Dp {
	val view = LocalView.current
	val density = LocalDensity.current
	return remember {
		val radius = 16.dp
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
			val insets = view.rootWindowInsets
			val corner = insets?.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)
			if (corner != null) {
				with(density) { corner.radius.toDp() }
			} else {
				radius
			}
		} else {
			radius
		}
	}
}

@OptIn(ExperimentalEncodingApi::class)
@Composable
fun AppNavHost(
	backStack: NavBackStack<NavKey>,
	navController: NavController,
	capturePhoto: MutableState<Boolean?>,
	modifier: Modifier = Modifier,
	snackbarHostState: SnackbarHostState,
	paddingValues: PaddingValues,
) {
	val imageManager = org.koin.compose.koinInject<SecureImageRepository>()
	val authManager = org.koin.compose.koinInject<AuthorizationRepository>()
	val scope = rememberCoroutineScope()

	LaunchedEffect(Unit) { authManager.checkSessionValidity() }

	val cornerRadius = rememberScreenCornerRadius()
	val roundedCornerDecorator = remember(cornerRadius) {
		NavEntryDecorator<Any> { entry ->
			Box(
				modifier = Modifier
					.fillMaxSize()
					.clip(RoundedCornerShape(cornerRadius))
			) {
				entry.Content()
			}
		}
	}

	NavDisplay(
		backStack = backStack,
		onBack = { if (backStack.isNotEmpty()) backStack.removeAt(backStack.lastIndex) },
		modifier = modifier,
		entryDecorators = listOf(
			rememberSaveableStateHolderNavEntryDecorator(),
			roundedCornerDecorator,
		),
		transitionSpec = {
			(slideInHorizontally { it } + fadeIn()) togetherWith
					(slideOutHorizontally { -it } + fadeOut())
		},
		popTransitionSpec = {
			(slideInHorizontally { -it } + fadeIn()) togetherWith
					(slideOutHorizontally { it } + fadeOut())
		},
		predictivePopTransitionSpec = {
			(slideInHorizontally { -it / 10 } + fadeIn()) togetherWith
					(scaleOut(targetScale = 0.9f) + fadeOut())
		},
		entryProvider = entryProvider {
			entry<Introduction> {
				IntroductionContent(
					navController = navController,
					modifier = Modifier.fillMaxSize(),
					paddingValues = paddingValues,
				)
			}
			entry<Camera> {
				CameraContent(
					capturePhoto = capturePhoto,
					navController = navController,
					modifier = Modifier.fillMaxSize(),
					paddingValues = paddingValues
				)
			}
			entry<Gallery> {
				val isAuthorized by authManager.isAuthorized.collectAsState()
				if (isAuthorized) {
					GalleryContent(
						navController = navController,
						modifier = Modifier.fillMaxSize(),
						paddingValues = paddingValues,
						snackbarHostState = snackbarHostState
					)
				} else {
					Box(modifier = Modifier.fillMaxSize()) {
						Text(
							text = stringResource(R.string.unauthorized),
							modifier = Modifier.align(Alignment.Center)
						)
					}
				}
			}
			entry<ViewMedia> { key ->
				if (authManager.checkSessionValidity()) {
					val mediaItem = imageManager.getMediaItemByName(key.mediaName)
					if (mediaItem != null) {
						ViewPhotoContent(
							initialMedia = mediaItem,
							navController = navController,
							modifier = Modifier.fillMaxSize(),
							paddingValues = paddingValues,
							snackbarHostState = snackbarHostState,
						)
					} else {
						Text(text = stringResource(R.string.media_content_none_selected))
					}
				} else {
					Box(modifier = Modifier.fillMaxSize()) {
						Text(
							text = stringResource(R.string.unauthorized),
							modifier = Modifier.align(Alignment.Center)
						)
					}
				}
			}
			entry<PinVerification> { key ->
				PinVerificationContent(
					navController = navController,
					returnKey = key.returnKey,
					snackbarHostState = snackbarHostState,
					modifier = Modifier.fillMaxSize()
				)
			}
			entry<Settings> {
				SettingsContent(
					navController = navController,
					modifier = Modifier.fillMaxSize(),
					paddingValues = paddingValues,
					snackbarHostState = snackbarHostState,
				)
			}
			entry<About> {
				AboutContent(
					navController = navController,
					modifier = Modifier.fillMaxSize(),
					paddingValues = paddingValues,
				)
			}
			entry<ObfuscatePhoto> { key ->
				if (authManager.checkSessionValidity()) {
					ObfuscatePhotoContent(
						photoName = key.photoName,
						navController = navController,
						snackbarHostState = snackbarHostState,
						outerScope = scope,
						paddingValues = paddingValues,
					)
				} else {
					Box(modifier = Modifier.fillMaxSize()) {
						Text(
							text = stringResource(R.string.unauthorized),
							modifier = Modifier.align(Alignment.Center)
						)
					}
				}
			}
			entry<ImportPhotos> { key ->
				if (authManager.checkSessionValidity()) {
					ImportPhotosContent(
						photosToImport = key.job.photos,
						navController = navController,
						paddingValues = paddingValues,
					)
				} else {
					Box(modifier = Modifier.fillMaxSize()) {
						Text(
							text = stringResource(R.string.unauthorized),
							modifier = Modifier.align(Alignment.Center)
						)
					}
				}
			}
		}
	)
}

fun enforceAuth(
	authManager: AuthorizationRepository,
	currentKey: NavKey?,
	navController: NavController
) {
	if (
		authManager.checkSessionValidity().not() &&
		currentKey !is PinVerification &&
		currentKey !is Introduction
	) {
		val returnKey = when (currentKey) {
			is ViewMedia -> ViewMedia(currentKey.mediaName)
			is ObfuscatePhoto -> ObfuscatePhoto(currentKey.photoName)
			is Gallery -> Gallery
			is Settings -> Settings
			is About -> About
			is ImportPhotos -> ImportPhotos(currentKey.job)
			else -> Camera
		}
		navController.navigateClearingBackStack(PinVerification(returnKey))
	}
}