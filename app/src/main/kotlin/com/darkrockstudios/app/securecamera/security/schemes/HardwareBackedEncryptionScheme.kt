package com.darkrockstudios.app.securecamera.security.schemes

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties.*
import android.security.keystore.StrongBoxUnavailableException
import com.darkrockstudios.app.securecamera.preferences.AppSettingsDataSource
import com.darkrockstudios.app.securecamera.preferences.HashedPin
import com.darkrockstudios.app.securecamera.security.DeviceInfoDataSource
import com.darkrockstudios.app.securecamera.security.HardwareSchemeConfig
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.PBKDF2
import dev.whyoleg.cryptography.algorithms.SHA256
import dev.whyoleg.cryptography.algorithms.SHA512
import dev.whyoleg.cryptography.operations.Hasher
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Duration.Companion.minutes


class HardwareBackedEncryptionScheme(
    private val appContext: Context,
    deviceInfo: DeviceInfoDataSource,
    private val appSettingsDataSource: AppSettingsDataSource,
) : SoftwareEncryptionScheme(deviceInfo) {
	private val provider = CryptographyProvider.Default
	private val ks: KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
	private val hasher: Hasher = CryptographyProvider.Default.get(SHA512).hasher()

	override suspend fun deriveKey(
		plainPin: String,
		hashedPin: HashedPin,
	): ByteArray {
		val config = appSettingsDataSource.getSchemeConfig() as HardwareSchemeConfig
		return if (config.ephemeralKey) {
			deriveEphemeralKey(plainPin, hashedPin)
		} else {
			deriveWrappedKey(plainPin, hashedPin)
		}
	}

	@OptIn(ExperimentalStdlibApi::class, ExperimentalEncodingApi::class)
	private suspend fun deriveEphemeralKey(
		plainPin: String,
		hashedPin: HashedPin,
	): ByteArray {
		val dSaltFile = dSaltFile()
		if (dSaltFile.exists().not()) error("dSalt not found!")

		val cipheredDsalt = dSaltFile.readBytes()
		val plainDsalt = decryptWithHardwareBackedKey(cipheredDsalt)
		val encodedDsalt = Base64.encode(plainDsalt)

		val deviceId = deviceInfo.getDeviceIdentifier()
		val encodedDeviceId = Base64.encode(deviceId)

		val dekInput =
			plainPin.toByteArray(Charsets.UTF_8) + encodedDsalt.toByteArray(Charsets.UTF_8) + encodedDeviceId.toByteArray(
				Charsets.UTF_8
			)

		val secretDerivation = provider.get(PBKDF2).secretDerivation(
			digest = SHA256,
			iterations = defaultKeyParams.iterations,
			outputSize = defaultKeyParams.outputSize,
			salt = hashedPin.salt.toByteArray()
		)

		return secretDerivation.deriveSecret(dekInput).toByteArray()
	}

	private suspend fun deriveWrappedKey(
		plainPin: String,
		hashedPin: HashedPin,
	): ByteArray {
		val dekFile = dekFile(hashedPin)
		if (dekFile.exists().not()) {
			createKey(plainPin, hashedPin)
		}

		val cipheredDek = dekFile.readBytes()
		return decryptWithHardwareBackedKey(cipheredDek)
	}

	/**
	 * Creates the Hardware backed key, and creates and encrypts the dSalt
	 * then stores it to disk.
	 */
	override suspend fun createKey(plainPin: String, hashedPin: HashedPin) {
		val config = appSettingsDataSource.getSchemeConfig() as HardwareSchemeConfig

		keyMutex.withLock {
			// Create hardware backed KEK if it doesn't exist
			if (ks.containsAlias(KEY_ALIAS).not()) {
				generateHardwareBackedKey(config)
			}

			if (config.ephemeralKey) {
				createEphemeralKey(plainPin, hashedPin)
			} else {
				createWrappedKey(plainPin, hashedPin)
			}
		}
	}

	private suspend fun createEphemeralKey(plainPin: String, hashedPin: HashedPin) {
		val dSaltFile = dSaltFile()
		if (dSaltFile.exists()) error("dSalt already exists!")

		// Create the dSalt
		val dSalt = ByteArray(DSALT_SIZE)
		SecureRandom.getInstanceStrong().nextBytes(dSalt)

		// Write it to disk
		val cipheredDSalt = encryptWithHardwareBackedKey(dSalt)
		dSaltFile.writeBytes(cipheredDSalt)
	}

	@OptIn(ExperimentalStdlibApi::class, ExperimentalEncodingApi::class)
	private suspend fun createWrappedKey(plainPin: String, hashedPin: HashedPin) {
		// Create the dSalt
		val dSalt = ByteArray(DSALT_SIZE)
		SecureRandom.getInstanceStrong().nextBytes(dSalt)

		// Derive the key
		val encodedDsalt = Base64.encode(dSalt)

		val deviceId = deviceInfo.getDeviceIdentifier()
		val encodedDeviceId = Base64.encode(deviceId)

		val dekInput =
			plainPin.toByteArray(Charsets.UTF_8) + encodedDsalt.toByteArray(Charsets.UTF_8) + encodedDeviceId.toByteArray(
				Charsets.UTF_8
			)

		val secretDerivation = provider.get(PBKDF2).secretDerivation(
			digest = SHA256,
			iterations = defaultKeyParams.iterations,
			outputSize = defaultKeyParams.outputSize,
			salt = hashedPin.salt.toByteArray()
		)

		val dekBytes = secretDerivation.deriveSecret(dekInput).toByteArray()
		val cipheredDek = encryptWithHardwareBackedKey(dekBytes)

		val key = dekFile(hashedPin)
		key.writeBytes(cipheredDek)
	}

	override suspend fun securityFailureReset() {
		// Delete all DEKs
		keyDir().listFiles { dir, name ->
			name.startsWith(DEK_FILENAME_PREFIX)
		}?.forEach { keyFile ->
			keyFile.delete()
		}
	}

	override fun activatePoisonPill(oldPin: HashedPin?) {
		oldPin?.let { dekFile(oldPin).delete() }
	}

	private fun generateHardwareBackedKey(config: HardwareSchemeConfig, keyAlias: String = KEY_ALIAS) {
		// 1. Build the key-generation parameters
		val spec = KeyGenParameterSpec.Builder(
			keyAlias,
			PURPOSE_ENCRYPT or PURPOSE_DECRYPT
		).apply {
			setKeySize(256)
			setBlockModes(BLOCK_MODE_GCM)
			setEncryptionPaddings(ENCRYPTION_PADDING_NONE)
			setUnlockedDeviceRequired(true)

			// Require user authentication for every use
//			if (config.requireBiometricAttestation) {
//				setUserAuthenticationRequired(true)
//				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
//					setUserAuthenticationParameters(config.authTimeout.inWholeSeconds.toInt(), AUTH_BIOMETRIC_STRONG)
//				}
//			}

			// Ask for StrongBox backing
			setIsStrongBoxBacked(true)
		}.build()

		try {
			// 2. Generate the key
			val keyGenerator = KeyGenerator.getInstance(
				KEY_ALGORITHM_AES,
				"AndroidKeyStore"
			)
			keyGenerator.init(spec)
			keyGenerator.generateKey()    // This creates (or replaces) the key in Keystore
		} catch (_: StrongBoxUnavailableException) {
			// Device lacks StrongBox; retry without StrongBox request
			val fallbackSpec = KeyGenParameterSpec.Builder(
				keyAlias,
				PURPOSE_ENCRYPT or PURPOSE_DECRYPT
			).apply {
				setKeySize(256)
				setBlockModes(BLOCK_MODE_GCM)
				setEncryptionPaddings(ENCRYPTION_PADDING_NONE)
				setIsStrongBoxBacked(false)
			}.build()

			val keyGenerator = KeyGenerator.getInstance(KEY_ALGORITHM_AES, "AndroidKeyStore")
			keyGenerator.init(fallbackSpec)
			keyGenerator.generateKey()
		}
	}

	private fun getHardwareEncryptionKey(keyAlias: String): SecretKey {
		return (ks.getEntry(keyAlias, null) as KeyStore.SecretKeyEntry).secretKey
	}

	private fun createKeyForAlias(keyAlias: String) {
		if (ks.containsAlias(keyAlias).not()) {
			generateHardwareBackedKey(HardwareSchemeConfig(false, 5.minutes, false), keyAlias)
		}
	}

	override suspend fun encryptWithKeyAlias(plain: ByteArray, keyAlias: String): ByteArray {
		createKeyForAlias(keyAlias)
		return encryptWithHardwareBackedKey(plain, keyAlias)
	}

	override suspend fun decryptWithKeyAlias(encrypted: ByteArray, keyAlias: String): ByteArray {
		createKeyForAlias(keyAlias)
		return decryptWithHardwareBackedKey(encrypted, keyAlias)
	}

	/**
	 * Encrypts plain using the StrongBox-backed AES key.
	 *
	 * @param plain      The plain text byte array to be encrypted
	 * @throws SecurityException if authentication is required but missing/expired
	 */
	private fun encryptWithHardwareBackedKey(plain: ByteArray, keyAlias: String = KEY_ALIAS): ByteArray {
		val secretKey = getHardwareEncryptionKey(keyAlias)

		// Prepare cipher
		val cipher = Cipher.getInstance(AES_GCM_MODE)
		cipher.init(Cipher.ENCRYPT_MODE, secretKey)

		// returns cipherText || authTag (tag at the end)
		val cipherText = cipher.doFinal(plain)

		// Concatenate IV + cipherText(+tag)
		return ByteArray(cipher.iv.size + cipherText.size).apply {
			System.arraycopy(cipher.iv, 0, this, 0, cipher.iv.size)
			System.arraycopy(cipherText, 0, this, cipher.iv.size, cipherText.size)
		}
	}

	/**
	 * Decrypts data produced by [encryptWithHardwareBackedKey].
	 *
	 * @param encrypted  The byte array formatted as IV ∥ cipherText ∥ authTag
	 * @return           The original plaintext bytes
	 * @throws SecurityException if authentication is required but missing/expired
	 * @throws javax.crypto.AEADBadTagException on tampering or wrong key
	 */
	private fun decryptWithHardwareBackedKey(encrypted: ByteArray, keyAlias: String = KEY_ALIAS): ByteArray {
		require(encrypted.size > IV_LENGTH_BYTES) { "Ciphertext too short" }

		val secretKey = getHardwareEncryptionKey(keyAlias)

		// Split encrypted bytes into parts
		val iv = encrypted.copyOfRange(0, IV_LENGTH_BYTES)
		val cipherAndTag = encrypted.copyOfRange(IV_LENGTH_BYTES, encrypted.size)

		// Init cipher for decryption
		val cipher = Cipher.getInstance(AES_GCM_MODE)
		val gcmSpec = GCMParameterSpec(TAG_LENGTH_BITS, iv)
		cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)

		// returns plaintext, or throws if auth fails
		return cipher.doFinal(cipherAndTag)
	}

	private fun dSaltFile(): File = File(keyDir(), DSALT_FILENAME)

	// Only public for MigratePinHash
	fun keyDir(): File {
		return File(appContext.filesDir, DEK_DIRECTORY).apply { mkdirs() }
	}

	// Only public for MigratePinHash
	@OptIn(ExperimentalEncodingApi::class)
	fun dekFile(hashedPin: HashedPin): File {
		val decoded = String(Base64.decode(hashedPin.hash))
		val hashed = Base64.UrlSafe.encode(hasher.hashBlocking(decoded.toByteArray()))
		return File(keyDir(), "${DEK_FILENAME_PREFIX}_${hashed}")
	}

	companion object {
		private const val KEY_ALIAS = "snapsafe_kek"
		private const val AES_GCM_MODE = "AES/GCM/NoPadding"
		private const val IV_LENGTH_BYTES = 12            // 96-bit IV recommended for GCM
		private const val TAG_LENGTH_BITS = 128            // 16-byte tag appended automatically
		private const val DSALT_SIZE = 64
		private const val DSALT_FILENAME = "dSalt"
		private const val DEK_FILENAME_PREFIX = "dek"
		private const val DEK_DIRECTORY = "keys"
	}
}
