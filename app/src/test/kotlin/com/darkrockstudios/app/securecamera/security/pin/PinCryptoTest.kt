package com.darkrockstudios.app.securecamera.security.pin

import com.darkrockstudios.app.securecamera.preferences.HashedPin
import com.darkrockstudios.app.securecamera.security.pin.PinCrypto.Companion.DEFAULT_COST_KIB
import com.darkrockstudios.app.securecamera.security.pin.PinCrypto.Companion.DEFAULT_ITERATIONS
import com.lambdapioneer.argon2kt.Argon2Kt
import com.lambdapioneer.argon2kt.Argon2KtResult
import com.lambdapioneer.argon2kt.Argon2Mode
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.util.*

class PinCryptoTest {
	private val deviceId: ByteArray = "test-device-id-123".encodeToByteArray()
	private lateinit var argon2: Argon2Kt
	private lateinit var crypto: PinCrypto

	private fun ByteArray.b64(): String = Base64.getEncoder().encodeToString(this)

	@Before
	fun setup() {
		argon2 = mockk()

		// Stub Argon2 hash: embed password and salt into a fake encoded string so we can verify later
		every {
			argon2.hash(
				mode = any<Argon2Mode>(),
				password = any<ByteArray>(),
				salt = any<ByteArray>(),
				tCostInIterations = DEFAULT_ITERATIONS,
				mCostInKibibyte = DEFAULT_COST_KIB,
			)
		} answers {
			val password = secondArg<ByteArray>()
			val salt = thirdArg<ByteArray>()
			val encoded = listOf("mocked", password.b64(), salt.b64()).joinToString("$")
			val result = mockk<Argon2KtResult>()
			every { result.encodedOutputAsString() } returns encoded
			result
		}

		crypto = PinCrypto(argon2 = argon2)
	}

	@Test
	fun `hashPin generates salt and hash`() = runTest {
		val pin = "1234"

		val hashedPin: HashedPin = crypto.hashPin(pin, deviceId)

		Assert.assertNotNull("Salt should not be null", hashedPin.salt)
		Assert.assertNotNull("Hash should not be null", hashedPin.hash)
		Assert.assertTrue("Salt should not be empty", hashedPin.salt.isNotEmpty())
		Assert.assertTrue("Hash should not be empty", hashedPin.hash.isNotEmpty())
	}

	@Test
	fun `hashPin generates different hashes for same PIN`() = runTest {
		val pin = "1234"

		val hashedPin1 = crypto.hashPin(pin, deviceId)
		val hashedPin2 = crypto.hashPin(pin, deviceId)

		Assert.assertNotEquals("Salts should be different", hashedPin1.salt, hashedPin2.salt)
		Assert.assertNotEquals("Hashes should be different", hashedPin1.hash, hashedPin2.hash)
	}

	@Test
	fun `verifyPin returns true for correct PIN`() = runTest {
		val pin = "1234"
		val hashedPin = crypto.hashPin(pin, deviceId)

		val want = pin.toByteArray() + deviceId

		every {
			argon2.verify(
				mode = Argon2Mode.ARGON2_I,
				encoded = any<String>(),
				password = match<ByteArray> { it.contentEquals(want) }
			)
		} returns true

		val result = crypto.verifyPin(pin, hashedPin, deviceId)

		Assert.assertTrue("Verification should succeed for correct PIN", result)
	}

	@Test
	fun `verifyPin returns false for incorrect PIN`() = runTest {
		val correctPin = "1234"
		val incorrectPin = "5678"

		every {
			argon2.verify(
				mode = Argon2Mode.ARGON2_I,
				encoded = any<String>(),
				password = any<ByteArray>()
			)
		} returns false

		val hashedPin = crypto.hashPin(correctPin, deviceId)

		val result = crypto.verifyPin(incorrectPin, hashedPin, deviceId)

		Assert.assertFalse("Verification should fail for incorrect PIN", result)
	}

	@Test
	fun `verifyPin handles empty PIN`() = runTest {
		val correctPin = "1234"
		val emptyPin = ""

		every {
			argon2.verify(
				mode = Argon2Mode.ARGON2_I,
				encoded = any<String>(),
				password = any<ByteArray>()
			)
		} returns false

		val hashedPin = crypto.hashPin(correctPin, deviceId)

		val result = crypto.verifyPin(emptyPin, hashedPin, deviceId)

		Assert.assertFalse("Verification should fail for empty PIN", result)
	}
}
