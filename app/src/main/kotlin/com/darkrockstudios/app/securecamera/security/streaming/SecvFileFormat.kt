package com.darkrockstudios.app.securecamera.security.streaming

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Constants and utilities for the SECV (Secure Encrypted Camera Video) file format.
 *
 * File Format (Version 1):
 * [Header: 64 bytes] - Located at start of file
 *   - Magic: "SECV" (4 bytes)
 *   - Version: uint16 (2 bytes)
 *   - Chunk size: uint32 (4 bytes)
 *   - Total chunks: uint64 (8 bytes)
 *   - Original size: uint64 (8 bytes)
 *   - Final chunk plaintext size: uint32 (4 bytes)
 *   - Reserved: padding to 64 bytes (34 bytes)
 *
 * [Encrypted Chunks]
 *   - Per chunk: [12-byte IV][ciphertext][16-byte auth tag]
 *
 * Design Rationale:
 * - Chunk offsets calculated arithmetically: offset = 64 + (chunkIndex * (chunkSize + 28))
 */
object SecvFileFormat {
	const val MAGIC = "SECV"
	const val VERSION: Short = 1
	const val HEADER_SIZE = 64
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
	private const val OFFSET_FINAL_CHUNK_SIZE = 26

	/**
	 * Represents the header of a SECV file (metadata at start of file).
	 */
	data class SecvHeader(
		val version: Short,
		val chunkSize: Int,
		val totalChunks: Long,
		val originalSize: Long,
		val finalChunkPlaintextSize: Int
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
			// Final chunk plaintext size
			buffer.putInt(finalChunkPlaintextSize)
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
				val finalChunkPlaintextSize = buffer.int

				return SecvHeader(
					version = version,
					chunkSize = chunkSize,
					totalChunks = totalChunks,
					originalSize = originalSize,
					finalChunkPlaintextSize = finalChunkPlaintextSize
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
	 * Calculate the size of a full encrypted chunk.
	 * Full chunk size = IV (12 bytes) + chunkSize + auth tag (16 bytes) = chunkSize + 28
	 */
	fun calculateFullEncryptedChunkSize(chunkSize: Int): Int {
		return chunkSize + IV_SIZE + AUTH_TAG_SIZE
	}

	/**
	 * Calculate the file offset for a given chunk index.
	 * Offset = header (64 bytes) + (chunkIndex * full encrypted chunk size)
	 */
	fun calculateChunkOffset(chunkIndex: Long, chunkSize: Int): Long {
		return HEADER_SIZE + (chunkIndex * calculateFullEncryptedChunkSize(chunkSize))
	}

	/**
	 * Calculate the plaintext offset for a given chunk index.
	 */
	fun calculatePlaintextOffset(chunkIndex: Long, chunkSize: Int): Long {
		return chunkIndex * chunkSize
	}
}
