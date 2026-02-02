package com.darkrockstudios.app.securecamera.usecases

import com.darkrockstudios.app.securecamera.auth.AuthorizationRepository
import com.darkrockstudios.app.securecamera.metadata.MetadataManager
import com.darkrockstudios.app.securecamera.preferences.AppSettingsDataSource
import com.darkrockstudios.app.securecamera.security.SchemeConfig
import com.darkrockstudios.app.securecamera.security.pin.PinRepository
import com.darkrockstudios.app.securecamera.security.schemes.EncryptionScheme

class CreatePinUseCase(
    private val authorizationRepository: AuthorizationRepository,
    private val encryptionScheme: EncryptionScheme,
    private val pinRepository: PinRepository,
    private val preferencesDataSource: AppSettingsDataSource,
    private val authorizePinUseCase: AuthorizePinUseCase,
    private val metadataManager: MetadataManager,
) {
	suspend fun createPin(pin: String, schemeConfig: SchemeConfig): Boolean {
		pinRepository.setAppPin(pin, schemeConfig)
		val hashedPin = authorizePinUseCase.authorizePin(pin)
		return if (hashedPin != null) {
			authorizationRepository.createKey(pin, hashedPin)
			encryptionScheme.deriveAndCacheKey(pin, hashedPin)
			metadataManager.loadIndex()
			preferencesDataSource.setIntroCompleted(true)
			true
		} else {
			false
		}
	}
}