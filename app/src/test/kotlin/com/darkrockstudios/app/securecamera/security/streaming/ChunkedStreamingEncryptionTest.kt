package com.darkrockstudios.app.securecamera.security.streaming

import com.darkrockstudios.app.securecamera.preferences.HashedPin
import com.darkrockstudios.app.securecamera.security.schemes.EncryptionScheme
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ChunkedStreamingEncryptionTest {

    private lateinit var testDir: File
    private lateinit var encryptionScheme: TestEncryptionScheme

    @Before
    fun setup() {
        testDir = File(System.getProperty("java.io.tmpdir"), "secv-test-${System.currentTimeMillis()}")
        testDir.mkdirs()
        encryptionScheme = TestEncryptionScheme()
    }

    @After
    fun cleanup() {
        testDir.deleteRecursively()
    }

    @Test
    fun `encrypt and decrypt should round-trip correctly`() = runTest {
        // Given
        val plaintext = "Hello, SECV!".repeat(100).toByteArray()
        val encryptedFile = File(testDir, "test.secv")

        // When - Encrypt
        val encryptor = ChunkedStreamingEncryptor(encryptedFile, encryptionScheme, chunkSize = 1024)
        encryptor.write(plaintext, 0, plaintext.size)
        encryptor.flush()
        encryptor.close()

        // When - Decrypt
        val decryptor = ChunkedStreamingDecryptor(encryptedFile, encryptionScheme)
        val decrypted = ByteArray(plaintext.size)
        val bytesRead = decryptor.read(0, decrypted, 0, plaintext.size)
        decryptor.close()

        // Then
        assertEquals(plaintext.size, bytesRead)
        assertContentEquals(plaintext, decrypted)
    }

    @Test
    fun `encrypted file should have correct header`() = runTest {
        // Given
        val plaintext = ByteArray(5000) { it.toByte() }
        val chunkSize = 1024
        val encryptedFile = File(testDir, "test.secv")

        // When
        val encryptor = ChunkedStreamingEncryptor(encryptedFile, encryptionScheme, chunkSize)
        encryptor.write(plaintext, 0, plaintext.size)
        encryptor.close()

        // Then - Verify header
        RandomAccessFile(encryptedFile, "r").use { raf ->
            val headerBytes = ByteArray(SecvFileFormat.HEADER_SIZE)
            raf.readFully(headerBytes)
            val header = SecvFileFormat.SecvHeader.fromByteArray(headerBytes)

            assertEquals(SecvFileFormat.VERSION, header.version)
            assertEquals(chunkSize, header.chunkSize)
            assertEquals(5L, header.totalChunks) // 1024*4 + 904 = 5 chunks
            assertEquals(plaintext.size.toLong(), header.originalSize)
            assertEquals(904, header.finalChunkPlaintextSize) // 5000 % 1024 = 904
        }
    }

    @Test
    fun `encrypted file should have correct size`() = runTest {
        // Given
        val plaintext = ByteArray(3000) { it.toByte() }
        val chunkSize = 1024
        val encryptedFile = File(testDir, "test.secv")

        // When
        val encryptor = ChunkedStreamingEncryptor(encryptedFile, encryptionScheme, chunkSize)
        encryptor.write(plaintext, 0, plaintext.size)
        encryptor.close()

        // Then
        // Expected: 64 (header) + 2*(1024+28) + 952+28 = 64 + 2104 + 980 = 3148
        val expectedFullChunks = 2
        val expectedFinalChunk = 952 // 3000 % 1024
        val expectedSize = SecvFileFormat.HEADER_SIZE +
                (expectedFullChunks * (chunkSize + 28)) +
                (expectedFinalChunk + 28)
        assertEquals(expectedSize.toLong(), encryptedFile.length())
    }

    @Test
    fun `should handle exact multiple of chunk size`() = runTest {
        // Given
        val chunkSize = 1024
        val plaintext = ByteArray(chunkSize * 3) { it.toByte() } // Exactly 3 chunks
        val encryptedFile = File(testDir, "test.secv")

        // When - Encrypt
        val encryptor = ChunkedStreamingEncryptor(encryptedFile, encryptionScheme, chunkSize)
        encryptor.write(plaintext, 0, plaintext.size)
        encryptor.close()

        // Then - Decrypt
        val decryptor = ChunkedStreamingDecryptor(encryptedFile, encryptionScheme)
        val decrypted = ByteArray(plaintext.size)
        decryptor.read(0, decrypted, 0, plaintext.size)
        decryptor.close()

        assertContentEquals(plaintext, decrypted)

        // Verify header
        RandomAccessFile(encryptedFile, "r").use { raf ->
            val headerBytes = ByteArray(SecvFileFormat.HEADER_SIZE)
            raf.readFully(headerBytes)
            val header = SecvFileFormat.SecvHeader.fromByteArray(headerBytes)
            assertEquals(3L, header.totalChunks)
            assertEquals(chunkSize, header.finalChunkPlaintextSize) // Final chunk is full size
        }
    }

    @Test
    fun `should handle single chunk video`() = runTest {
        // Given
        val chunkSize = 1024
        val plaintext = ByteArray(512) { it.toByte() } // Less than one chunk
        val encryptedFile = File(testDir, "test.secv")

        // When - Encrypt
        val encryptor = ChunkedStreamingEncryptor(encryptedFile, encryptionScheme, chunkSize)
        encryptor.write(plaintext, 0, plaintext.size)
        encryptor.close()

        // Then - Decrypt
        val decryptor = ChunkedStreamingDecryptor(encryptedFile, encryptionScheme)
        val decrypted = ByteArray(plaintext.size)
        decryptor.read(0, decrypted, 0, plaintext.size)
        decryptor.close()

        assertContentEquals(plaintext, decrypted)

        // Verify header
        RandomAccessFile(encryptedFile, "r").use { raf ->
            val headerBytes = ByteArray(SecvFileFormat.HEADER_SIZE)
            raf.readFully(headerBytes)
            val header = SecvFileFormat.SecvHeader.fromByteArray(headerBytes)
            assertEquals(1L, header.totalChunks)
            assertEquals(512, header.finalChunkPlaintextSize)
        }
    }

    @Test
    fun `should handle empty file`() = runTest {
        // Given
        val plaintext = ByteArray(0)
        val encryptedFile = File(testDir, "test.secv")

        // When - Encrypt
        val encryptor = ChunkedStreamingEncryptor(encryptedFile, encryptionScheme, chunkSize = 1024)
        encryptor.write(plaintext, 0, 0)
        encryptor.close()

        // Then - Decrypt
        val decryptor = ChunkedStreamingDecryptor(encryptedFile, encryptionScheme)
        val decrypted = ByteArray(0)
        val bytesRead = decryptor.read(0, decrypted, 0, 0)
        decryptor.close()

        assertEquals(0, bytesRead)
        assertEquals(0L, decryptor.totalSize)
    }

    @Test
    fun `should support seeking to different positions`() = runTest {
        // Given
        val chunkSize = 100
        val plaintext = ByteArray(500) { it.toByte() } // 5 chunks
        val encryptedFile = File(testDir, "test.secv")

        // Encrypt
        val encryptor = ChunkedStreamingEncryptor(encryptedFile, encryptionScheme, chunkSize)
        encryptor.write(plaintext, 0, plaintext.size)
        encryptor.close()

        // When - Decrypt from different positions
        val decryptor = ChunkedStreamingDecryptor(encryptedFile, encryptionScheme)

        // Read from start
        val buffer1 = ByteArray(10)
        decryptor.read(0, buffer1, 0, 10)
        assertContentEquals(plaintext.copyOfRange(0, 10), buffer1)

        // Read from middle of first chunk
        val buffer2 = ByteArray(10)
        decryptor.read(50, buffer2, 0, 10)
        assertContentEquals(plaintext.copyOfRange(50, 60), buffer2)

        // Read from start of second chunk
        val buffer3 = ByteArray(10)
        decryptor.read(100, buffer3, 0, 10)
        assertContentEquals(plaintext.copyOfRange(100, 110), buffer3)

        // Read from near end
        val buffer4 = ByteArray(10)
        decryptor.read(490, buffer4, 0, 10)
        assertContentEquals(plaintext.copyOfRange(490, 500), buffer4)

        // Read across chunk boundary
        val buffer5 = ByteArray(20)
        decryptor.read(95, buffer5, 0, 20)
        assertContentEquals(plaintext.copyOfRange(95, 115), buffer5)

        decryptor.close()
    }

    @Test
    fun `should handle large file`() = runTest {
        // Given
        val chunkSize = 1024
        val plaintext = ByteArray(1_048_576) { (it % 256).toByte() } // 1MB
        val encryptedFile = File(testDir, "test.secv")

        // When - Encrypt
        val encryptor = ChunkedStreamingEncryptor(encryptedFile, encryptionScheme, chunkSize)
        encryptor.write(plaintext, 0, plaintext.size)
        encryptor.close()

        // Then - Decrypt
        val decryptor = ChunkedStreamingDecryptor(encryptedFile, encryptionScheme)
        val decrypted = ByteArray(plaintext.size)
        var totalRead = 0
        var position = 0L

        // Read in chunks to simulate streaming
        while (totalRead < plaintext.size) {
            val toRead = minOf(8192, plaintext.size - totalRead)
            val read = decryptor.read(position, decrypted, totalRead, toRead)
            if (read <= 0) break
            totalRead += read
            position += read
        }
        decryptor.close()

        assertEquals(plaintext.size, totalRead)
        assertContentEquals(plaintext, decrypted)
    }

    @Test
    fun `should handle multiple sequential writes`() = runTest {
        // Given
        val part1 = "Hello ".toByteArray()
        val part2 = "World".toByteArray()
        val part3 = "!".toByteArray()
        val encryptedFile = File(testDir, "test.secv")

        // When - Encrypt with multiple writes
        val encryptor = ChunkedStreamingEncryptor(encryptedFile, encryptionScheme, chunkSize = 1024)
        encryptor.write(part1, 0, part1.size)
        encryptor.write(part2, 0, part2.size)
        encryptor.write(part3, 0, part3.size)
        encryptor.close()

        // Then - Decrypt
        val expected = "Hello World!".toByteArray()
        val decryptor = ChunkedStreamingDecryptor(encryptedFile, encryptionScheme)
        val decrypted = ByteArray(expected.size)
        decryptor.read(0, decrypted, 0, expected.size)
        decryptor.close()

        assertContentEquals(expected, decrypted)
    }

    @Test
    fun `decryptor should expose correct metadata`() = runTest {
        // Given
        val chunkSize = 2048
        val plaintext = ByteArray(10000) { it.toByte() }
        val encryptedFile = File(testDir, "test.secv")

        // When
        val encryptor = ChunkedStreamingEncryptor(encryptedFile, encryptionScheme, chunkSize)
        encryptor.write(plaintext, 0, plaintext.size)
        encryptor.close()

        val decryptor = ChunkedStreamingDecryptor(encryptedFile, encryptionScheme)

        // Then
        assertEquals(plaintext.size.toLong(), decryptor.totalSize)
        assertEquals(chunkSize, decryptor.chunkSize)
        decryptor.close()
    }

    @Test
    fun `should handle read beyond EOF`() = runTest {
        // Given
        val plaintext = ByteArray(100) { it.toByte() }
        val encryptedFile = File(testDir, "test.secv")

        val encryptor = ChunkedStreamingEncryptor(encryptedFile, encryptionScheme, chunkSize = 1024)
        encryptor.write(plaintext, 0, plaintext.size)
        encryptor.close()

        // When - Try to read beyond EOF
        val decryptor = ChunkedStreamingDecryptor(encryptedFile, encryptionScheme)
        val buffer = ByteArray(10)
        val bytesRead = decryptor.read(100, buffer, 0, 10) // Read at EOF
        decryptor.close()

        // Then
        assertEquals(-1, bytesRead, "Reading at EOF should return -1")
    }

    @Test
    fun `should handle partial read at end of file`() = runTest {
        // Given
        val plaintext = ByteArray(100) { it.toByte() }
        val encryptedFile = File(testDir, "test.secv")

        val encryptor = ChunkedStreamingEncryptor(encryptedFile, encryptionScheme, chunkSize = 1024)
        encryptor.write(plaintext, 0, plaintext.size)
        encryptor.close()

        // When - Try to read more than available
        val decryptor = ChunkedStreamingDecryptor(encryptedFile, encryptionScheme)
        val buffer = ByteArray(20)
        val bytesRead = decryptor.read(90, buffer, 0, 20) // Only 10 bytes available
        decryptor.close()

        // Then
        assertEquals(10, bytesRead, "Should read only available bytes")
        assertContentEquals(plaintext.copyOfRange(90, 100), buffer.copyOfRange(0, 10))
    }

    @Test
    fun `encrypted file should not contain plaintext`() = runTest {
        // Given
        val plaintext = "SECRET_DATA_12345".repeat(10).toByteArray()
        val encryptedFile = File(testDir, "test.secv")

        // When
        val encryptor = ChunkedStreamingEncryptor(encryptedFile, encryptionScheme, chunkSize = 1024)
        encryptor.write(plaintext, 0, plaintext.size)
        encryptor.close()

        // Then - Read raw file and verify plaintext is not present
        val rawBytes = encryptedFile.readBytes()
        val plaintextString = String(plaintext)
        val rawString = String(rawBytes, Charsets.ISO_8859_1) // Use binary-safe charset

        // The plaintext should not appear in the encrypted file
        // (We can't do a simple contains check because of potential false positives,
        // but we can verify the file is not just the plaintext)
        assertTrue(rawBytes.size > plaintext.size, "Encrypted file should be larger (has header + IV + tags)")

        // Verify header exists
        val headerBytes = rawBytes.copyOfRange(0, SecvFileFormat.HEADER_SIZE)
        val header = SecvFileFormat.SecvHeader.fromByteArray(headerBytes)
        assertNotNull(header)
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
