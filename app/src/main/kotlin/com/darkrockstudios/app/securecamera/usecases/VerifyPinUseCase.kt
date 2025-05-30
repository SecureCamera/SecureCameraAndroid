package com.darkrockstudios.app.securecamera.usecases

import com.darkrockstudios.app.securecamera.auth.AuthorizationRepository
import com.darkrockstudios.app.securecamera.camera.SecureImageRepository
import com.darkrockstudios.app.securecamera.security.pin.PinRepository
import com.darkrockstudios.app.securecamera.security.schemes.EncryptionScheme

class VerifyPinUseCase(
	private val authManager: AuthorizationRepository,
	private val imageManager: SecureImageRepository,
	private val pinRepository: PinRepository,
	private val encryptionScheme: EncryptionScheme,
	private val migratePinHash: MigratePinHash,
) {
	suspend fun verifyPin(pin: String): Boolean {
		migratePinHash.runMigration(pin)

		if (pinRepository.hasPoisonPillPin() && pinRepository.verifyPoisonPillPin(pin)) {
			encryptionScheme.activatePoisonPill(oldPin = pinRepository.getHashedPin())
			imageManager.activatePoisonPill()
			authManager.activatePoisonPill()
		}

		val hashedPin = authManager.verifyPin(pin)
		return if (hashedPin != null) {
			encryptionScheme.deriveAndCacheKey(pin, hashedPin)
			authManager.resetFailedAttempts()
			true
		} else {
			false
		}
	}
}
