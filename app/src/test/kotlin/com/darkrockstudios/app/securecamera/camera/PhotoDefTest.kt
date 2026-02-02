package com.darkrockstudios.app.securecamera.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.*

class PhotoDefTest {

	@Test
	fun `dateTaken returns date from metadataTimestamp when provided`() {
		// Create a known timestamp
		val expectedTimestamp = 1673782245500L // 2023-01-15 10:30:45.500 UTC

		// Create a PhotoDef with UUID-based filename and metadata timestamp
		val photoDef = PhotoDef(
			photoName = "img_550e8400e29b41d4a716446655440000.jpg",
			photoFormat = "jpg",
			photoFile = File("dummy/path"),
			metadataTimestamp = expectedTimestamp
		)

		// Get the date
		val result = photoDef.dateTaken()

		// Verify the timestamp matches
		assertEquals(expectedTimestamp, result.time)
	}

	@Test
	fun `dateTaken returns current date when metadataTimestamp is null`() {
		// Create a PhotoDef without metadata timestamp
		val photoDef = PhotoDef(
			photoName = "img_550e8400e29b41d4a716446655440000.jpg",
			photoFormat = "jpg",
			photoFile = File("dummy/path"),
			metadataTimestamp = null
		)

		// Get the current time before calling dateTaken
		val beforeTime = Date()

		// Call dateTaken which should return current date
		val result = photoDef.dateTaken()

		// Get the current time after calling dateTaken
		val afterTime = Date()

		// Verify the returned date is between beforeTime and afterTime
		assertTrue(result.time >= beforeTime.time - 1000 && result.time <= afterTime.time + 1000)
	}
}
