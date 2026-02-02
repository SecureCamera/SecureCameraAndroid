package com.darkrockstudios.app.securecamera.metadata

import org.junit.Test
import kotlin.test.assertEquals

class SecmFileFormatTest {

	@Test
	fun `entryOffset should return header size for slot 0`() {
		val offset = SecmFileFormat.entryOffset(0)
		assertEquals(SecmFileFormat.HEADER_SIZE.toLong(), offset)
	}

	@Test
	fun `entryOffset should return correct offset for slot 1`() {
		val offset = SecmFileFormat.entryOffset(1)
		val expected = SecmFileFormat.HEADER_SIZE + SecmFileFormat.ENTRY_BLOCK_SIZE
		assertEquals(expected.toLong(), offset)
	}

	@Test
	fun `entryOffset should return correct offset for arbitrary slot`() {
		val slot = 100
		val offset = SecmFileFormat.entryOffset(slot)
		val expected = SecmFileFormat.HEADER_SIZE + (slot * SecmFileFormat.ENTRY_BLOCK_SIZE)
		assertEquals(expected.toLong(), offset)
	}

	@Test
	fun `entry block size should accommodate IV plus ciphertext plus auth tag`() {
		val expected = SecmFileFormat.IV_SIZE + SecmFileFormat.ENTRY_PLAINTEXT_SIZE + SecmFileFormat.AUTH_TAG_SIZE
		assertEquals(expected, SecmFileFormat.ENTRY_BLOCK_SIZE)
	}

	@Test
	fun `header size should be 32 bytes`() {
		assertEquals(32, SecmFileFormat.HEADER_SIZE)
	}

	@Test
	fun `entry plaintext size should be 140 bytes`() {
		assertEquals(140, SecmFileFormat.ENTRY_PLAINTEXT_SIZE)
	}
}
