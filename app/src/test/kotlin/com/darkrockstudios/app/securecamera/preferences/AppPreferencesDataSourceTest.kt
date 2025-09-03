package com.darkrockstudios.app.securecamera.preferences

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.darkrockstudios.app.securecamera.security.SchemeConfig
import com.darkrockstudios.app.securecamera.security.SoftwareSchemeConfig
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import testutil.FakeDataStore

class AppPreferencesDataSourceTest {
	private fun newSut(): AppSettingsDataSource {
		val context = mockk<Context>(relaxed = true)
		val dataStore = FakeDataStore<Preferences>(emptyPreferences())
		return PreferencesAppSettingsDataSource(context = context, dataStore = dataStore)
	}

	@Test
	fun `getCipherKey generates and persists Base64 key`() = runTest {
		val sut = newSut()
		val key1 = sut.getCipherKey()
		assertNotNull(key1)
		// 128 bytes -> Base64 length 172
		assertEquals(172, key1.length)
		val key2 = sut.getCipherKey()
		assertEquals(key1, key2)
	}

	@Test
	fun `intro completed flow defaults false and can be updated`() = runTest {
		val sut = newSut()
		assertEquals(false, sut.hasCompletedIntro.first())
		sut.setIntroCompleted(true)
		assertEquals(true, sut.hasCompletedIntro.first())
		sut.setIntroCompleted(false)
		assertEquals(false, sut.hasCompletedIntro.first())
	}

	@Test
	fun `sanitize flags default to true and are settable`() = runTest {
		val sut = newSut()
		assertEquals(true, sut.sanitizeFileName.first())
		assertEquals(true, sut.sanitizeMetadata.first())
		sut.setSanitizeFileName(false)
		sut.setSanitizeMetadata(false)
		assertEquals(false, sut.sanitizeFileName.first())
		assertEquals(false, sut.sanitizeMetadata.first())
	}

	@Test
	fun `session timeout default and set-get`() = runTest {
		val sut = newSut()
		assertEquals(PreferencesAppSettingsDataSource.SESSION_TIMEOUT_DEFAULT, sut.sessionTimeout.first())
		assertEquals(PreferencesAppSettingsDataSource.SESSION_TIMEOUT_DEFAULT, sut.getSessionTimeout())
		val newTimeout = PreferencesAppSettingsDataSource.SESSION_TIMEOUT_1_MIN
		sut.setSessionTimeout(newTimeout)
		assertEquals(newTimeout, sut.sessionTimeout.first())
		assertEquals(newTimeout, sut.getSessionTimeout())
	}

	@Test
	fun `failed pin attempts default 0 and set-get`() = runTest {
		val sut = newSut()
		assertEquals(0, sut.getFailedPinAttempts())
		sut.setFailedPinAttempts(3)
		assertEquals(3, sut.getFailedPinAttempts())
	}

	@Test
	fun `last failed timestamp default 0 and set-get`() = runTest {
		val sut = newSut()
		assertEquals(0L, sut.getLastFailedAttemptTimestamp())
		sut.setLastFailedAttemptTimestamp(12345L)
		assertEquals(12345L, sut.getLastFailedAttemptTimestamp())
	}

	@Test
	fun `setAppPin stores ciphered pin, marks ciphered, and stores scheme config`() = runTest {
		val sut = newSut()
		val scheme: SchemeConfig = SoftwareSchemeConfig
		val schemeJson = Json.encodeToString(SchemeConfig.serializer(), scheme)
		sut.setAppPin(cipheredPin = "ciphered-abc", schemeConfigJson = schemeJson)
		assertEquals("ciphered-abc", sut.getCipheredPin())
		assertTrue(sut.isPinCiphered())
		val decoded = sut.getSchemeConfig()
		assertTrue(decoded is SoftwareSchemeConfig)
	}

	@Test
	fun `poison pill set-get-activate-remove`() = runTest {
		val sut = newSut()
		// Initially none
		assertNull(sut.getPlainPoisonPillPin())
		assertNull(sut.getHashedPoisonPillPin())
		// Set
		sut.setPoisonPillPin(cipheredHashedPin = "hash-x", cipheredPlainPin = "plain-y")
		assertEquals("plain-y", sut.getPlainPoisonPillPin())
		assertEquals("hash-x", sut.getHashedPoisonPillPin())
		// Activate replaces APP_PIN and removes poison values
		sut.activatePoisonPill(ciphered = "activated")
		assertEquals("activated", sut.getCipheredPin())
		assertNull(sut.getPlainPoisonPillPin())
		assertNull(sut.getHashedPoisonPillPin())
		// Explicit remove after setting again
		sut.setPoisonPillPin(cipheredHashedPin = "h2", cipheredPlainPin = "p2")
		sut.removePoisonPillPin()
		assertNull(sut.getPlainPoisonPillPin())
		assertNull(sut.getHashedPoisonPillPin())
	}

	@Test
	fun `securityFailureReset clears data but sets prod ready true`() = runTest {
		val sut = newSut()
		// Seed some values
		sut.setIntroCompleted(true)
		sut.setSanitizeFileName(false)
		sut.setSanitizeMetadata(false)
		sut.setFailedPinAttempts(7)
		sut.setLastFailedAttemptTimestamp(55L)
		sut.setSessionTimeout(PreferencesAppSettingsDataSource.SESSION_TIMEOUT_10_MIN)
		val schemeJson = Json.encodeToString(SchemeConfig.serializer(), SoftwareSchemeConfig)
		sut.setAppPin("pinX", schemeJson)
		sut.setPoisonPillPin("h", "p")

		// Reset
		sut.securityFailureReset()

		// Verify defaults/cleared
		assertEquals(false, sut.hasCompletedIntro.first())
		assertEquals(true, sut.sanitizeFileName.first())
		assertEquals(true, sut.sanitizeMetadata.first())
		assertEquals(0, sut.getFailedPinAttempts())
		assertEquals(0L, sut.getLastFailedAttemptTimestamp())
		assertEquals(PreferencesAppSettingsDataSource.SESSION_TIMEOUT_DEFAULT, sut.sessionTimeout.first())
		assertNull(sut.getCipheredPin())
		assertFalse(sut.isPinCiphered())
		assertNull(sut.getPlainPoisonPillPin())
		assertNull(sut.getHashedPoisonPillPin())
	}
}
