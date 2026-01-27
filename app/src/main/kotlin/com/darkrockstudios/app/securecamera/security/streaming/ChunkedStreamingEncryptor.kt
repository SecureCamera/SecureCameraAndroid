package com.darkrockstudios.app.securecamera.security.streaming

import kotlinx.coroutines.Dispatchers
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
 * This encryptor writes data in the SECV trailer format:
 * 1. Encrypted chunks are written sequentially as data arrives
 * 2. On close, the index table and trailer are appended to the end
 * 3. No file rewriting needed, preventing memory spikes on large videos
 *
 * Thread-safe via mutex for all write operations.
 */
class ChunkedStreamingEncryptor(
	private val outputFile: File,
	private val keyBytes: ByteArray,
	private val chunkSize: Int = SecvFileFormat.DEFAULT_CHUNK_SIZE
) : StreamingEncryptor {

	private val mutex = Mutex()
	private val secureRandom = SecureRandom()

	private var randomAccessFile: RandomAccessFile? = null
	private var currentBuffer = ByteArray(chunkSize)
	private var bufferPosition = 0
	private var totalBytesWritten = 0L
	private val chunkIndexEntries = mutableListOf<SecvFileFormat.ChunkIndexEntry>()
	private var isClosed = false
	private var isFlushed = false

	// Track current position in file where we're writing chunk data
	private var currentChunkDataOffset = 0L

	init {
		outputFile.parentFile?.mkdirs()

		randomAccessFile = RandomAccessFile(outputFile, "rw")

		// Chunks are written starting at offset 0
		// Index table and trailer will be appended at the end
		currentChunkDataOffset = 0L
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
	 * Encrypts a chunk of data and writes it to the temporary storage.
	 * Must be called while holding the mutex.
	 */
	private suspend fun writeChunk(data: ByteArray, length: Int) = withContext(Dispatchers.IO) {
		val raf = randomAccessFile ?: throw IllegalStateException("File not open")

		// Generate fresh IV for this chunk
		val iv = ByteArray(SecvFileFormat.IV_SIZE)
		secureRandom.nextBytes(iv)

		// Encrypt the chunk
		val cipher = Cipher.getInstance("AES/GCM/NoPadding")
		val keySpec = SecretKeySpec(keyBytes, "AES")
		val gcmSpec = GCMParameterSpec(SecvFileFormat.AUTH_TAG_SIZE * 8, iv)
		cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)

		val plaintext = if (length == data.size) data else data.copyOf(length)
		val ciphertext = cipher.doFinal(plaintext)

		// Record the chunk index entry
		val encryptedSize = SecvFileFormat.IV_SIZE + ciphertext.size
		chunkIndexEntries.add(
			SecvFileFormat.ChunkIndexEntry(
				offset = currentChunkDataOffset,
				encryptedSize = encryptedSize
			)
		)

		// Write IV + ciphertext (which includes auth tag)
		raf.seek(currentChunkDataOffset)
		raf.write(iv)
		raf.write(ciphertext)

		currentChunkDataOffset += encryptedSize
	}

	override fun close() {
		if (isClosed) return

		val raf = randomAccessFile ?: return

		// Run flush synchronously if needed
		if (!isFlushed && bufferPosition > 0) {
			// Write remaining data synchronously
			val iv = ByteArray(SecvFileFormat.IV_SIZE)
			secureRandom.nextBytes(iv)

			val cipher = Cipher.getInstance("AES/GCM/NoPadding")
			val keySpec = SecretKeySpec(keyBytes, "AES")
			val gcmSpec = GCMParameterSpec(SecvFileFormat.AUTH_TAG_SIZE * 8, iv)
			cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)

			val plaintext = currentBuffer.copyOf(bufferPosition)
			val ciphertext = cipher.doFinal(plaintext)

			val encryptedSize = SecvFileFormat.IV_SIZE + ciphertext.size
			chunkIndexEntries.add(
				SecvFileFormat.ChunkIndexEntry(
					offset = currentChunkDataOffset,
					encryptedSize = encryptedSize
				)
			)

			raf.seek(currentChunkDataOffset)
			raf.write(iv)
			raf.write(ciphertext)
			currentChunkDataOffset += encryptedSize
			bufferPosition = 0
		}

		// Append index table at current position
		for (entry in chunkIndexEntries) {
			raf.write(entry.toByteArray())
		}

		// Append trailer at end (now we know totalChunks and originalSize)
		val trailer = SecvFileFormat.SecvTrailer(
			version = SecvFileFormat.VERSION,
			chunkSize = chunkSize,
			totalChunks = chunkIndexEntries.size.toLong(),
			originalSize = totalBytesWritten
		)
		raf.write(trailer.toByteArray())

		randomAccessFile?.close()
		randomAccessFile = null
		isClosed = true

		// Zero out the key copy and buffer
		keyBytes.fill(0)
		currentBuffer.fill(0)
	}

}
