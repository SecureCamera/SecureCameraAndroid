package com.darkrockstudios.app.securecamera.gallery

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.darkrockstudios.app.securecamera.BaseViewModel
import com.darkrockstudios.app.securecamera.camera.MediaItem
import com.darkrockstudios.app.securecamera.camera.SecureImageRepository
import com.darkrockstudios.app.securecamera.preferences.AppSettingsDataSource
import com.darkrockstudios.app.securecamera.share.shareMediaWithProvider
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

	fun loadMedia() {
		viewModelScope.launch {
			_uiState.update { it.copy(isLoading = true) }
			val media = imageManager.getAllMedia()
			_uiState.update { it.copy(mediaItems = media, isLoading = false) }
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

	fun toggleMediaSelection(mediaName: String) {
		val currentSelected = uiState.value.selectedMedia
		val newSelected = if (currentSelected.contains(mediaName)) {
			currentSelected - mediaName
		} else {
			currentSelected + mediaName
		}

		_uiState.update {
			it.copy(
				selectedMedia = newSelected,
				isSelectionMode = newSelected.isNotEmpty()
			)
		}
	}

	fun startSelectionMode(mediaName: String) {
		_uiState.update {
			it.copy(
				isSelectionMode = true,
				selectedMedia = setOf(mediaName)
			)
		}
	}

	fun clearSelection() {
		_uiState.update {
			it.copy(
				isSelectionMode = false,
				selectedMedia = emptySet()
			)
		}
	}

	fun showDeleteConfirmation() {
		_uiState.update { it.copy(showDeleteConfirmation = true) }
	}

	fun dismissDeleteConfirmation() {
		_uiState.update { it.copy(showDeleteConfirmation = false) }
	}

	fun deleteSelectedMedia() {
		val mediaItems = uiState.value.selectedMedia.mapNotNull { imageManager.getMediaItemByName(it) }
		imageManager.deleteMediaItems(mediaItems)

		val updatedMedia = uiState.value.mediaItems.filter { it.mediaName !in uiState.value.selectedMedia }
		_uiState.update {
			it.copy(
				mediaItems = updatedMedia,
				selectedMedia = emptySet(),
				isSelectionMode = false,
				showDeleteConfirmation = false
			)
		}
	}

	fun shareSelectedMedia(context: Context) {
		val mediaItems = uiState.value.selectedMedia.mapNotNull {
			imageManager.getMediaItemByName(it)
		}
		if (mediaItems.isNotEmpty()) {
			viewModelScope.launch(Dispatchers.IO) {
				shareMediaWithProvider(
					mediaItems = mediaItems,
					context = context
				)
				withContext(Dispatchers.Main) {
					clearSelection()
				}
			}
		}
	}

	fun selectAllMedia() {
		val allMediaNames = uiState.value.mediaItems.map { it.mediaName }.toSet()
		_uiState.update {
			it.copy(
				selectedMedia = allMediaNames,
				isSelectionMode = allMediaNames.isNotEmpty()
			)
		}
	}
}

data class GalleryUiState(
	val mediaItems: List<MediaItem> = emptyList(),
	val isLoading: Boolean = true,
	val isSelectionMode: Boolean = false,
	val selectedMedia: Set<String> = emptySet(),
	val showDeleteConfirmation: Boolean = false,
	val sanitizeFileName: Boolean = true,
	val sanitizeMetadata: Boolean = true,
)
