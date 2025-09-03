package com.darkrockstudios.app.securecamera

import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.navigation3.runtime.NavBackStack
import com.darkrockstudios.app.securecamera.auth.AuthorizationRepository
import com.darkrockstudios.app.securecamera.navigation.AppNavHost
import com.darkrockstudios.app.securecamera.navigation.NavController
import com.darkrockstudios.app.securecamera.navigation.enforceAuth
import com.darkrockstudios.app.securecamera.preferences.AppSettingsDataSource
import com.darkrockstudios.app.securecamera.ui.theme.SecureCameraTheme
import org.koin.compose.KoinContext
import org.koin.compose.koinInject

@Composable
fun App(
	capturePhoto: MutableState<Boolean?>,
	backStack: NavBackStack,
	navController: NavController
) {
	KoinContext {
		SecureCameraTheme {
			val snackbarHostState = remember { SnackbarHostState() }
			val preferencesManager = koinInject<AppSettingsDataSource>()
			val authorizationRepository = koinInject<AuthorizationRepository>()

			val hasCompletedIntro by preferencesManager.hasCompletedIntro.collectAsState(initial = null)

			VerifySessionOnResume(navController, hasCompletedIntro, authorizationRepository)

			if (hasCompletedIntro != null) {
				Scaffold(
					snackbarHost = { SnackbarHost(snackbarHostState) },
					modifier = Modifier.imePadding()
				) { paddingValues ->
					AppNavHost(
						backStack = backStack,
						navController = navController,
						capturePhoto = capturePhoto,
						modifier = Modifier,
						snackbarHostState = snackbarHostState,
						paddingValues = paddingValues,
					)
				}
			}
		}
	}
}

@Composable
private fun VerifySessionOnResume(
	navController: NavController,
	hasCompletedIntro: Boolean?,
	authorizationRepository: AuthorizationRepository
) {
	var requireAuthCheck = remember { false }
	LifecycleResumeEffect(hasCompletedIntro) {
		if (hasCompletedIntro == true && requireAuthCheck) {
			// Use the top-of-stack key in Nav3
			enforceAuth(authorizationRepository, null, navController)
		}
		onPauseOrDispose {
			requireAuthCheck = true
		}
	}
}
