package com.darkrockstudios.app.securecamera.viewphoto

import android.content.Context
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.viewModelScope
import com.darkrockstudios.app.securecamera.BaseViewModel
import com.darkrockstudios.app.securecamera.R
import com.darkrockstudios.app.securecamera.camera.PhotoDef
import com.darkrockstudios.app.securecamera.camera.SecureImageRepository
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
    private val initialPhotoName: String,
) : BaseViewModel<ViewPhotoUiState>() {

	private var currentIndex: Int
		get() = uiState.value.currentIndex
		set(value) {
			_uiState.update { it.copy(currentIndex = value) }
		}

	override fun createState() = ViewPhotoUiState()

	init {
		val photos = imageManager.getPhotos().sortedByDescending { photoDef ->
			photoDef.dateTaken()
		}
		val initialIndex = photos.indexOfFirst { it.photoName == initialPhotoName }
		val initialPhoto = photos[initialIndex]

		viewModelScope.launch {
			val hasPoisonPill = pinRepository.hasPoisonPillPin()
			val isDecoy = imageManager.isDecoyPhoto(initialPhoto)

			_uiState.update {
				it.copy(
					photos = photos,
					currentIndex = initialIndex,
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

	fun setCurrentPhotoIndex(index: Int) {
		currentIndex = index
		viewModelScope.launch {
			val isDecoy = getCurrentPhoto()?.let { imageManager.isDecoyPhoto(it) } ?: false
			_uiState.update { it.copy(isDecoy = isDecoy) }
		}
	}

	fun getCurrentPhoto(): PhotoDef? {
		val photos = uiState.value.photos
		return if (photos.isNotEmpty() && currentIndex >= 0 && currentIndex < photos.size) {
			photos[currentIndex]
		} else {
			null
		}
	}

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

	fun deleteCurrentPhoto() {
		val currentPhoto = getCurrentPhoto() ?: return
		imageManager.deleteImage(currentPhoto)
		_uiState.update { it.copy(photoDeleted = true) }
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
	val photos: List<PhotoDef> = emptyList(),
	val currentIndex: Int = 0,
	val hasPoisonPill: Boolean = false,
	val isDecoy: Boolean = false,
	val isDecoyLoading: Boolean = false,
	val showDeleteConfirmation: Boolean = false,
	val showInfoDialog: Boolean = false,
	val photoDeleted: Boolean = false,
	val sanitizeFileName: Boolean = false,
	val sanitizeMetadata: Boolean = false
)