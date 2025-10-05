package com.darkrockstudios.app.securecamera.security

import dev.whyoleg.cryptography.random.CryptographyRandom
import java.nio.ByteBuffer

class ShardedKey(
	key: ByteArray
) {
	private val part1: ByteBuffer
	private val part2: ByteBuffer
	private val keySize: Int = key.size

	init {
		// Randomly size the storage buffer so it is not the exact length of an AES key
		val part1Size = keySize + CryptographyRandom.nextInt(3, 155)
		part1 = ByteBuffer.allocateDirect(part1Size)
		val part1Array = ByteArray(part1Size)
		CryptographyRandom.nextBytes(part1Array)
		part1.put(part1Array)
		part1.flip()

		// Randomly size the storage buffer so it is not the exact length of an AES key
		val part2Size = keySize + CryptographyRandom.nextInt(5, 111)
		part2 = ByteBuffer.allocateDirect(part2Size)
		val part2Array = ByteArray(part2Size)
		CryptographyRandom.nextBytes(part2Array)
		part2.put(part2Array)
		part2.flip()

		// Now fill the data part with our XOR'd key
		for (i in key.indices) {
			val xorByte = (key[i].toInt() xor part1.get(i).toInt()).toByte()
			part2.put(i, xorByte)
		}
	}

	/**
	 * Reconstructs the original key from its XOR-split parts.
	 *
	 * @return The reconstructed original key
	 */
	fun reconstructKey(): ByteArray {
		val originalKey = ByteArray(keySize)
		for (i in originalKey.indices) {
			originalKey[i] = (part1.get(i).toInt() xor part2.get(i).toInt()).toByte()
		}

		return originalKey
	}

	fun evict() {
		part1.zeroOut()
		part2.zeroOut()
	}
}

private fun ByteBuffer.zeroOut() {
	if (hasArray()) {
		val a = array()
		val start = arrayOffset() + position()
		val end = arrayOffset() + limit()
		java.util.Arrays.fill(a, start, end, 0.toByte())
	} else {
		val pos = position()
		val lim = limit()
		for (i in 0 until capacity()) {
			put(i, 0)
		}
		position(pos)
		limit(lim)
	}
}
