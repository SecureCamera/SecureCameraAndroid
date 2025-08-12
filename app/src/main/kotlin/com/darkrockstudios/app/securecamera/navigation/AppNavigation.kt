package com.darkrockstudios.app.securecamera.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entry
import androidx.navigation3.runtime.entryProvider
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

@OptIn(ExperimentalEncodingApi::class)
@Composable
fun AppNavHost(
	backStack: NavBackStack,
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

	NavDisplay(
		backStack = backStack,
		onBack = { if (backStack.isNotEmpty()) backStack.removeAt(backStack.lastIndex) },
		modifier = modifier,
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
			entry<ViewPhoto> { key ->
				if (authManager.checkSessionValidity()) {
					val photo = imageManager.getPhotoByName(key.photoName)
					if (photo != null) {
						ViewPhotoContent(
							initialPhoto = photo,
							navController = navController,
							modifier = Modifier.fillMaxSize(),
							paddingValues = paddingValues,
							snackbarHostState = snackbarHostState,
						)
					} else {
						Text(text = stringResource(R.string.photo_content_none_selected))
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
					returnRoute = key.returnRoute,
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
		val returnRoute = when (currentKey) {
			is ViewPhoto -> AppDestinations.createViewPhotoRoute(currentKey.photoName)
			is ObfuscatePhoto -> AppDestinations.createObfuscatePhotoRoute(currentKey.photoName)
			is Gallery -> AppDestinations.GALLERY_ROUTE
			is Settings -> AppDestinations.SETTINGS_ROUTE
			is About -> AppDestinations.ABOUT_ROUTE
			is ImportPhotos -> AppDestinations.createImportPhotosRoute(currentKey.job.photos)
			else -> AppDestinations.CAMERA_ROUTE
		}
		navController.navigate(AppDestinations.createPinVerificationRoute(returnRoute)) { launchSingleTop = true }
	}
}