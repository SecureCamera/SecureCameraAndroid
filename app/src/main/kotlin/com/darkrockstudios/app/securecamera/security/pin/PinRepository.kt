package com.darkrockstudios.app.securecamera.security.pin

import com.darkrockstudios.app.securecamera.preferences.HashedPin
import com.darkrockstudios.app.securecamera.security.SchemeConfig

interface PinRepository {
	suspend fun setAppPin(pin: String, schemeConfig: SchemeConfig)
	suspend fun getHashedPin(): HashedPin?

	suspend fun verifySecurityPin(pin: String): Boolean {
		val storedHashedPin = getHashedPin() ?: return false
		return verifyPin(pin, storedHashedPin)
	}

	suspend fun hashPin(pin: String): HashedPin
	suspend fun verifyPin(inputPin: String, storedHash: HashedPin): Boolean
	suspend fun setPoisonPillPin(pin: String)
	suspend fun getPlainPoisonPillPin(): String?
	suspend fun getHashedPoisonPillPin(): HashedPin?
	suspend fun activatePoisonPill()
	suspend fun removePoisonPillPin()

	/**
	 * Check if a Poison Pill PIN is set
	 */
	suspend fun hasPoisonPillPin(): Boolean {
		return getHashedPin() != null && getHashedPoisonPillPin() != null
	}

	/**
	 * Verify if the input PIN matches the Poison Pill PIN
	 */
	suspend fun verifyPoisonPillPin(pin: String): Boolean {
		val storedHashedPin = getHashedPoisonPillPin() ?: return false
		return verifyPin(pin, storedHashedPin)
	}
}