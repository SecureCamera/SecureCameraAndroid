package com.darkrockstudios.app.securecamera

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import com.darkrockstudios.app.securecamera.auth.AuthorizationRepository
import com.darkrockstudios.app.securecamera.encryption.VideoEncryptionService
import com.darkrockstudios.app.securecamera.navigation.*
import com.darkrockstudios.app.securecamera.navigation.Camera
import com.darkrockstudios.app.securecamera.preferences.AppSettingsDataSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.android.inject
import timber.log.Timber
import kotlin.time.Duration.Companion.seconds

class MainActivity : ComponentActivity() {
	private var capturePhoto = mutableStateOf<Boolean?>(null)
	private val locationRepository: LocationRepository by inject()
	private val preferences: AppSettingsDataSource by inject()
	private val authorizationRepository: AuthorizationRepository by inject()
	lateinit var navController: NavController
	private lateinit var navBackStack: androidx.navigation3.runtime.NavBackStack<androidx.navigation3.runtime.NavKey>

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		if (BuildConfig.DEBUG.not()) {
			window.setFlags(
				WindowManager.LayoutParams.FLAG_SECURE,
				WindowManager.LayoutParams.FLAG_SECURE
			)
		}

		enableEdgeToEdge()

		val startKey = determineStartKey()
		setContent {
			val backStack = rememberNavBackStack(startKey)
			val controller = remember(backStack) { Nav3CompatController(backStack) }
			navController = controller
			navBackStack = backStack
			App(capturePhoto, backStack, navController)
		}

		startKeepAliveWatcher()
	}

	private fun determineStartKey(): NavKey {
		val photosToImport = receiveFiles()
		val hasCompletedIntro = runBlocking { preferences.hasCompletedIntro.firstOrNull() ?: false }
		return if (hasCompletedIntro) {
			val targetKey: DestinationKey = if (photosToImport.isNotEmpty()) {
				ImportPhotos(PhotoImportJob(photosToImport))
			} else {
				Camera
			}
			if (authorizationRepository.checkSessionValidity()) {
				// Session is valid - recover any stranded temp video files immediately
				// This ensures unencrypted videos are encrypted ASAP
				VideoEncryptionService.recoverStrandedFiles(this)
				targetKey
			} else {
				PinVerification(targetKey)
			}
		} else {
			Introduction
		}
	}

	override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
		return when (keyCode) {
			KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN -> {
				// Only handle volume button as shutter when on Camera screen
				if (::navBackStack.isInitialized && navBackStack.lastOrNull() == Camera) {
					val curValue = capturePhoto.value
					capturePhoto.value = if (curValue != null) {
						!curValue
					} else {
						true
					}
					true
				} else {
					// Let the system handle volume buttons on other screens
					super.onKeyDown(keyCode, event)
				}
			}

			else -> super.onKeyDown(keyCode, event)
		}
	}

	override fun onResume() {
		super.onResume()
		locationRepository.refreshPermissionStatus()
	}

	private fun startKeepAliveWatcher() {
		lifecycleScope.launch {
			repeatOnLifecycle(Lifecycle.State.RESUMED) {
				Timber.d("Starting session keep-alive watcher")
				while (isActive) {
					if (authorizationRepository.checkSessionValidity()) {
						authorizationRepository.keepAliveSession()
						Timber.d("Session keep-alive ping sent")
					} else {
						Timber.d("No valid session, do not send keep-alive")
					}
					delay(30.seconds)
				}
				Timber.d("Stopping session keep-alive watcher")
			}
		}
	}

	private fun receiveFiles(): List<Uri> {
		val intent = getIntent()

		return if (Intent.ACTION_SEND == intent.action && intent.type != null) {
			if (intent.type?.startsWith("image/") == true) {
				handleSingleImage(intent)
			} else {
				emptyList()
			}
		} else if (Intent.ACTION_SEND_MULTIPLE == intent.action && intent.type != null) {
			if (intent.type?.startsWith("image/") == true) {
				handleMultipleImages(intent)
			} else {
				emptyList()
			}
		} else {
			emptyList()
		}
	}

	private fun handleSingleImage(intent: Intent): List<Uri> {
		return intent.getParcelableExtra<Uri?>(Intent.EXTRA_STREAM)?.let { imageUri ->
			listOf(imageUri)
		} ?: emptyList()
	}

	private fun handleMultipleImages(intent: Intent): List<Uri> {
		val imageUris = intent.getParcelableArrayListExtra<Uri?>(Intent.EXTRA_STREAM)
		return imageUris?.filterNotNull() ?: emptyList()
	}
}
