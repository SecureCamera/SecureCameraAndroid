package com.darkrockstudios.app.securecamera.camera

import timber.log.Timber
import java.io.File
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

data class VideoDef(
	val videoName: String,
	val videoFormat: String,
	val videoFile: File,
) : MediaItem {

	override val mediaName: String get() = videoName
	override val mediaFile: File get() = videoFile
	override val mediaType: MediaType get() = MediaType.VIDEO

	override fun dateTaken(): Date {
		try {
			// Video filename format: video_yyyyMMdd_HHmmss.mp4
			val dateString = videoName.removePrefix("video_").removeSuffix(".$videoFormat")
			val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
			return dateFormat.parse(dateString) ?: Date()
		} catch (e: ParseException) {
			Timber.w(e, "Failed to parse video name to date")
			return Date()
		}
	}
}
