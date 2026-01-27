package com.darkrockstudios.app.securecamera.security.streaming

import com.darkrockstudios.app.securecamera.security.schemes.EncryptionScheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Implementation of StreamingDecryptor that decrypts SECV files chunk by chunk.
 * Supports random access for video seeking by using the chunk index table.
 * Reads the trailer format with trailer and index at the end of the file.
 *
 * Thread-safe via mutex for all read operations.
 */
class ChunkedStreamingDecryptor(
	encryptedFile: File,
	private val encryptionScheme: EncryptionScheme
) : StreamingDecryptor {

	private val mutex = Mutex()
	private val randomAccessFile: RandomAccessFile
	private var isClosed = false

	private val trailer: SecvFileFormat.SecvTrailer
	private val chunkIndex: List<SecvFileFormat.ChunkIndexEntry>

	// Cache for the most recently decrypted chunk to avoid re-decryption on sequential reads
	private var cachedChunkIndex: Long = -1
	private var cachedChunkData: ByteArray? = null

	override val totalSize: Long
		get() = trailer.originalSize

	override val chunkSize: Int
		get() = trailer.chunkSize

	init {
		require(encryptedFile.exists()) { "Encrypted file does not exist: ${encryptedFile.absolutePath}" }

		randomAccessFile = RandomAccessFile(encryptedFile, "r") ?: error("Failed to open file for reading")
		val fileLength = randomAccessFile.length()

		// Read trailer from end of file
		val trailerPosition = SecvFileFormat.calculateTrailerPosition(fileLength)
		randomAccessFile.seek(trailerPosition)
		val trailerBytes = ByteArray(SecvFileFormat.TRAILER_SIZE)
		randomAccessFile.readFully(trailerBytes)
		trailer = SecvFileFormat.SecvTrailer.fromByteArray(trailerBytes)

		require(trailer.version == SecvFileFormat.VERSION) {
			"Unsupported SECV version: ${trailer.version}"
		}

		// Read chunk index table (just before trailer)
		val indexPosition = SecvFileFormat.calculateIndexTablePosition(fileLength, trailer.totalChunks)
		randomAccessFile.seek(indexPosition)
		val indexTableSize = trailer.totalChunks * SecvFileFormat.CHUNK_INDEX_ENTRY_SIZE
		val indexBytes = ByteArray(indexTableSize.toInt())
		randomAccessFile.readFully(indexBytes)

		chunkIndex = (0 until trailer.totalChunks).map { i ->
			SecvFileFormat.ChunkIndexEntry.fromByteArray(
				indexBytes,
				(i * SecvFileFormat.CHUNK_INDEX_ENTRY_SIZE).toInt()
			)
		}
	}

	override suspend fun read(position: Long, buffer: ByteArray, offset: Int, length: Int): Int {
		require(position >= 0) { "Position must be non-negative" }
		require(offset >= 0) { "Offset must be non-negative" }
		require(length >= 0) { "Length must be non-negative" }
		require(offset + length <= buffer.size) { "Offset + length exceeds buffer size" }

		if (position >= totalSize) {
			return -1 // EOF
		}

		mutex.withLock {
			check(!isClosed) { "Decryptor is closed" }

			val actualLength = minOf(length.toLong(), totalSize - position).toInt()
			var bytesRead = 0
			var currentPosition = position

			while (bytesRead < actualLength) {
				// Determine which chunk contains the current position
				val chunkIndex = (currentPosition / chunkSize).toInt()
				val offsetInChunk = (currentPosition % chunkSize).toInt()

				// Get decrypted chunk data (from cache or by decrypting)
				val chunkData = getDecryptedChunk(chunkIndex.toLong())

				// Calculate how much we can read from this chunk
				val availableInChunk = chunkData.size - offsetInChunk
				val toRead = minOf(availableInChunk, actualLength - bytesRead)

				// Copy data to output buffer
				System.arraycopy(chunkData, offsetInChunk, buffer, offset + bytesRead, toRead)

				bytesRead += toRead
				currentPosition += toRead
			}

			return bytesRead
		}
	}

	/**
	 * Gets the decrypted data for a specific chunk, using cache when possible.
	 * Must be called while holding the mutex.
	 */
	private suspend fun getDecryptedChunk(chunkIdx: Long): ByteArray {
		// Return cached chunk if available
		val cachedData = cachedChunkData
		if (chunkIdx == cachedChunkIndex && cachedData != null) {
			return cachedData
		}

		val decrypted = decryptChunk(chunkIdx)

		// Update cache
		cachedChunkIndex = chunkIdx
		cachedChunkData = decrypted

		return decrypted
	}

	/**
	 * Decrypts a single chunk from the file.
	 */
	private suspend fun decryptChunk(chunkIdx: Long): ByteArray = withContext(Dispatchers.IO) {
		val raf = randomAccessFile

		require(chunkIdx < chunkIndex.size) { "Chunk index out of bounds: $chunkIdx" }

		val entry = chunkIndex[chunkIdx.toInt()]

		// Read the encrypted chunk data (IV + ciphertext with auth tag)
		val encryptedData = ByteArray(entry.encryptedSize)
		raf.seek(entry.offset)
		raf.readFully(encryptedData)

		val iv = encryptedData.copyOfRange(0, SecvFileFormat.IV_SIZE)
		val ciphertext = encryptedData.copyOfRange(SecvFileFormat.IV_SIZE, encryptedData.size)

		val keyBytes = encryptionScheme.getDerivedKey()
		try {
			// Decrypt
			val cipher = Cipher.getInstance("AES/GCM/NoPadding")
			val keySpec = SecretKeySpec(keyBytes, "AES")
			val gcmSpec = GCMParameterSpec(SecvFileFormat.AUTH_TAG_SIZE * 8, iv)
			cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)

			cipher.doFinal(ciphertext)
		} finally {
			// Immediately zero key bytes after use
			keyBytes.fill(0)
		}
	}

	override fun close() {
		if (isClosed) return

		randomAccessFile.close()
		isClosed = true

		// Clear cache
		cachedChunkData?.fill(0)
		cachedChunkData = null
		cachedChunkIndex = -1
	}
}
