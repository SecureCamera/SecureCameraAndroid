package com.darkrockstudios.app.securecamera.security.streaming

import java.io.Closeable
import java.io.File

/**
 * Interface for streaming encryption capability.
 * Allows encryption/decryption of large files in chunks without loading the entire file into memory.
 */
interface StreamingEncryptionScheme {
	/**
	 * Creates a streaming encryptor that will write encrypted data to the output file.
	 *
	 * @param outputFile The file to write encrypted data to
	 * @param chunkSize The size of each chunk in bytes (default 1MB)
	 * @return A StreamingEncryptor for writing data
	 */
	suspend fun createStreamingEncryptor(
		outputFile: File,
		chunkSize: Int = SecvFileFormat.DEFAULT_CHUNK_SIZE
	): StreamingEncryptor

	/**
	 * Creates a streaming decryptor for reading encrypted data from a file.
	 *
	 * @param encryptedFile The encrypted SECV file to read from
	 * @return A StreamingDecryptor for reading decrypted data
	 */
	suspend fun createStreamingDecryptor(encryptedFile: File): StreamingDecryptor
}

/**
 * Interface for writing encrypted data in a streaming fashion.
 * Data is buffered and encrypted in chunks.
 */
interface StreamingEncryptor : Closeable {
	/**
	 * Writes data to the encrypted stream.
	 * Data is buffered until a full chunk is available, then encrypted and written.
	 *
	 * @param data The data to write
	 * @param offset The offset in the data array to start reading from
	 * @param length The number of bytes to write
	 */
	suspend fun write(data: ByteArray, offset: Int = 0, length: Int = data.size)

	/**
	 * Flushes any remaining buffered data as a final partial chunk.
	 * This should be called before close() to ensure all data is written.
	 */
	suspend fun flush()

	/**
	 * Closes the encryptor and releases resources.
	 * Automatically calls flush() if not already called.
	 */
	override fun close()
}

/**
 * Interface for reading decrypted data in a streaming fashion.
 * Supports random access to support video seeking.
 */
interface StreamingDecryptor : Closeable {
	/**
	 * The total size of the original (unencrypted) data.
	 */
	val totalSize: Long

	/**
	 * The size of each plaintext chunk (except possibly the last one).
	 */
	val chunkSize: Int

	/**
	 * Reads decrypted data starting at the given position in the original file.
	 *
	 * @param position The position in the original (unencrypted) file to read from
	 * @param buffer The buffer to read data into
	 * @param offset The offset in the buffer to start writing to
	 * @param length The maximum number of bytes to read
	 * @return The number of bytes actually read, or -1 if at end of file
	 */
	suspend fun read(position: Long, buffer: ByteArray, offset: Int = 0, length: Int): Int

	/**
	 * Closes the decryptor and releases resources.
	 */
	override fun close()
}
