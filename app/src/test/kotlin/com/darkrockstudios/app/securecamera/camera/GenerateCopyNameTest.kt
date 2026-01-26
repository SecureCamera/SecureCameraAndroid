package com.darkrockstudios.app.securecamera.camera

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class GenerateCopyNameTest {
	@Test
	fun returnsCpWhenNoneExist() {
		val dir = createTempDirectory(prefix = "genCopyNone").toFile().apply { deleteOnExit() }
		val result = SecureImageRepository.generateCopyName(dir, "photo_123.jpg")
		assertEquals("photo_123_cp.jpg", result)
	}

	@Test
	fun returnsCp1WhenCpExists() {
		val dir = createTempDirectory(prefix = "genCopyOne").toFile().apply { deleteOnExit() }
		File(dir, "photo_123_cp.jpg").createNewFile()
		val result = SecureImageRepository.generateCopyName(dir, "photo_123.jpg")
		assertEquals("photo_123_cp1.jpg", result)
	}

	@Test
	fun returnsNextIndexWhenManyExist() {
		val dir = createTempDirectory(prefix = "genCopyMany").toFile().apply { deleteOnExit() }
		File(dir, "photo_123_cp.jpg").createNewFile()
		File(dir, "photo_123_cp1.jpg").createNewFile()
		File(dir, "photo_123_cp2.jpg").createNewFile()
		val result = SecureImageRepository.generateCopyName(dir, "photo_123.jpg")
		assertEquals("photo_123_cp3.jpg", result)
	}

	@Test
	fun preservesBaseWithDots() {
		val dir = createTempDirectory(prefix = "genCopyDots").toFile().apply { deleteOnExit() }
		val result = SecureImageRepository.generateCopyName(dir, "my.photo.001.jpg")
		assertEquals("my.photo.001_cp.jpg", result)
	}
}
