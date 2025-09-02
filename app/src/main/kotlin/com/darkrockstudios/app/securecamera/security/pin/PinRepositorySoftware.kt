package com.darkrockstudios.app.securecamera.security.pin

import com.darkrockstudios.app.securecamera.preferences.AppPreferencesDataSource
import com.darkrockstudios.app.securecamera.preferences.HashedPin
import com.darkrockstudios.app.securecamera.security.DeviceInfoDataSource
import com.darkrockstudios.app.securecamera.security.SchemeConfig
import com.darkrockstudios.app.securecamera.security.XorCipher
import kotlinx.serialization.json.Json
import kotlin.io.encoding.ExperimentalEncodingApi

class PinRepositorySoftware(
	private val dataSource: AppPreferencesDataSource,
	private val deviceInfo: DeviceInfoDataSource,
	private val pinCrypto: PinCrypto,
) : PinRepository {

	override suspend fun setAppPin(pin: String, schemeConfig: SchemeConfig) {
		val hashedPin: HashedPin = hashPin(pin)
		val key = dataSource.getCipherKey()

		val cipheredHash = XorCipher.encrypt(Json.encodeToString(hashedPin), key)
		val config = Json.encodeToString(schemeConfig)
		dataSource.setAppPin(cipheredHash, config)
	}

	override suspend fun getHashedPin(): HashedPin? {
		val key = dataSource.getCipherKey()
		val storedPinJson = dataSource.getCipheredPin()?.let { XorCipher.decrypt(it, key) } ?: return null
		return Json.decodeFromString(HashedPin.serializer(), storedPinJson)
	}

	@OptIn(ExperimentalStdlibApi::class)
	override suspend fun hashPin(pin: String): HashedPin {
		return pinCrypto.hashPin(pin, deviceInfo.getDeviceIdentifier())
	}

	@OptIn(ExperimentalStdlibApi::class)
	override suspend fun verifyPin(inputPin: String, storedHash: HashedPin): Boolean {
		return pinCrypto.verifyPin(inputPin, storedHash, deviceInfo.getDeviceIdentifier())
	}

	/**
	 * Set the Poison Pill PIN
	 */
	@OptIn(ExperimentalEncodingApi::class)
	override suspend fun setPoisonPillPin(pin: String) {
		val hashedPin: HashedPin = hashPin(pin)
		val cipherKey = dataSource.getCipherKey()

		val cipheredHashedPpp = XorCipher.encrypt(Json.encodeToString(hashedPin), cipherKey)
		val cipheredPlainPpp = XorCipher.encrypt(pin, cipherKey)

		dataSource.setPoisonPillPin(cipheredHashedPpp, cipheredPlainPpp)
	}

	@OptIn(ExperimentalEncodingApi::class)
	override suspend fun getPlainPoisonPillPin(): String? {
		val encryptedStoredPin = dataSource.getPlainPoisonPillPin() ?: return null
		return XorCipher.decrypt(encryptedStoredPin, dataSource.getCipherKey())
	}

	/**
	 * Get the hashed Poison Pill PIN
	 */
	override suspend fun getHashedPoisonPillPin(): HashedPin? {
		val cipherKey = dataSource.getCipherKey()
		val storedPinJson = dataSource.getHashedPoisonPillPin()?.let { XorCipher.decrypt(it, cipherKey) } ?: return null
		return Json.decodeFromString(storedPinJson)
	}

	/**
	 * Activate the Poison Pill - replaces the regular PIN with the Poison Pill PIN
	 */
	override suspend fun activatePoisonPill() {
		val poisonPillPin = getHashedPoisonPillPin() ?: error("No PPP")
		val cipherKey = dataSource.getCipherKey()
		val ciphered = XorCipher.encrypt(Json.encodeToString(poisonPillPin), cipherKey)

		dataSource.activatePoisonPill(ciphered)

		removePoisonPillPin()
	}

	/**
	 * Remove the Poison Pill PIN
	 */
	override suspend fun removePoisonPillPin() {
		dataSource.removePoisonPillPin()
	}
}