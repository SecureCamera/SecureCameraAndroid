package com.darkrockstudios.app.securecamera.metadata

/**
 * Binary file format constants and utilities for the SECM (Secure Camera Metadata) format.
 *
 * File Structure:
 * [Header - 32 bytes, unencrypted]
 * [Entry 0 - 168 bytes, independently encrypted]
 * [Entry 1 - 168 bytes, independently encrypted]
 * ...
 * [Entry N - 168 bytes, independently encrypted]
 *
 * Each entry is independently encrypted with its own IV, allowing in-place updates.
 */
object SecmFileFormat {
	/** Magic bytes identifying SECM format: "SECM" (0x5345434D) */
	const val MAGIC = "SECM"

	/** Current format version */
	const val VERSION: Short = 1

	/** Size of the unencrypted header in bytes */
	const val HEADER_SIZE = 32

	/** Size of plaintext entry data before encryption */
	const val ENTRY_PLAINTEXT_SIZE = 140

	/** Size of each encrypted entry block: 12 (IV) + 140 (ciphertext) + 16 (auth tag) */
	const val ENTRY_BLOCK_SIZE = 168

	/** IV size for AES-GCM encryption */
	const val IV_SIZE = 12

	/** Authentication tag size for AES-GCM */
	const val AUTH_TAG_SIZE = 16

	// Entry status values
	/** Slot is empty and available for reuse */
	const val STATUS_EMPTY: Byte = 0

	/** Slot contains an active entry */
	const val STATUS_ACTIVE: Byte = 1

	/** Slot was deleted and can be reused */
	const val STATUS_DELETED: Byte = 2

	// Header field offsets
	const val HEADER_OFFSET_MAGIC = 0
	const val HEADER_OFFSET_VERSION = 4
	const val HEADER_OFFSET_PHOTO_COUNT = 6
	const val HEADER_OFFSET_VIDEO_COUNT = 10
	const val HEADER_OFFSET_CAPACITY = 14
	const val HEADER_OFFSET_ENTRY_SIZE = 18
	const val HEADER_OFFSET_RESERVED = 20

	// Entry field offsets (within plaintext)
	const val ENTRY_OFFSET_STATUS = 0
	const val ENTRY_OFFSET_FILENAME = 1
	const val ENTRY_OFFSET_TIMESTAMP = 49
	const val ENTRY_OFFSET_MEDIA_TYPE = 57
	const val ENTRY_OFFSET_ORIGINAL_NAME = 58
	const val ENTRY_OFFSET_FILE_SIZE = 106
	const val ENTRY_OFFSET_RESERVED = 114

	// Field sizes
	const val FILENAME_MAX_LENGTH = 48
	const val ORIGINAL_NAME_MAX_LENGTH = 48
	const val ENTRY_RESERVED_SIZE = 26
	const val HEADER_RESERVED_SIZE = 12

	/** Directory name for metadata storage */
	const val METADATA_DIR = ".metadata"

	/** Filename for main media index */
	const val MEDIA_INDEX_FILENAME = "media_index.secm"

	/** Marker file for migration in progress */
	const val MIGRATION_MARKER_FILENAME = ".migration_in_progress"

	/**
	 * Calculates the byte offset for a given entry slot.
	 * @param slot The zero-based slot index
	 * @return The byte offset from the beginning of the file
	 */
	fun entryOffset(slot: Int): Long = HEADER_SIZE + (slot.toLong() * ENTRY_BLOCK_SIZE)
}
