package com.darkrockstudios.app.securecamera.auth

import android.content.Context
import com.darkrockstudios.app.securecamera.preferences.AppSettingsDataSource
import com.darkrockstudios.app.securecamera.preferences.HashedPin
import com.darkrockstudios.app.securecamera.security.schemes.EncryptionScheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlin.math.pow
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/**
 * Manages user authorization state, including PIN verification and session expiration.
 */
class AuthorizationRepository(
    private val preferences: AppSettingsDataSource,
    private val encryptionScheme: EncryptionScheme,
    private val context: Context,
    private val clock: Clock,
) {
	companion object {
		const val MAX_FAILED_ATTEMPTS = 10
	}

	private val _isAuthorized = MutableStateFlow(false)
	val isAuthorized: StateFlow<Boolean> = _isAuthorized.asStateFlow()

	private var lastAuthTimeMs: Instant = Instant.DISTANT_PAST
	private var lastKeepAliveMs: Instant = Instant.DISTANT_PAST

	suspend fun securityFailureReset() {
		preferences.securityFailureReset()
	}

	/**
	 * Gets the current number of failed PIN attempts
	 * @return The number of failed attempts
	 */
	suspend fun getFailedAttempts(): Int {
		return preferences.getFailedPinAttempts()
	}

	/**
	 * Sets the number of failed PIN attempts
	 * @param count The number of failed attempts to set
	 */
	suspend fun setFailedAttempts(count: Int) {
		preferences.setFailedPinAttempts(count)
	}

	/**
	 * Increments the failed PIN attempts counter, stores the current timestamp, and returns the new count
	 * @return The updated number of failed attempts
	 */
	suspend fun incrementFailedAttempts(): Int {
		val currentCount = getFailedAttempts()
		val newCount = currentCount + 1
		setFailedAttempts(newCount)

		// Store the current timestamp as the last failed attempt time
		preferences.setLastFailedAttemptTimestamp(System.currentTimeMillis())

		return newCount
	}

	/**
	 * Gets the timestamp of the last failed PIN attempt
	 * @return The timestamp of the last failed attempt
	 */
	suspend fun getLastFailedAttemptTimestamp(): Long {
		return preferences.getLastFailedAttemptTimestamp()
	}

	/**
	 * Calculates the remaining backoff time in seconds based on the number of failed attempts and the last failed attempt timestamp
	 * @return The remaining backoff time in seconds, or 0 if no backoff is needed
	 */
	suspend fun calculateRemainingBackoffSeconds(): Int {
		val failedAttempts = getFailedAttempts()
		if (failedAttempts <= 1) {
			return 0
		}

		val lastFailedTimestamp = getLastFailedAttemptTimestamp()
		if (lastFailedTimestamp <= 0) {
			return 0
		}

		val backoffTime = (2 * 2.0.pow(failedAttempts - 1.0)).toInt()
		val elapsedSeconds = ((System.currentTimeMillis() - lastFailedTimestamp) / 1000).toInt()
		val remainingSeconds = backoffTime - elapsedSeconds

		return if (remainingSeconds > 0) remainingSeconds else 0
	}

	/**
	 * Resets the failed PIN attempts counter to zero and clears the last failed attempt timestamp
	 */
	suspend fun resetFailedAttempts() {
		setFailedAttempts(0)
		preferences.setLastFailedAttemptTimestamp(0)
	}

	/**
	 * Initial key creation
	 */
	suspend fun createKey(pin: String, hashedPin: HashedPin): Boolean {
		encryptionScheme.createKey(pin, hashedPin)
		return true
	}

	/**
	 * Marks the current session as authorized and updates the last authentication time.
	 * Also starts the SessionService to monitor session validity.
	 */
	fun authorizeSession() {
		lastAuthTimeMs = clock.now()
		_isAuthorized.value = true
		startSessionService()
	}

	private fun startSessionService() {
		SessionService.startService(context)
	}

	private fun stopSessionService() {
		SessionService.stopService(context)
	}

	/**
	 * Updates the keep-alive timestamp to extend the session validity
	 * without requiring re-authentication.
	 */
	fun keepAliveSession() {
		if (_isAuthorized.value) {
			lastKeepAliveMs = clock.now()
		}
	}

	/**
	 * Checks if the current session is still valid or has expired.
	 * @return True if the session is valid, false if it has expired
	 */
	fun checkSessionValidity(): Boolean = runBlocking {
		if (!_isAuthorized.value) {
			return@runBlocking false
		}

		val sessionTimeoutMs = preferences.getSessionTimeout().milliseconds
		val currentTime = clock.now()

		// Use lastKeepAliveMs instead of lastAuthTimeMs if it's set
		val timeToCheck = if (lastKeepAliveMs > Instant.DISTANT_PAST) lastKeepAliveMs else lastAuthTimeMs
		val sessionValid = (currentTime - timeToCheck) < sessionTimeoutMs

		if (!sessionValid) {
			_isAuthorized.value = false
		}

		return@runBlocking sessionValid
	}

	/**
	 * Explicitly revokes the current authorization session.
	 * Also stops the SessionService.
	 */
	fun revokeAuthorization() {
		_isAuthorized.value = false
		lastAuthTimeMs = Instant.DISTANT_PAST
		lastKeepAliveMs = Instant.DISTANT_PAST
		stopSessionService()
	}
}
