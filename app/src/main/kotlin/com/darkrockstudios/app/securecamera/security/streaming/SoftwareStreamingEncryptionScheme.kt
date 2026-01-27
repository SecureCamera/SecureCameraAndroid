package com.darkrockstudios.app.securecamera.security.streaming

import com.darkrockstudios.app.securecamera.security.schemes.EncryptionScheme
import java.io.File

/**
 * Software-based implementation of StreamingEncryptionScheme.
 * Passes the EncryptionScheme to encryptors/decryptors so they can retrieve
 * key bytes on-demand, minimizing the lifetime of plain-text key material in memory.
 */
class SoftwareStreamingEncryptionScheme(
	private val encryptionScheme: EncryptionScheme
) : StreamingEncryptionScheme {

	override suspend fun createStreamingEncryptor(
		outputFile: File,
		chunkSize: Int
	): StreamingEncryptor {
		return ChunkedStreamingEncryptor(
			outputFile = outputFile,
			encryptionScheme = encryptionScheme,
			chunkSize = chunkSize
		)
	}

	override suspend fun createStreamingDecryptor(encryptedFile: File): StreamingDecryptor {
		return ChunkedStreamingDecryptor(
			encryptedFile = encryptedFile,
			encryptionScheme = encryptionScheme
		)
	}
}
