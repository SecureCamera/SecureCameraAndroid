package com.darkrockstudios.app.securecamera.settings

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.darkrockstudios.app.securecamera.BaseViewModel
import com.darkrockstudios.app.securecamera.LocationPermissionStatus
import com.darkrockstudios.app.securecamera.LocationRepository
import com.darkrockstudios.app.securecamera.R
import com.darkrockstudios.app.securecamera.preferences.AppSettingsDataSource
import com.darkrockstudios.app.securecamera.preferences.PreferencesAppSettingsDataSource.Companion.SESSION_TIMEOUT_DEFAULT
import com.darkrockstudios.app.securecamera.security.SecurityLevel
import com.darkrockstudios.app.securecamera.security.SecurityLevelDetector
import com.darkrockstudios.app.securecamera.security.pin.PinRepository
import com.darkrockstudios.app.securecamera.usecases.PinSizeUseCase
import com.darkrockstudios.app.securecamera.usecases.PinStrengthCheckUseCase
import com.darkrockstudios.app.securecamera.usecases.RemovePoisonPillIUseCase
import com.darkrockstudios.app.securecamera.usecases.SecurityResetUseCase
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val appContext: Context,
    private val preferences: AppSettingsDataSource,
    private val pinRepository: PinRepository,
    private val locationRepository: LocationRepository,
    private val securityResetUseCase: SecurityResetUseCase,
    private val pinStrengthCheck: PinStrengthCheckUseCase,
    private val pinSizeUseCase: PinSizeUseCase,
    private val removePoisonPillIUseCase: RemovePoisonPillIUseCase,
    private val securityLevelDetector: SecurityLevelDetector
) : BaseViewModel<SettingsUiState>() {

	override fun createState() = SettingsUiState(
		securityLevel = securityLevelDetector.detectSecurityLevel(),
		pinSize = pinSizeUseCase.getPinSizeRange(),
	)

	init {
		observePreferences()
		checkPoisonPillStatus()
		refreshLocationPermissionStatus()
	}

	private fun observePreferences() {
		viewModelScope.launch {
			preferences.sanitizeFileName.collect { sanitizeFileName ->
				_uiState.update { it.copy(sanitizeFileName = sanitizeFileName) }
			}
		}

		viewModelScope.launch {
			preferences.sanitizeMetadata.collect { sanitizeMetadata ->
				_uiState.update { it.copy(sanitizeMetadata = sanitizeMetadata) }
			}
		}

		viewModelScope.launch {
			preferences.sessionTimeout.collect { sessionTimeout ->
				_uiState.update { it.copy(sessionTimeout = sessionTimeout) }
			}
		}

		viewModelScope.launch {
			locationRepository.locationPermissionStatus.collect { status ->
				_uiState.update { it.copy(locationPermissionStatus = status) }
			}
		}
	}

	private fun checkPoisonPillStatus() {
		viewModelScope.launch {
			val hasPoisonPillPin = pinRepository.hasPoisonPillPin()
			_uiState.update { it.copy(hasPoisonPillPin = hasPoisonPillPin) }
		}
	}

	fun refreshLocationPermissionStatus() {
		viewModelScope.launch {
			locationRepository.refreshPermissionStatus()
		}
	}

	fun setSanitizeFileName(checked: Boolean) {
		viewModelScope.launch {
			preferences.setSanitizeFileName(checked)
		}
	}

	fun setSanitizeMetadata(checked: Boolean) {
		viewModelScope.launch {
			preferences.setSanitizeMetadata(checked)
		}
	}

	fun setSessionTimeout(timeout: Long) {
		viewModelScope.launch {
			preferences.setSessionTimeout(timeout)
		}
	}

	fun showLocationDialog() {
		_uiState.update { it.copy(showLocationDialog = true) }
	}

	fun dismissLocationDialog() {
		_uiState.update { it.copy(showLocationDialog = false) }
	}

	fun showSecurityResetDialog() {
		_uiState.update { it.copy(showSecurityResetDialog = true) }
	}

	fun dismissSecurityResetDialog() {
		_uiState.update { it.copy(showSecurityResetDialog = false) }
	}

	fun performSecurityReset() {
		viewModelScope.launch {
			securityResetUseCase.reset()
			_uiState.update { it.copy(securityResetComplete = true) }
		}
	}

	fun showPoisonPillDialog() {
		_uiState.update { it.copy(showPoisonPillDialog = true) }
	}

	fun dismissPoisonPillDialog() {
		_uiState.update { it.copy(showPoisonPillDialog = false) }
	}

	fun showPoisonPillPinCreationDialog() {
		_uiState.update {
			it.copy(
				showPoisonPillDialog = false,
				showPoisonPillPinCreationDialog = true
			)
		}
	}

	fun dismissPoisonPillPinCreationDialog() {
		_uiState.update { it.copy(showPoisonPillPinCreationDialog = false) }
	}

	fun setPoisonPillPin(pin: String) {
		viewModelScope.launch {
			pinRepository.setPoisonPillPin(pin)
			_uiState.update {
				it.copy(
					showPoisonPillPinCreationDialog = false,
					hasPoisonPillPin = true,
					showDecoyPhotoExplanationDialog = true
				)
			}
		}
	}

	fun dismissDecoyPhotoExplanationDialog() {
		_uiState.update { it.copy(showDecoyPhotoExplanationDialog = false) }
	}

	fun showRemovePoisonPillDialog() {
		_uiState.update { it.copy(showRemovePoisonPillDialog = true) }
	}

	fun dismissRemovePoisonPillDialog() {
		_uiState.update { it.copy(showRemovePoisonPillDialog = false) }
	}

	fun removePoisonPillPin() {
		viewModelScope.launch {
			removePoisonPillIUseCase.removePoisonPill()
			_uiState.update {
				it.copy(
					showRemovePoisonPillDialog = false,
					hasPoisonPillPin = false,
					poisonPillRemoved = true
				)
			}
		}
	}

	private suspend fun isSameAsAuthPin(pin: String): Boolean {
		return pinRepository.verifySecurityPin(pin)
	}

	suspend fun validatePoisonPillPin(pin: String, confirmPin: String): String? {
		val strongPin = pinStrengthCheck.isPinStrongEnough(pin)
		return if (pin != confirmPin || (pin.length in uiState.value.pinSize).not()) {
			appContext.getString(R.string.pin_creation_error)
		} else if (isSameAsAuthPin(pin)) {
			appContext.getString(R.string.poison_pill_creation_error)
		} else if (strongPin.not()) {
			appContext.getString(R.string.pin_creation_error_weak_pin)
		} else {
			null
		}
	}
}

data class SettingsUiState(
	val sanitizeFileName: Boolean = true,
	val sanitizeMetadata: Boolean = true,
	val sessionTimeout: Long = SESSION_TIMEOUT_DEFAULT,
	val locationPermissionStatus: LocationPermissionStatus = LocationPermissionStatus.DENIED,
	val hasPoisonPillPin: Boolean = false,
	val showLocationDialog: Boolean = false,
	val showSecurityResetDialog: Boolean = false,
	val showPoisonPillDialog: Boolean = false,
	val showPoisonPillPinCreationDialog: Boolean = false,
	val showDecoyPhotoExplanationDialog: Boolean = false,
	val showRemovePoisonPillDialog: Boolean = false,
	val securityResetComplete: Boolean = false,
	val poisonPillRemoved: Boolean = false,
	val securityLevel: SecurityLevel = SecurityLevel.SOFTWARE,
	val pinSize: IntRange,
)
