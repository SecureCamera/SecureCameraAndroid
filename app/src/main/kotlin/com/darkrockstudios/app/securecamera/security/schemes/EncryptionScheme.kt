package com.darkrockstudios.app.securecamera.security.schemes

import com.darkrockstudios.app.securecamera.preferences.HashedPin
import com.darkrockstudios.app.securecamera.security.streaming.StreamingEncryptionScheme
import java.io.File

/**
 * Encryption schemes used to encrypt and decrypt files.
 * We have two concrete Schemes: Software and Hardware
 */
interface EncryptionScheme {
	/**
	 * Encrypts plaintext data and writes it to a file.
	 * This will use the pre-derived key in the cache.
	 */
	suspend fun encryptToFile(plain: ByteArray, targetFile: File)

	/**
	 * Encrypts plaintext data using the provided key and writes it to a file
	 */
	suspend fun encryptToFile(plain: ByteArray, keyBytes: ByteArray, targetFile: File)

	/**
	 * Encrypts plaintext data using the provided key and returns the ciphered bytes
	 */
	suspend fun encrypt(plain: ByteArray, keyBytes: ByteArray): ByteArray

	/**
	 * Encrypts plaintext data using the provided key alias and returns the ciphered bytes
	 */
	suspend fun encryptWithKeyAlias(plain: ByteArray, keyAlias: String): ByteArray

	/**
	 * Decrypts ciphered data using the provided key alias and returns the plain text bytes
	 */
	suspend fun decryptWithKeyAlias(encrypted: ByteArray, keyAlias: String): ByteArray

	/**
	 * Decrypts an encrypted file and returns the plaintext data
	 * This will use the pre-derived key in memory.
	 */
	suspend fun decryptFile(encryptedFile: File): ByteArray

	suspend fun deriveAndCacheKey(plainPin: String, hashedPin: HashedPin)

	/**
	 * Get the currently derived encryption key,
	 * or throws if one hasn't been derived yet.
	 */
	suspend fun getDerivedKey(): ByteArray

	suspend fun deriveKey(plainPin: String, hashedPin: HashedPin): ByteArray

	fun evictKey()

	/**
	 * First time key creation
	 */
	suspend fun createKey(plainPin: String, hashedPin: HashedPin)

	suspend fun securityFailureReset()

	fun activatePoisonPill(oldPin: HashedPin?)

	/**
	 * Returns a streaming encryption scheme for encrypting/decrypting large files
	 * (like videos) in chunks without loading the entire file into memory.
	 *
	 * @return StreamingEncryptionScheme if supported, null otherwise
	 */
	fun getStreamingCapability(): StreamingEncryptionScheme?
}