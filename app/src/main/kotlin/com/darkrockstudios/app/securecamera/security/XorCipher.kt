package com.darkrockstudios.app.securecamera.security

import java.nio.charset.StandardCharsets
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

object XorCipher {
	@OptIn(ExperimentalEncodingApi::class)
	fun encrypt(plaintext: String, key: String): String {
		if (key.isBlank()) error("Key must not be empty!")
		val obfuscated = xor(plaintext.toByteArray(StandardCharsets.UTF_8), key)
		return String(Base64.Default.encodeToByteArray(obfuscated))
	}

	@OptIn(ExperimentalEncodingApi::class)
	fun decrypt(ciphertextB64: String, key: String): String {
		if (key.isBlank()) error("Key must not be empty!")
		val decoded: ByteArray = Base64.Default.decode(ciphertextB64)
		return String(xor(decoded, key), StandardCharsets.UTF_8)
	}

	private fun xor(input: ByteArray, key: String): ByteArray {
		val key = key.toByteArray(StandardCharsets.UTF_8)
		val out = ByteArray(input.size)

		for (i in input.indices) {
			out[i] = (input[i].toInt() xor key[i % key.size].toInt()).toByte()
		}
		return out
	}
}