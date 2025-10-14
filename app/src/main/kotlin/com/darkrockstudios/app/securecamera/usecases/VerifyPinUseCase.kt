package com.darkrockstudios.app.securecamera.usecases

import com.darkrockstudios.app.securecamera.auth.AuthorizationRepository
import com.darkrockstudios.app.securecamera.camera.SecureImageRepository
import com.darkrockstudios.app.securecamera.security.pin.PinRepository
import com.darkrockstudios.app.securecamera.security.schemes.EncryptionScheme

class VerifyPinUseCase(
    private val imageRepository: SecureImageRepository,
    private val authRepository: AuthorizationRepository,
    private val pinRepository: PinRepository,
    private val encryptionScheme: EncryptionScheme,
    private val authorizePinUseCase: AuthorizePinUseCase,
) {
	suspend fun verifyPin(pin: String): Boolean {
		if (pinRepository.hasPoisonPillPin() && pinRepository.verifyPoisonPillPin(pin)) {
			encryptionScheme.activatePoisonPill(oldPin = pinRepository.getHashedPin())
			imageRepository.activatePoisonPill()
			pinRepository.activatePoisonPill()
		}

		val hashedPin = authorizePinUseCase.authorizePin(pin)
		return if (hashedPin != null) {
			encryptionScheme.deriveAndCacheKey(pin, hashedPin)
			true
		} else {
            authRepository.incrementFailedAttempts()
            false
		}
	}
}
