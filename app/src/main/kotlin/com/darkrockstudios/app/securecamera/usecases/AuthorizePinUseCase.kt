package com.darkrockstudios.app.securecamera.usecases

import com.darkrockstudios.app.securecamera.auth.AuthorizationRepository
import com.darkrockstudios.app.securecamera.preferences.HashedPin
import com.darkrockstudios.app.securecamera.security.pin.PinRepository

class AuthorizePinUseCase(
	private val authManager: AuthorizationRepository,
	private val pinRepository: PinRepository,
) {
	/**
	 * Authorizes user by verifying the PIN and updates the authorization state if successful.
	 * @param pin The PIN entered by the user
	 * @return True if the PIN is correct, false otherwise
	 */
	suspend fun authorizePin(pin: String): HashedPin? {
		val hashedPin = pinRepository.getHashedPin()
		val isValid = pinRepository.verifySecurityPin(pin)
		return if (isValid && hashedPin != null) {
			authManager.authorizeSession()
			// Reset failed attempts counter on successful verification
			authManager.resetFailedAttempts()
			hashedPin
		} else {
			null
		}
	}
}