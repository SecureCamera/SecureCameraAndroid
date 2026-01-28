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
 * Supports random access for video seeking by calculating chunk offsets arithmetically.
 * Reads the header format with metadata at the start of the file.
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

	private val header: SecvFileFormat.SecvHeader

	// Cache for the most recently decrypted chunk to avoid re-decryption on sequential reads
	private var cachedChunkIndex: Long = -1
	private var cachedChunkData: ByteArray? = null

	override val totalSize: Long
		get() = header.originalSize

	override val chunkSize: Int
		get() = header.chunkSize

	init {
		require(encryptedFile.exists()) { "Encrypted file does not exist: ${encryptedFile.absolutePath}" }

		randomAccessFile = RandomAccessFile(encryptedFile, "r") ?: error("Failed to open file for reading")

		// Read header from start of file
		randomAccessFile.seek(0)
		val headerBytes = ByteArray(SecvFileFormat.HEADER_SIZE)
		randomAccessFile.readFully(headerBytes)
		header = SecvFileFormat.SecvHeader.fromByteArray(headerBytes)

		require(header.version == SecvFileFormat.VERSION) {
			"Unsupported SECV version: ${header.version}"
		}
	}

	override suspend fun read(position: Long, buffer: ByteArray, offset: Int, length: Int): Int {
		require(position >= 0) { "Position must be non-negative" }
		require(offset >= 0) { "Offset must be non-negative" }
		require(length >= 0) { "Length must be non-negative" }
		require(offset + length <= buffer.size) { "Offset + length exceeds buffer size" }

		// Reading 0 bytes always succeeds and returns 0
		if (length == 0) {
			return 0
		}

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

		require(chunkIdx < header.totalChunks) { "Chunk index out of bounds: $chunkIdx" }

		// Calculate chunk offset arithmetically
		val chunkOffset = SecvFileFormat.calculateChunkOffset(chunkIdx, header.chunkSize)

		// Determine encrypted size based on whether this is the final chunk
		val isFinalChunk = (chunkIdx == header.totalChunks - 1)
		val encryptedSize = if (isFinalChunk) {
			SecvFileFormat.calculateEncryptedChunkSize(header.finalChunkPlaintextSize)
		} else {
			SecvFileFormat.calculateFullEncryptedChunkSize(header.chunkSize)
		}

		// Read the encrypted chunk data (IV + ciphertext with auth tag)
		val encryptedData = ByteArray(encryptedSize)
		raf.seek(chunkOffset)
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
