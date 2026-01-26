package com.darkrockstudios.app.securecamera.security.streaming

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.RandomAccessFile

/**
 * Helper class for encrypting video files using streaming encryption.
 * Handles the post-recording encryption flow and secure temp file deletion.
 */
class VideoEncryptionHelper(
	private val streamingScheme: StreamingEncryptionScheme
) {
	private val _encryptionProgress = MutableStateFlow<EncryptionProgress>(EncryptionProgress.Idle)
	val encryptionProgress: StateFlow<EncryptionProgress> = _encryptionProgress.asStateFlow()

	/**
	 * Encrypts a video file, converting it from .mp4 to .secv format.
	 * The original temp file is securely deleted after encryption.
	 *
	 * @param tempFile The unencrypted temporary video file
	 * @param outputFile The target encrypted .secv file
	 * @param chunkSize Size of encryption chunks (default 1MB)
	 * @return true if encryption was successful, false otherwise
	 */
	suspend fun encryptVideoFile(
		tempFile: File,
		outputFile: File,
		chunkSize: Int = SecvFileFormat.DEFAULT_CHUNK_SIZE
	): Boolean = withContext(Dispatchers.IO) {
		try {
			_encryptionProgress.value = EncryptionProgress.Starting

			if (!tempFile.exists()) {
				Timber.e("Temp video file does not exist: ${tempFile.absolutePath}")
				_encryptionProgress.value = EncryptionProgress.Error("Source file not found")
				return@withContext false
			}

			val totalSize = tempFile.length()
			var bytesProcessed = 0L

			_encryptionProgress.value = EncryptionProgress.InProgress(0f)

			// Create the streaming encryptor
			val encryptor = streamingScheme.createStreamingEncryptor(outputFile, chunkSize)

			try {
				// Read the temp file in chunks and write to encryptor
				RandomAccessFile(tempFile, "r").use { raf ->
					val buffer = ByteArray(chunkSize)

					while (bytesProcessed < totalSize) {
						val bytesToRead = minOf(chunkSize.toLong(), totalSize - bytesProcessed).toInt()
						raf.readFully(buffer, 0, bytesToRead)

						encryptor.write(buffer, 0, bytesToRead)

						bytesProcessed += bytesToRead
						val progress = bytesProcessed.toFloat() / totalSize.toFloat()
						_encryptionProgress.value = EncryptionProgress.InProgress(progress)
					}

					// Flush any remaining buffered data
					encryptor.flush()
				}
			} finally {
				encryptor.close()
			}

			// Verify the output file was created
			if (!outputFile.exists() || outputFile.length() == 0L) {
				Timber.e("Encrypted output file is empty or missing")
				_encryptionProgress.value = EncryptionProgress.Error("Encryption failed")
				return@withContext false
			}

			tempFile.delete()

			_encryptionProgress.value = EncryptionProgress.Completed
			Timber.i("Video encryption completed: ${outputFile.absolutePath}")

			return@withContext true
		} catch (e: Exception) {
			Timber.e(e, "Failed to encrypt video file")
			_encryptionProgress.value = EncryptionProgress.Error(e.message ?: "Unknown error")

			// Clean up partial output file on error
			if (outputFile.exists()) {
				outputFile.delete()
			}

			return@withContext false
		}
	}

	/**
	 * Resets the encryption progress state to Idle.
	 */
	fun resetProgress() {
		_encryptionProgress.value = EncryptionProgress.Idle
	}

	/**
	 * Represents the current state of video encryption.
	 */
	sealed class EncryptionProgress {
		data object Idle : EncryptionProgress()
		data object Starting : EncryptionProgress()
		data class InProgress(val progress: Float) : EncryptionProgress() // 0.0 to 1.0
		data object Completed : EncryptionProgress()
		data class Error(val message: String) : EncryptionProgress()
	}
}
