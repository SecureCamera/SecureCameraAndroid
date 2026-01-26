package com.darkrockstudios.app.securecamera.security.streaming

import com.darkrockstudios.app.securecamera.security.schemes.EncryptionScheme
import java.io.File

/**
 * Software-based implementation of StreamingEncryptionScheme.
 * Uses the derived encryption key from the parent EncryptionScheme.
 */
class SoftwareStreamingEncryptionScheme(
	private val encryptionScheme: EncryptionScheme
) : StreamingEncryptionScheme {

	override suspend fun createStreamingEncryptor(
		outputFile: File,
		chunkSize: Int
	): StreamingEncryptor {
		val keyBytes = encryptionScheme.getDerivedKey()
		return ChunkedStreamingEncryptor(
			outputFile = outputFile,
			keyBytes = keyBytes.copyOf(), // Make a copy since encryptor will zero it on close
			chunkSize = chunkSize
		)
	}

	override suspend fun createStreamingDecryptor(encryptedFile: File): StreamingDecryptor {
		val keyBytes = encryptionScheme.getDerivedKey()
		return ChunkedStreamingDecryptor(
			encryptedFile = encryptedFile,
			keyBytes = keyBytes.copyOf() // Make a copy since decryptor will zero it on close
		)
	}
}
