package com.darkrockstudios.app.securecamera.gallery

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.darkrockstudios.app.securecamera.BaseViewModel
import com.darkrockstudios.app.securecamera.camera.PhotoDef
import com.darkrockstudios.app.securecamera.camera.SecureImageRepository
import com.darkrockstudios.app.securecamera.preferences.AppSettingsDataSource
import com.darkrockstudios.app.securecamera.share.sharePhotosWithProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GalleryViewModel(
	private val imageManager: SecureImageRepository,
	private val preferencesManager: AppSettingsDataSource
) : BaseViewModel<GalleryUiState>() {

	override fun createState() = GalleryUiState()

	init {
		observePreferences()
	}

	fun loadPhotos() {
		viewModelScope.launch {
			_uiState.update { it.copy(isLoading = true) }
			val photos = imageManager.getPhotos().sortedByDescending { it.dateTaken() }
			_uiState.update { it.copy(photos = photos, isLoading = false) }
		}
	}

	private fun observePreferences() {
		viewModelScope.launch {
			preferencesManager.sanitizeFileName.collect { sanitizeFileName ->
				_uiState.update { it.copy(sanitizeFileName = sanitizeFileName) }
			}
		}

		viewModelScope.launch {
			preferencesManager.sanitizeMetadata.collect { sanitizeMetadata ->
				_uiState.update { it.copy(sanitizeMetadata = sanitizeMetadata) }
			}
		}
	}

	fun togglePhotoSelection(photoName: String) {
		val currentSelectedPhotos = uiState.value.selectedPhotos
		val newSelectedPhotos = if (currentSelectedPhotos.contains(photoName)) {
			currentSelectedPhotos - photoName
		} else {
			currentSelectedPhotos + photoName
		}

		_uiState.update {
			it.copy(
				selectedPhotos = newSelectedPhotos,
				isSelectionMode = newSelectedPhotos.isNotEmpty()
			)
		}
	}

	fun startSelectionMode(photoName: String) {
		_uiState.update {
			it.copy(
				isSelectionMode = true,
				selectedPhotos = setOf(photoName)
			)
		}
	}

	fun clearSelection() {
		_uiState.update {
			it.copy(
				isSelectionMode = false,
				selectedPhotos = emptySet()
			)
		}
	}

	fun showDeleteConfirmation() {
		_uiState.update { it.copy(showDeleteConfirmation = true) }
	}

	fun dismissDeleteConfirmation() {
		_uiState.update { it.copy(showDeleteConfirmation = false) }
	}

	fun deleteSelectedPhotos() {
		val photoDefs = uiState.value.selectedPhotos.mapNotNull { imageManager.getPhotoByName(it) }
		imageManager.deleteImages(photoDefs)

		val updatedPhotos = uiState.value.photos.filter { it !in photoDefs }
		_uiState.update {
			it.copy(
				photos = updatedPhotos,
				selectedPhotos = emptySet(),
				isSelectionMode = false,
				showDeleteConfirmation = false
			)
		}
	}

	fun shareSelectedPhotos(context: Context) {
		val photoDefs = uiState.value.selectedPhotos.mapNotNull { imageManager.getPhotoByName(it) }
		if (photoDefs.isNotEmpty()) {
			viewModelScope.launch(Dispatchers.IO) {
				sharePhotosWithProvider(
					photos = photoDefs,
					context = context
				)
				withContext(Dispatchers.Main) {
					clearSelection()
				}
			}
		}
	}

	fun selectAllPhotos() {
		val allPhotoNames = uiState.value.photos.map { it.photoName }.toSet()
		_uiState.update {
			it.copy(
				selectedPhotos = allPhotoNames,
				isSelectionMode = allPhotoNames.isNotEmpty()
			)
		}
	}
}

data class GalleryUiState(
	val photos: List<PhotoDef> = emptyList(),
	val isLoading: Boolean = true,
	val isSelectionMode: Boolean = false,
	val selectedPhotos: Set<String> = emptySet(),
	val showDeleteConfirmation: Boolean = false,
	val sanitizeFileName: Boolean = true,
	val sanitizeMetadata: Boolean = true
)
