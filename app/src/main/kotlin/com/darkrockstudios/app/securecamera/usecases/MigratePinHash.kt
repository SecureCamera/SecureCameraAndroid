package com.darkrockstudios.app.securecamera.usecases

import at.favre.lib.crypto.bcrypt.BCrypt
import com.darkrockstudios.app.securecamera.preferences.AppPreferencesDataSource
import com.darkrockstudios.app.securecamera.preferences.HashedPin
import com.darkrockstudios.app.securecamera.security.DeviceInfoDataSource
import com.darkrockstudios.app.securecamera.security.SecurityLevel
import com.darkrockstudios.app.securecamera.security.SecurityLevelDetector
import com.darkrockstudios.app.securecamera.security.XorCipher
import com.darkrockstudios.app.securecamera.security.pin.PinRepository.Companion.ARGON_COST
import com.darkrockstudios.app.securecamera.security.pin.PinRepository.Companion.ARGON_ITERATIONS
import com.darkrockstudios.app.securecamera.security.pin.PinRepositoryHardware.Companion.PIN_KEY_ALIAS
import com.darkrockstudios.app.securecamera.security.schemes.EncryptionScheme
import com.darkrockstudios.app.securecamera.security.schemes.HardwareBackedEncryptionScheme
import com.darkrockstudios.app.securecamera.utils.base64Decode
import com.darkrockstudios.app.securecamera.utils.base64Encode
import com.darkrockstudios.app.securecamera.utils.base64EncodeUrlSafe
import com.lambdapioneer.argon2kt.Argon2Kt
import com.lambdapioneer.argon2kt.Argon2KtResult
import com.lambdapioneer.argon2kt.Argon2Mode
import kotlinx.serialization.json.Json
import timber.log.Timber

class MigratePinHash(
	private val detector: SecurityLevelDetector,
	private val dataSource: AppPreferencesDataSource,
	private val encryptionScheme: EncryptionScheme,
	private val removePoisonPillIUseCase: RemovePoisonPillIUseCase,
	private val deviceInfo: DeviceInfoDataSource,
) {
	private val argon2Kt = Argon2Kt()

	suspend fun runMigration(pin: String) {
		if (dataSource.isPinCiphered().not() && verifyLegacyPin(pin)) {
			removePoisonPillIUseCase.removePoisonPill()

			val level = detector.detectSecurityLevel()
			when (level) {
				SecurityLevel.TEE, SecurityLevel.STRONGBOX -> migrateToHardware(pin)
				SecurityLevel.SOFTWARE -> migrateToSoftware(pin)
			}
		}
	}

	private suspend fun verifyLegacyPin(pin: String): Boolean {
		val hashedPin = getLegacyHashedPin()
		val result = BCrypt.verifyer().verify(pin.toCharArray(), hashedPin?.hash?.base64Decode())
		return result.verified
	}

	private suspend fun getLegacyHashedPin(): HashedPin? {
		val key = dataSource.getCipherKey()
		val storedPinJson = dataSource.getCipheredPin()?.let { XorCipher.decrypt(it, key) } ?: return null
		return Json.decodeFromString(storedPinJson)
	}

	private suspend fun migrateToHardware(pin: String) {
		val legacyPin = getLegacyHashedPin()
		val newPin = argonHashPin(pin, legacyPin!!.salt)

		val hashedPinJson = Json.Default.encodeToString(newPin)

		val cipheredHash = encryptionScheme
			.encryptWithKeyAlias(hashedPinJson.toByteArray(), PIN_KEY_ALIAS)
			.base64Encode()
		val configJson = Json.encodeToString(dataSource.getSchemeConfig())

		dataSource.setAppPin(cipheredHash, configJson)

		// Migrate the encryption key, just need to rename it
		encryptionScheme as HardwareBackedEncryptionScheme
		// There should only be 1 key because we removed the Poison Pill
		encryptionScheme.keyDir().listFiles()?.first()?.let { oldKeyFile ->
			val newKeyFile = encryptionScheme.dekFile(newPin)
			oldKeyFile.renameTo(newKeyFile)
		}
	}

	private suspend fun argonHashPin(pin: String, salt: String): HashedPin {
		val hashResult: Argon2KtResult = argon2Kt.hash(
			mode = Argon2Mode.ARGON2_I,
			password = pin.toByteArray() + deviceInfo.getDeviceIdentifier(),
			salt = salt.toByteArray(),
			tCostInIterations = ARGON_ITERATIONS,
			mCostInKibibyte = ARGON_COST,
		)

		return HashedPin(
			hashResult.encodedOutputAsString().toByteArray().base64EncodeUrlSafe(),
			salt
		)
	}

	private suspend fun migrateToSoftware(pin: String) {
		Timber.i("Migrating PIN Hash to argon2")
		val legacyPin = getLegacyHashedPin()
		val schemeConfig = dataSource.getSchemeConfig()
		val key = dataSource.getCipherKey()

		val hashedPin = argonHashPin(pin, legacyPin!!.salt)

		val cipheredHash = XorCipher.encrypt(Json.Default.encodeToString(hashedPin), key)
		val configJson = Json.encodeToString(schemeConfig)

		dataSource.setAppPin(cipheredHash, configJson)
	}
}