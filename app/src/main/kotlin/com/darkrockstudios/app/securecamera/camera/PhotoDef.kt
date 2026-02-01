package com.darkrockstudios.app.securecamera.camera

import java.io.File
import java.util.*

data class PhotoDef(
	val photoName: String,
	val photoFormat: String,
	val photoFile: File,
	val metadataTimestamp: Long? = null,
) : MediaItem {

	override val mediaName: String get() = photoName
	override val mediaFile: File get() = photoFile
	override val mediaType: MediaType get() = MediaType.PHOTO

	override fun dateTaken(): Date {
		return metadataTimestamp?.let { Date(it) } ?: Date()
	}
}
