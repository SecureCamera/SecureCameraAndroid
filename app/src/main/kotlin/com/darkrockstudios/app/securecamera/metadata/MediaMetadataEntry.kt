package com.darkrockstudios.app.securecamera.metadata

import com.darkrockstudios.app.securecamera.camera.MediaType
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Represents a single media metadata entry stored in the SECM sidecar file.
 *
 * @property filename Current filename of the media file (e.g., "img_550e8400e29b41d4a716446655440000.jpg")
 * @property originalTimestamp Epoch milliseconds when the media was captured, used for sorting
 * @property mediaType Type of media (PHOTO or VIDEO)
 * @property originalFilename Pre-migration filename for audit trail (nullable)
 * @property fileSize Size of the media file in bytes, for integrity checking
 */
data class MediaMetadataEntry(
	val filename: String,
	val originalTimestamp: Long,
	val mediaType: MediaType,
	val originalFilename: String? = null,
	val fileSize: Long = 0
) {
	/**
	 * Serializes this entry to a 140-byte plaintext buffer.
	 *
	 * Layout (140 bytes total):
	 * - Offset 0: Status (1 byte) - always ACTIVE when serializing
	 * - Offset 1: Filename (48 bytes, null-padded)
	 * - Offset 49: Timestamp (8 bytes, int64)
	 * - Offset 57: MediaType (1 byte, 0=PHOTO, 1=VIDEO)
	 * - Offset 58: OriginalName (48 bytes, null-padded, or all zeros if null)
	 * - Offset 106: FileSize (8 bytes, int64)
	 * - Offset 114: Reserved (26 bytes, zero-filled)
	 */
	fun toBytes(): ByteArray {
		val buffer = ByteBuffer.allocate(SecmFileFormat.ENTRY_PLAINTEXT_SIZE)
		buffer.order(ByteOrder.LITTLE_ENDIAN)

		// Status (1 byte) - always active when we're writing an entry
		buffer.put(SecmFileFormat.STATUS_ACTIVE)

		// Filename (48 bytes, null-padded)
		val filenameBytes = filename.toByteArray(Charsets.UTF_8)
		val filenameTruncated = filenameBytes.copyOf(
			minOf(filenameBytes.size, SecmFileFormat.FILENAME_MAX_LENGTH)
		)
		buffer.put(filenameTruncated)
		// Pad remaining space with zeros
		repeat(SecmFileFormat.FILENAME_MAX_LENGTH - filenameTruncated.size) {
			buffer.put(0)
		}

		// Timestamp (8 bytes)
		buffer.putLong(originalTimestamp)

		// MediaType (1 byte)
		buffer.put(if (mediaType == MediaType.PHOTO) 0 else 1)

		// OriginalName (48 bytes, null-padded or all zeros)
		if (originalFilename != null) {
			val originalBytes = originalFilename.toByteArray(Charsets.UTF_8)
			val originalTruncated = originalBytes.copyOf(
				minOf(originalBytes.size, SecmFileFormat.ORIGINAL_NAME_MAX_LENGTH)
			)
			buffer.put(originalTruncated)
			repeat(SecmFileFormat.ORIGINAL_NAME_MAX_LENGTH - originalTruncated.size) {
				buffer.put(0)
			}
		} else {
			repeat(SecmFileFormat.ORIGINAL_NAME_MAX_LENGTH) {
				buffer.put(0)
			}
		}

		// FileSize (8 bytes)
		buffer.putLong(fileSize)

		// Reserved (26 bytes, zero-filled)
		repeat(SecmFileFormat.ENTRY_RESERVED_SIZE) {
			buffer.put(0)
		}

		return buffer.array()
	}

	companion object {
		/**
		 * Deserializes a 140-byte plaintext buffer to a MediaMetadataEntry.
		 *
		 * @param bytes The 140-byte plaintext buffer
		 * @return The deserialized entry, or null if status is not ACTIVE
		 */
		fun fromBytes(bytes: ByteArray): MediaMetadataEntry? {
			require(bytes.size == SecmFileFormat.ENTRY_PLAINTEXT_SIZE) {
				"Invalid entry size: ${bytes.size}, expected ${SecmFileFormat.ENTRY_PLAINTEXT_SIZE}"
			}

			val buffer = ByteBuffer.wrap(bytes)
			buffer.order(ByteOrder.LITTLE_ENDIAN)

			// Status (1 byte)
			val status = buffer.get()
			if (status != SecmFileFormat.STATUS_ACTIVE) {
				return null
			}

			// Filename (48 bytes, null-terminated)
			val filenameBytes = ByteArray(SecmFileFormat.FILENAME_MAX_LENGTH)
			buffer.get(filenameBytes)
			val filename = filenameBytes.decodeNullTerminatedString()

			// Timestamp (8 bytes)
			val timestamp = buffer.getLong()

			// MediaType (1 byte)
			val mediaTypeByte = buffer.get()
			val mediaType = if (mediaTypeByte == 0.toByte()) MediaType.PHOTO else MediaType.VIDEO

			// OriginalName (48 bytes, null-terminated)
			val originalNameBytes = ByteArray(SecmFileFormat.ORIGINAL_NAME_MAX_LENGTH)
			buffer.get(originalNameBytes)
			val originalFilename = originalNameBytes.decodeNullTerminatedString().takeIf { it.isNotEmpty() }

			// FileSize (8 bytes)
			val fileSize = buffer.getLong()

			// Skip reserved bytes (26 bytes)
			// buffer.position(buffer.position() + SecmFileFormat.ENTRY_RESERVED_SIZE)

			return MediaMetadataEntry(
				filename = filename,
				originalTimestamp = timestamp,
				mediaType = mediaType,
				originalFilename = originalFilename,
				fileSize = fileSize
			)
		}

		/**
		 * Creates a "deleted" entry for writing to disk.
		 * The entry will have STATUS_DELETED and all other fields zeroed.
		 */
		fun deletedEntryBytes(): ByteArray {
			val buffer = ByteBuffer.allocate(SecmFileFormat.ENTRY_PLAINTEXT_SIZE)
			buffer.order(ByteOrder.LITTLE_ENDIAN)
			buffer.put(SecmFileFormat.STATUS_DELETED)
			// Rest is already zero-filled
			return buffer.array()
		}

		/**
		 * Creates an "empty" entry for pre-allocated slots.
		 * The entry will have STATUS_EMPTY and all other fields zeroed.
		 */
		fun emptyEntryBytes(): ByteArray {
			val buffer = ByteBuffer.allocate(SecmFileFormat.ENTRY_PLAINTEXT_SIZE)
			buffer.order(ByteOrder.LITTLE_ENDIAN)
			buffer.put(SecmFileFormat.STATUS_EMPTY)
			// Rest is already zero-filled
			return buffer.array()
		}

		/**
		 * Extracts just the status byte from an entry's plaintext.
		 */
		fun getStatus(bytes: ByteArray): Byte {
			return bytes[SecmFileFormat.ENTRY_OFFSET_STATUS]
		}
	}
}

/**
 * Decodes a null-terminated UTF-8 string from a byte array.
 */
private fun ByteArray.decodeNullTerminatedString(): String {
	val nullIndex = indexOf(0)
	val length = if (nullIndex >= 0) nullIndex else size
	return String(this, 0, length, Charsets.UTF_8)
}
