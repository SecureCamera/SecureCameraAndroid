package com.darkrockstudios.app.securecamera.security.streaming

import com.darkrockstudios.app.securecamera.preferences.HashedPin
import com.darkrockstudios.app.securecamera.security.schemes.EncryptionScheme
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Tests for SECV format validation and corruption detection.
 */
class SecvFormatValidationTest {

    private lateinit var testDir: File
    private lateinit var encryptionScheme: TestEncryptionScheme

    @Before
    fun setup() {
        testDir = File(System.getProperty("java.io.tmpdir"), "secv-validation-test-${System.currentTimeMillis()}")
        testDir.mkdirs()
        encryptionScheme = TestEncryptionScheme()
    }

    @After
    fun cleanup() {
        testDir.deleteRecursively()
    }

    @Test
    fun `should reject file with invalid magic bytes`() = runTest {
        // Given - Create a valid encrypted file
        val plaintext = ByteArray(100) { it.toByte() }
        val encryptedFile = File(testDir, "test.secv")

        val encryptor = ChunkedStreamingEncryptor(encryptedFile, encryptionScheme, chunkSize = 1024)
        encryptor.write(plaintext, 0, plaintext.size)
        encryptor.close()

        // When - Corrupt the magic bytes
        RandomAccessFile(encryptedFile, "rw").use { raf ->
            raf.seek(0)
            raf.write("BAAD".toByteArray())
        }

        // Then - Should fail to open
        assertFailsWith<IllegalArgumentException> {
            ChunkedStreamingDecryptor(encryptedFile, encryptionScheme)
        }
    }

    @Test
    fun `should reject file with unsupported version`() = runTest {
        // Given - Create a valid encrypted file
        val plaintext = ByteArray(100) { it.toByte() }
        val encryptedFile = File(testDir, "test.secv")

        val encryptor = ChunkedStreamingEncryptor(encryptedFile, encryptionScheme, chunkSize = 1024)
        encryptor.write(plaintext, 0, plaintext.size)
        encryptor.close()

        // When - Change version to unsupported value
        RandomAccessFile(encryptedFile, "rw").use { raf ->
            raf.seek(4) // Skip magic
            raf.writeShort(99) // Invalid version
        }

        // Then - Should fail to open
        assertFailsWith<IllegalArgumentException> {
            ChunkedStreamingDecryptor(encryptedFile, encryptionScheme)
        }
    }

    @Test
    fun `should reject file that is too small`() = runTest {
        // Given - Create a file smaller than header size
        val tooSmall = File(testDir, "too-small.secv")
        tooSmall.writeBytes(ByteArray(32)) // Only 32 bytes, need 64

        // When/Then
        assertFailsWith<Exception> {
            ChunkedStreamingDecryptor(tooSmall, encryptionScheme)
        }
    }

    @Test
    fun `should reject non-existent file`() = runTest {
        // Given
        val nonExistent = File(testDir, "does-not-exist.secv")

        // When/Then
        assertFailsWith<IllegalArgumentException> {
            ChunkedStreamingDecryptor(nonExistent, encryptionScheme)
        }
    }

    @Test
    fun `should handle chunk boundary edge cases`() = runTest {
        // Given - Data that aligns exactly with chunk boundaries
        val chunkSize = 100
        val plaintext = ByteArray(chunkSize * 3) { it.toByte() }
        val encryptedFile = File(testDir, "test.secv")

        // When
        val encryptor = ChunkedStreamingEncryptor(encryptedFile, encryptionScheme, chunkSize)
        encryptor.write(plaintext, 0, plaintext.size)
        encryptor.close()

        val decryptor = ChunkedStreamingDecryptor(encryptedFile, encryptionScheme)

        // Read exactly at chunk boundaries
        val buffer1 = ByteArray(chunkSize)
        decryptor.read(0, buffer1, 0, chunkSize) // First chunk

        val buffer2 = ByteArray(chunkSize)
        decryptor.read(chunkSize.toLong(), buffer2, 0, chunkSize) // Second chunk

        val buffer3 = ByteArray(chunkSize)
        decryptor.read((chunkSize * 2).toLong(), buffer3, 0, chunkSize) // Third chunk

        decryptor.close()

        // Then
        assertEquals(plaintext.copyOfRange(0, chunkSize).toList(), buffer1.toList())
        assertEquals(plaintext.copyOfRange(chunkSize, chunkSize * 2).toList(), buffer2.toList())
        assertEquals(plaintext.copyOfRange(chunkSize * 2, chunkSize * 3).toList(), buffer3.toList())
    }

    @Test
    fun `should handle reading single byte at a time`() = runTest {
        // Given
        val plaintext = ByteArray(100) { it.toByte() }
        val encryptedFile = File(testDir, "test.secv")

        val encryptor = ChunkedStreamingEncryptor(encryptedFile, encryptionScheme, chunkSize = 30)
        encryptor.write(plaintext, 0, plaintext.size)
        encryptor.close()

        // When - Read byte by byte
        val decryptor = ChunkedStreamingDecryptor(encryptedFile, encryptionScheme)
        val decrypted = ByteArray(plaintext.size)
        for (i in plaintext.indices) {
            decryptor.read(i.toLong(), decrypted, i, 1)
        }
        decryptor.close()

        // Then
        assertEquals(plaintext.toList(), decrypted.toList())
    }

    @Test
    fun `should handle very small chunk size`() = runTest {
        // Given - Very small chunks (1 byte)
        val chunkSize = 1
        val plaintext = ByteArray(10) { it.toByte() }
        val encryptedFile = File(testDir, "test.secv")

        // When
        val encryptor = ChunkedStreamingEncryptor(encryptedFile, encryptionScheme, chunkSize)
        encryptor.write(plaintext, 0, plaintext.size)
        encryptor.close()

        val decryptor = ChunkedStreamingDecryptor(encryptedFile, encryptionScheme)
        val decrypted = ByteArray(plaintext.size)
        decryptor.read(0, decrypted, 0, plaintext.size)
        decryptor.close()

        // Then
        assertEquals(plaintext.toList(), decrypted.toList())

        // Verify file structure
        RandomAccessFile(encryptedFile, "r").use { raf ->
            val headerBytes = ByteArray(SecvFileFormat.HEADER_SIZE)
            raf.readFully(headerBytes)
            val header = SecvFileFormat.SecvHeader.fromByteArray(headerBytes)

            assertEquals(10L, header.totalChunks) // 10 chunks of 1 byte each
            assertEquals(1, header.chunkSize)
            assertEquals(1, header.finalChunkPlaintextSize)
        }
    }

    @Test
    fun `should handle very large chunk size`() = runTest {
        // Given - Chunk size larger than data
        val chunkSize = 10_000
        val plaintext = ByteArray(100) { it.toByte() }
        val encryptedFile = File(testDir, "test.secv")

        // When
        val encryptor = ChunkedStreamingEncryptor(encryptedFile, encryptionScheme, chunkSize)
        encryptor.write(plaintext, 0, plaintext.size)
        encryptor.close()

        val decryptor = ChunkedStreamingDecryptor(encryptedFile, encryptionScheme)
        val decrypted = ByteArray(plaintext.size)
        decryptor.read(0, decrypted, 0, plaintext.size)
        decryptor.close()

        // Then
        assertEquals(plaintext.toList(), decrypted.toList())

        // Verify file structure - should be single chunk
        RandomAccessFile(encryptedFile, "r").use { raf ->
            val headerBytes = ByteArray(SecvFileFormat.HEADER_SIZE)
            raf.readFully(headerBytes)
            val header = SecvFileFormat.SecvHeader.fromByteArray(headerBytes)

            assertEquals(1L, header.totalChunks)
            assertEquals(100, header.finalChunkPlaintextSize)
        }
    }

    @Test
    fun `should validate chunk offsets are calculated correctly`() = runTest {
        // Given
        val chunkSize = 1024
        val plaintext = ByteArray(5000) { it.toByte() } // 5 chunks
        val encryptedFile = File(testDir, "test.secv")

        // When
        val encryptor = ChunkedStreamingEncryptor(encryptedFile, encryptionScheme, chunkSize)
        encryptor.write(plaintext, 0, plaintext.size)
        encryptor.close()

        // Then - Manually verify file structure
        RandomAccessFile(encryptedFile, "r").use { raf ->
            // Read header
            val headerBytes = ByteArray(SecvFileFormat.HEADER_SIZE)
            raf.readFully(headerBytes)
            val header = SecvFileFormat.SecvHeader.fromByteArray(headerBytes)

            assertEquals(5L, header.totalChunks)

            // Verify each chunk is at the expected offset
            for (chunkIdx in 0 until header.totalChunks) {
                val expectedOffset = SecvFileFormat.calculateChunkOffset(chunkIdx, chunkSize)
                raf.seek(expectedOffset)

                // Read IV (should be 12 bytes)
                val iv = ByteArray(SecvFileFormat.IV_SIZE)
                val ivRead = raf.read(iv)
                assertEquals(SecvFileFormat.IV_SIZE, ivRead, "IV should be 12 bytes at chunk $chunkIdx")

                // Verify we can read the ciphertext
                val isFinalChunk = (chunkIdx == header.totalChunks - 1)
                val plaintextSize = if (isFinalChunk) header.finalChunkPlaintextSize else chunkSize
                val ciphertextSize = plaintextSize + SecvFileFormat.AUTH_TAG_SIZE

                val ciphertext = ByteArray(ciphertextSize)
                val cipherRead = raf.read(ciphertext)
                assertEquals(ciphertextSize, cipherRead, "Ciphertext size should match at chunk $chunkIdx")
            }
        }
    }

    @Test
    fun `file size should match expected format`() = runTest {
        // Given
        val chunkSize = 1000
        val plaintext = ByteArray(3500) { it.toByte() } // 3 full chunks + 500 byte final chunk
        val encryptedFile = File(testDir, "test.secv")

        // When
        val encryptor = ChunkedStreamingEncryptor(encryptedFile, encryptionScheme, chunkSize)
        encryptor.write(plaintext, 0, plaintext.size)
        encryptor.close()

        // Then - Calculate expected size
        val fullChunks = 3
        val finalChunkSize = 500
        val expectedSize = SecvFileFormat.HEADER_SIZE + // 64
                (fullChunks * (chunkSize + 28)) +           // 3 * 1028 = 3084
                (finalChunkSize + 28)                        // 528
        // Total: 64 + 3084 + 528 = 3676

        assertEquals(expectedSize.toLong(), encryptedFile.length(), "File size should match expected format")
    }

    @Test
    fun `decryptor should cache chunks efficiently`() = runTest {
        // Given
        val chunkSize = 100
        val plaintext = ByteArray(500) { it.toByte() }
        val encryptedFile = File(testDir, "test.secv")

        val encryptor = ChunkedStreamingEncryptor(encryptedFile, encryptionScheme, chunkSize)
        encryptor.write(plaintext, 0, plaintext.size)
        encryptor.close()

        // When - Read same chunk multiple times
        val decryptor = ChunkedStreamingDecryptor(encryptedFile, encryptionScheme)

        val buffer1 = ByteArray(10)
        decryptor.read(50, buffer1, 0, 10) // Chunk 0

        val buffer2 = ByteArray(10)
        decryptor.read(55, buffer2, 0, 10) // Same chunk 0 (should use cache)

        val buffer3 = ByteArray(10)
        decryptor.read(150, buffer3, 0, 10) // Chunk 1 (different chunk)

        decryptor.close()

        // Then - Verify correct data was read
        assertEquals(plaintext.copyOfRange(50, 60).toList(), buffer1.toList())
        assertEquals(plaintext.copyOfRange(55, 65).toList(), buffer2.toList())
        assertEquals(plaintext.copyOfRange(150, 160).toList(), buffer3.toList())
    }

    /**
     * Simple test encryption scheme that uses a fixed key for testing.
     */
    private class TestEncryptionScheme : EncryptionScheme {
        private val testKey = ByteArray(32) { it.toByte() }

        override suspend fun getDerivedKey(): ByteArray = testKey.copyOf()

        // Unused methods for this test
        override suspend fun encryptToFile(plain: ByteArray, targetFile: File) = error("Not used")
        override suspend fun encryptToFile(plain: ByteArray, keyBytes: ByteArray, targetFile: File) = error("Not used")
        override suspend fun encrypt(plain: ByteArray, keyBytes: ByteArray): ByteArray = error("Not used")
        override suspend fun encryptWithKeyAlias(plain: ByteArray, keyAlias: String): ByteArray = error("Not used")
        override suspend fun decryptWithKeyAlias(encrypted: ByteArray, keyAlias: String): ByteArray = error("Not used")
        override suspend fun decryptFile(encryptedFile: File): ByteArray = error("Not used")
        override suspend fun deriveAndCacheKey(plainPin: String, hashedPin: HashedPin) = error("Not used")
        override suspend fun deriveKey(plainPin: String, hashedPin: HashedPin): ByteArray = error("Not used")
        override fun evictKey() = Unit
        override suspend fun createKey(plainPin: String, hashedPin: HashedPin) = error("Not used")
        override suspend fun securityFailureReset() = error("Not used")
        override fun activatePoisonPill(oldPin: HashedPin?) = Unit
        override fun getStreamingCapability(): StreamingEncryptionScheme? = null
    }
}
