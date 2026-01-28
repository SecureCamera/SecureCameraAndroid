package com.darkrockstudios.app.securecamera.security.streaming

import com.darkrockstudios.app.securecamera.security.schemes.EncryptionScheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Implementation of StreamingEncryptor that encrypts data in chunks using AES-GCM.
 *
 * This encryptor writes data in the SECV header format:
 * 1. Writes 64-byte placeholder header at start
 * 2. Encrypted chunks are written sequentially as data arrives
 * 3. On close, seeks to position 0 and writes final header with metadata
 *
 * Thread-safe via mutex for all write operations.
 */
class ChunkedStreamingEncryptor(
	private val outputFile: File,
	private val encryptionScheme: EncryptionScheme,
	private val chunkSize: Int = SecvFileFormat.DEFAULT_CHUNK_SIZE
) : StreamingEncryptor {

	private val mutex = Mutex()
	private val secureRandom = SecureRandom()

	private var randomAccessFile: RandomAccessFile? = null
	private var currentBuffer = ByteArray(chunkSize)
	private var bufferPosition = 0
	private var totalBytesWritten = 0L
	private var totalChunks = 0L
	private var finalChunkPlaintextSize = 0
	private var isClosed = false
	private var isFlushed = false

	// Track current position in file where we're writing chunk data
	private var currentChunkDataOffset = 0L

	init {
		outputFile.parentFile?.mkdirs()

		randomAccessFile = RandomAccessFile(outputFile, "rw")

		// Write placeholder header (64 zero bytes) at start
		// Will be filled in with actual metadata on close()
		val placeholderHeader = ByteArray(SecvFileFormat.HEADER_SIZE)
		randomAccessFile?.write(placeholderHeader)

		// Chunks are written starting after the header
		currentChunkDataOffset = SecvFileFormat.HEADER_SIZE.toLong()
	}

	override suspend fun write(data: ByteArray, offset: Int, length: Int) {
		require(offset >= 0) { "Offset must be non-negative" }
		require(length >= 0) { "Length must be non-negative" }
		require(offset + length <= data.size) { "Offset + length exceeds data size" }

		mutex.withLock {
			check(!isClosed) { "Encryptor is closed" }
			check(!isFlushed) { "Cannot write after flush" }

			var remaining = length
			var currentOffset = offset

			while (remaining > 0) {
				val spaceInBuffer = chunkSize - bufferPosition
				val bytesToCopy = minOf(remaining, spaceInBuffer)

				System.arraycopy(data, currentOffset, currentBuffer, bufferPosition, bytesToCopy)
				bufferPosition += bytesToCopy
				currentOffset += bytesToCopy
				remaining -= bytesToCopy
				totalBytesWritten += bytesToCopy

				// If buffer is full, encrypt and write the chunk
				if (bufferPosition == chunkSize) {
					writeChunk(currentBuffer, chunkSize)
					bufferPosition = 0
				}
			}
		}
	}

	override suspend fun flush() {
		mutex.withLock {
			check(!isClosed) { "Encryptor is closed" }

			if (isFlushed) return

			// Write any remaining data as a final partial chunk
			if (bufferPosition > 0) {
				writeChunk(currentBuffer, bufferPosition)
				bufferPosition = 0
			}

			isFlushed = true
		}
	}

	/**
	 * Encrypts a chunk of data and writes it to the file.
	 * Must be called while holding the mutex.
	 */
	private suspend fun writeChunk(data: ByteArray, length: Int) = withContext(Dispatchers.IO) {
		val raf = randomAccessFile ?: throw IllegalStateException("File not open")

		// Generate fresh IV for this chunk
		val iv = ByteArray(SecvFileFormat.IV_SIZE)
		secureRandom.nextBytes(iv)

		val keyBytes = encryptionScheme.getDerivedKey()
		try {
			// Encrypt the chunk
			val cipher = Cipher.getInstance("AES/GCM/NoPadding")
			val keySpec = SecretKeySpec(keyBytes, "AES")
			val gcmSpec = GCMParameterSpec(SecvFileFormat.AUTH_TAG_SIZE * 8, iv)
			cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)

			val plaintext = if (length == data.size) data else data.copyOf(length)
			val ciphertext = cipher.doFinal(plaintext)

			// Track chunk count and final chunk plaintext size
			totalChunks++
			finalChunkPlaintextSize = length

			// Write IV + ciphertext (which includes auth tag)
			raf.seek(currentChunkDataOffset)
			raf.write(iv)
			raf.write(ciphertext)

			val encryptedSize = SecvFileFormat.IV_SIZE + ciphertext.size
			currentChunkDataOffset += encryptedSize
		} finally {
			// Immediately zero key bytes after use
			keyBytes.fill(0)
		}
	}

	override fun close() {
		if (isClosed) return

		val raf = randomAccessFile ?: return

		// Run flush synchronously if needed
		if (!isFlushed && bufferPosition > 0) {
			// Write remaining data synchronously
			val iv = ByteArray(SecvFileFormat.IV_SIZE)
			secureRandom.nextBytes(iv)

			val keyBytes = runBlocking { encryptionScheme.getDerivedKey() }
			try {
				val cipher = Cipher.getInstance("AES/GCM/NoPadding")
				val keySpec = SecretKeySpec(keyBytes, "AES")
				val gcmSpec = GCMParameterSpec(SecvFileFormat.AUTH_TAG_SIZE * 8, iv)
				cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)

				val plaintext = currentBuffer.copyOf(bufferPosition)
				val ciphertext = cipher.doFinal(plaintext)

				// Track chunk count and final chunk plaintext size
				totalChunks++
				finalChunkPlaintextSize = bufferPosition

				raf.seek(currentChunkDataOffset)
				raf.write(iv)
				raf.write(ciphertext)

				val encryptedSize = SecvFileFormat.IV_SIZE + ciphertext.size
				currentChunkDataOffset += encryptedSize
				bufferPosition = 0
			} finally {
				// Immediately zero key bytes after use
				keyBytes.fill(0)
			}
		}

		// Seek to beginning and write header with final metadata
		raf.seek(0)
		val header = SecvFileFormat.SecvHeader(
			version = SecvFileFormat.VERSION,
			chunkSize = chunkSize,
			totalChunks = totalChunks,
			originalSize = totalBytesWritten,
			finalChunkPlaintextSize = finalChunkPlaintextSize
		)
		raf.write(header.toByteArray())

		randomAccessFile?.close()
		randomAccessFile = null
		isClosed = true

		// Zero out buffer
		currentBuffer.fill(0)
	}

}
