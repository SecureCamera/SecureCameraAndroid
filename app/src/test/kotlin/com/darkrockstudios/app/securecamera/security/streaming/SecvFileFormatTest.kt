package com.darkrockstudios.app.securecamera.security.streaming

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SecvFileFormatTest {

    @Test
    fun `calculateEncryptedChunkSize should add IV and auth tag`() {
        // Given
        val plaintextSize = 1024

        // When
        val encryptedSize = SecvFileFormat.calculateEncryptedChunkSize(plaintextSize)

        // Then
        val expected = plaintextSize + SecvFileFormat.IV_SIZE + SecvFileFormat.AUTH_TAG_SIZE
        assertEquals(expected, encryptedSize, "Encrypted size should be plaintext + 12 (IV) + 16 (tag)")
    }

    @Test
    fun `calculateFullEncryptedChunkSize should return chunk size plus 28`() {
        // Given
        val chunkSize = 1_048_576 // 1MB

        // When
        val fullEncryptedSize = SecvFileFormat.calculateFullEncryptedChunkSize(chunkSize)

        // Then
        assertEquals(
            chunkSize + 28,
            fullEncryptedSize,
            "Full encrypted chunk size should be chunk size + 28"
        )
    }

    @Test
    fun `calculateChunkOffset should return correct offset for first chunk`() {
        // Given
        val chunkIndex = 0L
        val chunkSize = 1_048_576

        // When
        val offset = SecvFileFormat.calculateChunkOffset(chunkIndex, chunkSize)

        // Then
        assertEquals(
            SecvFileFormat.HEADER_SIZE.toLong(),
            offset,
            "First chunk should start at offset 64 (after header)"
        )
    }

    @Test
    fun `calculateChunkOffset should return correct offset for second chunk`() {
        // Given
        val chunkIndex = 1L
        val chunkSize = 1_048_576

        // When
        val offset = SecvFileFormat.calculateChunkOffset(chunkIndex, chunkSize)

        // Then
        val expected = SecvFileFormat.HEADER_SIZE + (chunkSize + 28)
        assertEquals(
            expected.toLong(),
            offset,
            "Second chunk should start after header + first chunk"
        )
    }

    @Test
    fun `calculateChunkOffset should return correct offset for arbitrary chunk`() {
        // Given
        val chunkIndex = 100L
        val chunkSize = 1_048_576

        // When
        val offset = SecvFileFormat.calculateChunkOffset(chunkIndex, chunkSize)

        // Then
        val expected = SecvFileFormat.HEADER_SIZE + (chunkIndex * (chunkSize + 28))
        assertEquals(expected, offset, "Chunk 100 should have correct calculated offset")
    }

    @Test
    fun `calculatePlaintextOffset should return correct offset`() {
        // Given
        val chunkIndex = 5L
        val chunkSize = 1024

        // When
        val offset = SecvFileFormat.calculatePlaintextOffset(chunkIndex, chunkSize)

        // Then
        assertEquals(5120L, offset, "Plaintext offset should be chunk index * chunk size")
    }

    @Test
    fun `SecvHeader serialization should round-trip correctly`() {
        // Given
        val header = SecvFileFormat.SecvHeader(
            version = 1,
            chunkSize = 1_048_576,
            totalChunks = 100L,
            originalSize = 104_857_600L,
            finalChunkPlaintextSize = 1024
        )

        // When
        val bytes = header.toByteArray()
        val deserialized = SecvFileFormat.SecvHeader.fromByteArray(bytes)

        // Then
        assertEquals(SecvFileFormat.HEADER_SIZE, bytes.size, "Header should be 64 bytes")
        assertEquals(header.version, deserialized.version)
        assertEquals(header.chunkSize, deserialized.chunkSize)
        assertEquals(header.totalChunks, deserialized.totalChunks)
        assertEquals(header.originalSize, deserialized.originalSize)
        assertEquals(header.finalChunkPlaintextSize, deserialized.finalChunkPlaintextSize)
    }

    @Test
    fun `SecvHeader should include magic bytes`() {
        // Given
        val header = SecvFileFormat.SecvHeader(
            version = 1,
            chunkSize = 1024,
            totalChunks = 10L,
            originalSize = 10240L,
            finalChunkPlaintextSize = 240
        )

        // When
        val bytes = header.toByteArray()
        val magic = bytes.copyOfRange(0, 4)

        // Then
        val magicString = String(magic, Charsets.US_ASCII)
        assertEquals("SECV", magicString, "Header should start with SECV magic bytes")
    }

    @Test
    fun `SecvHeader fromByteArray should validate magic bytes`() {
        // Given
        val invalidBytes = ByteArray(SecvFileFormat.HEADER_SIZE)
        invalidBytes[0] = 'B'.code.toByte()
        invalidBytes[1] = 'A'.code.toByte()
        invalidBytes[2] = 'D'.code.toByte()
        invalidBytes[3] = '!'.code.toByte()

        // When/Then
        assertFailsWith<IllegalArgumentException> {
            SecvFileFormat.SecvHeader.fromByteArray(invalidBytes)
        }
    }

    @Test
    fun `SecvHeader fromByteArray should reject undersized buffer`() {
        // Given
        val tooSmall = ByteArray(32)

        // When/Then
        assertFailsWith<IllegalArgumentException> {
            SecvFileFormat.SecvHeader.fromByteArray(tooSmall)
        }
    }

    @Test
    fun `SecvHeader should handle maximum values`() {
        // Given
        val header = SecvFileFormat.SecvHeader(
            version = Short.MAX_VALUE,
            chunkSize = Int.MAX_VALUE,
            totalChunks = Long.MAX_VALUE,
            originalSize = Long.MAX_VALUE,
            finalChunkPlaintextSize = Int.MAX_VALUE
        )

        // When
        val bytes = header.toByteArray()
        val deserialized = SecvFileFormat.SecvHeader.fromByteArray(bytes)

        // Then
        assertEquals(header.version, deserialized.version)
        assertEquals(header.chunkSize, deserialized.chunkSize)
        assertEquals(header.totalChunks, deserialized.totalChunks)
        assertEquals(header.originalSize, deserialized.originalSize)
        assertEquals(header.finalChunkPlaintextSize, deserialized.finalChunkPlaintextSize)
    }

    @Test
    fun `SecvHeader should handle zero values`() {
        // Given
        val header = SecvFileFormat.SecvHeader(
            version = 0,
            chunkSize = 0,
            totalChunks = 0L,
            originalSize = 0L,
            finalChunkPlaintextSize = 0
        )

        // When
        val bytes = header.toByteArray()
        val deserialized = SecvFileFormat.SecvHeader.fromByteArray(bytes)

        // Then
        assertEquals(header.version, deserialized.version)
        assertEquals(header.chunkSize, deserialized.chunkSize)
        assertEquals(header.totalChunks, deserialized.totalChunks)
        assertEquals(header.originalSize, deserialized.originalSize)
        assertEquals(header.finalChunkPlaintextSize, deserialized.finalChunkPlaintextSize)
    }
}
