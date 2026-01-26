package com.darkrockstudios.app.securecamera.auth

import android.content.Context
import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavKey
import com.darkrockstudios.app.securecamera.BaseViewModel
import com.darkrockstudios.app.securecamera.R
import com.darkrockstudios.app.securecamera.encryption.VideoEncryptionService
import com.darkrockstudios.app.securecamera.gallery.vibrateDevice
import com.darkrockstudios.app.securecamera.navigation.Introduction
import com.darkrockstudios.app.securecamera.usecases.InvalidateSessionUseCase
import com.darkrockstudios.app.securecamera.usecases.PinSizeUseCase
import com.darkrockstudios.app.securecamera.usecases.SecurityResetUseCase
import com.darkrockstudios.app.securecamera.usecases.VerifyPinUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PinVerificationViewModel(
	private val appContext: Context,
	private val authRepository: AuthorizationRepository,
	private val invalidateSessionUseCase: InvalidateSessionUseCase,
	private val securityResetUseCase: SecurityResetUseCase,
	private val verifyPinUseCase: VerifyPinUseCase,
	private val pinSizeUseCase: PinSizeUseCase,
) : BaseViewModel<PinVerificationUiState>() {

	override fun createState() = PinVerificationUiState()

	init {
		loadInitialState()
	}

	private fun loadInitialState() {
		viewModelScope.launch {
			val failedAttempts = authRepository.getFailedAttempts()

			val remainingBackoff = authRepository.calculateRemainingBackoffSeconds()
			val isBackoffActive = remainingBackoff > 0

			_uiState.update {
				it.copy(
					failedAttempts = failedAttempts,
					remainingBackoffSeconds = remainingBackoff,
					isBackoffActive = isBackoffActive,
					error = if (isBackoffActive) PinVerificationError.INVALID_PIN else PinVerificationError.NONE
				)
			}

			if (isBackoffActive) {
				startBackoffCountdown()
			}
		}
	}

	private fun startBackoffCountdown() {
		viewModelScope.launch {
			while (uiState.value.remainingBackoffSeconds > 0) {
				delay(1000)
				_uiState.update {
					it.copy(
						remainingBackoffSeconds = it.remainingBackoffSeconds - 1
					)
				}
			}

			_uiState.update {
				it.copy(
					isBackoffActive = false
				)
			}
		}
	}

	fun validatePin(newPin: String): Boolean {
		val pinSize = pinSizeUseCase.getPinSizeRange()
		return if (newPin.length <= pinSize.max() && newPin.all { char -> char.isDigit() }) {
			clearError()
			true
		} else {
			false
		}
	}

	fun clearError() {
		_uiState.update {
			it.copy(
				error = PinVerificationError.NONE
			)
		}
	}


	fun verify(pin: String, returnKey: NavKey, onNavigate: (NavKey) -> Unit, onFailure: () -> Unit) {
		val currentState = uiState.value

		if (pin.isBlank()) {
			_uiState.update { it.copy(error = PinVerificationError.EMPTY_PIN) }
			return
		}

		if (currentState.isBackoffActive) {
			return
		}

		_uiState.update { it.copy(isVerifying = true) }

		viewModelScope.launch(Dispatchers.Default) {
			val isValid = verifyPinUseCase.verifyPin(pin)

			if (isValid) {
				// Recover any stranded temp video files immediately after auth
				// This ensures unencrypted videos are encrypted ASAP
				VideoEncryptionService.recoverStrandedFiles(appContext)

				withContext(Dispatchers.Main) {
					_uiState.update {
						it.copy(
							failedAttempts = 0
						)
					}
					onNavigate(returnKey)
				}
			} else {
                val newFailedAttempts = authRepository.getFailedAttempts()
                val remainingBackoff = authRepository.calculateRemainingBackoffSeconds()
				val isBackoffActive = remainingBackoff > 0

				withContext(Dispatchers.Main) {
					vibrateDevice(appContext)

					_uiState.update {
						it.copy(
							isVerifying = false,
							failedAttempts = newFailedAttempts,
							remainingBackoffSeconds = remainingBackoff,
							isBackoffActive = isBackoffActive,
							error = PinVerificationError.INVALID_PIN
						)
					}

					if (isBackoffActive) {
						startBackoffCountdown()
					}

					if (newFailedAttempts >= AuthorizationRepository.MAX_FAILED_ATTEMPTS) {
						// Nuke it all
						securityResetUseCase.reset()
						showMessage(appContext.getString(R.string.pin_verification_all_data_deleted))
						onNavigate(Introduction)
					}

					onFailure()
				}
			}
		}
	}

	fun invalidateSession() = invalidateSessionUseCase.invalidateSession()
}

enum class PinVerificationError {
	EMPTY_PIN,
	INVALID_PIN,
	NONE
}

data class PinVerificationUiState(
	val error: PinVerificationError = PinVerificationError.NONE,
	val isVerifying: Boolean = false,
	val failedAttempts: Int = 0,
	val isBackoffActive: Boolean = false,
	val remainingBackoffSeconds: Int = 0
)
