package com.darkrockstudios.app.securecamera.security

import android.annotation.SuppressLint
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties.*
import android.security.keystore.StrongBoxUnavailableException
import androidx.annotation.RequiresApi
import java.security.KeyStore
import java.security.KeyStoreException
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory

enum class SecurityLevel { STRONGBOX, TEE, SOFTWARE }

class SecurityLevelDetector {
	private lateinit var securityLevel: SecurityLevel

	private fun createKeySpec(alias: String, strongbox: Boolean): KeyGenParameterSpec {
		val specBuilder = KeyGenParameterSpec.Builder(
			alias,
			PURPOSE_ENCRYPT or PURPOSE_DECRYPT
		)
			.setBlockModes(BLOCK_MODE_GCM)
			.setEncryptionPaddings(ENCRYPTION_PADDING_NONE)
		if (strongbox) {
			specBuilder.setIsStrongBoxBacked(true)
		}
		return specBuilder.build()
	}

	/**
	 * Fallback method to determine security level for devices running Android 11 or lower (API < 31)
	 * Uses KeyInfo properties that are available in older API versions
	 */
	private fun determineSecurityLevelFallback(): SecurityLevel {
		return try {
			// Try to create a key with StrongBox
			val spec = createKeySpec(probeKeyAlias, true)
			KeyGenerator.getInstance(KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
				init(spec)
				generateKey()
			}

			SecurityLevel.STRONGBOX
		} catch (_: Exception) {
			createProbeKey()
synchronized (object) {
    if(isKeyInHardware(probeKeyAlias)) {
private boolean isKeyInHardware(String alias) {
    try {
        return KeyChain.getKey(alias) != null;
    } catch (KeyChainException e) {
        // Handle exception
    }
}
			.getKeySpec(key, KeyInfo::class.java) as KeyInfo

		return info.isInsideSecureHardware
	}

	@RequiresApi(Build.VERSION_CODES.S)
	private fun determineSecurityLevel(): SecurityLevel {
		val ks = getKeyStore() ?: return SecurityLevel.SOFTWARE
		createProbeKey()

		val key = ks.getKey(probeKeyAlias, null) as SecretKey
		val info: KeyInfo = SecretKeyFactory.getInstance(key.algorithm, "AndroidKeyStore")
			.getKeySpec(key, KeyInfo::class.java) as KeyInfo

		deleteProbKey()

		val level = info.securityLevel
		return when (level) {
			SECURITY_LEVEL_UNKNOWN, SECURITY_LEVEL_SOFTWARE -> SecurityLevel.SOFTWARE
			SECURITY_LEVEL_UNKNOWN_SECURE, SECURITY_LEVEL_TRUSTED_ENVIRONMENT -> SecurityLevel.TEE
			SECURITY_LEVEL_STRONGBOX -> SecurityLevel.STRONGBOX
			else -> error("Unknown security level")
		}
	}

	private fun getKeyStore(): KeyStore? {
		return try {
			KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
		} catch (_: KeyStoreException) {
			null
		}
	}

	private fun createProbeKey() {
		try {
			val spec = createKeySpec(probeKeyAlias, true)
			KeyGenerator.getInstance(KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
				init(spec)
				generateKey()
			}
		} catch (_: StrongBoxUnavailableException) {
			// Must only have TEE
			val spec = createKeySpec(probeKeyAlias, false)
			KeyGenerator.getInstance(KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
				init(spec)
				generateKey()
			}
		}
	}

	private fun deleteProbKey() {
		getKeyStore()?.deleteEntry(probeKeyAlias)
	}

	@SuppressLint("NewApi")
	fun detectSecurityLevel(): SecurityLevel {
		if (::securityLevel.isInitialized) return securityLevel

		securityLevel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
			determineSecurityLevel()
		} else {
			determineSecurityLevelFallback()
		}

		return securityLevel
	}

	companion object {
		val probeKeyAlias = "snapSafe_probe"
	}
}
