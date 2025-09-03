package com.darkrockstudios.app.securecamera.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.darkrockstudios.app.securecamera.TestClock
import com.darkrockstudios.app.securecamera.preferences.AppSettingsDataSource
import com.darkrockstudios.app.securecamera.preferences.PreferencesAppSettingsDataSource
import com.darkrockstudios.app.securecamera.preferences.HashedPin
import com.darkrockstudios.app.securecamera.security.SoftwareSchemeConfig
import com.darkrockstudios.app.securecamera.security.pin.PinRepository
import com.darkrockstudios.app.securecamera.security.schemes.EncryptionScheme
import com.darkrockstudios.app.securecamera.usecases.AuthorizePinUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import testutil.FakeDataStore
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@ExperimentalCoroutinesApi
class AuthorizationManagerTest {

	private lateinit var context: Context
	private lateinit var preferencesManager: AppSettingsDataSource
	private lateinit var authManager: AuthorizationRepository
	private lateinit var authorizePinUseCase: AuthorizePinUseCase
	private lateinit var pinRepository: PinRepository
	private lateinit var dataStore: DataStore<Preferences>
	private lateinit var encryptionManager: EncryptionScheme
	private lateinit var clock: TestClock

	private val configJson = Json.encodeToString(SoftwareSchemeConfig)

	@Before
	fun setup() {
		context = mockk(relaxed = true)
		dataStore = FakeDataStore(emptyPreferences())
		preferencesManager = spyk(PreferencesAppSettingsDataSource(context, dataStore))
		encryptionManager = mockk(relaxed = true)
		clock = TestClock(Instant.fromEpochSeconds(1))

		authManager = AuthorizationRepository(preferencesManager, encryptionManager, context, clock)

		pinRepository = mockk()
		coEvery { pinRepository.getHashedPin() } returns HashedPin("hashed_pin", "salt")
		coEvery { pinRepository.verifySecurityPin(any()) } returns true

		authorizePinUseCase = AuthorizePinUseCase(authManager, pinRepository)
	}

	@Test
	fun `checkSessionValidity should return false when not authorized`() {
		// Given
		// Initial state is not authorized

		// When
		val result = authManager.checkSessionValidity()

		// Then
		assertFalse(result)
		assertFalse(authManager.isAuthorized.value)
	}

	@Test
	fun `checkSessionValidity should return true when session is valid`() = runTest {
		// Given
		val pin = "1234"
		preferencesManager.setAppPin(pin, configJson)
		authorizePinUseCase.authorizePin(pin)

		// When
		val result = authManager.checkSessionValidity()

		// Then
		assertTrue(result)
		assertTrue(authManager.isAuthorized.value)
	}

	@Test
	fun `checkSessionValidity should return false when session has expired`() = runTest {
		// Given
		val pin = "1234"
		preferencesManager.setAppPin(pin, configJson)

		// Set a very small session timeout (1 millisecond)
		preferencesManager.setSessionTimeout(1L)

		authorizePinUseCase.authorizePin(pin)

		clock.advanceBy(1.seconds)

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
	fun `getFailedAttempts should return the count from preferences`() = runTest {
		// Given
		val expectedCount = 3
		preferencesManager.setFailedPinAttempts(expectedCount)

		// When
		val result = authManager.getFailedAttempts()

		// Then
		assertEquals(expectedCount, result)
	}

	@Test
	fun `setFailedAttempts should update the count in preferences`() = runTest {
		// Given
		val count = 5

		// When
		authManager.setFailedAttempts(count)

		// Then
		assertEquals(count, preferencesManager.getFailedPinAttempts())
	}

	@Test
	fun `incrementFailedAttempts should increase the count by one and store timestamp`() = runTest {
		// Given
		val initialCount = 2
		val expectedCount = initialCount + 1
		preferencesManager.setFailedPinAttempts(initialCount)
		preferencesManager.setLastFailedAttemptTimestamp(0) // Reset timestamp

		// When
		val result = authManager.incrementFailedAttempts()

		// Then
		assertEquals(expectedCount, result)
		assertEquals(expectedCount, preferencesManager.getFailedPinAttempts())
		assertTrue(preferencesManager.getLastFailedAttemptTimestamp() > 0) // Timestamp should be updated
	}

	@Test
	fun `resetFailedAttempts should set the count to zero and reset timestamp`() = runTest {
		// Given
		preferencesManager.setFailedPinAttempts(5)
		preferencesManager.setLastFailedAttemptTimestamp(1000L)

		// When
		authManager.resetFailedAttempts()

		// Then
		assertEquals(0, preferencesManager.getFailedPinAttempts())
		assertEquals(0L, preferencesManager.getLastFailedAttemptTimestamp())
	}


	@Test
	fun `getLastFailedAttemptTimestamp should return the timestamp from preferences`() = runTest {
		// Given
		val expectedTimestamp = 1234567890L
		preferencesManager.setLastFailedAttemptTimestamp(expectedTimestamp)

		// When
		val result = authManager.getLastFailedAttemptTimestamp()

		// Then
		assertEquals(expectedTimestamp, result)
	}

	@Test
	fun `calculateRemainingBackoffSeconds should return 0 when no failed attempts`() = runTest {
		// Given
		preferencesManager.setFailedPinAttempts(0)

		// When
		val result = authManager.calculateRemainingBackoffSeconds()

		// Then
		assertEquals(0, result)
	}

	@Test
	fun `calculateRemainingBackoffSeconds should return 0 when no timestamp`() = runTest {
		// Given
		preferencesManager.setFailedPinAttempts(3)
		preferencesManager.setLastFailedAttemptTimestamp(0L)

		// When
		val result = authManager.calculateRemainingBackoffSeconds()

		// Then
		assertEquals(0, result)
	}

	@Test
	fun `calculateRemainingBackoffSeconds should calculate correct backoff time`() = runTest {
		// Given
		val failedAttempts = 3
		val backoffTime = (2 * Math.pow(2.0, failedAttempts - 1.0)).toInt() // 8 seconds
		val currentTime = System.currentTimeMillis()
		val lastFailedTime = currentTime - 3000 // 3 seconds ago

		preferencesManager.setFailedPinAttempts(failedAttempts)
		preferencesManager.setLastFailedAttemptTimestamp(lastFailedTime)

		// When
		val result = authManager.calculateRemainingBackoffSeconds()

		// Then
		// Should be approximately 5 seconds remaining (8 - 3)
		assertTrue(result > 0 && result <= backoffTime)
	}

	@Test
	fun `securityFailureReset should delegate to preferencesManager`() = runTest {
		// Given
		val pin = "1234"

		// Create a new AuthorizationRepository for this test
		val testAuthManager =
			AuthorizationRepository(preferencesManager, encryptionManager, context, clock)

		// Set up initial state
		preferencesManager.setAppPin(pin, configJson)
		preferencesManager.setFailedPinAttempts(5)
		preferencesManager.setLastFailedAttemptTimestamp(1000L)

		// Mock verifySecurityPin to return true initially, then false after reset
		coEvery { pinRepository.verifySecurityPin(pin) } returns true andThen false

		// When
		testAuthManager.securityFailureReset()

		// Then
		// Verify that securityFailureReset was called on the preferences manager
		coVerify { preferencesManager.securityFailureReset() }
	}


	@Test
	fun `calculateRemainingBackoffSeconds should return 0 when elapsed time exceeds backoff time`() = runTest {
		// Given
		val failedAttempts = 2
		val currentTime = System.currentTimeMillis()
		val lastFailedTime = currentTime - 5000 // 5 seconds ago (exceeds backoff time)

		coEvery { preferencesManager.getFailedPinAttempts() } returns failedAttempts
		coEvery { preferencesManager.getLastFailedAttemptTimestamp() } returns lastFailedTime

		// When
		val result = authManager.calculateRemainingBackoffSeconds()

		// Then
		assertEquals(0, result)
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
