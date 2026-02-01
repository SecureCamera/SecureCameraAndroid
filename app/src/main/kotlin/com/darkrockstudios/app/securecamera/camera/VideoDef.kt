package com.darkrockstudios.app.securecamera.camera

import com.darkrockstudios.app.securecamera.security.streaming.SecvFileFormat
import java.io.File
import java.util.*

data class VideoDef(
	val videoName: String,
	val videoFormat: String,
	val videoFile: File,
	val metadataTimestamp: Long? = null,
) : MediaItem {

	override val mediaName: String get() = videoName
	override val mediaFile: File get() = videoFile
	override val mediaType: MediaType get() = MediaType.VIDEO

	/**
	 * Returns true if this video is encrypted (uses .secv format).
	 */
	val isEncrypted: Boolean
		get() = videoFormat == SecvFileFormat.FILE_EXTENSION

	override fun dateTaken(): Date {
		return metadataTimestamp?.let { Date(it) } ?: Date()
	}
}
