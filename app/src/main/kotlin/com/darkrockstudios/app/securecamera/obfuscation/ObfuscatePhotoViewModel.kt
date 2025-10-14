package com.darkrockstudios.app.securecamera.obfuscation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavKey
import com.darkrockstudios.app.securecamera.BaseViewModel
import com.darkrockstudios.app.securecamera.R
import com.darkrockstudios.app.securecamera.camera.PhotoDef
import com.darkrockstudios.app.securecamera.camera.SecureImageRepository
import com.darkrockstudios.app.securecamera.navigation.ViewPhoto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

class ObfuscatePhotoViewModel(
	private val appContext: Context,
	private val imageManager: SecureImageRepository,
	private val facialDetection: FacialDetection,
) : BaseViewModel<ObfuscatePhotoUiState>() {

	override fun createState() = ObfuscatePhotoUiState()

	fun toggleRegionObfuscation(index: Int) {
		_uiState.update { state ->
			val regions = state.regions.toMutableList()
			if (index in regions.indices) {
				val region = regions[index]
				when (region) {
					is FaceRegion -> {
						regions[index] = FaceRegion(region.face, !region.obfuscate)
					}

					else -> {
						regions.removeAt(index)
					}
				}
			}
			state.copy(regions = regions)
		}
	}

	fun loadPhoto(photoName: String) {
		viewModelScope.launch {
			_uiState.update { it.copy(isLoading = true) }

			imageManager.getPhotoByName(photoName)?.let { photo ->
				val bitmap = imageManager.readImage(photo)
				_uiState.update {
					it.copy(
						photoDef = photo,
						originalBitmap = bitmap,
						imageBitmap = bitmap.asImageBitmap(),
						isLoading = false
					)
				}
				findFaces()
			} ?: run {
				_uiState.update { it.copy(isLoading = false) }
			}
		}
	}

	fun findFaces() {
		_uiState.update { it.copy(isFindingFaces = true) }
		viewModelScope.launch(Dispatchers.IO) {
			uiState.value.originalBitmap?.let { bitmap ->
				val foundFaces = facialDetection.processForFaces(bitmap)

				if (foundFaces.isNotEmpty()) {
					val newRegions = foundFaces.map { FaceRegion(it) }
					val manualRegions = uiState.value.regions.filter { it !is FaceRegion }
					_uiState.update { state ->
						state.copy(
							regions = newRegions + manualRegions,
							isFindingFaces = false
						)
					}
					Timber.i("Found ${foundFaces.size} faces")
				} else {
					_uiState.update { it.copy(isFindingFaces = false) }
					Timber.w("Failed face detection in Image")
				}
			} ?: run {
				Timber.e("findFaces: originalBitmap was null")
				_uiState.update { it.copy(isFindingFaces = false) }
			}
		}
	}

	fun obscureRegions() {
		uiState.value.originalBitmap?.let { bitmap ->
			if (uiState.value.regions.isNotEmpty()) {
				val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
				val regionsToObfuscate = uiState.value.regions.filter { it.obfuscate }

				regionsToObfuscate.forEach { region ->
					maskFace(mutableBitmap, region, appContext, MaskMode.PIXELATE)
				}

				_uiState.update {
					it.copy(
						imageBitmap = mutableBitmap.asImageBitmap(),
						obscuredBitmap = mutableBitmap,
						regions = emptyList()
					)
				}

				showFacesObscuredMessage(regionsToObfuscate.size)
			}
		} ?: run {
			Timber.e("obscureFaces: originalBitmap was null")
		}
	}

	private fun showFacesObscuredMessage(faceCount: Int) {
		val message = appContext.getString(R.string.obscure_toast_faces_obscured, faceCount)
		showMessage(message)
	}

	fun clearObfuscation() {
		_uiState.update {
			it.copy(
				obscuredBitmap = null,
				imageBitmap = it.originalBitmap?.asImageBitmap()
			)
		}
		findFaces()
		showFacesClearedMessage()
	}

	private fun showFacesClearedMessage() {
		val message = appContext.getString(R.string.obscure_toast_faces_cleared)
		showMessage(message)
	}

	fun showSaveDialog() {
		_uiState.update { it.copy(showSaveDialog = true) }
	}

	fun dismissSaveDialog() {
		_uiState.update { it.copy(showSaveDialog = false) }
	}

	fun overwriteOriginal(onSuccess: () -> Unit) {
		val bitmap = uiState.value.obscuredBitmap ?: return
		uiState.value.photoDef?.let { photo ->
			viewModelScope.launch {
				try {
					imageManager.updateImage(
						bitmap = bitmap,
						photoDef = photo,
					)

					Timber.i("Overwritten original image: ${photo.photoName}")
					showOverwriteSuccessMessage()
					onSuccess()
				} catch (e: Exception) {
					Timber.e(e, "Failed to overwrite original image")
					showSaveErrorMessage()
				}
			}
		} ?: run {
			Timber.e("overwriteOriginal: photoDef was null")
		}
	}

	fun saveAsCopy(onNavigate: (NavKey) -> Unit) {
		val bitmap = uiState.value.obscuredBitmap ?: return
		uiState.value.photoDef?.let { photo ->
			viewModelScope.launch {
				try {
					val newPhotoDef = imageManager.saveImageCopy(
						bitmap = bitmap,
						photoDef = photo,
					)

					Timber.i("Saved copy of image: ${newPhotoDef.photoName}")
					showCopySuccessMessage()
					onNavigate(ViewPhoto(newPhotoDef.photoName))
				} catch (e: Exception) {
					Timber.e(e, "Failed to save copy of image")
					showSaveErrorMessage()
				}
			}
		} ?: run {
			Timber.e("saveAsCopy: photoDef was null")
		}
	}

	private fun showCopySuccessMessage() {
		val message = appContext.getString(R.string.obscure_toast_copy_success)
		showMessage(message)
	}

	private fun showSaveErrorMessage() {
		val message = appContext.getString(R.string.obscure_toast_save_error)
		showMessage(message)
	}

	private fun showOverwriteSuccessMessage() {
		val message = appContext.getString(R.string.obscure_toast_overwrite_success)
		showMessage(message)
	}

	fun startRegionCreation() {
		_uiState.update { it.copy(isCreatingRegion = true, currentRegion = null) }
	}

	fun updateRegion(startX: Int, startY: Int, endX: Int, endY: Int) {
		// Ensure coordinates are in the correct order (top-left to bottom-right)
		val left = minOf(startX, endX)
		val top = minOf(startY, endY)
		val right = maxOf(startX, endX)
		val bottom = maxOf(startY, endY)

		_uiState.update { it.copy(currentRegion = Rect(left, top, right, bottom)) }
	}

	fun finishRegionCreation() {
		uiState.value.currentRegion?.let { rect ->
			// Only add if the region has some size
			if (rect.width() > 5 && rect.height() > 5) {
				val newRegion = ManualRegion(rect)
				val updatedRegions = uiState.value.regions + newRegion
				_uiState.update {
					it.copy(
						regions = updatedRegions,
						isCreatingRegion = false,
						currentRegion = null
					)
				}
				Timber.i("Added manual region: $rect")
			} else {
				// Region too small, just cancel
				cancelRegionCreation()
			}
		} ?: run {
			cancelRegionCreation()
		}
	}

	fun cancelRegionCreation() {
		_uiState.update { it.copy(isCreatingRegion = false, currentRegion = null) }
	}
}

data class ObfuscatePhotoUiState(
	val photoDef: PhotoDef? = null,
	val imageBitmap: ImageBitmap? = null,
	val originalBitmap: Bitmap? = null,
	val obscuredBitmap: Bitmap? = null,
	val isLoading: Boolean = true,
	val isFindingFaces: Boolean = false,
	val showSaveDialog: Boolean = false,
	val regions: List<Region> = emptyList(),
	val isCreatingRegion: Boolean = false,
	val currentRegion: Rect? = null,
)
