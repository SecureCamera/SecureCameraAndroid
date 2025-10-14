package com.darkrockstudios.app.securecamera.preferences

import com.darkrockstudios.app.securecamera.security.XorCipher
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class XorCipherTest {

	@Test
	fun `encrypt produces non-plaintext output`() {
		// Given
		val plaintext = "Hello, World!"
		val key = "SecretKey"

		// When
		val encrypted = XorCipher.encrypt(plaintext, key)

		// Then
		assertNotEquals(plaintext, encrypted, "Encrypted text should not match plaintext")
	}

	@Test
	fun `decrypt returns original plaintext`() {
		// Given
		val plaintext = "Hello, World!"
		val key = "SecretKey"
		val encrypted = XorCipher.encrypt(plaintext, key)

		// When
		val decrypted = XorCipher.decrypt(encrypted, key)

		// Then
		assertEquals(plaintext, decrypted, "Decrypted text should match original plaintext")
	}

	@Test
	fun `different keys produce different encrypted outputs`() {
		// Given
		val plaintext = "Hello, World!"
		val key1 = "SecretKey1"
		val key2 = "SecretKey2"

		// When
		val encrypted1 = XorCipher.encrypt(plaintext, key1)
		val encrypted2 = XorCipher.encrypt(plaintext, key2)

		// Then
		assertNotEquals(encrypted1, encrypted2, "Different keys should produce different encrypted outputs")
	}

	@Test
	fun `decrypt with wrong key produces incorrect result`() {
		// Given
		val plaintext = "Hello, World!"
		val correctKey = "CorrectKey"
		val wrongKey = "WrongKey"
		val encrypted = XorCipher.encrypt(plaintext, correctKey)

		// When
		val decrypted = XorCipher.decrypt(encrypted, wrongKey)

		// Then
		assertNotEquals(plaintext, decrypted, "Decryption with wrong key should not match original plaintext")
	}

	@Test
	fun `encrypt and decrypt empty string`() {
		// Given
		val plaintext = ""
		val key = "SecretKey"

		// When
		val encrypted = XorCipher.encrypt(plaintext, key)
		val decrypted = XorCipher.decrypt(encrypted, key)

		// Then
		assertEquals(plaintext, decrypted, "Decrypted empty string should match original empty string")
	}

	@Test
	fun `encrypt and decrypt with special characters`() {
		// Given
		val plaintext = "!@#$%^&*()_+{}|:<>?~`-=[]\\;',./\""
		val key = "Key!@#"

		// When
		val encrypted = XorCipher.encrypt(plaintext, key)
		val decrypted = XorCipher.decrypt(encrypted, key)

		// Then
		assertEquals(plaintext, decrypted, "Decrypted text with special characters should match original")
	}

	@Test
	fun `encrypt throws exception with empty key`() {
		// Given
		val plaintext = "Hello, World!"
		val key = ""

		// When/Then
		val exception = assertFailsWith<IllegalStateException> {
			XorCipher.encrypt(plaintext, key)
		}
		assertEquals("Key must not be empty!", exception.message)
	}

	@Test
	fun `decrypt throws exception with empty key`() {
		// Given
		val ciphertext = "SomeCiphertextValue"
		val key = ""

		// When/Then
		val exception = assertFailsWith<IllegalStateException> {
			XorCipher.decrypt(ciphertext, key)
		}
		assertEquals("Key must not be empty!", exception.message)
	}

	@Test
	fun `encrypt throws exception with blank key`() {
		// Given
		val plaintext = "Hello, World!"
		val key = "   "  // Whitespace-only key

		// When/Then
		val exception = assertFailsWith<IllegalStateException> {
			XorCipher.encrypt(plaintext, key)
		}
		assertEquals("Key must not be empty!", exception.message)
	}

	@Test
	fun `decrypt throws exception with blank key`() {
		// Given
		val ciphertext = "SomeCiphertextValue"
		val key = "   "  // Whitespace-only key

		// When/Then
		val exception = assertFailsWith<IllegalStateException> {
			XorCipher.decrypt(ciphertext, key)
		}
		assertEquals("Key must not be empty!", exception.message)
	}
}
