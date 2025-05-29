package com.darkrockstudios.app.securecamera.usecases

import com.darkrockstudios.app.securecamera.auth.AuthorizationRepository
import com.darkrockstudios.app.securecamera.preferences.AppPreferencesDataSource
import com.darkrockstudios.app.securecamera.security.SchemeConfig
import com.darkrockstudios.app.securecamera.security.pin.PinRepository
import com.darkrockstudios.app.securecamera.security.schemes.EncryptionScheme

class CreatePinUseCase(
	private val authorizationRepository: AuthorizationRepository,
	private val encryptionScheme: EncryptionScheme,
	private val pinRepository: PinRepository,
	private val preferencesDataSource: AppPreferencesDataSource,
) {
	suspend fun createPin(pin: String, schemeConfig: SchemeConfig): Boolean {
		pinRepository.setAppPin(pin, schemeConfig)
		val hashedPin = authorizationRepository.verifyPin(pin)
		return if (hashedPin != null) {
			authorizationRepository.createKey(pin, hashedPin)
			encryptionScheme.deriveAndCacheKey(pin, hashedPin)
			preferencesDataSource.setIntroCompleted(true)
			true
		} else {
			false
		}
	}
}