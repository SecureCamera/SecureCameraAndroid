package com.darkrockstudios.app.securecamera.viewphoto

import android.content.Context
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.viewModelScope
import com.darkrockstudios.app.securecamera.BaseViewModel
import com.darkrockstudios.app.securecamera.R
import com.darkrockstudios.app.securecamera.camera.*
import com.darkrockstudios.app.securecamera.preferences.AppSettingsDataSource
import com.darkrockstudios.app.securecamera.security.pin.PinRepository
import com.darkrockstudios.app.securecamera.share.sharePhotoWithProvider
import com.darkrockstudios.app.securecamera.usecases.AddDecoyPhotoUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ViewPhotoViewModel(
	private val appContext: Context,
	private val imageManager: SecureImageRepository,
	private val preferencesManager: AppSettingsDataSource,
	private val pinRepository: PinRepository,
	private val addDecoyPhotoUseCase: AddDecoyPhotoUseCase,
	private val initialMediaName: String,
) : BaseViewModel<ViewPhotoUiState>() {

	private var currentIndex: Int
		get() = uiState.value.currentIndex
		set(value) {
			_uiState.update { it.copy(currentIndex = value) }
		}

	override fun createState() = ViewPhotoUiState()

	init {
		val mediaItems = imageManager.getAllMedia()
		val initialIndex = mediaItems.indexOfFirst { it.mediaName == initialMediaName }
		val initialMedia = mediaItems.getOrNull(initialIndex)

		viewModelScope.launch {
			val hasPoisonPill = pinRepository.hasPoisonPillPin()
			val isDecoy = (initialMedia as? PhotoDef)?.let { imageManager.isDecoyPhoto(it) } ?: false

			_uiState.update {
				it.copy(
					mediaItems = mediaItems,
					currentIndex = if (initialIndex >= 0) initialIndex else 0,
					hasPoisonPill = hasPoisonPill,
					isDecoy = isDecoy
				)
			}
		}

		viewModelScope.launch {
			preferencesManager.sanitizeMetadata.collect { sanitizeMetadata ->
				_uiState.update { it.copy(sanitizeMetadata = sanitizeMetadata) }
			}
		}
	}

	suspend fun loadPhotoImage(photo: PhotoDef): ImageBitmap = withContext(Dispatchers.Default) {
		return@withContext imageManager.readImage(photo).asImageBitmap()
	}

	fun setCurrentMediaIndex(index: Int) {
		currentIndex = index
		viewModelScope.launch {
			val currentMedia = getCurrentMedia()
			val isDecoy = (currentMedia as? PhotoDef)?.let { imageManager.isDecoyPhoto(it) } ?: false
			_uiState.update { it.copy(isDecoy = isDecoy) }
		}
	}

	fun getCurrentMedia(): MediaItem? {
		val mediaItems = uiState.value.mediaItems
		return if (mediaItems.isNotEmpty() && currentIndex >= 0 && currentIndex < mediaItems.size) {
			mediaItems[currentIndex]
		} else {
			null
		}
	}

	fun getCurrentPhoto(): PhotoDef? = getCurrentMedia() as? PhotoDef

	fun getCurrentVideo(): VideoDef? = getCurrentMedia() as? VideoDef

	fun toggleDecoyStatus() {
		val currentPhoto = getCurrentPhoto() ?: return

		_uiState.update { it.copy(isDecoyLoading = true) }

		viewModelScope.launch(Dispatchers.Default) {
			if (uiState.value.isDecoy) {
				imageManager.removeDecoyPhoto(currentPhoto)
				withContext(Dispatchers.Main) {
					_uiState.update {
						it.copy(
							isDecoy = false,
							isDecoyLoading = false,
						)
					}
					showMessage(appContext.getString(R.string.decoy_removed))
				}
			} else {
				val success = addDecoyPhotoUseCase.addDecoyPhoto(currentPhoto)
				withContext(Dispatchers.Main) {
					_uiState.update {
						it.copy(
							isDecoy = success,
							isDecoyLoading = false,
						)
					}
					if (success) {
						showMessage(appContext.getString(R.string.decoy_added))
					} else {
						showMessage(
							appContext.getString(
								R.string.decoy_limit_reached,
								SecureImageRepository.MAX_DECOY_PHOTOS
							)
						)
					}
				}
			}
		}
	}

	fun showDeleteConfirmation() {
		_uiState.update { it.copy(showDeleteConfirmation = true) }
	}

	fun hideDeleteConfirmation() {
		_uiState.update { it.copy(showDeleteConfirmation = false) }
	}

	fun deleteCurrentMedia() {
		val currentMedia = getCurrentMedia() ?: return
		imageManager.deleteMediaItem(currentMedia)
		_uiState.update { it.copy(mediaDeleted = true) }
	}

	fun showInfoDialog() {
		_uiState.update { it.copy(showInfoDialog = true) }
	}

	fun hideInfoDialog() {
		_uiState.update { it.copy(showInfoDialog = false) }
	}

	fun sharePhoto(context: Context) {
		val currentPhoto = getCurrentPhoto() ?: return

		viewModelScope.launch {
			sharePhotoWithProvider(
				photo = currentPhoto,
				context = context
			)
		}
	}
}

data class ViewPhotoUiState(
	val mediaItems: List<MediaItem> = emptyList(),
	val currentIndex: Int = 0,
	val hasPoisonPill: Boolean = false,
	val isDecoy: Boolean = false,
	val isDecoyLoading: Boolean = false,
	val showDeleteConfirmation: Boolean = false,
	val showInfoDialog: Boolean = false,
	val mediaDeleted: Boolean = false,
	val sanitizeFileName: Boolean = false,
	val sanitizeMetadata: Boolean = false
) {
	val currentMediaType: MediaType?
		get() = mediaItems.getOrNull(currentIndex)?.mediaType
}
