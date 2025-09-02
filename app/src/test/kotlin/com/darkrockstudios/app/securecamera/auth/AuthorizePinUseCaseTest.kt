package com.darkrockstudios.app.securecamera.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.darkrockstudios.app.securecamera.TestClock
import com.darkrockstudios.app.securecamera.preferences.AppPreferencesDataSource
import com.darkrockstudios.app.securecamera.preferences.AppPreferencesDataSourceImpl
import com.darkrockstudios.app.securecamera.preferences.HashedPin
import com.darkrockstudios.app.securecamera.security.SoftwareSchemeConfig
import com.darkrockstudios.app.securecamera.security.pin.PinRepository
import com.darkrockstudios.app.securecamera.security.schemes.EncryptionScheme
import com.darkrockstudios.app.securecamera.usecases.AuthorizePinUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import testutil.FakeDataStore
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

class AuthorizePinUseCaseTest {

	private lateinit var context: Context
	private lateinit var preferencesManager: AppPreferencesDataSource
	private lateinit var authorizePinUseCase: AuthorizePinUseCase
	private lateinit var dataStore: DataStore<Preferences>
	private lateinit var encryptionManager: EncryptionScheme
	private lateinit var authManager: AuthorizationRepository
	private lateinit var pinRepository: PinRepository
	private lateinit var clock: TestClock

	private val configJson = Json.encodeToString(SoftwareSchemeConfig)

	@Before
	fun setup() {
		context = mockk(relaxed = true)
		dataStore = FakeDataStore(emptyPreferences())
		preferencesManager = spyk(AppPreferencesDataSourceImpl(context, dataStore))
		encryptionManager = mockk(relaxed = true)
		pinRepository = mockk()
		clock = TestClock(Instant.fromEpochSeconds(1))

		// Default mocks for PinRepository methods
		coEvery { pinRepository.getHashedPin() } returns HashedPin("hashed_pin", "salt")
		coEvery { pinRepository.verifySecurityPin(any()) } returns true
		coEvery { pinRepository.activatePoisonPill() } returns Unit
		coEvery { pinRepository.verifyPoisonPillPin(any()) } returns true
		coEvery { pinRepository.hasPoisonPillPin() } returns true

		authManager = AuthorizationRepository(preferencesManager, encryptionManager, context, clock)
		authorizePinUseCase = AuthorizePinUseCase(authManager, pinRepository)
	}

	@Test
	fun `verifyPin should update authorization state when PIN is valid`() = runTest {
		// Given
		val pin = "1234"
		preferencesManager.setAppPin(pin, configJson)

		// When
		val result = authorizePinUseCase.authorizePin(pin)

		// Then
		assertNotNull(result)
		assertTrue(authManager.isAuthorized.first())
	}

	@Test
	fun `verifyPin should not update authorization state when PIN is invalid`() = runTest {
		// Given
		val correctPin = "1234"
		val incorrectPin = "5678"
		preferencesManager.setAppPin(correctPin, configJson)

		// Mock verifySecurityPin to return false for incorrect PIN
		coEvery { pinRepository.verifySecurityPin(incorrectPin) } returns false

		// When
		val result = authorizePinUseCase.authorizePin(incorrectPin)

		// Then
		assertNull(result)
		assertFalse(authManager.isAuthorized.first())
	}

	@Test
	fun `checkSessionValidity should return false when session has expired`() = runTest {
		// Given
		val pin = "1234"
		preferencesManager.setAppPin(pin, configJson)

		coEvery { pinRepository.getHashedPin() } returns null

		// Set a very small session timeout (1 millisecond)
		preferencesManager.setSessionTimeout(1L)

		authorizePinUseCase.authorizePin(pin)

		// Wait for the session to expire
		Thread.sleep(10)

		// When
		val result = authManager.checkSessionValidity()

		// Then
		assertFalse(result)
		assertFalse(authManager.isAuthorized.value)
	}

	@Test
	fun `revokeAuthorization should reset authorization state`() = runTest {
		// Given
		val pin = "1234"
		preferencesManager.setAppPin(pin, configJson)
		authorizePinUseCase.authorizePin(pin)
		assertTrue(authManager.isAuthorized.first())

		// When
		authManager.revokeAuthorization()

		// Then
		assertFalse(authManager.isAuthorized.first())
	}

	@Test
	fun `setSessionTimeout should update the timeout duration`() = runTest {
		// Given
		val pin = "1234"
		val customTimeout = TimeUnit.SECONDS.toMillis(30)
		preferencesManager.setAppPin(pin, configJson)
		preferencesManager.setSessionTimeout(customTimeout)

		// When
		authorizePinUseCase.authorizePin(pin)

		// Then
		assertTrue(authManager.checkSessionValidity())

		// Fast-forward time but less than the timeout
		Thread.sleep(10)
		assertTrue(authManager.checkSessionValidity())
	}

	@Test
	fun `verifyPin should reset failed attempts when PIN is valid`() = runTest {
		// Given
		val pin = "1234"
		preferencesManager.setAppPin(pin, configJson)
		preferencesManager.setFailedPinAttempts(3)
		preferencesManager.setLastFailedAttemptTimestamp(1000L)

		// When
		val result = authorizePinUseCase.authorizePin(pin)

		// Then
		assertNotNull(result)
		assertEquals(0, preferencesManager.getFailedPinAttempts())
		assertEquals(0L, preferencesManager.getLastFailedAttemptTimestamp())
	}

	@Test
	fun `verifyPin should not update authorization state when PIN is valid but hashedPin is null`() = runTest {
		// Given
		val pin = "1234"
		coEvery { pinRepository.verifySecurityPin(pin) } returns true
		coEvery { pinRepository.getHashedPin() } returns null

		// When
		val result = authorizePinUseCase.authorizePin(pin)

		// Then
		assertNull(result)
		assertFalse(authManager.isAuthorized.first())
		coVerify { pinRepository.verifySecurityPin(pin) }
	}

	@Test
	fun `keepAliveSession should extend session validity`() = runTest {
		// Given
		val pin = "1234"
		preferencesManager.setAppPin(pin, configJson)

		// Set a session timeout
		val sessionTimeout = 1000L // 1 second
		preferencesManager.setSessionTimeout(sessionTimeout)

		// Set initial time in the test clock
		val initialTime = Instant.fromEpochMilliseconds(1000)
		clock.fixedInstant = initialTime

		// Authorize the session
		authorizePinUseCase.authorizePin(pin)
		assertTrue(authManager.isAuthorized.first())

		// Advance time by half the session timeout
		clock.fixedInstant = initialTime.plus((sessionTimeout / 2).milliseconds)

		// Verify session is still valid
		assertTrue(authManager.checkSessionValidity())

		// Keep the session alive
		authManager.keepAliveSession()

		// Advance time beyond the original session timeout
		// but within the timeout of the keep-alive
		clock.fixedInstant = initialTime.plus((sessionTimeout + 100).milliseconds)

		// When
		val result = authManager.checkSessionValidity()

		// Then
		assertTrue(result)
		assertTrue(authManager.isAuthorized.first())
	}
}