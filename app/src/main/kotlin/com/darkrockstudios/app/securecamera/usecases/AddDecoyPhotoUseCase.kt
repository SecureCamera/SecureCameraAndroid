package com.darkrockstudios.app.securecamera.usecases

import com.darkrockstudios.app.securecamera.camera.PhotoDef
import com.darkrockstudios.app.securecamera.camera.SecureImageRepository
import com.darkrockstudios.app.securecamera.security.pin.PinRepository
import com.darkrockstudios.app.securecamera.security.schemes.EncryptionScheme

class AddDecoyPhotoUseCase(
	private val pinRepository: PinRepository,
	private val encryptionScheme: EncryptionScheme,
	private val imageRepository: SecureImageRepository,
) {
	suspend fun addDecoyPhoto(photoDef: PhotoDef): Boolean {
		val ppp = pinRepository.getHashedPoisonPillPin() ?: return false
		val plain = pinRepository.getPlainPoisonPillPin() ?: return false
		val keyBytes = encryptionScheme.deriveKey(plainPin = plain, hashedPin = ppp)
		return imageRepository.addDecoyPhotoWithKey(photoDef, keyBytes)
	}
}
