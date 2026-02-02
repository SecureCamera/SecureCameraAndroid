package com.darkrockstudios.app.securecamera.metadata

import com.darkrockstudios.app.securecamera.camera.MediaType
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class MediaMetadataEntryTest {

	@Test
	fun `toBytes and fromBytes should round-trip photo entry`() {
		val entry = MediaMetadataEntry(
			filename = "img_abc123.jpg",
			originalTimestamp = 1234567890123L,
			mediaType = MediaType.PHOTO,
			originalFilename = "IMG_20240101_120000.jpg",
			fileSize = 1024000L
		)

		val bytes = entry.toBytes()
		val restored = MediaMetadataEntry.fromBytes(bytes)

		assertNotNull(restored)
		assertEquals(entry.filename, restored.filename)
		assertEquals(entry.originalTimestamp, restored.originalTimestamp)
		assertEquals(entry.mediaType, restored.mediaType)
		assertEquals(entry.originalFilename, restored.originalFilename)
		assertEquals(entry.fileSize, restored.fileSize)
	}

	@Test
	fun `toBytes and fromBytes should round-trip video entry`() {
		val entry = MediaMetadataEntry(
			filename = "vid_def456.secv",
			originalTimestamp = 9876543210987L,
			mediaType = MediaType.VIDEO,
			originalFilename = "VID_20240615_183000.mp4",
			fileSize = 50000000L
		)

		val bytes = entry.toBytes()
		val restored = MediaMetadataEntry.fromBytes(bytes)

		assertNotNull(restored)
		assertEquals(entry.filename, restored.filename)
		assertEquals(entry.originalTimestamp, restored.originalTimestamp)
		assertEquals(entry.mediaType, restored.mediaType)
		assertEquals(entry.originalFilename, restored.originalFilename)
		assertEquals(entry.fileSize, restored.fileSize)
	}

	@Test
	fun `toBytes and fromBytes should handle null originalFilename`() {
		val entry = MediaMetadataEntry(
			filename = "img_xyz789.jpg",
			originalTimestamp = 1111111111111L,
			mediaType = MediaType.PHOTO,
			originalFilename = null,
			fileSize = 500000L
		)

		val bytes = entry.toBytes()
		val restored = MediaMetadataEntry.fromBytes(bytes)

		assertNotNull(restored)
		assertNull(restored.originalFilename)
	}

	@Test
	fun `toBytes should produce correct size`() {
		val entry = MediaMetadataEntry(
			filename = "test.jpg",
			originalTimestamp = 0L,
			mediaType = MediaType.PHOTO
		)

		val bytes = entry.toBytes()
		assertEquals(SecmFileFormat.ENTRY_PLAINTEXT_SIZE, bytes.size)
	}

	@Test
	fun `toBytes should set status to active`() {
		val entry = MediaMetadataEntry(
			filename = "test.jpg",
			originalTimestamp = 0L,
			mediaType = MediaType.PHOTO
		)

		val bytes = entry.toBytes()
		assertEquals(SecmFileFormat.STATUS_ACTIVE, MediaMetadataEntry.getStatus(bytes))
	}

	@Test
	fun `deletedEntryBytes should produce correct size`() {
		val bytes = MediaMetadataEntry.deletedEntryBytes()
		assertEquals(SecmFileFormat.ENTRY_PLAINTEXT_SIZE, bytes.size)
	}

	@Test
	fun `deletedEntryBytes should have deleted status`() {
		val bytes = MediaMetadataEntry.deletedEntryBytes()
		assertEquals(SecmFileFormat.STATUS_DELETED, MediaMetadataEntry.getStatus(bytes))
	}

	@Test
	fun `emptyEntryBytes should produce correct size`() {
		val bytes = MediaMetadataEntry.emptyEntryBytes()
		assertEquals(SecmFileFormat.ENTRY_PLAINTEXT_SIZE, bytes.size)
	}

	@Test
	fun `emptyEntryBytes should have empty status`() {
		val bytes = MediaMetadataEntry.emptyEntryBytes()
		assertEquals(SecmFileFormat.STATUS_EMPTY, MediaMetadataEntry.getStatus(bytes))
	}

	@Test
	fun `fromBytes should return null for deleted entry`() {
		val bytes = MediaMetadataEntry.deletedEntryBytes()
		val result = MediaMetadataEntry.fromBytes(bytes)
		assertNull(result)
	}

	@Test
	fun `fromBytes should return null for empty entry`() {
		val bytes = MediaMetadataEntry.emptyEntryBytes()
		val result = MediaMetadataEntry.fromBytes(bytes)
		assertNull(result)
	}

	@Test
	fun `toBytes should truncate long filename`() {
		val longFilename = "a".repeat(100)
		val entry = MediaMetadataEntry(
			filename = longFilename,
			originalTimestamp = 0L,
			mediaType = MediaType.PHOTO
		)

		val bytes = entry.toBytes()
		val restored = MediaMetadataEntry.fromBytes(bytes)

		assertNotNull(restored)
		assertEquals(SecmFileFormat.FILENAME_MAX_LENGTH, restored.filename.length)
	}

	@Test
	fun `toBytes should handle maximum file size`() {
		val entry = MediaMetadataEntry(
			filename = "large.secv",
			originalTimestamp = 0L,
			mediaType = MediaType.VIDEO,
			fileSize = Long.MAX_VALUE
		)

		val bytes = entry.toBytes()
		val restored = MediaMetadataEntry.fromBytes(bytes)

		assertNotNull(restored)
		assertEquals(Long.MAX_VALUE, restored.fileSize)
	}
}
