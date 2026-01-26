package com.darkrockstudios.app.securecamera.playback

import androidx.media3.datasource.DataSource
import com.darkrockstudios.app.securecamera.security.streaming.StreamingDecryptor

/**
 * Factory for creating EncryptedVideoDataSource instances.
 * Each call to createDataSource returns a new DataSource that shares
 * the same StreamingDecryptor.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class EncryptedVideoDataSourceFactory(
	private val decryptor: StreamingDecryptor
) : DataSource.Factory {

	override fun createDataSource(): DataSource {
		return EncryptedVideoDataSource(decryptor)
	}
}
