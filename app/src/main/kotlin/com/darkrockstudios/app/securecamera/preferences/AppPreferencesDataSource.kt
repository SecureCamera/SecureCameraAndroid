package com.darkrockstudios.app.securecamera.preferences

import com.darkrockstudios.app.securecamera.security.SchemeConfig
import kotlinx.coroutines.flow.Flow

interface AppPreferencesDataSource {
	/**
	 * Check if the user has completed the introduction
	 */
	val hasCompletedIntro: Flow<Boolean?> // DELETE ME after beta migration is over
	val isProdReady: Flow<Boolean?>

	/**
	 * Get the sanitized file name preference
	 */
	val sanitizeFileName: Flow<Boolean>
	val sanitizeFileNameDefault: Boolean

	/**
	 * Get the sanitized metadata preference
	 */
	val sanitizeMetadata: Flow<Boolean>
	val sanitizeMetadataDefault: Boolean

	/**
	 * Get the session timeout preference
	 */
	val sessionTimeout: Flow<Long>

	suspend fun getCipherKey(): String
	suspend fun getCipheredPin(): String?

	// DELETE ME after beta migration is over
	suspend fun markProdReady()

	/**
	 * Set the introduction completion status
	 */
	suspend fun setIntroCompleted(completed: Boolean)

	/**
	 * Set the app PIN
	 */
	suspend fun setAppPin(cipheredPin: String, schemeConfigJson: String)

	/**
	 * Set the sanitize file name preference
	 */
	suspend fun setSanitizeFileName(sanitize: Boolean)

	/**
	 * Set the sanitize metadata preference
	 */
	suspend fun setSanitizeMetadata(sanitize: Boolean)

	/**
	 * Get the current failed PIN attempts count
	 */
	suspend fun getFailedPinAttempts(): Int

	/**
	 * Set the failed PIN attempts count
	 */
	suspend fun setFailedPinAttempts(count: Int)

	/**
	 * Get the current timestamp of the last failed PIN attempt
	 */
	suspend fun getLastFailedAttemptTimestamp(): Long

	/**
	 * Set the timestamp of the last failed PIN attempt
	 */
	suspend fun setLastFailedAttemptTimestamp(timestamp: Long)

	/**
	 * Resets all user data and preferences when a security failure occurs.
	 * This deletes all stored preferences including PIN, intro completion status, and security settings.
	 */
	suspend fun securityFailureReset()

	/**
	 * Get the current session timeout value
	 */
	suspend fun getSessionTimeout(): Long

	/**
	 * Set the session timeout value
	 */
	suspend fun setSessionTimeout(timeoutMs: Long)
	suspend fun getSchemeConfig(): SchemeConfig

	/**
	 * Set the Poison Pill PIN
	 */
	suspend fun setPoisonPillPin(cipheredHashedPin: String, cipheredPlainPin: String)

	suspend fun getPlainPoisonPillPin(): String?

	/**
	 * Get the hashed Poison Pill PIN
	 */
	suspend fun getHashedPoisonPillPin(): String?

	/**
	 * Activate the Poison Pill - replaces the regular PIN with the Poison Pill PIN
	 */
	suspend fun activatePoisonPill(ciphered: String)

	/**
	 * Remove the Poison Pill PIN
	 */
	suspend fun removePoisonPillPin()
	suspend fun isPinCiphered(): Boolean
}