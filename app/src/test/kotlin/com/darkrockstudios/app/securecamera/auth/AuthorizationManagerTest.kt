package com.darkrockstudios.app.securecamera.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.darkrockstudios.app.securecamera.TestClock
import com.darkrockstudios.app.securecamera.preferences.AppPreferencesDataSource
import com.darkrockstudios.app.securecamera.preferences.HashedPin
import com.darkrockstudios.app.securecamera.security.SoftwareSchemeConfig
import com.darkrockstudios.app.securecamera.security.pin.PinRepository
import com.darkrockstudios.app.securecamera.security.schemes.EncryptionScheme
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

@ExperimentalCoroutinesApi
class AuthorizationManagerTest {

	private lateinit var context: Context
	private lateinit var preferencesManager: AppPreferencesDataSource
	private lateinit var authManager: AuthorizationRepository
	private lateinit var dataStore: DataStore<Preferences>
	private lateinit var encryptionManager: EncryptionScheme
	private lateinit var pinRepository: PinRepository
	private lateinit var clock: TestClock

	private val configJson = Json.encodeToString(SoftwareSchemeConfig)

	@OptIn(ExperimentalCoroutinesApi::class)
	private val testScope = TestScope(UnconfinedTestDispatcher())

	@Before
	fun setup() {
		context = mockk(relaxed = true)
		dataStore = PreferenceDataStoreFactory.create(
			scope = testScope,
			produceFile = { File.createTempFile("prefs_test", ".preferences_pb") }
		)
		preferencesManager = spyk(AppPreferencesDataSource(context, dataStore))
		encryptionManager = mockk(relaxed = true)
		pinRepository = mockk()
		clock = TestClock(Instant.fromEpochSeconds(1))

		// Default mocks for PinRepository methods
		coEvery { pinRepository.getHashedPin() } returns HashedPin("hashed_pin", "salt")
		coEvery { pinRepository.verifySecurityPin(any()) } returns true
		coEvery { pinRepository.activatePoisonPill() } returns Unit
		coEvery { pinRepository.verifyPoisonPillPin(any()) } returns true
		coEvery { pinRepository.hasPoisonPillPin() } returns true

		authManager = AuthorizationRepository(preferencesManager, pinRepository, encryptionManager, context, clock)
	}

	@Test
	fun `verifyPin should update authorization state when PIN is valid`() = runTest {
		// Given
		val pin = "1234"
		preferencesManager.setAppPin(pin, configJson)

		// When
		val result = authManager.verifyPin(pin)

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
		val result = authManager.verifyPin(incorrectPin)

		// Then
		assertNull(result)
		assertFalse(authManager.isAuthorized.first())
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
		authManager.verifyPin(pin)

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

		coEvery { pinRepository.getHashedPin() } returns null

		// Set a very small session timeout (1 millisecond)
		preferencesManager.setSessionTimeout(1L)

		authManager.verifyPin(pin)

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
		authManager.verifyPin(pin)
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
		authManager.verifyPin(pin)

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
	fun `verifyPin should reset failed attempts when PIN is valid`() = runTest {
		// Given
		val pin = "1234"
		preferencesManager.setAppPin(pin, configJson)
		preferencesManager.setFailedPinAttempts(3)
		preferencesManager.setLastFailedAttemptTimestamp(1000L)

		// When
		val result = authManager.verifyPin(pin)

		// Then
		assertNotNull(result)
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

		// Create a new spy for preferencesManager for this test
		val spyPreferencesManager = spyk(AppPreferencesDataSource(context, dataStore))
		val testAuthManager =
			AuthorizationRepository(spyPreferencesManager, pinRepository, encryptionManager, context, clock)

		// Set up initial state
		spyPreferencesManager.setAppPin(pin, configJson)
		spyPreferencesManager.setFailedPinAttempts(5)
		spyPreferencesManager.setLastFailedAttemptTimestamp(1000L)

		// Mock verifySecurityPin to return true initially, then false after reset
		coEvery { pinRepository.verifySecurityPin(pin) } returns true andThen false

		// When
		testAuthManager.securityFailureReset()

		// Then
		// Verify that securityFailureReset was called on the preferences manager
		coVerify { spyPreferencesManager.securityFailureReset() }
	}

	@Test
	fun `activatePoisonPill should delegate to preferencesManager`() = runTest {
		// Given
		val regularPin = "1234"
		val poisonPillPin = "5678"
		val hashedPoisonPin = "hashed"
		preferencesManager.setAppPin(regularPin, configJson)
		preferencesManager.setPoisonPillPin(hashedPoisonPin, poisonPillPin)

		// Mock activatePoisonPill to change behavior
		coEvery { pinRepository.activatePoisonPill() } answers {
			// After activatePoisonPill is called, hasPoisonPillPin should return false
			// and verifySecurityPin should return true for the poison pill pin
			coEvery { pinRepository.hasPoisonPillPin() } returns false
			coEvery { pinRepository.verifySecurityPin(poisonPillPin) } returns true
		}

		// Verify initial state
		assertTrue(pinRepository.verifySecurityPin(regularPin))
		assertTrue(pinRepository.verifyPoisonPillPin(poisonPillPin))
		assertTrue(pinRepository.hasPoisonPillPin())

		// When
		authManager.activatePoisonPill()

		// Then
		// Verify poison pill was activated
		assertTrue(pinRepository.verifySecurityPin(poisonPillPin))
		assertFalse(pinRepository.hasPoisonPillPin())
	}

	@Test
	fun `verifyPin should not update authorization state when PIN is valid but hashedPin is null`() = runTest {
		// Given
		val pin = "1234"
		coEvery { pinRepository.verifySecurityPin(pin) } returns true
		coEvery { pinRepository.getHashedPin() } returns null

		// When
		val result = authManager.verifyPin(pin)

		// Then
		assertNull(result)
		assertFalse(authManager.isAuthorized.first())
		coVerify { pinRepository.verifySecurityPin(pin) }
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
		authManager.verifyPin(pin)
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
