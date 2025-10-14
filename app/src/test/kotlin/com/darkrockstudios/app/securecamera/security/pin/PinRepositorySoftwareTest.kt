package com.darkrockstudios.app.securecamera.security.pin

import com.darkrockstudios.app.securecamera.preferences.AppSettingsDataSource
import com.darkrockstudios.app.securecamera.preferences.HashedPin
import com.darkrockstudios.app.securecamera.security.DeviceInfoDataSource
import com.darkrockstudios.app.securecamera.security.SchemeConfig
import com.darkrockstudios.app.securecamera.security.SoftwareSchemeConfig
import com.darkrockstudios.app.securecamera.security.XorCipher
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PinRepositorySoftwareTest {
	private lateinit var dataSource: AppSettingsDataSource
	private lateinit var deviceInfo: DeviceInfoDataSource
	private lateinit var pinCrypto: PinCrypto
	private lateinit var repo: PinRepositorySoftware

	private val deviceId = "device-id-123".toByteArray()
	private val cipherKey = "c1ph3r-k3y"

	@Before
	fun setup() {
		dataSource = mockk(relaxed = true)
		deviceInfo = mockk()
		pinCrypto = mockk()

		coEvery { deviceInfo.getDeviceIdentifier() } returns deviceId
		coEvery { dataSource.getCipherKey() } returns cipherKey

		repo = PinRepositorySoftware(dataSource, deviceInfo, pinCrypto)
	}

	@Test
	fun `setAppPin should hash and store ciphered pin and config`() = runTest {
		val pin = "1234"
		val hashed = HashedPin(hash = "hash123", salt = "salt123")
		coEvery { pinCrypto.hashPin(pin, deviceId) } returns hashed

		val config: SchemeConfig = SoftwareSchemeConfig
		val configJson = Json.encodeToString(config)
		val expectedCiphered = XorCipher.encrypt(Json.encodeToString(hashed), cipherKey)

		repo.setAppPin(pin, SoftwareSchemeConfig)

		coVerify { dataSource.setAppPin(expectedCiphered, configJson) }
	}

	@Test
	fun `getHashedPin should decrypt and return stored pin`() = runTest {
		val stored = HashedPin("h", "s")
		val storedJson = Json.encodeToString(stored)
		val ciphered = XorCipher.encrypt(storedJson, cipherKey)
		coEvery { dataSource.getCipheredPin() } returns ciphered

		val result = repo.getHashedPin()

		assertNotNull(result)
		assertEquals(stored.hash, result!!.hash)
		assertEquals(stored.salt, result.salt)
	}

	@Test
	fun `getHashedPin returns null when none stored`() = runTest {
		coEvery { dataSource.getCipheredPin() } returns null
		val result = repo.getHashedPin()
		assertNull(result)
	}

	@Test
	fun `hashPin delegates to PinCrypto with device id`() = runTest {
		val pin = "9999"
		val hashed = HashedPin("hh", "ss")
		coEvery { pinCrypto.hashPin(pin, deviceId) } returns hashed

		val result = repo.hashPin(pin)

		assertEquals(hashed, result)
	}

	@Test
	fun `verifyPin delegates to PinCrypto with device id`() = runTest {
		val input = "1111"
		val stored = HashedPin("h1", "s1")
		coEvery { pinCrypto.verifyPin(input, stored, deviceId) } returns true

		assertTrue(repo.verifyPin(input, stored))
	}

	@Test
	fun `verifySecurityPin uses getHashedPin and verifyPin`() = runTest {
		val input = "2222"
		val stored = HashedPin("h2", "s2")
		coEvery { dataSource.getCipheredPin() } returns XorCipher.encrypt(Json.encodeToString(stored), cipherKey)
		coEvery { pinCrypto.verifyPin(input, stored, deviceId) } returns true

		assertTrue(repo.verifySecurityPin(input))
	}

	@Test
	fun `verifySecurityPin returns false when no stored pin`() = runTest {
		coEvery { dataSource.getCipheredPin() } returns null
		assertFalse(repo.verifySecurityPin("0000"))
	}

	@Test
	fun `setPoisonPillPin stores ciphered hashed and plain`() = runTest {
		val ppp = "5678"
		val hashed = HashedPin("ph", "ps")
		coEvery { pinCrypto.hashPin(ppp, deviceId) } returns hashed

		val expectedHashed = XorCipher.encrypt(Json.encodeToString(hashed), cipherKey)
		val expectedPlain = XorCipher.encrypt(ppp, cipherKey)

		repo.setPoisonPillPin(ppp)

		coVerify { dataSource.setPoisonPillPin(expectedHashed, expectedPlain) }
	}

	@Test
	fun `getPlainPoisonPillPin decrypts plain PPP`() = runTest {
		val ppp = "7777"
		val ciphered = XorCipher.encrypt(ppp, cipherKey)
		coEvery { dataSource.getPlainPoisonPillPin() } returns ciphered

		val result = repo.getPlainPoisonPillPin()
		assertEquals(ppp, result)
	}

	@Test
	fun `getPlainPoisonPillPin returns null when not set`() = runTest {
		coEvery { dataSource.getPlainPoisonPillPin() } returns null
		assertNull(repo.getPlainPoisonPillPin())
	}

	@Test
	fun `getHashedPoisonPillPin decrypts and decodes`() = runTest {
		val stored = HashedPin("h3", "s3")
		val ciphered = XorCipher.encrypt(Json.encodeToString(stored), cipherKey)
		coEvery { dataSource.getHashedPoisonPillPin() } returns ciphered

		val result = repo.getHashedPoisonPillPin()
		assertNotNull(result)
		assertEquals(stored, result)
	}

	@Test
	fun `hasPoisonPillPin true only when both main and PPP exist`() = runTest {
		// Main pin present
		coEvery { dataSource.getCipheredPin() } returns XorCipher.encrypt(
			Json.encodeToString(HashedPin("mh", "ms")),
			cipherKey
		)
		// PPP present
		coEvery { dataSource.getHashedPoisonPillPin() } returns XorCipher.encrypt(
			Json.encodeToString(
				HashedPin(
					"ph",
					"ps"
				)
			), cipherKey
		)

		assertTrue(repo.hasPoisonPillPin())
	}

	@Test
	fun `hasPoisonPillPin false when one missing`() = runTest {
		// Main pin present, PPP missing
		coEvery { dataSource.getCipheredPin() } returns XorCipher.encrypt(
			Json.encodeToString(HashedPin("mh", "ms")),
			cipherKey
		)
		coEvery { dataSource.getHashedPoisonPillPin() } returns null
		assertFalse(repo.hasPoisonPillPin())

		// Main pin missing, PPP present
		coEvery { dataSource.getCipheredPin() } returns null
		coEvery { dataSource.getHashedPoisonPillPin() } returns XorCipher.encrypt(
			Json.encodeToString(
				HashedPin(
					"ph",
					"ps"
				)
			), cipherKey
		)
		assertFalse(repo.hasPoisonPillPin())
	}

	@Test
	fun `verifyPoisonPillPin delegates to verifyPin`() = runTest {
		val input = "9898"
		val stored = HashedPin("h9", "s9")
		coEvery { dataSource.getHashedPoisonPillPin() } returns XorCipher.encrypt(
			Json.encodeToString(stored),
			cipherKey
		)
		coEvery { pinCrypto.verifyPin(input, stored, deviceId) } returns true

		assertTrue(repo.verifyPoisonPillPin(input))
	}

	@Test
	fun `activatePoisonPill moves PPP to main and removes PPP`() = runTest {
		val ppp = HashedPin("ph1", "ps1")
		val pppJson = Json.encodeToString(ppp)
		val cipheredPPP = XorCipher.encrypt(pppJson, cipherKey)
		coEvery { dataSource.getHashedPoisonPillPin() } returns cipheredPPP

		val expectedMain = XorCipher.encrypt(pppJson, cipherKey)

		repo.activatePoisonPill()

		coVerify { dataSource.activatePoisonPill(expectedMain) }
		coVerify { dataSource.removePoisonPillPin() }
	}

	@Test(expected = IllegalStateException::class)
	fun `activatePoisonPill throws when no PPP`() = runTest {
		coEvery { dataSource.getHashedPoisonPillPin() } returns null
		repo.activatePoisonPill()
	}

	@Test
	fun `removePoisonPillPin delegates to data source`() = runTest {
		repo.removePoisonPillPin()
		coVerify { dataSource.removePoisonPillPin() }
	}
}
