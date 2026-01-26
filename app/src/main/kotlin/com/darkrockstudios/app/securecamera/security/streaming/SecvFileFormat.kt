package com.darkrockstudios.app.securecamera.security.streaming

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Constants and utilities for the SECV (Secure Encrypted Camera Video) file format.
 *
 * File Format:
 * [Header: 64 bytes]
 *   - Magic: "SECV" (4 bytes)
 *   - Version: uint16 (2 bytes)
 *   - Chunk size: uint32 (4 bytes)
 *   - Total chunks: uint64 (8 bytes)
 *   - Original size: uint64 (8 bytes)
 *   - Reserved: padding to 64 bytes (38 bytes)
 *
 * [Chunk Index Table: 12 bytes per chunk]
 *   - Chunk offset: uint64 (8 bytes)
 *   - Encrypted size: uint32 (4 bytes)
 *
 * [Encrypted Chunks]
 *   - Per chunk: [12-byte IV][ciphertext][16-byte auth tag]
 */
object SecvFileFormat {
	const val MAGIC = "SECV"
	const val VERSION: Short = 1
	const val HEADER_SIZE = 64
	const val CHUNK_INDEX_ENTRY_SIZE = 12
	const val IV_SIZE = 12
	const val AUTH_TAG_SIZE = 16
	const val DEFAULT_CHUNK_SIZE = 1_048_576 // 1MB

	const val FILE_EXTENSION = "secv"

	// Header field offsets
	private const val OFFSET_MAGIC = 0
	private const val OFFSET_VERSION = 4
	private const val OFFSET_CHUNK_SIZE = 6
	private const val OFFSET_TOTAL_CHUNKS = 10
	private const val OFFSET_ORIGINAL_SIZE = 18

	/**
	 * Represents the header of a SECV file.
	 */
	data class SecvHeader(
		val version: Short,
		val chunkSize: Int,
		val totalChunks: Long,
		val originalSize: Long
	) {
		fun toByteArray(): ByteArray {
			val buffer = ByteBuffer.allocate(HEADER_SIZE)
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
			fun fromByteArray(bytes: ByteArray): SecvHeader {
				require(bytes.size >= HEADER_SIZE) { "Header too small" }

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

				return SecvHeader(
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
	 * Calculate the offset where chunk data starts (after header and index table).
	 */
	fun calculateChunkDataOffset(totalChunks: Long): Long {
		return HEADER_SIZE + (totalChunks * CHUNK_INDEX_ENTRY_SIZE)
	}

	/**
	 * Calculate the plaintext offset for a given chunk index.
	 */
	fun calculatePlaintextOffset(chunkIndex: Long, chunkSize: Int): Long {
		return chunkIndex * chunkSize
	}
}
