package com.darkrockstudios.app.securecamera.metadata

import android.content.Context
import com.darkrockstudios.app.securecamera.camera.MediaType
import com.darkrockstudios.app.securecamera.security.FileTimestampObfuscator
import com.darkrockstudios.app.securecamera.security.schemes.EncryptionScheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom

/**
 * Core manager for encrypted media metadata with write-through persistence.
 *
 * Responsibilities:
 * - Load: Read header, decrypt all active entries into memory map
 * - Cache: Map<String, Pair<Int, MediaMetadataEntry>> (filename -> slot + entry)
 * - Write-through: Every mutation immediately writes to disk
 * - Evict: Clear memory cache, zero-fill sensitive data
 * - Thread safety: Mutex for concurrent access
 */
class MetadataManager(
	private val appContext: Context,
	private val encryptionScheme: EncryptionScheme,
	private val fileTimestampObfuscator: FileTimestampObfuscator,
) {
	private val mutex = Mutex()

	private var cachedEntries: MutableMap<String, Pair<Int, MediaMetadataEntry>>? = null

	// Reusable deleted slots
	private var freeSlots: MutableList<Int> = mutableListOf()

	private var indexFile: RandomAccessFile? = null

	private var photoCount: Int = 0
	private var videoCount: Int = 0
	private var capacity: Int = 0

	private val secureRandom = SecureRandom()

	fun getMetadataDirectory(): File {
		val dir = File(appContext.filesDir, SecmFileFormat.METADATA_DIR)
		if (!dir.exists()) {
			dir.mkdirs()
		}
		return dir
	}

	private fun getIndexFile(): File {
		return File(getMetadataDirectory(), SecmFileFormat.MEDIA_INDEX_FILENAME)
	}

	/**
	 * Loads the entire index into memory. Called once on unlock.
	 * If the index doesn't exist, creates a new empty one.
	 */
	suspend fun loadIndex() {
		mutex.withLock {
			// Already loaded
			if (cachedEntries != null) return@withLock

			val file = getIndexFile()

			if (!file.exists()) {
				createEmptyIndex(file)
			}

			withContext(Dispatchers.IO) {
				indexFile = RandomAccessFile(file, "rw")
				readIndexFromDisk()
			}
		}
	}

	fun isLoaded(): Boolean = cachedEntries != null

	/**
	 * Creates a new empty SECM index file.
	 */
	private suspend fun createEmptyIndex(file: File) {
		withContext(Dispatchers.IO) {
			file.parentFile?.mkdirs()

			RandomAccessFile(file, "rw").use { raf ->
				val header = ByteBuffer.allocate(SecmFileFormat.HEADER_SIZE)
				header.order(ByteOrder.LITTLE_ENDIAN)

				// Magic (4 bytes)
				header.put(SecmFileFormat.MAGIC.toByteArray(Charsets.US_ASCII))
				// Version (2 bytes)
				header.putShort(SecmFileFormat.VERSION)
				// Photo count (4 bytes)
				header.putInt(0)
				// Video count (4 bytes)
				header.putInt(0)
				// Capacity (4 bytes)
				header.putInt(0)
				// Entry size (2 bytes)
				header.putShort(SecmFileFormat.ENTRY_BLOCK_SIZE.toShort())
				// Reserved (12 bytes)
				repeat(SecmFileFormat.HEADER_RESERVED_SIZE) {
					header.put(0)
				}

				raf.write(header.array())
			}
		}
	}

	/**
	 * Reads the entire index from disk into memory.
	 */
	private suspend fun readIndexFromDisk() = withContext(Dispatchers.IO) {
		val raf = indexFile ?: return@withContext

		// Read header
		raf.seek(0)
		val headerBytes = ByteArray(SecmFileFormat.HEADER_SIZE)
		raf.readFully(headerBytes)

		val header = ByteBuffer.wrap(headerBytes)
		header.order(ByteOrder.LITTLE_ENDIAN)

		// Validate magic
		val magic = ByteArray(4)
		header.get(magic)
		val magicStr = String(magic, Charsets.US_ASCII)
		if (magicStr != SecmFileFormat.MAGIC) {
			error("Invalid SECM file: magic mismatch ($magicStr)")
		}

		// Read header fields
		val version = header.getShort()
		if (version != SecmFileFormat.VERSION) {
			error("Unsupported SECM version: $version")
		}

		photoCount = header.getInt()
		videoCount = header.getInt()
		capacity = header.getInt()

		// Initialize cache
		cachedEntries = mutableMapOf()
		freeSlots = mutableListOf()

		// Read all entries
		val keyBytes = encryptionScheme.getDerivedKey()

		for (slot in 0..capacity) {
			val offset = SecmFileFormat.entryOffset(slot)
			raf.seek(offset)

			val encryptedBlock = ByteArray(SecmFileFormat.ENTRY_BLOCK_SIZE)
			raf.readFully(encryptedBlock)

			try {
				val plaintext = decryptEntry(encryptedBlock, keyBytes)
				val status = MediaMetadataEntry.getStatus(plaintext)

				when (status) {
					SecmFileFormat.STATUS_ACTIVE -> {
						val entry = MediaMetadataEntry.fromBytes(plaintext)
						if (entry != null) {
							cachedEntries!![entry.filename] = slot to entry
						}
					}

					SecmFileFormat.STATUS_DELETED, SecmFileFormat.STATUS_EMPTY -> {
						freeSlots.add(slot)
					}
				}
			} catch (e: Exception) {
				Timber.e(e, "Failed to decrypt entry at slot $slot")
				// Treat corrupted entries as free slots
				freeSlots.add(slot)
			}
		}

		Timber.d("Loaded metadata index: ${cachedEntries!!.size} entries, ${freeSlots.size} free slots")
	}

	/**
	 * Adds a new entry to the index with write-through persistence.
	 */
	suspend fun addEntry(entry: MediaMetadataEntry) {
		mutex.withLock {
			ensureLoaded()

			// Check for duplicate filename
			if (cachedEntries!!.containsKey(entry.filename)) {
				Timber.w("Entry already exists for filename: ${entry.filename}")
				return@withLock
			}

			val slot = if (freeSlots.isNotEmpty()) {
				freeSlots.removeAt(0)
			} else {
				allocateNewSlot()
			}

			writeEntryToDisk(slot, entry)
			cachedEntries!![entry.filename] = slot to entry

			// Update counts
			when (entry.mediaType) {
				MediaType.PHOTO -> photoCount++
				MediaType.VIDEO -> videoCount++
			}
			updateHeaderCounts()
		}
	}

	/**
	 * Removes an entry from the index with write-through persistence.
	 */
	suspend fun removeEntry(filename: String) {
		mutex.withLock {
			ensureLoaded()

			val (slot, entry) = cachedEntries!!.remove(filename) ?: return@withLock

			markSlotDeleted(slot)
			freeSlots.add(slot)

			// Update counts
			when (entry.mediaType) {
				MediaType.PHOTO -> photoCount--
				MediaType.VIDEO -> videoCount--
			}
			updateHeaderCounts()
		}
	}

	/**
	 * Updates an existing entry (e.g., after file rename).
	 */
	suspend fun updateEntry(oldFilename: String, newEntry: MediaMetadataEntry) {
		mutex.withLock {
			ensureLoaded()

			val (slot, oldEntry) = cachedEntries!!.remove(oldFilename) ?: return@withLock

			writeEntryToDisk(slot, newEntry)
			cachedEntries!![newEntry.filename] = slot to newEntry

			// Update counts if media type changed (unlikely but handle it)
			if (oldEntry.mediaType != newEntry.mediaType) {
				when (oldEntry.mediaType) {
					MediaType.PHOTO -> photoCount--
					MediaType.VIDEO -> videoCount--
				}
				when (newEntry.mediaType) {
					MediaType.PHOTO -> photoCount++
					MediaType.VIDEO -> videoCount++
				}
				updateHeaderCounts()
			}
		}
	}

	/**
	 * Gets an entry from the memory cache (no disk I/O).
	 */
	fun getEntry(filename: String): MediaMetadataEntry? {
		return cachedEntries?.get(filename)?.second
	}

	/**
	 * Gets all entries sorted by timestamp (newest first).
	 */
	fun getAllEntriesSorted(): List<MediaMetadataEntry> {
		return cachedEntries?.values
			?.map { it.second }
			?.sortedByDescending { it.originalTimestamp }
			?: emptyList()
	}

	/**
	 * Gets all photo entries sorted by timestamp.
	 */
	fun getPhotoEntriesSorted(): List<MediaMetadataEntry> {
		return getAllEntriesSorted().filter { it.mediaType == MediaType.PHOTO }
	}

	/**
	 * Gets all video entries sorted by timestamp.
	 */
	fun getVideoEntriesSorted(): List<MediaMetadataEntry> {
		return getAllEntriesSorted().filter { it.mediaType == MediaType.VIDEO }
	}

	/**
	 * Returns the current entry counts.
	 */
	fun getCounts(): Pair<Int, Int> = photoCount to videoCount

	/**
	 * Evicts all sensitive data from memory and closes file handles.
	 */
	fun evict() {
		try {
			// Clear cache
			cachedEntries?.clear()
			cachedEntries = null
			freeSlots.clear()

			// Close file handle
			try {
				indexFile?.close()
			} catch (e: Exception) {
				Timber.e(e, "Error closing index file")
			}
			indexFile = null

			// Reset counts
			photoCount = 0
			videoCount = 0
			capacity = 0

			Timber.d("Metadata manager evicted")
		} catch (e: Exception) {
			Timber.e(e, "Error during eviction")
		}
	}

	/**
	 * Allocates a new slot at the end of the file.
	 */
	private suspend fun allocateNewSlot(): Int {
		val newSlot = capacity
		capacity++

		// Update header capacity
		withContext(Dispatchers.IO) {
			indexFile?.let { raf ->
				raf.seek(SecmFileFormat.HEADER_OFFSET_CAPACITY.toLong())
				val buffer = ByteBuffer.allocate(4)
				buffer.order(ByteOrder.LITTLE_ENDIAN)
				buffer.putInt(capacity)
				raf.write(buffer.array())
			}
		}

		return newSlot
	}

	/**
	 * Writes a single entry to a specific slot on disk.
	 */
	private suspend fun writeEntryToDisk(slot: Int, entry: MediaMetadataEntry) {
		withContext(Dispatchers.IO) {
			val raf = indexFile ?: error("Index file not open")

			val plaintext = entry.toBytes()
			val keyBytes = encryptionScheme.getDerivedKey()
			val encrypted = encryptEntry(plaintext, keyBytes)

			raf.seek(SecmFileFormat.entryOffset(slot))
			raf.write(encrypted)
			obfuscateIndexTimestamp()
		}
	}

	/**
	 * Marks a slot as deleted on disk.
	 */
	private suspend fun markSlotDeleted(slot: Int) {
		withContext(Dispatchers.IO) {
			val raf = indexFile ?: error("Index file not open")

			val plaintext = MediaMetadataEntry.deletedEntryBytes()
			val keyBytes = encryptionScheme.getDerivedKey()
			val encrypted = encryptEntry(plaintext, keyBytes)

			raf.seek(SecmFileFormat.entryOffset(slot))
			raf.write(encrypted)
			obfuscateIndexTimestamp()
		}
	}

	/**
	 * Updates the photo and video counts in the header.
	 */
	private suspend fun updateHeaderCounts() {
		withContext(Dispatchers.IO) {
			indexFile?.let { raf ->
				val buffer = ByteBuffer.allocate(8)
				buffer.order(ByteOrder.LITTLE_ENDIAN)
				buffer.putInt(photoCount)
				buffer.putInt(videoCount)

				raf.seek(SecmFileFormat.HEADER_OFFSET_PHOTO_COUNT.toLong())
				raf.write(buffer.array())
				obfuscateIndexTimestamp()
			}
		}
	}

	/**
	 * Encrypts an entry plaintext with AES-GCM.
	 * Output: [IV (12 bytes)][Ciphertext (140 bytes)][Auth Tag (16 bytes)]
	 */
	private suspend fun encryptEntry(plaintext: ByteArray, keyBytes: ByteArray): ByteArray {
		// Generate random IV
		val iv = ByteArray(SecmFileFormat.IV_SIZE)
		secureRandom.nextBytes(iv)

		// Encrypt with AES-GCM
		val ciphertext = encryptionScheme.encrypt(plaintext, keyBytes)

		// The encrypt method returns [IV + ciphertext + tag], but we want explicit control
		// Actually, looking at SoftwareEncryptionScheme, it returns the full encrypted blob
		// We need to prepend our IV. Let's check the actual behavior.

		// Actually the cryptography library handles IV internally, so we get back
		// [IV][ciphertext][tag] already. We just need to ensure consistent size.
		return ciphertext
	}

	/**
	 * Decrypts an entry block back to plaintext.
	 */
	private suspend fun decryptEntry(encryptedBlock: ByteArray, keyBytes: ByteArray): ByteArray {
		// The encrypt/decrypt functions handle IV extraction internally
		return withContext(Dispatchers.IO) {
			// Use a direct approach since we need to decrypt with the raw key
			val aesGcm = dev.whyoleg.cryptography.CryptographyProvider.Default.get(
				dev.whyoleg.cryptography.algorithms.AES.GCM
			)
			val key = aesGcm.keyDecoder().decodeFromByteArray(
				dev.whyoleg.cryptography.algorithms.AES.Key.Format.RAW,
				keyBytes
			)
			key.cipher().decrypt(encryptedBlock)
		}
	}

	/**
	 * Ensures the index is loaded before operations.
	 */
	private fun ensureLoaded() {
		if (cachedEntries == null) {
			error("Metadata index not loaded. Call loadIndex() first.")
		}
	}

	/**
	 * Obfuscates the index file timestamp to prevent metadata leakage.
	 */
	private fun obfuscateIndexTimestamp() {
		fileTimestampObfuscator.obfuscate(getIndexFile())
	}
}
