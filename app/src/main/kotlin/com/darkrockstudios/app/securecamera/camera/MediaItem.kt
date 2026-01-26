package com.darkrockstudios.app.securecamera.camera

import java.io.File
import java.util.*

/**
 * Sealed interface representing a media item in the gallery.
 * Both photos and videos implement this interface for unified handling.
 */
sealed interface MediaItem {
	val mediaName: String
	val mediaFile: File

	/**
	 * Returns the date/time when this media was captured.
	 * Used for sorting in the gallery.
	 */
	fun dateTaken(): Date

	/**
	 * The type of media (photo or video)
	 */
	val mediaType: MediaType
}

enum class MediaType {
	PHOTO,
	VIDEO
}
