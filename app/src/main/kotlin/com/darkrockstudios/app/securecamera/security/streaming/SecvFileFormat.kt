package com.darkrockstudios.app.securecamera.security.streaming

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Constants and utilities for the SECV (Secure Encrypted Camera Video) file format.
 *
 * File Format:
 * [Encrypted Chunks]
 *   - Per chunk: [12-byte IV][ciphertext][16-byte auth tag]
 *
 * [Chunk Index Table: 12 bytes per chunk]
 *   - Chunk offset: uint64 (8 bytes)
 *   - Encrypted size: uint32 (4 bytes)
 *
 * [Trailer: 64 bytes] - Located at end of file
 *   - Magic: "SECV" (4 bytes)
 *   - Version: uint16 (2 bytes)
 *   - Chunk size: uint32 (4 bytes)
 *   - Total chunks: uint64 (8 bytes)
 *   - Original size: uint64 (8 bytes)
 *   - Reserved: padding to 64 bytes (38 bytes)
 *
 * The trailer format (chunks first, metadata at end) eliminates the need
 * to rewrite the entire file when encryption completes, preventing memory
 * spikes from loading large videos into RAM.
 */
object SecvFileFormat {
	const val MAGIC = "SECV"
	const val VERSION: Short = 1
	const val TRAILER_SIZE = 64
	const val CHUNK_INDEX_ENTRY_SIZE = 12
	const val IV_SIZE = 12
	const val AUTH_TAG_SIZE = 16
	const val DEFAULT_CHUNK_SIZE = 1_048_576 // 1MB

	const val FILE_EXTENSION = "secv"

	// Trailer field offsets
	private const val OFFSET_MAGIC = 0
	private const val OFFSET_VERSION = 4
	private const val OFFSET_CHUNK_SIZE = 6
	private const val OFFSET_TOTAL_CHUNKS = 10
	private const val OFFSET_ORIGINAL_SIZE = 18

	/**
	 * Represents the trailer of a SECV file (metadata at end of file).
	 */
	data class SecvTrailer(
		val version: Short,
		val chunkSize: Int,
		val totalChunks: Long,
		val originalSize: Long
	) {
		fun toByteArray(): ByteArray {
			val buffer = ByteBuffer.allocate(TRAILER_SIZE)
			buffer.order(ByteOrder.LITTLE_ENDIAN)

			// Magic
			buffer.put(MAGIC.toByteArray(Charsets.US_ASCII))
			// Version
			buffer.putShort(version)
			// Chunk size
			buffer.putInt(chunkSize)
			// Total chunks
			buffer.putLong(totalChunks)
			// Original size
			buffer.putLong(originalSize)
			// Reserved (remaining bytes are zero by default)

			return buffer.array()
		}

		companion object {
			fun fromByteArray(bytes: ByteArray): SecvTrailer {
				require(bytes.size >= TRAILER_SIZE) { "Trailer too small" }

				val buffer = ByteBuffer.wrap(bytes)
				buffer.order(ByteOrder.LITTLE_ENDIAN)

				// Verify magic
				val magic = ByteArray(4)
				buffer.get(magic)
				require(String(magic, Charsets.US_ASCII) == MAGIC) { "Invalid SECV magic" }

				val version = buffer.short
				val chunkSize = buffer.int
				val totalChunks = buffer.long
				val originalSize = buffer.long

				return SecvTrailer(
					version = version,
					chunkSize = chunkSize,
					totalChunks = totalChunks,
					originalSize = originalSize
				)
			}
		}
	}

	/**
	 * Represents an entry in the chunk index table.
	 */
	data class ChunkIndexEntry(
		val offset: Long,
		val encryptedSize: Int
	) {
		fun toByteArray(): ByteArray {
			val buffer = ByteBuffer.allocate(CHUNK_INDEX_ENTRY_SIZE)
			buffer.order(ByteOrder.LITTLE_ENDIAN)
			buffer.putLong(offset)
			buffer.putInt(encryptedSize)
			return buffer.array()
		}

		companion object {
			fun fromByteArray(bytes: ByteArray, offset: Int = 0): ChunkIndexEntry {
				val buffer = ByteBuffer.wrap(bytes, offset, CHUNK_INDEX_ENTRY_SIZE)
				buffer.order(ByteOrder.LITTLE_ENDIAN)
				return ChunkIndexEntry(
					offset = buffer.long,
					encryptedSize = buffer.int
				)
			}
		}
	}

	/**
	 * Calculate the size of encrypted data for a given plaintext size.
	 * Encrypted size = IV (12 bytes) + ciphertext (same as plaintext) + auth tag (16 bytes)
	 */
	fun calculateEncryptedChunkSize(plaintextSize: Int): Int {
		return IV_SIZE + plaintextSize + AUTH_TAG_SIZE
	}

	/**
	 * Calculate the position of the trailer in the file (last 64 bytes).
	 * For trailer format, trailer is at: fileLength - TRAILER_SIZE
	 */
	fun calculateTrailerPosition(fileLength: Long): Long {
		return fileLength - TRAILER_SIZE
	}

	/**
	 * Calculate the position of the index table in the file.
	 * For trailer format, index is at: fileLength - TRAILER_SIZE - (totalChunks * CHUNK_INDEX_ENTRY_SIZE)
	 */
	fun calculateIndexTablePosition(fileLength: Long, totalChunks: Long): Long {
		return fileLength - TRAILER_SIZE - (totalChunks * CHUNK_INDEX_ENTRY_SIZE)
	}

	/**
	 * Calculate the plaintext offset for a given chunk index.
	 */
	fun calculatePlaintextOffset(chunkIndex: Long, chunkSize: Int): Long {
		return chunkIndex * chunkSize
	}
}
