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
 * This encryptor writes data in the SECV format:
 * 1. First, data is collected and encrypted in chunks
 * 2. Each chunk is written to a temporary area
 * 3. On close/flush, the header and index table are written
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
	// We'll reserve space for header and index table, then write chunks
	private var currentChunkDataOffset = 0L

	init {
		// Create parent directories if needed
		outputFile.parentFile?.mkdirs()

		// Open file for writing
		randomAccessFile = RandomAccessFile(outputFile, "rw")

		// We don't know the total chunks yet, so we'll write header at the end
		// For now, skip past where the header will go
		// We'll need to rewrite the file structure at close time
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

		// Run flush synchronously if needed
		if (!isFlushed && bufferPosition > 0) {
			// Write remaining data synchronously
			val raf = randomAccessFile ?: return
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

		// Now rewrite the file with proper structure:
		// [Header][Index Table][Chunk Data]
		rewriteWithHeader()

		randomAccessFile?.close()
		randomAccessFile = null
		isClosed = true

		// Zero out the key copy and buffer
		keyBytes.fill(0)
		currentBuffer.fill(0)
	}

	/**
	 * Rewrites the file to include header and index table at the beginning.
	 * This reads all chunk data and rewrites with proper structure.
	 */
	private fun rewriteWithHeader() {
		val raf = randomAccessFile ?: return

		if (chunkIndexEntries.isEmpty()) {
			// No data written, create empty file with just header
			val header = SecvFileFormat.SecvHeader(
				version = SecvFileFormat.VERSION,
				chunkSize = chunkSize,
				totalChunks = 0,
				originalSize = 0
			)
			raf.seek(0)
			raf.write(header.toByteArray())
			raf.setLength(SecvFileFormat.HEADER_SIZE.toLong())
			return
		}

		// Read all chunk data into memory (we need to shift it)
		val totalChunkDataSize = currentChunkDataOffset
		val chunkData = ByteArray(totalChunkDataSize.toInt())
		raf.seek(0)
		raf.readFully(chunkData)

		// Calculate new offsets for chunks (they'll be shifted by header + index size)
		val headerAndIndexSize = SecvFileFormat.HEADER_SIZE +
				(chunkIndexEntries.size * SecvFileFormat.CHUNK_INDEX_ENTRY_SIZE)

		// Update chunk offsets
		val updatedEntries = chunkIndexEntries.mapIndexed { index, entry ->
			val originalOffset = if (index == 0) 0L else chunkIndexEntries[index - 1].let {
				it.offset + it.encryptedSize
			}
			entry.copy(offset = headerAndIndexSize + originalOffset)
		}

		// Write header
		val header = SecvFileFormat.SecvHeader(
			version = SecvFileFormat.VERSION,
			chunkSize = chunkSize,
			totalChunks = chunkIndexEntries.size.toLong(),
			originalSize = totalBytesWritten
		)

		raf.seek(0)
		raf.write(header.toByteArray())

		// Write index table
		var indexOffset = SecvFileFormat.HEADER_SIZE.toLong()
		var dataOffset = headerAndIndexSize.toLong()

		for (i in chunkIndexEntries.indices) {
			val entry = SecvFileFormat.ChunkIndexEntry(
				offset = dataOffset,
				encryptedSize = chunkIndexEntries[i].encryptedSize
			)
			raf.seek(indexOffset)
			raf.write(entry.toByteArray())
			indexOffset += SecvFileFormat.CHUNK_INDEX_ENTRY_SIZE
			dataOffset += chunkIndexEntries[i].encryptedSize
		}

		// Write chunk data at new position
		raf.seek(headerAndIndexSize.toLong())
		raf.write(chunkData)

		// Truncate file to exact size
		raf.setLength(headerAndIndexSize + totalChunkDataSize)
	}
}
