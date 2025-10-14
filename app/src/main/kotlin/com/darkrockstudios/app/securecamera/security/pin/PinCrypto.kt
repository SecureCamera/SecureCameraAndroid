package com.darkrockstudios.app.securecamera.security.pin

import com.darkrockstudios.app.securecamera.preferences.HashedPin
import com.darkrockstudios.app.securecamera.utils.base64DecodeUrlSafe
import com.darkrockstudios.app.securecamera.utils.base64EncodeUrlSafe
import com.lambdapioneer.argon2kt.Argon2Kt
import com.lambdapioneer.argon2kt.Argon2KtResult
import com.lambdapioneer.argon2kt.Argon2Mode
import dev.whyoleg.cryptography.random.CryptographyRandom

/**
 * Pure hashing/verification helper for PINs. No I/O, KMP‑friendly.
 * Binds the hash to the provided deviceId bytes by concatenating to the PIN bytes.
 */
class PinCrypto(
	private val argon2: Argon2Kt = Argon2Kt(),
	private val iterations: Int = DEFAULT_ITERATIONS,
	private val costKiB: Int = DEFAULT_COST_KIB,
) {
	fun hashPin(pin: String, deviceId: ByteArray): HashedPin {
		val salt = CryptographyRandom.nextBytes(16)
		val password = pin.toByteArray() + deviceId
		val result: Argon2KtResult = argon2.hash(
			mode = Argon2Mode.ARGON2_I,
			password = password,
			salt = salt,
			tCostInIterations = iterations,
			mCostInKibibyte = costKiB,
		)
		return HashedPin(
			result.encodedOutputAsString().toByteArray().base64EncodeUrlSafe(),
			salt.base64EncodeUrlSafe(),
		)
	}

	fun verifyPin(pin: String, stored: HashedPin, deviceId: ByteArray): Boolean {
		val password = pin.toByteArray() + deviceId
		return argon2.verify(
			mode = Argon2Mode.ARGON2_I,
			encoded = String(stored.hash.base64DecodeUrlSafe()),
			password = password,
		)
	}

	companion object {
		const val DEFAULT_ITERATIONS = 5
		const val DEFAULT_COST_KIB = 65536
	}
}
