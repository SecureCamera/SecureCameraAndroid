package com.darkrockstudios.app.securecamera.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import com.darkrockstudios.app.securecamera.security.streaming.StreamingDecryptor
import kotlinx.coroutines.runBlocking

/**
 * ExoPlayer DataSource that reads from an encrypted SECV video file.
 * Uses StreamingDecryptor for on-the-fly chunk-based decryption.
 *
 * This implementation supports seeking in encrypted videos by leveraging
 * the chunk index table in the SECV format.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class EncryptedVideoDataSource(
	private val decryptor: StreamingDecryptor
) : BaseDataSource(/* isNetwork= */ false) {

	private var uri: Uri? = null
	private var position: Long = 0
	private var bytesRemaining: Long = 0

	override fun open(dataSpec: DataSpec): Long {
		transferInitializing(dataSpec)

		uri = dataSpec.uri
		position = dataSpec.position

		val totalSize = decryptor.totalSize

		bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
			dataSpec.length
		} else {
			totalSize - position
		}

		if (bytesRemaining < 0) {
			bytesRemaining = 0
		}

		transferStarted(dataSpec)

		return bytesRemaining
	}

	override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
		if (length == 0) {
			return 0
		}

		if (bytesRemaining == 0L) {
			return C.RESULT_END_OF_INPUT
		}

		val bytesToRead = minOf(length.toLong(), bytesRemaining).toInt()

		// Use runBlocking since DataSource.read() is synchronous
		val bytesRead = runBlocking {
			decryptor.read(position, buffer, offset, bytesToRead)
		}

		if (bytesRead == -1) {
			return C.RESULT_END_OF_INPUT
		}

		position += bytesRead
		bytesRemaining -= bytesRead

		bytesTransferred(bytesRead)

		return bytesRead
	}

	override fun getUri(): Uri? = uri

	override fun close() {
		uri = null
		// Note: We don't close the decryptor here as it may be reused
		// The caller is responsible for closing the decryptor when done
		transferEnded()
	}
}
